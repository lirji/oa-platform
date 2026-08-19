package com.lrj.oa.iam.infrastructure.mapper;

/**
 * 角色继承展开的一行结果。
 *
 * <p>刻意用显式 DTO 而不是 {@code Map<String,Object>}：PostgreSQL 会把未加引号的列别名
 * <b>全部转成小写</b>，于是 {@code AS grantedRoleId} 拿到的键其实是 {@code grantedroleid}，
 * 取值直接 null。用 DTO + 下划线转驼峰是确定的，不依赖任何大小写约定。
 */
public class RolePermissionRow {

    private Long grantedRoleId;
    private Long permissionId;

    public Long getGrantedRoleId() { return grantedRoleId; }
    public void setGrantedRoleId(Long v) { this.grantedRoleId = v; }
    public Long getPermissionId() { return permissionId; }
    public void setPermissionId(Long v) { this.permissionId = v; }
}
