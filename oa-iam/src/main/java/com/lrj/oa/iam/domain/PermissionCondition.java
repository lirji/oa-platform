package com.lrj.oa.iam.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

@TableName("oa_iam.permission_condition")
public class PermissionCondition {
    @TableId(type = IdType.AUTO) private Long id;
    private Long tenantId;
    private Long roleId;
    private Long permissionId;
    private String expression;
    private String description;
    private Boolean enabled;
    private String createdBy;
    private OffsetDateTime createdAt;
    private String updatedBy;
    private OffsetDateTime updatedAt;
    private OffsetDateTime deletedAt;

    public Long getId() { return id; }
    public void setId(Long v) { id = v; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long v) { tenantId = v; }
    public Long getRoleId() { return roleId; }
    public void setRoleId(Long v) { roleId = v; }
    public Long getPermissionId() { return permissionId; }
    public void setPermissionId(Long v) { permissionId = v; }
    public String getExpression() { return expression; }
    public void setExpression(String v) { expression = v; }
    public String getDescription() { return description; }
    public void setDescription(String v) { description = v; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean v) { enabled = v; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String v) { createdBy = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime v) { createdAt = v; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String v) { updatedBy = v; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime v) { updatedAt = v; }
    public OffsetDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(OffsetDateTime v) { deletedAt = v; }
}
