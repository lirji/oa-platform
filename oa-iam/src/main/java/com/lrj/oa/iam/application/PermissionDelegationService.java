package com.lrj.oa.iam.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lrj.oa.common.api.ResultCode;
import com.lrj.oa.common.context.TenantContext;
import com.lrj.oa.common.exception.BusinessException;
import com.lrj.oa.iam.api.dto.PermissionDelegationDtos.CreatePermissionDelegation;
import com.lrj.oa.iam.api.dto.PermissionDelegationDtos.PermissionDelegationPage;
import com.lrj.oa.iam.api.dto.PermissionDelegationDtos.PermissionDelegationView;
import com.lrj.oa.iam.api.dto.PermissionDelegationDtos.RoleOption;
import com.lrj.oa.iam.domain.Identity;
import com.lrj.oa.iam.domain.IdentityStatus;
import com.lrj.oa.iam.domain.IdentityType;
import com.lrj.oa.iam.domain.PermissionDelegation;
import com.lrj.oa.iam.domain.Role;
import com.lrj.oa.iam.infrastructure.cache.PermissionEngine;
import com.lrj.oa.iam.infrastructure.mapper.IdentityMapper;
import com.lrj.oa.iam.infrastructure.mapper.PermissionDelegationMapper;
import com.lrj.oa.iam.infrastructure.mapper.RoleMapper;
import com.lrj.oa.iam.infrastructure.mapper.RolePermissionRow;
import com.lrj.oa.security.context.UserContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

/**
 * 权限委托。写入 {@code permission_delegation}，不碰待办 {@code oa_iam.delegation}。
 * Check 认时间窗；{@code @RequiresPerm} 热路径不读本表。
 */
@Service
public class PermissionDelegationService {

    private static final TypeReference<List<String>> STRINGS = new TypeReference<>() {};
    private static final TypeReference<List<Long>> LONGS = new TypeReference<>() {};

    private final PermissionDelegationMapper delegations;
    private final IdentityMapper identities;
    private final RoleMapper roles;
    private final PermissionCatalog catalog;
    private final PermissionEngine engine;
    private final ObjectMapper json;

    public PermissionDelegationService(PermissionDelegationMapper delegations, IdentityMapper identities,
                                       RoleMapper roles, PermissionCatalog catalog, PermissionEngine engine,
                                       ObjectMapper json) {
        this.delegations = delegations;
        this.identities = identities;
        this.roles = roles;
        this.catalog = catalog;
        this.engine = engine;
        this.json = json;
    }

