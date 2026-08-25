# 用户权限加载与缓存预热链路

本文说明用户首次进入 OA Console 或 Mobile 后，权限如何从身份认证一路加载到前端，以及
`PermissionEngine`、员工缓存、权限目录和组织树分别在什么时候预热。权限模型、接口授权和
数据权限的完整说明见 [权限体系与全局保障指南](AUTHORIZATION.md)。

## 1. 结论

当前实现采用“全局基础数据启动预热 + 单用户权限首次访问懒预热”，不是服务启动时批量计算
全部用户权限：

- 服务启动时主动加载权限点目录和组织树。
- 用户权限 L1 Caffeine、员工信息 Caffeine 和任职关系 Caffeine 启动时为空。
- 用户首次发出已认证请求时，后端按 `L1 Caffeine -> L2 Redis -> L3 DB 重算` 获取权限快照。
- 对 `/api/v1/me/permissions` 而言，通常是 `PermVersionHeaderFilter` 在进入 Controller 前首次
  调用 `PermissionEngine.snapshot(userId)`，因此 Controller 随后的读取会命中 L1。
- L2 Redis 使用 `oa:perm:snap:{userId}`，已有快照可以跨应用进程重启复用，但应用不会在启动时
  扫描 Redis 并装入 L1。

## 2. Console 用户首次进入

### 2.1 前端启动与认证

Console 的 Provider 挂载顺序如下（`oa-console/src/main.tsx:19`）：

```text
QueryClientProvider
  -> AppAuthProvider
       -> AuthBridge
       -> PermBridge
       -> RouterProvider
```

其中：

1. `AppAuthProvider` 挂载 `react-oidc-context` 的 `AuthProvider`。OIDC 用户保存在
   `sessionStorage`，关闭标签页后清除（`oa-console/src/auth/oidcConfig.ts:11`）。
2. `AuthBridge` 只把 OIDC 身份同步进 `authStore`；OA 权限不读取 Casdoor groups，而以
   后端 `permCodes` 为准（`oa-console/src/auth/AuthBridge.tsx:14`）。
3. `ProtectedRoute` 只判断“是否已经认证”，页面授权由下一层 `PermRoute` 判断
   （`oa-console/src/auth/ProtectedRoute.tsx:22`）。
4. JWT 模式下，用户尚未认证时 `PermBridge` 的权限查询为 disabled；登录回调完成、
   `auth.isAuthenticated=true` 后才开始请求。DEV 模式不经过 OIDC，查询立即启用。

### 2.2 权限请求

`PermBridge` 使用 React Query 发起权限请求（`oa-console/src/auth/PermBridge.tsx:35`）：

```text
queryKey: ["me-permissions"]
GET /api/v1/me/permissions
```

请求经过统一 Axios 客户端，身份注入规则是：

| 模式 | 请求头 | 身份来源 |
|---|---|---|
| JWT | `Authorization: Bearer <token>` | Casdoor OIDC `sub` |
| DEV | `X-OA-User: <userId>` | `VITE_DEV_USER` 或测试覆写 |

实现位置为 `oa-console/src/shared/api/identity.ts:31`。开发环境由
`oa-console/vite.config.ts:36` 把 `/api/v1/**` 代理至 `oa-app:8400`；生产环境由
`oa-console/nginx.conf:46` 完成相同转发。

### 2.3 权限写入前端状态

接口成功后，权限存在两层前端内存状态：

```text
React Query ["me-permissions"]（权威数据源）
  -> PermBridge.useEffect
  -> permStore.applyPermissions()
  -> permCodes 转为 Set，并写入菜单、范围、版本、代理与提权信息
```

`permStore` 是供路由守卫、组件和非 React 代码同步读取的 Zustand 镜像，不是第二个权威源
（`oa-console/src/store/permStore.ts:5`）。当前没有把用户权限持久化到 localStorage 或 IndexedDB，
浏览器刷新后会重新请求。

主要消费点：

- `AppLayout` 使用后端返回的 `menus` 渲染导航菜单（`oa-console/src/components/layout/AppLayout.tsx:27`）。
- `PermRoute` 在 `loaded=false` 时显示 Skeleton，加载完成后才判断 403
  （`oa-console/src/auth/PermRoute.tsx:18`）。
