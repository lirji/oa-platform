package com.lrj.oa.job.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.LongSupplier;

/**
 * 跑批执行框架：幂等占位 → 执行 → 回写结果。
 *
 * <p><b>幂等靠数据库唯一约束抢坑，不靠分布式锁。</b>
 * 多实例同时到点、定时器重复触发、运维手工重跑，三种情况用同一条 {@code uk_job_idem} 挡住：
 * 谁先插入成功谁执行，其余的直接跳过。分布式锁还要处理超时、续期、脑裂，
 * 而这里根本不需要"持有"什么 —— 只需要"这件事有没有人做过"。
 *
 * <p><b>占位必须是独立事务</b>（{@code REQUIRES_NEW}）：跟业务同事务的话，
 * 业务失败回滚会把占位一起回滚，下一轮又跑一遍 —— 但这次可能是在业务已部分生效之后。
 * 号段分配踩过同样的坑（Phase 4）。
 */
@Component
public class JobRunner {

    private static final Logger log = LoggerFactory.getLogger(JobRunner.class);

    private final JdbcTemplate jdbc;

    public JobRunner(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /**
     * 带幂等的执行。
     *
     * @param body 返回影响行数
     * @return 实际执行返回 true；被幂等挡下返回 false
     */
    public boolean run(String jobName, String idemKey, int shard, int shardTotal, LongSupplier body) {
        Long runId = claim(jobName, idemKey, shard, shardTotal);
        if (runId == null) {
            log.debug("跑批 {} idem={} 已执行过，跳过", jobName, idemKey);
            return false;
        }
        long t0 = System.nanoTime();
        try {
            long affected = body.getAsLong();
            finish(runId, "SUCCESS", affected, null);
            log.info("跑批 {} 分片 {}/{} 完成：影响 {} 行，耗时 {} ms",
                    jobName, shard, shardTotal, affected, (System.nanoTime() - t0) / 1_000_000);
            return true;
        } catch (Exception e) {
            // 失败要标记出来，而且【不删占位行】—— 删了就变成"自动无限重试"，
            // 一个必然失败的 job 会每分钟重跑一次，把日志和数据库都淹掉。
            // 需要重跑时由运维显式删这一行，那是一个有意识的决定。
            finish(runId, "FAILED", 0, e.toString());
            log.error("跑批 {} 分片 {}/{} 失败：{}", jobName, shard, shardTotal, e.toString());
            return true;
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    Long claim(String jobName, String idemKey, int shard, int shardTotal) {
        try {
            return jdbc.queryForObject("""
                    INSERT INTO oa_sys.job_run(job_name, shard, shard_total, idem_key, status)
                    VALUES (?, ?, ?, ?, 'RUNNING') RETURNING id
                    """, Long.class, jobName, shard, shardTotal, idemKey);
        } catch (DuplicateKeyException e) {
            return null;
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void finish(long runId, String status, long affected, String error) {
        jdbc.update("UPDATE oa_sys.job_run SET status = ?, affected = ?, error = ?, finished_at = now()"
                + " WHERE id = ?", status, affected, error, runId);
    }
}
