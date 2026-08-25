package com.lrj.oa.iam.api.dto;

import java.util.List;

public final class RoleAdminDtos {
    private RoleAdminDtos() {}

    public record RoleSummary(
            Long id, String code, String name, String type, String defaultScope,
            String status, boolean builtin, Integer version, String remark,
            long directPermissionCount, long effectivePermissionCount,
            long inheritedRoleCount, long grantCount
    ) {}

    public record RoleDetail(
            Long id, String code, String name, String type, String defaultScope,
            String status, boolean builtin, Integer version, String remark,
            List<Long> directPermissionIds, List<Long> effectivePermissionIds,
            List<Long> inheritedRoleIds, long grantCount
    ) {}
}