- 页面和按钮通过 `usePerm()` 统一判定权限（`oa-console/src/auth/usePerm.ts:32`）。
- 与调用者数据范围有关的业务 Query Key 通过 `usePermVersion()` 带上权限版本，避免收权后复用
  旧查询结果（`oa-console/src/auth/usePerm.ts:28`）。

主应用壳和权限请求是并行挂载的，所以首次进入首页时菜单可能短暂为空；受权限保护的具体路由
会等待 `loaded=true`，不会在权限尚未返回时误显示 403。

## 3. 后端首次权限加载

一次 `/api/v1/me/permissions` 请求的主链路如下：

```text
Spring Security 校验 JWT（JWT 模式）
  -> UserContextFilter：解析身份并装配 UserContext
  -> PermVersionHeaderFilter：获取权限快照并写 X-OA-Perm-Version
  -> MeController.myPermissions()
  -> 返回用户、权限点、菜单、数据范围、代理、提权和版本
```

### 3.1 用户上下文与员工缓存

`UserContextFilter` 的顺序在 Spring Security 之后，因此 JWT principal 已经可用
（`oa-iam/src/main/java/com/lrj/oa/iam/identity/UserContextFilter.java:37`）。它按以下顺序装配身份：

```text
JWT sub / X-OA-User
  -> UserContextFilter.assemble(userId)
  -> OrgQueryService.getEmployeeByUserId(userId)
  -> EmployeeCache.employee(userId)
  -> 缓存未命中：employee + active assignment 查询
  -> UserContextHolder + TenantContext
```

员工资料和任职列表是两个独立的 Caffeine 缓存。第一次构造权限快照时，
`PermissionSnapshotBuilder` 还会调用 `activeAssignments(userId)`；如果任职缓存尚未命中，会通过
`EmployeeCache.assignments()` 再加载任职数据。实现见：

- `oa-org/src/main/java/com/lrj/oa/org/application/OrgQueryService.java:99`
- `oa-org/src/main/java/com/lrj/oa/org/infrastructure/cache/EmployeeCache.java:65`

员工或任职发生变化时按用户失效；本节点收到组织树业务变更事件时全部失效
（`oa-org/src/main/java/com/lrj/oa/org/infrastructure/cache/EmployeeCache.java:81`）。

### 3.2 快照的实际首次触发点

`PermVersionHeaderFilter` 在调用后续过滤器链和 Controller 之前执行：

```text
PermissionEngine.snapshot(userId)
  -> response.setHeader("X-OA-Perm-Version", combinedVersion)
  -> chain.doFilter(...)
```

代码位于 `oa-iam/src/main/java/com/lrj/oa/iam/identity/PermVersionHeaderFilter.java:45`。
所以在正常的已认证请求中，权限快照会先在这里读取或建立。随后
`MeController.myPermissions()` 再调用 `engine.snapshot(userId)` 时通常已经命中本节点 L1。

`/api/v1/me/permissions` 标记为 `@PublicApi`，未认证请求会返回 HTTP 200 和 `userId=null` 的空权限
清单，而不是 401。Console 在 JWT 模式下通过 query `enabled` 避免登录前抢跑；如果仍收到空清单，
`PermBridge` 会写入 anonymous 状态并引导重新登录。后端实现见
`oa-iam/src/main/java/com/lrj/oa/iam/web/MeController.java:50`。

### 3.3 接口返回内容

`MeController` 从权限快照和权限目录组装：

| 字段 | 来源/用途 |
|---|---|
| `userId`、`username`、`employeeId` | 当前 `UserContext` |
| `primaryOrgId`、`primaryOrgPath` | 当前员工主组织 |
| `version` | `epoch * 1_000_000 + userVersion` |
| `permCodes` | 快照权限位图对应的 code 集合 |
| `menus` | 权限目录中类型为 `MENU` 且用户拥有的节点 |
| `dataScope`、`scopePrefixes` | 合并后的数据范围 |
| `moduleScope` | 各业务模块自己的数据范围 |
| `delegators` | 当前用户正在代理的委托人 |
| `elevatedCodes` | 当前处于 JIT 提权状态的权限点 |

实现位置：`oa-iam/src/main/java/com/lrj/oa/iam/web/MeController.java:61`。

## 4. PermissionEngine 三级加载路径

权限快照热路径入口是 `PermissionEngine.snapshot(userId)`
（`oa-iam/src/main/java/com/lrj/oa/iam/infrastructure/cache/PermissionEngine.java:179`）：

