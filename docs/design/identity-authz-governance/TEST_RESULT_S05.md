# TEST_RESULT · S05

- Artifact: `TEST_RESULT`
- Slice: S05
- Date: 2026-09-15
- Validator: implementation-validation

## 实现选择

权限委托写入新表 `oa_iam.permission_delegation`（V114），**不**写入待办 `oa_iam.delegation`（D-GOV-014）。
Check 在自身快照未命中后认时间窗 + 委托人仍 `engine.has`；**不**把委托写入 `PermissionEngine` 热路径，因此待办委托与 `@RequiresPerm` 行为不变。

## 命令

```
mvn -pl oa-iam -am test
mvn -pl oa-iam,oa-app -am test -Dsurefire.failIfNoSpecifiedTests=false \
  -Dtest=PermissionDelegationServiceTest,AuthzCheckServiceTest,DelegationRuleTest,ApiSurfaceGoldenTest,ControllerPermissionCoverageTest
pnpm gen:perm --check
pnpm exec vitest run src/shared/perm/codes.test.ts src/shared/api/errors.test.ts
pnpm exec tsc --noEmit
```

## 验收对照

| 验收 | 结果 | 证据 |
|---|---|---|
| 委托人必须当前持有 | PASS | PermissionDelegationServiceTest.createRequiresCurrentHolder → 3004 |
| 禁止再次委托 | PASS | PermissionDelegationServiceTest.reDelegateIsRejected |
| 时间窗内 Check ALLOW | PASS | AuthzCheckServiceTest.activePermissionDelegationAllowsCheck → `delegation:{id}` |
| 无有效委托 DENY | PASS | AuthzCheckServiceTest.expiredOrMissingDelegationDoesNotAllow；findActiveCovering 委托人失权为空 |
| 待办委托语义不变 | PASS | DelegationRuleTest；oa-iam 全量 106 tests（含 PermissionEngine / GrantService） |
| 待办委托不能调管理接口 | PASS | Check 只读 `permission_delegation`；`@RequiresPerm` 仍走快照，不读本表 |
| Golden / 权限覆盖 | PASS | ApiSurfaceGoldenTest, ControllerPermissionCoverageTest |
| perm `oa:iam:delegate` | PASS | 沿用 V10；`pnpm gen:perm --check`；codes.test.ts |
| Mapper XML | PASS | PermissionDelegationMapper.xml，无新注解 SQL |
| 前端 Tab 权限委托 | UNVERIFIED | tsc 通过；未对运行中 console 做 E2E |
| 过期靠时间窗落库 | UNVERIFIED | SQL `valid_from/valid_to` + status；未 rebuild / Flyway V114 |

## Gate

`PASS_WITH_ASSUMPTIONS`

可执行验收已过。运行期迁移与浏览器依赖重建容器，本轮无 deploy grant。
