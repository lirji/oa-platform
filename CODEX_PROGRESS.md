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
