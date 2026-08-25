# QA Report

## Environment

- Target: 本地工作树，JDK 21、Node/TypeScript、PostgreSQL/Redis/Kafka Docker 运行环境。
- Scope: AC-01～AC-12 的自动化、架构、契约、构建和运行态验证。

## Cases

| ID | AC | Case | Evidence | Verdict |
| --- | --- | --- | --- | --- |
| QA-01 | 01 | 永久 CUSTOM + JIT NONE 组合 | 快照仍为 CUSTOM，JIT 只点亮高危位 | pass |
| QA-02 | 02 | 伪造委托人及范围外流程 | Approval/Todo 单测拒绝 | pass |
| QA-03 | 03 | 跨组织员工对象访问 | EmployeeServiceDataScopeTest | pass |
| QA-04 | 04 | 外部请求数据面登记 | DataAccess/JDBC Golden、Controller→Mapper 架构门禁 | pass |
| QA-05 | 05 | 多角色同权限、条件一过一拒 | ABAC/PermissionSnapshot scope 组合测试 | pass |
| QA-06 | 06 | CUSTOM UI、自动默认范围、有效期 | GrantsPage helpers、TypeScript 生产构建 | pass |
| QA-07 | 07/08 | 非法组织、自授、越级高权、范围上限 | GrantServiceGovernanceTest | pass |
| QA-08 | 09 | 重复授权及任职收权 | 幂等返回既有 ID；OrgChangeListener 全局 epoch 测试 | pass |
| QA-09 | 10 | 申请不生效、申请人自批 | GrantServiceGovernanceTest；审批 API/Golden | pass |
| QA-10 | 10 | 公告非受众列表/已读 | AnnouncementServiceTest | pass |
| QA-11 | 11 | API、权限常量、文档和 CI | Golden、`gen:perm --check`、`gen:api:check`、compose config 与文档审查 | pass |
| QA-12 | 12 | Docker 冷构建、部署和健康 | 6 个应用镜像 `--pull --no-cache`；10 个容器 healthy；Flyway/API/日志冒烟 | pass |

## Automated Regression

- 后端完整冷构建：`mvn clean package` pass；14 模块聚合回归 pass，IAM 55 tests。
- 前端：12 files / 109 tests pass；生产构建、权限/OpenAPI 生成物检查和 300 KB 体积预算 pass。
- Docker：4 个后端、PC、移动端最终镜像均使用 `--pull --no-cache`；因 Docker Hub 瞬时 TLS/EOF 重试后全部成功。
- 部署：PostgreSQL、Redis、Kafka、MinIO 与 6 个应用容器共 10 个容器均 healthy；应用服务使用最终镜像 ID。
- 数据库：Flyway V17/V83 `success=true`，`oa_iam.elevation_request` 和 `oa:iam:elevation:approve` 已存在。
- API：6 个服务正确 ping 端点通过；角色管理和提权申请列表返回 200；未认证角色管理返回 401；运行态 OpenAPI 为 106 paths。
- 日志：正确运行态冒烟后的 6 个应用日志窗口无 ERROR/Exception/FATAL。

## Runtime Defect Found During QA

首次调用提权审批列表时，PostgreSQL 无法推断 `#{requesterId} IS NULL` 的参数类型而返回 500。已将
`ElevationRequestMapper.list` 改为 MyBatis 动态 `<if>` 条件，重新执行 Maven 构建、oa-app 冷镜像构建、
容器替换与接口复测；管理员列表和个人列表均返回 200。

## Verdict

AC-01～AC-12 全部 pass，可以交付。
