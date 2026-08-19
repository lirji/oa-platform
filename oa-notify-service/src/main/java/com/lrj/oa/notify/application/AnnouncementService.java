package com.lrj.oa.notify.application;

import com.lrj.oa.common.api.ResultCode;
import com.lrj.oa.common.exception.BusinessException;
import com.lrj.oa.notify.NotifyProperties;
import com.lrj.oa.notify.api.dto.NotifyDtos;
import com.lrj.oa.notify.infrastructure.ws.SessionRegistry;
import org.roaringbitmap.RoaringBitmap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 公告。万人广播是这个服务的压力峰值场景（FINAL_PLAN §9.3）。
 *
 * <p><b>验收线</b>：10,000 人推送完成 &lt; 30s；已读回执存储 &lt; 100KB。
 */
@Service
public class AnnouncementService {

    private static final Logger log = LoggerFactory.getLogger(AnnouncementService.class);

    private final JdbcTemplate jdbc;
    private final RecipientIndex recipients;
    private final ReadReceiptStore receipts;
    private final SessionRegistry sessions;
    private final int batchSize;

    public AnnouncementService(JdbcTemplate jdbc, RecipientIndex recipients, ReadReceiptStore receipts,
                               SessionRegistry sessions, NotifyProperties props) {
        this.jdbc = jdbc;
        this.recipients = recipients;
        this.receipts = receipts;
        this.sessions = sessions;
        this.batchSize = props.getBroadcast().getBatchSize();
    }

    /**
     * 发布公告并广播。
     *
     * <p>受众位图在这里定死。之后即使有人入职/离职，这条公告的"应读人数"也不该变 ——
     * 已读率是对<b>发布当时</b>那批人的统计，跟着组织变动漂移的比率没有意义。
     */
    @Transactional
    public long publish(NotifyDtos.PublishAnnouncement cmd, String publisherId, String publisherName) {
        List<String> to = cmd.recipients();
        if (to == null || to.isEmpty()) {
            throw BusinessException.of(ResultCode.BAD_REQUEST, "公告必须指定收件人（受众由发布方按数据权限算好传入）");
        }
        long t0 = System.nanoTime();

        Long id = jdbc.queryForObject("""
                INSERT INTO oa_notify.announcement
                    (title, content, publisher_id, publisher_name, audience_type, audience_count, expire_at)
                VALUES (?, ?, ?, ?, 'EXPLICIT', ?, ?)
                RETURNING id
                """, Long.class, cmd.title(), cmd.content(), publisherId, publisherName, to.size(),
                cmd.expireAt());
        if (id == null) throw BusinessException.of(ResultCode.INTERNAL_ERROR, "公告写入失败");

        // 受众位图：一次性算好存下，之后"谁还没读"= 受众 ANDNOT 已读，纯位运算。
        RoaringBitmap audience = new RoaringBitmap();
        for (int seq : recipients.resolve(to)) audience.add(seq);
        receipts.saveAudience(id, audience);

        // 站内信落库：分批 batchUpdate。万人一次性 batch 会让单条 SQL 参数量爆掉，
        // 且失败时整批回滚——分批后失败只影响一批。
        // 这里不做 dedup 抢坑：公告 id 是刚生成的自增值，同一条公告不可能被重复发布，
        // 没有需要去重的重放场景。dedup_key 只是留给"按公告清理站内信"用。
        String link = "/announcements/" + id;
        for (int i = 0; i < to.size(); i += batchSize) {
            List<String> slice = to.subList(i, Math.min(to.size(), i + batchSize));
            jdbc.batchUpdate("""
                    INSERT INTO oa_notify.notification
                        (tenant_id, user_id, category, title, content, biz_type, biz_id, link, dedup_key)
                    VALUES (1, ?, 'ANNOUNCEMENT', ?, ?, 'ANNOUNCEMENT', ?, ?, ?)
                    """, slice.stream()
                    .map(u -> new Object[]{u, cmd.title(), cmd.content(), String.valueOf(id), link,
                            "ANN:" + id})
                    .toList());
        }
        log.info("公告 {} 已落库：受众 {} 人，耗时 {} ms", id, to.size(),
                (System.nanoTime() - t0) / 1_000_000);
        return id;
    }

