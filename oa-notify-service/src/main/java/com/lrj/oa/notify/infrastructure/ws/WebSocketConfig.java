package com.lrj.oa.notify.infrastructure.ws;

import com.lrj.oa.security.config.OaSecurityProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private final WsTicketService tickets;

    public WebSocketConfig(NotifyWebSocketHandler handler, OaSecurityProperties securityProps,
                           WsTicketService tickets) {
        this.handler = handler;
        this.securityProps = securityProps;
        this.tickets = tickets;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws")
                .addInterceptors(new IdentityHandshakeInterceptor(securityProps, tickets))
                .setAllowedOrigins(securityProps.getAllowedOrigins().toArray(String[]::new));
    }

    /** 握手拦截器：把身份放进 attributes，供 handler 使用。 */
    static class IdentityHandshakeInterceptor implements HandshakeInterceptor {

        private static final Logger log = LoggerFactory.getLogger(IdentityHandshakeInterceptor.class);

        private final OaSecurityProperties props;
        private final WsTicketService tickets;

        IdentityHandshakeInterceptor(OaSecurityProperties props, WsTicketService tickets) {
            this.props = props;
            this.tickets = tickets;
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
                    // JWT：/ws 本身在 Spring Security 放行，身份只能来自先前用 Bearer token
                    // 换取的短期一次性 ticket。绝不接受 userId 或长期 access_token query。
                    String ticket = servlet.getServletRequest().getParameter("ticket");
                    try {
                        userId = tickets.consume(ticket).map(WsTicketService.TicketIdentity::userId).orElse(null);
                    } catch (RuntimeException ex) {
                        // Redis 故障必须 fail-closed；日志不包含 ticket 原文。
                        log.warn("WebSocket ticket 校验失败（已拒绝握手）: {}", ex.getClass().getSimpleName());
                        return false;
                    }
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
