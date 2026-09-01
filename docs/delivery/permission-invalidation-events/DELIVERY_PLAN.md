# Permission Invalidation Events Delivery Plan

## Requirement

Improve role-change notification and permission snapshot invalidation without making Kafka the correctness boundary: keep a database permission epoch as truth, use Redis Pub/Sub for low-latency JVM-local cache invalidation, and publish durable role domain events through an outbox to the OA Kafka bus.

## Repository Evidence

- Permission snapshots currently use JVM-local Caffeine L1, Redis L2, and PostgreSQL rebuilds in `oa-iam`.
- `oa_iam.perm_epoch` is a single global row; role writes call `IamInvalidationService.all(...)` before the surrounding transaction commits.
- `RedisInvalidationBus` is best-effort broadcast and the engine polls the database epoch at most once per second.
- OA Kafka and an outbox publisher already exist for flow commands, but IAM has no durable role event.
- Tenant IDs already exist on IAM truth tables and `TenantContext` defaults to tenant `1`.

## Feasibility

- Verdict: go
- Constraints: preserve mixed-version behavior during rolling deployment; do not make Kafka availability block role writes; avoid cross-module Mapper injection; keep L1 loading free of synchronized I/O.
- Dependencies: PostgreSQL, Redis, existing OA Kafka configuration, Spring transaction synchronization.
- Risks and mitigations:
  - Old nodes only understand global epoch messages: continue bumping the legacy global row and keep the existing Pub/Sub record shape during migration.
  - Pub/Sub can be lost: tenant epoch polling remains authoritative.
  - Kafka can be unavailable: role event is committed to an IAM outbox and retried asynchronously.
  - Duplicate/late events: include event ID and epoch; cache consumers apply only newer epochs; Kafka consumers are required to be idempotent.

## Product Design

- Actors and goals: permission administrators need role changes to take effect promptly and safely; operators need reliable role-change events and observable retry state.
- Scope: role CRUD/matrix/inheritance invalidation, tenant-scoped snapshot versions and cache keys, after-commit Pub/Sub, durable `iam.role.changed.v1` events, tests and operational documentation.
- Out of scope: UI changes, external Kafka consumers, splitting `default_scope` and `max_scope`, deployment to production.
- Business rules:
  - Database state and tenant epoch commit atomically.
  - Cache notification never precedes transaction commit.
  - Kafka failure never rolls back an already committed role change.
  - A role change in tenant A must not invalidate tenant B on fully upgraded nodes.
  - Rolling deployment remains safe through the legacy global epoch fence.

## Acceptance Criteria

| ID | Observable behavior | Priority | Verification |
| --- | --- | --- | --- |
| AC-01 | A role permission/status/inheritance update atomically advances that tenant's epoch and records one pending role event. | Must | Service and mapper tests |
| AC-02 | Local L1 eviction and Redis publication occur only after commit; rollback produces neither. | Must | Transaction synchronization unit test |
| AC-03 | A tenant epoch notification evicts only that tenant on upgraded nodes and ignores duplicate/older epochs. | Must | Permission engine unit test |
| AC-04 | Missing Pub/Sub is recovered by the one-second database epoch refresh and old L1/L2 snapshots are rejected. | Must | Permission engine tests |
| AC-05 | Kafka publication uses `iam.role.changed.v1`, marks success, and retries failures without affecting role transactions. | Must | Publisher unit tests |
| AC-06 | Existing single-tenant data and rolling old nodes remain safe after migration. | Must | Migration inspection and compatibility tests |
| AC-07 | Operator documentation describes topology, monitoring, rollout, and rollback. | Should | Documentation review |

## UI/UX Design

- Applicability: Not applicable. No user-facing surface or API response changes.
- Flow and component map: unchanged.
- State matrix: unchanged; the existing permission-version refresh path remains in use.
- Responsive and accessibility behavior: not applicable.

## Technical Solution

- Chosen approach:
  - Add `oa_iam.tenant_perm_epoch`, backfilled from the legacy epoch.
  - Keep bumping legacy `perm_epoch` as a rolling-deployment fence while new code reads tenant epochs.
  - Key L1/L2 by `(tenantId, userId)` and maintain a per-tenant local epoch cache.
  - Encode tenant and epoch in the existing `CacheInvalidation.key` field so old binaries still deserialize the message and conservatively clear all L1 entries.
  - Register local eviction and Pub/Sub using transaction `afterCommit`; execute immediately only when no transaction exists.
  - Add a typed IAM outbox API and an `oa-app` Kafka publisher for `iam.role.changed.v1`.
