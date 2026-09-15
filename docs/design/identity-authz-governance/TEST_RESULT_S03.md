# TEST_RESULT · S03

- Artifact: `TEST_RESULT`
- Slice: S03
- Date: 2026-09-15
- Validator: implementation-validation

## 命令

```
mvn -pl oa-iam -am test
mvn -pl oa-app -am test -Dsurefire.failIfNoSpecifiedTests=false -Dtest=ApiSurfaceGoldenTest,ControllerPermissionCoverageTest,ArchitectureRulesTest
```

## 验收对照

| 验收 | 结果 | 证据 |
|---|---|---|
| USER 1 跳含部门 / 属主反向 OWNS / HAS_ROLE | PASS | IdentityGraphServiceTest.userGraphHasOrgOwnerAndRoles |
| Agent 只展示属主 OWNS，不查角色 | PASS | IdentityGraphServiceTest.agentShowsOwnerOwnsEdgeWithoutRoles |
| 组织缓存未命中不拖垮图谱 | PASS | IdentityGraphServiceTest.orgLookupFailureFallsBackToPath |
| 不存在身份 3010 | PASS | IdentityGraphServiceTest.missingIdentityIsNotFound |
| Golden 含 GET `.../graph` | PASS | ApiSurfaceGoldenTest |
| 无新权限点（复用 identity:view） | PASS | 迁移 V112 无 INSERT permission |
| 写边不对前端开放 | PASS | 仅 GET；现算 identity/grant_record，无边表写 API |
| 热路径未退化 | PASS | oa-iam 90 tests |
| 浏览器身份详情图谱 | UNVERIFIED | 未 rebuild console / 无本片 E2E |

## Gate

`PASS_WITH_ASSUMPTIONS`

可执行验收已过。运行期 V112 与浏览器依赖重建容器，本轮无 deploy grant。
