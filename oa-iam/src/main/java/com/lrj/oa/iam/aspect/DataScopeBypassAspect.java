package com.lrj.oa.iam.aspect;

import com.lrj.oa.iam.infrastructure.datascope.DataScopeMetrics;
import com.lrj.oa.security.annotation.DataScopeBypass;
import com.lrj.oa.security.context.UserContext;
import com.lrj.oa.security.context.UserContextHolder;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/** 对批准的数据权限旁路进行强制元数据校验、指标和审计日志记录。 */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 25)
public class DataScopeBypassAspect {

    private static final Logger log = LoggerFactory.getLogger(DataScopeBypassAspect.class);
    private final DataScopeMetrics metrics;

    public DataScopeBypassAspect(DataScopeMetrics metrics) {
        this.metrics = metrics;
    }

    @Around("@annotation(bypass)")
    public Object audit(ProceedingJoinPoint jp, DataScopeBypass bypass) throws Throwable {
        if (bypass.reason().isBlank() || bypass.tables().length == 0
                || Arrays.stream(bypass.tables()).anyMatch(String::isBlank)) {
            throw new IllegalStateException("@DataScopeBypass 必须声明 reason 和 tables: "
                    + jp.getSignature().toShortString());
        }
        metrics.bypass();
        UserContext user = UserContextHolder.peek();
        log.warn("★ 数据权限旁路 subject={} method={} reason={} tables={} traceId={}",
                user == null ? "system" : user.userId(), jp.getSignature().toShortString(),
                bypass.reason(), Arrays.toString(bypass.tables()), MDC.get("traceId"));
        return jp.proceed();
    }
}
