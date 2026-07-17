# Issue #195 Member List Before Scenario

Status: **production-code-ready/after-not-measured**

This directory records the before-only local Docker evidence for Issue #195. PM-controlled execution G completed all eleven measured cases with zero failures, but the shared-stack boundary cannot prove absence of transient external load. G is therefore a conditional shared-stack before baseline only: it is not automatically adoptable, is not an improvement claim, and must not be counted as a performance achievement.

The user subsequently authorized the two G-supported production N+1 removals. The code and focused regressions are complete, but no after Docker/DB/HTTP/k6 load was run in this development session. G remains unchanged and the implementation has no measured improvement claim.

Previous authoring checkpoint: `scenario-ready/not-measured` (superseded by the completed G checkpoint above).

## Actual-before Checkpoint

- Provisioning succeeded for `PERF_1000_20260716_195_A`: exactly 1,000 ACTIVE service-role `USER` rows were verified and its success manifest is preserved.
- Fixture preparation succeeded for `ISSUE195_BEFORE_20260716_A`: 25 campuses, 1,000 explicit membership additions (1,025 total new memberships including automatic creators), and 101 duties were verified and its success manifest is preserved.
- `EXEC195_BEFORE_20260716_A` was rejected during initialization of the first warmup process. Installed k6 attempted to parse `scenario-contract.json` as a JavaScript module and exited `107` with `Unexpected token :`. Warmup and measured HTTP request counts were both exactly `0`; no baseline metric was produced.
- Execution A and its report directory are non-reusable.
- `EXEC195_BEFORE_20260716_B` completed only the first `admin_users/first_page` warmup: 142 HTTP requests, checks 1,278/1,278, HTTP/custom failure 0, p50 173.60 ms, p95 435.84 ms, p99 667.86 ms, and max 1.22 s. Measured HTTP requests were exactly 0. The warmup summary validator rejected k6 v2's actual zero-failure Rate shape `{value:0, passes:0, fails:142}` because the old validator expected a missing `rate` field. These diagnostic warmup values are rejected evidence, not a baseline or performance achievement.
- Execution B and its report directory are non-reusable and remain read-only.
- `EXEC195_BEFORE_20260716_C` completed only the first `admin_users/first_page` warmup: 530 HTTP requests, checks 4,770/4,770, HTTP/custom failure 0, p50 52.47 ms, p95 76.32 ms, p99 105.12 ms, and max 280.34 ms. Measured HTTP requests were exactly 0. After the measured-before DB evidence, the first resource snapshot rejected Docker's rounded decimal binary display (`766.3MiB`, `268.3MiB`, and `19.55MiB`) because the old parser required the scaled value itself to be an integer. These diagnostic warmup values are rejected evidence, not a baseline or performance achievement.
- Execution C and its report directory are non-reusable and remain read-only. The preserved C summary also demonstrates why future k6 `setup()` data must never contain the runtime access token; the historical file is not modified, copied, or treated as adoptable evidence.
- `EXEC195_BEFORE_20260716_D` completed the first `admin_users/first_page` warmup with 542 requests and the measured phase with 7,197 requests, checks 64,773/64,773, failure 0, p50 158.55 ms, p95 234.07 ms, p99 301.20 ms, max 686.75 ms, and throughput 59.902795/s. The after-table-counter artifact then contained psql's `Output format is csv.` status line and failed at `measured-evidence-after`. All D values are rejected diagnostic evidence only, not a baseline or performance achievement.
- Execution D and its report directory are non-reusable and remain read-only.
- `EXEC195_BEFORE_20260716_E` completed the first `admin_users/first_page` warmup with 334 requests and the measured phase with 4,200 requests, checks 37,800/37,800, failure 0, p50 226.10 ms, p95 630.94 ms, p99 943.16 ms, max 1.38 s, and throughput 34.858498/s. The DB-wide `xact_commit` delta was 8,427 while the request-derived transactions plus one observer commit were 8,401, so the old exact-equality gate rejected the case at `measured-evidence-after`. A separate no-load 120-second PM observation recorded raw DB-wide commit delta 18, of which one was the exact observer commit and 17 remained background/unattributed; rollback stayed zero. The deployed runtime had `FAITHLOG_SCHEDULER_ENABLED=false`, so these commits are not attributed to FaithLog schedulers. All E values are rejected diagnostic evidence only, not a baseline or performance achievement.
- Execution E and its report directory are non-reusable and remain read-only. The preserved B/C/D/E reports are not modified, cleaned, copied, printed, or used as an automatic-adoption source by this correction.
- `EXEC195_BEFORE_20260716_F` completed `admin_users/first_page` and reached the first rejection at `admin_users/middle_page` `measured-evidence-after`. The completed warmup had 837 requests with zero failure; the measured phase had 12,342 requests, all checks passed, and zero failure. Measured DB-wide commits were `167243 -> 191857` (delta 24,614), rollback stayed zero, while the old hard-coded `requestCount × 2 + observer` gate required 24,685 and rejected a shortfall of 71. The immediately-before same-duration idle control recorded raw commit delta 40, background/unattributed 39, and rollback zero. These values are diagnostic-only and are not a baseline, performance achievement, or automatic-adoption source.
- F's after DB snapshot was captured 6.304 seconds after the measured-end marker, later than the measured maximum HTTP duration of 4.117026 seconds. The runner therefore was not merely reading before the longest HTTP request finished. PostgreSQL cumulative statistics still publish per backend and the DB-wide snapshot cannot force another backend's flush or attribute commits to request phases, so adding an arbitrary settle delay cannot prove the source-derived two transactions for every request. The static current-develop source boundary remains two transactions per request, but the DB-wide evidence gate now uses only the conservative `requestCount + one before-observer commit` minimum. Everything above that minimum remains source-unattributed and `conditional-not-adoptable`; idle-control subtraction, proportional estimation, tolerance, and automatic adoption remain prohibited.
- Execution F and its entire report directory are non-reusable and remain read-only. The actual-fixture regression reads F in place and verifies every file's content, SHA-256, size, and nanosecond mtime are unchanged; it writes validator output only to a temporary directory. The F temporary account was restored to service role `USER`, and PM confirmed the performance lock/process cleanup.
- `EXEC195_BEFORE_20260716_G` completed measured adoption for all 11 cases and every measured `failureRate` was `0`. No intermediate case was rejected. The only `first-rejection.json` is the runner's designed `final-classification` exit `2`: `status=conditional-not-adoptable`, `automaticAdoption=false`, because boundary evidence and a cooperative lock cannot prove the absence of transient external shared-stack load. G is a conditional shared-stack before baseline for an explicitly reviewed comparison only; it is not an automatic-adoption source or performance achievement.
- G used the preserved `PERF_1000_20260716_195_A` / `ISSUE195_BEFORE_20260716_A` dataset and fixture. PM confirmed one temporary account was restored to service role `USER` and that no performance lock or runner process remained. G and its report are complete and non-reusable; provisioning, fixture preparation, or G must not be rerun or cleaned up.
- Before F, PM independently ran a 2-second read-only control smoke after the E tooling correction. It produced `status=supporting-only`, `automaticAdoption=false`, `configuredDuration=2s`, raw control commit delta 2, background/unattributed delta 1, rollback delta 0, and `backgroundSubtractionApplied=false`. This is smoke diagnostic evidence only, not a baseline or performance achievement. The separate Issue #208 common audit gate was completed before F.

