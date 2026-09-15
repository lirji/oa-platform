# FRONTEND_ARCHITECTURE · 治理控制台

- Artifact: `FRONTEND_ARCHITECTURE`
- Owner: `frontend-architecture-design`
- Brownfield：`oa-console` React 18 + Vite + antd 5 + TanStack Query + Zustand + React Router 6

## 1. 目标与非目标

**目标**：把 `/iam` 从「OA 权限策略页」演进为身份与授权治理工作台，绑定正式 CONTRACTS。

**非目标**：重做壳层/主题/菜单系统；给 oa-mobile 做治理台；引入第二套 Table/Form/Button。

## 2. 角色与主作业

管理员主作业在 PC：查身份、改授权、解释决策、处理申请、看风险。
员工主作业：工作台待办（已有）、申请权限（后期切片）、委托（后期 UI）。

首页仍是工作台（待办），**不**改成 IAM 卡片站点图。治理入口继续走后端菜单 `oa:menu:iam` → `/iam`。

## 3. 信息架构（兼容 + 演进）

**兼容（本期默认）**

```
/iam                 治理工作台（Tabs，避免新顶栏菜单撑爆首屏预算）
  身份目录           S01  新增
  主体授权           已有
  角色管理           已有
  用户组             已有
  ABAC 条件          已有
  提权审批           已有
  权限申请           S04
  权限委托           S05
  风险发现           S09
/iam/sandbox         已有；S02 增加 Check 试算；S08 增加资源反查
/iam/identities/:id  身份详情（含标签、生命周期、Agent 属主）
/org/*               人事主数据，不替代身份目录
/report/audit        HTTP 审计，与决策审计分工展示
```

**演进（不在第一片做）**：若 Tab 过载，再把 `oa:menu:iam` 拆子菜单（需后端 menus 子节点，且评估 gzip）。

oa-mobile：治理 UI 范围外（A6）。

## 4. 状态与数据流

- 会话：现有 OIDC + `permStore` + `X-OA-Perm-Version`
- 服务端状态：TanStack Query，queryKey 必须带 `permVersion`
- URL：沙盘已有 `userId/permCode`；身份详情用 path id；列表筛选进 search params
- 禁止编造 JSON；类型以 OpenAPI / CONTRACTS 为准
- 403 显示无权限，不显示空表；网络错误不显示「暂无数据」；202 显示进行中

## 5. 设计系统

单一 antd 5 + 现有 `PageHeader` / `DataCard` / `Can` / `ScopeHint`。
身份类型、风险等级用 Tag，不引入新图表库。

## 6. 屏幕状态（核心页）

每页覆盖：loading / empty / error / 403 / success / 409 conflict（乐观锁 version）。
Access Request：审批中用进行中态，不提前显示「已授权」。

## 7. 权限点（前端只引用 PERM 常量）

新增 code 由迁移生成 `pnpm gen:perm`，源码禁止手写裸 `oa:` 字面量（`codes.test.ts`）。

## 8. 下游契约需求

- 身份列表游标分页、类型化 DTO（不要 `Map`）
- Check API 请求/响应
- 决策审计查询
- 身份详情含 labels / status / ownerIdentityId

## 9. 首屏预算

新增治理页必须 `React.lazy` 路由级或 Tab 内懒加载，不得进入登录首屏 chunk（300KB gzip）。
