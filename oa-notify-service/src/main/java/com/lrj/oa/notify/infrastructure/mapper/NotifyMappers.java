package com.lrj.oa.notify.infrastructure.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Collection;

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

    public static class RecipientScopeRow {
        public String userId;
        public Long orgId;
        public String orgPath;
    }

    @Mapper
    public interface NotificationMapper {

        @Select("""
                <script>
                SELECT e.user_id AS userId, a.org_unit_id AS orgId, o.path AS orgPath
                  FROM oa_org.employee e
                  LEFT JOIN oa_org.employee_org_assignment a
                    ON a.employee_id=e.id AND a.assignment_type='PRIMARY' AND a.valid_to IS NULL
                  LEFT JOIN oa_org.org_unit o ON o.tenant_id=e.tenant_id AND o.id=a.org_unit_id
                 WHERE e.tenant_id=#{tenantId} AND e.status&lt;&gt;'LEFT' AND e.user_id IN
                 <foreach item="id" collection="userIds" open="(" separator="," close=")">#{id}</foreach>
                </script>
                """)
        List<RecipientScopeRow> recipientScopes(@Param("tenantId") long tenantId,
                                                @Param("userIds") Collection<String> userIds);

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
