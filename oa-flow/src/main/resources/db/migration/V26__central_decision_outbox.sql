CREATE TABLE oa_flow.central_decision_outbox (
    request_id varchar(36) PRIMARY KEY REFERENCES oa_flow.central_access_request(request_id),
    tenant_id varchar(36) NOT NULL,
    application_id varchar(100) NOT NULL,
    environment varchar(40) NOT NULL,
    event_id varchar(36) NOT NULL UNIQUE,
    payload_json text NOT NULL,
    state varchar(16) NOT NULL DEFAULT 'PENDING' CHECK(state IN ('PENDING','RUNNING','DONE','DEAD')),
    attempts integer NOT NULL DEFAULT 0 CHECK(attempts BETWEEN 0 AND 5),
    lease_token varchar(36),
    lease_until timestamptz,
    next_attempt_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    last_error varchar(80),
    created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    updated_at timestamptz NOT NULL DEFAULT clock_timestamp()
);
COMMENT ON TABLE oa_flow.central_decision_outbox IS '真实审批证据的可靠回调Outbox；不承载Grant';
COMMENT ON COLUMN oa_flow.central_decision_outbox.request_id IS '固定申请来源';
COMMENT ON COLUMN oa_flow.central_decision_outbox.event_id IS '稳定业务事件ID';
COMMENT ON COLUMN oa_flow.central_decision_outbox.payload_json IS '实际审批证据生成的固定事件体';
COMMENT ON COLUMN oa_flow.central_decision_outbox.state IS '投递技术状态';
COMMENT ON COLUMN oa_flow.central_decision_outbox.attempts IS '最大五次尝试';
COMMENT ON COLUMN oa_flow.central_decision_outbox.lease_token IS '本次领取令牌';
COMMENT ON COLUMN oa_flow.central_decision_outbox.lease_until IS '租约UTC截止';
COMMENT ON COLUMN oa_flow.central_decision_outbox.next_attempt_at IS '退避后重试UTC';
COMMENT ON COLUMN oa_flow.central_decision_outbox.last_error IS '脱敏错误代码';
COMMENT ON COLUMN oa_flow.central_decision_outbox.created_at IS '事件冻结UTC';
COMMENT ON COLUMN oa_flow.central_decision_outbox.updated_at IS '投递更新UTC';
CREATE INDEX central_decision_due_idx ON oa_flow.central_decision_outbox(next_attempt_at,request_id) WHERE state IN ('PENDING','RUNNING');
COMMENT ON COLUMN oa_flow.central_decision_outbox.tenant_id IS '中央目标企业';
COMMENT ON COLUMN oa_flow.central_decision_outbox.application_id IS '中央目标应用';
COMMENT ON COLUMN oa_flow.central_decision_outbox.environment IS '中央目标环境';
