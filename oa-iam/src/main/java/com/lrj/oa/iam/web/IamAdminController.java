package com.lrj.oa.iam.web;

import com.lrj.oa.common.api.Result;
import com.lrj.oa.iam.application.GrantService;
import com.lrj.oa.iam.application.PermissionCatalog;
import com.lrj.oa.iam.application.PermissionSnapshotBuilder;
import com.lrj.oa.iam.domain.PermissionSnapshot;
import com.lrj.oa.iam.infrastructure.cache.PermissionEngine;
import com.lrj.oa.security.annotation.RequiresPerm;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 权限运维与<b>权限调试器后端</b>。
 *
 * <p>{@code /explain} 是整套权限体系里最有价值的一个端点：回答"为什么这个人能（不能）看到这个"。
 * Phase 3 的前端权限调试器页直接消费它。
 */
@RestController
@RequestMapping("/api/v1/iam/admin")
public class IamAdminController {

    private final PermissionEngine engine;
    private final PermissionSnapshotBuilder builder;
    private final PermissionCatalog catalog;
    private final GrantService grantService;

    public IamAdminController(PermissionEngine engine, PermissionSnapshotBuilder builder,
                              PermissionCatalog catalog, GrantService grantService) {
        this.engine = engine;
        this.builder = builder;
        this.catalog = catalog;
        this.grantService = grantService;
    }

    @GetMapping("/cache-stats")
    @RequiresPerm("oa:iam:admin")
    public Result<Map<String, Object>> cacheStats() {
        return Result.ok(engine.stats());
    }

    /**
     * 解释某个用户的判权结果：缓存里是什么、从数据库重算又是什么，两者是否一致。
     * 排查"改了权限怎么没生效"时，这一个接口就能定位问题在缓存还是在授权本身。
     */
    @GetMapping("/explain")
    @RequiresPerm("oa:iam:admin")
    public Result<Map<String, Object>> explain(@RequestParam String userId,
                                               @RequestParam(required = false) String permCode) {
        PermissionSnapshot cached = engine.snapshot(userId);
        PermissionSnapshot truth = builder.build(userId);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("userId", userId);
        out.put("cachedEpoch", cached.epoch());
        out.put("truthEpoch", truth.epoch());
        out.put("cachedPermCount", cached.permCount());
        out.put("truthPermCount", truth.permCount());
        out.put("consistent", cached.permCodes().equals(truth.permCodes()));
        out.put("dataScope", cached.mergedScope().type().name());
        out.put("scopePrefixes", cached.mergedScope().pathPrefixes());
        out.put("moduleScope", cached.moduleScope().entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, e -> e.getValue().type().name())));
        out.put("delegators", cached.delegators());
        out.put("expiresInMs", cached.expireAt() - System.currentTimeMillis());

        if (permCode != null) {
            int id = catalog.idOf(permCode);
            Map<String, Object> one = new LinkedHashMap<>();
            one.put("permCode", permCode);
            one.put("known", id >= 0);
            one.put("allowedByCache", id >= 0 && cached.has(id));
            one.put("allowedByRecompute", id >= 0 && truth.has(id));
            one.put("requiresElevation", id >= 0 && catalog.requiresElevation(id));
            one.put("currentlyElevated", id >= 0 && cached.isElevated(id));
            out.put("check", one);
        }
        return Result.ok(out);
    }

    /**
     * 判权热路径的实测延迟。
     *
     * <p>直接在服务端循环调用判权，避开 HTTP / JSON / 网络的噪声 ——
     * 验收标准写的是"判权 P99 < 1ms（不含网络）"，就该这么量。
     */
    @GetMapping("/bench")
    @RequiresPerm("oa:iam:admin")
    public Result<Map<String, Object>> bench(@RequestParam String userId,
                                             @RequestParam(defaultValue = "oa:employee:view") String permCode,
                                             @RequestParam(defaultValue = "20000") int iterations) {
        int n = Math.min(Math.max(iterations, 100), 200_000);
        // 预热：让 L1 装载好、JIT 编译过，否则量到的是冷启动而不是稳态
        for (int i = 0; i < 2000; i++) engine.has(userId, permCode);

        long[] samples = new long[n];
        for (int i = 0; i < n; i++) {
            long t0 = System.nanoTime();
            engine.has(userId, permCode);
            samples[i] = System.nanoTime() - t0;
        }
        Arrays.sort(samples);
        double toUs = 1000.0;
        return Result.ok(new LinkedHashMap<>(Map.of(
                "iterations", n,
                "p50Us", samples[(int) (n * 0.50)] / toUs,
                "p95Us", samples[(int) (n * 0.95)] / toUs,
                "p99Us", samples[(int) (n * 0.99)] / toUs,
                "maxUs", samples[n - 1] / toUs)));
    }

    /** 到期临时授权的回收。生产由 oa-job-service 定时调用，这里保留手动入口便于验证。 */
    @PostMapping("/reclaim-expired")
    @RequiresPerm("oa:iam:admin")
    public Result<Map<String, Object>> reclaim(@RequestParam(defaultValue = "500") int limit) {
        return Result.ok(Map.of("reclaimed", grantService.reclaimExpired(limit)));
    }
}
