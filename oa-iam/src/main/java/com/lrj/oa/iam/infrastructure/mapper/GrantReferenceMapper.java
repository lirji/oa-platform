package com.lrj.oa.iam.infrastructure.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;

@Mapper
public interface GrantReferenceMapper {
    class ScopeTarget { public String userId; public Long orgId; public String orgPath; }

    @Select("""
            SELECT e.user_id AS userId, a.org_unit_id AS orgId, o.path AS orgPath
              FROM oa_org.employee e
              LEFT JOIN oa_org.employee_org_assignment a
                ON a.employee_id=e.id AND a.assignment_type='PRIMARY' AND a.valid_to IS NULL
              LEFT JOIN oa_org.org_unit o ON o.tenant_id=e.tenant_id AND o.id=a.org_unit_id
             WHERE e.tenant_id=#{tenantId} AND e.user_id=#{userId} AND e.status&lt;&gt;'LEFT' LIMIT 1
            """)
    ScopeTarget activeUser(@Param("tenantId") long tenantId, @Param("userId") String userId);

    @Select("SELECT NULL::text AS userId, id AS orgId, path AS orgPath FROM oa_org.org_unit WHERE tenant_id=#{tenantId} AND id=#{id} AND status='ACTIVE'")
    ScopeTarget activeOrg(@Param("tenantId") long tenantId, @Param("id") long id);

    @Select("""
            SELECT NULL::text AS userId, p.org_unit_id AS orgId, o.path AS orgPath
              FROM oa_org.position p JOIN oa_org.org_unit o ON o.tenant_id=p.tenant_id AND o.id=p.org_unit_id
             WHERE p.tenant_id=#{tenantId} AND p.id=#{id} AND p.status='ACTIVE'
            """)
    ScopeTarget activePosition(@Param("tenantId") long tenantId, @Param("id") long id);

    @Select("""
            <script>SELECT count(*) FROM oa_org.org_unit
             WHERE tenant_id=#{tenantId} AND status='ACTIVE' AND id IN
             <foreach item="id" collection="ids" open="(" separator="," close=")">#{id}</foreach></script>
            """)
    long countActiveOrgs(@Param("tenantId") long tenantId, @Param("ids") Collection<Long> ids);
}
