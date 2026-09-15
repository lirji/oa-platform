package com.lrj.oa.iam.application;

import com.lrj.oa.common.api.ResultCode;
import com.lrj.oa.common.exception.BusinessException;
import com.lrj.oa.iam.api.dto.AuthzDtos.CheckDecision;
import com.lrj.oa.iam.api.dto.AuthzDtos.CheckRequest;
import com.lrj.oa.iam.api.dto.AuthzDtos.EnvironmentRef;
import com.lrj.oa.iam.api.dto.AuthzDtos.PrincipalRef;
import com.lrj.oa.iam.api.dto.AuthzDtos.ResourceRef;
import com.lrj.oa.iam.domain.Identity;
import com.lrj.oa.iam.infrastructure.cache.PermissionEngine;
import com.lrj.oa.iam.infrastructure.mapper.AuthzDecisionLogMapper;
import com.lrj.oa.iam.infrastructure.mapper.IdentityMapper;
import com.lrj.oa.security.context.UserContext;
import com.lrj.oa.security.context.UserContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

class AuthzCheckServiceTest {

    private final IdentityMapper identities = mock(IdentityMapper.class);
    private final PermissionEngine engine = mock(PermissionEngine.class);
    private final AuthzDecisionLogMapper decisions = mock(AuthzDecisionLogMapper.class);
    private final PermissionDelegationService delegations = mock(PermissionDelegationService.class);
    private final AuthzCheckService service = new AuthzCheckService(identities, engine, decisions, delegations);

    @BeforeEach
    void login() {
        UserContextHolder.set(new UserContext("u1", "alice", 1L, 2L, "/1/2/", 1L));
    }

    @AfterEach
    void clear() {
        UserContextHolder.clear();
    }

    @Test
    void userWithPermissionIsAllow() {
        Identity me = user("id-1", "ACTIVE");
        when(identities.selectById(1L, "id-1")).thenReturn(me);
        when(engine.has("u1", "oa:iam:view")).thenReturn(true);

        CheckDecision d = service.check(req("id-1", "USER", "API", "oa:iam:view", "READ"));

        assertThat(d.decision()).isEqualTo("ALLOW");
        assertThat(d.policyId()).isEqualTo("rbac:effective");
        verify(decisions).insert(any());
    }

    @Test
    void userWithoutPermissionIsDeny() {
        when(identities.selectById(1L, "id-1")).thenReturn(user("id-1", "ACTIVE"));
        when(engine.has("u1", "oa:iam:admin")).thenReturn(false);

        CheckDecision d = service.check(req("id-1", "USER", "API", "oa:iam:admin", "READ"));

        assertThat(d.decision()).isEqualTo("DENY");
        assertThat(d.policyId()).isEqualTo("default:deny");
    }

    @Test
    void activePermissionDelegationAllowsCheck() {
        when(identities.selectById(1L, "id-1")).thenReturn(user("id-1", "ACTIVE"));
        when(engine.has("u1", "oa:iam:admin")).thenReturn(false);
        when(delegations.findActiveCovering(eq("id-1"), eq("oa:iam:admin"), any())).thenReturn(Optional.of(42L));

        CheckDecision d = service.check(req("id-1", "USER", "API", "oa:iam:admin", "READ"));

        assertThat(d.decision()).isEqualTo("ALLOW");
        assertThat(d.policyId()).isEqualTo("delegation:42");
    }

    @Test
    void expiredOrMissingDelegationDoesNotAllow() {
        when(identities.selectById(1L, "id-1")).thenReturn(user("id-1", "ACTIVE"));
        when(engine.has("u1", "oa:iam:admin")).thenReturn(false);
        when(delegations.findActiveCovering(eq("id-1"), eq("oa:iam:admin"), any())).thenReturn(Optional.empty());

        CheckDecision d = service.check(req("id-1", "USER", "API", "oa:iam:admin", "READ"));

        assertThat(d.decision()).isEqualTo("DENY");
        assertThat(d.policyId()).isEqualTo("default:deny");
    }

    @Test
    void cannotCheckSomeoneElseWithoutAdmin() {
        Identity other = user("id-2", "ACTIVE");
        other.setExternalKey("u2");
        when(identities.selectById(1L, "id-2")).thenReturn(other);
        when(engine.has("u1", "oa:iam:admin")).thenReturn(false);

        assertThatThrownBy(() -> service.check(req("id-2", "USER", "API", "oa:iam:view", "READ")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).resultCode())
                .isEqualTo(ResultCode.PERM_DENIED);
        verify(decisions, never()).insert(any());
    }

    @Test
    void agentIsDeniedWithoutInheritingOwner() {
        Identity agent = user("ag-1", "ACTIVE");
        agent.setIdentityType("AGENT");
        agent.setExternalKey("agent-1");
        agent.setOwnerIdentityId("id-1");
        when(identities.selectById(1L, "ag-1")).thenReturn(agent);
        when(engine.has("u1", "oa:iam:admin")).thenReturn(true);
        when(engine.has("u1", "kb.search")).thenReturn(true);
        when(engine.has("agent-1", "kb.search")).thenReturn(false);

        CheckDecision d = service.check(req("ag-1", "AGENT", "TOOL", "kb.search", "INVOKE"));

        assertThat(d.decision()).isEqualTo("DENY");
        assertThat(d.reason()).contains("不继承属主");
        verify(engine, never()).has("u1", "kb.search");
        verify(decisions).insert(any());
    }

