package com.lrj.oa.common.id;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 单据 / 公文文号编号生成（号段模式）。
 *
 * <p>一次从数据库领 1000 个号缓存在本地，用完再领。
 * <b>不用 {@code SELECT ... FOR UPDATE}</b>：那会把所有单据提交串行化到一行上，
 * 万人级早高峰集中提单时立刻成为瓶颈。号段模式把数据库交互降到每 1000 单一次。
 *
 * <p>代价是重启会浪费一段号（号码不连续）。单据号只需唯一可读，不需要连续 —— 可以接受。
 * 但**文号**（公文）通常要求年内连续，那种场景要用 {@link #nextSequential}，
 * 它每次都推进数据库，用性能换连续性 —— 这个取舍必须由调用方显式做出，不能默认。
 *
 * <p>★ 归属：本类属于 <b>oa-common</b>（FINAL_PLAN §7.1 把"号段生成器"划在这里）。
 * 早先误放在 oa-flow，导致 oa-doc 要用文号时只能跨业务模块依赖。
 */
@Component
public class SegmentIdGenerator {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter YEAR = DateTimeFormatter.ofPattern("yyyy");

    private final SegmentAllocator allocator;
    private final Map<String, Segment> segments = new ConcurrentHashMap<>();

    public SegmentIdGenerator(SegmentAllocator allocator) { this.allocator = allocator; }

    /** 生成形如 {@code LEAVE20260819000123} 的单号。 */
    public String next(String bizTag) {
        long seq = nextSeq(bizTag);
        return "%s%s%06d".formatted(bizTag, LocalDate.now().format(DAY), seq % 1_000_000);
    }

    /** 生成形如 {@code 京办发〔2026〕7 号} 的公文文号。year 内连续，不走本地缓存。 */
    public String nextDocNumber(String prefix, String bizTag) {
        long seq = allocator.allocateOne(bizTag);
        return "%s〔%s〕%d 号".formatted(prefix, LocalDate.now().format(YEAR), seq);
    }

    /** 严格连续的下一个序号（每次都推进数据库）。 */
    public long nextSequential(String bizTag) {
        return allocator.allocateOne(bizTag);
    }

    public long nextSeq(String bizTag) {
        Segment seg = segments.computeIfAbsent(bizTag, t -> new Segment(0, -1));
        // ★ 用 ReentrantLock 而不是 synchronized：锁里会调 allocator.allocate()，那是一次 JDBC 往返。
        //   JDK21 虚拟线程在 synchronized 里阻塞会 pin 载体线程（CLAUDE.md 的头号纪律，
        //   Phase 5 已经被 Caffeine 坑掉过 30 倍吞吐）。号段耗尽虽然千次才一次，
        //   但冷启动时多个 bizTag 同时首领号，正好是并发最高的时刻。
        seg.lock.lock();
        try {
            if (seg.cursor.get() >= seg.max) {
                Map<String, Object> next = allocator.allocate(bizTag);
                long max = ((Number) next.get("max_id")).longValue();
                int step = ((Number) next.get("step")).intValue();
                seg.cursor.set(max - step);
                seg.max = max;
            }
            return seg.cursor.incrementAndGet();
        } finally {
            seg.lock.unlock();
        }
    }

    private static final class Segment {
        final AtomicLong cursor;
        final ReentrantLock lock = new ReentrantLock();
        volatile long max;
        Segment(long start, long max) { this.cursor = new AtomicLong(start); this.max = max; }
    }
}
