# CONTRACTS · 身份与授权治理

- Artifact: `CONTRACTS`
- Base URL: `/api/v1`
- 统一包装: `{ code, message, data, traceId }`（`Result<T>`）
- 成功 `code=0`。未认证 1401，无权限 1403，资源不存在 1404，冲突 1409。
- 时间：ISO-8601 timestamptz，UTC。
- 主体：Human 对外仍可见 `subjectId`=Casdoor sub；平台身份主键为 `identityId`（UUID 字符串）。
- 列表：游标分页，禁止 offset。
- 结果：类型化 DTO，禁止 `Map<String,Object>`。
- 每个 handler：`@RequiresPerm` 或 `@PublicApi`；权限点必须出现在 IAM 迁移。
- 改路径或权限点必须更新 `oa-app/src/test/resources/api-surface.golden`。

新增错误码（3xxx）：

| code | 枚举 | 语义 |
|---|---|---|
| 3010 | IDENTITY_NOT_FOUND | 身份不存在 |
| 3011 | IDENTITY_TYPE_IMMUTABLE | 类型创建后不可改 |
| 3012 | IDENTITY_STATUS_CONFLICT | 非法状态迁移 |
| 3013 | NHI_OWNER_REQUIRED | Agent 必须有 Owner |
| 3020 | AUTHZ_CHECK_INVALID | Check 请求不完整 |
| 3030 | ACCESS_REQUEST_INVALID | 申请单不合法 |
| 3040 | DELEGATION_PERM_INVALID | 权限委托不合法 |
| 3050 | CREDENTIAL_REVOKED | 凭证已吊销 |

---

## S01 Identity

### IdentityView

```
identityId, tenantId, identityType, displayName, source, status,
externalKey, employeeId, orgId, orgPath,
riskLevel, ownerIdentityId, expiredAt, version,
labels[], createdAt, updatedAt
```

`identityType`: `USER | SERVICE_ACCOUNT | API_CLIENT | APPLICATION | AGENT | BOT | AUTOMATION_WORKER`  
`status`: `CREATED | ACTIVE | SUSPENDED | DISABLED | EXPIRED | DELETED`  
`source`: `INTERNAL | CASDOOR | HR | LDAP | API | MOCK`  
`riskLevel`: `LOW | MEDIUM | HIGH | CRITICAL`

### GET `/iam/identities`

- Perm: `oa:iam:identity:view`
- Query: `type?`, `status?`, `q?`（匹配 displayName/externalKey，不含中文拼进 URL 以外的方案：前端 encode）、`cursor?`（上一页最后 `seq`）、`size?` default 50 max 200
- Data: `{ items: IdentityView[], nextCursor: string, hasMore: boolean }`
- `nextCursor` 无更多时为 `""`

### GET `/iam/identities/{identityId}`

- Perm: `oa:iam:identity:view`
- 404/3010 若不存在或已 DELETED（对外当不存在）

### POST `/iam/identities`

- Perm: `oa:iam:identity:admin`
- Body: `{ identityType, displayName, externalKey, ownerIdentityId?, expiredAt?, labels?, attributes? }`
- `identityType=USER` 拒绝（Human 只能由员工投影创建）→ 1400
- Agent：`ownerIdentityId` 必填且必须是 USER → 3013
- 201 语义：HTTP 200 + Result，与本仓库其它写接口一致（不改全局 201 习惯）
- 冲突：同一 `(tenantId, identityType, externalKey)` → 1409
- Idempotency: 无 header；冲突即幂等读已存在资源不自动返回（避免误更新），调用方改 externalKey 或走 PUT

### PUT `/iam/identities/{identityId}`

- Perm: `oa:iam:identity:admin`
- Body: `{ displayName?, expiredAt?, ownerIdentityId?, version }`
- `version` 必填乐观锁；不匹配 1409
- 不可改 `identityType`、`externalKey`、`employeeId`

### POST `/iam/identities/{identityId}/status`

- Perm: `oa:iam:identity:admin`
- Body: `{ status, version }`
- 允许：ACTIVE↔SUSPENDED；ACTIVE/SUSPENDED→DISABLED；DISABLED→ACTIVE；→DELETED 仅 DISABLED/EXPIRED
- USER 的 DISABLED 不替代员工离职流程；离职由组织事件投影

### PUT `/iam/identities/{identityId}/labels`

- Perm: `oa:iam:identity:admin`
- Body: `{ labels: string[], version }`
- 全量替换；合法值：`EMPLOYEE CONTRACTOR ADMIN PRIVILEGED SERVICE_ACCOUNT AGENT EXTERNAL HIGH_RISK`

Human 投影规则（只读侧约定）：

- 入职 → USER + ACTIVE + label EMPLOYEE 或 CONTRACTOR
- 离职 → DISABLED，不删除行
- 调岗 → 更新 orgId/orgPath

---

## S02 Authorization Check + Decision Audit

### POST `/authz/check`

- Perm: `oa:iam:check`（自己的 principal）或 `oa:iam:admin`（任意 principal）
- Body:

