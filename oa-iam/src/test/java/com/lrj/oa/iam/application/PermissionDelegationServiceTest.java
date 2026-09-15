package com.lrj.oa.iam.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lrj.oa.common.api.ResultCode;
import com.lrj.oa.common.exception.BusinessException;
import com.lrj.oa.iam.api.dto.PermissionDelegationDtos.CreatePermissionDelegation;
import com.lrj.oa.iam.api.dto.PermissionDelegationDtos.PermissionDelegationView;
import com.lrj.oa.iam.domain.Identity;
import com.lrj.oa.iam.domain.PermissionDelegation;
import com.lrj.oa.iam.domain.Role;
import com.lrj.oa.iam.infrastructure.cache.PermissionEngine;
import com.lrj.oa.iam.infrastructure.mapper.IdentityMapper;
import com.lrj.oa.iam.infrastructure.mapper.PermissionDelegationMapper;
import com.lrj.oa.iam.infrastructure.mapper.RoleMapper;
import com.lrj.oa.iam.infrastructure.mapper.RolePermissionRow;
import com.lrj.oa.security.context.UserContext;
import com.lrj.oa.security.context.UserContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

class PermissionDelegationServiceTest {

    private final PermissionDelegationMapper mapper = mock(PermissionDelegationMapper.class);
    private final IdentityMapper identities = mock(IdentityMapper.class);
    private final RoleMapper roles = mock(RoleMapper.class);
    private final PermissionCatalog catalog = mock(PermissionCatalog.class);
    private final PermissionEngine engine = mock(PermissionEngine.class);
    private final PermissionDelegationService service = new PermissionDelegationService(
            mapper, identities, roles, catalog, engine, new ObjectMapper());

    @BeforeEach
    void login() {
        UserContextHolder.set(new UserContext("u1", "alice", 1L, 2L, "/1/2/", 1L));
        when(identities.selectByExternalKey(1L, "USER", "u1")).thenReturn(user("id-1", "u1"));
        when(catalog.idOf("oa:iam:view")).thenReturn(1);
        when(catalog.codeOf(1)).thenReturn("oa:iam:view");
    }

    @AfterEach
    void clear() {
        UserContextHolder.clear();
    }

    @Test
    void createRequiresCurrentHolder() {
        when(identities.selectById(1L, "id-2")).thenReturn(user("id-2", "u2"));
        when(engine.has("u1", "oa:iam:view")).thenReturn(false);

        assertThatThrownBy(() -> service.create(cmd("id-2", List.of("oa:iam:view"), null)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).resultCode())
                .isEqualTo(ResultCode.DELEGATION_INVALID);
        verify(mapper, never()).insert(any());
    }

