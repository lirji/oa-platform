package com.lrj.oa.iam.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lrj.oa.iam.domain.Role;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

@Mapper
public interface RoleMapper extends BaseMapper<Role> {

    @Select("SELECT * FROM oa_iam.role WHERE tenant_id = #{tenantId} AND code = #{code}")
    Role selectByCode(@Param("tenantId") Long tenantId, @Param("code") String code);

    @Select("SELECT * FROM oa_iam.role WHERE tenant_id=#{tenantId} AND id=#{id}")
    Role selectTenantById(@Param("tenantId") long tenantId, @Param("id") long id);

    /**
     * 角色继承展开：给定被授予的角色，返回它<b>及其全部后代角色</b>所含的权限点。
     *
     * <p>方向别搞反：闭包里 (ancestor=SUPER_ADMIN, descendant=HR_ADMIN) 表示
     * "SUPER_ADMIN 继承 HR_ADMIN 的权限"，所以要按 ancestor 查、取 descendant 的权限。
     *
     * @return 每行 {@code granted_role_id -> permission_id}，用于把权限点归回到具体那条授权
     */
    @Select("""
            <script>
            SELECT ri.ancestor_role_id AS granted_role_id, rp.permission_id AS permission_id
              FROM oa_iam.role_inherit ri
              JOIN oa_iam.role_permission rp ON rp.role_id = ri.descendant_role_id
             WHERE ri.ancestor_role_id IN
             <foreach item="id" collection="roleIds" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    List<RolePermissionRow> selectPermissionsOfRoles(@Param("roleIds") Collection<Long> roleIds);

    @Select("""
            SELECT count(*) > 0
              FROM oa_iam.role r
              JOIN oa_iam.role_inherit ri ON ri.ancestor_role_id=r.id
              JOIN oa_iam.role_permission rp ON rp.role_id=ri.descendant_role_id
             WHERE r.tenant_id=#{tenantId} AND ri.ancestor_role_id=#{roleId}
               AND rp.permission_id=#{permissionId}
            """)
    boolean roleIncludesPermission(@Param("tenantId") long tenantId,
                                   @Param("roleId") long roleId,
                                   @Param("permissionId") long permissionId);
}
