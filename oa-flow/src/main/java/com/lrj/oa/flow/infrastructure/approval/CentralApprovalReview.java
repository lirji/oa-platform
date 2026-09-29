package com.lrj.oa.flow.infrastructure.approval;

import com.lrj.authz.protocol.ApprovalDtos.Start;
import com.lrj.oa.common.api.*;
import com.lrj.oa.common.exception.BusinessException;
import com.lrj.oa.flow.infrastructure.mapper.CentralApprovalMapper;
import com.lrj.oa.flow.infrastructure.workflow.WorkflowGateway;
import com.lrj.oa.security.context.UserContextHolder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;

/** 审批依据只能由当前真实任务办理人读取，不把OA目录开放给外部申请人。 */
@Service
@ConditionalOnProperty(name="oa.flow.central-approval.enabled",havingValue="true")
public class CentralApprovalReview {
    private final CentralApprovalMapper mapper;
    private final CentralApprovalProperties config;
    private final WorkflowGateway workflow;
    /** OA保存的原始快照是展示依据，读取不查询auth数据库。 */
    public CentralApprovalReview(CentralApprovalMapper mapper,CentralApprovalProperties config,WorkflowGateway workflow) {
        this.mapper=mapper;this.config=config;this.workflow=workflow;
    }
    /** 审批权限还必须与实际任务、当前配置和固定策略审批人一致；首版不支持代理批准。 */
    public Start review(String taskId) {
        var actor=UserContextHolder.require();
        if(taskId==null || !taskId.matches("[A-Za-z0-9_-]{1,100}") || !workflow.remote())throw denied();
        var task=workflow.findTasks(null,null).stream().filter(t->taskId.equals(t.taskId()) && actor.userId().equals(t.assignee())).findFirst().orElseThrow(CentralApprovalReview::denied);
        var row=mapper.findBusiness(task.businessKey());if(row==null)throw denied();
        Start snapshot;
        try {snapshot=CentralApprovalWire.read(row.payloadJson().getBytes(StandardCharsets.UTF_8),Start.class);}
        catch(java.io.IOException invalidSnapshot) {
            // 解析异常可能包含原文片段；只暴露稳定故障码，不把敏感审批依据写入日志。
            throw new IllegalStateException("APPROVAL_SNAPSHOT_UNREADABLE");
        }
        var bridge=config.members().get(snapshot.approverMembershipId());
        if(!config.tenantId().equals(snapshot.tenantId()) || !config.applicationId().equals(snapshot.applicationId()) || !config.environment().equals(snapshot.environment())
                || bridge==null || bridge.generation()!=snapshot.approverGeneration() || !bridge.userId().equals(actor.userId()) || actor.tenantId()!=config.oaTenantId())throw denied();
        return snapshot;
    }
    private static BusinessException denied(){return BusinessException.of(ResultCode.PERM_DENIED,"仅当前指定审批人可查看申请依据");}
}
