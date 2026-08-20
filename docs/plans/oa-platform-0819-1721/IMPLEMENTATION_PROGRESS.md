# OA Platform 实施进度（权威进度文件）

> 计划：同目录 `FINAL_PLAN.md`（845 行）+ `DECISION_RECORD.md`（ADR-0001~0011）
> 项目：**本仓库** · groupId `com.lrj.oa` · 版本 `0.1.0-SNAPSHOT`
> （2026-08-20 起随代码一起版本化；此前放在仓库外的 `../docs/plans/`，改动没有任何备份）

| Phase | 内容 | 状态 |
|---|---|---|
| **0** | 基建与硬门禁 | ✅ **完成，冒烟 14/14 全绿** |
| **1** | 组织域 | ✅ **完成，冒烟 28/28 全绿** |
| **2** | 权限域 ★最关键 | ✅ **完成，冒烟 32/32 全绿** |
| 3 | PC 前端底座 `oa-console` | 🔄 **主体已落地（15 步走完 0~12）**，待补测试 / 交付收口 / JWT（见文末） |
| **4** | 工作台 + 审批底座 | ✅ **完成，冒烟 25/25 全绿** |
| **4b** | ★ 接真中台（原 Phase 4 的阻塞项） | ✅ **完成，冒烟 28/28 全绿** |
| **5** | 考勤域 | ✅ **完成，冒烟 19/19 全绿** |
| **6** | 通知（后端） | ✅ **完成，冒烟 28/28 全绿** |
| 6 | 移动端 H5（先走 `/frontend-plan`） | ⬜ 未开始 |
| **7** | 文档与行政 | ✅ **完成，冒烟 45/45 全绿** |
| **8** | 报表 / 审计 / 跑批 / 文件 / 渗透 | ✅ **完成，冒烟 40/40 全绿** |
| 8 | k6 全链路压测 + nginx 前端交付 | ⬜ 待前端就绪 |

> 冒烟总计 **231 条断言全绿**（14+28+32+25+28+19+28+45+40）。
> **后端已全部完成**；剩余仅前端（Phase 3 PC 控制台、Phase 6 移动端 H5）与依赖前端的交付项。
> 全链路压测：工作台首屏 P99 **55.7 ms**（验收线 200 ms），6 个读路径全部在预算内。

---

## Phase 0 ✅（2026-08-19）

**冒烟**：`deploy/scripts/phase0-smoke.sh` → **通过 14 / 失败 0**

### 交付物
- 15 个 Maven 模块骨架（父 pom + `oa-dependencies` BOM + 10 业务模块 + 4 可部署单元），全 reactor 构建通过
- `oa-common`：`Result` / `ResultCode`（分区段编码）/ `BusinessException` / `TenantContext`
- `oa-security`：`UserContext`（**subject = Casdoor sub UUID**）/ `UserContextHolder` /
  `@RequiresPerm` / `@PublicApi` / `@DataScope` / `@Sensitive` / `DataScopeType` / `DataScopeRule` /
  `PermissionChecker` 端口（**只定义端口，实现在 oa-iam** —— oa-security 不依赖 oa-iam）/
  `OaSecurityConfig`（四个可部署单元共用，`oa.security.mode=DEV|JWT`）
- `oa-app`：主应用 :8400 + `HealthController` + `GlobalExceptionHandler` + Flyway `V1__baseline.sql`
  （7 个业务 schema + `btree_gist` + `pgcrypto`）
- 三个独立服务 main class + 配置：notify :8401 / file :8402 / job :8403
- `deploy/docker-compose.yml`（`name: oa-platform`，端口全变量化）+ `.env.example` + `phase0-smoke.sh`

### ★ 硬门禁 A —— JDK21 虚拟线程 pinning 实测结论：**通过**

| 组 | 配置 | 结果 |
|---|---|---|
| **对照组**（故意 `synchronized` + sleep） | 8 虚拟线程 | **pinning 事件 = 8** → 探针有效 |
| **真实负载**（HikariCP + JDBC） | 2000 虚拟线程 / 池 20 | 成功 2000 / 失败 0 / **248ms** / **pinning 事件 = 0** |

**结论**：`spring.threads.virtual.enabled=true` **保留启用**。
探针：`oa-app/src/test/.../Phase0PinningProbeTest.java`，用 JFR `jdk.VirtualThreadPinned` 事件计数。
**对照组是这个门禁最重要的一半** —— 一个测不出 pinning 的探针报"0 次"毫无价值。
后续引入新的 JDBC/ORM 组件（如 MyBatis 复杂映射、分布式锁客户端）需重跑本探针。

### ★ 硬门禁 B —— 中台制品可解析：**通过**
`com.lrj.workflow:workflow-platform-sdk:0.1.0-SNAPSHOT` 与 `com.lrj.authz:auth-platform-sdk:0.1.0-SNAPSHOT`
均可离线解析。
⚠️ **前提**：必须用 **system maven**（`/Users/liruijun/personal/devUtils/apache-maven-3.9.12/bin`），
其 `settings.xml` 的 `localRepository` 指向 `/Users/liruijun/personal/repository`。
**用 `./mvnw`（走 `~/.m2`）会解析不到中台制品** —— 故本项目**不提供 mvnw**，README 显式写死用 system mvn。

### ★ CI 纪律已验证会咬人（反证）
故意注入一个无授权注解的 `TempViolationController#leak` →
`ControllerPermissionCoverageTest` **BUILD FAILURE 并点名该方法**；移除后恢复绿。
（同 pinning 对照组的思路：能通过的检查 ≠ 有效的检查。）

### 已就位的 CI 纪律
| 测试 | 守什么 |
|---|---|
| `ControllerPermissionCoverageTest` | 每个 handler 必须有 `@RequiresPerm` 或 `@PublicApi(reason=...)`；`reason` 不得为空 |
| `ArchitectureRulesTest` | 跨模块只能走 `..api..`；模块无循环依赖；Controller 不直连 Mapper；禁 `java.util.Date` |
| `Phase0PinningProbeTest` | 虚拟线程 pinning 回归 |

### Phase 0 踩到的坑（记下来免得重踩）
1. **zsh 不做无引号变量分词**（bash 会）：`for m in $MODS` 会把整串当成一个词，
   结果建出一个名字含 15 个模块名的目录。脚本里循环一律显式列举或用数组。
2. **`oa-security` 引入 `spring-boot-starter-oauth2-resource-server` 会连带 spring-security**，
   默认拦截一切 → `/ping` 返回 401。已由 `OaSecurityConfig` 统一处理，
   并把"认证 vs 授权"的分工写进注释（授权归 `@RequiresPerm`，不在 SecurityConfig 里堆 antMatchers）。
3. **`spring-boot-maven-plugin:repackage` 要求有 main class**：三个独立服务只建目录不写
   `*Application` 会在 `install` 阶段失败。
4. Kafka 宿主端口必须与容器内 `PLAINTEXT_HOST` 监听端口**同号**（39092:39092），
   否则 `advertised.listeners` 对不上（workflow 项目已踩过）。

