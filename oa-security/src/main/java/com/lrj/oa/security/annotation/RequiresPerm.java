package com.lrj.oa.security.annotation;

import java.lang.annotation.*;

/**
 * 接口权限注解 —— <b>系统唯一的安全边界</b>（见 DECISION_RECORD ADR-0008）。
 *
 * <p>前端的菜单/按钮裁剪只是体验，任何被前端隐藏的能力，后端都必须靠这个注解拦住。
 *
 * <p>CI 强制：{@code ControllerPermissionCoverageTest} 会扫描所有 {@code @RestController}
 * 的 public handler，缺本注解且无 {@link PublicApi} 的 <b>构建失败</b>。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequiresPerm {

    /** 权限点 code，如 {@code oa:leave:approve}。多个时按 {@link #logical()} 组合。 */
    String[] value();

    Logical logical() default Logical.AND;

    /**
     * 是否强制要求 JIT 临时提权。true 时，即便用户持有永久授权，
     * 也必须存在一条活跃的 TEMPORARY grant 才放行（用于导出全员薪资、删除组织这类高危操作）。
     */
    boolean elevation() default false;

    enum Logical { AND, OR }
}
