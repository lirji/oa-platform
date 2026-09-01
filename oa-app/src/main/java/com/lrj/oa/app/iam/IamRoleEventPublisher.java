package com.lrj.oa.app.iam;

import com.lrj.oa.iam.api.IamRoleEventOutboxApi;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/** Delivers durable IAM role events to the OA Kafka bus; cache invalidation remains on Redis Pub/Sub. */
@Component
@ConditionalOnProperty(name = "oa.iam.outbox.enabled", havingValue = "true", matchIfMissing = true)
public class IamRoleEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(IamRoleEventPublisher.class);

    private final IamRoleEventOutboxApi outbox;
    private final KafkaTemplate<String, String> kafka;
    private final int batchSize;
    private final String claimant = "iam-outbox-" + UUID.randomUUID().toString().substring(0, 8);
    private final AtomicLong sent = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();

    public IamRoleEventPublisher(IamRoleEventOutboxApi outbox,
                                 @Qualifier("oaEventKafkaTemplate")
                                 ObjectProvider<KafkaTemplate<String, String>> kafkaProvider,
                                 ObjectProvider<MeterRegistry> meterProvider,
                                 @Value("${oa.iam.outbox.batch-size:100}") int batchSize) {
        this.outbox = outbox;
        this.kafka = kafkaProvider.getIfAvailable();
        this.batchSize = Math.min(Math.max(batchSize, 1), 500);
        MeterRegistry meters = meterProvider.getIfAvailable();
        if (meters != null) {
            meters.gauge("oa_iam_outbox_sent_total", sent, AtomicLong::get);
            meters.gauge("oa_iam_outbox_failed_total", failed, AtomicLong::get);
            meters.gauge("oa_iam_outbox_pending", outbox,
                    value -> value.count("PENDING"));
            meters.gauge("oa_iam_outbox_dead", outbox,
                    value -> value.count("FAILED"));
        }
        if (kafka == null) log.warn("OA Kafka 不可用，IAM 角色事件保留在 Outbox 等待投递");
    }

    @Scheduled(fixedDelayString = "${oa.iam.outbox.poll-ms:1000}")
    public void publishPending() {
        if (kafka == null) return;
        // Lease one row at a time. Leasing a large batch before sequential Kafka sends lets later leases expire
        // and another replica can duplicate-deliver them while this publisher is still working through the batch.
        for (int i = 0; i < batchSize; i++) {
            var claimed = outbox.claimPending(1, claimant);
            if (claimed.isEmpty()) break;
            IamRoleEventOutboxApi.PendingEvent event = claimed.getFirst();
            try {
                kafka.send(event.topic(), event.messageKey(), event.payload()).get(10, TimeUnit.SECONDS);
                outbox.markSent(event.id(), claimant);
                sent.incrementAndGet();
            } catch (Exception e) {
                outbox.markFailed(event.id(), claimant, e.toString());
                failed.incrementAndGet();
                log.warn("IAM 角色事件投递失败 id={} eventId={} topic={}：{}",
                        event.id(), event.eventId(), event.topic(), e.toString());
            }
        }
    }

    public Map<String, Object> stats() {
        return Map.of("sent", sent.get(), "failed", failed.get(),
                "pending", outbox.count("PENDING"), "dead", outbox.count("FAILED"),
                "kafkaAvailable", kafka != null, "claimant", claimant);
    }
}
