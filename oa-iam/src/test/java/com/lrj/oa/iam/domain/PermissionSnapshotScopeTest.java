package com.lrj.oa.iam.domain;

import com.lrj.oa.security.model.DataScopeRule;
import com.lrj.oa.security.model.DataScopeType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PermissionSnapshotScopeTest {

    @Test
    void scopesAreWidenedPerPermissionNotAcrossTheWholeModule() {
        DataScopeRule orgOnly = new DataScopeRule(
                DataScopeType.ORG_AND_SUB, List.of("/1/23/"), Set.of(23L), "u1");
        PermissionSnapshot snapshot = PermissionSnapshot.builder("u1")
                .addPerm(7, "oa:asset:read")
                .addPerm(8, "oa:vehicle:book")
                .scope(7, "admin", orgOnly)
                .scope(8, "admin", DataScopeRule.all())
                .build();

        assertThat(snapshot.scopeOfPermission(7)).isEqualTo(orgOnly);
        assertThat(snapshot.scopeOfPermission(8).type()).isEqualTo(DataScopeType.ALL);
        assertThat(snapshot.scopeOf("admin").type()).as("legacy module view remains widest during migration")
                .isEqualTo(DataScopeType.ALL);
        assertThat(snapshot.scopeOfPermission(999).type()).isEqualTo(DataScopeType.NONE);
    }

    @Test
    void samePermissionCombinesIndependentOrganizationGrants() {
        PermissionSnapshot snapshot = PermissionSnapshot.builder("u1")
                .addPerm(7, "oa:employee:view")
                .scope(7, "org", new DataScopeRule(
                        DataScopeType.ORG_AND_SUB, List.of("/1/2/"), Set.of(2L), "u1"))
                .scope(7, "org", new DataScopeRule(
                        DataScopeType.ORG_AND_SUB, List.of("/1/5/"), Set.of(5L), "u1"))
                .build();

        assertThat(snapshot.scopeOfPermission(7).pathPrefixes()).containsExactlyInAnyOrder("/1/2/", "/1/5/");
        assertThat(snapshot.scopeOfPermission(7).orgIds()).containsExactlyInAnyOrder(2L, 5L);
    }

    @Test
    void mixedScopeTypesAreUnionedInsteadOfDroppingTheNarrowerGrant() {
        DataScopeRule result = PermissionSnapshot.widen(
                new DataScopeRule(DataScopeType.SELF, List.of(), Set.of(), "u1"),
                new DataScopeRule(DataScopeType.ORG_AND_SUB, List.of("/1/2/"), Set.of(2L), "u1"));

        assertThat(result.type()).isEqualTo(DataScopeType.CUSTOM);
        assertThat(result.pathPrefixes()).containsExactly("/1/2/");
        assertThat(result.orgIds()).containsExactly(2L);
        assertThat(result.selfUserId()).isEqualTo("u1");
    }

    @Test
    void allAndNoneRemainAbsorbingAndIdentityElements() {
        DataScopeRule org = new DataScopeRule(DataScopeType.ORG, List.of(), Set.of(2L), "u1");
        assertThat(PermissionSnapshot.widen(DataScopeRule.none(), org)).isEqualTo(org);
        assertThat(PermissionSnapshot.widen(org, DataScopeRule.all()).type()).isEqualTo(DataScopeType.ALL);
    }
}
