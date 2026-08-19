package com.lrj.oa.security.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * OA 统一安全链路。四个可部署单元（app / notify / file / job）共用同一份，避免各写一套。
 *
 * <p><b>认证与授权的分工</b>：
 * <ul>
 *   <li>这里只管<b>认证</b>（你是谁）——校验 Casdoor 签发的 JWT；</li>
 *   <li><b>授权</b>（你能干什么）由 {@code @RequiresPerm} 切面在方法级做，
 *       不在这里写一长串 antMatchers。理由：万人级 OA 有近千个权限点，
 *       写在 SecurityConfig 里既无法复用也无法被 CI 覆盖检查。</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(OaSecurityProperties.class)
public class OaSecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(OaSecurityConfig.class);

    @Bean
    public SecurityFilterChain oaSecurityFilterChain(HttpSecurity http, OaSecurityProperties props) throws Exception {
        String[] publicPaths = props.getPublicPaths().toArray(String[]::new);

        http
            .csrf(csrf -> csrf.disable())          // 纯 token 前后端分离，无 cookie 会话
            .cors(Customizer.withDefaults())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(reg -> {
                reg.requestMatchers(publicPaths).permitAll();
                if (props.getMode() == OaSecurityProperties.Mode.DEV) {
                    reg.anyRequest().permitAll();
                } else {
                    reg.anyRequest().authenticated();
                }
            });

        if (props.getMode() == OaSecurityProperties.Mode.JWT) {
            http.oauth2ResourceServer(o -> o.jwt(Customizer.withDefaults()));
        } else {
            log.warn("""

                ╔══════════════════════════════════════════════════════════════════╗
                ║  oa.security.mode=DEV —— 认证已关闭，所有接口无需 token 即可访问   ║
                ║  仅限本地开发 / Phase 0 冒烟。接入 Casdoor 后必须改成 JWT：        ║
                ║    oa.security.mode: JWT                                         ║
                ║    spring.security.oauth2.resourceserver.jwt.issuer-uri: ...     ║
                ╚══════════════════════════════════════════════════════════════════╝""");
        }
        return http.build();
    }

    /**
     * CORS。
     *
     * <p>★ 之前只有 {@code .cors(Customizer.withDefaults())} 而<b>没有任何
     * CorsConfigurationSource bean</b> —— 那等于"启用 CORS 处理但没有允许任何来源"，
     * 实测 preflight 直接 403 "Invalid CORS request"。前端从 5473 直连 8400 一定失败，
     * 而失败发生在浏览器侧，后端日志上什么都看不到。
     *
     * <p><b>主路径仍然是同源反代</b>（dev 走 vite proxy、prod 走 nginx），
     * 那样根本不产生跨源请求。这份配置是给"直连联调"和"移动端从别的域打过来"兜底的，
     * 所以允许的来源<b>必须显式列举</b>，不能用 {@code *}：
     * 带凭据的请求在 {@code allowCredentials=true} 时用 {@code *} 会被浏览器直接拒绝，
     * 而且通配来源意味着任何网页都能拿用户的 token 打我们的接口。
     */
    @Bean
    public org.springframework.web.cors.CorsConfigurationSource corsConfigurationSource(
            OaSecurityProperties props) {
        var cfg = new org.springframework.web.cors.CorsConfiguration();
        cfg.setAllowedOriginPatterns(props.getAllowedOrigins());
        cfg.setAllowedMethods(java.util.List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        cfg.setAllowedHeaders(java.util.List.of("*"));
        // 前端要读权限版本号来判断"权限刚被改了"，必须显式暴露 —— 默认只有 6 个简单响应头可读。
        cfg.setExposedHeaders(java.util.List.of("X-OA-Perm-Version", "Content-Disposition"));
        cfg.setAllowCredentials(true);
        cfg.setMaxAge(1800L);
        var src = new org.springframework.web.cors.UrlBasedCorsConfigurationSource();
        src.registerCorsConfiguration("/**", cfg);
        log.info("CORS 允许来源：{}（主路径仍应走同源反代，这只是直连联调的兜底）",
                props.getAllowedOrigins());
        return src;
    }
}
