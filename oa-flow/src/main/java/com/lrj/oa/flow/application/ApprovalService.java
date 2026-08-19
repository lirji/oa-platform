package com.lrj.oa.flow.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lrj.oa.common.api.ResultCode;
import com.lrj.oa.common.context.TenantContext;
import com.lrj.oa.common.exception.BusinessException;
import com.lrj.oa.flow.domain.ApprovalInstance;
import com.lrj.oa.flow.infrastructure.mapper.FlowMappers;
import com.lrj.oa.flow.infrastructure.mapper.OutboxMapper;
import com.lrj.oa.flow.infrastructure.mapper.TodoMapper;
import com.lrj.oa.flow.infrastructure.workflow.LocalWorkflowGateway;
import com.lrj.oa.flow.infrastructure.workflow.WorkflowGateway;
import com.lrj.oa.security.context.UserContext;
import com.lrj.oa.security.context.UserContextHolder;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.BiConsumer;

/**
 * 审批编排的 OA 侧入口。
 *
 * <p><b>发起</b>与业务写入同事务，只往发件箱写一条 {@code StartProcessCommandV1}；
 * <b>办理</b>走网关（需要即时反馈）。这正是 workflow-platform 已锁定的接入方式。
 *
 * <p>审批人由 {@link ApproverResolver} 在<b>本侧</b>算好塞进流程变量（ADR-0010），
 * 中台不需要、也不应该知道 OA 的组织结构。
 */
@Service
public class ApprovalService {

    private static final Logger log = LoggerFactory.getLogger(ApprovalService.class);
    /** 与中台契约一致的 topic 名，来自 workflow-platform 的 Published Language。 */
    public static final String TOPIC_COMMAND_START = "workflow.command.start.v1";

    private final FlowMappers.ApprovalInstanceMapper instanceMapper;
    private final FlowMappers.FormTemplateMapper templateMapper;
    private final OutboxMapper outboxMapper;
    private final TodoMapper todoMapper;
    private final WorkflowGateway gateway;
    private final ObjectProvider<LocalWorkflowGateway> localGateway;
    private final ObjectMapper json;
    private final JdbcTemplate jdbc;

    /** 业务侧的完成回调，按 bizType 注册（请假、报销…各自处理自己的后置动作）。 */
    private final Map<String, BiConsumer<String, String>> finishHandlers = new HashMap<>();

    public ApprovalService(FlowMappers.ApprovalInstanceMapper instanceMapper,
                           FlowMappers.FormTemplateMapper templateMapper,
                           OutboxMapper outboxMapper, TodoMapper todoMapper,
                           WorkflowGateway gateway,
                           ObjectProvider<LocalWorkflowGateway> localGateway,
                           ObjectMapper json, JdbcTemplate jdbc) {
        this.instanceMapper = instanceMapper;
        this.templateMapper = templateMapper;
        this.outboxMapper = outboxMapper;
        this.todoMapper = todoMapper;
        this.gateway = gateway;
        this.localGateway = localGateway;
        this.json = json;
        this.jdbc = jdbc;
    }

    @PostConstruct
    void wireLocalGateway() {
        LocalWorkflowGateway local = localGateway.getIfAvailable();
        if (local == null) return;
        local.setCallback(new LocalWorkflowGateway.Callback() {
            @Override public void onTaskCreated(WorkflowGateway.Task task) { projectTodo(task); }
            @Override public void onFinished(String pid, String outcome) { onProcessFinished(pid, outcome); }
        });
    }

    public void registerFinishHandler(String bizType, BiConsumer<String, String> handler) {
        finishHandlers.put(bizType, handler);
    }

    // ───────────────────────────────────────────── 发起

    public record SubmitRequest(String bizType, String businessKey, String processDefinitionKey,
                                String formTemplateCode, Map<String, Object> formData,
                                String title, String summary, List<String> approverChain) {}

