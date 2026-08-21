package com.lrj.oa.iam.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lrj.oa.iam.domain.PermissionCondition;
import org.apache.ibatis.annotations.*;

import java.util.Collection;
import java.util.List;

@Mapper
public interface PermissionConditionMapper extends BaseMapper<PermissionCondition> {

    @Select("""
            <script>
            SELECT * FROM oa_iam.permission_condition
             WHERE tenant_id=#{tenantId} AND deleted_at IS NULL
               <if test="roleId != null">AND role_id=#{roleId}</if>
               <if test="permissionId != null">AND permission_id=#{permissionId}</if>
             ORDER BY role_id, permission_id, id
            </script>
            """)
    List<PermissionCondition> selectPolicies(@Param("tenantId") long tenantId,
                                             @Param("roleId") Long roleId,
                                             @Param("permissionId") Long permissionId);

    @Select("""
            <script>
            SELECT * FROM oa_iam.permission_condition
             WHERE tenant_id=#{tenantId} AND deleted_at IS NULL AND enabled=true AND role_id IN
             <foreach item="id" collection="roleIds" open="(" separator="," close=")">#{id}</foreach>
             ORDER BY role_id, permission_id, id
            </script>
            """)
    List<PermissionCondition> selectEnabledForRoles(@Param("tenantId") long tenantId,
                                                    @Param("roleIds") Collection<Long> roleIds);

    @Select("""
            SELECT count(*) FROM oa_iam.permission_condition
             WHERE tenant_id=#{tenantId} AND role_id=#{roleId} AND permission_id=#{permissionId}
               AND deleted_at IS NULL
            """)
    int countLiveForTarget(@Param("tenantId") long tenantId,
                           @Param("roleId") long roleId,
                           @Param("permissionId") long permissionId);

    @Select("SELECT * FROM oa_iam.permission_condition WHERE tenant_id=#{tenantId} AND id=#{id} AND deleted_at IS NULL")
    PermissionCondition selectLiveById(@Param("tenantId") long tenantId, @Param("id") long id);

    @Update("""
            UPDATE oa_iam.permission_condition
               SET role_id=#{roleId}, permission_id=#{permissionId}, expression=#{expression},
                   description=#{description}, updated_by=#{by}, updated_at=now()
             WHERE tenant_id=#{tenantId} AND id=#{id} AND deleted_at IS NULL
            """)
    int updatePolicy(@Param("tenantId") long tenantId, @Param("id") long id, @Param("roleId") long roleId,
                     @Param("permissionId") long permissionId, @Param("expression") String expression,
                     @Param("description") String description, @Param("by") String by);

    @Update("""
            UPDATE oa_iam.permission_condition SET enabled=#{enabled}, updated_by=#{by}, updated_at=now()
             WHERE tenant_id=#{tenantId} AND id=#{id} AND deleted_at IS NULL AND enabled<>#{enabled}
            """)
    int setEnabled(@Param("tenantId") long tenantId, @Param("id") long id,
                   @Param("enabled") boolean enabled, @Param("by") String by);

    @Update("""
            UPDATE oa_iam.permission_condition SET deleted_at=now(), enabled=false, updated_by=#{by}, updated_at=now()
             WHERE tenant_id=#{tenantId} AND id=#{id} AND deleted_at IS NULL
            """)
    int softDelete(@Param("tenantId") long tenantId, @Param("id") long id, @Param("by") String by);
}