### 运行中的资源
```
docker: oa-postgres(:35432) oa-redis(:36379) oa-kafka(:39092) oa-minio(:39000/:39001)
compose project name = oa-platform（起前先 down --remove-orphans）
```

---

## Phase 1 ✅（2026-08-19）

**冒烟**：`deploy/scripts/phase1-org-smoke.sh` → **通过 28 / 失败 0**
**单测**：`OrgTreeSnapshotTest` 7 项 + Phase 0 的 9 项，全 reactor 绿

### 交付物
- `V2__org.sql`（167 行）：`org_unit` / `org_closure` / `job_position` / `employee` /
  `employee_org_assignment` / `reporting_line` / `org_tree_version`，
  含 `path text_pattern_ops` 索引、主岗偏唯一索引、实线上级偏唯一索引、路径格式 CHECK
- `OrgTreeSnapshot` 不可变内存快照（预计算后代/祖先，最小路径前缀归约）+ `OrgTreeCache`（COW）
- `OrgClosureMapper` 闭包维护 SQL（新增派生 / detach / attach / 一致性核对）
- `OrgUnitService`（建、移子树、改、撤销）、`EmployeeService`（入职、调岗、兼岗、汇报线、离职）
- `OrgQueryApi` + `OrgQueryService`（跨模块唯一入口）
- `SensitiveCrypto`（AES-GCM 密文列 + HMAC 确定性哈希列）
- `OrgSeedService`（PG COPY 批量装载）+ 开关控制的 `OrgSeedController`
- 22 个 REST 端点，全部带 `@RequiresPerm`（CI 覆盖检查通过）

### 实测数据
| 项 | 结果 |
|---|---|
| 批量装载 3,000 组织 + 10,000 员工 + 19,182 闭包 + 9,936 汇报线 | **~1.0 秒**（验收线 30 秒） |
| 组织树深度 | 6 层（集团→公司→事业部→中心→部门→团队） |
| 移动 341 节点子树后 closure/path 一致性 | **0 条不一致** |
| 内存快照同步 | 移动后立即生效，无需等轮询 |
| 逐级上报链 | 4 级（汇报线独立于组织树，确实走通） |
| `path` 前缀谓词 | 命中 `ix_org_path`，谓词被改写成 `~>=~`/`~<~` 范围扫描 |

### ★ Phase 1 抓到的三个问题（值得记住）

**① 内存快照的后代预计算写错了 —— 真 bug，且"看起来能跑"**
迭代式后序遍历里，节点第二次被 peek 时 `onStack.add()` 返回 false，被误判成环，
于是**所有有子节点的节点都只算出自己**；叶子节点却是对的，所以表面正常。
根节点的后代数从 3000 变成 1 —— 这会让数据权限直接漏掉整棵子树。
修法：用 `expanded` 集合区分"已展开子节点"，环检测改为"子节点已 expanded 但还没有结果"。
**已补 `OrgTreeSnapshotTest` 7 项单测**（含尾斜杠防前缀误匹配、3000 节点深链不爆栈、脏数据成环不死循环），
让构建来守这个类，而不是靠冒烟脚本事后发现。

**② 小表上 Seq Scan 是正确计划，不能用它判断索引没建对**
原断言 `EXPLAIN 里必须出现 Index` 在 3,000 行的表上必然失败 —— PG 选 Seq Scan 是对的。
正确的验证是 `SET enable_seqscan=off` 后看 **Index Cond 是否被改写成 `~>=~` / `~<~` 范围扫描**，
这个改写只有 `text_pattern_ops` opclass 才会发生，是判断 opclass 配对没配对的确凿证据。

**③ 冒烟脚本自身的两个坑**
- `jq -r 'length'` 作用在 `Result` 包装对象上返回 4（字段数）而不是数组长度，必须 `.data | length`；
- bash 里 `$VAR` 后紧跟中文标点会被并进变量名（`$DESC_ALL，` → 未定义变量），一律写 `${VAR}`。

### 其它设计要点（已落代码）
- `@TransactionalEventListener(fallbackExecution = true)`：用 AFTER_COMMIT 避免读到未提交数据；
  `fallbackExecution` 不能省，否则批量导入这类无事务上下文发的事件会被静默丢弃。
- `OrgTreeCache.rebuild()` **必须先读版本再读数据**。反过来会把旧数据打上新版本号，
  之后版本比对永远相等，快照永久陈旧 —— 现在这个顺序最差只是多重建一次。
- MyBatis-Plus 拦截器顺序：分页必须最后。Phase 2 的 `@DataScope` 要插在分页**之前**，
  否则会出现"列表被过滤了、总数没被过滤"的经典越权。
- `ArchitectureRulesTest` 原来带 `DO_NOT_INCLUDE_JARS`，而其它业务模块正是以 jar 形式挂在
  oa-app 的 classpath 上 —— 等于所有跨模块规则空转。已移除，并加了"确实扫到 oa-org 的类"前置断言。

---

## Phase 2 ✅（2026-08-19）—— 最关键的一刀

**冒烟**：`deploy/scripts/phase2-perm-smoke.sh` → **通过 32 / 失败 0**
**单测**：`DataScopeConditionTest` 9 项 + `OrgTreeSnapshotTest` 7 项 + Phase 0 的 9 项，全 reactor 绿

### 实测数据（验收标准逐条对上）

| 验收项 | 结果 |
|---|---|
| **判权 P99** | **1.25 μs**（验收线 1 ms，快了约 800 倍）；p50 = 0.25 μs |
| 授权 → 生效 | **立即**（本节点直接剔除缓存） |
| 撤权 → 失效 | **1 秒内**（走全局 epoch，1 秒是本地 epoch 缓存窗口） |
| 部门授权继承 | 授权给 2 层之上的部门，下级员工继承到；关掉 `include_descendants` 立刻不再继承 |
| 数据权限 | 同一接口：SELF 见 1 人 / ORG_AND_SUB 见 276 人 / ALL 见 10,000 人 |
| 调岗 → 数据范围 | 276 → 277 人，随新部门子树立即变化 |
| 临时授权 | 到点自动失效（快照 TTL 被压到到期时刻，没被 5 分钟 TTL 拖住） |
| JIT 提权 | 持永久授权仍被拒（3002）→ 激活后放行；提权到未持有的角色被拒 |
| 委托代理 | 代理关系进入代理人快照，且数据范围未被放大 |
| 影子校验 | 259 次采样，不一致 **0** 次 |

### 交付物
- `V10__iam.sql`（213 行）：permission / role / role_inherit / role_permission / grant_record /
  delegation / permission_condition / perm_epoch / perm_user_version + 22 个权限点目录 + 4 个内置角色 + 继承闭包
- `V11__self_service_perms.sql`、`V4__relax_period_checks.sql`（见下方"抓到的问题"）
- `PermissionSnapshot`（RoaringBitmap 位图）+ `PermissionSnapshotBuilder`（L3 权威重算）+
  `PermissionEngine`（L1 Caffeine / L2 Redis / L3 DB，实现 `PermissionChecker` 端口）
