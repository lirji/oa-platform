package com.lrj.oa.report.application;

import com.lrj.oa.common.exception.BusinessException;
import com.lrj.oa.security.annotation.RequiresPerm;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * 自动审计所有<b>写操作</b>（POST / PUT / DELETE 的 handler）。
 *
 * <p>为什么用切面而不是在每个 service 里手写 {@code audit.record(...)}：
 * 手写的必然会漏 —— 而且漏掉的恰好是新加的、最需要被看住的那个接口。
 * 这和 {@code ControllerPermissionCoverageTest} 是同一个思路：
 * <b>让机制去记，而不是让人去记得</b>。
 *
 * <p>只审计写操作：把所有 GET 也记下来，审计表会以每天几百万行增长，
 * 结果是没人查得动。敏感读（导出、通讯录全量）应由那些接口自己显式补一条。
 *
 * <p>★ Order 必须<b>小于</b> {@code RequiresPermAspect}（HIGHEST_PRECEDENCE + 10），
 * 即本切面在<b>外层</b>。反过来的话，判权抛异常时本切面根本没被进入，
 * {@code DENIED} 一条也记不到 —— 而"谁在试探哪个接口"恰恰是审计最该回答的问题。
 * 一开始写成 +50（内层）就是这个后果：看起来审计在工作（SUCCESS 都有），
 * 唯独缺了最重要的那类记录。
 */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class AuditRecordingAspect {

    private final AuditService audit;

    public AuditRecordingAspect(AuditService audit) { this.audit = audit; }

    @Around("@within(org.springframework.web.bind.annotation.RestController)")
    public Object around(ProceedingJoinPoint jp) throws Throwable {
        Method method = ((org.aspectj.lang.reflect.MethodSignature) jp.getSignature()).getMethod();
        if (!isWrite(method)) return jp.proceed();

        String action = actionOf(jp, method);
        String module = moduleOf(method);
        Map<String, Object> detail = new HashMap<>();
        String ip = clientIp();
        if (ip != null) detail.put("clientIp", ip);

        try {
            Object result = jp.proceed();
            audit.record(action, module, null, null, "SUCCESS", null, detail);
            return result;
        } catch (BusinessException e) {
            // 权限拒绝与业务校验失败分开记：前者是安全事件，后者是用户操作失误。
            boolean denied = e.resultCode().code() == 3001 || e.resultCode().code() == 1403
                    || e.resultCode().code() == 3002 || e.resultCode().code() == 3005;
            audit.record(action, module, null, null, denied ? "DENIED" : "FAILED",
                    e.resultCode().code() + ": " + e.getMessage(), detail);
            throw e;
        } catch (Throwable t) {
            audit.record(action, module, null, null, "FAILED", t.toString(), detail);
            throw t;
        }
    }

    private static boolean isWrite(Method m) {
        return m.isAnnotationPresent(PostMapping.class)
                || m.isAnnotationPresent(PutMapping.class)
                || m.isAnnotationPresent(DeleteMapping.class);
    }

    /** 动作名优先用权限点 code（它本来就是"谁能做什么"的规范命名），否则退回类名#方法名。 */
    private static String actionOf(ProceedingJoinPoint jp, Method m) {
        RequiresPerm rp = m.getAnnotation(RequiresPerm.class);
        if (rp != null && rp.value().length > 0) return rp.value()[0];
        return jp.getSignature().getDeclaringType().getSimpleName() + "#" + m.getName();
    }

    private static String moduleOf(Method m) {
        RequiresPerm rp = m.getAnnotation(RequiresPerm.class);
        if (rp != null && rp.value().length > 0) {
            String[] parts = rp.value()[0].split(":");
            if (parts.length >= 2) return parts[1];
        }
        String pkg = m.getDeclaringClass().getPackageName();
        int i = pkg.indexOf("com.lrj.oa.");
        return i < 0 ? "unknown" : pkg.substring(i + 11).split("\\.")[0];
    }

    private static String clientIp() {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (!(attrs instanceof ServletRequestAttributes sra)) return null;
        HttpServletRequest req = sra.getRequest();
        String xff = req.getHeader("X-Forwarded-For");
        // 取第一段：XFF 是可追加的，后面的段可能是代理自己加的。
        // 注意它同样是【客户端可伪造】的，审计里记它只是线索，不能当身份用。
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        return req.getRemoteAddr();
    }
}
