package com.lrj.oa.iam.application;

import com.lrj.oa.common.api.ResultCode;
import com.lrj.oa.common.exception.BusinessException;
import com.lrj.oa.iam.api.dto.IdentityDtos.IdentityGraph;
import com.lrj.oa.iam.domain.Identity;
import com.lrj.oa.iam.infrastructure.mapper.IdentityMapper;
import com.lrj.oa.iam.infrastructure.mapper.IdentityMapper.RoleGrantRow;
import com.lrj.oa.org.api.OrgQueryApi;
import com.lrj.oa.org.api.dto.OrgUnitView;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

class IdentityGraphServiceTest {

    private final IdentityMapper mapper = mock(IdentityMapper.class);
    private final OrgQueryApi orgs = mock(OrgQueryApi.class);
    private final IdentityGraphService service = new IdentityGraphService(mapper, orgs);

    @Test
    void missingIdentityIsNotFound() {
        when(mapper.selectById(1L, "missing")).thenReturn(null);
        assertThatThrownBy(() -> service.graph("missing"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).resultCode())
                .isEqualTo(ResultCode.IDENTITY_NOT_FOUND);
    }

    @Test
    void userGraphHasOrgOwnerAndRoles() {
        Identity user = identity("id-1", "USER", "alice", "u1", 10L, "/1/10/", null);
        Identity agent = identity("ag-1", "AGENT", "bot", "agent-1", null, null, "id-1");
        when(mapper.selectById(1L, "id-1")).thenReturn(user);
        when(orgs.getOrg(10L)).thenReturn(new OrgUnitView(
                10L, 1L, "ENG", "工程部", "工程", "DEPT", "/1/10/", 2, 1,
                null, null, "ACTIVE"));
        when(mapper.selectOwnedBy(eq(1L), eq("id-1"), anyInt())).thenReturn(List.of(agent));
        when(mapper.selectActiveRoleGrants(eq(1L), eq("u1"), eq(10L), any()))
                .thenReturn(List.of(new RoleGrantRow(2L, "EMPLOYEE", "员工", 9L, "USER")));

        IdentityGraph graph = service.graph("id-1");

        assertThat(graph.nodes()).extracting(n -> n.type()).contains("IDENTITY", "ORG_UNIT", "ROLE");
        assertThat(graph.edges()).extracting(e -> e.type())
                .containsExactlyInAnyOrder("BELONGS_TO", "OWNS", "HAS_ROLE");
        assertThat(graph.edges()).anyMatch(e -> "BELONGS_TO".equals(e.type()) && e.to().equals("ORG_UNIT:10"));
        assertThat(graph.edges()).anyMatch(e -> "OWNS".equals(e.type()) && e.to().equals("IDENTITY:ag-1"));
        assertThat(graph.edges()).anyMatch(e -> "HAS_ROLE".equals(e.type()) && e.to().equals("ROLE:2"));
    }

    @Test
    void agentShowsOwnerOwnsEdgeWithoutRoles() {
        Identity agent = identity("ag-1", "AGENT", "bot", "agent-1", null, null, "id-1");
        Identity owner = identity("id-1", "USER", "alice", "u1", 10L, "/1/10/", null);
        when(mapper.selectById(1L, "ag-1")).thenReturn(agent);
        when(mapper.selectById(1L, "id-1")).thenReturn(owner);
        when(mapper.selectOwnedBy(eq(1L), eq("ag-1"), anyInt())).thenReturn(List.of());

        IdentityGraph graph = service.graph("ag-1");

        assertThat(graph.edges()).extracting(e -> e.type()).containsExactly("OWNS");
        assertThat(graph.edges().get(0).from()).isEqualTo("IDENTITY:id-1");
        assertThat(graph.edges().get(0).to()).isEqualTo("IDENTITY:ag-1");
        verify(mapper, never()).selectActiveRoleGrants(anyLong(), any(), any(), any());
    }

    @Test
    void orgLookupFailureFallsBackToPath() {
        Identity user = identity("id-1", "USER", "alice", "u1", 10L, "/1/10/", null);
        when(mapper.selectById(1L, "id-1")).thenReturn(user);
        when(orgs.getOrg(10L)).thenThrow(BusinessException.of(ResultCode.ORG_NOT_FOUND));
        when(mapper.selectOwnedBy(eq(1L), eq("id-1"), anyInt())).thenReturn(List.of());
        when(mapper.selectActiveRoleGrants(eq(1L), eq("u1"), eq(10L), any())).thenReturn(List.of());

        IdentityGraph graph = service.graph("id-1");

        assertThat(graph.nodes()).anyMatch(n -> "ORG_UNIT".equals(n.type()) && "/1/10/".equals(n.label()));
    }

    private static Identity identity(String id, String type, String name, String key,
                                     Long orgId, String orgPath, String ownerId) {
        Identity row = new Identity();
        row.setId(id);
        row.setTenantId(1L);
        row.setIdentityType(type);
        row.setDisplayName(name);
        row.setExternalKey(key);
        row.setStatus("ACTIVE");
        row.setOrgId(orgId);
        row.setOrgPath(orgPath);
        row.setOwnerIdentityId(ownerId);
        return row;
    }
}
