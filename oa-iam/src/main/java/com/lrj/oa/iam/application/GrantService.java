package com.lrj.oa.iam.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lrj.oa.common.api.ResultCode;
import com.lrj.oa.common.context.TenantContext;
import com.lrj.oa.common.exception.BusinessException;
import com.lrj.oa.iam.application.command.IamCommands;
import com.lrj.oa.iam.domain.*;
import com.lrj.oa.iam.infrastructure.cache.PermissionEngine;
import com.lrj.oa.iam.infrastructure.mapper.*;
import com.lrj.oa.security.context.UserContextHolder;
import com.lrj.oa.security.model.DataScopeType;
import com.lrj.oa.security.port.DataScopeAccessChecker;
import com.lrj.oa.security.annotation.ObjectScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

/**
 * 授权的写侧。所有会改变判权结果的操作都必须从这里走，因为失效通知在这里发。
 *
 * <p><b>失效策略的不对称（安全设计，别改成对称的）</b>：
 * <ul>
 *   <li><b>收权 → bump 租户 epoch</b>：撤销、部门级授权变更、角色权限变更。
 *       各节点 1 秒内作废该租户。代价是租户内懒重算，换的是"撤权立即生效"这个安全底线。</li>
 *   <li><b>给个人加权 → 只 bump 用户版本 + 本节点剔除 + 通知其它节点</b>。
 *       通知丢了最坏是新权限晚几分钟生效 —— 不构成安全问题。</li>
 * </ul>
 */
@Service
public class GrantService {

    private static final Logger log = LoggerFactory.getLogger(GrantService.class);

    private final GrantMapper grantMapper;
    private final RoleMapper roleMapper;
    private final DelegationMapper delegationMapper;
    private final PermissionEngine engine;
    private final IamInvalidationService invalidation;
    private final ObjectMapper json;
    private final int maxElevationHours;
    private final UserGroupMapper userGroupMapper;
    private final GrantReferenceMapper references;
    private final DataScopeAccessChecker dataScope;
    private final ElevationRequestMapper elevationRequests;
    /** 只给来源链解释用。判权热路径一行 SQL 都不许有（P99 = 1.25μs 就是这么来的）。 */
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    public GrantService(GrantMapper grantMapper, RoleMapper roleMapper, DelegationMapper delegationMapper,
                        PermissionEngine engine, IamInvalidationService invalidation, ObjectMapper json,
                        org.springframework.jdbc.core.JdbcTemplate jdbc, UserGroupMapper userGroupMapper,
                        GrantReferenceMapper references, DataScopeAccessChecker dataScope,
                        ElevationRequestMapper elevationRequests,
                        @Value("${oa.iam.elevation.max-hours:8}") int maxElevationHours) {
        this.jdbc = jdbc;
        this.grantMapper = grantMapper;
        this.roleMapper = roleMapper;
        this.delegationMapper = delegationMapper;
        this.engine = engine;
        this.invalidation = invalidation;
        this.json = json;
        this.maxElevationHours = maxElevationHours;
        this.userGroupMapper = userGroupMapper;
        this.references = references;
        this.dataScope = dataScope;
        this.elevationRequests = elevationRequests;
    }

    // ───────────────────────────────────────────── 授权

