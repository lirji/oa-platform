# 接口清单

> **由源码抽取生成**（扫描全部 `@RestController` 的 `@RequestMapping` + 各 handler 的
> `@RequiresPerm` / `@PublicApi`），与代码保持一致。四个可部署后端共 **135 个 handler**。
>
> 这是一份**权限点对照表**：回答"这个接口需要什么权限"。
> 请求/响应的字段契约以 **`GET :8400/v3/api-docs`**（springdoc，含 75 个 schema）为准，
> 前端可直接用它生成 TS 类型。

## 通用约定

RBAC、ABAC、接口权限、数据权限和字段权限的执行顺序、管理方式与覆盖边界见
[权限体系与全局保障指南](AUTHORIZATION.md)。本页只维护端点与权限点对照。

- 统一响应体 `Result{ code, message, data, traceId }`，`code: 0` 表示成功。
  **例外**：`GET /api/v1/file/{id}/download` 返回裸二进制（`Content-Disposition` 按 RFC 5987 编码中文名），
  前端的响应拦截器要为它开例外。
- **HTTP 状态码与业务 code 是双轨的**，只判 status 会误判：
  403 里混了 3001/3002/3003/3004/3005，409 里混了 2002/2003/2011/5001。
- 每个响应都带 **`X-OA-Perm-Version`** 头（`epoch*1e6 + userVersion`）。
  前端比对它就知道权限变了 —— 零额外请求。403 上也带，能区分"权限刚被改"与"本来就没有"。
- **身份**：生产默认 JWT，校验 Casdoor 签名、`iss`、`aud` 后取 `jwt.sub`（UUID 字符串）；
  DEV 模式才读取 `X-OA-User`，JWT 模式对该头 fail-closed，不允许覆盖身份。
- **浏览器 WebSocket**：先用 Bearer JWT 调 `POST /api/v1/notify/ws-ticket`，再连接
  `/ws?ticket=<一次性票据>`。票据默认 30 秒、绑定用户/租户、Redis 原子消费且不可重放；
  禁止把 access token 或 `userId` 放进 URL。
- **`★需提权`** 表示该权限点 `require_elevation=true`：持有永久授权仍会被拒（3002），
  必须先 `POST /api/v1/iam/elevations` 申请一条活跃的 JIT 提权。
- **数据权限不抛错**：算不出范围时 SQL 生成 `1 = 0`，接口返回 **200 + 空数组**。
  前端不能把它当成"没有数据"——应结合 `/me/permissions` 的 `dataScope` 与 `scopePrefixes` 提示用户。

## 错误码

| code | 含义 | HTTP |
|---|---|---|
| 0 | 成功 | 200 |
| 1400 / 1401 / 1403 / 1404 / 1409 | 参数不合法 / 未认证 / 无权限 / 不存在 / 状态冲突 | 同名 |
| 2002 / 2003 / 2011 | 组织移动成环 / 组织下仍有成员 / 主岗冲突 | 409 |
| 3001 / 3002 / 3003 / 3004 / 3005 | 权限不足 / **需临时提权** / 授权过期 / 委托无效 / 超出数据权限 | 403 |
| 4001 / 4002 / 4003 | 流程发起失败 / 待办不存在 / 表单模板不合法 | — |
| 5001 / 5002 | 重复打卡 / 假期额度不足 | 409 / — |
| 9000 / 9001 | 系统内部错误 / 依赖服务不可用 | 500 |

---

## 系统　`oa-app`　:8400

| 方法 | 路径 | 权限点 |
|---|---|---|
| GET | `/api/v1/system/ping` | (公开) |

## 组织人事　`oa-org`　:8400

