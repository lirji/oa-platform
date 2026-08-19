package com.lrj.oa.flow.infrastructure.workflow;

import com.lrj.oa.common.api.ResultCode;
import com.lrj.oa.common.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * <b>中台的本地测试替身</b>，不是第二个流程引擎。
 *
 * <p>存在的理由：接真中台需要先给 workflow-platform 补一个通用 {@code completeTask} 端点
 * 并部署 {@code oa-generic-approval-v1} BPMN（计划里列为 Phase 4 的前置依赖）。
 * 在那之前，OA 侧的审批链路——审批人计算、发件箱、待办读模型、额度扣减——
 * 需要能端到端跑通并被冒烟验证，否则这些逻辑就只能靠读代码来相信。
 *
 * <p>它<b>只</b>按流程变量里的 {@code approverChain} 做顺序流转，语义与目标 BPMN 一致：
 * 逐级审批、任一环节驳回即终止。默认<b>不装配</b>（{@code oa.flow.workflow.mode=REMOTE}），
 * 中台就绪后无需改任何业务代码，换个配置即可。
 */
@Component
@ConditionalOnProperty(name = "oa.flow.workflow.mode", havingValue = "LOCAL")
public class LocalWorkflowGateway implements WorkflowGateway {

    private static final Logger log = LoggerFactory.getLogger(LocalWorkflowGateway.class);

    private final Map<String, TaskState> tasks = new ConcurrentHashMap<>();
    private final Map<String, Instance> instances = new ConcurrentHashMap<>();
    private final AtomicLong seq = new AtomicLong();

    /** 流程实例被推进后的回调（由 ApprovalService 注入，用于回写 OA 侧状态）。 */
    public interface Callback {
        void onTaskCreated(Task task);
        void onFinished(String processInstanceId, String outcome);
    }

    private volatile Callback callback = new Callback() {
        public void onTaskCreated(Task t) {}
        public void onFinished(String pid, String outcome) {}
    };

    public void setCallback(Callback cb) { this.callback = cb; }

    public LocalWorkflowGateway() {
        log.warn("""

            ╔══════════════════════════════════════════════════════════════════╗
            ║  oa.flow.workflow.mode=LOCAL —— 正在使用【中台测试替身】          ║
            ║  仅用于本地端到端验证。接入 workflow-platform 需要：              ║
            ║    1) 给中台补通用 completeTask 端点（只增不改）                  ║
            ║    2) 部署 oa-generic-approval-v1 BPMN（必须带 BPMNDI）           ║
            ║  之后把 mode 改成 REMOTE 即可，业务代码一行不用动。               ║
            ╚══════════════════════════════════════════════════════════════════╝""");
    }

    /** 由 ApprovalService 在发件箱投递成功后调用，模拟中台收到 start 命令。 */
    public String startProcess(String definitionKey, String businessKey, List<String> approverChain) {
        String pid = "local-pi-" + seq.incrementAndGet();
        instances.put(pid, new Instance(pid, definitionKey, businessKey, List.copyOf(approverChain), 0));
        createTaskFor(pid, 0);
        return pid;
    }

    private void createTaskFor(String pid, int index) {
        Instance ins = instances.get(pid);
        if (ins == null || index >= ins.chain.size()) return;
        String taskId = "local-task-" + seq.incrementAndGet();
        Task t = new Task(taskId, pid, ins.definitionKey, ins.businessKey,
                "第 " + (index + 1) + " 级审批", ins.chain.get(index), null);
        tasks.put(taskId, new TaskState(t, index));
        callback.onTaskCreated(t);
    }

    @Override
    public List<Task> findTasks(String definitionKey, String businessKey) {
        return tasks.values().stream().map(TaskState::task)
                .filter(t -> businessKey == null || businessKey.equals(t.businessKey()))
                .filter(t -> definitionKey == null || definitionKey.equals(t.processDefinitionKey()))
                .toList();
    }

    @Override
    public List<Task> findTasksByAssignee(String assignee) {
        return tasks.values().stream().map(TaskState::task)
                .filter(t -> assignee.equals(t.assignee())).toList();
    }

    @Override
    public void completeTask(String taskId, String outcome, String actor, String onBehalfOf,
                             String comment, Map<String, Object> variables) {
        TaskState st = tasks.remove(taskId);
        if (st == null) throw BusinessException.of(ResultCode.FLOW_TASK_NOT_FOUND, "待办不存在或已被处理: " + taskId);
        String pid = st.task().processInstanceId();
        Instance ins = instances.get(pid);
        if (ins == null) return;

        if ("REJECT".equalsIgnoreCase(outcome)) {
            instances.remove(pid);
            callback.onFinished(pid, "REJECTED");
            return;
        }
        int next = st.index() + 1;
        if (next >= ins.chain.size()) {
            instances.remove(pid);
            callback.onFinished(pid, "APPROVED");
        } else {
            createTaskFor(pid, next);
        }
    }

    @Override
    public void claim(String taskId, String userId) { reassign(taskId, userId); }

    @Override
    public void reassign(String taskId, String assignee) {
        tasks.computeIfPresent(taskId, (k, st) -> {
            Task t = st.task();
            return new TaskState(new Task(t.taskId(), t.processInstanceId(), t.processDefinitionKey(),
                    t.businessKey(), t.name(), assignee, t.candidateGroup()), st.index());
        });
    }

    @Override
    public boolean remote() { return false; }

    private record TaskState(Task task, int index) {}
    private record Instance(String pid, String definitionKey, String businessKey,
                            List<String> chain, int cursor) {}
}
