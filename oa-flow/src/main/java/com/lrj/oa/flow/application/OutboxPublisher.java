package com.lrj.oa.flow.application;

import com.lrj.oa.flow.infrastructure.mapper.OutboxMapper;
import com.lrj.oa.flow.infrastructure.workflow.WorkflowCommandKafkaConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 发件箱投递器。把 {@code oa_outbox} 里的待发消息推到 Kafka。
 *
 * <p>投递与业务写入<b>解耦</b>：业务事务只负责把消息写进发件箱（同事务、必然原子），
 * 真正发送由这里异步完成，失败按指数退避重试。于是"单据落了消息没发"这种
 * 分布式系统里最常见的不一致，在源头上就不可能发生。
 *
 * <p>取任务用 {@code FOR UPDATE SKIP LOCKED}：多实例并行投递而不会重复发同一条，
 * 也不会互相阻塞。
 */
@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxMapper outboxMapper;
    private final KafkaTemplate<String, String> kafka;
    /** 中台命令总线；未配置时为 null，此时命令与业务事件同走一条总线（本地替身模式）。 */
    private final KafkaTemplate<String, String> workflowKafka;
    private final int batchSize;
    private final AtomicLong sent = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();

    public OutboxPublisher(OutboxMapper outboxMapper,
                           @Qualifier("oaEventKafkaTemplate")
                           ObjectProvider<KafkaTemplate<String, String>> kafkaProvider,
                           @Qualifier("workflowCommandKafkaTemplate")
                           ObjectProvider<KafkaTemplate<String, String>> workflowKafkaProvider,
                           @Value("${oa.flow.outbox.batch-size:100}") int batchSize) {
        this.outboxMapper = outboxMapper;
        this.kafka = kafkaProvider.getIfAvailable();
        this.workflowKafka = workflowKafkaProvider.getIfAvailable();
        this.batchSize = batchSize;
        if (this.kafka == null) log.warn("未找到 KafkaTemplate，发件箱只入库不投递");
    }

    /**
     * 按主题选总线。中台命令必须投到中台自己的 Kafka —— 投错总线时 send() 会正常返回 offset，
     * 消费方却永远收不到，是一种没有任何错误日志的静默失败。
     */
    private KafkaTemplate<String, String> busFor(String topic) {
        if (workflowKafka != null && topic != null
                && topic.startsWith(WorkflowCommandKafkaConfig.COMMAND_TOPIC_PREFIX)) {
            return workflowKafka;
        }
        return kafka;
    }

    @Scheduled(fixedDelayString = "${oa.flow.outbox.poll-ms:1000}")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publishPending() {
        if (kafka == null) return;
        List<Map<String, Object>> batch = outboxMapper.pollPending(batchSize);
        for (Map<String, Object> row : batch) {
            Long id = ((Number) row.get("id")).longValue();
            String topic = (String) row.get("topic");
            String key = (String) row.get("msgKey");
            String payload = (String) row.get("payload");
            try {
                // 同步等待：投递失败必须能被本事务感知并转入重试，不能 fire-and-forget
                busFor(topic).send(topic, key, payload).get(10, TimeUnit.SECONDS);
                outboxMapper.markSent(id);
                sent.incrementAndGet();
            } catch (Exception e) {
                outboxMapper.markFailed(id, e.toString());
                failed.incrementAndGet();
                log.warn("发件箱投递失败 id={} topic={}：{}", id, topic, e.toString());
            }
        }
    }

    public Map<String, Object> stats() {
        return Map.of("sent", sent.get(), "failed", failed.get(),
                "pending", outboxMapper.countByStatus("PENDING"),
                "dead", outboxMapper.countByStatus("FAILED"),
                "kafkaAvailable", kafka != null,
                "workflowBusAvailable", workflowKafka != null);
    }
}
