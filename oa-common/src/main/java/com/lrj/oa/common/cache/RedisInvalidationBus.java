package com.lrj.oa.common.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.UUID;

/**
 * Redis Pub/Sub 实现。频道 {@code oa:cache:invalidate}。
 *
 * <p>失效消息<b>可以丢</b>：丢了最坏是等到 TTL 或版本轮询兜底，不会读到错误结果 ——
 * 因为快照里带着 epoch/version，读取时会与真值比对。正因如此才敢用 Pub/Sub 而不是 Kafka。
 */
public class RedisInvalidationBus implements InvalidationBus, MessageListener {

    public static final String CHANNEL = "oa:cache:invalidate";

    private static final Logger log = LoggerFactory.getLogger(RedisInvalidationBus.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final ApplicationEventPublisher events;
    private final String nodeId = UUID.randomUUID().toString().substring(0, 8);

    /**
     * 刻意<b>不</b>复用应用级 ObjectMapper：失效消息是内部协议，
     * 不应该被业务侧的 Jackson 定制（比如敏感字段脱敏序列化器）改变形状，
     * 也不该因此把自己卷进 ObjectMapper 的 Bean 构造链里。
     */
    public RedisInvalidationBus(StringRedisTemplate redis, ApplicationEventPublisher events) {
        this.redis = redis;
        this.json = new ObjectMapper();
        this.events = events;
        log.info("缓存失效总线已启用，本节点 id = {}", nodeId);
    }

    @Override
    public void publish(String type, String key) {
        try {
            redis.convertAndSend(CHANNEL, json.writeValueAsString(new CacheInvalidation(type, key, nodeId)));
        } catch (Exception e) {
            // 通知失败不能影响业务：本节点已经失效过了，其它节点靠轮询兜底
            log.warn("发布缓存失效通知失败 type={} key={}: {}", type, key, e.toString());
        }
    }

    @Override
    public void onMessage(org.springframework.data.redis.connection.Message message, byte[] pattern) {
        try {
            CacheInvalidation msg = json.readValue(message.getBody(), CacheInvalidation.class);
            if (nodeId.equals(msg.nodeId())) return;      // 自己发的，本节点早就处理过了
            log.debug("收到跨节点缓存失效: {} {}", msg.type(), msg.key());
            events.publishEvent(msg);
        } catch (Exception e) {
            log.warn("解析缓存失效消息失败: {}", e.toString());
        }
    }

    public String nodeId() { return nodeId; }
}
