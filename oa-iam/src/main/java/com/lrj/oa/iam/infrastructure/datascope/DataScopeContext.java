package com.lrj.oa.iam.infrastructure.datascope;

import com.lrj.oa.security.annotation.DataScope;
import com.lrj.oa.security.model.DataScopeRule;

/**
 * 当前线程正在生效的数据权限上下文。
 *
 * <p>为什么需要它：{@code @DataScope} 标在<b>服务方法</b>上，而 SQL 改写发生在
 * <b>MyBatis 拦截器</b>里，两者之间没有直接的调用参数可传。ThreadLocal 是它们之间唯一的桥。
 * 切面负责 set/clear，拦截器只读。
 */
public final class DataScopeContext {

    public record Active(DataScope annotation, DataScopeRule rule) {}

    private static final ThreadLocal<Active> HOLDER = new ThreadLocal<>();
    /**
     * 本次 {@code @DataScope} 是否真的被拦截器用上了。
     *
     * <p>★ 防的是一个很安静的洞：{@code @DataScope} 只对<b>走 MyBatis</b> 的查询生效。
     * 标在一个用 JdbcTemplate 手写 SQL 的方法上，切面照常设上下文、拦截器<b>永远不会被调用</b>，
     * 于是这个方法看起来"有数据权限"，实际返回全量数据 —— 代码 review 时最容易放过的那种，
     * 因为注解就明晃晃写在那儿。
     * 有了这个标记，切面可以在方法返回时发现"设了却没人用"，把静默泄露变成一条刺眼的告警。
     */
    private static final ThreadLocal<Boolean> CONSUMED = new ThreadLocal<>();

    private DataScopeContext() {}

    public static void set(DataScope annotation, DataScopeRule rule) {
        HOLDER.set(new Active(annotation, rule));
        CONSUMED.set(Boolean.FALSE);
    }

    public static Active peek() {
        Active a = HOLDER.get();
        if (a != null) CONSUMED.set(Boolean.TRUE);
        return a;
    }

    /** 切面用：本次上下文有没有被 MyBatis 拦截器取走过。 */
    public static boolean wasConsumed() {
        return Boolean.TRUE.equals(CONSUMED.get());
    }

    public static void clear() {
        HOLDER.remove();
        CONSUMED.remove();
    }
}