- `SnapshotCodec`（L2 编解码）、`PermissionCatalog`（权限点目录内存副本）
- `InvalidationBus` + `RedisInvalidationBus`（跨节点失效总线，组织树缓存一并接上）
- 四个注解的实现：`RequiresPermAspect` / `DataScopeAspect` + `OaDataPermissionHandler`（SQL 改写）/
  `SensitiveSerializer` + `SensitiveModule`（序列化层脱敏）/ `UnannotatedHandlerGuard`（fail-closed 兜底）
- `GrantService`（授权 / 撤权 / JIT 提权 / 委托 / 到期回收）、`BootstrapAdminInitializer`
- `UserContextFilter`（JWT 或 DEV 模式 `X-OA-User` 装配身份）
- 通讯录读模型视图 `v_employee_directory` + `DirectoryService`（数据权限第一个真实落点）
- 权限调试器后端 `/iam/admin/explain`（"为什么这个人能看到这个"）+ `/bench`

### 关键设计决策（实现期定的，计划里没写这么细）

**① 失效策略刻意不对称 —— 收权走全局，授权走个人**
- **收权**（撤销、部门级授权变更、组织树变更、离职）→ bump 全局 epoch，各节点 1 秒内全量作废。
  代价是一次全量重算，换的是"撤权立即生效"这条安全底线。
- **给个人加权** → 只 bump 用户版本 + 本节点剔除 + 通知其它节点。
  通知丢了最坏是新权限晚几分钟生效，**不构成安全问题**。

  这样热路径只需比对一个每秒刷新的全局 epoch，不必为每个请求查一次用户版本 —— 这是 P99 能压到 1μs 的关键。

**② 快照 TTL 必须被临时授权的到期时刻压低**
少了这一步，一条 30 秒后过期的 JIT 提权会被 5 分钟 TTL 的快照继续放行 —— 一个安静的提权漏洞。

**③ 兜底方案由"URL 映射表"改成"fail-closed 拦截器"**
计划原写"用 URL 映射表做兜底过滤器"。实现时改成"**没有授权注解的 handler 一律拒绝**"，
理由是更可靠：URL 表要人维护，漏配一条就漏一个接口；而 fail-closed 覆盖**全部**未来新增接口，不需要任何人记得做什么。

**④ JIT 提权只能激活「已持有」的角色**
否则"自助提权"就等于"自助越权"。这也正是 JIT 的原义（如 sudo：不在 sudoers 里的人执行 sudo 也没用）。
因此提权接口可以放开给普通员工自助调用，闸门在服务层而不在接口权限上。

**⑤ 委托代理与 JIT 提权是两件事**
委托**不叠加权限**，只让代理人能办委托人本来就该办的待办；已用冒烟断言守住。

### ★ Phase 2 抓到的问题（六个，其中五个是真 bug）

1. **影子校验同步执行会顶穿 P99** —— 每次校验做一次完整 DB 重算（约 5ms），
   1% 采样等于给 **1% 的全部请求**加 5ms，实测 p99 从 0.9μs 飙到 **5034μs**。
   改成有界队列 + 丢弃策略的异步执行后回到 **1.25μs**。诊断手段绝不能拖慢它要诊断的东西。
2. **`BootstrapAdminInitializer` 写了授权却没走失效协议** —— `ApplicationRunner` 跑在 Web 服务器启动
   **之后**，这段窗口里已有请求把"该用户无权限"的空快照缓存住；只 bump 数据库 epoch 不够，
   本地 epoch 有 1 秒缓存窗口。表现为"引导明明成功了，管理员却还是 403"。补 `evictAllLocal()`。
3. **调岗不触发权限失效** —— 数据范围锚定在本人所在组织上，而调岗改的是任职关系不是组织树。
   只监听组织树变更的话，调岗后数据范围会一直停在旧部门，是个安静的越权。
   新增 `EmployeeAssignmentChangedEvent`；离职额外**撤销该账号全部授权**（只失效快照不够，
   授权还在库里，userId 复用或账号重启用时权限会"复活"）。
4. **`#{keyword} IS NULL` 让 PostgreSQL 推断不出参数类型** —— 整条查询报
   `could not determine data type of parameter $1`，接口 500、字段全 null，极易被误读成"数据为空"。
   改用动态 SQL 把条件整段去掉。
5. **同日创建又撤销违反 `effective_to > effective_from`** —— 建错部门当天撤销、上午入职下午调岗
   都是合法的，`[from, to)` 左闭右开下 `from == to` 表示"零长度、从未生效"，正是纠错需要的表达。
   三处约束一并放宽为 `>=`（`V4__relax_period_checks.sql`）。
6. **`Map<String,Object>` 接结果集在 PG + MyBatis 下是个陷阱**（本阶段踩了**两次**）——
   PG 把列名转小写、MyBatis 又可能按 `mapUnderscoreToCamelCase` 改键名，
   两层规则叠加后 `map.get("mobile_enc")` 该写成什么全靠猜，猜错就静默返回 null。
   一律改成类型化 DTO（`RolePermissionRow` / `DirectoryRow`）。

### 顺带定下的两条工程约定
- **Flyway 开 `out-of-order`**：按模块划分版本号区间（V1 app / V2-V9 org / V10-V19 iam / V20+ 其它）
  必然产生"后补的 V4 排在已执行的 V10 之前"。接受它的前提是各模块 schema 互不相交、跨模块迁移无顺序依赖；
  将来若出现跨模块顺序依赖，应改用时间戳版本号而不是继续开这个开关。
- **MyBatis-Plus 拦截器顺序**：数据权限必须排在分页**之前**，否则分页改写出的 count 语句绕过数据权限，
  出现"列表被过滤了、总数没被过滤"的经典越权。

### 已知边界（明确没做）
- `USER_GROUP` 主体类型：表里预留，解析器遇到会**告警并跳过**（需要一张动态人群表，Phase 3）。
- ABAC 条件引擎：建表完成，`oa.iam.abac.enabled=false`，Phase 4 报销场景才开。
- JIT 提权尚未接审批流：`approval_instance_id` 字段已留，Phase 4 接 `oa-elevation-v1` BPMN。

---

## Phase 4 ✅（2026-08-19）—— 审批底座

**冒烟**：`deploy/scripts/phase4-flow-smoke.sh` → **通过 25 / 失败 0**

### 交付物
- `V20__flow.sql`（212 行）：form_template（JSON Schema 版本化）/ approval_instance / approval_node_log /
  todo_item（CQRS 宽表）/ oa_outbox + oa_inbox / leave_type + leave_balance + leave_balance_txn /
  leave_request / id_segment；`V21__flow_perms.sql` 权限点
- `ApproverResolver` —— **ADR-0010 的实现**：审批人在 OA 侧算好塞进流程变量，中台不反查 OA
- `OutboxPublisher` —— 事务发件箱投递，`FOR UPDATE SKIP LOCKED` 多实例并行，指数退避
- `ApprovalService` / `LeaveService` / `TodoService`（含与中台对账）
- `WorkflowGateway` 端口 + `RemoteWorkflowGateway`（接中台）/ `LocalWorkflowGateway`（本地测试替身）
- `SegmentIdGenerator` + `SegmentAllocator`（号段模式单号）
- `JsonbTypeHandler`（见下）

