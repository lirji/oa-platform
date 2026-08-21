package com.lrj.oa.iam.application;

import com.lrj.oa.common.exception.BusinessException;
import com.lrj.oa.iam.application.command.IamCommands;
import com.lrj.oa.iam.domain.UserGroup;
import com.lrj.oa.iam.infrastructure.mapper.UserGroupMapper;
import com.lrj.oa.org.api.OrgQueryApi;
import com.lrj.oa.org.api.dto.EmployeeView;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UserGroupServiceTest {
    private final UserGroupMapper mapper = mock(UserGroupMapper.class);
    private final OrgQueryApi org = mock(OrgQueryApi.class);
    private final IamInvalidationService invalidation = mock(IamInvalidationService.class);
    private final UserGroupService service = new UserGroupService(mapper, org, invalidation);

    @Test
    void addMembersDeduplicatesValidatesEmployeesAndInvalidatesGlobally() {
        UserGroup group = group(7, "ACTIVE");
        when(mapper.selectTenantGroup(1, 7)).thenReturn(group);
        when(org.getEmployeeByUserId(anyString())).thenReturn(mock(EmployeeView.class));
        OffsetDateTime until = OffsetDateTime.now().plusDays(1);

        int affected = service.addMembers(7,
                new IamCommands.AddGroupMembers(List.of("u1", " u1 ", "u2"), null, until));

        assertThat(affected).isEqualTo(2);
        verify(mapper, times(2)).upsertMember(eq(1L), eq(7L), anyString(), any(), eq(until), anyString());
        verify(invalidation).all("user-group-members-add#7");
    }

    @Test
    void disabledGroupAndInvalidPeriodAreRejected() {
        when(mapper.selectTenantGroup(1, 7)).thenReturn(group(7, "DISABLED"));
        assertThatThrownBy(() -> service.addMembers(7,
                new IamCommands.AddGroupMembers(List.of("u1"), null, null)))
                .isInstanceOf(BusinessException.class);

        when(mapper.selectTenantGroup(1, 7)).thenReturn(group(7, "ACTIVE"));
        OffsetDateTime now = OffsetDateTime.now();
        assertThatThrownBy(() -> service.addMembers(7,
                new IamCommands.AddGroupMembers(List.of("u1"), now, now)))
                .isInstanceOf(BusinessException.class);
    }

    private static UserGroup group(long id, String status) {
        UserGroup value = new UserGroup();
        value.setId(id);
        value.setTenantId(1L);
        value.setStatus(status);
        return value;
    }
}