```text
snapshot(userId)
  -> epoch()：全局 epoch 本地缓存最多 1 秒
  -> L1 Caffeine[userId]
       命中且 epoch / ABAC 模式 / expireAt 有效 -> 返回
  -> L2 Redis["oa:perm:snap:" + userId]
       命中且有效 -> 回填 L1 -> 返回
  -> L3 PermissionSnapshotBuilder.build(userId)
       -> 回填 L1
       -> 写入 L2
       -> 返回
```

### 4.1 L1 Caffeine

- Key：`userId`
- 默认最大条数：20,000
- 默认写入后 TTL：300,000 ms
- 进程内缓存，应用重启即清空
- 快照即使仍在 TTL 内，只要 epoch、ABAC 模式或快照自身到期时间不匹配，也视为无效

### 4.2 L2 Redis

- Key：`oa:perm:snap:{userId}`
- 默认 TTL：1,800 秒
- 通过 `SnapshotCodec` 序列化完整权限快照
- 实际 Redis TTL 不会超过快照自己的 `expireAt`
- Redis 读取、解码或写入失败时降级到 L3，不让缓存故障直接阻断业务

### 4.3 L3 数据库重算

L1、L2 都未命中或无效时，进入
`oa-iam/src/main/java/com/lrj/oa/iam/application/PermissionSnapshotBuilder.java:61`：

```text
读取 perm_epoch 和用户版本
  -> 读取用户有效任职
  -> 从 OrgTreeCache 读取组织祖先和路径前缀
  -> 读取有效用户组
  -> 查询 USER / ORG_UNIT / POSITION / USER_GROUP 的有效授权
  -> 展开角色继承和角色权限
  -> ABAC 开启时读取角色权限条件
  -> 合并权限位图、临时提权和权限点级/模块级数据范围
  -> 查询有效委托关系
  -> 计算下一次授权或组成员关系的生效/到期边界
  -> 生成 PermissionSnapshot
```

主要真值表包括：

- `oa_iam.perm_epoch`
- `oa_iam.perm_user_version`
- 员工与任职相关表
- `oa_iam.user_group`、`oa_iam.user_group_member`
- `oa_iam.grant_record`
- `oa_iam.role_inherit`、`oa_iam.role_permission`
- 权限条件表
- `oa_iam.delegation`

`role_inherit_edge` 是在线角色管理的写侧直接关系，不进入用户快照重算热路径。角色变更事务会先由它
重建 ACTIVE-only 的 `role_inherit` 闭包并推进全局权限纪元，之后用户首次访问才按新闭包懒重算快照。

临时授权或用户组成员关系存在更早的生效/到期边界时，快照 `expireAt` 会提前，不能简单沿用默认
5 分钟 TTL。

## 5. 服务启动时的全局预热

### 5.1 权限点目录

`PermissionCatalog.@PostConstruct` 启动时执行 `PermissionMapper.selectCatalog()`，并生成不可变的：

- `code -> id`
- `id -> code`
- `id -> module`
- `id -> requireElevation`
- 完整权限点列表

代码：`oa-iam/src/main/java/com/lrj/oa/iam/application/PermissionCatalog.java:35`。

权限目录只有几百到一千条，常驻内存用于权限 code 与位图 id 的转换，也用于构建前端菜单，避免
每次权限快照重算都重复查询目录。

### 5.2 组织树

`OrgTreeCache.@PostConstruct` 在启动时执行：

```text
读取 org_tree_version
  -> 查询全部组织节点
  -> OrgTreeSnapshot.build(...)
  -> 原子替换当前不可变快照
```

代码：`oa-org/src/main/java/com/lrj/oa/org/infrastructure/cache/OrgTreeCache.java:58`。

权限重算中的祖先组织、后代组织和最小路径前缀都直接读取该内存快照，不在用户权限热路径重复
查询组织树数据库。

### 5.3 启动时不会做的事情

以下组件只在启动时创建空缓存，不会加载所有用户：

- `PermissionEngine` L1 用户权限快照
- `EmployeeCache.employees`
- `EmployeeCache.assignments`

启动阶段会先执行权限目录加载和组织树重建，但这不代表每个用户的权限已经进入本节点 L1。
另外，`OrgTreeCache.rebuild()` 重建失败时会记录错误并沿用当前快照，首次启动时当前快照可能仍为空；
排障时不能只凭应用健康状态推断组织树数据完整。新节点接收第一个用户请求时，可能命中已有
Redis L2，也可能进入 L3 重算。

