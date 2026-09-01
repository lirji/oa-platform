package com.lrj.oa.iam.infrastructure.cache;

import com.lrj.oa.common.cache.CacheInvalidation;
import com.lrj.oa.common.context.TenantContext;
import com.lrj.oa.iam.application.PermissionCatalog;
import com.lrj.oa.iam.application.PermissionSnapshotBuilder;
import com.lrj.oa.iam.domain.PermissionEpochInvalidation;
import com.lrj.oa.iam.domain.PermissionSnapshot;
import com.lrj.oa.iam.infrastructure.mapper.PermVersionMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class PermissionEngineTenantCacheTest {

    @AfterEach void clearTenant() { TenantContext.clear(); }

    @Test
    @SuppressWarnings("unchecked")
    void tenantMessageEvictsOnlyItsTenantAndOlderEpochIsIgnored() {
        PermissionSnapshotBuilder builder = mock(PermissionSnapshotBuilder.class);
        PermissionCatalog catalog = mock(PermissionCatalog.class);
        PermVersionMapper versions = mock(PermVersionMapper.class);
        ObjectProvider<StringRedisTemplate> redis = mock(ObjectProvider.class);
        ObjectProvider<MeterRegistry> meters = mock(ObjectProvider.class);
        when(redis.getIfAvailable()).thenReturn(null);
        when(meters.getIfAvailable()).thenReturn(null);
        when(versions.currentEpoch(1L)).thenReturn(1L);
        when(versions.currentEpoch(2L)).thenReturn(1L);

        Map<Long, Long> truth = new ConcurrentHashMap<>(Map.of(1L, 1L, 2L, 1L));
        AtomicInteger builds = new AtomicInteger();
        when(builder.build("u1")).thenAnswer(ignored -> {
            long tenantId = TenantContext.get();
            builds.incrementAndGet();
            return PermissionSnapshot.builder("u1").epoch(truth.get(tenantId)).userVersion(0)
                    .abacEnabled(false).expireAt(System.currentTimeMillis() + 60_000).build();
        });

        PermissionEngine engine = new PermissionEngine(builder, catalog, versions, redis, meters,
                100, 60_000, false, 60, false, 0, false, false);

        TenantContext.set(1L);
        engine.snapshot("u1");
        TenantContext.set(2L);
        engine.snapshot("u1");
        assertThat(builds).hasValue(2);

        truth.put(1L, 2L);
        String key = new PermissionEpochInvalidation(1L, 2L, "e1", "role-change").encode();
        engine.onInvalidation(new CacheInvalidation(CacheInvalidation.TYPE_PERM_EPOCH, key, "other"));

        TenantContext.set(2L);
        engine.snapshot("u1");
        assertThat(builds).as("tenant 2 remains warm").hasValue(2);
        TenantContext.set(1L);
        assertThat(engine.snapshot("u1").epoch()).isEqualTo(2L);
        assertThat(builds).hasValue(3);

        String older = new PermissionEpochInvalidation(1L, 1L, "e0", "late").encode();
        engine.onInvalidation(new CacheInvalidation(CacheInvalidation.TYPE_PERM_EPOCH, older, "other"));
        engine.snapshot("u1");
        assertThat(builds).as("late epoch must not evict fresh data").hasValue(3);
        engine.shutdown();
    }

    @Test
    void payloadCodecRejectsMalformedValues() {
        assertThat(PermissionEpochInvalidation.decode("v1|bad|2|id|")).isEmpty();
        assertThat(PermissionEpochInvalidation.decode("")).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void rollingUpgradeFenceObservesLegacyWriterAndOldMessageForcesRefresh() {
        PermissionSnapshotBuilder builder = mock(PermissionSnapshotBuilder.class);
        PermissionCatalog catalog = mock(PermissionCatalog.class);
        PermVersionMapper versions = mock(PermVersionMapper.class);
        ObjectProvider<StringRedisTemplate> redis = mock(ObjectProvider.class);
        ObjectProvider<MeterRegistry> meters = mock(ObjectProvider.class);
        when(redis.getIfAvailable()).thenReturn(null);
        when(meters.getIfAvailable()).thenReturn(null);
        when(versions.currentEpoch(1L)).thenReturn(5L);
        when(builder.build("u1")).thenAnswer(ignored -> PermissionSnapshot.builder("u1")
                .epoch(versions.currentLegacyEpoch()).userVersion(0).abacEnabled(false)
                .expireAt(System.currentTimeMillis() + 60_000).build());
        // Builder consumes one legacy value per rebuild; reserve 9 for first build and 10 for second.
        when(versions.currentLegacyEpoch()).thenReturn(9L, 9L, 10L, 10L);

        PermissionEngine engine = new PermissionEngine(builder, catalog, versions, redis, meters,
                100, 60_000, false, 60, false, 0, false, true);
        TenantContext.set(1L);
        assertThat(engine.snapshot("u1").epoch()).isEqualTo(9L);

        engine.onInvalidation(new CacheInvalidation(CacheInvalidation.TYPE_PERM_EPOCH, "", "old-node"));
        assertThat(engine.snapshot("u1").epoch()).isEqualTo(10L);
        verify(builder, times(2)).build("u1");
        engine.shutdown();
    }
}
