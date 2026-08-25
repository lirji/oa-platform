package com.lrj.oa.admin.application;

import com.lrj.oa.admin.api.dto.AdminDtos;
import com.lrj.oa.common.api.ResultCode;
import com.lrj.oa.admin.infrastructure.mapper.AdminMappers;
import com.lrj.oa.common.exception.BusinessException;
import com.lrj.oa.common.context.TenantContext;
import com.lrj.oa.security.annotation.DataScope;
import com.lrj.oa.security.context.UserContext;
import com.lrj.oa.security.context.UserContextHolder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    private final AdminMappers.AssetQueryMapper assetQuery;
    private final AdminMappers.SupplyMapper supplies;

    public AssetService(AdminMappers.AssetQueryMapper assetQuery, AdminMappers.SupplyMapper supplies) {
        this.assetQuery = assetQuery;
        this.supplies = supplies;
    }

    /**
     * 资产列表。
     *
     * <p>★ 走 MyBatis 而不是 JdbcTemplate：{@code @DataScope} 的 SQL 改写发生在
     * MyBatis 拦截器里，手写 JdbcTemplate 会绕过它 —— 注解还在、过滤没了，
     * 是一种代码 review 时最容易放过的全量泄露。严格模式下 DataScopeAspect 会对
     * "设了却没人消费"直接拒绝，但正确的做法是一开始就用对路径。
     */
    @DataScope(permission = "oa:asset:read", table = "oa_admin.asset", module = "admin", alias = "a")
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
    @DataScope(permission = "oa:asset:claim", table = "oa_admin.asset", module = "admin", alias = "a")
    public void claim(long assetId, String remark) {
        UserContext ctx = UserContextHolder.require();
        String status = assetQuery.statusForUpdate(assetId);
        if (status == null) throw BusinessException.of(ResultCode.NOT_FOUND, "资产不存在");
        if (!"IDLE".equals(status)) {
            throw BusinessException.of(ResultCode.CONFLICT, "资产当前状态为 " + status + "，不可领用");
        }
        if (assetQuery.claim(assetId, ctx.userId()) == 0) {
            throw BusinessException.of(ResultCode.CONFLICT, "资产已被其他人领用");
        }
        assetQuery.insertTxn(assetId, "CLAIM", ctx.userId(), status, "IN_USE", remark);
    }

    @Transactional
    @DataScope(permission = "oa:asset:claim", table = "oa_admin.asset", module = "admin", alias = "a")
    public void giveBack(long assetId) {
        UserContext ctx = UserContextHolder.require();
        // 只能还自己持有的：条件带 holder_id，别人还不了你的资产。
        int n = assetQuery.giveBack(assetId, ctx.userId());
        if (n == 0) throw BusinessException.of(ResultCode.CONFLICT, "该资产不在你名下");
        assetQuery.insertTxn(assetId, "RETURN", ctx.userId(), "IN_USE", "IDLE", null);
    }

    public List<AdminDtos.SupplyView> supplies() {
        return supplies.list(TenantContext.get()).stream()
                .map(r -> new AdminDtos.SupplyView(r.id, r.code, r.name, r.unit, r.stock))
                .toList();
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
            n = supplies.take(TenantContext.get(), supplyId, qty);
        } catch (DataIntegrityViolationException e) {
            throw BusinessException.of(ResultCode.CONFLICT, "库存不足");
        }
        if (n == 0) throw BusinessException.of(ResultCode.CONFLICT, "库存不足或用品不存在");
        supplies.insertRequest(supplyId, ctx.userId(), qty, ctx.primaryOrgId(), ctx.primaryOrgPath());
    }
}
