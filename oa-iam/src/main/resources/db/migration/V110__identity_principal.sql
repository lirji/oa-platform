-- ═══════════════════════════════════════════════════════════════════
-- 身份治理 S01：统一 Principal / Identity 投影。
-- IAM 原区间 V10–V19 已用完；out-of-order 开启，新版本从 V110 起。
-- Human 的 grant_record.subject_id 仍是 Casdoor sub，本表是叠加目录，不改判权热路径。
-- ═══════════════════════════════════════════════════════════════════

CREATE TABLE oa_iam.identity (
    id                 uuid         PRIMARY KEY,
    seq                bigserial    NOT NULL,
    tenant_id          bigint       NOT NULL DEFAULT 1,
    identity_type      varchar(32)  NOT NULL,
    display_name       varchar(128) NOT NULL,
    source             varchar(32)  NOT NULL DEFAULT 'INTERNAL',
    status             varchar(16)  NOT NULL DEFAULT 'ACTIVE',
    -- USER = Casdoor sub；NHI = 调用方给出的稳定业务键
    external_key       varchar(128) NOT NULL,
    employee_id        bigint,
    org_id             bigint,
    org_path           varchar(512),
    attributes         jsonb        NOT NULL DEFAULT '{}'::jsonb,
    risk_level         varchar(16)  NOT NULL DEFAULT 'LOW',
    owner_identity_id  uuid,
    expired_at         timestamptz,
    version            int          NOT NULL DEFAULT 0,
    created_at         timestamptz  NOT NULL DEFAULT now(),
    updated_at         timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT ck_identity_type CHECK (identity_type IN (
        'USER','SERVICE_ACCOUNT','API_CLIENT','APPLICATION','AGENT','BOT','AUTOMATION_WORKER')),
    CONSTRAINT ck_identity_status CHECK (status IN (
        'CREATED','ACTIVE','SUSPENDED','DISABLED','EXPIRED','DELETED')),
    CONSTRAINT ck_identity_source CHECK (source IN (
        'INTERNAL','CASDOOR','HR','LDAP','API','MOCK')),
    CONSTRAINT ck_identity_risk CHECK (risk_level IN ('LOW','MEDIUM','HIGH','CRITICAL')),
    CONSTRAINT ck_identity_org_path CHECK (org_path IS NULL OR org_path LIKE '/%/'),
    CONSTRAINT ck_identity_owner_self CHECK (owner_identity_id IS NULL OR owner_identity_id <> id)
);

COMMENT ON TABLE  oa_iam.identity IS '统一身份主体。Human 由员工投影，NHI/Agent 在此创建';
COMMENT ON COLUMN oa_iam.identity.id IS '身份主键 UUID，对外 identityId';
COMMENT ON COLUMN oa_iam.identity.seq IS '游标分页序号，不对外作为业务主键';
COMMENT ON COLUMN oa_iam.identity.tenant_id IS '租户。本期默认 1';
COMMENT ON COLUMN oa_iam.identity.identity_type IS 'USER 或非人身份类型';
COMMENT ON COLUMN oa_iam.identity.display_name IS '展示名';
COMMENT ON COLUMN oa_iam.identity.source IS '身份来源系统';
COMMENT ON COLUMN oa_iam.identity.status IS '生命周期状态';
COMMENT ON COLUMN oa_iam.identity.external_key IS '来源侧稳定键。USER 为 Casdoor sub';
COMMENT ON COLUMN oa_iam.identity.employee_id IS '关联员工主键，仅 USER';
COMMENT ON COLUMN oa_iam.identity.org_id IS '主岗组织投影，运行时不 join oa_org';
COMMENT ON COLUMN oa_iam.identity.org_path IS '主岗物化路径投影';
COMMENT ON COLUMN oa_iam.identity.attributes IS '可进 ABAC 的扩展属性 JSON';
COMMENT ON COLUMN oa_iam.identity.risk_level IS '风险等级投影，权威后期由 Risk 上下文写入';
COMMENT ON COLUMN oa_iam.identity.owner_identity_id IS 'Agent 等 NHI 的属主身份';
COMMENT ON COLUMN oa_iam.identity.expired_at IS '过期时间；判权切片再消费';
COMMENT ON COLUMN oa_iam.identity.version IS '乐观锁';
COMMENT ON COLUMN oa_iam.identity.created_at IS '创建时间';
COMMENT ON COLUMN oa_iam.identity.updated_at IS '最后更新时间';

