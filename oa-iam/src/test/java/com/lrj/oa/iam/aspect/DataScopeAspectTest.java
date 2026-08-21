package com.lrj.oa.iam.aspect;

import com.lrj.oa.common.api.ResultCode;
import com.lrj.oa.common.exception.BusinessException;
import com.lrj.oa.iam.infrastructure.cache.PermissionEngine;
import com.lrj.oa.iam.infrastructure.datascope.DataScopeContext;
import com.lrj.oa.iam.infrastructure.datascope.DataScopeMetrics;
import com.lrj.oa.iam.infrastructure.datascope.OaDataPermissionHandler;
import com.lrj.oa.security.annotation.DataScope;
import com.lrj.oa.security.context.UserContext;
import com.lrj.oa.security.context.UserContextHolder;
import com.lrj.oa.security.model.DataScopeRule;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.sf.jsqlparser.schema.Table;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.lang.reflect.Method;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DataScopeAspectTest {

    private final PermissionEngine engine = mock(PermissionEngine.class);
    private final MeterRegistry registry = new SimpleMeterRegistry();
    private final DataScopeMetrics metrics = metrics(registry);
    private final Fixture fixture = new Fixture();

    @BeforeEach
    void identity() {
        UserContextHolder.set(new UserContext("u-1", "alice", 1L, 10L, "/1/10/", 1L));
    }

    @AfterEach
    void clear() {
        AuthorizationContext.clear();
        DataScopeContext.clear();
        UserContextHolder.clear();
    }

    @Test
    @DisplayName("数据权限必须绑定本次接口实际通过的权限点")
    void rejects_permission_not_authorized_at_entry() throws Throwable {
        DataScopeAspect aspect = new DataScopeAspect(engine, true, metrics);

        assertThatThrownBy(() -> aspect.apply(joinPoint("scoped", null)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).resultCode())
                        .isEqualTo(ResultCode.PERM_DENIED));
        assertThat(counter("oa_data_scope_denied_total")).isEqualTo(1.0);
    }

    @Test
    @DisplayName("strict 模式下目标 SQL 未执行必须 fail-closed")
    void strict_mode_rejects_unconsumed_scope() throws Throwable {
        AuthorizationContext.push(Set.of("oa:employee:view"));
        when(engine.dataScopeForPermission("u-1", "oa:employee:view")).thenReturn(DataScopeRule.none());
        DataScopeAspect aspect = new DataScopeAspect(engine, true, metrics);

        assertThatThrownBy(() -> aspect.apply(joinPoint("scoped", null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("未命中目标表");
        assertThat(counter("oa_data_scope_missing_total")).isEqualTo(1.0);
        assertThat(counter("oa_data_scope_denied_total")).isEqualTo(1.0);
    }

    @Test
    @DisplayName("ALL 明确求值后可返回，但不会被计作谓词注入")
    void all_is_explicitly_evaluated() throws Throwable {
        AuthorizationContext.push(Set.of("oa:employee:view"));
        when(engine.dataScopeForPermission("u-1", "oa:employee:view")).thenReturn(DataScopeRule.all());
        OaDataPermissionHandler handler = new OaDataPermissionHandler(metrics);
        DataScopeAspect aspect = new DataScopeAspect(engine, true, metrics);

        Object result = aspect.apply(joinPoint("scoped", () ->
                handler.getSqlSegment(table("t"), null, "Fixture.scoped")));

        assertThat(result).isEqualTo("ok");
        assertThat(counter("oa_data_scope_evaluations_total")).isEqualTo(1.0);
        assertThat(counter("oa_data_scope_predicates_total")).isZero();
        assertThat(DataScopeContext.peek()).isNull();
    }

    @Test
    @DisplayName("嵌套接口授权上下文结束后恢复外层权限")
    void nested_authorization_context_restores_outer_permissions() {
        AuthorizationContext.push(Set.of("outer"));
        AuthorizationContext.push(Set.of("inner"));
        assertThat(AuthorizationContext.allows("inner")).isTrue();
        assertThat(AuthorizationContext.allows("outer")).isFalse();
        AuthorizationContext.pop();
        assertThat(AuthorizationContext.allows("outer")).isTrue();
    }

    private ProceedingJoinPoint joinPoint(String methodName, Runnable duringProceed) throws Throwable {
        Method method = Fixture.class.getDeclaredMethod(methodName);
        MethodSignature signature = mock(MethodSignature.class);
        when(signature.getMethod()).thenReturn(method);
        when(signature.toShortString()).thenReturn("Fixture." + methodName + "()");
        ProceedingJoinPoint jp = mock(ProceedingJoinPoint.class);
        when(jp.getSignature()).thenReturn(signature);
        when(jp.getTarget()).thenReturn(fixture);
        when(jp.proceed()).thenAnswer(invocation -> {
            if (duringProceed != null) duringProceed.run();
            return "ok";
        });
        return jp;
    }

    private static Table table(String alias) {
        Table table = new Table("oa_org.v_employee_directory");
        table.setAlias(new net.sf.jsqlparser.expression.Alias(alias));
        return table;
    }

    private double counter(String name) {
        return registry.get(name).counter().count();
    }

    @SuppressWarnings("unchecked")
    private static DataScopeMetrics metrics(MeterRegistry registry) {
        ObjectProvider<MeterRegistry> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(registry);
        return new DataScopeMetrics(provider);
    }

    static class Fixture {
        @DataScope(permission = "oa:employee:view", table = "oa_org.v_employee_directory",
                module = "org", alias = "t")
        String scoped() { return "ok"; }
    }
}
