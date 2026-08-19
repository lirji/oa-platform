package com.lrj.oa.iam.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

@TableName("oa_iam.role")
public class Role {
    @TableId(type = IdType.AUTO) private Long id;
    private Long tenantId;
    private String code;
    private String name;
    private String type;
    private String defaultScope;
    private String status;
    private Boolean builtin;
    @Version private Integer version;
    private String remark;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long v) { this.tenantId = v; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getDefaultScope() { return defaultScope; }
    public void setDefaultScope(String v) { this.defaultScope = v; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Boolean getBuiltin() { return builtin; }
    public void setBuiltin(Boolean builtin) { this.builtin = builtin; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