    @Transactional
    public Long grant(IamCommands.Grant cmd) {
        SubjectType subjectType = parseSubject(cmd.subjectType());
        long tenantId = TenantContext.get();
        var targetRole = roleMapper.selectTenantById(tenantId, cmd.roleId());
        if (targetRole == null) {
            throw BusinessException.of(ResultCode.NOT_FOUND, "角色不存在: " + cmd.roleId());
        }
        if (!"ACTIVE".equals(targetRole.getStatus())) {
            throw BusinessException.of(ResultCode.CONFLICT, "不能授予已停用或删除的角色");
        }
        String actor = currentUser();
        if (subjectType == SubjectType.USER && actor.equals(cmd.subjectId())) {
            throw BusinessException.of(ResultCode.PERM_DENIED, "禁止给自己新增角色授权（四眼原则）");
        }
        if ((Boolean.TRUE.equals(targetRole.getBuiltin())
                || roleMapper.roleIncludesHighPrivilege(tenantId, targetRole.getId())) && !"system".equals(actor)
                && !engine.has(actor, "oa:iam:admin")) {
            throw BusinessException.of(ResultCode.PERM_DENIED, "高权角色只能由权限管理员授予");
        }

        GrantReferenceMapper.ScopeTarget target = requireSubject(tenantId, subjectType, cmd.subjectId());
        if (subjectType == SubjectType.USER_GROUP) {
            Long groupId;
            try { groupId = Long.valueOf(cmd.subjectId()); }
            catch (NumberFormatException e) {
                throw BusinessException.of(ResultCode.BAD_REQUEST, "USER_GROUP subjectId 必须是用户组 id");
            }
            UserGroup group = userGroupMapper.selectTenantGroup(TenantContext.get(), groupId);
            if (group == null) throw BusinessException.of(ResultCode.NOT_FOUND, "用户组不存在: " + groupId);
            if (!"ACTIVE".equals(group.getStatus())) {
                throw BusinessException.of(ResultCode.CONFLICT, "不能给已禁用用户组授权");
            }
            if (!"system".equals(actor)
                    && engine.dataScopeForPermission(actor, "oa:iam:grant").type() != DataScopeType.ALL) {
                throw BusinessException.of(ResultCode.DATA_SCOPE_DENIED, "跨组织用户组授权要求 ALL 数据范围");
            }
        } else if (!"system".equals(actor)) {
            dataScope.require("oa:iam:grant", target.orgId, target.orgPath, target.userId);
        }
        GrantType grantType = cmd.grantType() == null ? GrantType.PERMANENT : parseGrantType(cmd.grantType());
        if (grantType == GrantType.TEMPORARY && cmd.validTo() == null) {
            throw BusinessException.of(ResultCode.BAD_REQUEST, "临时授权必须指定 validTo");
        }
        OffsetDateTime validFrom = cmd.validFrom() == null ? OffsetDateTime.now() : cmd.validFrom();
        if (cmd.validTo() != null && !cmd.validTo().isAfter(validFrom)) {
            throw BusinessException.of(ResultCode.BAD_REQUEST, "授权结束时间必须晚于开始时间");
        }
        String scopeType = normalizeScope(cmd.scopeType(), targetRole.getDefaultScope());
        if (!scopeAllowedByRole(scopeType, targetRole.getDefaultScope())) {
            throw BusinessException.of(ResultCode.PERM_DENIED,
                    "请求范围 " + scopeType + " 超出角色可分配上限 " + targetRole.getDefaultScope());
        }
        List<Long> scopeOrgIds = normalizeScopeOrgs(tenantId, scopeType, cmd.scopeOrgIds(), actor);
        boolean includeDescendants = cmd.includeDescendants() == null || cmd.includeDescendants();
        String scopeJson = writeJson(scopeOrgIds);
        String lockKey = String.join("|", "oa-grant", String.valueOf(tenantId), subjectType.name(),
                cmd.subjectId(), String.valueOf(cmd.roleId()), scopeType, scopeJson,
                String.valueOf(includeDescendants), grantType.name(), String.valueOf(cmd.validTo()));
        grantMapper.lockGrantKey(lockKey);
        GrantRecord duplicate = grantMapper.selectDuplicateActive(tenantId, subjectType.name(), cmd.subjectId(),
                cmd.roleId(), scopeType, scopeJson, includeDescendants, grantType.name(), cmd.validTo());
        if (duplicate != null) {
            log.info("幂等命中已有授权 id={}", duplicate.getId());
            return duplicate.getId();
        }

        GrantRecord g = new GrantRecord();
        g.setTenantId(tenantId);
        g.setSubjectType(subjectType.name());
        g.setSubjectId(cmd.subjectId());
        g.setRoleId(cmd.roleId());
        g.setScopeType(scopeType);
        g.setScopeOrgIds(scopeJson);
        g.setIncludeDescendants(includeDescendants);
        g.setGrantType(grantType.name());
        g.setValidFrom(validFrom);
        g.setValidTo(cmd.validTo());
        g.setSource("MANUAL");
        g.setReason(cmd.reason());
        g.setGrantedBy(actor);
        g.setGrantedAt(OffsetDateTime.now());
        grantMapper.insert(g);

        afterGrantChange(subjectType, cmd.subjectId(), "grant#" + g.getId());
        log.info("授权 id={} {}={} role={} scope={} type={} 至 {}",
                g.getId(), subjectType, cmd.subjectId(), cmd.roleId(), scopeType, grantType, cmd.validTo());
        return g.getId();
    }

