package com.lrj.oa.iam.api.dto;

import java.time.OffsetDateTime;
import java.util.List;

/** 权限申请契约。 */
public final class AccessRequestDtos {
    private AccessRequestDtos() {}

    public record CreateAccessRequest(
            String requestType,
            Long roleId,
            List<String> permCodes,
            OffsetDateTime validTo,
            String reason,
            String commandId
    ) {}

    public record AccessRequestDecision(String reason) {}

    public record AccessRequestView(
            long id,
            String commandId,
            String requesterUserId,
            String requesterIdentityId,
            String requestType,
            Long roleId,
            String roleCode,
            String roleName,
            OffsetDateTime validTo,
            String reason,
            String status,
            String decidedBy,
            OffsetDateTime decidedAt,
            String decisionReason,
            Long grantId,
            String approvalInstanceId,
            OffsetDateTime requestedAt
    ) {}

    public record AccessRequestPage(List<AccessRequestView> items, String nextCursor, boolean hasMore) {}

    public record RoleOption(long id, String code, String name) {}
}
