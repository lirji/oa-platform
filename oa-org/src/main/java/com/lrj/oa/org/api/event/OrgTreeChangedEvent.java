package com.lrj.oa.org.api.event;

/**
 * 组织树发生结构性变更。本进程内同步刷新内存快照；跨节点靠 {@code org_tree_version} 轮询收敛。
 *
 * @param reason 变更原因，仅用于日志与审计
 * @param orgId  受影响的组织（批量变更时为 null）
 */
public record OrgTreeChangedEvent(String reason, Long orgId) {
    public static OrgTreeChangedEvent of(String reason, Long orgId) { return new OrgTreeChangedEvent(reason, orgId); }
    public static OrgTreeChangedEvent bulk(String reason) { return new OrgTreeChangedEvent(reason, null); }
}
