package com.lrj.oa.iam.infrastructure.mapper;

import org.apache.ibatis.annotations.*;
import java.time.OffsetDateTime;
import java.util.List;

@Mapper
public interface ElevationRequestMapper {
    class Row {
        public Long id; public Long tenantId; public String requesterId; public Long roleId;
        public String roleCode; public String roleName; public Integer hours; public String reason;
        public String status; public OffsetDateTime requestedAt; public String decidedBy;
        public OffsetDateTime decidedAt; public String decisionReason; public Long grantId;
    }
    @Select("SELECT id FROM oa_iam.elevation_request WHERE tenant_id=#{tenantId} AND requester_id=#{requesterId} AND role_id=#{roleId} AND status='PENDING'")
    Long pendingId(@Param("tenantId") long tenantId, @Param("requesterId") String requesterId, @Param("roleId") long roleId);
    @Select("""
            WITH inserted AS (
                INSERT INTO oa_iam.elevation_request(tenant_id, requester_id, role_id, hours, reason)
                VALUES (#{tenantId},#{requesterId},#{roleId},#{hours},#{reason})
                ON CONFLICT DO NOTHING RETURNING id
            )
            SELECT id FROM inserted
            UNION ALL
            SELECT id FROM oa_iam.elevation_request
             WHERE tenant_id=#{tenantId} AND requester_id=#{requesterId}
               AND role_id=#{roleId} AND status='PENDING'
            LIMIT 1
            """)
    Long insert(@Param("tenantId") long tenantId, @Param("requesterId") String requesterId,
                @Param("roleId") long roleId, @Param("hours") int hours, @Param("reason") String reason);
    @Select("""
            SELECT q.id,q.tenant_id AS tenantId,q.requester_id AS requesterId,q.role_id AS roleId,
                   r.code AS roleCode,r.name AS roleName,q.hours,q.reason,q.status,q.requested_at AS requestedAt,
                   q.decided_by AS decidedBy,q.decided_at AS decidedAt,q.decision_reason AS decisionReason,q.grant_id AS grantId
              FROM oa_iam.elevation_request q JOIN oa_iam.role r ON r.id=q.role_id
             WHERE q.tenant_id=#{tenantId} AND q.id=#{id} FOR UPDATE OF q
            """)
    Row selectForUpdate(@Param("tenantId") long tenantId, @Param("id") long id);
    @Select("""
            <script>
            SELECT q.id,q.tenant_id AS tenantId,q.requester_id AS requesterId,q.role_id AS roleId,
                   r.code AS roleCode,r.name AS roleName,q.hours,q.reason,q.status,q.requested_at AS requestedAt,
                   q.decided_by AS decidedBy,q.decided_at AS decidedAt,q.decision_reason AS decisionReason,q.grant_id AS grantId
              FROM oa_iam.elevation_request q JOIN oa_iam.role r ON r.id=q.role_id
             WHERE q.tenant_id=#{tenantId}
               <if test="requesterId != null and requesterId != ''">AND q.requester_id=#{requesterId}</if>
               <if test="status != null and status != ''">AND q.status=#{status}</if>
             ORDER BY q.id DESC LIMIT #{limit}
            </script>
            """)
    List<Row> list(@Param("tenantId") long tenantId,@Param("requesterId") String requesterId,
                   @Param("status") String status,@Param("limit") int limit);
    @Update("UPDATE oa_iam.elevation_request SET status='APPROVED',decided_by=#{by},decided_at=now(),decision_reason=#{reason},grant_id=#{grantId} WHERE tenant_id=#{tenantId} AND id=#{id} AND status='PENDING'")
    int approve(@Param("tenantId") long tenantId,@Param("id") long id,@Param("by") String by,
                @Param("reason") String reason,@Param("grantId") long grantId);
    @Update("UPDATE oa_iam.elevation_request SET status='REJECTED',decided_by=#{by},decided_at=now(),decision_reason=#{reason} WHERE tenant_id=#{tenantId} AND id=#{id} AND status='PENDING'")
    int reject(@Param("tenantId") long tenantId,@Param("id") long id,@Param("by") String by,@Param("reason") String reason);
}
