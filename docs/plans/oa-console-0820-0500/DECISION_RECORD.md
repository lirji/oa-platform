# oa-console 决策记录（Phase 3 · PC 管理控制台）

> 依据：`/frontend-plan` 六路只读调研（需求与用户流 / UIUX / 前端架构 / 仓库约束 / 移动端适配 / 测试与风险）
> 后端现状：Phase 0–8 全部完成，231 条冒烟断言全绿；本轮开工前已补齐 10 个后端缺口（见 ADR-C1）
> 参照脚手架：`auth-platform/auth-console`、`workflow-platform/workflow-console`、`recon-platform/recon-console`（同一家族三代）

---

## ADR-C1 —— 先补后端缺口，再动前端（已执行）

**背景**：调研核实出 10 处"前端做不下去"的地方，其中 3 处是硬阻塞。

| # | 缺口 | 后果 |
|---|---|---|
| 1 | 无角色列表端点 | 授权页与 JIT 提权拿不到 `roleId`，只能硬编码 bigserial 生成的 id |
| 2 | 后端 CORS 只写了 `.cors(withDefaults())` 没有 `CorsConfigurationSource` | 跨源直连 preflight 403，且失败在浏览器侧、后端日志干净 |
| 3 | 9 个 MENU 权限点 route/icon/parent/sort 全 NULL | 前端必须维护第二份菜单定义，两份迟早漂移 |
| 4 | 无"以他人视角预览" | 沙盘右栏只能 DEV 冒充，JWT 模式下上线即失效 |
| 5 | `/explain` 不返回来源链 | 沙盘中栏（§16 的核心诉求）无米下锅 |
| 6 | `/directory/delta` 零实现 | 万人通讯录增量同步依赖一个不存在的接口 |
| 7 | 通讯录只有 `limit≤500`、无分页 | 一万人拿不全 |
| 8 | 无 `perm.changed` 事件 | "改角色后不刷新即生效"链路是断的 |
| 9 | `/me` 未下发 `moduleScope` | 无法按模块提示数据范围 |
| 10 | 无"我发起的单据"列表 | 提单后不记住单号就再也找不回来 |

**决策**：**全部在前端动手之前补掉**，而不是让前端绕。

**为什么不绕**：前端绕这些缺口的方式分别是「硬编码 roleId」「前端自己拼来源链」「DEV 冒充身份」——
每一个都是把后端语义在前端抄一遍。两份实现分叉的那天，沙盘会**理直气壮地解释错**，
而那正是这个页面存在的全部意义。

**已完成**（commit `dcadfc2`，实测通过）：
`GET /iam/roles` · `/iam/roles/mine` · `/iam/permissions/catalog` ·
`GET /iam/admin/preview?userId=` · `GET /iam/admin/why?userId=&permCode=` ·
`GET /org/directory/page?cursor=&size=` · `GET /org/directory/delta?since=`（含墓碑与 fullResync）·
`GET /flow/todos/mine` · `X-OA-Perm-Version` 响应头 · `moduleScope` 下发 · CORS 显式来源 ·
V13 菜单元数据 + 孤儿 code 标 DISABLED

---

## ADR-C2 —— 单包 `oa-console`，但写成 monorepo-ready（不 Day-1 上 pnpm workspace）

| 方案 | 内容 | 判定 |
|---|---|---|
| A | Day-1 pnpm workspace，`@oa/shared` 预构建产 dist + dts | ❌ HMR 要常驻 `--watch`，跨包 sourcemap 差 |
| A′ | Day-1 workspace，`@oa/shared` source-only（`exports: "./src/index.ts"`） | ⚠️ 可行，但仍要付全部 monorepo 成本 |
| **B′** | **先单包，预付极小"可迁移税"，Phase 6 一次 `git mv` 抽 workspace** | ✅ **采纳** |

**采纳 B′ 的三条理由**：