    /**
     * 广播推送（长连）。<b>不在发布事务里</b>：见 NotifyService.pushAfterCommit 的理由。
     * 只推在线的人，离线的人靠"上线拉未读"补 —— 这也是广播能在 30 秒内跑完的原因：
     * 工作量是"在线人数"而不是"全员人数"。
     */
    public int broadcastPush(long announcementId, List<String> to, String title) {
        String payload = "{\"type\":\"ANNOUNCEMENT\",\"id\":" + announcementId
                + ",\"title\":\"" + title.replace("\"", "\\\"") + "\"}";
        int delivered = 0;
        for (String u : to) delivered += sessions.push(u, payload);
        return delivered;
    }

    /** 标记我已读这条公告。走位图缓冲，不写明细行。 */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void markRead(long announcementId, String userId) {
        receipts.markRead(announcementId, recipients.resolveOne(userId));
    }

    public boolean hasRead(long announcementId, String userId) {
        return receipts.readBitmap(announcementId).contains(recipients.resolveOne(userId));
    }

    /** 已读统计：受众 / 已读 / 未读 + 位图实际占用字节（验收要量这个数）。 */
    public NotifyDtos.ReadStats stats(long announcementId, int unreadSample) {
        receipts.flush();   // 统计要看最新数字，先把内存缓冲落下去
        RoaringBitmap audience = receipts.audienceBitmap(announcementId);
        RoaringBitmap read = receipts.readBitmap(announcementId);
        RoaringBitmap unread = RoaringBitmap.andNot(audience, read);

        List<Integer> sampleSeqs = new ArrayList<>();
        var it = unread.getIntIterator();
        while (it.hasNext() && sampleSeqs.size() < Math.max(0, unreadSample)) sampleSeqs.add(it.next());
        List<String> sample = new ArrayList<>(recipients.namesOf(sampleSeqs).values());

        return new NotifyDtos.ReadStats(announcementId, audience.getCardinality(),
                read.getCardinality(), unread.getCardinality(),
                receipts.storedBytes(announcementId), sample);
    }

    public List<NotifyDtos.AnnouncementView> list(String userId, int limit) {
        List<NotifyDtos.AnnouncementView> out = new ArrayList<>();
        jdbc.query("""
                SELECT id, title, content, publisher_id, publisher_name, audience_count,
                       status, published_at, expire_at
                  FROM oa_notify.announcement
                 WHERE status = 'PUBLISHED' AND (expire_at IS NULL OR expire_at > now())
                 ORDER BY published_at DESC LIMIT ?
                """, rs -> {
            long id = rs.getLong("id");
            out.add(new NotifyDtos.AnnouncementView(id, rs.getString("title"), rs.getString("content"),
                    rs.getString("publisher_id"), rs.getString("publisher_name"),
                    rs.getInt("audience_count"), rs.getString("status"),
                    rs.getObject("published_at", OffsetDateTime.class),
                    rs.getObject("expire_at", OffsetDateTime.class),
                    userId == null ? null : hasRead(id, userId)));
        }, Math.min(Math.max(limit, 1), 100));
        return out;
    }

    @Transactional
    public void revoke(long announcementId) {
        int n = jdbc.update("UPDATE oa_notify.announcement SET status = 'REVOKED'"
                + " WHERE id = ? AND status = 'PUBLISHED'", announcementId);
        if (n == 0) throw BusinessException.of(ResultCode.NOT_FOUND, "公告不存在或已撤回");
        // 撤回连带撤掉站内信：留着的话用户点进去会看到一条已撤回的公告，比看不到更困惑。
        jdbc.update("DELETE FROM oa_notify.notification WHERE biz_type = 'ANNOUNCEMENT' AND biz_id = ?",
                String.valueOf(announcementId));
    }
}
