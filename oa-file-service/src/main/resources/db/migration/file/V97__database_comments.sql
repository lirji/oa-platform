-- 文件服务数据字典注释。

COMMENT ON TABLE oa_sys.file_object IS 'MinIO 文件对象的业务元数据与授权归属信息';
COMMENT ON COLUMN oa_sys.file_object.id IS '文件元数据主键';
COMMENT ON COLUMN oa_sys.file_object.tenant_id IS '租户标识，当前默认租户为 1';
COMMENT ON COLUMN oa_sys.file_object.object_key IS '对象存储中不可猜测的对象键';
COMMENT ON COLUMN oa_sys.file_object.bucket IS '对象所在的 MinIO bucket';
COMMENT ON COLUMN oa_sys.file_object.file_name IS '上传时的原始文件名';
COMMENT ON COLUMN oa_sys.file_object.content_type IS '文件 MIME 类型';
COMMENT ON COLUMN oa_sys.file_object.size_bytes IS '文件字节数';
COMMENT ON COLUMN oa_sys.file_object.sha256 IS '文件内容 SHA-256，用于完整性校验、秒传和去重';
COMMENT ON COLUMN oa_sys.file_object.biz_type IS '归属业务类型，用于选择下载授权规则';
COMMENT ON COLUMN oa_sys.file_object.biz_id IS '归属业务标识';
COMMENT ON COLUMN oa_sys.file_object.owner_id IS '文件所有人的 Casdoor sub';
COMMENT ON COLUMN oa_sys.file_object.org_id IS '上传发生时的组织主键快照';
COMMENT ON COLUMN oa_sys.file_object.org_path IS '上传发生时的组织路径快照，用于数据权限过滤';
COMMENT ON COLUMN oa_sys.file_object.created_at IS '文件元数据创建时间';
