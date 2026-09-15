# TEST_RESULT · S08

- Artifact: `TEST_RESULT`
- Slice: S08
- Date: 2026-09-15
- Validator: implementation-validation

## 实现选择

`GET /iam/admin/who-has-access` 按权限点 SQL 反查有效 `grant_record` + 权限委托，不把全员丢进 `PermissionEngine`。组织主体用 `org_path LIKE 前缀%`，算不出前缀则 0 行。来源：USER 距离 0=`Role`，角色继承/组织=`Inherited`，用户组=`Group`，委托且委托人仍 `engine.has`=`Delegation`。`why` / `preview` / `explain` 仍返回 `Map`，未改 JSON。无新表。

## 命令

```
mvn -pl oa-iam -am test
mvn -pl oa-iam,oa-app -am test -Dsurefire.failIfNoSpecifiedTests=false \
  -Dtest=WhoHasAccessServiceTest,ApiSurfaceGoldenTest,ControllerPermissionCoverageTest
pnpm exec tsc --noEmit   # oa-console
```

## 验收对照

| 验收 | 结果 | 证据 |
|---|---|---|
| permCode 返回身份+来源 | PASS | WhoHasAccessServiceTest.userGrantDistanceZeroIsRole |
| 角色继承 Inherited | PASS | userGrantDistancePositiveIsInherited |
| 用户组 Group | PASS | groupGrantIsGroupSource |
| 组织走 path 前缀而非 org_id IN | PASS | orgGrantUsesPathPrefixNotIdList；无 path 则 0 行 |
| 委托 Delegation；委托人丢失则不列出 | PASS | activeDelegationIsDelegationSource / delegationIgnoredWhenDelegatorLostPermission |
| resourceType=API 等同 permCode | PASS | resourceTypeApiUsesResourceIdAsPermCode |
| 非 API / 缺参 3020 | PASS | missingQueryIsInvalid / nonApiResourceIsInvalid |
| 未知权限 known=false | PASS | unknownPermReturnsEmptyKnownFalse |
| why/preview/explain 未改 | PASS | IamAdminController 仍返回 Map；本片只新增 who-has-access |
| Golden / 权限覆盖 | PASS | ApiSurfaceGoldenTest, ControllerPermissionCoverageTest |
| oa-iam 全量 | PASS | 130 tests |
| 沙盘资源反查 UI | UNVERIFIED | tsc 通过；无运行中新进程 / 未 E2E |
| live API | UNVERIFIED | localhost:8400 仍是旧进程 |

## Gate

`PASS_WITH_ASSUMPTIONS`
