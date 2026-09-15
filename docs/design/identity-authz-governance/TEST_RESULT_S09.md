# TEST_RESULT · S09

- Artifact: `TEST_RESULT`
- Slice: S09
- Date: 2026-09-15
- Validator: implementation-validation

## 实现选择

V117：`risk_rule` + `risk_finding`。三条内置规则，无 DSL。扫描 `POST /iam/risk/scan` 在请求内跑完，**不**引入 oa-job。同一规则+身份在 OPEN/ACKNOWLEDGED/IGNORED 上至多一条。

长期未用只扫非 USER：Human 走 `@RequiresPerm` 热路径，不写 `authz_decision_log`（D-GOV-008），用该表判 USER 会把全体员工打成风险。

CONTRACTS 未写 scan 端点；切片要求「跑规则产生 OPEN finding」，GET 带副作用不合适，故增加 `POST /scan`。

## 命令

```
mvn -pl oa-iam -am test
mvn -pl oa-iam,oa-app -am test -Dsurefire.failIfNoSpecifiedTests=false \
  -Dtest=RiskFindingServiceTest,ApiSurfaceGoldenTest,ControllerPermissionCoverageTest
pnpm exec tsc --noEmit   # oa-console
```

## 验收对照

| 验收 | 结果 | 证据 |
|---|---|---|
| 扫描三条内置规则可开 finding | PASS | RiskFindingServiceTest.scanOpensThreeBuiltinRules |
| 已有 OPEN/ACK/IGNORE 不重复开 | PASS | scanDoesNotDuplicateActiveFinding |
| OPEN→RESOLVED | PASS | openToResolved |
| 非法流转 1400 | PASS | resolvedCannotMove |
| 缺失 1404 | PASS | missingFindingIsNotFound |
| Golden / 权限覆盖 | PASS | ApiSurfaceGoldenTest, ControllerPermissionCoverageTest |
| oa-iam 全量 | PASS | 136 tests |
| `/iam` Tab 风险发现 | UNVERIFIED | tsc 通过；无运行中新进程 / 未 E2E |
| live Flyway V117 | UNVERIFIED | 未 rebuild |

## Gate

`PASS_WITH_ASSUMPTIONS`
