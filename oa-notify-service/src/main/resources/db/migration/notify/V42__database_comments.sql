-- 通知域数据字典注释，并把父表注释复制到当前物理分区。

COMMENT ON TABLE oa_notify.recipient IS '通知域用户与稠密整数序号的映射，供 RoaringBitmap 使用';
COMMENT ON COLUMN oa_notify.recipient.seq IS '通知域分配的稠密整数序号';
COMMENT ON COLUMN oa_notify.recipient.tenant_id IS '租户标识，当前默认租户为 1';
COMMENT ON COLUMN oa_notify.recipient.user_id IS '收件人的 Casdoor sub';
COMMENT ON COLUMN oa_notify.recipient.created_at IS '首次进入通知域的时间';

COMMENT ON TABLE oa_notify.notification IS '按创建时间按月分区的用户站内信';
COMMENT ON COLUMN oa_notify.notification.id IS '站内信主键，与 created_at 共同组成分区主键';
COMMENT ON COLUMN oa_notify.notification.tenant_id IS '租户标识，当前默认租户为 1';
COMMENT ON COLUMN oa_notify.notification.user_id IS '收件人的 Casdoor sub';
COMMENT ON COLUMN oa_notify.notification.category IS '通知类别：TODO、ANNOUNCEMENT 或 SYSTEM';
COMMENT ON COLUMN oa_notify.notification.title IS '通知标题';
COMMENT ON COLUMN oa_notify.notification.content IS '通知正文';
COMMENT ON COLUMN oa_notify.notification.biz_type IS '关联业务类型';
COMMENT ON COLUMN oa_notify.notification.biz_id IS '关联业务标识';
COMMENT ON COLUMN oa_notify.notification.link IS '前端点击通知后的跳转地址';
COMMENT ON COLUMN oa_notify.notification.dedup_key IS '同一业务事件投递的幂等键';
COMMENT ON COLUMN oa_notify.notification.read_at IS '用户阅读时间，为空表示未读';
COMMENT ON COLUMN oa_notify.notification.created_at IS '通知创建时间，也是月分区键';

COMMENT ON TABLE oa_notify.notification_dedup IS '不分区的站内信幂等键表，保证同一事件只生成一次用户通知';
COMMENT ON COLUMN oa_notify.notification_dedup.tenant_id IS '租户标识，当前默认租户为 1';
COMMENT ON COLUMN oa_notify.notification_dedup.user_id IS '收件人的 Casdoor sub';
COMMENT ON COLUMN oa_notify.notification_dedup.dedup_key IS '业务事件幂等键';
COMMENT ON COLUMN oa_notify.notification_dedup.created_at IS '幂等键首次登记时间，也是清理依据';

COMMENT ON TABLE oa_notify.announcement IS '公告内容、发布人、受众规模和发布状态';
COMMENT ON COLUMN oa_notify.announcement.id IS '公告主键';
COMMENT ON COLUMN oa_notify.announcement.tenant_id IS '租户标识，当前默认租户为 1';
COMMENT ON COLUMN oa_notify.announcement.title IS '公告标题';
COMMENT ON COLUMN oa_notify.announcement.content IS '公告正文';
COMMENT ON COLUMN oa_notify.announcement.publisher_id IS '发布人的 Casdoor sub';
COMMENT ON COLUMN oa_notify.announcement.publisher_name IS '发布人姓名快照';
COMMENT ON COLUMN oa_notify.announcement.audience_type IS '受众类型，当前默认 EXPLICIT';
COMMENT ON COLUMN oa_notify.announcement.audience_count IS '发布时计算出的受众人数';
COMMENT ON COLUMN oa_notify.announcement.status IS '公告状态：PUBLISHED 或 REVOKED';
COMMENT ON COLUMN oa_notify.announcement.published_at IS '发布时间';
COMMENT ON COLUMN oa_notify.announcement.expire_at IS '公告失效时间';

COMMENT ON TABLE oa_notify.announcement_read IS '公告已读用户的 RoaringBitmap 聚合回执';
COMMENT ON COLUMN oa_notify.announcement_read.announcement_id IS '公告主键';
COMMENT ON COLUMN oa_notify.announcement_read.bitmap IS '已读用户稠密序号的 RoaringBitmap 序列化数据';
COMMENT ON COLUMN oa_notify.announcement_read.read_count IS '位图对应的已读人数';
COMMENT ON COLUMN oa_notify.announcement_read.updated_at IS '位图最后合并时间';

COMMENT ON TABLE oa_notify.announcement_audience IS '公告应接收用户的 RoaringBitmap 受众快照';
COMMENT ON COLUMN oa_notify.announcement_audience.announcement_id IS '公告主键';
COMMENT ON COLUMN oa_notify.announcement_audience.bitmap IS '目标用户稠密序号的 RoaringBitmap 序列化数据';
COMMENT ON COLUMN oa_notify.announcement_audience.updated_at IS '受众位图最后修改时间';

COMMENT ON TABLE oa_notify.channel_log IS '邮件、短信、企微和飞书等非站内渠道的投递日志';
COMMENT ON COLUMN oa_notify.channel_log.id IS '渠道投递日志主键';
COMMENT ON COLUMN oa_notify.channel_log.tenant_id IS '租户标识，当前默认租户为 1';
COMMENT ON COLUMN oa_notify.channel_log.channel IS '投递渠道：EMAIL、SMS、WECOM 或 FEISHU';
COMMENT ON COLUMN oa_notify.channel_log.user_id IS '目标用户的 Casdoor sub';
COMMENT ON COLUMN oa_notify.channel_log.title IS '投递消息标题';
COMMENT ON COLUMN oa_notify.channel_log.status IS '投递状态：SENT、FAILED 或 SKIPPED';
COMMENT ON COLUMN oa_notify.channel_log.error IS '投递失败原因';
COMMENT ON COLUMN oa_notify.channel_log.created_at IS '投递尝试时间';

DO $$
DECLARE
    parent_oid oid := 'oa_notify.notification'::regclass;
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
