-- ═══════════════════════════════════════════════════════════════════
-- Phase 8 跑批。版本号区间 V90-V99。schema: oa_sys（复用，不再新开）
-- 由 oa-job-service 自己迁移，独立历史表（同 notify 的理由）。
-- ═══════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS oa_sys.job_run (
    id          bigserial   PRIMARY KEY,
    job_name    varchar(64) NOT NULL,
    -- 分片号与总片数。跑批按 employee_id % shards 分片并行（FINAL_PLAN §9.3 场景③）。
    shard       int         NOT NULL DEFAULT 0,
    shard_total int         NOT NULL DEFAULT 1,
    -- 幂等键：同一个 job 的同一逻辑日 + 同一分片只该跑一次。
    -- 定时器重复触发、运维手工重跑、多实例同时到点，三种情况都靠它挡住。
    idem_key    varchar(128) NOT NULL,
    status      varchar(16) NOT NULL DEFAULT 'RUNNING',   -- RUNNING | SUCCESS | FAILED
    affected    bigint      NOT NULL DEFAULT 0,
    error       text,
    started_at  timestamptz NOT NULL DEFAULT now(),
    finished_at timestamptz,
    CONSTRAINT uk_job_idem UNIQUE (job_name, idem_key),
    CONSTRAINT ck_job_status CHECK (status IN ('RUNNING','SUCCESS','FAILED'))
);
CREATE INDEX IF NOT EXISTS ix_job_run_name ON oa_sys.job_run(job_name, started_at DESC);
