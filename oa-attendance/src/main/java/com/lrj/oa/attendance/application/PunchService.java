package com.lrj.oa.attendance.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lrj.oa.attendance.infrastructure.mapper.PunchMapper;
import com.lrj.oa.security.context.UserContext;
import com.lrj.oa.security.context.UserContextHolder;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 打卡削峰。
 *
 * <p><b>为什么需要削峰</b>：10,000 人 × 1.5 次集中在 9:00 前后，若六成落在五分钟内，
 * 瞬时就是 300–500 QPS。而打卡这件事对用户的要求是"按下去立刻有反馈"，
 * 对系统的要求是"一条都不能丢、一条都不能重"。
 *
 * <p><b>链路</b>：
 * <ol>
 *   <li>Redis {@code SETNX} 幂等键 —— 重复打卡直接返回成功，不进后续链路；</li>
 *   <li>入有界队列后<b>立即返回</b>，用户感知 &lt; 50ms；</li>
 *   <li>后台线程按"攒够一批或到时间"批量 INSERT，把 500 次单行写压成 1 次批量写；</li>
 *   <li>队列满则<b>降级为同步直写</b> —— 宁可慢，不可丢；</li>
 *   <li>落库失败则<b>删掉 Redis 幂等键</b>，让用户重试能成功，同时进死信表兜底。</li>
 * </ol>
 *
 * <p>第 5 条最容易被忘：幂等键先于落库写入，落库失败却不撤销的话，
 * 用户会永远卡在"显示打过了、库里没有"的状态。
 */
@Service
public class PunchService {

    private static final Logger log = LoggerFactory.getLogger(PunchService.class);
    private static final String IDEM_PREFIX = "oa:punch:";

    private final PunchMapper punchMapper;
    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final int batchSize;
    private final long flushIntervalMs;

    private final BlockingQueue<PunchMapper.PunchRow> queue;
    private ScheduledExecutorService flusher;

    private final AtomicLong accepted = new AtomicLong();
    private final AtomicLong duplicated = new AtomicLong();
    private final AtomicLong persisted = new AtomicLong();
    private final AtomicLong degraded = new AtomicLong();
    private final AtomicLong deadLettered = new AtomicLong();

    public PunchService(PunchMapper punchMapper, ObjectProvider<StringRedisTemplate> redisProvider,
                        ObjectMapper json,
                        @Value("${oa.attendance.punch.queue-size:20000}") int queueSize,
                        @Value("${oa.attendance.punch.batch-size:500}") int batchSize,
                        @Value("${oa.attendance.punch.flush-ms:200}") long flushIntervalMs) {
        this.punchMapper = punchMapper;
        this.redis = redisProvider.getIfAvailable();
        this.json = json;
        this.batchSize = batchSize;
        this.flushIntervalMs = flushIntervalMs;
        this.queue = new ArrayBlockingQueue<>(queueSize);
    }

    @PostConstruct
    void start() {
        flusher = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "oa-punch-flusher");
            t.setDaemon(true);
            return t;
        });
        flusher.scheduleWithFixedDelay(this::drainAndPersist, flushIntervalMs, flushIntervalMs, TimeUnit.MILLISECONDS);
        log.info("打卡削峰就绪：队列={} 批量={} 刷盘间隔={}ms", queue.remainingCapacity(), batchSize, flushIntervalMs);
    }

    @PreDestroy
    void stop() {
        if (flusher != null) flusher.shutdown();
        drainAndPersist();      // 停机前把队列里的清空，不能带着未落库的打卡下线
    }

    public record PunchResult(boolean accepted, boolean duplicate, String message) {}

    /** 打卡。返回极快 —— 真正的落库在后台批量完成。 */
    public PunchResult punch(String type, BigDecimal lat, BigDecimal lng, String deviceId, String source) {
        UserContext ctx = UserContextHolder.require();
        LocalDate today = LocalDate.now();
        String idemKey = IDEM_PREFIX + ctx.userId() + ":" + today + ":" + type;

        // ① Redis 幂等：重复打卡不进后续链路。TTL 2 天，跨过日界后自然失效。
        if (redis != null) {
            Boolean first = redis.opsForValue().setIfAbsent(idemKey, "1", Duration.ofDays(2));
            if (!Boolean.TRUE.equals(first)) {
                duplicated.incrementAndGet();
                return new PunchResult(true, true, "今日已" + ("IN".equals(type) ? "上班" : "下班") + "打卡");
            }
        }

        PunchMapper.PunchRow row = new PunchMapper.PunchRow();
        row.tenantId = ctx.tenantId();
        row.userId = ctx.userId();
        row.employeeId = ctx.employeeId();
        row.punchDate = today;
        row.punchType = type;
        row.punchTime = OffsetDateTime.now();
        row.source = source == null ? "MOBILE" : source;
        row.latitude = lat;
        row.longitude = lng;
        row.deviceId = deviceId;
        row.orgId = ctx.primaryOrgId();
        row.orgPath = ctx.primaryOrgPath();

        accepted.incrementAndGet();
        if (!queue.offer(row)) {
            // ② 队列满 → 降级同步直写。慢一点没关系，丢了才是事故。
            degraded.incrementAndGet();
            persistBatch(List.of(row));
        }
        return new PunchResult(true, false, "打卡成功");
    }

    /** 后台批量落库：把 N 次单行写压成一次批量写。 */
    void drainAndPersist() {
        List<PunchMapper.PunchRow> batch = new ArrayList<>(batchSize);
        queue.drainTo(batch, batchSize);
        if (batch.isEmpty()) return;
        persistBatch(batch);
    }

    private void persistBatch(List<PunchMapper.PunchRow> batch) {
        try {
            punchMapper.batchInsert(batch);
            persisted.addAndGet(batch.size());
        } catch (Exception e) {
            log.error("打卡批量落库失败（{} 条），转死信并撤销幂等键", batch.size(), e);
            for (PunchMapper.PunchRow row : batch) {
                // ★ 撤销幂等键：否则用户会永远停在"显示打过了、库里没有"的状态
                if (redis != null) {
                    redis.delete(IDEM_PREFIX + row.userId + ":" + row.punchDate + ":" + row.punchType);
                }
                try {
                    punchMapper.deadLetter(json.writeValueAsString(Map.of(
                            "userId", row.userId, "date", row.punchDate.toString(),
                            "type", row.punchType, "time", row.punchTime.toString())), e.toString());
                    deadLettered.incrementAndGet();
                } catch (Exception ignored) { /* 死信也写不进去只能靠日志了 */ }
            }
        }
    }

    /** 冒烟/压测用：强制把队列排空，避免断言时还有在途数据。 */
    public void flushNow() {
        while (!queue.isEmpty()) drainAndPersist();
    }

    public Map<String, Object> stats() {
        return Map.of(
                "accepted", accepted.get(),
                "duplicated", duplicated.get(),
                "persisted", persisted.get(),
                "degradedToSync", degraded.get(),
                "deadLettered", deadLettered.get(),
                "queueDepth", queue.size(),
                "redisAvailable", redis != null);
    }
}
