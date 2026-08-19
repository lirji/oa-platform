package com.lrj.oa.flow.application;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 单据编号生成（号段模式）。
 *
 * <p>一次从数据库领 1000 个号缓存在本地，用完再领。
 * <b>不用 {@code SELECT ... FOR UPDATE}</b>：那会把所有单据提交串行化到一行上，
 * 万人级早高峰集中提单时立刻成为瓶颈。号段模式把数据库交互降到每 1000 单一次。
 *
 * <p>代价是重启会浪费一段号（号码不连续）。单据号只需唯一可读，不需要连续 —— 这个代价可以接受。
 */
@Component
public class SegmentIdGenerator {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final SegmentAllocator allocator;
    private final Map<String, Segment> segments = new ConcurrentHashMap<>();

    public SegmentIdGenerator(SegmentAllocator allocator) { this.allocator = allocator; }

    /** 生成形如 {@code LEAVE20260819000123} 的单号。 */
    public String next(String bizTag) {
        long seq = nextSeq(bizTag);
        return "%s%s%06d".formatted(bizTag, LocalDate.now().format(DAY), seq % 1_000_000);
    }

    private long nextSeq(String bizTag) {
        Segment seg = segments.computeIfAbsent(bizTag, t -> new Segment(0, -1));
        synchronized (seg) {
            if (seg.cursor.get() >= seg.max) {
                Map<String, Object> next = allocator.allocate(bizTag);
                long max = ((Number) next.get("max_id")).longValue();
                int step = ((Number) next.get("step")).intValue();
                seg.cursor.set(max - step);
                seg.max = max;
            }
            return seg.cursor.incrementAndGet();
        }
    }

    private static final class Segment {
        final AtomicLong cursor;
        volatile long max;
        Segment(long start, long max) { this.cursor = new AtomicLong(start); this.max = max; }
    }

}
