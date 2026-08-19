package com.lrj.oa.common.cache;

/**
 * 跨节点缓存失效总线。
 *
 * <p><b>契约（很重要，写错方向会出安全问题）</b>：
 * 调用方<b>必须自己先完成本节点的失效</b>，再调 {@link #publish}。
 * 本总线只负责通知<b>其它</b>节点 —— 收到自己发的消息会被跳过。
 *
 * <p>这样设计的好处：Redis 挂了也只是跨节点收敛退化到轮询兜底，
 * 本节点的正确性完全不依赖它。
 */
public interface InvalidationBus {

    void publish(String type, String key);

    default void publishGlobal(String type) { publish(type, ""); }
}
