package com.lrj.oa.iam.application;

import com.lrj.oa.common.cache.CacheInvalidation;
import com.lrj.oa.common.cache.InvalidationBus;
import com.lrj.oa.iam.infrastructure.cache.PermissionEngine;
import com.lrj.oa.iam.infrastructure.mapper.PermVersionMapper;
import com.lrj.oa.org.api.event.EmployeeAssignmentChangedEvent;
import com.lrj.oa.org.api.event.OrgTreeChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 组织树一变，数据范围就变 —— 权限快照必须跟着作废。
 *
 * <p>典型场景：把某个部门从 A 事业部移到 B 事业部，A 的负责人应当<b>立刻</b>看不到它的数据。
 * 这是"改组织立即生效"里最容易被忽略的一半：大家都记得改角色要刷缓存，却忘了改组织也要。
 */
@Component
public class OrgChangeListener {

    private static final Logger log = LoggerFactory.getLogger(OrgChangeListener.class);

    private final PermVersionMapper versionMapper;
    private final PermissionEngine engine;
    private final com.lrj.oa.iam.infrastructure.mapper.GrantMapper grantMapper;
    private final InvalidationBus bus;

    public OrgChangeListener(PermVersionMapper versionMapper, PermissionEngine engine,
                             com.lrj.oa.iam.infrastructure.mapper.GrantMapper grantMapper,
                             ObjectProvider<InvalidationBus> busProvider) {
        this.versionMapper = versionMapper;
        this.engine = engine;
        this.grantMapper = grantMapper;
        this.bus = busProvider.getIfAvailable();
    }

    /**
     * 任职关系变了（调岗 / 兼岗 / 离职）→ 该用户的数据范围随之改变，快照必须作废。
     *
     * <p>离职额外做一件事：<b>撤销该账号的全部授权</b>。
     * 只让快照失效是不够的 —— 授权还在库里，等哪天这个 userId 被复用（或账号被重新启用），
     * 权限就会"复活"。离职是收权动作，必须落到授权本身。
     */
    @TransactionalEventListener(fallbackExecution = true)
    public void onAssignmentChanged(EmployeeAssignmentChangedEvent event) {
        String userId = event.userId();
        if (event.left()) {
            int revoked = 0;
            long tenantId = com.lrj.oa.common.context.TenantContext.get();
            for (var g : grantMapper.selectBySubject(tenantId, "USER", userId)) {
                revoked += grantMapper.revoke(tenantId,
                        g.getId(), "system", "员工离职自动回收");
            }
            versionMapper.bumpEpoch();
            engine.evictAllLocal();
            if (bus != null) bus.publish(CacheInvalidation.TYPE_PERM_EPOCH, "");
            log.warn("员工 {} 离职，已撤销其 {} 条授权", userId, revoked);
            return;
        }
        // 任职变化既可能加权也可能收权；Pub/Sub 丢失时仅 bump userVersion 无法让热路径发现。
        // 统一推进全局 epoch，保证调岗/关闭兼岗带来的范围收缩在 1 秒内可靠生效。
        versionMapper.bumpEpoch();
        engine.evictAllLocal();
        if (bus != null) bus.publish(CacheInvalidation.TYPE_PERM_EPOCH, "");
        log.info("任职变更（{}）推进全局权限纪元，可靠作废 {} 的旧范围", event.reason(), userId);
    }

    @TransactionalEventListener(fallbackExecution = true)
    public void onOrgTreeChanged(OrgTreeChangedEvent event) {
        versionMapper.bumpEpoch();
        engine.evictAllLocal();
        if (bus != null) bus.publish(CacheInvalidation.TYPE_PERM_EPOCH, "");
        log.info("组织树变更（{}）连带作废全部权限快照", event.reason());
    }
}