### G conditional shared-stack measurements

| Scenario/case | Requests | Throughput (req/s) | p50 (ms) | p95 (ms) | p99 (ms) | Max (ms) |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| `admin_users/first_page` | 12,184 | 101.448553 | 94.2745 | 143.09245 | 184.99649 | 358.14 |
| `admin_users/middle_page` | 11,827 | 98.474679 | 96.575 | 148.7566 | 197.0886 | 378.738 |
| `admin_users/large_page` | 5,068 | 42.156241 | 231.0955 | 339.36645 | 414.71317 | 629.44 |
| `admin_users/role_filter` | 11,367 | 94.611608 | 102.196 | 151.342 | 190.66136 | 367.141 |
| `admin_users/search_filter` | 12,131 | 101.026384 | 94.949 | 142.469 | 182.5931 | 420.767 |
| `admin_campuses/first_page` | 79,092 | 658.991255 | 13.3865 | 23.76245 | 37.35135 | 283.972 |
| `admin_campuses/middle_page` | 223,554 | 1862.811422 | 4.663 | 8.567 | 13.77794 | 132.495 |
| `admin_campuses/large_page` | 75,773 | 631.341399 | 14.189 | 24.402 | 36.66448 | 182.333 |
| `admin_campuses/active_search` | 152,317 | 1269.176371 | 6.905 | 12.0332 | 18.45352 | 148.245 |
| `campus_members/full_list` | 5,160 | 42.909684 | 216.426 | 338.1192 | 435.98053 | 904.414 |
| `duty_assignments/full_list` | 133,099 | 1108.765775 | 7.472 | 14.022 | 22.06626 | 128.569 |

The two evidence-backed optimization candidates are `admin_users/large_page` and `campus_members/full_list`: both are about 42 req/s with p95 near 339 ms, and both have a current-source N+1 lookup path. This observation selects investigation scope only. It is not a user-approved target, regression threshold, causal attribution percentage, or improvement result.

## API Inventory

The inventory is taken from the current production controllers and Spring REST Docs.

| Scenario | Exact API | Supported query contract | Required authorization | Production order |
| --- | --- | --- | --- | --- |
| `admin_users` | `GET /api/v1/admin/users` | `name`, `email`, `userId`, `role`, `page`, `size`, `sort` | active service `ADMIN` | requested stable sort; cases use `id,asc` |
| `admin_campuses` | `GET /api/v1/admin/campuses` | `name`, `region`, `status`, `page`, `size`, `sort` | active service `ADMIN` | requested stable sort; cases use `id,asc` |
| `campus_members` | `GET /api/v1/admin/campuses/{campusId}/members` | none | active service `ADMIN`, or ACTIVE campus `MINISTER`/`ELDER`/`CAMPUS_LEADER` | `campus_members.id asc`; ACTIVE only |
| `duty_assignments` | `GET /api/v1/admin/campuses/{campusId}/duty-assignments` | optional `staleOnly` (baseline fixes `staleOnly=false`) | active service `ADMIN`, or ACTIVE campus `MINISTER`/`ELDER`/`CAMPUS_LEADER` | `campus_duty_assignments.id asc`; active assignment with ACTIVE membership only |

The member and duty endpoints are intentionally not given invented pagination. Role/search/page combinations apply to the service-admin user API; status/search/page combinations apply to the service-admin campus API. The two pageable admin APIs default to `size=20`, reject sizes above `100`, and the scenario separates `size=20` from the approved maximum `size=100`. `includeArchived` is not supported by any Issue #195 endpoint; the Issue #201 archive opt-in contract belongs to billing and poll lists and is not sent here.

## G Source Boundary And Current Implementation

