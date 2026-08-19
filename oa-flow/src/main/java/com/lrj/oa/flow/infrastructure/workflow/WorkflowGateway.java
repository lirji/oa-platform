package com.lrj.oa.flow.infrastructure.workflow;

import java.util.List;
import java.util.Map;

/**
 * 流程中台的出站端口。
 *
 * <p><b>发起</b>不在这里 —— 发起必须与业务写入同事务，走 {@code oa_outbox} → Kafka
 * （workflow-platform 已明确否决过同步 HTTP 发起：它破坏事务边界）。
 * 这个端口只承担<b>需要即时反馈</b>的动作：查待办、办理、认领、转办。
 */
public interface WorkflowGateway {

    /** 一条待办任务。字段对齐中台 {@code TaskView} 的语义子集。 */
    record Task(String taskId, String processInstanceId, String processDefinitionKey,
                String businessKey, String name, String assignee, String candidateGroup) {}

    /** 中台侧的流程实例状态。running=false 表示流程已走完（中台不会主动推这件事，只能问）。 */
    record ProcessInfo(String processInstanceId, String businessKey, boolean running) {}

    /**
     * 按业务单号查流程实例。两个用途：
     * ① 发起是异步的（发件箱 → Kafka），OA 提单时拿不到 processInstanceId，靠这个回绑；
     * ② 流程结束中台不会推事件，靠这个发现。
     */
    java.util.Optional<ProcessInfo> findProcess(String businessKey);

    List<Task> findTasks(String definitionKey, String businessKey);

    /** 列出指派给某人的全部待办（对账用）。 */
    List<Task> findTasksByAssignee(String assignee);

    /**
     * 办理一个任务。
     *
     * @param outcome   APPROVE / REJECT
     * @param actor     实际操作人
     * @param onBehalfOf 被代理人；非空表示这是代理办理，要在轨迹上留痕
     */
    void completeTask(String taskId, String outcome, String actor, String onBehalfOf,
                      String comment, Map<String, Object> variables);

    void claim(String taskId, String userId);

    void reassign(String taskId, String assignee);

    /** 当前是否接的是真中台。冒烟与运维需要知道自己在跟谁说话。 */
    boolean remote();
}
