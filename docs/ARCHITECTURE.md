# 架构全景

> 面向要接手这套代码的人。每一节先说**为什么这么设计**，再说怎么实现的。
> 只讲已经落地并被冒烟验证过的部分；没做的在末尾单列。

---

## 1. 组织建模：单表 + 闭包 + 物化路径 + 拉链

### 为什么不为「团队/部门/组」各建一张表

万人企业的组织层级**不固定**（实测生成的树是 6 层，现实中常见 6–9 层），
而且随时出现"事业部下面直接挂组"这类越级挂载。固定层级的三张表撑不过第一次组织调整。

所以只有一张 `org_unit`，层级由 `parent_id` 表达，`type`（GROUP/COMPANY/BU/CENTER/DEPT/TEAM/SQUAD/VIRTUAL）
只是展示与规则标签，**不是结构**。

### 三种表示同时存在，各司其职

| 表示 | 用途 | 代价 |
|---|---|---|
| `parent_id` | 唯一真相，增删改都改它 | 递归查询慢 |
| `org_closure(ancestor, descendant, distance)` | 祖先/后代查询 O(1)，权限继承的核心 | 组织调整时写放大（低频，可接受） |
| `path` 物化路径 `/1/23/456/` | 面包屑、排序，**以及数据权限的前缀匹配** | 移动子树要批量改前缀 |

**路径的尾斜杠不是装饰**：没有它，`LIKE '/1/23/%'` 会把 `/1/234/` 也匹配进来 ——
那是跨部门的数据泄露。`OrgTreeSnapshotTest` 里专门有一条用例守着这件事。

### 移动子树的三步（必须同事务）

```
① 断开子树与所有外部祖先的连边（子树内部连边保留）
② 新父的全部祖先(含自身) × 子树全部节点，两两连边
③ 整棵子树换路径前缀、平移深度
```
不变式：`closure` 里存在 (a, d) ⟺ d 的 path 以 a 的 path 为前缀，且 `distance = d.depth − a.depth`。
`GET /api/v1/org/units/consistency` 就是核对这三条，正常恒为 0。

### 人、岗、任职是三件事

- `employee` —— 人。`user_id` 是 Casdoor `sub`（UUID），全系统唯一主体标识。
- `job_position` —— 岗位（职族/职级/是否管理岗）。
- `employee_org_assignment` —— **任职关系，拉链表**。一人多岗（主岗/兼岗/虚线）都在这里。
  关闭一段任职是写 `valid_to` 而**不是删行** —— 两年后要能回答"这张单子发起时他在哪个部门"。
- `reporting_line` —— **汇报线，独立于组织树**。矩阵式管理下"逐级上报"经常不等于组织树；
  把它硬绑在组织树上，第一个矩阵式组织的请假审批就会走错人。

### OrgTreeCache：最大的性能杠杆

全量组织树（万人级约 3,000 节点）常驻内存，不可变快照 + COW 整体替换，读侧完全无锁。
**后代与祖先在构建期预计算**，所以查询是真正的 O(1)。

三条收敛路径：本节点自己改的走事务提交后事件（立即）；别的节点改的走 Redis 失效总线（毫秒）；
总线不可用时靠轮询 `org_tree_version`（5 秒上界）兜底。

> ⚠️ 重建时**必须先读版本再读数据**。反过来会把旧数据打上新版本号，
> 之后版本比对永远相等，快照永久陈旧 —— 现在这个顺序最差只是多重建一次。

---

## 2. 判权引擎：热路径零 DB 零远程

### 快照结构

```
PermissionSnapshot {
  permBits      : RoaringBitmap   // 800 个权限点压缩后 < 1 KB
  elevatedBits  : RoaringBitmap   // 当前活跃的 JIT 提权覆盖的权限点
  permissionScope: Map<权限点, 范围> // 数据范围的强制读取入口
  mergedScope   : DataScopeRule   // 合并后最宽的数据范围
  moduleScope   : Map<模块, 范围>  // 迁移期诊断字段，不能用于严格判权
  delegators    : Set<String>     // 我正在代理谁
  epoch / userVersion / expireAt
}
```
单份快照 2–4 KB，一万用户全驻内存约 30 MB。判定是 O(1) 位图查。

### 三级缓存

