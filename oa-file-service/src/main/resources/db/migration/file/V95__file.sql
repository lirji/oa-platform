-- ═══════════════════════════════════════════════════════════════════
-- Phase 7/8 文件服务。版本号区间 V95-V99。schema: oa_sys
-- 由 oa-file-service 自己迁移，独立历史表（同 notify / job 的理由）。
-- ═══════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS oa_sys.file_object (
    id           bigserial    PRIMARY KEY,
    tenant_id    bigint       NOT NULL DEFAULT 1,
    -- 对象存储里的 key。★ 与业务 id 分离且不可猜：直接用 "kb/123.pdf" 这种可枚举的 key，
    -- 拿到一个链接就能推出别人的 —— 对象存储的预签名 URL 一旦泄露就绕过了所有应用层判权。
    object_key   varchar(256) NOT NULL,
    bucket       varchar(64)  NOT NULL,
    file_name    varchar(256) NOT NULL,
    content_type varchar(128),
    size_bytes   bigint       NOT NULL,
    -- 内容哈希：秒传与去重的依据，也是"这个文件被改过没有"的证据
    sha256       varchar(64),
    -- 归属业务，判权时用它决定"谁能下载这个文件"
    biz_type     varchar(32),
    biz_id       varchar(64),
    owner_id     varchar(64)  NOT NULL,
    org_id       bigint,
    org_path     varchar(512),
    created_at   timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT uk_file_object_key UNIQUE (bucket, object_key)
);
CREATE INDEX IF NOT EXISTS ix_file_owner ON oa_sys.file_object(owner_id, created_at DESC);
CREATE INDEX IF NOT EXISTS ix_file_biz ON oa_sys.file_object(biz_type, biz_id);
CREATE INDEX IF NOT EXISTS ix_file_sha ON oa_sys.file_object(sha256);
CREATE INDEX IF NOT EXISTS ix_file_org_path ON oa_sys.file_object(org_path text_pattern_ops);
