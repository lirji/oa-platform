package com.lrj.oa.org.api.dto;

import java.time.LocalDate;

/** 一段任职关系。同一人可有多条（主岗 + 兼岗 + 虚线）。 */
public record AssignmentView(
        Long id, Long employeeId, Long orgUnitId, String orgName, String orgPath,
        Long positionId, String positionName,
        String assignmentType, boolean leader,
        LocalDate validFrom, LocalDate validTo
) {
    public boolean active() { return validTo == null; }
}
