package com.lrj.oa.iam.aspect;

import com.lrj.oa.iam.infrastructure.datascope.DataScopeMetrics;
import com.lrj.oa.security.annotation.DataScopeBypass;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DataScopeBypassAspectTest {

    @Test
    @DisplayName("显式数据权限旁路记录指标并继续调用")
    @SuppressWarnings("unchecked")
    void audited_bypass_proceeds() throws Throwable {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ObjectProvider<MeterRegistry> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(registry);
        DataScopeBypassAspect aspect = new DataScopeBypassAspect(new DataScopeMetrics(provider));
        ProceedingJoinPoint jp = mock(ProceedingJoinPoint.class);
        Signature signature = mock(Signature.class);
        when(signature.toShortString()).thenReturn("Fixture.maintenance()");
        when(jp.getSignature()).thenReturn(signature);
        when(jp.proceed()).thenReturn("ok");

        Object result = aspect.audit(jp, Fixture.class.getDeclaredMethod("maintenance")
                .getAnnotation(DataScopeBypass.class));

        assertThat(result).isEqualTo("ok");
        assertThat(registry.get("oa_data_scope_bypass_total").counter().count()).isEqualTo(1.0);
    }

    static class Fixture {
        @DataScopeBypass(reason = "测试基础设施维护旁路", tables = "oa_iam.permission")
        void maintenance() {}
    }
}
