package com.lrj.oa.iam.application;

import com.lrj.oa.common.api.ResultCode;
import com.lrj.oa.common.context.TenantContext;
import com.lrj.oa.common.exception.BusinessException;
import com.lrj.oa.iam.api.dto.AuthzDtos.WhoHasAccessHolder;
import com.lrj.oa.iam.api.dto.AuthzDtos.WhoHasAccessResult;
import com.lrj.oa.iam.domain.Identity;
import com.lrj.oa.iam.domain.PermissionDelegation;
import com.lrj.oa.iam.domain.UserGroupMember;
import com.lrj.oa.iam.infrastructure.cache.PermissionEngine;
import com.lrj.oa.iam.infrastructure.mapper.IdentityMapper;
import com.lrj.oa.iam.infrastructure.mapper.PermissionDelegationMapper;
import com.lrj.oa.iam.infrastructure.mapper.UserGroupMapper;
import com.lrj.oa.iam.infrastructure.mapper.WhoHasAccessMapper;
import com.lrj.oa.iam.infrastructure.mapper.WhoHasAccessMapper.GrantHit;
import com.lrj.oa.org.api.OrgQueryApi;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 资源反查：谁持有某权限点。SQL 反查授权，不把全员丢进 {@code PermissionEngine}。
 * 组织主体按 {@code org_path LIKE 前缀%}，禁止展开成 org_id IN 大列表。
 */
@Service
public class WhoHasAccessService {

    static final int MAX_ITEMS = 200;

    private final PermissionCatalog catalog;
    private final WhoHasAccessMapper grants;
    private final IdentityMapper identities;
    private final UserGroupMapper groups;
    private final PermissionDelegationMapper delegations;
    private final OrgQueryApi orgs;
    private final PermissionEngine engine;

    public WhoHasAccessService(PermissionCatalog catalog,
                               WhoHasAccessMapper grants,
                               IdentityMapper identities,
                               UserGroupMapper groups,
                               PermissionDelegationMapper delegations,
                               OrgQueryApi orgs,
                               PermissionEngine engine) {
        this.catalog = catalog;
        this.grants = grants;
        this.identities = identities;
        this.groups = groups;
        this.delegations = delegations;
        this.orgs = orgs;
        this.engine = engine;
    }

    public WhoHasAccessResult query(String permCode, String resourceType, String resourceId) {
        String code = resolvePermCode(permCode, resourceType, resourceId);
        int permId = catalog.idOf(code);
        if (permId < 0) {
            return new WhoHasAccessResult(code, false, List.of());
        }

        long tenantId = TenantContext.get();
        OffsetDateTime now = OffsetDateTime.now();
        Map<String, WhoHasAccessHolder> out = new LinkedHashMap<>();

        for (GrantHit hit : grants.selectActiveGrantsForPerm(tenantId, permId, now)) {
            if (out.size() >= MAX_ITEMS) {
                break;
            }
            switch (hit.subjectType() == null ? "" : hit.subjectType()) {
                case "USER" -> addUserGrant(tenantId, hit, out);
                case "USER_GROUP" -> addGroupGrant(tenantId, now, hit, out);
                case "ORG_UNIT" -> addOrgGrant(tenantId, hit, out);
                default -> {
                    // POSITION 本期不反查：identity 没有岗位字段，展开会退化成 org_id IN。
                }
            }
        }
        addDelegations(tenantId, code, now, out);
        return new WhoHasAccessResult(code, true, List.copyOf(out.values()));
    }

    static String resolvePermCode(String permCode, String resourceType, String resourceId) {
        if (StringUtils.hasText(permCode)) {
            return permCode.trim();
        }
        if (!StringUtils.hasText(resourceType) && !StringUtils.hasText(resourceId)) {
            throw BusinessException.of(ResultCode.AUTHZ_CHECK_INVALID, "需要 permCode 或 resourceType+resourceId");
        }
        if (!"API".equalsIgnoreCase(resourceType) || !StringUtils.hasText(resourceId)) {
            throw BusinessException.of(ResultCode.AUTHZ_CHECK_INVALID,
                    "本期只支持 permCode 或 resourceType=API（权限点）");
        }
        return resourceId.trim();
    }

    private void addUserGrant(long tenantId, GrantHit hit, Map<String, WhoHasAccessHolder> out) {
        Identity identity = firstUserByExternalKey(tenantId, hit.subjectId());
        if (identity == null) {
            return;
        }
        boolean inherited = hit.roleDistance() > 0;
        String source = inherited ? "Inherited" : "Role";
        String via = inherited
                ? "经角色继承获得「" + hit.roleName() + "」（" + hit.roleCode() + "）"
                : "角色「" + hit.roleName() + "」（" + hit.roleCode() + "）直接授予";
        put(out, identity, source, via, hit.grantId());
    }

