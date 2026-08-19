package com.lrj.oa.notify.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * userId（Casdoor sub，字符串）→ 稠密 int 序号 的双向索引。
 *
 * <p><b>为什么需要它</b>：已读回执用 RoaringBitmap 存，位图的下标必须是 int。
 * 系统的主体标识却是 UUID 字符串（ADR：主体统一用 Casdoor sub）。
 * 拿 {@code employee.id} 当下标是不行的 —— 那是 oa_org 的东西，跨 schema 访问被明令禁止，
 * 而且通知域不该因为组织域改了主键策略就跟着改。所以通知域维护自己的一份映射。
 *
 * <p><b>稠密性很重要</b>：RoaringBitmap 对稠密的小整数集合压缩得最好（万人约 1.3KB）。
 * 如果下标稀疏（比如直接用 hash），同样一万人可能膨胀到几十 KB。自增序号天然稠密。
 */
@Component
public class RecipientIndex {

    private static final Logger log = LoggerFactory.getLogger(RecipientIndex.class);

    private final JdbcTemplate jdbc;
    /** 进程内缓存。映射一旦分配就永不改变，因此可以无脑缓存、无需失效。 */
    private final Map<String, Integer> toSeq = new ConcurrentHashMap<>();
    private final Map<Integer, String> toUser = new ConcurrentHashMap<>();

    public RecipientIndex(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 批量取序号，缺失的当场分配。
     *
     * <p>一次性 upsert 全部再一次性查回，而不是逐个 {@code SELECT 不存在则 INSERT} ——
     * 万人公告下后者是一万次往返，且并发下必然撞唯一约束。
     *
     * @return 与入参同序的序号列表
     */
    @Transactional
    public List<Integer> resolve(List<String> userIds) {
        List<String> missing = userIds.stream().distinct().filter(u -> !toSeq.containsKey(u)).toList();
        if (!missing.isEmpty()) {
            // ON CONFLICT DO NOTHING：并发插同一个人时后到的静默跳过，不报错也不覆盖序号。
            jdbc.batchUpdate("INSERT INTO oa_notify.recipient(tenant_id, user_id) VALUES (1, ?) ON CONFLICT DO NOTHING",
                    missing.stream().map(u -> new Object[]{u}).toList());
            loadInto(missing);
        }
        List<Integer> out = new ArrayList<>(userIds.size());
        for (String u : userIds) {
            Integer s = toSeq.get(u);
            if (s == null) {
                // 理论上不该发生（刚 upsert 过）。宁可显式失败也不要往位图里写一个错的下标——
                // 写错下标 = 把"已读"记到别人头上，而且事后无法分辨。
                throw new IllegalStateException("收件人序号分配失败: " + u);
            }
            out.add(s);
        }
        return out;
    }

    public int resolveOne(String userId) {
        return resolve(List.of(userId)).get(0);
    }

    /** 反查：位图里的下标 → userId。用于"谁还没读"这类需要落到人的查询。 */
    public Map<Integer, String> namesOf(List<Integer> seqs) {
        List<Integer> unknown = seqs.stream().distinct().filter(s -> !toUser.containsKey(s)).toList();
        if (!unknown.isEmpty()) {
            String in = String.join(",", unknown.stream().map(String::valueOf).toList());
            jdbc.query("SELECT seq, user_id FROM oa_notify.recipient WHERE seq IN (" + in + ")", rs -> {
                cache(rs.getString("user_id"), rs.getInt("seq"));
            });
        }
        Map<Integer, String> out = new LinkedHashMap<>();
        for (Integer s : seqs) {
            String u = toUser.get(s);
            if (u != null) out.put(s, u);
        }
        return out;
    }

    private void loadInto(List<String> userIds) {
        String placeholders = String.join(",", userIds.stream().map(u -> "?").toList());
        jdbc.query("SELECT seq, user_id FROM oa_notify.recipient WHERE tenant_id = 1 AND user_id IN (" + placeholders + ")",
                userIds.toArray(), rs -> {
                    cache(rs.getString("user_id"), rs.getInt("seq"));
                });
    }

    private void cache(String userId, int seq) {
        toSeq.put(userId, seq);
        toUser.put(seq, userId);
    }

    public int cachedSize() { return toSeq.size(); }
}
