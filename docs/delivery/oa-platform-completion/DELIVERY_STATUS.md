# Delivery Status

## Goal

完成 OA Platform 剩余 PC、认证、移动端和发布质量门禁，达到可重复本地验收与 CI-ready 状态。

## State

- Phase: Complete
- Status: complete
- Last updated: 2026-08-21

## Completed

- Phase 1–4：基于仓库、权威计划与当前工作区完成可行性、产品、UI/UX、技术方案和验收矩阵。
- Gate A：用户已明确批准建议顺序与全量范围。
- 基线验证：后端 16 模块 `mvn test` 成功；PC 102 条单测、类型检查、构建与 287.5KB 首屏门禁通过。
- Slice 1 / AC-01：PC E2E 从 39 条未执行收口为 41 条全绿；补齐 A2 来源链，修复共享全局权限纪元导致的并行竞态。
- Slice 2 / AC-02、AC-03：新增 `/kb` 知识库列表/单篇复核/访问解释；88 路径 OpenAPI 快照与 TS 漂移门禁；完整 PC E2E 47/47。
- Slice 3 / AC-04、AC-05：PC 生产镜像与 8404 Nginx、四服务代理、compose app profile、源码镜像构建、Phase 3 冒烟及 GitHub Actions 已落地；完整关卡 14/14。
- Slice 4 / AC-06、AC-07：四服务生产默认 JWT，issuer/audience/大 header/DEV header 隔离；本地 Casdoor 应用幂等注册；Redis 30 秒一次性 WS ticket、重放拒绝、严格 Origin 与前端连接；真实门禁 13/13。
- Slice 5 / AC-08：独立 `oa-mobile` 已交付打卡、真实待办办理、万人通讯录、公告已读，含权限门禁、OIDC、390/320 窄屏、174KB 首屏预算、8405 镜像与 Phase 5 非破坏性发布关卡；完整关卡 13/13。
- Slice 5 联调修复：LOCAL 流程替身的进程/任务 ID 改为跨重启唯一，首待办在审批实例回绑后再投影，避免重复 PID 与待办业务字段永久为空。
- Slice 6 / AC-09、AC-10：完成证据化审查、README/架构/API/Runbook/权威进度同步；修复 WS 会话并发上限竞态与公告列表连接池自锁；最终全链路 6 场景各 1000 请求、0 失败且 P99 全部达标。

## Changed Files

- `docs/delivery/oa-platform-completion/DELIVERY_PLAN.md` — 完整交付方案与 AC 矩阵。
- `docs/delivery/oa-platform-completion/DELIVERY_STATUS.md` — 可恢复状态。
- 既有 `oa-console` 修改与未跟踪 E2E 属于本轮开始前工作区内容，已纳入 Slice 1，未覆盖。
- `oa-console/playwright.config.ts` — 共享后端/epoch 的发布 E2E 固定单 worker。
- `oa-console/e2e/directory.spec.ts` — 双标签页等待最终一致而非中间分页态。
- `oa-console/e2e/live-perm.spec.ts` — 失败路径也清理临时授权。
- `oa-console/e2e/sandbox-source.spec.ts` — A2 完整来源链真实验收。
- `oa-console/src/pages/doc/KnowledgePage.tsx` — 知识库对象级权限 UI。
- `oa-console/e2e/knowledge.spec.ts` — 列表、详情、解释与后端 403。
- `oa-console/scripts/gen-openapi.mjs`, `oa-console/openapi/oa-app.json`, `oa-console/src/shared/types/openapi.d.ts` — 字段契约生成链。
- `oa-console/package.json`, `oa-console/pnpm-lock.yaml` — OpenAPI 生成命令与固定依赖。
- `oa-console/Dockerfile`, `oa-console/nginx.conf`, `oa-console/proxy_params`, `oa-console/.dockerignore` — 可部署 PC 镜像、SPA/WS/四服务代理与健康检查。
- `deploy/docker-compose.yml`, `deploy/build-images.sh`, `deploy/.env.example` — PC app profile、五镜像构建及变量契约。
- `deploy/scripts/phase3-console-smoke.sh` — 从当前源码重建镜像并执行完整 PC 发布前关卡。
- `.github/workflows/ci.yml` — GitHub Actions JDK 21 后端和 Node 20 PC 门禁。
- `oa-security/**`, `oa-iam/**/UserContextFilter.java` — JWT 安全默认、JWKS/issuer/audience 校验及 JWT 下硬禁 DEV 身份头。
- `oa-notify-service/**/ws/**`, `NotifyController.java` — 一次性 WS ticket 的签发、Redis 原子消费与握手认证。
- `oa-console/src/shared/api/notifySocket.ts`, `PermBridge.tsx`, `e2e/jwt-auth.spec.ts` — 浏览器 ticket 连接与真实 OIDC/refresh/WS 验收。
- `deploy/scripts/provision-oa-casdoor.sh`, `phase4-jwt-ws-smoke.sh` — 本地 Casdoor 应用注册与 JWT/WS 完整发布关卡。
- `oa-mobile/**` — 独立移动端应用、四场景页面、单测/E2E、OIDC、体积门禁和 Nginx 镜像。
- `deploy/scripts/phase5-mobile-smoke.sh` — 万人夹具上的非破坏性移动端发布关卡。
- `oa-flow/**/LocalWorkflowGateway.java`, `ApprovalService.java` — LOCAL 重启唯一 ID 与首待办正确投影。
- `oa-notify-service/**/SessionRegistry.java` — CAS 原子预留每用户 WS 会话名额。
- `oa-notify-service/**/AnnouncementService.java`, `ReadReceiptStore.java` — 列表完成后批量查询回执，消除嵌套 JDBC 自锁。
- `README.md`, `docs/ARCHITECTURE.md`, `docs/API.md`, `docs/RUNBOOK.md`, 权威实施进度 — 最终状态与操作契约。
- `CODE_REVIEW.md`, `QA_REPORT.md` — 审查发现、AC 证据与最终负载基线。

