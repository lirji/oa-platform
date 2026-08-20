# oa-console 实施计划（Phase 3 · PC 管理控制台）

> 决策依据：同目录 `DECISION_RECORD.md`（ADR-C1 ~ C10）
> 上游计划：`../oa-platform-0819-1721/FINAL_PLAN.md` §8.2 / §16 · `IMPLEMENTATION_PROGRESS.md` Phase 3 待办
> 后端：Phase 0–8 全部完成（231 条冒烟断言全绿）+ 本轮已补 10 个前端缺口（`dcadfc2`）
> **已过一轮独立评审**：评审实测了全部新增端点（均 200），并指出 16 条问题；
> 其中 4 条是后端契约缺陷（含我自己违反的"结果集一律用类型化 DTO"），已修复（`2e4ca97`）；
> 其余已反映到本文档（步骤拆分、逐页响应式矩阵、可测的验收断言、CI 夹具风险）。

---

## 1. Goals / Non-goals

### Goals
1. **权限渲染体系**跑通：`usePerm` / `<Can>` / `<PermRoute>`，菜单与按钮按 `permCodes` 裁剪
2. **Casdoor SSO** 接入，`oa.security.mode` 具备切 JWT 的条件（两阶段：先 DEV 联调、后切 JWT）
3. **组织管理页**（树 + 拖拽移动）、**员工管理页**、**角色与授权管理页**
4. ★ **权限沙盘页**（三栏联动）—— FINAL_PLAN §16 的"首个可演示里程碑"
5. **万人通讯录**（虚拟滚动 + IndexedDB 增量同步）
6. **工作台**（待办 + 我发起的单据），作为登录后的首页

### Non-goals（明确不做，并说明依据）
- **移动端适配**：oa-mobile 的非目标清单几乎就是 oa-console 的全部页面（ADR-C9）。
  只做四条响应式底线，不做完整适配
- **表单设计器 / 表单引擎**：`form_template` 表有、REST 零暴露，做不了；且 R12 明确"首版不做"
- **审批流程轨迹图（bpmn-js）**：后端无轨迹端点
- **公文 / 知识库 / 行政 / 考勤打卡的完整页面**：33 个端点可用，但都是员工自助型，属 oa-mobile 主场。
  本轮只做知识库列表 + 对象级判权解释器（与沙盘同源，值得一并展示）
- **WebSocket `perm.changed`**：依赖 ADR-C5 未解的 JWT 握手；ADR-C4 的另两条腿已满足验收
- **pnpm monorepo**：ADR-C2，Phase 6 再抽
- **ProComponents**：ADR-C6
- **暗色模式**：与家族一致

---

## 2. 视觉方向与设计参考

**沿用家族既有视觉语言**（不是新方向，因此无需用户在风格上做选择）。依据：
`auth-console` / `workflow-console` / `recon-console` 的 `src/theme/colors.ts` **逐字节相同**。

完整 token 表见 ADR-C10。要点：主色 `#315EFB`、圆角 8/12、控件高 36/40、
Header 56、Sider 224/72、内容区 max-width 1440、页底 `#F5F7FA`。

**登录页**以 `workflow-console/src/pages/LoginPage.tsx` + `global.css` 的 `.wf-login-*` 为蓝本
（左品牌 hero + 右 SSO 卡，深蓝渐变 `#10224a → #1e3a8a → #315efb`，玻璃拟态徽章，
`prefers-reduced-motion` 已处理），类名前缀 `wf-` → `oa-`，BPMN 线稿换成组织树/审批流线稿。

**唯一的新增视觉决策**：权限沙盘是家族里第一个三栏页面，家族里**没有可抄的先例**
（`auth-console` 那轮重设计的三栏方案 Solution C 明确未被采纳）。因此给出具体数字，
而不是指向一份没被采纳的文档：

| 元素 | 规格 |
|---|---|
| 左栏（组织树） | 宽 280（C 档 260），可拖拽调宽 240–360，`overflow-y:auto` |
| 中栏（来源链） | `flex:1; min-width:480`，超过则内部滚动 |
| 右栏（预览 Inspector） | 宽 360（C 档 320 且默认收起为 overlay Drawer） |
| 栏间分隔 | `1px solid #E6EAF0`（不用阴影，家族无浮层分栏先例） |
| 各栏 sticky header | 高 44，底色 `#F7F9FC`（与 Table headerBg 一致） |
| **来源链渲染** | antd `<Timeline>`，每条一个节点：`via`（主文案）+ `roleCode·rolePath`（次要文字 `#667085`）+ `grantType`/`validTo` 用 Tag |
| **判定状态** | 用 Tag：allowed=`success` 绿 / denied=`default` 灰 / 需提权=`warning` 橙 |
| **`consistent=false`** | 中栏**顶部 Alert `type="error"`**（不是整栏底色——整栏染红会盖住来源链本身） |

