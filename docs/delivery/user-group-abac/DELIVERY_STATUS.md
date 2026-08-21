# Delivery Status

## Goal

完成 OA 内部 USER_GROUP 授权与安全的 ABAC 条件引擎，并交付管理 UI、测试、文档和 CI-ready 证据。

## State

- Phase: 8 — Delivery complete
- Status: complete
- Last updated: 2026-08-21

## Completed

- 前一交付已提交为 `50264fb` 并推送 `origin/main`。
- Gate A 已批准显式成员用户组、受限 ABAC、同来源 AND/跨来源 OR/无条件旁路语义。
- V14 已实现并在真实 PostgreSQL/Flyway 上成功应用；用户组、成员和 ABAC 条件均按租户隔离。
- USER_GROUP 已进入授权校验、角色继承、数据范围、时间边界、L1/L2 快照、全局 epoch 与沙盘来源链。
- ABAC CRUD、受限表达式、快照分支、编解码、AOP 运行时、fail-closed、指标与默认关闭开关已完成。
- PC `/iam` 已升级为主体授权、用户组和 ABAC 三视图，支持组编辑/禁用/成员时间窗和条件完整生命周期。
- OpenAPI、API golden、环境变量、Compose、README、Architecture、API、Runbook 与权威进度已同步。
- Phase 9 可重复冒烟覆盖 USER_GROUP 生效/撤销、来源解释、ABAC 运行时拒绝/恢复与 CRUD 回收。

## Verification Log

| Command or check | Result | Notes |
| --- | --- | --- |
| `git push origin main` | pass | 前置提交 `50264fb` 已推送 |
| `mvn -B test` | pass | 16/16 reactor modules；IAM 19 tests；app security/architecture/golden 10 tests |
| `pnpm test` | pass | 10 files / 105 tests |
| `pnpm build` | pass | TypeScript + Vite production build |
| `pnpm size` | pass | initial gzip 288.2/300 KB |
| IAM policy Playwright | pass | 3/3：admin三视图、普通员工403、390px |
| Phase 9 smoke | pass | 10/10；含 ABAC 真实接口 403 与停用后 200 |
| Flyway V14 | pass | PostgreSQL 16，out-of-order migration success |
| OpenAPI | pass | 96 paths；snapshot/types/check zero drift |
| API surface golden | pass | 13 个新增 handler 均为 `oa:iam:admin` |
| Compose / CI YAML / shell / diff | pass | config、YAML、`bash -n`、`git diff --check` |

## Decisions And Deviations

- 用户组是 OA 显式成员组；动态规则组、嵌套组与 Casdoor group 同步保持范围外。
- ABAC 不查业务库，只读方法参数和 `#user`；同一角色权限最多 32 条条件，编译缓存最多 10,000 表达式。
- 缺失策略分支、未知权限、变量/类型/求值异常全部 fail-closed。
- 测试发现 golden 提取器不继承 class-level 授权注解，已修复测试基础设施以与运行时 AOP/覆盖门禁一致。

## Residual Risks

- ABAC 默认关闭；生产开启前必须按租户审查已有条件并在测试环境运行 Phase 9。
- 首版不提供业务对象属性加载器；只有 `taskId` 而没有金额等入参的 handler 无法编写金额条件。
- 未执行生产部署或生产容量压测；V14 additive schema 保留，不建议物理回滚。

## Next Action

按 Runbook 在目标环境先保持 `OA_IAM_ABAC_ENABLED=false` 部署，运行 Phase 9，审查策略后再灰度开启。