    @Transactional
    public void revoke(Long grantId, String reason) {
        GrantRecord g = grantMapper.selectById(grantId);
        if (g == null) throw BusinessException.of(ResultCode.NOT_FOUND, "授权不存在: " + grantId);
        if (!java.util.Objects.equals(g.getTenantId(), TenantContext.get())) {
            throw BusinessException.of(ResultCode.NOT_FOUND, "授权不存在: " + grantId);
        }
        if (grantMapper.revoke(TenantContext.get(), grantId, currentUser(), reason) == 0) {
            throw BusinessException.of(ResultCode.CONFLICT, "该授权已被撤销");
        }
        // ★ 收权一律走租户 epoch：宁可租户内重算，也不能让撤销延迟生效
        bumpEpochAndBroadcast("revoke#" + grantId);
        log.info("撤销授权 id={} 主体={}:{} 原因={}", grantId, g.getSubjectType(), g.getSubjectId(), reason);
    }

    // ───────────────────────────────────────────── JIT 提权

    /**
     * 临时提权。
     *
     * <p><b>安全闸门不在"谁能调这个接口"，而在"只能激活你本来就持有的角色"</b> ——
     * 这正是 JIT 提权的原义：平时有这个角色但处于未激活态，做高危操作时才临时点亮。
     * 就像 sudo：不在 sudoers 里的人，能执行 sudo 命令也没用。
     * 少了这一条，自助提权就等于自助越权。
     *
     * <p>申请只落 PENDING 请求；必须由另一名具备审批权限的人批准后才生成 elevation-only grant。
     */
    @Transactional
    public Long elevate(String userId, IamCommands.Elevate cmd) {
        int hours = cmd.hours() == null ? 1 : cmd.hours();
        if (hours <= 0 || hours > maxElevationHours) {
            throw BusinessException.of(ResultCode.BAD_REQUEST,
                    "提权时长必须在 1~" + maxElevationHours + " 小时之间");
        }
        if (cmd.reason() == null || cmd.reason().isBlank()) {
            throw BusinessException.of(ResultCode.BAD_REQUEST, "提权必须填写理由（会进审计）");
        }
        var targetRole = roleMapper.selectTenantById(TenantContext.get(), cmd.roleId());
        if (targetRole == null) {
            throw BusinessException.of(ResultCode.NOT_FOUND, "角色不存在: " + cmd.roleId());
        }
        if (!"ACTIVE".equals(targetRole.getStatus())) {
            throw BusinessException.of(ResultCode.CONFLICT, "不能提权到已停用或删除的角色");
        }
        // ★ 只能激活自己已持有的角色
        boolean holdsRole = grantMapper.selectBySubject(TenantContext.get(), SubjectType.USER.name(), userId).stream()
                .anyMatch(g -> cmd.roleId().equals(g.getRoleId())
                        && g.grantTypeEnum() == GrantType.PERMANENT
                        && g.activeAt(OffsetDateTime.now()));
        if (!holdsRole) {
            throw BusinessException.of(ResultCode.PERM_DENIED,
                    "只能临时激活自己已持有的角色，不能凭空提权到角色 " + cmd.roleId());
        }
        Long pending = elevationRequests.pendingId(TenantContext.get(), userId, cmd.roleId());
        if (pending != null) return pending;
        Long requestId = elevationRequests.insert(TenantContext.get(), userId, cmd.roleId(), hours,
                cmd.reason().trim());
        log.warn("★ JIT 提权申请进入四眼审批 request={} user={} role={} {}小时",
                requestId, userId, cmd.roleId(), hours);
        return requestId;
    }

