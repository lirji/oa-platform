package com.lrj.oa.doc.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

/** 文档域 DTO。 */
public final class DocDtos {

    private DocDtos() {}

    public record DraftDoc(@NotBlank String direction, @NotBlank String title, String body,
                           String docType, String urgency, String secrecy, String sourceOrg) {}

    public record DocView(Long id, String direction, String docNumber, String title, String body,
                          String docType, String urgency, String secrecy, String status,
                          String drafterId, OffsetDateTime issuedAt, OffsetDateTime archivedAt,
                          Long orgId) {}

    public record CreateKbDoc(Long folderId, @NotBlank String title, String summary, String body,
                              String fileKey) {}

    public record KbDocView(Long id, Long folderId, String title, String summary, String body,
                            String ownerId, int version, OffsetDateTime updatedAt) {}

    /** 共享给谁。subjectType ∈ USER / ORG / ALL；ORG 时 subjectId 是组织 id。 */
    public record ShareKb(@NotBlank String resourceType, @NotNull Long resourceId,
                          @NotBlank String subjectType, String subjectId, String level) {}

    /** 判权解释：为什么这个人能（不能）看这份文档。知识库开关无论开关都要能回答。 */
    public record KbAccessExplain(Long docId, String userId, boolean allowed, String level,
                                  String decidedBy, String detail) {}
}