**来源链必须去重折叠**：实测同一角色可能出现十几条同源授权（继承闭包展开的结果）。
按 `(roleCode, via, scopeType, grantType)` 分组，同组折叠为一行 + "另有 N 条同源授权"可展开。
不折叠的话中栏是十几行几乎一样的噪声，**恰恰看不出"部门继承"这条最有说服力的链路**。

---

## 3. 路由与页面流

```
/login                      公开。开发模式显示"免登录进入"，JWT 模式跳 Casdoor
/callback                   OIDC 回调，按 state.returnTo 回跳深链
/403 /404                   Result 页

（以下均在 ProtectedRoute 内，各自再套 PermRoute）
/workbench                  工作台（首页）        oa:menu:workbench
  ├ 待办列表 / 我发起的单据 两个 Tab
/org                        组织人事              oa:menu:org
  ├ /org/tree               组织树 + 拖拽移动     oa:org:view
  ├ /org/employees          员工管理              oa:employee:view
  └ /org/directory          通讯录（虚拟滚动）    oa:employee:view
/iam                        权限中心              oa:menu:iam
  ├ /iam/grants             角色与授权管理        oa:iam:view
  └ /iam/sandbox            ★ 权限沙盘            oa:iam:admin
/kb                         知识库（列表 + 判权解释）oa:menu:kb
/report                     管理驾驶舱            oa:menu:report
  └ /report/audit           审计（需 JIT 提权）    oa:audit:view
/me                         个人中心（我的授权/代理/提权）
```

**关键流程**：

1. **登录装载**：Casdoor → `/callback` → `GET /me/permissions`（**一次请求**，
   它已含 username/employeeId/primaryOrg，FINAL_PLAN 写的 `/me/profile` 并不存在）
   → `<PermBridge>` 写 store → 菜单由 `menus`（后端已带 route/icon/sortOrder）渲染
2. **JIT 提权闭环**：任意操作收到 `3002` → 弹提权对话框（选项来自 `GET /iam/roles/mine`）
   → `POST /iam/elevations` → 重拉权限 → **自动重放原请求** → 顶栏提权倒计时徽标
   （数据源 `GET /iam/elevations/mine` 的 `remainingMs`，**本轮新增**——
   在此之前只有 `elevatedCodes` 没有到期时间，倒计时无处取数；
   `/explain` 的 `expiresInMs` 是快照缓存 TTL 不是提权 TTL，拿它当倒计时会显示假数字）
   → **提权到期时**：徽标消失、受影响按钮回到未提权态；进行中的请求返回 3003 归一为"提权已过期，请重新申请"

   ⚠️ **提权对话框自身可能 403**：`/iam/roles/mine` 与 `POST /iam/elevations` 都要 `oa:iam:elevate`。
   3002 分支必须先判 `has('oa:iam:elevate')`，没有就直接给"请联系管理员授予 X 角色"文案而**不弹选择器**——
   "申请入口存在但一点就 403"比不给入口更糟。
3. **★ 权限沙盘**：左树选人/选部门 → 中栏 `GET /iam/admin/why` 展示来源链 +
   `GET /iam/admin/explain` 展示缓存一致性 → 右栏 `GET /iam/admin/preview` 展示他的菜单/权限/数据范围
   → 演示动作：拖组织 / 加临时授权 / 撤权 → 右栏变化

---

## 4. 组件树（标注复用 vs 新建）

```
main.tsx  [复用] ConfigProvider(zhCN, appTheme) > AntdApp > QueryClientProvider > AppAuthProvider > RouterProvider
├── AppAuthProvider          [复用] workflow-console/src/auth/
│   ├── AuthBridge           [复用] OIDC → authStore 镜像
│   └── PermBridge           [新建] /me/permissions → permStore（Set 化 + version 去重 + userId==null 守卫）
├── AppLayout                [复用] Sider(224/72) + Header(汉堡+面包屑+用户 Dropdown) + Drawer(<lg)
│   └── 菜单                  [改造] 数据源从静态 NAV 改为后端 menus（route/icon 已由 V13 填好）
├── ProtectedRoute           [复用] 认证守卫（清掉 PHARMACIST/authz-admin 专有词）
├── PermRoute                [新建] 授权守卫，AdminRoute 的泛化版；★ 必须包在 Suspense 外层
├── Can                      [新建] 按钮级裁剪，mode="hide"|"disable"
├── PageHeader               [复用] 逐字相同，直接拿
├── AsyncState               [复用] PageSkeleton / ErrorState / EmptyState 三原语
├── DataCard + usePagedQuery [新建] 游标分页封装（recon-console 有裸写范式可参照）
├── ScopeHint                [新建] ★ 空态里显示"当前数据范围：本部门及下级（3 个前缀）"
├── OrgTree                  [新建] antd Tree + 懒加载 + 拖拽 + 成环前端预判
├── VirtualDirectory         [新建] @tanstack/react-virtual + 分组吸顶 + 首字母索引
├── PermSandbox              [新建] 三栏：OrgTree | WhyChain | PreviewPanel
└── ElevationDialog          [新建] 3002 拦截 → 提权 → 重放
```

