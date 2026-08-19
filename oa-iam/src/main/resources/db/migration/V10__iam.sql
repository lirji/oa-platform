-- ═══════════════════════════════════════════════════════════════════
-- Phase 2 权限域（ADR-0005 / 0007 / 0008）
-- 迁移版本分配：V1 oa-app / V2-V9 oa-org / V10-V19 oa-iam / V20+ 其它模块
-- ═══════════════════════════════════════════════════════════════════

-- ───────────────────────────────── 权限点
CREATE TABLE oa_iam.permission (
    id                bigserial    PRIMARY KEY,
    code              varchar(128) NOT NULL UNIQUE,   -- 'oa:leave:approve'
    name              varchar(128) NOT NULL,
    type              varchar(8)   NOT NULL,          -- MENU|BUTTON|API|DATA|FIELD
    module            varchar(32),                    -- org|iam|flow|attendance|doc|admin|report
    parent_id         bigint       REFERENCES oa_iam.permission(id),
    resource          varchar(256),                   -- API 类型：URL 模式（留给运营配置，非安全边界）
    method            varchar(8),
    icon              varchar(64),
    route             varchar(256),
    sort_order        int          NOT NULL DEFAULT 0,
    status            varchar(16)  NOT NULL DEFAULT 'ACTIVE',
    builtin           boolean      NOT NULL DEFAULT false,
    -- ★ 高危权限点：即便持有永久授权，也必须存在活跃的 JIT 提权才放行
    require_elevation boolean      NOT NULL DEFAULT false,
    remark            text,
    created_at        timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT ck_perm_type CHECK (type IN ('MENU','BUTTON','API','DATA','FIELD'))
);
CREATE INDEX ix_perm_module ON oa_iam.permission(module);
CREATE INDEX ix_perm_parent ON oa_iam.permission(parent_id);

-- ───────────────────────────────── 角色 + 角色继承（RBAC1）
CREATE TABLE oa_iam.role (
    id            bigserial   PRIMARY KEY,
    tenant_id     bigint      NOT NULL DEFAULT 1,
    code          varchar(64) NOT NULL,
    name          varchar(128) NOT NULL,
    type          varchar(16) NOT NULL DEFAULT 'BUSINESS',   -- SYSTEM|BUSINESS|CUSTOM
    default_scope varchar(24) NOT NULL DEFAULT 'SELF',
    status        varchar(16) NOT NULL DEFAULT 'ACTIVE',
    builtin       boolean     NOT NULL DEFAULT false,
    version       int         NOT NULL DEFAULT 0,
    remark        text,
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_role_scope CHECK (default_scope IN ('ALL','ORG_AND_SUB','ORG','SELF','CUSTOM','NONE'))
);
CREATE UNIQUE INDEX uk_role_code ON oa_iam.role(tenant_id, code);

-- 角色继承闭包。和组织树同一套思路：把递归查询摊平成一次 join。
CREATE TABLE oa_iam.role_inherit (
    ancestor_role_id   bigint   NOT NULL REFERENCES oa_iam.role(id) ON DELETE CASCADE,
    descendant_role_id bigint   NOT NULL REFERENCES oa_iam.role(id) ON DELETE CASCADE,
    distance           smallint NOT NULL,
    PRIMARY KEY (ancestor_role_id, descendant_role_id)
);

