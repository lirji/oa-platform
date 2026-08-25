package com.lrj.oa.flow.api.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** 工作台“我发起的”统一视图。 */
public record MyApplicationView(String bizType, String docNo, String status, String title,
                                String summary, BigDecimal amount, BigDecimal days,
                                OffsetDateTime createdAt) {}