- #200 authorization remains active service `ADMIN` or an ACTIVE campus `MINISTER`/`ELDER`/`CAMPUS_LEADER`. At the G source commit, the duty list already performed a bulk user lookup while the campus-member list still performed a per-member user lookup. The current implementation now bulk-loads campus-member users as well; this does not retroactively change G or claim a pristine pre-bulk duty baseline.
- #202 enables RLS but `FORCE ROW LEVEL SECURITY` is not used. G verified the bound PostgreSQL runtime identity and continuity required by this scenario; it did not isolate RLS as a performance factor, so no RLS-specific causal result is claimed.
- #206 adds a billing paging tie-break and is not applicable to these member/campus APIs. Admin cases request explicit `id,asc`; campus member and duty repositories return their own IDs in ascending order.

## Minimal Production Optimization (Implemented, After Not Measured)

The user authorized only the two G cases that combine the slowest observed throughput/latency with a directly visible source N+1 path. `admin_campuses` remains excluded because it has no equivalent enrichment loop. `duty_assignments` remains excluded because the G source already bulk-loaded users.

### Implemented files and query shape

1. Admin user page enrichment:
   - `AdminUserManagementService.java`, `AdminCampusMemberRepositoryPort.java`, and `CampusMemberRepository.java` now use the internal `AdminUserCampusRow.java` read projection. Controllers, request/response DTO fields, entities, and `getUser`/role-mutation locking semantics are unchanged.
   - The service materializes the page content, collects its user IDs, and issues one bulk membership-to-campus projection query. The query includes inactive and active memberships exactly as before, uses a left join with explicit missing-campus detection, and orders by `member.userId ASC, member.id ASC`.
   - Group rows by user ID, then map the original page content in its original pageable order. Within each user, keep membership ID ascending. A missing campus must still raise `CAMPUS_NOT_FOUND`; it must not be silently dropped by an inner join.
   - This removes the page-sized per-user membership reads and per-membership campus reads while retaining the existing admin authorization check before the search and the same read-only transaction.
2. Campus full member list enrichment:
   - `CampusMemberManagementService.java` retains the existing manager authorization and ACTIVE-membership/ID-ascending query, then calls the already implemented `CampusAccessPolicy.getUsers(...)` once. `UserRepository.findCampusUsersByIds(...)` overrides the port default with one `findAllById` bulk read.
   - Map users by ID while iterating the original membership list, so membership ID order and response cardinality remain unchanged. Missing users still raise `CAMPUS_MEMBER_NOT_FOUND`; inactive users remain representable exactly as in the current per-member lookup because this list gates on membership status, not user active state.

The expected database round-trip change is deterministic and should be proved in tests, not inferred from G latency: admin page enrichment becomes one bulk enrichment query after the existing authorization/page/count work, and campus member user enrichment becomes one bulk user query after the existing authorization/membership work. The JWT filter transaction and endpoint service transaction boundaries are not changed.

### Compatibility and schema boundary

- API/frontend: no endpoint, query parameter, page metadata, JSON field, value meaning, HTTP status, or ErrorCode changes. No frontend client, type, state, or UI change is required.
- Authorization/concurrency: keep authorization before data enrichment, preserve `@Transactional(readOnly=true)`, introduce no write or lock, and leave role mutation/deletion lock order untouched. Page order, membership ID ascending order, ACTIVE-only campus membership filtering, and all existing missing-resource behavior remain exact.
- Flyway/index: no Flyway migration or new index is required for the first N+1 removal. The existing `uk_campus_members_campus_user (campus_id, user_id)` supports campus-scoped membership lookup by its leftmost column, while users and campuses are bulk-read by primary key. The proposed admin `user_id IN (...)` projection may still scan `campus_members` because the current unique key does not lead with `user_id`; do not add an index speculatively. Only a separately approved post-change query plan showing that scan dominates may justify a later `campus_members (user_id, id)` index and Flyway change.

### TDD evidence

1. The focused RED used a four-user page with two shared campuses and observed 8 prepared statements; the GREEN path uses exactly 3 while preserving page number/size/total metadata, requested user order, `campusCount`, campus values, and membership ID order.
2. The focused campus RED used four ACTIVE members plus one inactive membership and observed 6 prepared statements; the GREEN path uses exactly 3 while returning only ACTIVE memberships in ID order with unchanged names/status.
3. Existing admin/campus service, controller, and REST Docs regressions remain required before after measurement. Missing-campus and missing-user ErrorCodes are preserved by explicit projection validation and the existing strict bulk lookup policy.

### Expected after verification

In a later PM-controlled slot, use the same workload/cardinality/runtime inputs and a fresh PM-assigned after execution ID; never reuse G. First require the deterministic query-count reductions and all functional tests to pass. Then compare all eleven zero-failure case metrics with G, focusing on `admin_users/large_page` and `campus_members/full_list` while checking the other nine for regression. No numerical latency/throughput acceptance threshold is approved here, so none may be invented. Shared-stack after evidence remains `conditional-not-adoptable` and cannot become an automatic performance achievement unless the user separately approves an exclusive/continuous provenance contract.

## Fresh Dataset Provisioning

Issue #195 cannot reuse the Issue #192 dataset: that namespace contains 999 ACTIVE `USER` rows plus one service `ADMIN`, while this scenario requires exactly 1,000 ACTIVE service-role `USER` rows and excludes the runtime admin. `provision-dataset.mjs` therefore reserves a fresh report namespace first, takes the actual Compose-project common performance lock, exact-checks full app/PostgreSQL/Redis container IDs, images, `StartedAt`, labels and the app published endpoint before and after the lock, then creates exactly 1,000 users through `POST /api/v1/auth/signup`. It re-reads all 1,000 rows through the admin list/detail APIs and accepts only unique, ACTIVE `USER` rows whose name and email both contain the dataset ID. It performs no update, role change, delete, cleanup, or retry of an occupied ID.

The completed G actual identifiers are:

