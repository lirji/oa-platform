package com.lrj.oa.flow.web;

import com.lrj.oa.common.api.Result;
import com.lrj.oa.flow.api.dto.TodoView;
import com.lrj.oa.flow.application.ApprovalService;
import com.lrj.oa.flow.application.TodoService;
import com.lrj.oa.flow.application.command.FlowCommands;
import com.lrj.oa.security.annotation.RequiresPerm;
import com.lrj.oa.security.context.UserContextHolder;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 工作台：待办列表与办理。 */
@RestController
@RequestMapping("/api/v1/flow/todos")
public class WorkbenchController {

    /** 我发起的一张单据。请假与 11 类通用单据在这里对齐成同一形状。 */
    public record MyApplication(String bizType, String docNo, String status, String title,
                                String summary, java.math.BigDecimal amount,
                                java.math.BigDecimal days, java.time.OffsetDateTime createdAt) {}

    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    private final TodoService todoService;
    private final ApprovalService approvalService;

    public WorkbenchController(TodoService todoService, ApprovalService approvalService,
                               org.springframework.jdbc.core.JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.todoService = todoService;
        this.approvalService = approvalService;
    }

    @GetMapping
    @RequiresPerm("oa:flow:todo:view")
    public Result<List<TodoView>> myTodos(@RequestParam(defaultValue = "50") int limit) {
        return Result.ok(todoService.myTodos(UserContextHolder.require().userId(), limit));
    }

    @GetMapping("/count")
    @RequiresPerm("oa:flow:todo:view")
    public Result<Map<String, Object>> count() {
        String me = UserContextHolder.require().userId();
        return Result.ok(Map.of(
                "pending", todoService.myPendingCount(me),
                "assignees", todoService.assigneesFor(me)));
    }

    @PostMapping("/{taskId}/complete")
    @RequiresPerm("oa:flow:todo:handle")
    public Result<Void> complete(@PathVariable String taskId, @Valid @RequestBody FlowCommands.CompleteTask cmd) {
        approvalService.completeTask(taskId, cmd.outcome(), cmd.comment(), cmd.onBehalfOf());
        return Result.ok();
    }

    /**
     * 我发起的单据（请假 + 11 类通用单据统一入口）。
     *
     * <p><b>为什么必须有</b>：在此之前提交一张单据后返回一个 requestNo，
     * 如果不记住它就<b>再也找不回来</b> —— 没有任何接口能列出"我提过什么"。
     * 用户提完单只能等审批结果自己找上门，中间连查进度都做不到。
     *
     * <p>合并两张表而不是让前端调两次：对用户来说"我发起的"就是一个列表，
     * 按时间倒序；请假单和报销单在这件事上没有区别。
     */
    @GetMapping("/mine")
    @RequiresPerm("oa:flow:todo:view")
    public Result<List<MyApplication>> myApplications(
            @RequestParam(required = false) String bizType,
            @RequestParam(defaultValue = "50") int limit) {
        String me = UserContextHolder.require().userId();
        int cap = Math.min(Math.max(limit, 1), 200);
        // UNION ALL 两张表：请假有独立表（额度语义），其余 11 类在 business_doc。
        // 列名对齐后按 created_at 统一排序 —— 前端不该关心它们分别存在哪。
        List<MyApplication> rows = jdbc.query("""
                SELECT * FROM (
                    SELECT 'LEAVE' AS biz_type, r.request_no AS doc_no, r.status,
                           t.name || ' ' || r.days || ' 天' AS title,
                           r.reason AS summary, NULL::numeric AS amount, r.days,
                           r.created_at
                      FROM oa_flow.leave_request r
                      JOIN oa_flow.leave_type t ON t.id = r.leave_type_id
                     WHERE r.user_id = ?
                    UNION ALL
                    SELECT d.biz_type, d.doc_no, d.status, d.title, d.summary,
                           d.amount, d.days, d.created_at
                      FROM oa_flow.business_doc d
                     WHERE d.applicant_id = ?
                ) x
                WHERE (?::text IS NULL OR x.biz_type = ?::text)
                ORDER BY x.created_at DESC
                LIMIT ?
                """, (rs, i) -> new MyApplication(
                        rs.getString("biz_type"), rs.getString("doc_no"), rs.getString("status"),
                        rs.getString("title"), rs.getString("summary"),
                        rs.getBigDecimal("amount"), rs.getBigDecimal("days"),
                        rs.getObject("created_at", java.time.OffsetDateTime.class)),
                me, me, bizType, bizType, cap);
        return Result.ok(rows);
    }
}