## 6. 权限变化后的刷新路径

### 6.1 后端失效

后端按变化范围使用两类失效策略：

```text
只影响一个用户
  -> bump userVersion + evictLocal(userId)：删除本节点 L1 + Redis L2
     （不同写侧的两个本地操作顺序不同，但都会在广播前完成）
  -> Redis Pub/Sub PERM_USER
  -> 其他节点删除该用户 L1/L2

可能收回全员权限
  -> bump 全局 epoch
  -> evictAllLocal()：清空本节点 L1，并强制下次重读 epoch
  -> Redis Pub/Sub PERM_EPOCH
  -> 其他节点清空 L1并重载 PermissionCatalog
```

Redis Pub/Sub 频道为 `oa:cache:invalidate`。监听处理位于
`oa-iam/src/main/java/com/lrj/oa/iam/infrastructure/cache/PermissionEngine.java:243`，消息协议位于
`oa-common/src/main/java/com/lrj/oa/common/cache/CacheInvalidation.java:10`。

组织树变化使用 `ORG_TREE` 通知，同时会令用户权限快照失效；组织路径和组织授权变化后，下一次
请求重新走同一条懒预热路径。

### 6.2 Console 刷新

Console 不使用固定周期轮询权限，主要依赖：

1. 每个响应的 `X-OA-Perm-Version`。Axios 响应拦截器读取版本，若与本地不同则 invalidate
   `["me-permissions"]` 和提权查询。
2. WebSocket `perm.changed` 消息。收到新版本后执行同样的 invalidate。
3. 窗口重新获得焦点或网络重连时重新获取权限。

实现位置：

- `oa-console/src/shared/api/client.ts:46`
- `oa-console/src/auth/PermBridge.tsx:83`

`PermBridge.lastHandled` 会记录已经处理的版本，防止权限接口自己的响应头再次触发 invalidate，
形成自持的请求风暴。

## 7. Mobile 的差异

Mobile 使用相同的后端 `/api/v1/me/permissions` 和同一套服务端缓存路径，但前端挂载位置不同：

```text
Guard 认证通过
  -> PermissionProvider
  -> React Query ["me", "permissions"]
  -> GET /api/v1/me/permissions
  -> PermissionContext
```

代码：

- `oa-mobile/src/router.tsx:11`
- `oa-mobile/src/permissions.tsx:20`

当前 Mobile 权限查询 `staleTime` 为 60 秒，支持手动 `reload()`；它还没有 Console 的
`X-OA-Perm-Version` 比对、WebSocket `perm.changed` 订阅和 Zustand 权限镜像。因此服务端加载链路
相同，但登录后的实时权限变化收敛能力弱于 Console。

## 8. 默认缓存配置

IAM 缓存的显式配置入口为 `oa-app/src/main/resources/application.yml:68`；员工缓存未在该 YAML 中
覆写时使用 `EmployeeCache` 构造器上的默认值：

| 配置 | 默认值 | 含义 |
|---|---:|---|
| `oa.iam.cache.ttl-ms` | `300000` | 用户权限快照默认 TTL |
| `oa.iam.cache.l1-max-size` | `20000` | L1 最大用户数 |
| `oa.iam.cache.l2-enabled` | `true` | 是否启用 Redis L2 |
| `oa.iam.cache.l2-ttl-seconds` | `1800` | Redis 快照最长 TTL |
| `oa.org.employee-cache.max-size` | `20000` | 员工、任职缓存各自最大条数 |
| `oa.org.employee-cache.ttl-ms` | `300000` | 员工与任职缓存 TTL |
| `oa.org.tree-cache.poll-ms` | `5000` | 组织树版本轮询间隔 |

排查首次进入慢时，应按“员工缓存 -> 权限 L1 -> Redis L2 -> L3 重算 -> 前端 Query”顺序观察，
而不是把所有首次延迟都归因于 `/me/permissions` Controller。

## 9. 角色管理集成、数据表与查询

角色管理中的“加载权限”有两种含义，排查时必须先区分：

1. **角色配置页加载**：读取某个角色的直接权限、继承后有效权限和权限点目录，供管理员编辑矩阵；
2. **用户运行时加载**：根据用户、组织、岗位、用户组的有效授权展开角色，生成用于实际判权的权限快照。

前者是管理端查询，不直接参与业务请求判权；后者才是 `@RequiresPerm`、数据范围和前端
`/api/v1/me/permissions` 的权威来源。

