# Review Report

## Scope

Reviewed the permission-keyed snapshot, request authorization context, strict data-scope execution, SQL handler,
bypass audit, startup guard, configuration, tests and documentation for the approved platform slice.

## Result

- Status: pass after repairs
- Critical findings remaining: none in the delivered slice
- High findings remaining: none in the delivered slice

## Findings And Repairs

1. OR `@RequiresPerm` initially exposed every declared permission to the data layer. It now exposes only permissions
   actually held by the user and, when enabled, accepted by ABAC. A regression test covers this invariant.
2. `DataScopeMetrics` had multiple constructors and failed real Spring startup. The injectable constructor is now
   explicit; Docker startup is verified.
3. Target-table alias mismatch used to skip SQL injection. It now increments mismatch/denied metrics and throws.
4. A scoped method that executes no matching target SQL used to log only a warning. Strict mode now rejects it,
   while ALL remains an explicit successful evaluation.
5. Direct JDBC could grow silently. The reviewed 21-file baseline and interceptor-ignore scan now fail the build on drift.
6. Annotated-method Golden alone did not stop an unannotated Mapper call to a migrated table. A governed-table registry
   now rejects context-free MyBatis access, and the Golden requires every scoped table to be registered.

## Security Review

- Unknown/unheld permission scope resolves to NONE.
- Permission scopes widen only within the same permission, not across a module.
- L2 snapshot schema is versioned; incompatible payloads are discarded and rebuilt.
- Thread-local authorization and scope stacks are removed in `finally` and restore nested callers.
- Bypass metadata is validated and audited with subject, method, reason, tables and trace ID.
- JWT mode refuses `oa.iam.enforce=false` and `oa.iam.data-scope.strict=false`.

## Residual Risk

The build gate constrains but does not secure the 21 existing direct-JDBC request-path classes with row predicates.
Those classes remain explicit Phase D migration debt. Multi-tenant trusted identity, INSERT ownership enforcement and
database RLS are also outside this slice.
