package com.lrj.oa.notify.infrastructure.ws;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * {@code /ws} 长连处理器。
 *
 * <p>身份来源与 REST 一致：DEV 模式取 {@code ?userId=}，JWT 模式消费由 Bearer REST 换得的
 * 短期一次性 ticket。
 * <b>DEV 的 query 参数在 JWT 模式下必须被忽略</b> —— 否则任何人都能带上别人的 userId
 * 连上来收别人的消息，是一个只在长连上出现、REST 侧的鉴权链完全看不到的越权。
 */
@Component
public class NotifyWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(NotifyWebSocketHandler.class);
    static final String ATTR_USER = "oaUserId";

    private final SessionRegistry registry;

    public NotifyWebSocketHandler(SessionRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String userId = (String) session.getAttributes().get(ATTR_USER);
        if (userId == null || userId.isBlank()) {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("missing identity"));
            return;
        }
        if (!registry.register(userId, session)) {
            session.close(CloseStatus.SERVICE_OVERLOAD.withReason("session limit reached"));
            return;
        }
        session.sendMessage(new TextMessage("{\"type\":\"WELCOME\",\"userId\":\"" + userId + "\"}"));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        // 客户端只用来发心跳。长连是【单向下行】通道：业务动作一律走 REST，
        // 免得同一件事出现两条语义可能不一致的入口。
        if ("ping".equals(message.getPayload())) {
            session.sendMessage(new TextMessage("{\"type\":\"PONG\"}"));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String userId = (String) session.getAttributes().get(ATTR_USER);
        if (userId != null) registry.unregister(userId, session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable ex) {
        String userId = (String) session.getAttributes().get(ATTR_USER);
        if (userId != null) registry.unregister(userId, session);
        log.debug("长连传输错误 user={}：{}", userId, ex.toString());
    }
}
