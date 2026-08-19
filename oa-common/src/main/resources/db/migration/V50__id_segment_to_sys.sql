-- ═══════════════════════════════════════════════════════════════════
-- 号段表归位：oa_flow.id_segment → oa_sys.id_segment。版本号区间 V50-V59（公共基础设施）。
--
-- 原因：号段生成器按 FINAL_PLAN §7.1 属于 oa-common，早先误放在 oa-flow。
-- 公文文号（oa-doc）也要用它，而"禁止跨 schema"意味着它留在 oa_flow 里
-- 就只能让 oa-doc 去依赖 oa-flow —— 让一个业务模块依赖另一个业务模块，
-- 只为了拿一个自增数。
--
-- 迁移把已用到的 max_id 一起搬过去：不搬的话新表从 0 开始，
-- 会重新发出已经用过的单号，撞上 leave_request 的唯一约束。
--
-- ★ V20 一个字都没动：它已经在库里执行过，改它会让 Flyway 校验和对不上、
--   启动直接失败（这正是 Flyway 该有的行为，别去 repair 绕过）。
--   兼容"老库已有表"和"新库刚由 V20 建表"两种情况的逻辑全部收在这里。
-- ═══════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS oa_sys.id_segment (
    biz_tag     varchar(32) PRIMARY KEY,
    max_id      bigint      NOT NULL DEFAULT 0,
    step        int         NOT NULL DEFAULT 1000,
    updated_at  timestamptz NOT NULL DEFAULT now()
);

-- 把旧表的进度搬过来（取两边较大值，重复执行安全）
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables
                WHERE table_schema = 'oa_flow' AND table_name = 'id_segment') THEN
        INSERT INTO oa_sys.id_segment(biz_tag, max_id, step)
        SELECT biz_tag, max_id, step FROM oa_flow.id_segment
        ON CONFLICT (biz_tag) DO UPDATE
            SET max_id = GREATEST(oa_sys.id_segment.max_id, EXCLUDED.max_id);
        DROP TABLE oa_flow.id_segment;
    END IF;
END $$;

-- 公文文号要求年内连续，step=1（每次都推进数据库，用性能换连续性）
INSERT INTO oa_sys.id_segment(biz_tag, max_id, step) VALUES ('DOC', 0, 1)
ON CONFLICT (biz_tag) DO NOTHING;
