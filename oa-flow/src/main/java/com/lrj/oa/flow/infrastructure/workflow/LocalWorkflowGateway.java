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
 * <p>存在的理由：中台前置条件（通用 {@code completeTask} 端点 + {@code oa-generic-approval-v1}
 * BPMN）现已就绪，REMOTE 是默认模式。但把审批链路的可测性绑死在"另一个系统必须在跑"上是脆弱的——
 * 单测、CI、以及中台停机时的回归，都需要一条不依赖外部进程的路径。
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
    /**
     * LOCAL 状态虽然在内存里，但 process/task id 会被投影进数据库。仅使用从 1 开始的序号，
     * 应用每次重启都会复用旧 id，随后按 process_instance_id 回查会命中多行。
     */
    private final String runId = UUID.randomUUID().toString().substring(0, 12);

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
            ║  中台侧前置条件均已就绪（通用 completeTask 端点 + oa-generic-     ║
            ║  approval-v1 BPMN），生产/联调请改成 REMOTE。                     ║
            ║  保留 LOCAL 的意义：中台不可用时业务链路仍可被测试覆盖。          ║
            ╚══════════════════════════════════════════════════════════════════╝""");
    }

    /** 由 ApprovalService 在发件箱投递成功后调用，模拟中台收到 start 命令。 */
    public String startProcess(String definitionKey, String businessKey, List<String> approverChain) {
        String pid = "local-pi-" + runId + "-" + seq.incrementAndGet();
        instances.put(pid, new Instance(pid, definitionKey, businessKey, List.copyOf(approverChain), 0));
        // 首任务要等 ApprovalService 把 pid 回绑审批实例后再投影，否则 todo 会永久缺少
        // instance/bizType/applicant 等字段。后续节点已经有绑定，可以立即回调。
        createTaskFor(pid, 0, false);
        return pid;
    }

    private void createTaskFor(String pid, int index, boolean notify) {
        Instance ins = instances.get(pid);
        if (ins == null || index >= ins.chain.size()) return;
        String taskId = "local-task-" + runId + "-" + seq.incrementAndGet();
        Task t = new Task(taskId, pid, ins.definitionKey, ins.businessKey,
                "第 " + (index + 1) + " 级审批", ins.chain.get(index), null);
        tasks.put(taskId, new TaskState(t, index));
        if (notify) callback.onTaskCreated(t);
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
            createTaskFor(pid, next, true);
        }
    }

    @Override
    public java.util.Optional<ProcessInfo> findProcess(String businessKey) {
        return instances.values().stream()
                .filter(i -> i.businessKey.equals(businessKey))
                .findFirst()
                .map(i -> new ProcessInfo(i.pid, i.businessKey, true));
        // 替身在流程结束时直接删实例，因此查不到 == 已结束。REMOTE 侧靠 running 字段区分，
        // 两者对 ApprovalService 的语义一致：Optional.empty() 或 running=false 都表示"不在跑了"。
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
