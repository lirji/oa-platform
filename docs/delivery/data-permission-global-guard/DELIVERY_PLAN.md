# Data Permission Global Guard Delivery Plan

## Requirement

Implement the approved global interface/data authorization plan in
`docs/plans/data-permission-global-guard/FINAL_PLAN.md` and deliver code, tests, QA, documentation, and CI-ready evidence.

## Repository Evidence

- Interface authorization already has build-time stance checks, API Golden, and `UnannotatedHandlerGuard`.
- Data authorization is opt-in: five production `@DataScope` methods.
- `DataScopeAspect` only warns when MyBatis does not consume the context.
- `OaDataPermissionHandler` silently skips alias mismatches.
- Permission snapshots currently widen data scopes by module, not permission.
- The local Docker profile uses DEV identity; production defaults remain JWT.

## Feasibility

- Verdict: go
- Constraints: preserve the existing dirty USER_GROUP/ABAC work; no destructive migration; system Maven/JDK 21; local-only QA.
- Dependencies: MyBatis-Plus 3.5.5, JSqlParser, Caffeine/Redis snapshot cache, existing permission catalog.
- Risks and mitigations: version the snapshot contract, stage strict mode, make ALL an explicit evaluated decision, test SELECT/UPDATE/DELETE and nested contexts.

## Product Design

- Actors and goals: users receive only rows allowed by the permission that authorized the operation; operators receive explicit failures and metrics for missing enforcement.
- Scope: permission-scoped data rules, strict consumption, table/alias verification, explicit bypass contract, build gates, first-party service migration, documentation and CI.
- Out of scope: changing user-facing screens; enabling true multi-tenant identity before a trusted tenant claim exists; destructive database RLS rollout.
- Business rules: RBAC/ABAC/JIT must pass first; each scoped operation names one permission; failures are fail-closed; bypass is explicit and audited.

## Acceptance Criteria

| ID | Observable behavior | Priority | Verification |
| --- | --- | --- | --- |
| AC-01 | OA handlers retain build/runtime authorization stance enforcement | P0 | coverage and Golden tests |
| AC-02 | Scoped data methods bind to an explicit permission and target table | P0 | annotation/architecture tests |
| AC-03 | Data scopes are stored and resolved per permission | P0 | snapshot/codec tests |
| AC-04 | SELECT/COUNT/UPDATE/DELETE receive the same scoped predicate | P0 | SQL rewrite tests |
| AC-05 | Missing consumption, alias/table mismatch, parsing failure and unknown scope fail closed in strict mode | P0 | aspect/handler tests |
| AC-06 | ALL is recorded as an evaluated decision, not confused with skipped enforcement | P0 | strict consumption tests |
| AC-07 | Existing Directory and Asset scoped paths are migrated without behavior regression | P0 | focused tests and smoke |
| AC-08 | Direct JDBC bypass is inventoried and constrained by build rules/explicit exceptions | P1 | architecture test and inventory |
| AC-09 | Deployment exposes strict mode and production-safe defaults | P0 | config/compose checks |
| AC-10 | Documentation, review, QA and CI reflect final behavior | P0 | artifact reconciliation |

## UI/UX Design

- Applicability: Not applicable. This is backend authorization infrastructure; current permission-denied responses remain unchanged.

## Technical Solution

- Chosen approach: extend `@DataScope`; add `@DataScopeBypass`; add request authorization context; store permission-keyed scopes in snapshots; version L2 codec; harden context consumption and SQL handler; migrate existing scoped services; add architecture and unit/integration gates.
- Alternatives rejected: module-only widening (over-broad); warning-only strictness (silent bypass); database-only RLS now (trusted multi-tenant session context is not available yet).
- Security and reliability: fail closed, explicit bypass audit, no untrusted tenant/user fields, versioned cache protocol, no I/O inside new synchronized sections.
- Compatibility: strict mode is configurable for staged migration; production example defaults strict; legacy module scope is retained only for diagnostics during migration.

## Implementation Sequence

1. Delivery state and focused baseline tests — AC-01/10.
2. Permission-keyed snapshot/codec/checker — AC-03.
3. Annotation, authorization context, strict execution record and handler — AC-02/04/05/06.
4. Migrate Directory/Asset and add bypass/architecture inventory — AC-07/08.
5. Configuration, observability, docs and CI — AC-09/10.
6. Review, local QA, repair and final reconciliation — all ACs.

## Verification Plan

| AC/Risk | Test level | Case or command | Required evidence |
| --- | --- | --- | --- |
| snapshot semantics | unit | `mvn -pl oa-iam -am test` | permission scopes survive codec and do not cross-widen |
| strict SQL | unit/component | data scope tests | select/update/delete, ALL, NONE, alias/table mismatch |
| build gates | architecture | reactor test | unauthorized data access/bypass declarations fail |
| regression | full backend | `mvn -B test` | all modules pass |
| runtime | localhost Docker | phase 1/2/8/9 smoke and health | no IDOR/security regression |

## Documentation Plan

Synchronize README, ARCHITECTURE, RUNBOOK, ABAC guide, approved plan, delivery reports, and configuration examples.

## CI Plan

Reuse the repository's GitHub Actions workflow when present; otherwise keep new tests in the Maven reactor so existing/default CI commands execute them.

## Rollout And Rollback

Use report mode for unmigrated modules, strict mode for migrated scopes, then make strict the JWT default. Roll back the application version only; snapshot protocol mismatch forces rebuild. Database changes, if any, are additive.

## Assumptions And Open Decisions

- One permission is the data-range owner for each scoped service operation.
- True multi-tenant RLS remains a later rollout because tenant identity is currently fixed to 1.

## Approval

- Status: approved
- Approved scope: `docs/plans/data-permission-global-guard/FINAL_PLAN.md`
- Evidence: user message “可以进行修改了” on 2026-08-21.
