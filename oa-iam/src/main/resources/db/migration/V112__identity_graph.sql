-- S03：身份 1 跳图谱为只读投影，运行时从 identity / grant_record 计算。
-- 不落边表，避免与授权写路径双写漂移（CONTRACTS：写边不对前端开放）。

COMMENT ON INDEX oa_iam.ix_identity_owner IS 'S03 图谱 OWNS 反向查询（属主→非人身份）';
