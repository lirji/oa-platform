# 接口权限与数据权限全局保障方案

> 状态：平台阶段 A～C 已实施；阶段 D 全仓业务迁移进行中，阶段 E/RLS 未实施。
> 日期：2026-08-21
> 目标：任何 HTTP 请求都不能绕过身份认证、接口权限或数据范围；任何例外必须显式声明、可审计、可测试。

已落地：权限点级快照、入口权限绑定、严格 SQL 消费、表/alias fail-closed、显式旁路审计、六项指标、
JWT 启动门禁、数据访问 Golden，以及 Directory/Asset 首批迁移。当前剩余边界是 21 个已冻结的
application/web 直接 JDBC 类；门禁禁止债务增长，但完成阶段 D 前不得宣称全仓行级数据权限已覆盖。

## 1. 结论与目标

当前接口权限已经具备构建期和运行期双重 fail-closed，基础可靠；数据权限的 SQL 改写器本身也按
fail-closed 设计，但它依赖业务方法主动添加 `@DataScope`，尚未形成全仓覆盖。

全局保障完成后的安全不变量是：

```text
可信身份
  → 接口显式授权立场
  → RBAC / ABAC / JIT
  → 权限点级数据范围
  → SELECT / UPDATE / DELETE SQL 强制注入范围
  → INSERT 目标范围校验
  → 无范围、未消费、别名错误、非法旁路全部拒绝
```

“全局”定义为：所有外部请求触发的业务数据读写，必须经过上述链路；后台任务、迁移和基础设施访问
只能使用受限的显式旁路，并留下原因、调用方和目标表审计记录。

## 2. 实施前基线（2026-08-21 核查）

### 2.1 已有保障

- 每个 REST handler 必须声明 `@RequiresPerm` 或 `@PublicApi(reason=...)`，否则测试失败。
- `UnannotatedHandlerGuard` 在运行时拒绝遗漏授权立场的 OA handler。
- API Golden 冻结“HTTP 方法 + 路径 + 权限点”，权限归属变化必须显式审查。
- `RequiresPermAspect` 执行 RBAC、ABAC 和 JIT 临时提权；未知权限和无身份均拒绝。
- `DataPermissionInterceptor` 位于分页前，并能处理 SELECT、UPDATE、DELETE。
- `NONE`、空组织范围、非法路径和 SQL 条件解析错误均 fail-closed。

### 2.2 已确认缺口

| 缺口 | 当前事实 | 具体风险 |
|---|---|---|
| 覆盖依赖人工 | 生产代码只有 5 个真实 `@DataScope` 方法 | 新增列表/详情忘记注解时返回全量数据 |
| 未消费只告警 | `@DataScope` 方法没有 MyBatis 消费时只写 WARN | 改用 JdbcTemplate 后注解仍在，但过滤消失 |
| 别名不匹配放行 | 目标表别名不等于注解 alias 时返回 `null` | SQL 重构改别名后静默失去 WHERE 条件 |
| 范围按模块取最宽 | 快照是 `module → DataScopeRule` | 同模块中无关权限的 `ALL` 可能放宽其他接口 |
| JDBC 路径较多 | 多个 application/web 文件直接或间接使用 JdbcTemplate | MyBatis 拦截器无法覆盖这些查询 |
| 本地身份可伪造 | 2026-08-21 核查时本地 Docker 使用 `OA_SECURITY_MODE=DEV` | `X-OA-User` 可切换身份，只能用于本地联调 |
| 租户仍是常量 | `TenantContext.DEFAULT_TENANT_ID=1` | 不能把现状视为多租户隔离保障 |

现有数据权限是“使用处安全”，不是“遗漏时安全”。本方案的重点是让遗漏本身无法通过构建或运行。

## 3. 核心设计

### 3.1 接口权限保持全局 fail-closed

保留当前 `@RequiresPerm`、`@PublicApi`、运行时 guard 和 API Golden。新增启动期安全约束：

- `JWT` 环境必须配置 JWKS、issuer、audience；任一缺失启动失败。
- 非本地环境必须满足 `OA_IAM_ENFORCE=true`。
- 非本地环境必须满足新的 `OA_DATA_SCOPE_STRICT=true`。
- `DEV` 模式启动时保持醒目告警；部署清单和生产流水线禁止 `DEV`。

`RequiresPermAspect` 在当前请求的授权上下文中记录实际通过的权限点，后续数据权限必须引用其中一个，
防止服务层使用与接口权限无关的数据范围。

