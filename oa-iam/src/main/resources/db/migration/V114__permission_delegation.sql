-- S05：权限委托。与 oa_iam.delegation（待办代理）分表，禁止混用（D-GOV-014）。
-- 过期靠 valid_from/valid_to 时间窗；撤销只改 status，不删行。

CREATE TABLE oa_iam.permission_delegation (
    id                      bigserial    PRIMARY KEY,
    tenant_id               bigint       NOT NULL DEFAULT 1,
    delegator_identity_id   uuid         NOT NULL REFERENCES oa_iam.identity(id),
    delegator_user_id       varchar(64)  NOT NULL,
    delegatee_identity_id   uuid         NOT NULL REFERENCES oa_iam.identity(id),
    perm_codes              jsonb        NOT NULL DEFAULT '[]'::jsonb,
    role_ids                jsonb        NOT NULL DEFAULT '[]'::jsonb,
    resource_scope          jsonb,
    valid_from              timestamptz  NOT NULL,
    valid_to                timestamptz  NOT NULL,
    re_delegate             boolean      NOT NULL DEFAULT false,
    reason                  text         NOT NULL,
    status                  varchar(16)  NOT NULL DEFAULT 'ACTIVE',
    created_at              timestamptz  NOT NULL DEFAULT now(),
    revoked_at              timestamptz,
    revoked_by              varchar(64),
    CONSTRAINT ck_perm_delegation_window CHECK (valid_to > valid_from),
    CONSTRAINT ck_perm_delegation_status CHECK (status IN ('ACTIVE','REVOKED')),
    CONSTRAINT ck_perm_delegation_not_self CHECK (delegator_identity_id <> delegatee_identity_id)
);

COMMENT ON TABLE  oa_iam.permission_delegation IS '权限委托。时间窗内被委托人可通过 Check；不写入待办 delegation 表';
COMMENT ON COLUMN oa_iam.permission_delegation.delegator_identity_id IS '委托人身份';
COMMENT ON COLUMN oa_iam.permission_delegation.delegator_user_id IS '委托人 Casdoor sub，Check 时校验其仍持有权限';
COMMENT ON COLUMN oa_iam.permission_delegation.delegatee_identity_id IS '被委托人身份';
COMMENT ON COLUMN oa_iam.permission_delegation.perm_codes IS '生效权限点（含创建时从角色展开）';
COMMENT ON COLUMN oa_iam.permission_delegation.role_ids IS '申请时指定的角色，仅审计';
COMMENT ON COLUMN oa_iam.permission_delegation.re_delegate IS '是否允许再次委托；本期强制 false';
COMMENT ON COLUMN oa_iam.permission_delegation.status IS 'ACTIVE 或 REVOKED；过期不改状态，靠时间窗';

CREATE INDEX ix_perm_delegation_delegatee
    ON oa_iam.permission_delegation (tenant_id, delegatee_identity_id, id);
CREATE INDEX ix_perm_delegation_delegator
    ON oa_iam.permission_delegation (tenant_id, delegator_identity_id, id);
