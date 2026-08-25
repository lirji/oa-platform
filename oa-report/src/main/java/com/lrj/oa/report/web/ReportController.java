package com.lrj.oa.report.web;

import com.lrj.oa.common.api.Result;
import com.lrj.oa.report.application.AuditService;
import com.lrj.oa.report.application.DashboardService;
import com.lrj.oa.security.annotation.RequiresPerm;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 报表与审计 REST。 */
@RestController
@RequestMapping("/api/v1/report")
public class ReportController {

    private final DashboardService dashboard;
    private final AuditService audit;

    public ReportController(DashboardService dashboard, AuditService audit) {
        this.dashboard = dashboard;
        this.audit = audit;
    }

    @GetMapping("/overview")
    @RequiresPerm("oa:report:view")
    public Result<Map<String, Object>> overview() {
        // 从 Controller 经 Spring 代理分别进入四个 @DataScope 方法，避免同类自调用绕过切面。
        return Result.ok(Map.of(
                "headcountTop", dashboard.headcount(null, 5),
                "approval", dashboard.approvalEfficiency(),
                "attendance", dashboard.attendanceSummary(7),
                "asset", dashboard.assetSummary()));
    }

    @GetMapping("/headcount")
    @RequiresPerm("oa:report:view")
    public Result<List<Map<String, Object>>> headcount(@RequestParam(required = false) String orgPath,
                                                       @RequestParam(defaultValue = "50") int limit) {
        return Result.ok(dashboard.headcount(orgPath, limit));
    }

    @GetMapping("/approval-efficiency")
    @RequiresPerm("oa:report:view")
    public Result<List<Map<String, Object>>> approval() { return Result.ok(dashboard.approvalEfficiency()); }

    @GetMapping("/attendance")
    @RequiresPerm("oa:report:view")
    public Result<List<Map<String, Object>>> attendance(@RequestParam(defaultValue = "30") int days) {
        return Result.ok(dashboard.attendanceSummary(days));
    }

    /**
     * 审计查询。
     *
     * <p>★ 需要<b>临时提权</b>（{@code elevation = true}）：审计日志里有全公司谁做过什么，
     * 是权限最高的一类只读数据。持有永久授权还不够，必须走一次 JIT 提权 ——
     * 让"我现在要查审计"成为一个有记录、有时限的动作，而不是一个人默默常驻的能力。
     */
    @GetMapping("/audit")
    @RequiresPerm(value = "oa:audit:view", elevation = true)
    public Result<List<Map<String, Object>>> auditQuery(@RequestParam(required = false) String actorId,
                                                        @RequestParam(required = false) String action,
                                                        @RequestParam(required = false) Boolean deniedOnly,
                                                        @RequestParam(defaultValue = "50") int limit) {
        return Result.ok(audit.query(actorId, action, deniedOnly, limit));
    }

    /**
     * 审计写入计数。
     *
     * <p>用 {@code oa:report:view} 而不是 {@code oa:audit:view}：后者带 require_elevation，
     * 而这里只返回两个计数器、不暴露任何审计内容。把运维观测也锁在提权后面，
     * 会让"审计队列是不是在丢日志"这种必须能随时看的指标反而看不到。
     * 权限分级要按<b>暴露了什么</b>划，不是按<b>属于哪个模块</b>划。
     */
    @GetMapping("/audit/stats")
    @RequiresPerm("oa:report:view")
    public Result<Map<String, Object>> auditStats() {
        return Result.ok(Map.of("written", audit.writtenCount(), "dropped", audit.droppedCount()));
    }

    /** 冒烟与运维用：把异步队列里的审计立刻落库，避免"记了但还没落盘"造成误判。 */
    @PostMapping("/audit/flush")
    @RequiresPerm("oa:report:view")
    public Result<Void> auditFlush() { audit.flushNow(); return Result.ok(); }
}