    @Transactional
    public Long approveElevation(long requestId, String decisionReason) {
        String approver = currentUser();
        var request = elevationRequests.selectForUpdate(TenantContext.get(), requestId);
        if (request == null) throw BusinessException.of(ResultCode.NOT_FOUND, "提权申请不存在: " + requestId);
        if (!"PENDING".equals(request.status)) throw BusinessException.of(ResultCode.CONFLICT, "提权申请已处理: " + request.status);
        if (approver.equals(request.requesterId)) {
            throw BusinessException.of(ResultCode.PERM_DENIED, "申请人不能审批自己的提权申请（四眼原则）");
        }
        var role = roleMapper.selectTenantById(TenantContext.get(), request.roleId);
        if (role == null || !"ACTIVE".equals(role.getStatus())) {
            throw BusinessException.of(ResultCode.CONFLICT, "目标角色已停用或删除");
        }
        boolean stillHolds = grantMapper.selectBySubject(TenantContext.get(), SubjectType.USER.name(), request.requesterId).stream()
                .anyMatch(g -> request.roleId.equals(g.getRoleId())
                        && g.grantTypeEnum() == GrantType.PERMANENT && g.activeAt(OffsetDateTime.now()));
        if (!stillHolds) throw BusinessException.of(ResultCode.PERM_DENIED, "申请人已不再持有该角色，不能批准提权");

        GrantRecord g = activateElevation(request.requesterId, request.roleId, request.hours,
                request.reason, requestId, approver);
        if (elevationRequests.approve(TenantContext.get(), requestId, approver,
                blankToNull(decisionReason), g.getId()) == 0) {
            throw BusinessException.of(ResultCode.CONFLICT, "提权申请已被并发处理");
        }
        invalidation.user(request.requesterId, "elevation-approved#" + requestId);
        log.warn("★ JIT 提权已批准 request={} grant={} requester={} approver={} validTo={}",
                requestId, g.getId(), request.requesterId, approver, g.getValidTo());
        return g.getId();
    }

    @Transactional
    public void rejectElevation(long requestId, String decisionReason) {
        String approver = currentUser();
        var request = elevationRequests.selectForUpdate(TenantContext.get(), requestId);
        if (request == null) throw BusinessException.of(ResultCode.NOT_FOUND, "提权申请不存在: " + requestId);
        if (approver.equals(request.requesterId)) {
            throw BusinessException.of(ResultCode.PERM_DENIED, "申请人不能审批自己的提权申请（四眼原则）");
        }
        if (elevationRequests.reject(TenantContext.get(), requestId, approver, blankToNull(decisionReason)) == 0) {
            throw BusinessException.of(ResultCode.CONFLICT, "提权申请已处理");
        }
    }

    public List<ElevationRequestMapper.Row> listElevationRequests(String requesterId, String status, int limit) {
        String normalized = status == null || status.isBlank() ? null : status.toUpperCase(java.util.Locale.ROOT);
        return elevationRequests.list(TenantContext.get(), requesterId, normalized, Math.min(Math.max(limit, 1), 200));
    }

    private GrantRecord activateElevation(String userId, long roleId, int hours, String reason,
                                          long requestId, String approver) {
        GrantRecord g = new GrantRecord();
        g.setTenantId(TenantContext.get());
        g.setSubjectType(SubjectType.USER.name());
        g.setSubjectId(userId);
        g.setRoleId(roleId);
        g.setScopeType("NONE");
        g.setIncludeDescendants(false);
        g.setGrantType(GrantType.TEMPORARY.name());
        g.setValidFrom(OffsetDateTime.now());
        g.setValidTo(OffsetDateTime.now().plusHours(hours));
        g.setSource("APPROVAL");
        g.setApprovalInstanceId("ELEVATION:" + requestId);
        g.setReason(reason);
        g.setGrantedBy(approver);
        g.setGrantedAt(OffsetDateTime.now());
        grantMapper.insert(g);
        return g;
    }

    // ───────────────────────────────────────────── 委托代理

