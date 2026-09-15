package com.lrj.oa.iam.infrastructure.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.List;

/** 权限反查：按 permission_id 反查仍有效的 grant_record，不扫全员引擎。 */
@Mapper
public interface WhoHasAccessMapper {

    List<GrantHit> selectActiveGrantsForPerm(@Param("tenantId") long tenantId,
                                             @Param("permId") int permId,
                                             @Param("now") OffsetDateTime now);

    record GrantHit(long grantId,
                    String subjectType,
                    String subjectId,
                    long roleId,
                    String roleCode,
                    String roleName,
                    int roleDistance,
                    String grantType,
                    Boolean includeDescendants) {}
}