## Verification Log

| Command or check | Result | Notes |
| --- | --- | --- |
| `mvn test` | pass | 16 reactor modules；35 tests |
| `pnpm test` (`oa-console`) | pass | 9 files / 102 tests；存在 React act warnings |
| `pnpm exec tsc --noEmit` | pass | 包含当前 E2E TS |
| `pnpm build && pnpm size` | pass | initial gzip 287.5KB / 300KB |
| `pnpm exec playwright test --list` | pass | 6 specs / 39 tests collected；尚未执行 |
| `pnpm e2e` | pass | 7 specs / 41 tests；单 worker；27.0s |
| `pnpm gen:api:fetch && pnpm gen:api:check` | pass | 88 paths；生成物一致 |
| `pnpm e2e`（Slice 2） | pass | 9 specs / 47 tests；31.1s |
| `pnpm build && pnpm size`（Slice 2） | pass | knowledge lazy chunk 2.18KB gzip；initial 287.6KB |
| `bash deploy/scripts/phase3-console-smoke.sh` | pass | 14/14；含 102 unit、47 E2E、万人夹具、五镜像与四代理 |
| GitHub workflow YAML parse + 本地 `mvn test` 等价门禁 | pass | YAML 有效；16 模块后端测试通过；GitHub 远端执行待提交后触发 |
| JWT/WS targeted Maven + Vitest + TS | pass | 默认 JWT、aud、DEV header、ticket TTL/重放/Origin；前端 URL 不含长期 token |
| `bash deploy/scripts/phase4-jwt-ws-smoke.sh` | pass | 13/13；真实 Casdoor、四服务矩阵、PKCE/callback/refresh、浏览器 WS |
| `mvn -pl oa-flow -am test` | pass | LOCAL 重启 ID 唯一回归测试通过 |
| `pnpm test && pnpm build && pnpm size` (`oa-mobile`) | pass | 4 tests；initial gzip 174.0KB / 220KB |
| `bash deploy/scripts/phase5-mobile-smoke.sh` | pass | 13/13；真实打卡/待办/通讯录/公告、390/320、镜像/SPA/四代理 |
| 最终 `mvn -B test` | pass | 16 reactor modules；包含 workflow ID、announcement batch、WS session CAS 新回归 |
| 最终 PC 静态关卡 | pass | OpenAPI check；10 files / 105 tests；build；287.6/300KB |
| 最终 Mobile 静态关卡 | pass | 1 file / 4 tests；build；174.0/220KB |
| 最终 Phase 5（公告批量修复后） | pass | 13/13；2/2 浏览器测试，公告已读与请假状态持久化 |
| 全链路负载 `1000 20` | pass | 六场景 0 失败；P99 6.2–205.2ms，均低于各自预算 |
| `git diff --check` / compose config / workflow YAML | pass | 补丁格式、compose 与 CI 语法有效 |

## Decisions And Deviations

- 现有 E2E 会真实修改授权/组织数据；执行前先审查清理与夹具，避免污染。
- GitHub Actions 作为 CI provider，依据 `origin` GitHub URL。
- E2E 不能跨 worker 并行：撤权推进全局 epoch，会让无关账号的 per-version IndexedDB 缓存一起失效；发布关卡采用单 worker。
- 知识库路由按后端 V13 契约使用 `/kb`，修正了计划初稿中的推测路径 `/doc/knowledge`。
- Phase 3 必须始终从当前源码重建后端镜像；仅检查本机 tag 存在会把陈旧迁移打进发布结果，本轮已用 V6/V13/V22/V23 缺失故障验证并修正。
- `/ws` 在 SecurityFilterChain 层公开，但只能由握手拦截器消费 Bearer REST 换得的一次性 ticket；Nginx 对该路径关闭 access log，长期 token 不进 URL。
- Nginx 使用 Docker DNS 动态解析四上游，修复后端滚动重建后旧网关缓存容器 IP 导致 502。
- 移动端 E2E 使用后端已允许的 `:5474` Origin；测试会断言写请求 HTTP 成功，避免把 CORS 失败 Toast 误判成业务成功。
- 公告列表禁止在外层 JDBC 行回调中逐行查询回执；改为释放连接后按页批量读取，1000 请求压测确认无连接池等待。
- 全链路组织树场景按真实契约使用 `maxDepth=3`，避免误测 3000 节点的完整树响应。

## Blockers And Residual Risks

- localhost workflow-platform `:8300` 当前未运行；移动端待办已用仓库 LOCAL 流程完整验收，最终报告需明确真流程仍是外部依赖。
- Casdoor OA application 已在本机实测；生产仍必须替换 localhost issuer、client id 与凭据。
- PC 首屏体积余量较小，知识库必须保持 lazy chunk。

## Completion

AC-01 至 AC-10 已全部完成。后续动作仅为仓库所有者选择提交/推送，以及按 Runbook 注入生产域名、
密钥、TLS、调度和真实外部服务配置；这些不属于本次本地实现范围。
