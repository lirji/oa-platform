package com.lrj.oa.iam.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lrj.oa.iam.domain.GrantRecord;
import com.lrj.oa.iam.domain.GrantType;
import com.lrj.oa.iam.domain.PermissionSnapshot;
import com.lrj.oa.iam.domain.SubjectType;
import com.lrj.oa.iam.infrastructure.mapper.*;
import com.lrj.oa.org.api.OrgQueryApi;
import com.lrj.oa.org.api.dto.AssignmentView;
import com.lrj.oa.security.model.DataScopeRule;
import com.lrj.oa.security.model.DataScopeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * L3：从数据库重算一份权限快照。缓存全部落空时才会走到这里（约 10–30ms）。
 *
 * <p>这个类是整套权限语义的<b>唯一权威实现</b> —— 判权、数据范围、临时提权、委托代理
 * 全部由它算出来，其余地方只是读快照。影子校验也是拿它的结果和缓存比对。
 */
@Component
public class PermissionSnapshotBuilder {

    private static final Logger log = LoggerFactory.getLogger(PermissionSnapshotBuilder.class);

    private final OrgQueryApi orgQuery;
    private final GrantMapper grantMapper;
    private final RoleMapper roleMapper;
    private final DelegationMapper delegationMapper;
    private final PermVersionMapper versionMapper;
    private final PermissionCatalog catalog;
    private final ObjectMapper json;
    private final long ttlMs;

    public PermissionSnapshotBuilder(OrgQueryApi orgQuery, GrantMapper grantMapper, RoleMapper roleMapper,
                                     DelegationMapper delegationMapper, PermVersionMapper versionMapper,
                                     PermissionCatalog catalog, ObjectMapper json,
                                     @Value("${oa.iam.cache.ttl-ms:300000}") long ttlMs) {
        this.orgQuery = orgQuery;
        this.grantMapper = grantMapper;
        this.roleMapper = roleMapper;
        this.delegationMapper = delegationMapper;
        this.versionMapper = versionMapper;
        this.catalog = catalog;
        this.json = json;
        this.ttlMs = ttlMs;
    }

    public PermissionSnapshot build(String userId) {
        long t0 = System.nanoTime();
        // 先读版本再读数据：反过来会把旧数据打上新版本号，导致快照永久陈旧（组织树缓存同理）
        long epoch = versionMapper.currentEpoch();
        long userVersion = versionMapper.userVersion(userId);
        OffsetDateTime now = OffsetDateTime.now();

        List<AssignmentView> assignments;
        try {
            assignments = orgQuery.activeAssignments(userId);
        } catch (Exception e) {
            // 账号存在但没有员工档案（外部账号 / 还没入职）：给空快照，而不是放行
            log.debug("用户 {} 没有任职记录，返回空快照", userId);
            return PermissionSnapshot.empty(userId, epoch, userVersion, ttlMs);
        }

        // 授权主体匹配用【全部】任职组织（含虚线：矩阵组织里挂虚线也可能带角色）；
        // 数据范围只用主岗+兼岗（虚线不叠加范围，见 AssignmentType 的约定）。
        Set<Long> allOrgIds = new LinkedHashSet<>();
        Set<Long> scopeOrgIds = new LinkedHashSet<>();
        Set<String> positionIds = new LinkedHashSet<>();
        for (AssignmentView a : assignments) {
            allOrgIds.add(a.orgUnitId());
            if (!"DOTTED".equals(a.assignmentType())) scopeOrgIds.add(a.orgUnitId());
            if (a.positionId() != null) positionIds.add(String.valueOf(a.positionId()));
        }

        // 部门授权要能被下级继承 → 候选主体是"本人所在组织 + 其全部祖先"
        Set<String> orgSubjects = new LinkedHashSet<>();
        Set<Long> directOrgIds = new LinkedHashSet<>(allOrgIds);
        for (Long o : allOrgIds) {
            for (Long a : orgQuery.ancestorIds(o)) orgSubjects.add(String.valueOf(a));
        }

        List<GrantRecord> grants = grantMapper.selectApplicable(userId, orgSubjects, positionIds, now);

        PermissionSnapshot.Builder sb = PermissionSnapshot.builder(userId)
                .epoch(epoch).userVersion(userVersion);

        List<GrantRecord> applicable = new ArrayList<>(grants.size());
        for (GrantRecord g : grants) {
            if (!applies(g, directOrgIds)) continue;
            applicable.add(g);
        }

        if (!applicable.isEmpty()) {
            Set<Long> roleIds = new LinkedHashSet<>();
            applicable.forEach(g -> roleIds.add(g.getRoleId()));

            // 角色继承展开：被授予的角色 + 它的全部后代角色所含的权限点
            Map<Long, Set<Integer>> permsByRole = new HashMap<>();
            for (var row : roleMapper.selectPermissionsOfRoles(roleIds)) {
                permsByRole.computeIfAbsent(row.getGrantedRoleId(), k -> new HashSet<>())
                        .add(row.getPermissionId().intValue());
            }

            for (GrantRecord g : applicable) {
                Set<Integer> perms = permsByRole.getOrDefault(g.getRoleId(), Set.of());
                if (perms.isEmpty()) continue;

                DataScopeRule rule = resolveScope(g, scopeOrgIds, userId);
                boolean temporary = g.grantTypeEnum() == GrantType.TEMPORARY;

                Set<String> touchedModules = new HashSet<>();
                for (int permId : perms) {
                    sb.addPerm(permId, catalog.codeOf(permId));
                    if (temporary) sb.addElevated(permId);
                    String module = catalog.moduleOf(permId);
                    if (module != null) touchedModules.add(module);
                }
                // 这条授权的作用域，落到它所触及的每个模块上
                sb.scope(null, rule);
                for (String m : touchedModules) sb.scope(m, rule);
            }
        }

        sb.delegators(delegationMapper.selectDelegatorsOf(userId));

        // ★ TTL 不能越过最近一条临时授权的到期时刻，否则过期提权会被继续放行
        long expireAt = System.currentTimeMillis() + ttlMs;
        OffsetDateTime boundary = grantMapper.nextBoundary(userId, orgSubjects, now);
        if (boundary != null) {
            long boundaryMs = boundary.toInstant().toEpochMilli();
            if (boundaryMs < expireAt) expireAt = boundaryMs;
        }
        sb.expireAt(expireAt);

        PermissionSnapshot snap = sb.build();
        log.debug("重算权限快照 user={} 授权={}条 权限点={} 耗时={}μs",
                userId, applicable.size(), snap.permCount(), (System.nanoTime() - t0) / 1000);
        return snap;
    }

