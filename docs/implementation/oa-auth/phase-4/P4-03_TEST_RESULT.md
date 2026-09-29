# P4-03 OA决策投递证据

CentralApprovalPostgresIT当前4项PASS，模块单测/架构/golden PASS。真实SQL及HTTP测试验证：没有引擎完成证据不产批准消息；实际办理日志与指定审批人对应后冻结单一event_id/body；503重投同体并最终DONE。Flowable端口本片为测试替身，真实引擎验收留P4-07。

V26独立回调Outbox使用原始签名字节、分区限定、短SQL事务、有界领取和五次退避；不与通知/Grant状态混用。新增迁移首次测试因未开启项目已有outOfOrder失败，修正装配后复验通过，不改历史迁移。Code Hygiene通过，仅无格式化器限制。
