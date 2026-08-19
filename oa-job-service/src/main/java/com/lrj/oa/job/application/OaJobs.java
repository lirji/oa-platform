package com.lrj.oa.job.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 具体跑批任务。Phase 5 与 Phase 2 遗留的"当前由接口手动触发"在这里被接管。
 *
 * <p>分片策略统一是 {@code employee_id % shardTotal}（FINAL_PLAN §9.3 场景③）：
 * 取模而不是按 id 区间切，是因为 id 区间会随离职留下空洞，越切越不均。
 */
@Component
public class OaJobs {

    private static final Logger log = LoggerFactory.getLogger(OaJobs.class);

    private final JdbcTemplate jdbc;
    private final JobRunner runner;
    private final int shardTotal;
    private final AtomicLong lastRunMs = new AtomicLong();

    public OaJobs(JdbcTemplate jdbc, JobRunner runner,
                  @Value("${oa.job.shards:16}") int shardTotal) {
        this.jdbc = jdbc;
        this.runner = runner;
        this.shardTotal = shardTotal;
    }

    // ───────────────────────────── 考勤日结（接管 Phase 5 的手动触发）
    /** 每天凌晨 01:10 结算前一天。分片并行，各片互不重叠。 */
    @Scheduled(cron = "${oa.job.cron.attendance-daily:0 10 1 * * *}")
    public void attendanceDaily() {
        computeAttendanceFor(LocalDate.now().minusDays(1));
    }

    /** 供手动触发与冒烟调用。 */
    public long computeAttendanceFor(LocalDate day) {
        long total = 0;
        for (int shard = 0; shard < shardTotal; shard++) {
            final int s = shard;
            String idem = "attendance-daily:" + day + ":" + s;
            runner.run("attendance-daily", idem, s, shardTotal, () -> aggregateShard(day, s));
        }
        Long n = jdbc.queryForObject("SELECT count(*) FROM oa_att.attendance_daily WHERE work_date = ?",
                Long.class, day);
        total = n == null ? 0 : n;
        lastRunMs.set(System.currentTimeMillis());
        return total;
    }

    /**
     * 单个分片的聚合。<b>全部在数据库里完成</b>，不把 10,000 人的打卡记录拉回 JVM 再算 ——
     * 那是 Phase 5 已经验证过的做法（10,000 人 165 ms）。
     */
    private long aggregateShard(LocalDate day, int shard) {
        return jdbc.update("""
                INSERT INTO oa_att.attendance_daily
                    (tenant_id, user_id, work_date, first_in, last_out, work_minutes, status,
                     org_id, org_path, computed_at)
                SELECT 1, p.user_id, p.punch_date,
                       min(p.punch_time) FILTER (WHERE p.punch_type = 'IN'),
                       max(p.punch_time) FILTER (WHERE p.punch_type = 'OUT'),
                       coalesce(EXTRACT(EPOCH FROM (
                           max(p.punch_time) FILTER (WHERE p.punch_type = 'OUT')
                         - min(p.punch_time) FILTER (WHERE p.punch_type = 'IN'))) / 60, 0)::int,
                       CASE WHEN min(p.punch_time) FILTER (WHERE p.punch_type = 'IN') IS NULL
                            THEN 'ABSENT' ELSE 'NORMAL' END,
                       max(p.org_id), max(p.org_path), now()
                  FROM oa_att.punch_record p
                 WHERE p.punch_date = ?
                   AND coalesce(p.employee_id, 0) % ? = ?
                 GROUP BY p.user_id, p.punch_date
                ON CONFLICT (tenant_id, user_id, work_date) DO UPDATE
                    SET first_in = EXCLUDED.first_in, last_out = EXCLUDED.last_out,
                        work_minutes = EXCLUDED.work_minutes, status = EXCLUDED.status,
                        computed_at = now()
                """, day, shardTotal, shard);
    }