- Alternatives rejected:
  - Kafka-only L1 invalidation: consumer groups distribute rather than broadcast and durable replay has little value for vanished JVM memory.
  - Redis Streams: introduces durable consumer state with the same fan-out mismatch while epoch polling already provides recovery.
  - Eager rebuilding of all affected users: high write amplification and difficult impact calculation across inheritance, organizations, positions, and groups.
- Modules and file map:
  - `oa-iam`: V18 migration, version mapper, cache keys/engine, invalidation service, IAM outbox mapper/API/service, role integration, focused tests.
  - `oa-app`: scheduled Kafka outbox publisher and tests/config.
  - `oa-common`: no wire-shape change; documentation clarification only if needed.
  - `docs`: architecture, database/component, runbook, and delivery artifacts.
- Contracts and data:
  - Pub/Sub key: `v1|<tenantId>|<epoch>|<eventId>|<base64url(reason)>` for `PERM_EPOCH`; old nodes ignore the key and clear globally.
  - Kafka topic: `iam.role.changed.v1`; key `<tenantId>:<roleId>`; JSON payload contains schemaVersion, eventId, eventType, tenantId, roleId, changeType, epoch, occurredAt, reason.
- Security and reliability: tenant keys prevent cross-tenant cache reuse; fail-closed snapshot checks remain; outbox is committed with role state; no secret payloads.
- Observability: publisher sent/failed counters, pending/failed outbox counts, structured invalidation logs, per-tenant cached epoch count.
- Compatibility and migration: additive V18 migration; legacy epoch table retained and advanced; old Redis keys age out naturally; message record shape unchanged.

## Implementation Sequence

1. Add tenant epoch and IAM outbox schema/API, covering AC-01 and AC-06.
2. Implement tenant-aware cache keys/epochs and after-commit invalidation, covering AC-02 through AC-04.
3. Integrate role writes and implement the OA Kafka publisher, covering AC-01 and AC-05.
4. Run review/repair, QA, documentation, and CI-equivalent checks, covering AC-07.

## Verification Plan

| AC/Risk | Test level | Case or command | Required evidence |
| --- | --- | --- | --- |
| AC-01/02 | Unit | IAM invalidation and role service tests | Ordered mapper/outbox calls; after-commit-only effects |
| AC-03/04 | Unit | Permission engine cache invalidation tests | Tenant isolation, duplicate/older event handling, epoch fallback |
| AC-05 | Unit | IAM outbox publisher tests | Success and retry state transitions |
| AC-06 | Build/schema | `mvn -pl oa-iam,oa-app -am test`; inspect V18 | Clean compile/tests and additive migration |
| AC-07 | Review | docs and CI inspection | Claims match final code |

## Documentation Plan

Update `docs/ARCHITECTURE.md`, `docs/DATABASES_AND_COMPONENTS.md`, and `docs/RUNBOOK.md`; add review, QA, status, and final delivery reports.

## CI Plan

The existing GitHub Actions backend reactor already runs `mvn -B test`, which covers the new tests. Update CI only if final implementation introduces a command not currently covered.

## Rollout And Rollback

- Rollout: pre-create `iam.role.changed.v1` in production, apply V18, deploy all instances, verify tenant epoch and outbox metrics, then exercise a role change and confirm Pub/Sub and Kafka delivery.
- Rolling safety: new writes also advance legacy global epoch; old nodes conservatively invalidate globally.
- Rollback: old binaries continue using `perm_epoch`; leave additive V18 tables in place. Disable IAM outbox publishing by configuration if Kafka delivery causes operational issues.

## Assumptions And Open Decisions

- Current `TenantContext` remains the source of tenant identity.
- Durable role events are published only by `oa-app`, where role administration endpoints and the OA Kafka producer are assembled.
- No external consumer is delivered in this scope.

## Approval

- Status: approved
- Approved scope: database epoch + Redis Pub/Sub for cache correctness/acceleration, Kafka Outbox for durable role events, tenant scoping, after-commit ordering, versioned/idempotent messages, observability and tests.
- Evidence: user message on 2026-08-25: “可以执行”.