---

## 5. 状态与边界情况（逐页）

**全局四态纪律**（照抄家族范式）：`isLoading → PageSkeleton` / `isError → ErrorState + onRetry` /
`空 → EmptyState` / `else → 内容`。loading 用 `isLoading` **不用 `isFetching`**；
error **不叠 toast**；多数据区**各自独立跑满四态**。

| 页面 | loading | empty | error | 特殊边界 |
|---|---|---|---|---|
| 工作台 | Skeleton×2（待办/我发起各一） | "暂无待办" | ErrorState | 待办为空 vs 无权限看待办 要分开文案 |
| 组织树 | Skeleton | 只有根节点 | ErrorState | **必须 `maxDepth=2` 懒加载**（缺省是整棵树）；`memberCount` 为 **null 表示未统计**，不要渲染成 0；拖到自己的后代 → 前端预判拦住；强行提交 → 409/2002 → 树回滚 |
| 员工管理 | Skeleton | ★ **`<ScopeHint>`**：空态必须显示当前数据范围 | ErrorState | 见下"数据权限静默失败" |
| 通讯录 | 骨架行 | 同上 | 顶部黄条 + 用本地缓存 | 首次全量分页拉；`permVersion` 变→清库重来；显示"更新于 X 分钟前" |
| 授权管理 | Skeleton | "该主体暂无授权" | ErrorState | `GET /iam/grants` 要求 subjectType+subjectId **都必填**，所以是"先选人再看授权"而非"列出全部" |
| **权限沙盘** | 三栏各自 Skeleton | 中栏"未选择用户" | 各栏独立 ErrorState | `consistent=false` 要**红色高亮**——它正是这个页面存在的理由 |
| 审计 | Skeleton | "无记录" | ErrorState | 未提权 → 3002 → 引导提权而非报"无权限" |

### 六类必须写清的边界（评审点名的）

| 边界 | 行为 |
|---|---|
| **并发编辑** | `PUT /org/units/{id}/parent` 无版本号，两人同拖同一节点是**静默后写胜出**。前端在拖拽提交前重取该节点 `path`，与本地不一致则提示"该组织刚被他人调整过，请刷新"而不是直接覆盖 |
| **乐观更新回滚** | 只有组织树做乐观更新（拖拽必须跟手）。**授权/撤权、员工编辑一律不做乐观更新** —— 撤权失败若已把行移出列表，用户会以为撤成功了 |
| **拖拽中断** | ESC 取消 / 拖出容器落空 → 不发请求；`PUT` 超时但后端可能已成功 → **不自动重试**（重试会二次移动），提示"结果未知，请刷新确认" |
| **IndexedDB 配额超限** | `QuotaExceededError` → 降级为纯网络模式 + 顶部提示"本地缓存不可用" + **不再重试写库** |
| **委托代理身份** | `delegators` 非空时顶栏显示"你正代理 X 的待办"横幅；待办列表里代理来的条目打标；办理时传 `onBehalfOf`。**不做这个的话会"操作到别人头上而不自知"** |
| **续期发生在操作中** | 拖拽/表单提交进行中触发 401 → 续期成功后**重放**该请求；续期失败 → 保留用户已填内容再跳登录（不要丢表单） |

### ★ 必须专门处理的三个静默失败

1. **数据权限不足 = HTTP 200 + `[]`**（后端 3005 从不抛出，`@DataScope` 算不出范围时生成 `1=0`）。
   与"真的没数据"**完全无法区分**。
   → **每个受数据权限约束的列表，空态必须渲染 `<ScopeHint>`**，显示 `dataScope` + `scopePrefixes` 条数。
   这是本次前端最容易"看着正常其实全错"的地方。

2. **`/me/permissions` 未认证返回 200 + 空清单而非 401**。
   → `PermBridge` 判 `userId == null` 就不写 store，否则表现为"整站菜单消失但不跳登录"。

3. **`<Can code="X">` 的 X 与后端 61 个 code 漂移**（打错字 = 永远隐藏 = 静默失败）。
   → `pnpm gen:perm` 从 `GET /iam/permissions/catalog` 生成 TS 常量 +
   **元测试**扫 `src/**` 里所有 `<Can code>` / `usePerm()` 字面量，断言全部落在常量集合内。
   这是后端 `ControllerPermissionCoverageTest` 的前端对偶。