### 3.2 数据范围从“模块级”收紧为“权限点级”

当前快照保存：

```text
module → DataScopeRule
```

目标改为：

```text
permissionId → DataScopeRule
```

具体修改：

1. `PermissionSnapshot` 增加 `Map<Integer, DataScopeRule> scopeByPermission`。
2. `PermissionSnapshotBuilder` 按每条 grant 实际贡献的 permission 合并 scope；只在同一个权限点内取并集或更宽范围。
3. `PermissionChecker.dataScope` 改为 `dataScope(userId, permissionCode)`。
4. `SnapshotCodec` 增加协议版本；旧 L2 快照版本不匹配时丢弃并重建。
5. 模块级范围只在迁移期保留兼容读取；严格模式启用后禁止 fallback 到模块最宽范围。

这样，“资产查看 ALL”不会自动把同属 admin 模块的“车辆查看”也扩大为 ALL。

### 3.3 每个数据访问必须声明立场

扩展 `@DataScope`，强制填写权限点和受管表：

```java
@DataScope(
    permission = "oa:employee:view",
    module = "org",
    table = "oa_org.v_employee_directory",
    alias = "t",
    orgColumn = "org_id",
    pathColumn = "org_path",
    userColumn = "user_id"
)
```

新增显式旁路注解：

```java
@DataScopeBypass(
    reason = "考勤日结批任务，需要跨组织聚合",
    tables = {"oa_att.punch_record"}
)
```

规则：

- 面向请求的查询、详情、导出、更新和删除必须使用 `@DataScope`。
- 每个 scoped 数据方法必须明确一个“数据范围归属权限点”，且该权限必须已在本次入口判权中通过。
  多权限 handler 不能回退到模块范围：应显式指定负责数据范围的权限；OR 场景若该权限未通过则拒绝，
  或拆成不同 service 方法分别绑定权限。
- 确实不需要行级过滤的方法必须使用 `@DataScopeBypass`，reason 和 tables 必填。
- `@PublicApi` 读取业务表时仍需数据访问立场，不能因为接口公开而自动获得全表权限。
- 旁路只允许后台任务、迁移、权限快照构建等白名单组件使用；普通 Controller/Application Service 使用时构建失败。
- 旁路必须记录用户/服务身份、方法、原因、目标表和 traceId。

新增 `DataAccessSurfaceGoldenTest`，冻结：

```text
服务方法 → 接口权限点 → 数据访问模式(SCOPED/BYPASS/PUBLIC) → 目标表 → 模块
```

任何新增、删除或变更都必须显式更新 Golden 并经过安全审查。

### 3.4 运行时严格消费协议

把当前 `DataScopeContext` 从布尔 `consumed` 升级为一次调用的执行记录：

```text
permissionCode
expectedTables
seenTables
evaluatedTables
predicateInjectedTables
bypassReason
```

严格模式规则：

1. `@DataScope` 方法结束时没有执行受管 SQL：抛出异常，不再只告警。
2. 看到目标表但没有完成策略求值：抛出异常。
3. 声明的目标表未出现或 SQL 中出现未声明的受管表：抛出异常。
4. 表别名不匹配：抛出异常；不能返回 `null` 静默放行。
5. `ALL` 虽然不增加 WHERE，也必须记录为“已求值、明确放行”，与“没有执行策略”区分。
6. 没有用户身份时使用 `NONE`；除显式公共数据策略外均生成 `1 = 0`。
7. 条件构造、JSqlParser 解析或上下文校验失败全部拒绝执行 SQL。
8. `finally` 清理 ThreadLocal，并增加嵌套调用栈，避免嵌套服务覆盖外层上下文。

MyBatis handler 应按 SQL 中的真实表名查“表策略注册表”，再使用 SQL 自带 alias 生成谓词，避免把 alias
作为唯一匹配依据。注册表包含模块、租户列、组织列、路径列、本人列和允许的操作类型。

### 3.5 读写统一保护

| 操作 | 目标行为 |
|---|---|
| SELECT / COUNT | 注入相同数据范围，分页 count 不得绕过 |
| UPDATE | 在原 WHERE 上追加租户和数据范围；影响 0 行按“不存在或无权”处理 |
| DELETE | 与 UPDATE 相同，禁止先按 ID 查出后无范围删除 |
| INSERT | tenant、creator、org/path 由可信上下文写入；请求中的同名字段不得直接采信 |
| 批量/导出 | 同样执行范围；不得通过独立 JdbcTemplate 导出绕过 |

