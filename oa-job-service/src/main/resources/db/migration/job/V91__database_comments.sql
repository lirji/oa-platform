-- 跑批服务数据字典注释。

COMMENT ON TABLE oa_sys.job_run IS '跑批任务按逻辑执行和分片记录的幂等运行日志';
COMMENT ON COLUMN oa_sys.job_run.id IS '任务运行记录主键';
COMMENT ON COLUMN oa_sys.job_run.job_name IS '任务名称';
COMMENT ON COLUMN oa_sys.job_run.shard IS '当前执行分片号，从 0 开始';
COMMENT ON COLUMN oa_sys.job_run.shard_total IS '本次执行的总分片数';
COMMENT ON COLUMN oa_sys.job_run.idem_key IS '逻辑执行幂等键，同一任务内唯一';
COMMENT ON COLUMN oa_sys.job_run.status IS '执行状态：RUNNING、SUCCESS 或 FAILED';
COMMENT ON COLUMN oa_sys.job_run.affected IS '本次执行影响的业务记录数';
COMMENT ON COLUMN oa_sys.job_run.error IS '执行失败时的错误摘要';
COMMENT ON COLUMN oa_sys.job_run.started_at IS '任务开始时间';
COMMENT ON COLUMN oa_sys.job_run.finished_at IS '任务结束时间';
