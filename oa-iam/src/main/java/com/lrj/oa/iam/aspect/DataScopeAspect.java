package com.lrj.oa.iam.aspect;

import com.lrj.oa.common.api.ResultCode;
import com.lrj.oa.common.exception.BusinessException;
import com.lrj.oa.iam.infrastructure.cache.PermissionEngine;
import com.lrj.oa.iam.infrastructure.datascope.DataScopeContext;
import com.lrj.oa.iam.infrastructure.datascope.DataScopeMetrics;
import com.lrj.oa.security.annotation.DataScope;
import com.lrj.oa.security.context.UserContext;
import com.lrj.oa.security.context.UserContextHolder;
import com.lrj.oa.security.model.DataScopeRule;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * 从权限快照取出当前用户的数据范围，放进 {@link DataScopeContext}，供 MyBatis 拦截器改写 SQL。
 *
 * <p><b>没有身份时给的是 {@link DataScopeRule#none()} 而不是"不加条件"</b> ——
 * 数据权限的安全默认值必须是"什么都看不到"，反过来就是一个全量泄露。
 */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 30)
public class DataScopeAspect {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DataScopeAspect.class);

    private final PermissionEngine engine;
    private final boolean strict;
    private final DataScopeMetrics metrics;

    public DataScopeAspect(PermissionEngine engine,
                           @Value("${oa.iam.data-scope.strict:true}") boolean strict,
                           DataScopeMetrics metrics) {
        this.engine = engine;
        this.strict = strict;
        this.metrics = metrics;
    }

    @Around("@annotation(com.lrj.oa.security.annotation.DataScope)")
    public Object apply(ProceedingJoinPoint jp) throws Throwable {
        DataScope ann = findAnnotation(jp);
        if (ann == null) return jp.proceed();

        if (ann.permission().isBlank() || ann.table().isBlank()) {
            throw new IllegalStateException("@DataScope 必须声明 permission 和 table: "
                    + jp.getSignature().toShortString());
        }
        if (!AuthorizationContext.allows(ann.permission())) {
            metrics.denied();
            throw BusinessException.of(ResultCode.PERM_DENIED,
                    "数据范围权限未通过接口判权: " + ann.permission());
        }

        UserContext ctx = UserContextHolder.peek();
        DataScopeRule rule = (ctx == null)
                ? DataScopeRule.none()
                : engine.dataScopeForPermission(ctx.userId(), ann.permission());

        DataScopeContext.Active active = DataScopeContext.push(ann, rule);
        try {
            Object result = jp.proceed();
            if (!active.evaluated()) {
                metrics.missing();
                String detail = "@DataScope 未命中目标表 " + ann.table()
                        + "，实际表=" + active.seenTables() + " method=" + jp.getSignature().toShortString();
                if (strict) {
                    metrics.denied();
                    throw new IllegalStateException(detail);
                }
                log.warn("★ {}", detail);
            } else {
                metrics.evaluated(active.predicateInjected());
            }
            return result;
        } finally {
            DataScopeContext.pop(active);
        }
    }

    private static DataScope findAnnotation(ProceedingJoinPoint jp) {
        MethodSignature sig = (MethodSignature) jp.getSignature();
        Object target = jp.getTarget();
        Method method = target == null ? sig.getMethod()
                : AopUtils.getMostSpecificMethod(sig.getMethod(), target.getClass());
        return AnnotatedElementUtils.findMergedAnnotation(method, DataScope.class);
    }
}