    @Transactional
    public Long delegate(String delegatorUserId, IamCommands.Delegate cmd) {
        if (delegatorUserId.equals(cmd.delegateeUserId())) {
            throw BusinessException.of(ResultCode.BAD_REQUEST, "不能把待办委托给自己");
        }
        if (cmd.validTo().isBefore(OffsetDateTime.now())) {
            throw BusinessException.of(ResultCode.BAD_REQUEST, "委托结束时间必须晚于现在");
        }
        String scope = cmd.scope() == null ? "ALL_TODO" : cmd.scope().trim().toUpperCase(java.util.Locale.ROOT);
        if (!java.util.Set.of("ALL_TODO", "BY_PROCESS_KEY", "BY_ROLE").contains(scope)) {
            throw BusinessException.of(ResultCode.BAD_REQUEST, "委托范围必须是 ALL_TODO、BY_PROCESS_KEY 或 BY_ROLE");
        }
        List<String> processKeys = cmd.processKeys() == null ? List.of() : cmd.processKeys().stream()
                .filter(java.util.Objects::nonNull).map(String::trim).filter(v -> !v.isBlank()).distinct().toList();
        List<Long> roleIds = cmd.roleIds() == null ? List.of() : cmd.roleIds().stream()
                .filter(java.util.Objects::nonNull).distinct().toList();
        if ("BY_PROCESS_KEY".equals(scope) && processKeys.isEmpty()) {
            throw BusinessException.of(ResultCode.BAD_REQUEST, "按流程委托必须至少选择一个流程定义");
        }
        if ("BY_ROLE".equals(scope)) {
            if (roleIds.isEmpty()) {
                throw BusinessException.of(ResultCode.BAD_REQUEST, "按角色委托必须至少选择一个角色");
            }
            if (roleMapper.countTenantRoles(TenantContext.get(), roleIds) != roleIds.size()) {
                throw BusinessException.of(ResultCode.BAD_REQUEST, "委托角色不存在、已删除或不属于当前租户");
            }
        }
        Delegation d = new Delegation();
        d.setTenantId(TenantContext.get());
        d.setDelegatorUserId(delegatorUserId);
        d.setDelegateeUserId(cmd.delegateeUserId());
        d.setScope(scope);
        d.setProcessKeys(writeJson("BY_PROCESS_KEY".equals(scope) ? processKeys : List.of()));
        d.setRoleIds(writeJson("BY_ROLE".equals(scope) ? roleIds : List.of()));
        d.setValidFrom(cmd.validFrom() == null ? OffsetDateTime.now() : cmd.validFrom());
        d.setValidTo(cmd.validTo());
        d.setReason(cmd.reason());
        d.setStatus("ACTIVE");
        d.setCreatedBy(currentUser());
        d.setCreatedAt(OffsetDateTime.now());
        delegationMapper.insert(d);

        // 代理关系写在被代理人的快照里，所以要剔除【代理人】的缓存
        invalidation.user(cmd.delegateeUserId(), "delegation-created#" + d.getId());
        log.info("委托 {} -> {} 至 {}", delegatorUserId, cmd.delegateeUserId(), cmd.validTo());
        return d.getId();
    }

    @Transactional
    public void revokeDelegation(Long id) {
        Delegation d = delegationMapper.selectById(id);
        if (d == null) throw BusinessException.of(ResultCode.NOT_FOUND, "委托不存在: " + id);
        delegationMapper.revoke(TenantContext.get(), id);
        // 收回代理也是"收权"，走租户 epoch
        bumpEpochAndBroadcast("revokeDelegation#" + id);
    }

    public List<GrantRecord> listBySubject(String subjectType, String subjectId) {
        return grantMapper.selectBySubject(TenantContext.get(), subjectType, subjectId);
    }

    public List<Delegation> listDelegationsOf(String userId) {
        return delegationMapper.selectByDelegator(TenantContext.get(), userId);
    }

    /** 到期回收：把已过期的临时授权正式标记为撤销，并留审计。由 oa-job-service 定时调用。 */
    @Transactional
    public int reclaimExpired(int limit) {
        List<GrantRecord> expired = grantMapper.selectExpired(TenantContext.get(), limit);
        for (GrantRecord g : expired) {
            grantMapper.revoke(TenantContext.get(), g.getId(), "system", "到期自动回收");
            log.info("回收到期授权 id={} 主体={}:{} 到期于 {}",
                    g.getId(), g.getSubjectType(), g.getSubjectId(), g.getValidTo());
        }
        // 判定本来就按时间窗过滤，这里不必 bump epoch —— 它们早就不生效了。
        return expired.size();
    }

    // ───────────────────────────────────────────── 内部

