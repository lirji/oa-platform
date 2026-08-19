-- ═══════════════════════════════════════════════════════════════════
-- Phase 4 审批域（ADR-0010）
-- 版本号区间：V20-V29 归 oa-flow
-- ═══════════════════════════════════════════════════════════════════

-- ───────────────────────────────── 表单模板（JSON Schema，版本化）
-- 单据引用 (template_id, version)，模板改版【不影响】历史单据 ——
-- 否则两年前那张请假单会用今天的表单去渲染，字段对不上。
CREATE TABLE oa_flow.form_template (
    id           bigserial   PRIMARY KEY,
    tenant_id    bigint      NOT NULL DEFAULT 1,
    code         varchar(64) NOT NULL,
    name         varchar(128) NOT NULL,
    version       int        NOT NULL DEFAULT 1,
    schema_json  jsonb       NOT NULL,
    status       varchar(16) NOT NULL DEFAULT 'DRAFT',   -- DRAFT|PUBLISHED|ARCHIVED
    published_at timestamptz,
    created_by   varchar(64),
    created_at   timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_tpl_status CHECK (status IN ('DRAFT','PUBLISHED','ARCHIVED'))
);
CREATE UNIQUE INDEX uk_tpl_code_ver ON oa_flow.form_template(tenant_id, code, version);
CREATE INDEX ix_tpl_published ON oa_flow.form_template(tenant_id, code) WHERE status = 'PUBLISHED';

-- ───────────────────────────────── 审批实例
CREATE TABLE oa_flow.approval_instance (
    id                     bigserial   PRIMARY KEY,
    tenant_id              bigint      NOT NULL DEFAULT 1,
    biz_type               varchar(32) NOT NULL,          -- LEAVE|OVERTIME|EXPENSE|...
    business_key           varchar(64) NOT NULL,          -- 业务单据 id
    process_definition_key varchar(64) NOT NULL,
    process_instance_id    varchar(64),                   -- 中台回填
    form_template_id       bigint      REFERENCES oa_flow.form_template(id),
    form_version           int,
    form_data              jsonb,
    applicant_user_id      varchar(64) NOT NULL,
    applicant_employee_id  bigint,
    applicant_name         varchar(64),
    -- ★ 发生时快照（ADR-0007）：组织调整【不回刷】历史单据。
    -- 既是性能优化（数据权限免 join），也是正确的业务语义——这张单属于当时那个部门。
    org_id                 bigint      NOT NULL,
    org_path               varchar(512) NOT NULL,
    approver_chain         jsonb,                         -- OA 侧算好的审批人链（ADR-0010）
    status                 varchar(16) NOT NULL DEFAULT 'SUBMITTED',
    outcome                varchar(16),                   -- APPROVED|REJECTED|CANCELLED
    submitted_at           timestamptz NOT NULL DEFAULT now(),
    finished_at            timestamptz,
    version                int         NOT NULL DEFAULT 0,
    CONSTRAINT ck_ai_status CHECK (status IN ('SUBMITTED','RUNNING','FINISHED','CANCELLED','FAILED'))
);
CREATE UNIQUE INDEX uk_ai_business ON oa_flow.approval_instance(tenant_id, biz_type, business_key);
CREATE INDEX ix_ai_applicant ON oa_flow.approval_instance(applicant_user_id);
CREATE INDEX ix_ai_pid       ON oa_flow.approval_instance(process_instance_id);
-- 数据权限前缀匹配（同 org_unit，必须 text_pattern_ops）
CREATE INDEX ix_ai_org_path  ON oa_flow.approval_instance(org_path text_pattern_ops);

