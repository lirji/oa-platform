package com.lrj.oa.org.infrastructure.cache;

import com.lrj.oa.common.context.TenantContext;
import com.lrj.oa.org.api.event.OrgTreeChangedEvent;
import com.lrj.oa.org.domain.OrgTreeSnapshot;
import com.lrj.oa.org.domain.OrgUnit;
import com.lrj.oa.org.infrastructure.mapper.OrgUnitMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 组织树内存缓存。持有一份不可变快照，整体替换（COW），读侧完全无锁。
 *
 * <p><b>三条收敛路径</b>：
 * <ol>
 *   <li>本节点自己改的 —— {@link OrgTreeChangedEvent} 在<b>事务提交后</b>触发，立即重建；</li>
 *   <li>别的节点改的 —— 轮询 {@code org_tree_version}，默认 5 秒，即跨节点收敛上界；</li>
 *   <li>Phase 2 会接入 Redis Pub/Sub 失效总线，把上界从秒级降到毫秒级。本轮先不引，
 *       因为权限失效也要同一条总线，两者应当一次性建好而不是各建一条。</li>
 * </ol>
 */
@Component
public class OrgTreeCache {

    private static final Logger log = LoggerFactory.getLogger(OrgTreeCache.class);

    private final OrgUnitMapper orgUnitMapper;
    private final ReentrantLock rebuildLock = new ReentrantLock();

    private volatile OrgTreeSnapshot current = OrgTreeSnapshot.empty();

    public OrgTreeCache(OrgUnitMapper orgUnitMapper) { this.orgUnitMapper = orgUnitMapper; }

    /** 读快照。热路径调用，必须零开销。 */
    public OrgTreeSnapshot snapshot() { return current; }

    @PostConstruct
    public void init() { rebuild("startup"); }

    /**
     * 用 {@code AFTER_COMMIT} 而不是普通 {@code @EventListener}：
     * 事务还没提交就重建，会读到旧数据并把陈旧快照当成新版本装上，
     * 之后版本号相等再也不会自动纠正 —— 这是个安静且难查的 bug。
     *
     * <p>{@code fallbackExecution = true} 不能省：没有它，在<b>无事务</b>上下文里发布的事件
     * （批量导入工具、运维脚本）会被静默丢弃，缓存永远等不到刷新。
     */
    @TransactionalEventListener(fallbackExecution = true)
    public void onTreeChanged(OrgTreeChangedEvent event) {
        log.debug("组织树变更事件: reason={} orgId={}", event.reason(), event.orgId());
        rebuild("event:" + event.reason());
    }

    @Scheduled(fixedDelayString = "${oa.org.tree-cache.poll-ms:5000}")
    public void pollVersion() {
        try {
            long dbVersion = orgUnitMapper.currentTreeVersion();
            if (dbVersion != current.version()) {
                log.info("检测到组织树版本变化 {} -> {}，重建内存快照", current.version(), dbVersion);
                rebuild("poll");
            }
        } catch (Exception e) {
            log.warn("轮询组织树版本失败，沿用现有快照: {}", e.toString());
        }
    }

    public void rebuild(String reason) {
        rebuildLock.lock();
        try {
            // ★ 顺序不能反：必须【先读版本，再读数据】。
            // 反过来的话，若变更恰好落在两次读之间，就会把旧数据打上新版本号，
            // 之后版本比对永远相等，快照永久陈旧。
            // 现在这个顺序最差只是多重建一次，永远不会卡在陈旧状态。
            long version = orgUnitMapper.currentTreeVersion();
            List<OrgUnit> units = orgUnitMapper.selectAllForTree(TenantContext.DEFAULT_TENANT_ID);
            long t0 = System.nanoTime();
            OrgTreeSnapshot next = OrgTreeSnapshot.build(version, units);
            current = next;
            log.info("组织树快照重建完成: reason={} 节点={} 版本={} 耗时={}ms",
                    reason, next.size(), version, (System.nanoTime() - t0) / 1_000_000);
        } catch (Exception e) {
            log.error("组织树快照重建失败，沿用现有快照（{} 个节点）", current.size(), e);
        } finally {
            rebuildLock.unlock();
        }
    }
}
