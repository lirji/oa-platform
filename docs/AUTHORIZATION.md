# 权限体系与全局保障指南

本文是 OA 权限体系的总入口，说明 RBAC、ABAC、接口权限、数据权限和字段权限如何协作，
以及管理员和开发者应该在哪里编辑、如何验证。ABAC 表达式细节见
[ABAC 授权与管理指南](ABAC.md)，数据权限全仓迁移计划见
[接口权限与数据权限全局保障方案](plans/data-permission-global-guard/FINAL_PLAN.md)。用户首次进入时的
权限装载、三级缓存懒预热和实时刷新路径见
[用户权限加载与缓存预热链路](PERMISSION_LOADING_AND_CACHE_WARMUP.md)。

## 1. 一次请求经过哪些授权层

```text
JWT / DEV 身份
  -> handler 必须声明 @RequiresPerm 或 @PublicApi
  -> RBAC：主体 -> 角色（含继承）-> 权限点
  -> ABAC：按租户 + 角色 + 权限点校验本次用户属性和方法参数
  -> JIT：高危权限确认是否处于临时提权状态
  -> AuthorizationContext：记录本次真正通过的权限点
  -> @DataScope：按该权限点读取行级范围并改写 SQL
  -> @Sensitive：按 FIELD 权限决定明文或脱敏
```

前四层决定“能不能调用”，数据权限决定“调用后能看到哪些行”，字段权限决定“这些行的敏感列
能否看明文”。前端菜单、路由和按钮裁剪只改善体验，不参与后端安全结论。

| 层次 | 回答的问题 | 当前实现 |
|---|---|---|
| 认证 | 调用者是谁 | 生产 JWT；DEV 才允许 `X-OA-User` |
| RBAC | 调用者是否拥有能力 | 主体授权、角色继承、权限点位图 |
| ABAC | 这次调用是否满足属性条件 | 受限 SpEL，RBAC 之后二次收窄 |
| 接口权限 | 是否能进入 handler | `@RequiresPerm`；公开接口必须显式 `@PublicApi` |
| 数据权限 | 能读取哪些业务行 | 权限点级 `@DataScope` + MyBatis SQL 改写 |
| 字段权限 | 敏感字段能否返回明文 | `@Sensitive(perm=...)` 序列化脱敏 |
| 页面权限 | 菜单、路由、按钮是否展示 | 后端权限清单驱动的前端裁剪，仅体验层 |

## 2. RBAC 模型

### 2.1 权限点、角色和继承

权限目录支持 `MENU`、`BUTTON`、`API`、`DATA`、`FIELD` 五种类型。当前数据库有 67 个权限点，
新增的 `oa:iam:elevation:approve` 用于 JIT 四眼审批；目录中的 `resource` 和 `method` 只是运营元数据，
真正的接口安全边界是源码中的 `@RequiresPerm`。

角色使用 RBAC1 继承闭包 `role_inherit`。给一个角色授权后，快照会展开该角色及其后代角色的权限。
内置继承链是 `SUPER_ADMIN -> HR_ADMIN -> DEPT_MANAGER -> EMPLOYEE`。权限热路径读取
`PermissionSnapshot` 的 RoaringBitmap，不查询数据库或远程服务。

### 2.2 授权主体与时效

统一授权记录是：

```text
主体 × 角色 × 数据范围 × 生效时间窗 × 授权类型
```

| 维度 | 可选值 | 说明 |
|---|---|---|
| 主体 | `USER` / `ORG_UNIT` / `POSITION` / `USER_GROUP` | 用户、组织、岗位或 OA 显式成员组 |
| 数据范围 | `ALL` / `ORG_AND_SUB` / `ORG` / `SELF` / `CUSTOM` / `NONE` | 每条授权携带自己的行级范围 |
| 授权类型 | `PERMANENT` / `TEMPORARY` / `DELEGATED` | 常驻、定时；`DELEGATED` 为表结构预留，实际流程委托使用独立表 |
| 时间 | `[valid_from, valid_to)` | 判权实时检查，不依赖回收任务才能过期 |

`include_descendants` 只控制 `ORG_UNIT` 主体授权是否向下级组织成员生效。它不改变数据范围：
`ORG` 和 `ORG_AND_SUB` 锚定当前用户的主岗/兼岗组织；需要固定到指定组织时使用
`CUSTOM + scopeOrgIds`。虚线任职可以获得组织主体的角色，但不会扩大数据范围。

