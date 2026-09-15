# TEST_RESULT · S07

- Artifact: `TEST_RESULT`
- Slice: S07
- Date: 2026-09-15
- Validator: implementation-validation

## 实现选择

请求内跑完同步，**不**引入 oa-job / 新进程。连接器 `INTERNAL`（员工投影）+ `MOCK`（内置 3 条 NHI 目录）。`command_id` 唯一幂等；身份靠 `(tenant, type, external_key)` 去重。LDAP 返回 1400。

## 命令

```
mvn -pl oa-iam -am test
mvn -pl oa-iam,oa-app -am test -Dsurefire.failIfNoSpecifiedTests=false \
  -Dtest=IdentitySyncServiceTest,IdentityServiceTest,ApiSurfaceGoldenTest,ControllerPermissionCoverageTest
```

## 验收对照

| 验收 | 结果 | 证据 |
|---|---|---|
| MOCK 首次创建 3 条 | PASS | IdentitySyncServiceTest.mockFullCreatesThenUpdatesSameKeys |
| 同 commandId 不重跑 | PASS | 同上第二次 start 仍 id=8，upsert 仍 3 次 |
| 同键 upsert 不双份 | PASS | IdentityServiceTest.upsertSyncedNhiIsIdempotentOnSameKey |
| INCREMENTAL 跳过已有 | PASS | mockIncrementalSkipsExisting；incrementalUpsertSkipsExisting |
| INTERNAL FULL 刷新+补齐 | PASS | internalFullRefreshesThenBackfills |
| LDAP 拒绝 | PASS | ldapIsRejected |
| Golden / 权限覆盖 | PASS | ApiSurfaceGoldenTest, ControllerPermissionCoverageTest |
| oa-iam 全量 | PASS | 118 tests |
| live Flyway V116 | UNVERIFIED | 未 rebuild |

## Gate

`PASS_WITH_ASSUMPTIONS`
