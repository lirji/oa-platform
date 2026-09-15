# TEST_RESULT · S06

- Artifact: `TEST_RESULT`
- Slice: S06
- Date: 2026-09-15
- Validator: implementation-validation

## 实现选择

表 `oa_iam.credential`（V115）只存 SHA-256 哈希与 last4。签发/轮换响应带一次性明文；列表永不回 secret。人员身份拒绝签发。身份进入 DISABLED/EXPIRED/DELETED 时吊销全部 ACTIVE 凭证。

## 命令

```
mvn -pl oa-iam -am test
mvn -pl oa-iam,oa-app -am test -Dsurefire.failIfNoSpecifiedTests=false \
  -Dtest=CredentialServiceTest,IdentityServiceTest,IdentityStatusTest,ApiSurfaceGoldenTest,ControllerPermissionCoverageTest
pnpm exec tsc --noEmit
pnpm exec vitest run src/shared/perm/codes.test.ts
```

## 验收对照

| 验收 | 结果 | 证据 |
|---|---|---|
| 签发返回明文且库内是哈希 | PASS | CredentialServiceTest.issueReturnsPlaintextOnceAndStoresHash |
| USER 不能签发 | PASS | CredentialServiceTest.humanIdentityCannotGetCredential |
| 列表只有 last4 | PASS | CredentialServiceTest.listDoesNotExposeHash |
| 吊销 / 轮换 | PASS | revokeMarksInactive / rotateIssuesNewAndMarksOldRotated |
| 停用身份连带收凭证 | PASS | IdentityServiceTest.leaveDisablesExistingUserIdentity 调用 revokeAllOfIdentity |
| 生命周期状态机 | PASS | IdentityStatusTest；changeStatus 非法迁移 IDENTITY_STATUS_CONFLICT |
| Golden / 权限覆盖 | PASS | ApiSurfaceGoldenTest, ControllerPermissionCoverageTest |
| oa-iam 全量 | PASS | 111 tests |
| 详情页凭证 UI | UNVERIFIED | tsc 通过；未 E2E / 未 rebuild Flyway V115 |

## Gate

`PASS_WITH_ASSUMPTIONS`