### 实测（关键几条）
| 项 | 结果 |
|---|---|
| 审批人计算 | 2 天 → 1 级（= 实线上级）；5 天 → 2 级。**量级驱动级数** |
| 事务发件箱 | `StartProcessCommandV1` 实发到 Kafka，消息含 `approverChain`、符合 `EventEnvelopeV1` 信封 |
| 待办读模型 | 审批人看到 1 条、申请人看不到自己的；办理后即从工作台消失 |
| 额度 | 提单冻结 2 → 通过后 used=2 frozen=0 available=8；驳回则释放 |
| 幂等 | 重放 CONSUME 流水被唯一约束挡下，已用天数没被扣成 4 |
| 组织快照 | 审批单冗余发生时 `org_path`，索引是 `text_pattern_ops` |

### 关于中台集成的现状（诚实说明）
`RemoteWorkflowGateway` 的**办理**路径目前会抛出明确异常，因为 workflow-platform 现有 SDK
只有审方专用的 `completeReview`，通用审批需要中台补一个 `completeTask` 端点
（计划里就列为 Phase 4 的前置依赖，只增不改）。在那之前用 `oa.flow.workflow.mode=LOCAL`
走**本地测试替身**跑通端到端 —— 替身只按 `approverChain` 做顺序流转，语义与目标 BPMN 一致，
且默认不装配。中台就绪后改一个配置即可，业务代码一行不动。

**顺带做的事**：`/Users/liruijun/personal/repository` 里装的 workflow-platform SDK jar（8-15）
比中台源码旧，缺 `claimTask`/`reassignTask`。已用 system mvn 重装 protocol + sdk 两个制品
（中台源码一行未改，工作树是干净的）。

### ★ Phase 4 抓到的问题
1. **PG 的 jsonb 列不接受 varchar 参数** —— 直接绑字符串报
   `column is of type jsonb but expression is of type character varying`。
   用 `JsonbTypeHandler`（PGobject）精准解决，而不是在 JDBC URL 上加 `stringtype=unspecified`
   ——后者会放松**所有**字符串参数的类型推断，用全局的宽松换局部的方便。
   顺带修掉 Phase 2 的**潜伏问题**：`grant_record.scope_org_ids` / `delegation.process_keys`
   也是 jsonb，之前只因为冒烟里从没传过值才没暴露。
2. **`@Transactional` 标在 MyBatis Mapper 接口上无效** —— Mapper 由 MyBatis 代理，
   不走 Spring 事务代理。号段推进必须是独立事务（`REQUIRES_NEW`），否则业务回滚会把号段退回去，
   下一张单据拿到同一批号，撞唯一约束才暴露。已拆出 `SegmentAllocator`。
3. **`@MapperScan` 通配只扫 `..infrastructure.mapper`** —— 嵌在 application 包里的 mapper 接口
   扫不到，启动即失败。Mapper 一律放规定的包下。
4. **psql 无返回行时仍会把命令标签 `INSERT 0 0` 打到 stdout** ——
   用 `RETURNING id` 是否为空来判断幂等，结果永远为"有输出"。冒烟里改成直接数行数。

---

## Phase 5 ✅（2026-08-19）—— 考勤与早高峰削峰

**冒烟**：`deploy/scripts/phase5-attendance-smoke.sh` → **通过 19 / 失败 0**

### 实测（验收线：500 QPS、错误率 0、无重复打卡）
| 项 | 结果 |
|---|---|
| 吞吐（10,000 人同时打卡，并发 300） | **512 QPS**，0 失败，0 重复 |
| 单请求延迟（并发 20，测服务端真实处理时间） | **P50 = 3.0 ms，P99 = 53 ms**，4,975 QPS |
| 落库 | 12,000 条全部落库、零丢失、零死信、零重复 |
| 分区 | 13 个分区（12 个月 + 兜底），数据全落当月分区，兜底分区为空 |
| 日结 | 10,000 人 **165 ms**（聚合全在数据库里完成） |
| 部门继承 | **一条根组织授权** → 10,000 人全部继承到打卡权限 |

### 交付物
- `V30__attendance.sql`：`punch_record`（按月声明式分区 + 兜底分区）/ `punch_dead_letter` /
  `attendance_daily` / `shift`；`V31__attendance_perms.sql`
- `PunchService` 削峰链路：Redis 幂等 → 有界队列 → 批量落库；队列满降级同步直写；
  落库失败**撤销幂等键**并进死信
- `AttendanceService` 日结（SQL 内聚合）
- `PunchLoadTest.java` 单文件压测程序（虚拟线程 + HttpClient，`java` 直接运行，无需构建）

### ★★★ Phase 5 抓到的最重要的一个坑（与 Phase 0 硬门禁直接相通）

**`Caffeine.get(key, loader)` 在虚拟线程下会 pin 载体线程。**

为了消除"一次请求打约 11 次库"的重复查询，加了员工读缓存。结果吞吐从 **555 QPS 塌到 17 QPS**，
9,999 次请求失败于 `Failed to obtain JDBC Connection` —— **加了缓存反而比不加慢 30 倍**。

原因：`Caffeine.get(key, mappingFunction)` 底层是 `ConcurrentHashMap.computeIfAbsent`，
它持有 bin 上的 `synchronized` 锁执行 mappingFunction。而 JDK21 的虚拟线程一旦在
`synchronized` 块里阻塞（这里是 JDBC 查询）就会 **pin 住载体线程**。
万人冷启动时几百个虚拟线程各自加载不同 key，载体线程被逐个钉死，整个应用停摆。

**这正是 Phase 0 硬门禁 A 测的那件事** —— 当时结论是"HikariCP + JDBC 不 pin"，
但那只覆盖了当时的组件。**新引入任何在 `synchronized` 里做 I/O 的组件都要重跑那个探针**，
这条已写进 CLAUDE.md。

修法：改用 **get-then-put**（`getIfPresent` → 加载 → `put`），加载过程完全不持锁。
代价是同一 key 可能被并发重复加载，但对"每人一个 key"的访问模式几乎不会发生。

### 另外两条
- **压测的延迟数字要看并发**：300 并发下 P99 = 1469 ms，看着很差；但那 ≈ 并发数 ÷ 吞吐
  （Little 定律），量的是**客户端排队**而不是服务端能力。把并发压到 20 再测，
  P50 = 3 ms、P99 = 53 ms —— 这才是"入队即返回"的真实成本。**别用高并发下的延迟当 SLO。**
- **Redis 幂等键要跟表一起清**：冒烟只 TRUNCATE 表、不清 Redis，上一轮的键会让这一轮
  全部被判成"已打卡"。这也顺带说明：Redis 与数据库可能不同步，
  **数据库上的唯一约束才是真正的幂等底线**，缓存那层只是省一次往返。

### 已知边界
排班（`shift` 只建了标准班）、加班调休、假期额度年初批量发放跑批未做；
`oa-job-service` 尚未接管日结与分区滚动（当前由接口手动触发）。

---

