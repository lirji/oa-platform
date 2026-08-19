package com.lrj.oa.notify.infrastructure.ws;

import com.lrj.oa.notify.NotifyProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 在线长连注册表：userId → 该用户的全部会话（同一个人可能开了 PC + 手机 + 多标签页）。
 *
 * <p><b>发送为什么用 ReentrantLock 而不是 synchronized</b>：
 * {@code WebSocketSession.sendMessage} 不是线程安全的，必须串行化；而它内部做的是网络 I/O。
 * JDK21 虚拟线程一旦在 {@code synchronized} 块里阻塞就会 <b>pin 住载体线程</b> ——
 * 这正是 Phase 5 被 Caffeine 坑掉 30 倍吞吐的那件事（见 CLAUDE.md）。
 * {@code ReentrantLock} 阻塞时虚拟线程会正常卸载，不会钉住载体线程。
 *
 * <p>广播是这个服务最重的动作（万人公告），一旦在这里 pin，整个应用会一起停摆。
 */
@Component
public class SessionRegistry {

    private static final Logger log = LoggerFactory.getLogger(SessionRegistry.class);

    private final Map<String, Set<Entry>> byUser = new ConcurrentHashMap<>();
    private final AtomicLong total = new AtomicLong();
    private final AtomicLong pushed = new AtomicLong();
    private final AtomicLong pushFailed = new AtomicLong();
    private final int maxSessions;

    public SessionRegistry(NotifyProperties props) {
        this.maxSessions = props.getWs().getMaxSessions();
    }

    /** 每条会话配一把自己的锁：串行化只发生在同一条连接上，不同用户之间零竞争。 */
    record Entry(WebSocketSession session, ReentrantLock lock) {}

    public boolean register(String userId, WebSocketSession session) {
        if (total.get() >= maxSessions) {
            log.warn("长连数已达上限 {}，拒绝新连接 user={}", maxSessions, userId);
            return false;
        }
        byUser.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet())
                .add(new Entry(session, new ReentrantLock()));
        total.incrementAndGet();
        return true;
    }

    public void unregister(String userId, WebSocketSession session) {
        Set<Entry> set = byUser.get(userId);
        if (set == null) return;
        if (set.removeIf(e -> e.session().getId().equals(session.getId()))) {
            total.decrementAndGet();
        }
        if (set.isEmpty()) byUser.remove(userId, set);
    }

    public boolean online(String userId) {
        Set<Entry> s = byUser.get(userId);
        return s != null && !s.isEmpty();
    }

    /**
     * 推给某人的所有在线会话。
     *
     * @return 实际送达的会话数；0 表示人不在线（<b>不是失败</b>——站内信已落库，
     *         下次上线拉取即可。长连只是"更快"，不是"唯一"的送达通道）
     */
    public int push(String userId, String payload) {
        Set<Entry> set = byUser.get(userId);
        if (set == null || set.isEmpty()) return 0;
        int n = 0;
        for (Entry e : set) {
            if (!e.session().isOpen()) { unregister(userId, e.session()); continue; }
            e.lock().lock();
            try {
                e.session().sendMessage(new TextMessage(payload));
                n++;
                pushed.incrementAndGet();
            } catch (IOException | IllegalStateException ex) {
                pushFailed.incrementAndGet();
                // 连接坏了就摘掉，不重试：客户端会重连，重连后走"拉未读"补齐。
                unregister(userId, e.session());
            } finally {
                e.lock().unlock();
            }
        }
        return n;
    }

    public Map<String, Object> stats() {
        return Map.of("sessions", total.get(), "users", byUser.size(),
                "pushed", pushed.get(), "pushFailed", pushFailed.get(), "maxSessions", maxSessions);
    }
}
