package com.lrj.oa.iam.domain;

import java.time.OffsetDateTime;

/** 权限委托。与待办 {@code oa_iam.delegation} 分表。 */
public class PermissionDelegation {
    private Long id;
    private Long tenantId;
    private String delegatorIdentityId;
    private String delegatorUserId;
    private String delegateeIdentityId;
    private String permCodes;
    private String roleIds;
    private String resourceScope;
    private OffsetDateTime validFrom;
    private OffsetDateTime validTo;
    private Boolean reDelegate;
    private String reason;
    private String status;
    private OffsetDateTime createdAt;
    private OffsetDateTime revokedAt;
    private String revokedBy;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public String getDelegatorIdentityId() { return delegatorIdentityId; }
    public void setDelegatorIdentityId(String delegatorIdentityId) { this.delegatorIdentityId = delegatorIdentityId; }
    public String getDelegatorUserId() { return delegatorUserId; }
    public void setDelegatorUserId(String delegatorUserId) { this.delegatorUserId = delegatorUserId; }
    public String getDelegateeIdentityId() { return delegateeIdentityId; }
    public void setDelegateeIdentityId(String delegateeIdentityId) { this.delegateeIdentityId = delegateeIdentityId; }
    public String getPermCodes() { return permCodes; }
    public void setPermCodes(String permCodes) { this.permCodes = permCodes; }
    public String getRoleIds() { return roleIds; }
    public void setRoleIds(String roleIds) { this.roleIds = roleIds; }
    public String getResourceScope() { return resourceScope; }
    public void setResourceScope(String resourceScope) { this.resourceScope = resourceScope; }
    public OffsetDateTime getValidFrom() { return validFrom; }
    public void setValidFrom(OffsetDateTime validFrom) { this.validFrom = validFrom; }
    public OffsetDateTime getValidTo() { return validTo; }
    public void setValidTo(OffsetDateTime validTo) { this.validTo = validTo; }
    public Boolean getReDelegate() { return reDelegate; }
    public void setReDelegate(Boolean reDelegate) { this.reDelegate = reDelegate; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getRevokedAt() { return revokedAt; }
    public void setRevokedAt(OffsetDateTime revokedAt) { this.revokedAt = revokedAt; }
    public String getRevokedBy() { return revokedBy; }
    public void setRevokedBy(String revokedBy) { this.revokedBy = revokedBy; }
}
