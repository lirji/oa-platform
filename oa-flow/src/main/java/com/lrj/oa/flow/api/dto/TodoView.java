package com.lrj.oa.flow.api.dto;

import java.time.OffsetDateTime;

/** 工作台待办条目。全部字段来自 todo_item 宽表，一条 SQL 出结果。 */
public class TodoView {

    private Long id;
    private String taskId;
    private String processInstanceId;
    private Long instanceId;
    private String bizType;
    private String title;
    private String summary;
    private String applicantUserId;
    private String applicantName;
    private String assigneeUserId;
    private String state;
    private Long orgId;
    private String orgPath;
    private OffsetDateTime createdAt;
    private OffsetDateTime dueAt;

    public Long getId() { return id; }
    public void setId(Long v) { this.id = v; }
    public String getTaskId() { return taskId; }
    public void setTaskId(String v) { this.taskId = v; }
    public String getProcessInstanceId() { return processInstanceId; }
    public void setProcessInstanceId(String v) { this.processInstanceId = v; }
    public Long getInstanceId() { return instanceId; }
    public void setInstanceId(Long v) { this.instanceId = v; }
    public String getBizType() { return bizType; }
    public void setBizType(String v) { this.bizType = v; }
    public String getTitle() { return title; }
    public void setTitle(String v) { this.title = v; }
    public String getSummary() { return summary; }
    public void setSummary(String v) { this.summary = v; }
    public String getApplicantUserId() { return applicantUserId; }
    public void setApplicantUserId(String v) { this.applicantUserId = v; }
    public String getApplicantName() { return applicantName; }
    public void setApplicantName(String v) { this.applicantName = v; }
    public String getAssigneeUserId() { return assigneeUserId; }
    public void setAssigneeUserId(String v) { this.assigneeUserId = v; }
    public String getState() { return state; }
    public void setState(String v) { this.state = v; }
    public Long getOrgId() { return orgId; }
    public void setOrgId(Long v) { this.orgId = v; }
    public String getOrgPath() { return orgPath; }
    public void setOrgPath(String v) { this.orgPath = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime v) { this.createdAt = v; }
    public OffsetDateTime getDueAt() { return dueAt; }
    public void setDueAt(OffsetDateTime v) { this.dueAt = v; }
}