### 错误码归一（HTTP status 与业务 code 双轨，只判 status 必错）

403 里混了 3001/3002/3003/3004/3005；409 里混了 2002/2003/2011/5001。
→ axios 拦截器统一归一成 discriminated union，**单测覆盖全表**：

| code | 处理 |
|---|---|
| 1401 | 触发续期 → 失败则跳登录（**只跳一次**） |
| 3001 | "无权限"，不给重试按钮 |
| **3002** | ★ **独立分支**：弹提权对话框 → 提权 → **重放原请求**。当成普通 403 的话用户永远找不到申请入口 |
| 3003 | "授权已过期，请重新申请" |
| 2002 | 组织成环 → 树回滚 + 定位冲突节点 |
| 2003 / 2011 | 就地红字 |
| 5002 | 额度不足 |
| 200 + `[]` | 见上，`<ScopeHint>` |

---

## 6. API 契约（全部已实测可用）

| 用途 | 端点 | 备注 |
|---|---|---|
| 权限装载 | `GET /me/permissions` | 含 menus(route/icon 已填) / moduleScope / version / elevatedCodes |
| 权限版本 | 响应头 `X-OA-Perm-Version` | 已 expose 到 CORS |
| 角色 | `GET /iam/roles` · `/iam/roles/mine` | **本轮新增** |
| 权限点目录 | `GET /iam/permissions/catalog` | **本轮新增**，含 `enabled`（孤儿 code 为 false） |
| 授权 | `GET/POST /iam/grants` · `DELETE /iam/grants/{id}` | GET 要求 subjectType+subjectId 都必填 |
| 提权 | `POST /iam/elevations` | body `{roleId, reason, hours}` |
| 委托 | `GET/POST /iam/delegations` · `DELETE /{id}` | |
| ★ 沙盘 | `GET /iam/admin/preview?userId=` | **本轮新增**：他人视角的 menus/permCodes/dataScope |
| ★ 沙盘 | `GET /iam/admin/why?userId=&permCode=` | **本轮新增**：来源链（部门继承/角色/临时授权） |
| 沙盘 | `GET /iam/admin/explain?userId=&permCode=` | 缓存一致性（`consistent`） |
| 沙盘 | `GET /iam/admin/bench?userId=&iterations=` | 现场实测判权 P99，展示效果强 |
| 组织树 | `GET /org/units/tree?rootId=&maxDepth=` | **必须传 maxDepth**，缺省是整棵树 |
| 组织移动 | `PUT /org/units/{orgId}/parent` | 成环 → 409/2002 |
| 员工 | `GET /org/employees/by-user/{userId}` + `/assignments` + `/manager-chain` + `/as-of?date=` | `as-of` 是低成本高说服力的"时间旅行"页 |
| 通讯录 | `GET /org/directory/page?cursor=&size=` | **本轮新增**游标分页 |
| 通讯录增量 | `GET /org/directory/delta?since=` | **本轮新增**，含 `deletions` 墓碑 + `fullResync` |
| 可见人数 | `GET /org/directory/count` | 沙盘右栏"能查到的数据行数" |
| 工作台 | `GET /flow/todos` · `/count` · `POST /{taskId}/complete` | |
| 我发起的 | `GET /flow/todos/mine` | **本轮新增**，已 UNION 请假与 11 类通用单据 = `/flow/docs/mine` 的全集。**工作台只用这一个**，不要两个都调 |
| 通用单据 | `GET /flow/docs/types` · `POST /flow/docs` · `GET /flow/docs/mine` | 11 类共用，schema 驱动渲染 |
| 驾驶舱 | `GET /report/overview` 等 4 个 | ⚠️ **Map 型**：`overview` 是 Map-of-List（`{approval:[...]}`），内层 **snake_case**，`avg_hours` 可为 null。`openapi-typescript` 只能产 `Record<string,unknown>` → **手写 interface + zod 运行时校验**，校验失败直接报错而非静默 undefined |
| 审计 | `GET /report/audit` | **需 JIT 提权** |
| 通知（:8401） | `GET /notify/messages` · `/unread-count` · `GET /announcements` | 跨端口，走 proxy |
| 文件（:8402） | `POST /file` · `GET /file/{id}/download` | ★ download **不走 Result 包装**，拦截器要开例外 |

**统一约定**：`Result{code,message,data,traceId}`，`code:0` 成功。
DEV 身份头 `X-OA-User`；JWT 模式取 `jwt.sub`。

---

## 7. 响应式与移动端适配策略

**断点表**（锚定 antd 默认 + 一档自定义 1440）：

