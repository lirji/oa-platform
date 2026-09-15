# PROGRESS_STATE

- Artifact: `PROGRESS_STATE`
- Owner: `update-progress-docs`
- Plan: `docs/design/identity-authz-governance/`

## 目标

把 oa-platform Brownfield 演进为身份与授权治理平台（最小可运行授权平台 → 再补申请/委托/生命周期/同步/风险/Agent）。

## 设计 Gate

`PASS_WITH_ASSUMPTIONS`（CONSISTENCY_REVIEW）

## Slice 状态

| ID | status | evidence | blocker |
|---|---|---|---|
| S01 | DONE | TEST_RESULT_S01.md | live Flyway/浏览器 UNVERIFIED |
| S02 | DONE | TEST_RESULT_S02.md | live V111/浏览器 UNVERIFIED |
| S03 | DONE | TEST_RESULT_S03.md | live V112/浏览器 UNVERIFIED |
| S04 | DONE | TEST_RESULT_S04.md | live V113 / why APPROVAL UNVERIFIED |
| S05 | DONE | TEST_RESULT_S05.md | live V114 / 浏览器 UNVERIFIED |
| S06 | DONE | TEST_RESULT_S06.md | live V115 / 浏览器 UNVERIFIED |
| S07 | DONE | TEST_RESULT_S07.md | live Flyway V116 UNVERIFIED |
| S08 | DONE | TEST_RESULT_S08.md | live API/浏览器 UNVERIFIED |
| S09 | DONE | TEST_RESULT_S09.md | live Flyway V117 / 浏览器 UNVERIFIED |
| S10 | DONE | TEST_RESULT_S10.md | live API/浏览器 UNVERIFIED |

## 已完成

- 设计全套
- S01 身份目录
- S02 Check API + 决策审计
- S03 身份 1 跳图谱
- S04 权限申请（本地四眼，`source=APPROVAL`）
- S05 权限委托（`permission_delegation` 与待办 `delegation` 分表；Check 认时间窗）
- S06 NHI 凭证（哈希 + last4；明文只出现一次；停用连带吊销）
- S07 身份同步（请求内跑 INTERNAL/MOCK；`command_id` 幂等；无 oa-job）
- S08 资源反查 who-has-access（SQL 反查；组织走 org_path 前缀）
- S09 风险发现（内置 3 规则；请求内扫描；无 oa-job）
- S10 Agent INVOKE Check（不继承 Owner；审计走 decision_log 同源）

## 未完成

- 运行中 compose 未 `clean install`（V110–V117 未落地）
- 浏览器 / live API 验收
- PR / release / deploy 未授权

## 下一步

S01–S10 可执行验收已过。本轮授权本地提交并推 `origin/main`。live Flyway / 浏览器仍需 `clean install` 后重启。不创建 PR、不发版、不部署。

## Git grants

`git_commit=yes` `git_push=yes` `create_pr=no` `release=no` `deploy=no`
