package com.lrj.oa.iam.api.dto;

import java.time.OffsetDateTime;

/** NHI 凭证契约。明文只出现在签发/轮换响应。 */
public final class CredentialDtos {
    private CredentialDtos() {}

    public record CreateCredential(String kind, OffsetDateTime expiresAt) {}

    public record CredentialView(
            long id,
            String identityId,
            String kind,
            String last4,
            String status,
            OffsetDateTime expiresAt,
            String createdBy,
            OffsetDateTime createdAt,
            OffsetDateTime revokedAt
    ) {}

    /** 签发或轮换：secret 仅此一次。 */
    public record IssuedCredential(CredentialView credential, String secret) {}
}