| 档 | 宽度 | oa-console 策略 |
|---|---|---|
| A | ≥1600 | 三栏全展开；内容区放宽到 1600（沙盘需要）；侧栏展开 |
| B | 1440–1599 | 三栏（左树 280 / 中弹性 / 右 360）；侧栏展开 |
| **C** | **1280–1439（1366×768 落这里）** | 侧栏**默认折叠 72**；右栏 Inspector 默认收起（点开为 overlay）；表格 pageSize 20→**10**（可用高度只剩 ~620px） |
| D | 992–1279 | 侧栏强制折叠；三栏 → 左树 + Tabs(来源/预览)；双栏 → 上下单栏 |
| E | 768–991 | 侧栏 → Drawer（脚手架自带，白拿）；全单栏；沙盘/拖拽出 Alert + 只读降级 |
| F | <768 | 只保证登录 + 工作台只读 + 通讯录查询；其余统一 Result 页引导去移动端 |

**逐页矩阵**（每格一句话说清这一页在这一档长什么样）：

| 页面 | C 档（1280–1439，含 1366） | D 档（992–1279） | E 档（768–991） |
|---|---|---|---|
| 工作台 | 两个 Tab 并排，卡片两列 | Tab 并排，卡片单列 | Tab 堆叠，卡片单列 |
| 组织树 | 树宽 280 + 详情；拖拽热区保持 ≥32px 行高 | Tabs(树/详情) | Alert「组织调整请在桌面端」+ 只读树，拖拽禁用 |
| 员工管理 | 全列 + `scroll.x`；筛选栏默认折叠；**pageSize 10** | `scroll.x`；筛选折叠 | 表格保持 x-scroll（可接受） |
| 通讯录 | 列表 + 首字母索引条（宽 24） | 索引条收进"跳转"下拉 | 只保留搜索，去掉索引条 |
| 角色授权 | 选人区左栏 280 + 授权列表；15:9 | 选人区移到**顶部**，授权列表全宽 | 单栏上下 |
| **权限沙盘** | 三栏，右 Inspector 默认收起为 overlay Drawer | 左树 + Tabs(来源/预览)，顶部固定"当前人 + 当前权限点"summary bar | Alert「需 ≥1280」+ 单栏只读的解释结果 |
| 知识库 / 驾驶舱 | 两列 | 单列 | 单列，不优化 |

三条必须遵守的工程底线：
① 纯显隐用 CSS media query 不用 JS `isMobile`（避免闪烁与 jsdom matchMedia 问题）；
② 用了 `Grid.useBreakpoint()` 就必须在 vitest setup 补 matchMedia stub（现成代码在
`workflow-console/src/test/setup.ts:7-9`），不补会写出假绿的测试；
③ 窄屏降级一律"提示 + 只读"，不是"提示 + 拦截"。

---

## 8. 文件级改动清单

```
oa-platform/oa-console/                      ← 新建（单包，Phase 6 再 git mv 进 monorepo）
├── package.json                             对齐家族版本；加 @tanstack/react-virtual、idb
├── tsconfig.json                            [复用] + paths: @oa/shared/* → src/shared/*
├── vite.config.ts                           [复用] 改 port 5473；proxy 四上游（见下）
├── playwright.config.ts                     [复用] 改 5473；★ 加 1366×768 与 1920×1080 两个 project
├── Dockerfile                               [复用 workflow 版] 改 EXPOSE 8404
├── nginx.conf                               [复用] 改 listen 8404；反代四上游；★ 补 WS upgrade 头
├── .env.example / src/env.d.ts / src/config/index.ts   [复用] 改键名
├── src/
│   ├── shared/                              ★ 可迁移税：不许 import antd/react-router
│   │   ├── api/{client,errors,result}.ts    [复用] 401 单飞续期 + 错误归一
│   │   ├── perm/{codes.gen.ts,evaluate.ts}  [新建] 生成的常量 + 纯判权函数
│   │   ├── types/api.gen.ts                 [新建] 由 /v3/api-docs 生成
│   │   └── config/
│   ├── theme/{colors,theme}.ts              [复用] 逐字复制 + 补两处遗留硬编码
│   ├── styles/{global,login}.css            [复用] 类名 wf- → oa-
│   ├── auth/{oidcConfig,AppAuthProvider,AuthBridge,ProtectedRoute}.tsx  [复用] ★ 清专有词
│   ├── auth/{PermBridge,PermRoute,Can,usePerm}.tsx                      [新建]
│   ├── components/{layout/*,common/AsyncState,common/DataCard,common/ScopeHint}.tsx
│   ├── pages/{Login,Callback,Workbench,OrgTree,Employees,Directory,Grants,Sandbox,Kb,Report,Audit,Me}/
│   ├── hooks/{usePagedQuery,useDirectorySync,useBurstInvalidate}.ts     [后者复用]
│   └── test/{setup,renderWithProviders,renderWithDataRouter}.tsx        [复用]
├── e2e/{login,perm,org,sandbox,directory}.smoke.spec.ts
└── scripts/gen-perm.ts                      从 /iam/permissions/catalog 生成常量

oa-platform/deploy/
├── docker-compose.yml                       + oa-console service（profiles: apps，8404）
└── build-images.sh                          + oa-console 镜像
```