### 9.1 角色配置页如何加载权限

打开角色配置抽屉时，Console 并行加载：

```text
GET /api/v1/iam/role-admin/{roleId}  -> 角色详情、直接权限、有效权限、直接继承关系
GET /api/v1/iam/permissions/catalog  -> 权限点目录
GET /api/v1/iam/role-admin           -> 可选的继承角色列表
```

前端实现位于 `oa-console/src/pages/iam/RoleManagementPanel.tsx:254`。角色详情由
`RoleAdminService.detail()` 组装，返回字段的含义是：

| 字段 | 来源 | 用途 |
|---|---|---|
| `directPermissionIds` | `role_permission` 中当前角色自己的记录 | 权限矩阵的勾选值，可以在线修改 |
| `effectivePermissionIds` | `role_inherit` 展开后所有后代角色的权限并集 | 只展示最终结果，继承得到的权限不会重复勾选 |
| `inheritedRoleIds` | `role_inherit_edge` 中当前角色的直接出边 | 角色继承多选框 |

直接权限查询位于 `RoleMapper.selectDirectPermissionIds()`，有效权限查询位于
`RoleMapper.selectEffectivePermissionIds()`。保存权限矩阵时调用：

```text
PUT /api/v1/iam/role-admin/{roleId}/permissions
```

服务端以乐观锁版本保护更新，先替换该角色的 `role_permission`，再推进全局权限纪元并广播失效。
因此已经在线的用户不会继续永久使用旧角色矩阵；其下一次权限快照读取会按新矩阵回源或重算。

### 9.2 用户运行时如何通过角色获得权限

L1、L2 都未命中时，`PermissionSnapshotBuilder.build(userId)` 的角色集成部分按以下顺序执行：

```text
用户有效任职
  -> 计算 USER / ORG_UNIT / POSITION / USER_GROUP 候选主体
  -> grant_record：过滤未生效、已到期、已撤销的授权
  -> 取得被授予的 ACTIVE 根角色
  -> role_inherit：展开根角色自身及其全部 ACTIVE 后代角色
  -> role_permission：读取这些角色的直接权限
  -> permission：只保留 ACTIVE 权限点
  -> 可选 permission_condition：按“被授予的根角色 + 权限点”装配 ABAC 条件
  -> 合并权限位图和每个权限点的数据范围
  -> 写入 L1 Caffeine 和 L2 Redis
```

角色继承的方向是：闭包行 `(ancestor_role_id, descendant_role_id)` 表示祖先角色获得后代角色的权限。
例如 `SUPER_ADMIN -> HR_ADMIN` 表示 `SUPER_ADMIN` 拥有 `HR_ADMIN` 的权限，而不是反过来。

`role_inherit_edge` 和 `role_inherit` 的职责不同：

- `role_inherit_edge` 是管理员配置的**直接继承边**，属于写侧真值；
- `role_inherit` 是由直接边重建的 **ACTIVE-only 传递闭包**，属于判权读路径；
- 运行时不会针对每个用户递归读取 `role_inherit_edge`；停用角色后重建闭包，该角色及经过它的继承路径会退出有效权限。

还要注意，`role.default_scope` 目前既是授权页自动带出的默认范围，也是该角色可分配范围的服务端上限。
管理员可以选择更窄的范围，但不能超过它；`CUSTOM` 会同时写入 `scope_org_ids` 和下级开关。
用户最终的数据范围取自具体授权记录的 `grant_record.scope_type` 和 `scope_org_ids`，再按权限点合并，
不能仅凭角色表判断某个用户能看哪些行。

### 9.3 数据保存位置

关系型真值保存在 PostgreSQL 默认物理数据库 `oa` 中，IAM 使用 `oa_iam` schema。开发环境默认端口
是 `35432`；Docker 数据目录通过命名卷 `oa-pg-data` 持久化到容器外。连接配置分别见
`oa-app/src/main/resources/application.yml:12` 和 `deploy/docker-compose.yml:10`。

