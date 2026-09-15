package com.lrj.oa.iam.application;

import com.lrj.oa.common.api.ResultCode;
import com.lrj.oa.common.exception.BusinessException;
import com.lrj.oa.iam.api.dto.AuthzDtos.WhoHasAccessResult;
import com.lrj.oa.iam.domain.Identity;
import com.lrj.oa.iam.domain.PermissionDelegation;
import com.lrj.oa.iam.domain.UserGroupMember;
import com.lrj.oa.iam.infrastructure.cache.PermissionEngine;
import com.lrj.oa.iam.infrastructure.mapper.IdentityMapper;
import com.lrj.oa.iam.infrastructure.mapper.PermissionDelegationMapper;
import com.lrj.oa.iam.infrastructure.mapper.UserGroupMapper;
import com.lrj.oa.iam.infrastructure.mapper.WhoHasAccessMapper;
import com.lrj.oa.iam.infrastructure.mapper.WhoHasAccessMapper.GrantHit;
import com.lrj.oa.org.api.OrgQueryApi;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

class WhoHasAccessServiceTest {

    private final PermissionCatalog catalog = mock(PermissionCatalog.class);
    private final WhoHasAccessMapper grants = mock(WhoHasAccessMapper.class);
    private final IdentityMapper identities = mock(IdentityMapper.class);
    private final UserGroupMapper groups = mock(UserGroupMapper.class);
    private final PermissionDelegationMapper delegations = mock(PermissionDelegationMapper.class);
    private final OrgQueryApi orgs = mock(OrgQueryApi.class);
    private final PermissionEngine engine = mock(PermissionEngine.class);
    private final WhoHasAccessService service = new WhoHasAccessService(
            catalog, grants, identities, groups, delegations, orgs, engine);

    @Test
    void missingQueryIsInvalid() {
        assertThatThrownBy(() -> service.query(null, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).resultCode())
                .isEqualTo(ResultCode.AUTHZ_CHECK_INVALID);
    }

    @Test
    void nonApiResourceIsInvalid() {
        assertThatThrownBy(() -> service.query(null, "DOC", "42"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).resultCode())
                .isEqualTo(ResultCode.AUTHZ_CHECK_INVALID);
        verify(grants, never()).selectActiveGrantsForPerm(anyLong(), anyInt(), any());
    }

    @Test
    void unknownPermReturnsEmptyKnownFalse() {
        when(catalog.idOf("oa:missing:perm")).thenReturn(-1);

        WhoHasAccessResult result = service.query("oa:missing:perm", null, null);

        assertThat(result.known()).isFalse();
        assertThat(result.items()).isEmpty();
        verify(grants, never()).selectActiveGrantsForPerm(anyLong(), anyInt(), any());
    }

