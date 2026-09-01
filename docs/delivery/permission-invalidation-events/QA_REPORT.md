# QA Report

## Test Target

- Packaged `oa-app` on isolated port `18410`.
- Dedicated temporary PostgreSQL database `oa_perm_event_qa_20260825_1608`.
- Existing local Redis and Kafka infrastructure only; no existing application instance or business database was changed.
- The temporary application was stopped gracefully and the QA-only database was dropped after verification.

## Acceptance Results

| Acceptance criterion | Evidence | Result |
| --- | --- | --- |
| AC-01 tenant epoch and role event are atomic | Service tests verify mapper/outbox ordering; V18 and mapper SQL executed successfully in a rollback transaction. | Pass |
| AC-02 external invalidation is after commit | Tests assert no eviction/publication before commit and none after rollback. | Pass |
| AC-03 tenant-only eviction and ordering | Engine tests cover tenant isolation and duplicate/older epoch rejection. | Pass |
| AC-04 missed Pub/Sub recovery | Engine tests cover database epoch refresh, stale L1/L2 rejection, legacy-only writer fencing, and old-message fallback. | Pass |
| AC-05 Kafka success and retry | Publisher tests cover success and broker failure; isolated QA changed the row to `SENT` and consumed its unique payload from `iam.role.changed.v1`. | Pass |
| AC-06 migration and rolling compatibility | Additive V18 migration applied on a fresh database as part of all 35 migrations; compatibility tests passed. | Pass |
| AC-07 operational documentation | Architecture, authorization, cache, database, compose, and runbook documentation reviewed against final code. | Pass |

## Integration Evidence

- `/actuator/health`: `UP` for application, PostgreSQL, and Redis.
- Flyway: all 35 migrations applied, including `18 - tenant permission epoch outbox`.
- Synthetic event ID: `00000000-0000-0000-0000-000000000018`.
- Database result: `status=SENT`, `attempts=0`, no error.
- Kafka result: payload with `reason=codex-qa-marker-20260825-1608` consumed from `iam.role.changed.v1`.
- Actuator meters: sent `1`, failed `0`, pending `0`, dead `0`.
- Prometheus text export was not enabled in the local endpoint exposure; meter registration and values were verified through `/actuator/metrics`.

## Build And Static Checks

| Check | Result |
| --- | --- |
| Focused clean IAM/app reactor tests | Pass: IAM 59, oa-app 14 |
| Focused test rerun after compatibility test | Pass: IAM 60, oa-app 14 |
| Full `mvn clean package` (16 modules) | Pass |
| Compose apps-profile configuration | Pass |
| `git diff --check` | Pass |

## Cleanup

The packaged QA process was terminated gracefully. The dedicated temporary database was deleted after confirming zero active connections; it contained only synthetic QA data and is not recoverable. The locally auto-created `iam.role.changed.v1` topic, containing only the synthetic event, was also deleted. Existing infrastructure containers were not restarted or reconfigured.

## Verdict

Pass. All must-have acceptance criteria are supported by automated or isolated integration evidence.
