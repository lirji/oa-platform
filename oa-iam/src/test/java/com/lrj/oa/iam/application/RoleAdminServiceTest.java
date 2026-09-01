package com.lrj.oa.iam.application;

import com.lrj.oa.common.exception.BusinessException;
import com.lrj.oa.iam.application.command.IamCommands;
import com.lrj.oa.iam.domain.Role;
import com.lrj.oa.iam.infrastructure.mapper.RoleMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class RoleAdminServiceTest {
    private final RoleMapper mapper = mock(RoleMapper.class);
    private final IamInvalidationService invalidation = mock(IamInvalidationService.class);
    private final RoleAdminService service = new RoleAdminService(mapper, invalidation);

    @Test
    void createNormalizesRoleBuildsMatrixAndRebuildsClosure() {
        doAnswer(invocation -> {
            Role role = invocation.getArgument(0);
            role.setId(19L);
            return 1;
        }).when(mapper).insert(any(Role.class));
        when(mapper.countActivePermissions(List.of(3L, 5L))).thenReturn(2L);
        when(mapper.countTenantRoles(1L, List.of(7L))).thenReturn(1L);

        service.create(new IamCommands.CreateRole(
                " finance_admin ", "财务管理员", "org", "财务审批",
                List.of(3L, 5L, 3L), List.of(7L)));

        verify(mapper).lockTenantRoleGraph(1L);
        verify(mapper).insertDirectPermissions(19L, List.of(3L, 5L));
        verify(mapper).insertInheritanceEdges(1L, 19L, List.of(7L));
        verify(mapper).deleteTenantClosure(1L);
        verify(mapper).insertTenantSelfClosure(1L);
        verify(mapper).insertTenantTransitiveClosure(1L);
        verify(invalidation).roleChanged(19L, "CREATED", "role-create#19");
    }

    @Test
    void inheritanceRejectsSelfCrossTenantAndCycles() {
        Role role = role(19, "FINANCE", false, "ACTIVE", 2);
        when(mapper.selectTenantById(1L, 19L)).thenReturn(role);

        assertThatThrownBy(() -> service.replaceInheritance(19,
                new IamCommands.ReplaceRoleInheritance(List.of(19L), 2)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能继承自己");

        when(mapper.countTenantRoles(1L, List.of(8L))).thenReturn(0L);
        assertThatThrownBy(() -> service.replaceInheritance(19,
                new IamCommands.ReplaceRoleInheritance(List.of(8L), 2)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("当前租户");

        when(mapper.countTenantRoles(1L, List.of(7L))).thenReturn(1L);
        when(mapper.touchVersion(1L, 19L, 2)).thenReturn(1);
        when(mapper.countInheritanceCycles(1L)).thenReturn(1L);
        assertThatThrownBy(() -> service.replaceInheritance(19,
                new IamCommands.ReplaceRoleInheritance(List.of(7L), 2)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("环路");
        verify(invalidation, never()).roleChanged(19L, "INHERITANCE_REPLACED", "role-inheritance#19");
    }

    @Test
    void builtInRoleCannotBeDisabledAndSuperAdminMatrixIsProtected() {
        Role superAdmin = role(1, "SUPER_ADMIN", true, "ACTIVE", 4);
        when(mapper.selectTenantById(1L, 1L)).thenReturn(superAdmin);

        assertThatThrownBy(() -> service.setEnabled(1,
                new IamCommands.SetRoleEnabled(false, 4)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("内置角色不能停用");
        assertThatThrownBy(() -> service.replacePermissions(1,
                new IamCommands.ReplaceRolePermissions(List.of(3L), 4)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("避免管理入口锁死");
    }

    @Test
    void statusChangeRebuildsActiveClosureAndInvalidatesGlobally() {
        Role custom = role(19, "FINANCE", false, "ACTIVE", 3);
        when(mapper.selectTenantById(1L, 19L)).thenReturn(custom);
        when(mapper.updateStatus(1L, 19L, "DISABLED", 3)).thenReturn(1);

        service.setEnabled(19, new IamCommands.SetRoleEnabled(false, 3));

        verify(mapper).lockTenantRoleGraph(1L);
        verify(mapper).deleteTenantClosure(1L);
        verify(mapper).insertTenantSelfClosure(1L);
        verify(mapper).insertTenantTransitiveClosure(1L);
        verify(invalidation).roleChanged(19L, "STATUS_CHANGED", "role-status#19=false");
    }

    @Test
    void deleteRejectsRoleWithAnyGrantHistory() {
        Role custom = role(19, "FINANCE", false, "DISABLED", 3);
        when(mapper.selectTenantById(1L, 19L)).thenReturn(custom);
        when(mapper.countGrantReferences(1L, 19L)).thenReturn(1L);

        assertThatThrownBy(() -> service.delete(19, 3))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已有授权记录");
        verify(mapper, never()).softDelete(anyLong(), anyLong(), anyInt());
    }

    @Test
    void staleVersionIsReportedAsConflict() {
        Role custom = role(19, "FINANCE", false, "ACTIVE", 3);
        when(mapper.selectTenantById(1L, 19L)).thenReturn(custom);
        when(mapper.updateRole(1L, 19L, "财务", "ALL", null, 2)).thenReturn(0);

        assertThatThrownBy(() -> service.update(19,
                new IamCommands.UpdateRole("财务", "ALL", null, 2)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("刷新后重试");
    }

    private static Role role(long id, String code, boolean builtin, String status, int version) {
        Role role = new Role();
        role.setId(id);
        role.setTenantId(1L);
        role.setCode(code);
        role.setName(code);
        role.setType(builtin ? "SYSTEM" : "CUSTOM");
        role.setDefaultScope("SELF");
        role.setBuiltin(builtin);
        role.setStatus(status);
        role.setVersion(version);
        return role;
    }
}
