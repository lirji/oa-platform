# Gap Analysis

对照提示词核心能力。状态：已有 / 部分已有 / 缺失 / 需要重构 / 建议新增。
**禁止**用重命名已有类来把「部分已有」写成「已有」。

| 目标能力 | 状态 | 现有 | 缺口 | 本期动作 |
|---|---|---|---|---|
| 统一 Identity / Principal（Human+NHI） | 缺失 | employee + Casdoor sub | 无 identity 表、无 identityType、无 Agent/ServiceAccount | **新增** identity 投影 + NHI 主数据 |
| Identity Graph | 部分已有 | org_closure、role_inherit、grant_record、delegation | 无统一 Node/Edge；无 owns/delegates/accesses 查询 | **新增** 关系表，一期 SQL 查询 |
| 统一授权请求 Check API | 缺失 | 仅 AOP + 权限点 | 无 (principal, resource, action, environment) | **新增** AuthZ Check，内部复用 PDP |
| RBAC | 已有 | 角色/继承/授权/位图 | 主体不含 NHI | 扩展 subject，不重做引擎 |
| ABAC | 部分已有 | 角色×权限点 SpEL | 无 Identity Label/Risk/Time/Device 作为一等属性；无 policy version | 扩展 Context；策略版本后期切片 |
| Authorization Decision Engine | 部分已有 | RequiresPermAspect 内联 | 未产品化为引擎 API；无 Conflict/DENY overlay | **抽出** DecisionEngine 门面，PEP 与 Check 共用 |
| Identity Label | 缺失 | employment_type 在员工表 | 不能进 ABAC | **新增** identity_label |
| 权限委托 Delegation | 部分已有 | 待办代理 | 不能把权限范围委托给另一 Principal | **新增** 权限委托（与 todo 委托分表或分 scope） |
| Identity Lifecycle | 部分已有 | 员工状态 + 离职收权 | 无 NHI 生命周期；Human 无 identity.status | **新增** identity.status 投影 |
| Credential | 缺失 | Casdoor 管人的密码 | 无 API Key/Agent secret 生命周期 | **新增** credential 哈希存储 |
| 多源同步 | 缺失 | 通讯录 delta 是客户端缓存 | 无 IdentitySource/Connector | **新增** 抽象 + MOCK |
| Who has access to what | 部分已有 | why/preview 按人 | 无按 Resource 反查 | **新增** 反向查询 |
| Risk Engine | 缺失 | — | 无规则/发现 | **新增** 规则检测 |
| Agent 授权 | 缺失 | — | Agent≠Principal | **新增** Agent 身份 + Tool Check |
| Access Request 工作流 | 部分已有 | JIT elevation + 通用 OA 流 | 无通用权限申请单与授权生效记录 | **新增** access_request，复用审批 |
| 决策审计 | 部分已有 | HTTP audit_log | 无 decision/policyId/reason 专用账 | **新增** authz_decision_log |
| Policy Store | 部分已有 | permission_condition | 无独立 Policy 聚合/版本/优先级 DENY | 后期切片；Check 先返回合成 policyId |
| 微服务拆分 | 不需要（本期） | 模块化单体 + 3 sidecar | 硬约束建议评估独立服务 | **暂不拆**（D-GOV-002） |
| MySQL | 不需要（本期） | PostgreSQL 16 | 硬约束写 MySQL | **不迁**（D-GOV-001） |
| Nacos/Apollo/SCG/Feign | 不需要（本期） | nginx + env + SDK | 硬约束目标架构 | **不引入**（D-GOV-003） |

## Mapping 表（提示词要求）

| 当前能力 | 可复用 | 需要改造 | 目标能力 |
|---|---|---|---|
| OA 审批 | 是 | 增加 Access Request 单据与授权生效 | Access Request |
| RBAC + 位图引擎 | 是 | 扩展 Principal；抽出 Decision 门面 | Authorization PDP |
| 用户/组织 | 是 | 投影为 Identity；不替换 employee | Identity |
| ABAC SpEL | 是 | 注入 Label/Risk/Environment | ABAC Condition |
| why/preview | 是 | 增加资源反查与 policyId | Effective Permission |
| HTTP 审计 | 部分 | 增加决策审计 | Decision Audit |
| 待办委托 | 是 | 保持不变；另做权限委托 | Workflow Delegation |
| 离职收权 | 是 | 同步 identity.status | Lifecycle |
| 定时 reclaim-expired | 是 | 覆盖 NHI/委托/申请 | Lifecycle |
| Kafka + Outbox | 是 | 仅在跨进程副作用时发身份/授权事件 | Identity/Permission events |
| 权限沙盘 UI | 是 | 扩展身份/Check/风险入口 | Governance Console |

## 明确「不是」的东西

- `DelegationAuthorizationService` **不是**权限委托。
- `permission_condition` **不是**带版本的 Policy Store。
- `audit_log` **不是** Authorization Decision Audit。
- `employee` **不是**统一 Identity（没有 NHI）。
- `PermissionChecker.has(userId, permCode)` **不是** (principal, resource, action) Check API。
