package com.lrj.oa.org.domain;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * 组织树的<b>不可变</b>内存快照。整体替换（COW），读侧无锁。
 *
 * <p>这是全系统最大的性能杠杆：把"求某人可见的全部子部门"从一次递归 SQL 变成内存查表，
 * 消灭约 90% 的权限相关数据库查询。万人级组织约 3,000 节点，含预计算的祖先/后代列表也只有几 MB。
 *
 * <p>后代与祖先列表在构建期<b>预计算</b>，因此查询是真正的 O(1)（返回已存在的不可变 List），
 * 而不是每次现算一遍 DFS。代价是构建期多一趟遍历、内存多约 30,000 个 Long 引用 —— 完全划算。
 */
public final class OrgTreeSnapshot {

    private static final Logger log = LoggerFactory.getLogger(OrgTreeSnapshot.class);

    /** 树节点。childIds 已按 sortOrder 排好序。 */
    public record Node(
            Long id, Long parentId, String code, String name, String shortName,
            OrgUnitType type, String path, int depth, int sortOrder,
            String leaderUserId, String deputyLeaderUserId, OrgStatus status,
            List<Long> childIds
    ) {}

    private final long version;
    private final Map<Long, Node> byId;
    private final Map<String, Long> byCode;
    private final List<Long> rootIds;
    private final Map<Long, List<Long>> descendants;   // 含自身
    private final Map<Long, List<Long>> ancestors;     // 含自身，由近及远

    private OrgTreeSnapshot(long version, Map<Long, Node> byId, Map<String, Long> byCode,
                            List<Long> rootIds, Map<Long, List<Long>> descendants,
                            Map<Long, List<Long>> ancestors) {
        this.version = version;
        this.byId = byId;
        this.byCode = byCode;
        this.rootIds = rootIds;
        this.descendants = descendants;
        this.ancestors = ancestors;
    }

    public static OrgTreeSnapshot empty() {
        return new OrgTreeSnapshot(-1L, Map.of(), Map.of(), List.of(), Map.of(), Map.of());
    }

    // ───────────────────────────────────────────── 构建

    public static OrgTreeSnapshot build(long version, List<OrgUnit> units) {
        Map<Long, OrgUnit> raw = new HashMap<>(units.size() * 2);
        for (OrgUnit u : units) raw.put(u.getId(), u);

        // 1) 收集子节点
        Map<Long, List<Long>> children = new HashMap<>();
        List<Long> roots = new ArrayList<>();
        for (OrgUnit u : units) {
            Long p = u.getParentId();
            if (p == null) {
                roots.add(u.getId());
            } else if (!raw.containsKey(p)) {
                // 父节点缺失（被物理删除或跨租户）——当作根处理，但要吼出来
                log.warn("组织 {}({}) 的父节点 {} 不存在，按根节点处理", u.getId(), u.getCode(), p);
                roots.add(u.getId());
            } else {
                children.computeIfAbsent(p, k -> new ArrayList<>()).add(u.getId());
            }
        }
        Comparator<Long> byOrder = Comparator
                .comparingInt((Long id) -> raw.get(id).getSortOrder() == null ? 0 : raw.get(id).getSortOrder())
                .thenComparingLong(id -> id);
        children.values().forEach(l -> l.sort(byOrder));
        roots.sort(byOrder);

        // 2) 冻结节点
        Map<Long, Node> byId = HashMap.newHashMap(units.size());
        Map<String, Long> byCode = HashMap.newHashMap(units.size());
        for (OrgUnit u : units) {
            List<Long> kids = List.copyOf(children.getOrDefault(u.getId(), List.of()));
            byId.put(u.getId(), new Node(
                    u.getId(), raw.containsKey(u.getParentId()) ? u.getParentId() : null,
                    u.getCode(), u.getName(), u.getShortName(),
                    u.typeEnum(), u.getPath(), u.getDepth() == null ? 0 : u.getDepth(),
                    u.getSortOrder() == null ? 0 : u.getSortOrder(),
                    u.getLeaderUserId(), u.getDeputyLeaderUserId(), u.statusEnum(), kids));
            byCode.put(u.getCode(), u.getId());
        }

        // 3) 预计算后代（含自身）。迭代式后序遍历，避免深树递归爆栈，并带环检测。
        Map<Long, List<Long>> descendants = HashMap.newHashMap(byId.size());
        for (Long root : roots) collectDescendants(root, byId, descendants);
        // 兜底：环里的节点走不到，给它们至少一份自反列表，避免后续 NPE
        for (Long id : byId.keySet()) descendants.computeIfAbsent(id, List::of);

        // 4) 预计算祖先（含自身，由近及远）
        Map<Long, List<Long>> ancestors = HashMap.newHashMap(byId.size());
        for (Long id : byId.keySet()) {
            List<Long> chain = new ArrayList<>(8);
            Long cur = id;
            int guard = 0;
            while (cur != null && guard++ < 64) {
                chain.add(cur);
                Node n = byId.get(cur);
                cur = n == null ? null : n.parentId();
            }
            ancestors.put(id, List.copyOf(chain));
        }

        return new OrgTreeSnapshot(version, Map.copyOf(byId), Map.copyOf(byCode),
                List.copyOf(roots), Map.copyOf(descendants), Map.copyOf(ancestors));
    }

