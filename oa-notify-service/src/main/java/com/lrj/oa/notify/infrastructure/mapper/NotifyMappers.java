package com.lrj.oa.notify.infrastructure.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.OffsetDateTime;
import java.util.List;

/** 通知域 Mapper。必须放在 {@code ..infrastructure.mapper} 包下（@MapperScan 只扫这个通配）。 */
public final class NotifyMappers {

    private NotifyMappers() {}

    /** 站内信查询用的行对象。类型化 DTO，不用 Map。 */
    public static class NotificationRow {
        public Long id;
        public String category;
        public String title;
        public String content;
        public String bizType;
        public String bizId;
        public String link;
        public OffsetDateTime readAt;
        public OffsetDateTime createdAt;
    }

    @Mapper
    public interface NotificationMapper {

        @Select("""
                SELECT id, category, title, content, biz_type AS bizType, biz_id AS bizId, link,
                       read_at AS readAt, created_at AS createdAt
                  FROM oa_notify.notification
                 WHERE tenant_id = 1 AND user_id = #{userId}
                   AND (#{unreadOnly} = false OR read_at IS NULL)
                 ORDER BY created_at DESC
                 LIMIT #{limit}
                """)
        List<NotificationRow> selectByUser(@Param("userId") String userId,
                                           @Param("unreadOnly") boolean unreadOnly,
                                           @Param("limit") int limit);

        @Select("SELECT count(*) FROM oa_notify.notification WHERE tenant_id = 1 AND user_id = #{userId} AND read_at IS NULL")
        int countUnread(@Param("userId") String userId);

        /** 只能标记自己的。WHERE 里带 user_id 是防越权的最后一道，别指望上层一定过滤对。 */
        @Update("UPDATE oa_notify.notification SET read_at = now()"
                + " WHERE tenant_id = 1 AND id = #{id} AND user_id = #{userId} AND read_at IS NULL")
        int markRead(@Param("id") long id, @Param("userId") String userId);

        @Update("UPDATE oa_notify.notification SET read_at = now()"
                + " WHERE tenant_id = 1 AND user_id = #{userId} AND read_at IS NULL")
        int markAllRead(@Param("userId") String userId);
    }
}
