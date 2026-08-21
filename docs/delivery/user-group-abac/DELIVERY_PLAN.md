# USER_GROUP And ABAC Delivery Plan

## Requirement

在当前 OA 权限域中完成 `USER_GROUP` 授权主体和 ABAC 条件引擎，使管理员可维护用户组、成员与
角色权限条件；判权、解释、缓存失效、PC 管理界面、API、测试和运维文档形成可重复验收的闭环。

## Repository Evidence

- `grant_record.subject_type` 与 `SubjectType` 已预留 `USER_GROUP`，但
  `PermissionSnapshotBuilder` 当前对它告警并跳过。
- 已有 `permission_condition(role_id, permission_id, expression, description)`，但没有 domain、mapper、
  service、API、执行器或快照契约；`oa.iam.abac.enabled=false`。
- 判权热路径只读 `PermissionSnapshot`，L1/L2/L3 与 epoch 失效协议已成熟；新能力不得在每次请求查库。
- PC 的 `/iam` 当前只支持 USER/ORG_UNIT 授权；权限沙盘来源链只解释 USER/ORG_UNIT/POSITION。
- 组织权威在 OA，不使用 Casdoor group；主体标识统一为 Casdoor `sub` 字符串。

## Feasibility

- Verdict: go
- Constraints:
  - 保持现有 USER/ORG_UNIT/POSITION 授权与数据范围语义兼容。
  - 组成员移除、组禁用、条件收紧必须走全局 epoch，收权最多 1 秒生效。
  - ABAC 热路径不得访问数据库或远程服务，不得允许数据库表达式执行任意 Java/Bean/类型调用。
  - Flyway 使用 `out-of-order=true`；IAM 继续使用其保留版本段 `V14`。
- Dependencies: PostgreSQL、现有 Redis 缓存、Spring AOP/Expression、React/Ant Design、现有 PC E2E 夹具。
- Risks and mitigations:
  - 表达式注入/RCE：只允许变量、属性/Map 索引、字面量、比较与布尔/基础算术；禁 type、constructor、bean、method、assignment。
  - 多来源语义错误：同一角色权限的条件 AND；不同角色/授权来源 OR；任一无条件来源即无条件放行。
  - 缓存旧策略：策略分支进入 L1/L2 快照；组/成员/条件任何变化推进 epoch 并清理缓存。
  - 成员时间边界越过 TTL：快照 `expireAt` 同时压低到最近成员或组授权的生效/到期边界。

## Product Design

- Actors and goals:
  - 权限管理员：建立用户组、批量增删成员、把现有角色授给组、配置/停用 ABAC 条件。
  - 员工：通过组获得与直接授权一致的角色/数据范围；条件不满足时得到明确 403。
  - 审计/运维：在权限沙盘看到“来自哪个用户组”和命中的条件，监控求值拒绝与错误。
- Scope:
  - OA 内部静态用户组，支持成员批量维护与可选生效/失效时间。
  - USER_GROUP 进入快照、TTL、来源链、预览与现有授权 CRUD。
  - 角色+权限点级 ABAC CRUD、受限表达式验证、快照缓存、AOP 执行和解释。
  - PC `/iam` 增加“授权 / 用户组 / ABAC 条件”管理视图。
- Out of scope:
  - 按 LDAP/SQL/脚本自动计算的动态规则组、嵌套用户组、Casdoor group 同步。
  - 请求后再查询业务数据库补充 ABAC 对象属性；首版只使用方法参数和当前用户上下文。
  - DENY policy、跨租户组、策略版本审批流和生产部署。
- Business rules:
  - 组 code 在租户内唯一；禁用为软操作，禁用组不参与判权；有历史授权/成员的组不物理删除。
  - 成员以 userId 唯一，可设置 `validFrom/validTo`；无员工档案的 userId 拒绝加入。
  - USER_GROUP 授权沿用 `grant_record` 的角色、时间窗与 scope；ORG/ORG_AND_SUB 仍锚定当前用户任职组织。
  - 同一 `(role, permission)` 的启用条件全部满足才算该来源通过；不同授权来源任一通过即可。
  - 未配置条件等价于无条件；ABAC 总开关关闭时保持历史 RBAC 行为。
  - 缺变量、类型错误、超时/解析错误一律 fail-closed，返回权限不足并记录不含敏感值的结构化日志。

## Acceptance Criteria