## Phase 3 待办（2026-08-19 立项时的清单，**已被文末的实施记录取代**）
> ⚠️ PC 前端**动手前必须先走 `/frontend-plan`**（auth-console / workflow-console 都是这么做的）——
> 已于 2026-08-20 走完，产出 `../oa-console-0820-0500/{DECISION_RECORD,FINAL_PLAN}.md`。

1. ~~monorepo（pnpm workspace）+ `@oa/shared` + 克隆 auth-console 脚手架~~
   → 决策改为**单包 + alias `@oa/shared`**（ADR-C2：可迁移税比过早 workspace 便宜）
2. ~~Casdoor SSO 接入 + 权限渲染体系~~ → 权限体系已完成；**JWT 切换独立为 step 15，仍阻塞**
3. ~~组织树 / 员工 / 角色授权页~~ → 已完成
4. ~~★ 权限调试器页~~ → 已完成（三栏沙盘，消费 `why` / `preview` / `explain`）
5. ~~万人通讯录~~ → 已完成（游标分页 + 虚拟滚动 + IndexedDB 增量同步）

**当前真实进度见文末「Phase 3 🔄 PC 控制台 oa-console」。**

---

## Phase 4b ✅（2026-08-20）—— 接真 workflow-platform

**冒烟**：`deploy/scripts/phase4b-remote-smoke.sh` → **通过 28 / 失败 0**

Phase 4 遗留的唯一阻塞项（中台缺通用 `completeTask`）已清除。

### 中台侧（`workflow-platform`，分支 `feat/generic-complete-task`，只增不改）
- `CompleteTaskRequest` / `TaskApplicationService.completeTask` / `POST /api/v1/tasks/{id}/complete`
- SDK 新增 `completeTask` 与 `findProcesses`，均为 **default 方法** —— 既有第三方实现不会因升级编译不过
- **outcome 不做枚举校验**：取值域由消费方 BPMN 的网关解释。中台若枚举结论，
  每加一种业务结论都要改中台，违背"编排通用、语义归业务"
- 信任边界：办理身份与 actionId 由服务端写入并**覆盖**调用方 variables 里的同名 key。
  保留名单含 `decision`/`opinion` —— 否则通用办理可注入 decision，被审方 BPMN 的结论网关误读。
  由 `CompleteTaskVariableGuardTest` 守住。中台测试 83 → 84 全绿。

### OA 侧
- `oa-generic-approval-v1.bpmn20.xml`（含 BPMNDI）：顺序多实例按 `approverChain` 串级，
  `completionCondition` 让任一环节 REJECT 立即终止。**BPMN 里没有任何 OA 概念** ——
  请假/报销/用印共用同一份定义，差异全在 `ApproverResolver` 算出的名单里（ADR-0010）
- `OaBpmnDeployer`：定义放消费方、由消费方自己 deploy，中台对 OA 零感知。先查后部
  （中台 deploy 端点不做重复过滤，不查会每次重启多一个版本）
- `WorkflowCommandKafkaConfig`：命令按主题前缀投到**中台自己的** Kafka
- `ApprovalService.syncFromRemote`（办理后即时对齐）+ `reconcileInstances`（回绑 pid、发现流程结束）

### 实测
| 项 | 结果 |
|---|---|
| 提单 → 中台起流程 | businessKey 对上，phase=WAITING_USER |
| 审批人 | 5 天 → 2 级，第一级 = 实线上级；一级通过后中台把任务交给二级 |
| 待办 | 二级待办**即时**出现（不等 60 秒对账）；申请人看不到自己的单 |
| 收尾 | 中台 phase=COMPLETED → OA 单据 APPROVED、额度 used=5/frozen=0、待办清空 |
| 驳回 | REJECT 后流程立即终止，冻结额度释放且已用天数未被误扣 |

### ★ 三个"没有错误日志"的静默失败
1. **SDK 的 `workflow.client.enabled` 默认 false** → 注入 `NoopWorkflowClient`，
   查询返回空列表、不抛异常。网关一边宣称 `remote()=true` 一边一条待办也查不到。
   已在 `RemoteWorkflowGateway` 构造器 fail-fast，并由冒烟断言守住。
2. **两套 compose 各有一套 Kafka**：投错总线时 `send()` 照常返回 offset，消费方永远收不到。
3. **中台实例查询路径是 `/process-instances` 且 `definitionKey` 必填**：
   路径写错得 404、漏参数得 400，都不会提示"你少了个参数"。

⚠️ **容器间跨 compose 联通仍是缺口**：两边的 Kafka 都 advertise 成 `kafka:9092`，
把 oa-app 接进 workflow 网络会让 `kafka` 这个名字在 oa-app 里变成二义。
本轮的 REMOTE 验证是**宿主进程**跑 oa-app（走 localhost:29092 / :8300）。
生产部署需要给两套 compose 规划不冲突的 broker 别名与 advertised listener。

---

## Phase 6 ✅（2026-08-20）—— 通知域（后端）

**冒烟**：`deploy/scripts/phase6-notify-smoke.sh` → **通过 28 / 失败 0**

| 验收项 | 结果 |
|---|---|
| **全员公告 10,000 人**（验收线 < 30s） | **502 ms** |
| **已读回执存储**（验收线 < 100KB） | **15 字节**（8,000 已读；runOptimize 把连续区间压成 run 编码） |
| 站内信落库 | 10,000 条全部落库 |
| 长连推送 | 在线用户即时收到（`WsProbe.java` 单文件探针，curl 不会说 WebSocket） |
| 未读/已读 | 8,000 已读 / 2,000 未读，能从位图反查出具体未读人 |

### 交付物
- `V40__notify.sql` + `V41__notify_dedup_fix.sql`：recipient 稠密索引 / notification（月分区）/
  announcement / announcement_read + announcement_audience（位图）/ channel_log / notification_dedup
- notify-service **自己跑 Flyway**，独立历史表 `flyway_schema_history_notify`
- `RecipientIndex`（userId UUID → 稠密 int）、`ReadReceiptStore`（位图 + 内存缓冲批量落库）
- `SessionRegistry` + `NotifyWebSocketHandler` + 握手期身份装配
- `NotifyChannel` SPI + 四渠道占位实现 + `ChannelDispatcher`（默认全关）

### ★ 抓到的两个真 bug
1. **幂等索引形同虚设**：分区表要求唯一索引含分区键，加上 `created_at` 后
   "唯一"变成"同一时刻才算重复"。冒烟里同 dedupKey 连发两次都 `inserted=1` 才暴露。
   修法：幂等键搬到**不分区**的 `notification_dedup` 表。
2. **独立服务的权限拒绝返回 HTTP 500 + 空 body**：`GlobalExceptionHandler` 只在 oa-app 里。
   拦是拦住了，但前端无从区分"无权限"和"服务器坏了"。已移到 `oa-common` 共用。

### 关键取舍（注释里都写了为什么）
- **判权引擎嵌进 notify**，而不是 HTTP 问 oa-app：判权在热路径，契约是零远程调用 P99<1ms。
  但把 iam/org 的 web 层排除掉，不在 :8401 再开一套 IAM 管理面。
