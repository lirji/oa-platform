# BACKEND_ARCHITECTURE · 身份与授权治理

- Artifact: `BACKEND_ARCHITECTURE`
- Owner: `backend-architecture-design`
- Consumes: `BRIEF`, 现有 `docs/ARCHITECTURE.md` / `docs/ADR.md`

## 1. 目标与非目标

**目标**：在现有 OA 上叠加企业级身份与授权治理能力，使平台能回答 BRIEF 的 12 个问题。

**非目标**：换数据库、拆微服务、重写 PermissionEngine 热路径、把 OA 业务迁出仓库。

## 2. 模式与约束

Brownfield。兼容：JDK21、Spring Boot 3.3.5、PostgreSQL、Redis、Kafka、Casdoor、模块化单体 + 3 sidecar。
限界上下文 ≠ 微服务。

## 3. Facts / Assumptions / Decisions / Risks

见 `BRIEF.md` 与 `DECISION_RECORD.md`。关键决策：D-GOV-001..013。

## 4. 领域 / 限界上下文 / 部署边界

### 4.1 战略设计

| 子域 | 类型 | 限界上下文 | 本期部署 |
|---|---|---|---|
| 认证 | 支撑 | Authentication（Casdoor，外部系统） | 外部 |
| 身份 | 核心 | Identity | `oa-iam` 模块 |
| 组织 | 支撑（已有） | Organization | `oa-org`（不改所有权） |
| 授权决策 | 核心 | Authorization | `oa-iam`（抽出 DecisionEngine） |
| 策略 | 核心 | Policy | `oa-iam` 内模块；一期仍用 role+condition |
| 授权记录 / 申请 | 核心 | Entitlement | `oa-iam` + 复用 `oa-flow` 审批 |
| 风险 | 支撑 | Risk | `oa-iam` 包，不独立进程 |
| 审计 | 支撑 | AuthzAudit | `oa-iam` 决策日志 + 已有 `oa-report` HTTP 审计 |
| 同步 | 支撑 | IdentitySync | `oa-iam` Connector；无独立服务 |
| Agent | 核心（后期切片） | AgentIdentity | 同一 Identity 上下文 |
| OA 业务 | 通用 | Attendance/Doc/Admin/Notify | 保持消费方 |

### 4.2 Context Mapping

```
Casdoor (ACL) --JWT--> Authentication adapter --UserContext--> Authorization
Organization (OHS) --Employee*Event / OrgQueryApi--> Identity (Customer)
Identity --PrincipalId--> Authorization
Policy --candidates--> Authorization
Risk --findings / riskLevel--> Identity + Authorization (PIP, 非热路径强依赖)
Entitlement --grant_record--> Authorization snapshot rebuild
oa-flow (ACL) <--AccessRequest start/complete--> Entitlement
OA 业务 PEP --RequiresPerm / Check--> Authorization
```

### 4.3 为什么不拆独立服务

建议评估的 `identity-service` / `authorization-service` / `policy-service` 等：

| 候选 | 结论 | 理由 |
|---|---|---|
| identity-service | **暂不拆** | 与 org 投影、grant 主体强一致；拆开后入职/离职/授权需要分布式事务 |
| authorization-service | **暂不拆** | 热路径必须进程内内存判定（ADR-0002/0005）；远程 PDP 会摧毁现网模型 |
| policy-service | **暂不拆** | 一期策略量小，与角色权限同事务发布 |
| access-request-service | **暂不拆** | 复用 oa-flow；独立进程无发布/团队边界证据 |
| risk-service | **暂不拆** | 一期规则检测，与授权读模型同库更简单 |
| identity-sync-service | **暂不拆** | 无真实外部系统；Connector 接口即可 |
| audit-service | **暂不拆** | 已有 oa-report；决策日志先写 oa_iam |
| agent-identity-service | **暂不拆** | Agent 是 Identity 的一种 type |

独立出去的仍只有：通知（连接数）、文件（IO）、跑批（CPU）—— 已存在，不扩大。

远期若出现「多产品共用 PDP、独立 SLA、独立团队」，再把 Authorization 以 **只读副本 + Check API** 方式抽进程，且 Human 热路径仍可留在各产品 PEP 的本地快照。

