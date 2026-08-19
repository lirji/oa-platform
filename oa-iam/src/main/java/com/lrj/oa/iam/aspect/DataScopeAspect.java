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

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DataScopeAspect.class);

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
            Object result = jp.proceed();
            if (!DataScopeContext.wasConsumed()) {
                // 设了数据权限却没有任何 MyBatis 查询取走它 —— 说明这个方法要么根本没查库，
                // 要么用的是 JdbcTemplate 这类绕过拦截器的路径。后者是个静默的全量泄露：
                // 注解明晃晃写着，行为却完全没有过滤。必须吵出来。
                log.warn("★ @DataScope 未生效：{}#{} 设置了数据权限上下文，但没有任何 MyBatis 查询消费它。"
                                + " 用 JdbcTemplate 手写 SQL 会绕过拦截器 —— 该方法可能正在返回全量数据。",
                        jp.getSignature().getDeclaringType().getSimpleName(), jp.getSignature().getName());
            }
            return result;
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
