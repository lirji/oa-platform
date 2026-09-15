package com.lrj.oa.iam.application;

import com.lrj.oa.common.api.ResultCode;
import com.lrj.oa.common.exception.BusinessException;
import com.lrj.oa.iam.api.dto.IdentityDtos.ChangeIdentityStatus;
import com.lrj.oa.iam.api.dto.IdentityDtos.CreateIdentity;
import com.lrj.oa.iam.api.dto.IdentityDtos.IdentityView;
import com.lrj.oa.iam.api.dto.IdentityDtos.ReplaceIdentityLabels;
import com.lrj.oa.iam.api.dto.IdentityDtos.UpdateIdentity;
import com.lrj.oa.iam.domain.Identity;
import com.lrj.oa.iam.infrastructure.mapper.IdentityMapper;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

class IdentityServiceTest {

    private final IdentityMapper mapper = mock(IdentityMapper.class);
    private final CredentialService credentials = mock(CredentialService.class);
    private final IdentityService service = new IdentityService(mapper, credentials);

    @Test
    void creatingUserIdentityIsRejected() {
        assertThatThrownBy(() -> service.create(new CreateIdentity(
                "USER", "张三", "sub-1", null, null, List.of())))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).resultCode())
                .isEqualTo(ResultCode.BAD_REQUEST);
        verify(mapper, never()).insert(any());
    }

    @Test
    void creatingServiceAccountPersistsAndReturnsView() {
        when(mapper.insert(any(Identity.class))).thenAnswer(inv -> {
            Identity row = inv.getArgument(0);
            when(mapper.selectById(eq(1L), eq(row.getId()))).thenReturn(copy(row));
            when(mapper.selectLabels(row.getId())).thenReturn(List.of("SERVICE_ACCOUNT"));
            return 1;
        });

        IdentityView view = service.create(new CreateIdentity(
                "SERVICE_ACCOUNT", "报表机器人", "sa-report", null, null, null));

        assertThat(view.identityType()).isEqualTo("SERVICE_ACCOUNT");
        assertThat(view.externalKey()).isEqualTo("sa-report");
        assertThat(view.status()).isEqualTo("ACTIVE");
        assertThat(view.labels()).containsExactly("SERVICE_ACCOUNT");
        verify(mapper).insertLabel(anyString(), eq("SERVICE_ACCOUNT"));
    }

    @Test
    void duplicateExternalKeyIsConflict() {
        when(mapper.insert(any(Identity.class))).thenThrow(new DuplicateKeyException("uk"));
        assertThatThrownBy(() -> service.create(new CreateIdentity(
                "SERVICE_ACCOUNT", "报表机器人", "sa-report", null, null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).resultCode())
                .isEqualTo(ResultCode.CONFLICT);
    }

    @Test
    void agentWithoutOwnerIsRejected() {
        assertThatThrownBy(() -> service.create(new CreateIdentity(
                "AGENT", "助手", "agent-1", null, null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).resultCode())
                .isEqualTo(ResultCode.NHI_OWNER_REQUIRED);
    }

    @Test
    void invalidStatusTransitionConflicts() {
        Identity row = user("id-1", "ACTIVE", 3);
        when(mapper.selectById(1L, "id-1")).thenReturn(row);

        assertThatThrownBy(() -> service.changeStatus("id-1", new ChangeIdentityStatus("DELETED", 3)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).resultCode())
                .isEqualTo(ResultCode.IDENTITY_STATUS_CONFLICT);
    }

    @Test
    void staleVersionOnUpdateConflicts() {
        Identity row = user("id-1", "ACTIVE", 2);
        when(mapper.selectById(1L, "id-1")).thenReturn(row);
        when(mapper.updateMutable(any())).thenReturn(0);

        assertThatThrownBy(() -> service.update("id-1", new UpdateIdentity("新名字", null, null, 2)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).resultCode())
                .isEqualTo(ResultCode.CONFLICT);
    }

    @Test
    void projectUserInsertsThenUpdatesIdempotently() {
        when(mapper.selectByExternalKey(1L, "USER", "u1")).thenReturn(null, user("id-9", "ACTIVE", 0));
        when(mapper.insert(any(Identity.class))).thenReturn(1);

        service.projectUser("u1", 88L, "李四", "FULL_TIME", "ACTIVE", 12L, "/1/12/");
        verify(mapper).insert(any(Identity.class));
        verify(mapper).insertLabel(anyString(), eq("EMPLOYEE"));

        service.projectUser("u1", 88L, "李四", "FULL_TIME", "ACTIVE", 13L, "/1/13/");
        verify(mapper).updateProjection(eq(1L), eq("id-9"), eq("李四"), eq("ACTIVE"), eq(13L), eq("/1/13/"), any());
    }

    @Test
    void leaveDisablesExistingUserIdentity() {
        Identity row = user("id-1", "ACTIVE", 1);
        when(mapper.selectByExternalKey(1L, "USER", "u1")).thenReturn(row);

        service.disableByUserId("u1");

        verify(mapper).updateProjection(eq(1L), eq("id-1"), eq("张三"), eq("DISABLED"), isNull(), isNull(), any());
        verify(credentials).revokeAllOfIdentity("id-1", "u1");
    }

    @Test
    void unknownLabelRejected() {
        Identity row = user("id-1", "ACTIVE", 1);
        when(mapper.selectById(1L, "id-1")).thenReturn(row);

        assertThatThrownBy(() -> service.replaceLabels("id-1",
                new ReplaceIdentityLabels(List.of("NOT_A_LABEL"), 1)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).resultCode())
                .isEqualTo(ResultCode.BAD_REQUEST);
    }

    @Test
    void deletedIdentityIsNotFound() {
        Identity row = user("id-1", "DELETED", 4);
        when(mapper.selectById(1L, "id-1")).thenReturn(row);
        assertThatThrownBy(() -> service.get("id-1"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).resultCode())
                .isEqualTo(ResultCode.IDENTITY_NOT_FOUND);
    }

    @Test
    void listUsesCursorAndHasMore() {
        Identity a = user("a", "ACTIVE", 0);
        a.setSeq(10L);
        Identity b = user("b", "ACTIVE", 0);
        b.setSeq(11L);
        Identity extra = user("c", "ACTIVE", 0);
        extra.setSeq(12L);
        when(mapper.selectPage(eq(1L), isNull(), isNull(), isNull(), isNull(), eq(3)))
                .thenReturn(List.of(a, b, extra));
        when(mapper.selectLabelsByIds(any())).thenReturn(List.of());

        var page = service.list(null, null, null, null, 2);
        assertThat(page.items()).hasSize(2);
        assertThat(page.hasMore()).isTrue();
        assertThat(page.nextCursor()).isEqualTo("11");
    }

    @Test
    void upsertSyncedNhiIsIdempotentOnSameKey() {
        when(mapper.selectByExternalKey(1L, "SERVICE_ACCOUNT", "mock:sa-directory"))
                .thenReturn(null, sa("sa-1"));
        when(mapper.insert(any(Identity.class))).thenReturn(1);

        assertThat(service.upsertSyncedNhi("SERVICE_ACCOUNT", "mock:sa-directory", "目录同步", "MOCK", true))
                .isEqualTo(IdentityService.SyncOutcome.CREATED);

        when(mapper.selectByExternalKey(1L, "SERVICE_ACCOUNT", "mock:sa-directory")).thenReturn(sa("sa-1"));
        when(mapper.updateProjection(eq(1L), eq("sa-1"), eq("目录同步"), eq("ACTIVE"), isNull(), isNull(), any()))
                .thenReturn(1);
        assertThat(service.upsertSyncedNhi("SERVICE_ACCOUNT", "mock:sa-directory", "目录同步", "MOCK", true))
                .isEqualTo(IdentityService.SyncOutcome.UPDATED);
        verify(mapper, org.mockito.Mockito.times(1)).insert(any(Identity.class));
    }

    @Test
    void incrementalUpsertSkipsExisting() {
        when(mapper.selectByExternalKey(1L, "SERVICE_ACCOUNT", "mock:sa-directory")).thenReturn(sa("sa-1"));
        assertThat(service.upsertSyncedNhi("SERVICE_ACCOUNT", "mock:sa-directory", "目录同步", "MOCK", false))
                .isEqualTo(IdentityService.SyncOutcome.SKIPPED);
        verify(mapper, never()).insert(any());
        verify(mapper, never()).updateProjection(any(Long.class), anyString(), anyString(), anyString(), any(), any(), any());
    }

    private static Identity sa(String id) {
        Identity row = user(id, "ACTIVE", 0);
        row.setIdentityType("SERVICE_ACCOUNT");
        row.setExternalKey("mock:sa-directory");
        row.setSource("MOCK");
        return row;
    }

    private static Identity user(String id, String status, int version) {
        Identity row = new Identity();
        row.setId(id);
        row.setTenantId(1L);
        row.setIdentityType("USER");
        row.setDisplayName("张三");
        row.setSource("CASDOOR");
        row.setStatus(status);
        row.setExternalKey("u1");
        row.setVersion(version);
        row.setCreatedAt(OffsetDateTime.now());
        row.setUpdatedAt(OffsetDateTime.now());
        return row;
    }

    private static Identity copy(Identity src) {
        Identity row = user(src.getId(), src.getStatus(), src.getVersion());
        row.setIdentityType(src.getIdentityType());
        row.setDisplayName(src.getDisplayName());
        row.setSource(src.getSource());
        row.setExternalKey(src.getExternalKey());
        row.setRiskLevel(src.getRiskLevel());
        return row;
    }
}