    @Test
    void reDelegateIsRejected() {
        CreatePermissionDelegation body = new CreatePermissionDelegation(
                "id-2", List.of("oa:iam:view"), null, null,
                null, OffsetDateTime.now().plusDays(1), true, "转委");
        assertThatThrownBy(() -> service.create(body))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).resultCode())
                .isEqualTo(ResultCode.BAD_REQUEST);
        verify(mapper, never()).insert(any());
    }

    @Test
    void createPersistsWhenHolderHasPerm() {
        when(identities.selectById(1L, "id-2")).thenReturn(user("id-2", "u2"));
        when(engine.has("u1", "oa:iam:view")).thenReturn(true);
        when(mapper.insert(any())).thenAnswer(inv -> {
            PermissionDelegation row = inv.getArgument(0);
            row.setId(9L);
            return 1;
        });
        when(mapper.selectById(1L, 9L)).thenAnswer(inv -> stored(9L));

        PermissionDelegationView view = service.create(cmd("id-2", List.of("oa:iam:view"), null));

        assertThat(view.id()).isEqualTo(9L);
        assertThat(view.direction()).isEqualTo("OUTGOING");
        assertThat(view.permCodes()).containsExactly("oa:iam:view");
        assertThat(view.status()).isEqualTo("ACTIVE");
        verify(mapper).insert(any());
    }

    @Test
    void createExpandsRoleIdsAndChecksHolder() {
        when(identities.selectById(1L, "id-2")).thenReturn(user("id-2", "u2"));
        Role role = new Role();
        role.setId(7L);
        role.setStatus("ACTIVE");
        when(roles.selectTenantById(1L, 7L)).thenReturn(role);
        RolePermissionRow row = new RolePermissionRow();
        row.setGrantedRoleId(7L);
        row.setPermissionId(1L);
        when(roles.selectPermissionsOfRoles(anyCollection())).thenReturn(List.of(row));
        when(engine.has("u1", "oa:iam:view")).thenReturn(true);
        when(mapper.insert(any())).thenAnswer(inv -> {
            PermissionDelegation d = inv.getArgument(0);
            d.setId(10L);
            return 1;
        });
        when(mapper.selectById(1L, 10L)).thenReturn(stored(10L));

        PermissionDelegationView view = service.create(cmd("id-2", null, List.of(7L)));

        assertThat(view.id()).isEqualTo(10L);
        verify(engine).has("u1", "oa:iam:view");
    }

    @Test
    void cannotDelegateToSelf() {
        when(identities.selectById(1L, "id-1")).thenReturn(user("id-1", "u1"));
        assertThatThrownBy(() -> service.create(cmd("id-1", List.of("oa:iam:view"), null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("自己");
        verify(mapper, never()).insert(any());
    }

    @Test
    void coveringRequiresDelegatorStillHolds() {
        PermissionDelegation row = stored(5L);
        when(mapper.selectActiveForDelegatee(eq(1L), eq("id-2"), any())).thenReturn(List.of(row));
        when(engine.has("u1", "oa:iam:view")).thenReturn(true);
        assertThat(service.findActiveCovering("id-2", "oa:iam:view", OffsetDateTime.now())).contains(5L);

        when(engine.has("u1", "oa:iam:view")).thenReturn(false);
        assertThat(service.findActiveCovering("id-2", "oa:iam:view", OffsetDateTime.now())).isEmpty();
    }

    @Test
    void coveringIgnoresUnrelatedPerm() {
        when(mapper.selectActiveForDelegatee(eq(1L), eq("id-2"), any())).thenReturn(List.of(stored(5L)));
        assertThat(service.findActiveCovering("id-2", "oa:iam:admin", OffsetDateTime.now())).isEmpty();
        verify(engine, never()).has("u1", "oa:iam:admin");
    }

    @Test
    void revokeMarksStatus() {
        when(mapper.selectById(1L, 9L)).thenReturn(stored(9L), revoked(9L));
        when(mapper.revoke(eq(1L), eq(9L), eq("u1"), any())).thenReturn(1);

        PermissionDelegationView view = service.revoke(9L);

        assertThat(view.status()).isEqualTo("REVOKED");
        verify(mapper).revoke(eq(1L), eq(9L), eq("u1"), any());
    }

    @Test
    void othersCannotRevoke() {
        PermissionDelegation row = stored(9L);
        row.setDelegatorUserId("u-other");
        when(mapper.selectById(1L, 9L)).thenReturn(row);
        assertThatThrownBy(() -> service.revoke(9L))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).resultCode())
                .isEqualTo(ResultCode.PERM_DENIED);
        verify(mapper, never()).revoke(eq(1L), eq(9L), any(), any());
    }

    private static CreatePermissionDelegation cmd(String delegatee, List<String> perms, List<Long> roleIds) {
        return new CreatePermissionDelegation(
                delegatee, perms, roleIds, null, null, OffsetDateTime.now().plusDays(1), false, "出差代理");
    }

    private static Identity user(String id, String externalKey) {
        Identity row = new Identity();
        row.setId(id);
        row.setTenantId(1L);
        row.setIdentityType("USER");
        row.setExternalKey(externalKey);
        row.setStatus("ACTIVE");
        return row;
    }

    private static PermissionDelegation stored(long id) {
        PermissionDelegation row = new PermissionDelegation();
        row.setId(id);
        row.setTenantId(1L);
        row.setDelegatorIdentityId("id-1");
        row.setDelegatorUserId("u1");
        row.setDelegateeIdentityId("id-2");
        row.setPermCodes("[\"oa:iam:view\"]");
        row.setRoleIds("[]");
        row.setValidFrom(OffsetDateTime.now().minusHours(1));
        row.setValidTo(OffsetDateTime.now().plusDays(1));
        row.setReDelegate(false);
        row.setReason("出差代理");
        row.setStatus("ACTIVE");
        row.setCreatedAt(OffsetDateTime.now());
        return row;
    }

    private static PermissionDelegation revoked(long id) {
        PermissionDelegation row = stored(id);
        row.setStatus("REVOKED");
        row.setRevokedAt(OffsetDateTime.now());
        row.setRevokedBy("u1");
        return row;
    }
}