    @Transactional
    public PermissionDelegationView create(CreatePermissionDelegation cmd) {
        if (cmd == null || !StringUtils.hasText(cmd.delegateeIdentityId()) || !StringUtils.hasText(cmd.reason())) {
            throw BusinessException.of(ResultCode.BAD_REQUEST, "被委托人与委托事由必填");
        }
        if (Boolean.TRUE.equals(cmd.reDelegate())) {
            throw BusinessException.of(ResultCode.BAD_REQUEST, "本期禁止再次委托");
        }
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime validFrom = cmd.validFrom() == null ? now : cmd.validFrom();
        if (cmd.validTo() == null || !cmd.validTo().isAfter(now) || !cmd.validTo().isAfter(validFrom)) {
            throw BusinessException.of(ResultCode.BAD_REQUEST, "validTo 必须晚于当前时间与 validFrom");
        }

        long tenantId = TenantContext.get();
        String userId = UserContextHolder.require().userId();
        Identity me = identities.selectByExternalKey(tenantId, IdentityType.USER.name(), userId);
        if (me == null || IdentityStatus.DELETED.name().equals(me.getStatus())) {
            throw BusinessException.of(ResultCode.IDENTITY_NOT_FOUND, "委托人身份不存在");
        }
        Identity delegatee = identities.selectById(tenantId, cmd.delegateeIdentityId().trim());
        if (delegatee == null || IdentityStatus.DELETED.name().equals(delegatee.getStatus())) {
            throw BusinessException.of(ResultCode.IDENTITY_NOT_FOUND);
        }
        if (!IdentityType.USER.name().equals(delegatee.getIdentityType())) {
            throw BusinessException.of(ResultCode.BAD_REQUEST, "只能委托给人员身份");
        }
        if (!IdentityStatus.ACTIVE.name().equals(delegatee.getStatus())
                && !IdentityStatus.CREATED.name().equals(delegatee.getStatus())) {
            throw BusinessException.of(ResultCode.IDENTITY_STATUS_CONFLICT, "被委托人状态不可用: " + delegatee.getStatus());
        }
        if (me.getId().equals(delegatee.getId())) {
            throw BusinessException.of(ResultCode.BAD_REQUEST, "不能委托给自己");
        }

        List<Long> roleIds = cmd.roleIds() == null ? List.of() : cmd.roleIds().stream()
                .filter(id -> id != null && id > 0).distinct().toList();
        for (Long roleId : roleIds) {
            Role role = roles.selectTenantById(tenantId, roleId);
            if (role == null) {
                throw BusinessException.of(ResultCode.NOT_FOUND, "角色不存在: " + roleId);
            }
            if (!"ACTIVE".equals(role.getStatus())) {
                throw BusinessException.of(ResultCode.CONFLICT, "不能委托已停用角色");
            }
        }

        LinkedHashSet<String> codes = new LinkedHashSet<>();
        if (cmd.permCodes() != null) {
            for (String code : cmd.permCodes()) {
                if (!StringUtils.hasText(code)) continue;
                String trimmed = code.trim();
                if (catalog.idOf(trimmed) < 0) {
                    throw BusinessException.of(ResultCode.BAD_REQUEST, "未知权限点: " + trimmed);
                }
                codes.add(trimmed);
            }
        }
        if (!roleIds.isEmpty()) {
            for (RolePermissionRow row : roles.selectPermissionsOfRoles(roleIds)) {
                if (row.getPermissionId() == null) continue;
                String code = catalog.codeOf(row.getPermissionId().intValue());
                if (StringUtils.hasText(code)) {
                    codes.add(code);
                }
            }
        }
        if (codes.isEmpty()) {
            throw BusinessException.of(ResultCode.BAD_REQUEST, "permCodes 或 roleIds 至少提供一项有效权限");
        }
        List<String> missing = new ArrayList<>();
        for (String code : codes) {
            if (!engine.has(userId, code)) {
                missing.add(code);
            }
        }
        if (!missing.isEmpty()) {
            throw BusinessException.of(ResultCode.DELEGATION_INVALID,
                    "委托人当前未持有（不能转委他人委托）: " + String.join(", ", missing));
        }

        PermissionDelegation row = new PermissionDelegation();
        row.setTenantId(tenantId);
        row.setDelegatorIdentityId(me.getId());
        row.setDelegatorUserId(userId);
        row.setDelegateeIdentityId(delegatee.getId());
        row.setPermCodes(writeJson(codes));
        row.setRoleIds(writeJson(roleIds));
        row.setResourceScope(blankToNull(cmd.resourceScope()));
        row.setValidFrom(validFrom);
        row.setValidTo(cmd.validTo());
        row.setReDelegate(false);
        row.setReason(cmd.reason().trim());
        row.setStatus("ACTIVE");
        row.setCreatedAt(now);
        delegations.insert(row);
        return toView(delegations.selectById(tenantId, row.getId()), me.getId());
    }

    public PermissionDelegationPage listMine(Long cursor, int size) {
        long tenantId = TenantContext.get();
        Identity me = identities.selectByExternalKey(tenantId, IdentityType.USER.name(),
                UserContextHolder.require().userId());
        if (me == null) {
            return new PermissionDelegationPage(List.of(), "", false);
        }
        int limit = Math.min(Math.max(size, 1), 200);
        List<PermissionDelegation> rows = delegations.selectPage(tenantId, me.getId(), cursor, limit + 1);
        boolean hasMore = rows.size() > limit;
        if (hasMore) {
            rows = new ArrayList<>(rows.subList(0, limit));
        }
        String next = rows.isEmpty() ? "" : String.valueOf(rows.get(rows.size() - 1).getId());
        String myId = me.getId();
        return new PermissionDelegationPage(rows.stream().map(r -> toView(r, myId)).toList(), next, hasMore);
    }

