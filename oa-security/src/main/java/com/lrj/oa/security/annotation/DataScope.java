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

    /** SQL 中目标表的别名，如 {@code t}。 */
    String alias() default "t";

    /** 组织 id 列名。 */
    String orgColumn() default "org_id";

    /** 组织物化路径列名 —— 前缀匹配的主力。 */
    String pathColumn() default "org_path";

    /** 创建人列名，SELF 范围用。 */
    String userColumn() default "creator_id";

    /**
     * 模块 key。用户的数据范围可按模块细分
     * （例：考勤=全公司只读、报销=本部门），留空则用合并后的最宽范围。
     */
    String module() default "";
}