    /**
     * 这条授权对当前用户是否真的适用。
     *
     * <p>核心是"部门权限继承"的第一层含义：授权落在<b>本人所在组织</b>时永远适用；
     * 落在<b>上级组织</b>时，只有 {@code include_descendants = true} 才向下惠及。
     */
    private boolean applies(GrantRecord g, Set<Long> directOrgIds) {
        SubjectType type;
        try {
            type = g.subjectTypeEnum();
        } catch (IllegalArgumentException e) {
            log.warn("授权 {} 的主体类型非法: {}，已忽略", g.getId(), g.getSubjectType());
            return false;
        }
        return switch (type) {
            case USER, POSITION -> true;
            case ORG_UNIT -> {
                Long subjectOrg = parseLong(g.getSubjectId());
                if (subjectOrg == null) yield false;
                if (directOrgIds.contains(subjectOrg)) yield true;      // 正好授在本人所在组织
                yield Boolean.TRUE.equals(g.getIncludeDescendants());   // 授在上级：看是否向下继承
            }
            // 动态人群需要一张 user_group 表，属于 Phase 3。这里明确跳过并告警，
            // 而不是静默当成"适用"或"不适用"——两种静默都会让人查半天。
            case USER_GROUP -> {
                log.warn("授权 {} 使用了 USER_GROUP 主体，Phase 2 尚未实现人群解析，已跳过", g.getId());
                yield false;
            }
        };
    }

    /**
     * 把一条授权的作用域翻译成可直接拼 SQL 的规则。
     *
     * <p>语义约定（容易搞混，写在这里）：
     * <ul>
     *   <li>{@code ORG_AND_SUB} / {@code ORG} 锚定在<b>本人所在组织</b>；</li>
     *   <li>要锚定在某个指定部门（"只管研发部及其下属"），用 {@code CUSTOM} + {@code scope_org_ids}。</li>
     * </ul>
     */
    private DataScopeRule resolveScope(GrantRecord g, Set<Long> userOrgIds, String userId) {
        DataScopeType type;
        try {
            type = DataScopeType.valueOf(g.getScopeType());
        } catch (IllegalArgumentException e) {
            log.warn("授权 {} 的作用域类型非法: {}，按 NONE 处理", g.getId(), g.getScopeType());
            return DataScopeRule.none();
        }
        return switch (type) {
            case ALL -> DataScopeRule.all();
            case SELF -> new DataScopeRule(DataScopeType.SELF, List.of(), Set.of(), userId);
            case ORG -> new DataScopeRule(DataScopeType.ORG, List.of(), Set.copyOf(userOrgIds), userId);
            case ORG_AND_SUB -> new DataScopeRule(DataScopeType.ORG_AND_SUB,
                    orgQuery.minimalPathPrefixes(userOrgIds), Set.copyOf(userOrgIds), userId);
            case CUSTOM -> {
                Set<Long> ids = parseOrgIds(g.getScopeOrgIds());
                if (ids.isEmpty()) yield DataScopeRule.none();
                yield new DataScopeRule(DataScopeType.CUSTOM,
                        orgQuery.minimalPathPrefixes(ids), ids, userId);
            }
            case NONE -> DataScopeRule.none();
        };
    }

    private Set<Long> parseOrgIds(String jsonArray) {
        if (jsonArray == null || jsonArray.isBlank()) return Set.of();
        try {
            Long[] arr = json.readValue(jsonArray, Long[].class);
            return new LinkedHashSet<>(Arrays.asList(arr));
        } catch (Exception e) {
            log.warn("解析 scope_org_ids 失败: {}", jsonArray);
            return Set.of();
        }
    }

    private static Long parseLong(String s) {
        try { return Long.valueOf(s); } catch (Exception e) { return null; }
    }
}