    private void addGroupGrant(long tenantId, OffsetDateTime now, GrantHit hit,
                               Map<String, WhoHasAccessHolder> out) {
        long groupId;
        try {
            groupId = Long.parseLong(hit.subjectId());
        } catch (NumberFormatException ex) {
            return;
        }
        List<String> keys = new ArrayList<>();
        for (UserGroupMember member : groups.selectMembers(tenantId, groupId)) {
            if (!memberActive(member, now) || !StringUtils.hasText(member.getUserId())) {
                continue;
            }
            keys.add(member.getUserId());
        }
        String via = "用户组 " + groupId + " · 角色「" + hit.roleName() + "」";
        for (Identity identity : usersByExternalKeys(tenantId, keys)) {
            if (out.size() >= MAX_ITEMS) {
                return;
            }
            put(out, identity, "Group", via, hit.grantId());
        }
    }

    private void addOrgGrant(long tenantId, GrantHit hit, Map<String, WhoHasAccessHolder> out) {
        Long orgId;
        try {
            orgId = Long.parseLong(hit.subjectId());
        } catch (NumberFormatException ex) {
            return;
        }
        boolean includeDescendants = hit.includeDescendants() == null || hit.includeDescendants();
        String prefix = null;
        if (includeDescendants) {
            try {
                prefix = orgs.pathOf(orgId);
            } catch (RuntimeException ex) {
                return;
            }
            if (!StringUtils.hasText(prefix)) {
                // 算不出前缀就一行都不给，禁止退化成全表或 org_id IN。
                return;
            }
        }
        int remain = MAX_ITEMS - out.size();
        if (remain <= 0) {
            return;
        }
        String via = includeDescendants
                ? "组织路径 " + prefix + " 授权（含下级，按 org_path 前缀继承）· 「" + hit.roleName() + "」"
                : "组织 " + orgId + " 授权（不含下级）· 「" + hit.roleName() + "」";
        for (Identity identity : identities.selectUsersByOrg(tenantId, orgId, prefix, includeDescendants, remain)) {
            put(out, identity, "Inherited", via, hit.grantId());
        }
    }

    private void addDelegations(long tenantId, String permCode, OffsetDateTime now,
                                Map<String, WhoHasAccessHolder> out) {
        if (out.size() >= MAX_ITEMS) {
            return;
        }
        List<PermissionDelegation> rows = delegations.selectActiveCoveringPerm(tenantId, permCode, now);
        if (rows.isEmpty()) {
            return;
        }
        List<String> ids = new ArrayList<>();
        for (PermissionDelegation row : rows) {
            if (StringUtils.hasText(row.getDelegateeIdentityId())) {
                ids.add(row.getDelegateeIdentityId());
            }
        }
        Map<String, Identity> byId = indexById(identitiesByIds(tenantId, ids));
        for (PermissionDelegation row : rows) {
            if (out.size() >= MAX_ITEMS) {
                return;
            }
            Identity identity = byId.get(row.getDelegateeIdentityId());
            if (identity == null || !listable(identity)) {
                continue;
            }
            if (!StringUtils.hasText(row.getDelegatorUserId())
                    || !engine.has(row.getDelegatorUserId(), permCode)) {
                continue;
            }
            put(out, identity, "Delegation",
                    "权限委托 #" + row.getId() + "（委托人仍持有）", row.getId());
        }
    }

    private Identity firstUserByExternalKey(long tenantId, String userId) {
        if (!StringUtils.hasText(userId)) {
            return null;
        }
        List<Identity> found = usersByExternalKeys(tenantId, List.of(userId));
        return found.isEmpty() ? null : found.getFirst();
    }

    private List<Identity> usersByExternalKeys(long tenantId, Collection<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return List.of();
        }
        List<Identity> rows = identities.selectByExternalKeys(tenantId, "USER", keys);
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<Identity> out = new ArrayList<>();
        for (Identity row : rows) {
            if (listable(row)) {
                out.add(row);
            }
        }
        return out;
    }

    private List<Identity> identitiesByIds(long tenantId, Collection<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<Identity> rows = identities.selectByIds(tenantId, ids);
        return rows == null ? List.of() : rows;
    }

    private static Map<String, Identity> indexById(List<Identity> rows) {
        Map<String, Identity> map = new LinkedHashMap<>();
        for (Identity row : rows) {
            map.put(row.getId(), row);
        }
        return map;
    }

    private static boolean memberActive(UserGroupMember member, OffsetDateTime now) {
        if (member.getValidFrom() != null && member.getValidFrom().isAfter(now)) {
            return false;
        }
        return member.getValidTo() == null || member.getValidTo().isAfter(now);
    }

    private static boolean listable(Identity identity) {
        if (identity == null || identity.getStatus() == null) {
            return false;
        }
        return "ACTIVE".equals(identity.getStatus()) || "CREATED".equals(identity.getStatus());
    }

    private static void put(Map<String, WhoHasAccessHolder> out,
                            Identity identity,
                            String source,
                            String via,
                            Long grantId) {
        if (out.size() >= MAX_ITEMS || identity == null || !StringUtils.hasText(identity.getId())) {
            return;
        }
        String key = identity.getId() + "|" + source + "|" + grantId;
        out.putIfAbsent(key, new WhoHasAccessHolder(
                identity.getId(),
                identity.getIdentityType(),
                identity.getDisplayName(),
                identity.getExternalKey(),
                source,
                via,
                grantId));
    }
}