```
{
  "principal": { "identityId": "...", "identityType": "USER|AGENT|..." },
  "resource":  { "type": "API|TOOL|DOC|...", "id": "...", "attributes": {} },
  "action":    "READ|WRITE|INVOKE|...",
  "environment": { "traceId"?, "tool"?, "now"? },
  "commandId"? 
}
```

- Human + `resource.type=API` 且 `resource.id` 为权限点 code 时：走现有 PDP 内存快照（含 ABAC/JIT）。
- Agent：不得加载 Owner 的 permBits；只评估 Agent 自己的授权。
- Data:

```
{
  "decision": "ALLOW" | "DENY",
  "policyId": "rbac:{roleCode}" | "abac:{conditionId}" | "default:deny",
  "reason": "string",
  "traceId": "string",
  "principalId": "identityId",
  "evaluatedAt": "timestamptz"
}
```

- 决策审计：每次 Check 写 `authz_decision_log`（失败不得阻断决策；记 error 指标）。
- HTTP `@RequiresPerm` 路径**不**同步写决策审计（避免热路径 IO）；继续走 `audit_log`。

### GET `/iam/admin/decisions`

- Perm: `oa:iam:admin`
- Query: `identityId?`, `decision?`, `from?`, `to?`, `cursor?`, `size?`
- Data: 决策审计列表（类型化）

合成 `policyId` 规则（S02，无独立 Policy 表时）：

- RBAC 命中：`rbac:{grantedRoleCode}`
- ABAC 收窄通过：`abac:{conditionId}`
- 默认拒绝：`default:deny`

---

## S03 Identity Graph

### GET `/iam/identities/{id}/graph`

- Perm: `oa:iam:identity:view`
- Data: `{ nodes, edges }` 1 跳（belongs_to / owns / has_role 只读投影）
- 写边不开放给前端；由身份/授权/组织事件维护

边类型：`BELONGS_TO | OWNS | HAS_ROLE | DELEGATES | ACCESSES`

---

## S04 Access Request

### POST `/iam/access-requests`

- Perm: `oa:iam:request`
- Body: `{ requestType, roleId?, permCodes?, validTo?, reason, resourceScope? }`
- `requestType`: `ROLE | TEMPORARY | DELEGATION | HIGH_RISK`
- 创建后进入审批（复用 oa-flow 或 elevation 同款本地四眼，由切片选定一种并在实现证据写明）
- 批准后写 `grant_record`，`source=APPROVAL`，`approval_instance_id` 必填
- 同一 `commandId`/业务键幂等

### GET `/iam/access-requests/mine`  perm `oa:iam:request`
### GET `/iam/access-requests` perm `oa:iam:admin`

---

## S05 Permission Delegation

与现有 `POST /iam/delegations`（待办代理）**并存，不混用**。

### POST `/iam/permission-delegations`

- Perm: `oa:iam:delegate`
- Body: `{ delegateeIdentityId, permCodes|roleIds, resourceScope?, validFrom, validTo, reDelegate:false, reason }`
- 委托人必须自己有效持有被委托权限
- 默认禁止再次委托
- 过期靠时间窗，不靠任务删除；任务只做回收标记

---

## S06 Lifecycle + Credential

### POST `/iam/identities/{id}/credentials`

- Perm: `oa:iam:identity:admin`
- 返回 **一次性** secret 明文仅此响应；库内仅哈希 + last4
- 之后 GET 永不回明文

### POST `/iam/credentials/{id}/revoke`  `/rotate`

Secret 不入日志。

---

## S07 Identity Sync

### POST `/iam/sync/jobs` perm `oa:iam:admin`
### GET `/iam/sync/jobs/{id}`

Connector：`INTERNAL`（员工投影）+ `MOCK`。无真实 LDAP。

---

## S08 Effective Permission 反向查询

### GET `/iam/admin/who-has-access`

- Perm: `oa:iam:admin`
- Query: `resourceType`, `resourceId` 或 `permCode`
- Data: 身份列表 + 来源（Direct/Role/Group/Delegation/Inherited）

现有 `why` / `preview` / `explain` **保持不变**。

---

## S09 Risk

### GET `/iam/risk/findings` perm `oa:iam:admin`
### POST `/iam/risk/findings/{id}/status`  OPEN→ACKNOWLEDGED|RESOLVED|IGNORED

规则一期内置，不提供复杂规则 DSL 编辑器。

---

## S10 Agent

复用 Identity `type=AGENT` + Check `action=INVOKE`。
Agent 调用写 `agent_invocation_audit`（可与 decision_log 同源或子表）。

---

## 事件（有订阅方才发）

| eventType | partition key | schemaVersion | 何时 |
|---|---|---|---|
| `iam.identity.changed.v1` | identityId | 1 | S06/S07 若有跨进程消费者 |
| `iam.authorization.granted.v1` | subjectId | 1 | S04 生效且需通知下游 |

S01 **不**发 Kafka（同进程投影足够）。

## 幂等 / 版本

- 写 Identity：`version` 乐观锁
- Check：只读
- Access Request / Grant：业务唯一键 + DB 约束
