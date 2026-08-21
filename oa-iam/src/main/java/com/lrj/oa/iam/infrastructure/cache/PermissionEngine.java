package com.lrj.oa.iam.infrastructure.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.lrj.oa.common.cache.CacheInvalidation;
import com.lrj.oa.iam.application.PermissionCatalog;
import com.lrj.oa.iam.application.PermissionSnapshotBuilder;
import com.lrj.oa.iam.domain.PermissionSnapshot;
import com.lrj.oa.iam.infrastructure.mapper.PermVersionMapper;
import com.lrj.oa.security.model.DataScopeRule;
import com.lrj.oa.security.port.PermissionChecker;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import jakarta.annotation.PreDestroy;

/**
 * 判权引擎 —— {@link PermissionChecker} 的实现，也是热路径上唯一被调用的东西。
 *
 * <p><b>三级缓存</b>：L1 Caffeine（纳秒级）→ L2 Redis（约 1ms）→ L3 数据库重算（10–30ms）。
 * 命中 L1 时全程零 DB、零远程调用。
 *
 * <p><b>失效策略里有一个刻意的不对称设计</b>：
 * <ul>
 *   <li><b>收权（撤销、改角色权限、组织调整）走全局 epoch</b> —— 各节点 1 秒内全部作废，
 *       安全优先，哪怕代价是一次全量重算；</li>
 *   <li><b>授权（给某人加角色）只 bump 用户版本 + 本节点直接剔除 + 通知其它节点</b> ——
 *       即使通知丢了，最坏也只是新权限晚几分钟生效，<b>不构成安全问题</b>。</li>
 * </ul>
 * 这样热路径只需比对一个每秒刷新一次的全局 epoch，而不必为每个请求查一次用户版本。
 */
@Component
public class PermissionEngine implements PermissionChecker {

    private static final Logger log = LoggerFactory.getLogger(PermissionEngine.class);
    private static final String L2_PREFIX = "oa:perm:snap:";

    private final PermissionSnapshotBuilder builder;
    private final PermissionCatalog catalog;
    private final PermVersionMapper versionMapper;
    private final StringRedisTemplate redis;
    private final SnapshotCodec codec;
    private final boolean l2Enabled;
    private final long l2TtlSeconds;
    private final boolean shadowEnabled;
    private final double shadowRate;
    private final boolean abacEnabled;

    private final Cache<String, PermissionSnapshot> l1;

    /** 全局 epoch 的本地副本，最多陈旧 1 秒 —— 这 1 秒就是"收权生效"的延迟上界。 */
    private volatile long cachedEpoch = -1;
    private volatile long cachedEpochAt = 0;
    private static final long EPOCH_REFRESH_MS = 1000;

    private final AtomicLong l1Hits = new AtomicLong();
    private final AtomicLong l2Hits = new AtomicLong();
    private final AtomicLong rebuilds = new AtomicLong();
    private final AtomicLong shadowChecks = new AtomicLong();
    private final AtomicLong shadowMismatches = new AtomicLong();