CREATE TABLE oa_iam.role_permission (
    role_id       bigint NOT NULL REFERENCES oa_iam.role(id) ON DELETE CASCADE,
    permission_id bigint NOT NULL REFERENCES oa_iam.permission(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);
CREATE INDEX ix_rp_perm ON oa_iam.role_permission(permission_id);

-- ───────────────────────────────── 授权表：全部权限语义的收敛点 ★
CREATE TABLE oa_iam.grant_record (
    id                   bigserial   PRIMARY KEY,
    tenant_id            bigint      NOT NULL DEFAULT 1,
    subject_type         varchar(16) NOT NULL,     -- USER|ORG_UNIT|POSITION|USER_GROUP
    subject_id           varchar(64) NOT NULL,
    role_id              bigint      NOT NULL REFERENCES oa_iam.role(id),
    scope_type           varchar(24) NOT NULL,
    scope_org_ids        jsonb,
    -- subject=ORG_UNIT 时：授权是否向下惠及子部门全员（"部门权限继承"的第一层含义）
    include_descendants  boolean     NOT NULL DEFAULT true,
    grant_type           varchar(16) NOT NULL DEFAULT 'PERMANENT',  -- PERMANENT|TEMPORARY|DELEGATED
    valid_from           timestamptz NOT NULL DEFAULT now(),
    valid_to             timestamptz,              -- null = 永久；★ 临时授权靠它，判定时按时间窗过滤
    source               varchar(16) NOT NULL DEFAULT 'MANUAL',     -- MANUAL|RULE|SYNC|APPROVAL
    approval_instance_id varchar(64),
    reason               text,
    granted_by           varchar(64),
    granted_at           timestamptz NOT NULL DEFAULT now(),
    revoked_at           timestamptz,
    revoked_by           varchar(64),
    revoke_reason        text,
    CONSTRAINT ck_grant_subject CHECK (subject_type IN ('USER','ORG_UNIT','POSITION','USER_GROUP')),
    CONSTRAINT ck_grant_type    CHECK (grant_type IN ('PERMANENT','TEMPORARY','DELEGATED')),
    CONSTRAINT ck_grant_scope   CHECK (scope_type IN ('ALL','ORG_AND_SUB','ORG','SELF','CUSTOM','NONE')),
    CONSTRAINT ck_grant_period  CHECK (valid_to IS NULL OR valid_to > valid_from)
);
CREATE INDEX ix_grant_subject  ON oa_iam.grant_record(subject_type, subject_id) WHERE revoked_at IS NULL;
CREATE INDEX ix_grant_role     ON oa_iam.grant_record(role_id) WHERE revoked_at IS NULL;
-- 到期回收任务扫这个
CREATE INDEX ix_grant_expiring ON oa_iam.grant_record(valid_to) WHERE valid_to IS NOT NULL AND revoked_at IS NULL;

COMMENT ON COLUMN oa_iam.grant_record.valid_to IS '临时授权到期时间。判权时按时间窗过滤，不依赖定时任务删除';

-- ───────────────────────────────── 委托代理（与临时提权是不同的东西）
CREATE TABLE oa_iam.delegation (
    id                bigserial   PRIMARY KEY,
    tenant_id         bigint      NOT NULL DEFAULT 1,
    delegator_user_id varchar(64) NOT NULL,
    delegatee_user_id varchar(64) NOT NULL,
    scope             varchar(16) NOT NULL DEFAULT 'ALL_TODO',   -- ALL_TODO|BY_PROCESS_KEY|BY_ROLE
    process_keys      jsonb,
    role_ids          jsonb,
    valid_from        timestamptz NOT NULL DEFAULT now(),
    valid_to          timestamptz,
    reason            text,
    status            varchar(16) NOT NULL DEFAULT 'ACTIVE',     -- ACTIVE|EXPIRED|REVOKED
    created_by        varchar(64),
    created_at        timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_deleg_self   CHECK (delegator_user_id <> delegatee_user_id),
    CONSTRAINT ck_deleg_status CHECK (status IN ('ACTIVE','EXPIRED','REVOKED'))
);
CREATE INDEX ix_deleg_delegatee ON oa_iam.delegation(delegatee_user_id) WHERE status = 'ACTIVE';
CREATE INDEX ix_deleg_delegator ON oa_iam.delegation(delegator_user_id) WHERE status = 'ACTIVE';

-- ───────────────────────────────── ABAC 条件（建表，引擎默认关，Phase 4 报销场景才开）
CREATE TABLE oa_iam.permission_condition (
    id            bigserial    PRIMARY KEY,
    role_id       bigint       NOT NULL REFERENCES oa_iam.role(id) ON DELETE CASCADE,
    permission_id bigint       NOT NULL REFERENCES oa_iam.permission(id) ON DELETE CASCADE,
    expression    varchar(512) NOT NULL,     -- SpEL: "#amount <= 5000"
    description   text,
    created_at    timestamptz  NOT NULL DEFAULT now()
);

-- ───────────────────────────────── 全局权限纪元（缓存失效的真值源）
-- Redis Pub/Sub 只是【快】通道；这张表才是权威，保证重启后仍能正确判断快照是否过期。
CREATE TABLE oa_iam.perm_epoch (
    id         smallint    PRIMARY KEY DEFAULT 1,
    epoch      bigint      NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_epoch_singleton CHECK (id = 1)
);
INSERT INTO oa_iam.perm_epoch(id, epoch) VALUES (1, 0);

-- 用户级版本：授权只影响某个人时，只 bump 他自己，避免全员快照失效
CREATE TABLE oa_iam.perm_user_version (
    user_id    varchar(64) PRIMARY KEY,
    version    bigint      NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL DEFAULT now()
);

-- ═══════════════════════════════════════════════════════════════════
-- 权限点目录：与 Phase 1 已有的 @RequiresPerm 一一对应。
-- 少一条，对应接口就永远判不过 —— 所以这份目录必须跟着 Controller 一起改。
-- ═══════════════════════════════════════════════════════════════════
INSERT INTO oa_iam.permission (code, name, type, module, builtin, require_elevation) VALUES
  ('oa:menu:org',            '组织人事',       'MENU',   'org',   true,  false),
  ('oa:menu:iam',            '权限中心',       'MENU',   'iam',   true,  false),
  ('oa:org:view',            '查看组织',       'API',    'org',   true,  false),
  ('oa:org:create',          '新建组织',       'API',    'org',   true,  false),
  ('oa:org:update',          '修改组织',       'API',    'org',   true,  false),
  ('oa:org:move',            '移动组织',       'API',    'org',   true,  false),
  ('oa:org:dissolve',        '撤销组织',       'API',    'org',   true,  true),
  ('oa:org:admin',           '组织管理员',     'API',    'org',   true,  false),
  ('oa:employee:view',       '查看员工',       'API',    'org',   true,  false),
  ('oa:employee:create',     '新建员工',       'API',    'org',   true,  false),
  ('oa:employee:update',     '修改员工',       'API',    'org',   true,  false),
  ('oa:employee:transfer',   '调岗/兼岗',      'API',    'org',   true,  false),
  ('oa:employee:leave',      '办理离职',       'API',    'org',   true,  false),
  ('oa:employee:export',     '导出员工名册',   'API',    'org',   true,  true),
  ('oa:iam:view',            '查看授权',       'API',    'iam',   true,  false),
  ('oa:iam:grant',           '授予角色',       'API',    'iam',   true,  false),
  ('oa:iam:revoke',          '撤销授权',       'API',    'iam',   true,  false),
  ('oa:iam:admin',           '权限管理员',     'API',    'iam',   true,  false),
  ('oa:iam:delegate',        '设置委托代理',   'API',    'iam',   true,  false),
  ('oa:field:mobile',        '查看手机号明文', 'FIELD',  'org',   true,  false),
  ('oa:field:idcard',        '查看身份证明文', 'FIELD',  'org',   true,  false);

-- 内置角色。default_scope 是"这个角色天然该看多宽"，授权时可再收窄。
INSERT INTO oa_iam.role (code, name, type, default_scope, builtin, remark) VALUES
  ('SUPER_ADMIN',  '超级管理员', 'SYSTEM',   'ALL',         true, '全部权限，全部数据'),
  ('HR_ADMIN',     '人事管理员', 'SYSTEM',   'ALL',         true, '组织人事全权，全公司数据'),
  ('DEPT_MANAGER', '部门负责人', 'BUSINESS', 'ORG_AND_SUB', true, '本部门及所有子部门'),
  ('EMPLOYEE',     '普通员工',   'BUSINESS', 'SELF',        true, '只看自己');

-- 角色继承：SUPER_ADMIN ⊃ HR_ADMIN ⊃ DEPT_MANAGER ⊃ EMPLOYEE
-- 闭包表，自反行 distance=0 必须有，否则角色查不到自己的权限。
INSERT INTO oa_iam.role_inherit (ancestor_role_id, descendant_role_id, distance)
SELECT r.id, r.id, 0 FROM oa_iam.role r;
INSERT INTO oa_iam.role_inherit (ancestor_role_id, descendant_role_id, distance)
SELECT a.id, d.id, x.dist FROM (VALUES
    ('SUPER_ADMIN','HR_ADMIN',1),     ('SUPER_ADMIN','DEPT_MANAGER',2), ('SUPER_ADMIN','EMPLOYEE',3),
    ('HR_ADMIN','DEPT_MANAGER',1),    ('HR_ADMIN','EMPLOYEE',2),
    ('DEPT_MANAGER','EMPLOYEE',1)
) AS x(anc, desc_, dist)
JOIN oa_iam.role a ON a.code = x.anc
JOIN oa_iam.role d ON d.code = x.desc_;

-- 角色 → 权限点
INSERT INTO oa_iam.role_permission (role_id, permission_id)
SELECT r.id, p.id FROM oa_iam.role r, oa_iam.permission p
 WHERE r.code = 'EMPLOYEE'
   AND p.code IN ('oa:menu:org','oa:org:view','oa:employee:view');

INSERT INTO oa_iam.role_permission (role_id, permission_id)
SELECT r.id, p.id FROM oa_iam.role r, oa_iam.permission p
 WHERE r.code = 'DEPT_MANAGER'
   AND p.code IN ('oa:employee:transfer','oa:field:mobile');

INSERT INTO oa_iam.role_permission (role_id, permission_id)
SELECT r.id, p.id FROM oa_iam.role r, oa_iam.permission p
 WHERE r.code = 'HR_ADMIN'
   AND p.code IN ('oa:org:create','oa:org:update','oa:org:move','oa:org:dissolve','oa:org:admin',
                  'oa:employee:create','oa:employee:update','oa:employee:leave','oa:employee:export',
                  'oa:field:idcard');

INSERT INTO oa_iam.role_permission (role_id, permission_id)
SELECT r.id, p.id FROM oa_iam.role r, oa_iam.permission p
 WHERE r.code = 'SUPER_ADMIN'
   AND p.code IN ('oa:menu:iam','oa:iam:view','oa:iam:grant','oa:iam:revoke','oa:iam:admin','oa:iam:delegate');
