-- 权限域数据字典注释。

COMMENT ON TABLE oa_iam.permission IS '权限点目录，统一描述菜单、按钮、接口、数据和字段权限';
COMMENT ON COLUMN oa_iam.permission.id IS '权限点主键';
COMMENT ON COLUMN oa_iam.permission.code IS '全局唯一权限编码，如 oa:leave:approve';
COMMENT ON COLUMN oa_iam.permission.name IS '权限点名称';
COMMENT ON COLUMN oa_iam.permission.type IS '权限类型：MENU、BUTTON、API、DATA 或 FIELD';
COMMENT ON COLUMN oa_iam.permission.module IS '所属业务模块';
COMMENT ON COLUMN oa_iam.permission.parent_id IS '父权限点主键，用于菜单或权限树';
COMMENT ON COLUMN oa_iam.permission.resource IS '资源模式，API 类型可存 URL 模式，但不作为后端安全边界';
COMMENT ON COLUMN oa_iam.permission.method IS 'HTTP 方法';
COMMENT ON COLUMN oa_iam.permission.icon IS '前端菜单图标标识';
COMMENT ON COLUMN oa_iam.permission.route IS '前端菜单路由';
COMMENT ON COLUMN oa_iam.permission.sort_order IS '同级权限点展示顺序';
COMMENT ON COLUMN oa_iam.permission.status IS '权限点状态，ACTIVE 或 DISABLED';
COMMENT ON COLUMN oa_iam.permission.builtin IS '是否为平台内置权限点';
COMMENT ON COLUMN oa_iam.permission.require_elevation IS '是否必须存在活跃的 JIT 提权才能使用';
COMMENT ON COLUMN oa_iam.permission.remark IS '权限点备注';
COMMENT ON COLUMN oa_iam.permission.created_at IS '创建时间';

COMMENT ON TABLE oa_iam.role IS 'RBAC 角色定义表';
COMMENT ON COLUMN oa_iam.role.id IS '角色主键';
COMMENT ON COLUMN oa_iam.role.tenant_id IS '租户标识，当前默认租户为 1';
COMMENT ON COLUMN oa_iam.role.code IS '租户内唯一的角色编码';
COMMENT ON COLUMN oa_iam.role.name IS '角色名称';
COMMENT ON COLUMN oa_iam.role.type IS '角色类型：SYSTEM、BUSINESS 或 CUSTOM';
COMMENT ON COLUMN oa_iam.role.default_scope IS '角色默认数据范围：ALL、ORG_AND_SUB、ORG、SELF、CUSTOM 或 NONE';
COMMENT ON COLUMN oa_iam.role.status IS '角色状态，默认 ACTIVE';
COMMENT ON COLUMN oa_iam.role.builtin IS '是否为平台内置角色';
COMMENT ON COLUMN oa_iam.role.version IS '乐观锁版本号';
COMMENT ON COLUMN oa_iam.role.remark IS '角色说明';
COMMENT ON COLUMN oa_iam.role.created_at IS '创建时间';
COMMENT ON COLUMN oa_iam.role.updated_at IS '最后修改时间';

COMMENT ON TABLE oa_iam.role_inherit IS '角色继承闭包表，保存祖先角色、后代角色和继承距离';
COMMENT ON COLUMN oa_iam.role_inherit.ancestor_role_id IS '祖先角色主键，拥有后代角色的权限';
COMMENT ON COLUMN oa_iam.role_inherit.descendant_role_id IS '后代角色主键';
COMMENT ON COLUMN oa_iam.role_inherit.distance IS '角色继承距离，自身关系为 0';

COMMENT ON TABLE oa_iam.role_permission IS '角色与权限点的多对多关联表';
COMMENT ON COLUMN oa_iam.role_permission.role_id IS '角色主键';
COMMENT ON COLUMN oa_iam.role_permission.permission_id IS '权限点主键';

