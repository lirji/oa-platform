# Permission Hardening Delivery Plan

## Requirement

按已确认的优先级完成权限安全收口：JIT 与委托越权、对象级和全业务数据权限、ABAC 来源范围绑定、
`CUSTOM` 授权管理闭环、高权授权治理与审批审计；同步文档、测试、CI，并最终执行 Docker 完整冷构建和本地部署。

## Repository Evidence

- JIT 当前写入 `scope_type=ALL`，且临时授权与普通授权共同贡献权限和数据范围。
- 员工详情、调岗、任职、离职等按 ID 操作只有接口权限，没有目标对象范围断言。
- ABAC 只返回 permission 级允许/拒绝，快照已经提前合并全部授权来源的数据范围。
- 委托办理信任客户端 `onBehalfOf`，运行时未消费委托的流程/角色范围。
- PC 主体授权缺少 `CUSTOM`、组织树和有效期；服务端仅校验 USER_GROUP 主体。
- 数据权限 Golden 仅覆盖 Directory/Asset，既有 JDBC 请求路径尚未完全迁移。
- 当前产品按单租户运行；真实多租户改造不在本轮启用范围。

## Feasibility

- Verdict: go
- Constraints: 保留工作区已有角色管理和文档改动；兼容既有单租户数据；不重置数据库。
- Dependencies: PostgreSQL、Redis、Kafka、现有本地 workflow gateway、React/Ant Design 管控台。
- Risks and mitigations:
  - 权限收紧造成既有接口 403：增加 IDOR、来源组合和 Docker API 回归。
  - 快照协议变更读取旧 Redis 值：提升 codec 版本并 fail-safe 重建。
  - 数据访问迁移范围较大：按外部请求链逐类迁移，后台任务只允许显式可审计旁路。
  - 高权审批涉及流程编排：复用现有审批实例/本地工作流能力；审批不可用时 fail-closed。

## Product Design

- Actors and goals: 普通员工安全使用待办；区域经理只能操作授权区域；IAM 管理员可配置可验证的授权；审计员可追溯高权变更。
- Scope: 当前单租户产品内的 RBAC、ABAC、JIT、委托、数据范围和授权管理闭环。
- Out of scope: 启用真实多租户、PostgreSQL RLS、外部 BPM 平台生产接入。
- Business rules:
  - JIT 只能激活用户已持有角色的高危权限，不得新增或扩大功能/数据权限。
  - 委托事实只能由服务端有效委托记录决定，客户端不得声明授权事实。
  - 列表、详情、导出、修改、删除必须使用同一权限点的数据范围。
  - ABAC 通过哪个授权来源，只能消费该来源贡献的数据范围。
  - `CUSTOM` 必须包含有效组织；主体、组织和范围必须在当前租户存在。
  - 管理员不得自授或授予超出自身可分配上限的高权角色/范围；重复活跃授权必须幂等。

## Acceptance Criteria

| ID | Observable behavior | Priority | Verification |
| --- | --- | --- | --- |
| AC-01 | JIT 激活不会把 `CUSTOM/ORG/SELF/NONE` 扩大为 `ALL`，也不会绕过 ABAC | P0 | snapshot/unit + API regression |
| AC-02 | 伪造 `onBehalfOf` 被拒；委托按流程范围过滤查询和办理 | P0 | flow/iam unit + API regression |
| AC-03 | 员工详情、更新、调岗、任职、汇报线、离职跨组织访问全部拒绝 | P0 | object-scope/IDOR integration tests |
| AC-04 | 所有外部请求业务数据访问均为 SCOPED 或获批的显式 BYPASS，Golden 无未审查路径 | P0 | architecture + data-access Golden |
| AC-05 | ABAC 仅合并本次通过来源的数据范围，多角色同权限不会串范围 | P0 | combinatorial unit tests |
| AC-06 | PC 可选择用户/组织/岗位、配置 `CUSTOM` 组织树、下级开关、授权类型和有效期 | P1 | component + browser QA |
| AC-07 | 服务端拒绝不存在主体、空/非法 CUSTOM、非法范围和越租户引用 | P1 | service/integration tests |
| AC-08 | 角色默认范围自动带入，最大范围在服务端强制；禁止自授/越级授予系统高权角色 | P1 | service/API tests |
| AC-09 | 相同活跃授权重试幂等；撤权、任职收权在缓存通知丢失场景仍可靠生效 | P1 | concurrency/cache tests |
| AC-10 | JIT 与高权授权具备审批关联和可靠、可定位的 before/after 审计 | P1 | workflow/audit integration tests |
| AC-11 | 受影响文档、OpenAPI、CI 与运行手册和最终行为一致 | P1 | doc/API golden/CI checks |
| AC-12 | 全仓测试、前端测试构建、Docker `--pull --no-cache` 冷构建、部署与健康检查通过 | P0 | command and runtime evidence |

## UI/UX Design

