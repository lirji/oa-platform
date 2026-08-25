-- 考勤域数据字典注释，并把父表注释复制到当前物理分区。

COMMENT ON TABLE oa_att.punch_record IS '按打卡时间按月分区的员工上下班打卡事实表';
COMMENT ON COLUMN oa_att.punch_record.id IS '打卡记录主键，与 punch_time 共同组成分区主键';
COMMENT ON COLUMN oa_att.punch_record.tenant_id IS '租户标识，当前默认租户为 1';
COMMENT ON COLUMN oa_att.punch_record.user_id IS '打卡用户的 Casdoor sub';
COMMENT ON COLUMN oa_att.punch_record.employee_id IS '打卡用户的员工内部主键';
COMMENT ON COLUMN oa_att.punch_record.punch_date IS '按业务时区计算的打卡日期';
COMMENT ON COLUMN oa_att.punch_record.punch_type IS '打卡类型：IN 上班或 OUT 下班';
COMMENT ON COLUMN oa_att.punch_record.punch_time IS '实际打卡时间，也是月分区键';
COMMENT ON COLUMN oa_att.punch_record.source IS '打卡来源：MOBILE、WEB、GATE 或 WIFI';
COMMENT ON COLUMN oa_att.punch_record.latitude IS '打卡位置纬度';
COMMENT ON COLUMN oa_att.punch_record.longitude IS '打卡位置经度';
COMMENT ON COLUMN oa_att.punch_record.device_id IS '打卡设备标识';
COMMENT ON COLUMN oa_att.punch_record.org_id IS '打卡发生时的组织主键快照';
COMMENT ON COLUMN oa_att.punch_record.org_path IS '打卡发生时的组织路径快照，用于数据权限和历史统计';
COMMENT ON COLUMN oa_att.punch_record.created_at IS '记录入库时间';

COMMENT ON TABLE oa_att.punch_dead_letter IS '批量打卡落库彻底失败后的死信记录，供人工补偿';
COMMENT ON COLUMN oa_att.punch_dead_letter.id IS '死信记录主键';
COMMENT ON COLUMN oa_att.punch_dead_letter.payload IS '无法落库的原始打卡载荷';
COMMENT ON COLUMN oa_att.punch_dead_letter.error IS '最后一次落库失败原因';
COMMENT ON COLUMN oa_att.punch_dead_letter.created_at IS '进入死信表的时间';

COMMENT ON TABLE oa_att.attendance_daily IS '员工每日考勤聚合结果';
COMMENT ON COLUMN oa_att.attendance_daily.id IS '考勤日结主键';
COMMENT ON COLUMN oa_att.attendance_daily.tenant_id IS '租户标识，当前默认租户为 1';
COMMENT ON COLUMN oa_att.attendance_daily.user_id IS '员工的 Casdoor sub';
COMMENT ON COLUMN oa_att.attendance_daily.work_date IS '考勤工作日期';
COMMENT ON COLUMN oa_att.attendance_daily.first_in IS '当日首次上班打卡时间';
COMMENT ON COLUMN oa_att.attendance_daily.last_out IS '当日最后一次下班打卡时间';
COMMENT ON COLUMN oa_att.attendance_daily.work_minutes IS '当日计算出的工作分钟数';
COMMENT ON COLUMN oa_att.attendance_daily.status IS '日结状态：NORMAL、LATE、EARLY、ABSENT、MISSING 或 LEAVE';
COMMENT ON COLUMN oa_att.attendance_daily.org_id IS '考勤发生时的组织主键快照';
COMMENT ON COLUMN oa_att.attendance_daily.org_path IS '考勤发生时的组织路径快照，用于数据权限和部门统计';
COMMENT ON COLUMN oa_att.attendance_daily.computed_at IS '最近一次日结计算时间';

COMMENT ON TABLE oa_att.shift IS '考勤班次定义表';
COMMENT ON COLUMN oa_att.shift.id IS '班次主键';
COMMENT ON COLUMN oa_att.shift.code IS '唯一班次编码';
COMMENT ON COLUMN oa_att.shift.name IS '班次名称';
COMMENT ON COLUMN oa_att.shift.start_time IS '标准上班时间';
COMMENT ON COLUMN oa_att.shift.end_time IS '标准下班时间';
COMMENT ON COLUMN oa_att.shift.late_after_minutes IS '超过标准上班时间多少分钟判定迟到';
COMMENT ON COLUMN oa_att.shift.status IS '班次状态，默认 ACTIVE';

DO $$
DECLARE
    parent_oid oid := 'oa_att.punch_record'::regclass;
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
