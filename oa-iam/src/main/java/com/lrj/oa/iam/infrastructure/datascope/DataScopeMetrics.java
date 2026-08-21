package com.lrj.oa.iam.infrastructure.datascope;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** 数据权限严格执行协议的低基数指标。 */
@Component
public class DataScopeMetrics {

    private final Counter evaluations;
    private final Counter predicates;
    private final Counter denied;
    private final Counter missing;
    private final Counter aliasMismatch;
    private final Counter bypass;

    @Autowired
    public DataScopeMetrics(ObjectProvider<MeterRegistry> meters) {
        this(meters.getIfAvailable());
    }

    private DataScopeMetrics(MeterRegistry registry) {
        evaluations = counter(registry, "oa_data_scope_evaluations_total");
        predicates = counter(registry, "oa_data_scope_predicates_total");
        denied = counter(registry, "oa_data_scope_denied_total");
        missing = counter(registry, "oa_data_scope_missing_total");
        aliasMismatch = counter(registry, "oa_data_scope_alias_mismatch_total");
        bypass = counter(registry, "oa_data_scope_bypass_total");
    }

    /** 仅供不启动 Spring 的 SQL 单元测试使用。 */
    public static DataScopeMetrics noop() { return new DataScopeMetrics((MeterRegistry) null); }

    public void evaluated(boolean predicateInjected) {
        increment(evaluations);
        if (predicateInjected) increment(predicates);
    }

    public void denied() { increment(denied); }
    public void missing() { increment(missing); }
    public void aliasMismatch() { increment(aliasMismatch); }
    public void bypass() { increment(bypass); }

    private static Counter counter(MeterRegistry registry, String name) {
        return registry == null ? null : registry.counter(name);
    }

    private static void increment(Counter counter) {
        if (counter != null) counter.increment();
    }
}
