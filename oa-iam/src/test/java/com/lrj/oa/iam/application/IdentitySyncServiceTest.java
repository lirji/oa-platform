package com.lrj.oa.iam.application;

import com.lrj.oa.common.api.ResultCode;
import com.lrj.oa.common.exception.BusinessException;
import com.lrj.oa.iam.api.dto.IdentitySyncDtos.CreateSyncJob;
import com.lrj.oa.iam.api.dto.IdentitySyncDtos.SyncJobView;
import com.lrj.oa.iam.application.IdentityService.SyncOutcome;
import com.lrj.oa.iam.application.IdentityService.SyncWriteCounts;
import com.lrj.oa.iam.domain.IdentitySyncJob;
import com.lrj.oa.iam.infrastructure.mapper.IdentitySyncJobMapper;
import com.lrj.oa.security.context.UserContext;
import com.lrj.oa.security.context.UserContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

class IdentitySyncServiceTest {

    private final IdentitySyncJobMapper jobs = mock(IdentitySyncJobMapper.class);
    private final IdentityService identities = mock(IdentityService.class);
    private final IdentitySyncService service = new IdentitySyncService(jobs, identities);

    @BeforeEach
    void login() {
        UserContextHolder.set(new UserContext("admin", "admin", 1L, 2L, "/1/2/", 1L));
    }

    @AfterEach
    void clear() {
        UserContextHolder.clear();
    }

    @Test
    void mockFullCreatesThenUpdatesSameKeys() {
        when(jobs.selectByCommandId(1L, "cmd-1")).thenReturn(null);
        when(jobs.insert(any())).thenAnswer(inv -> {
            IdentitySyncJob row = inv.getArgument(0);
            row.setId(8L);
            return 1;
        });
        when(identities.upsertSyncedNhi(anyString(), anyString(), anyString(), eq("MOCK"), eq(true)))
                .thenReturn(SyncOutcome.CREATED, SyncOutcome.CREATED, SyncOutcome.CREATED);
        when(jobs.selectById(1L, 8L)).thenAnswer(inv -> stored(8L, "SUCCEEDED", 3, 0, 0));

        SyncJobView first = service.start(new CreateSyncJob("MOCK", "FULL", "cmd-1"));
        assertThat(first.status()).isEqualTo("SUCCEEDED");
        assertThat(first.createdCount()).isEqualTo(3);
        verify(identities, times(3)).upsertSyncedNhi(anyString(), anyString(), anyString(), eq("MOCK"), eq(true));

        when(jobs.selectByCommandId(1L, "cmd-1")).thenReturn(stored(8L, "SUCCEEDED", 3, 0, 0));
        SyncJobView again = service.start(new CreateSyncJob("MOCK", "FULL", "cmd-1"));
        assertThat(again.id()).isEqualTo(8L);
        verify(identities, times(3)).upsertSyncedNhi(anyString(), anyString(), anyString(), eq("MOCK"), eq(true));
    }

    @Test
    void mockIncrementalSkipsExisting() {
        when(jobs.selectByCommandId(anyLong(), any())).thenReturn(null);
        when(jobs.insert(any())).thenAnswer(inv -> {
            IdentitySyncJob row = inv.getArgument(0);
            row.setId(9L);
            return 1;
        });
        when(identities.upsertSyncedNhi(anyString(), anyString(), anyString(), eq("MOCK"), eq(false)))
                .thenReturn(SyncOutcome.SKIPPED);
        when(jobs.selectById(1L, 9L)).thenReturn(stored(9L, "SUCCEEDED", 0, 0, 3));

        SyncJobView view = service.start(new CreateSyncJob("MOCK", "INCREMENTAL", null));
        assertThat(view.skippedCount()).isEqualTo(3);
        verify(identities, times(3)).upsertSyncedNhi(anyString(), anyString(), anyString(), eq("MOCK"), eq(false));
    }

    @Test
    void internalFullRefreshesThenBackfills() {
        when(jobs.selectByCommandId(anyLong(), any())).thenReturn(null);
        when(jobs.insert(any())).thenAnswer(inv -> {
            IdentitySyncJob row = inv.getArgument(0);
            row.setId(2L);
            return 1;
        });
        when(identities.syncFromEmployees(true)).thenReturn(new SyncWriteCounts(4, 10, 0));
        when(jobs.selectById(1L, 2L)).thenReturn(stored(2L, "SUCCEEDED", 4, 10, 0));

        SyncJobView view = service.start(new CreateSyncJob("INTERNAL", "FULL", "int-1"));
        assertThat(view.createdCount()).isEqualTo(4);
        assertThat(view.updatedCount()).isEqualTo(10);
        verify(identities).syncFromEmployees(true);
        verify(identities, never()).upsertSyncedNhi(anyString(), anyString(), anyString(), anyString(), anyBoolean());
    }

    @Test
    void ldapIsRejected() {
        assertThatThrownBy(() -> service.start(new CreateSyncJob("LDAP", "FULL", "x")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).resultCode())
                .isEqualTo(ResultCode.BAD_REQUEST);
        verify(jobs, never()).insert(any());
    }

    @Test
    void missingJobIsNotFound() {
        when(jobs.selectById(1L, 99L)).thenReturn(null);
        assertThatThrownBy(() -> service.get(99L))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).resultCode())
                .isEqualTo(ResultCode.NOT_FOUND);
    }

    private static IdentitySyncJob stored(long id, String status, int created, int updated, int skipped) {
        IdentitySyncJob row = new IdentitySyncJob();
        row.setId(id);
        row.setTenantId(1L);
        row.setCommandId("cmd-1");
        row.setConnector("MOCK");
        row.setMode("FULL");
        row.setStatus(status);
        row.setCreatedCount(created);
        row.setUpdatedCount(updated);
        row.setSkippedCount(skipped);
        row.setCreatedBy("admin");
        return row;
    }
}
