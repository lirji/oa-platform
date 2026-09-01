-- Tenant-scoped permission epochs let a role change invalidate only its tenant.
-- Keep the legacy singleton epoch as a rolling-deployment fence for old binaries.
CREATE TABLE oa_iam.tenant_perm_epoch (
    tenant_id  bigint      PRIMARY KEY,
    epoch      bigint      NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_tenant_perm_epoch_tenant CHECK (tenant_id > 0),
    CONSTRAINT ck_tenant_perm_epoch_value CHECK (epoch >= 0)
);

INSERT INTO oa_iam.tenant_perm_epoch(tenant_id, epoch)
SELECT tenant_id, (SELECT epoch FROM oa_iam.perm_epoch WHERE id = 1)
  FROM (
        SELECT 1::bigint AS tenant_id
        UNION
        SELECT tenant_id FROM oa_iam.role
        UNION
        SELECT tenant_id FROM oa_iam.grant_record
  ) tenants
ON CONFLICT (tenant_id) DO NOTHING;

-- IAM domain-event outbox. Cache invalidation still uses Redis Pub/Sub; this table
-- is for durable role-change facts consumed by audit/integration subscribers.
CREATE TABLE oa_iam.iam_outbox (
    id              bigserial    PRIMARY KEY,
    event_id        uuid         NOT NULL UNIQUE,
    tenant_id       bigint       NOT NULL,
    topic           varchar(128) NOT NULL,
    msg_key         varchar(160) NOT NULL,
    payload         jsonb        NOT NULL,
    status          varchar(16)  NOT NULL DEFAULT 'PENDING',
    attempts        int          NOT NULL DEFAULT 0,
    next_attempt_at timestamptz  NOT NULL DEFAULT now(),
    claimed_by      varchar(96),
    claimed_until   timestamptz,
    last_error      text,
    created_at      timestamptz  NOT NULL DEFAULT now(),
    sent_at         timestamptz,
    CONSTRAINT ck_iam_outbox_status CHECK (status IN ('PENDING','PROCESSING','SENT','FAILED')),
    CONSTRAINT ck_iam_outbox_attempts CHECK (attempts >= 0)
);

CREATE INDEX ix_iam_outbox_ready
    ON oa_iam.iam_outbox(next_attempt_at, id)
    WHERE status IN ('PENDING','PROCESSING');

COMMENT ON TABLE oa_iam.tenant_perm_epoch IS 'Per-tenant permission snapshot epoch; authoritative cache invalidation version';
COMMENT ON TABLE oa_iam.iam_outbox IS 'Durable IAM domain-event outbox delivered asynchronously to the OA Kafka bus';
COMMENT ON COLUMN oa_iam.iam_outbox.event_id IS 'Globally unique consumer idempotency key';
COMMENT ON COLUMN oa_iam.iam_outbox.claimed_until IS 'Expired PROCESSING rows become claimable after publisher failure';
