package com.lrj.oa.doc.infrastructure.mapper;

import org.apache.ibatis.annotations.*;

import java.time.OffsetDateTime;
import java.util.List;

@Mapper
public interface KnowledgeBaseMapper {
    class DocRow {
        public Long id;
        public Long folderId;
        public String title;
        public String summary;
        public String body;
        public String ownerId;
        public Integer version;
        public OffsetDateTime updatedAt;
    }

    @Select("""
            INSERT INTO oa_doc.kb_doc
                (tenant_id, folder_id, title, summary, body, file_key, owner_id)
            VALUES (#{tenantId}, #{folderId}, #{title}, #{summary}, #{body}, #{fileKey}, #{ownerId})
            RETURNING id
            """)
    Long insert(@Param("tenantId") long tenantId, @Param("folderId") Long folderId,
                @Param("title") String title, @Param("summary") String summary,
                @Param("body") String body, @Param("fileKey") String fileKey,
                @Param("ownerId") String ownerId);

    @Select("""
            SELECT id, folder_id AS folderId, title, summary, body, owner_id AS ownerId,
                   version, updated_at AS updatedAt
              FROM oa_doc.kb_doc WHERE tenant_id=#{tenantId} AND id=#{id}
            """)
    DocRow selectById(@Param("tenantId") long tenantId, @Param("id") long id);

    @Update("""
            UPDATE oa_doc.kb_doc
               SET title=#{title}, summary=#{summary}, body=#{body}, file_key=#{fileKey},
                   version=version+1, updated_at=now()
             WHERE tenant_id=#{tenantId} AND id=#{id}
            """)
    int update(@Param("tenantId") long tenantId, @Param("id") long id,
               @Param("title") String title, @Param("summary") String summary,
               @Param("body") String body, @Param("fileKey") String fileKey);

    @Select("SELECT path FROM oa_org.org_unit WHERE tenant_id=#{tenantId} AND id=#{id}")
    String orgPath(@Param("tenantId") long tenantId, @Param("id") long id);

    @Insert("""
            INSERT INTO oa_doc.kb_share
                (tenant_id, resource_type, resource_id, subject_type, subject_id,
                 subject_org_path, level, granted_by)
            VALUES (#{tenantId}, #{resourceType}, #{resourceId}, #{subjectType}, #{subjectId},
                    #{subjectOrgPath}, #{level}, #{grantedBy})
            ON CONFLICT (tenant_id, resource_type, resource_id, subject_type, subject_id)
            DO UPDATE SET level=EXCLUDED.level, granted_by=EXCLUDED.granted_by,
                          subject_org_path=EXCLUDED.subject_org_path
            """)
    int upsertShare(@Param("tenantId") long tenantId, @Param("resourceType") String resourceType,
                    @Param("resourceId") long resourceId, @Param("subjectType") String subjectType,
                    @Param("subjectId") String subjectId, @Param("subjectOrgPath") String subjectOrgPath,
                    @Param("level") String level, @Param("grantedBy") String grantedBy);

    @Select("""
            SELECT id, folder_id AS folderId, title, summary, owner_id AS ownerId,
                   version, updated_at AS updatedAt
              FROM oa_doc.kb_doc WHERE tenant_id=#{tenantId}
             ORDER BY updated_at DESC LIMIT #{limit}
            """)
    List<DocRow> recent(@Param("tenantId") long tenantId, @Param("limit") long limit);
}