`USER_GROUP` 是 OA 内部显式成员组，不等同于 Casdoor group。成员有独立时间窗，组被禁用、成员被
移出或到期后，相关角色不再进入用户快照。

### 2.3 临时授权、JIT 和委托不是一回事

| 机制 | 用途 | 安全约束 |
|---|---|---|
| 定时角色 | 在指定时间窗内授予角色 | 快照 TTL 不得跨过最近到期边界 |
| JIT 提权 | 临时激活高危能力 | 只能激活本人已有的直接永久角色授权；申请进入 `PENDING`，须由另一名审批人批准 |
| 委托代理 | 代办他人的流程待办 | 不扩大代理人的普通接口/数据权限，轨迹保留 `on_behalf_of` |

## 3. ABAC 如何叠加在 RBAC 上

ABAC 不是 RBAC 的替代品，而是 RBAC 权限来源上的二次条件。策略维度是：

```text
租户 × 角色 × 权限点 × 条件表达式
```

用户必须先通过 RBAC 获得权限点，系统才会校验这个角色与权限点上的 ABAC 条件。同一
“角色 + 权限点”的多条条件按 `AND`；来自不同角色的条件分支按 `OR`；存在一个没有启用条件的
适用角色来源时，该来源视为无条件授权并直接放行。

数据范围不会提前把所有角色来源混成一份：ABAC 返回本次真正通过的来源角色，后续
`@DataScope` 只合并这些角色贡献的范围，未通过条件的 `ALL` 来源不能放宽已通过的 `CUSTOM` 来源。

ABAC 只读取 `#user`、`#args`、`#p0/#a0` 和可用的方法参数名。请求热路径不查数据库、不调远程；
禁止类型、Bean、构造器、方法调用与赋值，表达式缺变量或求值异常时拒绝。它控制的是接口调用，
不会直接生成 SQL，也不能替代 `@DataScope` 或 `@Sensitive`。

开关为 `OA_IAM_ABAC_ENABLED=true`。只有部署环境显式设置并重启主应用后才会启用；
无条件策略来源仍保持原 RBAC 语义。
完整属性、表达式和发布回退步骤见 [ABAC 授权与管理指南](ABAC.md)。

## 4. 接口权限如何全局保障

### 4.1 运行时

- 受保护 handler 使用 `@RequiresPerm`，多个权限点支持 `AND`（默认）或 `OR`。
- 没有身份返回 401；身份有效但 RBAC、ABAC 或 JIT 不通过返回 403。
- 只有明确公开的 handler 才能使用 `@PublicApi(reason="...")`，理由不能为空。
- `UnannotatedHandlerGuard` 在运行期拒绝任何没有声明授权立场的 handler，默认 fail-closed。
- RBAC/ABAC 真正通过的权限点会写入调用链 `AuthorizationContext`，供数据权限继续校验。
- `OA_IAM_ENFORCE=false` 会绕过接口判权；JWT 模式启动保护会禁止这种配置。

### 4.2 构建期

- `ControllerPermissionCoverageTest` 和三个独立服务的 `*AuthorizationStanceTest` 扫描全部 handler。
- 四份 `api-surface.golden` 冻结 148 个端点的“HTTP 方法 + 路径 + 权限点/公开立场”。
- `@PublicApi` 必须写审计理由，不能为了通过构建随意添加。
- 前端 `PermRoute`、`Can` 和权限驱动菜单必须复用同一权限 code，但它们不是安全边界。

新增接口时，必须同时完成 handler 注解、权限目录、角色映射、API Golden、前端权限常量和越权测试。
仅隐藏按钮、仅在网关配 URL 或仅在业务代码里写 `if` 都不算完成接口授权。

## 5. 数据权限如何全局保障

### 5.1 权限点级范围

一条授权的范围会合并到 `PermissionSnapshot.permissionScope[权限点]`。数据方法必须声明：

```java
@DataScope(
    permission = "oa:employee:view",
    table = "oa_org.v_employee_directory",
    alias = "t",
    orgColumn = "org_id",
    pathColumn = "org_path",
    userColumn = "user_id"
)
```

`permission` 必须是本次接口 RBAC/ABAC 已实际通过的权限点；同模块其他权限的 `ALL` 不会放宽当前
查询。`module` 只保留作兼容和诊断元数据，严格判权不使用模块合并范围。

