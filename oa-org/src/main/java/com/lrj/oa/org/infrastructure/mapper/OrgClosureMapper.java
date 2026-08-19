package com.lrj.oa.org.infrastructure.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 闭包表维护 —— 组织域最容易写错的地方，SQL 全部收在这里，逐条注释语义。
 *
 * <p>不变式（{@code OrgConsistencyChecker} 会定期核对）：
 * <ol>
 *   <li>每个节点都有一行自反 {@code (id, id, 0)}；</li>
 *   <li>{@code closure} 里 (a, d) 存在 ⟺ d 的 path 以 a 的 path 为前缀；</li>
 *   <li>{@code distance} 等于两者 depth 之差。</li>
 * </ol>
 */
@Mapper
public interface OrgClosureMapper {

    /** 根节点：只插自反行。 */
    @Insert("INSERT INTO oa_org.org_closure(ancestor_id, descendant_id, distance) VALUES (#{id}, #{id}, 0)")
    int insertSelfRow(@Param("id") Long id);

    /**
     * 新增子节点：继承父的全部祖先（距离 +1），再补自反行。
     * 一条语句完成，无需先查父的祖先列表。
     */
    @Insert("""
            INSERT INTO oa_org.org_closure(ancestor_id, descendant_id, distance)
            SELECT ancestor_id, #{newId}, distance + 1
              FROM oa_org.org_closure
             WHERE descendant_id = #{parentId}
            UNION ALL
            SELECT #{newId}, #{newId}, 0
            """)
    int insertForNewChild(@Param("newId") Long newId, @Param("parentId") Long parentId);

    /**
     * 移动子树第一步：断开"子树内节点 × 子树外祖先"的全部连边。
     *
     * <p>子树内部的连边（含各自的自反行）必须保留 —— 子树内部结构不变。
     * PG 的语句级快照保证子查询读到的是删除前的状态，边删边读是安全的。
     */
    @Delete("""
            DELETE FROM oa_org.org_closure
             WHERE descendant_id IN (SELECT descendant_id FROM oa_org.org_closure WHERE ancestor_id = #{movingId})
               AND ancestor_id  NOT IN (SELECT descendant_id FROM oa_org.org_closure WHERE ancestor_id = #{movingId})
            """)
    int detachSubtree(@Param("movingId") Long movingId);

    /**
     * 移动子树第二步：把"新父的全部祖先(含自身)"与"子树全部节点"两两连边。
     * 距离 = 新父到其祖先的距离 + 子树内距离 + 1。
     */
    @Insert("""
            INSERT INTO oa_org.org_closure(ancestor_id, descendant_id, distance)
            SELECT sup.ancestor_id, sub.descendant_id, sup.distance + sub.distance + 1
              FROM oa_org.org_closure sup
              JOIN oa_org.org_closure sub ON TRUE
             WHERE sup.descendant_id = #{newParentId}
               AND sub.ancestor_id   = #{movingId}
            """)
    int attachSubtree(@Param("movingId") Long movingId, @Param("newParentId") Long newParentId);

    /** 子树全部节点 id（含自身）。 */
    @Select("SELECT descendant_id FROM oa_org.org_closure WHERE ancestor_id = #{orgId}")
    List<Long> subtreeIds(@Param("orgId") Long orgId);

    /** a 是否为 b 的祖先（含 a == b）。移动前的成环检查用它。 */
    @Select("SELECT count(*) > 0 FROM oa_org.org_closure WHERE ancestor_id = #{a} AND descendant_id = #{b}")
    boolean isAncestorOf(@Param("a") Long a, @Param("b") Long b);

    @Delete("DELETE FROM oa_org.org_closure WHERE ancestor_id = #{orgId} OR descendant_id = #{orgId}")
    int deleteAllFor(@Param("orgId") Long orgId);

    /**
     * 一致性核对：closure 与 path 是否还对得上。
     * 返回不一致的行数，正常应恒为 0；不为 0 说明某次组织调整没走完事务。
     */
    @Select("""
            SELECT count(*) FROM (
                SELECT c.ancestor_id, c.descendant_id
                  FROM oa_org.org_closure c
                  JOIN oa_org.org_unit a ON a.id = c.ancestor_id
                  JOIN oa_org.org_unit d ON d.id = c.descendant_id
                 WHERE position(a.path in d.path) <> 1
                    OR c.distance <> d.depth - a.depth
                UNION ALL
                -- 反向：path 上是祖先关系，closure 里却没有对应行
                SELECT a.id, d.id
                  FROM oa_org.org_unit a
                  JOIN oa_org.org_unit d ON position(a.path in d.path) = 1
                 WHERE NOT EXISTS (SELECT 1 FROM oa_org.org_closure c
                                    WHERE c.ancestor_id = a.id AND c.descendant_id = d.id)
            ) x
            """)
    long countInconsistencies();
}
