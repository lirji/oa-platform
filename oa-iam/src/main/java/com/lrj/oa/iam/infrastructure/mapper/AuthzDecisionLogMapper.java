package com.lrj.oa.iam.infrastructure.mapper;

import com.lrj.oa.iam.domain.AuthzDecisionLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AuthzDecisionLogMapper {

    int insert(AuthzDecisionLog row);

    List<AuthzDecisionLog> selectPage(@Param("tenantId") long tenantId,
                                      @Param("principalId") String principalId,
                                      @Param("decision") String decision,
                                      @Param("cursor") Long cursor,
                                      @Param("limit") int limit);
}
