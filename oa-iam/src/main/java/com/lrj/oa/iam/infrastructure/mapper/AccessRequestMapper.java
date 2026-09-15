package com.lrj.oa.iam.infrastructure.mapper;

import com.lrj.oa.iam.domain.AccessRequest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.List;

@Mapper
public interface AccessRequestMapper {

    int insert(AccessRequest row);

    AccessRequest selectById(@Param("tenantId") long tenantId, @Param("id") long id);

    AccessRequest selectByCommandId(@Param("tenantId") long tenantId, @Param("commandId") String commandId);

    AccessRequest selectPending(@Param("tenantId") long tenantId,
                                @Param("requesterUserId") String requesterUserId,
                                @Param("requestType") String requestType,
                                @Param("roleId") Long roleId);

    List<AccessRequest> selectPage(@Param("tenantId") long tenantId,
                                   @Param("requesterUserId") String requesterUserId,
                                   @Param("status") String status,
                                   @Param("cursor") Long cursor,
                                   @Param("limit") int limit);

    int approve(@Param("tenantId") long tenantId,
                @Param("id") long id,
                @Param("decidedBy") String decidedBy,
                @Param("decisionReason") String decisionReason,
                @Param("grantId") long grantId,
                @Param("approvalInstanceId") String approvalInstanceId,
                @Param("decidedAt") OffsetDateTime decidedAt);

    int reject(@Param("tenantId") long tenantId,
               @Param("id") long id,
               @Param("decidedBy") String decidedBy,
               @Param("decisionReason") String decisionReason,
               @Param("decidedAt") OffsetDateTime decidedAt);
}
