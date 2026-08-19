package com.lrj.oa.org.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 组织树内存快照的单元测试。
 *
 * <p>为什么值得单独测：这个类在权限热路径上被每个请求调用，而它的后代预计算一度写错 ——
 * 所有<b>有子节点</b>的节点都只算出自己，叶子节点却是对的，所以"看起来能跑"。
 * 这种 bug 只有针对性用例能抓住，冒烟脚本发现它已经太晚了。
 */
class OrgTreeSnapshotTest {

    /** 构造：1 根 → 2 子 → 每子 2 孙，共 7 节点。 */
    private static List<OrgUnit> sevenNodeTree() {
        return List.of(
                unit(1L, null, "/1/", 0),
                unit(2L, 1L, "/1/2/", 1),
                unit(3L, 1L, "/1/3/", 1),
                unit(4L, 2L, "/1/2/4/", 2),
                unit(5L, 2L, "/1/2/5/", 2),
                unit(6L, 3L, "/1/3/6/", 2),
                unit(7L, 3L, "/1/3/7/", 2));
    }

    @Test
    @DisplayName("后代必须含整棵子树，而不只是自己")
    void descendants_cover_whole_subtree() {
        OrgTreeSnapshot snap = OrgTreeSnapshot.build(1L, sevenNodeTree());

        assertThat(snap.descendantIds(1L))
                .as("根的后代应为全部 7 个节点（含自身）—— 只返回自己就是预计算写错了")
                .containsExactlyInAnyOrder(1L, 2L, 3L, 4L, 5L, 6L, 7L);
        assertThat(snap.descendantIds(2L)).containsExactlyInAnyOrder(2L, 4L, 5L);
        assertThat(snap.descendantIds(3L)).containsExactlyInAnyOrder(3L, 6L, 7L);
        assertThat(snap.descendantIds(4L)).as("叶子只有自己").containsExactly(4L);
    }

    @Test
    @DisplayName("祖先由近及远，含自身")
    void ancestors_are_ordered_from_self_upward() {
        OrgTreeSnapshot snap = OrgTreeSnapshot.build(1L, sevenNodeTree());
        assertThat(snap.ancestorIds(4L)).containsExactly(4L, 2L, 1L);
        assertThat(snap.ancestorIds(1L)).containsExactly(1L);
    }

    @Test
    @DisplayName("isAncestor 用 path 前缀判定，不能误判兄弟节点")
    void is_ancestor_uses_path_prefix() {
        OrgTreeSnapshot snap = OrgTreeSnapshot.build(1L, sevenNodeTree());
        assertThat(snap.isAncestor(1L, 4L)).isTrue();
        assertThat(snap.isAncestor(2L, 4L)).isTrue();
        assertThat(snap.isAncestor(3L, 4L)).as("兄弟分支不是祖先").isFalse();
        assertThat(snap.isAncestor(4L, 2L)).as("方向反了不成立").isFalse();
        assertThat(snap.isAncestor(2L, 2L)).as("自身算祖先").isTrue();
    }

    @Test
    @DisplayName("路径尾斜杠必须能挡住 /1/23/ 与 /1/234/ 的前缀误匹配")
    void trailing_slash_prevents_prefix_collision() {
        // 这是 path 设计里最容易被省掉、省掉就必出数据权限越权的一个字符
        OrgTreeSnapshot snap = OrgTreeSnapshot.build(1L, List.of(
                unit(1L, null, "/1/", 0),
                unit(23L, 1L, "/1/23/", 1),
                unit(234L, 1L, "/1/234/", 1)));
        assertThat(snap.isAncestor(23L, 234L))
                .as("/1/234/ 不能被判成 /1/23/ 的后代，否则跨部门数据会被看到")
                .isFalse();
    }

    @Test
    @DisplayName("最小路径前缀：被覆盖的后代要被吃掉")
    void minimal_prefixes_drop_covered_paths() {
        OrgTreeSnapshot snap = OrgTreeSnapshot.build(1L, sevenNodeTree());
        assertThat(snap.minimalPathPrefixes(List.of(2L, 4L, 5L)))
                .as("4、5 都在 2 的子树里，应只剩 2 的路径")
                .containsExactly("/1/2/");
        assertThat(snap.minimalPathPrefixes(List.of(2L, 3L)))
                .as("两个互不覆盖的分支要都保留")
                .containsExactlyInAnyOrder("/1/2/", "/1/3/");
        assertThat(snap.minimalPathPrefixes(List.of(999L))).as("不存在的组织直接忽略").isEmpty();
    }

    @Test
    @DisplayName("深树不爆栈：3000 节点单链")
    void deep_chain_does_not_blow_the_stack() {
        List<OrgUnit> chain = new ArrayList<>();
        StringBuilder path = new StringBuilder("/");
        for (long i = 1; i <= 3000; i++) {
            path.append(i).append('/');
            chain.add(unit(i, i == 1 ? null : i - 1, path.toString(), (int) i - 1));
        }
        OrgTreeSnapshot snap = OrgTreeSnapshot.build(1L, chain);
        assertThat(snap.descendantIds(1L)).hasSize(3000);
        assertThat(snap.ancestorIds(3000L)).as("祖先链走 64 层就停，防数据成环转死").hasSize(64);
    }

    @Test
    @DisplayName("数据里有环时不能死循环，其余节点仍要可用")
    void cyclic_data_does_not_hang() {
        // 2 -> 3 -> 2 互为父子（脏数据），另有一条正常分支
        OrgTreeSnapshot snap = OrgTreeSnapshot.build(1L, List.of(
                unit(1L, null, "/1/", 0),
                unit(2L, 3L, "/1/2/", 1),
                unit(3L, 2L, "/1/3/", 1),
                unit(4L, 1L, "/1/4/", 1)));
        assertThat(snap.descendantIds(1L)).as("正常分支不受脏数据影响").contains(1L, 4L);
        assertThat(snap.size()).isEqualTo(4);
    }

    private static OrgUnit unit(Long id, Long parentId, String path, int depth) {
        OrgUnit u = new OrgUnit();
        u.setId(id);
        u.setParentId(parentId);
        u.setCode("ORG-" + id);
        u.setName("组织" + id);
        u.setType(OrgUnitType.DEPT.name());
        u.setPath(path);
        u.setDepth(depth);
        u.setSortOrder(0);
        u.setStatus(OrgStatus.ACTIVE.name());
        return u;
    }
}
