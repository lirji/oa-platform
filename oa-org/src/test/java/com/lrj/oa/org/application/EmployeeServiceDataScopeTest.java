package com.lrj.oa.org.application;

import com.lrj.oa.common.api.ResultCode;
import com.lrj.oa.common.crypto.SensitiveCrypto;
import com.lrj.oa.common.exception.BusinessException;
import com.lrj.oa.org.application.command.OrgCommands;
import com.lrj.oa.org.domain.AssignmentType;
import com.lrj.oa.org.domain.Employee;
import com.lrj.oa.org.domain.EmployeeOrgAssignment;
import com.lrj.oa.org.domain.OrgTreeSnapshot;
import com.lrj.oa.org.infrastructure.cache.OrgTreeCache;
import com.lrj.oa.org.infrastructure.mapper.*;
import com.lrj.oa.security.port.DataScopeAccessChecker;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class EmployeeServiceDataScopeTest {

    @Test
    void crossRegionUpdateIsRejectedBeforeAnyWrite() {
        EmployeeMapper employees = mock(EmployeeMapper.class);
        AssignmentMapper assignments = mock(AssignmentMapper.class);
        OrgTreeCache tree = mock(OrgTreeCache.class);
        OrgTreeSnapshot snapshot = mock(OrgTreeSnapshot.class);
        when(tree.snapshot()).thenReturn(snapshot);
        when(snapshot.pathOf(20L)).thenReturn("/1/20/");
        DataScopeAccessChecker denied = (permission, orgId, path, owner) -> false;
        EmployeeService service = new EmployeeService(employees, assignments,
                mock(ReportingLineMapper.class), mock(OrgUnitMapper.class), mock(SensitiveCrypto.class),
                mock(ApplicationEventPublisher.class), tree, denied);

        Employee employee = new Employee();
        employee.setId(99L);
        employee.setUserId("south-user");
        when(employees.selectTenantById(1L, 99L)).thenReturn(employee);
        EmployeeOrgAssignment primary = new EmployeeOrgAssignment();
        primary.setEmployeeId(99L);
        primary.setOrgUnitId(20L);
        primary.setAssignmentType(AssignmentType.PRIMARY.name());
        when(assignments.selectActiveByEmployee(99L)).thenReturn(List.of(primary));

        assertThatThrownBy(() -> service.update(99L,
                new OrgCommands.UpdateEmployee(null, null, null, null, null, null, null)))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> org.assertj.core.api.Assertions.assertThat(ex.resultCode())
                                .isEqualTo(ResultCode.DATA_SCOPE_DENIED));
        verify(employees, never()).updateById(any());
    }
}
