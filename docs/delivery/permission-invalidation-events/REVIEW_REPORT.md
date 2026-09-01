# Review Report

## Scope

Adversarial review of the permission invalidation and IAM role-event changes against the approved delivery plan. The review covered transaction ordering, tenant isolation, rolling compatibility, Redis Pub/Sub loss, outbox leasing, Kafka wiring, observability, tests, and operational documentation.

## Confirmed Findings And Repairs

| Severity | Finding | Repair | Verification |
| --- | --- | --- | --- |
| High | During a rolling deployment, an old writer could advance only the legacy global epoch. A new reader that missed Pub/Sub could otherwise continue accepting its tenant epoch. | New nodes keep a default-enabled legacy epoch fence and compare both values until every writer is V18-aware. | Compatibility unit test exercises old notifications and legacy-only advancement. |
| Medium | Leasing a batch and sending rows sequentially allowed later leases to expire before their send completed, increasing duplicate publication risk. | The publisher claims and sends one event at a time while retaining bounded polling. | Publisher success/retry tests and isolated Kafka QA. |
| Medium | Multiple Kafka domains could make an unqualified `KafkaTemplate` publish OA business events through workflow-platform configuration. | Added an explicit `oaEventKafkaTemplate` from `spring.kafka` and qualified both OA event publishers. | Application context/tests and real broker publication. |

## Reviewed And Accepted Behaviors

- Redis Pub/Sub remains best-effort. A missed message is recovered by authoritative tenant-epoch polling; it is not a correctness boundary.
- L2 entries are not bulk-deleted for a tenant. Their embedded epoch makes stale values unusable, and normal expiry/replacement reclaims them without a Redis key scan.
- Kafka publication is at-least-once. A crash between broker acknowledgement and the outbox `SENT` update can cause a duplicate; `eventId` is the consumer idempotency key.
- The existing three-field Redis invalidation record is retained. New tenant data is version-encoded in `key`, while old binaries conservatively clear their whole L1 cache.
- Continuing to advance the legacy global epoch during migration causes broader invalidation only while the fence is enabled; this is the intentional rolling-safety tradeoff.

## Security And Data Isolation

- L1 and L2 keys include tenant ID and user ID.
- Tenant epoch lookup and invalidation are tenant-scoped.
- External eviction/publication is registered after transaction commit; rollback emits no invalidation.
- The outbox payload contains identifiers and change metadata, not credentials or permission snapshot contents.

## Verdict

Pass. All confirmed high/medium findings were repaired and covered by tests or isolated integration evidence. No blocking finding remains.
