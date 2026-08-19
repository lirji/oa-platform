-- ═══════════════════════════════════════════════════════════════════
-- 通用业务单据。补齐 FINAL_PLAN 模块 11 的其余 11 类（请假已在 V20 单独建表）。
--
-- ★ 为什么不给每一类各建一张表 + 各写一个 Service：
--   出差/加班/报销/借款/用印/合同/采购/入职/离职/调岗/调休，这 11 类的差别
--   只在【表单字段】和【审批级数怎么算】两处，流转、留痕、发件箱、待办投影全一样。
--   复制 11 份的代价不是写的时候累，而是以后每改一次审批链逻辑要改 11 处，
--   且必然有一处忘了改 —— 那一处会安静地用旧规则跑下去。
--
--   请假为什么留了独立表：它有额度冻结/扣减/释放这套独有的强一致语义
--   （leave_balance + txn 幂等），塞进通用 jsonb 会让那套约束无处安放。
--   "大部分通用 + 少数特殊单独处理"比"全部通用"和"全部特殊"都更诚实。
-- ═══════════════════════════════════════════════════════════════════

CREATE TABLE oa_flow.business_doc (
    id           bigserial    PRIMARY KEY,
    tenant_id    bigint       NOT NULL DEFAULT 1,
    biz_type     varchar(32)  NOT NULL,
    doc_no       varchar(64)  NOT NULL,
    applicant_id varchar(64)  NOT NULL,
    applicant_name varchar(64),
    title        varchar(200) NOT NULL,
    summary      varchar(512),
    -- 表单数据。★ 用 jsonb 而不是宽表：11 类单据的字段并集有上百个，
    -- 宽表会变成一张绝大多数列恒为 NULL 的表，而且加字段要改表。
    -- 代价是失去列级约束 —— 由 form_template 的 JSON Schema 在写入前校验补回来。
    form_data    jsonb        NOT NULL DEFAULT '{}'::jsonb,
    -- 从 form_data 里提出来的两个【驱动审批级数】的量，冗余成列是为了能建索引、能统计。
    -- 报销按金额定级、出差按天数定级，这两个维度覆盖了 11 类里的绝大多数。
    amount       numeric(14,2),
    days         numeric(6,1),
    status       varchar(16)  NOT NULL DEFAULT 'PENDING',   -- PENDING | APPROVED | REJECTED | CANCELLED
    org_id       bigint,
    org_path     varchar(512),
    created_at   timestamptz  NOT NULL DEFAULT now(),
    finished_at  timestamptz,
    CONSTRAINT uk_business_doc_no UNIQUE (tenant_id, doc_no),
    CONSTRAINT ck_business_doc_status CHECK (status IN ('PENDING','APPROVED','REJECTED','CANCELLED'))
);
CREATE INDEX ix_business_doc_applicant ON oa_flow.business_doc(applicant_id, created_at DESC);
CREATE INDEX ix_business_doc_type ON oa_flow.business_doc(biz_type, status, created_at DESC);
CREATE INDEX ix_business_doc_org_path ON oa_flow.business_doc(org_path text_pattern_ops);
-- jsonb 内容检索（"谁报销过差旅费"这类查询）
CREATE INDEX ix_business_doc_form ON oa_flow.business_doc USING gin (form_data);

-- ───────────────────────────────── 审批级数规则
-- ★ 规则进表而不是进代码：改一条"报销超过 5 万加一级"不该需要发版。
-- driver 说明按什么定级；thresholds 是有序的阈值表，第一个匹配的生效。
CREATE TABLE oa_flow.approval_level_rule (
    biz_type    varchar(32)  PRIMARY KEY,
    driver      varchar(16)  NOT NULL,      -- DAYS | AMOUNT | FIXED
    -- 形如 [{"lte": 3, "levels": 1}, {"lte": 7, "levels": 2}, {"levels": 3}]
    -- 最后一条不带 lte，表示"超过前面所有阈值"的兜底。没有兜底 = 大额单据算不出级数。
    thresholds  jsonb        NOT NULL,
    description varchar(256),
    CONSTRAINT ck_rule_driver CHECK (driver IN ('DAYS','AMOUNT','FIXED'))
);

INSERT INTO oa_flow.approval_level_rule(biz_type, driver, thresholds, description) VALUES
  ('OVERTIME',   'DAYS',   '[{"lte":1,"levels":1},{"levels":2}]',                        '加班：1 天内直属上级，超过加一级'),
  ('TRAVEL',     'DAYS',   '[{"lte":3,"levels":1},{"lte":7,"levels":2},{"levels":3}]',   '出差：按天数分三档'),
  ('COMPENSATORY','DAYS',  '[{"levels":1}]',                                             '调休：一律直属上级'),
  ('REIMBURSE',  'AMOUNT', '[{"lte":1000,"levels":1},{"lte":10000,"levels":2},{"lte":50000,"levels":3},{"levels":4}]', '报销：按金额分四档'),
  ('LOAN',       'AMOUNT', '[{"lte":10000,"levels":2},{"levels":3}]',                    '借款：起步两级'),
  ('SEAL',       'FIXED',  '[{"levels":2}]',                                             '用印：固定两级'),
  ('CONTRACT',   'AMOUNT', '[{"lte":100000,"levels":3},{"levels":4}]',                   '合同：起步三级'),
  ('PURCHASE',   'AMOUNT', '[{"lte":5000,"levels":2},{"lte":50000,"levels":3},{"levels":4}]', '采购：按金额分三档'),
  ('ONBOARD',    'FIXED',  '[{"levels":2}]',                                             '入职：固定两级'),
  ('OFFBOARD',   'FIXED',  '[{"levels":3}]',                                             '离职：固定三级'),
  ('TRANSFER',   'FIXED',  '[{"levels":3}]',                                             '调岗：固定三级')
