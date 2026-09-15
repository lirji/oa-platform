-- 授权决策审计。Check API 写入；@RequiresPerm 热路径不写（D-GOV-008）。

CREATE TABLE oa_iam.authz_decision_log (
    id              bigserial    PRIMARY KEY,
    tenant_id       bigint       NOT NULL DEFAULT 1,
    command_id      varchar(64),
    trace_id        varchar(64),
    caller_user_id  varchar(64)  NOT NULL,
    principal_id    uuid         NOT NULL,
    identity_type   varchar(32)  NOT NULL,
    resource_type   varchar(32)  NOT NULL,
    resource_id     varchar(256) NOT NULL,
    action          varchar(32)  NOT NULL,
    decision        varchar(8)   NOT NULL,
    policy_id       varchar(128) NOT NULL,
    reason          varchar(512) NOT NULL,
    evaluated_at    timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT ck_authz_decision CHECK (decision IN ('ALLOW','DENY'))
);

COMMENT ON TABLE  oa_iam.authz_decision_log IS '授权决策审计。Check API 每请一行';
COMMENT ON COLUMN oa_iam.authz_decision_log.id IS '自增主键，兼游标';
COMMENT ON COLUMN oa_iam.authz_decision_log.tenant_id IS '租户';
COMMENT ON COLUMN oa_iam.authz_decision_log.command_id IS '调用方可选幂等/关联键';
COMMENT ON COLUMN oa_iam.authz_decision_log.trace_id IS '请求跟踪号';
COMMENT ON COLUMN oa_iam.authz_decision_log.caller_user_id IS '发起 Check 的登录主体 Casdoor sub';
COMMENT ON COLUMN oa_iam.authz_decision_log.principal_id IS '被判定的身份';
COMMENT ON COLUMN oa_iam.authz_decision_log.identity_type IS '被判定身份类型';
COMMENT ON COLUMN oa_iam.authz_decision_log.resource_type IS '资源类型，如 API/TOOL';
COMMENT ON COLUMN oa_iam.authz_decision_log.resource_id IS '资源标识，API 时为权限点 code';
COMMENT ON COLUMN oa_iam.authz_decision_log.action IS '动作';
COMMENT ON COLUMN oa_iam.authz_decision_log.decision IS 'ALLOW 或 DENY';
COMMENT ON COLUMN oa_iam.authz_decision_log.policy_id IS '合成策略标识';
COMMENT ON COLUMN oa_iam.authz_decision_log.reason IS '人话原因';
COMMENT ON COLUMN oa_iam.authz_decision_log.evaluated_at IS '判定时间';

CREATE INDEX ix_authz_decision_principal ON oa_iam.authz_decision_log (tenant_id, principal_id, id);
CREATE INDEX ix_authz_decision_time ON oa_iam.authz_decision_log (tenant_id, evaluated_at);

INSERT INTO oa_iam.permission (code, name, type, module, builtin, require_elevation)
VALUES ('oa:iam:check', '调用授权判定', 'API', 'iam', true, false)
ON CONFLICT (code) DO NOTHING;

INSERT INTO oa_iam.role_permission (role_id, permission_id)
SELECT r.id, p.id FROM oa_iam.role r, oa_iam.permission p
 WHERE r.code IN ('SUPER_ADMIN', 'EMPLOYEE')
   AND p.code = 'oa:iam:check'
ON CONFLICT DO NOTHING;
