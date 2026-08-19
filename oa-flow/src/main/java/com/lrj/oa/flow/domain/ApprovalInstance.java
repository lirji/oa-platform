package com.lrj.oa.flow.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lrj.oa.common.mybatis.JsonbTypeHandler;
import com.baomidou.mybatisplus.annotation.Version;
import java.time.OffsetDateTime;

/**
 * 一张审批单在 OA 侧的镜像。流程编排在中台（Flowable），这里只存状态与关联。
 *
 * <p>{@code orgId}/{@code orgPath} 是<b>发生时快照</b>：组织调整不回刷历史单据（ADR-0007）。
 */
@TableName(value = "oa_flow.approval_instance", autoResultMap = true)
public class ApprovalInstance {

    @TableId(type = IdType.AUTO) private Long id;
    private Long tenantId;
    /** LEAVE|OVERTIME|EXPENSE… */
    private String bizType;
    /** 业务单据 id */
    private String businessKey;
    private String processDefinitionKey;
    /** 中台回填 */
    private String processInstanceId;
    private Long formTemplateId;
    private Integer formVersion;
    /** 表单数据快照(jsonb) */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String formData;
    private String applicantUserId;
    private Long applicantEmployeeId;
    private String applicantName;
    /** 发生时快照 */
    private Long orgId;
    /** 发生时快照，数据权限前缀匹配用 */
    private String orgPath;
    /** OA 侧算好的审批人链（ADR-0010） */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String approverChain;
    private String status;
    private String outcome;
    private OffsetDateTime submittedAt;
    private OffsetDateTime finishedAt;
    @Version private Integer version;

    public Long getId() { return id; }
    public void setId(Long v) { this.id = v; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long v) { this.tenantId = v; }
    public String getBizType() { return bizType; }
    public void setBizType(String v) { this.bizType = v; }
    public String getBusinessKey() { return businessKey; }
    public void setBusinessKey(String v) { this.businessKey = v; }
    public String getProcessDefinitionKey() { return processDefinitionKey; }
    public void setProcessDefinitionKey(String v) { this.processDefinitionKey = v; }
    public String getProcessInstanceId() { return processInstanceId; }
    public void setProcessInstanceId(String v) { this.processInstanceId = v; }
    public Long getFormTemplateId() { return formTemplateId; }
    public void setFormTemplateId(Long v) { this.formTemplateId = v; }
    public Integer getFormVersion() { return formVersion; }
    public void setFormVersion(Integer v) { this.formVersion = v; }
    public String getFormData() { return formData; }
    public void setFormData(String v) { this.formData = v; }
    public String getApplicantUserId() { return applicantUserId; }
    public void setApplicantUserId(String v) { this.applicantUserId = v; }
    public Long getApplicantEmployeeId() { return applicantEmployeeId; }
    public void setApplicantEmployeeId(Long v) { this.applicantEmployeeId = v; }
    public String getApplicantName() { return applicantName; }
    public void setApplicantName(String v) { this.applicantName = v; }
    public Long getOrgId() { return orgId; }
    public void setOrgId(Long v) { this.orgId = v; }
    public String getOrgPath() { return orgPath; }
    public void setOrgPath(String v) { this.orgPath = v; }
    public String getApproverChain() { return approverChain; }
    public void setApproverChain(String v) { this.approverChain = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public String getOutcome() { return outcome; }
    public void setOutcome(String v) { this.outcome = v; }
    public OffsetDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(OffsetDateTime v) { this.submittedAt = v; }
    public OffsetDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(OffsetDateTime v) { this.finishedAt = v; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer v) { this.version = v; }
}
