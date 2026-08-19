package com.lrj.oa.flow.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;

/** 假期类型。事假不占额度，所以 needBalance 是个真实开关而不是摆设。 */
@TableName("oa_flow.leave_type")
public class LeaveType {

    @TableId(type = IdType.AUTO) private Long id;
    private String code;
    private String name;
    private Boolean paid;
    /** 事假为 false，不校验也不扣额度 */
    private Boolean needBalance;
    private BigDecimal maxDays;
    private String status;

    public Long getId() { return id; }
    public void setId(Long v) { this.id = v; }
    public String getCode() { return code; }
    public void setCode(String v) { this.code = v; }
    public String getName() { return name; }
    public void setName(String v) { this.name = v; }
    public Boolean getPaid() { return paid; }
    public void setPaid(Boolean v) { this.paid = v; }
    public Boolean getNeedBalance() { return needBalance; }
    public void setNeedBalance(Boolean v) { this.needBalance = v; }
    public BigDecimal getMaxDays() { return maxDays; }
    public void setMaxDays(BigDecimal v) { this.maxDays = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
}
