package com.lrj.oa.iam.infrastructure.mapper;

import com.lrj.oa.iam.domain.Identity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;

@Mapper
public interface IdentityMapper {

    int insert(Identity row);

    Identity selectById(@Param("tenantId") long tenantId, @Param("id") String id);

    Identity selectByExternalKey(@Param("tenantId") long tenantId,
                                 @Param("identityType") String identityType,
                                 @Param("externalKey") String externalKey);

    Identity selectByEmployeeId(@Param("tenantId") long tenantId, @Param("employeeId") long employeeId);

    List<Identity> selectPage(@Param("tenantId") long tenantId,
                              @Param("identityType") String identityType,
                              @Param("status") String status,
                              @Param("q") String q,
                              @Param("cursor") Long cursor,
                              @Param("limit") int limit);

    int updateMutable(Identity row);

    int updateStatus(@Param("tenantId") long tenantId,
                     @Param("id") String id,
                     @Param("status") String status,
                     @Param("version") int version,
                     @Param("updatedAt") OffsetDateTime updatedAt);

    int updateProjection(@Param("tenantId") long tenantId,
                         @Param("id") String id,
                         @Param("displayName") String displayName,
                         @Param("status") String status,
                         @Param("orgId") Long orgId,
                         @Param("orgPath") String orgPath,
                         @Param("updatedAt") OffsetDateTime updatedAt);

    int deleteLabels(@Param("identityId") String identityId);

    int insertLabel(@Param("identityId") String identityId, @Param("label") String label);

    List<String> selectLabels(@Param("identityId") String identityId);

    List<IdentityLabelRow> selectLabelsByIds(@Param("ids") Collection<String> ids);

    List<Identity> selectByIds(@Param("tenantId") long tenantId, @Param("ids") Collection<String> ids);

    List<Identity> selectByExternalKeys(@Param("tenantId") long tenantId,
                                        @Param("identityType") String identityType,
                                        @Param("keys") Collection<String> keys);

    List<Identity> selectUsersByOrg(@Param("tenantId") long tenantId,
                                    @Param("orgId") Long orgId,
                                    @Param("orgPathPrefix") String orgPathPrefix,
                                    @Param("includeDescendants") boolean includeDescendants,
                                    @Param("limit") int limit);

    List<Identity> selectOwnedBy(@Param("tenantId") long tenantId,
                                 @Param("ownerIdentityId") String ownerIdentityId,
                                 @Param("limit") int limit);

    List<RoleGrantRow> selectActiveRoleGrants(@Param("tenantId") long tenantId,
                                              @Param("userId") String userId,
                                              @Param("orgId") Long orgId,
                                              @Param("now") OffsetDateTime now);

    int backfillUsersFromEmployees();

    int refreshUsersFromEmployees();

    int backfillUserLabels();

    record IdentityLabelRow(String identityId, String label) {}

    record RoleGrantRow(long roleId, String roleCode, String roleName, long grantId, String subjectType) {}
}