    @Test
    void userGrantDistanceZeroIsRole() {
        when(catalog.idOf("oa:iam:view")).thenReturn(7);
        when(grants.selectActiveGrantsForPerm(eq(1L), eq(7), any()))
                .thenReturn(List.of(hit(11L, "USER", "u1", 0, true)));
        when(identities.selectByExternalKeys(eq(1L), eq("USER"), anyCollection()))
                .thenReturn(List.of(user("id-1", "u1")));
        when(delegations.selectActiveCoveringPerm(eq(1L), eq("oa:iam:view"), any())).thenReturn(List.of());

        WhoHasAccessResult result = service.query("oa:iam:view", null, null);

        assertThat(result.known()).isTrue();
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst().source()).isEqualTo("Role");
        assertThat(result.items().getFirst().identityId()).isEqualTo("id-1");
        assertThat(result.items().getFirst().grantId()).isEqualTo(11L);
    }

    @Test
    void userGrantDistancePositiveIsInherited() {
        when(catalog.idOf("oa:iam:view")).thenReturn(7);
        when(grants.selectActiveGrantsForPerm(eq(1L), eq(7), any()))
                .thenReturn(List.of(hit(12L, "USER", "u1", 2, true)));
        when(identities.selectByExternalKeys(eq(1L), eq("USER"), anyCollection()))
                .thenReturn(List.of(user("id-1", "u1")));
        when(delegations.selectActiveCoveringPerm(eq(1L), eq("oa:iam:view"), any())).thenReturn(List.of());

        WhoHasAccessResult result = service.query("oa:iam:view", null, null);

        assertThat(result.items()).extracting(h -> h.source()).containsExactly("Inherited");
    }

    @Test
    void groupGrantIsGroupSource() {
        when(catalog.idOf("oa:iam:view")).thenReturn(7);
        when(grants.selectActiveGrantsForPerm(eq(1L), eq(7), any()))
                .thenReturn(List.of(hit(13L, "USER_GROUP", "88", 0, true)));
        UserGroupMember member = new UserGroupMember();
        member.setUserId("u1");
        member.setValidFrom(OffsetDateTime.now().minusDays(1));
        when(groups.selectMembers(1L, 88L)).thenReturn(List.of(member));
        when(identities.selectByExternalKeys(eq(1L), eq("USER"), anyCollection()))
                .thenReturn(List.of(user("id-1", "u1")));
        when(delegations.selectActiveCoveringPerm(eq(1L), eq("oa:iam:view"), any())).thenReturn(List.of());

        WhoHasAccessResult result = service.query("oa:iam:view", null, null);

        assertThat(result.items()).extracting(h -> h.source()).containsExactly("Group");
        assertThat(result.items().getFirst().via()).contains("用户组 88");
    }

    @Test
    void orgGrantUsesPathPrefixNotIdList() {
        when(catalog.idOf("oa:iam:view")).thenReturn(7);
        when(grants.selectActiveGrantsForPerm(eq(1L), eq(7), any()))
                .thenReturn(List.of(hit(14L, "ORG_UNIT", "10", 0, true)));
        when(orgs.pathOf(10L)).thenReturn("/1/10/");
        when(identities.selectUsersByOrg(eq(1L), eq(10L), eq("/1/10/"), eq(true), anyInt()))
                .thenReturn(List.of(user("id-1", "u1")));
        when(delegations.selectActiveCoveringPerm(eq(1L), eq("oa:iam:view"), any())).thenReturn(List.of());

        WhoHasAccessResult result = service.query("oa:iam:view", null, null);

        assertThat(result.items()).extracting(h -> h.source()).containsExactly("Inherited");
        assertThat(result.items().getFirst().via()).contains("/1/10/");
        verify(orgs, never()).descendantIds(any());
        verify(identities).selectUsersByOrg(1L, 10L, "/1/10/", true, WhoHasAccessService.MAX_ITEMS);
    }

    @Test
    void orgGrantWithoutPathYieldsNoUsers() {
        when(catalog.idOf("oa:iam:view")).thenReturn(7);
        when(grants.selectActiveGrantsForPerm(eq(1L), eq(7), any()))
                .thenReturn(List.of(hit(14L, "ORG_UNIT", "10", 0, true)));
        when(orgs.pathOf(10L)).thenReturn(null);
        when(delegations.selectActiveCoveringPerm(eq(1L), eq("oa:iam:view"), any())).thenReturn(List.of());

        WhoHasAccessResult result = service.query("oa:iam:view", null, null);

        assertThat(result.items()).isEmpty();
        verify(identities, never()).selectUsersByOrg(anyLong(), any(), any(), anyBoolean(), anyInt());
    }

    @Test
    void orgGrantExactMatchDoesNotUsePrefix() {
        when(catalog.idOf("oa:iam:view")).thenReturn(7);
        when(grants.selectActiveGrantsForPerm(eq(1L), eq(7), any()))
                .thenReturn(List.of(hit(15L, "ORG_UNIT", "10", 0, false)));
        when(identities.selectUsersByOrg(eq(1L), eq(10L), isNull(), eq(false), anyInt()))
                .thenReturn(List.of(user("id-1", "u1")));
        when(delegations.selectActiveCoveringPerm(eq(1L), eq("oa:iam:view"), any())).thenReturn(List.of());

        WhoHasAccessResult result = service.query("oa:iam:view", null, null);

        assertThat(result.items()).hasSize(1);
        verify(orgs, never()).pathOf(any());
        verify(orgs, never()).descendantIds(any());
    }

    @Test
    void activeDelegationIsDelegationSource() {
        when(catalog.idOf("oa:iam:view")).thenReturn(7);
        when(grants.selectActiveGrantsForPerm(eq(1L), eq(7), any())).thenReturn(List.of());
        PermissionDelegation row = new PermissionDelegation();
        row.setId(42L);
        row.setDelegateeIdentityId("id-2");
        row.setDelegatorUserId("u1");
        when(delegations.selectActiveCoveringPerm(eq(1L), eq("oa:iam:view"), any())).thenReturn(List.of(row));
        when(identities.selectByIds(eq(1L), anyCollection())).thenReturn(List.of(user("id-2", "u2")));
        when(engine.has("u1", "oa:iam:view")).thenReturn(true);

        WhoHasAccessResult result = service.query("oa:iam:view", null, null);

        assertThat(result.items()).extracting(h -> h.source()).containsExactly("Delegation");
        assertThat(result.items().getFirst().grantId()).isEqualTo(42L);
    }

    @Test
    void delegationIgnoredWhenDelegatorLostPermission() {
        when(catalog.idOf("oa:iam:view")).thenReturn(7);
        when(grants.selectActiveGrantsForPerm(eq(1L), eq(7), any())).thenReturn(List.of());
        PermissionDelegation row = new PermissionDelegation();
        row.setId(42L);
        row.setDelegateeIdentityId("id-2");
        row.setDelegatorUserId("u1");
        when(delegations.selectActiveCoveringPerm(eq(1L), eq("oa:iam:view"), any())).thenReturn(List.of(row));
        when(identities.selectByIds(eq(1L), anyCollection())).thenReturn(List.of(user("id-2", "u2")));
        when(engine.has("u1", "oa:iam:view")).thenReturn(false);

        WhoHasAccessResult result = service.query("oa:iam:view", null, null);

        assertThat(result.items()).isEmpty();
    }

    @Test
    void resourceTypeApiUsesResourceIdAsPermCode() {
        when(catalog.idOf("oa:iam:view")).thenReturn(7);
        when(grants.selectActiveGrantsForPerm(eq(1L), eq(7), any())).thenReturn(List.of());
        when(delegations.selectActiveCoveringPerm(eq(1L), eq("oa:iam:view"), any())).thenReturn(List.of());

        WhoHasAccessResult result = service.query(null, "API", "oa:iam:view");

        assertThat(result.permCode()).isEqualTo("oa:iam:view");
        assertThat(result.known()).isTrue();
        verify(catalog).idOf("oa:iam:view");
        verify(engine, never()).has(anyString(), anyString());
    }

    private static GrantHit hit(long grantId, String subjectType, String subjectId,
                                int distance, boolean includeDescendants) {
        return new GrantHit(grantId, subjectType, subjectId, 3L, "EMPLOYEE", "员工",
                distance, "PERMANENT", includeDescendants);
    }

    private static Identity user(String id, String userId) {
        Identity row = new Identity();
        row.setId(id);
        row.setTenantId(1L);
        row.setIdentityType("USER");
        row.setDisplayName(userId);
        row.setExternalKey(userId);
        row.setStatus("ACTIVE");
        return row;
    }
}
