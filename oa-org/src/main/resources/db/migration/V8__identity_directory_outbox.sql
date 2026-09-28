CREATE TABLE oa_org.identity_directory_source (
    tenant_id bigint PRIMARY KEY CHECK (tenant_id > 0),
    source varchar(100) NOT NULL,
    environment varchar(40) NOT NULL,
    last_sequence bigint NOT NULL DEFAULT 0 CHECK (last_sequence >= 0),
    acked_sequence bigint NOT NULL DEFAULT 0 CHECK (acked_sequence >= 0),
    acked_fingerprint char(64),
    initialized boolean NOT NULL DEFAULT false,
    registered_by varchar(160) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (acked_sequence <= last_sequence),
    CHECK ((acked_sequence = 0 AND acked_fingerprint IS NULL) OR (acked_sequence > 0 AND acked_fingerprint IS NOT NULL AND acked_fingerprint ~ '^[a-f0-9]{64}$'))
);
COMMENT ON TABLE oa_org.identity_directory_source IS '已受控登记的企业目录来源与确认水位';
COMMENT ON COLUMN oa_org.identity_directory_source.tenant_id IS 'OA 企业固定范围';
COMMENT ON COLUMN oa_org.identity_directory_source.source IS '来源系统代码';
COMMENT ON COLUMN oa_org.identity_directory_source.environment IS '来源环境代码';
COMMENT ON COLUMN oa_org.identity_directory_source.last_sequence IS '已提交分区最大连续序号，非普通 sequence';
COMMENT ON COLUMN oa_org.identity_directory_source.acked_sequence IS 'auth 已确认连续提交水位';
COMMENT ON COLUMN oa_org.identity_directory_source.acked_fingerprint IS '最后确认事件摘要，初始为空';
COMMENT ON COLUMN oa_org.identity_directory_source.initialized IS '初始化封存批次已在源端提交';
COMMENT ON COLUMN oa_org.identity_directory_source.registered_by IS '受控登记操作者';
COMMENT ON COLUMN oa_org.identity_directory_source.created_at IS '来源登记时间';

CREATE TABLE oa_org.identity_directory_version (
    tenant_id bigint NOT NULL REFERENCES oa_org.identity_directory_source(tenant_id),
    aggregate_type varchar(24) NOT NULL CHECK (aggregate_type IN ('EMPLOYEE','ORG')),
    aggregate_id bigint NOT NULL CHECK (aggregate_id > 0),
    version bigint NOT NULL CHECK (version >= 1),
    PRIMARY KEY (tenant_id, aggregate_type, aggregate_id)
);
COMMENT ON TABLE oa_org.identity_directory_version IS '目录聚合专用版本，不依赖员工行乐观锁是否递增';
COMMENT ON COLUMN oa_org.identity_directory_version.tenant_id IS '已登记企业';
COMMENT ON COLUMN oa_org.identity_directory_version.aggregate_type IS '源目录聚合类型';
COMMENT ON COLUMN oa_org.identity_directory_version.aggregate_id IS '员工或组织主键';
COMMENT ON COLUMN oa_org.identity_directory_version.version IS '目录单调版本';

CREATE TABLE oa_org.identity_directory_outbox (
    tenant_id bigint NOT NULL REFERENCES oa_org.identity_directory_source(tenant_id),
    partition_sequence bigint NOT NULL CHECK (partition_sequence >= 1),
    event_id varchar(36) NOT NULL,
    aggregate_type varchar(24) NOT NULL CHECK (aggregate_type IN ('EMPLOYEE','ORG','SNAPSHOT_BEGIN','SNAPSHOT_END')),
    aggregate_id varchar(160) NOT NULL,
    aggregate_version bigint NOT NULL CHECK (aggregate_version >= 1),
    snapshot_id varchar(36),
    fingerprint char(64) NOT NULL CHECK (fingerprint ~ '^[a-f0-9]{64}$'),
    event_json jsonb NOT NULL CHECK (jsonb_typeof(event_json) = 'object' AND octet_length(event_json::text) <= 65536),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id, partition_sequence),
    UNIQUE (tenant_id, event_id),
    UNIQUE (tenant_id, aggregate_type, aggregate_id, aggregate_version)
);
COMMENT ON TABLE oa_org.identity_directory_outbox IS '与 OA 组织业务同事务生成的不可变目录事件';
COMMENT ON COLUMN oa_org.identity_directory_outbox.tenant_id IS '固定来源企业';
COMMENT ON COLUMN oa_org.identity_directory_outbox.partition_sequence IS '事务门闩内串行分配的连续序号';
COMMENT ON COLUMN oa_org.identity_directory_outbox.event_id IS '稳定事件 UUID，重试不重新生成';
COMMENT ON COLUMN oa_org.identity_directory_outbox.aggregate_type IS '事件聚合或控制类型';
COMMENT ON COLUMN oa_org.identity_directory_outbox.aggregate_id IS '源主键或快照 UUID';
COMMENT ON COLUMN oa_org.identity_directory_outbox.aggregate_version IS '同聚合单调版本';
COMMENT ON COLUMN oa_org.identity_directory_outbox.snapshot_id IS '封存快照批次，普通增量为空';
COMMENT ON COLUMN oa_org.identity_directory_outbox.fingerprint IS '完整事件摘要，确认时必须匹配';
COMMENT ON COLUMN oa_org.identity_directory_outbox.event_json IS '协议最小事实，不包含邮箱手机号等非必要字段';
COMMENT ON COLUMN oa_org.identity_directory_outbox.created_at IS '与业务原子提交的事件生成时间';

