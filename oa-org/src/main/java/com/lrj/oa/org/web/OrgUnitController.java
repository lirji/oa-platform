package com.lrj.oa.org.web;

import com.lrj.oa.common.api.Result;
import com.lrj.oa.org.api.OrgQueryApi;
import com.lrj.oa.org.api.dto.OrgTreeNodeView;
import com.lrj.oa.org.api.dto.OrgUnitView;
import com.lrj.oa.org.application.OrgQueryService;
import com.lrj.oa.org.application.OrgUnitService;
import com.lrj.oa.org.application.command.OrgCommands;
import com.lrj.oa.security.annotation.RequiresPerm;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 组织单元管理。
 *
 * <p>每个 handler 都必须声明授权立场 —— {@code ControllerPermissionCoverageTest} 会让构建失败。
 * 注意本 Phase 只是<b>标注</b>：切面实现在 Phase 2，届时这些注解自动开始生效，无需改动本文件。
 */
@RestController
@RequestMapping("/api/v1/org/units")
public class OrgUnitController {

    private final OrgUnitService orgUnitService;
    private final OrgQueryApi orgQuery;
    private final OrgQueryService orgQueryService;

    public OrgUnitController(OrgUnitService orgUnitService, OrgQueryApi orgQuery,
                             OrgQueryService orgQueryService) {
        this.orgUnitService = orgUnitService;
        this.orgQuery = orgQuery;
        this.orgQueryService = orgQueryService;
    }

    @GetMapping("/tree")
    @RequiresPerm("oa:org:view")
    public Result<List<OrgTreeNodeView>> tree(@RequestParam(required = false) Long rootId,
                                              @RequestParam(required = false) Integer maxDepth) {
        return Result.ok(orgQuery.tree(rootId, maxDepth));
    }

    @GetMapping("/{orgId}")
    @RequiresPerm("oa:org:view")
    public Result<OrgUnitView> get(@PathVariable Long orgId) {
        return Result.ok(orgQuery.getOrg(orgId));
    }

    @GetMapping("/{orgId}/descendants")
    @RequiresPerm("oa:org:view")
    public Result<List<Long>> descendants(@PathVariable Long orgId) {
        return Result.ok(orgQuery.descendantIds(orgId));
    }

    @GetMapping("/{orgId}/ancestors")
    @RequiresPerm("oa:org:view")
    public Result<List<Long>> ancestors(@PathVariable Long orgId) {
        return Result.ok(orgQuery.ancestorIds(orgId));
    }

    /**
     * 给定一组组织，返回最小路径前缀集合。
     * 这是数据权限拼 SQL 时真正要用的东西，暴露出来便于 Phase 3 的权限调试器解释"为什么能看到这些行"。
     */
    @PostMapping("/path-prefixes")
    @RequiresPerm("oa:org:view")
    public Result<List<String>> pathPrefixes(@RequestBody List<Long> orgIds) {
        return Result.ok(orgQuery.minimalPathPrefixes(orgIds));
    }

    @PostMapping
    @RequiresPerm("oa:org:create")
    public Result<Long> create(@Valid @RequestBody OrgCommands.CreateOrg cmd) {
        return Result.ok(orgUnitService.create(cmd));
    }

    @PutMapping("/{orgId}")
    @RequiresPerm("oa:org:update")
    public Result<Void> update(@PathVariable Long orgId, @Valid @RequestBody OrgCommands.UpdateOrg cmd) {
        orgUnitService.update(orgId, cmd);
        return Result.ok();
    }

    @PutMapping("/{orgId}/parent")
    @RequiresPerm("oa:org:move")
    public Result<Void> move(@PathVariable Long orgId, @RequestBody OrgCommands.MoveOrg cmd) {
        orgUnitService.move(orgId, cmd.newParentId());
        return Result.ok();
    }

    @DeleteMapping("/{orgId}")
    @RequiresPerm("oa:org:dissolve")
    public Result<Void> dissolve(@PathVariable Long orgId) {
        orgUnitService.dissolve(orgId);
        return Result.ok();
    }

    /** 闭包表与物化路径的一致性自检。正常恒为 0；非 0 说明某次组织调整没走完事务。 */
    @GetMapping("/consistency")
    @RequiresPerm("oa:org:admin")
    public Result<Map<String, Object>> consistency() {
        long bad = orgUnitService.checkConsistency();
        return Result.ok(Map.of("inconsistencies", bad, "healthy", bad == 0));
    }

    /** 组织树内存快照状态。snapshotVersion 落后 dbVersion 说明本节点还没收敛。 */
    @GetMapping("/cache-stats")
    @RequiresPerm("oa:org:admin")
    public Result<Map<String, Object>> cacheStats() {
        return Result.ok(orgQueryService.cacheStats());
    }
}
