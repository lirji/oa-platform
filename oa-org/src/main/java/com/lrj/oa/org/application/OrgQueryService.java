package com.lrj.oa.org.application;

import com.lrj.oa.common.api.ResultCode;
import com.lrj.oa.common.exception.BusinessException;
import com.lrj.oa.org.api.OrgQueryApi;
import com.lrj.oa.org.api.dto.*;
import com.lrj.oa.org.domain.*;
import com.lrj.oa.org.infrastructure.cache.OrgTreeCache;
import com.lrj.oa.org.infrastructure.mapper.*;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;

/**
 * {@link OrgQueryApi} 的实现。
 *
 * <p><b>一条分界线</b>：凡是只关乎"组织结构"的查询（祖先、后代、路径、树）一律走
 * {@link OrgTreeCache} 的内存快照，零 DB；凡是关乎"人"的查询（任职、汇报线）才落库。
 * 前者在权限热路径上每个请求都要调，后者只在装配用户上下文时调一次。
 */
@Service
public class OrgQueryService implements OrgQueryApi {

    private final OrgTreeCache treeCache;
    private final EmployeeMapper employeeMapper;
    private final AssignmentMapper assignmentMapper;
    private final ReportingLineMapper reportingLineMapper;
    private final JobPositionMapper positionMapper;
    private final OrgUnitMapper orgUnitMapper;

    public OrgQueryService(OrgTreeCache treeCache, EmployeeMapper employeeMapper,
                           AssignmentMapper assignmentMapper, ReportingLineMapper reportingLineMapper,
                           JobPositionMapper positionMapper, OrgUnitMapper orgUnitMapper) {
        this.treeCache = treeCache;
        this.employeeMapper = employeeMapper;
        this.assignmentMapper = assignmentMapper;
        this.reportingLineMapper = reportingLineMapper;
        this.positionMapper = positionMapper;
        this.orgUnitMapper = orgUnitMapper;
    }

    // ───────────────────────────────────────────── 组织（内存）

    @Override
    public OrgUnitView getOrg(Long orgId) {
        var n = treeCache.snapshot().node(orgId);
        if (n == null) throw BusinessException.of(ResultCode.ORG_NOT_FOUND, "组织不存在: " + orgId);
        return toView(n);
    }

    @Override public List<Long> descendantIds(Long orgId) { return treeCache.snapshot().descendantIds(orgId); }
    @Override public List<Long> ancestorIds(Long orgId)   { return treeCache.snapshot().ancestorIds(orgId); }
    @Override public String pathOf(Long orgId)            { return treeCache.snapshot().pathOf(orgId); }
    @Override public boolean isAncestor(Long a, Long b)   { return treeCache.snapshot().isAncestor(a, b); }

    @Override
    public List<String> minimalPathPrefixes(Collection<Long> orgIds) {
        return treeCache.snapshot().minimalPathPrefixes(orgIds);
    }

    @Override
    public List<OrgTreeNodeView> tree(Long rootId, Integer maxDepth) {
        OrgTreeSnapshot snap = treeCache.snapshot();
        int limit = maxDepth == null ? Integer.MAX_VALUE : maxDepth;
        List<Long> roots = rootId == null ? snap.rootIds() : List.of(rootId);
        List<OrgTreeNodeView> out = new ArrayList<>(roots.size());
        for (Long r : roots) {
            OrgTreeNodeView v = buildTree(snap, r, limit);
            if (v != null) out.add(v);
        }
        return out;
    }

    private OrgTreeNodeView buildTree(OrgTreeSnapshot snap, Long id, int remainingDepth) {
        var n = snap.node(id);
        if (n == null) return null;
        List<OrgTreeNodeView> children = List.of();
        if (remainingDepth > 0 && !n.childIds().isEmpty()) {
            children = new ArrayList<>(n.childIds().size());
            for (Long c : n.childIds()) {
                OrgTreeNodeView cv = buildTree(snap, c, remainingDepth - 1);
                if (cv != null) children.add(cv);
            }
        }
        return new OrgTreeNodeView(n.id(), n.parentId(), n.code(), n.name(), n.type().name(),
                n.path(), n.depth(), n.sortOrder(), n.status().name(), n.leaderUserId(),
                // 成员数是库查询，只在需要时取；树接口默认不带（-1 表示未统计）
                -1, children);
    }

    // ───────────────────────────────────────────── 人（查库）

    @Override
    public EmployeeView getEmployeeByUserId(String userId) {
        Employee e = employeeMapper.selectByUserId(userId);
        if (e == null) throw BusinessException.of(ResultCode.EMPLOYEE_NOT_FOUND, "员工不存在: " + userId);
        return toView(e, assignmentMapper.selectActiveByEmployee(e.getId()));
    }

    @Override
    public List<AssignmentView> activeAssignments(String userId) {
        Employee e = employeeMapper.selectByUserId(userId);
        if (e == null) return List.of();
        return assignmentMapper.selectActiveByEmployee(e.getId()).stream().map(this::toView).toList();
    }

    @Override
    public Long primaryOrgId(String userId) {
        Employee e = employeeMapper.selectByUserId(userId);
        if (e == null) return null;
        return assignmentMapper.selectActiveByEmployee(e.getId()).stream()
                .filter(a -> a.typeEnum() == AssignmentType.PRIMARY)
                .map(EmployeeOrgAssignment::getOrgUnitId)
                .findFirst().orElse(null);
    }

    @Override
    public Optional<String> directManagerUserId(String userId) {
        Employee e = employeeMapper.selectByUserId(userId);
        if (e == null) return Optional.empty();
        return Optional.ofNullable(reportingLineMapper.selectSolidManagerUserId(e.getId()));
    }

