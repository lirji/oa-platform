package com.lrj.oa.iam.application;

import com.lrj.oa.common.cache.CacheInvalidation;
import com.lrj.oa.common.cache.InvalidationBus;
import com.lrj.oa.iam.infrastructure.cache.PermissionEngine;
import com.lrj.oa.iam.infrastructure.mapper.GrantMapper;
import com.lrj.oa.iam.infrastructure.mapper.PermVersionMapper;
import com.lrj.oa.org.api.event.EmployeeAssignmentChangedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import static org.mockito.Mockito.*;

class OrgChangeListenerTest {

    @Test
    @SuppressWarnings("unchecked")
    void assignmentChangeUsesGlobalEpochSoScopeContractionSurvivesLostPubSub() {
        PermVersionMapper versions = mock(PermVersionMapper.class);
        PermissionEngine engine = mock(PermissionEngine.class);
        InvalidationBus bus = mock(InvalidationBus.class);
        ObjectProvider<InvalidationBus> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(bus);
        OrgChangeListener listener = new OrgChangeListener(versions, engine, mock(GrantMapper.class), provider);

        listener.onAssignmentChanged(EmployeeAssignmentChangedEvent.of("u1", "transfer"));

        verify(versions).bumpEpoch();
        verify(engine).evictAllLocal();
        verify(bus).publish(CacheInvalidation.TYPE_PERM_EPOCH, "");
        verify(versions, never()).bumpUserVersion(anyString());
    }
}
