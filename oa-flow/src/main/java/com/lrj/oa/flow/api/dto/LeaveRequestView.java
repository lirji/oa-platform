package com.lrj.oa.flow.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/** 请假单视图。 */
public class LeaveRequestView {

    private Long id;
    private String requestNo;
    private String userId;
    private String applicantName;
    private String leaveTypeCode;
    private String leaveTypeName;
    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal days;
    private String reason;
    private String status;
    private Long orgId;
    private String orgPath;
    private OffsetDateTime createdAt;
    private String processInstanceId;
    private java.util.List<String> approverChain;

    public Long getId() { return id; }
    public void setId(Long v) { this.id = v; }
    public String getRequestNo() { return requestNo; }
    public void setRequestNo(String v) { this.requestNo = v; }
    public String getUserId() { return userId; }
    public void setUserId(String v) { this.userId = v; }
    public String getApplicantName() { return applicantName; }
    public void setApplicantName(String v) { this.applicantName = v; }
    public String getLeaveTypeCode() { return leaveTypeCode; }
    public void setLeaveTypeCode(String v) { this.leaveTypeCode = v; }
    public String getLeaveTypeName() { return leaveTypeName; }
    public void setLeaveTypeName(String v) { this.leaveTypeName = v; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate v) { this.startDate = v; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate v) { this.endDate = v; }
    public BigDecimal getDays() { return days; }
    public void setDays(BigDecimal v) { this.days = v; }
    public String getReason() { return reason; }
    public void setReason(String v) { this.reason = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public Long getOrgId() { return orgId; }
    public void setOrgId(Long v) { this.orgId = v; }
    public String getOrgPath() { return orgPath; }
    public void setOrgPath(String v) { this.orgPath = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime v) { this.createdAt = v; }
    public String getProcessInstanceId() { return processInstanceId; }
    public void setProcessInstanceId(String v) { this.processInstanceId = v; }
    public java.util.List<String> getApproverChain() { return approverChain; }
    public void setApproverChain(java.util.List<String> v) { this.approverChain = v; }
}
