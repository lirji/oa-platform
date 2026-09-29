CREATE TABLE oa_flow.central_access_request (
    request_id varchar(36) PRIMARY KEY,
    request_version bigint NOT NULL CHECK(request_version=1),
    snapshot_hash varchar(64) NOT NULL,
    payload_hash varchar(64) NOT NULL,
    payload_json text NOT NULL,
    instance_id bigint REFERENCES oa_flow.approval_instance(id),
    created_at timestamptz NOT NULL DEFAULT clock_timestamp()
);
COMMENT ON TABLE oa_flow.central_access_request IS '中央IAM与OA审批实例的唯一幂等映射；不存Grant';
COMMENT ON COLUMN oa_flow.central_access_request.request_id IS 'auth申请标识';
COMMENT ON COLUMN oa_flow.central_access_request.request_version IS '不可变申请修订';
COMMENT ON COLUMN oa_flow.central_access_request.snapshot_hash IS 'auth固定快照摘要';
COMMENT ON COLUMN oa_flow.central_access_request.payload_hash IS '完整启动体摘要';
COMMENT ON COLUMN oa_flow.central_access_request.payload_json IS '固定申请内容与审批依据';
COMMENT ON COLUMN oa_flow.central_access_request.instance_id IS '既有OA真实审批实例';
COMMENT ON COLUMN oa_flow.central_access_request.created_at IS '创建UTC时间';
CREATE TABLE oa_flow.central_approval_nonce (
    nonce varchar(36) PRIMARY KEY,
    received_at timestamptz NOT NULL DEFAULT clock_timestamp()
);
COMMENT ON TABLE oa_flow.central_approval_nonce IS '已验证传输nonce去重证据；未定保留策略前不删除';
COMMENT ON COLUMN oa_flow.central_approval_nonce.nonce IS '签名传输唯一标识';
COMMENT ON COLUMN oa_flow.central_approval_nonce.received_at IS '接收UTC时刻';
