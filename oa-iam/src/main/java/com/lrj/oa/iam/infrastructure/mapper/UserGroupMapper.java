package com.lrj.oa.iam.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lrj.oa.iam.domain.UserGroup;
import com.lrj.oa.iam.domain.UserGroupMember;
import org.apache.ibatis.annotations.*;

import java.time.OffsetDateTime;
import java.util.List;

@Mapper
public interface UserGroupMapper extends BaseMapper<UserGroup> {

    @Select("""
            <script>
            SELECT * FROM oa_iam.user_group
             WHERE tenant_id = #{tenantId}
               <if test="status != null">AND status = #{status}</if>
             ORDER BY id
            </script>
            """)
    List<UserGroup> selectGroups(@Param("tenantId") long tenantId, @Param("status") String status);

    @Select("SELECT * FROM oa_iam.user_group WHERE id=#{id} AND tenant_id=#{tenantId}")
    UserGroup selectTenantGroup(@Param("tenantId") long tenantId, @Param("id") long id);

    @Select("""
            SELECT g.id
              FROM oa_iam.user_group_member m
              JOIN oa_iam.user_group g ON g.id=m.group_id AND g.tenant_id=m.tenant_id
             WHERE m.tenant_id=#{tenantId} AND m.user_id=#{userId}
               AND m.revoked_at IS NULL AND g.status='ACTIVE'
               AND m.valid_from <= #{now} AND (m.valid_to IS NULL OR m.valid_to > #{now})
             ORDER BY g.id
            """)
    List<Long> selectActiveGroupIds(@Param("tenantId") long tenantId,
                                    @Param("userId") String userId,
                                    @Param("now") OffsetDateTime now);

    @Select("""
            SELECT m.* FROM oa_iam.user_group_member m
             WHERE m.tenant_id=#{tenantId} AND m.group_id=#{groupId} AND m.revoked_at IS NULL
             ORDER BY m.user_id
            """)
    List<UserGroupMember> selectMembers(@Param("tenantId") long tenantId, @Param("groupId") long groupId);

    @Select("""
            SELECT min(boundary) FROM (
                SELECT m.valid_from AS boundary
                  FROM oa_iam.user_group_member m JOIN oa_iam.user_group g ON g.id=m.group_id
                 WHERE m.tenant_id=#{tenantId} AND m.user_id=#{userId} AND m.revoked_at IS NULL
                   AND g.status='ACTIVE' AND m.valid_from > #{now}
                UNION ALL
                SELECT m.valid_to AS boundary
                  FROM oa_iam.user_group_member m JOIN oa_iam.user_group g ON g.id=m.group_id
                 WHERE m.tenant_id=#{tenantId} AND m.user_id=#{userId} AND m.revoked_at IS NULL
                   AND g.status='ACTIVE' AND m.valid_to > #{now}
            ) b
            """)
    OffsetDateTime nextMembershipBoundary(@Param("tenantId") long tenantId,
                                          @Param("userId") String userId,
                                          @Param("now") OffsetDateTime now);

    @Insert("""
            INSERT INTO oa_iam.user_group_member
                (tenant_id,group_id,user_id,valid_from,valid_to,created_by)
            VALUES (#{tenantId},#{groupId},#{userId},#{validFrom},#{validTo},#{by})
            ON CONFLICT (tenant_id,group_id,user_id) WHERE revoked_at IS NULL
            DO UPDATE SET valid_from=excluded.valid_from, valid_to=excluded.valid_to,
                          created_by=excluded.created_by, created_at=now()
            """)
    int upsertMember(@Param("tenantId") long tenantId, @Param("groupId") long groupId,
                     @Param("userId") String userId, @Param("validFrom") OffsetDateTime validFrom,
                     @Param("validTo") OffsetDateTime validTo, @Param("by") String by);

    @Update("""
            UPDATE oa_iam.user_group_member SET revoked_at=now(), revoked_by=#{by}
             WHERE tenant_id=#{tenantId} AND group_id=#{groupId} AND user_id=#{userId}
               AND revoked_at IS NULL
            """)
    int revokeMember(@Param("tenantId") long tenantId, @Param("groupId") long groupId,
                     @Param("userId") String userId, @Param("by") String by);

    @Update("""
            UPDATE oa_iam.user_group
               SET name=#{name}, description=#{description}, updated_by=#{by}, updated_at=now()
             WHERE tenant_id=#{tenantId} AND id=#{id}
            """)
    int updateGroup(@Param("tenantId") long tenantId, @Param("id") long id,
                    @Param("name") String name, @Param("description") String description,
                    @Param("by") String by);

    @Update("""
            UPDATE oa_iam.user_group
               SET status=#{status}, updated_by=#{by}, updated_at=now()
             WHERE tenant_id=#{tenantId} AND id=#{id} AND status<>#{status}
            """)
    int updateStatus(@Param("tenantId") long tenantId, @Param("id") long id,
                     @Param("status") String status, @Param("by") String by);
}
