package com.lrj.oa.iam.api.dto;

import java.time.OffsetDateTime;
import java.util.List;

/** 风险发现契约。规则内置，扫描在请求内完成。 */
public final class RiskFindingDtos {
    private RiskFindingDtos() {}

    public record RiskFindingView(
            long id,
            String ruleCode,
            String severity,
            String identityId,
            String identityType,
            String displayName,
            String externalKey,
            String summary,
            String status,
            OffsetDateTime detectedAt,
            OffsetDateTime updatedAt,
            String updatedBy,
            String note
    ) {}

    public record RiskFindingPage(List<RiskFindingView> items, String nextCursor, boolean hasMore) {}

    public record ScanResult(int opened, int scannedRules) {}

    public record ChangeStatusRequest(String status, String note) {}
}
