# TEST_RESULT · S10

- Artifact: `TEST_RESULT`
- Slice: S10
- Date: 2026-09-15
- Validator: implementation-validation

## 实现选择

Agent `INVOKE` 复用 `POST /authz/check`。只评估 Agent `external_key` 自己的快照，**绝不**加载属主 permBits。调用审计与其它 Check 同源写入 `authz_decision_log`（CONTRACTS 允许同源；不另开 V118 / oa-job）。属主可在无 `oa:iam:admin` 时 Check 自己的 Agent。无新 REST 路径。

## 命令

```
mvn -pl oa-iam -am test
pnpm exec tsc --noEmit   # oa-console
```

## 验收对照

| 验收 | 结果 | 证据 |
|---|---|---|
| Owner 有权、Agent 无授权仍 DENY | PASS | AuthzCheckServiceTest.agentIsDeniedWithoutInheritingOwner |
| Agent 自身授权 INVOKE ALLOW | PASS | agentOwnGrantAllowsInvoke |
| 非 INVOKE 拒绝 | PASS | agentNonInvokeIsDenied |
| 调用写入决策审计 | PASS | 上述 ALLOW/DENY 均 verify insert |
| 属主可代查自己的 Agent | PASS | ownerCanCheckOwnedAgentWithoutAdmin |
| oa-iam 全量 | PASS | 139 tests |
| 身份详情 INVOKE 试算 | UNVERIFIED | tsc 通过；无运行中新进程 / 未 E2E |
| live Check | UNVERIFIED | 旧进程未加载本片 |

## Gate

`PASS_WITH_ASSUMPTIONS`
