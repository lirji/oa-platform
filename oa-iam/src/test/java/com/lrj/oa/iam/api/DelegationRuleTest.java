package com.lrj.oa.iam.api;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DelegationRuleTest {

    @Test
    void processAndRoleScopesFailClosedOutsideConfiguredRange() {
        var process = new DelegationRule(1, "owner", "BY_PROCESS_KEY", Set.of("leave-v1"), Set.of());
        assertThat(process.matches("leave-v1", null)).isTrue();
        assertThat(process.matches("expense-v1", null)).isFalse();

        var role = new DelegationRule(2, "owner", "BY_ROLE", Set.of(), Set.of(7L));
        assertThat(role.matches("anything", "role:7")).isTrue();
        assertThat(role.matches("anything", "8")).isFalse();
        assertThat(role.matches("anything", "manager")).isFalse();
    }
}