    private void afterGrantChange(SubjectType subjectType, String subjectId, String reason) {
        if (subjectType == SubjectType.USER) {
            invalidation.user(subjectId, reason);
        } else {
            // 部门/岗位授权影响的人可能成千上万，逐个失效既慢又容易漏 —— 直接走全局
            bumpEpochAndBroadcast(reason);
        }
    }

    private void bumpEpochAndBroadcast(String reason) {
        invalidation.all(reason);
        log.info("租户权限纪元已推进（{}），各节点将在 1 秒内作废该租户快照", reason);
    }

    private String writeJson(Object v) {
        if (v == null) return null;
        try { return json.writeValueAsString(v); } catch (Exception e) { return null; }
    }

    private static SubjectType parseSubject(String s) {
        try { return SubjectType.valueOf(s); }
        catch (IllegalArgumentException e) { throw BusinessException.of(ResultCode.BAD_REQUEST, "非法主体类型: " + s); }
    }

    private static GrantType parseGrantType(String s) {
        try { return GrantType.valueOf(s); }
        catch (IllegalArgumentException e) { throw BusinessException.of(ResultCode.BAD_REQUEST, "非法授权类型: " + s); }
    }

    private GrantReferenceMapper.ScopeTarget requireSubject(long tenantId, SubjectType type, String subjectId) {
        try {
            return switch (type) {
                case USER -> {
                    var target = references.activeUser(tenantId, subjectId);
                    if (target == null) throw BusinessException.of(ResultCode.NOT_FOUND, "用户不存在或已离职: " + subjectId);
                    yield target;
                }
                case ORG_UNIT -> {
                    var target = references.activeOrg(tenantId, Long.parseLong(subjectId));
                    if (target == null) throw BusinessException.of(ResultCode.NOT_FOUND, "组织不存在或已停用: " + subjectId);
                    yield target;
                }
                case POSITION -> {
                    var target = references.activePosition(tenantId, Long.parseLong(subjectId));
                    if (target == null) throw BusinessException.of(ResultCode.NOT_FOUND, "岗位不存在或已停用: " + subjectId);
                    yield target;
                }
                case USER_GROUP -> new GrantReferenceMapper.ScopeTarget();
            };
        } catch (NumberFormatException e) {
            throw BusinessException.of(ResultCode.BAD_REQUEST, type + " subjectId 必须是数字 id");
        }
    }

