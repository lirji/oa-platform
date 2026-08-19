package com.lrj.oa.flow.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class FlowCommands {

    private FlowCommands() {}

    public record SubmitLeave(
            @NotBlank String leaveTypeCode,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,
            @NotNull BigDecimal days,
            String reason
    ) {}

    public record CompleteTask(
            @NotBlank String outcome,          // APPROVE | REJECT
            String comment,
            /** 代理办理时填被代理人；服务端会校验委托关系是否有效。 */
            String onBehalfOf
    ) {}

    public record GrantBalance(
            @NotBlank String userId,
            @NotBlank String leaveTypeCode,
            @NotBlank String period,
            @NotNull BigDecimal days
    ) {}
}
