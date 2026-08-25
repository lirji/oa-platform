CREATE TABLE oa_iam.elevation_request (
    id bigserial PRIMARY KEY, tenant_id bigint NOT NULL DEFAULT 1,
    requester_id varchar(64) NOT NULL, role_id bigint NOT NULL REFERENCES oa_iam.role(id),
    hours int NOT NULL, reason text NOT NULL, status varchar(16) NOT NULL DEFAULT 'PENDING',
    requested_at timestamptz NOT NULL DEFAULT now(), decided_by varchar(64), decided_at timestamptz,
    decision_reason text, grant_id bigint REFERENCES oa_iam.grant_record(id),
    CONSTRAINT ck_elevation_hours CHECK (hours > 0),
    CONSTRAINT ck_elevation_status CHECK (status IN ('PENDING','APPROVED','REJECTED','CANCELLED'))
);
CREATE INDEX ix_elevation_pending ON oa_iam.elevation_request(tenant_id, requested_at) WHERE status='PENDING';
CREATE UNIQUE INDEX uk_elevation_one_pending ON oa_iam.elevation_request(tenant_id, requester_id, role_id) WHERE status='PENDING';

INSERT INTO oa_iam.permission(code, name, type, module, status, builtin, require_elevation, remark)
VALUES ('oa:iam:elevation:approve', '审批临时提权', 'API', 'iam', 'ACTIVE', true, false,
        '四眼审批 JIT 提权申请；审批人不得是申请人') ON CONFLICT (code) DO NOTHING;
INSERT INTO oa_iam.role_permission(role_id, permission_id)
SELECT r.id, p.id FROM oa_iam.role r CROSS JOIN oa_iam.permission p
 WHERE r.code IN ('SUPER_ADMIN','IAM_ADMIN') AND p.code='oa:iam:elevation:approve'
ON CONFLICT DO NOTHING;

COMMENT ON TABLE oa_iam.elevation_request IS 'JIT 临时提权四眼审批请求，批准后才生成 elevation-only 授权';
COMMENT ON COLUMN oa_iam.elevation_request.grant_id IS '批准后生成的 grant_record 主键';
