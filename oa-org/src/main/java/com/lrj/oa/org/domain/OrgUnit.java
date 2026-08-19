package com.lrj.oa.org.domain;

import com.baomidou.mybatisplus.annotation.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/** 组织单元。表结构与设计理由见 {@code V2__org.sql}。 */
@TableName("oa_org.org_unit")
public class OrgUnit {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private Long parentId;
    private String code;
    private String name;
    private String shortName;
    private String type;

    /** 物化路径 {@code /1/23/456/}，首尾均带斜杠。尾斜杠让 {@code LIKE '/1/23/%'} 不误匹配 {@code /1/234/}。 */
    private String path;

    private Integer depth;
    private Integer sortOrder;
    private String leaderUserId;
    private String deputyLeaderUserId;
    private String costCenter;
    private String status;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    private String remark;

    @Version
    private Integer version;

    private String createdBy;
    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;
    private String updatedBy;
    private OffsetDateTime updatedAt;

    public OrgUnitType typeEnum() { return OrgUnitType.valueOf(type); }
    public OrgStatus statusEnum() { return OrgStatus.valueOf(status); }
    public boolean isRoot() { return parentId == null; }

    /** 本节点作为父时，子节点应有的路径前缀。 */
    public String childPathPrefix() { return path; }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getShortName() { return shortName; }
    public void setShortName(String shortName) { this.shortName = shortName; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public Integer getDepth() { return depth; }
    public void setDepth(Integer depth) { this.depth = depth; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public String getLeaderUserId() { return leaderUserId; }
    public void setLeaderUserId(String leaderUserId) { this.leaderUserId = leaderUserId; }
    public String getDeputyLeaderUserId() { return deputyLeaderUserId; }
    public void setDeputyLeaderUserId(String v) { this.deputyLeaderUserId = v; }
    public String getCostCenter() { return costCenter; }
    public void setCostCenter(String costCenter) { this.costCenter = costCenter; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(LocalDate v) { this.effectiveFrom = v; }
    public LocalDate getEffectiveTo() { return effectiveTo; }
    public void setEffectiveTo(LocalDate v) { this.effectiveTo = v; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime v) { this.createdAt = v; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime v) { this.updatedAt = v; }
}