| 方法 | 路径 | 权限点 |
|---|---|---|
| GET | `/api/v1/org/directory` | oa:employee:view |
| GET | `/api/v1/org/directory/count` | oa:employee:view |
| GET | `/api/v1/org/directory/delta` | oa:employee:view |
| GET | `/api/v1/org/directory/page` | oa:employee:view |
| POST | `/api/v1/org/employees` | oa:employee:create |
| DELETE | `/api/v1/org/employees/assignments/{assignmentId}` | oa:employee:transfer |
| GET | `/api/v1/org/employees/by-user/{userId}` | oa:employee:view |
| GET | `/api/v1/org/employees/by-user/{userId}/as-of` | oa:employee:view |
| GET | `/api/v1/org/employees/by-user/{userId}/assignments` | oa:employee:view |
| GET | `/api/v1/org/employees/by-user/{userId}/manager-chain` | oa:employee:view |
| PUT | `/api/v1/org/employees/{employeeId}` | oa:employee:update |
| POST | `/api/v1/org/employees/{employeeId}/assignments` | oa:employee:transfer |
| POST | `/api/v1/org/employees/{employeeId}/leave` | oa:employee:leave |
| PUT | `/api/v1/org/employees/{employeeId}/reporting-line` | oa:employee:transfer |
| POST | `/api/v1/org/employees/{employeeId}/transfer` | oa:employee:transfer |
| DELETE | `/api/v1/org/seed` | oa:org:admin |
| POST | `/api/v1/org/seed` | oa:org:admin |
| POST | `/api/v1/org/units` | oa:org:create |
| GET | `/api/v1/org/units/cache-stats` | oa:org:admin |
| GET | `/api/v1/org/units/consistency` | oa:org:admin |
| POST | `/api/v1/org/units/path-prefixes` | oa:org:view |
| GET | `/api/v1/org/units/tree` | oa:org:view |
| DELETE | `/api/v1/org/units/{orgId}` | oa:org:dissolve |
| GET | `/api/v1/org/units/{orgId}` | oa:org:view |
| PUT | `/api/v1/org/units/{orgId}` | oa:org:update |
| GET | `/api/v1/org/units/{orgId}/ancestors` | oa:org:view |
| GET | `/api/v1/org/units/{orgId}/descendants` | oa:org:view |
| PUT | `/api/v1/org/units/{orgId}/parent` | oa:org:move |

## 权限中心　`oa-iam`　:8400

ABAC 接口的策略维度、表达式上下文和组合规则见 [ABAC 授权与管理指南](ABAC.md)。

| 方法 | 路径 | 权限点 |
|---|---|---|
| GET | `/api/v1/iam/admin/bench` | oa:iam:admin |
| GET | `/api/v1/iam/admin/cache-stats` | oa:iam:admin |
| GET | `/api/v1/iam/admin/explain` | oa:iam:admin |
| GET | `/api/v1/iam/admin/preview` | oa:iam:admin |
| POST | `/api/v1/iam/admin/reclaim-expired` | oa:iam:admin |
| GET | `/api/v1/iam/admin/why` | oa:iam:admin |
| DELETE | `/api/v1/iam/abac/conditions/{id}` | oa:iam:admin |
| GET | `/api/v1/iam/abac/conditions` | oa:iam:admin |
| POST | `/api/v1/iam/abac/conditions` | oa:iam:admin |
| PUT | `/api/v1/iam/abac/conditions/{id}` | oa:iam:admin |
| POST | `/api/v1/iam/abac/conditions/{id}/enabled` | oa:iam:admin |
| POST | `/api/v1/iam/abac/validate` | oa:iam:admin |
| GET | `/api/v1/iam/delegations` | oa:iam:delegate |
| GET | `/api/v1/iam/elevations/mine` | oa:iam:elevate |
| POST | `/api/v1/iam/delegations` | oa:iam:delegate |
| DELETE | `/api/v1/iam/delegations/{id}` | oa:iam:delegate |
| POST | `/api/v1/iam/elevations` | oa:iam:elevate |
| GET | `/api/v1/iam/grants` | oa:iam:view |
| POST | `/api/v1/iam/grants` | oa:iam:grant |
| DELETE | `/api/v1/iam/grants/{grantId}` | oa:iam:revoke |
| GET | `/api/v1/iam/groups` | oa:iam:admin |
| POST | `/api/v1/iam/groups` | oa:iam:admin |
| PUT | `/api/v1/iam/groups/{id}` | oa:iam:admin |
| POST | `/api/v1/iam/groups/{id}/enabled` | oa:iam:admin |
| GET | `/api/v1/iam/groups/{id}/members` | oa:iam:admin |
| POST | `/api/v1/iam/groups/{id}/members` | oa:iam:admin |
| DELETE | `/api/v1/iam/groups/{id}/members/{userId}` | oa:iam:admin |
| GET | `/api/v1/iam/permissions/catalog` | oa:iam:view |
| GET | `/api/v1/iam/roles` | oa:iam:view |
| GET | `/api/v1/iam/roles/mine` | oa:iam:elevate |
| GET | `/api/v1/me/permissions` | (公开) |

