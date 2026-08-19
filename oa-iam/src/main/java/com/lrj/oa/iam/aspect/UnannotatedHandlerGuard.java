package com.lrj.oa.iam.aspect;

import com.lrj.oa.security.annotation.PublicApi;
import com.lrj.oa.security.annotation.RequiresPerm;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;

/**
 * 运行时兜底：任何<b>没有声明授权立场</b>的 handler 一律拒绝（fail-closed）。
 *
 * <p>计划里原本写的是"用 URL 映射表做兜底过滤器"。实现时改成了这个 ——
 * 理由是它更可靠：URL 表需要人去维护，漏配一条就等于漏了一个接口；
 * 而"没有注解就拒绝"覆盖的是<b>全部</b>未来新增的接口，不需要任何人记得做什么。
 *
 * <p>它与 {@code ControllerPermissionCoverageTest} 是双保险：
 * 构建期拦不住的（比如有人临时跳过测试），运行期还会拒绝并打 ERROR 日志。
 * 正常情况下这段代码永远不会触发。
 */
@Component
public class UnannotatedHandlerGuard implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(UnannotatedHandlerGuard.class);

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod hm)) return true;

        // 框架自带端点（actuator、swagger、错误页）不在管辖范围
        String pkg = hm.getBeanType().getPackageName();
        if (!pkg.startsWith("com.lrj.oa")) return true;

        boolean declared = AnnotatedElementUtils.hasAnnotation(hm.getMethod(), RequiresPerm.class)
                || AnnotatedElementUtils.hasAnnotation(hm.getMethod(), PublicApi.class)
                || AnnotatedElementUtils.hasAnnotation(hm.getBeanType(), RequiresPerm.class)
                || AnnotatedElementUtils.hasAnnotation(hm.getBeanType(), PublicApi.class);
        if (declared) return true;

        log.error("★ 接口 {}#{} 未声明授权立场（缺 @RequiresPerm / @PublicApi），已按 fail-closed 拒绝",
                hm.getBeanType().getSimpleName(), hm.getMethod().getName());
        writeForbidden(response);
        return false;
    }

    private static void writeForbidden(HttpServletResponse response) {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        try {
            response.getOutputStream().write(
                    "{\"code\":3001,\"message\":\"接口未声明授权立场，已拒绝\",\"data\":null,\"traceId\":null}"
                            .getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) { }
    }
}
