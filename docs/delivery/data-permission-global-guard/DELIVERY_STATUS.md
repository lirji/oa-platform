# Delivery Status

## Goal

Deliver permission-bound, fail-closed global data authorization infrastructure from the approved plan.

## State

- Phase: Phase 9 — final reconciliation
- Status: complete for the approved platform slice
- Last updated: 2026-08-21

## Completed

- Gate A approved; repository, build, CI metadata, current authorization chain, data-scope coverage and bypass risks inspected.
- Delivery plan recorded with AC-01 through AC-10.
- Permission snapshots resolve data range by permission rather than module and use versioned L2 payloads.
- `@DataScope` binds explicit permission/table metadata to the permission that passed RBAC/ABAC.
- Strict execution records distinguish evaluated ALL from missing interception; alias mismatch and missing target fail closed.
- Existing Directory and Asset scopes are migrated; strict mode is exposed in application and Docker configuration.
- Six low-cardinality metrics and audited `@DataScopeBypass` execution are implemented.
- Data access and direct-JDBC Golden tests run under the existing `mvn -B test` CI job.
- JWT startup rejects disabled interface enforcement or disabled data-scope strict mode.
- Local Docker runtime verified both ALL and ORG_AND_SUB SQL behavior with zero missing/alias/denied counters.

## Changed Files

- `docs/delivery/data-permission-global-guard/DELIVERY_PLAN.md` — approved delivery contract.
- `docs/delivery/data-permission-global-guard/DELIVERY_STATUS.md` — live workflow state.
- `oa-security/.../DataScope.java`, `DataScopeBypass.java`, `PermissionChecker.java` — public authorization contracts.
- `oa-iam/.../PermissionSnapshot*`, `SnapshotCodec.java`, `PermissionEngine.java` — permission-keyed scope snapshot.
- `oa-iam/.../RequiresPermAspect.java`, `DataScopeAspect.java`, `AuthorizationContext.java` — request-to-data binding.
- `oa-iam/.../datascope/*` — nested strict execution record, SQL fail-closed enforcement and metrics.
- `oa-org/.../DirectoryService.java`, `oa-admin-biz/.../AssetService.java` — first migrated scoped services.
- `oa-app/src/main/resources/application.yml`, `deploy/docker-compose.yml`, `deploy/.env.example` — strict-mode configuration.
- `oa-app/src/test/.../DataAccessSurfaceGoldenTest.java`, `JdbcBypassArchitectureTest.java` and two Golden files — build gates.
- `README.md`, `docs/ARCHITECTURE.md`, `docs/RUNBOOK.md`, `docs/ABAC.md` and approved plan — synchronized behavior and residual risk.

## Verification Log

| Command or check | Result | Notes |
| --- | --- | --- |
| Repository and instruction inspection | pass | main branch; GitHub remote; dirty USER_GROUP/ABAC work preserved |
| MyBatis-Plus interceptor bytecode inspection | pass | 3.5.5 handles SELECT, UPDATE and DELETE |
| `mvn -B -pl oa-iam -am test -DskipITs` | pass | permission scope, codec and strict SQL unit tests |
| `mvn -B -pl oa-app -am test -DskipITs` | pass | 12-module reactor; 24 IAM and 10 app tests passed |
| `mvn -B -pl oa-app -am test -DskipITs` after final gates | pass | 34 IAM tests and 12 app tests passed |
| `mvn -B test` | pass | all 16 reactor modules passed |
| Local Docker rebuild/start | pass | `oa-app` healthy on `:18400`; DEV/LOCAL, IAM/ABAC/strict enabled |
| Directory/Asset runtime reads | pass | seed admin ALL; seed-user-9999 ORG_AND_SUB visible=4 and asset list filtered empty |
| Data-scope metrics | pass | final image: evaluations=4, predicates=2, denied/missing/alias mismatch=0 |
| `phase9-iam-policy-smoke.sh` | pass | USER_GROUP lifecycle and ABAC deny/restore/CRUD all passed |
| `git diff --check` | pass | no whitespace errors |

## Decisions And Deviations

- UI/UX phase is not applicable; no user-facing surface changes are approved.
- PostgreSQL RLS is design-only until trusted tenant identity is implemented.
- Existing CI workflow already runs `mvn -B test`; no workflow edit was required for the new gates.

## Blockers And Residual Risks

- The approved platform slice is complete, but whole-repository row-level coverage is not: 21 reviewed application/web
  classes still use direct JDBC. The Golden blocks growth and makes the debt explicit; module migration remains Phase D.
- INSERT trusted-field enforcement, governed-table registry, tenant identity/TenantLine and PostgreSQL RLS remain later phases.

## Next Action

Migrate the 21 reviewed JDBC request-path classes module by module; delete each Golden entry as its MyBatis/data stance migration lands.