- `PERF_DATASET_ID=PERF_1000_20260716_195_A`
- `PERF_FIXTURE_RUN_ID=ISSUE195_BEFORE_20260716_A`
- `PERF_EXECUTION_RUN_ID=EXEC195_BEFORE_20260716_G` (complete, conditional, and non-reusable; `..._A` through `..._F` are rejected and non-reusable)
- report reservation: `performance/k6/issue-195/reports/PERF_1000_20260716_195_A/_provisioning/`

The G source input was exactly `6796ed146244d8f3f5b5dd7048ebe16865084a97`. Operational provenance was the clean detached checkout `/private/tmp/FaithLog-perf-206-deploy` at that commit and an app image created after the checkout. The G-bound identities were app container `a7df78b330f457a7fd60a9531362d0f1f063ae7aa6cae5f2d996eb8cb51fe79d` / image `sha256:8e0f8d85d697a7d34aabf3703ddb27b4f1af326dec4f7c35556986303b0b816c`, PostgreSQL container `81aa74ca1b491b45eb691b3d65de9e42eb47ef64a6bcb961d0b627b030139ae9` / image `sha256:48d29282d2b43c402465c28f8572021b59aaf43574056faaad2fd7bb85ffdd4e`, and Redis container `4109f6525948d12d1e5377fb6160c8955f6c3fcd7816e02786b2dd8031e23de9` / image `sha256:80dd823f4d2bf93dd5e418a0ae2817319a1ba279953e234082e54a5a18306223`. The app image had no OCI revision label or `git.properties`, so image-alone cryptographic source proof remained unavailable; this approved operational bind is a G limitation, not a new blocker. Any approved after run must establish a new exact source/image/container/target bind rather than reusing these identities as current facts.

All values below are runtime-required; none has a script fallback. Passwords remain in memory/environment only and must not be placed in shell history, argv, reports, or committed files.

```bash
BASE_URL=http://127.0.0.1:28080 \
PERF_ADMIN_EMAIL=<runtime-only-admin-email> \
PERF_ADMIN_PASSWORD=<runtime-only-admin-password> \
PERF_DATASET_MEMBER_PASSWORD=<runtime-only-member-password> \
PERF_DATASET_ID=PERF_1000_20260716_195_A \
PERF_SOURCE_COMMIT=6796ed146244d8f3f5b5dd7048ebe16865084a97 \
PERF_REPORT_ROOT=/Users/josephuk77/.codex/worktrees/73c5/FaithLog/performance/k6/issue-195/reports \
APP_CONTAINER_ID=<reverified-full-app-container-id> \
EXPECTED_APP_COMPOSE_SERVICE=<reverified-app-service> \
EXPECTED_APP_IMAGE_ID=<reverified-full-app-image-id> \
POSTGRES_CONTAINER_ID=<reverified-full-postgres-container-id> \
EXPECTED_POSTGRES_COMPOSE_SERVICE=<reverified-postgres-service> \
EXPECTED_POSTGRES_IMAGE_ID=<reverified-full-postgres-image-id> \
REDIS_CONTAINER_ID=<reverified-full-redis-container-id> \
EXPECTED_REDIS_COMPOSE_SERVICE=<reverified-redis-service> \
EXPECTED_REDIS_IMAGE_ID=<reverified-full-redis-image-id> \
EXPECTED_ACTIVE_MEMBERS=1000 \
TOKEN_SAFETY_MARGIN_SECONDS=120 \
node performance/k6/issue-195/provision-dataset.mjs
```

The preflight token is not reused after the potentially long signup loop. Immediately after the 1,000th signup, provisioning logs in again, decodes the in-memory JWT `exp`, and requires at least runtime `TOKEN_SAFETY_MARGIN_SECONDS` remaining before verification. It rechecks before every verification page/detail request (therefore at least every 100-user chunk) and logs in again only when the remaining lifetime falls below the margin. Token and expiry values are never persisted; the manifest records only `verificationTokenRefreshCount`.

Any failure writes the first secret-free `first-rejection.json`, marks the dataset ID non-reusable, preserves every additive row, and releases the common lock. Post-signup login failure is `verification-login`; insufficient or malformed fresh-token lifetime is `verification-token`. There is no automatic cleanup. A partial or colliding attempt requires brand-new dataset, fixture, and execution IDs.

## Fixture Contract

`PERF_DATASET_ID` identifies the freshly provisioned PERFORMANCE user dataset. `PERF_FIXTURE_RUN_ID` identifies only the additive Issue #195 relationship fixture. They are never interchangeable. Preparation stops unless the dataset search resolves to exactly 1,000 users, every user is ACTIVE with service role `USER`, every name/email contains `datasetId`, and the runtime service admin is outside that dataset. Before login or mutation, fixture preparation requires an absolute `PERF_REPORT_ROOT`, exact numeric-loopback `BASE_URL` (`127.0.0.1` or `[::1]`), plus app/PostgreSQL/Redis container identities, compares their actual Compose services and images with the runtime-approved values, and requires the URL origin to match exactly one published app-container port. It then holds the same `/tmp/faithlog-performance-{composeProject}.lock` used by measurement.

The preparation script requires a fresh `ISSUE195_*` run ID and exact runtime-approved source commit, app/PostgreSQL/Redis Compose services, immutable image IDs, fixture cardinalities, and `TOKEN_SAFETY_MARGIN_SECONDS`. Before Docker inspection or any API call, it atomically reserves `$PERF_REPORT_ROOT/$PERF_DATASET_ID/$PERF_FIXTURE_RUN_ID`; an existing directory rejects the run with zero API/DB mutation. Docker inspection receives a credential-filtered child environment. The script captures all three containers' immutable identities before the common lock, rechecks them after the lock before login or mutation, and checks them again before writing the manifest. There is no target, image, credential, token-margin, or cardinality fallback. It creates:

- 25 new ACTIVE fixture campuses whose names contain `fixtureRunId` and whose region equals `datasetId`;
- one primary campus with exactly 1,000 ACTIVE memberships: the service admin creator plus 999 existing ACTIVE dataset users;
- one isolation campus containing a sentinel dataset user who is deliberately absent from the primary campus;
- 100 new active MEAL assignments and one new active COFFEE assignment in the primary campus.

The script uses only public APIs. It decodes the server-issued JWT `exp` in memory and checks the runtime safety margin before every authenticated request, including all 1,000 detail reads, 1,126 mutation requests, and final correctness reads. When remaining lifetime is insufficient, it logs in again before the request; tokens, credentials, and expiry values are never persisted or printed, while only `refreshCount` is recorded. It never patches or deletes an existing user, membership, duty, campus, or QA row. It never changes a dataset user's service role or campus role, so it does not increment an existing user's `tokenVersion`. A partial preparation is preserved; retry with a new `fixtureRunId` instead of cleaning up or reusing the old one.

After report reservation, every failure writes one exclusive, secret-free `first-rejection.json` containing the exact stage, completed campus/membership/duty counts, `refreshCount`, `reusable=false`, and `automaticCleanup=false`. Later failures cannot overwrite the first receipt. Successful completion writes `fixture-manifest.json` exclusively; neither path deletes partial rows or permits the same fixture run ID to be retried.

Credentials are required only as runtime environment variables and are not written to manifests or reports.

```bash
BASE_URL=http://127.0.0.1:28080 \
PERF_ADMIN_EMAIL=runtime-only-admin@example.com \
PERF_ADMIN_PASSWORD=runtime-only-secret \
PERF_DATASET_ID=PERF_1000_YYYYMMDD_A \
PERF_FIXTURE_RUN_ID=ISSUE195_YYYYMMDD_A \
PERF_SOURCE_COMMIT=approved-origin-develop-commit \
PERF_REPORT_ROOT=/absolute/path/to/FaithLog/performance/k6/issue-195/reports \
APP_CONTAINER_ID=actual-app-container-id \
EXPECTED_APP_COMPOSE_SERVICE=runtime-verified-app-service \
EXPECTED_APP_IMAGE_ID=runtime-verified-full-app-image-id \
POSTGRES_CONTAINER_ID=actual-postgres-container-id \
EXPECTED_POSTGRES_COMPOSE_SERVICE=runtime-verified-postgres-service \
EXPECTED_POSTGRES_IMAGE_ID=runtime-verified-full-postgres-image-id \
REDIS_CONTAINER_ID=actual-redis-container-id \
EXPECTED_REDIS_COMPOSE_SERVICE=runtime-verified-redis-service \
EXPECTED_REDIS_IMAGE_ID=runtime-verified-full-redis-image-id \
EXPECTED_ACTIVE_MEMBERS=1000 \
EXPECTED_DUTY_ASSIGNMENTS=101 \
TOKEN_SAFETY_MARGIN_SECONDS=120 \
node performance/k6/issue-195/prepare-fixture.mjs
```

This docs-only session does not run the command. The preparation command above is retained as the historical contract that produced the preserved manifests; G is complete, so provisioning and fixture preparation must not be repeated.

## Independent Cases

Each case below uses separate warmup and measured k6 processes. The operator must supply `WARMUP_VUS`, `WARMUP_DURATION`, `MEASURED_VUS`, and `MEASURED_DURATION` as no-default runtime inputs; there are no load defaults or latency thresholds. `MAX_FAILURE_RATE` is also runtime-required and the current exact correctness gate accepts only the explicitly supplied value `0`. The runner executes every process sequentially under the Compose-project common performance lock; another load test or fixture mutation must not run in parallel.

- `admin_users`: first page (`page=0,size=20`), middle page (`page=25,size=20`), large page (`page=0,size=100`), `role=USER`, and dataset name+email search.
- `admin_campuses`: first page (`page=0,size=20`), middle page (`page=1,size=20`), large page (`page=0,size=100`), and exact ACTIVE primary-campus search. Every case is isolated by `fixtureRunId` and `datasetId`.
- `campus_members`: one unpaginated full-list run against exactly 1,000 ACTIVE members.
- `duty_assignments`: one unpaginated full-list run against 101 active assignments with explicit `staleOnly=false`.

Every run emits a scenario/case-specific duration Trend with p50/p95/p99/max, request Counter rate as endpoint throughput, and failure Rate. Correctness checks cover HTTP 200, the success envelope, response shape, strict ascending order, page metadata where applicable, filter results, exact pageable/list cardinality, and campus isolation using the sentinel user.

## Runtime Evidence And Runner

The runner requires the exact approved source commit, `BASE_URL`, already-running app/PostgreSQL/Redis container IDs, expected Compose services, and full immutable image IDs at runtime. It reads all three containers' actual `com.docker.compose.project` and `com.docker.compose.service` labels, rejects project/service/image mismatch, and requires `BASE_URL` to be the exact loopback HTTP origin of one app-container published port before lock acquisition. After acquiring the actual-project common lock, it captures immutable app/PostgreSQL/Redis container IDs and names, image IDs, `StartedAt`, labels/ports, plus PostgreSQL current database, TCP server address/port, and postmaster start time. That post-lock capture is exact-bound again to the original verified Compose project, approved services/images/database, approved source manifest, and `BASE_URL` published endpoint before report evidence, login, or warmup begins. Each measured case checks this full identity immediately before its DB counter window and after that window, and a final check closes the execution. Name reuse, image/container replacement, restart, port/label drift, database endpoint change, Redis replacement, or postmaster restart is non-adoptable.