- Applicability: applicable，修改 `/iam` 主体授权页。
- Flow and component map: 主体类型 → 可搜索选择器 → 角色 → 自动带出默认/最大范围 → 范围类型 → CUSTOM 组织树和下级开关 → 时效与理由 → 预览/提交。
- State matrix: 加载禁用、无候选项空态、字段级校验、403 权限态、409 幂等/冲突态、提交成功后刷新台账。
- Responsive and accessibility behavior: 沿用 Ant Design 栅格；窄屏单列；所有输入有 label、错误提示并支持键盘操作。

## Technical Solution

- Chosen approach:
  - 将 JIT 建模为 elevation-only 来源，快照只增加 elevated bits，不贡献 RBAC、ABAC 或 scope。
  - 快照保存授权来源级 permission/scope/ABAC，判权上下文记录本次通过来源，数据切面消费其范围并集。
  - 新增服务端委托判定 API，查询和办理均按 process key/role 规则执行。
  - 为对象读写提供权限点级范围断言或受限 SQL；逐步迁移外部请求 JDBC。
  - 授权写侧增加主体/范围/上限/幂等/审批校验和结构化审计。
- Alternatives rejected:
  - 仅前端限制：可被直接 API 绕过。
  - JIT 复制为一条 `ALL` 或单一 scope 临时授权：多来源/多范围时仍会放大或丢失。
  - 只保护列表：详情和写操作仍存在 IDOR。
- Modules and file map: `oa-iam` 快照/授权/ABAC/委托；`oa-org` 对象范围；其余业务模块数据访问；`oa-flow` 委托与审批；`oa-console` 授权 UI；`oa-app` Golden/集成测试；`docs`、`.github/workflows/ci.yml`、`deploy`。
- Contracts and data: 新增来源级快照结构和版本；授权 API 补完整验证/幂等响应；必要时新增向前兼容 Flyway 迁移。
- Security and reliability: fail-closed、租户条件、服务端可信属性、全局 epoch 收权、事务审计/outbox 或等价可靠写入。
- Observability: 保留权限/ABAC/数据范围指标，增加委托拒绝、JIT 申请/批准、高权授权拒绝与幂等命中指标/审计字段。
- Compatibility and migration: 旧快照版本丢弃重建；既有授权数据保留；不启用真实多租户。

## Implementation Sequence

1. JIT elevation-only 与委托服务端校验（AC-01、AC-02）。
2. 员工对象级范围和 IDOR 回归（AC-03）。
3. 外部请求数据访问迁移与 Golden 收口（AC-04）。
4. ABAC 来源级范围绑定与缓存可靠收权（AC-05、AC-09）。
5. CUSTOM UI、服务端验证、授权上限/幂等和高权审批审计（AC-06～AC-10）。
6. 全量审查、QA、文档、CI、冷构建和部署（AC-11、AC-12）。

## Verification Plan

| AC/Risk | Test level | Case or command | Required evidence |
| --- | --- | --- | --- |
| AC-01/05 | unit/integration | `mvn -pl oa-iam -am test` | mixed source scope assertions |
| AC-02/03 | unit/API | `mvn -pl oa-flow,oa-org,oa-app -am test` | spoof/IDOR denial |
| AC-04 | architecture | app Golden and JDBC bypass tests | reviewed surface |
| AC-06/07/08 | frontend/service/API | Vitest + Maven + local API | form and validation evidence |
| AC-09/10 | integration | PostgreSQL/cache/workflow/audit cases | revoke and approval evidence |
| AC-11/12 | full | CI-equivalent commands and Docker cold build | healthy containers and smoke |

## Documentation Plan

更新 `AUTHORIZATION.md`、`ABAC.md`、`API.md`、`ARCHITECTURE.md`、`RUNBOOK.md`、ADR、OpenAPI 及本交付五份证据文档。

## CI Plan

沿用 GitHub Actions，补充新增安全组合测试、前端测试构建、Golden 与 compose 配置校验；部署只在本地执行，不加入远程 CI。

## Rollout And Rollback

- Rollout: 数据迁移 → 后端 → 前端；清理 Redis 权限快照；观察拒绝/错误/旁路指标；执行区域 IDOR 烟测。
- Rollback: 回滚应用镜像；Flyway 迁移只做向前兼容新增，保留旧字段；必要时关闭新增 UI 入口但不得关闭 fail-closed 安全校验。

## Assumptions And Open Decisions

- 产品继续按单租户运行；多租户启用前需另行完成 tenant key、TenantLine/RLS 和跨租户矩阵。
- 本地 workflow 模式用于审批集成 QA，外部 BPM 生产联调不作为本地完成条件。
- 高权角色至少包括内置角色和包含 IAM 管理/授权权限的角色。

## Approval

- Status: approved
- Approved scope: 按前次审查给出的推荐顺序全部实施并最终 Docker 冷构建部署。
- Evidence: 用户消息“按照你的推荐顺序执行，最后重新构建部署docker”。
