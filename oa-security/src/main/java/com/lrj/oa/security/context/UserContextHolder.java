package com.lrj.oa.security.context;

import com.lrj.oa.common.api.ResultCode;
import com.lrj.oa.common.exception.BusinessException;

/**
 * UserContext 的线程持有者。由 JwtAuthFilter 在请求入口装配、在 finally 清理。
 *
 * <p>虚拟线程下 ThreadLocal 语义不变（每个虚拟线程独立），但 <b>必须 clear</b>：
 * 平台线程池复用会串号，虚拟线程虽然一次性但 clear 能让 GC 更早回收。
 */
public final class UserContextHolder {

    private static final ThreadLocal<UserContext> HOLDER = new ThreadLocal<>();

    private UserContextHolder() {}

    public static void set(UserContext ctx) { HOLDER.set(ctx); }

    /** 取当前用户；未认证抛 401。需要可空语义时用 {@link #peek()}。 */
    public static UserContext require() {
        UserContext ctx = HOLDER.get();
        if (ctx == null) throw BusinessException.of(ResultCode.UNAUTHORIZED);
        return ctx;
    }

    public static UserContext peek() { return HOLDER.get(); }

    public static String currentUserId() { return require().userId(); }

    public static void clear() { HOLDER.remove(); }
}
