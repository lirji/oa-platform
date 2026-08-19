package com.lrj.oa.common.cache;

/**
 * 一条跨节点缓存失效通知。
 *
 * @param type   失效类型：{@link #TYPE_PERM_EPOCH} / {@link #TYPE_PERM_USER} / {@link #TYPE_ORG_TREE}
 * @param key    受影响的键（用户级失效时是 userId；全局失效为空串）
 * @param nodeId 发出者节点 id，收到自己发的消息时跳过
 */
public record CacheInvalidation(String type, String key, String nodeId) {

    /** 权限元数据变了（角色/权限点/角色继承/部门级授权），全员快照作废。 */
    public static final String TYPE_PERM_EPOCH = "PERM_EPOCH";
    /** 只影响某个用户的授权变更。 */
    public static final String TYPE_PERM_USER = "PERM_USER";
    /** 组织树结构变了。 */
    public static final String TYPE_ORG_TREE = "ORG_TREE";
}