为 SELECT、UPDATE、DELETE 分别增加 SQL 改写测试；INSERT 增加目标组织校验器和字段覆盖测试。

### 3.6 收敛 JdbcTemplate 和其他绕过路径

第一阶段采用“请求路径禁用、后台显式旁路”：

- ArchUnit 禁止 `..web..` 和普通 `..application..` 直接依赖 `JdbcTemplate`/`DataSource`/`Connection`。
- 现有请求链路中的 JdbcTemplate 查询迁移到 MyBatis Mapper，并纳入数据权限拦截器。
- 性能敏感的批处理、COPY、号段和内部权限真值查询保留 JDBC，但必须放在批准的 infrastructure/job 包，
  使用 `@DataScopeBypass` 并审计。
- 禁止业务代码使用 `@InterceptorIgnore(dataPermission = "true")`；只允许基础设施白名单。

第二阶段为关键表增加 PostgreSQL RLS 作为数据库侧纵深防御，至少先覆盖 tenant 隔离和高敏表。
这样即使未来有人重新引入原生 JDBC，数据库仍可阻止跨租户或跨主体访问。

### 3.7 租户隔离

当前系统是单租户实现，不能宣称已有多租户全局保障。启用多租户前必须完成：

1. 从受信 JWT claim/域名映射解析 tenantId，不再固定为 1。
2. 在 DataPermissionInterceptor 之前安装 TenantLineInnerInterceptor，覆盖所有 tenant 表。
3. 关键表增加 PostgreSQL RLS tenant policy，通过事务级 `SET LOCAL` 注入受信 tenant/user/scope，
   避免连接池复用残留会话变量，并保护 JdbcTemplate 和人工漏写条件的情况。
4. 唯一索引、外键和缓存 key 全部包含 tenantId。
5. 增加 tenant A token 枚举 tenant B ID 的全量 IDOR 用例。

## 4. 构建门禁

新增以下测试和检查：

| 门禁 | 失败条件 |
|---|---|
| `DataAuthorizationStanceTest` | 持久化访问方法既无 `@DataScope` 也无批准旁路 |
| `DataAccessSurfaceGoldenTest` | 方法、权限、模式、表或模块发生未审查漂移 |
| `DataScopePermissionBindingTest` | `@DataScope.permission` 不属于入口 `@RequiresPerm` |
| `JdbcBypassArchitectureTest` | web/application 直接使用 JdbcTemplate/DataSource/Connection |
| `DataScopeSqlRewriteTest` | SELECT/COUNT/UPDATE/DELETE 任一范围类型改写错误 |
| `DataScopeStrictConsumptionTest` | 未消费、别名错误、错误表或嵌套上下文没有拒绝 |
| `GovernedTableRegistryTest` | 含 tenant/org/user 归属列的业务表既未注册也未显式豁免 |
| `TenantPredicateCoverageTest` | 受管表 SQL 缺 tenant 约束 |
| `IdorMatrixTest` | 列表、详情、导出、修改、删除存在跨用户/组织/租户访问 |

CI 必须运行四个可部署单元的接口授权立场检查，并为主应用生成接口权限与数据访问两份 Golden。

## 5. 可观测性与审计

新增指标：

```text
oa_data_scope_evaluations_total
oa_data_scope_predicates_total
oa_data_scope_denied_total
oa_data_scope_missing_total
oa_data_scope_alias_mismatch_total
oa_data_scope_bypass_total
```

生产告警：

- `missing` 或 `alias_mismatch` 任意增长立即告警；严格模式下请求同时失败。
- `bypass` 按调用方、原因和表聚合，出现新组合立即告警。
- `denied` 突增用于发现授权配置或组织数据异常。
- 日志只记录权限点、表、范围类型和 traceId，不记录业务敏感值。

## 6. 分阶段实施

### 阶段 A：冻结现状与安全配置

1. 生成数据访问清单：全部 handler、所有 Mapper/JdbcTemplate 路径和业务表。
2. 为现有数据访问标记 `SCOPED`、`BYPASS`、`PUBLIC` 或“待整改”。
3. 增加生产配置启动门禁：JWT、IAM enforce、data-scope strict。
4. 建立 `DataAccessSurfaceGoldenTest`，初始只报告、不阻断。

### 阶段 B：权限点级快照

