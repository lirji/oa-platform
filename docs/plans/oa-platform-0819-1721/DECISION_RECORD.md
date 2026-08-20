# OA Platform 决策记录（DECISION_RECORD）

> 立项日期：2026-08-19 · 项目：**本仓库** · groupId `com.lrj.oa`
> 定位：**企业级 OA 协同办公平台**，目标规模 **10,000 员工**（万人级），生产级工程质量。
> 关联资产：[[auth-platform]]（Casdoor SSO + SpiceDB）、[[workflow-platform]]（Flowable BPMN）、[[his-platform]]（微服务惯例参照）。

---

## ADR-0001 底座复用度 = 混合（用户 2026-08-19 拍板）

**决策**：Casdoor 只做**认证/SSO**；审批编排复用 **workflow-platform**（Flowable :8300）；
**组织树 + RBAC + 部门继承 + 数据权限由 OA 自建本地引擎**，判权全程内存化、不走网络。

**备选与否决理由**：

| 方案 | 内容 | 否决理由 |
|---|---|---|
| B 最大化复用 | 组织=Casdoor group，判权全走 SpiceDB | ① 页面权限一次登录要判几百个权限点，万人级下远程 check 是灾难（即便 checkBulk 也是网络往返 + SpiceDB 快照一致性开销）；② SpiceDB 强在**任意对象 ACL 图**，弱在**结构化组织 RBAC**——"部门及子部门 + 岗位 + 时效"塞进 zed 会变成难维护的递归 permission；③ `8543` 写 schema 是**全量替换语义**，OA 频繁演进 schema 会威胁 knowledge/his/recsys/risk 四份现存模型；④ Casdoor group 无法表达"一人多岗 / 生效日期 / 汇报线"。 |
| C 全自建 | 自己做登录 + 自己做审批引擎 | 重复造轮子，且与你已投入的两个中台故事线断开；Flowable 的会签/加签/转办/时限在 workflow-platform 已跑通。 |

**保留的复用口子**：知识库/网盘这类**任意对象共享**子域，正是 `knowledge.zed` 已建模的场景 →
挂 auth-platform SDK，开关 `oa.authz.spicedb.enabled` 默认 `false`（Noop），可回退。

---

## ADR-0002 服务端形态 = 模块化单体 + 3 个独立服务（用户拍板）

**决策**：按限界上下文分 Maven 模块，单一 `oa-app` 启动；只把 **通知推送 / 文件 / 定时任务** 独立部署。

**理由（含数字）**：万人级 OA 的 DAU ≈ 7,000，除打卡早高峰外常态 QPS 在几十到几百量级，
**峰值也就 500 QPS 级**。而组织+权限是**强一致、强耦合**域——拆成微服务后，
"跨服务判权 + 跨服务组织快照 + 分布式事务" 的成本远大于收益。

**独立出去的三个，各有非功能理由**（不是拍脑袋）：
- `oa-notify-service` — WebSocket 长连接，**连接数**而非 QPS 驱动扩容，与主应用的伸缩曲线完全不同；
- `oa-file-service` — 大流量 IO，会挤占主应用线程/带宽；
- `oa-job-service` — 跑批（考勤月结、报表预计算、授权到期回收）与在线请求争 CPU，必须隔离。

**纪律（把"留缝"变成可执行约束）**：跨模块**禁止**直接注入对方 Repository/Entity，只能经
`XxxQueryApi` 接口 + 领域事件。用 **ArchUnit 测试强制**，违反则构建失败。

**被否决**：完整微服务（Nacos+Gateway+DB per service）——展示力强但组织/权限域会被撕裂；
纯单体——无拆分缝，后续通知服务扩容时无法独立部署。

---

## ADR-0003 前端 = React18+antd5（PC）+ 独立移动端 H5（用户拍板）

**决策**：PC `oa-console`（React18 + Vite5 + TS + antd5 + ProComponents），
移动端 `oa-mobile` 独立工程；两者经 pnpm workspace 共享 `@oa/shared`（API client / 类型 / 权限 hook / 表单渲染器）。