Immediately before each case's warmup, the runner obtains a fresh access token outside that case's DB counter window. It decodes only the in-memory JWT `exp` and requires remaining lifetime to cover the approved warmup, one measured-duration idle control window, the measured load, and the runtime-required safety margin: `warmup + (2 × measured) + safety`. This first gate runs before warmup and before any control snapshot/observer write. After the control and measured-before evidence, a DB/API-free validator checks the same token again immediately before k6 and requires remaining lifetime to be strictly greater than measured duration plus the margin. The token itself is never written or printed. Only that case's warmup and measured k6 processes receive it, and the shell clears it when the case ends. The k6 setup validates the runtime token but returns no setup data, so `handleSummary` cannot serialize it; VUs read only the process-scoped runtime value. API admin credentials and PostgreSQL user/database/password values remain unexported shell variables. The login child receives only the API credentials, and k6 receives none of the DB credentials. PostgreSQL password forwarding uses a command-scoped Docker client environment plus value-free `docker exec -e PGPASSWORD`, so the secret is absent from Docker CLI argv and reports/logs. The control JSON conversion child runs with a minimal environment containing only its non-secret path/timestamp/phase/output inputs; API and database credentials are not inherited.

Every warmup/measured summary is fail-closed validated for the exact case duration/request/failure metrics. Both direct k6 v2 metrics and `metric.values` shapes are accepted. Request count must be a positive safe integer and throughput must be finite/positive. For the custom failure Rate, k6 v2 records failure observations as `passes` and non-failure observations as `fails`, so exact zero failure requires numeric `value=0`, `passes=0`, and `fails=request Counter count`; missing, non-finite, string, positive value, positive passes, or count mismatch is rejected. Latency values must be finite/non-negative and satisfy `p50 <= p95 <= p99 <= max`. The normalized measured adoption preserves the Counter count, throughput, zero Rate value, and ordered Trend values, and the DB integrity gate repeats those exact types before deriving its transaction expectation. No correctness or latency/throughput threshold is relaxed.

After each case's warmup, the measured case gets immediately-before/after read-only table counters plus current-database-only, non-truncated `pg_stat_statements` inventories. Each phase always writes an exact machine-readable availability record with schema version 1, its `before`/`after` phase, `available`/`unavailable` status, and the exact relation or null. Only two valid unavailable records with no NDJSON snapshots produce the optional `query-evidence-unavailable` result. Availability changing across the window, malformed/missing markers, an available marker without its snapshot, or an unavailable marker coexisting with a snapshot is `non-adoptable`. When both phases are available, each NDJSON file must parse line-by-line and contain at least one production query after the marked observer SQL is excluded. An empty/observer-only inventory is `available-query-snapshot-empty`; malformed JSON is `available-query-snapshot-malformed`, and both produce a machine-readable non-adoptable result. Every query row preserves PostgreSQL's `(userid, dbid, queryid, toplevel)` identity; duplicate or malformed identities fail closed instead of being overwritten, and each identity's counters are compared independently before summing endpoint calls. PostgreSQL cumulative `calls` and `rows` values are collected as decimal strings and compared/subtracted with strict `BigInt`, so values above JavaScript's safe-integer boundary remain lossless in the report. `total_exec_time` remains a separately validated finite decimal number. Table counters must contain the required 1,000-member/25-campus/101-duty role/type/isolation expectations and remain byte-semantically equal as parsed metrics. A missing before/after query, counter regression, after-only cumulative query, or negative/invalid counter is `non-adoptable`; verified unchanged non-empty snapshots remain explicit zero-call evidence. The workflow never installs the extension or changes PostgreSQL configuration.

All collector `psql` invocations use quiet machine-output mode, including table-counter and runtime-integrity scripts with `\pset` plus the `-At` availability/query paths. Meta-command status banners such as `Output format is csv.` and `Tuples only is on.` are therefore excluded from stdout artifacts without changing SQL, metrics, observer ordering, DB windows, or pg_stat_statements semantics. A fake psql execution contract parses every table/runtime/available/unavailable query artifact and rejects any banner contamination.

Before each measured DB window, the runner captures a same-duration idle control with no k6/API load. The before/after control snapshots use the same strict typed database, observer identity, capture timestamps, external-session, planner, four-table maintenance, transaction-counter, and observer-overhead schema as measured evidence. Capture overhead is recorded separately; the idle interval from control-before completion to control-after start must be at least the runtime `MEASURED_DURATION`. Control rollback, external activity, planner/maintenance drift, missing/malformed fields, or anything other than exactly one documented observer commit is fail-closed. The validator derives the raw control commit delta and the background/unattributed delta with strict `BigInt`, never subtracts or proportionally estimates it from the measured window, and fixes its use to `supporting-only` with `automaticAdoption=false`.

