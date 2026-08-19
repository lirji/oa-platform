package com.lrj.oa.iam.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lrj.oa.common.mybatis.JsonbTypeHandler;

import java.time.OffsetDateTime;

/**
 * 委托代理。与临时提权的<b>本质区别</b>：代理人不会因此获得委托人的权限，
 * 只是能以委托人名义办他本来就该办的待办；动作落 {@code on_behalf_of} 留痕。
 */
@TableName(value = "oa_iam.delegation", autoResultMap = true)
public class Delegation {
    @TableId(type = IdType.AUTO) private Long id;
    private Long tenantId;
    private String delegatorUserId;
    private String delegateeUserId;
    private String scope;
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String processKeys;
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String roleIds;
    private OffsetDateTime validFrom;
    private OffsetDateTime validTo;
    private String reason;
    private String status;
    private String createdBy;
    private OffsetDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long v) { this.tenantId = v; }
    public String getDelegatorUserId() { return delegatorUserId; }
    public void setDelegatorUserId(String v) { this.delegatorUserId = v; }
    public String getDelegateeUserId() { return delegateeUserId; }
    public void setDelegateeUserId(String v) { this.delegateeUserId = v; }
    public String getScope() { return scope; }
    public void setScope(String v) { this.scope = v; }
    public String getProcessKeys() { return processKeys; }
    public void setProcessKeys(String v) { this.processKeys = v; }
    public String getRoleIds() { return roleIds; }
    public void setRoleIds(String v) { this.roleIds = v; }
    public OffsetDateTime getValidFrom() { return validFrom; }
    public void setValidFrom(OffsetDateTime v) { this.validFrom = v; }
    public OffsetDateTime getValidTo() { return validTo; }
    public void setValidTo(OffsetDateTime v) { this.validTo = v; }
    public String getReason() { return reason; }
    public void setReason(String v) { this.reason = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String v) { this.createdBy = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime v) { this.createdAt = v; }
}
