package com.lrj.oa.iam.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lrj.oa.iam.domain.GrantRecord;
import com.lrj.oa.iam.infrastructure.mapper.*;
import com.lrj.oa.org.api.OrgQueryApi;
import com.lrj.oa.org.api.dto.AssignmentView;
import com.lrj.oa.security.model.DataScopeType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PermissionSnapshotBuilderElevationTest {

    @Test
    void jitRecordElevatesButDoesNotExpandCustomScope() {
        OrgQueryApi org = mock(OrgQueryApi.class);
        GrantMapper grants = mock(GrantMapper.class);
        RoleMapper roles = mock(RoleMapper.class);
        DelegationMapper delegations = mock(DelegationMapper.class);
        PermVersionMapper versions = mock(PermVersionMapper.class);
        PermissionCatalog catalog = mock(PermissionCatalog.class);
        UserGroupMapper groups = mock(UserGroupMapper.class);
        PermissionConditionMapper conditions = mock(PermissionConditionMapper.class);

        when(versions.currentEpoch()).thenReturn(4L);
        when(versions.userVersion("u1")).thenReturn(7L);
        when(org.activeAssignments("u1")).thenReturn(List.of(new AssignmentView(
                1L, 2L, 10L, "华东", "/1/10/", 3L, "经理", "PRIMARY", true,
                LocalDate.now().minusYears(1), null)));
        when(org.ancestorIds(10L)).thenReturn(List.of(1L, 10L));
        when(org.minimalPathPrefixes(Set.of(10L))).thenReturn(List.of("/1/10/"));
        when(groups.selectActiveGroupIds(anyLong(), eq("u1"), any())).thenReturn(List.of());
        when(delegations.selectActiveForDelegatee(anyLong(), eq("u1"))).thenReturn(List.of());

        GrantRecord permanent = grant("PERMANENT", "MANUAL", "CUSTOM", "[10]");
        GrantRecord elevation = grant("TEMPORARY", "APPROVAL", "NONE", null);
        when(grants.selectApplicable(eq(1L), eq("u1"), anyCollection(), anyCollection(), anyCollection(), any()))
                .thenReturn(List.of(permanent, elevation));

        RolePermissionRow row = new RolePermissionRow();
        row.setGrantedRoleId(5L);
        row.setPermissionId(9L);
        when(roles.selectPermissionsOfRoles(anyCollection())).thenReturn(List.of(row));
        when(catalog.codeOf(9)).thenReturn("oa:employee:view");
        when(catalog.moduleOf(9)).thenReturn("org");
        when(catalog.requiresElevation(9)).thenReturn(true);

        PermissionSnapshotBuilder builder = new PermissionSnapshotBuilder(org, grants, roles, delegations,
                versions, catalog, groups, conditions, new ObjectMapper(), 300_000, false);
        var snapshot = builder.build("u1");

        assertThat(snapshot.has(9)).isTrue();
        assertThat(snapshot.isElevated(9)).isTrue();
        assertThat(snapshot.scopeOfPermission(9).type()).isEqualTo(DataScopeType.CUSTOM);
        assertThat(snapshot.scopeOfPermission(9).orgIds()).containsExactly(10L);
    }

    private static GrantRecord grant(String type, String source, String scope, String orgIds) {
        GrantRecord grant = new GrantRecord();
        grant.setId("APPROVAL".equals(source) ? 2L : 1L);
        grant.setTenantId(1L);
        grant.setSubjectType("USER");
        grant.setSubjectId("u1");
        grant.setRoleId(5L);
        grant.setGrantType(type);
        grant.setSource(source);
        grant.setScopeType(scope);
        grant.setScopeOrgIds(orgIds);
        grant.setIncludeDescendants(false);
        return grant;
    }
}