## 工作台与审批　`oa-flow`　:8400

| 方法 | 路径 | 权限点 |
|---|---|---|
| GET | `/api/v1/flow/admin/status` | oa:flow:admin |
| POST | `/api/v1/flow/docs` | oa:doc-flow:submit |
| GET | `/api/v1/flow/docs/mine` | oa:doc-flow:submit |
| GET | `/api/v1/flow/docs/types` | oa:doc-flow:submit |
| POST | `/api/v1/flow/leave` | oa:leave:apply |
| GET | `/api/v1/flow/leave/balances` | oa:leave:apply |
| POST | `/api/v1/flow/leave/balances/grant` | oa:leave:grant |
| GET | `/api/v1/flow/leave/types` | oa:leave:apply |
| GET | `/api/v1/flow/leave/{requestNo}` | oa:leave:view |
| GET | `/api/v1/flow/todos` | oa:flow:todo:view |
| GET | `/api/v1/flow/todos/count` | oa:flow:todo:view |
| GET | `/api/v1/flow/todos/mine` | oa:flow:todo:view |
| POST | `/api/v1/flow/todos/{taskId}/complete` | oa:flow:todo:handle |

## 考勤　`oa-attendance`　:8400

| 方法 | 路径 | 权限点 |
|---|---|---|
| GET | `/api/v1/attendance/admin/stats` | oa:attendance:admin |
| POST | `/api/v1/attendance/daily-compute` | oa:attendance:admin |
| GET | `/api/v1/attendance/me` | oa:attendance:view |
| POST | `/api/v1/attendance/punch` | oa:attendance:punch |

## 公文与知识库　`oa-doc`　:8400

| 方法 | 路径 | 权限点 |
|---|---|---|
| GET | `/api/v1/doc/kb` | oa:kb:read |
| POST | `/api/v1/doc/kb` | oa:kb:write |
| GET | `/api/v1/doc/kb/authorizer` | oa:kb:share |
| POST | `/api/v1/doc/kb/share` | oa:kb:share |
| GET | `/api/v1/doc/kb/{id}` | oa:kb:read |
| PUT | `/api/v1/doc/kb/{id}` | oa:kb:read |
| GET | `/api/v1/doc/kb/{id}/explain` | oa:kb:share |
| GET | `/api/v1/doc/official` | oa:doc:read |
| POST | `/api/v1/doc/official` | oa:doc:draft |
| POST | `/api/v1/doc/official/{id}/archive` | oa:doc:archive |
| POST | `/api/v1/doc/official/{id}/issue` | oa:doc:issue |

## 行政（会议室/资产/用品/车辆/访客）　`oa-admin-biz`　:8400

| 方法 | 路径 | 权限点 |
|---|---|---|
| GET | `/api/v1/admin-biz/assets` | oa:asset:read |
| POST | `/api/v1/admin-biz/assets/claim` | oa:asset:claim |
| POST | `/api/v1/admin-biz/assets/{id}/return` | oa:asset:claim |
| GET | `/api/v1/admin-biz/rooms` | oa:room:book |
| POST | `/api/v1/admin-biz/rooms/bookings` | oa:room:book |
| POST | `/api/v1/admin-biz/rooms/bookings/{id}/cancel` | oa:room:book |
| GET | `/api/v1/admin-biz/rooms/{roomId}/bookings` | oa:room:book |
| GET | `/api/v1/admin-biz/supplies` | oa:supply:request |
| POST | `/api/v1/admin-biz/supplies/requests` | oa:supply:request |
| GET | `/api/v1/admin-biz/vehicles` | oa:vehicle:book |
| POST | `/api/v1/admin-biz/vehicles/bookings` | oa:vehicle:book |
| GET | `/api/v1/admin-biz/visitors` | oa:visitor:invite |
| POST | `/api/v1/admin-biz/visitors` | oa:visitor:invite |
| POST | `/api/v1/admin-biz/visitors/{id}/check-in` | oa:visitor:manage |
| POST | `/api/v1/admin-biz/visitors/{id}/check-out` | oa:visitor:manage |

