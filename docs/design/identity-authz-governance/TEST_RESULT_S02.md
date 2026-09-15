# TEST_RESULT · S02

- Artifact: `TEST_RESULT`
- Slice: S02
- Date: 2026-09-15
- Validator: implementation-validation

## 命令

```
mvn -pl oa-iam -am test
mvn -pl oa-app -am test -Dsurefire.failIfNoSpecifiedTests=false -Dtest=ApiSurfaceGoldenTest,ControllerPermissionCoverageTest,ArchitectureRulesTest
pnpm gen:perm
pnpm exec vitest run src/shared/perm/codes.test.ts src/shared/api/errors.test.ts
```

## 验收对照

| 验收 | 结果 | 证据 |
|---|---|---|
| USER 持有权限 ALLOW + policyId | PASS | AuthzCheckServiceTest.userWithPermissionIsAllow → `rbac:effective` |
| USER 未持有 DENY | PASS | AuthzCheckServiceTest.userWithoutPermissionIsDeny → `default:deny` |
| 越权判定他人 1403 | PASS | AuthzCheckServiceTest.cannotCheckSomeoneElseWithoutAdmin |
| Agent 不继承 Owner | PASS | AuthzCheckServiceTest.agentIsDeniedWithoutInheritingOwner |
| 非 ACTIVE DENY | PASS | AuthzCheckServiceTest.disabledIdentityDenied |
| 审计写入失败不影响判定 | PASS | AuthzCheckServiceTest.auditInsertFailureDoesNotFailDecision |
| 请求不完整 3020 | PASS | AuthzCheckServiceTest.incompleteRequestIsInvalid |
| Golden / 注解覆盖 | PASS | ApiSurfaceGoldenTest, ControllerPermissionCoverageTest |
| 热路径未退化 | PASS | oa-iam 全量 86 tests（含 PermissionEngine） |
| 前端 perm 目录含 `oa:iam:check` | PASS | codes.test.ts 70 个权限点；parseGolden 识别 `a\|b` |
| Mapper XML | PASS | AuthzDecisionLogMapper.xml，无新注解 SQL |
| 决策表真实落库 | UNVERIFIED | 单测 mock insert；未 rebuild 运行中 oa-app / Flyway V111 |
| 浏览器沙盘 Check 试算 | UNVERIFIED | 未对运行中 console 做 E2E；无本片 Playwright |

## Gate

`PASS_WITH_ASSUMPTIONS`

可执行验收已过。运行期迁移与浏览器依赖重建容器，本轮无 deploy grant。
