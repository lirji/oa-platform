package com.lrj.oa.iam.application;

import com.lrj.oa.iam.infrastructure.mapper.GrantMapper;
import com.lrj.oa.org.api.event.EmployeeAssignmentChangedEvent;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

class OrgChangeListenerTest {

    @Test
    void assignmentChangeUsesTenantEpochSoScopeContractionSurvivesLostPubSub() {
        IamInvalidationService invalidation = mock(IamInvalidationService.class);
        OrgChangeListener listener = new OrgChangeListener(mock(GrantMapper.class), invalidation);

        listener.onAssignmentChanged(EmployeeAssignmentChangedEvent.of("u1", "transfer"));

        verify(invalidation).all("assignment-changed#u1:transfer");
    }
}
