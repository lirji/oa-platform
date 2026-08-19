package com.lrj.oa.iam.identity;

import com.lrj.oa.common.context.TenantContext;
import com.lrj.oa.org.api.OrgQueryApi;
import com.lrj.oa.org.api.dto.EmployeeView;
import com.lrj.oa.security.context.UserContext;
import com.lrj.oa.security.context.UserContextHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 把"当前是谁"装配进 {@link UserContextHolder}。
 *
 * <p>两条来源，取决于 {@code oa.security.mode}：
 * <ul>
 *   <li><b>JWT</b>：从 Casdoor 签发的 token 里取 {@code sub}（UUID）与 {@code name}；</li>
 *   <li><b>DEV</b>：从 {@code X-OA-User} 请求头取 userId。
 *       没有它的话，DEV 模式下所有 {@code @RequiresPerm} 都会因"无身份"而 401，
 *       联调根本没法进行；有了它，还能直接用不同 header 切换身份来验证权限差异。</li>
 * </ul>
 *
 * <p>无论哪条路径，{@code finally} 里都必须清理 —— 线程复用会串号。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class UserContextFilter extends OncePerRequestFilter {

    public static final String DEV_USER_HEADER = "X-OA-User";

    private static final Logger log = LoggerFactory.getLogger(UserContextFilter.class);

    private final OrgQueryApi orgQuery;

    public UserContextFilter(OrgQueryApi orgQuery) { this.orgQuery = orgQuery; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        try {
            String userId = resolveUserId(request);
            if (userId != null) {
                UserContextHolder.set(assemble(userId));
                TenantContext.set(TenantContext.DEFAULT_TENANT_ID);
            }
            chain.doFilter(request, response);
        } finally {
            UserContextHolder.clear();
            TenantContext.clear();
        }
    }

    private String resolveUserId(HttpServletRequest request) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            return jwt.getSubject();          // Casdoor sub（UUID），全系统主体标识
        }
        String dev = request.getHeader(DEV_USER_HEADER);
        return (dev == null || dev.isBlank()) ? null : dev.trim();
    }

    /**
     * 把 userId 补全成完整上下文。查不到员工档案也<b>不</b>拒绝 ——
     * 账号存在但未入职是合法状态，交给判权去决定他什么都做不了。
     */
    private UserContext assemble(String userId) {
        try {
            EmployeeView e = orgQuery.getEmployeeByUserId(userId);
            return new UserContext(userId, e.name(), e.id(), e.primaryOrgId(),
                    e.primaryOrgPath(), TenantContext.DEFAULT_TENANT_ID);
        } catch (Exception ex) {
            log.debug("用户 {} 没有员工档案，按无归属身份处理", userId);
            return new UserContext(userId, userId, null, null, null, TenantContext.DEFAULT_TENANT_ID);
        }
    }
}
