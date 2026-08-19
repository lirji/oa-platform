-- ═══════════════════════════════════════════════════════════════════
-- 修一个"看起来建了唯一约束、实际上不去重"的真 bug。
--
-- V40 里写的是：
--   CREATE UNIQUE INDEX uk_notification_dedup
--     ON oa_notify.notification(tenant_id, user_id, dedup_key, created_at)
--
-- created_at 是被迫加进去的 —— PostgreSQL 要求【分区表上的唯一索引必须包含分区键】。
-- 但这一加，唯一性就变成了"同一时刻的同一条才算重复"，而重复投递必然发生在不同时刻。
-- 于是这个索引对幂等毫无作用：Kafka 重放一次，用户就多收到一条一模一样的消息。
-- 冒烟里同 dedupKey 连发两次、两次都 inserted=1，才把它暴露出来。
--
-- 结论：分区表上做不了"跨时间的唯一"。幂等键必须放在一张【不分区】的小表上。
-- 这张表天然可裁剪（按 created_at 删旧行），代价只是一次额外的插入。
-- ═══════════════════════════════════════════════════════════════════

DROP INDEX IF EXISTS oa_notify.uk_notification_dedup;

CREATE TABLE IF NOT EXISTS oa_notify.notification_dedup (
    tenant_id  bigint       NOT NULL DEFAULT 1,
    user_id    varchar(64)  NOT NULL,
    dedup_key  varchar(128) NOT NULL,
    created_at timestamptz  NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, user_id, dedup_key)
);
-- 保留窗口靠这个索引裁剪：DELETE ... WHERE created_at < now() - interval '30 days'
CREATE INDEX IF NOT EXISTS ix_notification_dedup_created ON oa_notify.notification_dedup(created_at);
