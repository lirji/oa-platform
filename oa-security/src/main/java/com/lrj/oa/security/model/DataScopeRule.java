package com.lrj.oa.security.model;

import java.util.List;
import java.util.Set;

/**
 * 一条可直接翻译成 SQL 片段的数据范围规则。
 *
 * @param type            范围类型
 * @param pathPrefixes    组织物化路径前缀，如 ['/1/23/', '/1/45/'] —— 主力，走 LIKE 前缀索引
 * @param orgIds          精确组织 id 集合（ORG 或前缀过多降级时用）
 * @param selfUserId      SELF 范围时的用户 id
 */
public record DataScopeRule(
        DataScopeType type,
        List<String> pathPrefixes,
        Set<Long> orgIds,
        String selfUserId
) {
    /** 前缀数量超过这个阈值就降级为 {@code org_id = ANY(?)}，避免 SQL 里挂一长串 OR。 */
    public static final int MAX_PREFIXES = 8;

    public static DataScopeRule none() {
        return new DataScopeRule(DataScopeType.NONE, List.of(), Set.of(), null);
    }

    public static DataScopeRule all() {
        return new DataScopeRule(DataScopeType.ALL, List.of(), Set.of(), null);
    }

    public boolean shouldDegradeToIdList() {
        return pathPrefixes.size() > MAX_PREFIXES;
    }
}
