package com.lrj.oa.iam.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.util.List;

public final class IamCommands {

    private IamCommands() {}

    /** 授予角色。支持 USER/ORG_UNIT/POSITION/USER_GROUP。 */
    public record Grant(
            @NotBlank String subjectType,          // USER | ORG_UNIT | POSITION | USER_GROUP
            @NotBlank String subjectId,
            @NotNull  Long roleId,
            String scopeType,                      // 空时采用角色默认；ALL|ORG_AND_SUB|ORG|SELF|CUSTOM|NONE
            List<Long> scopeOrgIds,
            Boolean includeDescendants,
            String grantType,                      // PERMANENT(默认) | TEMPORARY
            OffsetDateTime validFrom,
            OffsetDateTime validTo,                // TEMPORARY 必填
            String reason
    ) {}

    /** JIT 提权申请。上限由 oa.iam.elevation.max-hours 控制。 */
    public record Elevate(
            @NotNull Long roleId,
            @NotBlank String reason,
            Integer hours
    ) {}

    public record ElevationDecision(String reason) {}

    /** 委托代理：把待办与职权临时交给他人。不会让代理人获得委托人的其它权限。 */
    public record Delegate(
            @NotBlank String delegateeUserId,
            String scope,                          // ALL_TODO(默认) | BY_PROCESS_KEY | BY_ROLE
            List<String> processKeys,
            List<Long> roleIds,
            OffsetDateTime validFrom,
            @NotNull OffsetDateTime validTo,
            String reason
    ) {}

    public record CreateGroup(@NotBlank String code, @NotBlank String name, String description) {}

    public record UpdateGroup(@NotBlank String name, String description) {}

    public record AddGroupMembers(
            @NotEmpty List<@NotBlank String> userIds,
            OffsetDateTime validFrom,
            OffsetDateTime validTo
    ) {}

    public record CreateRole(
            @NotBlank String code,
            @NotBlank String name,
            @NotBlank String defaultScope,
            String remark,
            List<@NotNull Long> permissionIds,
            List<@NotNull Long> inheritedRoleIds
    ) {}

    public record UpdateRole(
            @NotBlank String name,
            @NotBlank String defaultScope,
            String remark,
            @NotNull Integer version
    ) {}

    public record CopyRole(
            @NotBlank String code,
            @NotBlank String name
    ) {}

    public record SetRoleEnabled(@NotNull Boolean enabled, @NotNull Integer version) {}

    public record ReplaceRolePermissions(
            @NotNull List<@NotNull Long> permissionIds,
            @NotNull Integer version
    ) {}

    public record ReplaceRoleInheritance(
            @NotNull List<@NotNull Long> inheritedRoleIds,
            @NotNull Integer version
    ) {}

    public record AbacCondition(
            @NotNull Long roleId,
            @NotNull Long permissionId,
            @NotBlank String expression,
            String description,
            Boolean enabled
    ) {}

    public record ValidateAbac(@NotBlank String expression) {}
}
