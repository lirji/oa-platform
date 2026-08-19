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

    private DataScopeContext() {}

    public static void set(DataScope annotation, DataScopeRule rule) {
        HOLDER.set(new Active(annotation, rule));
    }

    public static Active peek() { return HOLDER.get(); }

    public static void clear() { HOLDER.remove(); }
}
