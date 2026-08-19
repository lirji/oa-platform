package com.lrj.oa.org.api.dto;

import java.time.LocalDate;

/**
 * 员工视图。手机/身份证等敏感字段<b>不在这里</b> ——
 * 它们走 Phase 2 的 {@code @Sensitive} 序列化层脱敏，避免任何路径上意外全量返回明文。
 */
public record EmployeeView(
        Long id, String userId, String empNo, String name, String enName, String avatar,
        String email, String employmentType, String status,
        LocalDate hireDate,
        Long primaryOrgId, String primaryOrgName, String primaryOrgPath,
        Long primaryPositionId, String primaryPositionName
) {}
