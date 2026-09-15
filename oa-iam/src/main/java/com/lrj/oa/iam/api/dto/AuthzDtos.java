package com.lrj.oa.iam.api.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/** 统一授权判定契约。 */
public final class AuthzDtos {
    private AuthzDtos() {}

    public record PrincipalRef(String identityId, String identityType) {}

    public record ResourceRef(String type, String id, Map<String, Object> attributes) {}

    public record EnvironmentRef(String traceId, String tool, String now) {}

    public record CheckRequest(
            PrincipalRef principal,
            ResourceRef resource,
            String action,
            EnvironmentRef environment,
            String commandId
    ) {}

    public record CheckDecision(
            String decision,
            String policyId,
            String reason,
            String traceId,
            String principalId,
            OffsetDateTime evaluatedAt
    ) {}

    public record DecisionLogView(
            long id,
            String commandId,
            String traceId,
            String callerUserId,
            String principalId,
            String identityType,
            String resourceType,
            String resourceId,
            String action,
            String decision,
            String policyId,
            String reason,
            OffsetDateTime evaluatedAt
    ) {}

    public record DecisionPage(List<DecisionLogView> items, String nextCursor, boolean hasMore) {}

    /** 资源反查：谁持有该权限点，以及来源。 */
    public record WhoHasAccessHolder(
            String identityId,
            String identityType,
            String displayName,
            String externalKey,
            String source,
            String via,
            Long grantId
    ) {}

    public record WhoHasAccessResult(
            String permCode,
            boolean known,
            List<WhoHasAccessHolder> items
    ) {}
}
