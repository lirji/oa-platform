package com.lrj.oa.iam.application;

import com.lrj.oa.common.api.ResultCode;
import com.lrj.oa.common.context.TenantContext;
import com.lrj.oa.common.exception.BusinessException;
import com.lrj.oa.iam.api.dto.AccessRequestDtos.AccessRequestPage;
import com.lrj.oa.iam.api.dto.AccessRequestDtos.AccessRequestView;
import com.lrj.oa.iam.api.dto.AccessRequestDtos.CreateAccessRequest;
import com.lrj.oa.iam.api.dto.AccessRequestDtos.RoleOption;
import com.lrj.oa.iam.domain.AccessRequest;
import com.lrj.oa.iam.domain.GrantRecord;
import com.lrj.oa.iam.domain.GrantType;
import com.lrj.oa.iam.domain.Identity;
import com.lrj.oa.iam.domain.IdentityType;
import com.lrj.oa.iam.domain.Role;
import com.lrj.oa.iam.domain.SubjectType;
import com.lrj.oa.iam.infrastructure.mapper.AccessRequestMapper;
import com.lrj.oa.iam.infrastructure.mapper.GrantMapper;
import com.lrj.oa.iam.infrastructure.mapper.IdentityMapper;
import com.lrj.oa.iam.infrastructure.mapper.RoleMapper;
import com.lrj.oa.security.context.UserContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 权限申请。本地四眼，对齐 {@code elevation_request}，不走远程流程引擎（D-GOV-010）。
 * 批准后写 {@code grant_record.source=APPROVAL}。
 */
@Service
public class AccessRequestService {

    private static final Logger log = LoggerFactory.getLogger(AccessRequestService.class);
    private static final Set<String> GRANTABLE = Set.of("ROLE", "TEMPORARY", "HIGH_RISK");

    private final AccessRequestMapper requests;
    private final RoleMapper roles;
    private final GrantMapper grants;
    private final IdentityMapper identities;
    private final IamInvalidationService invalidation;

    public AccessRequestService(AccessRequestMapper requests, RoleMapper roles, GrantMapper grants,
                                IdentityMapper identities, IamInvalidationService invalidation) {
        this.requests = requests;
        this.roles = roles;
        this.grants = grants;
        this.identities = identities;
        this.invalidation = invalidation;
    }

    @Transactional
    public AccessRequestView create(CreateAccessRequest cmd) {
        if (cmd == null || !StringUtils.hasText(cmd.reason())) {
            throw BusinessException.of(ResultCode.BAD_REQUEST, "申请事由必填");
        }
        String type = cmd.requestType() == null ? "ROLE" : cmd.requestType().trim().toUpperCase();
        if ("DELEGATION".equals(type)) {
            throw BusinessException.of(ResultCode.BAD_REQUEST, "权限委托请走 /iam/permission-delegations");
        }
        if (!GRANTABLE.contains(type)) {
            throw BusinessException.of(ResultCode.BAD_REQUEST, "未知申请类型: " + type);
        }
        if (cmd.roleId() == null) {
            throw BusinessException.of(ResultCode.BAD_REQUEST, "本期只支持按角色申请，roleId 必填");
        }
        if ("TEMPORARY".equals(type) && cmd.validTo() == null) {
            throw BusinessException.of(ResultCode.BAD_REQUEST, "TEMPORARY 申请必须指定 validTo");
        }
        if (cmd.validTo() != null && !cmd.validTo().isAfter(OffsetDateTime.now())) {
            throw BusinessException.of(ResultCode.BAD_REQUEST, "validTo 必须晚于当前时间");
        }

        long tenantId = TenantContext.get();
        String userId = UserContextHolder.require().userId();
        if (StringUtils.hasText(cmd.commandId())) {
            AccessRequest existing = requests.selectByCommandId(tenantId, cmd.commandId().trim());
            if (existing != null) {
                return toView(existing);
            }
        }
        AccessRequest pending = requests.selectPending(tenantId, userId, type, cmd.roleId());
        if (pending != null) {
            return toView(pending);
        }

        Role role = roles.selectTenantById(tenantId, cmd.roleId());
        if (role == null) {
            throw BusinessException.of(ResultCode.NOT_FOUND, "角色不存在: " + cmd.roleId());
        }
        if (!"ACTIVE".equals(role.getStatus())) {
            throw BusinessException.of(ResultCode.CONFLICT, "不能申请已停用角色");
        }

        Identity identity = identities.selectByExternalKey(tenantId, IdentityType.USER.name(), userId);
        OffsetDateTime now = OffsetDateTime.now();
        AccessRequest row = new AccessRequest();
        row.setTenantId(tenantId);
        row.setCommandId(emptyToNull(cmd.commandId()));
        row.setRequesterUserId(userId);
        row.setRequesterIdentityId(identity == null ? null : identity.getId());
        row.setRequestType(type);
        row.setRoleId(cmd.roleId());
        row.setPermCodes("[]");
        row.setValidTo(cmd.validTo());
        row.setReason(cmd.reason().trim());
        row.setStatus("PENDING");
        row.setRequestedAt(now);
        try {
            requests.insert(row);
        } catch (DuplicateKeyException ex) {
            AccessRequest again = StringUtils.hasText(cmd.commandId())
                    ? requests.selectByCommandId(tenantId, cmd.commandId().trim())
                    : requests.selectPending(tenantId, userId, type, cmd.roleId());
            if (again != null) {
                return toView(again);
            }
            throw BusinessException.of(ResultCode.CONFLICT, "已有相同的待审批申请");
        }
        return toView(requests.selectById(tenantId, row.getId()));
    }