    @Override
    public List<String> managerChain(String userId, int maxLevel) {
        Employee e = employeeMapper.selectByUserId(userId);
        if (e == null) return List.of();
        List<String> chain = new ArrayList<>();
        Set<Long> seen = new HashSet<>();       // 数据万一成环也不能把审批发起线程转死
        Long cursor = e.getId();
        seen.add(cursor);
        for (int i = 0; i < maxLevel; i++) {
            Long managerId = reportingLineMapper.selectSolidManagerId(cursor);
            if (managerId == null || !seen.add(managerId)) break;
            String uid = employeeMapper.selectUserIdById(managerId);
            if (uid == null) break;
            chain.add(uid);
            cursor = managerId;
        }
        return chain;
    }

    @Override
    public List<String> orgLeaderUserIds(Long orgId, boolean bubbleUpIfEmpty) {
        OrgTreeSnapshot snap = treeCache.snapshot();
        List<Long> candidates = bubbleUpIfEmpty ? snap.ancestorIds(orgId) : List.of(orgId);
        for (Long id : candidates) {
            // ① 任职表里标了 is_leader 的
            List<String> byAssignment = assignmentMapper.selectLeaderUserIds(id);
            if (!byAssignment.isEmpty()) return byAssignment;
            // ② 组织表上直接配的负责人
            var n = snap.node(id);
            if (n != null && n.leaderUserId() != null && !n.leaderUserId().isBlank()) {
                return List.of(n.leaderUserId());
            }
        }
        return List.of();
    }

    @Override
    public OrgSnapshotView asOf(String userId, LocalDate date) {
        Employee e = employeeMapper.selectByUserId(userId);
        if (e == null) throw BusinessException.of(ResultCode.EMPLOYEE_NOT_FOUND, "员工不存在: " + userId);
        LocalDate day = date == null ? LocalDate.now() : date;

        List<EmployeeOrgAssignment> asOf = assignmentMapper.selectAsOf(e.getId(), day);
        Long primaryOrgId = asOf.stream()
                .filter(a -> a.typeEnum() == AssignmentType.PRIMARY)
                .map(EmployeeOrgAssignment::getOrgUnitId)
                .findFirst().orElse(null);

        // 主岗 + 兼岗计入数据范围；虚线只影响汇报，不叠加范围（AssignmentType 的注释里写死了这条）
        List<Long> scopeOrgIds = asOf.stream()
                .filter(a -> a.typeEnum() != AssignmentType.DOTTED)
                .map(EmployeeOrgAssignment::getOrgUnitId)
                .distinct().toList();

        OrgTreeSnapshot snap = treeCache.snapshot();
        String managerUserId = reportingLineMapper.selectSolidManagerUserId(e.getId());
        var primaryNode = primaryOrgId == null ? null : snap.node(primaryOrgId);

        return new OrgSnapshotView(userId, day,
                primaryOrgId,
                primaryNode == null ? null : primaryNode.path(),
                primaryNode == null ? null : primaryNode.name(),
                scopeOrgIds,
                snap.minimalPathPrefixes(scopeOrgIds),
                managerUserId);
    }

    // ───────────────────────────────────────────── 转换

    private static OrgUnitView toView(OrgTreeSnapshot.Node n) {
        return new OrgUnitView(n.id(), n.parentId(), n.code(), n.name(), n.shortName(),
                n.type().name(), n.path(), n.depth(), n.sortOrder(),
                n.leaderUserId(), n.deputyLeaderUserId(), n.status().name());
    }

    private EmployeeView toView(Employee e, List<EmployeeOrgAssignment> assignments) {
        EmployeeOrgAssignment primary = assignments.stream()
                .filter(a -> a.typeEnum() == AssignmentType.PRIMARY).findFirst().orElse(null);
        OrgTreeSnapshot snap = treeCache.snapshot();
        var node = primary == null ? null : snap.node(primary.getOrgUnitId());
        JobPosition pos = (primary == null || primary.getPositionId() == null)
                ? null : positionMapper.selectById(primary.getPositionId());

        return new EmployeeView(e.getId(), e.getUserId(), e.getEmpNo(), e.getName(), e.getEnName(),
                e.getAvatar(), e.getEmail(), e.getEmploymentType(), e.getStatus(), e.getHireDate(),
                primary == null ? null : primary.getOrgUnitId(),
                node == null ? null : node.name(),
                node == null ? null : node.path(),
                pos == null ? null : pos.getId(),
                pos == null ? null : pos.getName());
    }

    private AssignmentView toView(EmployeeOrgAssignment a) {
        var node = treeCache.snapshot().node(a.getOrgUnitId());
        JobPosition pos = a.getPositionId() == null ? null : positionMapper.selectById(a.getPositionId());
        return new AssignmentView(a.getId(), a.getEmployeeId(), a.getOrgUnitId(),
                node == null ? null : node.name(),
                node == null ? null : node.path(),
                a.getPositionId(), pos == null ? null : pos.getName(),
                a.getAssignmentType(), Boolean.TRUE.equals(a.getIsLeader()),
                a.getValidFrom(), a.getValidTo());
    }

    /** 给运维/冒烟脚本用：组织树内存快照的当前状态。 */
    public Map<String, Object> cacheStats() {
        OrgTreeSnapshot snap = treeCache.snapshot();
        return Map.of(
                "nodes", snap.size(),
                "roots", snap.rootIds().size(),
                "snapshotVersion", snap.version(),
                "dbVersion", orgUnitMapper.currentTreeVersion());
    }
}
