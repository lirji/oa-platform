package com.lrj.oa.iam.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lrj.oa.common.cache.CacheInvalidation;
import com.lrj.oa.common.cache.InvalidationBus;
import com.lrj.oa.common.context.TenantContext;
import com.lrj.oa.iam.domain.PermissionEpochInvalidation;
import com.lrj.oa.iam.infrastructure.cache.PermissionEngine;
import com.lrj.oa.iam.infrastructure.mapper.IamOutboxMapper;
import com.lrj.oa.iam.infrastructure.mapper.PermVersionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** IAM 角色、USER_GROUP、ABAC 写侧共用的租户收权与提交后通知协议。 */
@Component
public class IamInvalidationService {
    private static final Logger log = LoggerFactory.getLogger(IamInvalidationService.class);
    private final PermVersionMapper versions;
    private final PermissionEngine engine;
    private final InvalidationBus bus;
    private final IamOutboxMapper outbox;
    private final ObjectMapper json;

    public IamInvalidationService(PermVersionMapper versions, PermissionEngine engine,
                                  ObjectProvider<InvalidationBus> busProvider,
                                  IamOutboxMapper outbox, ObjectMapper json) {
        this.versions = versions;
        this.engine = engine;
        this.bus = busProvider.getIfAvailable();
        this.outbox = outbox;
        this.json = json;
    }

    public void all(String reason) {
        long tenantId = TenantContext.get();
        String eventId = UUID.randomUUID().toString();
        long epoch = versions.bumpEpoch(tenantId);
        publishAfterCommit(new PermissionEpochInvalidation(tenantId, epoch, eventId, reason));
        log.info("IAM 写操作推进租户权限纪元 tenant={} epoch={}：{}", tenantId, epoch, reason);
    }

    /** Role truth, epoch, and durable domain event are written in the caller's transaction. */
    public void roleChanged(long roleId, String changeType, String reason) {
        long tenantId = TenantContext.get();
        String eventId = UUID.randomUUID().toString();
        long epoch = versions.bumpEpoch(tenantId);
        String payload = roleEventPayload(eventId, tenantId, roleId, changeType, epoch, reason);
        outbox.append(eventId, tenantId, IamOutboxMapper.ROLE_CHANGED_TOPIC,
                tenantId + ":" + roleId, payload);
        publishAfterCommit(new PermissionEpochInvalidation(tenantId, epoch, eventId, reason));
        log.info("角色变更写入权限纪元与事件 tenant={} role={} epoch={} type={}",
                tenantId, roleId, epoch, changeType);
    }

    /** User-scoped additions do not need a tenant-wide cold start, but notifications still wait for commit. */
    public void user(String userId, String reason) {
        long tenantId = TenantContext.get();
        versions.bumpUserVersion(userId);
        afterCommit(() -> {
            engine.evictLocal(tenantId, userId);
            if (bus != null) bus.publish(CacheInvalidation.TYPE_PERM_USER, userId);
        });
        log.info("IAM 写操作推进用户权限版本 tenant={} user={}：{}", tenantId, userId, reason);
    }

    private void publishAfterCommit(PermissionEpochInvalidation invalidation) {
        Runnable action = () -> {
            engine.evictTenant(invalidation.tenantId(), invalidation.epoch());
            if (bus != null) bus.publish(CacheInvalidation.TYPE_PERM_EPOCH, invalidation.encode());
        };
        afterCommit(action);
    }

    private void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { action.run(); }
            });
        } else {
            action.run();
        }
    }

    private String roleEventPayload(String eventId, long tenantId, long roleId, String changeType,
                                    long epoch, String reason) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("schemaVersion", 1);
        payload.put("eventId", eventId);
        payload.put("eventType", "IAM_ROLE_CHANGED");
        payload.put("tenantId", tenantId);
        payload.put("roleId", roleId);
        payload.put("changeType", changeType);
        payload.put("epoch", epoch);
        payload.put("occurredAt", OffsetDateTime.now().toString());
        payload.put("reason", reason);
        try {
            return json.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize IAM role event", e);
        }
    }
}
