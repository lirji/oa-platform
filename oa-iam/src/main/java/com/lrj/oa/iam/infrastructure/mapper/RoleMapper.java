package com.lrj.oa.iam.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lrj.oa.iam.domain.Role;
import com.lrj.oa.iam.api.dto.RoleAdminDtos.RoleSummary;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Collection;
import java.util.List;

@Mapper
public interface RoleMapper extends BaseMapper<Role> {

    @Select("SELECT * FROM oa_iam.role WHERE tenant_id = #{tenantId} AND code = #{code}")
    Role selectByCode(@Param("tenantId") Long tenantId, @Param("code") String code);

    @Select("SELECT * FROM oa_iam.role WHERE tenant_id=#{tenantId} AND id=#{id}")
    Role selectTenantById(@Param("tenantId") long tenantId, @Param("id") long id);

    @Select("""
            <script>
            SELECT * FROM oa_iam.role
             WHERE tenant_id=#{tenantId}
               <if test="status != null and status != ''">AND status=#{status}</if>
               <if test="keyword != null and keyword != ''">
                 AND (upper(code) LIKE concat('%', upper(#{keyword}), '%')
                      OR upper(name) LIKE concat('%', upper(#{keyword}), '%'))
               </if>
             ORDER BY builtin DESC, id
            </script>
            """)
    List<Role> selectAdminRoles(@Param("tenantId") long tenantId,
                                @Param("status") String status,
                                @Param("keyword") String keyword);

    @Select("""
            <script>
            SELECT r.id, r.code, r.name, r.type, r.default_scope, r.status, r.builtin, r.version, r.remark,
                   (SELECT count(*) FROM oa_iam.role_permission rp
                     JOIN oa_iam.permission p ON p.id=rp.permission_id AND p.status='ACTIVE'
                    WHERE rp.role_id=r.id) AS direct_permission_count,
                   (SELECT count(DISTINCT rp.permission_id) FROM oa_iam.role_inherit ri
                     JOIN oa_iam.role_permission rp ON rp.role_id=ri.descendant_role_id
                     JOIN oa_iam.permission p ON p.id=rp.permission_id AND p.status='ACTIVE'
                    WHERE ri.ancestor_role_id=r.id) AS effective_permission_count,
                   (SELECT count(*) FROM oa_iam.role_inherit_edge edge
                    WHERE edge.tenant_id=#{tenantId} AND edge.parent_role_id=r.id) AS inherited_role_count,
                   (SELECT count(*) FROM oa_iam.grant_record g
                    WHERE g.tenant_id=#{tenantId} AND g.role_id=r.id) AS grant_count
              FROM oa_iam.role r
             WHERE r.tenant_id=#{tenantId}
               <choose>
                 <when test="status != null and status != ''">AND r.status=#{status}</when>
                 <otherwise>AND r.status&lt;&gt;'DELETED'</otherwise>
               </choose>
               <if test="keyword != null and keyword != ''">
                 AND (upper(r.code) LIKE concat('%', upper(#{keyword}), '%')
                      OR upper(r.name) LIKE concat('%', upper(#{keyword}), '%'))
               </if>
             ORDER BY r.builtin DESC, r.id
            </script>
            """)
    List<RoleSummary> selectAdminRoleSummaries(@Param("tenantId") long tenantId,
                                               @Param("status") String status,
                                               @Param("keyword") String keyword);

    @Update("""
            UPDATE oa_iam.role
               SET name=#{name}, default_scope=#{defaultScope}, remark=#{remark},
                   version=version+1, updated_at=now()
             WHERE tenant_id=#{tenantId} AND id=#{id} AND version=#{version} AND status!='DELETED'
            """)
    int updateRole(@Param("tenantId") long tenantId, @Param("id") long id,
                   @Param("name") String name, @Param("defaultScope") String defaultScope,
                   @Param("remark") String remark, @Param("version") int version);

    @Update("""
            UPDATE oa_iam.role SET status=#{status}, version=version+1, updated_at=now()
             WHERE tenant_id=#{tenantId} AND id=#{id} AND version=#{version} AND status!='DELETED'
            """)
    int updateStatus(@Param("tenantId") long tenantId, @Param("id") long id,
                     @Param("status") String status, @Param("version") int version);

    @Update("""
            UPDATE oa_iam.role SET version=version+1, updated_at=now()
             WHERE tenant_id=#{tenantId} AND id=#{id} AND version=#{version} AND status!='DELETED'
            """)
    int touchVersion(@Param("tenantId") long tenantId, @Param("id") long id,
                     @Param("version") int version);

