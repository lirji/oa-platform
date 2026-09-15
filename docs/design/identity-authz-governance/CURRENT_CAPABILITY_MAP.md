# 当前能力地图

对照目标 IAM/AuthZ 平台扫描本仓库。证据均为源码/迁移/文档路径，不把改名当成新能力。

## 1. 能力总表

| 域 | 现状 | 关键证据 |
|---|---|---|
| 用户体系 | Casdoor `sub` + `oa_org.employee.user_id` | `UserContext.java`, `V2__org.sql` |
| 组织/部门 | 单表 + 闭包 + 物化路径 + 任职拉链 | `docs/ARCHITECTURE.md` §1, `oa-org` |
| 角色 | RBAC1 继承；在线角色管理；内置不可乱改 | `RoleAdminService`, `V16__role_management.sql` |
| 权限点 | MENU/BUTTON/API/DATA/FIELD 目录；67 个 code | `V10__iam.sql` + 后续 perm 迁移 |
| 菜单/按钮 | 后端 menus 下发；前端 `PermRoute`/`Can` 仅体验 | `MeController`, `AppLayout.tsx` |
| 数据权限 | `@DataScope` + `org_path LIKE`；部分表已严格迁移 | `AUTHORIZATION.md` §5 |
| 字段权限 | `@Sensitive` | `AUTHORIZATION.md` §6 |
| OA 审批 | 请假/公文 + 流程中台；Outbox 发起 | `oa-flow`, `RemoteWorkflowGateway` |
| JIT 提权 | 四眼审批 `elevation_request` → TEMPORARY grant | `V17__elevation_approval.sql` |
| 待办委托 | `oa_iam.delegation`，不叠加 RBAC | `DelegationAuthorizationService` |
| 授权记录 | `grant_record`（永久/临时） | `GrantService` |
| HTTP 审计 | `oa_sys.audit_log` SUCCESS/DENIED/FAILED | `AuditRecordingAspect` |
| SSO/认证 | Casdoor OIDC + JWT；DEV 才 `X-OA-User` | `OaSecurityConfig`, `AppAuthProvider.tsx` |
| 多租户 | `tenant_id` 列 + 默认租户 1；无 RLS | `AUTHORIZATION.md` |
| Redis | L2 权限快照、打卡幂等、WS ticket | `PermissionEngine`, punch path |
| MQ | Kafka + `oa_flow.oa_outbox` + `oa_iam.iam_outbox` | `OutboxPublisher`, `IamRoleEventPublisher` |
| 定时任务 | Spring `@Scheduled`；oa-job-service | `OaJobs` grant expire 等 |
| 前端管理 | `/iam` 授权/角色/组/ABAC/提权审批；`/iam/sandbox` | `GrantsPage.tsx`, `SandboxPage.tsx` |
| 权限可见性 | `/me/permissions`, `preview`, `why`, `explain` | `IamAdminController` |
| ABAC | 受限 SpEL，默认关闭 | `AbacEvaluator`, `OA_IAM_ABAC_ENABLED` |
| 对象 ACL | 知识库 Local 授权；SpiceDB 骨架 | `LocalKbAuthorizer` |

## 2. 认证 vs 授权（已分离）

```
Authentication: Casdoor JWT → UserContext.userId (sub)
Authorization:  RequiresPermAspect → PermissionEngine + AbacEvaluator + JIT
                → AuthorizationContext → DataScope / Sensitive
```

登录成功 ≠ 业务权限。此项已符合硬约束 §15。

## 3. 现有 PEP / PDP / PIP 映射

| 概念 | 当前落点 | 缺口 |
|---|---|---|
| PEP | `RequiresPermAspect`、前端 PermRoute（非安全边界）、KB authorizer | 无统一 Check API；无 Agent Tool PEP |
| PDP | `PermissionEngine` + `AbacEvaluator` | 无显式 Decision DTO（policyId/reason）；无独立 Policy Store 版本 |
| PIP | `UserContext` + OrgQueryApi + 方法参数 | 无 Identity attributes/labels/risk 进入热路径 |
| Policy Store | `permission` / `role_permission` / `permission_condition` | 无 `policy_version`；条件是 SpEL 字符串 |

## 4. 前端 IA（治理相关）

```
/iam            GrantsPage  tabs: 主体授权 / 角色 / 用户组 / ABAC / 提权审批
/iam/sandbox    有效权限 + 来源链 + 缓存一致性
/org/*          组织树 / 员工 / 通讯录（人，不是统一 Identity）
/report/audit   HTTP 审计
```

无：身份目录、NHI/Agent、权限申请单、权限委托管理 UI、风险发现、决策审计、反向「谁能访问该资源」。

## 5. 部署与中间件

见 `docs/DATABASES_AND_COMPONENTS.md`。本期不改变 compose 拓扑。
