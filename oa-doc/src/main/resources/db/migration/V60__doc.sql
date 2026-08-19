-- ═══════════════════════════════════════════════════════════════════
-- Phase 7 文档域。版本号区间 V60-V69。schema: oa_doc
-- ═══════════════════════════════════════════════════════════════════

-- ───────────────────────────────── 公文
-- 收文（外部来文）与发文（本单位对外）共用一张表，靠 direction 区分：
-- 两者的字段、流转、归档几乎一致，拆两张表会让"全部公文"的查询变成 UNION。
CREATE TABLE oa_doc.official_doc (
    id          bigserial    PRIMARY KEY,
    tenant_id   bigint       NOT NULL DEFAULT 1,
    direction   varchar(8)   NOT NULL,                    -- IN 收文 | OUT 发文
    -- 文号：发文在【核发】时才生成，收文没有本单位文号。所以可空，
    -- 但一旦生成就必须唯一 —— 重号的公文在档案管理上是事故。
    doc_number  varchar(64),
    title       varchar(200) NOT NULL,
    body        text,
    doc_type    varchar(32)  NOT NULL DEFAULT 'NOTICE',    -- NOTICE 通知 | REPORT 报告 | REQUEST 请示
    urgency     varchar(16)  NOT NULL DEFAULT 'NORMAL',    -- NORMAL | URGENT | FLASH
    secrecy     varchar(16)  NOT NULL DEFAULT 'PUBLIC',    -- PUBLIC | INTERNAL | SECRET
    source_org  varchar(128),                              -- 收文的来文单位
    drafter_id  varchar(64),
    -- DRAFT 拟稿 → REVIEWING 审核中 → ISSUED 已核发 → ARCHIVED 已归档；任一步可 CANCELLED
    status      varchar(16)  NOT NULL DEFAULT 'DRAFT',
    approval_business_key varchar(64),                     -- 关联 oa-flow 的审批单（跨模块只存 key）
    issued_at   timestamptz,
    archived_at timestamptz,
    org_id      bigint,
    org_path    varchar(512),
    created_at  timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT ck_doc_direction CHECK (direction IN ('IN','OUT')),
    CONSTRAINT ck_doc_status CHECK (status IN ('DRAFT','REVIEWING','ISSUED','ARCHIVED','CANCELLED')),
    CONSTRAINT ck_doc_secrecy CHECK (secrecy IN ('PUBLIC','INTERNAL','SECRET')),
    -- 已核发就必须有文号。没有这条约束，"核发了但没文号"会一路流到归档才被人发现。
    CONSTRAINT ck_doc_number_when_issued CHECK (
        status NOT IN ('ISSUED','ARCHIVED') OR doc_number IS NOT NULL)
);
CREATE UNIQUE INDEX uk_doc_number ON oa_doc.official_doc(tenant_id, doc_number)
    WHERE doc_number IS NOT NULL;
CREATE INDEX ix_doc_org_path ON oa_doc.official_doc(org_path text_pattern_ops);
CREATE INDEX ix_doc_status ON oa_doc.official_doc(status, created_at DESC);
-- 全文检索降级方案：ES 是可选项（FINAL_PLAN 开放项 4），不上 ES 就用 PG 的 GIN。
-- simple 配置不做中文分词，效果不如 ES，但"能搜到标题里的词"这个底线是有的。
CREATE INDEX ix_doc_fulltext ON oa_doc.official_doc
    USING gin (to_tsvector('simple', coalesce(title,'') || ' ' || coalesce(body,'')));

-- 公文流转轨迹
CREATE TABLE oa_doc.doc_flow_log (
    id         bigserial    PRIMARY KEY,
    doc_id     bigint       NOT NULL REFERENCES oa_doc.official_doc(id),
    action     varchar(16)  NOT NULL,      -- SUBMIT | ISSUE | ARCHIVE | CANCEL | READ
    actor_id   varchar(64)  NOT NULL,
    from_status varchar(16),
    to_status   varchar(16),
    remark     varchar(256),
    created_at timestamptz  NOT NULL DEFAULT now()
);
CREATE INDEX ix_doc_flow_doc ON oa_doc.doc_flow_log(doc_id, created_at);

