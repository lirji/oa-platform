# TEST_RESULT · S01

- Artifact: `TEST_RESULT`
- Slice: S01
- Date: 2026-09-15
- Validator: implementation-validation

## 命令

```
mvn -pl oa-iam -am test
mvn -pl oa-app -am test -Dtest=ApiSurfaceGoldenTest,ControllerPermissionCoverageTest,ArchitectureRulesTest
pnpm gen:perm
pnpm exec vitest run src/shared/perm/codes.test.ts src/shared/api/errors.test.ts src/pages/iam/GrantsPage.test.ts
```

## 验收对照

| 验收 | 结果 | 证据 |
|---|---|---|
| USER 只能投影不能 POST | PASS | IdentityServiceTest.creatingUserIdentityIsRejected |
| NHI 创建成功 | PASS | IdentityServiceTest.creatingServiceAccountPersistsAndReturnsView |
| 重复 externalKey → 1409 | PASS | IdentityServiceTest.duplicateExternalKeyIsConflict |
| Agent 无 Owner → 3013 | PASS | IdentityServiceTest.agentWithoutOwnerIsRejected |
| 非法状态迁移 → 3012 | PASS | IdentityServiceTest.invalidStatusTransitionConflicts |
| 乐观锁冲突 → 1409 | PASS | IdentityServiceTest.staleVersionOnUpdateConflicts |
| 离职禁用身份 | PASS | IdentityServiceTest.leaveDisablesExistingUserIdentity + listener |
| 入职投影 | PASS | IdentityProjectionListenerTest.createdEventProjectsUser |
| 游标分页 | PASS | IdentityServiceTest.listUsesCursorAndHasMore |
| 未知标签拒绝 | PASS | IdentityServiceTest.unknownLabelRejected |
| DELETED 对外 3010 | PASS | IdentityServiceTest.deletedIdentityIsNotFound |
| Golden / 注解覆盖 | PASS | ApiSurfaceGoldenTest, ControllerPermissionCoverageTest |
| 热路径未退化 | PASS | oa-iam 全量 77 tests 含 PermissionEngine |
| 前端 perm 目录 | PASS | codes.test.ts 69 个权限点 |
| Mapper XML | PASS | 源码审查 IdentityMapper.xml，无注解 SQL |
| Flyway 在运行中 compose 执行 | UNVERIFIED | 未 rebuild oa-app；localhost:8400 仍是旧进程 |
| 浏览器点身份目录 | UNVERIFIED | 未对运行中 console 做 E2E；无 Playwright 本片 |

## Gate

`PASS_WITH_ASSUMPTIONS`

可执行验收已过。运行期迁移/浏览器依赖重建容器，本轮无 deploy grant。
