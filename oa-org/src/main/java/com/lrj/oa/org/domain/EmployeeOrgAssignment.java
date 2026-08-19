package com.lrj.oa.org.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 任职关系（拉链表）。一人多岗 + 时间维度都落在这里。
 * 关闭一段任职是把 {@code validTo} 写上，<b>不是删行</b> —— 历史审批要能还原当时的归属。
 */
@TableName("oa_org.employee_org_assignment")
public class EmployeeOrgAssignment {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private Long employeeId;
    private Long orgUnitId;
    private Long positionId;
    private String assignmentType;
    private Boolean isLeader;
    private LocalDate validFrom;
    private LocalDate validTo;
    private String createdBy;
    private OffsetDateTime createdAt;

    public AssignmentType typeEnum() { return AssignmentType.valueOf(assignmentType); }
    public boolean active() { return validTo == null; }

    /** 该任职在给定日期是否有效（闭开区间 [validFrom, validTo)）。 */
    public boolean activeOn(LocalDate date) {
        return !date.isBefore(validFrom) && (validTo == null || date.isBefore(validTo));
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long v) { this.tenantId = v; }
    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long v) { this.employeeId = v; }
    public Long getOrgUnitId() { return orgUnitId; }
    public void setOrgUnitId(Long v) { this.orgUnitId = v; }
    public Long getPositionId() { return positionId; }
    public void setPositionId(Long v) { this.positionId = v; }
    public String getAssignmentType() { return assignmentType; }
    public void setAssignmentType(String v) { this.assignmentType = v; }
    public Boolean getIsLeader() { return isLeader; }
    public void setIsLeader(Boolean v) { this.isLeader = v; }
    public LocalDate getValidFrom() { return validFrom; }
    public void setValidFrom(LocalDate v) { this.validFrom = v; }
    public LocalDate getValidTo() { return validTo; }
    public void setValidTo(LocalDate v) { this.validTo = v; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String v) { this.createdBy = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime v) { this.createdAt = v; }
}
