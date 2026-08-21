-- USER_GROUP 主体与 ABAC 条件运行时元数据。
-- 表均为 additive；ABAC 仍由 oa.iam.abac.enabled 灰度开启。

CREATE TABLE oa_iam.user_group (
    id          bigserial    PRIMARY KEY,
    tenant_id   bigint       NOT NULL DEFAULT 1,
    code        varchar(64)  NOT NULL,
    name        varchar(128) NOT NULL,
    description text,
    status      varchar(16)  NOT NULL DEFAULT 'ACTIVE',
    created_by  varchar(64),
    created_at  timestamptz  NOT NULL DEFAULT now(),
    updated_by  varchar(64),
    updated_at  timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT ck_user_group_status CHECK (status IN ('ACTIVE','DISABLED'))
);
CREATE UNIQUE INDEX uk_user_group_code ON oa_iam.user_group(tenant_id, code);
CREATE INDEX ix_user_group_status ON oa_iam.user_group(tenant_id, status, id);

CREATE TABLE oa_iam.user_group_member (
    id          bigserial   PRIMARY KEY,
    tenant_id   bigint      NOT NULL DEFAULT 1,
    group_id    bigint      NOT NULL REFERENCES oa_iam.user_group(id),
    user_id     varchar(64) NOT NULL,
    valid_from  timestamptz NOT NULL DEFAULT now(),
    valid_to    timestamptz,
    created_by  varchar(64),
    created_at  timestamptz NOT NULL DEFAULT now(),
    revoked_by  varchar(64),
    revoked_at  timestamptz,
    CONSTRAINT ck_user_group_member_period CHECK (valid_to IS NULL OR valid_to > valid_from)
);
CREATE UNIQUE INDEX uk_user_group_member_active
    ON oa_iam.user_group_member(tenant_id, group_id, user_id) WHERE revoked_at IS NULL;
CREATE INDEX ix_user_group_member_user
    ON oa_iam.user_group_member(tenant_id, user_id, valid_from, valid_to) WHERE revoked_at IS NULL;
CREATE INDEX ix_user_group_member_group
    ON oa_iam.user_group_member(tenant_id, group_id, id) WHERE revoked_at IS NULL;

ALTER TABLE oa_iam.permission_condition
    ADD COLUMN tenant_id bigint NOT NULL DEFAULT 1,
    ADD COLUMN enabled boolean NOT NULL DEFAULT true,
    ADD COLUMN created_by varchar(64),
    ADD COLUMN updated_by varchar(64),
    ADD COLUMN updated_at timestamptz NOT NULL DEFAULT now(),
    ADD COLUMN deleted_at timestamptz;

CREATE INDEX ix_permission_condition_lookup
    ON oa_iam.permission_condition(tenant_id, role_id, permission_id, id)
    WHERE enabled AND deleted_at IS NULL;

COMMENT ON TABLE oa_iam.user_group IS 'OA 内部显式成员用户组；不复用 Casdoor group';
COMMENT ON TABLE oa_iam.user_group_member IS '用户组成员时间窗；判权与快照 TTL 都按 valid_from/valid_to';
COMMENT ON COLUMN oa_iam.permission_condition.expression IS
    '受限 SpEL：只允许方法参数/只读 user、比较/布尔/基础算术；禁止类型、Bean、构造器与方法调用';