| 层 | 介质 | 命中成本 |
|---|---|---|
| L1 | Caffeine 进程内 | 纳秒级（实测判权 P50 = 0.25 μs） |
| L2 | Redis（JSON + base64 位图） | 约 1 ms |
| L3 | 数据库重算 | 10–30 ms |

L2 用 JSON 而不是 Kryo/Protobuf：它不在热路径上（命中 L1 就走不到），
为几毫秒引入一个二进制框架及其版本兼容问题不划算；位图本身仍是 RoaringBitmap 的紧凑格式。

### ★ 失效策略刻意不对称

| 变更 | 处理 | 理由 |
|---|---|---|
| **收权**：撤销、部门级授权、角色权限、组织调整、离职 | bump **全局纪元**，各节点 1 秒内全量作废 | 撤权延迟生效是安全事故，宁可全量重算 |
| **给个人加权** | 只 bump 用户版本 + 本节点剔除 + 通知其它节点 | 通知丢了最坏是新权限晚几分钟生效，**不构成安全问题** |

放弃对称换来的是：**热路径只需比对一个每秒刷新的全局纪元**，
不必为每个请求查一次用户版本 —— 这是 P99 能压到 1 μs 的关键。

### 快照 TTL 必须被临时授权的到期时刻压低

少了这一步，一条 30 秒后过期的 JIT 提权会被 5 分钟 TTL 的快照继续放行 —— 一个安静的提权漏洞。

### 影子校验

按采样率（默认 1%）把缓存结果与数据库重算结果比对，不一致打 ERROR 并计入
`oa_perm_mismatch_total`。"改权不生效"必须有能报警的兜底，而不是靠人肉发现。

> ⚠️ 校验**必须异步**。同步执行等于给 1% 的**全部**请求加一次完整 DB 重算（约 5 ms），
> 实测把 P99 从 0.9 μs 顶到 5034 μs。诊断手段不能拖慢它要诊断的东西。

---

## 3. 三类权限的边界

| 类型 | 落点 | 是否安全边界 |
|---|---|---|
| **接口权限** | `@RequiresPerm` 切面 + fail-closed 拦截器兜底 | ✅ **唯一安全边界** |
| **数据权限** | 权限点级 `@DataScope` + 严格 SQL 消费协议 + `@Sensitive`（列级） | ⚠️ 平台能力已强制，业务覆盖迁移中 |
| **页面权限** | 后端下发 permCodes/菜单，前端裁剪展示 | ❌ 仅体验 |

接口权限已有构建期覆盖测试、API Golden 和运行期未声明拒绝三道门禁。数据权限平台层现在按
`permission → DataScopeRule` 解析；`@DataScope` 必须声明入口权限点和真实目标表，而且该权限点必须是
本次 RBAC/ABAC 实际通过的权限。严格模式下，目标 SQL 未执行、目标表/alias 错配、条件构造或解析失败
都会拒绝请求；`ALL` 也会留下“已明确求值”记录。首批受管表注册表还会拒绝没有任何 DataScope
上下文的 MyBatis 查询，让已迁移表在遗漏注解时同样 fail-closed。JWT 模式启动时禁止关闭接口判权或严格模式。

当前已迁移并冻结 5 个 `DirectoryService` / `AssetService` 数据方法。全仓仍有 21 个 application/web
类直接使用 JDBC；`JdbcBypassArchitectureTest` 已冻结这份债务，任何新增都会使 CI 失败，但这些既有路径
仍要按模块迁移后，才能宣称所有业务数据已全局覆盖。完整清单、阶段和验收标准见
[接口权限与数据权限全局保障方案](plans/data-permission-global-guard/FINAL_PLAN.md)。

### 数据权限怎么翻译成 SQL

| 范围 | 生成的条件 |
|---|---|
| `ALL` | （不加条件） |
| `ORG_AND_SUB` | `t.org_path LIKE '/1/23/%'`（多范围 OR 拼；超过 8 个前缀降级为 `IN`） |
| `ORG` | `t.org_id IN (…)` |
| `SELF` | `t.user_id = '…'` |
| `NONE` | `1 = 0` —— **算不出范围时一行都不给**，绝不能返回"不加条件" |

硬要求：业务表冗余 `org_id` + `org_path`，`org_path` 索引必须是 `text_pattern_ops`。
组织路径在拼进 SQL 前还要过一遍格式白名单（只允许数字与斜杠），杜绝任何注入可能。

