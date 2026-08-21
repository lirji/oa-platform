# Final QA Report

## Result

All acceptance criteria AC-01 through AC-10 are satisfied in the local delivery environment.
Static gates, unit tests, real-browser flows, image/Nginx integration, JWT/Casdoor/WebSocket security
and the full-chain load matrix pass. Production rollout itself was not requested and remains subject to
the prerequisites in the runbook.

## Acceptance evidence

| AC | Result | Evidence |
| --- | --- | --- |
| AC-01 PC A1–A10 | Pass | PC Vitest 105 tests; Playwright 9 specs / 47 tests; Phase 3 14/14. |
| AC-02 knowledge UI | Pass | `/kb` list/search/detail/explain/403 E2E and route-level permission guard. |
| AC-03 OpenAPI TS | Pass | 88-path snapshot, generated declaration, `pnpm gen:api:check`. |
| AC-04 compose/image/Nginx | Pass | Compose config, current-source images, PC SPA/WS/four upstream proxies. |
| AC-05 GitHub Actions | Pass | Workflow YAML parses; local Maven and both frontend-equivalent jobs pass. |
| AC-06 JWT/Casdoor | Pass | Phase 4 13/13: real token claims, four-service matrix, PKCE/callback/refresh and large header. |
| AC-07 safe browser WS | Pass | One-time ticket success, replay rejection, DEV user query rejection, Origin and log checks. |
| AC-08 mobile | Pass | 4 unit tests; real punch/todo/directory/announcement E2E; 390px and 320px; Phase 5 13/13. |
| AC-09 documentation | Pass | README, architecture, API, runbook, authoritative progress and delivery reports synchronized. |
| AC-10 review/QA/load | Pass | Full Maven/frontend gates, evidence-based review and 6-scenario load matrix pass. |

## Build and functional gates

| Gate | Final result |
| --- | --- |
| `mvn -B test` | Pass across 16 reactor modules, including new workflow, announcement and WebSocket concurrency tests. |
| PC `gen:api:check`, test, TypeScript/build, size | Pass; 105 unit tests; initial gzip 287.6 / 300 KB. |
| PC Playwright | Pass; 47/47 in a single worker because permission epoch mutation is shared state. |
| Mobile test, TypeScript/build, size | Pass; 4 unit tests; initial gzip 174.0 / 220 KB. |
| Phase 3 PC | Pass 14/14. |
| Phase 4 JWT/WS | Pass 13/13. |
| Phase 5 Mobile | Pass 13/13. |
| Compose and CI syntax | Pass. |
| `git diff --check` | Pass. |

## Full-chain load evidence

Command:

```bash
OA_NOTIFY_BASE=http://127.0.0.1:8401 OA_ADMIN=seed-user-1 \
  java deploy/scripts/FullChainLoadTest.java http://127.0.0.1:18400 1000 20
```

| Scenario | Success / failure | P50 | P95 | P99 | Budget |
| --- | ---: | ---: | ---: | ---: | ---: |
| Workbench todos | 1000 / 0 | 12.3 ms | 39.7 ms | 63.3 ms | 200 ms |
| My permissions | 1000 / 0 | 2.7 ms | 4.8 ms | 6.2 ms | 100 ms |
| Directory page | 1000 / 0 | 43.5 ms | 122.2 ms | 185.9 ms | 200 ms |
| Organization tree (`maxDepth=3`) | 1000 / 0 | 3.2 ms | 6.9 ms | 12.1 ms | 200 ms |
| Executive overview | 1000 / 0 | 34.5 ms | 149.4 ms | 205.2 ms | 500 ms |
| Announcement list | 1000 / 0 | 8.0 ms | 22.4 ms | 48.7 ms | 200 ms |

The first load attempt exposed the announcement connection-pool self-deadlock. A later run exposed an
incorrect organization-tree query parameter. Both defects were corrected before the table above was
recorded; failed exploratory numbers are not treated as the release baseline.

## Environment notes

- JDK 21 and system Maven are required because workflow/auth artifacts use the configured local repository.
- Integration gates use localhost compose/processes because Testcontainers is unavailable on this host.
- Traditional backend phase scripts run with explicit DEV mode; production-equivalent Phase 4 runs JWT.
- No production deployment was performed; commit and push are repository-owner release actions outside the QA cases.
