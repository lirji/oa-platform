# OA Platform Completion Delivery Plan

## Requirement

按已确认顺序完成剩余交付：PC 控制台 E2E 收口、知识库与字段契约、容器/冒烟/CI、
Casdoor/JWT 与 WebSocket 认证、移动端 H5，以及最终审查、QA、文档和全链路压测。

## Repository Evidence

- 后端 Phase 0–8 已有实现与 9 组冒烟脚本，`mvn test` 当前通过。
- `oa-console` 主体已落地，当前有 102 条单测和 39 条尚未完整执行的 E2E。
- `oa-console/src/shared/types`、知识库前端、`oa-mobile`、PC 交付冒烟及 CI 尚不存在。
- 四个可部署单元仍使用 DEV 或默认 DEV 安全模式；JWT WebSocket 握手仍为已知缺口。
- `origin` 为 GitHub，因此 CI 采用 GitHub Actions。

## Feasibility

- Verdict: conditional-go
- Constraints:
  - 必须保留现有未提交的 PC E2E 与修复，不能覆盖用户工作。
  - 本机 Testcontainers 不可用，真实集成走 localhost compose/进程与 bash 冒烟。
  - JWT 联调依赖本机 Casdoor；真 workflow 集成依赖 `:8300` 服务可用。
  - 任何测试数据授权/撤权必须走 API，避免绕过权限失效协议。
- Dependencies:
  - JDK 21、system Maven、pnpm、Docker、Chromium、Casdoor、PostgreSQL/Redis/Kafka/MinIO。
- Risks and mitigations:
  - E2E 会改变授权和组织状态：串行运行、测试前后显式清理、使用固定夹具。
  - 首屏体积余量仅约 12.5KB：知识库继续路由级 lazy，移动端独立包。
  - JWT 会影响 231 条 DEV 冒烟：保留显式 DEV 测试 profile，生产默认 JWT。
  - WebSocket 浏览器不能自定义 Authorization 头：使用安全的一次性 ticket/cookie 握手方案，禁止 query 中长期 token。

## Product Design

- Actors and goals:
  - OA 管理员：在 PC 管控台管理组织、授权、知识库并解释权限来源。
  - 普通员工/审批人：在移动端打卡、查看与办理待办、查通讯录、读公告。
  - 运维/开发：用 compose、冒烟、CI 和文档可重复构建与验收。
- Scope:
  - 完成当前 PC 计划的 A1–A10、知识库页面、生成式 API 类型、生产交付与认证。
  - 新建独立 `oa-mobile` React + antd-mobile H5，覆盖原计划四个主功能。
- Out of scope:
  - `USER_GROUP`、ABAC、JIT 审批流、真实邮件/短信/企微/飞书 SDK、SpiceDB 接通、薪资/总账。
  - 生产部署、真实生产凭据、提交或推送 Git。
- Business rules:
  - 前端裁剪不是安全边界；所有受保护行为必须由后端再次判权。
  - 数据范围不足必须显式提示，不把 200+空数组冒充“全量为空”。
  - JWT 模式不得允许 localStorage DEV 身份覆写。
  - 移动端与 PC 共用协议语义，不复制后端判权规则。

## Acceptance Criteria

| ID | Observable behavior | Priority | Verification |
| --- | --- | --- | --- |
| AC-01 | PC A1–A10 的自动化断言可执行且通过，包含权限实时失效、后端边界、来源链、缓存、组织树、响应式、范围提示和提权 | P0 | Vitest + Playwright + phase3 smoke |
| AC-02 | PC 提供知识库列表、搜索、访问解释和拒绝态，路由/菜单受权限保护 | P0 | 组件测试 + E2E + Phase 7 API |
| AC-03 | `/v3/api-docs` 可生成 TS 契约，生成结果参与类型检查并有漂移门禁 | P0 | 生成命令 + clean diff/check |
| AC-04 | compose/profile 可启动 `oa-console`，镜像脚本可构建，nginx 代理 HTTP/WS，PC 冒烟可复现 | P0 | compose config + image build + smoke |
| AC-05 | GitHub Actions 在干净环境运行后端测试、前端单测/类型/构建/体积门禁 | P0 | workflow 语法检查 + 本地等价命令 |
| AC-06 | PC 和四个服务支持生产 JWT，DEV 仅显式测试 profile；登录、回调、续期和权限请求可联调 | P0 | Casdoor localhost 集成 + JWT 冒烟 |
| AC-07 | JWT 模式浏览器 WebSocket 可安全认证，长期 token 不出现在 URL | P0 | WS 集成测试 + 探针 |
| AC-08 | `oa-mobile` 可完成打卡、待办查看/办理、通讯录查询和公告查看/已读 | P0 | 单测 + Playwright mobile + API 冒烟 |
| AC-09 | README、架构、API、运行手册和权威进度与最终代码一致 | P1 | 文档交叉检查 |
| AC-10 | 全部相关构建、审查、QA、全链路读压测通过，残余外部风险明确 | P0 | Maven/pnpm/E2E/smoke/load evidence |

## UI/UX Design

- Applicability: applicable，沿用现有 OA 家族视觉语言，不另起设计系统。
- Flow and component map:
  - PC：后端菜单 → `/kb` → 列表/搜索 → 访问解释 Drawer/拒绝态。
  - Mobile：底部四标签（工作台、打卡、通讯录、公告）；待办详情从工作台进入。
