package com.lrj.oa.org.api;

import com.lrj.oa.org.api.dto.*;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 组织域对外的<b>唯一</b>入口。其它模块（oa-iam / oa-flow / oa-attendance …）只能依赖这个接口，
 * 不许碰 {@code ..org.domain..} 与 {@code ..org.infrastructure..} —— {@code ArchitectureRulesTest} 会拦。
 *
 * <p>标注"内存"的方法全部命中 {@code OrgTreeCache} 的不可变快照：零 DB、零远程，可放心在热路径调用。
 */
public interface OrgQueryApi {

    // ── 组织（内存） ─────────────────────────────────────
    OrgUnitView getOrg(Long orgId);

    /** 后代 id（含自身）。内存。 */
    List<Long> descendantIds(Long orgId);

    /** 祖先 id（含自身，由近及远）。内存。 */
    List<Long> ancestorIds(Long orgId);

    /** 物化路径 {@code /1/23/456/}。内存。 */
    String pathOf(Long orgId);

    /** a 是否为 b 的祖先（含 a == b）。内存。 */
    boolean isAncestor(Long a, Long b);

    /**
     * 把一组组织归约成<b>最小路径前缀集合</b>，数据权限拼 WHERE 直接用。内存。
     *
     * <p>这是 ADR-0007 的落点：给出前缀而不是展开成上千个 id，
     * 让 SQL 长成 {@code org_path LIKE '/1/23/%'} 而不是 {@code org_id IN (…5000 项…)}。
     */
    List<String> minimalPathPrefixes(Collection<Long> orgIds);

    /** 组织树（含子节点）。rootId 为 null 时返回全部根。内存。 */
    List<OrgTreeNodeView> tree(Long rootId, Integer maxDepth);

    // ── 人（查库，可缓存） ───────────────────────────────
    EmployeeView getEmployeeByUserId(String userId);

    /** 当前全部有效任职（主岗 + 兼岗 + 虚线）。 */
    List<AssignmentView> activeAssignments(String userId);

    /** 主岗组织 id；无主岗返回 null。 */
    Long primaryOrgId(String userId);

    /** 直属上级（实线汇报线，<b>不是</b>父部门负责人）。 */
    Optional<String> directManagerUserId(String userId);

    /** 逐级上报链，最多 maxLevel 级。审批流的"逐级审批"取它。 */
    List<String> managerChain(String userId, int maxLevel);

    /**
     * 部门负责人的 userId 列表。
     *
     * @param bubbleUpIfEmpty 本部门没设负责人时，是否向上级部门冒泡查找。
     *                        审批场景应传 true —— 否则新建部门还没配负责人，单据就卡死了。
     */
    List<String> orgLeaderUserIds(Long orgId, boolean bubbleUpIfEmpty);

    /** 某人在历史时点的组织归属快照。 */
    OrgSnapshotView asOf(String userId, LocalDate date);
}
