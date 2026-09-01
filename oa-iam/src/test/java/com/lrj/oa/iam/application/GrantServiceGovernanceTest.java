package com.lrj.oa.iam.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lrj.oa.common.exception.BusinessException;
import com.lrj.oa.iam.application.command.IamCommands;
import com.lrj.oa.iam.domain.GrantRecord;
import com.lrj.oa.iam.domain.Role;
import com.lrj.oa.iam.infrastructure.cache.PermissionEngine;
import com.lrj.oa.iam.infrastructure.mapper.*;
import com.lrj.oa.security.context.UserContext;
import com.lrj.oa.security.context.UserContextHolder;
import com.lrj.oa.security.port.DataScopeAccessChecker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class GrantServiceGovernanceTest {
    private final GrantMapper grants = mock(GrantMapper.class);
    private final RoleMapper roles = mock(RoleMapper.class);
    private final ElevationRequestMapper requests = mock(ElevationRequestMapper.class);
    private final PermissionEngine engine = mock(PermissionEngine.class);
    private final DataScopeAccessChecker dataScope = mock(DataScopeAccessChecker.class);
    private final GrantReferenceMapper references = mock(GrantReferenceMapper.class);
    private final IamInvalidationService invalidation = mock(IamInvalidationService.class);
    private final GrantService service = new GrantService(grants, roles, mock(DelegationMapper.class),
            engine, invalidation, new ObjectMapper(), mock(JdbcTemplate.class),
            mock(UserGroupMapper.class), references, dataScope, requests, 8);

    @AfterEach void clear() { UserContextHolder.clear(); }

    @Test
    void directSelfGrantIsRejectedBeforeAnyWrite() {
        UserContextHolder.set(new UserContext("admin", "A", 1L, 10L, "/1/10/", 1L));
        when(roles.selectTenantById(1L, 2L)).thenReturn(role(2L, "CUSTOM", false));

        assertThatThrownBy(() -> service.grant(new IamCommands.Grant(
                "USER", "admin", 2L, "CUSTOM", List.of(10L), true,
                "PERMANENT", null, null, "self")))
                .isInstanceOf(BusinessException.class).hasMessageContaining("禁止给自己");
        verify(grants, never()).insert(any());
    }

    @Test
    void elevationRequestDoesNotCreateGrantBeforeApproval() {
        UserContextHolder.set(new UserContext("u1", "U", 1L, 10L, "/1/10/", 1L));
        when(roles.selectTenantById(1L, 2L)).thenReturn(role(2L, "CUSTOM", false));
        GrantRecord permanent = new GrantRecord();
        permanent.setRoleId(2L); permanent.setGrantType("PERMANENT");
        permanent.setValidFrom(OffsetDateTime.now().minusDays(1));
        when(grants.selectBySubject(1L, "USER", "u1")).thenReturn(List.of(permanent));
        // Mockito 对包装数值类型的默认值是 0；这里必须显式模拟“没有待审批申请”。
        when(requests.pendingId(1L, "u1", 2L)).thenReturn(null);
        when(requests.insert(1L, "u1", 2L, 2, "排障")).thenReturn(12L);

        assertThat(service.elevate("u1", new IamCommands.Elevate(2L, "排障", 2))).isEqualTo(12L);
        verify(grants, never()).insert(any());
    }

    @Test
    void requesterCannotApproveOwnElevation() {
        UserContextHolder.set(new UserContext("u1", "U", 1L, 10L, "/1/10/", 1L));
        ElevationRequestMapper.Row row = new ElevationRequestMapper.Row();
        row.id = 12L; row.requesterId = "u1"; row.roleId = 2L; row.status = "PENDING";
        when(requests.selectForUpdate(1L, 12L)).thenReturn(row);

        assertThatThrownBy(() -> service.approveElevation(12L, null))
                .isInstanceOf(BusinessException.class).hasMessageContaining("四眼原则");
        verify(grants, never()).insert(any());
    }

    @Test
    void duplicateActiveGrantReturnsExistingIdWithoutWriting() {
        UserContextHolder.set(new UserContext("admin", "A", 1L, 10L, "/1/10/", 1L));
        when(roles.selectTenantById(1L, 2L)).thenReturn(role(2L, "ALL", false));
        when(references.activeUser(1L, "u2")).thenReturn(target("u2", 20L, "/1/20/"));
        GrantRecord existing = new GrantRecord(); existing.setId(44L);
        when(grants.selectDuplicateActive(anyLong(), anyString(), anyString(), anyLong(), anyString(),
                anyString(), anyBoolean(), anyString(), isNull())).thenReturn(existing);

        Long id = service.grant(new IamCommands.Grant("USER", "u2", 2L, "ORG", List.of(), false,
                "PERMANENT", null, null, "重复提交"));

        assertThat(id).isEqualTo(44L);
        verify(grants, never()).insert(any());
    }

    @Test
    void customScopeRejectsUnknownOrCrossTenantOrg() {
        UserContextHolder.set(new UserContext("admin", "A", 1L, 10L, "/1/10/", 1L));
        when(roles.selectTenantById(1L, 2L)).thenReturn(role(2L, "CUSTOM", false));
        when(references.activeUser(1L, "u2")).thenReturn(target("u2", 20L, "/1/20/"));
        when(references.countActiveOrgs(1L, List.of(999L))).thenReturn(0L);

        assertThatThrownBy(() -> service.grant(new IamCommands.Grant(
                "USER", "u2", 2L, "CUSTOM", List.of(999L), true,
                "PERMANENT", null, null, "越界组织")))
                .isInstanceOf(BusinessException.class).hasMessageContaining("非当前租户组织");
        verify(grants, never()).insert(any());
    }

    private static Role role(long id, String scope, boolean builtin) {
        Role role = new Role(); role.setId(id); role.setTenantId(1L); role.setStatus("ACTIVE");
        role.setDefaultScope(scope); role.setBuiltin(builtin); return role;
    }

    private static GrantReferenceMapper.ScopeTarget target(String userId, long orgId, String path) {
        GrantReferenceMapper.ScopeTarget target = new GrantReferenceMapper.ScopeTarget();
        target.userId = userId; target.orgId = orgId; target.orgPath = path; return target;
    }
}