The control adoption is then exact-bound again to the runner's case, expected `MEASURED_DURATION`, BigInt equation `controlCommitDelta = backgroundCommitDelta + observerCommitOverhead(1)`, and timestamp order. Its after capture must complete before the measured-before snapshot and within runtime-required `RESOURCE_BOUNDARY_MAX_GAP_SECONDS`; an old control from the same case cannot be mixed in. The measured validator parses the actual `faithlog-issue195-observer-{case}-{before|after}` identity and exact-binds its stable case to the runner's `EVIDENCE_CASE`, measured `scenario-case`, and `issue195_{sanitized scenario}_{sanitized case}` metric. Mixing evidence from another case is non-adoptable. It requires zero boundary external sessions, exact planner/maintenance stability, zero rollback delta, and strict `BigInt`. Static current-develop source comparison records two transactions per request: the request authentication `tokenVersion` repository transaction and the endpoint service read transaction. However, `pg_stat_database` is cumulative DB-wide evidence whose cross-backend publication and source attribution cannot prove both commits for every completed request. Its conservative acceptance minimum is therefore `measured request count + one documented before-observer commit`; fewer commits fail closed. The source-derived `2 × requests + observer` value remains a diagnostic field, not the acceptance threshold. Every commit above the conservative minimum remains source-unattributed supporting evidence and makes the case `conditional-not-adoptable`; it is never subtracted, proportionally estimated, tolerated as a request transaction, or attributed to a scheduler. The runtime-integrity observer SQL remains excluded from `pg_stat_statements` deltas, `case-windows.ndjson` and the redacted application log remain attribution evidence, and PostgreSQL statistics are never reset.

Each measured endpoint case also records app/PostgreSQL/Redis `docker stats --no-stream` snapshots immediately before and after its k6 process, including the full container ID, name, image ID, `StartedAt`, normalized finite non-negative CPU percentage, and memory bytes. Docker CLI memory values are rounded display values; supported `B/kB/KB/KiB/MB/MiB/GB/GiB/TB/TiB` inputs are deterministically normalized to the nearest whole byte and then required to be non-negative safe integers. Negative, non-finite, unsupported, or overflowing values fail closed. This normalization changes no threshold or cadence contract. A fail-closed adoption validator requires the exact three-container set, exact scenario/case, one before and one after snapshot, full initial immutable identity, strict persisted schema, `before < measured-start <= measured-end < after`, and both boundary gaps within runtime-required `RESOURCE_BOUNDARY_MAX_GAP_SECONDS`. These are exactly two validated boundary observations, not continuous sampling or a claim about in-run peak CPU/RAM. Runtime admin email/password values are redacted before the application log is written.

The report directory is atomically created before source/target preflight. From the source-contract gate onward, any non-zero path records `first-rejection.json` with only schema version, non-adoptable status, `automaticAdoption=false`, stage, scenario/case, and exit code. Creation is exclusive and later cleanup or validation failures cannot overwrite the first rejection. Credentials, tokens, raw environment values, and exception text are excluded.

```bash
BASE_URL=http://127.0.0.1:28080 \
PERF_ADMIN_EMAIL=runtime-only-admin@example.com \
PERF_ADMIN_PASSWORD=runtime-only-secret \
PERF_DATASET_ID=PERF_1000_YYYYMMDD_A \
PERF_FIXTURE_RUN_ID=ISSUE195_YYYYMMDD_A \
PERF_EXECUTION_RUN_ID=EXEC195_YYYYMMDD_A \
PERF_SOURCE_COMMIT=approved-origin-develop-commit \
PERF_REPORT_ROOT=/absolute/path/to/FaithLog/performance/k6/issue-195/reports \
CAMPUS_ID=primary-campus-id \
ISOLATION_CAMPUS_ID=isolation-campus-id \
ISOLATION_USER_ID=isolation-user-id \
APP_CONTAINER_ID=actual-app-container-id \
EXPECTED_APP_COMPOSE_SERVICE=runtime-verified-app-service \
EXPECTED_APP_IMAGE_ID=runtime-verified-full-app-image-id \
POSTGRES_CONTAINER_ID=actual-postgres-container-id \
EXPECTED_POSTGRES_COMPOSE_SERVICE=runtime-verified-postgres-service \
EXPECTED_POSTGRES_IMAGE_ID=runtime-verified-full-postgres-image-id \
REDIS_CONTAINER_ID=actual-redis-container-id \
EXPECTED_REDIS_COMPOSE_SERVICE=runtime-verified-redis-service \
EXPECTED_REDIS_IMAGE_ID=runtime-verified-full-redis-image-id \
POSTGRES_USER=runtime-only-user \
POSTGRES_DB=runtime-only-db \
POSTGRES_PASSWORD=runtime-only-secret \
WARMUP_VUS=no-default-runtime-value \
WARMUP_DURATION=no-default-runtime-value \
MEASURED_VUS=no-default-runtime-value \
MEASURED_DURATION=no-default-runtime-value \
MAX_FAILURE_RATE=0 \
TOKEN_SAFETY_MARGIN_SECONDS=no-default-runtime-seconds \
EXPECTED_ACTIVE_MEMBERS=1000 \
EXPECTED_DUTY_ASSIGNMENTS=101 \
RESOURCE_BOUNDARY_MAX_GAP_SECONDS=no-default-runtime-seconds \
K6_BIN=no-default-runtime-k6-binary \
performance/k6/issue-195/run-baseline.sh
```

### Completed PM actual sequence

1. PM reused the preserved successful dataset/fixture without provisioning or fixture mutation and reserved fresh `EXEC195_BEFORE_20260716_G`.
2. PM reverified the source/runtime/container/target boundaries and ran all cases serially under the common cooperative lock with explicit runtime inputs.
3. All 11 measured adoption records passed with `failureRate=0`; no case-level rejection occurred.
4. The runner wrote the designed final `conditional-not-adoptable` classification and exited `2`. This is the only G first rejection and does not invalidate the internally consistent case measurements; it prevents automatic adoption.
5. PM restored one temporary account to service role `USER` and confirmed zero remaining lock/process. G is complete and must not be rerun. Any after comparison requires user approval, a new source/runtime bind, and a fresh PM-assigned execution ID.

G's 22 k6 processes represented exactly 27 minutes 30 seconds of configured load time. Eleven immediately-before idle controls added exactly 22 configured minutes at the 2-minute measured duration, for 49 minutes 30 seconds of serialized warmup/control/measured time before login/evidence/validation overhead. Provisioning and fixture time were already complete and were not repeated. These are configured durations, not latency results or performance achievements.

