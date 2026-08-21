# USER_GROUP / ABAC Delivery Report

## Outcome

USER_GROUP 与 ABAC 已完成端到端交付。管理员可在 PC 维护显式用户组、成员时间窗、组授权和受限 ABAC 条件；
运行时通过权限快照实现零业务 I/O 判权，支持租户隔离、秒级收权、来源解释、默认关闭灰度与安全回滚。

## Delivered

- V14：`user_group`、`user_group_member`，以及 tenant-aware `permission_condition` 生命周期字段与索引。
- USER_GROUP：CRUD/成员批量维护、授权校验、角色继承、数据范围、时间边界、缓存失效和沙盘来源链。
- ABAC：CRUD/校验、受限 SpEL、AND/OR/旁路语义、fail-closed、快照与 L2 codec、AOP、指标和上限保护。
- PC：主体授权新增 POSITION/USER_GROUP，用户组管理与成员 Drawer，ABAC 条件管理和语法帮助。
- 契约/发布：96-path OpenAPI、API golden、环境变量/Compose、Phase 9 smoke、README/Architecture/API/Runbook/进度。

## Quality Gates

| Gate | Result |
| --- | --- |
| Product/UX/technical Gate A | Approved by user “继续” |
| Backend full reactor | Pass, 16/16 modules |
| PC unit/build/size | Pass, 105 tests, 288.2/300 KB |
| Browser focused E2E | Pass, 3/3 |
| PostgreSQL migration/API runtime | Pass |
| USER_GROUP/ABAC Phase 9 | Pass, 10/10 |
| OpenAPI/golden/config/docs | Pass |
| Code review | Pass |

## Rollout And Rollback

1. 部署 additive V14 和代码，保持 `OA_IAM_ABAC_ENABLED=false`。
2. 在目标环境运行 Phase 9，审查租户内条件和用户组授权。
3. 测试环境打开 ABAC，监控 evaluations/denied/errors，再灰度生产。
4. 异常时关闭 ABAC 并重启即可恢复 RBAC；用户组可逐个禁用。V14 表无需也不应物理回滚。

## Repository State

- 前置项目收口提交 `50264fb` 已推送远程 `main`。
- 本次 USER_GROUP/ABAC 变更已实现并验证，但按用户原始顺序要求没有自动创建第二个 commit 或 push。
- 未执行生产部署。

## References

- `DELIVERY_PLAN.md` — approved scope and AC-01～AC-10.
- `REVIEW_REPORT.md` — findings and security review.
- `QA_REPORT.md` — test evidence and limits.
- `DELIVERY_STATUS.md` — final recoverable status.
