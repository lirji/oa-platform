package com.lrj.oa.iam.application;

import com.lrj.oa.common.cache.CacheInvalidation;
import com.lrj.oa.common.cache.InvalidationBus;
import com.lrj.oa.iam.infrastructure.cache.PermissionEngine;
import com.lrj.oa.iam.infrastructure.mapper.PermVersionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/** USER_GROUP/ABAC 写侧共用的全局收权协议。 */
@Component
public class IamInvalidationService {
    private static final Logger log = LoggerFactory.getLogger(IamInvalidationService.class);
    private final PermVersionMapper versions;
    private final PermissionEngine engine;
    private final InvalidationBus bus;

    public IamInvalidationService(PermVersionMapper versions, PermissionEngine engine,
                                  ObjectProvider<InvalidationBus> busProvider) {
        this.versions = versions;
        this.engine = engine;
        this.bus = busProvider.getIfAvailable();
    }

    public void all(String reason) {
        versions.bumpEpoch();
        engine.evictAllLocal();
        if (bus != null) bus.publish(CacheInvalidation.TYPE_PERM_EPOCH, "");
        log.info("USER_GROUP/ABAC 推进全局权限纪元：{}", reason);
    }
}
