package com.lrj.oa.iam.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

@TableName("oa_iam.user_group_member")
public class UserGroupMember {
    @TableId(type = IdType.AUTO) private Long id;
    private Long tenantId;
    private Long groupId;
    private String userId;
    private OffsetDateTime validFrom;
    private OffsetDateTime validTo;
    private String createdBy;
    private OffsetDateTime createdAt;
    private String revokedBy;
    private OffsetDateTime revokedAt;

    public Long getId() { return id; }
    public void setId(Long v) { id = v; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long v) { tenantId = v; }
    public Long getGroupId() { return groupId; }
    public void setGroupId(Long v) { groupId = v; }
    public String getUserId() { return userId; }
    public void setUserId(String v) { userId = v; }
    public OffsetDateTime getValidFrom() { return validFrom; }
    public void setValidFrom(OffsetDateTime v) { validFrom = v; }
    public OffsetDateTime getValidTo() { return validTo; }
    public void setValidTo(OffsetDateTime v) { validTo = v; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String v) { createdBy = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime v) { createdAt = v; }
    public String getRevokedBy() { return revokedBy; }
    public void setRevokedBy(String v) { revokedBy = v; }
    public OffsetDateTime getRevokedAt() { return revokedAt; }
    public void setRevokedAt(OffsetDateTime v) { revokedAt = v; }
}
