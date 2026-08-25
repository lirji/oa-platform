# Delivery Report

## Outcome

角色管理已从“只读角色目录 + 主体授权”补齐为可在线管理的完整闭环：自定义角色 CRUD、复制、启停、权限矩阵、角色继承、防环、停用收权、租户隔离、并发保护、全局缓存失效、统一审计和 PC 管理界面均已交付。

## Requirement Coverage

| AC | Implementation evidence | Verification evidence | Status |
| --- | --- | --- | --- |
| AC-01 | `RoleAdminService#create`、V16、CreateRole contract | service tests + API QA | complete |
| AC-02 | update/status + version CAS + active closure rebuild | unit + status API QA | complete |
| AC-03 | copy direct permissions/inheritance to custom role | service path + compile/API contract | complete |
| AC-04 | builtin/in-use guards + soft delete | unit + delete API QA | complete |
| AC-05 | direct/effective permission APIs and UI matrix | API QA + browser drawer | complete |
| AC-06 | direct edge table, tenant lock, cycle detection, closure rebuild | unit + HTTP 409 + PostgreSQL migration | complete |
| AC-07 | grant/JIT ACTIVE guards, epoch invalidation, active-only closure | unit + grant/closure API QA | complete |
| AC-08 | RoleManagementPanel and responsive controls | 107 tests + build + browser desktop/mobile QA | complete |
| AC-09 | `oa:iam:admin`, API Golden, automatic write audit | architecture tests + audit DB query | complete |
| AC-10 | OpenAPI/type/docs and existing CI parity | contract checks + full builds | complete |

## Changed Files

- Backend: V16 migration、`RoleAdminService`、`RoleAdminController`、Role DTO/commands/mapper/tests、角色目录租户过滤、授权/提权 ACTIVE 校验、IAM 失效日志。
- Frontend: `RoleManagementPanel.tsx`、其单测、`GrantsPage.tsx` 页签接入、OpenAPI snapshot/types。
- Gates/docs: API Golden、`docs/API.md`、`docs/AUTHORIZATION.md`、本交付目录五份证据文档。

## Build And Test Results

- `mvn test`: BUILD SUCCESS，16 个 reactor 模块全部通过。
- `pnpm gen:perm --check && pnpm gen:api:check`: pass。
- `pnpm test`: 11 files / 107 tests pass。
- `pnpm build`: pass。
- `pnpm size`: 288.2 KB gzip / 300 KB pass。
- Flyway/PostgreSQL/API/browser QA: pass。

## Code Review And QA Verdicts

- Code review: pass；已修复停用继承收权、列表 N+1、租户过滤、查询缓存版本和停用 JIT 校验问题。
- QA: pass；真实数据库、API 和桌面/窄屏浏览器交互均覆盖。

## Documentation Changes

- 权限指南已改为在线角色管理真实行为，包含边/闭包、软删除、停用、审计与 SUPER_ADMIN 保护规则。
- API 文档和 OpenAPI 生成类型已加入 9 个角色管理端点。
- README、架构、ADR、数据库组件清单、权限加载链路和运行手册已同步直接边/闭包职责、并发协议、
  发布验证与回退流程。

## Local Docker Deployment Verification (2026-08-24)

- 相关后端 reactor 测试通过：`oa-iam` 40 条、`oa-app` 12 条；PC 权限/OpenAPI 契约与 107 条单测通过。
- 从当前源码生成 `oa-app`、`oa-notify-service`、`oa-file-service`、`oa-job-service`、`oa-console`、
  `oa-mobile` 六个 `local` 镜像，并只重建六个应用容器；PostgreSQL、Redis、Kafka、MinIO 容器 ID 未变。
- 六个应用容器及四个基础设施容器均为 healthy；主应用实际宿主端口为 `.env` 配置的 `18400`，
  PC/Mobile 为 `8404/8405`，两端 Nginx 代理 ping 均返回 `code=0`。
- Flyway V16 `role management` 为 success，`oa_iam.role_inherit_edge` 存在；部署后 OpenAPI 为
  102 paths / 9 role-admin operations。无身份访问角色管理返回 401，DEV 管理身份读取返回 `code=0`。
- Ubuntu ports APT 上游恢复后已补跑完整冷构建：全仓 `mvn clean package`（含测试）通过，四个后端与
  PC/移动端全部使用 `--pull --no-cache` 重建。镜像内四个 JAR SHA-256 与宿主机构建制品逐一一致，
  `oa-job-service` 降至 607 MB，不再包含上一轮复用的旧 JAR 层；两套 Nginx 配置校验通过。

## CI Changes And Validation

现有 GitHub Actions 已完整覆盖本次所需的 backend reactor、权限/API Golden、前端契约、单测、构建和包体积，因此未新增重复 pipeline。底层命令均已在本地通过；远程 runner 尚未触发。

## Deviations From Plan

- 审查后收紧内置角色保护：所有内置角色不可停用，SUPER_ADMIN 的权限和继承矩阵不可在线修改。
- 角色默认列表不展示软删除记录，但保留 DELETED 筛选用于审计追溯。
- 停用角色会从 active closure 中移除，启用时重建恢复，语义比仅在快照查询过滤更严格。

## Rollout, Monitoring, And Rollback

- 顺序：执行 V16 → 发布后端 → 发布 PC；旧 `/roles` 和主体授权契约兼容。
- 监控：角色写接口 409/5xx、`role-*` epoch 日志、审计成功/失败分布、PC 请求错误。
- 回滚：前后端可回退；V16 新表为附加结构。已产生的业务角色变更应通过审计记录人工反向操作，不直接删生产数据。

## Remaining Risks Or External Actions

- 需要合入分支后由 GitHub Actions 再验证一次干净环境。
- 本地 DEV 数据库保留了 4 个已软删除的 `CODEX_*_QA` 角色作为验收痕迹，默认管理列表不可见。