Reports are written below the ignored path `performance/k6/issue-195/reports/{datasetId}/{fixtureRunId}/{executionRunId}/`. `PERF_EXECUTION_RUN_ID` must be a fresh `EXEC195_*` identifier; the runner atomically creates its directory and refuses every existing directory instead of overwriting or appending stale evidence. Keep separate warmup/measured summaries and normalized evidence-validator records, case windows, CPU/RAM snapshots, target/Compose identity, run metadata, and per-case DB evidence together. Passing an individual evidence validator means only that evidence file is structurally consistent. Boundary activity evidence and a cooperative lock cannot prove the absence of transient frontend/QA/CPU-only shared-stack load, so the final `measurement-classification.json` is fixed to `conditional-not-adoptable` with `automaticAdoption=false`; the runner writes it and exits non-zero. A future automatically adoptable mode requires a separate user-approved exclusive/continuous provenance contract and is not selected here. Do not commit credentials, tokens, raw environment dumps, or report files.

Authoring verification: the first PM-finding contract failed `6/8` before passing `8/8`; the second PM test-only RED was `8 pass / 5 fail` before `13/13`. Subsequent PM audits expanded strict target/token/query/activity/summary/report/table/runtime/resource contracts through `35/35`. The current-develop drift contract then failed `1/1` before `36/36`. The final common integrity audit preserved those 36 contracts and failed all five new source/image/workload, Redis continuity, Rate observation math, full-resource cadence, and first-rejection contracts before `41/41`. Self-review then reproduced two remaining fixture-PostgreSQL continuity and pre-lock rejection-preservation gaps as `2/2` RED, followed by one credential child-inheritance ordering RED, before `43/43` GREEN. Fresh provisioning first failed all six top-level contracts without an implementation, then passed 11 nested/total contracts. The provisioning JWT finding produced `9 pass / 7 fail` RED before targeted `16/16`. Fixture JWT/receipt/child-environment tests then reached `8/8`; the strict absolute-report-root, numeric-loopback, child allowlist, and immutable-identity bundle reproduced `10 assertions / 7 pass / 3 fail` RED before GREEN. Final handoff review reproduced runner fallback and provisioner relative-root acceptance before aligning all three phases to one runtime-required absolute root. The actual k6 JSON-module failure then reproduced as installed-k6 inspect exit `107` before `open()` plus `JSON.parse` made the same inspect pass. Execution B then exposed k6 v2's actual Rate `value/passes/fails` shape; the preserved summary failed RED before direct/values, warmup/measured, and normalized DB-integrity flow all passed with strict zero-failure math. Execution C exposed Docker's rounded decimal memory display and the preserved summary's historical setup-data token. Three targeted contracts failed before nearest-whole-byte normalization, strict capture-to-adoption coverage, and token-free k6 setup/serialization passed, including an installed-k6 no-HTTP micro script. Execution D then exposed psql meta-command status contamination after 7,197 successful measured requests; the fake collector failed RED before quiet machine-output mode made table/runtime/available/unavailable query artifacts parse cleanly. Execution E exposed DB-wide background commits and added the strict same-duration control without subtraction. Execution F then exposed the opposite DB-wide false rejection: the current source still has two transactions per request, but the cumulative snapshot was 71 below that source-derived diagnostic threshold even 6.304 seconds after measured end. The preserved F actual fixture and publication counterexample failed RED before the conservative `requestCount + observer` minimum passed without tolerance, subtraction, or automatic adoption. Final issue-local verification is fixture `10/10` + provisioning `19/19` + scenario `53/53` = `82/82`. Node/Bash syntax, JSON parse, installed-k6 inspect/no-HTTP serialization, and `git diff --check` are issue-local gates; the earlier full Gradle `449 tests / 0 failures / 0 errors / 3 skipped` predates this scenario-only adjustment and was not rerun. This correction reads F evidence in place, verifies all content/hash/size/mtime unchanged, and runs only fake collectors, k6 inspect, and a no-HTTP micro script with non-secret inputs; it does not run Docker, DB, HTTP, seed, provisioning, fixture mutation, or k6 load. No production Java or REST Docs changed. PM then independently verified clean HEAD `bc19e803c35b3f8e46140efb95b5da37cbecb373`, production/Flyway diff 0, preserved-F `82/82` GREEN, and source publication-safe lower-bound review finding 0 before G. The docs-only G checkpoint re-read the 11 measured adoption records and final classification without running HTTP, DB, Docker, or k6 load.

## Hard Stops

- Local Docker only. The k6 and fixture scripts reject remote targets.
- 다른 부하와 병렬 실행 금지. Fixture and measurement share `/tmp/faithlog-performance-{composeProject}.lock`. Operator confirmation alone does not make shared-stack results automatically adoptable; this scenario exits with a conditional classification until the user approves an exclusive/continuous provenance contract.
- Issue #192-#199 test code may be corrected in parallel, but actual load remains PM-controlled and strictly sequential. Scenario-ready tooling never reserves or consumes an actual measurement slot.
- Shared Docker lifecycle is out of scope: do not run `up`, `down`, `build`, rebuild, prune, or volume cleanup from this workflow.
- Docker는 이 개발 세션에서 실행하지 않는다.
- Docker, seed, DB, HTTP, and k6 load execution are deferred; this development session runs only non-secret `k6 inspect` initialization validation.
- production Java/API/authorization/response/error/transaction/Entity/DB/Flyway/dependency 변경 금지.
- G may be cited only as a conditional shared-stack before baseline. It must not be described as automatically adoptable, an optimization result, or a resume performance achievement.
