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

    /**
     * 以<b>他人视角</b>预览：目标用户能看到哪些菜单、持有哪些权限点、数据范围多宽。
     *
     * <p>★ 权限沙盘（FINAL_PLAN §16）右栏"该员工看到的菜单 + 能调的接口"就靠它。
     * 在此之前唯一的办法是 DEV 模式下带 {@code X-OA-User: 目标用户} 去调
     * {@code /me/permissions} —— 那在 JWT 模式下根本不通，
     * 意味着沙盘一上生产就失效。预览别人的权限本身是管理动作，
     * 就该是一个显式的、要 {@code oa:iam:admin} 的接口，而不是靠伪造身份。
     *
     * <p>返回的是<b>重算真值</b>而不是缓存：沙盘的用途正是排查"缓存对不对"，
     * 拿缓存去解释缓存等于什么都没说。
     */
    @GetMapping("/preview")
    @RequiresPerm("oa:iam:admin")
    public Result<Map<String, Object>> preview(@RequestParam String userId) {
        PermissionSnapshot truth = builder.build(userId);
        List<String> codes = new ArrayList<>(truth.permCodes());
        Collections.sort(codes);

        // 菜单：从权限点目录取 MENU 类型并按 route/icon/sort_order 组装。
        // 与 /me/permissions 用同一份目录，避免"我看到的菜单"和"预览别人看到的菜单"两套逻辑分叉。
        List<Map<String, Object>> menus = new ArrayList<>();
        for (com.lrj.oa.iam.domain.Permission p : catalog.all()) {
            if (!"MENU".equals(p.getType())) continue;
            if (!truth.permCodes().contains(p.getCode())) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", p.getCode());
            m.put("name", p.getName());
            m.put("route", p.getRoute());
            m.put("icon", p.getIcon());
            m.put("sortOrder", p.getSortOrder() == null ? 0 : p.getSortOrder());
            menus.add(m);
        }
        menus.sort(java.util.Comparator.comparingInt(m -> (Integer) m.get("sortOrder")));

        Map<String, String> moduleScope = new LinkedHashMap<>();
        truth.moduleScope().forEach((k, v) -> moduleScope.put(k, v.type().name()));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("userId", userId);
        out.put("permCodes", codes);
        out.put("permCount", codes.size());
        out.put("menus", menus);
        out.put("dataScope", truth.mergedScope().type().name());
        out.put("scopePrefixes", truth.mergedScope().pathPrefixes());
        out.put("moduleScope", moduleScope);
        out.put("delegators", truth.delegators());
        return Result.ok(out);
    }

    /**
     * 来源链：这个人的这条权限<b>是怎么来的</b>。
     *
     * <p>★ FINAL_PLAN §16 要求沙盘中栏回答"来自哪个部门继承 / 哪个角色 / 哪条临时授权"。
     * {@code /explain} 只回答"是不是"与"缓存对不对"，回答不了"为什么"。
     *
     * <p>这件事<b>必须由后端做</b>：继承规则（角色继承闭包 + 组织授权按 org_path 前缀
     * 向下继承 + 任职关系）全在后端。让前端多调几次 {@code /iam/grants} 自己拼，
     * 等于把判权语义在前端抄一遍 —— 两份实现迟早分叉，而分叉的那天，
     * 沙盘会理直气壮地解释错。
     */
    @GetMapping("/why")
    @RequiresPerm("oa:iam:admin")
    public Result<Map<String, Object>> why(@RequestParam String userId,
                                           @RequestParam String permCode) {
        int permId = catalog.idOf(permCode);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("userId", userId);
        out.put("permCode", permCode);
        out.put("known", permId >= 0);
        if (permId < 0) {
            // 目录里没有的 code 一律判拒（fail-closed）。这也是打错字的表现，
            // 直接说清楚，别让人对着一个恒为 false 的判定猜半天。
            out.put("allowed", false);
            out.put("sources", List.of());
            out.put("reason", "权限点目录中不存在该 code —— 目录里没有的一律判为拒绝");
            return Result.ok(out);
        }
        PermissionSnapshot truth = builder.build(userId);
        out.put("allowed", truth.has(permId));
        out.put("currentlyElevated", truth.isElevated(permId));
        out.put("sources", grantService.explainSources(userId, permCode));
        return Result.ok(out);
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
