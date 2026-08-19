-- ═══════════════════════════════════════════════════════════════════
-- Phase 1 组织域（ADR-0006）
-- 迁移版本分配：V1 oa-app 基线 / V2 oa-org / V10 oa-iam / V20 oa-flow
--              V30 oa-attendance / V40 oa-doc / V50 oa-admin
-- 每个模块拥有自己的迁移文件，Flyway 扫 classpath:db/migration 跨 jar 合并。
-- ═══════════════════════════════════════════════════════════════════

-- ───────────────────────────────── 组织单元
-- 刻意【不】为 团队/部门/组 各建一张表：万人企业层级 6-9 层且会越级挂载，
-- 固定层级表在第一次组织调整时就崩。type 只是展示与规则标签，不是结构。
CREATE TABLE oa_org.org_unit (
    id                    bigserial    PRIMARY KEY,
    tenant_id             bigint       NOT NULL DEFAULT 1,
    parent_id             bigint       REFERENCES oa_org.org_unit(id),
    code                  varchar(64)  NOT NULL,
    name                  varchar(128) NOT NULL,
    short_name            varchar(64),
    type                  varchar(16)  NOT NULL,
    -- 物化路径 '/1/23/456/'，首尾都带斜杠。
    -- 尾斜杠不是装饰：它让 LIKE '/1/23/%' 不会误匹配 /1/234/。
    path                  varchar(512) NOT NULL,
    depth                 smallint     NOT NULL,
    sort_order            int          NOT NULL DEFAULT 0,
    leader_user_id        varchar(64),
    deputy_leader_user_id varchar(64),
    cost_center           varchar(64),
    status                varchar(16)  NOT NULL DEFAULT 'ACTIVE',
    effective_from        date         NOT NULL DEFAULT current_date,
    effective_to          date,
    remark                text,
    version               int          NOT NULL DEFAULT 0,
    created_by            varchar(64),
    created_at            timestamptz  NOT NULL DEFAULT now(),
    updated_by            varchar(64),
    updated_at            timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT ck_org_type   CHECK (type IN ('GROUP','COMPANY','BU','CENTER','DEPT','TEAM','SQUAD','VIRTUAL')),
    CONSTRAINT ck_org_status CHECK (status IN ('ACTIVE','FROZEN','DISSOLVED')),
    CONSTRAINT ck_org_path   CHECK (path LIKE '/%/'),
    CONSTRAINT ck_org_period CHECK (effective_to IS NULL OR effective_to > effective_from)
);

CREATE UNIQUE INDEX uk_org_code   ON oa_org.org_unit(tenant_id, code);
CREATE        INDEX ix_org_parent ON oa_org.org_unit(parent_id);
-- ★★★ 数据权限的命脉（ADR-0007）：PG 非 C locale 下 LIKE 'prefix%' 不走普通 btree 索引，
--     必须 text_pattern_ops。漏掉这一行，前缀匹配退化成全表扫描，且只有压测才会发现。
CREATE        INDEX ix_org_path   ON oa_org.org_unit(path text_pattern_ops);
CREATE        INDEX ix_org_active ON oa_org.org_unit(tenant_id) WHERE status = 'ACTIVE';

COMMENT ON COLUMN oa_org.org_unit.path IS '物化路径 /1/23/456/，数据权限前缀匹配的依据';
COMMENT ON INDEX  oa_org.ix_org_path  IS '必须是 text_pattern_ops，否则 LIKE 前缀不走索引';

-- ───────────────────────────────── 闭包表：祖先/后代查询 O(1)
-- 写放大只发生在组织调整（万人级一天几十次），换来权限继承查询免递归。
CREATE TABLE oa_org.org_closure (
    ancestor_id   bigint   NOT NULL REFERENCES oa_org.org_unit(id) ON DELETE CASCADE,
    descendant_id bigint   NOT NULL REFERENCES oa_org.org_unit(id) ON DELETE CASCADE,
    distance      smallint NOT NULL,          -- 自身 distance = 0
    PRIMARY KEY (ancestor_id, descendant_id)
);
CREATE INDEX ix_closure_desc ON oa_org.org_closure(descendant_id, ancestor_id);