    @Update("""
            UPDATE oa_iam.role SET status='DELETED', version=version+1, updated_at=now()
             WHERE tenant_id=#{tenantId} AND id=#{id} AND version=#{version}
               AND builtin=false AND status!='DELETED'
            """)
    int softDelete(@Param("tenantId") long tenantId, @Param("id") long id,
                   @Param("version") int version);

    @Select("SELECT count(*) FROM oa_iam.grant_record WHERE tenant_id=#{tenantId} AND role_id=#{roleId}")
    long countGrantReferences(@Param("tenantId") long tenantId, @Param("roleId") long roleId);

    @Select("SELECT permission_id FROM oa_iam.role_permission WHERE role_id=#{roleId} ORDER BY permission_id")
    List<Long> selectDirectPermissionIds(@Param("roleId") long roleId);

    @Select("""
            SELECT DISTINCT rp.permission_id
              FROM oa_iam.role_inherit ri
              JOIN oa_iam.role_permission rp ON rp.role_id=ri.descendant_role_id
              JOIN oa_iam.permission p ON p.id=rp.permission_id AND p.status='ACTIVE'
             WHERE ri.ancestor_role_id=#{roleId}
             ORDER BY rp.permission_id
            """)
    List<Long> selectEffectivePermissionIds(@Param("roleId") long roleId);

    @Select("""
            SELECT inherited_role_id FROM oa_iam.role_inherit_edge
             WHERE tenant_id=#{tenantId} AND parent_role_id=#{roleId}
             ORDER BY inherited_role_id
            """)
    List<Long> selectInheritedRoleIds(@Param("tenantId") long tenantId, @Param("roleId") long roleId);

