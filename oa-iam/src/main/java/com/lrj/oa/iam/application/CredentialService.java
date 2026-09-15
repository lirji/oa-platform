package com.lrj.oa.iam.application;

import com.lrj.oa.common.api.ResultCode;
import com.lrj.oa.common.context.TenantContext;
import com.lrj.oa.common.exception.BusinessException;
import com.lrj.oa.iam.api.dto.CredentialDtos.CreateCredential;
import com.lrj.oa.iam.api.dto.CredentialDtos.CredentialView;
import com.lrj.oa.iam.api.dto.CredentialDtos.IssuedCredential;
import com.lrj.oa.iam.domain.Credential;
import com.lrj.oa.iam.domain.Identity;
import com.lrj.oa.iam.domain.IdentityStatus;
import com.lrj.oa.iam.domain.IdentityType;
import com.lrj.oa.iam.infrastructure.mapper.CredentialMapper;
import com.lrj.oa.iam.infrastructure.mapper.IdentityMapper;
import com.lrj.oa.security.context.UserContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

/**
 * NHI 凭证。明文只在签发/轮换的返回值里出现，不写库、不打日志。
 */
@Service
public class CredentialService {

    private static final Logger log = LoggerFactory.getLogger(CredentialService.class);
    private static final Set<String> KINDS = Set.of("API_KEY", "AGENT_SECRET");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final CredentialMapper credentials;
    private final IdentityMapper identities;

    public CredentialService(CredentialMapper credentials, IdentityMapper identities) {
        this.credentials = credentials;
        this.identities = identities;
    }

    @Transactional
    public IssuedCredential issue(String identityId, CreateCredential cmd) {
        Identity principal = requireNhi(identityId);
        if (!IdentityStatus.ACTIVE.name().equals(principal.getStatus())
                && !IdentityStatus.CREATED.name().equals(principal.getStatus())) {
            throw BusinessException.of(ResultCode.IDENTITY_STATUS_CONFLICT,
                    "仅 ACTIVE 身份可签发凭证: " + principal.getStatus());
        }
        String kind = resolveKind(cmd == null ? null : cmd.kind(), principal.getIdentityType());
        OffsetDateTime expiresAt = cmd == null ? null : cmd.expiresAt();
        if (expiresAt != null && !expiresAt.isAfter(OffsetDateTime.now())) {
            throw BusinessException.of(ResultCode.BAD_REQUEST, "expiresAt 必须晚于当前时间");
        }
        return persistNew(principal.getId(), kind, expiresAt, UserContextHolder.require().userId());
    }

    public List<CredentialView> list(String identityId) {
        requireVisible(identityId);
        return credentials.selectByIdentity(TenantContext.get(), identityId).stream()
                .map(this::toView)
                .toList();
    }

    @Transactional
    public CredentialView revoke(long id) {
        Credential row = requireActive(id);
        String actor = UserContextHolder.require().userId();
        if (credentials.markInactive(TenantContext.get(), id, "REVOKED", actor, OffsetDateTime.now()) == 0) {
            throw BusinessException.of(ResultCode.CONFLICT, "凭证已被处理");
        }
        log.info("凭证已吊销 id={} identity={}", id, row.getIdentityId());
        return toView(credentials.selectById(TenantContext.get(), id));
    }

    @Transactional
    public IssuedCredential rotate(long id) {
        Credential old = requireActive(id);
        String actor = UserContextHolder.require().userId();
        if (credentials.markInactive(TenantContext.get(), id, "ROTATED", actor, OffsetDateTime.now()) == 0) {
            throw BusinessException.of(ResultCode.CONFLICT, "凭证已被处理");
        }
        IssuedCredential issued = persistNew(old.getIdentityId(), old.getKind(), old.getExpiresAt(), actor);
        log.info("凭证已轮换 oldId={} newId={} identity={}", id, issued.credential().id(), old.getIdentityId());
        return issued;
    }

    /** 身份停用/删除时收回全部有效凭证。 */
    @Transactional
    public int revokeAllOfIdentity(String identityId, String actor) {
        if (!StringUtils.hasText(identityId)) {
            return 0;
        }
        int n = credentials.revokeActiveOfIdentity(TenantContext.get(), identityId,
                StringUtils.hasText(actor) ? actor : "system", OffsetDateTime.now());
        if (n > 0) {
            log.info("身份停用连带吊销凭证 identity={} count={}", identityId, n);
        }
        return n;
    }

    private IssuedCredential persistNew(String identityId, String kind, OffsetDateTime expiresAt, String actor) {
        String secret = newSecret();
        OffsetDateTime now = OffsetDateTime.now();
        Credential row = new Credential();
        row.setTenantId(TenantContext.get());
        row.setIdentityId(identityId);
        row.setKind(kind);
        row.setSecretHash(sha256(secret));
        row.setLast4(secret.substring(secret.length() - 4));
        row.setStatus("ACTIVE");
        row.setExpiresAt(expiresAt);
        row.setCreatedBy(actor);
        row.setCreatedAt(now);
        credentials.insert(row);
        Credential stored = credentials.selectById(TenantContext.get(), row.getId());
        return new IssuedCredential(toView(stored), secret);
    }

    private Credential requireActive(long id) {
        Credential row = credentials.selectById(TenantContext.get(), id);
        if (row == null) {
            throw BusinessException.of(ResultCode.NOT_FOUND, "凭证不存在: " + id);
        }
        if (!"ACTIVE".equals(row.getStatus())) {
            throw BusinessException.of(ResultCode.CONFLICT, "凭证已失效: " + row.getStatus());
        }
        requireVisible(row.getIdentityId());
        return row;
    }

    private Identity requireNhi(String identityId) {
        Identity row = requireVisible(identityId);
        IdentityType type;
        try {
            type = IdentityType.valueOf(row.getIdentityType());
        } catch (IllegalArgumentException ex) {
            throw BusinessException.of(ResultCode.BAD_REQUEST, "未知身份类型");
        }
        if (type.human()) {
            throw BusinessException.of(ResultCode.BAD_REQUEST, "人员身份凭证由 IdP 管理，不能在此签发");
        }
        return row;
    }

    private Identity requireVisible(String identityId) {
        if (!StringUtils.hasText(identityId)) {
            throw BusinessException.of(ResultCode.IDENTITY_NOT_FOUND);
        }
        Identity row = identities.selectById(TenantContext.get(), identityId);
        if (row == null || IdentityStatus.DELETED.name().equals(row.getStatus())) {
            throw BusinessException.of(ResultCode.IDENTITY_NOT_FOUND);
        }
        return row;
    }

    private CredentialView toView(Credential row) {
        return new CredentialView(
                row.getId(), row.getIdentityId(), row.getKind(), row.getLast4(), row.getStatus(),
                row.getExpiresAt(), row.getCreatedBy(), row.getCreatedAt(), row.getRevokedAt());
    }

    private static String resolveKind(String requested, String identityType) {
        if (StringUtils.hasText(requested)) {
            String kind = requested.trim().toUpperCase();
            if (!KINDS.contains(kind)) {
                throw BusinessException.of(ResultCode.BAD_REQUEST, "未知凭证类型: " + requested);
            }
            return kind;
        }
        return "AGENT".equals(identityType) ? "AGENT_SECRET" : "API_KEY";
    }

    static String sha256(String secret) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(secret.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 不可用", ex);
        }
    }

    private static String newSecret() {
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        return "oa_sk_" + Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }
}
