-- S09：风险规则目录与发现。规则内置，不提供 DSL 编辑器。扫描在请求内跑完，不引入 oa-job。

CREATE TABLE oa_iam.risk_rule (
    code        varchar(32)  PRIMARY KEY,
    name        varchar(128) NOT NULL,
    severity    varchar(16)  NOT NULL,
    enabled     boolean      NOT NULL DEFAULT true,
    description text         NOT NULL,
    CONSTRAINT ck_risk_rule_severity CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL'))
);

COMMENT ON TABLE  oa_iam.risk_rule IS '内置风险规则目录。本期无规则编辑器';
COMMENT ON COLUMN oa_iam.risk_rule.code IS 'STALE_UNUSED / LEAVER_RESIDUAL / AGENT_ORPHAN';
COMMENT ON COLUMN oa_iam.risk_rule.severity IS '发现默认严重级别';

INSERT INTO oa_iam.risk_rule (code, name, severity, description) VALUES
    ('STALE_UNUSED', '长期未用', 'MEDIUM', '非人身份超过宽限期仍无 Check ALLOW，热路径 RequiresPerm 不写决策审计故不扫 USER'),
    ('LEAVER_RESIDUAL', '离职残留授权', 'HIGH', 'USER 已停用/过期但仍有有效 grant 或权限委托'),
    ('AGENT_ORPHAN', 'Agent 无主', 'HIGH', 'AGENT 没有属主，或属主已停用');

CREATE TABLE oa_iam.risk_finding (
    id              bigserial    PRIMARY KEY,
    tenant_id       bigint       NOT NULL DEFAULT 1,
    rule_code       varchar(32)  NOT NULL REFERENCES oa_iam.risk_rule(code),
    severity        varchar(16)  NOT NULL,
    identity_id     uuid         NOT NULL,
    identity_type   varchar(32)  NOT NULL,
    display_name    varchar(256) NOT NULL,
    external_key    varchar(128) NOT NULL,
    summary         varchar(512) NOT NULL,
    status          varchar(16)  NOT NULL,
    detected_at     timestamptz  NOT NULL DEFAULT now(),
    updated_at      timestamptz  NOT NULL DEFAULT now(),
    updated_by      varchar(64),
    note            text,
    CONSTRAINT ck_risk_finding_status CHECK (status IN ('OPEN', 'ACKNOWLEDGED', 'RESOLVED', 'IGNORED')),
    CONSTRAINT ck_risk_finding_severity CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL'))
);

COMMENT ON TABLE  oa_iam.risk_finding IS '风险发现。同一规则+身份在 OPEN/ACKNOWLEDGED/IGNORED 上至多一条';
COMMENT ON COLUMN oa_iam.risk_finding.status IS 'OPEN→ACKNOWLEDGED|RESOLVED|IGNORED';
COMMENT ON COLUMN oa_iam.risk_finding.identity_id IS '被指出的身份';

CREATE UNIQUE INDEX uk_risk_finding_active
    ON oa_iam.risk_finding (tenant_id, rule_code, identity_id)
    WHERE status IN ('OPEN', 'ACKNOWLEDGED', 'IGNORED');
CREATE INDEX ix_risk_finding_list
    ON oa_iam.risk_finding (tenant_id, status, id);