- **已读先进内存位图、定时批量落库**：万人同时点开时逐次 `SELECT FOR UPDATE` 会把所有人
  排在同一把行锁上。代价（崩溃丢一个刷盘周期的已读）在公告场景可接受；
  **同样手法不能用在额度与打卡上**。
- **长连发送用 ReentrantLock 不用 synchronized**：`sendMessage` 里是网络 I/O，
  synchronized 会 pin 载体线程（Phase 5 的 Caffeine 教训）。
- **公告受众由发布方算好传入**，通知域不解析组织结构（同 ADR-0010 的原则）。

### 工程纪律修补
权限覆盖检查原来只活在 oa-app 的测试里，而 ArchUnit 扫的是 **classpath** ——
oa-app 不依赖 notify/file/job，那三个可部署单元的 Controller **一个都没被检查过**。
规则抽到 `oa-security` 的 test-jar（只此一份），规则自身先断言"确实扫到了 @RestController"。
已用反证验过会咬人（注入无注解 handler → 构建失败并点名）。

---

## Phase 7 ✅（2026-08-20）—— 文档域 + 行政域

**冒烟**：`deploy/scripts/phase7-doc-admin-smoke.sh` → **通过 45 / 失败 0**

### ★ 会议室并发预定（验收线：100 请求只成功 1 条）
```
100 并发抢同一时段 → 200 × 1，409 × 99，库里 1 条
```
靠 `EXCLUDE USING gist (room_id WITH =, during WITH &&) WHERE status='BOOKED'`。
应用层**不**实现第二份冲突检测：那是 check-then-act，要堵住就得引入分布式锁，
锁的粒度/超时/重入/脑裂全是新坑。应用层只把约束冲突翻译成人话。
区间用 `[)`：11:00 起的下一场能订上，部分重叠被拒；取消只改状态即让出时段。

### 其它并发写点（各用各的手段，理由写在方法上）
| 场景 | 手段 | 实测 |
|---|---|---|
| 车辆预定 | 同一套排他约束（不写第二份实现） | 重叠被拒 |
| 用品库存 | 条件更新 `WHERE stock >= ?` + `CHECK(stock>=0)` | 50 人抢 10 件，恰好 10 成 |
| 资产领用 | `SELECT FOR UPDATE` + 状态机 | 30 人抢 1 台，只 1 人领到 |

### 公文
文号**核发时**才生成（撤销的稿子不占号，年度序列不能有空洞），用 `step=1` 的号段
换连续性；实测 6 号 → 7 号连续。`CHECK` 保证 ISSUED 必有文号。

### 知识库：接口权限之外还有对象级权限
所有员工都有 `oa:kb:read`，只做接口层判权 = 换个 id 就能读别人的私有文档。
`KbAuthorizer` 端口双实现：内置 `kb_share` 完整可用（ORG 共享按 org_path 前缀继承），
SpiceDB 路径默认关**且未接通时 fail-closed** —— 放行才是灾难。
⚠️ SpiceDB 接通的前置仍未做：`oa.zed` 必须与 knowledge/his/recsys/risk 合并后整体写入（R7）。

### ★ 抓到的问题
1. **号段生成器错放在 oa-flow**（计划里属 oa-common），导致 oa-doc 要拿文号只能跨业务模块依赖。
   已归位并把它 `synchronized` 块里做 JDBC 的 pinning 隐患换成 `ReentrantLock`。
2. **`@DataScope` 只对走 MyBatis 的查询生效**。标在 JdbcTemplate 手写 SQL 的方法上，
   切面照常设上下文、拦截器永远不被调用 —— 注解明晃晃写着，实际返回全量数据。
   已加"设了却没人消费"的告警；正确做法是需要数据权限就用 Mapper。
3. **PG 自带分词切不了中文**：`to_tsvector('simple','关于国庆放假的通知')` 是一个整 token，
   搜"放假"永远 0 行，接口返回 200 + 空数组，像"确实没这份文件"。改 `pg_trgm` + ILIKE。
4. **裸 SQL 改 grant_record 会绕过失效协议**（排查最久的一个）：epoch 不 bump，
   L1+L2 的旧快照继续放行；清 Redis 也不够，**必须先 FLUSHDB 再重启**。
   靠 `/iam/admin/explain` 的 `consistent=false` 才定位到 —— 影子校验第一次派上用场。
5. 改已执行过的迁移 = Flyway checksum 不匹配、启动失败（这是它该有的行为，别 repair 绕过）。

---

## Phase 8 ✅（2026-08-20）—— 报表 / 审计 / 跑批 / 文件 / 渗透

**冒烟**：`deploy/scripts/phase8-report-audit-smoke.sh` → **通过 40 / 失败 0**

最后三个空模块（`oa-report` 0 行、`oa-job-service` / `oa-file-service` 各 12 行）填上了。

### 审计（oa-report）
- `V80__report_audit.sql`：`audit_log` 按月分区 + `dict` + `sys_config` + 4 个驾驶舱视图
- **只记写操作**：全记 GET 会让表每天涨几百万行，一张查不动的审计表等于没有审计
- `AuditRecordingAspect` 自动覆盖所有 POST/PUT/DELETE handler ——
  手写 `audit.record()` 必然会漏，而漏掉的恰好是新加的、最需要看住的那个接口
- 异步有界队列 + 满则丢弃**并计数**：诊断手段不能拖慢它要诊断的东西（Phase 2 影子校验的学费）

**★ 抓到自己的 bug**：切面 Order 一开始写成 `+50`，即在 `RequiresPermAspect(+10)` 的**内层**，
于是判权抛异常时审计切面根本没被进入 —— SUCCESS 全都记着、**DENIED 一条也没有**。
而"谁在试探哪个接口"恰恰是审计最该回答的问题。改成 `+5` 后 2 条越权尝试正确留痕。

### 驾驶舱
4 个只读视图（编制人效 / 审批时效 / 考勤汇总 / 资产分布）。用视图不用物化视图：
万人级下这些聚合是秒级的，物化带来的刷新时机、陈旧度、并发刷新锁全是额外复杂度。
真扛不住了再物化，那时也有真实数字支撑决策。

### 跑批（oa-job-service）
- 幂等靠 `uk_job_idem` 唯一约束**抢坑**，不用分布式锁 —— 不需要"持有"什么，
  只需要"这件事有没有人做过"；锁的超时/续期/脑裂全是不必要的复杂度
- 失败**不删占位行**：删了就变成自动无限重试，一个必然失败的 job 会淹掉日志和数据库
- 接管 Phase 5 / Phase 2 遗留的手动触发：考勤日结（**16 分片**，`employee_id % 16`
  而不是 id 区间 —— 区间会随离职留下空洞，越切越不均）、授权到期回收、分区滚动、年假发放
- 实测：日结 16 片全部 SUCCESS；重复触发被幂等挡下（run 行数仍为 1）

### 文件（oa-file-service）
- `object_key` 用 **UUID**：可枚举的 key + 预签名 URL = 拿到一个就能推出别人的，
  而预签名是**直连对象存储**的，签出去就绕过了全部应用层判权
- 预签名仅在判权通过后签发且短时效（**时效即泄露窗口**）
- 首版判权保守到"仅上传者可读"，注释写明了为什么宁可太紧

