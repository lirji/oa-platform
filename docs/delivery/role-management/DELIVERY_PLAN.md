# Role Management Delivery Plan

## Requirement

在现有 IAM 授权体系上补齐可在线维护的角色管理能力：自定义角色新增、编辑、复制、停用和删除；直接权限矩阵；角色继承；继续支持向用户、组织、岗位和用户组授予角色；所有写操作具备租户隔离、并发保护、闭包一致性、权限缓存失效和审计记录。

## Repository Evidence

- `RoleController` 当前只提供角色和权限目录查询。
- `GrantsPage` 只能将现有角色授给主体。
- `role_inherit` 是角色继承闭包，`role_permission` 保存直接权限。
- `IamInvalidationService` 已实现全局权限纪元推进和节点广播。
- `AuditRecordingAspect` 自动审计所有 Controller POST/PUT/DELETE 写操作。
- API Golden、控制器授权覆盖、前端类型检查和 GitHub Actions 已作为现有发布门禁。

## Feasibility

- Verdict: go
- Constraints: 保留四个内置角色；不破坏现有 `/roles` 下拉契约；不修改用户已有未提交改动；继承闭包必须事务性维护。
- Dependencies: PostgreSQL/Flyway、Spring/MyBatis-Plus、React/Ant Design、TanStack Query。
- Risks and mitigations:
  - 继承环或闭包漂移：新增直接继承边表，以事务、租户级数据库锁、环路预检和全量闭包重建维护。
  - 并发覆盖：角色基础信息使用 `version` 乐观锁；矩阵和继承写入也校验版本。
  - 停用角色仍参与授权：授权服务拒绝非 ACTIVE 角色，角色变更推进全局 epoch。
  - 删除破坏历史：采用软删除，仅允许无授权引用的自定义角色删除。

## Product Design

- Actors and goals: 权限管理员创建组织所需角色、配置其能力和继承关系，并将角色授予各类主体。
- Scope: 角色列表、创建、编辑、复制、启停、软删除、直接权限矩阵、角色继承、使用量提示、主体授权现有能力联动。
- Out of scope: 在线新增权限点、审批流式角色变更、批量导入、跨租户角色模板、修改内置角色编码或删除内置角色。
- Business rules:
  - 自定义角色编码为租户内唯一的 2~64 位大写字母/数字/`_`/`-`，创建后不可修改。
  - 内置角色不可删除、不可改编码/类型；允许调整名称、说明、默认范围、权限和继承，以满足既有“配置角色”的语义。
  - 角色不能继承自己，也不能形成直接或间接环。
  - 停用角色不能被新授予；删除要求不存在任何授权记录。
  - 每次角色、矩阵或继承变更均推进全局权限纪元并由现有切面写审计。

## Acceptance Criteria

| ID | Observable behavior | Priority | Verification |
| --- | --- | --- | --- |
| AC-01 | 管理员可创建租户隔离的自定义角色，非法或重复编码被拒绝 | P0 | service tests + build |
| AC-02 | 管理员可编辑角色、停用/启用角色，版本冲突不会静默覆盖 | P0 | service tests |
| AC-03 | 管理员可复制角色及其直接权限/继承配置 | P1 | service tests |
| AC-04 | 管理员可软删除无授权引用的自定义角色，内置或在用角色不可删除 | P0 | service tests |
| AC-05 | 管理员可配置角色直接权限矩阵，继承权限可识别但不被误当直接权限 | P0 | service/mapper contract tests |
| AC-06 | 管理员可配置多角色继承；自继承、跨租户和环路被拒绝；闭包重建一致 | P0 | service tests + migration/SQL review |
| AC-07 | 非 ACTIVE 角色不能被新授予，已有授权在角色停用后经 epoch 失效 | P0 | GrantService tests + invalidation verification |
| AC-08 | PC 提供角色列表、筛选、创建/编辑/复制/启停/删除及权限/继承配置，并适配窄屏 | P0 | frontend tests/build |
| AC-09 | 所有新增 API 受 `oa:iam:admin` 保护并进入 API Golden；写操作由统一审计切面覆盖 | P0 | architecture/golden tests |
| AC-10 | 文档、OpenAPI/类型契约和 CI 门禁与最终行为一致 | P1 | contract/docs/CI checks |

## UI/UX Design

