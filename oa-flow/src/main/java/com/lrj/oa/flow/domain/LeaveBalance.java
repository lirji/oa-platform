package com.lrj.oa.flow.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 假期额度。用<b>乐观锁</b>而不是分布式锁：同一个人的额度天然低并发，
 * 上分布式锁是杀鸡用牛刀，还多一个可用性依赖。
 */
@TableName("oa_flow.leave_balance")
public class LeaveBalance {

    @TableId(type = IdType.AUTO) private Long id;
    private Long tenantId;
    private String userId;
    private Long leaveTypeId;
    /** 年度，如 2026 */
    private String period;
    private BigDecimal totalDays;
    private BigDecimal usedDays;
    /** 审批中冻结，防止连提两单把额度用超 */
    private BigDecimal frozenDays;
    @Version private Integer version;
    private OffsetDateTime updatedAt;

    /** 可用额度 = 总额 − 已用 − 冻结。 */
    public BigDecimal available() {
        return totalDays.subtract(usedDays).subtract(frozenDays);
    }

    public Long getId() { return id; }
    public void setId(Long v) { this.id = v; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long v) { this.tenantId = v; }
    public String getUserId() { return userId; }
    public void setUserId(String v) { this.userId = v; }
    public Long getLeaveTypeId() { return leaveTypeId; }
    public void setLeaveTypeId(Long v) { this.leaveTypeId = v; }
    public String getPeriod() { return period; }
    public void setPeriod(String v) { this.period = v; }
    public BigDecimal getTotalDays() { return totalDays; }
    public void setTotalDays(BigDecimal v) { this.totalDays = v; }
    public BigDecimal getUsedDays() { return usedDays; }
    public void setUsedDays(BigDecimal v) { this.usedDays = v; }
    public BigDecimal getFrozenDays() { return frozenDays; }
    public void setFrozenDays(BigDecimal v) { this.frozenDays = v; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer v) { this.version = v; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime v) { this.updatedAt = v; }
}
