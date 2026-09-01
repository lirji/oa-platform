# Delivery Report

## Outcome

Permission snapshot invalidation is now tenant-scoped and transaction-safe. PostgreSQL tenant epochs remain the correctness source, Redis Pub/Sub accelerates JVM L1 eviction, and durable role-change events are delivered asynchronously through an IAM outbox to the OA Kafka bus.

## Delivered

- Tenant-scoped permission epoch table, backfilled from the existing global epoch.
- Tenant-aware L1 and L2 cache keys, per-tenant local epoch state, stale-notification rejection, and database fallback.
- After-commit local eviction and Redis publication for role, grant, organization, and bootstrap changes.
- Versioned Redis payload that remains deserializable by old instances.
- Legacy global-epoch compatibility fence for safe rolling upgrade and rollback.
- Durable `iam.role.changed.v1` outbox records committed with role changes.
- Explicit OA Kafka producer and scheduled lease/send/retry publisher.
- Sent/failed counters and pending/dead gauges.
- Focused transaction, isolation, ordering, compatibility, success, and retry tests.
- Architecture, authorization, database, cache, deployment, and operations documentation.

## Operational Contract

- Kafka is not used to decide whether a permission snapshot is valid.
- Redis Pub/Sub loss can delay L1 eviction only until the next database epoch refresh.
- Kafka consumers must process `eventId` idempotently because delivery is at-least-once.
- During rollout, keep `OA_IAM_LEGACY_EPOCH_FENCE_ENABLED=true` on all upgraded services. Disable it only after all permission-writing nodes are V18+; re-enable it before rolling back to an older writer.
- The IAM outbox publisher can be disabled independently if Kafka has an operational incident; role writes and permission correctness continue to work.

## Verification Summary

- Focused IAM/app suites passed.
- Full 16-module Maven clean package passed.
- V18 and mapper SQL passed PostgreSQL execution checks.
- A fresh isolated database applied all migrations and a packaged application completed Outbox → Kafka delivery with healthy metrics.
- Compose configuration and whitespace checks passed.

Detailed evidence is in `QA_REPORT.md`; review findings and repairs are in `REVIEW_REPORT.md`.

## Deployment Status

Implementation and release-readiness work are complete. No existing environment was deployed or restarted. Production rollout remains an explicit operator action following `docs/RUNBOOK.md`.
