package com.lrj.oa.common.id;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * 号段领取。
 *
 * <p>必须是<b>独立事务</b>（{@code REQUIRES_NEW}）：号段推进不能跟着业务事务回滚 ——
 * 否则一张提交失败的单据会把号段退回去，下一张单据拿到<b>同一批号码</b>，
 * 撞上单号唯一约束才暴露。号码浪费一段无所谓，重复才是问题。
 *
 * <p>注意：{@code @Transactional} 标在 MyBatis Mapper 接口上是<b>无效</b>的 ——
 * Mapper 由 MyBatis 代理，不走 Spring 的事务代理。必须像这样放在 Spring Bean 的方法上。
 * 这里直接用 JdbcTemplate，免得为一张两列的表再引一个 Mapper 接口。
 */
@Service
public class SegmentAllocator {

    private final JdbcTemplate jdbc;

    public SegmentAllocator(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** 领一整段（step 个号），返回 max_id 与 step。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Map<String, Object> allocate(String bizTag) {
        ensureTag(bizTag, 1000);
        return jdbc.queryForMap("""
                UPDATE oa_sys.id_segment SET max_id = max_id + step, updated_at = now()
                 WHERE biz_tag = ?
                RETURNING max_id, step
                """, bizTag);
    }

    /** 只领一个号（要求严格连续的场景，如公文文号）。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public long allocateOne(String bizTag) {
        ensureTag(bizTag, 1);
        Long v = jdbc.queryForObject("""
                UPDATE oa_sys.id_segment SET max_id = max_id + 1, updated_at = now()
                 WHERE biz_tag = ?
                RETURNING max_id
                """, Long.class, bizTag);
        return v == null ? 0L : v;
    }

    /** 自动注册未知 bizTag：新增一类单据不该还要记得先去插一行种子数据。 */
    private void ensureTag(String bizTag, int step) {
        jdbc.update("INSERT INTO oa_sys.id_segment(biz_tag, max_id, step) VALUES (?, 0, ?)"
                + " ON CONFLICT (biz_tag) DO NOTHING", bizTag, step);
    }
}
