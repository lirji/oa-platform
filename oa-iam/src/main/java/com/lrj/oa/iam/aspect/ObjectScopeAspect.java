package com.lrj.oa.iam.aspect;

import com.lrj.oa.common.api.ResultCode;
import com.lrj.oa.common.exception.BusinessException;
import com.lrj.oa.iam.infrastructure.datascope.DataScopeMetrics;
import com.lrj.oa.security.annotation.ObjectScope;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/** 对对象级 owner/ACL 数据访问协议做入口权限和元数据强制检查。 */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class ObjectScopeAspect {
    private final DataScopeMetrics metrics;

    public ObjectScopeAspect(DataScopeMetrics metrics) {
        this.metrics = metrics;
    }

    @Around("@annotation(scope)")
    public Object guard(ProceedingJoinPoint jp, ObjectScope scope) throws Throwable {
        if (scope.permission().isBlank() || scope.reason().isBlank() || scope.tables().length == 0
                || Arrays.stream(scope.tables()).anyMatch(String::isBlank)) {
            throw new IllegalStateException("@ObjectScope 必须声明 permission/tables/reason: "
                    + jp.getSignature().toShortString());
        }
        if (!AuthorizationContext.allows(scope.permission())) {
            metrics.denied();
            throw BusinessException.of(ResultCode.PERM_DENIED,
                    "对象范围权限未通过接口判权: " + scope.permission());
        }
        metrics.objectGuarded();
        return jp.proceed();
    }
}
