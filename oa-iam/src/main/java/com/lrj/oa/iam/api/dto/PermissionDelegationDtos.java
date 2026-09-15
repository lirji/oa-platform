package com.lrj.oa.iam.api.dto;

import java.time.OffsetDateTime;
import java.util.List;

/** 权限委托契约。与待办委托并存。 */
public final class PermissionDelegationDtos {
    private PermissionDelegationDtos() {}

    public record CreatePermissionDelegation(
            String delegateeIdentityId,
            List<String> permCodes,
            List<Long> roleIds,
            String resourceScope,
            OffsetDateTime validFrom,
            OffsetDateTime validTo,
            Boolean reDelegate,
            String reason
    ) {}

    public record PermissionDelegationView(
            long id,
            String direction,
            String delegatorIdentityId,
            String delegatorUserId,
            String delegateeIdentityId,
            List<String> permCodes,
            List<Long> roleIds,
            OffsetDateTime validFrom,
            OffsetDateTime validTo,
            boolean reDelegate,
            String reason,
            String status,
            OffsetDateTime createdAt,
            OffsetDateTime revokedAt
    ) {}

    public record RoleOption(long id, String code, String name) {}

    public record PermissionDelegationPage(
            List<PermissionDelegationView> items,
            String nextCursor,
            boolean hasMore
    ) {}
}
