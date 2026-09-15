package com.lrj.oa.iam.application;

import com.lrj.oa.common.api.ResultCode;
import com.lrj.oa.common.context.TenantContext;
import com.lrj.oa.common.exception.BusinessException;
import com.lrj.oa.iam.api.dto.AuthzDtos.CheckDecision;
import com.lrj.oa.iam.api.dto.AuthzDtos.CheckRequest;
import com.lrj.oa.iam.api.dto.AuthzDtos.DecisionLogView;
import com.lrj.oa.iam.api.dto.AuthzDtos.DecisionPage;
import com.lrj.oa.iam.domain.AuthzDecisionLog;
import com.lrj.oa.iam.domain.Identity;
import com.lrj.oa.iam.domain.IdentityStatus;
import com.lrj.oa.iam.domain.IdentityType;
import com.lrj.oa.iam.infrastructure.cache.PermissionEngine;
import com.lrj.oa.iam.infrastructure.mapper.AuthzDecisionLogMapper;
import com.lrj.oa.iam.infrastructure.mapper.IdentityMapper;
import com.lrj.oa.security.context.UserContext;
import com.lrj.oa.security.context.UserContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 统一授权判定门面（PDP）。HTTP {@code @RequiresPerm} 仍走切面热路径；
 * 本服务给治理台、Agent 入口和跨资源 Check 用。
 */
@Service
public class AuthzCheckService {

    private static final Logger log = LoggerFactory.getLogger(AuthzCheckService.class);

    private final IdentityMapper identities;
    private final PermissionEngine engine;
    private final AuthzDecisionLogMapper decisions;
    private final PermissionDelegationService delegations;

    public AuthzCheckService(IdentityMapper identities, PermissionEngine engine,
                             AuthzDecisionLogMapper decisions, PermissionDelegationService delegations) {
        this.identities = identities;
        this.engine = engine;
        this.decisions = decisions;
        this.delegations = delegations;
    }

    public CheckDecision check(CheckRequest req) {
        if (req == null || req.principal() == null || !StringUtils.hasText(req.principal().identityId())
                || req.resource() == null || !StringUtils.hasText(req.resource().type())
                || !StringUtils.hasText(req.resource().id()) || !StringUtils.hasText(req.action())) {
            throw BusinessException.of(ResultCode.AUTHZ_CHECK_INVALID);
        }
        UserContext caller = UserContextHolder.require();
        Identity principal = identities.selectById(TenantContext.get(), req.principal().identityId());
        if (principal == null || IdentityStatus.DELETED.name().equals(principal.getStatus())) {
            throw BusinessException.of(ResultCode.IDENTITY_NOT_FOUND);
        }
        if (!canCheck(caller, principal)) {
            throw BusinessException.of(ResultCode.PERM_DENIED, "只能判定自己的身份，或需要 oa:iam:admin");
        }

        OffsetDateTime now = OffsetDateTime.now();
        String traceId = firstNonBlank(
                req.environment() == null ? null : req.environment().traceId(),
                MDC.get("traceId"));
        CheckDecision decision = decide(principal, req, traceId, now);
        persistQuietly(caller.userId(), principal, req, decision);
        return decision;
    }

    public DecisionPage list(String identityId, String decision, Long cursor, int size) {
        int limit = Math.min(Math.max(size, 1), 200);
        List<AuthzDecisionLog> rows = decisions.selectPage(TenantContext.get(),
                emptyToNull(identityId), emptyToNull(decision), cursor, limit + 1);
        boolean hasMore = rows.size() > limit;
        if (hasMore) {
            rows = new ArrayList<>(rows.subList(0, limit));
        }
        String next = rows.isEmpty() ? "" : String.valueOf(rows.get(rows.size() - 1).getId());
        return new DecisionPage(rows.stream().map(this::toView).toList(), next, hasMore);
    }