    @Test
    void agentOwnGrantAllowsInvoke() {
        Identity agent = user("ag-1", "ACTIVE");
        agent.setIdentityType("AGENT");
        agent.setExternalKey("agent-1");
        agent.setOwnerIdentityId("id-1");
        when(identities.selectById(1L, "ag-1")).thenReturn(agent);
        when(engine.has("u1", "oa:iam:admin")).thenReturn(true);
        when(engine.has("agent-1", "kb.search")).thenReturn(true);

        CheckDecision d = service.check(req("ag-1", "AGENT", "TOOL", "kb.search", "INVOKE"));

        assertThat(d.decision()).isEqualTo("ALLOW");
        assertThat(d.policyId()).isEqualTo("rbac:effective");
        verify(engine, never()).has("u1", "kb.search");
        verify(decisions).insert(any());
    }

    @Test
    void ownerCanCheckOwnedAgentWithoutAdmin() {
        Identity agent = user("ag-1", "ACTIVE");
        agent.setIdentityType("AGENT");
        agent.setExternalKey("agent-1");
        agent.setOwnerIdentityId("id-1");
        when(identities.selectById(1L, "ag-1")).thenReturn(agent);
        when(identities.selectById(1L, "id-1")).thenReturn(user("id-1", "ACTIVE"));
        when(engine.has("u1", "oa:iam:admin")).thenReturn(false);
        when(engine.has("agent-1", "kb.search")).thenReturn(true);

        CheckDecision d = service.check(req("ag-1", "AGENT", "TOOL", "kb.search", "INVOKE"));

        assertThat(d.decision()).isEqualTo("ALLOW");
        verify(engine, never()).has("u1", "kb.search");
    }

    @Test
    void agentNonInvokeIsDenied() {
        Identity agent = user("ag-1", "ACTIVE");
        agent.setIdentityType("AGENT");
        agent.setExternalKey("agent-1");
        when(identities.selectById(1L, "ag-1")).thenReturn(agent);
        when(engine.has("u1", "oa:iam:admin")).thenReturn(true);

        CheckDecision d = service.check(req("ag-1", "AGENT", "TOOL", "kb.search", "READ"));

        assertThat(d.decision()).isEqualTo("DENY");
        assertThat(d.reason()).contains("INVOKE");
        verify(engine, never()).has("agent-1", "kb.search");
    }

    @Test
    void disabledIdentityDenied() {
        when(identities.selectById(1L, "id-1")).thenReturn(user("id-1", "DISABLED"));
        CheckDecision d = service.check(req("id-1", "USER", "API", "oa:iam:view", "READ"));
        assertThat(d.decision()).isEqualTo("DENY");
        assertThat(d.reason()).contains("不是 ACTIVE");
    }

    @Test
    void incompleteRequestIsInvalid() {
        assertThatThrownBy(() -> service.check(req("", "USER", "API", "oa:iam:view", "READ")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).resultCode())
                .isEqualTo(ResultCode.AUTHZ_CHECK_INVALID);
        verify(decisions, never()).insert(any());
    }

    @Test
    void missingIdentityIsNotFound() {
        when(identities.selectById(1L, "missing")).thenReturn(null);
        assertThatThrownBy(() -> service.check(req("missing", "USER", "API", "oa:iam:view", "READ")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).resultCode())
                .isEqualTo(ResultCode.IDENTITY_NOT_FOUND);
    }

    @Test
    void auditInsertFailureDoesNotFailDecision() {
        when(identities.selectById(1L, "id-1")).thenReturn(user("id-1", "ACTIVE"));
        when(engine.has("u1", "oa:iam:view")).thenReturn(true);
        doThrow(new RuntimeException("db down")).when(decisions).insert(any());

        CheckDecision d = service.check(req("id-1", "USER", "API", "oa:iam:view", "READ"));

        assertThat(d.decision()).isEqualTo("ALLOW");
    }

    @Test
    void nonApiResourceIsDeniedInThisSlice() {
        when(identities.selectById(1L, "id-1")).thenReturn(user("id-1", "ACTIVE"));
        CheckDecision d = service.check(req("id-1", "USER", "DOC", "doc-1", "READ"));
        assertThat(d.decision()).isEqualTo("DENY");
        assertThat(d.reason()).contains("resource.type=API");
        verify(engine, never()).has("u1", "doc-1");
    }

    private static Identity user(String id, String status) {
        Identity row = new Identity();
        row.setId(id);
        row.setTenantId(1L);
        row.setIdentityType("USER");
        row.setExternalKey("u1");
        row.setStatus(status);
        row.setDisplayName("alice");
        return row;
    }

    private static CheckRequest req(String identityId, String type, String resourceType, String resourceId, String action) {
        return new CheckRequest(
                new PrincipalRef(identityId, type),
                new ResourceRef(resourceType, resourceId, Map.of()),
                action,
                new EnvironmentRef("t-1", null, null),
                "cmd-1");
    }
}
