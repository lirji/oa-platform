package com.lrj.oa.iam.aspect;

import com.lrj.oa.iam.application.AbacEvaluator;
import com.lrj.oa.iam.application.PermissionCatalog;
import com.lrj.oa.iam.infrastructure.cache.PermissionEngine;
import com.lrj.oa.security.annotation.RequiresPerm;
import com.lrj.oa.security.context.UserContext;
import com.lrj.oa.security.context.UserContextHolder;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RequiresPermAspectAuthorizationContextTest {

    @AfterEach
    void clear() {
        AuthorizationContext.clear();
        UserContextHolder.clear();
    }

    @Test
    void or_rule_exposes_only_the_permission_actually_held() throws Throwable {
        PermissionEngine engine = mock(PermissionEngine.class);
        PermissionCatalog catalog = mock(PermissionCatalog.class);
        AbacEvaluator abac = mock(AbacEvaluator.class);
        when(abac.enabled()).thenReturn(false);
        when(engine.has("u-1", "held")).thenReturn(true);
        when(engine.has("u-1", "missing")).thenReturn(false);
        UserContextHolder.set(new UserContext("u-1", "alice", 1L, 10L, "/1/10/", 1L));

        Method method = Fixture.class.getDeclaredMethod("orEndpoint");
        MethodSignature signature = mock(MethodSignature.class);
        when(signature.getMethod()).thenReturn(method);
        ProceedingJoinPoint jp = mock(ProceedingJoinPoint.class);
        when(jp.getSignature()).thenReturn(signature);
        when(jp.getTarget()).thenReturn(new Fixture());
        when(jp.getArgs()).thenReturn(new Object[0]);
        when(jp.proceed()).thenAnswer(invocation -> {
            assertThat(AuthorizationContext.allows("held")).isTrue();
            assertThat(AuthorizationContext.allows("missing")).isFalse();
            return "ok";
        });

        Object result = new RequiresPermAspect(engine, catalog, abac, true).check(jp);

        assertThat(result).isEqualTo("ok");
        assertThat(AuthorizationContext.allows("held")).isFalse();
    }

    static class Fixture {
        @RequiresPerm(value = {"held", "missing"}, logical = RequiresPerm.Logical.OR)
        String orEndpoint() { return "ok"; }
    }
}
