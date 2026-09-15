package com.lrj.oa.iam.application;

import com.lrj.oa.common.api.ResultCode;
import com.lrj.oa.common.exception.BusinessException;
import com.lrj.oa.iam.api.dto.AccessRequestDtos.AccessRequestView;
import com.lrj.oa.iam.api.dto.AccessRequestDtos.CreateAccessRequest;
import com.lrj.oa.iam.domain.AccessRequest;
import com.lrj.oa.iam.domain.GrantRecord;
import com.lrj.oa.iam.domain.Identity;
import com.lrj.oa.iam.domain.Role;
import com.lrj.oa.iam.infrastructure.mapper.AccessRequestMapper;
import com.lrj.oa.iam.infrastructure.mapper.GrantMapper;
import com.lrj.oa.iam.infrastructure.mapper.IdentityMapper;
import com.lrj.oa.iam.infrastructure.mapper.RoleMapper;
import com.lrj.oa.security.context.UserContext;
import com.lrj.oa.security.context.UserContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

class AccessRequestServiceTest {

    private final AccessRequestMapper requests = mock(AccessRequestMapper.class);
    private final RoleMapper roles = mock(RoleMapper.class);
    private final GrantMapper grants = mock(GrantMapper.class);
    private final IdentityMapper identities = mock(IdentityMapper.class);
    private final IamInvalidationService invalidation = mock(IamInvalidationService.class);
    private final AccessRequestService service =
            new AccessRequestService(requests, roles, grants, identities, invalidation);

    @BeforeEach
    void login() {
        UserContextHolder.set(new UserContext("u1", "alice", 1L, 2L, "/1/2/", 1L));
    }

    @AfterEach
    void clear() {
        UserContextHolder.clear();
    }

    @Test
    void createPendingRoleRequest() {
        when(requests.selectPending(1L, "u1", "ROLE", 2L)).thenReturn(null);
        when(roles.selectTenantById(1L, 2L)).thenReturn(role());
        Identity me = new Identity();
        me.setId("id-1");
        when(identities.selectByExternalKey(1L, "USER", "u1")).thenReturn(me);
        when(requests.insert(any())).thenAnswer(inv -> {
            AccessRequest row = inv.getArgument(0);
            row.setId(11L);
            return 1;
        });
        AccessRequest stored = pending(11L);
        stored.setRequesterIdentityId("id-1");
        when(requests.selectById(1L, 11L)).thenReturn(stored);

        AccessRequestView view = service.create(new CreateAccessRequest(
                "ROLE", 2L, null, null, "需要报表权限", "cmd-1"));

        assertThat(view.status()).isEqualTo("PENDING");
        assertThat(view.roleCode()).isEqualTo("REPORT_VIEWER");
        verify(grants, never()).insert(any());
    }

    @Test
    void duplicateCommandIdIsIdempotent() {
        when(requests.selectByCommandId(1L, "cmd-1")).thenReturn(pending(9L));
        AccessRequestView view = service.create(new CreateAccessRequest(
                "ROLE", 2L, null, null, "重复", "cmd-1"));
        assertThat(view.id()).isEqualTo(9L);
        verify(requests, never()).insert(any());
    }

    @Test
    void delegationTypeRejected() {
        assertThatThrownBy(() -> service.create(new CreateAccessRequest(
                "DELEGATION", 2L, null, null, "代理", null)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).resultCode())
                .isEqualTo(ResultCode.BAD_REQUEST);
    }

    @Test
    void approveWritesApprovalGrant() {
        when(requests.selectById(1L, 11L)).thenReturn(pending(11L), approved(11L));
        when(roles.selectTenantById(1L, 2L)).thenReturn(role());
        UserContextHolder.set(new UserContext("admin", "adm", 1L, 2L, "/1/2/", 1L));
        when(grants.insert(any(GrantRecord.class))).thenAnswer(inv -> {
            GrantRecord g = inv.getArgument(0);
            g.setId(88L);
            return 1;
        });
        when(requests.approve(eq(1L), eq(11L), eq("admin"), any(), eq(88L), eq("ACCESS:11"), any()))
                .thenReturn(1);

        AccessRequestView view = service.approve(11L, "同意");

        assertThat(view.status()).isEqualTo("APPROVED");
        verify(invalidation).user("u1", "access-request-approved#11");
        verify(grants).insert(any(GrantRecord.class));
    }

    @Test
    void cannotApproveOwnRequest() {
        when(requests.selectById(1L, 11L)).thenReturn(pending(11L));
        assertThatThrownBy(() -> service.approve(11L, null))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).resultCode())
                .isEqualTo(ResultCode.PERM_DENIED);
        verify(grants, never()).insert(any());
    }

    private static Role role() {
        Role role = new Role();
        role.setId(2L);
        role.setCode("REPORT_VIEWER");
        role.setName("报表只读");
        role.setStatus("ACTIVE");
        role.setDefaultScope("SELF");
        return role;
    }

    private static AccessRequest pending(long id) {
        AccessRequest row = new AccessRequest();
        row.setId(id);
        row.setTenantId(1L);
        row.setRequesterUserId("u1");
        row.setRequestType("ROLE");
        row.setRoleId(2L);
        row.setRoleCode("REPORT_VIEWER");
        row.setRoleName("报表只读");
        row.setReason("需要报表权限");
        row.setStatus("PENDING");
        row.setRequestedAt(OffsetDateTime.now());
        return row;
    }

    private static AccessRequest approved(long id) {
        AccessRequest row = pending(id);
        row.setStatus("APPROVED");
        row.setGrantId(88L);
        row.setApprovalInstanceId("ACCESS:" + id);
        row.setDecidedBy("admin");
        return row;
    }
}
