-- 审计、字典与系统配置数据字典注释，并同步当前审计物理分区。

COMMENT ON TABLE oa_sys.audit_log IS '按创建时间按月分区的写操作、敏感读取和拒绝访问审计日志';
COMMENT ON COLUMN oa_sys.audit_log.id IS '审计记录主键，与 created_at 共同组成分区主键';
COMMENT ON COLUMN oa_sys.audit_log.tenant_id IS '租户标识，当前默认租户为 1';
COMMENT ON COLUMN oa_sys.audit_log.actor_id IS '实际操作人的 Casdoor sub';
COMMENT ON COLUMN oa_sys.audit_log.actor_name IS '操作人姓名快照';
COMMENT ON COLUMN oa_sys.audit_log.on_behalf_of IS '委托代理场景中被代表人的 Casdoor sub';
COMMENT ON COLUMN oa_sys.audit_log.action IS '业务动作编码，如 org.employee.transfer';
COMMENT ON COLUMN oa_sys.audit_log.module IS '动作所属业务模块';
COMMENT ON COLUMN oa_sys.audit_log.target_type IS '操作目标类型';
COMMENT ON COLUMN oa_sys.audit_log.target_id IS '操作目标业务标识';
COMMENT ON COLUMN oa_sys.audit_log.outcome IS '操作结果：SUCCESS、DENIED 或 FAILED';
COMMENT ON COLUMN oa_sys.audit_log.error IS '拒绝或失败原因摘要';
COMMENT ON COLUMN oa_sys.audit_log.client_ip IS '请求客户端 IP 地址';
COMMENT ON COLUMN oa_sys.audit_log.detail IS '脱敏后的结构化审计详情 JSON';
COMMENT ON COLUMN oa_sys.audit_log.org_id IS '操作发生时的组织主键快照';
COMMENT ON COLUMN oa_sys.audit_log.org_path IS '操作发生时的组织路径快照，用于数据权限过滤';
COMMENT ON COLUMN oa_sys.audit_log.created_at IS '操作发生时间，也是月分区键';

COMMENT ON TABLE oa_sys.dict IS '租户级业务字典项';
COMMENT ON COLUMN oa_sys.dict.id IS '字典项主键';
COMMENT ON COLUMN oa_sys.dict.tenant_id IS '租户标识，当前默认租户为 1';
COMMENT ON COLUMN oa_sys.dict.dict_type IS '字典类型编码';
COMMENT ON COLUMN oa_sys.dict.item_code IS '字典类型内唯一的字典项编码';
COMMENT ON COLUMN oa_sys.dict.item_name IS '字典项显示名称';
COMMENT ON COLUMN oa_sys.dict.sort_no IS '同类字典项展示顺序';
COMMENT ON COLUMN oa_sys.dict.enabled IS '字典项是否启用';

COMMENT ON TABLE oa_sys.sys_config IS '平台运行时系统配置键值表';
COMMENT ON COLUMN oa_sys.sys_config.config_key IS '全局唯一配置键';
COMMENT ON COLUMN oa_sys.sys_config.config_value IS '配置值文本';
COMMENT ON COLUMN oa_sys.sys_config.description IS '配置用途说明';
COMMENT ON COLUMN oa_sys.sys_config.updated_by IS '最后修改人的 Casdoor sub';
COMMENT ON COLUMN oa_sys.sys_config.updated_at IS '最后修改时间';

DO $$
DECLARE
    parent_oid oid := 'oa_sys.audit_log'::regclass;
    child_oid oid;
    partition_meta record;
    column_meta record;
BEGIN
    FOR partition_meta IN
        SELECT child_ns.nspname AS schema_name, child.relname AS table_name
          FROM pg_inherits inherited
          JOIN pg_class child ON child.oid = inherited.inhrelid
          JOIN pg_namespace child_ns ON child_ns.oid = child.relnamespace
         WHERE inherited.inhparent = parent_oid
    LOOP
        child_oid := to_regclass(format('%I.%I', partition_meta.schema_name, partition_meta.table_name));
        EXECUTE format(
            'COMMENT ON TABLE %I.%I IS %L',
            partition_meta.schema_name,
            partition_meta.table_name,
            obj_description(parent_oid, 'pg_class') || '；物理分区，数据范围由分区约束决定'
        );

        FOR column_meta IN
            SELECT child_attr.attname,
                   col_description(parent_oid, parent_attr.attnum) AS comment_text
              FROM pg_attribute parent_attr
              JOIN pg_attribute child_attr
                ON child_attr.attrelid = child_oid
               AND child_attr.attname = parent_attr.attname
               AND child_attr.attnum > 0
               AND NOT child_attr.attisdropped
             WHERE parent_attr.attrelid = parent_oid
               AND parent_attr.attnum > 0
               AND NOT parent_attr.attisdropped
               AND col_description(parent_oid, parent_attr.attnum) IS NOT NULL
        LOOP
            EXECUTE format(
                'COMMENT ON COLUMN %I.%I.%I IS %L',
                partition_meta.schema_name,
                partition_meta.table_name,
                column_meta.attname,
                column_meta.comment_text
            );
        END LOOP;
    END LOOP;
END $$;
