-- ═══════════════════════════════════════════════════════════════════
-- Phase 8 审计与报表。版本号区间 V80-V89。schema: oa_sys
-- ═══════════════════════════════════════════════════════════════════

-- ───────────────────────────────── 审计日志（按月分区）
-- 只记【写操作】与【敏感读】。把所有 GET 都记下来会让审计表以每天几百万行增长，
-- 结果是没人查得动 —— 一张查不动的审计表等于没有审计。
CREATE TABLE oa_sys.audit_log (
    id          bigserial    NOT NULL,
    tenant_id   bigint       NOT NULL DEFAULT 1,
    actor_id    varchar(64),
    actor_name  varchar(64),
    -- 代理办理时留痕：谁替谁做的。委托代理是 OA 的核心能力，
    -- 审计里分不清"张三自己批的"和"李四代张三批的"，委托功能就没法追责。
    on_behalf_of varchar(64),
    action      varchar(64)  NOT NULL,      -- 形如 org.employee.transfer / flow.task.complete
    module      varchar(32)  NOT NULL,
    target_type varchar(32),
    target_id   varchar(64),
    -- 结果与失败原因：被拒绝的操作【也要记】。只记成功的审计日志看不出有人在试探。
    outcome     varchar(16)  NOT NULL,      -- SUCCESS | DENIED | FAILED
    error       varchar(512),
    client_ip   varchar(64),
    detail      jsonb,
    org_id      bigint,
    org_path    varchar(512),
    created_at  timestamptz  NOT NULL DEFAULT now(),
    PRIMARY KEY (id, created_at),
    CONSTRAINT ck_audit_outcome CHECK (outcome IN ('SUCCESS','DENIED','FAILED'))
) PARTITION BY RANGE (created_at);

CREATE INDEX ix_audit_actor ON oa_sys.audit_log(actor_id, created_at DESC);
CREATE INDEX ix_audit_action ON oa_sys.audit_log(action, created_at DESC);
CREATE INDEX ix_audit_target ON oa_sys.audit_log(target_type, target_id, created_at DESC);
-- 只给被拒绝的建部分索引：安全排查最常问的就是"最近谁被拒了"，
-- 而 DENIED 只占极小比例，部分索引又小又快。
CREATE INDEX ix_audit_denied ON oa_sys.audit_log(created_at DESC) WHERE outcome = 'DENIED';
CREATE INDEX ix_audit_org_path ON oa_sys.audit_log(org_path text_pattern_ops);

DO $$
DECLARE d date := date_trunc('month', current_date)::date; i int;
BEGIN
    FOR i IN 0..11 LOOP
        EXECUTE format(
            'CREATE TABLE IF NOT EXISTS oa_sys.audit_log_%s PARTITION OF oa_sys.audit_log
             FOR VALUES FROM (%L) TO (%L)',
            to_char(d + (i || ' month')::interval, 'YYYYMM'),
            (d + (i || ' month')::interval)::date,
            (d + ((i + 1) || ' month')::interval)::date);
    END LOOP;
END $$;
CREATE TABLE IF NOT EXISTS oa_sys.audit_log_default PARTITION OF oa_sys.audit_log DEFAULT;

-- ───────────────────────────────── 字典与系统配置（模块 18 的另一半）
CREATE TABLE oa_sys.dict (
    id         bigserial    PRIMARY KEY,
    tenant_id  bigint       NOT NULL DEFAULT 1,
    dict_type  varchar(32)  NOT NULL,
    item_code  varchar(32)  NOT NULL,
    item_name  varchar(64)  NOT NULL,
    sort_no    int          NOT NULL DEFAULT 0,
    enabled    boolean      NOT NULL DEFAULT true,
    CONSTRAINT uk_dict UNIQUE (tenant_id, dict_type, item_code)
);

CREATE TABLE oa_sys.sys_config (
    config_key   varchar(64) PRIMARY KEY,
    config_value text,
    description  varchar(256),
    updated_by   varchar(64),
    updated_at   timestamptz NOT NULL DEFAULT now()
);

-- ───────────────────────────────── 驾驶舱：只读视图
-- 用视图而不是物化视图：万人级下这些聚合是秒级的，物化视图带来的刷新时机、
-- 陈旧度、并发刷新锁全是额外复杂度。真到扛不住了再物化，那时也有真实数字支撑决策。

-- 编制人效：各部门在岗人数（含子部门要在查询侧用 org_path 前缀，不在视图里递归）
CREATE OR REPLACE VIEW oa_sys.v_headcount AS
SELECT o.id AS org_id, o.name AS org_name, o.path AS org_path,
       count(DISTINCT a.employee_id) AS headcount
  FROM oa_org.org_unit o
  LEFT JOIN oa_org.employee_org_assignment a
         ON a.org_unit_id = o.id AND a.valid_to IS NULL
 WHERE o.status = 'ACTIVE'
 GROUP BY o.id, o.name, o.path;

-- 审批时效：按业务类型统计平均/最长审批耗时与在办量
CREATE OR REPLACE VIEW oa_sys.v_approval_efficiency AS
SELECT biz_type,
       count(*)                                                              AS total,
       count(*) FILTER (WHERE status = 'FINISHED')                           AS finished,
       count(*) FILTER (WHERE status <> 'FINISHED')                          AS running,
       count(*) FILTER (WHERE outcome = 'REJECTED')                          AS rejected,
       round(avg(EXTRACT(EPOCH FROM (finished_at - submitted_at)) / 3600.0)
             FILTER (WHERE finished_at IS NOT NULL)::numeric, 2)             AS avg_hours,
       round(max(EXTRACT(EPOCH FROM (finished_at - submitted_at)) / 3600.0)
             FILTER (WHERE finished_at IS NOT NULL)::numeric, 2)             AS max_hours
  FROM oa_flow.approval_instance
 GROUP BY biz_type;

-- 考勤统计：按日汇总（明细在 oa_att.attendance_daily，这里只做维度聚合）
CREATE OR REPLACE VIEW oa_sys.v_attendance_summary AS
SELECT work_date,
       count(*)                                        AS total,
       count(*) FILTER (WHERE status = 'NORMAL')       AS normal,
       count(*) FILTER (WHERE status = 'LATE')         AS late,
       count(*) FILTER (WHERE status = 'ABSENT')       AS absent,
       count(*) FILTER (WHERE status = 'LEAVE')        AS on_leave
  FROM oa_att.attendance_daily
 GROUP BY work_date;

-- 资产分布
CREATE OR REPLACE VIEW oa_sys.v_asset_summary AS
SELECT category, status, count(*) AS cnt, coalesce(sum(price), 0) AS total_price
  FROM oa_admin.asset
 GROUP BY category, status;
