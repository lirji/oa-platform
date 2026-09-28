package com.lrj.oa.org.infrastructure.directory;

import com.lrj.oa.security.context.ServiceIdentity;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.web.filter.OncePerRequestFilter;

/** 目录具有独立认证链；即使 OA DEV 模式或公共路径配置过宽，目录仍然默认拒绝。 */
@Configuration
@EnableConfigurationProperties(IdentityDirectoryExportProperties.class)
public class IdentityDirectorySecurityConfiguration {
    public static final String SERVICE = "oa-directory";

    /** 只匹配内部目录命名空间；未声明的方法/子路径不能落入普通员工安全链。 */
    @Bean @Order(0)
    public SecurityFilterChain identityDirectorySecurity(HttpSecurity http, IdentityDirectoryExportProperties config) throws Exception {
        http.securityMatcher("/internal/directory/**")
                .csrf(csrf -> csrf.disable())
                .requestCache(cache -> cache.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterAfter(new CredentialFilter(config), SecurityContextHolderFilter.class)
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(HttpMethod.GET, "/internal/directory/v1/events", "/internal/directory/v1/status").authenticated()
                        .requestMatchers(HttpMethod.POST, "/internal/directory/v1/ack").authenticated()
                        .anyRequest().denyAll())
                .exceptionHandling(errors -> errors.authenticationEntryPoint((request, response, failure) -> response.setStatus(401)));
        return http.build();
    }

    /** 不注册为全局 Servlet Filter，防止在安全链外重复执行或扩大服务身份有效范围。 */
    static final class CredentialFilter extends OncePerRequestFilter {
        private final IdentityDirectoryExportProperties config;
        CredentialFilter(IdentityDirectoryExportProperties config) { this.config = config; }
        @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
            if (!config.enabled()) { response.setStatus(404); return; }
            boolean local = config.allowLoopbackHttp() && loopback(request.getRemoteAddr()) && loopback(request.getLocalAddr());
            if (!request.isSecure() && !local) { response.setStatus(403); return; }
            var headers = Collections.list(request.getHeaders("Authorization"));
            if (headers.size() != 1 || !valid(headers.getFirst())) { response.setStatus(401); return; }
            var context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(new ServiceIdentity(SERVICE, config.tenantId()), null, List.of()));
            SecurityContextHolder.setContext(context);
            try { chain.doFilter(request, response); }
            finally { SecurityContextHolder.clearContext(); }
        }
        private boolean valid(String header) {
            if (!header.matches("Bearer [A-Za-z0-9_-]{43,128}")) { return false; }
            try {
                byte[] actual = MessageDigest.getInstance("SHA-256").digest(header.substring(7).getBytes(StandardCharsets.US_ASCII));
                return MessageDigest.isEqual(actual, HexFormat.of().parseHex(config.credentialSha256()));
            } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 不可用", impossible); }
        }
        private static boolean loopback(String address) {
            return Set.of("127.0.0.1", "::1", "0:0:0:0:0:0:0:1").contains(address);
        }
    }
}
