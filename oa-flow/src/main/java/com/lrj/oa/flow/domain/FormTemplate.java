package com.lrj.oa.flow.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lrj.oa.common.mybatis.JsonbTypeHandler;
import java.time.OffsetDateTime;

/**
 * 表单模板（JSON Schema）。单据引用 (id, version)，模板改版不影响历史单据 ——
 * 否则两年前那张请假单会被今天的表单渲染，字段对不上。
 */
@TableName(value = "oa_flow.form_template", autoResultMap = true)
public class FormTemplate {

    @TableId(type = IdType.AUTO) private Long id;
    private Long tenantId;
    private String code;
    private String name;
    /** 模板版本，与单据上的 formVersion 对应 */
    private Integer version;
    /** JSON Schema 原文 */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String schemaJson;
    /** DRAFT|PUBLISHED|ARCHIVED */
    private String status;
    private OffsetDateTime publishedAt;
    private String createdBy;
    private OffsetDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long v) { this.id = v; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long v) { this.tenantId = v; }
    public String getCode() { return code; }
    public void setCode(String v) { this.code = v; }
    public String getName() { return name; }
    public void setName(String v) { this.name = v; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer v) { this.version = v; }
    public String getSchemaJson() { return schemaJson; }
    public void setSchemaJson(String v) { this.schemaJson = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public OffsetDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(OffsetDateTime v) { this.publishedAt = v; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String v) { this.createdBy = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime v) { this.createdAt = v; }
}