1. **共享包唯一的强需求方（oa-mobile）用什么框架至今没定。** ADR-0003 与风险 R13 都写着
   "Phase 6 前必须拍板"。在不知道消费者是 React 还是 Vue 之前设计 `@oa/shared` 的 API，
   是在赌一个自己写下来"待定"的赌注。若最终选 Vant(Vue3)，`packages/` 里除纯 TS 外
   所有 React hook 全部报废，而 monorepo 的复杂度已经付了三个 Phase。

2. **Phase 3 的两条验收标准（改角色不刷新即生效、调试器能解释完整来源链）一个字都不涉及代码共享。**
   monorepo 在 Phase 3 提供零业务价值，只提供成本。

3. **单包沿用两个既有 console 只需约 8 处值替换**（端口 5373→5473、proxy target、
   `EXPOSE 8302→8404`、nginx `listen`/`proxy_pass`）；而拆 monorepo 要重写 Dockerfile
   （构建上下文上移、`--filter oa-console...` 分层安装、`workspace:*` 在容器里解析）、
   分层 tsconfig（且不能用 project references —— `composite + noEmit` 会撞 TS6310，
   这条警告本来就是从这两个项目实战里来的）、`resolve.dedupe` 去重 React、
   `server.fs.allow`。**全 workspace 零先例。**

**"可迁移税"（Phase 3 就付，成本近乎为零）**：
- `src/shared/` 子目录装将来会进 `@oa/shared` 的东西：`api/`（http + Result 解包 + 错误映射）、
  `types/`（生成的 DTO）、`perm/`（permCode 常量 + **不含 React** 的纯判权函数）、`config/`
- **纪律：`src/shared/**` 不许 import antd / react-router**，用 ESLint `no-restricted-imports` 强制。
  这条规则本身就是"将来能不能抽出去"的持续验收
- `tsconfig.paths` + `vite.resolve.alias` 都配 `@oa/shared/* → src/shared/*`，
  Phase 6 抽包时业务代码 import **一行不改**
- 依赖版本严格对齐两个既有 console，Phase 6 合并 workspace 时不会版本分裂

---

## ADR-C3 —— 权限数据：react-query 拉取 + zustand 镜像

| | 纯 react-query | 纯 zustand | **query + zustand 镜像** |
|---|---|---|---|
| 请求编排（重试/去重/失效） | 现成 | 全手写，家族零先例 | 现成 |
| 命令式读（axios 拦截器等非 React 处） | 需持 queryClient 单例 | `getState()` | `getState()` |
| `has()` 性能 | permCodes 是数组，`includes()` O(n)，一页判几十次 | Set 常驻 | Set 常驻 |
| version 去重逻辑有地方放 | 要在 select 里绕 | 有 | **Bridge 里天然有位置** |
| 与仓库既有模式一致 | — | — | **完全同构** |

**采纳第三种**，因为仓库里已经有这个模式：`workflow-console/src/auth/AuthBridge.tsx` 的注释原文是
"把 react-oidc-context 的会话态同步进 authStore 镜像（供 UI/守卫同步读取）"——权威源是 query，
zustand 是镜像。落法：`usePermissionsQuery()`（唯一 fetch 点）→ `<PermBridge/>`（写 store + Set 化 + version 去重）→ `usePerm()` 只读 store。

**必须处理的坑**：`/me/permissions` 是 `@PublicApi` 且**未认证返回 200 + 空清单而非 401**。
token 过期瞬间拉到空清单会把菜单全部清空，用户看到的是"我被撤销了所有权限"。
Bridge 里必须 `if (data.userId == null) return`（不写 store，交给 auth 层）。

---

## ADR-C4 —— "改角色后不刷新即生效"用三条腿，而不是押注长连

| 做法 | 判定 |
|---|---|
| A 纯 WebSocket 推 `perm.changed` | ⚠️ **JWT 模式下浏览器连不上**（见 ADR-C5）；且收权推全局 epoch → 一次撤权让万人同时重拉，而那一刻恰是三级缓存刚全量作废、快照最慢的时刻 |
| B 轮询 `/me/permissions` | ❌ 万人 / 15s = **667 QPS 常态基线**，与打卡峰值同量级，只为一个几乎从不变的东西 |
| **C = 响应头 + 焦点/重连 + （条件具备时）WS** | ✅ **采纳** |
| D SSE | ❌ `EventSource` 同样不能带 header，没解决根问题；且 notify 已建好 WS 且 H5 也要用，再建一套是重复投资 |