    /**
     * 影子校验专用线程池。<b>必须异步</b>：一次校验要做完整的数据库重算（约 5ms），
     * 同步执行等于给 1% 的【全部】请求加 5ms —— 判权 P99 会直接被它顶穿。
     * 有界队列 + 丢弃策略：校验是诊断手段，宁可漏采样也不能拖慢或压垮业务线程。
     */
    private final ThreadPoolExecutor shadowExecutor = new ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(256),
            r -> { Thread t = new Thread(r, "oa-shadow-verify"); t.setDaemon(true); return t; },
            new ThreadPoolExecutor.DiscardPolicy());

    public PermissionEngine(PermissionSnapshotBuilder builder, PermissionCatalog catalog,
                            PermVersionMapper versionMapper,
                            ObjectProvider<StringRedisTemplate> redisProvider,
                            ObjectProvider<MeterRegistry> meterProvider,
                            @Value("${oa.iam.cache.l1-max-size:20000}") int l1MaxSize,
                            @Value("${oa.iam.cache.ttl-ms:300000}") long ttlMs,
                            @Value("${oa.iam.cache.l2-enabled:true}") boolean l2Enabled,
                            @Value("${oa.iam.cache.l2-ttl-seconds:1800}") long l2TtlSeconds,
                            @Value("${oa.iam.shadow-verify.enabled:false}") boolean shadowEnabled,
                            @Value("${oa.iam.shadow-verify.sample-rate:0.01}") double shadowRate,
                            @Value("${oa.iam.abac.enabled:false}") boolean abacEnabled) {
        this.builder = builder;
        this.catalog = catalog;
        this.versionMapper = versionMapper;
        this.redis = redisProvider.getIfAvailable();
        this.codec = new SnapshotCodec(new ObjectMapper());
        this.l2Enabled = l2Enabled && this.redis != null;
        this.l2TtlSeconds = l2TtlSeconds;
        this.shadowEnabled = shadowEnabled;
        this.shadowRate = shadowRate;
        this.abacEnabled = abacEnabled;
        this.l1 = Caffeine.newBuilder()
                .maximumSize(l1MaxSize)
                .expireAfterWrite(Duration.ofMillis(ttlMs))
                .build();

        MeterRegistry meters = meterProvider.getIfAvailable();
        if (meters != null) {
            meters.gauge("oa_perm_l1_hits", l1Hits, AtomicLong::get);
            meters.gauge("oa_perm_l2_hits", l2Hits, AtomicLong::get);
            meters.gauge("oa_perm_rebuilds", rebuilds, AtomicLong::get);
            // ★ 这个指标必须长期为 0；不为 0 说明缓存与真值漂移了
            meters.gauge("oa_perm_mismatch_total", shadowMismatches, AtomicLong::get);
        }
        log.info("判权引擎就绪：L1={} TTL={}ms L2={} 影子校验={}({}%)",
                l1MaxSize, ttlMs, this.l2Enabled ? "Redis" : "关闭",
                shadowEnabled ? "开" : "关", shadowRate * 100);
    }

    // ───────────────────────────────────────────── PermissionChecker

    @Override
    public boolean has(String userId, String permCode) {
        int permId = catalog.idOf(permCode);
        if (permId < 0) {
            // 注解写了一个权限点目录里没有的 code：拒绝，而不是放行。
            // 这类错配只会在权限点目录漏加时出现，静默放行就是越权。
            log.warn("权限点 {} 不在目录中，判定为拒绝（检查 V10 权限目录是否漏加）", permCode);
            return false;
        }
        PermissionSnapshot snap = snapshot(userId);
        boolean allowed = snap.has(permId);
        maybeShadowVerify(userId, permId, allowed);
        return allowed;
    }

    @Override
    public boolean hasAll(String userId, Collection<String> permCodes) {
        PermissionSnapshot snap = snapshot(userId);
        for (String c : permCodes) {
            int id = catalog.idOf(c);
            if (id < 0 || !snap.has(id)) return false;
        }
        return true;
    }

    @Override
    public boolean hasAny(String userId, Collection<String> permCodes) {
        PermissionSnapshot snap = snapshot(userId);
        for (String c : permCodes) {
            int id = catalog.idOf(c);
            if (id >= 0 && snap.has(id)) return true;
        }
        return false;
    }

    @Override
    public boolean isElevated(String userId, String permCode) {
        int permId = catalog.idOf(permCode);
        return permId >= 0 && snapshot(userId).isElevated(permId);
    }

    @Override
    public DataScopeRule dataScopeForPermission(String userId, String permissionCode) {
        int permId = catalog.idOf(permissionCode);
        return snapshot(userId).scopeOfPermission(permId);
    }

    @Override
    public Collection<String> delegators(String userId) {
        return snapshot(userId).delegators();
    }

    // ───────────────────────────────────────────── 缓存

    /** 取快照。热路径入口。 */
    public PermissionSnapshot snapshot(String userId) {
        long epoch = epoch();
        long now = System.currentTimeMillis();

        PermissionSnapshot s = l1.getIfPresent(userId);
        if (fresh(s, epoch, now)) { l1Hits.incrementAndGet(); return s; }

        if (l2Enabled) {
            s = readL2(userId);
            if (fresh(s, epoch, now)) {
                l1.put(userId, s);
                l2Hits.incrementAndGet();
                return s;
            }
        }

        s = builder.build(userId);
        rebuilds.incrementAndGet();
        l1.put(userId, s);
        writeL2(s);
        return s;
    }

    private boolean fresh(PermissionSnapshot s, long epoch, long now) {
        return s != null && s.epoch() == epoch && s.abacEnabled() == abacEnabled && !s.expired(now);
    }

    /** 全局 epoch，本地缓存 1 秒。收到失效通知时立刻作废重取。 */
    private long epoch() {
        long now = System.currentTimeMillis();
        if (cachedEpoch < 0 || now - cachedEpochAt > EPOCH_REFRESH_MS) {
            try {
                cachedEpoch = versionMapper.currentEpoch();
                cachedEpochAt = now;
            } catch (Exception e) {
                if (cachedEpoch < 0) throw e;   // 首次就失败，没法继续
                log.warn("读取权限 epoch 失败，沿用本地值 {}: {}", cachedEpoch, e.toString());
            }
        }
        return cachedEpoch;
    }

    private PermissionSnapshot readL2(String userId) {
        try {
            String payload = redis.opsForValue().get(L2_PREFIX + userId);
            return payload == null ? null : codec.decode(payload);
        } catch (Exception e) {
            log.debug("读取 L2 快照失败 user={}: {}", userId, e.toString());
            return null;
        }
    }

    private void writeL2(PermissionSnapshot s) {
        if (!l2Enabled) return;
        try {
            // L2 存活时间不能超过快照自身的有效期（临时授权边界可能比 TTL 更早）
            long ttl = Math.min(l2TtlSeconds, Math.max(1, (s.expireAt() - System.currentTimeMillis()) / 1000));
            redis.opsForValue().set(L2_PREFIX + s.userId(), codec.encode(s), Duration.ofSeconds(ttl));
        } catch (Exception e) {
            log.debug("写入 L2 快照失败 user={}: {}", s.userId(), e.toString());
        }
    }

    // ───────────────────────────────────────────── 失效

    /** 本节点立即剔除某人的缓存（L1 + L2）。调用方随后应通过总线通知其它节点。 */
    public void evictLocal(String userId) {
        l1.invalidate(userId);
        if (l2Enabled) {
            try { redis.delete(L2_PREFIX + userId); } catch (Exception ignored) { }
        }
    }

    /** 本节点全量失效。 */
    public void evictAllLocal() {
        l1.invalidateAll();
        cachedEpochAt = 0;      // 强制下次读 epoch 时回源
    }

    @EventListener
    public void onInvalidation(CacheInvalidation msg) {
        switch (msg.type()) {
            case CacheInvalidation.TYPE_PERM_EPOCH, CacheInvalidation.TYPE_ORG_TREE -> {
                log.info("收到全局权限失效通知（{}），清空本节点 L1", msg.type());
                evictAllLocal();
                catalog.reload();
            }
            case CacheInvalidation.TYPE_PERM_USER -> evictLocal(msg.key());
            default -> { }
        }
    }

    // ───────────────────────────────────────────── 影子校验

    /**
     * 按采样率把缓存结果与数据库重算结果比对。
     * "改权不生效"是安全事故，必须有一个能报警的兜底，而不是靠人肉发现。
     */
    private void maybeShadowVerify(String userId, int permId, boolean cachedAnswer) {
        if (!shadowEnabled || ThreadLocalRandom.current().nextDouble() >= shadowRate) return;
        shadowExecutor.execute(() -> {
            try {
                shadowChecks.incrementAndGet();
                boolean truth = builder.build(userId).has(permId);
                if (truth != cachedAnswer) {
                    shadowMismatches.incrementAndGet();
                    log.error("★ 权限影子校验不一致！user={} perm={}({}) 缓存={} 真值={}",
                            userId, permId, catalog.codeOf(permId), cachedAnswer, truth);
                }
            } catch (Exception e) {
                log.debug("影子校验执行失败: {}", e.toString());
            }
        });
    }

    @PreDestroy
    public void shutdown() { shadowExecutor.shutdownNow(); }

    public Map<String, Object> stats() {
        return Map.of(
                "l1Size", l1.estimatedSize(),
                "l1Hits", l1Hits.get(),
                "l2Hits", l2Hits.get(),
                "l2Enabled", l2Enabled,
                "rebuilds", rebuilds.get(),
                "epoch", epoch(),
                "shadowChecks", shadowChecks.get(),
                "shadowMismatches", shadowMismatches.get());
    }
}
