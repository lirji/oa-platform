package com.lrj.oa.flow.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/** 请假单。业务数据留在 OA，流程状态在中台，两者靠 businessKey 关联。 */
@TableName("oa_flow.leave_request")
public class LeaveRequest {

    @TableId(type = IdType.AUTO) private Long id;
    private Long tenantId;
    /** 号段生成，不用 SELECT FOR UPDATE */
    private String requestNo;
    private String userId;
    private Long employeeId;
    private Long leaveTypeId;
    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal days;
    private String reason;
    private String status;
    /** 发生时快照 */
    private Long orgId;
    /** 发生时快照 */
    private String orgPath;
    private OffsetDateTime createdAt;
    private OffsetDateTime decidedAt;
    @Version private Integer version;

    public Long getId() { return id; }
    public void setId(Long v) { this.id = v; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long v) { this.tenantId = v; }
    public String getRequestNo() { return requestNo; }
    public void setRequestNo(String v) { this.requestNo = v; }
    public String getUserId() { return userId; }
    public void setUserId(String v) { this.userId = v; }
    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long v) { this.employeeId = v; }
    public Long getLeaveTypeId() { return leaveTypeId; }
    public void setLeaveTypeId(Long v) { this.leaveTypeId = v; }
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
    public OffsetDateTime getDecidedAt() { return decidedAt; }
    public void setDecidedAt(OffsetDateTime v) { this.decidedAt = v; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer v) { this.version = v; }
}