COMMENT ON TABLE oa_iam.grant_record IS '主体角色授权记录，是用户、组织、岗位和用户组授权语义的统一收敛点';
COMMENT ON COLUMN oa_iam.grant_record.id IS '授权记录主键';
COMMENT ON COLUMN oa_iam.grant_record.tenant_id IS '租户标识，当前默认租户为 1';
COMMENT ON COLUMN oa_iam.grant_record.subject_type IS '授权主体类型：USER、ORG_UNIT、POSITION 或 USER_GROUP';
COMMENT ON COLUMN oa_iam.grant_record.subject_id IS '授权主体标识，类型由 subject_type 决定';
COMMENT ON COLUMN oa_iam.grant_record.role_id IS '被授予的角色主键';
COMMENT ON COLUMN oa_iam.grant_record.scope_type IS '数据范围类型：ALL、ORG_AND_SUB、ORG、SELF、CUSTOM 或 NONE';
COMMENT ON COLUMN oa_iam.grant_record.scope_org_ids IS 'CUSTOM 数据范围包含的组织主键 JSON 数组';
COMMENT ON COLUMN oa_iam.grant_record.include_descendants IS '组织主体授权是否向下覆盖子组织成员';
COMMENT ON COLUMN oa_iam.grant_record.grant_type IS '授权类型：PERMANENT、TEMPORARY 或 DELEGATED';
COMMENT ON COLUMN oa_iam.grant_record.valid_from IS '授权生效时间';
COMMENT ON COLUMN oa_iam.grant_record.valid_to IS '授权到期时间，为空表示永久；判权按时间窗过滤';
COMMENT ON COLUMN oa_iam.grant_record.source IS '授权来源：MANUAL、RULE、SYNC 或 APPROVAL';
COMMENT ON COLUMN oa_iam.grant_record.approval_instance_id IS '产生该授权的审批实例业务标识';
COMMENT ON COLUMN oa_iam.grant_record.reason IS '授权原因';
COMMENT ON COLUMN oa_iam.grant_record.granted_by IS '授权人的 Casdoor sub';
COMMENT ON COLUMN oa_iam.grant_record.granted_at IS '授权创建时间';
COMMENT ON COLUMN oa_iam.grant_record.revoked_at IS '撤销时间，为空表示未撤销';
COMMENT ON COLUMN oa_iam.grant_record.revoked_by IS '撤销人的 Casdoor sub';
COMMENT ON COLUMN oa_iam.grant_record.revoke_reason IS '撤销原因';

COMMENT ON TABLE oa_iam.delegation IS '用户委托代理配置，控制待办按流程或角色转交';
COMMENT ON COLUMN oa_iam.delegation.id IS '委托记录主键';
COMMENT ON COLUMN oa_iam.delegation.tenant_id IS '租户标识，当前默认租户为 1';
COMMENT ON COLUMN oa_iam.delegation.delegator_user_id IS '委托人的 Casdoor sub';
COMMENT ON COLUMN oa_iam.delegation.delegatee_user_id IS '受托人的 Casdoor sub';
COMMENT ON COLUMN oa_iam.delegation.scope IS '委托范围：ALL_TODO、BY_PROCESS_KEY 或 BY_ROLE';
COMMENT ON COLUMN oa_iam.delegation.process_keys IS 'BY_PROCESS_KEY 范围包含的流程定义键 JSON 数组';
COMMENT ON COLUMN oa_iam.delegation.role_ids IS 'BY_ROLE 范围包含的角色主键 JSON 数组';
COMMENT ON COLUMN oa_iam.delegation.valid_from IS '委托生效时间';
COMMENT ON COLUMN oa_iam.delegation.valid_to IS '委托到期时间';
COMMENT ON COLUMN oa_iam.delegation.reason IS '委托原因';
COMMENT ON COLUMN oa_iam.delegation.status IS '委托状态：ACTIVE、EXPIRED 或 REVOKED';
COMMENT ON COLUMN oa_iam.delegation.created_by IS '创建人的 Casdoor sub';
COMMENT ON COLUMN oa_iam.delegation.created_at IS '创建时间';

