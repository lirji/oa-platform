package com.lrj.oa.iam.api.dto;

import java.time.OffsetDateTime;
import java.util.List;

/** 身份治理对外 DTO。字段与 CONTRACTS IdentityView 对齐。 */
public final class IdentityDtos {
    private IdentityDtos() {}

    public record IdentityView(
            String identityId,
            long tenantId,
            String identityType,
            String displayName,
            String source,
            String status,
            String externalKey,
            Long employeeId,
            Long orgId,
            String orgPath,
            String riskLevel,
            String ownerIdentityId,
            OffsetDateTime expiredAt,
            int version,
            List<String> labels,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {}

    public record IdentityPage(
            List<IdentityView> items,
            String nextCursor,
            boolean hasMore
    ) {}

    public record CreateIdentity(
            String identityType,
            String displayName,
            String externalKey,
            String ownerIdentityId,
            OffsetDateTime expiredAt,
            List<String> labels
    ) {}

    public record UpdateIdentity(
            String displayName,
            OffsetDateTime expiredAt,
            String ownerIdentityId,
            int version
    ) {}

    public record ChangeIdentityStatus(String status, int version) {}

    public record ReplaceIdentityLabels(List<String> labels, int version) {}

    public record GraphNode(String id, String type, String label, String refId) {}

    public record GraphEdge(String from, String to, String type) {}

    public record IdentityGraph(List<GraphNode> nodes, List<GraphEdge> edges) {}
}