ON CONFLICT (biz_type) DO NOTHING;

-- ───────────────────────────────── 11 类表单模板
-- JSON Schema 只做最小必要校验（必填 + 类型 + 范围）。首版刻意不做联动与公式（R12）：
-- 表单引擎的范围一旦放开就会无限延期，而 15 种字段类型 + 必填校验已经能覆盖真实单据。
INSERT INTO oa_flow.form_template(code, name, version, schema_json, status, published_at, created_by) VALUES
 ('OVERTIME', '加班申请单', 1, '{"type":"object","required":["startAt","endAt","days","reason"],
   "properties":{"startAt":{"type":"string","title":"开始时间"},"endAt":{"type":"string","title":"结束时间"},
   "days":{"type":"number","title":"加班天数","minimum":0.5},"reason":{"type":"string","title":"事由"}}}',
   'PUBLISHED', now(), 'system'),
 ('TRAVEL', '出差申请单', 1, '{"type":"object","required":["destination","startDate","endDate","days","reason"],
   "properties":{"destination":{"type":"string","title":"目的地"},"startDate":{"type":"string","title":"开始日期"},
   "endDate":{"type":"string","title":"结束日期"},"days":{"type":"number","title":"天数","minimum":0.5},
   "reason":{"type":"string","title":"事由"}}}', 'PUBLISHED', now(), 'system'),
 ('COMPENSATORY', '调休申请单', 1, '{"type":"object","required":["startDate","days"],
   "properties":{"startDate":{"type":"string","title":"日期"},"days":{"type":"number","title":"天数","minimum":0.5},
   "reason":{"type":"string","title":"事由"}}}', 'PUBLISHED', now(), 'system'),
 ('REIMBURSE', '报销单', 1, '{"type":"object","required":["amount","category","reason"],
   "properties":{"amount":{"type":"number","title":"金额","minimum":0.01},
   "category":{"type":"string","title":"费用类别"},"reason":{"type":"string","title":"事由"},
   "invoiceCount":{"type":"integer","title":"发票张数"}}}', 'PUBLISHED', now(), 'system'),
 ('LOAN', '借款单', 1, '{"type":"object","required":["amount","reason","repayDate"],
   "properties":{"amount":{"type":"number","title":"金额","minimum":0.01},"reason":{"type":"string","title":"事由"},
   "repayDate":{"type":"string","title":"预计归还日"}}}', 'PUBLISHED', now(), 'system'),
 ('SEAL', '用印申请单', 1, '{"type":"object","required":["sealType","docName","count"],
   "properties":{"sealType":{"type":"string","title":"印章类型"},"docName":{"type":"string","title":"文件名称"},
   "count":{"type":"integer","title":"份数","minimum":1},"reason":{"type":"string","title":"用途"}}}',
   'PUBLISHED', now(), 'system'),
 ('CONTRACT', '合同审批单', 1, '{"type":"object","required":["counterparty","amount","subject"],
   "properties":{"counterparty":{"type":"string","title":"对方单位"},"amount":{"type":"number","title":"合同金额","minimum":0},
   "subject":{"type":"string","title":"标的"},"reason":{"type":"string","title":"说明"}}}',
   'PUBLISHED', now(), 'system'),
 ('PURCHASE', '采购申请单', 1, '{"type":"object","required":["item","amount","qty"],
   "properties":{"item":{"type":"string","title":"物品"},"amount":{"type":"number","title":"预算金额","minimum":0.01},
   "qty":{"type":"integer","title":"数量","minimum":1},"reason":{"type":"string","title":"用途"}}}',
   'PUBLISHED', now(), 'system'),
 ('ONBOARD', '入职申请单', 1, '{"type":"object","required":["candidateName","position","onboardDate"],
   "properties":{"candidateName":{"type":"string","title":"候选人"},"position":{"type":"string","title":"岗位"},
   "onboardDate":{"type":"string","title":"入职日期"}}}', 'PUBLISHED', now(), 'system'),
 ('OFFBOARD', '离职申请单', 1, '{"type":"object","required":["lastDate","reason"],
   "properties":{"lastDate":{"type":"string","title":"最后工作日"},"reason":{"type":"string","title":"离职原因"},
   "handoverTo":{"type":"string","title":"交接人"}}}', 'PUBLISHED', now(), 'system'),
 ('TRANSFER', '调岗申请单', 1, '{"type":"object","required":["targetOrg","targetPosition","effectiveDate"],
   "properties":{"targetOrg":{"type":"string","title":"目标部门"},"targetPosition":{"type":"string","title":"目标岗位"},
   "effectiveDate":{"type":"string","title":"生效日期"},"reason":{"type":"string","title":"原因"}}}',
   'PUBLISHED', now(), 'system')
ON CONFLICT DO NOTHING;
