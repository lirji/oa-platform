package com.lrj.oa.iam.identity;

import com.lrj.oa.iam.infrastructure.cache.PermissionEngine;
import com.lrj.oa.security.context.UserContext;
import com.lrj.oa.security.context.UserContextHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 在每个响应上带 {@code X-OA-Perm-Version}。
 *
 * <p><b>解决什么</b>："改了角色，前端不刷新就生效"这条验收标准，靠长连推送是不够的：
 * <ul>
 *   <li>WebSocket 会断（弱网、代理超时、JWT 模式下浏览器还连不上）；</li>
 *   <li>轮询在万人规模下是常态负载 —— 15 秒一次就是 667 QPS，只为一个几乎从不变的东西。</li>
 * </ul>
 *
 * <p>而<b>用户正在操作</b>时，本来就在发请求。把版本号搭在响应头上，前端拦截器一比对就知道
 * 权限变了 —— <b>零额外请求</b>，且覆盖长连断掉的场景。它也让 403 变得可解释：
 * 同一个 403，版本号变了说明"你的权限刚被改了"，没变说明"你本来就没有"。
 *
 * <p>放在 {@code oa-common} 之外、{@code oa-iam} 之内，是因为它要读判权引擎的快照；
 * 但四个可部署单元都嵌了 oa-iam，所以四个都会带上这个头。
 *
 * <p>取快照走的是 L1 缓存（进程内、纳秒级），不会给热路径增加可观测的开销。
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 10)
public class PermVersionHeaderFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-OA-Perm-Version";

    private final PermissionEngine engine;

    public PermVersionHeaderFilter(PermissionEngine engine) { this.engine = engine; }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse resp, FilterChain chain)
            throws ServletException, IOException {
        // ★ 必须在 chain 之前写：响应一旦提交（大文件下载、流式响应）就加不上头了。
        //   身份由 UserContextFilter 更早装配，这里能读到。
        UserContext ctx = UserContextHolder.peek();
        if (ctx != null) {
            try {
                var snap = engine.snapshot(ctx.userId());
                resp.setHeader(HEADER, String.valueOf(snap.epoch() * 1_000_000L + snap.userVersion()));
            } catch (Exception ignored) {
                // 拿不到版本号不该让请求失败 —— 它是给前端的提示，不是业务数据。
            }
        }
        chain.doFilter(req, resp);
    }
}