## 5. 数据 Source of Truth

| 数据 | 权威 | 写入者 | 缓存 |
|---|---|---|---|
| Human 人事 | `oa_org.employee` | oa-org | OrgTree / 查询 |
| Identity 目录 | `oa_iam.identity` | oa-iam（投影+NHI CRUD） | 无默认 L1（非热路径） |
| 角色/权限/授权 | `oa_iam.*` 现表 | oa-iam | PermissionSnapshot L1/L2 |
| ABAC 条件 | `permission_condition` | oa-iam | 打进快照 |
| 待办委托 | `oa_iam.delegation` | oa-iam | 快照 delegators |
| 权限委托 | 新表 `permission_delegation` | oa-iam | 进快照或 Check |
| Access Request | 新表 `access_request` | oa-iam | 无 |
| 审批实例 | `oa_flow.approval_instance` | oa-flow | 现有 |
| 决策审计 | `oa_iam.authz_decision_log` | oa-iam | 只写 |
| Credential | `oa_iam.credential`（哈希） | oa-iam | 无；明文永不落库 |
| Risk | `risk_rule` / `risk_finding` | oa-iam | 无 |
| 有效权限 | **计算读模型** PermissionSnapshot | builder | L1/L2；DB 授权仍是写权威 |

Redis **不是**身份/权限/Policy 的写权威。

## 6. 同步 API / 异步事件

**同步**

- 治理 CRUD：Identity / Grant / Policy / AccessRequest / Delegation / Risk
- `POST /api/v1/authz/check`：决策
- 现有 `/iam/admin/why|preview|explain` 保留

**异步（仅有真实跨进程副作用时）**

- 已有：`iam.role.changed.v1`、流程 StartProcess
- 新增（有订阅方时才发）：IdentityChanged、AuthorizationGranted/Revoked、AccessRequestDecided、RiskFindingOpened
- 机制：沿用 `iam_outbox` / `oa_outbox`，禁止「提交后再裸 send」作为唯一通道

Human 判权热路径 **不**走 MQ。

## 7. 一致性、幂等、失败恢复

- Identity 投影：员工事件与 identity upsert 同进程；监听失败可凭 `subject_id` 重放（幂等 unique）。
- 授权生效：审批通过后写 `grant_record` + 可选 outbox，grant 以 `(subject, role, valid window, approval_instance_id)` 去重。
- Check API：只读；重复调用同一决策不产生副作用（审计行允许重复，用 `commandId` 去重可选）。
- 离职：已有收权协议必须继续走 API/服务层以 bump epoch，禁止裸 SQL。

## 8. 安全 / 授权 / 租户

- 认证入口仍在各服务 JWT 过滤器（nginx 只做路由，不是 PDP）。
- 新 handler 必须 `@RequiresPerm`；Check API 本身需要调用者身份，且默认只能 check 自己，除非 `oa:iam:admin`。
- Agent Check：Principal 必须是 Agent identity，Owner 权限不得默认继承。
- 租户：沿用 tenant_id=1（A1）。

## 9. 运行时依赖与模块图

```
oa-console nginx → oa-app (oa-iam/oa-org/oa-flow/...)
                 → oa-notify / oa-file / oa-job
oa-app → PostgreSQL, Redis, Kafka, Casdoor JWKS, workflow-platform
```

本期不新增进程、端口、broker、数据库引擎。

## 10. 可观测

沿用 actuator health/info/metrics。新增指标（实现时再挂）：

- `oa_authz_check_total{decision}`
- `oa_authz_decision_audit_total`
- `oa_identity_sync_lag`（有 sync 切片时）

不编造 SLO 数字。Tracing 仍缺失：本期不新上 OTel（未达 Complexity Budget），继续用现有 `traceId` 字段透传。

## 11. CONTRACTS 必须承接

见 `CONTRACTS.md`：Identity CRUD、Check、Decision 形态、错误码、游标分页、幂等、权限点。

## 12. 未决问题

无 HOLD。Policy 一等聚合推迟到 DecisionEngine 稳定之后（S02 用合成 policyId）。
