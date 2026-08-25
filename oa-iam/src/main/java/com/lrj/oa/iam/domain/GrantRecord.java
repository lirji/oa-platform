package com.lrj.oa.iam.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lrj.oa.common.mybatis.JsonbTypeHandler;

import java.time.OffsetDateTime;

/** 一条授权。主体 × 角色 × 作用域 × 时效，全部权限语义的收敛点。 */
@TableName(value = "oa_iam.grant_record", autoResultMap = true)
public class GrantRecord {
    @TableId(type = IdType.AUTO) private Long id;
    private Long tenantId;
    private String subjectType;
    private String subjectId;
    private Long roleId;
    private String scopeType;
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String scopeOrgIds;          // jsonb，用 String 承接，业务侧自己解析
    private Boolean includeDescendants;
    private String grantType;
    private OffsetDateTime validFrom;
    private OffsetDateTime validTo;
    private String source;
    private String approvalInstanceId;
    private String reason;
    private String grantedBy;
    private OffsetDateTime grantedAt;
    private OffsetDateTime revokedAt;
    private String revokedBy;
    private String revokeReason;

    public SubjectType subjectTypeEnum() { return SubjectType.valueOf(subjectType); }
    public GrantType grantTypeEnum() { return GrantType.valueOf(grantType); }

    /**
     * JIT 记录只是一枚临时“激活标记”，不得再次贡献角色、ABAC 或数据范围。
     * 普通定时授权虽然同为 TEMPORARY，仍然是一条真实授权，不能混为一谈。
     */
    public boolean jitElevation() {
        return grantTypeEnum() == GrantType.TEMPORARY && "APPROVAL".equals(source);
    }

    /** 在给定时刻是否有效：未撤销 且 落在 [validFrom, validTo) 内。 */
    public boolean activeAt(OffsetDateTime t) {
        if (revokedAt != null) return false;
        if (validFrom != null && t.isBefore(validFrom)) return false;
        return validTo == null || t.isBefore(validTo);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long v) { this.tenantId = v; }
    public String getSubjectType() { return subjectType; }
    public void setSubjectType(String v) { this.subjectType = v; }
    public String getSubjectId() { return subjectId; }
    public void setSubjectId(String v) { this.subjectId = v; }
    public Long getRoleId() { return roleId; }
    public void setRoleId(Long v) { this.roleId = v; }
    public String getScopeType() { return scopeType; }
    public void setScopeType(String v) { this.scopeType = v; }
    public String getScopeOrgIds() { return scopeOrgIds; }
    public void setScopeOrgIds(String v) { this.scopeOrgIds = v; }
    public Boolean getIncludeDescendants() { return includeDescendants; }
    public void setIncludeDescendants(Boolean v) { this.includeDescendants = v; }
    public String getGrantType() { return grantType; }
    public void setGrantType(String v) { this.grantType = v; }
    public OffsetDateTime getValidFrom() { return validFrom; }
    public void setValidFrom(OffsetDateTime v) { this.validFrom = v; }
    public OffsetDateTime getValidTo() { return validTo; }
    public void setValidTo(OffsetDateTime v) { this.validTo = v; }
    public String getSource() { return source; }
    public void setSource(String v) { this.source = v; }
    public String getApprovalInstanceId() { return approvalInstanceId; }
    public void setApprovalInstanceId(String v) { this.approvalInstanceId = v; }
    public String getReason() { return reason; }
    public void setReason(String v) { this.reason = v; }
    public String getGrantedBy() { return grantedBy; }
    public void setGrantedBy(String v) { this.grantedBy = v; }
    public OffsetDateTime getGrantedAt() { return grantedAt; }
    public void setGrantedAt(OffsetDateTime v) { this.grantedAt = v; }
    public OffsetDateTime getRevokedAt() { return revokedAt; }
    public void setRevokedAt(OffsetDateTime v) { this.revokedAt = v; }
    public String getRevokedBy() { return revokedBy; }
    public void setRevokedBy(String v) { this.revokedBy = v; }
    public String getRevokeReason() { return revokeReason; }
    public void setRevokeReason(String v) { this.revokeReason = v; }
}
