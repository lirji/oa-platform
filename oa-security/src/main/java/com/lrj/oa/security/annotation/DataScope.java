package com.lrj.oa.security.annotation;

import java.lang.annotation.*;

/**
 * 数据权限（行级）注解。由 MyBatis 拦截器读取并改写 SQL。
 *
 * <p><b>核心性能约定（ADR-0007）</b>：{@code ORG_AND_SUB} 范围翻译成
 * {@code t.org_path LIKE '/1/23/%'} 前缀匹配，<b>绝不</b>展开成 {@code org_id IN (5000 个 id)}。
 * 因此所有业务单据表必须冗余 {@code org_id} + {@code org_path} 两列，
 * 且 {@code org_path} 上要有 {@code text_pattern_ops} 索引
 * （PG 非 C locale 下普通 btree 索引不走前缀 LIKE）。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DataScope {

    /** 本次数据范围归属的权限点，必须同时通过接口判权。 */
    String permission();

    /** 需要保护的主表或视图全名，如 {@code oa_org.v_employee_directory}。 */
    String table();

    /** SQL 中目标表的别名，如 {@code t}。 */
    String alias() default "t";

    /** 组织 id 列名。 */
    String orgColumn() default "org_id";

    /** 组织物化路径列名 —— 前缀匹配的主力。 */
    String pathColumn() default "org_path";

    /** 创建人列名，SELF 范围用。 */
    String userColumn() default "creator_id";

    /**
     * 模块 key，仅保留作迁移兼容和诊断元数据。
     * 严格判权只按 {@link #permission()} 读取权限点级范围，不能回退到模块或全局最宽范围。
     */
    String module() default "";
}
