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
}
