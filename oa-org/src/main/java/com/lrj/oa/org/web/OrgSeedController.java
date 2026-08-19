package com.lrj.oa.org.web;

import com.lrj.oa.common.api.Result;
import com.lrj.oa.org.application.OrgSeedService;
import com.lrj.oa.security.annotation.RequiresPerm;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

/**
 * 万人级数据装载。
 *
 * <p>整个 Controller 挂在 {@code oa.org.seed.enabled} 开关下，默认<b>不装配</b> ——
 * 生产环境里这个类根本不会成为 Bean，而不是"有接口但但愿没人调"。
 */
@RestController
@RequestMapping("/api/v1/org/seed")
@ConditionalOnProperty(name = "oa.org.seed.enabled", havingValue = "true")
public class OrgSeedController {

    private final OrgSeedService seedService;

    public OrgSeedController(OrgSeedService seedService) { this.seedService = seedService; }

    @PostMapping
    @RequiresPerm("oa:org:admin")
    public Result<OrgSeedService.SeedResult> seed(@RequestParam(defaultValue = "3000") int orgs,
                                                  @RequestParam(defaultValue = "10000") int employees) {
        return Result.ok(seedService.seed(orgs, employees));
    }

    @DeleteMapping
    @RequiresPerm("oa:org:admin")
    public Result<Void> truncate() {
        seedService.truncateAll();
        return Result.ok();
    }
}
