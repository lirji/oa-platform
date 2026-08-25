package com.lrj.oa.iam.application;

import com.lrj.oa.security.context.UserContext;
import com.lrj.oa.security.context.UserContextHolder;
import com.lrj.oa.security.model.DataScopeRule;
import com.lrj.oa.security.model.DataScopeType;
import com.lrj.oa.security.port.PermissionChecker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PermissionDataScopeAccessCheckerTest {
    private final PermissionChecker permissions = mock(PermissionChecker.class);
    private final PermissionDataScopeAccessChecker checker = new PermissionDataScopeAccessChecker(permissions);

    @AfterEach
    void clear() {
        UserContextHolder.clear();
    }

    @Test
    void customExactAndDescendantScopesDoNotCrossRegions() {
        UserContextHolder.set(new UserContext("manager", "M", 1L, 10L, "/1/10/", 1L));
        when(permissions.dataScopeForPermission("manager", "oa:employee:update"))
                .thenReturn(new DataScopeRule(DataScopeType.CUSTOM,
                        List.of("/1/10/"), Set.of(10L), "manager"));

        assertThat(checker.allows("oa:employee:update", 11L, "/1/10/11/", "east-user")).isTrue();
        assertThat(checker.allows("oa:employee:update", 20L, "/1/20/", "south-user")).isFalse();
    }

    @Test
    void customWithoutDescendantsOnlyAllowsExactOrgAndSelfOnlyAllowsOwner() {
        UserContextHolder.set(new UserContext("manager", "M", 1L, 10L, "/1/10/", 1L));
        when(permissions.dataScopeForPermission("manager", "oa:employee:view"))
                .thenReturn(new DataScopeRule(DataScopeType.CUSTOM, List.of(), Set.of(10L), "manager"));
        assertThat(checker.allows("oa:employee:view", 10L, "/1/10/", "u1")).isTrue();
        assertThat(checker.allows("oa:employee:view", 11L, "/1/10/11/", "u2")).isFalse();

        when(permissions.dataScopeForPermission("manager", "oa:self"))
                .thenReturn(new DataScopeRule(DataScopeType.SELF, List.of(), Set.of(), "manager"));
        assertThat(checker.allows("oa:self", 20L, "/1/20/", "manager")).isTrue();
        assertThat(checker.allows("oa:self", 10L, "/1/10/", "someone-else")).isFalse();
    }
}
