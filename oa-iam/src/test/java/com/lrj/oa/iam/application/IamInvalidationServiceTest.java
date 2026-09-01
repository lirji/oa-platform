package com.lrj.oa.iam.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lrj.oa.common.cache.CacheInvalidation;
import com.lrj.oa.common.cache.InvalidationBus;
import com.lrj.oa.common.context.TenantContext;
import com.lrj.oa.iam.domain.PermissionEpochInvalidation;
import com.lrj.oa.iam.infrastructure.cache.PermissionEngine;
import com.lrj.oa.iam.infrastructure.mapper.IamOutboxMapper;
import com.lrj.oa.iam.infrastructure.mapper.PermVersionMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class IamInvalidationServiceTest {
    private final PermVersionMapper versions = mock(PermVersionMapper.class);
    private final PermissionEngine engine = mock(PermissionEngine.class);
    private final InvalidationBus bus = mock(InvalidationBus.class);
    private final IamOutboxMapper outbox = mock(IamOutboxMapper.class);

    @AfterEach
    void cleanup() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
        TenantContext.clear();
    }

    @Test
    @SuppressWarnings("unchecked")
    void roleEventAndEpochAreWrittenBeforeCommitButExternalInvalidationWaitsForCommit() throws Exception {
        TenantContext.set(7L);
        when(versions.bumpEpoch(7L)).thenReturn(12L);
        ObjectProvider<InvalidationBus> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(bus);
        IamInvalidationService service = new IamInvalidationService(
                versions, engine, provider, outbox, new ObjectMapper());

        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
        service.roleChanged(19L, "PERMISSIONS_REPLACED", "role-permissions#19");

        verify(versions).bumpEpoch(7L);
        ArgumentCaptor<String> eventId = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(outbox).append(eventId.capture(), eq(7L), eq(IamOutboxMapper.ROLE_CHANGED_TOPIC),
                eq("7:19"), payload.capture());
        assertThat(new ObjectMapper().readTree(payload.getValue()).path("epoch").asLong()).isEqualTo(12L);
        verifyNoInteractions(engine, bus);

        for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
            sync.afterCommit();
        }

        verify(engine).evictTenant(7L, 12L);
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(bus).publish(eq(CacheInvalidation.TYPE_PERM_EPOCH), key.capture());
        PermissionEpochInvalidation decoded = PermissionEpochInvalidation.decode(key.getValue()).orElseThrow();
        assertThat(decoded.tenantId()).isEqualTo(7L);
        assertThat(decoded.epoch()).isEqualTo(12L);
        assertThat(decoded.eventId()).isEqualTo(eventId.getValue());
    }

    @Test
    @SuppressWarnings("unchecked")
    void rollbackDoesNotEvictOrPublish() {
        when(versions.bumpEpoch(1L)).thenReturn(3L);
        ObjectProvider<InvalidationBus> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(bus);
        IamInvalidationService service = new IamInvalidationService(
                versions, engine, provider, outbox, new ObjectMapper());

        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
        service.all("will-rollback");
        for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
            sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        }

        verifyNoInteractions(engine, bus);
    }
}
