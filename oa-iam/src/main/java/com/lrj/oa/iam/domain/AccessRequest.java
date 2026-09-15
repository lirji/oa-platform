package com.lrj.oa.iam.domain;

import java.time.OffsetDateTime;

/** 权限申请。四眼批准后才写 grant_record。 */
public class AccessRequest {
    private Long id;
    private Long tenantId;
    private String commandId;
    private String requesterUserId;
    private String requesterIdentityId;
    private String requestType;
    private Long roleId;
    private String permCodes;
    private OffsetDateTime validTo;
    private String reason;
    private String status;
    private OffsetDateTime requestedAt;
    private String decidedBy;
    private OffsetDateTime decidedAt;
    private String decisionReason;
    private Long grantId;
    private String approvalInstanceId;
    private String roleCode;
    private String roleName;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public String getCommandId() { return commandId; }
    public void setCommandId(String commandId) { this.commandId = commandId; }
    public String getRequesterUserId() { return requesterUserId; }
    public void setRequesterUserId(String requesterUserId) { this.requesterUserId = requesterUserId; }
    public String getRequesterIdentityId() { return requesterIdentityId; }
    public void setRequesterIdentityId(String requesterIdentityId) { this.requesterIdentityId = requesterIdentityId; }
    public String getRequestType() { return requestType; }
    public void setRequestType(String requestType) { this.requestType = requestType; }
    public Long getRoleId() { return roleId; }
    public void setRoleId(Long roleId) { this.roleId = roleId; }
    public String getPermCodes() { return permCodes; }
    public void setPermCodes(String permCodes) { this.permCodes = permCodes; }
    public OffsetDateTime getValidTo() { return validTo; }
    public void setValidTo(OffsetDateTime validTo) { this.validTo = validTo; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public OffsetDateTime getRequestedAt() { return requestedAt; }
    public void setRequestedAt(OffsetDateTime requestedAt) { this.requestedAt = requestedAt; }
    public String getDecidedBy() { return decidedBy; }
    public void setDecidedBy(String decidedBy) { this.decidedBy = decidedBy; }
    public OffsetDateTime getDecidedAt() { return decidedAt; }
    public void setDecidedAt(OffsetDateTime decidedAt) { this.decidedAt = decidedAt; }
    public String getDecisionReason() { return decisionReason; }
    public void setDecisionReason(String decisionReason) { this.decisionReason = decisionReason; }
    public Long getGrantId() { return grantId; }
    public void setGrantId(Long grantId) { this.grantId = grantId; }
    public String getApprovalInstanceId() { return approvalInstanceId; }
    public void setApprovalInstanceId(String approvalInstanceId) { this.approvalInstanceId = approvalInstanceId; }
    public String getRoleCode() { return roleCode; }
    public void setRoleCode(String roleCode) { this.roleCode = roleCode; }
    public String getRoleName() { return roleName; }
    public void setRoleName(String roleName) { this.roleName = roleName; }
}
