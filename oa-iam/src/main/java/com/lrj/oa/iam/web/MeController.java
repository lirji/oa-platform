package com.lrj.oa.iam.web;

import com.lrj.oa.common.api.Result;
import com.lrj.oa.iam.application.PermissionCatalog;
import com.lrj.oa.iam.domain.Permission;
import com.lrj.oa.iam.domain.PermissionSnapshot;
import com.lrj.oa.iam.infrastructure.cache.PermissionEngine;
import com.lrj.oa.security.annotation.PublicApi;
import com.lrj.oa.security.context.UserContext;
import com.lrj.oa.security.context.UserContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

/**
 * 前端权限渲染的数据来源。
 *
 * <p>这里下发的 permCodes 与菜单只用于<b>展示裁剪</b>，不是安全边界（ADR-0008）——
 * 前端藏起来的按钮，对应接口在服务端依然由 {@code @RequiresPerm} 拦着。
 *
 * <p>{@code version} 是给前端做增量刷新用的：权限变更后经 WebSocket 推新 version，
 * 前端比对不一致就重拉，页面不刷新即生效。
 */
@RestController
@RequestMapping("/api/v1/me")
public class MeController {

    private final PermissionEngine engine;
    private final PermissionCatalog catalog;

    public MeController(PermissionEngine engine, PermissionCatalog catalog) {
        this.engine = engine;
        this.catalog = catalog;
    }

    public record MenuNode(String code, String name, String icon, String route,
                           int sortOrder, List<MenuNode> children) {}

    public record MyPermissions(String userId, String username, Long employeeId,
                                Long primaryOrgId, String primaryOrgPath,
                                long version, List<String> permCodes, List<MenuNode> menus,
                                String dataScope, List<String> scopePrefixes,
                                List<String> delegators, List<String> elevatedCodes,
                                /** 每模块独立的数据范围。快照里一直有，之前没下发，
                                 *  前端就无法按模块提示"你在这个模块能看到多宽"。 */
                                Map<String, String> moduleScope) {}

    @GetMapping("/permissions")
    @PublicApi(reason = "只返回【调用者自己】的权限清单，不含他人数据；未认证时返回空清单")
    public Result<MyPermissions> myPermissions() {
        UserContext ctx = UserContextHolder.peek();
        if (ctx == null) {
            // ★ 未认证返回【空清单而不是 401】是刻意的（本接口 @PublicApi，登录页也要能调）。
            //   但前端必须判 userId == null 才知道"这是未认证"，否则会把它当成
            //   "我被撤销了所有权限"，表现为整站菜单清空却不跳登录。
            return Result.ok(new MyPermissions(null, null, null, null, null, 0,
                    List.of(), List.of(), "NONE", List.of(), List.of(), List.of(), Map.of()));
        }
        PermissionSnapshot snap = engine.snapshot(ctx.userId());

        List<String> codes = new ArrayList<>(snap.permCodes());
        Collections.sort(codes);

        List<String> elevated = new ArrayList<>();
        snap.elevatedBits().forEach((org.roaringbitmap.IntConsumer) id -> {
            String c = catalog.codeOf(id);
            if (c != null) elevated.add(c);
        });
        Collections.sort(elevated);

        return Result.ok(new MyPermissions(
                ctx.userId(), ctx.username(), ctx.employeeId(), ctx.primaryOrgId(), ctx.primaryOrgPath(),
                // epoch + userVersion 合成一个前端可直接比对的版本号
                snap.epoch() * 1_000_000L + snap.userVersion(),
                codes, buildMenus(snap),
                snap.mergedScope().type().name(),
                snap.mergedScope().pathPrefixes(),
                new ArrayList<>(snap.delegators()),
                elevated,
                moduleScopeOf(snap)));
    }

    /** 按权限点目录里的 MENU 类型过滤出可见菜单树。 */
    private List<MenuNode> buildMenus(PermissionSnapshot snap) {
        Map<Long, List<Permission>> byParent = new LinkedHashMap<>();
        for (Permission p : catalog.all()) {
            if (!"MENU".equals(p.getType())) continue;
            if (!snap.permCodes().contains(p.getCode())) continue;
            byParent.computeIfAbsent(p.getParentId() == null ? -1L : p.getParentId(), k -> new ArrayList<>()).add(p);
        }
        return toNodes(byParent, -1L);
    }

    private List<MenuNode> toNodes(Map<Long, List<Permission>> byParent, Long parentKey) {
        List<Permission> children = byParent.getOrDefault(parentKey, List.of());
        List<MenuNode> out = new ArrayList<>(children.size());
        for (Permission p : children) {
            out.add(new MenuNode(p.getCode(), p.getName(), p.getIcon(), p.getRoute(),
                    p.getSortOrder() == null ? 0 : p.getSortOrder(),
                    toNodes(byParent, p.getId())));
        }
        out.sort(Comparator.comparingInt(MenuNode::sortOrder));
        return out;
    }

    /** 每模块的数据范围。沙盘与"你在这个模块能看到多宽"的提示都要它。 */
    private static Map<String, String> moduleScopeOf(PermissionSnapshot snap) {
        Map<String, String> m = new LinkedHashMap<>();
        snap.moduleScope().forEach((k, v) -> m.put(k, v.type().name()));
        return m;
    }
}