| 范围 | SQL 语义 |
|---|---|
| `ALL` | 明确求值后不追加条件 |
| `ORG_AND_SUB` | `org_path LIKE '<安全路径>%'`，多个最小前缀用 OR |
| `ORG` | `org_id IN (...)` |
| `SELF` | `userColumn = 当前 userId` |
| `CUSTOM` | 指定组织及可选下级路径；精确组织、自身与路径条件按 OR 合并，不做不安全的宽化降级 |
| `NONE` | `1 = 0`，一行都不给 |

业务表必须具备正确的 `org_id`、`org_path`、本人字段及索引；列名通过注解显式声明。数据范围不足
通常返回 200 和空集合，不用 403 暴露数据存在性；前端应结合 `/api/v1/me/permissions` 展示范围提示。

### 5.2 强制消费协议

严格模式 `OA_DATA_SCOPE_STRICT=true` 下，以下情况都会在 SQL 发出前或方法返回前失败：

- 入口没有真正通过 `@DataScope.permission` 对应的接口权限；
- 声明的目标表没有执行，或 SQL 表名、别名与注解不一致；
- SQL 条件构造、路径校验或解析失败；
- 已注册受管表通过 MyBatis 查询时没有任何 `@DataScope` 上下文。

`ALL` 也必须留下“已明确求值”记录，不能把“没有注入谓词”误当成“拦截器没有运行”。系统级旁路
只能用 `@DataScopeBypass(reason, tables)`，普通 web/application 包禁止声明，旁路会记录主体、方法、
表、理由和 traceId。业务代码也禁止用 `@InterceptorIgnore(dataPermission = "true")` 关闭拦截器。

### 5.3 当前覆盖边界

平台机制已经强制，但业务迁移尚未全仓完成：

- 已严格迁移并由 Golden 冻结 5 个方法：`DirectoryService` 4 个、`AssetService` 1 个。
- 已注册 2 张受管表：`oa_org.v_employee_directory`、`oa_admin.asset`。
- 仍有 21 个 application/web 直接 JDBC 类处于审查后的迁移基线；CI 禁止新增，但这些既有路径
  不能宣称已获得统一的行级过滤。
- 当前租户上下文固定为租户 1，真正多租户仍需补数据库 RLS 或 MyBatis TenantLine。

因此，“全局保障”当前准确含义是：所有接口必须声明授权立场；所有新数据权限路径必须进入统一协议，
已迁移表不能绕过；既有 JDBC 债务按计划只减不增。不能把它表述为“全部业务表已完成行级权限”。

## 6. 字段权限与敏感数据

敏感字段在 DTO 上使用 `@Sensitive(type=..., perm="oa:field:...")`。Jackson 序列化时读取当前用户
权限：有 FIELD 权限返回明文，没有则按手机号、证件号等类型脱敏。数据库中的手机号等敏感值仍由
`SensitiveCrypto` 加密存储。字段脱敏只解决列值暴露，不能替代接口权限和行级数据权限。

## 7. 管理员怎么编辑

PC 管控台入口是 `/iam`：

1. “角色管理”：拥有 `oa:iam:admin` 后可创建和复制自定义角色，编辑基本信息、直接权限矩阵和角色继承，
   以及启停或软删除角色。内置角色不可停用或删除；`SUPER_ADMIN` 的权限矩阵和继承关系禁止在线修改，
   防止误操作锁死管理入口。
2. “主体授权”：选择 `USER`、`ORG_UNIT`、`POSITION` 或 `USER_GROUP`，选择已有角色和数据范围后授权；
   留空角色可只查询该主体。查询授权需要 `oa:iam:view`，新增和撤销分别需要
   `oa:iam:grant`、`oa:iam:revoke`。
3. “用户组”：拥有 `oa:iam:admin` 后可创建、改名、启停组，批量加入最多 500 个用户并设置成员时间窗。
4. “ABAC 条件”：拥有 `oa:iam:admin` 后选择角色和权限点，填写、校验、启停或软删除表达式。
5. “提权审批”：拥有 `oa:iam:elevation:approve` 后批准或拒绝待处理申请；申请人不能审批自己。
6. “权限沙盘” `/iam/sandbox`：用 `why`、`explain` 和 `preview` 检查某用户的权限来源、范围和缓存真值。

角色直接继承边保存在 `role_inherit_edge`，判权热路径仍读取 `role_inherit` 传递闭包。在线变更由写服务
在租户级数据库锁内校验环路、重建闭包、推进 `perm_epoch`，并由统一写操作切面记录成功、失败和拒绝审计。
角色编码创建后不可修改；删除采用软删除，存在任何授权历史的角色不能删除。停用角色不能新增授权，
它及经它继承的权限会在闭包重建和全局缓存失效后退出判权结果。