| ID | Observable behavior | Priority | Verification |
| --- | --- | --- | --- |
| AC-01 | 管理员可创建、修改、禁用、查询用户组并批量增删有效期成员 | P0 | service/controller tests + API smoke |
| AC-02 | USER_GROUP grant 只对当前有效成员生效；移除成员或禁用组后最多 1 秒收权 | P0 | snapshot/integration smoke |
| AC-03 | 组授权沿用角色继承、临时授权和数据范围，快照不会越过成员/授权时间边界 | P0 | builder/TTL tests |
| AC-04 | 沙盘来源链明确展示用户组、角色继承与授权记录 | P1 | query test + PC E2E |
| AC-05 | 管理员可创建、验证、启停、删除角色权限 ABAC 条件 | P0 | API/component tests |
| AC-06 | ABAC 启用时按“同来源 AND、跨来源 OR、无条件来源旁路”执行 | P0 | evaluator/aspect/snapshot tests |
| AC-07 | 恶意 SpEL 类型/构造器/Bean/方法/赋值表达式被创建时拒绝，运行错误 fail-closed | P0 | adversarial unit tests |
| AC-08 | ABAC 求值仅读快照和方法参数，L2 编解码后语义不变 | P0 | codec + no-I/O focused tests |
| AC-09 | PC 可管理组、成员、组授权和条件，覆盖 loading/empty/error/disabled/窄屏 | P1 | Vitest + Playwright |
| AC-10 | OpenAPI、golden、README/API/架构/Runbook/CI 与最终代码一致，全量回归通过 | P0 | generation/build/smoke/review/QA |

## UI/UX Design

- Applicability: applicable；沿用现有 `/iam` 页面和 Ant Design，不新增独立设计系统或菜单权限。
- Flow and component map:

```text
/iam
├─ 授权：主体类型新增“用户组”，选择/输入组后沿用现有角色与 scope 表单
├─ 用户组：组列表 → 新建/编辑/禁用 → 成员 Drawer → 批量加入/移除
└─ ABAC 条件：角色 + 权限点筛选 → 条件表 → 新建/编辑/启停/删除 + 语法帮助
```

- State matrix:
  - 首次加载 skeleton；无组/无成员/无条件有明确空态。
  - 禁用组显示灰色状态且不能新增成员/授权；已有历史数据仍可查看。
  - 表达式在提交前显示允许变量/运算符；后端拒绝时保留输入并显示具体安全校验原因。
  - 收权成功文案明确“全局 epoch，最多 1 秒生效”，不做撤权乐观更新。
- Responsive/accessibility:
  - 1366×768 两栏，窄屏改为单栏/Drawer；表单控件有 label，可键盘操作，状态不只依赖颜色。

## Technical Solution

- Chosen approach:
  - `user_group` + `user_group_member` 两张 IAM 表，成员软撤销并支持时间窗。
  - `GrantMapper` 只读取当前用户所属的 ACTIVE 组授权；`PermissionSnapshotBuilder` 将组来源与条件分支一起装入快照。
  - ABAC 使用 Spring `SimpleEvaluationContext` 加创建时词法/AST allow-list；绑定 `#p0/#a0`、参数名（可用时）、`#args` 和只读 `#user`。
  - 快照为每个 permission 保存 authorization branches；branch 内条件 AND，branches 间 OR；空 branch 表示无条件来源。
- Alternatives rejected:
  - Casdoor group：与 OA 一人多岗/时间维度和既有“组织权威在 OA”决策冲突。
  - 把成员 userId JSON 塞进 grant：无法索引、增量维护、审计或表达成员有效期。
  - 每次请求查询条件/成员：破坏热路径零 DB 和现有 1ms P99 目标。
  - 不受限 StandardEvaluationContext：数据库表达式可调用类型、构造器或 Bean，存在代码执行风险。
  - 仅按 permission 聚合成一组条件：会错误地把不同角色来源做 AND，导致本应通过的独立授权被拒。
- Modules and anticipated file map:
  - `oa-iam/.../db/migration/V14__user_group_abac.sql`
  - `oa-iam/domain/{UserGroup,UserGroupMember,PermissionCondition,AbacBranch}.java`
  - `oa-iam/infrastructure/mapper/{UserGroupMapper,PermissionConditionMapper}.java`
  - `oa-iam/application/{UserGroupService,AbacPolicyService,AbacEvaluator}.java`
  - `oa-iam/web/{UserGroupController,AbacPolicyController}.java`
  - `PermissionSnapshotBuilder`, `PermissionSnapshot`, `SnapshotCodec`, `PermissionEngine`, `RequiresPermAspect`, `GrantService/Mapper`。
  - `oa-console/src/pages/iam/**`、E2E、OpenAPI 快照/types。
  - `deploy/scripts/phase9-iam-policy-smoke.sh`、API golden、CI/README/docs/delivery。
