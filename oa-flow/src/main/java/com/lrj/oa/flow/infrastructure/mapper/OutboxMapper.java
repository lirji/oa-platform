package com.lrj.oa.flow.infrastructure.mapper;

import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Map;

/**
 * 事务发件箱。
 *
 * <p><b>为什么必须有它</b>：发起流程要发 Kafka，而业务单据要落库。
 * 两件事若不在同一个事务里，就必然出现"单据落了消息没发"或"消息发了单据回滚"。
 * 发件箱把消息也变成一次数据库写入，于是两者天然原子；投递由独立轮询完成，失败可重试。
 * 这也是 workflow-platform 已锁定的接入方式（同步 HTTP 发起被明确否决过）。
 */
@Mapper
public interface OutboxMapper {

    @Insert("""
            INSERT INTO oa_flow.oa_outbox(topic, msg_key, payload)
            VALUES (#{topic}, #{key}, #{payload})
            """)
    int append(@Param("topic") String topic, @Param("key") String key, @Param("payload") String payload);

    @Select("""
            SELECT id, topic, msg_key AS "msgKey", payload, attempts
              FROM oa_flow.oa_outbox
             WHERE status = 'PENDING' AND next_attempt_at <= now()
             ORDER BY id
             LIMIT #{limit}
             FOR UPDATE SKIP LOCKED
            """)
    List<Map<String, Object>> pollPending(@Param("limit") int limit);

    @Update("UPDATE oa_flow.oa_outbox SET status = 'SENT', sent_at = now() WHERE id = #{id}")
    int markSent(@Param("id") Long id);

    /** 指数退避：第 n 次失败后等 2^n 秒再试，最多 60 秒；超过 10 次转 FAILED 等人工介入。 */
    @Update("""
            UPDATE oa_flow.oa_outbox
               SET attempts = attempts + 1,
                   last_error = #{error},
                   status = CASE WHEN attempts + 1 >= 10 THEN 'FAILED' ELSE 'PENDING' END,
                   next_attempt_at = now() + make_interval(secs => least(power(2, attempts + 1), 60))
             WHERE id = #{id}
            """)
    int markFailed(@Param("id") Long id, @Param("error") String error);

    @Select("SELECT count(*) FROM oa_flow.oa_outbox WHERE status = #{status}")
    long countByStatus(@Param("status") String status);

    // ── 收件箱：按 eventId 幂等去重（中台契约要求） ──────────
    @Insert("""
            INSERT INTO oa_flow.oa_inbox(event_id, event_type) VALUES (#{eventId}, #{eventType})
            ON CONFLICT (event_id) DO NOTHING
            """)
    int markConsumed(@Param("eventId") String eventId, @Param("eventType") String eventType);
}
