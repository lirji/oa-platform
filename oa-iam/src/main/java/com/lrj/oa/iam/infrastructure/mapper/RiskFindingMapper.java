package com.lrj.oa.iam.infrastructure.mapper;

import com.lrj.oa.iam.domain.Identity;
import com.lrj.oa.iam.domain.RiskFinding;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.List;

@Mapper
public interface RiskFindingMapper {

    List<Identity> selectStaleUnused(@Param("tenantId") long tenantId,
                                     @Param("since") OffsetDateTime since);

    List<Identity> selectLeaverResidual(@Param("tenantId") long tenantId,
                                        @Param("now") OffsetDateTime now);

    List<Identity> selectOrphanAgents(@Param("tenantId") long tenantId);

    int insertOpen(RiskFinding row);

    RiskFinding selectById(@Param("tenantId") long tenantId, @Param("id") long id);

    List<RiskFinding> selectPage(@Param("tenantId") long tenantId,
                                 @Param("status") String status,
                                 @Param("cursor") Long cursor,
                                 @Param("limit") int limit);

    int updateStatus(@Param("tenantId") long tenantId,
                     @Param("id") long id,
                     @Param("fromStatus") String fromStatus,
                     @Param("toStatus") String toStatus,
                     @Param("note") String note,
                     @Param("updatedBy") String updatedBy,
                     @Param("updatedAt") OffsetDateTime updatedAt);
}