    private static String normalizeScope(String requested, String roleDefault) {
        String value = requested == null || requested.isBlank() ? roleDefault : requested;
        try { return DataScopeType.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT)).name(); }
        catch (Exception e) { throw BusinessException.of(ResultCode.BAD_REQUEST, "非法数据范围: " + requested); }
    }

    /** 当前模型以角色 default_scope 同时作为可分配范围上限。 */
    private static boolean scopeAllowedByRole(String requested, String maximum) {
        DataScopeType req = DataScopeType.valueOf(requested);
        DataScopeType max = DataScopeType.valueOf(maximum);
        if (req == DataScopeType.NONE || req == DataScopeType.SELF) return true;
        return switch (max) {
            case ALL -> true;
            case ORG_AND_SUB -> req != DataScopeType.ALL;
            case CUSTOM -> Set.of(DataScopeType.CUSTOM, DataScopeType.ORG).contains(req);
            case ORG -> req == DataScopeType.ORG;
            case SELF, NONE -> false;
        };
    }

    private List<Long> normalizeScopeOrgs(long tenantId, String scopeType, List<Long> requested, String actor) {
        List<Long> ids = requested == null ? List.of() : requested.stream()
                .filter(java.util.Objects::nonNull).distinct().sorted().toList();
        if (!"CUSTOM".equals(scopeType)) {
            if (!ids.isEmpty()) throw BusinessException.of(ResultCode.BAD_REQUEST, "只有 CUSTOM 范围可以指定组织");
            return List.of();
        }
        if (ids.isEmpty()) throw BusinessException.of(ResultCode.BAD_REQUEST, "CUSTOM 范围必须至少选择一个组织");
        if (references.countActiveOrgs(tenantId, ids) != ids.size()) {
            throw BusinessException.of(ResultCode.BAD_REQUEST, "CUSTOM 包含不存在、停用或非当前租户组织");
        }
        if (!"system".equals(actor)) {
            for (Long id : ids) {
                var org = references.activeOrg(tenantId, id);
                dataScope.require("oa:iam:grant", id, org.orgPath, actor);
            }
        }
        return ids;
    }

    private static String currentUser() {
        var ctx = UserContextHolder.peek();
        return ctx == null ? "system" : ctx.userId();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /**
     * 解释「这条权限是怎么来的」—— 返回所有能推出该权限点的授权来源。
     *
     * <p>★ 权限沙盘中栏要回答的正是这个（FINAL_PLAN §16）。
     * 放在后端而不是让前端多调几次 {@code /iam/grants} 自己拼：
     * 继承规则（角色继承闭包 + 组织授权按 org_path 前缀向下继承 + 任职关系）全在后端，
     * 前端拼装等于把判权语义抄一遍 —— 两份实现迟早分叉，
     * 而分叉的那天，沙盘会理直气壮地解释错。
     *
     * <p>一条权限可能有<b>多个</b>来源（本人被直接授权 + 所在部门也被授权），
     * 所以返回列表而不是单条：撤销其中一条不一定就失去权限，这件事必须让人看见。
     */
    /**
     * 一条来源。★ 用类型化 record 而不是 {@code Map<String,Object>} —— CLAUDE.md 硬约束第 9 条：
     * PG 把列名转小写、MyBatis 又可能转驼峰，两层叠加后前端取 {@code s.roleCode} 还是
     * {@code s.role_code} 全靠猜，猜错静默拿到 undefined。
     */
    public record PermSource(Long grantId, String subjectType, String subjectId,
                             String roleCode, String roleName, Integer roleDistance,
                             String grantType, String scopeType, Boolean includeDescendants,
                             java.time.OffsetDateTime validTo, String reason,
                             String grantedBy, java.time.OffsetDateTime grantedAt,
                             String via, String rolePath, String abacExpression) {}

    @ObjectScope(permission = "oa:iam:admin",
            tables = {"oa_iam.grant_record", "oa_iam.role", "oa_iam.permission", "oa_org.employee"},
            strategy = ObjectScope.Strategy.RESOURCE,
            reason = "权限沙盘管理员读取授权真值与组织来源链")
    public java.util.List<PermSource> explainSources(String userId, String permCode) {
        // 一次查完：直接授权（USER）+ 组织授权（ORG_UNIT，按 org_path 前缀继承）+ 岗位授权。
        // 角色继承走 role_inherit 闭包表，所以"父角色持有该权限"也会被算进来。
        return jdbc.query("""
                WITH my_orgs AS (
                    SELECT o.id AS org_id, o.path AS org_path, o.name AS org_name
                      FROM oa_org.employee e
                      JOIN oa_org.employee_org_assignment a
                        ON a.employee_id = e.id AND a.valid_to IS NULL
                      JOIN oa_org.org_unit o ON o.tenant_id=e.tenant_id AND o.id = a.org_unit_id
                     WHERE e.tenant_id=? AND e.user_id = ?
                ),
                my_positions AS (
                    SELECT a.position_id
                      FROM oa_org.employee e
                      JOIN oa_org.employee_org_assignment a
                        ON a.employee_id = e.id AND a.valid_to IS NULL
                     WHERE e.tenant_id=? AND e.user_id = ? AND a.position_id IS NOT NULL
                ),
                my_groups AS (
                    SELECT g.id, g.name
                      FROM oa_iam.user_group_member gm
                      JOIN oa_iam.user_group g ON g.id=gm.group_id AND g.tenant_id=gm.tenant_id
                     WHERE gm.user_id=? AND gm.tenant_id=? AND gm.revoked_at IS NULL
                       AND g.status='ACTIVE' AND gm.valid_from<=now()
                       AND (gm.valid_to IS NULL OR gm.valid_to>now())
                )
                SELECT g.id                AS grant_id,
                       g.subject_type      AS subject_type,
                       g.subject_id        AS subject_id,
                       r.code              AS role_code,
                       r.name              AS role_name,
                       ri.distance         AS role_distance,
                       g.grant_type        AS grant_type,
                       g.scope_type        AS scope_type,
                       g.include_descendants AS include_descendants,
                       g.valid_to          AS valid_to,
                       g.reason            AS reason,
                       g.granted_by        AS granted_by,
                       g.granted_at        AS granted_at,
                       CASE
                         WHEN g.subject_type = 'USER'      THEN '本人被直接授权'
                         WHEN g.subject_type = 'ORG_UNIT'  THEN '所在组织「' || coalesce(og.name, g.subject_id) || '」被授权'
                                                                || CASE WHEN g.include_descendants THEN '（含下级，按 org_path 前缀继承）' ELSE '' END
                         WHEN g.subject_type = 'POSITION'  THEN '所任岗位被授权'
                         WHEN g.subject_type = 'USER_GROUP' THEN '所在用户组「' || coalesce(ug.name, g.subject_id) || '」被授权'
                         ELSE g.subject_type
                       END AS via,
                       CASE WHEN ri.distance = 0 THEN '该角色直接包含此权限'
                            ELSE '经角色继承（向上 ' || ri.distance || ' 层）获得' END AS role_path,
                       (SELECT string_agg(pc.expression, ' AND ' ORDER BY pc.id)
                         FROM oa_iam.permission_condition pc
                         WHERE pc.tenant_id=g.tenant_id
                           AND pc.role_id=g.role_id AND pc.permission_id=p.id
                           AND pc.enabled AND pc.deleted_at IS NULL) AS abac_expression
                  FROM oa_iam.grant_record g
                  JOIN oa_iam.role_inherit ri ON ri.ancestor_role_id = g.role_id
                  JOIN oa_iam.role_permission rp ON rp.role_id = ri.descendant_role_id
                  JOIN oa_iam.permission p ON p.id = rp.permission_id AND p.code = ?
                  JOIN oa_iam.role r ON r.tenant_id=g.tenant_id AND r.id = g.role_id
                  -- ★ 把 og.id 转成 text 去比，而不是把 subject_id 转成 bigint。
                  --   subject_id 对 USER 授权是 Casdoor sub（UUID 串），转 bigint 会炸；
                  --   加 `subject_id ~ '^[0-9]+$'` 守卫也不够 —— PG 不保证 AND 的短路顺序，
                  --   优化器仍可能先算那个转换。反过来转就没有任何一行会失败。
                  LEFT JOIN oa_org.org_unit og
                         ON og.tenant_id=g.tenant_id AND g.subject_type = 'ORG_UNIT' AND og.id::text = g.subject_id
                  LEFT JOIN my_groups ug
                         ON g.subject_type = 'USER_GROUP' AND ug.id::text = g.subject_id
                 WHERE g.tenant_id=? AND g.revoked_at IS NULL
                   AND (g.valid_to IS NULL OR g.valid_to > now())
                   AND (
                        (g.subject_type = 'USER' AND g.subject_id = ?)
                     OR (g.subject_type = 'ORG_UNIT' AND EXISTS (
                            SELECT 1 FROM my_orgs m
                             WHERE (g.include_descendants AND m.org_path LIKE og.path || '%')
                                OR (NOT g.include_descendants AND m.org_id = og.id)))
                     OR (g.subject_type = 'POSITION' AND EXISTS (
                            SELECT 1 FROM my_positions mp WHERE mp.position_id::text = g.subject_id))
                     OR (g.subject_type = 'USER_GROUP' AND ug.id IS NOT NULL)
                   )
                 ORDER BY g.id
                """, (rs, i) -> new PermSource(
                        rs.getLong("grant_id"), rs.getString("subject_type"), rs.getString("subject_id"),
                        rs.getString("role_code"), rs.getString("role_name"),
                        (Integer) rs.getObject("role_distance"),
                        rs.getString("grant_type"), rs.getString("scope_type"),
                        (Boolean) rs.getObject("include_descendants"),
                        rs.getObject("valid_to", java.time.OffsetDateTime.class),
                        rs.getString("reason"), rs.getString("granted_by"),
                        rs.getObject("granted_at", java.time.OffsetDateTime.class),
                        rs.getString("via"), rs.getString("role_path"), rs.getString("abac_expression")),
                TenantContext.get(), userId, TenantContext.get(), userId,
                userId, TenantContext.get(), permCode, TenantContext.get(), userId);
    }
}