CREATE UNIQUE INDEX uk_identity_type_key ON oa_iam.identity (tenant_id, identity_type, external_key);
CREATE UNIQUE INDEX uk_identity_seq ON oa_iam.identity (tenant_id, seq);
CREATE UNIQUE INDEX uk_identity_employee ON oa_iam.identity (tenant_id, employee_id)
    WHERE employee_id IS NOT NULL;
CREATE INDEX ix_identity_status ON oa_iam.identity (tenant_id, status);
CREATE INDEX ix_identity_owner ON oa_iam.identity (owner_identity_id)
    WHERE owner_identity_id IS NOT NULL;
CREATE INDEX ix_identity_org_path ON oa_iam.identity (org_path text_pattern_ops)
    WHERE org_path IS NOT NULL;

ALTER TABLE oa_iam.identity
    ADD CONSTRAINT fk_identity_owner
    FOREIGN KEY (owner_identity_id) REFERENCES oa_iam.identity(id);

CREATE TABLE oa_iam.identity_label (
    identity_id uuid        NOT NULL REFERENCES oa_iam.identity(id) ON DELETE CASCADE,
    label       varchar(32) NOT NULL,
    PRIMARY KEY (identity_id, label),
    CONSTRAINT ck_identity_label CHECK (label IN (
        'EMPLOYEE','CONTRACTOR','ADMIN','PRIVILEGED','SERVICE_ACCOUNT',
        'AGENT','EXTERNAL','HIGH_RISK'))
);

COMMENT ON TABLE  oa_iam.identity_label IS '身份标签，可进入后续 ABAC Context';
COMMENT ON COLUMN oa_iam.identity_label.identity_id IS '身份主键';
COMMENT ON COLUMN oa_iam.identity_label.label IS '标签枚举值';

-- 一次性投影现有员工。这是迁移回填，不是运行期跨 schema join。
INSERT INTO oa_iam.identity (
    id, tenant_id, identity_type, display_name, source, status,
    external_key, employee_id, org_id, org_path, risk_level, version
)
SELECT gen_random_uuid(),
       e.tenant_id,
       'USER',
       e.name,
       'CASDOOR',
       CASE e.status
           WHEN 'LEFT' THEN 'DISABLED'
           WHEN 'LEAVING' THEN 'SUSPENDED'
           ELSE 'ACTIVE'
       END,
       e.user_id,
       e.id,
       a.org_unit_id,
       u.path,
       'LOW',
       0
  FROM oa_org.employee e
  LEFT JOIN oa_org.employee_org_assignment a
         ON a.employee_id = e.id
        AND a.assignment_type = 'PRIMARY'
        AND a.valid_to IS NULL
  LEFT JOIN oa_org.org_unit u ON u.id = a.org_unit_id;

INSERT INTO oa_iam.identity_label (identity_id, label)
SELECT i.id,
       CASE WHEN e.employment_type IN ('OUTSOURCE', 'CONSULTANT') THEN 'CONTRACTOR' ELSE 'EMPLOYEE' END
  FROM oa_iam.identity i
  JOIN oa_org.employee e ON e.id = i.employee_id
 WHERE i.identity_type = 'USER';

INSERT INTO oa_iam.permission (code, name, type, module, builtin, require_elevation)
VALUES
  ('oa:iam:identity:view',  '查看身份目录', 'API', 'iam', true, false),
  ('oa:iam:identity:admin', '管理身份主体', 'API', 'iam', true, false)
ON CONFLICT (code) DO NOTHING;

INSERT INTO oa_iam.role_permission (role_id, permission_id)
SELECT r.id, p.id
  FROM oa_iam.role r, oa_iam.permission p
 WHERE r.code = 'SUPER_ADMIN'
   AND p.code IN ('oa:iam:identity:view', 'oa:iam:identity:admin')
ON CONFLICT DO NOTHING;
