-- S07：身份同步任务。连接器仅 INTERNAL / MOCK；无真实 LDAP。任务在请求内跑完，不引入独立调度进程。

CREATE TABLE oa_iam.identity_sync_job (
    id              bigserial    PRIMARY KEY,
    tenant_id       bigint       NOT NULL DEFAULT 1,
    command_id      varchar(64)  NOT NULL,
    connector       varchar(16)  NOT NULL,
    mode            varchar(16)  NOT NULL,
    status          varchar(16)  NOT NULL,
    created_count   int          NOT NULL DEFAULT 0,
    updated_count   int          NOT NULL DEFAULT 0,
    skipped_count   int          NOT NULL DEFAULT 0,
    error_message   text,
    started_at      timestamptz,
    finished_at     timestamptz,
    created_by      varchar(64)  NOT NULL,
    created_at      timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT ck_sync_connector CHECK (connector IN ('INTERNAL', 'MOCK')),
    CONSTRAINT ck_sync_mode CHECK (mode IN ('FULL', 'INCREMENTAL')),
    CONSTRAINT ck_sync_status CHECK (status IN ('RUNNING', 'SUCCEEDED', 'FAILED'))
);

COMMENT ON TABLE  oa_iam.identity_sync_job IS '身份同步任务。幂等键 command_id；重复同步靠 identity 唯一键去重';
COMMENT ON COLUMN oa_iam.identity_sync_job.connector IS 'INTERNAL=员工投影；MOCK=内置演示目录';
COMMENT ON COLUMN oa_iam.identity_sync_job.mode IS 'FULL 刷新已有+补齐缺失；INCREMENTAL 只补齐缺失';
COMMENT ON COLUMN oa_iam.identity_sync_job.command_id IS '客户端幂等键；同键返回原任务不重跑';

CREATE UNIQUE INDEX uk_sync_job_command
    ON oa_iam.identity_sync_job (tenant_id, command_id);
CREATE INDEX ix_sync_job_created
    ON oa_iam.identity_sync_job (tenant_id, id DESC);
