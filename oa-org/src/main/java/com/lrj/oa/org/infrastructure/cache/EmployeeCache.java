package com.lrj.oa.org.infrastructure.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.lrj.oa.org.api.dto.AssignmentView;
import com.lrj.oa.org.api.dto.EmployeeView;
import com.lrj.oa.org.api.event.EmployeeAssignmentChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 员工与任职关系的读缓存。
 *
 * <p><b>为什么需要</b>：Phase 5 的早高峰压测暴露出来 —— 一次请求要打约 11 次库，
 * 其中身份装配和权限快照重算<b>各自都在查同一个人的任职关系</b>。
 * 万人同时打卡时全是冷缓存，这些查询在 20 个连接的池子上排队，
 * 单请求延迟被推到几百毫秒。吞吐达标，但延迟难看，而且浪费的全是重复查询。
 *
 * <p>组织与任职是<b>低频变更、高频读取</b>的典型，正是该缓存的东西。
 * 失效走已有的 {@link EmployeeAssignmentChangedEvent} 与组织树变更事件，
 * 不需要新造一套失效机制。
 */
@Component
public class EmployeeCache {

    private static final Logger log = LoggerFactory.getLogger(EmployeeCache.class);

    private final Cache<String, EmployeeView> employees;
    private final Cache<String, List<AssignmentView>> assignments;

    public EmployeeCache(@Value("${oa.org.employee-cache.max-size:20000}") int maxSize,
                         @Value("${oa.org.employee-cache.ttl-ms:300000}") long ttlMs) {
        this.employees = Caffeine.newBuilder().maximumSize(maxSize)
                .expireAfterWrite(Duration.ofMillis(ttlMs)).recordStats().build();
        this.assignments = Caffeine.newBuilder().maximumSize(maxSize)
                .expireAfterWrite(Duration.ofMillis(ttlMs)).recordStats().build();
        log.info("员工读缓存就绪：max={} ttl={}ms", maxSize, ttlMs);
    }

    /**
     * ★★★ 必须用 get-then-put，<b>不能</b>用 {@code cache.get(key, loader)}。
     *
     * <p>{@code Caffeine.get(key, mappingFunction)} 底层是 {@code ConcurrentHashMap.computeIfAbsent}，
     * 它会持有 bin 上的 {@code synchronized} 锁来执行 mappingFunction。
     * 而 JDK21 的虚拟线程一旦在 {@code synchronized} 块里阻塞（这里是 JDBC 查询），
     * 就会 <b>pin 住载体线程</b> —— 这正是 Phase 0 硬门禁 A 测的那件事。
     *
     * <p>后果是灾难性的：万人冷启动时几百个虚拟线程各自加载不同的 key，
     * 载体线程被逐个钉死，吞吐从 555 QPS 塌到 17 QPS（Phase 5 实测）。
     * <b>加缓存反而比不加更慢</b>。
     *
     * <p>get-then-put 的代价是同一个 key 可能被并发加载多次（做了重复功），
     * 换来的是加载过程完全不持锁。对"每人一个 key"的访问模式，重复加载极少发生，
     * 这笔交换非常划算。
     */
    public EmployeeView employee(String userId, Function<String, EmployeeView> loader) {
        EmployeeView cached = employees.getIfPresent(userId);
        if (cached != null) return cached;
        EmployeeView loaded = loader.apply(userId);
        if (loaded != null) employees.put(userId, loaded);
        return loaded;
    }

    public List<AssignmentView> assignments(String userId, Function<String, List<AssignmentView>> loader) {
        List<AssignmentView> cached = assignments.getIfPresent(userId);
        if (cached != null) return cached;
        List<AssignmentView> loaded = loader.apply(userId);
        if (loaded != null) assignments.put(userId, loaded);
        return loaded;
    }

    @TransactionalEventListener(fallbackExecution = true)
    public void onAssignmentChanged(EmployeeAssignmentChangedEvent e) {
        employees.invalidate(e.userId());
        assignments.invalidate(e.userId());
    }

    /** 组织树一变，视图里的组织名/路径就全过期了。 */
    @EventListener
    public void onOrgTreeChanged(com.lrj.oa.org.api.event.OrgTreeChangedEvent e) {
        employees.invalidateAll();
        assignments.invalidateAll();
    }

    public void invalidate(String userId) {
        employees.invalidate(userId);
        assignments.invalidate(userId);
    }

    public Map<String, Object> stats() {
        return Map.of(
                "employeeSize", employees.estimatedSize(),
                "employeeHitRate", String.format("%.3f", employees.stats().hitRate()),
                "assignmentSize", assignments.estimatedSize(),
                "assignmentHitRate", String.format("%.3f", assignments.stats().hitRate()));
    }
}