> ⚠️ MyBatis-Plus 拦截器顺序：**数据权限必须排在分页之前**。
> 排在后面的话，分页改写出的 count 语句会绕过数据权限，
> 出现"列表被过滤了、总数没被过滤"的经典越权。

运行期指标为 `oa_data_scope_evaluations_total`、`oa_data_scope_predicates_total`、
`oa_data_scope_denied_total`、`oa_data_scope_missing_total`、`oa_data_scope_alias_mismatch_total` 和
`oa_data_scope_bypass_total`。`missing`/`alias_mismatch` 在严格模式下既计数也失败；旁路记录主体、方法、
reason、表和 traceId。

### 三类临时授权，不能混为一谈

| 形态 | 用途 | 关键约束 |
|---|---|---|
| 定时角色 | 授权带生效区间 | 判定时按时间窗过滤，**不依赖定时任务删除** |
| JIT 提权 | 高危操作临时抬权 | **只能激活自己已持有的角色**（如 sudo），否则自助提权=自助越权 |
| 委托代理 | 出差时把待办交给他人 | **不叠加权限**，只让代理人办委托人本来就该办的待办，动作留 `on_behalf_of` |

---

## 4. 审批链路：发起走发件箱，办理走网关

```
提单（一个事务）
  ├─ INSERT 业务单据（请假单）
  ├─ INSERT approval_instance（含发生时的 org_id/org_path 快照）
  ├─ 冻结假期额度 + 写额度流水
  └─ INSERT oa_outbox（StartProcessCommandV1）
            ↓ OutboxPublisher 轮询（FOR UPDATE SKIP LOCKED，多实例并行）
        Kafka workflow.command.start.v1 → 流程中台
            ↓ 生命周期回调
        todo_item 读模型（工作台首屏一条 SQL 出结果）
```

**为什么发起必须走发件箱**：发消息和写单据若不在同一个事务里，
就必然出现"单据落了消息没发"或"消息发了单据回滚"。发件箱把消息也变成一次数据库写入，
两者天然原子；投递异步完成、失败指数退避。这也是 workflow-platform 已锁定的接入方式
（同步 HTTP 发起被明确否决过：它破坏事务边界）。

**审批人在 OA 侧算好**（`ApproverResolver`）：沿实线汇报线逐级取 N 级，
取不满回落到部门负责人（允许向上冒泡，否则新建部门还没配负责人单据就卡死）。
级数由业务量级决定 —— 2 天 1 级、5 天 2 级。
中台不需要、也不应该知道 OA 的组织结构。

**额度分冻结与消耗两步**：提单冻结，防同一个人连提两单把额度用超；
审批通过才真正消耗，驳回则释放。`(request_id, action)` 唯一约束让**幂等由数据库保证**，
重放同一条消息不会扣两次。

**中台集成的现状**：`RemoteWorkflowGateway` 已使用通用 `completeTask` SDK，
`phase4b-remote-smoke.sh` 覆盖真中台发起与办理；生产默认 `REMOTE`。
`oa.flow.workflow.mode=LOCAL` 只用于不启动外部中台的本地测试，它按 `approverChain`
顺序流转，进程/任务 ID 带运行实例前缀，且在审批实例绑定后才投影首个待办，避免重启碰撞和空业务字段。

---

## 5. 早高峰打卡削峰

10,000 人 × 1.5 次集中在 9:00 前后，六成落在五分钟内就是瞬时 300–500 QPS。
而打卡对用户的要求是"按下去立刻有反馈"，对系统的要求是"一条不丢、一条不重"。

```
POST /attendance/punch
 ① Redis SETNX 幂等键（重复打卡直接返回成功，不进后续链路）
 ② 入有界队列后【立即返回】——实测 P50 = 3 ms
 ③ 后台线程"攒够一批或到时间"批量 INSERT，把 500 次单行写压成 1 次批量写
 ④ 队列满 → 降级同步直写（宁可慢，不可丢）
 ⑤ 落库失败 → 【撤销 Redis 幂等键】+ 进死信表
```

第 ⑤ 步最容易被忘：幂等键先于落库写入，落库失败却不撤销的话，
用户会永远卡在"显示打过了、库里没有"的状态。

`punch_record` 按月声明式分区 + 兜底分区：删三个月前的数据变成 DETACH 一个分区（秒级、不锁表），
而不是一条会拖垮线上的 DELETE；兜底分区保证漏建分区时不丢卡。