- Contracts and data:
  - additive V14 migration；增强 `permission_condition` 的 `enabled/updated_at/created_by` 与唯一/查询索引。
  - REST under `/api/v1/iam/groups` and `/api/v1/iam/abac/conditions`，均要求现有 `oa:iam:admin`；组授权仍走 `/iam/grants`。
  - 不改变现有 grant JSON；`subjectType=USER_GROUP` 的 `subjectId` 使用 group id 字符串。
- Security and reliability:
  - tenant 条件贯穿 CRUD/解析；禁用/移除/收紧条件走 epoch。
  - 表达式最大 512 字符、最大条件数/分支数受限；求值不暴露 Spring context、Class、request/response 或 Bean。
  - 日志记录 condition id、permission、异常类型，不记录业务参数值。
- Observability:
  - 指标 `oa_iam_abac_evaluations_total`、`oa_iam_abac_denied_total`、`oa_iam_abac_errors_total`；管理预览返回条件分支摘要。
- Compatibility and migration:
  - USER_GROUP migration 后立即可用；没有组时不改变任何快照。
  - ABAC 默认 `OA_IAM_ABAC_ENABLED=false`，发布前 smoke 显式开启；启用前可先配置并审查条件。

## Implementation Sequence

1. V14、用户组 domain/mapper/service/API、失效协议（AC-01）。
2. USER_GROUP 快照/TTL/来源链与 focused integration tests（AC-02～04）。
3. ABAC condition CRUD、安全解析/evaluator、快照分支/codec/AOP（AC-05～08）。
4. PC 三视图、OpenAPI/golden、浏览器与发布 smoke（AC-09）。
5. 审查修复、全量 QA、文档/CI/交付报告（AC-10）。

## Verification Plan

| AC/Risk | Test level | Case or command | Required evidence |
| --- | --- | --- | --- |
| AC-01～04 | unit/integration | `mvn -pl oa-iam,oa-app -am test` + Phase 9 smoke | 组 CRUD/成员时间窗/授权/撤权/why |
| AC-05～08 | adversarial/unit | evaluator, aspect, snapshot, codec tests | AND/OR/旁路、恶意表达式拒绝、无 DB 热路径 |
| AC-09 | frontend/e2e | PC Vitest + Playwright IAM spec | 三视图、错误态、1366/窄屏 |
| AC-10 | delivery | OpenAPI check、`mvn -B test`、PC build/size、compose/CI syntax | 完整日志与 QA 报告 |

## Documentation Plan

更新 README、API、ARCHITECTURE、RUNBOOK、权威实施进度；新增表达式变量/安全子集、组生命周期、
ABAC 开关/回滚和排障说明。

## CI Plan

沿用 GitHub Actions；新增测试自动被 Maven/PC job 覆盖。若新增独立 smoke 依赖本地服务，则作为发布前门禁记录，
不在无数据库服务的普通 CI 中伪执行。

## Rollout And Rollback

1. 先部署 additive migration 和代码，保持 ABAC 关闭；创建测试组并验证组授权。
2. 配置条件后用沙盘预览，再在测试环境打开 `OA_IAM_ABAC_ENABLED=true`，运行 Phase 9。
3. 生产按租户开启并监控 deny/error；异常时关闭 ABAC 可恢复历史 RBAC，组可逐个禁用。
4. 不回滚 V14 表；代码回滚时新增表/列保持兼容且无副作用。

## Assumptions And Open Decisions

- “USER_GROUP”按 OA 内部显式成员组实现；规则自动算群、嵌套组不包含在本轮。
- ABAC 首版只使用方法入参与只读用户上下文，不为求值额外查询业务库。
- 多条件和多来源组合按本计划的 AND/OR 规则；ABAC 默认关闭以保证升级兼容。
- 若业务希望“报销金额”在办理待办时求值，需要后续设计对象属性提供器；当前 `/todos/{taskId}/complete`
  只有 taskId，擅自在权限切面查业务库会破坏热路径和模块边界。

## Approval

- Status: approved
- Approved scope: explicit-member USER_GROUP, restricted-ABAC semantics, PC/API/tests/docs/QA in this plan.
- Evidence: 2026-08-21 user message “继续” after Gate A summary of the three material semantics.
