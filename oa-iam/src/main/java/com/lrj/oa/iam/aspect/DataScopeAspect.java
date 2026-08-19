package com.lrj.oa.iam.aspect;

import com.lrj.oa.iam.infrastructure.cache.PermissionEngine;
import com.lrj.oa.iam.infrastructure.datascope.DataScopeContext;
import com.lrj.oa.security.annotation.DataScope;
import com.lrj.oa.security.context.UserContext;
import com.lrj.oa.security.context.UserContextHolder;
import com.lrj.oa.security.model.DataScopeRule;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;
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

    private final PermissionEngine engine;

    public DataScopeAspect(PermissionEngine engine) { this.engine = engine; }

    @Around("@annotation(com.lrj.oa.security.annotation.DataScope)")
    public Object apply(ProceedingJoinPoint jp) throws Throwable {
        DataScope ann = findAnnotation(jp);
        if (ann == null) return jp.proceed();

        UserContext ctx = UserContextHolder.peek();
        DataScopeRule rule = (ctx == null)
                ? DataScopeRule.none()
                : engine.dataScope(ctx.userId(), ann.module());

        DataScopeContext.set(ann, rule);
        try {
            return jp.proceed();
        } finally {
            // 嵌套调用与线程复用都会让残留的上下文污染下一次查询，必须清
            DataScopeContext.clear();
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