    // ───────────────────────────── 授权到期回收（接管 Phase 2 的手动触发）
    /**
     * 每 5 分钟回收过期授权。
     *
     * <p>过期判定本来就在判权热路径里做（快照 TTL 被压到到期时刻），这个跑批不是为了"生效"，
     * 而是为了<b>清理</b>：过期的行留在库里，会让权限重算每次都多扫一遍，
     * 也让"这个人现在有哪些授权"这个问题的答案里混着一堆早已失效的记录。
     */
    @Scheduled(cron = "${oa.job.cron.grant-expire:0 */5 * * * *}")
    public void expireGrants() {
        String idem = "grant-expire:" + (System.currentTimeMillis() / 300_000);
        // 表里没有 status 列 —— "是否有效"由 revoked_at IS NULL + valid_to 共同表达
        // （见 GrantMapper 的活跃授权过滤条件）。所以"回收"就是按同一套语义标成已撤销，
        // 而不是发明第三种状态：多一种状态就多一处需要每个查询都记得处理的分支。
        runner.run("grant-expire", idem, 0, 1, () ->
                jdbc.update("UPDATE oa_iam.grant_record"
                        + " SET revoked_at = now(), revoked_by = 'system:job',"
                        + "     revoke_reason = 'AUTO_EXPIRED'"
                        + " WHERE revoked_at IS NULL AND valid_to IS NOT NULL AND valid_to <= now()"));
    }

    // ───────────────────────────── 分区滚动
    /**
     * 每月 1 日 00:05 为下个月预建分区。
     *
     * <p>建表用 {@code IF NOT EXISTS}，重复执行安全。<b>兜底分区不能省</b> ——
     * 万一这个 job 挂了几个月，数据会先落进 default 分区而不是直接写失败。
     * 打卡不能因为运维忘了建分区就丢（Phase 5 已定的原则）。
     */
    @Scheduled(cron = "${oa.job.cron.partition-roll:0 5 0 1 * *}")
    public void rollPartitions() {
        String idem = "partition-roll:" + LocalDate.now().withDayOfMonth(1);
        runner.run("partition-roll", idem, 0, 1, () -> {
            long n = 0;
            n += ensureMonthlyPartitions("oa_att", "punch_record", "punch_time", 3);
            n += ensureMonthlyPartitions("oa_notify", "notification", "created_at", 3);
            n += ensureMonthlyPartitions("oa_sys", "audit_log", "created_at", 3);
            return n;
        });
    }

    /** 为未来 monthsAhead 个月建分区。返回新建数量。 */
    public long ensureMonthlyPartitions(String schema, String table, String col, int monthsAhead) {
        long created = 0;
        for (int i = 1; i <= monthsAhead; i++) {
            LocalDate from = LocalDate.now().withDayOfMonth(1).plusMonths(i);
            LocalDate to = from.plusMonths(1);
            String suffix = "%d%02d".formatted(from.getYear(), from.getMonthValue());
            String part = "%s.%s_%s".formatted(schema, table, suffix);
            Integer exists = jdbc.queryForObject(
                    "SELECT count(*) FROM pg_tables WHERE schemaname = ? AND tablename = ?",
                    Integer.class, schema, table + "_" + suffix);
            if (exists != null && exists > 0) continue;
            jdbc.execute("CREATE TABLE IF NOT EXISTS %s PARTITION OF %s.%s FOR VALUES FROM ('%s') TO ('%s')"
                    .formatted(part, schema, table, from, to));
            created++;
        }
        return created;
    }

    // ───────────────────────────── 假期额度年初批量发放（Phase 5 的已知边界）
    /**
     * 每年 1 月 1 日发放年假额度。
     *
     * <p>用 {@code ON CONFLICT DO NOTHING} 而不是 UPDATE：重复执行不该把员工
     * 已经用掉的天数覆盖回去。年初发放是"确保有这一行"，不是"重置这一行"。
     */
    @Scheduled(cron = "${oa.job.cron.leave-grant:0 30 0 1 1 *}")
    public void grantAnnualLeave() {
        grantAnnualLeaveFor(String.valueOf(LocalDate.now().getYear()));
    }

    public long grantAnnualLeaveFor(String period) {
        final long[] affected = {0};
        runner.run("leave-annual-grant", "leave-annual-grant:" + period, 0, 1, () -> {
            // 注意是 leave_type_id 外键而不是 code —— 表结构里没有 code 列，
            // 写错列名在 MyBatis/JdbcTemplate 下是运行期才炸的那种错。
            affected[0] = jdbc.update("""
                    INSERT INTO oa_flow.leave_balance
                        (tenant_id, user_id, leave_type_id, period, total_days, used_days, frozen_days)
                    SELECT 1, e.user_id, t.id, ?, 10, 0, 0
                      FROM oa_org.employee e
                      CROSS JOIN oa_flow.leave_type t
                     WHERE e.status = 'ACTIVE' AND t.code = 'ANNUAL'
                    ON CONFLICT DO NOTHING
                    """, period);
            return affected[0];
        });
        return affected[0];
    }

    public Map<String, Object> stats() {
        return Map.of("shardTotal", shardTotal, "lastRunMs", lastRunMs.get());
    }
}
