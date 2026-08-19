-- Phase 0 基线：只建 schema 与扩展，业务表从 Phase 1 起按模块分文件加。
-- 模块化单体：同库分 schema，禁止跨 schema join（为将来拆库留缝）。

CREATE SCHEMA IF NOT EXISTS oa_org;
CREATE SCHEMA IF NOT EXISTS oa_iam;
CREATE SCHEMA IF NOT EXISTS oa_flow;
CREATE SCHEMA IF NOT EXISTS oa_att;
CREATE SCHEMA IF NOT EXISTS oa_doc;
CREATE SCHEMA IF NOT EXISTS oa_admin;
CREATE SCHEMA IF NOT EXISTS oa_sys;

-- 会议室预定用 gist 排他约束（EXCLUDE USING gist ... WITH &&）需要 btree_gist
CREATE EXTENSION IF NOT EXISTS btree_gist;
-- 敏感字段确定性哈希 / 加密辅助
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- 冒烟脚本用的基建自检表
CREATE TABLE IF NOT EXISTS oa_sys.schema_probe (
    id          bigserial PRIMARY KEY,
    phase       varchar(16)  NOT NULL,
    note        text,
    created_at  timestamptz  NOT NULL DEFAULT now()
);
INSERT INTO oa_sys.schema_probe(phase, note) VALUES ('phase0', 'baseline schemas + btree_gist + pgcrypto');
