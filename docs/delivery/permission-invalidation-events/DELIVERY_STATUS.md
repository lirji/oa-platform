# Delivery Status

## Goal

Deliver tenant-scoped, after-commit permission cache invalidation and durable role-change events without moving authorization correctness onto Kafka.

## State

- Phase: 9 - delivery complete
- Status: complete
- Last updated: 2026-08-25

## Completed

- Phases 1-4: repository discovery, product design, non-applicable UI determination, technical design, and adversarial compatibility pass.
- Gate A: approved by the user's “可以执行” message.
- Slice 1 / AC-01, AC-06: added V18 tenant epoch and IAM event outbox with a legacy global-epoch compatibility fence.
- Slice 2 / AC-02, AC-03, AC-04: added tenant-aware L1/L2 keys, per-tenant epoch cache, versioned Pub/Sub payload, after-commit delivery, and tenant propagation for shadow verification.
- Slice 3 / AC-01, AC-05: role writes append `iam.role.changed.v1`; `oa-app` leases and publishes events through an explicitly qualified OA Kafka producer.
- Focused clean reactor test: IAM 59 tests and oa-app 14 tests passed; Phase0 pinning probe remained at zero events.
- V18 and mapper SQL were executed inside a PostgreSQL transaction and rolled back successfully.
- Adversarial review findings were repaired: mixed-version epoch fencing, outbox lease expiry exposure, and Kafka-template ambiguity.
- Full 16-module `mvn clean package` reactor passed; IAM now has 60 tests and oa-app has 14 tests.
- An isolated packaged-app run applied all 35 Flyway migrations, published a synthetic IAM outbox event to Kafka, exposed the expected metrics, and was fully cleaned up.
- Architecture, authorization, cache, database, deployment, and runbook documentation are synchronized.

## Changed Files

- `docs/delivery/permission-invalidation-events/DELIVERY_PLAN.md` - approved design and acceptance matrix.
- `docs/delivery/permission-invalidation-events/DELIVERY_STATUS.md` - workflow state.
- `oa-iam/src/main/resources/db/migration/V18__tenant_permission_epoch_outbox.sql` - additive tenant epoch and outbox schema.
- `oa-iam/src/main/java/com/lrj/oa/iam/**` - tenant cache/invalidation, outbox API and role integration.
- `oa-flow/src/main/java/com/lrj/oa/flow/infrastructure/workflow/OaEventKafkaConfig.java` - explicit OA event-bus producer.
- `oa-app/src/main/java/com/lrj/oa/app/iam/IamRoleEventPublisher.java` - IAM outbox Kafka delivery.
- Focused IAM and app test files - transaction, tenant, ordering, retry, and compatibility coverage.

## Verification Log

| Command or check | Result | Notes |
| --- | --- | --- |
| Repository/CI/dependency inspection | pass | Existing OA Kafka, Redis Pub/Sub, Flyway, and backend CI confirmed. |
| `mvn -pl oa-iam,oa-app -am clean test -DskipTests=false` | pass | IAM 59, oa-app 14, dependency-module tests all passed. |
| `mvn -pl oa-iam,oa-app -am test` | pass | IAM 60 after compatibility-fence coverage; oa-app 14. |
| `mvn clean package` | pass | Full 16-module reactor and all tests passed. |
| PostgreSQL V18 + mapper statements in `BEGIN ... ROLLBACK` | pass | DDL, legacy/tenant bump, insert, lease and returning mappings parsed/executed. |
| Isolated packaged-app QA | pass | Health UP; 35 migrations applied; outbox `SENT`; Kafka payload consumed. |
| Actuator outbox metrics | pass | sent=1, failed=0, pending=0, dead=0. |
| `docker compose -p oa-platform -f deploy/docker-compose.yml --profile apps config -q` | pass | Compose configuration is valid. |
| `git diff --check` | pass | No whitespace errors. |

## Decisions And Deviations

- Preserve the three-field `CacheInvalidation` wire shape for rolling compatibility; tenant/epoch use a versioned `key` payload.
- Continue advancing legacy global epoch while new nodes read tenant epoch.
- Lease IAM outbox events one at a time to prevent later rows in a sequential batch from expiring before send.
- Use an explicitly named `oaEventKafkaTemplate` so IAM/flow business events cannot be sent to workflow-platform Kafka.

## Blockers And Residual Risks

- No delivery blockers.
- Kafka delivery is intentionally at-least-once: a crash after broker acknowledgement but before `SENT` persistence may duplicate an event. Consumers must deduplicate by `eventId`.
- Prometheus export is not exposed by the current local application configuration; the same meters were verified through `/actuator/metrics`.
- Production must pre-create `iam.role.changed.v1`; only local Compose may rely on automatic topic creation.

## Next Action

Roll out using the two-stage legacy-fence procedure in `docs/RUNBOOK.md`; production deployment itself remains outside this delivery.
