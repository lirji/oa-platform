package com.lrj.oa.flow.infrastructure.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

@Mapper
public interface WorkbenchMapper {
    class ApplicationRow {
        public String bizType; public String docNo; public String status; public String title;
        public String summary; public BigDecimal amount; public BigDecimal days; public OffsetDateTime createdAt;
    }

    @Select("""
            SELECT * FROM (
                SELECT 'LEAVE' AS "bizType", r.request_no AS "docNo", r.status,
                       t.name || ' ' || r.days || ' 天' AS title, r.reason AS summary,
                       NULL::numeric AS amount, r.days, r.created_at AS "createdAt"
                  FROM oa_flow.leave_request r JOIN oa_flow.leave_type t ON t.id=r.leave_type_id
                 WHERE r.tenant_id=#{tenantId} AND r.user_id=#{userId}
                UNION ALL
                SELECT d.biz_type AS "bizType", d.doc_no AS "docNo", d.status, d.title, d.summary,
                       d.amount, d.days, d.created_at AS "createdAt"
                  FROM oa_flow.business_doc d
                 WHERE d.tenant_id=#{tenantId} AND d.applicant_id=#{userId}
            ) x
            WHERE (#{bizType}::text IS NULL OR x."bizType"=#{bizType}::text)
            ORDER BY x."createdAt" DESC LIMIT #{limit}
            """)
    List<ApplicationRow> selectOwn(@Param("tenantId") long tenantId, @Param("userId") String userId,
                                   @Param("bizType") String bizType, @Param("limit") int limit);
}
