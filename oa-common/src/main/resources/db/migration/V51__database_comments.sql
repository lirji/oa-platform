-- 公共基础表的数据字典注释。
-- 单独新增迁移，不修改已执行过的建表脚本，避免 Flyway checksum 漂移。

COMMENT ON TABLE oa_sys.schema_probe IS '平台基线与数据库能力的安装探针记录';
COMMENT ON COLUMN oa_sys.schema_probe.id IS '探针记录主键';
COMMENT ON COLUMN oa_sys.schema_probe.phase IS '创建该记录的平台实施阶段';
COMMENT ON COLUMN oa_sys.schema_probe.note IS '探针结果或基线能力说明';
COMMENT ON COLUMN oa_sys.schema_probe.created_at IS '探针记录创建时间';

COMMENT ON TABLE oa_sys.id_segment IS '全平台业务编号的数据库号段分配表';
COMMENT ON COLUMN oa_sys.id_segment.biz_tag IS '业务号段标识，如 DOC、LEAVE';
COMMENT ON COLUMN oa_sys.id_segment.max_id IS '当前已分配号段的最大编号';
COMMENT ON COLUMN oa_sys.id_segment.step IS '每次申请号段时预分配的编号数量';
COMMENT ON COLUMN oa_sys.id_segment.updated_at IS '号段最后推进时间';
