package com.lrj.oa.flow.infrastructure.workflow;

import com.lrj.workflow.protocol.api.CompleteTaskRequest;
import com.lrj.workflow.protocol.api.TaskView;
import com.lrj.workflow.sdk.WorkflowClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 接真 workflow-platform（:8300）的适配器。
 *
 * <p>办理走中台的通用端点 {@code POST /api/v1/tasks/{taskId}/complete}
 * （本项目对 workflow-platform 的唯一改造请求，已按"只增不改"补齐：审方专用的
 * {@code complete-review} 语义原封不动，通用审批另开一条路）。
 *
 * <p>注意中台的办理返回 <b>202 + PENDING_BUSINESS</b>，语义是"人工决定已受理"，
 * 不是"业务已落地"。OA 侧的额度扣减、待办投影由自己的事件链路完成，
 * 不能把这个 202 当成业务成功的凭据。
 */
@Component
@ConditionalOnProperty(name = "oa.flow.workflow.mode", havingValue = "REMOTE", matchIfMissing = true)
public class RemoteWorkflowGateway implements WorkflowGateway {

    private static final Logger log = LoggerFactory.getLogger(RemoteWorkflowGateway.class);
    private static final String TENANT = "oa";
    /** OA 全部审批共用一份通用编排（差异在 approverChain 里，不在 BPMN 里）。 */
    private static final String DEFINITION_KEY = "oaGenericApproval";

    private final WorkflowClient client;

    public RemoteWorkflowGateway(WorkflowClient client) {
        this.client = client;
        // ★ 启动即校验，别等到线上"待办不出现"才发现。
        // SDK 的 workflow.client.enabled 默认 false，此时注入的是 NoopWorkflowClient：
        // 查询返回空列表、不抛异常。REMOTE 模式配上 Noop 客户端 = 网关自称接了中台、
        // 实际上一条待办也查不到，且没有任何错误日志。这种"配置对了一半"必须在启动时就炸。
        if (client instanceof com.lrj.workflow.sdk.NoopWorkflowClient) {
            throw new IllegalStateException(
                    "oa.flow.workflow.mode=REMOTE 但 workflow-platform SDK 未启用"
                            + "（workflow.client.enabled=false → NoopWorkflowClient）。"
                            + " 置 workflow.client.enabled=true，或把 mode 改成 LOCAL 用本地替身。");
        }
        log.info("流程网关接入 workflow-platform（tenant={}，client={}）", TENANT,
                client.getClass().getSimpleName());
    }

    @Override
    public List<Task> findTasks(String definitionKey, String businessKey) {
        return client.findTasks(TENANT, definitionKey, businessKey).stream().map(RemoteWorkflowGateway::toTask).toList();
    }

    @Override
    public List<Task> findTasksByAssignee(String assignee) {
        // 中台的 /tasks/search 支持按办理人过滤；SDK 目前只暴露了 findTasks，
        // 因此这里退化为"取本租户全部待办再过滤"。任务量大时应推动 SDK 增加按人查询。
        return client.findTasks(TENANT, null, null).stream()
                .map(RemoteWorkflowGateway::toTask)
                .filter(t -> assignee.equals(t.assignee()))
                .toList();
    }

    @Override
    public void completeTask(String taskId, String outcome, String actor, String onBehalfOf,
                             String comment, Map<String, Object> variables) {
        // 代理办理的留痕：中台只认 actor 一个办理人身份，被代理人作为业务变量随行，
        // 由 OA 侧的 approval_node_log 落 on_behalf_of —— 代理关系是 OA 的语义，不该塞进中台模型。
        Map<String, Object> vars = new HashMap<>(variables == null ? Map.of() : variables);
        if (onBehalfOf != null && !onBehalfOf.isBlank()) {
            vars.put("onBehalfOf", onBehalfOf);
        }
        String actionId = client.completeTask(TENANT, taskId,
                new CompleteTaskRequest(outcome, comment, vars, actor, actor, null));
        log.info("中台办理受理 taskId={} outcome={} actionId={}", taskId, outcome, actionId);
    }

    @Override
    public java.util.Optional<ProcessInfo> findProcess(String businessKey) {
        var list = client.findProcesses(TENANT, DEFINITION_KEY, businessKey);
        if (list == null || list.isEmpty()) {
            return java.util.Optional.empty();
        }
        var v = list.get(0);
        return java.util.Optional.of(new ProcessInfo(v.processInstanceId(), v.businessKey(), v.running()));
    }

    @Override
    public void claim(String taskId, String userId) { client.claimTask(TENANT, taskId, userId); }

    @Override
    public void reassign(String taskId, String assignee) { client.reassignTask(TENANT, taskId, assignee); }

    @Override
    public boolean remote() { return true; }

    private static Task toTask(TaskView v) {
        return new Task(v.taskId(), v.processInstanceId(), v.processDefinitionKey(),
                v.businessKey(), v.name(), v.assignee(),
                v.candidateGroups() == null || v.candidateGroups().isEmpty() ? null : v.candidateGroups().get(0));
    }
}
