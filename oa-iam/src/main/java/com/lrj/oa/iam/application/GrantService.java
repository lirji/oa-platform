package com.lrj.oa.iam.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lrj.oa.common.api.ResultCode;
import com.lrj.oa.common.cache.CacheInvalidation;
import com.lrj.oa.common.cache.InvalidationBus;
import com.lrj.oa.common.context.TenantContext;
import com.lrj.oa.common.exception.BusinessException;
import com.lrj.oa.iam.application.command.IamCommands;
import com.lrj.oa.iam.domain.*;
import com.lrj.oa.iam.infrastructure.cache.PermissionEngine;
import com.lrj.oa.iam.infrastructure.mapper.*;
import com.lrj.oa.security.context.UserContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 授权的写侧。所有会改变判权结果的操作都必须从这里走，因为失效通知在这里发。
 *
 * <p><b>失效策略的不对称（安全设计，别改成对称的）</b>：
 * <ul>
 *   <li><b>收权 → bump 全局 epoch</b>：撤销、部门级授权变更、角色权限变更。
 *       各节点 1 秒内全量作废。代价是一次全量重算，换的是"撤权立即生效"这个安全底线。</li>
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
    private final PermVersionMapper versionMapper;
    private final PermissionEngine engine;
    private final InvalidationBus bus;
    private final ObjectMapper json;
    private final int maxElevationHours;
    /** 只给来源链解释用。判权热路径一行 SQL 都不许有（P99 = 1.25μs 就是这么来的）。 */
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    public GrantService(GrantMapper grantMapper, RoleMapper roleMapper, DelegationMapper delegationMapper,
                        PermVersionMapper versionMapper, PermissionEngine engine,
                        ObjectProvider<InvalidationBus> busProvider, ObjectMapper json,
                        org.springframework.jdbc.core.JdbcTemplate jdbc,
                        @Value("${oa.iam.elevation.max-hours:8}") int maxElevationHours) {
        this.jdbc = jdbc;
        this.grantMapper = grantMapper;
        this.roleMapper = roleMapper;
        this.delegationMapper = delegationMapper;
        this.versionMapper = versionMapper;
        this.engine = engine;
        this.bus = busProvider.getIfAvailable();
        this.json = json;
        this.maxElevationHours = maxElevationHours;
    }

    // ───────────────────────────────────────────── 授权

    @Transactional
    public Long grant(IamCommands.Grant cmd) {
        SubjectType subjectType = parseSubject(cmd.subjectType());
        if (roleMapper.selectById(cmd.roleId()) == null) {
            throw BusinessException.of(ResultCode.NOT_FOUND, "角色不存在: " + cmd.roleId());
        }
        GrantType grantType = cmd.grantType() == null ? GrantType.PERMANENT : parseGrantType(cmd.grantType());
        if (grantType == GrantType.TEMPORARY && cmd.validTo() == null) {
            throw BusinessException.of(ResultCode.BAD_REQUEST, "临时授权必须指定 validTo");
        }

        GrantRecord g = new GrantRecord();
        g.setTenantId(TenantContext.get());
        g.setSubjectType(subjectType.name());
        g.setSubjectId(cmd.subjectId());
        g.setRoleId(cmd.roleId());
        g.setScopeType(cmd.scopeType());
        g.setScopeOrgIds(writeJson(cmd.scopeOrgIds()));
        g.setIncludeDescendants(cmd.includeDescendants() == null || cmd.includeDescendants());
        g.setGrantType(grantType.name());
        g.setValidFrom(cmd.validFrom() == null ? OffsetDateTime.now() : cmd.validFrom());
        g.setValidTo(cmd.validTo());
        g.setSource("MANUAL");
        g.setReason(cmd.reason());
        g.setGrantedBy(currentUser());
        g.setGrantedAt(OffsetDateTime.now());
        grantMapper.insert(g);

        afterGrantChange(subjectType, cmd.subjectId(), "grant#" + g.getId());
        log.info("授权 id={} {}={} role={} scope={} type={} 至 {}",
                g.getId(), subjectType, cmd.subjectId(), cmd.roleId(), cmd.scopeType(), grantType, cmd.validTo());
        return g.getId();
    }

    @Transactional
    public void revoke(Long grantId, String reason) {
        GrantRecord g = grantMapper.selectById(grantId);
        if (g == null) throw BusinessException.of(ResultCode.NOT_FOUND, "授权不存在: " + grantId);
        if (grantMapper.revoke(grantId, currentUser(), reason) == 0) {
            throw BusinessException.of(ResultCode.CONFLICT, "该授权已被撤销");
        }
        // ★ 收权一律走全局 epoch：宁可全量重算，也不能让撤销延迟生效
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
     * <p>Phase 4 会把这里接到 workflow-platform 的 {@code oa-elevation-v1} 审批流上，
     * 审批单号字段（{@code approval_instance_id}）已经留好。
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
        // ★ 只能激活自己已持有的角色
        boolean holdsRole = grantMapper.selectBySubject(SubjectType.USER.name(), userId).stream()
                .anyMatch(g -> cmd.roleId().equals(g.getRoleId())
                        && g.grantTypeEnum() == GrantType.PERMANENT
                        && g.activeAt(OffsetDateTime.now()));
        if (!holdsRole) {
            throw BusinessException.of(ResultCode.PERM_DENIED,
                    "只能临时激活自己已持有的角色，不能凭空提权到角色 " + cmd.roleId());
        }
        GrantRecord g = new GrantRecord();
        g.setTenantId(TenantContext.get());
        g.setSubjectType(SubjectType.USER.name());
        g.setSubjectId(userId);
        g.setRoleId(cmd.roleId());
        g.setScopeType("ALL");
        g.setIncludeDescendants(false);
        g.setGrantType(GrantType.TEMPORARY.name());
        g.setValidFrom(OffsetDateTime.now());
        g.setValidTo(OffsetDateTime.now().plusHours(hours));
        g.setSource("APPROVAL");
        g.setReason(cmd.reason());
        g.setGrantedBy(currentUser());
        g.setGrantedAt(OffsetDateTime.now());
        grantMapper.insert(g);

        engine.evictLocal(userId);
        versionMapper.bumpUserVersion(userId);
        publish(CacheInvalidation.TYPE_PERM_USER, userId);
        log.warn("★ JIT 提权 user={} role={} {}小时 理由={}（提权期间操作将被标记审计）",
                userId, cmd.roleId(), hours, cmd.reason());
        return g.getId();
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
        Delegation d = new Delegation();
        d.setTenantId(TenantContext.get());
        d.setDelegatorUserId(delegatorUserId);
        d.setDelegateeUserId(cmd.delegateeUserId());
        d.setScope(cmd.scope() == null ? "ALL_TODO" : cmd.scope());
        d.setProcessKeys(writeJson(cmd.processKeys()));
        d.setRoleIds(writeJson(cmd.roleIds()));
        d.setValidFrom(cmd.validFrom() == null ? OffsetDateTime.now() : cmd.validFrom());
        d.setValidTo(cmd.validTo());
        d.setReason(cmd.reason());
        d.setStatus("ACTIVE");
        d.setCreatedBy(currentUser());
        d.setCreatedAt(OffsetDateTime.now());
        delegationMapper.insert(d);

        // 代理关系写在被代理人的快照里，所以要剔除【代理人】的缓存
        engine.evictLocal(cmd.delegateeUserId());
        versionMapper.bumpUserVersion(cmd.delegateeUserId());
        publish(CacheInvalidation.TYPE_PERM_USER, cmd.delegateeUserId());
        log.info("委托 {} -> {} 至 {}", delegatorUserId, cmd.delegateeUserId(), cmd.validTo());
        return d.getId();
    }

    @Transactional
    public void revokeDelegation(Long id) {
        Delegation d = delegationMapper.selectById(id);
        if (d == null) throw BusinessException.of(ResultCode.NOT_FOUND, "委托不存在: " + id);
        delegationMapper.revoke(id);
        // 收回代理也是"收权"，走全局 epoch
        bumpEpochAndBroadcast("revokeDelegation#" + id);
    }

    public List<GrantRecord> listBySubject(String subjectType, String subjectId) {
        return grantMapper.selectBySubject(subjectType, subjectId);
    }

    public List<Delegation> listDelegationsOf(String userId) {
        return delegationMapper.selectByDelegator(userId);
    }

    /** 到期回收：把已过期的临时授权正式标记为撤销，并留审计。由 oa-job-service 定时调用。 */
    @Transactional
    public int reclaimExpired(int limit) {
        List<GrantRecord> expired = grantMapper.selectExpired(limit);
        for (GrantRecord g : expired) {
            grantMapper.revoke(g.getId(), "system", "到期自动回收");
            log.info("回收到期授权 id={} 主体={}:{} 到期于 {}",
                    g.getId(), g.getSubjectType(), g.getSubjectId(), g.getValidTo());
        }
        // 判定本来就按时间窗过滤，这里不必 bump epoch —— 它们早就不生效了。
        return expired.size();
    }

    // ───────────────────────────────────────────── 内部

    private void afterGrantChange(SubjectType subjectType, String subjectId, String reason) {
        if (subjectType == SubjectType.USER) {
            engine.evictLocal(subjectId);
            versionMapper.bumpUserVersion(subjectId);
            publish(CacheInvalidation.TYPE_PERM_USER, subjectId);
        } else {
            // 部门/岗位授权影响的人可能成千上万，逐个失效既慢又容易漏 —— 直接走全局
            bumpEpochAndBroadcast(reason);
        }
    }

    private void bumpEpochAndBroadcast(String reason) {
        versionMapper.bumpEpoch();
        engine.evictAllLocal();
        publish(CacheInvalidation.TYPE_PERM_EPOCH, "");
        log.info("权限全局纪元已推进（{}），各节点将在 1 秒内作废全部快照", reason);
    }

    private void publish(String type, String key) {
        if (bus != null) bus.publish(type, key);
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

    private static String currentUser() {
        var ctx = UserContextHolder.peek();
        return ctx == null ? "system" : ctx.userId();
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
                             String via, String rolePath) {}

    public java.util.List<PermSource> explainSources(String userId, String permCode) {
        // 一次查完：直接授权（USER）+ 组织授权（ORG_UNIT，按 org_path 前缀继承）+ 岗位授权。
        // 角色继承走 role_inherit 闭包表，所以"父角色持有该权限"也会被算进来。
        return jdbc.query("""
                WITH my_orgs AS (
                    SELECT o.id AS org_id, o.path AS org_path, o.name AS org_name
                      FROM oa_org.employee e
                      JOIN oa_org.employee_org_assignment a
                        ON a.employee_id = e.id AND a.valid_to IS NULL
                      JOIN oa_org.org_unit o ON o.id = a.org_unit_id
                     WHERE e.user_id = ?
                ),
                my_positions AS (
                    SELECT a.position_id
                      FROM oa_org.employee e
                      JOIN oa_org.employee_org_assignment a
                        ON a.employee_id = e.id AND a.valid_to IS NULL
                     WHERE e.user_id = ? AND a.position_id IS NOT NULL
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
                         ELSE g.subject_type
                       END AS via,
                       CASE WHEN ri.distance = 0 THEN '该角色直接包含此权限'
                            ELSE '经角色继承（向上 ' || ri.distance || ' 层）获得' END AS role_path
                  FROM oa_iam.grant_record g
                  JOIN oa_iam.role_inherit ri ON ri.ancestor_role_id = g.role_id
                  JOIN oa_iam.role_permission rp ON rp.role_id = ri.descendant_role_id
                  JOIN oa_iam.permission p ON p.id = rp.permission_id AND p.code = ?
                  JOIN oa_iam.role r ON r.id = g.role_id
                  -- ★ 把 og.id 转成 text 去比，而不是把 subject_id 转成 bigint。
                  --   subject_id 对 USER 授权是 Casdoor sub（UUID 串），转 bigint 会炸；
                  --   加 `subject_id ~ '^[0-9]+$'` 守卫也不够 —— PG 不保证 AND 的短路顺序，
                  --   优化器仍可能先算那个转换。反过来转就没有任何一行会失败。
                  LEFT JOIN oa_org.org_unit og
                         ON g.subject_type = 'ORG_UNIT' AND og.id::text = g.subject_id
                 WHERE g.revoked_at IS NULL
                   AND (g.valid_to IS NULL OR g.valid_to > now())
                   AND (
                        (g.subject_type = 'USER' AND g.subject_id = ?)
                     OR (g.subject_type = 'ORG_UNIT' AND EXISTS (
                            SELECT 1 FROM my_orgs m
                             WHERE (g.include_descendants AND m.org_path LIKE og.path || '%')
                                OR (NOT g.include_descendants AND m.org_id = og.id)))
                     OR (g.subject_type = 'POSITION' AND EXISTS (
                            SELECT 1 FROM my_positions mp WHERE mp.position_id::text = g.subject_id))
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
                        rs.getString("via"), rs.getString("role_path")),
                userId, userId, permCode, userId);
    }
}
