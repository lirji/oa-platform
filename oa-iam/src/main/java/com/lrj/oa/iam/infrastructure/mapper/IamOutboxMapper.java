package com.lrj.oa.iam.infrastructure.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface IamOutboxMapper {

    String ROLE_CHANGED_TOPIC = "iam.role.changed.v1";

    @Insert("""
            INSERT INTO oa_iam.iam_outbox(event_id, tenant_id, topic, msg_key, payload)
            VALUES (CAST(#{eventId} AS uuid), #{tenantId}, #{topic}, #{messageKey}, CAST(#{payload} AS jsonb))
            """)
    int append(@Param("eventId") String eventId, @Param("tenantId") long tenantId,
               @Param("topic") String topic, @Param("messageKey") String messageKey,
               @Param("payload") String payload);

    /** Atomically lease rows so multiple application replicas can publish without holding a DB lock during Kafka I/O. */
    @Select("""
            UPDATE oa_iam.iam_outbox outbox
               SET status='PROCESSING', claimed_by=#{claimant},
                   claimed_until=now() + interval '30 seconds'
             WHERE outbox.id IN (
                   SELECT id FROM oa_iam.iam_outbox
                    WHERE (status='PENDING' AND next_attempt_at <= now())
                       OR (status='PROCESSING' AND claimed_until < now())
                    ORDER BY id
                    LIMIT #{limit}
                    FOR UPDATE SKIP LOCKED
             )
            RETURNING id, event_id::text AS event_id, tenant_id, topic,
                      msg_key AS message_key, payload::text AS payload, attempts
            """)
    List<Row> claimPending(@Param("limit") int limit, @Param("claimant") String claimant);

    @Update("""
            UPDATE oa_iam.iam_outbox
               SET status='SENT', sent_at=now(), claimed_by=NULL, claimed_until=NULL
             WHERE id=#{id} AND status='PROCESSING' AND claimed_by=#{claimant}
            """)
    int markSent(@Param("id") long id, @Param("claimant") String claimant);

    /** Exponential retry up to ten attempts; the lease is always released. */
    @Update("""
            UPDATE oa_iam.iam_outbox
               SET attempts=attempts+1,
                   last_error=#{error},
                   status=CASE WHEN attempts+1 >= 10 THEN 'FAILED' ELSE 'PENDING' END,
                   next_attempt_at=now() + make_interval(secs => least(power(2, attempts+1), 60)),
                   claimed_by=NULL,
                   claimed_until=NULL
             WHERE id=#{id} AND status='PROCESSING' AND claimed_by=#{claimant}
            """)
    int markFailed(@Param("id") long id, @Param("claimant") String claimant,
                   @Param("error") String error);

    @Select("SELECT count(*) FROM oa_iam.iam_outbox WHERE status=#{status}")
    long countByStatus(@Param("status") String status);

    class Row {
        private Long id;
        private String eventId;
        private Long tenantId;
        private String topic;
        private String messageKey;
        private String payload;
        private Integer attempts;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getEventId() { return eventId; }
        public void setEventId(String eventId) { this.eventId = eventId; }
        public Long getTenantId() { return tenantId; }
        public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
        public String getTopic() { return topic; }
        public void setTopic(String topic) { this.topic = topic; }
        public String getMessageKey() { return messageKey; }
        public void setMessageKey(String messageKey) { this.messageKey = messageKey; }
        public String getPayload() { return payload; }
        public void setPayload(String payload) { this.payload = payload; }
        public Integer getAttempts() { return attempts; }
        public void setAttempts(Integer attempts) { this.attempts = attempts; }
    }
}
