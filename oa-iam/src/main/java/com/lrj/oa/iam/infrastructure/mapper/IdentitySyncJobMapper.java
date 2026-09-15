package com.lrj.oa.iam.infrastructure.mapper;

import com.lrj.oa.iam.domain.IdentitySyncJob;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface IdentitySyncJobMapper {

    int insert(IdentitySyncJob row);

    IdentitySyncJob selectById(@Param("tenantId") long tenantId, @Param("id") long id);

    IdentitySyncJob selectByCommandId(@Param("tenantId") long tenantId, @Param("commandId") String commandId);

    int finish(IdentitySyncJob row);
}