**三条腿的分工**：

| 通道 | 覆盖 | 额外请求 |
|---|---|---|
| **`X-OA-Perm-Version` 响应头**（已实现） | **用户在操作时的正确性** —— 只要在用系统就一定发现权限变了；403 上也带，能区分"权限刚被改"与"本来就没有" | **0** |
| `refetchOnWindowFocus` + `refetchOnReconnect` | 从后台标签页回来 / 断网恢复 | 0（事件驱动） |
| WS `perm.changed`（**Phase 3 不做**，见下） | 用户什么都没干时的即时性 | 0 |

**为什么 WS 那条腿推迟**：它依赖 ADR-C5 未解的 JWT 握手问题。而**去掉它，"不刷新即生效"仍然成立** ——
只是从"亚秒"变成"下一次交互时"。演示场景里操作者本来就在点，观感无差别。
这是一个明确的、有降级路径的取舍，不是遗漏。

---

## ADR-C5 —— JWT 模式下的 WebSocket 握手：**列为已知缺口，Phase 3 不解**

**事实**：系统是 `SessionCreationPolicy.STATELESS` + 无 cookie；`WebSocketConfig` 在 JWT 模式下
只认 `getUserPrincipal()`，注释明写"绝不回退 query"；而浏览器原生 `WebSocket` **不能设自定义 header**。
→ 现有握手逻辑在生产模式下浏览器根本认证不了。

三条出路各有代价：`?access_token=`（会进 nginx access log）、`Sec-WebSocket-Protocol` 夹带（hack）、
连后首帧鉴权（要改 handler，且握手失败即关的现有语义要动）。

**决策：Phase 3 不选**。理由：① 它是后端接入方案问题，不该在前端计划里顺手定；
② ADR-C4 已经让"不刷新即生效"不依赖 WS；③ 通知/公告在 DEV 模式下可用，生产上线前再定不迟。
**写进风险清单，Phase 6 移动端前必须解决**（H5 靠长连收公告，绕不开）。

---

## ADR-C6 —— 不引入 `@ant-design/pro-components`

FINAL_PLAN §8.2 列了 ProTable/ProForm，但：

1. **三个同族 console 一个都没用**，全是裸 antd + 手写 `columns` + 手写四态分支；
2. **首屏预算 300KB gzip 是硬验收**，而实测 antd 单 chunk 原始体积就 1.08MB（gzip 约 300–340KB）
   —— **仅 antd 就基本吃满预算**，再加 ProComponents 必红；
3. 引入意味着 `AsyncState` 三原语、四态分支、筛选卡范式全部作废（ProTable 自带 `request`/`search`/`emptyText`），
   视觉语言会明显偏离家族。

**决策：不引。** 用裸 antd Table + 自封一层 `usePagedQuery` + `<DataCard>`。
这是对 FINAL_PLAN 的一处**有意偏离**，理由记在此处。

---

## ADR-C7 —— 虚拟滚动分场景选型

| 场景 | 选型 | 理由 |
|---|---|---|
| 员工管理页（表格） | **antd Table `virtual`** | 零新依赖（antd ≥5.9 内置）；与全站 Table 的排序/筛选/主题一致；固定行高足够 |
| 通讯录（头像卡片 + 分组吸顶 + 首字母索引） | **`@tanstack/react-virtual`** | 需要可变行高与 sticky group header；与已用的 react-query 同家族；**有 Vue 版本**，H5 若选 Vant 逻辑不作废 |
| react-window | 否决 | 既不如 antd 一致，又不如 tanstack 与现有栈亲 |

**比选库更重要**：瓶颈不在虚拟滚动，在**万行每次 keystroke 的 filter 重算**。
→ 搜索 debounce 200ms + 预建拼音/首字母倒排索引 + 走 IndexedDB 索引而非主线程 `Array.filter`。

---

## ADR-C8 —— IndexedDB 缓存的三条硬约束