### ★ 渗透用例（验收标准第 5 条）全过
| 用例 | 结果 |
|---|---|
| 员工给自己授权 | 403（自助提权 ≠ 自助越权） |
| 员工查审计 / 核发公文 | 403 |
| 改文件 id 下载 / 预签名别人的文件 | 403 |
| 改知识库 id 读别人的文档 | 403（接口权限之外还有对象级） |
| 绕过前端直调建组织接口 | 403（页面权限只是体验，接口权限才是边界） |
| 无身份的写请求 | 被拒 |
| **上述 5 次越权尝试** | **全部在 audit_log 留痕** |

### 审计查询要 JIT 提权
持 `SUPER_ADMIN` 直查审计仍返回 **3002**，提权后才放行 ——
让"我现在要查审计"成为一个有记录、有时限的动作，而不是某人默默常驻的能力。
（`stats`/`flush` 用 `oa:report:view`：权限分级按**暴露了什么**划，不按属于哪个模块划。）

### 交付
四个镜像重建后整栈 `--profile apps up -d`，**四个容器全部 healthy**。
compose 补齐了三个独立服务此前缺失的 PG 凭据 / 加密密钥 / healthcheck ——
它们现在各有自己的表、自己的 Flyway 历史表和内嵌判权引擎，
按 Phase 0 的占位配置起来会是"容器 Up 但功能不可用"。

---

## Phase 3 前置 —— 后端缺口修复（2026-08-20）

走 `/frontend-plan` 时六路只读调研核实出 **10 个"前端做不下去"的后端缺口**，
其中 3 个是硬阻塞。**全部在前端动手之前补掉**（commit `dcadfc2`），而不是让前端绕。

**为什么不绕**：前端绕这些缺口的方式分别是「硬编码 roleId」「自己拼来源链」「DEV 冒充身份」——
每一个都是把后端语义在前端抄一遍。分叉那天，权限沙盘会**理直气壮地解释错**，
而那正是这个页面存在的全部意义。

### 补齐清单（全部实测通过）

| # | 缺口 | 补法 |
|---|---|---|
| 1 | **无角色列表端点** —— 授权与提权都要 roleId，前端只能硬编码 bigserial 生成的 id | `GET /iam/roles` · `/iam/roles/mine` · `/iam/permissions/catalog` |
| 2 | **CORS 只写了 `.cors(withDefaults())` 没有 source** —— 跨源 preflight 403，失败在浏览器侧、后端日志干净 | 显式列举来源（不用 `*`）+ expose `X-OA-Perm-Version` |
| 3 | **9 个 MENU 权限点 route/icon/parent/sort 全 NULL** —— 前端必须维护第二份菜单定义 | `V13__menu_metadata.sql`；顺带把 3 个"有权限点没有 handler"的孤儿 code 标 DISABLED |
| 4 | **无"以他人视角预览"** —— 沙盘右栏只能 DEV 冒充，JWT 下上线即失效 | `GET /iam/admin/preview?userId=` |
| 5 | **`/explain` 不返回来源链** —— 沙盘中栏（§16 核心诉求）无米下锅 | `GET /iam/admin/why?userId=&permCode=` |
| 6 | **`/directory/delta` 零实现** | `sync_seq` 水位线 + 触发器 + 墓碑视图 + `fullResync` 逃生舱 |
| 7 | **通讯录只有 limit≤500、无分页** | `GET /org/directory/page?cursor=&size=` |
| 8 | **无 `perm.changed`**，"改角色不刷新即生效"链路是断的 | `X-OA-Perm-Version` 响应头（零额外请求） |
| 9 | `/me` 未下发 `moduleScope` | 补上 |
| 10 | **无"我发起的单据"列表** —— 提单后不记住单号就再也找不回来 | `GET /flow/todos/mine`（UNION 请假 + 11 类通用单据） |

### 几个设计要点

- **水位线用序列不用 `updated_at`**：时钟回拨会让那段时间的更新被**永远跳过**；
  同毫秒多行会在游标边界重复；批量导入时时间戳全相同无法分页。
- **任职变更也要 bump 员工水位线**：调岗时 `employee` 表一个字节都没变，
  但他在通讯录里的部门变了。只看 employee 的变更，调岗永远同步不过去。
- **墓碑必须单独查**：离职是"行从视图里消失"而不是"行变化"，
  只发变更行的话客户端**永远收不到消息**，本地会一直躺着已离职的人。
- **墓碑刻意不带数据权限**：告诉客户端"删掉这个 id"不泄露任何信息（它本来就在客户端）；
  反过来若墓碑被过滤，一个人调岗出我的可见范围时我收不到墓碑，本地就永远留着他 —— **那才是泄露**。
- **`X-OA-Perm-Version` 而不是轮询**：万人 / 15 秒 = 667 QPS 常态基线，与打卡峰值同量级，
  只为一个几乎从不变的东西。而用户在操作时本来就在发请求。

### 路上踩的两个

1. `role_inherit` 的列叫 `ancestor_role_id` / `descendant_role_id`，不是 `ancestor_id`。
2. `subject_id::bigint` 在 USER 授权（Casdoor sub 是 UUID 串）上会炸；
   **加 `~ '^[0-9]+$'` 守卫也不够** —— PG 不保证 AND 的短路顺序，优化器仍可能先算那个转换。
   必须反过来把 `og.id` 转 text。这类错误只在库里恰好有 USER 授权时才出现，干净的库上测不出来。

---

## 通用业务单据（2026-08-20）—— 补齐 Phase 4 的"12 类只做了 1 类"

**一份实现服务 11 类**（出差/加班/调休/报销/借款/用印/合同/采购/入职/离职/调岗）。
差异全部抽成数据：表单字段 → `form_template` 的 JSON Schema；审批级数 → `approval_level_rule`
（driver = DAYS | AMOUNT | FIXED + 有序阈值）。流转、留痕、发件箱、待办投影完全共用。

复制 11 份的代价不在写的时候，而在**以后每改一次审批链逻辑要改 11 处**，
且必然有一处忘了改 —— 那一处会安静地按旧规则跑下去。

| 实测 | 结果 |
|---|---|
| 报销 800 元 | 1 级 |
| 报销 30000 元 | 3 级（受汇报链长度上限，不凭空造审批人） |
| 出差 10 天 | 3 级 |
| 用印 FIXED | 2 级（不看量） |
| 缺必填 / 低于下界 / 未知类型 | 1400 / 1400 / 4003，错误信息都能直接给用户看 |

**请假刻意不走这里**：它有额度冻结/扣减/释放的强一致语义，塞进通用 jsonb 会让那套约束无处安放。
"大部分通用 + 少数特殊单独处理"比"全部通用"和"全部特殊"都更诚实。

**规则表缺兜底档时显式失败**而不是默认 1 级 —— 一张五十万的合同走一级审批是事故。
**结束回调的类型清单从规则表读**，不写死：否则新插一类单据，提单成功、审批走完，
单据状态却永远停在 PENDING（没人注册回调），每一步看起来都成功了。

---

## Phase 3 🔄（2026-08-20）—— PC 控制台 `oa-console`