## 报表与审计　`oa-report`　:8400

| 方法 | 路径 | 权限点 |
|---|---|---|
| GET | `/api/v1/report/approval-efficiency` | oa:report:view |
| GET | `/api/v1/report/attendance` | oa:report:view |
| GET | `/api/v1/report/audit` | oa:audit:view ★需提权 |
| POST | `/api/v1/report/audit/flush` | oa:report:view |
| GET | `/api/v1/report/audit/stats` | oa:report:view |
| GET | `/api/v1/report/headcount` | oa:report:view |
| GET | `/api/v1/report/overview` | oa:report:view |

## 通知与公告（独立服务）　`oa-notify-service`　:8401

| 方法 | 路径 | 权限点 |
|---|---|---|
| GET | `/api/v1/announcements` | oa:announce:read |
| POST | `/api/v1/announcements` | oa:announce:publish |
| POST | `/api/v1/announcements/{id}/read` | oa:announce:read |
| POST | `/api/v1/announcements/{id}/revoke` | oa:announce:revoke |
| GET | `/api/v1/announcements/{id}/stats` | oa:announce:stats |
| GET | `/api/v1/notify/messages` | oa:notify:read |
| POST | `/api/v1/notify/messages` | oa:notify:send |
| POST | `/api/v1/notify/messages/read-all` | oa:notify:read |
| GET | `/api/v1/notify/messages/unread-count` | oa:notify:read |
| POST | `/api/v1/notify/messages/{id}/read` | oa:notify:read |
| GET | `/api/v1/notify/ping` | (公开) |
| POST | `/api/v1/notify/ws-ticket` | oa:notify:read |
| GET | `/api/v1/notify/ws-stats` | oa:notify:send |

## 文件服务（独立服务）　`oa-file-service`　:8402

| 方法 | 路径 | 权限点 |
|---|---|---|
| POST | `/api/v1/file` | oa:file:upload |
| GET | `/api/v1/file/mine` | oa:file:read |
| GET | `/api/v1/file/ping` | (公开) |
| DELETE | `/api/v1/file/{id}` | oa:file:upload |
| GET | `/api/v1/file/{id}/download` | oa:file:read |
| GET | `/api/v1/file/{id}/presign` | oa:file:read |

## 跑批服务（独立服务）　`oa-job-service`　:8403

| 方法 | 路径 | 权限点 |
|---|---|---|
| POST | `/api/v1/job/attendance-daily` | oa:job:run |
| POST | `/api/v1/job/grant-expire` | oa:job:run |
| POST | `/api/v1/job/leave-annual-grant` | oa:job:run |
| POST | `/api/v1/job/partition-roll` | oa:job:run |
| GET | `/api/v1/job/ping` | (公开) |
| GET | `/api/v1/job/runs` | oa:job:run |

---

## 权限点目录

全部 66 个权限点（含 9 个 MENU、2 个 FIELD、55 个 API）可在运行时查：

```
GET /api/v1/iam/permissions/catalog     # 需 oa:iam:view
```

返回含 `route` / `icon` / `sortOrder`（菜单元数据）与 `enabled`
（`false` = 目录中已定义但**尚无接口实现**，前端不应据此渲染入口）。

菜单树直接来自 `GET /api/v1/me/permissions` 的 `menus` 字段，已带路由与图标 ——
前端**不需要**再维护第二份菜单定义。

## OpenAPI 与 TypeScript 契约

`oa-app` 的字段级契约由 `GET /v3/api-docs` 提供。仓库固定了 96 条 path 的快照
`oa-console/openapi/oa-app.json`，并生成 `oa-console/src/shared/types/openapi.d.ts`：

```bash
cd oa-console
pnpm gen:api:fetch   # 后端运行时有意更新快照和类型
pnpm gen:api:check   # CI：快照重生成后必须零漂移
```

独立通知、文件和跑批服务的路径由本页与各服务 `api-surface.golden` 守护；当前 TS 快照仅针对主应用。
