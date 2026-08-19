package com.lrj.oa.org.api.event;

/**
 * 某人的任职关系发生变化（调岗、加减兼岗、离职）。
 *
 * <p>为什么必须有这个事件：数据权限的 {@code ORG_AND_SUB} 范围锚定在<b>本人所在组织</b>上。
 * 调岗改的是任职关系而不是组织树，如果只监听组织树变更，
 * 调岗后这个人的数据范围会一直停在旧部门 —— 一个安静的越权。
 *
 * @param userId  受影响的人（Casdoor sub）
 * @param reason  变更原因，仅用于日志与审计
 * @param left    是否为离职（离职要连带收回全部授权）
 */
public record EmployeeAssignmentChangedEvent(String userId, String reason, boolean left) {
    public static EmployeeAssignmentChangedEvent of(String userId, String reason) {
        return new EmployeeAssignmentChangedEvent(userId, reason, false);
    }
    public static EmployeeAssignmentChangedEvent left(String userId) {
        return new EmployeeAssignmentChangedEvent(userId, "leave", true);
    }
}
