package com.lrj.oa.iam.web;

import com.lrj.oa.common.api.Result;
import com.lrj.oa.security.annotation.RequiresPerm;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * 角色与权限点目录的只读出口。
 *
 * <p><b>为什么必须有</b>：授权（{@code POST /iam/grants}）与 JIT 提权
 * （{@code POST /iam/elevations}）都要传 {@code roleId}，而在此之前
 * <b>没有任何接口能列出角色</b> —— 前端只能把 4 个内置角色的 id 硬编码进代码。
 * 那些 id 是 {@code bigserial} 生成的，重跑迁移、手工插过角色都会偏移，
 * 硬编码的结果是"在开发机上好好的，换个环境就授权给了错误的角色"。
 *
 * <p>权限点目录同理：前端要按 code 渲染按钮，得知道有哪些 code、
 * 哪些需要提权、哪些是<b>目录里有但没有接口实现的孤儿</b>（status=DISABLED）——
 * 为不存在的功能渲染入口，点了 404，比没有那个入口更糟。
 */
@RestController
@RequestMapping("/api/v1/iam")
public class RoleController {

    private final JdbcTemplate jdbc;

    public RoleController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record RoleView(Long id, String code, String name, String type,
                           String defaultScope, boolean builtin, String remark) {}

    public record PermissionView(Long id, String code, String name, String type, String module,
                                 String route, String icon, int sortOrder,
                                 boolean requireElevation, boolean enabled, String remark) {}

    /** 全部启用中的角色。授权页与提权对话框的下拉数据源。 */
    @GetMapping("/roles")
    @RequiresPerm("oa:iam:view")
    public Result<List<RoleView>> roles() {
        List<RoleView> out = new ArrayList<>();
        jdbc.query("""
                SELECT id, code, name, type, default_scope, builtin, remark
                  FROM oa_iam.role WHERE status = 'ACTIVE' ORDER BY id
                """, (RowCallbackHandler) rs -> out.add(new RoleView(
                        rs.getLong("id"), rs.getString("code"), rs.getString("name"),
                        rs.getString("type"), rs.getString("default_scope"),
                        rs.getBoolean("builtin"), rs.getString("remark"))));
        return Result.ok(out);
    }

    /**
     * 我持有哪些角色 —— JIT 提权只能激活<b>已持有</b>的角色（sudo 的原义），
     * 而查别人的授权需要 {@code oa:iam:view}（普通员工没有）。
     * 没有这个接口，普通员工就永远不知道自己能提权到什么，自助提权形同虚设。
     */
    @GetMapping("/roles/mine")
    @RequiresPerm("oa:iam:elevate")
    public Result<List<RoleView>> myRoles() {
        String me = com.lrj.oa.security.context.UserContextHolder.require().userId();
        List<RoleView> out = new ArrayList<>();
        jdbc.query("""
                SELECT DISTINCT r.id, r.code, r.name, r.type, r.default_scope, r.builtin, r.remark
                  FROM oa_iam.role r
                  JOIN oa_iam.grant_record g ON g.role_id = r.id
                 WHERE g.subject_type = 'USER' AND g.subject_id = ?
                   AND g.revoked_at IS NULL
                   AND (g.valid_to IS NULL OR g.valid_to > now())
                   AND r.status = 'ACTIVE'
                 ORDER BY r.id
                """, (RowCallbackHandler) rs -> out.add(new RoleView(
                        rs.getLong("id"), rs.getString("code"), rs.getString("name"),
                        rs.getString("type"), rs.getString("default_scope"),
                        rs.getBoolean("builtin"), rs.getString("remark"))), me);
        return Result.ok(out);
    }

    /**
     * 权限点目录。前端据此生成 TS 常量（拼错的 code 会永远隐藏按钮，是个静默失败），
     * 并据 {@code enabled} 跳过尚无实现的孤儿 code。
     */
    @GetMapping("/permissions/catalog")
    @RequiresPerm("oa:iam:view")
    public Result<List<PermissionView>> catalog() {
        List<PermissionView> out = new ArrayList<>();
        jdbc.query("""
                SELECT id, code, name, type, module, route, icon, sort_order,
                       require_elevation, status, remark
                  FROM oa_iam.permission ORDER BY sort_order, id
                """, (RowCallbackHandler) rs -> out.add(new PermissionView(
                        rs.getLong("id"), rs.getString("code"), rs.getString("name"),
                        rs.getString("type"), rs.getString("module"), rs.getString("route"),
                        rs.getString("icon"), rs.getInt("sort_order"),
                        rs.getBoolean("require_elevation"),
                        !"DISABLED".equals(rs.getString("status")), rs.getString("remark"))));
        return Result.ok(out);
    }
}
