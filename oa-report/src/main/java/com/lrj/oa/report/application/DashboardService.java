package com.lrj.oa.report.application;

import com.lrj.oa.report.infrastructure.mapper.DashboardMapper;
import com.lrj.oa.security.annotation.DataScope;
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

    private final DashboardMapper mapper;

    public DashboardService(DashboardMapper mapper) { this.mapper = mapper; }

    /** 编制人效。orgPathPrefix 非空时只看那棵子树（前缀匹配，不展开 id 列表）。 */
    @DataScope(permission = "oa:report:view", table = "oa_sys.v_headcount", alias = "h",
            module = "report", userColumn = "org_path")
    public List<Map<String, Object>> headcount(String orgPathPrefix, int limit) {
        return mapper.headcount(orgPathPrefix == null || orgPathPrefix.isBlank() ? null : orgPathPrefix, cap(limit));
    }

    @DataScope(permission = "oa:report:view", table = "oa_sys.v_approval_efficiency_scoped", alias = "a",
            module = "report", userColumn = "applicant_user_id")
    public List<Map<String, Object>> approvalEfficiency() {
        return mapper.approvalEfficiency();
    }

    @DataScope(permission = "oa:report:view", table = "oa_sys.v_attendance_summary_scoped", alias = "a",
            module = "report", userColumn = "user_id")
    public List<Map<String, Object>> attendanceSummary(int days) {
        return mapper.attendanceSummary(Math.max(days, 1));
    }

    @DataScope(permission = "oa:report:view", table = "oa_sys.v_asset_summary_scoped", alias = "a",
            module = "report", userColumn = "holder_id")
    public List<Map<String, Object>> assetSummary() {
        return mapper.assetSummary();
    }

    private static int cap(int n) { return Math.min(Math.max(n, 1), 200); }
}