-- ───────────────────────────────── 岗位
-- 表名用 job_position：position 在 SQL 标准里是函数名，某些上下文需要引号。
CREATE TABLE oa_org.job_position (
    id         bigserial   PRIMARY KEY,
    tenant_id  bigint      NOT NULL DEFAULT 1,
    code       varchar(64) NOT NULL,
    name       varchar(128) NOT NULL,
    job_family varchar(32),                   -- 职族：研发/产品/销售/职能
    job_level  varchar(16),                   -- 职级：P5 / M3
    is_manager boolean     NOT NULL DEFAULT false,
    status     varchar(16) NOT NULL DEFAULT 'ACTIVE',
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uk_position_code ON oa_org.job_position(tenant_id, code);

-- ───────────────────────────────── 员工
CREATE TABLE oa_org.employee (
    id              bigserial   PRIMARY KEY,
    tenant_id       bigint      NOT NULL DEFAULT 1,
    -- ★ 主体标识 = Casdoor sub（UUID 字符串）。绝不用自增 Long 当 subject。
    user_id         varchar(64) NOT NULL,
    emp_no          varchar(32) NOT NULL,
    name            varchar(64) NOT NULL,
    en_name         varchar(64),
    avatar          varchar(512),
    -- 敏感字段：密文列 + 确定性 HMAC 哈希列（哈希列供精确查，密文列供展示解密）
    mobile_enc      bytea,
    mobile_hash     varchar(64),
    email           varchar(128),
    id_card_enc     bytea,
    id_card_hash    varchar(64),
    gender          varchar(8),
    birthday        date,
    hire_date       date,
    regular_date    date,
    leave_date      date,
    employment_type varchar(16) NOT NULL DEFAULT 'FULL_TIME',
    status          varchar(16) NOT NULL DEFAULT 'ACTIVE',
    version         int         NOT NULL DEFAULT 0,
    created_by      varchar(64),
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_by      varchar(64),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_emp_status CHECK (status IN ('PROBATION','ACTIVE','LEAVING','LEFT')),
    CONSTRAINT ck_emp_type   CHECK (employment_type IN ('FULL_TIME','INTERN','OUTSOURCE','CONSULTANT'))
);
CREATE UNIQUE INDEX uk_emp_user   ON oa_org.employee(user_id);
CREATE UNIQUE INDEX uk_emp_no     ON oa_org.employee(tenant_id, emp_no);
CREATE        INDEX ix_emp_mobile ON oa_org.employee(mobile_hash) WHERE mobile_hash IS NOT NULL;
CREATE        INDEX ix_emp_name   ON oa_org.employee(tenant_id, name);

-- ───────────────────────────────── 任职关系（拉链表）★ 一人多岗的核心
CREATE TABLE oa_org.employee_org_assignment (
    id              bigserial   PRIMARY KEY,
    tenant_id       bigint      NOT NULL DEFAULT 1,
    employee_id     bigint      NOT NULL REFERENCES oa_org.employee(id),
    org_unit_id     bigint      NOT NULL REFERENCES oa_org.org_unit(id),
    position_id     bigint      REFERENCES oa_org.job_position(id),
    assignment_type varchar(16) NOT NULL,     -- PRIMARY 主岗 / CONCURRENT 兼岗 / DOTTED 虚线
    is_leader       boolean     NOT NULL DEFAULT false,
    valid_from      date        NOT NULL DEFAULT current_date,
    valid_to        date,                      -- null = 至今
    created_by      varchar(64),
    created_at      timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_assign_type   CHECK (assignment_type IN ('PRIMARY','CONCURRENT','DOTTED')),
    CONSTRAINT ck_assign_period CHECK (valid_to IS NULL OR valid_to > valid_from)
);
-- 一人同一时刻只能有一个主岗 —— 用偏唯一索引让数据库来守，而不是靠应用层记得检查
CREATE UNIQUE INDEX uk_primary_assignment ON oa_org.employee_org_assignment(employee_id)
    WHERE assignment_type = 'PRIMARY' AND valid_to IS NULL;
CREATE INDEX ix_assign_emp_active ON oa_org.employee_org_assignment(employee_id) WHERE valid_to IS NULL;
CREATE INDEX ix_assign_org_active ON oa_org.employee_org_assignment(org_unit_id) WHERE valid_to IS NULL;
-- as-of 历史还原走这个
CREATE INDEX ix_assign_emp_period ON oa_org.employee_org_assignment(employee_id, valid_from, valid_to);

-- ───────────────────────────────── 汇报线 ★ 独立于组织树
-- 矩阵式管理下"逐级上报"经常不等于组织树（跨部门项目经理、虚线上级）。
-- 把它硬绑在组织树上，第一个矩阵式组织的请假审批就会走错人。
CREATE TABLE oa_org.reporting_line (
    id                  bigserial   PRIMARY KEY,
    tenant_id           bigint      NOT NULL DEFAULT 1,
    employee_id         bigint      NOT NULL REFERENCES oa_org.employee(id),
    manager_employee_id bigint      NOT NULL REFERENCES oa_org.employee(id),
    type                varchar(8)  NOT NULL,   -- SOLID 实线 / DOTTED 虚线
    valid_from          date        NOT NULL DEFAULT current_date,
    valid_to            date,
    created_at          timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_rl_type   CHECK (type IN ('SOLID','DOTTED')),
    CONSTRAINT ck_rl_self   CHECK (employee_id <> manager_employee_id),
    CONSTRAINT ck_rl_period CHECK (valid_to IS NULL OR valid_to > valid_from)
);
CREATE UNIQUE INDEX uk_solid_manager ON oa_org.reporting_line(employee_id)
    WHERE type = 'SOLID' AND valid_to IS NULL;
CREATE INDEX ix_rl_manager ON oa_org.reporting_line(manager_employee_id) WHERE valid_to IS NULL;

-- ───────────────────────────────── 组织树版本（跨节点缓存收敛用）
-- 单行表。任何组织变更 +1；各节点轮询发现版本变了就重建内存快照。
-- Phase 2 接入 Redis Pub/Sub 后收敛从"秒级上界"降到毫秒，本表保留作兜底。
CREATE TABLE oa_org.org_tree_version (
    id         smallint    PRIMARY KEY DEFAULT 1,
    version    bigint      NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_singleton CHECK (id = 1)
);
INSERT INTO oa_org.org_tree_version(id, version) VALUES (1, 0);
