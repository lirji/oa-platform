-- S04：权限申请。本地四眼（对齐 elevation_request），不引入第二套 BPM / 远程流程。

CREATE TABLE oa_iam.access_request (
    id                      bigserial    PRIMARY KEY,
    tenant_id               bigint       NOT NULL DEFAULT 1,
    command_id              varchar(64),
    requester_user_id       varchar(64)  NOT NULL,
    requester_identity_id   uuid,
    request_type            varchar(16)  NOT NULL,
    role_id                 bigint       REFERENCES oa_iam.role(id),
    perm_codes              jsonb        NOT NULL DEFAULT '[]'::jsonb,
    valid_to                timestamptz,
    reason                  text         NOT NULL,
    status                  varchar(16)  NOT NULL DEFAULT 'PENDING',
    requested_at            timestamptz  NOT NULL DEFAULT now(),
    decided_by              varchar(64),
    decided_at              timestamptz,
    decision_reason         text,
    grant_id                bigint       REFERENCES oa_iam.grant_record(id),
    approval_instance_id    varchar(64),
    CONSTRAINT ck_access_request_type CHECK (request_type IN ('ROLE','TEMPORARY','DELEGATION','HIGH_RISK')),
    CONSTRAINT ck_access_request_status CHECK (status IN ('PENDING','APPROVED','REJECTED','CANCELLED'))
);

COMMENT ON TABLE  oa_iam.access_request IS '权限申请。批准后写 grant_record.source=APPROVAL';
COMMENT ON COLUMN oa_iam.access_request.command_id IS '调用方幂等键';
COMMENT ON COLUMN oa_iam.access_request.requester_user_id IS '申请人 Casdoor sub';
COMMENT ON COLUMN oa_iam.access_request.requester_identity_id IS '申请人身份投影';
COMMENT ON COLUMN oa_iam.access_request.request_type IS 'ROLE 常授 / TEMPORARY 定时 / DELEGATION 委托申请 / HIGH_RISK';
COMMENT ON COLUMN oa_iam.access_request.role_id IS '申请的角色';
COMMENT ON COLUMN oa_iam.access_request.perm_codes IS '可选权限点列表，本期 ROLE 路径不消费';
COMMENT ON COLUMN oa_iam.access_request.valid_to IS 'TEMPORARY 必填截止时间';
COMMENT ON COLUMN oa_iam.access_request.reason IS '申请事由，进审计';
COMMENT ON COLUMN oa_iam.access_request.status IS '四眼状态';
COMMENT ON COLUMN oa_iam.access_request.grant_id IS '批准后生成的授权';
COMMENT ON COLUMN oa_iam.access_request.approval_instance_id IS 'ACCESS:{id}，写入 grant_record';

CREATE UNIQUE INDEX uk_access_request_command
    ON oa_iam.access_request (tenant_id, command_id)
    WHERE command_id IS NOT NULL;
CREATE UNIQUE INDEX uk_access_request_pending_role
    ON oa_iam.access_request (tenant_id, requester_user_id, request_type, role_id)
    WHERE status = 'PENDING' AND role_id IS NOT NULL;
CREATE INDEX ix_access_request_requester
    ON oa_iam.access_request (tenant_id, requester_user_id, id);
CREATE INDEX ix_access_request_status
    ON oa_iam.access_request (tenant_id, status, id);

INSERT INTO oa_iam.permission (code, name, type, module, builtin, require_elevation)
VALUES ('oa:iam:request', '提交权限申请', 'API', 'iam', true, false)
ON CONFLICT (code) DO NOTHING;

INSERT INTO oa_iam.role_permission (role_id, permission_id)
SELECT r.id, p.id FROM oa_iam.role r, oa_iam.permission p
 WHERE r.code IN ('SUPER_ADMIN', 'EMPLOYEE')
   AND p.code = 'oa:iam:request'
ON CONFLICT DO NOTHING;