COMMENT ON TABLE oa_iam.permission_condition IS '角色权限的受限 ABAC 条件定义表';
COMMENT ON COLUMN oa_iam.permission_condition.id IS 'ABAC 条件主键';
COMMENT ON COLUMN oa_iam.permission_condition.role_id IS '条件所属角色主键';
COMMENT ON COLUMN oa_iam.permission_condition.permission_id IS '条件约束的权限点主键';
COMMENT ON COLUMN oa_iam.permission_condition.expression IS '受限 SpEL 表达式，只允许白名单上下文和运算符';
COMMENT ON COLUMN oa_iam.permission_condition.description IS '条件的业务说明';
COMMENT ON COLUMN oa_iam.permission_condition.created_at IS '创建时间';
COMMENT ON COLUMN oa_iam.permission_condition.tenant_id IS '租户标识，当前默认租户为 1';
COMMENT ON COLUMN oa_iam.permission_condition.enabled IS '条件是否启用';
COMMENT ON COLUMN oa_iam.permission_condition.created_by IS '创建人的 Casdoor sub';
COMMENT ON COLUMN oa_iam.permission_condition.updated_by IS '最后修改人的 Casdoor sub';
COMMENT ON COLUMN oa_iam.permission_condition.updated_at IS '最后修改时间';
COMMENT ON COLUMN oa_iam.permission_condition.deleted_at IS '软删除时间，为空表示未删除';

COMMENT ON TABLE oa_iam.perm_epoch IS '全局权限纪元单行表，是跨节点权限缓存失效的权威版本';
COMMENT ON COLUMN oa_iam.perm_epoch.id IS '固定为 1 的单行主键';
COMMENT ON COLUMN oa_iam.perm_epoch.epoch IS '当前全局权限纪元';
COMMENT ON COLUMN oa_iam.perm_epoch.updated_at IS '纪元最后推进时间';

COMMENT ON TABLE oa_iam.perm_user_version IS '用户级权限版本表，用于只失效受影响用户的权限快照';
COMMENT ON COLUMN oa_iam.perm_user_version.user_id IS '用户 Casdoor sub';
COMMENT ON COLUMN oa_iam.perm_user_version.version IS '用户当前权限版本号';
COMMENT ON COLUMN oa_iam.perm_user_version.updated_at IS '版本最后推进时间';

COMMENT ON TABLE oa_iam.user_group IS 'OA 内部显式成员用户组，不复用 Casdoor group';
COMMENT ON COLUMN oa_iam.user_group.id IS '用户组主键';
COMMENT ON COLUMN oa_iam.user_group.tenant_id IS '租户标识，当前默认租户为 1';
COMMENT ON COLUMN oa_iam.user_group.code IS '租户内唯一的用户组编码';
COMMENT ON COLUMN oa_iam.user_group.name IS '用户组名称';
COMMENT ON COLUMN oa_iam.user_group.description IS '用户组说明';
COMMENT ON COLUMN oa_iam.user_group.status IS '用户组状态：ACTIVE 或 DISABLED';
COMMENT ON COLUMN oa_iam.user_group.created_by IS '创建人的 Casdoor sub';
COMMENT ON COLUMN oa_iam.user_group.created_at IS '创建时间';
COMMENT ON COLUMN oa_iam.user_group.updated_by IS '最后修改人的 Casdoor sub';
COMMENT ON COLUMN oa_iam.user_group.updated_at IS '最后修改时间';

COMMENT ON TABLE oa_iam.user_group_member IS '用户组成员时间窗记录，支持到期和主动撤销';
COMMENT ON COLUMN oa_iam.user_group_member.id IS '成员记录主键';
COMMENT ON COLUMN oa_iam.user_group_member.tenant_id IS '租户标识，当前默认租户为 1';
COMMENT ON COLUMN oa_iam.user_group_member.group_id IS '所属用户组主键';
COMMENT ON COLUMN oa_iam.user_group_member.user_id IS '成员用户的 Casdoor sub';
COMMENT ON COLUMN oa_iam.user_group_member.valid_from IS '成员关系生效时间';
COMMENT ON COLUMN oa_iam.user_group_member.valid_to IS '成员关系到期时间，为空表示持续有效';
COMMENT ON COLUMN oa_iam.user_group_member.created_by IS '创建人的 Casdoor sub';
COMMENT ON COLUMN oa_iam.user_group_member.created_at IS '创建时间';
COMMENT ON COLUMN oa_iam.user_group_member.revoked_by IS '撤销人的 Casdoor sub';
COMMENT ON COLUMN oa_iam.user_group_member.revoked_at IS '成员关系撤销时间，为空表示未撤销';