-- ───────────────────────────────── 知识库 / 网盘
CREATE TABLE oa_doc.kb_folder (
    id         bigserial    PRIMARY KEY,
    tenant_id  bigint       NOT NULL DEFAULT 1,
    parent_id  bigint       REFERENCES oa_doc.kb_folder(id),
    name       varchar(128) NOT NULL,
    -- 与组织树同一套路数：物化路径 + text_pattern_ops，前缀查子树
    path       varchar(512) NOT NULL,
    owner_id   varchar(64)  NOT NULL,
    created_at timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT ck_kb_folder_path CHECK (path ~ '^/([0-9]+/)*$')
);
CREATE INDEX ix_kb_folder_path ON oa_doc.kb_folder(path text_pattern_ops);

CREATE TABLE oa_doc.kb_doc (
    id          bigserial    PRIMARY KEY,
    tenant_id   bigint       NOT NULL DEFAULT 1,
    folder_id   bigint       REFERENCES oa_doc.kb_folder(id),
    title       varchar(200) NOT NULL,
    summary     varchar(512),
    -- 正文与附件分家：正文进库（要全文检索），附件进 MinIO（由 oa-file-service 管），
    -- 这里只存对象 key。把二进制塞进数据库会让备份、复制、全表扫描全部变慢。
    body        text,
    file_key    varchar(256),
    owner_id    varchar(64)  NOT NULL,
    version     int          NOT NULL DEFAULT 1,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    updated_at  timestamptz  NOT NULL DEFAULT now()
);
CREATE INDEX ix_kb_doc_folder ON oa_doc.kb_doc(folder_id);
CREATE INDEX ix_kb_doc_fulltext ON oa_doc.kb_doc
    USING gin (to_tsvector('simple', coalesce(title,'') || ' ' || coalesce(summary,'') || ' ' || coalesce(body,'')));

-- 知识库共享（内置实现）。
-- ★ 这张表是 SpiceDB 关闭时的兜底，也是打开时的"影子"：
--   ADR 决定知识库这类【任意对象 ACL】可选挂 auth-platform 的 SpiceDB，
--   但开关默认 false（oa.authz.spicedb.enabled）。两条路径必须都能独立工作，
--   否则"关掉开关回退"就只是句口号。
CREATE TABLE oa_doc.kb_share (
    id           bigserial   PRIMARY KEY,
    tenant_id    bigint      NOT NULL DEFAULT 1,
    resource_type varchar(16) NOT NULL,      -- FOLDER | DOC
    resource_id  bigint      NOT NULL,
    -- USER 指定人 / ORG 部门及子部门 / ALL 全员
    subject_type varchar(16) NOT NULL,
    subject_id   varchar(64),
    -- ORG 授权时冗余 org_path，判权走前缀匹配而不是展开成 id 列表
    subject_org_path varchar(512),
    -- READ 只读 / WRITE 可编辑
    level        varchar(8)  NOT NULL DEFAULT 'READ',
    granted_by   varchar(64) NOT NULL,
    created_at   timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_kb_share_rtype CHECK (resource_type IN ('FOLDER','DOC')),
    CONSTRAINT ck_kb_share_stype CHECK (subject_type IN ('USER','ORG','ALL')),
    CONSTRAINT ck_kb_share_level CHECK (level IN ('READ','WRITE')),
    CONSTRAINT uk_kb_share UNIQUE (tenant_id, resource_type, resource_id, subject_type, subject_id)
);
CREATE INDEX ix_kb_share_resource ON oa_doc.kb_share(resource_type, resource_id);
CREATE INDEX ix_kb_share_org_path ON oa_doc.kb_share(subject_org_path text_pattern_ops);

-- 种子：一个根目录
INSERT INTO oa_doc.kb_folder(id, parent_id, name, path, owner_id)
VALUES (1, NULL, '公司知识库', '/1/', 'system')
ON CONFLICT DO NOTHING;
SELECT setval(pg_get_serial_sequence('oa_doc.kb_folder','id'), GREATEST((SELECT max(id) FROM oa_doc.kb_folder), 1));
