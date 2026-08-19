package com.lrj.oa.org.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lrj.oa.org.domain.OrgUnit;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface OrgUnitMapper extends BaseMapper<OrgUnit> {

    /** 构建内存树用：一次拉全量。万人级约 3,000 行，几 MB，直接全取。 */
    @Select("SELECT * FROM oa_org.org_unit WHERE tenant_id = #{tenantId} ORDER BY depth, sort_order, id")
    List<OrgUnit> selectAllForTree(@Param("tenantId") Long tenantId);

    /**
     * 移动子树第三步：整棵子树的 path 换前缀、depth 平移。
     *
     * <p>用 {@code substr(path, len+1)} 而不是标准的 {@code substring(path from len+1)} ——
     * 后者的 FROM 关键字语法在 SQL 解析器（MyBatis-Plus 的防全表拦截器用 JSqlParser）里兼容性差。
     */
    @Update("""
            UPDATE oa_org.org_unit
               SET path = #{newPrefix} || substr(path, #{oldPrefixLen} + 1),
                   depth = depth + #{depthDelta},
                   updated_at = now()
             WHERE path LIKE #{oldPrefix} || '%'
            """)
    int shiftSubtreePath(@Param("oldPrefix") String oldPrefix,
                         @Param("oldPrefixLen") int oldPrefixLen,
                         @Param("newPrefix") String newPrefix,
                         @Param("depthDelta") int depthDelta);

    /** 新建节点后回填自身 path（需要先拿到自增 id 才能拼）。 */
    @Update("UPDATE oa_org.org_unit SET path = #{path}, depth = #{depth} WHERE id = #{id}")
    int fillPath(@Param("id") Long id, @Param("path") String path, @Param("depth") int depth);

    @Select("SELECT count(*) FROM oa_org.org_unit WHERE parent_id = #{orgId} AND status <> 'DISSOLVED'")
    int countActiveChildren(@Param("orgId") Long orgId);

    @Select("""
            SELECT count(*) FROM oa_org.employee_org_assignment a
              JOIN oa_org.employee e ON e.id = a.employee_id
             WHERE a.org_unit_id = #{orgId} AND a.valid_to IS NULL AND e.status <> 'LEFT'
            """)
    int countActiveMembers(@Param("orgId") Long orgId);

    // ── 组织树版本：跨节点缓存收敛 ────────────────────────────
    @Select("SELECT version FROM oa_org.org_tree_version WHERE id = 1")
    long currentTreeVersion();

    @Update("UPDATE oa_org.org_tree_version SET version = version + 1, updated_at = now() WHERE id = 1")
    int bumpTreeVersion();
}
