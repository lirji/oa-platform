# Final Code Review

## Scope and verdict

Review scope covers the complete working-tree delivery: PC and mobile clients, OpenAPI generation,
JWT/Casdoor security, WebSocket ticket authentication, local workflow behavior, compose/Nginx/image
delivery, CI and load tooling. Verdict: **pass with deployment prerequisites**. No unresolved P0/P1
code defect was found after fixes and regression testing.

## Findings fixed during review

| Severity | Finding | Resolution and evidence |
| --- | --- | --- |
| P0 | Announcement list performed a nested `hasRead` JDBC query inside the outer row callback. At pool-size concurrency every request held one connection while waiting for a second, self-deadlocking the pool. | Materialize rows first, release the connection, resolve the recipient once and bulk-fetch all page receipts. Added `AnnouncementServiceTest`; final 1000-request announcement load had 0 failures and P99 48.7 ms. |
| P1 | WebSocket max-session enforcement used get-then-increment, so concurrent handshakes could exceed the per-user cap. | Reserve the slot with CAS and release on registration failure. Added a concurrent `SessionRegistryTest`. |
| P1 | LOCAL workflow process/task IDs were sequence-only and could collide with persisted IDs after an application restart. | Prefix IDs with a per-run UUID; added restart uniqueness coverage. |
| P1 | LOCAL workflow emitted the first task before `approval_instance` had its process ID, permanently projecting a todo without instance/business metadata. | Bind the process ID first and then project current tasks; later callbacks keep their normal path. |
| P1 | Mobile E2E used a CORS-disallowed port and accepted any Toast, allowing an error Toast to look green. | Use the allowed `:5474` origin and assert successful write responses plus durable backend state. |
| P2 | Full-chain organization-tree scenario sent `depth`, while the API contract is `maxDepth`; it silently benchmarked the entire 3000-node tree. | Corrected the parameter and reran the full 1000×20 matrix. |
| P2 | WebSocket handler comments still described the removed DEV query identity path. | Updated the contract documentation to the one-time ticket flow. |

## Security review

- Four deployable services default to JWT and validate signature, issuer and audience. `X-OA-User`
  is accepted only in explicit DEV mode and cannot override JWT identity.
- Browser WebSocket authentication uses a random 256-bit, 30-second Redis ticket bound to identity,
  atomically consumed once. Access tokens are not accepted in URL query strings.
- Handshake Origin is allow-listed and `/ws` access logging is disabled to avoid ticket disclosure.
- Frontend permission rendering remains non-authoritative; backend handler permission coverage and
  fail-closed runtime checks remain the security boundary.
- Knowledge-base object authorization is enforced server-side; the UI includes explanation and denial
  states but does not replace the authorizer.

## Reliability and data review

- Workflow IDs and task projection are restart-safe for LOCAL test mode.
- Announcement read state is fetched in one bounded query and no longer risks pool recursion.
- WebSocket capacity checks are atomic under concurrent handshakes.
- Nginx resolves Docker upstreams dynamically, so rolling backend recreation does not retain stale IPs.
- Existing non-destructive mobile fixtures create uniquely tagged records and verify resulting state.

## Residual risks and prerequisites

- `workflow-platform` on `:8300` was unavailable for the final session. The existing Phase 4b remote
  gate is retained, while this delivery's mobile flow was validated with the explicit LOCAL test mode.
- Production must supply real Casdoor domains/client configuration, crypto keys, TLS and bootstrap policy;
  localhost provisioning values are development-only.
- PC initial gzip is 287.6 KB against a 300 KB budget, leaving limited growth headroom.
- Vitest emits non-failing React `act(...)` warnings; assertions pass, but future test cleanup should remove noise.
- GitHub Actions syntax and every local equivalent gate pass; hosted execution begins only after push.
- `USER_GROUP`, ABAC, JIT-to-BPMN, the SpiceDB KB adapter and real external message providers remain
  explicitly out of scope and fail closed or disabled as documented.
