package com.lrj.oa.org.api.event;

/**
 * 员工档案已创建。IAM 用它幂等投影 USER 身份，不在组织域写身份表。
 *
 * @param userId         Casdoor sub
 * @param employeeId     员工主键
 * @param name           姓名
 * @param employmentType 用工类型，决定 EMPLOYEE/CONTRACTOR 标签
 * @param status         员工状态
 * @param primaryOrgId   主岗组织，可空
 * @param primaryOrgPath 主岗路径投影，可空
 */
public record EmployeeCreatedEvent(
        String userId,
        Long employeeId,
        String name,
        String employmentType,
        String status,
        Long primaryOrgId,
        String primaryOrgPath
) {}
