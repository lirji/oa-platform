package com.lrj.oa.flow.application;

import com.lrj.oa.common.context.TenantContext;
import com.lrj.oa.flow.api.dto.TodoView;
import com.lrj.oa.flow.infrastructure.mapper.TodoMapper;
import com.lrj.oa.flow.infrastructure.workflow.WorkflowGateway;
import com.lrj.oa.iam.api.DelegationRule;
import com.lrj.oa.iam.application.DelegationAuthorizationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 工作台待办。
 *
 * <p>读的是 {@code todo_item} 宽表 —— 首屏一条 SQL 出结果，不现场 join。
 * 宽表由两条路维护：事件/回调<b>投影</b>（快）+ 定时<b>对账</b>（准）。
 * 中台的生命周期事件契约上是 best-effort，所以只靠投影会漂，必须有对账兜底。
 *
 * <p>查询时把<b>我代理的人</b>一并算进办理人集合 —— 这就是委托代理在待办侧的落点。
 */
@Service
public class TodoService {

    private static final Logger log = LoggerFactory.getLogger(TodoService.class);

    private final TodoMapper todoMapper;
    private final WorkflowGateway gateway;
    private final DelegationAuthorizationService delegations;
    private final ApprovalService approvalService;

    public TodoService(TodoMapper todoMapper, WorkflowGateway gateway,
                       DelegationAuthorizationService delegations, ApprovalService approvalService) {
        this.todoMapper = todoMapper;
        this.gateway = gateway;
        this.delegations = delegations;
        this.approvalService = approvalService;
    }

    /** 我的待办 = 指派给我的 ∪ 指派给我代理的人的。 */
    public List<TodoView> myTodos(String userId, int limit) {
        List<DelegationRule> rules = delegations.activeRules(userId);
        Set<String> assignees = new LinkedHashSet<>();
        assignees.add(userId);
        rules.forEach(r -> assignees.add(r.delegatorUserId()));
        return todoMapper.selectPending(TenantContext.get(), assignees, 200).stream()
                .filter(t -> userId.equals(t.getAssigneeUserId()) || rules.stream().anyMatch(r ->
                        r.delegatorUserId().equals(t.getAssigneeUserId())
                                && r.matches(t.getProcessDefinitionKey(), t.getCandidateGroup())))
                .limit(Math.min(Math.max(limit, 1), 200))
                .toList();
    }

    public long myPendingCount(String userId) { return myTodos(userId, 200).size(); }

    public Set<String> assigneesFor(String userId) {
        Set<String> all = new LinkedHashSet<>();
        all.add(userId);
        delegations.activeRules(userId).forEach(r -> all.add(r.delegatorUserId()));
        return all;
    }

    /**
     * 与中台对账：把中台里存在、本地读模型漏掉的待办补回来。
     *
     * <p>只在接真中台时有意义 —— 本地替身模式下投影是同进程回调，不会丢。
     */
    @Scheduled(fixedDelayString = "${oa.flow.todo.reconcile-ms:60000}")
    public void reconcile() {
        if (!gateway.remote()) return;
        try {
            List<String> local = todoMapper.selectPendingTaskIds(TenantContext.DEFAULT_TENANT_ID);
            Set<String> localSet = new LinkedHashSet<>(local);
            List<WorkflowGateway.Task> remote = gateway.findTasks(null, null);
            List<WorkflowGateway.Task> missing = new ArrayList<>();
            for (WorkflowGateway.Task t : remote) if (!localSet.contains(t.taskId())) missing.add(t);
            missing.forEach(approvalService::projectTodo);
            if (!missing.isEmpty()) log.warn("待办对账补回 {} 条（生命周期事件是 best-effort，会漏）", missing.size());
        } catch (Exception e) {
            log.debug("待办对账失败（中台不可达？）: {}", e.toString());
        }
    }
}
