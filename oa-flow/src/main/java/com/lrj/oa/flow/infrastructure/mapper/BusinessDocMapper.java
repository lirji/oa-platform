package com.lrj.oa.flow.infrastructure.mapper;

import org.apache.ibatis.annotations.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

@Mapper
public interface BusinessDocMapper {
    class TemplateRow { public String name; public String schemaJson; }
    class RuleRow { public String driver; public String thresholds; }
    class DocRow {
        public Long id; public String bizType; public String docNo; public String title; public String summary;
        public String formData; public BigDecimal amount; public BigDecimal days; public String status;
        public OffsetDateTime createdAt;
    }
    class TypeRow {
        public String code; public String name; public Integer version; public String schemaJson;
        public String driver; public String rule;
    }

    @Select("SELECT biz_type FROM oa_flow.approval_level_rule ORDER BY biz_type")
    List<String> configuredTypes();

    @Select("""
            INSERT INTO oa_flow.business_doc
                (tenant_id, biz_type, doc_no, applicant_id, applicant_name, title, summary,
                 form_data, amount, days, status, org_id, org_path)
            VALUES (#{tenantId}, #{bizType}, #{docNo}, #{applicantId}, #{applicantName}, #{title},
                    #{summary}, CAST(#{formData} AS jsonb), #{amount}, #{days}, 'PENDING', #{orgId}, #{orgPath})
            RETURNING id
            """)
    Long insert(@Param("tenantId") long tenantId, @Param("bizType") String bizType,
                @Param("docNo") String docNo, @Param("applicantId") String applicantId,
                @Param("applicantName") String applicantName, @Param("title") String title,
                @Param("summary") String summary, @Param("formData") String formData,
                @Param("amount") BigDecimal amount, @Param("days") BigDecimal days,
                @Param("orgId") Long orgId, @Param("orgPath") String orgPath);

    @Select("""
            SELECT name, schema_json::text AS schemaJson FROM oa_flow.form_template
             WHERE tenant_id=#{tenantId} AND code=#{code} AND status='PUBLISHED'
             ORDER BY version DESC LIMIT 1
            """)
    TemplateRow latestTemplate(@Param("tenantId") long tenantId, @Param("code") String code);

    @Select("SELECT driver, thresholds::text AS thresholds FROM oa_flow.approval_level_rule WHERE biz_type=#{bizType}")
    RuleRow levelRule(@Param("bizType") String bizType);

    @Select("""
            <script>
            SELECT id, biz_type AS bizType, doc_no AS docNo, title, summary,
                   form_data::text AS formData, amount, days, status, created_at AS createdAt
              FROM oa_flow.business_doc
             WHERE tenant_id=#{tenantId} AND applicant_id=#{applicantId}
             <if test="bizType != null and bizType != ''">AND biz_type=#{bizType}</if>
             ORDER BY id DESC LIMIT #{limit}
            </script>
            """)
    List<DocRow> selectOwn(@Param("tenantId") long tenantId, @Param("applicantId") String applicantId,
                           @Param("bizType") String bizType, @Param("limit") int limit);

    @Update("UPDATE oa_flow.business_doc SET status=#{status}, finished_at=now() WHERE tenant_id=#{tenantId} AND doc_no=#{docNo} AND status='PENDING'")
    int finish(@Param("tenantId") long tenantId, @Param("docNo") String docNo,
               @Param("status") String status);

    @Select("""
            SELECT t.code, t.name, t.version, t.schema_json::text AS schemaJson,
                   r.driver, r.description AS rule
              FROM oa_flow.form_template t
              LEFT JOIN oa_flow.approval_level_rule r ON r.biz_type=t.code
             WHERE t.tenant_id=#{tenantId} AND t.status='PUBLISHED' AND t.code&lt;&gt;'LEAVE'
             ORDER BY t.code
            """)
    List<TypeRow> supportedTypes(@Param("tenantId") long tenantId);
}
