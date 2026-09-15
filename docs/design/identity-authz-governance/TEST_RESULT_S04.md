# TEST_RESULT · S04

- Artifact: `TEST_RESULT`
- Slice: S04
- Date: 2026-09-15
- Validator: implementation-validation

## 实现选择

本地四眼（对齐 `elevation_request`），**不**走远程 oa-flow / Kafka（D-GOV-010）。`approval_instance_id = ACCESS:{id}`。

## 命令

```
mvn -pl oa-iam -am test -Dsurefire.failIfNoSpecifiedTests=false -Dtest=AccessRequestServiceTest
mvn -pl oa-app -am test -Dsurefire.failIfNoSpecifiedTests=false -Dtest=ApiSurfaceGoldenTest,ControllerPermissionCoverageTest
pnpm gen:perm && pnpm exec vitest run src/shared/perm/codes.test.ts
```

## 验收对照

| 验收 | 结果 | 证据 |
|---|---|---|
| 提交 ROLE 申请落 PENDING | PASS | AccessRequestServiceTest.createPendingRoleRequest |
| commandId 幂等 | PASS | AccessRequestServiceTest.duplicateCommandIdIsIdempotent |
| DELEGATION 拒走本片 | PASS | AccessRequestServiceTest.delegationTypeRejected |
| 自批 1403 | PASS | AccessRequestServiceTest.cannotApproveOwnRequest |
| 批准写 grant + invalidation | PASS | AccessRequestServiceTest.approveWritesApprovalGrant；代码 `source=APPROVAL` |
| Golden / 权限覆盖 | PASS | ApiSurfaceGoldenTest, ControllerPermissionCoverageTest |
| perm `oa:iam:request` | PASS | codes.test.ts 71 点 |
| why 中看到 APPROVAL 来源 | UNVERIFIED | 需 live grant + `/iam/admin/why`；未 rebuild |
| 浏览器「权限申请」Tab | UNVERIFIED | 未 E2E |

## Gate

`PASS_WITH_ASSUMPTIONS`
