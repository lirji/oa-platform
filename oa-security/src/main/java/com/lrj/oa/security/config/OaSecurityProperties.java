package com.lrj.oa.security.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "oa.security")
public class OaSecurityProperties {

    /**
     * 认证模式。
     * <ul>
     *   <li>{@code JWT} —— 校验 Casdoor 签发的 token（生产唯一合法值）；</li>
     *   <li>{@code DEV} —— 不校验，仅本地开发/Phase 0 冒烟用，启动时会打显眼 WARN。</li>
     * </ul>
     * 默认 JWT。DEV 必须由本地测试显式开启，避免漏配环境变量时以无认证模式上线。
     */
    private Mode mode = Mode.JWT;

    /** Casdoor JWKS；与 issuer 分开配置，容器可经 host.docker.internal 取 key。 */
    private String jwkSetUri = "http://localhost:8000/.well-known/jwks";

    /** token 的 iss 必须逐字符匹配。 */
    private String issuer = "http://localhost:8000";

    /** token 的 aud 必须包含该值；JWT 模式下不能为空。 */
    private String audience = "";

    /** 无需认证的路径（存活探针、OIDC 回调、静态资源等）。 */
    private List<String> publicPaths = new ArrayList<>(List.of(
            "/api/v1/system/ping",
            "/api/v1/notify/ping",
            "/api/v1/file/ping",
            "/api/v1/job/ping",
            "/actuator/health",
            "/actuator/health/**",
            "/actuator/info",
            "/actuator/prometheus",
            "/ws",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html"
    ));

    /**
     * 允许跨源的来源列表。默认只放本机前端的 dev / prod 端口。
     * 生产要加真实域名；<b>永远不要写 {@code *}</b> —— allowCredentials=true 时浏览器会拒绝，
     * 且通配等于任何网页都能带着用户凭据打我们的接口。
     */
    private List<String> allowedOrigins = new java.util.ArrayList<>(List.of(
            "http://localhost:5473", "http://127.0.0.1:5473",   // oa-console dev
            "http://localhost:8404", "http://127.0.0.1:8404",   // oa-console prod
            "http://localhost:5474", "http://127.0.0.1:5474",   // oa-mobile dev
            "http://localhost:8405", "http://127.0.0.1:8405"));  // oa-mobile prod

    public List<String> getAllowedOrigins() { return allowedOrigins; }
    public void setAllowedOrigins(List<String> v) { this.allowedOrigins = v; }

    public enum Mode { DEV, JWT }

    public Mode getMode() { return mode; }
    public void setMode(Mode mode) { this.mode = mode; }
    public String getJwkSetUri() { return jwkSetUri; }
    public void setJwkSetUri(String jwkSetUri) { this.jwkSetUri = jwkSetUri; }
    public String getIssuer() { return issuer; }
    public void setIssuer(String issuer) { this.issuer = issuer; }
    public String getAudience() { return audience; }
    public void setAudience(String audience) { this.audience = audience; }
    public List<String> getPublicPaths() { return publicPaths; }
    public void setPublicPaths(List<String> publicPaths) { this.publicPaths = publicPaths; }
}
