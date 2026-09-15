package com.lrj.oa.iam.api.dto;

import java.time.OffsetDateTime;

/** 身份同步契约。连接器仅 INTERNAL / MOCK。 */
public final class IdentitySyncDtos {
    private IdentitySyncDtos() {}

    public record CreateSyncJob(String connector, String mode, String commandId) {}

    public record SyncJobView(
            long id,
            String commandId,
            String connector,
            String mode,
            String status,
            int createdCount,
            int updatedCount,
            int skippedCount,
            String errorMessage,
            OffsetDateTime startedAt,
            OffsetDateTime finishedAt,
            String createdBy,
            OffsetDateTime createdAt
    ) {}
}
