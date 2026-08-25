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
    private final Map<Integer, DataScopeRule> permissionScope;
    private final Map<Integer, Map<Long, DataScopeRule>> permissionRoleScope;
    private final Set<String> delegators;
    private final boolean abacEnabled;
    private final RoaringBitmap abacUnconditionalBits;
    private final Map<Integer, Set<Long>> abacUnconditionalRoles;
    private final Map<Integer, List<AbacBranch>> abacBranches;
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
        this.permissionScope = Map.copyOf(b.permissionScope);
        Map<Integer, Map<Long, DataScopeRule>> roleScopes = new HashMap<>();
        b.permissionRoleScope.forEach((permission, scopes) -> roleScopes.put(permission, Map.copyOf(scopes)));
        this.permissionRoleScope = Map.copyOf(roleScopes);
        this.delegators = Set.copyOf(b.delegators);
        this.abacEnabled = b.abacEnabled;
        this.abacUnconditionalBits = b.abacUnconditionalBits;
        Map<Integer, Set<Long>> unconditionalRoles = new HashMap<>();
        b.abacUnconditionalRoles.forEach((permission, roles) ->
                unconditionalRoles.put(permission, Set.copyOf(roles)));
        this.abacUnconditionalRoles = Map.copyOf(unconditionalRoles);
        Map<Integer, List<AbacBranch>> branches = new HashMap<>();
        b.abacBranches.forEach((k, v) -> branches.put(k, List.copyOf(v)));
        this.abacBranches = Map.copyOf(branches);
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

    /** 权限点级数据范围；没有该权限或范围时安全地返回 NONE。 */
    public DataScopeRule scopeOfPermission(int permId) {
        if (permId < 0 || !has(permId)) return DataScopeRule.none();
        return permissionScope.getOrDefault(permId, DataScopeRule.none());
    }

    /** 只合并本次 ABAC 真正通过的角色来源；空集合安全返回 NONE。 */
    public DataScopeRule scopeOfPermissionRoles(int permId, Collection<Long> roleIds) {
        if (permId < 0 || roleIds == null || roleIds.isEmpty()) return DataScopeRule.none();
        Map<Long, DataScopeRule> byRole = permissionRoleScope.getOrDefault(permId, Map.of());
        DataScopeRule result = DataScopeRule.none();
        for (Long roleId : roleIds) result = widen(result, byRole.get(roleId));
        return result;
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
    public Map<Integer, DataScopeRule> permissionScope() { return permissionScope; }
    public Map<Integer, Map<Long, DataScopeRule>> permissionRoleScope() { return permissionRoleScope; }
    public Set<String> delegators() { return delegators; }
    public boolean abacEnabled() { return abacEnabled; }
    public boolean abacUnconditional(int permId) { return abacUnconditionalBits.contains(permId); }
    public RoaringBitmap abacUnconditionalBits() { return abacUnconditionalBits; }
    public Set<Long> abacUnconditionalRoles(int permId) {
        return abacUnconditionalRoles.getOrDefault(permId, Set.of());
    }
    public Map<Integer, Set<Long>> abacUnconditionalRoles() { return abacUnconditionalRoles; }
    public Map<Integer, List<AbacBranch>> abacBranches() { return abacBranches; }
    public long builtAt() { return builtAt; }
    public long expireAt() { return expireAt; }
    public int permCount() { return permBits.getCardinality(); }

    public static Builder builder(String userId) { return new Builder(userId); }

    /**
     * 合并两条数据范围规则并取真正的集合并集。
     *
     * <p>范围类型不是一条全序：{@code SELF}、精确组织与另一个组织子树互不包含，
     * 不能按一个“宽度”数字二选一。混合类型统一规范化为 {@code CUSTOM}，同时保留
     * 路径前缀、精确组织和本人条件，由 SQL 处理器用 OR 组合。
     */
    public static DataScopeRule widen(DataScopeRule a, DataScopeRule b) {
        if (a == null) return b;
        if (b == null) return a;
        if (a.type() == DataScopeType.NONE) return b;
        if (b.type() == DataScopeType.NONE) return a;
        if (a.type() == DataScopeType.ALL || b.type() == DataScopeType.ALL) return DataScopeRule.all();

        LinkedHashSet<String> prefixes = new LinkedHashSet<>(a.pathPrefixes());
        prefixes.addAll(b.pathPrefixes());
        LinkedHashSet<Long> orgIds = new LinkedHashSet<>(a.orgIds());
        orgIds.addAll(b.orgIds());
        String selfUserId = a.selfUserId() != null ? a.selfUserId() : b.selfUserId();

        DataScopeType resultType = a.type() == b.type() ? a.type() : DataScopeType.CUSTOM;
        return new DataScopeRule(resultType, List.copyOf(prefixes), Set.copyOf(orgIds), selfUserId);
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
        private final Map<Integer, DataScopeRule> permissionScope = new HashMap<>();
        private final Map<Integer, Map<Long, DataScopeRule>> permissionRoleScope = new HashMap<>();
        private Set<String> delegators = Set.of();
        private boolean abacEnabled;
        private final RoaringBitmap abacUnconditionalBits = new RoaringBitmap();
        private final Map<Integer, Set<Long>> abacUnconditionalRoles = new HashMap<>();
        private final Map<Integer, LinkedHashSet<AbacBranch>> abacBranches = new HashMap<>();
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

        /** 同时维护兼容的模块范围和新的权限点级范围。 */
        public Builder scope(int permId, String module, DataScopeRule rule) {
            scope(module, rule);
            permissionScope.merge(permId, rule, PermissionSnapshot::widen);
            return this;
        }

        public Builder scope(long roleId, int permId, String module, DataScopeRule rule) {
            scope(permId, module, rule);
            permissionRoleScope.computeIfAbsent(permId, ignored -> new HashMap<>())
                    .merge(roleId, rule, PermissionSnapshot::widen);
            return this;
        }

        public Builder delegators(Collection<String> v) { this.delegators = Set.copyOf(v); return this; }
        public Builder abacEnabled(boolean v) { this.abacEnabled = v; return this; }
        public Builder markAbacUnconditional(int permId) { abacUnconditionalBits.add(permId); return this; }
        public Builder markAbacUnconditional(int permId, long roleId) {
            markAbacUnconditional(permId);
            abacUnconditionalRoles.computeIfAbsent(permId, ignored -> new LinkedHashSet<>()).add(roleId);
            return this;
        }
        public Builder addAbacBranch(int permId, AbacBranch branch) {
            abacBranches.computeIfAbsent(permId, ignored -> new LinkedHashSet<>()).add(branch);
            return this;
        }
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

        public Builder rawPermissionScope(Map<Integer, DataScopeRule> perPermission) {
            if (perPermission != null) this.permissionScope.putAll(perPermission);
            return this;
        }

        public Builder rawPermissionRoleScope(Map<Integer, Map<Long, DataScopeRule>> values) {
            if (values != null) values.forEach((permission, scopes) ->
                    this.permissionRoleScope.put(permission, new HashMap<>(scopes)));
            return this;
        }

        public Builder rawAbac(boolean enabled, RoaringBitmap unconditional,
                               Map<Integer, List<AbacBranch>> branches,
                               Map<Integer, Set<Long>> unconditionalRoles) {
            this.abacEnabled = enabled;
            if (unconditional != null) this.abacUnconditionalBits.or(unconditional);
            if (branches != null) branches.forEach((id, values) ->
                    this.abacBranches.computeIfAbsent(id, ignored -> new LinkedHashSet<>()).addAll(values));
            if (unconditionalRoles != null) unconditionalRoles.forEach((id, values) ->
                    this.abacUnconditionalRoles.put(id, new LinkedHashSet<>(values)));
            return this;
        }

        public PermissionSnapshot build() { return new PermissionSnapshot(this); }
    }

    /** 无任何权限的空快照。未入职账号、或找不到员工记录时用它 —— 安全默认值是"什么都不能做"。 */
    public static PermissionSnapshot empty(String userId, long epoch, long userVersion, long ttlMs) {
        return empty(userId, epoch, userVersion, ttlMs, false);
    }

    public static PermissionSnapshot empty(String userId, long epoch, long userVersion, long ttlMs,
                                           boolean abacEnabled) {
        return builder(userId).epoch(epoch).userVersion(userVersion)
                .abacEnabled(abacEnabled)
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
