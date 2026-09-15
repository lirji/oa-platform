# IMPLEMENTATION_SLICES

- Artifact: `IMPLEMENTATION_SLICES`
- Owner: `implementation-slicing`
- Preserve IDs. 状态字段由 `update-progress-docs` 更新。

总原则：每片可观察、一次 pass、先契约后 UI。Brownfield 不重做 S0 脚手架。

| ID | 可观察结果 | needs | owner | contracts | migration | runtime | parallel | status |
|---|---|---|---|---|---|---|---|---|
| S01 | 身份目录可列出 Human 投影与可创建 NHI；详情含类型/状态/标签 | — | cross-cutting | Identity CRUD | V110 oa_iam | 无新组件 | no | DONE |
| S02 | `POST /authz/check` 对 USER+权限点给出 ALLOW/DENY+policyId，并留下决策审计 | S01 | backend+frontend | Check + decisions | V111 | 无 | no | DONE |
| S03 | 身份详情可看 1 跳图谱（部门/属主/角色） | S01 | backend+frontend | graph | V112 | 无 | no | DONE |
| S04 | 提交权限申请→审批→grant 生效可在 why 中看到 APPROVAL 来源 | S02 | cross-cutting | access-requests | V113 | 复用 Kafka/outbox 仅当走远程流程 | no | DONE |
| S05 | 权限委托在时间窗内使被委托人通过 Check；过期即 DENY；与待办委托互不干扰 | S02 | backend+frontend | permission-delegations | V114 | 无 | no | DONE |
| S06 | NHI 生命周期状态机 + credential 哈希/轮换/吊销；明文只出现一次 | S01 | backend+frontend | credentials | V115 | 无 | no | DONE |
| S07 | MOCK 全量/增量同步幂等 upsert，重复同步不双份身份 | S01 | backend | sync jobs | V116 | job-service 可选 | no | DONE |
| S08 | `who-has-access?permCode=` 返回身份+来源 | S02,S03 | backend+frontend | who-has-access | 可能无新表 | 无 | no | DONE |
| S09 | 跑规则产生 OPEN finding（长期未用、离职残留、Agent 无主等） | S06,S08 | backend+frontend | risk findings | V117 | job-service | no | DONE |
| S10 | Agent 作为 Principal Check INVOKE；不继承 Owner；调用可审计 | S02,S06 | cross-cutting | Agent check | V118 若需 invocation 表 | 无 | no | DONE |

## S01 详情

**可观察**：管理员打开 `/iam`「身份目录」，能看到由员工投影的 USER，能创建 SERVICE_ACCOUNT，能改标签与状态。

**本 pass 范围**

- 表 `identity` / `identity_label` + 权限点 + 员工投影 + NHI CRUD
- GET 列表/详情，POST/PUT/status/labels
- 入职事件投影、离职 DISABLE
- 控制台 Tab + 详情页
- 单测 + Golden + `pnpm gen:perm`
- **不含** Check API、图谱、申请、委托、凭证、同步、风险、Agent 工具调用（创建 AGENT 行可以，Check 走 S10）

**验收**

1. 迁移后现有 ACTIVE 员工有且仅有一条 USER identity，`externalKey=user_id`
2. LEFT 员工投影为 DISABLED
3. POST USER 返回 1400
4. POST SERVICE_ACCOUNT 成功；重复 externalKey 1409
5. 无 `oa:iam:identity:view` 的用户 1403
6. 列表游标稳定；DTO 非 Map
7. SQL 仅在 Mapper XML
8. Golden 含新端点；`pnpm gen:perm --check` 通过
9. 前端 Tab 能列出并创建 NHI；403/空/错误态可区分
10. 不改 PermissionEngine 热路径行为（现有 IAM 单测仍绿）

**影响路径**：`oa-iam`, `oa-org`（仅 api 事件）, `oa-common` ResultCode, `oa-app` golden, `oa-console`

## 后续片验收（摘要）

- S02：USER 持有权限 ALLOW；未持有 DENY；admin 代查；越权 check 他人 1403；决策表有行；热路径单测不退化
- S05：待办委托仍不能让被委托人调管理接口
- S10：Owner ALLOW 时 Agent 无授权仍 DENY

## Runtime

任何切片默认 **不** 改 compose。首次需要新端口/组件时才走 `runtime-and-deploy`（本期预计不需要）。