日结聚合**完全在 SQL 里完成**（万人 165 ms）—— 把 500 万行捞到应用里算既慢又吃内存。

---

## 6. 模块纪律

跨模块只允许依赖对方的 `..api..` 包（接口 + DTO）与领域事件。
写在文档里的纪律三个月后会被一句 `@Autowired OrgUnitMapper` 悄悄焊死，
所以由 `ArchitectureRulesTest` 来守 —— 违反即构建失败。

`oa-security` **只定义判权端口不含实现**，实现在 `oa-iam`。
这样任何模块依赖注解都不会把权限域的实现细节拖进来。

> ⚠️ ArchUnit 测试**不能加 `DO_NOT_INCLUDE_JARS`**：其它业务模块正是以 jar 形式挂在
> oa-app 的 classpath 上，加了等于所有跨模块规则空转。测试里有一条前置断言专门防这个。

---

## 7. 前端、认证与实时通知

PC `oa-console` 与移动端 `oa-mobile` 是两个独立 Vite 应用，共用 REST 语义但不复制后端判权。
PC 覆盖组织、IAM、权限沙盘、知识库和报表；移动端覆盖待办办理、打卡、通讯录与公告已读。
两者均消费 `/api/v1/me/permissions`，菜单和按钮裁剪只改善体验，后端注解仍是安全边界。

生产安全模式默认 `JWT`：四个服务统一校验 Casdoor 的签名、issuer 与 audience，JWT 模式硬拒绝
`X-OA-User`。浏览器 WebSocket 无法设置 Authorization header，因此先通过 Bearer REST
申请 256-bit、30 秒有效的一次性 ticket；握手时以 query 传递，Redis 原子取用即删，绑定用户/租户，
并校验 Origin。Nginx 对 `/ws` 关闭 access log，长期 token 从不进入 URL。

公告列表先完整释放主查询连接，再一次批量读取本页已读状态；禁止在 JDBC 行回调中发起嵌套查询，
否则连接池满载时每个请求都会“占着一个连接再等一个连接”，形成自锁。

OpenAPI 快照生成 `oa-console/src/shared/types/openapi.d.ts` 并参与类型检查；PC 和移动端分别受
300KB / 220KB 首屏 gzip 门禁约束。容器 Nginx 使用 Docker DNS 动态解析四个后端，避免滚动重建后
继续缓存旧容器 IP。

## 8. USER_GROUP 与 ABAC

- `USER_GROUP` 是 OA 内部显式成员组，不复用 Casdoor group。`user_group_member` 支持
  `valid_from/valid_to` 与软撤销；快照 TTL 会被最近成员边界压低，组禁用或移出成员推进全局 epoch。
- 组授权仍使用 `grant_record`，继承角色、临时授权和数据范围语义；判权热路径只读权限快照，
  来源链会明确显示用户组。
- ABAC 条件按 `(tenant, role, permission)` 管理并进入 L1/L2 快照。同一角色权限下的条件 AND、
  不同角色分支 OR，任一适用角色没有条件时旁路。受限 SpEL 只暴露方法入参和只读 `#user`，
  禁止类型、Bean、构造器、方法调用与赋值；缺变量或求值错误 fail-closed。
- `OA_IAM_ABAC_ENABLED=false` 为默认兼容模式；先配置/审查策略，再按环境开启并监控
  `oa_iam_abac_{evaluations,denied,errors}_total`。

ABAC 的完整属性清单、组合规则、表达式限制和编辑流程见 [ABAC 授权与管理指南](ABAC.md)。

## 9. 明确没做的

- 动态规则组、嵌套用户组与 Casdoor group 同步不在首版 USER_GROUP 范围。
- ABAC 不会在请求时查业务数据库；仅能使用方法参数和用户上下文。
- JIT 提权接审批流：`approval_instance_id` 字段已留，尚未接 BPMN。
- 排班、加班调休仍未实现；额度年初批量发放、考勤日结与分区滚动已有跑批端点，但生产调度策略需部署方配置。
- 知识库当前使用本地对象授权实现；`oa.authz.spicedb.enabled=true` 的 SpiceDB 适配器仍是显式 fail-fast 骨架。
- 真实邮件、短信、企微、飞书供应商通道未接入；当前通知域完成站内信、公告、WebSocket 与回执。