    public List<RoleOption> delegateableRoles() {
        return roles.selectAdminRoles(TenantContext.get(), "ACTIVE", null).stream()
                .map(r -> new RoleOption(r.getId(), r.getCode(), r.getName()))
                .toList();
    }

    @Transactional
    public PermissionDelegationView revoke(long id) {
        long tenantId = TenantContext.get();
        String userId = UserContextHolder.require().userId();
        PermissionDelegation row = delegations.selectById(tenantId, id);
        if (row == null) {
            throw BusinessException.of(ResultCode.NOT_FOUND, "权限委托不存在: " + id);
        }
        if (!userId.equals(row.getDelegatorUserId())) {
            throw BusinessException.of(ResultCode.PERM_DENIED, "只能撤销自己发出的委托");
        }
        if (!"ACTIVE".equals(row.getStatus())) {
            throw BusinessException.of(ResultCode.CONFLICT, "委托已撤销");
        }
        if (delegations.revoke(tenantId, id, userId, OffsetDateTime.now()) == 0) {
            throw BusinessException.of(ResultCode.CONFLICT, "委托已被并发撤销");
        }
        Identity me = identities.selectByExternalKey(tenantId, IdentityType.USER.name(), userId);
        return toView(delegations.selectById(tenantId, id), me == null ? row.getDelegatorIdentityId() : me.getId());
    }

    /**
     * Check 用：时间窗内 ACTIVE 且覆盖该权限点，并且委托人此刻仍持有。
     * 不走待办委托，也不把结果写进 PermissionEngine 快照。
     */
    public Optional<Long> findActiveCovering(String delegateeIdentityId, String permCode, OffsetDateTime now) {
        if (!StringUtils.hasText(delegateeIdentityId) || !StringUtils.hasText(permCode)) {
            return Optional.empty();
        }
        List<PermissionDelegation> rows = delegations.selectActiveForDelegatee(
                TenantContext.get(), delegateeIdentityId, now);
        for (PermissionDelegation row : rows) {
            if (!parseCodes(row.getPermCodes()).contains(permCode)) {
                continue;
            }
            if (engine.has(row.getDelegatorUserId(), permCode)) {
                return Optional.of(row.getId());
            }
        }
        return Optional.empty();
    }

    private PermissionDelegationView toView(PermissionDelegation row, String viewerIdentityId) {
        String direction = row.getDelegatorIdentityId().equals(viewerIdentityId) ? "OUTGOING" : "INCOMING";
        return new PermissionDelegationView(
                row.getId(), direction, row.getDelegatorIdentityId(), row.getDelegatorUserId(),
                row.getDelegateeIdentityId(), parseCodes(row.getPermCodes()), parseLongs(row.getRoleIds()),
                row.getValidFrom(), row.getValidTo(), Boolean.TRUE.equals(row.getReDelegate()),
                row.getReason(), row.getStatus(), row.getCreatedAt(), row.getRevokedAt());
    }

    private List<String> parseCodes(String raw) {
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        try {
            List<String> parsed = json.readValue(raw, STRINGS);
            return parsed == null ? List.of() : parsed;
        } catch (JsonProcessingException ex) {
            return List.of();
        }
    }

    private List<Long> parseLongs(String raw) {
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        try {
            List<Long> parsed = json.readValue(raw, LONGS);
            return parsed == null ? List.of() : parsed;
        } catch (JsonProcessingException ex) {
            return List.of();
        }
    }

    private String writeJson(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("序列化委托载荷失败", ex);
        }
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