    /**
     * 迭代式后序遍历，把每个节点的后代列表算出来。
     *
     * <p>每个节点会被 peek 两次：第一次把未解析的子节点压栈，第二次（子节点都已在 {@code out} 里）
     * 才拼出自己的结果。{@code expanded} 记录"已经展开过子节点"，
     * <b>它同时就是环检测</b>：若某个子节点已 expanded 却还没有结果，说明它正在当前这条路径上。
     */
    private static void collectDescendants(Long root, Map<Long, Node> byId, Map<Long, List<Long>> out) {
        Deque<Long> stack = new ArrayDeque<>();
        Set<Long> expanded = new HashSet<>();
        stack.push(root);

        while (!stack.isEmpty()) {
            Long id = stack.peek();
            if (out.containsKey(id)) { stack.pop(); continue; }      // 已算完（被重复压栈）
            Node node = byId.get(id);
            if (node == null) { stack.pop(); continue; }

            if (expanded.add(id)) {
                boolean pushedAny = false;
                for (Long c : node.childIds()) {
                    if (out.containsKey(c)) continue;                // 子树已算完
                    if (expanded.contains(c)) {                      // 子节点仍在当前路径上 ⇒ 环
                        log.error("组织树存在环：{} -> {}，已断开该边；请跑 /org/units/consistency", id, c);
                        continue;
                    }
                    stack.push(c);
                    pushedAny = true;
                }
                if (pushedAny) continue;                             // 先解析子树，下一轮再回到自己
            }

            // 到这里说明子节点全部有结果了，拼出自己的后代列表
            List<Long> acc = new ArrayList<>();
            acc.add(id);
            for (Long c : node.childIds()) {
                List<Long> sub = out.get(c);
                if (sub != null) acc.addAll(sub);
            }
            out.put(id, List.copyOf(acc));
            stack.pop();
        }
    }

    // ───────────────────────────────────────────── 查询（全部内存 O(1)）

    public long version() { return version; }
    public int size() { return byId.size(); }
    public List<Long> rootIds() { return rootIds; }
    public Node node(Long id) { return byId.get(id); }
    public Node byCode(String code) { Long id = byCode.get(code); return id == null ? null : byId.get(id); }
    public boolean contains(Long id) { return byId.containsKey(id); }

    /** 后代 id（含自身）。 */
    public List<Long> descendantIds(Long id) { return descendants.getOrDefault(id, List.of()); }

    /** 祖先 id（含自身，由近及远）。 */
    public List<Long> ancestorIds(Long id) { return ancestors.getOrDefault(id, List.of()); }

    public String pathOf(Long id) { Node n = byId.get(id); return n == null ? null : n.path(); }

    /** a 是否为 b 的祖先（含 a == b）。 */
    public boolean isAncestor(Long a, Long b) {
        Node na = byId.get(a), nb = byId.get(b);
        return na != null && nb != null && nb.path().startsWith(na.path());
    }

    /**
     * 把一组组织 id 归约成<b>最小路径前缀集合</b> —— 数据权限拼 SQL 直接用它。
     *
     * <p>例：{@code {23, 456}} 且 456 在 23 的子树里 ⇒ 只返回 {@code ['/1/23/']}。
     * 少一个前缀就少一段 OR 条件，这在 WHERE 里是实打实的。
     */
    public List<String> minimalPathPrefixes(Collection<Long> orgIds) {
        List<String> paths = new ArrayList<>();
        for (Long id : orgIds) {
            Node n = byId.get(id);
            if (n != null) paths.add(n.path());
        }
        paths.sort(Comparator.comparingInt(String::length).thenComparing(s -> s));
        List<String> minimal = new ArrayList<>();
        for (String p : paths) {
            boolean covered = false;
            for (String kept : minimal) {
                if (p.startsWith(kept)) { covered = true; break; }
            }
            if (!covered) minimal.add(p);
        }
        return List.copyOf(minimal);
    }
}