计划 `../oa-console-0820-0500/FINAL_PLAN.md`（15 步）· 决策 `DECISION_RECORD.md`（ADR-C1~C12）。
代码在 `oa-platform/oa-console`（**并进本仓库作为子目录**，与同族 workflow-console /
auth-console 一致；此前那个 `git init` 出来的空 .git 已移除 —— 嵌套仓库会让父仓库
只看见一个不可进入的 gitlink）。

### 已完成：step 0 ~ 12 的主体

| 步 | 产出 |
|---|---|
| 0 | `deploy/scripts/seed-console-fixture.sh` —— 3000 组织 / 1 万员工 + 五个固定账号 + ORG_UNIT 与 TEMPORARY 授权各一条 |
| 1–2 | 脚手架 + `src/shared/`（四上游 proxy、错误码归一、identity adapter、perm 类型） |
| 3a–3b | `PermBridge` / `usePerm` / `<Can>` / `<PermRoute>` / `useElevationFlow`（3002 提权闭环） |
| 4–8 | 后端驱动菜单 · 工作台 · 组织树 · 员工 · 角色授权 |
| 9a–9d | 权限沙盘三栏（`why` 来源链 / `preview` 他人视角 / `explain` 一致性 Alert） |
| 10–11 | 通讯录游标分页 + 虚拟滚动 + IndexedDB 增量同步（`useDirectorySync`） |
| 12 | 驾驶舱 + 审计（走 JIT 提权） |
| 14 部分 | Dockerfile / nginx.conf / playwright.config.ts / e2e fixtures |

**实测**：`tsc --noEmit` 干净 · `vite build` 通过 · vitest **17/17** · 首屏门禁绿。

### ★ 首屏 287.4 KB gzip / 预算 300（硬验收 A4）

原本 **414 KB**。原因不是没做路由级 lazy，而是**应用壳自己没 lazy** ——
`AppLayout` 里的 Layout/Menu/Drawer/Table/Tabs/Modal 把大半个 antd 拖进了首屏块。
壳改成 lazy 后，未登录访客只下载登录页那点东西（Card/Button/Typography），
壳在登录之后才加载，那时用户本来就在等页面切换。

`scripts/check-size.mjs` 量的是 **index.html 实际引用/预加载的字节**，
不是 `dist` 之和 —— 后者会把懒加载路由块也算进去，对"第一次访问的人下载了多少"没有意义。
这个数字**只会往上漂**：加一个 antd 组件、少写一个 lazy 都会把它推过线，
没有断言的话等想起来量时已经不知道是哪次改动引入的。

### 几个刻意的写法

- **守卫必须在 `Suspense` 外层**：写反了会先下载几百 KB chunk 再告诉用户 403 ——
  既白下载，又泄露了"这个页面存在"。
- **chunk 边界 = MENU code 边界**：菜单可见性与代码分割是同一条线，最好解释。
- **vite proxy 顺序敏感**：oa-console 与两个前辈的结构性差异是**四上游**
  （app 8400 / notify 8401 / file 8402 / job 8403）。匹配按声明顺序，
  具体前缀必须排在 `/api/v1` 之前，否则三个独立服务的请求全被打到 8400 返回 **404 而不是报错**。
- **dev server 显式绑 `127.0.0.1`**：vite 默认绑 `localhost`，macOS 上解析成 `::1`，
  而 curl / Playwright 打 127.0.0.1 会 connection refused —— 表现是
  "dev server 明明说 ready 了却连不上"。playwright 的 baseURL 必须与它一致。
- **`defineConfig` 从 `vitest/config` 取、`loadEnv` 从 `vite` 取**：混着拿会报
  "does not provide an export named 'loadEnv'"，且**只在构建时才炸**。
- **vitest 的 `include` 必须限死 `src/**`**：否则会去收集 `e2e/` 下的 Playwright 用例然后炸掉。
- **夹具授权一律走 API**：裸 SQL 改 `grant_record` 不 bump epoch，L1+L2 里的旧快照继续放行
  （静默失败清单第 9 条）。夹具还必须造一条 **ORG_UNIT 授权** ——
  默认 seed 全是 USER 直授，沙盘中栏渲染出来只有"本人被直接授权"，
  恰恰看不出**继承**这条最有说服力的链路，而那正是这个页面存在的意义。

### ⬜ 未完成（下一个 session 从这里接）

| # | 缺口 | 影响 |
|---|---|---|
| ~~1~~ | ~~`scripts/gen-perm.ts` 不存在~~ | ✅ **已补**（`e9d0dcb`）：从迁移 SQL 生成 66 个权限点 + 与四份 golden 交叉校验；A10 元测试与漂移门禁都已实测能红 |
| 2 | `src/shared/types/` 是**空目录** —— openapi-typescript 没跑 | 字段级契约没有机器保证 |
| ~~3~~ | ~~`useAppBreakpoint()` 不存在~~ | ✅ **已补**（`e9d0dcb`）：六档表 + 接进 AppLayout / SandboxPage / AuditPage。**顺带修掉一个真错判** —— 原代码用 antd 的 `xxl`(1600) 当窄屏分界，把整个 B 档（1440–1599）折叠了 |
| 4 | 单测 **2/9**（错误码映射表 + permCode 元测试）+ 断点 25 条 | 还缺：`<Can>`/`<PermRoute>`、IndexedDB、成环判定、中文序列化、401 单飞、queryKey 完整性、缓存失效 |
| 5 | **e2e 零个 spec**（只有 fixtures.json） | **A1/A3/A5/A6/A7 五条硬验收全部无法执行** |
| 6 | 知识库页未做（step 12 只做了驾驶舱 + 审计） | |
| 7 | step 14 未收口：`deploy/docker-compose.yml` 与 `build-images.sh` 里**没有 oa-console**，`phase3-console-smoke.sh` 未写 | 整栈起不全 |
| 8 | step 15 JWT 切换 | 计划里已标阻塞：Casdoor 无 oa 应用；切换后 9 个冒烟的 `X-OA-User` 手法失效（**231 条断言依赖它**），需先定"冒烟怎么拿 token" |

**建议顺序**：~~1+3~~ ✅ → 4+5（把五条硬验收变成可执行）→ 6 → 7 → 8。

### 本轮补齐时发现的两件事

1. **测试桩本身会造假绿**：`src/test/setup.ts` 原来的 matchMedia 桩是 `matches: false` 恒定值。
   它让 antd 不炸，但任何"窄屏应该降级"的断言在它下面都通过 —— 所有断点都不命中，
   组件永远走同一条分支。已改成按 `window.innerWidth` 真求值并派发 change 事件
   （`src/test/viewport.ts` 的 `setViewportWidth()`）。§7 底线②点名过这件事。
2. **B 档一直被当成窄屏**：antd 最近的两个断点是 `xl`(1200) 与 `xxl`(1600)，
   而 §7 的 C 档下界是 1440。用 `!screens.xxl` 判窄屏，1440–1599 的屏幕
   白白损失一个展开的侧栏和一整栏 Inspector —— 用起来别扭，但没人会去报这种 bug。

vitest 现在 **47 条**（原 17）。
