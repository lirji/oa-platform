-- S06：NHI 凭证。库内只存哈希与 last4，明文只在创建/轮换响应出现一次。

CREATE TABLE oa_iam.credential (
    id              bigserial    PRIMARY KEY,
    tenant_id       bigint       NOT NULL DEFAULT 1,
    identity_id     uuid         NOT NULL REFERENCES oa_iam.identity(id),
    kind            varchar(32)  NOT NULL,
    secret_hash     varchar(64)  NOT NULL,
    last4           varchar(8)   NOT NULL,
    status          varchar(16)  NOT NULL DEFAULT 'ACTIVE',
    expires_at      timestamptz,
    created_by      varchar(64)  NOT NULL,
    created_at      timestamptz  NOT NULL DEFAULT now(),
    revoked_at      timestamptz,
    revoked_by      varchar(64),
    CONSTRAINT ck_credential_kind CHECK (kind IN ('API_KEY', 'AGENT_SECRET')),
    CONSTRAINT ck_credential_status CHECK (status IN ('ACTIVE', 'REVOKED', 'ROTATED'))
);

COMMENT ON TABLE  oa_iam.credential IS '非人身份凭证。明文不入库，仅哈希与 last4';
COMMENT ON COLUMN oa_iam.credential.secret_hash IS '明文 SHA-256 十六进制，用于后续核验';
COMMENT ON COLUMN oa_iam.credential.last4 IS '明文末四位，列表展示用';
COMMENT ON COLUMN oa_iam.credential.status IS 'ACTIVE / REVOKED / ROTATED';

CREATE UNIQUE INDEX uk_credential_hash
    ON oa_iam.credential (tenant_id, secret_hash);
CREATE INDEX ix_credential_identity
    ON oa_iam.credential (tenant_id, identity_id, id);
