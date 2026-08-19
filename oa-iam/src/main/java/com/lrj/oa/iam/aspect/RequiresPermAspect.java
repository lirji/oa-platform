package com.lrj.oa.iam.aspect;

import com.lrj.oa.common.api.ResultCode;
import com.lrj.oa.common.exception.BusinessException;
import com.lrj.oa.iam.application.PermissionCatalog;
import com.lrj.oa.iam.infrastructure.cache.PermissionEngine;
import com.lrj.oa.security.annotation.RequiresPerm;
import com.lrj.oa.security.context.UserContext;
import com.lrj.oa.security.context.UserContextHolder;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

/**
 * {@link RequiresPerm} 的执行者 —— 系统<b>唯一</b>的安全边界（ADR-0008）。
 *
 * <p>判定全程读内存快照，零 DB、零远程调用。
 */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequiresPermAspect {

    private static final Logger log = LoggerFactory.getLogger(RequiresPermAspect.class);

    private final PermissionEngine engine;
    private final PermissionCatalog catalog;
    private final boolean enforce;

    public RequiresPermAspect(PermissionEngine engine, PermissionCatalog catalog,
                              @Value("${oa.iam.enforce:true}") boolean enforce) {
        this.engine = engine;
        this.catalog = catalog;
        this.enforce = enforce;
        if (!enforce) {
            log.warn("""

                ╔══════════════════════════════════════════════════════════════════╗
                ║  oa.iam.enforce=false —— 接口权限校验已【关闭】，全部放行         ║
                ║  只应在联调期短暂使用；它绕过的是系统唯一的安全边界。             ║
                ╚══════════════════════════════════════════════════════════════════╝""");
        }
    }

    @Before("@annotation(com.lrj.oa.security.annotation.RequiresPerm)"
            + " || @within(com.lrj.oa.security.annotation.RequiresPerm)")
    public void check(JoinPoint jp) {
        if (!enforce) return;

        RequiresPerm ann = findAnnotation(jp);
        if (ann == null) return;

        UserContext ctx = UserContextHolder.peek();
        if (ctx == null) {
            // 没有身份就不可能有权限。这里返回 401 而不是 403，区分"你是谁"和"你能不能"。
            throw BusinessException.of(ResultCode.UNAUTHORIZED, "未识别到用户身份");
        }

        List<String> codes = Arrays.asList(ann.value());
        boolean ok = ann.logical() == RequiresPerm.Logical.AND
                ? engine.hasAll(ctx.userId(), codes)
                : engine.hasAny(ctx.userId(), codes);

        if (!ok) {
            log.info("拒绝访问 user={} 需要={}({}) 方法={}",
                    ctx.userId(), codes, ann.logical(), jp.getSignature().toShortString());
            throw BusinessException.of(ResultCode.PERM_DENIED, "需要权限: " + String.join(", ", codes));
        }

        // 高危操作：即便有永久授权，也必须存在一条活跃的 JIT 临时提权
        if (ann.elevation() || codes.stream().anyMatch(c -> catalog.requiresElevation(catalog.idOf(c)))) {
            boolean elevated = codes.stream().allMatch(c -> engine.isElevated(ctx.userId(), c));
            if (!elevated) {
                log.warn("拒绝高危操作 user={} 权限={} —— 缺少有效的临时提权", ctx.userId(), codes);
                throw BusinessException.of(ResultCode.PERM_ELEVATION_REQUIRED,
                        "该操作需要临时提权，请先申请：" + String.join(", ", codes));
            }
            log.warn("★ 提权态操作 user={} 权限={} 方法={}（已记入审计）",
                    ctx.userId(), codes, jp.getSignature().toShortString());
        }
    }

    private static RequiresPerm findAnnotation(JoinPoint jp) {
        MethodSignature sig = (MethodSignature) jp.getSignature();
        Object target = jp.getTarget();
        // 代理场景下 sig.getMethod() 可能拿到接口方法，要取最具体的实现方法
        Method method = target == null ? sig.getMethod()
                : AopUtils.getMostSpecificMethod(sig.getMethod(), target.getClass());
        RequiresPerm ann = AnnotatedElementUtils.findMergedAnnotation(method, RequiresPerm.class);
        if (ann != null) return ann;
        return target == null ? null
                : AnnotatedElementUtils.findMergedAnnotation(target.getClass(), RequiresPerm.class);
    }
}
