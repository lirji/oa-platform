package com.lrj.oa.iam.application;

import com.lrj.oa.common.api.ResultCode;
import com.lrj.oa.common.exception.BusinessException;
import com.lrj.oa.org.api.OrgQueryApi;
import com.lrj.oa.org.api.dto.EmployeeView;
import com.lrj.oa.org.api.event.EmployeeAssignmentChangedEvent;
import com.lrj.oa.org.api.event.EmployeeCreatedEvent;
import com.lrj.oa.org.api.event.OrgTreeChangedEvent;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class IdentityProjectionListenerTest {

    private final IdentityService identities = mock(IdentityService.class);
    private final OrgQueryApi org = mock(OrgQueryApi.class);
    private final IdentityProjectionListener listener = new IdentityProjectionListener(identities, org);

    @Test
    void createdEventProjectsUser() {
        listener.onCreated(new EmployeeCreatedEvent("u1", 9L, "张三", "FULL_TIME", "PROBATION", 3L, "/1/3/"));
        verify(identities).projectUser("u1", 9L, "张三", "FULL_TIME", "PROBATION", 3L, "/1/3/");
    }

    @Test
    void leaveDisablesIdentityWithoutReadingOrg() {
        listener.onAssignmentChanged(EmployeeAssignmentChangedEvent.left("u1"));
        verify(identities).disableByUserId("u1");
        verifyNoInteractions(org);
    }

    @Test
    void transferRefreshesProjectionFromOrgQuery() {
        EmployeeView view = new EmployeeView(9L, "u1", "E1", "张三", null, null, null,
                "FULL_TIME", "ACTIVE", null, 4L, "研发", "/1/4/", null, null);
        when(org.getEmployeeByUserId("u1")).thenReturn(view);
        listener.onAssignmentChanged(EmployeeAssignmentChangedEvent.of("u1", "transfer"));
        verify(identities).projectUser("u1", 9L, "张三", "FULL_TIME", "ACTIVE", 4L, "/1/4/");
    }

    @Test
    void seedEventBackfillsIdentities() {
        listener.onOrgTreeChanged(OrgTreeChangedEvent.bulk("seed"));
        verify(identities).backfillFromEmployees();
    }

    @Test
    void missingEmployeeIsIgnored() {
        when(org.getEmployeeByUserId("ghost"))
                .thenThrow(BusinessException.of(ResultCode.EMPLOYEE_NOT_FOUND));
        listener.onAssignmentChanged(EmployeeAssignmentChangedEvent.of("ghost", "transfer"));
        verifyNoInteractions(identities);
    }
}