- Applicability: applicable。
- Flow and component map: `/iam` 增加“角色管理”页签；上方搜索/状态筛选与新建按钮；主体为角色表格；编辑抽屉分“基本信息、权限配置、角色继承”，复制和删除使用确认对话框。
- State matrix: 独立处理加载、空列表、API 错误、无权限隐藏、内置/在用角色禁用删除、保存中、防重复提交和版本冲突后刷新。
- Responsive and accessibility behavior: 桌面表格横向信息完整，窄屏允许横向滚动；表单字段有显式 label/校验提示；操作按钮使用文本与确认说明，不只依赖颜色。

## Technical Solution

- Chosen approach: 新增 `role_inherit_edge` 保存直接继承关系，`role_inherit` 继续作为判权热路径闭包；应用服务事务性更新边并重建当前租户闭包。
- Alternatives rejected:
  - 直接把 `role_inherit.distance=1` 当长期直接边：闭包重建时会丢失来源，且语义混杂。
  - 每次判权递归 CTE：破坏现有热路径零递归/低延迟设计。
  - 物理删除角色：会破坏历史审计与授权引用。
- Modules and file map:
  - `oa-iam`: V16 migration、命令 DTO、RoleMapper、RoleAdminService、RoleAdminController、相关单测。
  - `oa-console`: GrantsPage 角色管理页签/组件测试、OpenAPI 快照与生成类型。
  - `oa-app`: API Golden 更新。
  - `docs`: 权限指南、API 和本交付证据。
- Contracts and data:
  - 保留 `GET /api/v1/iam/roles` 为 ACTIVE 下拉目录。
  - 新增 `/api/v1/iam/role-admin` 下的列表、详情、创建、更新、复制、启停、删除、权限和继承接口。
  - 新表记录租户内直接继承边；闭包表由服务统一重建。
- Security and reliability: 所有管理 API 使用 `oa:iam:admin`；所有查询和写入带 tenant；事务内租户级 advisory lock；乐观版本检查；批量数量上限；安全错误不泄露其他租户对象。
- Observability: 复用写接口审计切面和权限失效日志。
- Compatibility and migration: V16 从现有闭包的 `distance=1` 回填直接边；旧角色查询和授权 API 不变。

## Implementation Sequence

1. 数据模型、契约、Mapper 与服务，覆盖 AC-01~AC-07。
2. 管理 API、授权门禁与 API Golden，覆盖 AC-09。
3. PC 角色管理交互与单测，覆盖 AC-08。
4. OpenAPI、文档、CI/构建验证和修复，覆盖 AC-10。
5. 完整差异审查、QA 回归与交付报告。

## Verification Plan

| AC/Risk | Test level | Case or command | Required evidence |
| --- | --- | --- | --- |
| AC-01~07 | unit/module | `mvn -pl oa-iam -am test` | tests pass |
| AC-09 | architecture | `mvn -pl oa-app -am test` | golden/permission coverage pass |
| AC-08 | frontend | `pnpm test && pnpm build` | tests and typecheck pass |
| AC-10 | contract | `pnpm gen:perm --check && pnpm gen:api:check` | no drift |
| Full regression | reactor/CI parity | `mvn test` and existing GitHub Actions commands | pass or explicit environment blocker |

## Documentation Plan

更新 `docs/AUTHORIZATION.md`、`docs/API.md`，并产出本目录的状态、审查、QA 和交付报告。

## CI Plan

现有 GitHub Actions 已覆盖后端 reactor、权限/API Golden、前端契约/单测/构建/包体积；若最终命令不变则无需扩大 CI，仅验证覆盖充分。

## Rollout And Rollback

- Rollout: 先执行 V16，再发布后端，最后发布 PC；新 API 与旧下拉接口兼容。
- Monitoring: 观察角色写操作审计、epoch 推进日志、API 4xx/5xx 和角色管理前端错误。
- Rollback: 前端/后端可回退且旧查询继续工作；数据库新表为附加结构。已发生的角色数据变更通过审计人工回滚，不自动删除用户数据。

## Assumptions And Open Decisions

- 使用既有 `oa:iam:admin` 管理权限，不新增更细权限点，避免未获确认的权限模型扩张。
- “删除”实现为软删除并禁止编码复用，以保留引用和审计可追溯性。
- 当前没有会阻止实施的业务开放问题。

## Approval

- Status: approved
- Approved scope: 上一轮列出的角色管理、角色权限配置、角色授予与安全治理能力。
- Evidence: 用户回复“把你列出来的这部分能力实现一下”。
