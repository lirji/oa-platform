package com.lrj.oa.iam.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IdentityStatusTest {

    @Test
    void activeCannotJumpToDeleted() {
        assertThat(IdentityStatus.ACTIVE.canTransitTo(IdentityStatus.DELETED)).isFalse();
        assertThat(IdentityStatus.DISABLED.canTransitTo(IdentityStatus.DELETED)).isTrue();
        assertThat(IdentityStatus.ACTIVE.canTransitTo(IdentityStatus.SUSPENDED)).isTrue();
    }
}
