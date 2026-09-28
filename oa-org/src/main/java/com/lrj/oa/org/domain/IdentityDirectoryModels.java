package com.lrj.oa.org.domain;

/** 组织域自有目录出口持久化值，不复用 IAM 角色 Outbox 或 auth 数据库模型。 */
public final class IdentityDirectoryModels {
    private IdentityDirectoryModels() {}
    /** 来源登记后自动捕获该企业组织事务；HTTP 开关不能悄悄关闭捕获。 */
    public record Source(long tenantId, String source, String environment, long lastSequence,
                         long ackedSequence, String ackedFingerprint, boolean initialized) {}
    /** 传输只读取已提交的不可变正文，凭据不进入消息。 */
    public record Outbox(long partitionSequence, String fingerprint, String eventJson) {}
    /** 最小员工事实，敏感个人资料不离开 OA。 */
    public record EmployeeFact(long id, String userId, String status) {}
    /** 父节点只保留直接关系，计算闭包不输出为授权。 */
    public record OrganizationFact(long id, Long parentId, String status) {}
}
