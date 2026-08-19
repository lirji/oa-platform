package com.lrj.oa.admin.application;

import com.lrj.oa.admin.api.dto.AdminDtos;
import com.lrj.oa.common.api.ResultCode;
import com.lrj.oa.admin.infrastructure.mapper.AdminMappers;
import com.lrj.oa.common.exception.BusinessException;
import com.lrj.oa.security.annotation.DataScope;
import com.lrj.oa.security.context.UserContext;
import com.lrj.oa.security.context.UserContextHolder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 资产与办公用品。
 *
 * <p>两种并发写点，用了两种不同的手段，理由都写在方法上：
 * 资产领用用<b>行锁 + 状态机</b>（量小、要看到"被谁抢先了"），
 * 用品领用用<b>条件更新 + CHECK 约束</b>（纯数量，行锁会把热门用品串行化）。
 */
@Service
public class AssetService {

    private final JdbcTemplate jdbc;
    private final AdminMappers.AssetQueryMapper assetQuery;

    public AssetService(JdbcTemplate jdbc, AdminMappers.AssetQueryMapper assetQuery) {
        this.jdbc = jdbc;
        this.assetQuery = assetQuery;
    }

    /**
     * 资产列表。
     *
     * <p>★ 走 MyBatis 而不是 JdbcTemplate：{@code @DataScope} 的 SQL 改写发生在
     * MyBatis 拦截器里，手写 JdbcTemplate 会绕过它 —— 注解还在、过滤没了，
     * 是一种代码 review 时最容易放过的全量泄露。DataScopeAspect 现在会对
     * "设了却没人消费"发告警，但正确的做法是一开始就用对路径。
     */
    @DataScope(module = "admin", alias = "a")
    public List<AdminDtos.AssetView> listAssets(String status, int limit) {
        return assetQuery.search(status, Math.min(Math.max(limit, 1), 500)).stream()
                .map(r -> new AdminDtos.AssetView(r.id, r.assetNo, r.name, r.category,
                        r.status, r.holderId, r.orgId))
                .toList();
    }

    /**
     * 领用资产。
     *
     * <p>用 {@code SELECT ... FOR UPDATE} + 状态机校验：资产是<b>单件</b>的，
     * 两个人同时点"领用"必须只有一个成功，而且失败那个要知道"已经被领走了"。
     * 量小（一件资产不会有几百人同时抢），行锁的代价可以忽略。
     */
    @Transactional
    public void claim(long assetId, String remark) {
        UserContext ctx = UserContextHolder.require();
        String status = jdbc.query("SELECT status FROM oa_admin.asset WHERE id = ? FOR UPDATE",
                rs -> rs.next() ? rs.getString(1) : null, assetId);
        if (status == null) throw BusinessException.of(ResultCode.NOT_FOUND, "资产不存在");
        if (!"IDLE".equals(status)) {
            throw BusinessException.of(ResultCode.CONFLICT, "资产当前状态为 " + status + "，不可领用");
        }
        jdbc.update("UPDATE oa_admin.asset SET status = 'IN_USE', holder_id = ? WHERE id = ?",
                ctx.userId(), assetId);
        jdbc.update("""
                INSERT INTO oa_admin.asset_txn(asset_id, action, actor_id, from_status, to_status, remark)
                VALUES (?, 'CLAIM', ?, ?, 'IN_USE', ?)
                """, assetId, ctx.userId(), status, remark);
    }

    @Transactional
    public void giveBack(long assetId) {
        UserContext ctx = UserContextHolder.require();
        // 只能还自己持有的：条件带 holder_id，别人还不了你的资产。
        int n = jdbc.update("UPDATE oa_admin.asset SET status = 'IDLE', holder_id = NULL"
                + " WHERE id = ? AND holder_id = ? AND status = 'IN_USE'", assetId, ctx.userId());
        if (n == 0) throw BusinessException.of(ResultCode.CONFLICT, "该资产不在你名下");
        jdbc.update("""
                INSERT INTO oa_admin.asset_txn(asset_id, action, actor_id, from_status, to_status)
                VALUES (?, 'RETURN', ?, 'IN_USE', 'IDLE')
                """, assetId, ctx.userId());
    }

    public List<AdminDtos.SupplyView> supplies() {
        List<AdminDtos.SupplyView> out = new ArrayList<>();
        jdbc.query("SELECT id, code, name, unit, stock FROM oa_admin.supply ORDER BY code",
                (RowCallbackHandler) rs -> out.add(new AdminDtos.SupplyView(rs.getLong("id"), rs.getString("code"),
                        rs.getString("name"), rs.getString("unit"), rs.getInt("stock"))));
        return out;
    }

    /**
     * 领用办公用品。
     *
     * <p>用<b>条件更新</b>（{@code WHERE stock >= ?}）而不是"先查库存再扣"：
     * 后者在并发下必然超发。条件更新把判断和扣减压进同一条语句，由数据库的行级
     * 原子性保证；影响行数为 0 就是库存不足。表上的 {@code CHECK (stock >= 0)}
     * 是最后一道 —— 万一有人绕过服务层直接改库，也不会留下负库存这种说不清的状态。
     */
    @Transactional
    public void requestSupply(long supplyId, int qty) {
        UserContext ctx = UserContextHolder.require();
        if (qty <= 0) throw BusinessException.of(ResultCode.BAD_REQUEST, "数量必须为正");
        int n;
        try {
            n = jdbc.update("UPDATE oa_admin.supply SET stock = stock - ? WHERE id = ? AND stock >= ?",
                    qty, supplyId, qty);
        } catch (DataIntegrityViolationException e) {
            throw BusinessException.of(ResultCode.CONFLICT, "库存不足");
        }
        if (n == 0) throw BusinessException.of(ResultCode.CONFLICT, "库存不足或用品不存在");
        jdbc.update("""
                INSERT INTO oa_admin.supply_request(supply_id, requester_id, qty, org_id, org_path)
                VALUES (?, ?, ?, ?, ?)
                """, supplyId, ctx.userId(), qty, ctx.primaryOrgId(), ctx.primaryOrgPath());
    }
}
