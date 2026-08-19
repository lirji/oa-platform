package com.lrj.oa.notify.application;

import com.lrj.oa.notify.api.dto.NotifyDtos;
import com.lrj.oa.notify.infrastructure.channel.ChannelDispatcher;
import com.lrj.oa.notify.infrastructure.mapper.NotifyMappers;
import com.lrj.oa.notify.infrastructure.ws.SessionRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 站内信。
 *
 * <p><b>送达模型</b>：落库是唯一的送达保证，长连推送与外部渠道都只是加速。
 * 人不在线不是失败 —— 下次上线拉未读即可。把"推送成功"当成"送达"是长连系统最常见的错觉。
 */
@Service
public class NotifyService {

    private static final Logger log = LoggerFactory.getLogger(NotifyService.class);

    private final JdbcTemplate jdbc;
    private final NotifyMappers.NotificationMapper mapper;
    private final SessionRegistry sessions;
    private final ChannelDispatcher channels;
    private final ObjectMapper json = new ObjectMapper();

    public NotifyService(JdbcTemplate jdbc, NotifyMappers.NotificationMapper mapper,
                         SessionRegistry sessions, ChannelDispatcher channels) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.sessions = sessions;
        this.channels = channels;
    }

    /**
     * 批量发站内信。
     *
     * @return 实际落库条数（幂等键命中的会少于收件人数）
     */
    @Transactional
    public int send(NotifyDtos.SendNotification cmd) {
        if (cmd.userIds() == null || cmd.userIds().isEmpty()) return 0;
        String category = cmd.category() == null ? "SYSTEM" : cmd.category();

        // 幂等：先抢 dedup 表的坑，抢到的人才真的插消息。
        // ★ 不能把唯一约束直接放在 notification 上 —— 它是分区表，PG 要求唯一索引必须
        //   包含分区键 created_at，一旦包含，"同一时刻才算重复"就等于不去重（见 V41 注释）。
        List<String> targets = cmd.userIds();
        if (cmd.dedupKey() != null && !cmd.dedupKey().isBlank()) {
            List<String> claimed = new ArrayList<>();
            for (String u : cmd.userIds()) {
                int n = jdbc.update("INSERT INTO oa_notify.notification_dedup(tenant_id, user_id, dedup_key)"
                        + " VALUES (1, ?, ?) ON CONFLICT DO NOTHING", u, cmd.dedupKey());
                if (n > 0) claimed.add(u);
            }
            targets = claimed;
            if (targets.isEmpty()) return 0;
        }

        List<Object[]> batch = targets.stream()
                .map(u -> new Object[]{u, category, cmd.title(), cmd.content(),
                        cmd.bizType(), cmd.bizId(), cmd.link(), cmd.dedupKey()})
                .toList();
        int[] r = jdbc.batchUpdate("""
                INSERT INTO oa_notify.notification
                    (tenant_id, user_id, category, title, content, biz_type, biz_id, link, dedup_key)
                VALUES (1, ?, ?, ?, ?, ?, ?, ?, ?)
                """, batch);
        int inserted = 0;
        for (int i : r) if (i > 0) inserted += i;
        return inserted;
    }

    /**
     * 落库之后的推送。<b>刻意与 {@link #send} 分开、且在事务之外调用</b>：
     * 在事务里推送，接收端可能先收到通知、再去查库却查不到（还没提交）——
     * 一个只在高并发下偶发、极难复现的"点开是空白"。
     */
    public void pushAfterCommit(NotifyDtos.SendNotification cmd) {
        String payload;
        try {
            payload = json.writeValueAsString(Map.of(
                    "type", "NOTIFICATION", "category", cmd.category() == null ? "SYSTEM" : cmd.category(),
                    "title", cmd.title() == null ? "" : cmd.title(),
                    "bizType", cmd.bizType() == null ? "" : cmd.bizType()));
        } catch (Exception e) {
            log.warn("推送载荷序列化失败：{}", e.toString());
            return;
        }
        for (String u : cmd.userIds()) {
            sessions.push(u, payload);
            channels.dispatch(u, cmd.title(), cmd.content());
        }
    }

    public List<NotifyDtos.NotificationView> list(String userId, boolean unreadOnly, int limit) {
        return mapper.selectByUser(userId, unreadOnly, Math.min(Math.max(limit, 1), 200)).stream()
                .map(r -> new NotifyDtos.NotificationView(r.id, r.category, r.title, r.content,
                        r.bizType, r.bizId, r.link, r.readAt != null, r.createdAt))
                .toList();
    }

    public int unreadCount(String userId) {
        return mapper.countUnread(userId);
    }

    public boolean markRead(long id, String userId) {
        return mapper.markRead(id, userId) > 0;
    }

    public int markAllRead(String userId) {
        return mapper.markAllRead(userId);
    }
}
