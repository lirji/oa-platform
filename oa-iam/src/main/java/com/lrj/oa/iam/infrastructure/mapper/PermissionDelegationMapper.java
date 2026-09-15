package com.lrj.oa.iam.infrastructure.mapper;

import com.lrj.oa.iam.domain.PermissionDelegation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.List;

@Mapper
public interface PermissionDelegationMapper {

    int insert(PermissionDelegation row);

    PermissionDelegation selectById(@Param("tenantId") long tenantId, @Param("id") long id);

    List<PermissionDelegation> selectPage(@Param("tenantId") long tenantId,
                                          @Param("identityId") String identityId,
                                          @Param("cursor") Long cursor,
                                          @Param("limit") int limit);

    List<PermissionDelegation> selectActiveForDelegatee(@Param("tenantId") long tenantId,
                                                        @Param("delegateeIdentityId") String delegateeIdentityId,
                                                        @Param("now") OffsetDateTime now);

    List<PermissionDelegation> selectActiveCoveringPerm(@Param("tenantId") long tenantId,
                                                        @Param("permCode") String permCode,
                                                        @Param("now") OffsetDateTime now);

    int revoke(@Param("tenantId") long tenantId,
               @Param("id") long id,
               @Param("revokedBy") String revokedBy,
               @Param("revokedAt") OffsetDateTime revokedAt);
}