**vite proxy（四上游，具体前缀必须排在 `/api` 之前）**：
```
/api/v1/notify, /api/v1/announcements, /ws  → :8401
/api/v1/file                                 → :8402
/api/v1/job                                  → :8403
/api/v1                                      → :8400
```
Casdoor `:8000` **不代理**（authority 直连保持 issuer 一致——两个 console 的注释都写死了这条）。

---

## 9. 实施步骤（按依赖排序）

| # | 步骤 | 产出 | 依赖 |
|---|---|---|---|
| **0** | **演示与测试数据集**：`POST /org/seed?orgs=3000&employees=10000`（接口已有，开关 `oa.org.seed.enabled`）+ 固定 5 个 e2e 账号（super / hr / dept-manager / employee / no-perm）+ **至少 1 条 ORG_UNIT(含下级) 授权 + 1 条 TEMPORARY 授权**（否则沙盘来源链只能展示"本人被直接授权"，看不出继承） | `deploy/scripts/seed-console-fixture.sh` | — |
| 1 | 脚手架落地：复制 workflow-console → oa-console，改 8 处值，清专有词，起 dev；**同时产出 `useAppBreakpoint()`** | `pnpm dev` 打通 8400 | — |
| 2 | `src/shared/` 骨架 + `gen-perm` + `openapi-typescript`；★ **沙盘 4 个端点与 `report/*` 手写 interface + zod** | 61 个 code 的 TS 常量 | 1 |
| **3a** | 权限体系核心：`PermBridge` / `usePerm` / `<Can>` / `<PermRoute>` + 错误码归一 | 单测覆盖全错误码表 | 2 |
| **3b** | 3002 提权闭环：拦截 → 挂起 → 提权 → **重放**；多请求同时 3002 只弹一次；倒计时（`/iam/elevations/mine`）；**无 `oa:iam:elevate` 时不弹选择器** | | 3a |
| **3c** | **最小 JWT 联调**：Casdoor 建应用 + 一份 yml 配 `issuer-uri`，让权限体系**在两种模式下都跑通一次** | 身份注入是可切换 adapter | 3a |
| 4 | AppLayout 菜单改为后端驱动（消费 menus 的 route/icon） | 换角色菜单就变 | 3a |
| 5 | 工作台（待办 + 我发起，只用 `/flow/todos/mine`） | 首页可用 | 4 |
| 6 | 组织树（`maxDepth=2` 懒加载 + 拖拽 + 成环预判 + 409 回滚 + 并发提示） | | 4 |
| 7 | 员工管理（antd Table virtual）+ `<ScopeHint>` + as-of 时间旅行 | | 6 |
| 8 | 角色与授权管理（消费 `/iam/roles`） | | 3a |
| **9a** | 沙盘三栏骨架 + C/D/E 三档降级（含 Inspector 收起） | | 6,8 |
| **9b** | 中栏：`why` 来源链（**按 (roleCode,via,scopeType,grantType) 去重折叠**）+ `explain` 一致性 Alert | | 9a,3b |
| **9c** | 右栏：`preview`（他人菜单/权限/范围）+ `/org/directory/count` 可见行数 | | 9a |
| **9d** | 演示动作联动：拖组织 / 加临时授权 / 撤权 → 右栏变化；`bench` 现场实测按钮 | **演示里程碑** | 9b,9c |
| 10 | 通讯录：游标分页 + 虚拟滚动（**delta/page 单次上限 500，必须循环拉到 `hasMore=false`**） | 万人可滚 | 4 |
| 11 | 通讯录：IndexedDB 增量同步（`(userId,permVersion)` 分区、敏感字段不落盘、墓碑、配额降级、陈旧提示） | | 10 |
| 12 | 知识库列表 + 对象级判权解释器；驾驶舱 + 审计（走提权） | | 3b |
| 13 | 响应式逐页过一遍（§7 矩阵）+ 1366 视口 | | 5–12 |
| 14 | 交付：Dockerfile / nginx（**含 WS upgrade 头**）/ compose / build-images + `phase3-console-smoke.sh` | | 全部 |
| 15 | 切 JWT 收尾：四份 yml + 冒烟改造（见下） | | 14 |

