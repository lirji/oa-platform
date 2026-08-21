# Delivery Report

## Outcome

The approved global-guard platform slice is implemented and verified. Interface authorization now passes the exact
RBAC/ABAC-approved permission into data authorization; scope snapshots are permission-keyed; migrated MyBatis methods
fail closed on missing execution, table/alias mismatch and condition errors; strict mode is production-enforced.

## Delivered

- Permission-keyed scope snapshots and versioned L2 serialization.
- Required `@DataScope(permission, table)` contract and nested execution records.
- Exact request-permission binding across RBAC, ABAC and OR permission rules.
- Strict SQL handling for SELECT/COUNT/UPDATE/DELETE and explicit ALL evaluation.
- Governed-table fail-closed enforcement when migrated MyBatis tables are accessed without a scope context.
- Audited `@DataScopeBypass` and six runtime metrics.
- Directory and Asset first-slice migration.
- JWT startup guard and Docker/application strict configuration.
- Data-access Golden and whole-repository direct-JDBC migration baseline in CI.
- Architecture, operations, ABAC and rollout documentation synchronized.

## Acceptance Reconciliation

| AC | Status | Evidence |
| --- | --- | --- |
| AC-01 | pass | handler stance and API Golden tests remain green |
| AC-02 | pass for migrated surface | required permission/table metadata and data-access Golden |
| AC-03 | pass | snapshot isolation and codec tests |
| AC-04 | pass for MyBatis scoped paths | read/write predicate consistency test and runtime reads |
| AC-05 | pass | strict aspect/handler tests |
| AC-06 | pass | ALL explicit-evaluation test and runtime metric |
| AC-07 | pass | Directory/Asset Docker reads |
| AC-08 | pass as migration constraint | 21-file reviewed baseline; new direct JDBC fails CI |
| AC-09 | pass | strict configuration and JWT startup guard |
| AC-10 | pass | review, QA, docs and existing CI reconciled |

## Remaining Rollout

This delivery does not claim every business table is already row-filtered. Phase D must migrate the 21 reviewed JDBC
application/web classes and expand the data-access Golden until the baseline reaches zero. INSERT trusted ownership,
governed-table registration, tenant-aware identity/TenantLine and PostgreSQL RLS remain the later defense-in-depth phase.
