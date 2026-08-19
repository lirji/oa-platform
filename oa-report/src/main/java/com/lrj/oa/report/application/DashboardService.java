package com.lrj.oa.report.application;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 管理驾驶舱。
 *
 * <p>全部走<b>只读视图</b>（{@code oa_sys.v_*}）。用视图而不是物化视图：
 * 万人级下这些聚合是秒级的，物化带来的刷新时机、陈旧度、并发刷新锁全是额外复杂度。
 * 真扛不住了再物化，那时也有真实数字支撑决策 —— 而不是现在凭想象优化。
 *
 * <p>⚠️ 驾驶舱是<b>汇总</b>视图，按设计对有权访问它的人展示全局数字，
 * 因此接口权限用 {@code oa:report:view}（只发给管理层），而不是靠数据权限逐行过滤。
 * 想要"部门经理只看自己部门"时，应当在查询侧用 org_path 前缀限制，而不是放开这个接口。
 */
@Service
public class DashboardService {

    private final JdbcTemplate jdbc;

    public DashboardService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** 编制人效。orgPathPrefix 非空时只看那棵子树（前缀匹配，不展开 id 列表）。 */
    public List<Map<String, Object>> headcount(String orgPathPrefix, int limit) {
        if (orgPathPrefix != null && !orgPathPrefix.isBlank()) {
            return jdbc.queryForList("SELECT * FROM oa_sys.v_headcount WHERE org_path LIKE ?"
                    + " ORDER BY headcount DESC LIMIT ?", orgPathPrefix + "%", cap(limit));
        }
        return jdbc.queryForList("SELECT * FROM oa_sys.v_headcount ORDER BY headcount DESC LIMIT ?", cap(limit));
    }

    public List<Map<String, Object>> approvalEfficiency() {
        return jdbc.queryForList("SELECT * FROM oa_sys.v_approval_efficiency ORDER BY total DESC");
    }

    public List<Map<String, Object>> attendanceSummary(int days) {
        return jdbc.queryForList("SELECT * FROM oa_sys.v_attendance_summary"
                + " WHERE work_date >= current_date - ?::int ORDER BY work_date DESC", Math.max(days, 1));
    }

    public List<Map<String, Object>> assetSummary() {
        return jdbc.queryForList("SELECT * FROM oa_sys.v_asset_summary ORDER BY category, status");
    }

    /** 一屏概览：驾驶舱首屏要的几个数字，一次查完，避免前端发六个请求。 */
    public Map<String, Object> overview() {
        return Map.of(
                "headcountTop", headcount(null, 5),
                "approval", approvalEfficiency(),
                "attendance", attendanceSummary(7),
                "asset", assetSummary());
    }

    private static int cap(int n) { return Math.min(Math.max(n, 1), 200); }
}
