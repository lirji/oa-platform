-- ═══════════════════════════════════════════════════════════════════
-- 把公文/知识库的检索从 tsvector 换成 trigram。
--
-- V60 建的是 GIN + to_tsvector('simple', ...)。它在中文上【完全不工作】：
-- 'simple' 配置按空白和标点切词，而中文不用空格，于是整句"关于国庆放假的通知"
-- 变成一个 token，搜"放假"永远返回 0 行。
--
--   SELECT to_tsvector('simple','关于国庆放假的通知') @@ plainto_tsquery('simple','放假');  → false
--   SELECT '关于国庆放假的通知' ILIKE '%放假%';                                            → true
--
-- 这比"没有搜索"更糟：接口 200、返回空数组，看起来像"确实没有这份文件"。
--
-- 真正的中文分词要装 zhparser / pg_bigm（本机没有），或上 ES（FINAL_PLAN 里是可选项）。
-- 在那之前，pg_trgm 的三元组索引能让 ILIKE '%词%' 走索引，是一个【真的能用】的降级。
-- 代价：短于 3 个字符的查询词退化为全表扫（trigram 需要 3 元组），
-- 以及索引比 tsvector 大。对几百份公文的量级，两者都无所谓。
-- ═══════════════════════════════════════════════════════════════════

CREATE EXTENSION IF NOT EXISTS pg_trgm;

DROP INDEX IF EXISTS oa_doc.ix_doc_fulltext;
DROP INDEX IF EXISTS oa_doc.ix_kb_doc_fulltext;

CREATE INDEX ix_doc_title_trgm ON oa_doc.official_doc USING gin (title gin_trgm_ops);
CREATE INDEX ix_doc_body_trgm  ON oa_doc.official_doc USING gin (body gin_trgm_ops);
CREATE INDEX ix_kb_title_trgm  ON oa_doc.kb_doc USING gin (title gin_trgm_ops);
CREATE INDEX ix_kb_body_trgm   ON oa_doc.kb_doc USING gin (body gin_trgm_ops);