    /**
     * 发起审批。<b>必须在业务事务内调用</b> —— 发件箱那条写入要和业务单据同生共死。
     */
    @Transactional
    public Long submit(SubmitRequest req) {
        UserContext ctx = UserContextHolder.require();
        if (ctx.primaryOrgId() == null) {
            throw BusinessException.of(ResultCode.FLOW_START_FAILED, "申请人没有主岗组织，无法确定单据归属");
        }
        if (instanceMapper.selectByBusiness(req.bizType(), req.businessKey()) != null) {
            throw BusinessException.of(ResultCode.CONFLICT, "该单据已发起过审批");
        }
        var template = templateMapper.selectLatestPublished(TenantContext.get(), req.formTemplateCode());

        ApprovalInstance ins = new ApprovalInstance();
        ins.setTenantId(TenantContext.get());
        ins.setBizType(req.bizType());
        ins.setBusinessKey(req.businessKey());
        ins.setProcessDefinitionKey(req.processDefinitionKey());
        ins.setFormTemplateId(template == null ? null : template.getId());
        ins.setFormVersion(template == null ? null : template.getVersion());
        ins.setFormData(writeJson(req.formData()));
        ins.setApplicantUserId(ctx.userId());
        ins.setApplicantEmployeeId(ctx.employeeId());
        ins.setApplicantName(ctx.username());
        // 发生时快照：组织调整不回刷（ADR-0007）
        ins.setOrgId(ctx.primaryOrgId());
        ins.setOrgPath(ctx.primaryOrgPath());
        ins.setApproverChain(writeJson(req.approverChain()));
        ins.setStatus("SUBMITTED");
        ins.setSubmittedAt(OffsetDateTime.now());
        ins.setVersion(0);
        instanceMapper.insert(ins);

        // 发件箱：与上面的 insert 在同一个事务里，二者要么都成立要么都不成立
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("applicant", ctx.userId());
        variables.put("applicantOrgId", ctx.primaryOrgId());
        variables.put("applicantOrgPath", ctx.primaryOrgPath());
        variables.put("approverChain", req.approverChain());
        variables.put("approvalInstanceId", ins.getId());
        variables.put("bizType", req.bizType());
        if (req.formData() != null) variables.putAll(scalarsOnly(req.formData()));

        Map<String, Object> command = new LinkedHashMap<>();
        command.put("processDefinitionKey", req.processDefinitionKey());
        command.put("businessKey", req.businessKey());
        command.put("idempotencyKey", req.bizType() + ":" + req.businessKey());
        command.put("initiator", ctx.userId());
        command.put("variables", variables);
        outboxMapper.append(TOPIC_COMMAND_START, req.businessKey(), writeJson(envelope(command, req)));

        // 本地替身模式：事务提交后再驱动，避免读到未提交数据
        LocalWorkflowGateway local = localGateway.getIfAvailable();
        if (local != null) {
            Long instanceId = ins.getId();
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override public void afterCommit() {
                            String pid = local.startProcess(req.processDefinitionKey(), req.businessKey(), req.approverChain());
                            instanceMapper.bindProcessInstance(instanceId, pid);
                        }
                    });
        }
        log.info("发起审批 bizType={} businessKey={} 审批链={}", req.bizType(), req.businessKey(), req.approverChain());
        return ins.getId();
    }

    // ───────────────────────────────────────────── 办理

    @Transactional
    public void completeTask(String taskId, String outcome, String comment, String onBehalfOf) {
        UserContext ctx = UserContextHolder.require();
        var task = gateway.findTasks(null, null).stream()
                .filter(t -> t.taskId().equals(taskId)).findFirst()
                .orElseThrow(() -> BusinessException.of(ResultCode.FLOW_TASK_NOT_FOUND, "待办不存在: " + taskId));

        String effectiveActor = onBehalfOf == null ? ctx.userId() : onBehalfOf;
        if (!effectiveActor.equals(task.assignee())) {
            throw BusinessException.of(ResultCode.PERM_DENIED, "该待办不属于你");
        }

        gateway.completeTask(taskId, outcome, ctx.userId(), onBehalfOf, comment, Map.of());
        todoMapper.finish(TenantContext.get(), taskId, "DONE");
        // 接真中台时，"下一级待办出现了"和"流程走完了"都不是推过来的事件——中台的生命周期
        // 事件契约上是 best-effort。办理是用户正等着结果的时刻，这里同步问一次中台，
        // 让工作台立刻正确；60 秒一次的对账只作兜底，不作主路径。
        if (gateway.remote()) {
            syncFromRemote(task.processInstanceId(), task.businessKey(), outcome);
        }

        ApprovalInstance ins = instanceMapper.selectByProcessInstance(task.processInstanceId());
        if (ins != null) {
            jdbc.update("""
                    INSERT INTO oa_flow.approval_node_log(instance_id, task_id, node_name, actor_user_id,
                                                          on_behalf_of, action, comment)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """, ins.getId(), taskId, task.name(), ctx.userId(), onBehalfOf, outcome, comment);
        }
        log.info("办理待办 taskId={} outcome={} actor={} onBehalfOf={}", taskId, outcome, ctx.userId(), onBehalfOf);
    }

    // ───────────────────────────────────────────── 回调

    /** 中台（或本地替身）产生新任务 → 投影进待办读模型。 */
    public void projectTodo(WorkflowGateway.Task task) {
        ApprovalInstance ins = instanceMapper.selectByProcessInstance(task.processInstanceId());
        TodoMapper.TodoUpsert u = new TodoMapper.TodoUpsert();
        u.tenantId = TenantContext.DEFAULT_TENANT_ID;
        u.taskId = task.taskId();
        u.processInstanceId = task.processInstanceId();
        u.instanceId = ins == null ? null : ins.getId();
        u.bizType = ins == null ? null : ins.getBizType();
        u.title = (ins == null ? "审批" : ins.getBizType()) + " · " + task.name();
        u.summary = ins == null ? null : ("申请人 " + ins.getApplicantName());
        u.applicantUserId = ins == null ? null : ins.getApplicantUserId();
        u.applicantName = ins == null ? null : ins.getApplicantName();
        u.assigneeUserId = task.assignee();
        u.candidateGroup = task.candidateGroup();
        u.orgId = ins == null ? null : ins.getOrgId();
        u.orgPath = ins == null ? null : ins.getOrgPath();
        todoMapper.upsert(u);
    }

    /**
     * 办理后立刻与中台对齐一次：有新任务就投影进待办，没有任务且流程已结束就收尾。
     *
     * <p>只在 REMOTE 生效。本地替身走回调，已经是同步的。
     */
    void syncFromRemote(String processInstanceId, String businessKey, String lastOutcome) {
        try {
            List<WorkflowGateway.Task> remaining = gateway.findTasks(null, businessKey);
            if (!remaining.isEmpty()) {
                remaining.forEach(this::projectTodo);
                return;
            }
            var info = gateway.findProcess(businessKey);
            if (info.isEmpty() || !info.get().running()) {
                String pid = info.map(WorkflowGateway.ProcessInfo::processInstanceId).orElse(processInstanceId);
                onProcessFinished(pid, "REJECT".equalsIgnoreCase(lastOutcome) ? "REJECTED" : "APPROVED");
            }
        } catch (Exception e) {
            // 对齐失败不能让办理本身回滚——人工决定已经在中台落了，回滚只会让两边更不一致。
            // 后果由 60 秒对账收敛，这里必须留下可查的痕迹。
            log.warn("办理后与中台对齐失败 businessKey={}：{}（等待对账收敛）", businessKey, e.toString());
        }
    }

    /** 流程走完 → 回写实例状态并通知业务侧。 */
    public void onProcessFinished(String processInstanceId, String outcome) {
        ApprovalInstance ins = instanceMapper.selectByProcessInstance(processInstanceId);
        if (ins == null) return;
        todoMapper.cancelByProcess(processInstanceId);
        jdbc.update("""
                UPDATE oa_flow.approval_instance
                   SET status = 'FINISHED', outcome = ?, finished_at = now()
                 WHERE id = ? AND status <> 'FINISHED'
                """, outcome, ins.getId());
        var handler = finishHandlers.get(ins.getBizType());
        if (handler != null) handler.accept(ins.getBusinessKey(), outcome);
        log.info("审批结束 bizType={} businessKey={} outcome={}", ins.getBizType(), ins.getBusinessKey(), outcome);
    }

    /**
     * 审批实例与中台对账（REMOTE 专用）。补两件中台不会主动告诉 OA 的事：
     *
     * <ol>
     *   <li><b>回绑 processInstanceId</b>——发起走发件箱 → Kafka，是异步的，提单那一刻 OA 拿不到实例 id。
     *       没有它，待办投影里的 instanceId/bizType 全是 null，工作台显示成一堆"审批 · 审批"。</li>
     *   <li><b>发现流程已结束</b>——中台的生命周期事件是 best-effort，漏一条就会留下一个
     *       永远 RUNNING 的单据和一笔永远冻结的额度。额度冻结不释放是会被员工投诉的那种 bug。</li>
     * </ol>
     *
     * <p>结论取自本地 {@code approval_node_log} 的最后一条动作，而不是问中台要——
     * 中台只知道流程终点，不知道 OA 的"通过/驳回"语义。
     */
    @Scheduled(fixedDelayString = "${oa.flow.instance.reconcile-ms:60000}")
    public void reconcileInstances() {
        if (!gateway.remote()) return;
        List<ApprovalInstance> unfinished;
        try {
            unfinished = instanceMapper.selectUnfinished(200);
        } catch (Exception e) {
            log.debug("审批实例对账取数失败：{}", e.toString());
            return;
        }
        int bound = 0, finished = 0;
        for (ApprovalInstance ins : unfinished) {
            try {
                var info = gateway.findProcess(ins.getBusinessKey());
                if (info.isEmpty()) continue;   // 中台还没起流程，下一轮再看
                if (ins.getProcessInstanceId() == null || ins.getProcessInstanceId().isBlank()) {
                    instanceMapper.bindProcessInstance(ins.getId(), info.get().processInstanceId());
                    bound++;
                }
                if (!info.get().running()) {
                    onProcessFinished(info.get().processInstanceId(), lastOutcomeOf(ins.getId()));
                    finished++;
                }
            } catch (Exception e) {
                log.debug("审批实例对账失败 businessKey={}：{}", ins.getBusinessKey(), e.toString());
            }
        }
        if (bound > 0 || finished > 0) {
            log.info("审批实例对账：回绑 {} 条，收尾 {} 条", bound, finished);
        }
    }

    /** 末环节动作决定整单结论：任一环节 REJECT 即驳回（BPMN 的 completionCondition 也是这么写的）。 */
    private String lastOutcomeOf(Long instanceId) {
        List<String> actions = jdbc.queryForList("""
                SELECT action FROM oa_flow.approval_node_log
                 WHERE instance_id = ? ORDER BY id DESC LIMIT 1
                """, String.class, instanceId);
        String last = actions.isEmpty() ? null : actions.get(0);
        return "REJECT".equalsIgnoreCase(last) ? "REJECTED" : "APPROVED";
    }

    public ApprovalInstance findByBusiness(String bizType, String businessKey) {
        return instanceMapper.selectByBusiness(bizType, businessKey);
    }

    // ───────────────────────────────────────────── 内部

    private Map<String, Object> envelope(Map<String, Object> command, SubmitRequest req) {
        Map<String, Object> env = new LinkedHashMap<>();
        env.put("eventId", UUID.randomUUID().toString());
        env.put("contractVersion", 1);
        env.put("eventType", "workflow.command.start.v1");
        env.put("occurredAt", OffsetDateTime.now().toInstant().toString());
        env.put("source", "oa-platform");
        env.put("tenantId", "oa");
        env.put("correlationId", req.bizType() + ":" + req.businessKey());
        env.put("payload", command);
        return env;
    }

    /** 流程变量白名单：只放标量，不把整个业务对象塞进流程引擎（中台契约的明确要求）。 */
    private static Map<String, Object> scalarsOnly(Map<String, Object> in) {
        Map<String, Object> out = new LinkedHashMap<>();
        in.forEach((k, v) -> {
            if (v == null || v instanceof String || v instanceof Number || v instanceof Boolean) out.put(k, v);
        });
        return out;
    }

    private String writeJson(Object v) {
        if (v == null) return null;
        try { return json.writeValueAsString(v); }
        catch (Exception e) { throw new IllegalStateException("序列化失败", e); }
    }
}
