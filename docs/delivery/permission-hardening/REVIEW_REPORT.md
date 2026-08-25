# Code Review Report

## Scope

审查权限快照、ABAC、授权/JIT、委托、对象/数据权限、受影响业务查询、控制台、迁移、Golden、文档与部署契约。

## Confirmed Findings And Resolutions

| Severity | Finding | Resolution |
| --- | --- | --- |
| Critical | JIT 普通临时授权可能扩大长期数据范围 | elevation-only 分支只写高危位，scope 固定 NONE，不进入 RBAC/ABAC/scope 合并 |
| High | ABAC 通过任一角色后会消费其他未通过来源的宽范围 | 快照保存来源角色范围，ABAC 返回 passing roles，AuthorizationContext 只携带通过来源的并集 |
| High | 委托办理信任客户端 `onBehalfOf` | 服务端加载有效委托并按流程/角色范围判定，伪造请求拒绝 |
| High | 员工详情和多项 ID 写操作存在 IDOR 风险 | 引入 `DataScopeAccessChecker`，所有详情/写路径在变更前检查目标组织/用户 |
| High | 公告列表、已读和发布受众可能越过 ACL/发布人范围 | 发布逐受众校验数据范围；列表与已读检查发布时受众位图；所有查询带租户 |
| Medium | Controller 直连新 Workbench Mapper 违反数据访问分层 | 新增 `WorkbenchService`，对象范围注解与 Mapper 调用下沉应用层 |
| Medium | 授权和提权部分 Mapper 查询缺少租户条件 | 快照候选、时间边界、主体列表、到期回收、来源解释和活跃提权均显式带租户 |
| Medium | 自定义高权角色可能绕过“内置角色”保护 | 角色只要直接或继承包含 IAM 管理/授权/撤权/审批能力即按高权角色保护 |
| Medium | JIT 申请并发重试可能撞唯一索引 | INSERT 使用 `ON CONFLICT DO NOTHING` 并返回既有 PENDING 申请 |
| Medium | 前端仍按旧语义在申请后立即重放高危请求 | 改为提示待四眼审批，不立即刷新/重放；新增审批页签 |
| Medium | PostgreSQL 无法推断提权列表可空参数的 SQL 类型 | Mapper 改为动态条件；重新冷构建 oa-app 并完成 200 回归 |

## Review Gates

- Controller 权限覆盖、API Golden、数据访问 Golden、JDBC bypass 和模块架构规则通过。
- IAM、Flow、Notify、File 和 App 聚合测试通过。
- `git diff --check`、权限/OpenAPI 生成物检查和 compose 配置校验通过。
- 全仓 Maven 冷构建、前端 109 tests/生产构建和 6 个镜像 `--pull --no-cache` 构建通过。
- 10 个容器 healthy；Flyway、角色管理、JIT 列表、鉴权失败、OpenAPI 和服务 ping 运行态验证通过。

## Residual Risks

- `default_scope` 同时承担默认值和最大值，模型安全且简单，但不能表达“默认 SELF、最多 ORG”这种双层运营配置。
- 对象权限注解是协议/可观测性门禁，业务对象谓词仍需在新增方法时由服务/SQL实现；Golden 防止未登记扩张。

## Verdict

pass，可以交付。远程 GitHub Actions 未触发，但本地 CI 等价门禁均已通过。
