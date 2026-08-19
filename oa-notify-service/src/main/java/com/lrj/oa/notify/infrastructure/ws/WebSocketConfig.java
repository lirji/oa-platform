package com.lrj.oa.notify.infrastructure.ws;

import com.lrj.oa.security.config.OaSecurityProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * 长连端点注册 + 握手期身份装配。
 *
 * <p>身份必须在<b>握手时</b>定死并放进 session attributes：连接建立之后再去解析身份，
 * 就得在每条消息上重做一次，而长连的生命周期可能跨越 token 过期。
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final NotifyWebSocketHandler handler;
    private final OaSecurityProperties securityProps;

    public WebSocketConfig(NotifyWebSocketHandler handler, OaSecurityProperties securityProps) {
        this.handler = handler;
        this.securityProps = securityProps;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws")
                .addInterceptors(new IdentityHandshakeInterceptor(securityProps))
                .setAllowedOriginPatterns("*");
    }

    /** 握手拦截器：把身份放进 attributes，供 handler 使用。 */
    static class IdentityHandshakeInterceptor implements HandshakeInterceptor {

        private final OaSecurityProperties props;

        IdentityHandshakeInterceptor(OaSecurityProperties props) {
            this.props = props;
        }

        @Override
        public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                       WebSocketHandler wsHandler, Map<String, Object> attributes) {
            String userId = null;
            if (request instanceof ServletServerHttpRequest servlet) {
                if (isDev()) {
                    // DEV：与 REST 侧的 X-OA-User 同源，允许 query 参数是为了浏览器能直连
                    userId = servlet.getServletRequest().getParameter("userId");
                    if (userId == null) userId = servlet.getServletRequest().getHeader("X-OA-User");
                } else {
                    // JWT：只认已认证的 principal。★ 绝不回退到 query 参数——
                    // 那等于任何人带上别人的 userId 就能收别人的消息。
                    var p = servlet.getServletRequest().getUserPrincipal();
                    userId = p == null ? null : p.getName();
                }
            }
            if (userId == null || userId.isBlank()) return false;   // 握手直接失败，不建立连接
            attributes.put(NotifyWebSocketHandler.ATTR_USER, userId);
            return true;
        }

        private boolean isDev() {
            return props.getMode() == null || "DEV".equalsIgnoreCase(props.getMode().name());
        }

        @Override
        public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Exception exception) {
        }
    }
}
