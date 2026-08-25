package com.lrj.oa.iam.infrastructure.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lrj.oa.iam.domain.AbacBranch;
import com.lrj.oa.iam.domain.PermissionSnapshot;
import com.lrj.oa.security.model.DataScopeRule;
import com.lrj.oa.security.model.DataScopeType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SnapshotCodecAbacTest {
    @Test
    void roundTripPreservesAbacModeUnconditionalBitsAndBranches() throws Exception {
        PermissionSnapshot source = PermissionSnapshot.builder("u1")
                .epoch(2).userVersion(3).abacEnabled(true)
                .addPerm(7, "oa:test:conditional").addPerm(8, "oa:test:plain")
                .scope(4, 7, "test", new DataScopeRule(DataScopeType.ORG_AND_SUB,
                        List.of("/1/23/"), Set.of(23L), "u1"))
                .scope(5, 8, "test", DataScopeRule.all())
                .markAbacUnconditional(8, 5)
                .addAbacBranch(7, new AbacBranch(4,
                        List.of(new AbacBranch.Condition(11, "#p0.amount <= 5000", "小额"))))
                .expireAt(System.currentTimeMillis() + 10_000).build();

        SnapshotCodec codec = new SnapshotCodec(new ObjectMapper());
        PermissionSnapshot decoded = codec.decode(codec.encode(source));

        assertThat(decoded.abacEnabled()).isTrue();
        assertThat(decoded.scopeOfPermission(7).type()).isEqualTo(DataScopeType.ORG_AND_SUB);
        assertThat(decoded.scopeOfPermission(7).pathPrefixes()).containsExactly("/1/23/");
        assertThat(decoded.scopeOfPermission(8).type()).isEqualTo(DataScopeType.ALL);
        assertThat(decoded.abacUnconditional(8)).isTrue();
        assertThat(decoded.abacUnconditionalRoles(8)).containsExactly(5L);
        assertThat(decoded.scopeOfPermissionRoles(7, Set.of(4L)).orgIds()).containsExactly(23L);
        assertThat(decoded.abacBranches().get(7)).containsExactly(
                new AbacBranch(4, List.of(new AbacBranch.Condition(11, "#p0.amount <= 5000", "小额"))));
    }

    @Test
    void rejectsLegacySnapshotsWithoutSchemaVersion() {
        SnapshotCodec codec = new SnapshotCodec(new ObjectMapper());
        assertThatThrownBy(() -> codec.decode("{}"))
                .isInstanceOf(java.io.IOException.class)
                .hasMessageContaining("协议版本不兼容");
    }
}