“主体授权”表单会自动采用角色的 `default_scope`，该字段同时作为当前角色的可分配范围上限；
服务端仍会再次校验，不能靠修改请求扩大范围。选择 `CUSTOM` 后可直接用组织树选择一个或多个组织，
并配置是否包含下级组织。主体、组织、岗位和用户组都必须是当前租户中的有效对象；相同活跃授权重试
返回既有授权 ID。

### 7.1 华东大区经理的推荐配置

“只能查看华东大区数据”是数据范围问题，通常不需要额外 ABAC：

1. 创建“华东大区经理”自定义角色，直接分配需要的查看/办理权限，`defaultScope` 设为 `CUSTOM`。
2. 给经理用户授予该角色，在 `scopeOrgIds` 中选择“华东大区”组织节点，并开启 `includeDescendants`。
3. 用权限沙盘确认目标权限点的范围只含华东路径，再以华南数据执行一次 IDOR 拒绝测试。

只有还要叠加“金额不超过 5 万”“仅工作时间”“只能审批本人所在成本中心”等请求属性条件时，才为
该角色的具体权限点配置 ABAC。ABAC 是二次收窄，不能代替 `CUSTOM` 数据范围。

```http
POST /api/v1/iam/grants
Content-Type: application/json

{
  "subjectType": "ORG_UNIT",
  "subjectId": "10",
  "roleId": 3,
  "scopeType": "CUSTOM",
  "scopeOrgIds": [10, 11],
  "includeDescendants": true,
  "grantType": "PERMANENT",
  "reason": "研发管理范围"
}
```

ABAC 条件使用权限目录中的数值 ID，而不是 permission code：

```http
POST /api/v1/iam/abac/conditions
Content-Type: application/json

{
  "roleId": 3,
  "permissionId": 42,
  "expression": "#p0.amount <= 5000 and #user.primaryOrgId == 10",
  "description": "部门负责人五千元内审批",
  "enabled": true
}
```

先调用 `/api/v1/iam/abac/validate` 校验表达式，并核对目标权限点对应的所有 handler 参数形状；
示例 ID 只用于展示，实际值必须从 `/api/v1/iam/roles` 和 `/api/v1/iam/permissions/catalog` 查询。

## 8. 变更、缓存与审计

权限快照采用 L1 Caffeine、L2 Redis、L3 数据库重算。收权、角色矩阵、组织继承、用户组和 ABAC
策略等可能影响多人时推进全局 epoch；只影响个人的加权可推进用户版本。Redis 通知负责快速传播，
数据库版本是重启后仍有效的真值；临时授权和组成员的最近时间边界会压低快照 TTL。

关键指标：

- 判权一致性：`oa_perm_mismatch_total`；
- ABAC：`oa_iam_abac_evaluations_total`、`oa_iam_abac_denied_total`、`oa_iam_abac_errors_total`；
- 数据权限：`oa_data_scope_evaluations_total`、`oa_data_scope_predicates_total`、
  `oa_data_scope_denied_total`、`oa_data_scope_missing_total`、
  `oa_data_scope_alias_mismatch_total`、`oa_data_scope_bypass_total`。

`missing`、`alias_mismatch`、`bypass` 或 ABAC `errors` 任意异常增长都应告警并排查。

## 9. 开发与发布检查清单

新增或修改受保护能力时：

- [ ] handler 已选择 `@RequiresPerm` 或有充分理由的 `@PublicApi`；
- [ ] 权限 code 已进入目录并分配给正确角色，前后端没有重复定义不同语义；
- [ ] ABAC 条件引用的参数在共用该权限点的所有 handler 上都存在且类型一致；
- [ ] 数据查询已走 MyBatis，`@DataScope.permission/table/alias/columns` 与 SQL 完全一致；
- [ ] 新受管表已加入注册表和 `data-access-surface.golden`，没有新增 JDBC 旁路；
- [ ] 敏感字段已加密存储，并用 FIELD 权限控制序列化；
- [ ] 已覆盖无身份、无权限、跨组织 IDOR、`NONE`、错 alias、撤权和过期场景；
- [ ] `mvn -B test`、相关阶段冒烟和权限指标检查通过；
- [ ] JWT 环境保持 `OA_IAM_ENFORCE=true`、`OA_DATA_SCOPE_STRICT=true`。
