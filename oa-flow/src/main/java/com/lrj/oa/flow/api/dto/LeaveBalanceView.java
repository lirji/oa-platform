package com.lrj.oa.flow.api.dto;

import java.math.BigDecimal;

/** 假期额度视图。available = total − used − frozen。 */
public class LeaveBalanceView {

    private String leaveTypeCode;
    private String leaveTypeName;
    private String period;
    private BigDecimal totalDays;
    private BigDecimal usedDays;
    private BigDecimal frozenDays;
    private BigDecimal availableDays;

    public String getLeaveTypeCode() { return leaveTypeCode; }
    public void setLeaveTypeCode(String v) { this.leaveTypeCode = v; }
    public String getLeaveTypeName() { return leaveTypeName; }
    public void setLeaveTypeName(String v) { this.leaveTypeName = v; }
    public String getPeriod() { return period; }
    public void setPeriod(String v) { this.period = v; }
    public BigDecimal getTotalDays() { return totalDays; }
    public void setTotalDays(BigDecimal v) { this.totalDays = v; }
    public BigDecimal getUsedDays() { return usedDays; }
    public void setUsedDays(BigDecimal v) { this.usedDays = v; }
    public BigDecimal getFrozenDays() { return frozenDays; }
    public void setFrozenDays(BigDecimal v) { this.frozenDays = v; }
    public BigDecimal getAvailableDays() { return availableDays; }
    public void setAvailableDays(BigDecimal v) { this.availableDays = v; }
}