通讯录是 **per-viewer** 的：`DirectoryService` 带 `@DataScope`（同一 URL 对不同人返回不同行），
`DirectoryEntryView.mobile` 带 `@Sensitive`（同一行对不同人字段值也不同）。因此：

1. **存的不是"通讯录"，是"我在这个权限版本下能看到的通讯录"。**
   store key 必须带 `userId`，记录里必须存 `permVersion`；权限版本变了→缓存整体作废。
   **否则被降权的人还能从本地缓存看到全公司名单——一个完全发生在客户端、后端日志毫无痕迹的越权。**
2. **手机号/邮箱不落盘。** 与 `oidcConfig` 里"token 存 sessionStorage、关标签页即清"的纪律直接矛盾。
   IndexedDB 只存脱敏字段与索引（姓名/工号/部门/职位/首字母/头像 URL），详情实时拉。
   缓存的价值（秒开 + 搜索）100% 保留，风险降到接近零。
3. **必须显示"数据更新于 X 分钟前"**，delta 失败时变黄。**不显示陈旧时间的离线缓存 = 骗人。**

用 `idb`（~1KB）而非 Dexie（~25KB）：只需要 `get/put/getAll/delete/index`，Dexie 的查询 DSL 用不上，而首屏预算紧。

---

## ADR-C9 —— 响应式：不做移动端适配，但有四条硬底线

**判断依据**：
1. FINAL_PLAN §8.3 把 oa-mobile 的**非目标**写成"组织管理、权限配置、表单设计器、报表"——
   那几乎就是 oa-console 的全部页面。做 oa-console 移动适配 = 违背已拍板的双端分工。
2. §8.2 全节**没有一个字**提到响应式/断点/viewport；§13 的前端验收只有 300KB gzip 一条。
3. 受众是内部管理员/HR/IT，设备是公司笔记本。
4. 三栏沙盘 + 拖拽 + 虚拟滚动真做移动适配约等于再造一个 oa-mobile。

**但"不做移动端" ≠ "只在 1920 上能跑"。四条可测底线**：

| # | 规则 |
|---|---|
| B1 | 任何 ≥1024px 视口下 `scrollWidth <= clientWidth`（溢出必须局部化） |
| B2 | **1366×768 是一等公民**（实际可用高度只有 ~600–620px） |
| B3 | 侧栏可折叠，且视口 <1440 时**默认折叠**（1366 下白吃 152px） |
| B4 | 表格横向滚动、不压列宽；操作列 `fixed:'right'` |

**断点表锚定 antd 默认值**（`lg=992`），加一档项目自定义 `1440`。
三栏最小宽度算术：左树 260 + 中 480 + 右 320 + gap 32 = 1092，加折叠侧栏 72 + padding 48 = **1212**
→ ≥1280 才勉强三栏、≥1440 才舒适。这就是把 1366 放进"右栏可收起"档的原因。

**窄屏降级范式照抄 `workflow-console/src/pages/DesignerPage.tsx:135-145`**：
`<Alert>提示需要更宽屏幕</Alert>` + **继续渲染只读预览**，而不是纯提示或纯拦截。

---

## ADR-C10 —— 视觉：完全沿用家族设计语言

三个同族 console 的 `theme/colors.ts` **逐字节相同**，说明这是一套已固化的设计语言。
oa-console **原样复制**，不另起炉灶：

主色 `#315EFB` · 软主色 `#EEF3FF` · 成功 `#16A36A` · 警告 `#D97706` · 错误 `#D92D20` ·
页底 `#F5F7FA` · 弱底 `#F7F9FC` · 边框 `#E6EAF0` · 文字 `#172033`/`#667085`/`#98A2B3` ·
圆角 8（LG 12）· 字号基准 14 · 控件高 36（LG 40）· Header 56 · Sider 224/72 · 内容区 max 1440

**不做暗色模式**（与家族一致）。登录页以 `workflow-console` 的左 hero + 右 SSO 卡为蓝本，
品牌渐变 `#10224a → #1e3a8a → #315efb` 保持不变，只把 BPMN 线稿换成 OA 语义的组织树/审批流线稿。