**为什么把 JWT 提前到 3c**：原计划排在最后，会让前 14 步的身份链路全是 DEV 假象 ——
**1401 续期单飞**（§10 单测 7）在 DEV 下永远不触发，那条单测在切换前一直是"绿但没验过真路径"；
`/callback` 深链回跳无从验证；e2e 的越权断言靠 `X-OA-User` 头，切换后 8 个 spec 全要改造。
3c 只做"最小联调"（能登录、能带 token、能续期），完整切换仍在 15。

**为什么 `useAppBreakpoint()` 提到 step 1**：§7 C 档要求的"pageSize 20→10"和"右栏 Inspector 收起"
是**组件结构决策**不是样式收尾，放到 step 13 会让 step 7/9 返工。

**步骤 15 的已知阻塞**：Casdoor 里目前**没有** oa 应用（实测查了 `application` 表）；
四个可部署单元各有一份 `oa.security.mode`；切 JWT 后 9 个冒烟脚本的 `X-OA-User` 手法全部失效
（231 条断言依赖它），需要先定"冒烟怎么拿 token"。**建议 Phase 3 主体在 DEV 模式完成，
JWT 切换单独作为一个收尾步骤并配套改冒烟。**

---

## 10. 测试策略

**单测（Vitest + RTL）**——判据：纯逻辑、错误映射、缓存正确性、时间相关
1. **错误码映射全表**（最划算的一条）：`{status, code}` → `{kind, text, action}`，覆盖上面那张表
2. `<Can>` / `usePerm` / `<PermRoute>`：含"未知 code 一律判拒"与"permCodes 为空不是放行而是全拒"
3. **permCode 元测试**：扫 `src/**` 的 `<Can code>` 字面量 ⊆ 生成的常量集合
4. IndexedDB 层（`fake-indexeddb`）：全量、增量 merge、**删除/离职**、schema 升级、写入中途 reject、配额超限降级
5. 成环判定纯函数 `canDrop(dragId, dropId, tree)` 表驱动
6. axios 参数序列化：中文 keyword 必须是 `%E6%94%BE%E5%81%87`；`undefined` 不拼进 query
7. 401 并发单飞：5 个并发 401 只触发一次续期、只跳一次登录（fakeTimers）
8. queryKey 完整性：权限相关的 key 必须含 permVersion
9. 缓存失效正确性：授权/撤权/组织移动后断言相关 key `isInvalidated === true`

**e2e（Playwright）**——判据：跨进程、真实浏览器 API
1. ★ **"隐藏了但后端也拦住了"**（ADR-0008 强制验收）：低权用户 → 断言按钮 `toHaveCount(0)`
   → **同一 context 里 `page.request.post()` 直接打接口** → 断言 403 且 `code===3001`。挑 8–10 个高危 code 数据驱动
2. ★ **"改角色后不刷新即生效"**：page A 停在某页 → 另一身份 `page.request` 撤权
   → 断言 **`page.on('framenavigated')` 计数为 0** 且按钮在 3s 内消失。
   **收权与授权分开测**（后端对二者的保证强度不同：收权走全局 epoch 必 1 秒内，授权不保证秒级）
3. 组织树拖拽成环：前端预判拦住 + 强行提交 409 后树回滚
4. 万人通讯录：断言 `[data-testid="dir-row"]` 数量 **< 60**（证明确实虚拟化）
5. IndexedDB 二次进入秒开：`page.route` 延迟 delta 2s，断言列表 500ms 内可见
6. 跨标签页并发同步：两个 page 同时 reload，断言最终一致且无 `ConstraintError`
7. **视口矩阵**：1920×1080 与 **1366×768** 两个 project（现有 console 只跑单视口，这是要修正的缺陷）
8. **诚实守卫负向断言**（家族约定）：全站禁止把 202 说成"操作成功"；
   沙盘在 `consistent===false` 时不得说"该员工能看到 X"

**冒烟**：`deploy/scripts/phase3-console-smoke.sh`，沿用家族的 `ok/bad/step` + 末尾计数范式。

**CI 边界（ADR-C12）**：CI 只跑 `vitest` + build gzip size 断言。
e2e 需要真实后端与特定 seed 账号、且撤权有副作用，**只本地跑**，串进上面那个冒烟脚本
作为发布前关卡。

**手工验证**：真实 Casdoor SSO 全流程 · Safari 隐私模式下的 IndexedDB 与静默续期 ·
3000 节点树的拖拽手感 · 沙盘三栏联动的观感延迟 · 弱网首屏。

---

## 11. 验收标准