CREATE TABLE oa_flow.approval_node_log (
    id            bigserial   PRIMARY KEY,
    instance_id   bigint      NOT NULL REFERENCES oa_flow.approval_instance(id),
    task_id       varchar(64),
    node_key      varchar(64),
    node_name     varchar(128),
    actor_user_id varchar(64),
    -- 委托代理办理时留痕：实际操作人是 actor，但是【以 on_behalf_of 的名义】办的
    on_behalf_of  varchar(64),
    action        varchar(16),                            -- APPROVE|REJECT|TRANSFER|CC
    comment       text,
    acted_at      timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_anl_instance ON oa_flow.approval_node_log(instance_id, acted_at);

-- ───────────────────────────────── 待办读模型（CQRS 宽表）
-- 工作台首屏要一条 SQL 出结果，不能现场 join 五张表 —— 万人级下那是首屏杀手。
CREATE TABLE oa_flow.todo_item (
    id                  bigserial   PRIMARY KEY,
    tenant_id           bigint      NOT NULL DEFAULT 1,
    task_id             varchar(64) NOT NULL,
    process_instance_id varchar(64),
    instance_id         bigint,
    biz_type            varchar(32),
    title               varchar(256),
    summary             varchar(512),
    applicant_user_id   varchar(64),
    applicant_name      varchar(64),
    assignee_user_id    varchar(64),
    candidate_group     varchar(64),
    state               varchar(16) NOT NULL DEFAULT 'PENDING',  -- PENDING|DONE|CANCELLED
    org_id              bigint,
    org_path            varchar(512),
    created_at          timestamptz NOT NULL DEFAULT now(),
    due_at              timestamptz,
    finished_at         timestamptz
);
CREATE UNIQUE INDEX uk_todo_task ON oa_flow.todo_item(tenant_id, task_id);
CREATE INDEX ix_todo_assignee ON oa_flow.todo_item(assignee_user_id, state, created_at DESC);
CREATE INDEX ix_todo_org_path ON oa_flow.todo_item(org_path text_pattern_ops);

-- ───────────────────────────────── 事务发件箱 / 收件箱
-- 发件箱：业务写入与消息投递在【同一个事务】里，杜绝"单据落了消息没发"。
CREATE TABLE oa_flow.oa_outbox (
    id              bigserial   PRIMARY KEY,
    topic           varchar(128) NOT NULL,
    msg_key         varchar(128),
    payload         text        NOT NULL,
    status          varchar(16) NOT NULL DEFAULT 'PENDING',  -- PENDING|SENT|FAILED
    attempts        int         NOT NULL DEFAULT 0,
    next_attempt_at timestamptz NOT NULL DEFAULT now(),
    last_error      text,
    created_at      timestamptz NOT NULL DEFAULT now(),
    sent_at         timestamptz
);
CREATE INDEX ix_outbox_pending ON oa_flow.oa_outbox(next_attempt_at)
    WHERE status = 'PENDING';

-- 收件箱：消费侧按 eventId 幂等去重（中台契约明确要求先做 inbox 去重）
CREATE TABLE oa_flow.oa_inbox (
    event_id    varchar(64) PRIMARY KEY,
    event_type  varchar(64),
    consumed_at timestamptz NOT NULL DEFAULT now()
);

-- ───────────────────────────────── 假期额度（请假单闭环需要）
CREATE TABLE oa_flow.leave_type (
    id           bigserial   PRIMARY KEY,
    code         varchar(32) NOT NULL UNIQUE,
    name         varchar(64) NOT NULL,
    paid         boolean     NOT NULL DEFAULT true,
    need_balance boolean     NOT NULL DEFAULT true,       -- 事假不扣额度
    max_days     numeric(6,1),
    status       varchar(16) NOT NULL DEFAULT 'ACTIVE'
);

CREATE TABLE oa_flow.leave_balance (
    id            bigserial   PRIMARY KEY,
    tenant_id     bigint      NOT NULL DEFAULT 1,
    user_id       varchar(64) NOT NULL,
    leave_type_id bigint      NOT NULL REFERENCES oa_flow.leave_type(id),
    period        varchar(8)  NOT NULL,                   -- '2026'
    total_days    numeric(6,1) NOT NULL DEFAULT 0,
    used_days     numeric(6,1) NOT NULL DEFAULT 0,
    frozen_days   numeric(6,1) NOT NULL DEFAULT 0,        -- 审批中冻结
    version       int         NOT NULL DEFAULT 0,         -- 乐观锁：同一人低并发，不必上分布式锁
    updated_at    timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uk_balance ON oa_flow.leave_balance(tenant_id, user_id, leave_type_id, period);

CREATE TABLE oa_flow.leave_balance_txn (
    id          bigserial    PRIMARY KEY,
    balance_id  bigint       NOT NULL REFERENCES oa_flow.leave_balance(id),
    request_id  varchar(64)  NOT NULL,                    -- ★ 幂等键：同一单据重复扣减无效
    action      varchar(16)  NOT NULL,                    -- FREEZE|CONSUME|RELEASE|GRANT
    days        numeric(6,1) NOT NULL,
    remark      text,
    created_at  timestamptz  NOT NULL DEFAULT now()
);
-- 同一单据 + 同一动作只能记一次 —— 幂等由数据库唯一约束保证，不靠应用层记得判重
CREATE UNIQUE INDEX uk_balance_txn ON oa_flow.leave_balance_txn(request_id, action);

-- ───────────────────────────────── 请假单
CREATE TABLE oa_flow.leave_request (
    id            bigserial    PRIMARY KEY,
    tenant_id     bigint       NOT NULL DEFAULT 1,
    request_no    varchar(32)  NOT NULL UNIQUE,
    user_id       varchar(64)  NOT NULL,
    employee_id   bigint,
    leave_type_id bigint       NOT NULL REFERENCES oa_flow.leave_type(id),
    start_date    date         NOT NULL,
    end_date      date         NOT NULL,
    days          numeric(6,1) NOT NULL,
    reason        text,
    status        varchar(16)  NOT NULL DEFAULT 'DRAFT',  -- DRAFT|PENDING|APPROVED|REJECTED|CANCELLED
    -- 发生时快照，同 approval_instance
    org_id        bigint       NOT NULL,
    org_path      varchar(512) NOT NULL,
    created_at    timestamptz  NOT NULL DEFAULT now(),
    decided_at    timestamptz,
    version       int          NOT NULL DEFAULT 0,
    CONSTRAINT ck_lr_period CHECK (end_date >= start_date),
    CONSTRAINT ck_lr_days   CHECK (days > 0)
);
CREATE INDEX ix_lr_user     ON oa_flow.leave_request(user_id, created_at DESC);
CREATE INDEX ix_lr_org_path ON oa_flow.leave_request(org_path text_pattern_ops);

-- ───────────────────────────────── 号段：单据编号不用 SELECT FOR UPDATE
CREATE TABLE oa_flow.id_segment (
    biz_tag     varchar(32) PRIMARY KEY,
    max_id      bigint      NOT NULL DEFAULT 0,
    step        int         NOT NULL DEFAULT 1000,
    updated_at  timestamptz NOT NULL DEFAULT now()
);
INSERT INTO oa_flow.id_segment(biz_tag, max_id, step) VALUES ('LEAVE', 0, 1000);

-- ───────────────────────────────── 种子数据
INSERT INTO oa_flow.leave_type(code, name, paid, need_balance, max_days) VALUES
  ('ANNUAL',   '年假',   true,  true,  15),
  ('SICK',     '病假',   true,  true,  30),
  ('PERSONAL', '事假',   false, false, NULL),
  ('MARRIAGE', '婚假',   true,  true,  10);

INSERT INTO oa_flow.form_template(code, name, version, schema_json, status, published_at, created_by)
VALUES ('LEAVE', '请假申请单', 1, '{
  "type": "object",
  "title": "请假申请",
  "required": ["leaveTypeCode", "startDate", "endDate", "days", "reason"],
  "properties": {
    "leaveTypeCode": {"type": "string", "title": "假期类型", "widget": "select", "optionsFrom": "/api/v1/flow/leave/types"},
    "startDate":     {"type": "string", "format": "date", "title": "开始日期"},
    "endDate":       {"type": "string", "format": "date", "title": "结束日期"},
    "days":          {"type": "number", "title": "天数", "minimum": 0.5},
    "reason":        {"type": "string", "title": "事由", "widget": "textarea", "maxLength": 500}
  }
}'::jsonb, 'PUBLISHED', now(), 'system');
