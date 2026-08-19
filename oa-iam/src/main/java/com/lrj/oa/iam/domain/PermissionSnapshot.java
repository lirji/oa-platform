package com.lrj.oa.iam.domain;

import com.lrj.oa.security.model.DataScopeRule;
import com.lrj.oa.security.model.DataScopeType;
import org.roaringbitmap.RoaringBitmap;

import java.util.*;

/**
 * 一个用户的权限快照 —— 判权热路径上唯一被读的东西。
 *
 * <p>权限点用 {@link RoaringBitmap} 存：800 个权限点压缩后不到 1 KB，
 * 单份快照 2–4 KB，一万用户全驻内存也只有约 30 MB，判定是 O(1) 位图查。
 *
 * <p><b>{@code expireAt} 不只是 TTL</b>：它取 TTL 与"最近一条临时授权到期时刻"的较小值。
 * 少了这一步，一条 30 秒后过期的 JIT 提权会被 5 分钟 TTL 的快照继续放行 ——
 * 那是一个安静的提权漏洞。
 */
public final class PermissionSnapshot {

    private final String userId;
    private final long epoch;
    private final long userVersion;
    private final RoaringBitmap permBits;
    private final Set<String> permCodes;
    private final RoaringBitmap elevatedBits;
    private final DataScopeRule mergedScope;
    private final Map<String, DataScopeRule> moduleScope;
    private final Set<String> delegators;
    private final long builtAt;
    private final long expireAt;

    private PermissionSnapshot(Builder b) {
        this.userId = b.userId;
        this.epoch = b.epoch;
        this.userVersion = b.userVersion;
        this.permBits = b.permBits;
        this.permCodes = Set.copyOf(b.permCodes);
        this.elevatedBits = b.elevatedBits;
        this.mergedScope = b.mergedScope;
        this.moduleScope = Map.copyOf(b.moduleScope);
        this.delegators = Set.copyOf(b.delegators);
        this.builtAt = b.builtAt;
        this.expireAt = b.expireAt;
    }

    // ── 热路径查询 ───────────────────────────────────────
    public boolean has(int permId) { return permBits.contains(permId); }
    public boolean isElevated(int permId) { return elevatedBits.contains(permId); }

    public DataScopeRule scopeOf(String module) {
        if (module == null || module.isBlank()) return mergedScope;
        return moduleScope.getOrDefault(module, mergedScope);
    }

    public boolean expired(long nowMs) { return nowMs >= expireAt; }

    // ── 访问器 ──────────────────────────────────────────
    public String userId() { return userId; }
    public long epoch() { return epoch; }
    public long userVersion() { return userVersion; }
    public Set<String> permCodes() { return permCodes; }
    public RoaringBitmap permBits() { return permBits; }
    public RoaringBitmap elevatedBits() { return elevatedBits; }
    public DataScopeRule mergedScope() { return mergedScope; }
    public Map<String, DataScopeRule> moduleScope() { return moduleScope; }
    public Set<String> delegators() { return delegators; }
    public long builtAt() { return builtAt; }
    public long expireAt() { return expireAt; }
    public int permCount() { return permBits.getCardinality(); }

    public static Builder builder(String userId) { return new Builder(userId); }

    /**
     * 合并两条数据范围规则，取<b>更宽</b>的那条；宽度相同则并集。
     *
     * <p>为什么是取宽不是取窄：一个人可能通过多条授权拿到同一模块的不同范围
     * （主岗给了本部门、兼岗给了另一个部门），他理应能看到两边的并集。
     * 取窄会让兼岗形同虚设。
     */
    public static DataScopeRule widen(DataScopeRule a, DataScopeRule b) {
        if (a == null) return b;
        if (b == null) return a;
        if (a.type().width() > b.type().width()) return a;
        if (b.type().width() > a.type().width()) return b;
        // 同宽 → 并集
        LinkedHashSet<String> prefixes = new LinkedHashSet<>(a.pathPrefixes());
        prefixes.addAll(b.pathPrefixes());
        LinkedHashSet<Long> orgIds = new LinkedHashSet<>(a.orgIds());
        orgIds.addAll(b.orgIds());
        return new DataScopeRule(a.type(), List.copyOf(prefixes), Set.copyOf(orgIds),
                a.selfUserId() != null ? a.selfUserId() : b.selfUserId());
    }

    public static final class Builder {
        private final String userId;
        private long epoch;
        private long userVersion;
        private final RoaringBitmap permBits = new RoaringBitmap();
        private final Set<String> permCodes = new HashSet<>();
        private final RoaringBitmap elevatedBits = new RoaringBitmap();
        private DataScopeRule mergedScope = DataScopeRule.none();
        private final Map<String, DataScopeRule> moduleScope = new HashMap<>();
        private Set<String> delegators = Set.of();
        private long builtAt = System.currentTimeMillis();
        private long expireAt = Long.MAX_VALUE;

        private Builder(String userId) { this.userId = userId; }

        public Builder epoch(long v) { this.epoch = v; return this; }
        public Builder userVersion(long v) { this.userVersion = v; return this; }

        public Builder addPerm(int permId, String code) {
            permBits.add(permId);
            if (code != null) permCodes.add(code);
            return this;
        }

        public Builder addElevated(int permId) { elevatedBits.add(permId); return this; }

        public Builder scope(String module, DataScopeRule rule) {
            mergedScope = widen(mergedScope, rule);
            if (module != null && !module.isBlank()) {
                moduleScope.merge(module, rule, PermissionSnapshot::widen);
            }
            return this;
        }

        public Builder delegators(Collection<String> v) { this.delegators = Set.copyOf(v); return this; }
        public Builder builtAt(long v) { this.builtAt = v; return this; }
        public Builder expireAt(long v) { this.expireAt = v; return this; }

        /** 直接装配（反序列化用），跳过增量累加。 */
        public Builder rawBits(RoaringBitmap perms, RoaringBitmap elevated, Collection<String> codes) {
            permBits.or(perms);
            elevatedBits.or(elevated);
            permCodes.addAll(codes);
            return this;
        }

        public Builder rawScope(DataScopeRule merged, Map<String, DataScopeRule> perModule) {
            this.mergedScope = merged;
            this.moduleScope.putAll(perModule);
            return this;
        }

        public PermissionSnapshot build() { return new PermissionSnapshot(this); }
    }

    /** 无任何权限的空快照。未入职账号、或找不到员工记录时用它 —— 安全默认值是"什么都不能做"。 */
    public static PermissionSnapshot empty(String userId, long epoch, long userVersion, long ttlMs) {
        return builder(userId).epoch(epoch).userVersion(userVersion)
                .scope(null, DataScopeRule.none())
                .expireAt(System.currentTimeMillis() + ttlMs)
                .build();
    }

    @Override
    public String toString() {
        return "PermissionSnapshot{user=%s epoch=%d ver=%d perms=%d elevated=%d scope=%s}"
                .formatted(userId, epoch, userVersion, permBits.getCardinality(),
                        elevatedBits.getCardinality(),
                        mergedScope == null ? DataScopeType.NONE : mergedScope.type());
    }
}
