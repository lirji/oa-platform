package com.lrj.oa.security.annotation;

import java.lang.annotation.*;

/**
 * 显式声明"这个接口不需要权限"。是 {@link RequiresPerm} 覆盖检查的唯一豁免方式。
 *
 * <p>设计意图：让"无鉴权"成为一个<b>需要主动书写、可被 grep 审计</b>的决定，
 * 而不是忘记加注解的默认后果。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface PublicApi {
    /** 为什么可以公开，必填，供安全审计阅读。 */
    String reason();
}
