# QA Report

## Automated Verification

| Verification | Result |
| --- | --- |
| `mvn -B -pl oa-iam -am test -DskipITs` | pass; 34 IAM tests |
| `mvn -B -pl oa-app -am test -DskipITs` | pass; 12-module dependency reactor, 12 app tests |
| `mvn -B test` | pass; all 16 reactor modules |
| `git diff --check` | pass |

The tests cover permission-level scope isolation, L2 codec versioning, RBAC/ABAC entry binding, OR permission
selection, strict missing-target rejection, ALL evaluation, nested contexts, alias mismatch, malicious paths,
SELECT/COUNT/UPDATE/DELETE predicate consistency, bypass metrics, JWT startup guards, data-access Golden drift and
direct-JDBC growth.

## Local Docker Verification

- Built backend images from current source and recreated `oa-app`.
- Runtime: healthy on `http://127.0.0.1:18400`.
- Effective flags: `OA_SECURITY_MODE=DEV`, `OA_WORKFLOW_MODE=LOCAL`, `OA_IAM_ENFORCE=true`,
  `OA_IAM_ABAC_ENABLED=true`, `OA_DATA_SCOPE_STRICT=true`.
- Admin Directory and Asset paths returned HTTP 200 under explicit ALL evaluation.
- `seed-user-9999` Directory count returned 4 for its ORG_AND_SUB prefix; asset list returned empty.
- Metrics on the final image after ALL and ORG_AND_SUB checks: evaluations 4, predicates 2, denied 0, missing 0,
  alias mismatch 0.
- Phase 9 USER_GROUP/ABAC smoke passed all ten gates and cleaned its temporary policy data.

## Incident During QA

The first recreated container exposed a constructor-selection failure in `DataScopeMetrics`; it was repaired and the
image rebuilt. A separate local port fallback attempted `:8400`, which was already occupied; `deploy/.env` was restored
to the established `:18400` DEV/LOCAL profile. No database cleanup or destructive reset was performed.

## QA Boundary

The existing Phase 1/2/7/8 scripts stop services and reseed or mutate shared local state, so they were not rerun.
Their backend coverage remains in the full reactor, while targeted read-only runtime checks exercised the changed SQL paths.
