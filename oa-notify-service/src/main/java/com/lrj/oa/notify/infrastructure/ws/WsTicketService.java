package com.lrj.oa.notify.infrastructure.ws;

import com.lrj.oa.notify.NotifyProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

/**
 * WebSocket 握手 ticket：256 bit 随机、短 TTL、Redis 原子取用即删。
 *
 * <p>ticket 不是 access token，不含用户信息；即使代理意外记录查询串，泄露窗口也只有几十秒，
 * 且第一位使用者会让它立即失效。Redis 不可用时不降级，握手 fail-closed。
 */
@Component
public class WsTicketService {

    static final String KEY_PREFIX = "oa:ws:ticket:";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();

    private final StringRedisTemplate redis;
    private final long ttlSeconds;

    public WsTicketService(StringRedisTemplate redis, NotifyProperties props) {
        this.redis = redis;
        this.ttlSeconds = props.getWs().getTicketTtlSeconds();
        if (ttlSeconds < 5 || ttlSeconds > 120) {
            throw new IllegalArgumentException("oa.notify.ws.ticket-ttl-seconds 必须在 5..120 秒");
        }
    }

    public IssuedTicket issue(String userId, long tenantId) {
        String payload = tenantId + "." + URL_ENCODER.encodeToString(userId.getBytes(StandardCharsets.UTF_8));
        for (int attempt = 0; attempt < 3; attempt++) {
            byte[] bytes = new byte[32];
            RANDOM.nextBytes(bytes);
            String ticket = URL_ENCODER.encodeToString(bytes);
            Boolean inserted = redis.opsForValue().setIfAbsent(
                    KEY_PREFIX + ticket, payload, Duration.ofSeconds(ttlSeconds));
            if (Boolean.TRUE.equals(inserted)) return new IssuedTicket(ticket, ttlSeconds);
        }
        throw new IllegalStateException("无法生成唯一 WebSocket ticket");
    }

    /** getAndDelete 在 Redis 端原子执行；并发重放最多只有一个握手能得到身份。 */
    public Optional<TicketIdentity> consume(String ticket) {
        if (ticket == null || !ticket.matches("[A-Za-z0-9_-]{43}")) return Optional.empty();
        String payload = redis.opsForValue().getAndDelete(KEY_PREFIX + ticket);
        if (payload == null) return Optional.empty();
        int dot = payload.indexOf('.');
        if (dot <= 0 || dot == payload.length() - 1) return Optional.empty();
        try {
            long tenantId = Long.parseLong(payload.substring(0, dot));
            String userId = new String(URL_DECODER.decode(payload.substring(dot + 1)), StandardCharsets.UTF_8);
            return userId.isBlank() ? Optional.empty() : Optional.of(new TicketIdentity(userId, tenantId));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    public record IssuedTicket(String ticket, long expiresInSeconds) {}
    public record TicketIdentity(String userId, long tenantId) {}
}
