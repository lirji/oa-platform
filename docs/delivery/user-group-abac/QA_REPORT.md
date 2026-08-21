# USER_GROUP / ABAC QA Report

## Verdict

Pass。AC-01 至 AC-10 均有自动化或真实服务证据；未执行生产部署。

## Acceptance Evidence

| AC | Evidence | Status |
| --- | --- | --- |
| AC-01 | 用户组 service tests；Phase 9 创建/加成员/移出/禁用；PC 创建/编辑/成员 Drawer | Pass |
| AC-02 | Phase 9 观察组权限出现，撤销后立即消失；epoch 日志与快照重建 | Pass |
| AC-03 | Grant/member next-boundary 进入 `expireAt`；角色继承/数据范围沿用原引擎 | Pass |
| AC-04 | Phase 9 `/iam/admin/why` 返回 `subjectType=USER_GROUP` 与组名 | Pass |
| AC-05 | Phase 9 validate/create/toggle/list/delete；PC 条件完整生命周期 | Pass |
| AC-06 | evaluator 单测覆盖 branch AND、source OR、无条件旁路；真实 endpoint false→403 | Pass |
| AC-07 | `T/new/@bean/getClass/method/assignment` 对抗用例拒绝；缺变量 fail-closed | Pass |
| AC-08 | Snapshot codec round-trip；求值器依赖仅 catalog/meter/快照/参数 | Pass |
| AC-09 | Playwright 3/3：管理员三视图、普通员工403、390×844 无页面横向溢出 | Pass |
| AC-10 | 96-path OpenAPI、13 API golden、16-module Maven、PC build/size、docs/config checks | Pass |

## Test Results

- Backend: `mvn -B test` — 16 reactor modules passed；IAM 19 tests，oa-app 10 tests。
- PC unit: 10 files / 105 tests passed；query-key version guard 包含新增查询。
- PC build: TypeScript + Vite passed；首屏 gzip 288.2 KB / 300 KB。
- Playwright focused: 3/3 passed，单 worker。
- Runtime smoke: Phase 9 10/10 passed；USER_GROUP effect/revoke and ABAC 403/restore observed.
- Database: Flyway V14 applied successfully to PostgreSQL 16；schema and tenant column inspected.
- Static: OpenAPI check、permission code check、Compose config、CI YAML parse、shell syntax、diff whitespace all passed.

## Test Data And Cleanup

- API smoke revoked created grants, soft-deleted conditions, removed members and disabled QA groups.
- Database intentionally retains disabled groups `QA_USER_GROUP_20260821_1033` and `QA_IAM_POLICY_SMOKE` as lifecycle/audit artifacts.
- Local source-run service used DEV auth and `OA_IAM_ABAC_ENABLED=true`; this is not production authentication evidence.

## Known Limits

- Existing Vitest suite emits React `act(...)` warnings but exits green；这些警告早于本功能且不影响断言结果。
- Phase 9 assumes the standard 10k seed fixture (`seed-user-1`, `seed-user-10000`, `seed-user-544`).
- No production tenant, JWT, TLS or load test was run for this feature slice.
