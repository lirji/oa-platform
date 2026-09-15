package com.lrj.oa.iam.infrastructure.mapper;

import com.lrj.oa.iam.domain.Credential;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.List;

@Mapper
public interface CredentialMapper {

    int insert(Credential row);

    Credential selectById(@Param("tenantId") long tenantId, @Param("id") long id);

    List<Credential> selectByIdentity(@Param("tenantId") long tenantId,
                                      @Param("identityId") String identityId);

    int markInactive(@Param("tenantId") long tenantId,
                     @Param("id") long id,
                     @Param("status") String status,
                     @Param("revokedBy") String revokedBy,
                     @Param("revokedAt") OffsetDateTime revokedAt);

    int revokeActiveOfIdentity(@Param("tenantId") long tenantId,
                               @Param("identityId") String identityId,
                               @Param("revokedBy") String revokedBy,
                               @Param("revokedAt") OffsetDateTime revokedAt);
}