| # | 标准 | 可执行断言 |
|---|---|---|
| A1 | 改角色后**不刷新即生效** | e2e：page A 停在某页 → 另一身份 `page.request` **撤权** → `page.on('framenavigated')` 计数为 0 且目标按钮 3s 内消失。**收权与授权分开测**：收权走全局 epoch 必 1 秒内；授权后端不保证秒级，断言放宽到"下一次交互后" |
| A2 | 权限调试器能解释**完整来源链** | 三条：① `sources.length >= 1`；② 每条必须渲染 `via` / `rolePath` / `grantType` / `validTo` 四字段；③ **存在 `subjectType=ORG_UNIT` 的来源时，页面必须出现"继承自 X 部门"文案**（step 0 的夹具保证有这样一条） |
| A3 | 前端隐藏的按钮后端依然 403 | e2e：低权用户 → 按钮 `toHaveCount(0)` → 同 context 里 `page.request.post()` → 403 且 `code===3001`。挑 8–10 个高危 code 数据驱动 |
| A4 | 首屏 JS **< 300KB gzip** | CI 对 `dist/assets/index-*.js` 做 gzip size 断言。⚠️ 口径先定（见 R-C2） |
| A5 | 通讯录二次进入**秒开** | e2e：`page.route` 人为延迟 delta 2s，断言列表在 **500ms** 内可见 |
| A6 | 3000 节点树初始渲染 `.ant-tree-treenode` **< 100** | e2e，依赖 step 0 的数据集 |
| A7 | **1366×768 无页面级横向滚动、主 CTA 可见** | ① `document.documentElement.scrollWidth <= clientWidth`；② 每页主按钮带 `data-testid="primary-action"` 且 `boundingBox().y < viewport.height` |
| A8 | 数据权限为空时空态显示范围 | 两个不同 `dataScope` 的 seed 用户查同一列表，断言空态文案**不同**（step 0 保证有这两个账号） |
| A9 | 3002 走提权闭环 | 单测（mapError 的 3002 分支 + 提权成功后自动重试）+ e2e 完整闭环，含**提权到期后按钮回到未提权态**（用 `/iam/elevations/mine` 的 `remainingMs`） |
| A10 | permCode 无漂移 | 元测试：扫 `src/**` 的 `<Can code>` / `usePerm()` 字面量 ⊆ 生成的常量集合 |

---

## 12. 风险与回滚

| # | 风险 | 影响 | 对策 |
|---|---|---|---|
| R-C1 | **JWT 模式下浏览器连不上 WS**（ADR-C5） | 通知/公告在生产不可用 | Phase 3 不依赖 WS（ADR-C4 三条腿去掉一条仍成立）；Phase 6 前必须解决 |
| R-C2 | **300KB gzip 与 antd 本身冲突**（antd 单 chunk 已 ~300KB gzip） | 硬验收第一天就红 | 先确认预算口径（是 initial chunks 总和还是登录页实际下载量）；路由级 lazy + CI size 断言 |
| R-C3 | Casdoor 无 oa 应用、后端无 `issuer-uri` | 步骤 15 卡住 | 主体在 DEV 完成，JWT 单独收尾；切换前先补 Casdoor 应用与四份 yml |
| R-C4 | 切 JWT 后 9 个冒烟的 `X-OA-User` 全失效（231 条断言） | 回归能力丢失 | 切换方案里必须含"冒烟怎么拿 token"，或保留 DEV profile 专供冒烟 |
| R-C5 | 数据权限静默返回空被当成"没数据" | 用户以为系统坏了 / 或以为自己看全了 | `<ScopeHint>` 列为硬验收 A8 |
| R-C6 | 移动端框架未定，`@oa/shared` 边界可能返工 | Phase 6 返工 | ADR-C2 已把风险压到最小（单包 + 可迁移税） |
| R-C7 | 沙盘 `preview`/`why` 每次都走 DB 重算 | 拖拽时请求放大 | 三栏各自 queryKey + `useDeferredValue` + `keepPreviousData`；e2e 断言一次拖拽 XHR < 5 |
| R-C8 | 200% 缩放 = 960 CSS px，落到 `<lg` 档 | 常规场景被当极端场景 | 列为待确认项（开放项 4） |
| ~~R-C9~~ | e2e 的 CI 可执行性 | — | **已决策（ADR-C12）**：e2e 只本地跑，CI 只跑 vitest + gzip size 断言。把跑不了的东西写进 CI 比没有 CI 更糟——长期红的流水线会训练所有人忽略它。e2e 串进 `phase3-console-smoke.sh`，与 9 个后端冒烟一起作为发布前关卡 |
| **R-C10** | **不引 MSW**（上游 §8.2 列了它，三个同族 console 都没有） | 部分 e2e 无法脱离真实后端 | 跟随家族不引，理由是"多一层 mock 就多一处与真后端漂移的地方"；代价由 R-C9 承担 |

**回滚**：oa-console 是独立可部署单元，不改任何后端行为；出问题直接停容器，后端与冒烟不受影响。
本轮已合入的后端缺口修复全部是**新增端点 + 新增列**，无破坏性变更（V6 视图重建严格保持原语义）。