    @Select("""
            <script>
            SELECT count(*) FROM oa_iam.role
             WHERE tenant_id=#{tenantId} AND status&lt;&gt;'DELETED' AND id IN
             <foreach item="id" collection="ids" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    long countTenantRoles(@Param("tenantId") long tenantId, @Param("ids") Collection<Long> ids);

    @Select("""
            <script>
            SELECT count(*) FROM oa_iam.permission WHERE status='ACTIVE' AND id IN
             <foreach item="id" collection="ids" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    long countActivePermissions(@Param("ids") Collection<Long> ids);

    @Delete("DELETE FROM oa_iam.role_permission WHERE role_id=#{roleId}")
    int deleteDirectPermissions(@Param("roleId") long roleId);

    @Insert("""
            <script>
            INSERT INTO oa_iam.role_permission(role_id, permission_id) VALUES
            <foreach item="id" collection="permissionIds" separator=",">(#{roleId}, #{id})</foreach>
            </script>
            """)
    int insertDirectPermissions(@Param("roleId") long roleId,
                                @Param("permissionIds") Collection<Long> permissionIds);

    @Delete("""
            DELETE FROM oa_iam.role_inherit_edge
             WHERE tenant_id=#{tenantId} AND parent_role_id=#{roleId}
            """)
    int deleteInheritanceEdges(@Param("tenantId") long tenantId, @Param("roleId") long roleId);

    @Delete("""
            DELETE FROM oa_iam.role_inherit_edge
             WHERE tenant_id=#{tenantId} AND inherited_role_id=#{roleId}
            """)
    int deleteIncomingInheritanceEdges(@Param("tenantId") long tenantId, @Param("roleId") long roleId);

    @Insert("""
            <script>
            INSERT INTO oa_iam.role_inherit_edge(tenant_id, parent_role_id, inherited_role_id) VALUES
            <foreach item="id" collection="inheritedRoleIds" separator=",">
              (#{tenantId}, #{roleId}, #{id})
            </foreach>
            </script>
            """)
    int insertInheritanceEdges(@Param("tenantId") long tenantId, @Param("roleId") long roleId,
                               @Param("inheritedRoleIds") Collection<Long> inheritedRoleIds);

    @Select("""
            SELECT 1 FROM (
              SELECT pg_advisory_xact_lock(hashtextextended('oa-role:' || CAST(#{tenantId} AS text), 0))
            ) locked
            """)
    int lockTenantRoleGraph(@Param("tenantId") long tenantId);

    @Select("""
            WITH RECURSIVE reach(ancestor, descendant) AS (
              SELECT parent_role_id, inherited_role_id
                FROM oa_iam.role_inherit_edge WHERE tenant_id=#{tenantId}
              UNION
              SELECT reach.ancestor, edge.inherited_role_id
                FROM reach
                JOIN oa_iam.role_inherit_edge edge
                  ON edge.tenant_id=#{tenantId} AND edge.parent_role_id=reach.descendant
            )
            SELECT count(*) FROM reach WHERE ancestor=descendant
            """)
    long countInheritanceCycles(@Param("tenantId") long tenantId);

    @Delete("""
            DELETE FROM oa_iam.role_inherit ri
             USING oa_iam.role r
             WHERE r.tenant_id=#{tenantId} AND ri.ancestor_role_id=r.id
            """)
    int deleteTenantClosure(@Param("tenantId") long tenantId);

    @Insert("""
            INSERT INTO oa_iam.role_inherit(ancestor_role_id, descendant_role_id, distance)
            SELECT id, id, 0 FROM oa_iam.role
             WHERE tenant_id=#{tenantId} AND status='ACTIVE'
            """)
    int insertTenantSelfClosure(@Param("tenantId") long tenantId);

    @Insert("""
            WITH RECURSIVE walk(ancestor, descendant, distance, path) AS (
              SELECT edge.parent_role_id, edge.inherited_role_id, 1,
                     ARRAY[edge.parent_role_id, edge.inherited_role_id]::bigint[]
                FROM oa_iam.role_inherit_edge edge
                JOIN oa_iam.role parent ON parent.id=edge.parent_role_id AND parent.status='ACTIVE'
                JOIN oa_iam.role inherited ON inherited.id=edge.inherited_role_id AND inherited.status='ACTIVE'
               WHERE edge.tenant_id=#{tenantId}
              UNION ALL
              SELECT walk.ancestor, edge.inherited_role_id, walk.distance+1,
                     walk.path || edge.inherited_role_id
                FROM walk
                JOIN oa_iam.role_inherit_edge edge
                  ON edge.tenant_id=#{tenantId} AND edge.parent_role_id=walk.descendant
                JOIN oa_iam.role inherited ON inherited.id=edge.inherited_role_id AND inherited.status='ACTIVE'
               WHERE NOT edge.inherited_role_id=ANY(walk.path)
            ), shortest AS (
              SELECT ancestor, descendant, min(distance) AS distance
                FROM walk GROUP BY ancestor, descendant
            )
            INSERT INTO oa_iam.role_inherit(ancestor_role_id, descendant_role_id, distance)
            SELECT ancestor, descendant, distance FROM shortest
            """)
    int insertTenantTransitiveClosure(@Param("tenantId") long tenantId);

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
              JOIN oa_iam.role root_role ON root_role.id=ri.ancestor_role_id AND root_role.status='ACTIVE'
              JOIN oa_iam.role inherited_role ON inherited_role.id=ri.descendant_role_id AND inherited_role.status='ACTIVE'
              JOIN oa_iam.role_permission rp ON rp.role_id = ri.descendant_role_id
              JOIN oa_iam.permission p ON p.id=rp.permission_id AND p.status='ACTIVE'
             WHERE ri.ancestor_role_id IN
             <foreach item="id" collection="roleIds" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    List<RolePermissionRow> selectPermissionsOfRoles(@Param("roleIds") Collection<Long> roleIds);

    @Select("""
            SELECT count(*) > 0
              FROM oa_iam.role r
              JOIN oa_iam.role_inherit ri ON ri.ancestor_role_id=r.id
              JOIN oa_iam.role inherited_role ON inherited_role.id=ri.descendant_role_id AND inherited_role.status='ACTIVE'
              JOIN oa_iam.role_permission rp ON rp.role_id=ri.descendant_role_id
              JOIN oa_iam.permission p ON p.id=rp.permission_id AND p.status='ACTIVE'
             WHERE r.tenant_id=#{tenantId} AND ri.ancestor_role_id=#{roleId}
               AND r.status='ACTIVE'
               AND rp.permission_id=#{permissionId}
            """)
    boolean roleIncludesPermission(@Param("tenantId") long tenantId,
                                   @Param("roleId") long roleId,
                                   @Param("permissionId") long permissionId);

    /** 自定义角色只要直接或继承获得权限治理能力，也按高权角色保护。 */
    @Select("""
            SELECT count(*) > 0
              FROM oa_iam.role r
              JOIN oa_iam.role_inherit ri ON ri.ancestor_role_id=r.id
              JOIN oa_iam.role_permission rp ON rp.role_id=ri.descendant_role_id
              JOIN oa_iam.permission p ON p.id=rp.permission_id AND p.status='ACTIVE'
             WHERE r.tenant_id=#{tenantId} AND r.id=#{roleId} AND r.status='ACTIVE'
               AND p.code IN ('oa:iam:admin','oa:iam:grant','oa:iam:revoke','oa:iam:elevation:approve')
            """)
    boolean roleIncludesHighPrivilege(@Param("tenantId") long tenantId, @Param("roleId") long roleId);
}