顺带把两处遗留硬编码（Table headerColor `#475467`、登录卡边框 `#EDF1F7`）补进 `colors.ts`。

---

## 已在 FINAL_PLAN 里做出的默认决定（可推翻）

计划评审指出：下面两条原本标为"待确认"，但 FINAL_PLAN 已经按某个选项写了。
为避免两份文档互相矛盾，改成**明确的默认决定**，你不同意再改：

| # | 决定 | 依据 | 推翻的代价 |
|---|---|---|---|
| D1 | **1366×768 写进硬验收**（A7） | 它是真实最常见的笔记本分辨率，而现有 test-plan 矩阵 1440/1024/768/390 恰好跳过了它 | 低：去掉 A7 后半段即可 |
| D2 | **沙盘门槛设 1280**（C 档三栏 + 右栏收起，而非降级为 Tabs） | §16 说沙盘是对外展示面；若演示机是 1366，降级成 Tabs 会让最有说服力的那一页失色 | 中：改成 1440 的话 1366 上要走 D 档的 Tabs 布局，9a 要多做一套 |

## ADR-C11 —— 移动端框架拍板：**antd-mobile（React）**（用户已确认）

上游 ADR-0003 把它列为开放项、R13 写"Phase 6 前必须拍板"。**本轮已拍板。**

**对 ADR-C2 的影响**：拍板后 `@oa/shared` **可以放 React hook** 了，
Day-1 monorepo 的收益上升。但 **B′ 仍然成立**，理由从三条变成两条：

1. Phase 3 的两条验收标准（不刷新即生效、来源链）一个字都不涉及代码共享；
2. Phase 6 的迁移是纯机械的（`git mv` + 改 4 个配置里的相对路径 + alias 指向），
   业务代码 import **一行不动**。

**变化的部分**：`src/shared/` 的边界可以放宽——原本"不许 import antd/react-router"
是为了防"万一 H5 是 Vue"。现在确定同为 React，这条纪律仍**保留**，但理由改成
"PC 的 antd 组件与 H5 的 antd-mobile 组件本就不能共用"——
共用的是 hook 与纯 TS，不是组件。这条线没变，只是理由更清楚了。

**`@oa/shared` 现在可以确定装什么**：
- 纯 TS：DTO 类型、permCode 常量、`Result` 解包、错误码映射、org_path 前缀匹配、色值原始量
- **React hook**（新增确定项）：`usePerm` / `usePermissionsQuery` / `useDirectorySync` 的逻辑核
- **仍然不装**：任何 antd 组件封装、`theme.ts`（antd ThemeConfig 是 antd 5 专有 schema）、`<PermRoute>`（依赖 react-router，H5 常用 tab 栈式路由）

## ADR-C12 —— e2e 只本地跑，CI 只跑单测（用户已确认）

评审 R-C9 指出：8 条 e2e 全需真实 8400 + 特定 seed 账号，且**撤权有副作用**
（第二次跑就不再是"从有到无"）。两条路：做可复原夹具进 CI，或只本地跑。

**决定：只本地跑。** CI 跑 `vitest` + build gzip size 断言。

**为什么**：把跑不了的东西写进 CI 比没有 CI 更糟 —— 一个长期红的流水线会训练所有人忽略它。
e2e 作为**发布前的手工关卡**（连同 9 个后端冒烟脚本一起跑）反而更可靠，
因为那时本来就有一套完整环境在跑。

**代价**：e2e 的回归保护依赖人记得跑。用 `deploy/scripts/phase3-console-smoke.sh`
把它和后端冒烟串在一起，降低"忘了跑"的概率。

## 待用户确认的开放项

1. **200% 浏览器缩放要不要支持** —— 1920 屏 200% = 960 CSS px，直接落到 `<lg` 档。
   若要支持，"992 以下的降级"就从极端场景变成常规场景，D/E 档优先级要显著上调。
2. **ESLint + Prettier 加不加** —— 上游 §8.2 写了要加，但三个同族 console 都没有。
   倾向：加，但只配 `no-restricted-imports`（守 `src/shared` 的纪律）+ 基础规则，不做大改造。
