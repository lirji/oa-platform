package com.lrj.oa.iam.domain;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/** 统一身份聚合的持久化形态。标签单独成表，组装在应用层。 */
public class Identity {
    private String id;
    private Long seq;
    private Long tenantId;
    private String identityType;
    private String displayName;
    private String source;
    private String status;
    private String externalKey;
    private Long employeeId;
    private Long orgId;
    private String orgPath;
    private String attributes;
    private String riskLevel;
    private String ownerIdentityId;
    private OffsetDateTime expiredAt;
    private Integer version;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private List<String> labels = new ArrayList<>();

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public Long getSeq() { return seq; }
    public void setSeq(Long seq) { this.seq = seq; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public String getIdentityType() { return identityType; }
    public void setIdentityType(String identityType) { this.identityType = identityType; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getExternalKey() { return externalKey; }
    public void setExternalKey(String externalKey) { this.externalKey = externalKey; }
    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }
    public Long getOrgId() { return orgId; }
    public void setOrgId(Long orgId) { this.orgId = orgId; }
    public String getOrgPath() { return orgPath; }
    public void setOrgPath(String orgPath) { this.orgPath = orgPath; }
    public String getAttributes() { return attributes; }
    public void setAttributes(String attributes) { this.attributes = attributes; }
    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
    public String getOwnerIdentityId() { return ownerIdentityId; }
    public void setOwnerIdentityId(String ownerIdentityId) { this.ownerIdentityId = ownerIdentityId; }
    public OffsetDateTime getExpiredAt() { return expiredAt; }
    public void setExpiredAt(OffsetDateTime expiredAt) { this.expiredAt = expiredAt; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
    public List<String> getLabels() { return labels; }
    public void setLabels(List<String> labels) { this.labels = labels == null ? new ArrayList<>() : labels; }
}