**移动端框架选型（本决策记录追加建议，需你确认）**：用户选项写的是 "Vant/Taro"，但 Vant 是 **Vue3** 生态。
- **推荐 `antd-mobile 5`（React）**：与 PC 同框架，`@oa/shared` 可真正复用（API 层、类型、表单渲染器、权限 hook）；
- 若坚持 **Vant（Vue3）**：`@oa/shared` 只能降级为"纯 TS 无框架部分"（API client + 类型 + 校验），
  表单渲染器要写两遍（这是 OA 最重的前端资产）→ 成本翻倍。
- 若未来要**微信小程序/多端**：再上 **Taro**（Taro 支持 React 语法，可延续 antd-mobile 心智）。

→ **计划按 antd-mobile(React) 编写**；若你要 Vant，Phase 6 前告诉我，改动集中在 `oa-mobile` 工程与 shared 边界。

---

## ADR-0004 交付节奏 = 全模块一次规划、实施分期（用户拍板）

FINAL_PLAN 覆盖全部 18 个模块的领域模型与接口契约；实施按 **Phase 0–8** 排期，
每个 Phase 有独立验收标准 + `deploy/scripts/*-smoke.sh` 冒烟脚本（因 [[local-dev-env]] 记录 **本机 Testcontainers 跑不起来**）。

---

## ADR-0005 判权 = 三级缓存 + 组织树常驻内存，热路径零 DB 零远程

**决策**：`PermissionSnapshot`（权限位图 + 数据范围规则）三级缓存 L1 Caffeine → L2 Redis → L3 DB 重算；
`OrgTreeCache` 把全量组织树（万人级约 3,000 节点 / 几 MB）常驻内存，COW 整体替换、无锁读。

**理由**：把"求某人可见的全部子部门"从递归 SQL 变成内存 O(1)，直接消灭 ~90% 的权限相关 DB 查询。
判权热路径目标 **P99 < 1ms**。

**代价与对策**：缓存与真值可能漂移 → Phase 2 内置**影子校验模式**（一段时间内同时走缓存与 DB 重算并比对，
不一致即告警），这是"改权不生效 = 安全事故"的兜底。

---

## ADR-0006 组织建模 = 单表 `org_unit` + `type` + 闭包表 + 物化路径 + 生效日期

**决策**：**不**为"团队/部门/组"各建一张表。

**理由**：万人级企业组织**层级不固定**（实际常见 6–9 层），且随时出现"事业部下直接挂组"这类越级挂载。
三张固定层级表在第一次组织调整时就会崩。`type` 只是展示与规则标签，不是结构。

**结构选型**：闭包表 `org_closure(ancestor, descendant, distance)` **并用** 物化路径 `path='/1/23/456/'`。
- 闭包表：祖先/后代查询 O(1)，是权限继承的核心；写放大在组织调整时（低频，一天几十次）完全可接受。
- 物化路径：给前端做面包屑/排序，更重要的是**数据权限直接用 `LIKE '/1/23/%'` 前缀匹配**（见 ADR-0007）。

**时间维度**：`org_unit` 与 `employee_org_assignment` 带 `valid_from/valid_to` 拉链，
支持"按 as_of 时间点还原当时组织"——历史审批的合规要求。

---

## ADR-0007 数据权限 SQL = `org_path` 前缀匹配，禁止 `IN (大列表)`

**决策**：`@DataScope` 拦截器改写 SQL 时，`ORG_AND_SUB` 范围翻译成 `t.org_path LIKE '/1/23/%'`，
而非把子部门展开成 `org_id IN (5000 个 id)`。多个不连续范围用 `OR` 拼前缀，超过 8 个前缀降级为 `= ANY(array)`。

**配套硬要求**：**所有业务单据表冗余 `org_id` + `org_path` 两列**，写入时从当前用户快照取值。
否则数据权限每次都要 join 组织表，万人级下必炸。