1. 修改 PermissionSnapshot/Builder/Codec/PermissionChecker。
2. 为 scope L2 协议增加版本并验证旧快照自动重建。
3. 更新 `@DataScope`，要求明确 permission 和 table。
4. 先迁移已有 DirectoryService 和 AssetService，验证行为不变。

### 阶段 C：严格运行时

1. 实现执行记录、真实表策略注册表、嵌套上下文。
2. 未消费、别名错配、目标表错配全部 fail-closed。
3. 覆盖 SELECT、COUNT、UPDATE、DELETE；为 INSERT 增加可信字段写入和范围验证。
4. 增加指标、旁路审计和管理端诊断信息。

### 阶段 D：按模块迁移

推荐顺序：

1. `oa-org`、`oa-admin-biz`：已有样板，先进入 strict。
2. `oa-flow`、`oa-attendance`：审批、待办、请假、打卡及日结。
3. `oa-doc`、`oa-report`：公文、知识库、审计和导出。
4. `oa-notify-service`、`oa-file-service`、`oa-job-service`：独立服务和后台任务旁路。
5. `oa-iam`：权限真值查询使用受限系统旁路，管理查询仍做租户隔离。

每个模块完成“清单归零 + IDOR 矩阵 + strict 冒烟”后才进入下一模块。

### 阶段 E：全局强制与数据库纵深

1. CI 从报告模式切换为阻断模式。
2. 所有 JWT 环境默认 `OA_DATA_SCOPE_STRICT=true`，生产不提供静默关闭路径。
3. 清除未批准 JdbcTemplate 请求路径。
4. 多租户上线前完成 TenantLine + PostgreSQL RLS。
5. 运行全量 API/IDOR/性能回归后灰度发布。

## 7. 验收标准

- AC-01：所有 OA handler 均有接口授权立场，遗漏在构建期和运行期都被拒绝。
- AC-02：所有外部请求的数据访问均有 SCOPED/PUBLIC/BYPASS 立场，未声明无法通过 CI。
- AC-03：数据范围以 permission 为键；同模块其他权限的 ALL 不会放宽当前权限。
- AC-04：SELECT、COUNT、UPDATE、DELETE 均注入同一租户和数据范围。
- AC-05：INSERT 的 tenant/creator/org/path 来自可信上下文，不能由请求伪造。
- AC-06：未消费、alias/table 不匹配、解析失败和未知范围全部 fail-closed。
- AC-07：普通 web/application 代码无法直接使用 JdbcTemplate、DataSource 或 Connection。
- AC-08：每个敏感资源通过跨用户、跨组织、跨租户的列表/详情/导出/修改/删除 IDOR 用例。
- AC-09：生产启动时必须是 JWT + IAM enforce + data-scope strict。
- AC-10：strict 模式性能回归后，接口判权和常用列表 P99 不超过既有预算的 10%。

## 8. 预计修改范围

核心代码：

- `oa-security/.../annotation/DataScope.java`
- `oa-security/.../annotation/DataScopeBypass.java`（新增）
- `oa-security/.../port/PermissionChecker.java`
- `oa-iam/.../domain/PermissionSnapshot.java`
- `oa-iam/.../application/PermissionSnapshotBuilder.java`
- `oa-iam/.../infrastructure/cache/SnapshotCodec.java`
- `oa-iam/.../aspect/DataScopeAspect.java`
- `oa-iam/.../infrastructure/datascope/*`
- `oa-common/.../config/MybatisPlusConfig.java`
- 各业务模块 application/mapper 中的数据访问方法

测试与配置：

- 新增数据授权立场、Golden、SQL 改写、严格消费、租户和 IDOR 测试。
- 新增 `OA_DATA_SCOPE_STRICT` 配置、指标和 Runbook 告警项。
- 新增 `data-access-surface.golden`。

## 9. 回滚原则

- 数据库迁移只做 additive，不删除原字段或表。
- 模块迁移期允许按模块从 report 切换 strict；已进入 strict 的生产模块不允许静默退回放行模式。
- 紧急情况下只能回滚应用版本，不能用“忽略数据权限”作为常规恢复手段。
- permission-scope 快照协议带版本，回滚版本应拒绝未知 L2 数据并重建，避免复用不兼容快照。

## 10. 推荐实施顺序

先完成阶段 A～C 的平台能力，再迁移业务模块。不要先给所有现有方法批量补同一个注解：如果没有
权限点绑定、严格消费和 JDBC 门禁，看起来覆盖率变高，实际仍可能静默绕过。