    public AccessRequestPage listMine(String status, Long cursor, int size) {
        return page(UserContextHolder.require().userId(), status, cursor, size);
    }

    public AccessRequestPage listAll(String status, Long cursor, int size) {
        return page(null, status, cursor, size);
    }

    public List<RoleOption> requestableRoles() {
        return roles.selectAdminRoles(TenantContext.get(), "ACTIVE", null).stream()
                .map(r -> new RoleOption(r.getId(), r.getCode(), r.getName()))
                .toList();
    }

    @Transactional
    public AccessRequestView approve(long id, String decisionReason) {
        String approver = UserContextHolder.require().userId();
        AccessRequest row = require(id);
        if (!"PENDING".equals(row.getStatus())) {
            throw BusinessException.of(ResultCode.CONFLICT, "申请已处理: " + row.getStatus());
        }
        if (approver.equals(row.getRequesterUserId())) {
            throw BusinessException.of(ResultCode.PERM_DENIED, "申请人不能审批自己的权限申请（四眼原则）");
        }
        Role role = roles.selectTenantById(TenantContext.get(), row.getRoleId());
        if (role == null || !"ACTIVE".equals(role.getStatus())) {
            throw BusinessException.of(ResultCode.CONFLICT, "目标角色已停用或删除");
        }
        String instanceId = "ACCESS:" + row.getId();
        GrantRecord grant = activate(row, role, approver, instanceId);
        OffsetDateTime now = OffsetDateTime.now();
        if (requests.approve(TenantContext.get(), id, approver, blankToNull(decisionReason),
                grant.getId(), instanceId, now) == 0) {
            throw BusinessException.of(ResultCode.CONFLICT, "申请已被并发处理");
        }
        invalidation.user(row.getRequesterUserId(), "access-request-approved#" + id);
        log.info("权限申请已批准 id={} grant={} requester={} approver={}",
                id, grant.getId(), row.getRequesterUserId(), approver);
        return toView(requests.selectById(TenantContext.get(), id));
    }

    @Transactional
    public AccessRequestView reject(long id, String decisionReason) {
        String approver = UserContextHolder.require().userId();
        AccessRequest row = require(id);
        if (approver.equals(row.getRequesterUserId())) {
            throw BusinessException.of(ResultCode.PERM_DENIED, "申请人不能审批自己的权限申请（四眼原则）");
        }
        if (requests.reject(TenantContext.get(), id, approver, blankToNull(decisionReason), OffsetDateTime.now()) == 0) {
            throw BusinessException.of(ResultCode.CONFLICT, "申请已处理");
        }
        return toView(requests.selectById(TenantContext.get(), id));
    }

    private AccessRequestPage page(String requesterUserId, String status, Long cursor, int size) {
        int limit = Math.min(Math.max(size, 1), 200);
        String normalized = StringUtils.hasText(status) ? status.trim().toUpperCase() : null;
        List<AccessRequest> rows = requests.selectPage(TenantContext.get(), emptyToNull(requesterUserId),
                normalized, cursor, limit + 1);
        boolean hasMore = rows.size() > limit;
        if (hasMore) {
            rows = new ArrayList<>(rows.subList(0, limit));
        }
        String next = rows.isEmpty() ? "" : String.valueOf(rows.get(rows.size() - 1).getId());
        return new AccessRequestPage(rows.stream().map(this::toView).toList(), next, hasMore);
    }

    private GrantRecord activate(AccessRequest row, Role role, String approver, String instanceId) {
        boolean temporary = "TEMPORARY".equals(row.getRequestType());
        GrantRecord g = new GrantRecord();
        g.setTenantId(TenantContext.get());
        g.setSubjectType(SubjectType.USER.name());
        g.setSubjectId(row.getRequesterUserId());
        g.setRoleId(role.getId());
        g.setScopeType(role.getDefaultScope() == null ? "SELF" : role.getDefaultScope());
        g.setIncludeDescendants(false);
        g.setGrantType(temporary ? GrantType.TEMPORARY.name() : GrantType.PERMANENT.name());
        g.setValidFrom(OffsetDateTime.now());
        g.setValidTo(row.getValidTo());
        g.setSource("APPROVAL");
        g.setApprovalInstanceId(instanceId);
        g.setReason(row.getReason());
        g.setGrantedBy(approver);
        g.setGrantedAt(OffsetDateTime.now());
        grants.insert(g);
        return g;
    }

    private AccessRequest require(long id) {
        AccessRequest row = requests.selectById(TenantContext.get(), id);
        if (row == null) {
            throw BusinessException.of(ResultCode.NOT_FOUND, "权限申请不存在: " + id);
        }
        return row;
    }

    private AccessRequestView toView(AccessRequest row) {
        return new AccessRequestView(
                row.getId(), row.getCommandId(), row.getRequesterUserId(), row.getRequesterIdentityId(),
                row.getRequestType(), row.getRoleId(), row.getRoleCode(), row.getRoleName(),
                row.getValidTo(), row.getReason(), row.getStatus(), row.getDecidedBy(),
                row.getDecidedAt(), row.getDecisionReason(), row.getGrantId(),
                row.getApprovalInstanceId(), row.getRequestedAt());
    }

    private static String emptyToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
