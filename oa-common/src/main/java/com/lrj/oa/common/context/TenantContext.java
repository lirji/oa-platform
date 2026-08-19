package com.lrj.oa.common.context;

/**
 * 租户上下文。本轮按单租户实施（默认 1），但所有表预留 tenant_id 列，
 * 未来开多租户时只需在入口填充这里，不动业务代码（见 FINAL_PLAN N3）。
 *
 * <p>用 ScopedValue 的前身 ThreadLocal：JDK21 虚拟线程下 ThreadLocal 仍可用，
 * 但每个虚拟线程一份副本，故必须在 finally 里 clear，否则线程池复用会串号。
 */
public final class TenantContext {

    public static final long DEFAULT_TENANT_ID = 1L;

    private static final ThreadLocal<Long> HOLDER = new ThreadLocal<>();

    private TenantContext() {}

    public static void set(Long tenantId) { HOLDER.set(tenantId); }

    public static long get() {
        Long v = HOLDER.get();
        return v == null ? DEFAULT_TENANT_ID : v;
    }

    public static void clear() { HOLDER.remove(); }
}
