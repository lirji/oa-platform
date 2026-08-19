# 接口一览

> 本文由源码抽取生成，与 `@RequiresPerm` 注解保持一致。
> 权限点目录在各模块的迁移文件里（`V10__iam.sql` / `V21__flow_perms.sql` / `V31__attendance_perms.sql`）——
> **目录里少一条，对应接口就永远判不过**：判权时找不到 code 一律拒绝，不会静默放行。
>
> 所有响应统一包在 `Result{code,message,data,traceId}` 里，`code:0` 为成功。
> 常见错误码：`1401` 未认证 · `3001` 权限不足 · `3002` 需要临时提权 ·
> `2002` 组织成环 · `2011` 主岗冲突 · `5002` 假期额度不足。



## 系统（`oa-app`）

| 方法 | 路径 | 权限点 |
|---|---|---|
| `GET` | `/api/v1/system/ping` | `（公开）` |

## 考勤（`oa-attendance`）

| 方法 | 路径 | 权限点 |
|---|---|---|
| `GET` | `/api/v1/attendance/admin/stats` | `oa:attendance:admin` |
| `POST` | `/api/v1/attendance/daily-compute` | `oa:attendance:admin` |
| `GET` | `/api/v1/attendance/me` | `oa:attendance:view` |
| `POST` | `/api/v1/attendance/punch` | `oa:attendance:punch` |

## 审批与工作台（`oa-flow`）

| 方法 | 路径 | 权限点 |
|---|---|---|
| `GET` | `/api/v1/flow/admin/status` | `oa:flow:admin` |
| `POST` | `/api/v1/flow/leave` | `oa:leave:apply` |
| `GET` | `/api/v1/flow/leave/balances` | `oa:leave:apply` |
| `POST` | `/api/v1/flow/leave/balances/grant` | `oa:leave:grant` |
| `GET` | `/api/v1/flow/leave/types` | `oa:leave:apply` |
| `GET` | `/api/v1/flow/leave/{requestNo}` | `oa:leave:view` |
| `GET` | `/api/v1/flow/todos` | `oa:flow:todo:view` |
| `GET` | `/api/v1/flow/todos/count` | `oa:flow:todo:view` |
| `POST` | `/api/v1/flow/todos/{taskId}/complete` | `oa:flow:todo:handle` |

## 权限中心（`oa-iam`）

| 方法 | 路径 | 权限点 |
|---|---|---|
| `GET` | `/api/v1/iam/admin/bench` | `oa:iam:admin` |
| `GET` | `/api/v1/iam/admin/cache-stats` | `oa:iam:admin` |
| `GET` | `/api/v1/iam/admin/explain` | `oa:iam:admin` |
| `POST` | `/api/v1/iam/admin/reclaim-expired` | `oa:iam:admin` |
| `GET` | `/api/v1/iam/delegations` | `oa:iam:delegate` |
| `POST` | `/api/v1/iam/delegations` | `oa:iam:delegate` |
| `DELETE` | `/api/v1/iam/delegations/{id}` | `oa:iam:delegate` |
| `POST` | `/api/v1/iam/elevations` | `oa:iam:elevate` |
| `GET` | `/api/v1/iam/grants` | `oa:iam:view` |
| `POST` | `/api/v1/iam/grants` | `oa:iam:grant` |
| `DELETE` | `/api/v1/iam/grants/{grantId}` | `oa:iam:revoke` |
| `GET` | `/api/v1/me/permissions` | `（公开）` |

## 组织人事（`oa-org`）

| 方法 | 路径 | 权限点 |
|---|---|---|
| `GET` | `/api/v1/org/directory` | `oa:employee:view` |
| `GET` | `/api/v1/org/directory/count` | `oa:employee:view` |
| `POST` | `/api/v1/org/employees` | `oa:employee:create` |
| `DELETE` | `/api/v1/org/employees/assignments/{assignmentId}` | `oa:employee:transfer` |
| `GET` | `/api/v1/org/employees/by-user/{userId}` | `oa:employee:view` |
| `GET` | `/api/v1/org/employees/by-user/{userId}/as-of` | `oa:employee:view` |
| `GET` | `/api/v1/org/employees/by-user/{userId}/assignments` | `oa:employee:view` |
| `GET` | `/api/v1/org/employees/by-user/{userId}/manager-chain` | `oa:employee:view` |
| `PUT` | `/api/v1/org/employees/{employeeId}` | `oa:employee:update` |
| `POST` | `/api/v1/org/employees/{employeeId}/assignments` | `oa:employee:transfer` |
| `POST` | `/api/v1/org/employees/{employeeId}/leave` | `oa:employee:leave` |
| `PUT` | `/api/v1/org/employees/{employeeId}/reporting-line` | `oa:employee:transfer` |
| `POST` | `/api/v1/org/employees/{employeeId}/transfer` | `oa:employee:transfer` |
| `DELETE` | `/api/v1/org/seed` | `oa:org:admin` |
| `POST` | `/api/v1/org/seed` | `oa:org:admin` |
| `POST` | `/api/v1/org/units` | `oa:org:create` |
| `GET` | `/api/v1/org/units/cache-stats` | `oa:org:admin` |
| `GET` | `/api/v1/org/units/consistency` | `oa:org:admin` |
| `POST` | `/api/v1/org/units/path-prefixes` | `oa:org:view` |
| `GET` | `/api/v1/org/units/tree` | `oa:org:view` |
| `DELETE` | `/api/v1/org/units/{orgId}` | `oa:org:dissolve` |
| `GET` | `/api/v1/org/units/{orgId}` | `oa:org:view` |
| `PUT` | `/api/v1/org/units/{orgId}` | `oa:org:update` |
| `GET` | `/api/v1/org/units/{orgId}/ancestors` | `oa:org:view` |
| `GET` | `/api/v1/org/units/{orgId}/descendants` | `oa:org:view` |
| `PUT` | `/api/v1/org/units/{orgId}/parent` | `oa:org:move` |