    private CheckDecision decide(Identity principal, CheckRequest req, String traceId, OffsetDateTime now) {
        if (!IdentityStatus.ACTIVE.name().equals(principal.getStatus())
                && !IdentityStatus.CREATED.name().equals(principal.getStatus())) {
            return deny(principal.getId(), "default:deny", "身份状态不是 ACTIVE: " + principal.getStatus(), traceId, now);
        }
        if (IdentityType.AGENT.name().equals(principal.getIdentityType())) {
            return decideAgent(principal, req, traceId, now);
        }
        if (!IdentityType.USER.name().equals(principal.getIdentityType())) {
            return deny(principal.getId(), "default:deny",
                    "非人身份默认拒绝，待对应类型授权切片接入", traceId, now);
        }
        if (!"API".equalsIgnoreCase(req.resource().type())) {
            return deny(principal.getId(), "default:deny",
                    "本期只判定 resource.type=API（权限点）", traceId, now);
        }
        String permCode = req.resource().id();
        boolean allowed = engine.has(principal.getExternalKey(), permCode);
        if (allowed) {
            return new CheckDecision("ALLOW", "rbac:effective",
                    "内存快照持有权限点 " + permCode, traceId, principal.getId(), now);
        }
        Long delegationId = delegations.findActiveCovering(principal.getId(), permCode, now).orElse(null);
        if (delegationId != null) {
            return new CheckDecision("ALLOW", "delegation:" + delegationId,
                    "权限委托 #" + delegationId + " 覆盖 " + permCode, traceId, principal.getId(), now);
        }
        return deny(principal.getId(), "default:deny", "快照未持有权限点 " + permCode, traceId, now);
    }

    /**
     * Agent 只评估自身 {@code external_key} 上的授权，禁止加载属主快照。
     * 调用记录与其它 Check 一样写入 {@code authz_decision_log}（同源审计，不另开进程表）。
     */
    private CheckDecision decideAgent(Identity principal, CheckRequest req, String traceId, OffsetDateTime now) {
        if (!"INVOKE".equalsIgnoreCase(req.action())) {
            return deny(principal.getId(), "default:deny", "Agent 只支持 action=INVOKE", traceId, now);
        }
        String resourceType = req.resource().type();
        if (!"TOOL".equalsIgnoreCase(resourceType) && !"API".equalsIgnoreCase(resourceType)) {
            return deny(principal.getId(), "default:deny",
                    "Agent INVOKE 只支持 resource.type=TOOL 或 API", traceId, now);
        }
        String permCode = req.resource().id();
        if (engine.has(principal.getExternalKey(), permCode)) {
            return new CheckDecision("ALLOW", "rbac:effective",
                    "Agent 自身快照持有 " + permCode, traceId, principal.getId(), now);
        }
        return deny(principal.getId(), "default:deny",
                "Agent 不继承属主权限，自身未持有 " + permCode, traceId, now);
    }

    private boolean canCheck(UserContext caller, Identity principal) {
        if (IdentityType.USER.name().equals(principal.getIdentityType())
                && caller.userId().equals(principal.getExternalKey())) {
            return true;
        }
        if (engine.has(caller.userId(), "oa:iam:admin")) {
            return true;
        }
        return isOwnerOfAgent(caller, principal);
    }

    private boolean isOwnerOfAgent(UserContext caller, Identity principal) {
        if (!IdentityType.AGENT.name().equals(principal.getIdentityType())
                || !StringUtils.hasText(principal.getOwnerIdentityId())) {
            return false;
        }
        Identity owner = identities.selectById(TenantContext.get(), principal.getOwnerIdentityId());
        return owner != null && caller.userId().equals(owner.getExternalKey());
    }

    private void persistQuietly(String callerUserId, Identity principal, CheckRequest req, CheckDecision decision) {
        try {
            AuthzDecisionLog row = new AuthzDecisionLog();
            row.setTenantId(TenantContext.get());
            row.setCommandId(req.commandId());
            row.setTraceId(decision.traceId());
            row.setCallerUserId(callerUserId);
            row.setPrincipalId(principal.getId());
            row.setIdentityType(principal.getIdentityType());
            row.setResourceType(req.resource().type());
            row.setResourceId(req.resource().id());
            row.setAction(req.action());
            row.setDecision(decision.decision());
            row.setPolicyId(decision.policyId());
            row.setReason(decision.reason());
            row.setEvaluatedAt(decision.evaluatedAt());
            decisions.insert(row);
        } catch (RuntimeException ex) {
            log.error("授权决策审计写入失败，不影响判定结果 principal={}", principal.getId(), ex);
        }
    }

    private DecisionLogView toView(AuthzDecisionLog row) {
        return new DecisionLogView(
                row.getId(), row.getCommandId(), row.getTraceId(), row.getCallerUserId(),
                row.getPrincipalId(), row.getIdentityType(), row.getResourceType(), row.getResourceId(),
                row.getAction(), row.getDecision(), row.getPolicyId(), row.getReason(), row.getEvaluatedAt());
    }

    private static CheckDecision deny(String principalId, String policyId, String reason,
                                      String traceId, OffsetDateTime now) {
        return new CheckDecision("DENY", policyId, reason, traceId, principalId, now);
    }

    private static String firstNonBlank(String a, String b) {
        if (StringUtils.hasText(a)) return a;
        return StringUtils.hasText(b) ? b : null;
    }

    private static String emptyToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
