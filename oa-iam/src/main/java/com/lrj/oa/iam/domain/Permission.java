package com.lrj.oa.iam.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("oa_iam.permission")
public class Permission {
    @TableId(type = IdType.AUTO) private Long id;
    private String code;
    private String name;
    private String type;
    private String module;
    private Long parentId;
    private String resource;
    private String method;
    private String icon;
    private String route;
    private Integer sortOrder;
    private String status;
    private Boolean builtin;
    /** 高危权限点：即便有永久授权，也必须存在活跃 JIT 提权才放行。 */
    private Boolean requireElevation;
    private String remark;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getModule() { return module; }
    public void setModule(String module) { this.module = module; }
    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
    public String getResource() { return resource; }
    public void setResource(String resource) { this.resource = resource; }
    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }
    public String getRoute() { return route; }
    public void setRoute(String route) { this.route = route; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Boolean getBuiltin() { return builtin; }
    public void setBuiltin(Boolean builtin) { this.builtin = builtin; }
    public Boolean getRequireElevation() { return requireElevation; }
    public void setRequireElevation(Boolean v) { this.requireElevation = v; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
