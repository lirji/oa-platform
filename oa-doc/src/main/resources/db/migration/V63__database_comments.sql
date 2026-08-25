-- 公文与知识库数据字典注释。

COMMENT ON TABLE oa_doc.official_doc IS '统一公文表，通过 direction 区分收文与发文并保存流转状态';
COMMENT ON COLUMN oa_doc.official_doc.id IS '公文主键';
COMMENT ON COLUMN oa_doc.official_doc.tenant_id IS '租户标识，当前默认租户为 1';
COMMENT ON COLUMN oa_doc.official_doc.direction IS '公文方向：IN 收文或 OUT 发文';
COMMENT ON COLUMN oa_doc.official_doc.doc_number IS '本单位公文文号，核发后必须存在且租户内唯一';
COMMENT ON COLUMN oa_doc.official_doc.title IS '公文标题';
COMMENT ON COLUMN oa_doc.official_doc.body IS '公文正文';
COMMENT ON COLUMN oa_doc.official_doc.doc_type IS '公文类型，如 NOTICE、REPORT、REQUEST';
COMMENT ON COLUMN oa_doc.official_doc.urgency IS '紧急程度：NORMAL、URGENT 或 FLASH';
COMMENT ON COLUMN oa_doc.official_doc.secrecy IS '密级：PUBLIC、INTERNAL 或 SECRET';
COMMENT ON COLUMN oa_doc.official_doc.source_org IS '收文的来文单位';
COMMENT ON COLUMN oa_doc.official_doc.drafter_id IS '拟稿人的 Casdoor sub';
COMMENT ON COLUMN oa_doc.official_doc.status IS '公文状态：DRAFT、REVIEWING、ISSUED、ARCHIVED 或 CANCELLED';
COMMENT ON COLUMN oa_doc.official_doc.approval_business_key IS '关联审批单的跨模块业务键';
COMMENT ON COLUMN oa_doc.official_doc.issued_at IS '核发时间';
COMMENT ON COLUMN oa_doc.official_doc.archived_at IS '归档时间';
COMMENT ON COLUMN oa_doc.official_doc.org_id IS '公文所属组织主键快照';
COMMENT ON COLUMN oa_doc.official_doc.org_path IS '公文所属组织路径快照，用于数据权限过滤';
COMMENT ON COLUMN oa_doc.official_doc.created_at IS '创建时间';

COMMENT ON TABLE oa_doc.doc_flow_log IS '公文提交、核发、归档、取消和阅读的流转轨迹';
COMMENT ON COLUMN oa_doc.doc_flow_log.id IS '公文流转轨迹主键';
COMMENT ON COLUMN oa_doc.doc_flow_log.doc_id IS '公文主键';
COMMENT ON COLUMN oa_doc.doc_flow_log.action IS '流转动作：SUBMIT、ISSUE、ARCHIVE、CANCEL 或 READ';
COMMENT ON COLUMN oa_doc.doc_flow_log.actor_id IS '操作人的 Casdoor sub';
COMMENT ON COLUMN oa_doc.doc_flow_log.from_status IS '操作前公文状态';
COMMENT ON COLUMN oa_doc.doc_flow_log.to_status IS '操作后公文状态';
COMMENT ON COLUMN oa_doc.doc_flow_log.remark IS '操作备注';
COMMENT ON COLUMN oa_doc.doc_flow_log.created_at IS '操作时间';

COMMENT ON TABLE oa_doc.kb_folder IS '知识库目录树，使用物化路径支持子树查询';
COMMENT ON COLUMN oa_doc.kb_folder.id IS '知识库目录主键';
COMMENT ON COLUMN oa_doc.kb_folder.tenant_id IS '租户标识，当前默认租户为 1';
COMMENT ON COLUMN oa_doc.kb_folder.parent_id IS '父目录主键，根目录为空';
COMMENT ON COLUMN oa_doc.kb_folder.name IS '目录名称';
COMMENT ON COLUMN oa_doc.kb_folder.path IS '目录物化路径，格式如 /1/23/';
COMMENT ON COLUMN oa_doc.kb_folder.owner_id IS '目录所有人的 Casdoor sub';
COMMENT ON COLUMN oa_doc.kb_folder.created_at IS '创建时间';

COMMENT ON TABLE oa_doc.kb_doc IS '知识库文档正文与附件对象键的元数据';
COMMENT ON COLUMN oa_doc.kb_doc.id IS '知识库文档主键';
COMMENT ON COLUMN oa_doc.kb_doc.tenant_id IS '租户标识，当前默认租户为 1';
COMMENT ON COLUMN oa_doc.kb_doc.folder_id IS '所属知识库目录主键';
COMMENT ON COLUMN oa_doc.kb_doc.title IS '文档标题';
COMMENT ON COLUMN oa_doc.kb_doc.summary IS '文档摘要';
COMMENT ON COLUMN oa_doc.kb_doc.body IS '可全文或模糊检索的正文';
COMMENT ON COLUMN oa_doc.kb_doc.file_key IS '附件在对象存储中的对象键';
COMMENT ON COLUMN oa_doc.kb_doc.owner_id IS '文档所有人的 Casdoor sub';
COMMENT ON COLUMN oa_doc.kb_doc.version IS '文档版本号';
COMMENT ON COLUMN oa_doc.kb_doc.created_at IS '创建时间';
COMMENT ON COLUMN oa_doc.kb_doc.updated_at IS '最后修改时间';

COMMENT ON TABLE oa_doc.kb_share IS '知识库目录或文档的内置对象级共享授权';
COMMENT ON COLUMN oa_doc.kb_share.id IS '共享授权主键';
COMMENT ON COLUMN oa_doc.kb_share.tenant_id IS '租户标识，当前默认租户为 1';
COMMENT ON COLUMN oa_doc.kb_share.resource_type IS '资源类型：FOLDER 或 DOC';
COMMENT ON COLUMN oa_doc.kb_share.resource_id IS '被共享资源的主键';
COMMENT ON COLUMN oa_doc.kb_share.subject_type IS '授权主体类型：USER、ORG 或 ALL';
COMMENT ON COLUMN oa_doc.kb_share.subject_id IS '授权主体标识，ALL 类型时可为空';
COMMENT ON COLUMN oa_doc.kb_share.subject_org_path IS 'ORG 主体的组织路径，用于子组织前缀匹配';
COMMENT ON COLUMN oa_doc.kb_share.level IS '共享级别：READ 或 WRITE';
COMMENT ON COLUMN oa_doc.kb_share.granted_by IS '授权人的 Casdoor sub';
COMMENT ON COLUMN oa_doc.kb_share.created_at IS '授权创建时间';
