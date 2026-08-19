package com.lrj.oa.notify.application;

import org.roaringbitmap.RoaringBitmap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 已读回执的位图存储。
 *
 * <p><b>为什么是位图不是明细表</b>：一条全员公告若"每人一行"，万人就是 10,000 行；
 * 一年几百条公告 = 几百万行，只为回答"读了多少人"。RoaringBitmap 把它压成一个 bytea，
 * 万人稠密位图约 1.3KB（验收线 &lt; 100KB）。
 * 代价是丢掉"谁在什么时刻读的"——公告不需要，需要审计到人到秒的场景（合规签署）
 * 应另建明细表，<b>不要</b>复用这里。
 *
 * <p><b>为什么要在内存里缓冲</b>：全员公告发出后的几分钟里，上万人几乎同时点开。
 * 如果每次已读都去 {@code SELECT ... FOR UPDATE} 那一行再写回，
 * 所有人会排在同一把行锁上 —— 位图省下的空间，全赔在锁竞争里。
 * 所以已读先并到进程内的位图，由定时任务批量合并落库。
 *
 * <p><b>这个取舍的代价要说清楚</b>：进程崩溃会丢掉最后一个刷盘周期内的已读记录。
 * 丢一条已读的后果是"公告在他那儿又显示成未读"，用户再点一次即可，
 * 不涉及数据正确性或权限。用这个代价换掉一个必然的锁热点，是划算的；
 * 但同样的手法<b>不能</b>用在额度扣减、打卡这类丢了就说不清的地方。
 */
@Component
public class ReadReceiptStore {

    private static final Logger log = LoggerFactory.getLogger(ReadReceiptStore.class);

    private final JdbcTemplate jdbc;
    /** announcementId → 待合并的已读位图 */
    private final Map<Long, RoaringBitmap> pending = new ConcurrentHashMap<>();

    public ReadReceiptStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 标记已读（进内存缓冲，稍后批量落库）。 */
    public void markRead(long announcementId, int recipientSeq) {
        RoaringBitmap bm = pending.computeIfAbsent(announcementId, k -> new RoaringBitmap());
        // RoaringBitmap 不是线程安全的；这里的锁只保护一次纯内存的 add，不含任何 I/O，
        // 因此不会 pin 载体线程（对比 Phase 5 的 Caffeine.get 教训：那里锁里做了 JDBC）。
        synchronized (bm) {
            bm.add(recipientSeq);
        }
    }

    /** 立即把缓冲合并进库（冒烟与关机时用；平时由定时任务调）。 */
    public void flush() {
        if (pending.isEmpty()) return;
        for (Long annId : pending.keySet().toArray(new Long[0])) {
            RoaringBitmap delta = pending.remove(annId);
            if (delta == null || delta.isEmpty()) continue;
            try {
                mergeIntoDb(annId, delta);
            } catch (Exception e) {
                // 合并失败就把 delta 放回去，下一轮重试。位图的 or 是幂等的，重复合并无害。
                pending.merge(annId, delta, (a, b) -> { a.or(b); return a; });
                log.warn("已读位图合并失败 announcementId={}：{}", annId, e.toString());
            }
        }
    }

    @Scheduled(fixedDelayString = "${oa.notify.read-flush-ms:2000}")
    public void scheduledFlush() {
        flush();
    }

    private void mergeIntoDb(long annId, RoaringBitmap delta) {
        // 行锁只被刷盘线程持有，读者不参与竞争——这正是缓冲的意义。
        byte[] existing = jdbc.query(
                "SELECT bitmap FROM oa_notify.announcement_read WHERE announcement_id = ? FOR UPDATE",
                rs -> rs.next() ? rs.getBytes(1) : null, annId);
        RoaringBitmap merged = existing == null ? new RoaringBitmap() : deserialize(existing);
        merged.or(delta);
        byte[] bytes = serialize(merged);
        if (existing == null) {
            jdbc.update("""
                    INSERT INTO oa_notify.announcement_read(announcement_id, bitmap, read_count, updated_at)
                    VALUES (?, ?, ?, now())
                    ON CONFLICT (announcement_id) DO UPDATE SET bitmap = EXCLUDED.bitmap,
                        read_count = EXCLUDED.read_count, updated_at = now()
                    """, annId, bytes, merged.getCardinality());
        } else {
            jdbc.update("UPDATE oa_notify.announcement_read SET bitmap = ?, read_count = ?, updated_at = now()"
                    + " WHERE announcement_id = ?", bytes, merged.getCardinality(), annId);
        }
    }

    /** 读位图（已合并 DB 中的 + 内存里还没落盘的，否则刚点的已读会"看起来没生效"）。 */
    public RoaringBitmap readBitmap(long annId) {
        byte[] b = jdbc.query("SELECT bitmap FROM oa_notify.announcement_read WHERE announcement_id = ?",
                rs -> rs.next() ? rs.getBytes(1) : null, annId);
        RoaringBitmap bm = b == null ? new RoaringBitmap() : deserialize(b);
        RoaringBitmap buffered = pending.get(annId);
        if (buffered != null) {
            synchronized (buffered) { bm.or(buffered); }
        }
        return bm;
    }

    public void saveAudience(long annId, RoaringBitmap audience) {
        jdbc.update("""
                INSERT INTO oa_notify.announcement_audience(announcement_id, bitmap, updated_at)
                VALUES (?, ?, now())
                ON CONFLICT (announcement_id) DO UPDATE SET bitmap = EXCLUDED.bitmap, updated_at = now()
                """, annId, serialize(audience));
    }

    public RoaringBitmap audienceBitmap(long annId) {
        byte[] b = jdbc.query("SELECT bitmap FROM oa_notify.announcement_audience WHERE announcement_id = ?",
                rs -> rs.next() ? rs.getBytes(1) : null, annId);
        return b == null ? new RoaringBitmap() : deserialize(b);
    }

    /** 位图在库里占多少字节。验收要量这个数（< 100KB）。 */
    public int storedBytes(long annId) {
        Integer n = jdbc.query("SELECT octet_length(bitmap) FROM oa_notify.announcement_read WHERE announcement_id = ?",
                rs -> rs.next() ? rs.getInt(1) : 0, annId);
        return n == null ? 0 : n;
    }

    static byte[] serialize(RoaringBitmap bm) {
        // runOptimize 前后能差好几倍：稠密连续区间会被压成 run 编码。
        // 万人全读的位图不优化约 1.3KB，优化后只剩几十字节。
        bm.runOptimize();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (DataOutputStream dos = new DataOutputStream(out)) {
            bm.serialize(dos);
        } catch (IOException e) {
            throw new IllegalStateException("位图序列化失败", e);
        }
        return out.toByteArray();
    }

    static RoaringBitmap deserialize(byte[] bytes) {
        RoaringBitmap bm = new RoaringBitmap();
        try (DataInputStream dis = new DataInputStream(new ByteArrayInputStream(bytes))) {
            bm.deserialize(dis);
        } catch (IOException e) {
            throw new IllegalStateException("位图反序列化失败", e);
        }
        return bm;
    }
}
