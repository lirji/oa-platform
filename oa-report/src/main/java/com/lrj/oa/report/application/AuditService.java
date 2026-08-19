package com.lrj.oa.report.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lrj.oa.security.context.UserContext;
import com.lrj.oa.security.context.UserContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 审计日志写入。
 *
 * <p><b>异步 + 有界队列 + 满则丢弃</b>。这个取舍在 Phase 2 已经付过学费：
 * 影子校验当时是同步执行的，1% 采样把 p99 从 0.9μs 顶到 5034μs ——
 * <b>诊断手段绝不能拖慢它要诊断的东西</b>。审计同理：它是旁路，
 * 不该让一次审批因为审计表慢而失败。
 *
 * <p>队列满时丢弃并计数（{@link #droppedCount()}）。丢弃是有代价的，
 * 所以必须<b>可观测</b> —— 一个悄悄丢日志的审计系统是最糟的组合：
 * 既没有性能问题的告警，也没有完整的记录。
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final JdbcTemplate jdbc;
    private final ObjectMapper json = new ObjectMapper();
    private final BlockingQueue<Object[]> queue;
    private final Thread worker;
    private volatile long dropped = 0;
    private volatile long written = 0;

    public AuditService(JdbcTemplate jdbc, @Value("${oa.audit.queue-size:10000}") int queueSize) {
        this.jdbc = jdbc;
        this.queue = new ArrayBlockingQueue<>(queueSize);
        this.worker = Thread.ofVirtual().name("audit-writer").start(this::drainLoop);
    }

    /** 记一条。永不抛异常 —— 审计失败不该让业务失败。 */
    public void record(String action, String module, String targetType, String targetId,
                       String outcome, String error, Map<String, Object> detail) {
        try {
            UserContext ctx = UserContextHolder.peek();
            String detailJson = detail == null ? null : json.writeValueAsString(detail);
            Object[] row = new Object[]{
                    ctx == null ? null : ctx.userId(),
                    ctx == null ? null : ctx.username(),
                    detail == null ? null : (String) detail.get("onBehalfOf"),
                    action, module, targetType, targetId, outcome, error,
                    detail == null ? null : (String) detail.get("clientIp"),
                    detailJson,
                    ctx == null ? null : ctx.primaryOrgId(),
                    ctx == null ? null : ctx.primaryOrgPath()};
            if (!queue.offer(row)) {
                dropped++;
                if (dropped % 100 == 1) {
                    log.warn("审计队列已满，已丢弃 {} 条 —— 说明写入跟不上产生速度，需要扩容或降采样", dropped);
                }
            }
        } catch (Exception e) {
            log.debug("审计记录构造失败（不影响业务）：{}", e.toString());
        }
    }

    private void drainLoop() {
        List<Object[]> batch = new ArrayList<>(200);
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Object[] first = queue.poll(1, TimeUnit.SECONDS);
                if (first == null) continue;
                batch.clear();
                batch.add(first);
                queue.drainTo(batch, 199);
                flush(batch);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.warn("审计批量写入失败，本批 {} 条丢弃：{}", batch.size(), e.toString());
            }
        }
    }

    private void flush(List<Object[]> batch) {
        jdbc.batchUpdate("""
                INSERT INTO oa_sys.audit_log
                    (actor_id, actor_name, on_behalf_of, action, module, target_type, target_id,
                     outcome, error, client_ip, detail, org_id, org_path)
                VALUES (?,?,?,?,?,?,?,?,?,?,?::jsonb,?,?)
                """, batch);
        written += batch.size();
    }

    /** 冒烟与运维要能确认"确实落库了"，而不是"看起来记了"。 */
    public void flushNow() {
        List<Object[]> batch = new ArrayList<>();
        queue.drainTo(batch);
        if (!batch.isEmpty()) flush(batch);
    }

    public long droppedCount() { return dropped; }
    public long writtenCount() { return written; }

    /** 审计查询。按人 / 按动作 / 只看被拒绝的。 */
    public List<Map<String, Object>> query(String actorId, String action, Boolean deniedOnly, int limit) {
        StringBuilder sql = new StringBuilder("""
                SELECT id, actor_id, actor_name, on_behalf_of, action, module, target_type, target_id,
                       outcome, error, created_at
                  FROM oa_sys.audit_log WHERE 1 = 1
                """);
        List<Object> args = new ArrayList<>();
        if (actorId != null && !actorId.isBlank()) { sql.append(" AND actor_id = ?"); args.add(actorId); }
        if (action != null && !action.isBlank())   { sql.append(" AND action = ?");   args.add(action); }
        if (Boolean.TRUE.equals(deniedOnly))       { sql.append(" AND outcome = 'DENIED'"); }
        sql.append(" ORDER BY created_at DESC, id DESC LIMIT ?");
        args.add(Math.min(Math.max(limit, 1), 500));

        List<Map<String, Object>> out = new ArrayList<>();
        jdbc.query(sql.toString(), (RowCallbackHandler) rs -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", rs.getLong("id"));
            m.put("actorId", rs.getString("actor_id"));
            m.put("actorName", rs.getString("actor_name"));
            m.put("onBehalfOf", rs.getString("on_behalf_of"));
            m.put("action", rs.getString("action"));
            m.put("module", rs.getString("module"));
            m.put("targetType", rs.getString("target_type"));
            m.put("targetId", rs.getString("target_id"));
            m.put("outcome", rs.getString("outcome"));
            m.put("error", rs.getString("error"));
            m.put("createdAt", rs.getObject("created_at", OffsetDateTime.class));
            out.add(m);
        }, args.toArray());
        return out;
    }
}
