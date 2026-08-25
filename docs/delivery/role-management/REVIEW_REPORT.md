# Code Review Report

## Scope And Diff Base

审查本次角色管理相关新增/修改文件及其与现有授权、快照、API Golden、控制台查询缓存和审计链路的交互。仓库中 README、ABAC、Job、数据库注释等既有用户改动不属于本次审查范围，均未覆盖。

## Confirmed Findings

| Severity | Finding | Failure scenario | Evidence | Resolution |
| --- | --- | --- | --- | --- |
| High | 停用角色若仍保留在继承闭包，父角色可能继续获得其权限 | 管理员停用子角色后，父角色快照仍经旧闭包获得权限，停用没有真正收权 | `RoleMapper` 闭包重建与 `selectPermissionsOfRoles` | 闭包只纳入 ACTIVE 节点；启停时在租户锁内重建闭包；真实 API 验证停用退出、启用恢复 |
| Medium | 管理角色列表按角色分别查询 4 组统计会产生 N+1 | 200 个角色会产生约 800 次额外查询，列表明显变慢 | `RoleAdminService#list` 初版 | 合并为一条带聚合子查询的 `selectAdminRoleSummaries`，真实 PostgreSQL 返回验证通过 |
| Medium | 原 `/roles` 和 `/roles/mine` 查询缺少租户过滤 | 多租户开启后可能在下拉中看到其他租户角色 | `RoleController` | 两个查询增加 `TenantContext` 条件 |
| Medium | 新增管理查询 key 未带权限版本 | 角色/权限变化后可能复用旧管理员视图缓存 | `queryKeys.test.ts` 首轮失败 | 所有角色管理和权限目录查询 key 加入 `permVersion`，门禁复测通过 |
| Medium | JIT 接口可对已停用的已持有角色创建临时记录 | 绕过 UI 直接请求会生成没有实际权限或语义错误的提权记录 | `GrantService#elevate` | 提权前校验目标角色必须为当前租户 ACTIVE |

## Rejected Suspicions

| Suspicion | Why rejected | Evidence |
| --- | --- | --- |
| 软删除后角色编码不能复用是缺陷 | 这是明确的审计和历史可追溯规则；计划与 UI 均告知不可恢复/不可复用 | `DELIVERY_PLAN.md`、删除确认文案、租户唯一索引 |
| 管理 API 复用 `oa:iam:admin` 权限过粗 | 本轮明确采用既有管理权限，避免未批准地扩张权限目录；所有 handler 仍有统一安全边界 | `RoleAdminController`、`ControllerPermissionCoverageTest` |
| 角色图更新会被多节点并发覆盖 | 所有图写入使用 PostgreSQL 租户级事务 advisory lock，版本写入另有乐观锁 | `RoleMapper#lockTenantRoleGraph`、`RoleAdminService` |

## Checks Rerun After Fixes

- `mvn test`：16 个 reactor 模块全部成功；`oa-iam` 40 测试、`oa-app` 12 个架构/Golden 测试通过。
- `pnpm gen:perm --check && pnpm gen:api:check`：权限与 OpenAPI 类型无漂移。
- `pnpm test`：11 个文件、107 测试通过。
- `pnpm build && pnpm size`：生产构建通过，首屏 288.2 KB gzip / 300 KB。
- PostgreSQL/Flyway/API 黑盒与浏览器交互复测通过。

## Residual Risks

- 未做数百角色规模的专门压测；列表已从 N+1 修复为单 SQL，当前默认约 200 角色目标下风险较低。
- 本地浏览器使用 DEV 身份验证 UI；JWT 安全立场由既有授权切面、Controller 权限覆盖和 API Golden 验证，未重复跑完整 Casdoor 登录流程。

## Verdict

pass