| 表 | 保存内容 | 是否在用户权限重算热路径读取 |
|---|---|---:|
| `oa_iam.permission` | 权限点 code、类型、模块、菜单元数据、状态、是否要求提权 | 是；目录启动时常驻内存 |
| `oa_iam.role` | 角色基本信息、租户、状态、默认范围、乐观锁版本 | 是 |
| `oa_iam.role_permission` | 角色直接拥有的权限点 | 是 |
| `oa_iam.role_inherit_edge` | 管理员配置的直接角色继承边 | 否；只用于写侧校验和重建闭包 |
| `oa_iam.role_inherit` | ACTIVE 角色继承传递闭包 | 是 |
| `oa_iam.grant_record` | 用户、组织、岗位、用户组到角色的授权及时间窗、数据范围 | 是 |
| `oa_iam.user_group` / `user_group_member` | OA 显式成员组及成员时间窗 | 是 |
| `oa_iam.permission_condition` | 角色权限的 ABAC 条件 | ABAC 开启时读取 |
| `oa_iam.perm_epoch` / `perm_user_version` | 全局和用户级权限缓存版本 | 是 |
| `oa_iam.delegation` | 流程委托代理关系 | 是，但不会叠加角色权限 |

基础表 DDL 位于 `oa-iam/src/main/resources/db/migration/V10__iam.sql`，直接继承边位于
`oa-iam/src/main/resources/db/migration/V16__role_management.sql`。用户任职和组织关系位于 `oa_org`
schema，不在 `oa_iam` 中重复保存。

### 9.4 本地连接与常用查询

本地 Compose 已启动时，可以直接进入 PostgreSQL：

```bash
docker exec -it oa-postgres psql -U oa -d oa
```

也可以从宿主机连接，默认地址为 `localhost:35432`，用户名和数据库名都是 `oa`；密码从
`OA_PG_PASSWORD` 获取，本地 Compose 未覆盖时使用仓库中的开发默认值。

查询一个角色的直接权限：

```sql
SELECT r.code AS role_code,
       p.code AS permission_code,
       p.name,
       p.type,
       p.module
  FROM oa_iam.role r
  JOIN oa_iam.role_permission rp ON rp.role_id = r.id
  JOIN oa_iam.permission p ON p.id = rp.permission_id
 WHERE r.tenant_id = 1
   AND r.code = 'TEST'
 ORDER BY p.module, p.code;
```

查询一个角色继承后的有效权限，并显示权限来自哪个角色：

```sql
SELECT DISTINCT
       root.code AS role_code,
       source.code AS source_role,
       ri.distance,
       p.code AS permission_code,
       p.name
  FROM oa_iam.role root
  JOIN oa_iam.role_inherit ri
    ON ri.ancestor_role_id = root.id
  JOIN oa_iam.role source
    ON source.id = ri.descendant_role_id
   AND source.status = 'ACTIVE'
  JOIN oa_iam.role_permission rp
    ON rp.role_id = source.id
  JOIN oa_iam.permission p
    ON p.id = rp.permission_id
   AND p.status = 'ACTIVE'
 WHERE root.tenant_id = 1
   AND root.code = 'TEST'
   AND root.status = 'ACTIVE'
 ORDER BY ri.distance, source.code, p.code;
```

查询一个角色的授权历史（包含已撤销和已过期记录）：

```sql
SELECT g.id,
       g.subject_type,
       g.subject_id,
       r.code AS role_code,
       g.scope_type,
       g.scope_org_ids,
       g.grant_type,
       g.valid_from,
       g.valid_to,
       g.revoked_at
  FROM oa_iam.grant_record g
  JOIN oa_iam.role r ON r.id = g.role_id
 WHERE g.tenant_id = 1
   AND r.code = 'TEST'
 ORDER BY g.id DESC;
```

只看当前生效且未撤销的授权时，在 `ORDER BY` 前增加：

```sql
AND g.revoked_at IS NULL
AND g.valid_from <= now()
AND (g.valid_to IS NULL OR g.valid_to > now())
```

不要用一条只连接 `subject_type = 'USER'` 的 SQL 作为用户最终权限结论，因为它会遗漏组织、岗位、
用户组授权，以及 `include_descendants`、角色状态、ABAC 和数据范围语义。查询用户最终权限应优先使用
后端与真实判权共用的重算实现：

```text
GET /api/v1/iam/admin/preview?userId={userId}
GET /api/v1/iam/admin/why?userId={userId}&permCode={permissionCode}
GET /api/v1/iam/admin/explain?userId={userId}&permCode={permissionCode}
```

这三个接口都要求调用者拥有 `oa:iam:admin`：`preview` 返回数据库重算后的完整权限，`why` 返回指定
权限点的授权来源，`explain` 对比当前缓存与数据库重算结果，适合排查“角色已经修改但权限没有生效”。
