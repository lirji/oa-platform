package com.lrj.oa.security.annotation;

import java.lang.annotation.*;

/** 声明固定服务身份入口；运行时守卫与端点均验证类型化身份，不能由员工角色或 isAdmin 替代。 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequiresServiceIdentity {
    /** 必须与专用认证链生成的服务代码精确一致。 */
    String value();
}
