package com.lrj.oa.flow.web;

import com.lrj.oa.common.api.Result;
import com.lrj.oa.flow.api.dto.MyApplicationView;
import com.lrj.oa.flow.api.dto.TodoView;
import com.lrj.oa.flow.application.ApprovalService;
import com.lrj.oa.flow.application.TodoService;
import com.lrj.oa.flow.application.WorkbenchService;
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

    private final TodoService todoService;
    private final ApprovalService approvalService;
    private final WorkbenchService workbenchService;

    public WorkbenchController(TodoService todoService, ApprovalService approvalService,
                               WorkbenchService workbenchService) {
        this.workbenchService = workbenchService;
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
    public Result<List<MyApplicationView>> myApplications(
            @RequestParam(required = false) String bizType,
            @RequestParam(defaultValue = "50") int limit) {
        String me = UserContextHolder.require().userId();
        // UNION ALL 两张表：请假有独立表（额度语义），其余 11 类在 business_doc。
        // 列名对齐后按 created_at 统一排序 —— 前端不该关心它们分别存在哪。
        return Result.ok(workbenchService.myApplications(me, bizType, limit));
    }
}
