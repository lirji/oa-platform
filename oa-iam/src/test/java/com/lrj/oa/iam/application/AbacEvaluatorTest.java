package com.lrj.oa.iam.application;

import com.lrj.oa.common.exception.BusinessException;
import com.lrj.oa.iam.domain.AbacBranch;
import com.lrj.oa.iam.domain.PermissionSnapshot;
import com.lrj.oa.security.context.UserContext;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class AbacEvaluatorTest {
    private final PermissionCatalog catalog = mock(PermissionCatalog.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<MeterRegistry> meters = mock(ObjectProvider.class);
    private final AbacEvaluator evaluator = new AbacEvaluator(catalog, meters, true);
    private final Method method;
    private final UserContext user = new UserContext("u1", "Alice", 1L, 10L, "/1/10/", 1L);

    AbacEvaluatorTest() throws Exception {
        method = Fixture.class.getMethod("submit", Request.class);
        when(catalog.idOf("oa:test")).thenReturn(9);
    }

    @Test
    void branchConditionsAreAndAndIndependentBranchesAreOr() {
        PermissionSnapshot snapshot = PermissionSnapshot.builder("u1").abacEnabled(true)
                .addPerm(9, "oa:test")
                .addAbacBranch(9, branch(1,
                        condition(1, "#p0.amount <= 5000"),
                        condition(2, "#user.primaryOrgId == 999")))
                .addAbacBranch(9, branch(2,
                        condition(3, "#p0.amount <= 5000"),
                        condition(4, "#user.primaryOrgId == 10")))
                .build();

        assertThat(evaluator.allowed(snapshot, "oa:test", method,
                new Object[]{new Request(new BigDecimal("4999"))}, user)).isTrue();
        assertThat(evaluator.allowed(snapshot, "oa:test", method,
                new Object[]{new Request(new BigDecimal("5001"))}, user)).isFalse();
    }

    @Test
    void unconditionalAuthorizationSourceBypassesConditionalSources() {
        PermissionSnapshot snapshot = PermissionSnapshot.builder("u1").abacEnabled(true)
                .addPerm(9, "oa:test").markAbacUnconditional(9)
                .addAbacBranch(9, branch(1, condition(1, "false"))).build();
        assertThat(evaluator.allowed(snapshot, "oa:test", method,
                new Object[]{new Request(BigDecimal.TEN)}, user)).isTrue();
    }

    @Test
    void missingVariableAndTypeErrorFailClosed() {
        PermissionSnapshot snapshot = PermissionSnapshot.builder("u1").abacEnabled(true)
                .addPerm(9, "oa:test")
                .addAbacBranch(9, branch(1, condition(1, "#missing.amount <= 1"))).build();
        assertThat(evaluator.allowed(snapshot, "oa:test", method,
                new Object[]{new Request(BigDecimal.ONE)}, user)).isFalse();
    }

    @Test
    void unknownPermissionAndMissingBranchFailClosed() {
        PermissionSnapshot snapshot = PermissionSnapshot.builder("u1").abacEnabled(true)
                .addPerm(9, "oa:test").build();
        assertThat(evaluator.allowed(snapshot, "oa:test", method,
                new Object[]{new Request(BigDecimal.ONE)}, user)).isFalse();
        assertThat(evaluator.allowed(snapshot, "oa:unknown", method,
                new Object[]{new Request(BigDecimal.ONE)}, user)).isFalse();
    }

    @Test
    void dangerousSpelFeaturesAreRejectedAtWriteTime() {
        List<String> malicious = List.of(
                "T(java.lang.Runtime).getRuntime().exec('id')",
                "new java.lang.ProcessBuilder('id').start()",
                "@environment.getProperty('HOME')",
                "#p0.getClass().classLoader",
                "#p0.amount = 1",
                "#p0.toString() == 'x'");
        for (String expression : malicious) {
            assertThatThrownBy(() -> evaluator.validate(expression))
                    .as(expression).isInstanceOf(BusinessException.class);
        }
        evaluator.validate("(#p0.amount <= 5000 and #user.tenantId == 1) or #p0.amount == null");
    }

    private static AbacBranch branch(long role, AbacBranch.Condition... conditions) {
        return new AbacBranch(role, List.of(conditions));
    }
    private static AbacBranch.Condition condition(long id, String expression) {
        return new AbacBranch.Condition(id, expression, null);
    }

    public record Request(BigDecimal amount) {}
    public static final class Fixture { public void submit(Request request) {} }
}
