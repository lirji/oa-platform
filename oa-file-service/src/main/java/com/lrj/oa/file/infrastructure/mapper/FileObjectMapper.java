package com.lrj.oa.file.infrastructure.mapper;

import org.apache.ibatis.annotations.*;

import java.time.OffsetDateTime;
import java.util.List;

@Mapper
public interface FileObjectMapper {
    class FileRow {
        public Long id;
        public String objectKey;
        public String bucket;
        public String fileName;
        public String contentType;
        public Long sizeBytes;
        public String sha256;
        public String bizType;
        public String bizId;
        public String ownerId;
        public Long orgId;
        public String orgPath;
        public OffsetDateTime createdAt;
    }

    @Select("""
            INSERT INTO oa_sys.file_object
                (tenant_id, object_key, bucket, file_name, content_type, size_bytes, sha256,
                 biz_type, biz_id, owner_id, org_id, org_path)
            VALUES (#{tenantId}, #{objectKey}, #{bucket}, #{fileName}, #{contentType}, #{sizeBytes},
                    #{sha256}, #{bizType}, #{bizId}, #{ownerId}, #{orgId}, #{orgPath}) RETURNING id
            """)
    Long insert(@Param("tenantId") long tenantId, @Param("objectKey") String objectKey,
                @Param("bucket") String bucket, @Param("fileName") String fileName,
                @Param("contentType") String contentType, @Param("sizeBytes") long sizeBytes,
                @Param("sha256") String sha256, @Param("bizType") String bizType,
                @Param("bizId") String bizId, @Param("ownerId") String ownerId,
                @Param("orgId") Long orgId, @Param("orgPath") String orgPath);

    @Select("""
            SELECT id, object_key AS objectKey, bucket, file_name AS fileName,
                   content_type AS contentType, size_bytes AS sizeBytes, sha256,
                   biz_type AS bizType, biz_id AS bizId, owner_id AS ownerId,
                   org_id AS orgId, org_path AS orgPath, created_at AS createdAt
              FROM oa_sys.file_object WHERE tenant_id=#{tenantId} AND id=#{id}
            """)
    FileRow selectById(@Param("tenantId") long tenantId, @Param("id") long id);

    @Delete("DELETE FROM oa_sys.file_object WHERE tenant_id=#{tenantId} AND id=#{id} AND owner_id=#{ownerId}")
    int deleteOwn(@Param("tenantId") long tenantId, @Param("id") long id,
                  @Param("ownerId") String ownerId);

    @Select("""
            SELECT id, file_name AS fileName, content_type AS contentType, size_bytes AS sizeBytes,
                   sha256, biz_type AS bizType, biz_id AS bizId, owner_id AS ownerId,
                   created_at AS createdAt
              FROM oa_sys.file_object
             WHERE tenant_id=#{tenantId} AND owner_id=#{ownerId}
             ORDER BY id DESC LIMIT #{limit}
            """)
    List<FileRow> selectOwn(@Param("tenantId") long tenantId, @Param("ownerId") String ownerId,
                            @Param("limit") int limit);
}
