package com.lrj.oa.org.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;

/** 组织域的入站命令。集中在一个文件里，避免十几个单字段 record 散落。 */
public final class OrgCommands {

    private OrgCommands() {}

    public record CreateOrg(
            Long parentId,                                  // null = 建根
            @NotBlank @Pattern(regexp = "[A-Za-z0-9_.-]{1,64}") String code,
            @NotBlank String name,
            String shortName,
            @NotBlank String type,                          // OrgUnitType
            Integer sortOrder,
            String leaderUserId,
            String costCenter,
            String remark
    ) {}

    public record UpdateOrg(
            String name, String shortName, String type, Integer sortOrder,
            String leaderUserId, String deputyLeaderUserId, String costCenter, String remark
    ) {}

    public record MoveOrg(Long newParentId) {}

    public record CreateEmployee(
            @NotBlank String userId,                        // Casdoor sub
            @NotBlank String empNo,
            @NotBlank String name,
            String enName, String email, String mobile, String idCard,
            String gender, LocalDate birthday, LocalDate hireDate,
            String employmentType,
            @NotNull Long primaryOrgId,                     // 入职即定主岗
            Long primaryPositionId,
            Long managerEmployeeId                          // 实线上级，可空
    ) {}

    public record UpdateEmployee(
            String name, String enName, String email, String mobile, String avatar,
            String gender, LocalDate birthday
    ) {}

    /** 调岗：关闭旧任职、开新任职，历史仍可 as-of 还原。 */
    public record TransferEmployee(
            @NotNull Long targetOrgId,
            Long targetPositionId,
            @NotNull LocalDate effectiveDate,
            Boolean asLeader,
            String reason
    ) {}

    /** 加兼岗 / 虚线归属。 */
    public record AddAssignment(
            @NotNull Long orgUnitId,
            Long positionId,
            @NotBlank String assignmentType,                // CONCURRENT | DOTTED
            Boolean asLeader,
            LocalDate validFrom
    ) {}

    public record SetReportingLine(
            @NotNull Long managerEmployeeId,
            @NotBlank String type,                          // SOLID | DOTTED
            LocalDate validFrom
    ) {}
}
