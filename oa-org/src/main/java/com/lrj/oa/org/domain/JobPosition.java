package com.lrj.oa.org.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

/** 岗位。表名 job_position —— position 是 SQL 标准函数名，做表名容易踩引号问题。 */
@TableName("oa_org.job_position")
public class JobPosition {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private String code;
    private String name;
    private String jobFamily;
    private String jobLevel;
    private Boolean isManager;
    private String status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long v) { this.tenantId = v; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getJobFamily() { return jobFamily; }
    public void setJobFamily(String v) { this.jobFamily = v; }
    public String getJobLevel() { return jobLevel; }
    public void setJobLevel(String v) { this.jobLevel = v; }
    public Boolean getIsManager() { return isManager; }
    public void setIsManager(Boolean v) { this.isManager = v; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime v) { this.createdAt = v; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime v) { this.updatedAt = v; }
}
