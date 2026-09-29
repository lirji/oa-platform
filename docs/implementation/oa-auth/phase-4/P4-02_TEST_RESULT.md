# P4-02 OA 实例适配证据

- 实际Git任务基线4ea8be9（用户已有恢复文档中的f07c978为历史状态）；独立分支feat/iam-p4-oa-access-lifecycle。用户已有CODEX_PROGRESS、identity-authz-governance进度/部署结果与tmp/保持不动。
- CentralApprovalPostgresIT 3项真实PG通过：并发仅一实例/Outbox，改体冲突和未知身份/跨分区拒绝，Outbox失败回滚映射与审批。使用独立auth_gov_p1_test_d55369a01efb；完整46迁移含新增V25，未触碰共享OA库。
- `mvn -q -pl oa-app -am -Pdirectory-it -Dit.test=CentralApprovalPostgresIT -Dfailsafe.failIfNoSpecifiedTests=false verify`通过；相关单测、架构和API golden通过。安全链3项单测覆盖原文改体、nonce重放和禁用。
- 独立oa-app jar真实启动、JWT普通入口、专用签名服务入口，跨进程HTTP5检查通过。中央申请08d6231c-dc99-473d-bb54-7913a06a3b70返回OA实例4；脱敏证据见auth仓phase-4/evidence/start-http.json。
- 首轮消费者使用不同Maven本地仓导致旧protocol编译失败，已向消费者本地仓安装当前protocol。架构规则把ObjectMapper也视为Mapper，序列化移至专用Wire适配而不放宽规则。HTTP启动发现安全Bean重名并修复；隔离运行配置补齐dev_infra Redis连接。失败日志保留.local/oa-auth-p4与auth/.local/governance/p4。
- 本片不宣称流程引擎已审批。共享workflow现有可信producer未登记OA，未修改其配置；P4-07需受控隔离引擎/总线运行。P4-03至07仍未完成。

质量门禁：实际基线4ea8be9、复用已批准公共契约auth-platform-protocol，最终IMPLEMENTATION_COMPLETE_WITH_LIMITATIONS（无格式化器）；初轮依赖登记缺失HOLD已通过记录依据解决。auth首次CLI stdout告警已修复，失败记录保留。
