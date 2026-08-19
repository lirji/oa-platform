package com.lrj.oa.org.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/** 汇报线。独立于组织树 —— 审批的"逐级上报"取它，不取 parent 部门负责人。 */
@TableName("oa_org.reporting_line")
public class ReportingLine {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private Long employeeId;
    private Long managerEmployeeId;
    private String type;
    private LocalDate validFrom;
    private LocalDate validTo;
    private OffsetDateTime createdAt;

    public ReportingType typeEnum() { return ReportingType.valueOf(type); }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long v) { this.tenantId = v; }
    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long v) { this.employeeId = v; }
    public Long getManagerEmployeeId() { return managerEmployeeId; }
    public void setManagerEmployeeId(Long v) { this.managerEmployeeId = v; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public LocalDate getValidFrom() { return validFrom; }
    public void setValidFrom(LocalDate v) { this.validFrom = v; }
    public LocalDate getValidTo() { return validTo; }
    public void setValidTo(LocalDate v) { this.validTo = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime v) { this.createdAt = v; }
}
