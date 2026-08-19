package com.lrj.oa.flow.infrastructure.workflow;

import com.lrj.workflow.protocol.api.TaskView;
import com.lrj.workflow.sdk.WorkflowClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 接真 workflow-platform（:8300）的适配器。
 *
 * <p>⚠️ <b>已知缺口</b>：中台现有的 SDK 只有审方专用的 {@code completeReview}，
 * 通用审批需要中台补一个 {@code completeTask(tenant, taskId, outcome, variables, comment)} 端点
 * ——这是本项目对 workflow-platform 的<b>唯一改造请求</b>，只增不改，
 * 计划里列为 Phase 4 的前置依赖。补齐前这个适配器的办理路径会抛出明确异常，
 * 而不是静默降级成"看起来成功了"。
 */
@Component
@ConditionalOnProperty(name = "oa.flow.workflow.mode", havingValue = "REMOTE", matchIfMissing = true)
public class RemoteWorkflowGateway implements WorkflowGateway {

    private static final Logger log = LoggerFactory.getLogger(RemoteWorkflowGateway.class);
    private static final String TENANT = "oa";

    private final WorkflowClient client;

    public RemoteWorkflowGateway(WorkflowClient client) {
        this.client = client;
        log.info("流程网关接入 workflow-platform（tenant={}）", TENANT);
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
        throw new UnsupportedOperationException(
                "workflow-platform 尚未提供通用 completeTask 端点（现有 SDK 只有审方专用的 completeReview）。"
                        + " 补齐前请用 oa.flow.workflow.mode=LOCAL 走本地替身，或先给中台增补该端点。");
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