**语义决策**：业务单据的 `org_path` 是**发生时快照**，组织调整**不回刷历史单据**。
这既是性能优化，也是正确的业务语义（"这张请假单属于当时的那个部门"）。

**PG 真坑**：非 C locale 下 `LIKE 'prefix%'` **不走**普通 btree 索引，
必须建 `CREATE INDEX ... ON org_unit (path text_pattern_ops)`。写进 Flyway，不许漏。

---

## ADR-0008 三类权限的边界：接口权限是唯一安全边界

| 类型 | 落点 | 性质 |
|---|---|---|
| **接口权限** | `@RequiresPerm` 切面 + URL 映射表兜底过滤器 | ✅ **唯一安全边界** |
| **数据权限** | `@DataScope` + SQL 改写 + `@Sensitive` 字段脱敏 | ✅ 安全边界（行级 + 列级） |
| **页面权限** | 后端下发 permCodes/菜单，前端裁剪展示 | ❌ **仅体验，不是安全边界** |

**工程强制**：CI 检查——所有 `@RestController` 的公开方法必须标注 `@RequiresPerm` 或显式 `@PublicApi`，
否则构建失败。防"新加接口忘了加注解"这类最常见的越权漏洞。

**验收强制**：Phase 8 必须有渗透用例证明"前端藏了但后端拦得住"。

---

## ADR-0009 菜单路由 = 后端下发可见集合，前端持有静态路由表

**决策**：**不**用"后端下发路由再 `addRoute` 动态注册"。
前端维护完整静态路由表（每条打 `meta.perm`），后端只下发**可见的菜单 code 集合 + 菜单元数据**（名称/图标/排序，供运营调整）。

**理由**：静态路由可被 TS 静态分析、可被 Vite 正确 code-split、不会因后端数据脏了整站白屏；
动态路由的唯一优势（后台随意加页面）在 OA 里是伪需求——页面组件本来就要前端发版。

---

## ADR-0010 审批人计算在 OA 侧，不在流程中台

**决策**：BPMN 的 `UserTask` 用**表达式变量**（`${directManager}` / `${deptLeader}` / `${hrbp}`），
值由 **OA 在发起流程时算好**塞进流程变量。

**理由**：审批人计算依赖组织树、汇报线、岗位、委托代理——这些数据全在 OA。
若让流程中台反查 OA，就把中台变成 OA 的下游耦合方，违背 workflow-platform "消费方极小 SDK 接入" 的定位。

**发起方式**：沿用 workflow-platform 已锁定的 ADR——OA 侧 **outbox（同事务写）→ Kafka `StartProcessCommandV1`**，
**不用同步 HTTP**（该项目已明确否决 C 方案：破坏事务边界）。

**工作台待办数据源**：消费 workflow 生命周期事件写 OA 本地 `todo_item` **CQRS 读模型**，
首屏一条 SQL 出结果；SDK `WorkflowClient` 只用于"办理/认领/转办"这类需即时反馈的动作。

---

## ADR-0011 万人级不做分库分表

**决策**：单 PostgreSQL 实例 + **声明式分区**（`punch_record` / `notification` / `audit_log` 按月），不做分库分表。

**理由**：最大表 `notification` 约 5,000 万行/年，PG 分区表 + 冷热归档完全够用。
分库分表会引入跨库事务与聚合查询难题，是**过度设计**。明确写"不做"，防止后续摇摆。

---

## 待你确认的开放问题（不阻塞实施）

1. **移动端框架**：antd-mobile(React，推荐) vs Vant(Vue3)——见 ADR-0003，Phase 6 前需定。
2. **多租户**：本轮按**单租户**实施，但所有表预留 `tenant_id` 列。若要真多租户（多法人公司隔离），Phase 1 前告知。
3. **企微/钉钉/飞书组织同步方向**：OA 作为组织**权威源**向外推，还是从飞书**拉取**？本计划按"OA 权威 + 可选向外同步"编写。
4. **Elasticsearch**：公文/知识库全文检索是否本轮做？计划中列为**可选**（Phase 7），不做则降级为 PG 全文索引。
