package com.lrj.oa.doc.infrastructure.mapper;

import org.apache.ibatis.annotations.*;

import java.time.OffsetDateTime;
import java.util.List;

@Mapper
public interface OfficialDocMapper {

    class DocRow {
        public Long id;
        public String direction;
        public String docNumber;
        public String title;
        public String body;
        public String docType;
        public String urgency;
        public String secrecy;
        public String status;
        public String drafterId;
        public OffsetDateTime issuedAt;
        public OffsetDateTime archivedAt;
        public Long orgId;
    }

    @Select("""
            INSERT INTO oa_doc.official_doc
                (tenant_id, direction, title, body, doc_type, urgency, secrecy, source_org,
                 drafter_id, status, org_id, org_path)
            VALUES (#{tenantId}, #{direction}, #{title}, #{body}, #{docType}, #{urgency}, #{secrecy},
                    #{sourceOrg}, #{drafterId}, 'DRAFT', #{orgId}, #{orgPath})
            RETURNING id
            """)
    Long insertDraft(@Param("tenantId") long tenantId, @Param("direction") String direction,
                     @Param("title") String title, @Param("body") String body,
                     @Param("docType") String docType, @Param("urgency") String urgency,
                     @Param("secrecy") String secrecy, @Param("sourceOrg") String sourceOrg,
                     @Param("drafterId") String drafterId, @Param("orgId") Long orgId,
                     @Param("orgPath") String orgPath);

    @Select("SELECT d.status FROM oa_doc.official_doc d WHERE d.tenant_id=#{tenantId} AND d.id=#{id} FOR UPDATE")
    String selectStatusForUpdate(@Param("tenantId") long tenantId, @Param("id") long id);

    @Update("UPDATE oa_doc.official_doc d SET doc_number=#{number}, status='ISSUED', issued_at=now() WHERE d.tenant_id=#{tenantId} AND d.id=#{id}")
    int issue(@Param("tenantId") long tenantId, @Param("id") long id, @Param("number") String number);

    @Update("UPDATE oa_doc.official_doc d SET status='ARCHIVED', archived_at=now() WHERE d.tenant_id=#{tenantId} AND d.id=#{id} AND d.status='ISSUED'")
    int archive(@Param("tenantId") long tenantId, @Param("id") long id);

    @Select("""
            <script>
            SELECT d.id, d.direction, d.doc_number AS docNumber, d.title, d.body,
                   d.doc_type AS docType, d.urgency, d.secrecy, d.status,
                   d.drafter_id AS drafterId, d.issued_at AS issuedAt,
                   d.archived_at AS archivedAt, d.org_id AS orgId
              FROM oa_doc.official_doc d
             WHERE d.tenant_id=#{tenantId}
             <if test="keyword != null and keyword != ''">
               AND (d.title ILIKE concat('%', #{keyword}, '%')
                    OR coalesce(d.body,'') ILIKE concat('%', #{keyword}, '%'))
             </if>
             <if test="status != null and status != ''">AND d.status=#{status}</if>
             ORDER BY d.created_at DESC LIMIT #{limit}
            </script>
            """)
    List<DocRow> search(@Param("tenantId") long tenantId, @Param("keyword") String keyword,
                        @Param("status") String status, @Param("limit") int limit);

    @Insert("""
            INSERT INTO oa_doc.doc_flow_log(doc_id, action, actor_id, from_status, to_status, remark)
            VALUES (#{docId}, #{action}, #{actor}, #{from}, #{to}, #{remark})
            """)
    int insertFlow(@Param("docId") long docId, @Param("action") String action,
                   @Param("actor") String actor, @Param("from") String from,
                   @Param("to") String to, @Param("remark") String remark);
}
