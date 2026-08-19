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

    private final TodoService todoService;
    private final ApprovalService approvalService;

    public WorkbenchController(TodoService todoService, ApprovalService approvalService) {
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
}
