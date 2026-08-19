-- ═══════════════════════════════════════════════════════════════════
-- Phase 5 考勤域。版本号区间 V30-V39。
-- ═══════════════════════════════════════════════════════════════════

-- ───────────────────────────────── 打卡记录（按月声明式分区）
-- 容量：10,000 人 × 2 次 × 250 天 ≈ 500 万行/年。单表也能撑，
-- 但分区让"删三个月前的数据"变成 DETACH 一个分区（秒级、不锁表），
-- 而不是一条会拖垮线上的 DELETE。
CREATE TABLE oa_att.punch_record (
    id          bigserial    NOT NULL,
    tenant_id   bigint       NOT NULL DEFAULT 1,
    user_id     varchar(64)  NOT NULL,
    employee_id bigint,
    punch_date  date         NOT NULL,
    punch_type  varchar(8)   NOT NULL,          -- IN | OUT
    punch_time  timestamptz  NOT NULL,
    source      varchar(16)  NOT NULL DEFAULT 'MOBILE',  -- MOBILE|WEB|GATE|WIFI
    latitude    numeric(10,6),
    longitude   numeric(10,6),
    device_id   varchar(64),
    -- 组织快照：考勤报表按部门统计，不能因为事后调岗就把历史考勤划到新部门去
    org_id      bigint,
    org_path    varchar(512),
    created_at  timestamptz  NOT NULL DEFAULT now(),
    PRIMARY KEY (id, punch_time),
    CONSTRAINT ck_punch_type CHECK (punch_type IN ('IN','OUT'))
) PARTITION BY RANGE (punch_time);

-- 幂等：同一个人同一天同一类型只留一条。分区表上的唯一索引必须带分区键。
CREATE UNIQUE INDEX uk_punch_idem ON oa_att.punch_record(tenant_id, user_id, punch_date, punch_type, punch_time);
CREATE INDEX ix_punch_user_date ON oa_att.punch_record(user_id, punch_date);
CREATE INDEX ix_punch_org_path  ON oa_att.punch_record(org_path text_pattern_ops);

-- 预建 12 个月分区 + 兜底分区。
-- 生产应由 oa-job-service 每月滚动创建；兜底分区保证漏建时不会直接写失败，
-- 而是先落进 default 再由运维搬迁 —— 打卡不能因为运维忘了建分区就丢。
DO $$
DECLARE
    d date := date_trunc('month', current_date)::date;
    i int;
BEGIN
    FOR i IN 0..11 LOOP
        EXECUTE format(
            'CREATE TABLE IF NOT EXISTS oa_att.punch_record_%s PARTITION OF oa_att.punch_record
             FOR VALUES FROM (%L) TO (%L)',
            to_char(d + (i || ' month')::interval, 'YYYYMM'),
            (d + (i || ' month')::interval)::date,
            (d + ((i + 1) || ' month')::interval)::date);
    END LOOP;
END $$;
CREATE TABLE IF NOT EXISTS oa_att.punch_record_default PARTITION OF oa_att.punch_record DEFAULT;

-- ───────────────────────────────── 打卡死信：批量落库彻底失败的记录
-- 打卡是"用户已经站在门口按了"的动作，宁可进死信人工补，也不能静默丢。
CREATE TABLE oa_att.punch_dead_letter (
    id         bigserial   PRIMARY KEY,
    payload    text        NOT NULL,
    error      text,
    created_at timestamptz NOT NULL DEFAULT now()
);

-- ───────────────────────────────── 日结
CREATE TABLE oa_att.attendance_daily (
    id            bigserial   PRIMARY KEY,
    tenant_id     bigint      NOT NULL DEFAULT 1,
    user_id       varchar(64) NOT NULL,
    work_date     date        NOT NULL,
    first_in      timestamptz,
    last_out      timestamptz,
    work_minutes  int         NOT NULL DEFAULT 0,
    status        varchar(16) NOT NULL DEFAULT 'NORMAL',  -- NORMAL|LATE|EARLY|ABSENT|MISSING
    org_id        bigint,
    org_path      varchar(512),
    computed_at   timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uk_daily ON oa_att.attendance_daily(tenant_id, user_id, work_date);
CREATE INDEX ix_daily_org_path ON oa_att.attendance_daily(org_path text_pattern_ops);
CREATE INDEX ix_daily_date ON oa_att.attendance_daily(work_date);

-- ───────────────────────────────── 班次（最简）
CREATE TABLE oa_att.shift (
    id         bigserial   PRIMARY KEY,
    code       varchar(32) NOT NULL UNIQUE,
    name       varchar(64) NOT NULL,
    start_time time        NOT NULL,
    end_time   time        NOT NULL,
    late_after_minutes  int NOT NULL DEFAULT 0,
    status     varchar(16) NOT NULL DEFAULT 'ACTIVE'
);
INSERT INTO oa_att.shift(code, name, start_time, end_time, late_after_minutes) VALUES
  ('STANDARD', '标准班 9:00-18:00', '09:00', '18:00', 0);