- State matrix:
  - 所有页覆盖 loading、empty、partial/data-scope、error、403、offline/cache、success。
  - 202/异步受理明确显示“已受理”，不得显示“操作成功”。
- Responsive and accessibility behavior:
  - PC 守 1920×1080 与 1366×768；移动端主验收 390×844，并覆盖 320px 窄屏。
  - 可操作控件有可访问名称、键盘焦点与足够点击区域；错误不只靠颜色表达。

## Technical Solution

- Chosen approach:
  - 在现有单仓中保留 `oa-console` 单包，新建独立 `oa-mobile`；共享协议先通过生成文件和无 React 依赖模块复用，避免提前引入 workspace 迁移风险。
  - PC/移动端继续走现有 REST；通知 WS 增加短期一次性握手 ticket。
  - OpenAPI 从运行中后端导出固定 JSON，再生成并检查 TS 类型。
- Alternatives rejected:
  - WebSocket query 直接带 access token：会进入访问日志与历史记录。
  - JWT 后删除 DEV 模式：会破坏全部本地冒烟；改为生产默认 JWT、测试显式 DEV。
  - 为移动端复制权限规则：容易与后端分叉，只消费 `/me/permissions`。
- Modules and file map:
  - `oa-console/e2e/**`, `oa-console/src/pages/doc/**`, `oa-console/src/shared/types/**`, scripts/config/tests。
  - `oa-security`, `oa-iam`, `oa-notify-service`：JWT/WS ticket 所需契约与实现。
  - `oa-mobile/**`：独立 H5 应用、测试、Docker/nginx。
  - `deploy/**`, `.github/workflows/**`, `docs/**`：交付、CI、文档。
- Contracts and data:
  - 不改变既有业务表语义；如需 WS ticket，使用 Redis 短 TTL 一次性键，不持久化长期 token。
  - OpenAPI 生成物固定版本并通过脚本检查漂移。
- Security and reliability:
  - JWT issuer/audience 可配置，DEV override 在 JWT 下硬禁用。
  - WS ticket 绑定 user/tenant、短 TTL、取用即删，失败 fail-closed。
  - E2E 清理写在 `finally/afterAll`，避免跨次运行污染。
- Observability:
  - 认证失败、ticket 重放/过期、同步降级均有结构化日志；不记录 token/ticket 原文。
- Compatibility and migration:
  - 保留 `OA_SECURITY_MODE=DEV` 的本地测试入口；生产 compose 使用 JWT。
  - 新前端容器通过 profile 加入，不影响仅启动基础设施的现有命令。

## Implementation Sequence

1. PC E2E 收口与缺陷修复（AC-01）。
2. 知识库与 OpenAPI 类型（AC-02、AC-03）。
3. PC compose、镜像、冒烟、CI（AC-04、AC-05）。
4. Casdoor/JWT 与 WebSocket ticket（AC-06、AC-07）。
5. `oa-mobile` 四功能垂直切片（AC-08）。
6. 完整审查、QA、文档、全链路压测（AC-09、AC-10）。

## Verification Plan

| AC/Risk | Test level | Case or command | Required evidence |
| --- | --- | --- | --- |
| AC-01 | unit/e2e | `pnpm test`, `pnpm e2e` | 断言数与通过结果 |
| AC-02/03 | unit/integration | 组件测试、OpenAPI 生成与漂移检查 | 生成物、无 diff、页面行为 |
| AC-04 | delivery | compose config/build、phase3 smoke | 容器健康与断言计数 |
| AC-05 | CI | actionlint/yaml parse、本地等价命令 | workflow 与命令日志 |
| AC-06/07 | integration | JWT REST + WS 认证/重放/过期 | HTTP/WS 状态与服务日志 |
| AC-08 | unit/e2e | mobile Vitest/Playwright | 四主流程通过 |
| AC-09/10 | audit | 全量构建、smoke、load、文档检查 | review/QA/delivery reports |

## Documentation Plan

- 同步 `README.md`、`docs/ARCHITECTURE.md`、`docs/RUNBOOK.md`、`docs/API.md`、两份实施进度。
- 记录 JWT/WS、PC/mobile 构建运行、测试夹具、回滚和已知外部依赖。

## CI Plan

- GitHub Actions：后端 JDK21/system-compatible Maven 测试；PC/mobile pnpm frozen install、单测、类型、构建、体积门禁。
- E2E 依赖真实本地后端与夹具，保留发布前本地关卡，不在无服务的普通 CI 中假装执行。

## Rollout And Rollback

- 先 DEV profile 完成回归，再在本地 JWT profile 验证，最后构建前端镜像。
- 前端容器可独立回退到上一镜像；安全模式可在测试环境显式回 DEV，生产不得回退。
- WS ticket 功能可通过配置关闭并 fail-closed，不降级到 URL 长期 token。

## Assumptions And Open Decisions

- 用户“按照你的建议顺序，全做”视为对上述既有计划范围和实施顺序的明确批准。
- GitHub 为 CI provider（由 `origin` 证实）。
- 仅修改本仓和 localhost 测试环境，不部署生产、不写入真实凭据。
- 实施校正：知识库使用后端 V13 已发布的 `/kb` route，而非方案初稿中的 `/doc/knowledge`；这是契约对齐，不改变范围。

## Approval

- Status: approved
- Approved scope: PC 收口 → 知识库/OpenAPI → 交付/CI → JWT/WS → mobile → 最终 QA/文档/压测。
- Evidence: 2026-08-21 用户消息“按照你的建议顺序，全做”。
