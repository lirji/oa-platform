# Codex Progress — OA Docker 重新部署（2026-10-02）

## 任务目标

将最新 OA main 产品源码重新部署到本机 Docker 并启动，保留业务数据和现有配置。

## 已完成

- 最新产品源码 a9994e0 的精确 CI36669495481 三项成功；六个应用镜像重新构建为 rev-a9994e0。
- 10 个原 OA 服务已启动且 healthy；端口 PC8404、移动8405、后端18400 保留，restart=unless-stopped。
- 四个新构建 JAR 与镜像字节一致；PC56、移动6静态文件 HTTP 字节一致；PC125测试、生成权限契约及生产构建预算通过。
- V8/V25/V26 三个新迁移成功；114张原业务表数据摘要与备份一致，原数据库/文件/卷保留。
- PC及移动真实PKCE登录、权限中心/待办实际页面、三个只读授权API和12健康/匿名拒绝检查通过，0页面脚本错误。
- 数据库、MinIO及原配置已备份，旧镜像已留回退标签。没有业务写入、重新种子、鉴权分区切换或工作树清理。

## 已修改文件

- 本检查点；docs/deployment/docker-redeploy-20261002.md。
- 忽略的 deploy/.env 仅更换 OA_IMAGE_TAG；.local/docker-redeploy-20261002/ 保存证据与备份。
- 原有未提交文档改动保留，本次 Git 只暂存本检查点新增内容和新报告。

## 未完成

- 部署验收无；文档 Git / CI 最终回执见私密 deployment-result.json，避免提交自引用。

## 当前问题

- 无部署阻断。保留历史验证脚本失败证据；当前成功回执与其分开。
- 后续重建容器需复核 restart 策略；保持原 LOCAL 工作流、JWT与凭据。

## 下一步建议

1. 读取正式报告和私密 deployment-result.json；DONE后不重复部署。
2. 用户可直接打开 http://localhost:8404/login 或 http://localhost:8405。

## 恢复 Prompt

请先读取本检查点、docs/deployment/docker-redeploy-20261002.md 和 .local/docker-redeploy-20261002/deployment-result.json；若 DONE 且容器版本/健康一致则本任务已完成。保留原有未提交文档、数据库、文件卷、备份及旧镜像，不清理、不重新规划已完成任务。

---

# Progress

## 任务目标

把运行中的 oa-platform Brownfield 演进为企业身份与授权治理平台：身份 → 权限数据 → 策略 → 决策 → 授权 → 审计 → 风险 → 生命周期。现有 OA 审批保留为 Access Request / 提权 / 委托工作流，不是产品核心。设计已批准；S01–S10 已落地并推送。不创建 PR / 不发版 / 不部署。

## 当前状态

S01–S10 已提交并推送 `origin/main`（`0049cd0`）。live Flyway / 浏览器仍 UNVERIFIED。

## 已完成

- 设计：`docs/design/identity-authz-governance/`
- S01 身份目录。`TEST_RESULT_S01.md`
- S02 `POST /authz/check` + 决策审计 + 沙盘 Check。`TEST_RESULT_S02.md`
- S03 `GET /iam/identities/{id}/graph`。`TEST_RESULT_S03.md`
- S04 权限申请本地四眼（不走远程流程）。`TEST_RESULT_S04.md`
- S05 权限委托。`TEST_RESULT_S05.md`
- S06 NHI 凭证。`TEST_RESULT_S06.md`
- S07 身份同步。`TEST_RESULT_S07.md`
- S08 资源反查。`TEST_RESULT_S08.md`
- S09 风险发现。`TEST_RESULT_S09.md`
- S10 Agent INVOKE。`TEST_RESULT_S10.md`

## 未完成

- 运行中 `localhost:8400` 仍是旧进程（V110–V117 未执行）
- 浏览器 E2E
- PR / release / deploy 未授权

## 下一步

本轮已提交并推送 `origin/main`：`0049cd0`。live API / 控制台仍需 `clean install` 后重启。不创建 PR、不发版、不部署。
