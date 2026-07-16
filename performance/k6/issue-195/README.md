# Issue #195 Member List Before Scenario

Status: **scenario-ready/not-measured**

This directory prepares the before-only local Docker baseline for Issue #195. No k6, seed, Docker, or DB command was run while authoring it. It does not contain a measured baseline or an improvement claim.

## API Inventory

The inventory is taken from the current production controllers and Spring REST Docs.

| Scenario | Exact API | Supported query contract | Required authorization | Production order |
| --- | --- | --- | --- | --- |
| `admin_users` | `GET /api/v1/admin/users` | `name`, `email`, `userId`, `role`, `page`, `size`, `sort` | active service `ADMIN` | requested stable sort; cases use `id,asc` |
| `admin_campuses` | `GET /api/v1/admin/campuses` | `name`, `region`, `status`, `page`, `size`, `sort` | active service `ADMIN` | requested stable sort; cases use `id,asc` |
| `campus_members` | `GET /api/v1/admin/campuses/{campusId}/members` | none | active service `ADMIN`, or ACTIVE campus `MINISTER`/`ELDER`/`CAMPUS_LEADER` | `campus_members.id asc`; ACTIVE only |
| `duty_assignments` | `GET /api/v1/admin/campuses/{campusId}/duty-assignments` | optional `staleOnly` (baseline fixes `staleOnly=false`) | active service `ADMIN`, or ACTIVE campus `MINISTER`/`ELDER`/`CAMPUS_LEADER` | `campus_duty_assignments.id asc`; active assignment with ACTIVE membership only |

The member and duty endpoints are intentionally not given invented pagination. Role/search/page combinations apply to the service-admin user API; status/search/page combinations apply to the service-admin campus API. The two pageable admin APIs default to `size=20`, reject sizes above `100`, and the scenario separates `size=20` from the approved maximum `size=100`. `includeArchived` is not supported by any Issue #195 endpoint; the Issue #201 archive opt-in contract belongs to billing and poll lists and is not sent here.

## Current Develop Boundary

- #200 authorization remains active service `ADMIN` or an ACTIVE campus `MINISTER`/`ELDER`/`CAMPUS_LEADER`. On current develop, the duty list already performs a bulk user lookup, while the campus-member list still performs a per-member user lookup. This scenario records that asymmetric current state and does not claim a pristine pre-bulk duty baseline.
- #202 enables RLS but `FORCE ROW LEVEL SECURITY` is not used. The owner JDBC path is therefore expected to remain unaffected, but its exact runtime identity and continuity still require the future authorized measurement; this scenario-only session does not verify or claim a DB result.
- #206 adds a billing paging tie-break and is not applicable to these member/campus APIs. Admin cases request explicit `id,asc`; campus member and duty repositories return their own IDs in ascending order.

## Fresh Dataset Provisioning

Issue #195 cannot reuse the Issue #192 dataset: that namespace contains 999 ACTIVE `USER` rows plus one service `ADMIN`, while this scenario requires exactly 1,000 ACTIVE service-role `USER` rows and excludes the runtime admin. `provision-dataset.mjs` therefore reserves a fresh report namespace first, takes the actual Compose-project common performance lock, exact-checks full app/PostgreSQL/Redis container IDs, images, `StartedAt`, labels and the app published endpoint before and after the lock, then creates exactly 1,000 users through `POST /api/v1/auth/signup`. It re-reads all 1,000 rows through the admin list/detail APIs and accepts only unique, ACTIVE `USER` rows whose name and email both contain the dataset ID. It performs no update, role change, delete, cleanup, or retry of an occupied ID.

The current fresh handoff candidates are:

- `PERF_DATASET_ID=PERF_1000_20260716_195_A`
- `PERF_FIXTURE_RUN_ID=ISSUE195_BEFORE_20260716_A`
- `PERF_EXECUTION_RUN_ID=EXEC195_BEFORE_20260716_A`
- report reservation: `performance/k6/issue-195/reports/PERF_1000_20260716_195_A/_provisioning/`

The source input is exactly `6796ed146244d8f3f5b5dd7048ebe16865084a97`. Operational provenance is the clean detached checkout `/private/tmp/FaithLog-perf-206-deploy` at that commit and an app image created after the checkout. The current handoff identities are app container `a7df78b330f457a7fd60a9531362d0f1f063ae7aa6cae5f2d996eb8cb51fe79d` / image `sha256:8e0f8d85d697a7d34aabf3703ddb27b4f1af326dec4f7c35556986303b0b816c`, PostgreSQL container `81aa74ca1b491b45eb691b3d65de9e42eb47ef64a6bcb961d0b627b030139ae9` / image `sha256:48d29282d2b43c402465c28f8572021b59aaf43574056faaad2fd7bb85ffdd4e`, and Redis container `4109f6525948d12d1e5377fb6160c8955f6c3fcd7816e02786b2dd8031e23de9` / image `sha256:80dd823f4d2bf93dd5e418a0ae2817319a1ba279953e234082e54a5a18306223`. The app image has no OCI revision label or `git.properties`, so image-alone cryptographic source proof is unavailable; this approved operational bind is recorded as a limitation, not a new blocker. PM must still re-read the full identities, actual Compose service labels, and `http://127.0.0.1:28080` binding immediately before execution.

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

This development session does not run the command. Once the concurrently active #192 analysis/load slot is clear and every runtime gate is reverified, the PM may run provisioning, fixture preparation, and load sequentially without another user approval.

## Independent Cases

Each case below uses separate warmup and measured k6 processes. The operator must supply `WARMUP_VUS`, `WARMUP_DURATION`, `MEASURED_VUS`, and `MEASURED_DURATION` as no-default runtime inputs; there are no load defaults or latency thresholds. `MAX_FAILURE_RATE` is also runtime-required and the current exact correctness gate accepts only the explicitly supplied value `0`. The runner executes every process sequentially under the Compose-project common performance lock; another load test or fixture mutation must not run in parallel.

- `admin_users`: first page (`page=0,size=20`), middle page (`page=25,size=20`), large page (`page=0,size=100`), `role=USER`, and dataset name+email search.
- `admin_campuses`: first page (`page=0,size=20`), middle page (`page=1,size=20`), large page (`page=0,size=100`), and exact ACTIVE primary-campus search. Every case is isolated by `fixtureRunId` and `datasetId`.
- `campus_members`: one unpaginated full-list run against exactly 1,000 ACTIVE members.
- `duty_assignments`: one unpaginated full-list run against 101 active assignments with explicit `staleOnly=false`.

Every run emits a scenario/case-specific duration Trend with p50/p95/p99/max, request Counter rate as endpoint throughput, and failure Rate. Correctness checks cover HTTP 200, the success envelope, response shape, strict ascending order, page metadata where applicable, filter results, exact pageable/list cardinality, and campus isolation using the sentinel user.

## Runtime Evidence And Runner

The runner requires the exact approved source commit, `BASE_URL`, already-running app/PostgreSQL/Redis container IDs, expected Compose services, and full immutable image IDs at runtime. It reads all three containers' actual `com.docker.compose.project` and `com.docker.compose.service` labels, rejects project/service/image mismatch, and requires `BASE_URL` to be the exact loopback HTTP origin of one app-container published port before lock acquisition. After acquiring the actual-project common lock, it captures immutable app/PostgreSQL/Redis container IDs and names, image IDs, `StartedAt`, labels/ports, plus PostgreSQL current database, TCP server address/port, and postmaster start time. That post-lock capture is exact-bound again to the original verified Compose project, approved services/images/database, approved source manifest, and `BASE_URL` published endpoint before report evidence, login, or warmup begins. Each measured case checks this full identity immediately before its DB counter window and after that window, and a final check closes the execution. Name reuse, image/container replacement, restart, port/label drift, database endpoint change, Redis replacement, or postmaster restart is non-adoptable.

Immediately before each case's warmup, the runner obtains a fresh access token outside that case's DB counter window. It decodes only the in-memory JWT `exp` and requires remaining lifetime to cover the approved warmup duration, measured duration, and runtime-required approved safety margin. After the before snapshot, a DB/API-free validator checks the same token again immediately before k6 and requires remaining lifetime to be strictly greater than measured duration plus the margin. The token itself is never written or printed. Only that case's warmup and measured k6 processes receive it, and the shell clears it when the case ends. API admin credentials and PostgreSQL user/database/password values remain unexported shell variables. The login child receives only the API credentials, and k6 receives none of the DB credentials. PostgreSQL password forwarding uses a command-scoped Docker client environment plus value-free `docker exec -e PGPASSWORD`, so the secret is absent from Docker CLI argv and reports/logs.

Every warmup/measured summary is fail-closed validated for the exact case duration/request/failure metrics. Both direct k6 v2 metrics and `metric.values` shapes are accepted. Request count must be a positive safe integer, throughput must be finite/positive, Rate `passes + fails` must equal the Counter request count, `fails` and rate must both be exactly zero, and latency values must be finite/non-negative and satisfy `p50 <= p95 <= p99 <= max`. The DB integrity gate repeats the positive-safe-integer request-count contract before deriving its transaction expectation. No latency or throughput target threshold is introduced.

After each case's warmup, the measured case gets immediately-before/after read-only table counters plus current-database-only, non-truncated `pg_stat_statements` inventories. Each phase always writes an exact machine-readable availability record with schema version 1, its `before`/`after` phase, `available`/`unavailable` status, and the exact relation or null. Only two valid unavailable records with no NDJSON snapshots produce the optional `query-evidence-unavailable` result. Availability changing across the window, malformed/missing markers, an available marker without its snapshot, or an unavailable marker coexisting with a snapshot is `non-adoptable`. When both phases are available, each NDJSON file must parse line-by-line and contain at least one production query after the marked observer SQL is excluded. An empty/observer-only inventory is `available-query-snapshot-empty`; malformed JSON is `available-query-snapshot-malformed`, and both produce a machine-readable non-adoptable result. Every query row preserves PostgreSQL's `(userid, dbid, queryid, toplevel)` identity; duplicate or malformed identities fail closed instead of being overwritten, and each identity's counters are compared independently before summing endpoint calls. PostgreSQL cumulative `calls` and `rows` values are collected as decimal strings and compared/subtracted with strict `BigInt`, so values above JavaScript's safe-integer boundary remain lossless in the report. `total_exec_time` remains a separately validated finite decimal number. Table counters must contain the required 1,000-member/25-campus/101-duty role/type/isolation expectations and remain byte-semantically equal as parsed metrics. A missing before/after query, counter regression, after-only cumulative query, or negative/invalid counter is `non-adoptable`; verified unchanged non-empty snapshots remain explicit zero-call evidence. The workflow never installs the extension or changes PostgreSQL configuration.

The last before observer transaction and first after observer transaction delimit a machine-readable DB integrity window. The validator parses the actual `faithlog-issue195-observer-{case}-{before|after}` identity and exact-binds its stable case to the runner's `EVIDENCE_CASE`, measured `scenario-case`, and `issue195_{sanitized scenario}_{sanitized case}` metric. Mixing evidence from another case is non-adoptable. It also requires a complete, typed evidence schema: current database, all nine planner settings, all four target tables and their analyze/autoanalyze/vacuum/autovacuum counters and timestamps, lossless decimal-string transaction counters, observer overhead, and the measured adoption record. Missing, null-coerced, malformed, array, object, or non-finite evidence fails closed. The gate excludes only its own PostgreSQL backend PID, so another session with the same `application_name` is still external activity. It requires zero external active sessions at both boundaries, exact planner and maintenance stability, zero rollback delta, and uses strict `BigInt` for `pg_stat_database.xact_commit delta = (measured request count × 2) + one documented before-observer commit`. The code-derived factor 2 is the request authentication `tokenVersion` repository transaction plus the endpoint service read transaction; a production transaction-boundary change therefore fails closed until this scenario contract is reviewed. Extra DB transactions, vacuum/analyze activity, or planner drift make the case non-adoptable. The runtime-integrity observer SQL has a stable marker and is excluded both when collecting and when deriving `pg_stat_statements` deltas, including its first appearance after a stats reset. `case-windows.ndjson` and the redacted application log remain attribution evidence. PostgreSQL statistics are never reset.

Each measured endpoint case also records app/PostgreSQL/Redis `docker stats --no-stream` snapshots immediately before and after its k6 process, including the full container ID, name, image ID, `StartedAt`, normalized finite non-negative CPU percentage, and memory bytes. A fail-closed adoption validator requires the exact three-container set, exact scenario/case, one before and one after snapshot, full initial immutable identity, strict persisted schema, `before < measured-start <= measured-end < after`, and both boundary gaps within runtime-required `RESOURCE_BOUNDARY_MAX_GAP_SECONDS`. These are exactly two validated boundary observations, not continuous sampling or a claim about in-run peak CPU/RAM. Runtime admin email/password values are redacted before the application log is written.

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

### Exact PM handoff sequence

1. Re-read source checkout cleanliness, full app/PostgreSQL/Redis identities, actual service labels, published endpoint, PostgreSQL server/postmaster identity, candidate DB namespace count `0`, and all three report paths. Stop before login if any value differs.
2. Run `provision-dataset.mjs` with the fresh dataset ID. Expected minimum API calls are 2 namespace reads, 2 logins (preflight and mandatory post-signup refresh), 1 `/me`, 1,000 signup mutations, 10 verification pages, and 1,000 detail reads: 2,015 calls, plus only the conditional token refreshes required by the runtime margin. Login may update the existing admin's last-login/session state; the only dataset business writes are 1,000 new ACTIVE `USER` rows. No existing dataset row is updated or deleted.
3. Remove `PERF_DATASET_MEMBER_PASSWORD` from the environment and run `prepare-fixture.mjs` with `PERF_FIXTURE_RUN_ID=ISSUE195_BEFORE_20260716_A`. It performs 1,126 additive mutation requests: 25 campus creates, 1,000 explicit membership adds, and 101 duty assignments. Expected new business rows are 25 campuses, 1,025 memberships including the automatic creator memberships, and 101 duties. Its full API flow is at least 2,141 calls including the initial login, preflight, dataset pages/details, and final correctness reads, plus only the conditional fresh-login calls required by the runtime token margin.
4. Read `fixture-manifest.json`; pass its primary campus, isolation campus, and isolation user IDs exactly as `CAMPUS_ID`, `ISOLATION_CAMPUS_ID`, and `ISOLATION_USER_ID`. Remove the member password from the runner environment. Run `run-baseline.sh` with `PERF_EXECUTION_RUN_ID=EXEC195_BEFORE_20260716_A` and the remaining required API/DB/runtime inputs.
5. The current recommended handoff values are `WARMUP_VUS=1`, `WARMUP_DURATION=30s`, `MEASURED_VUS=10`, `MEASURED_DURATION=2m`, `MAX_FAILURE_RATE=0`, `TOKEN_SAFETY_MARGIN_SECONDS=120`, and `RESOURCE_BOUNDARY_MAX_GAP_SECONDS=10`. They are recommendations, not hard-coded defaults or already measured/approved results; PM supplies them explicitly at runtime under the user's automatic sequential-load decision. `K6_BIN`, service labels, API/DB credentials, and all reverified immutable identities remain required inputs.

The 22 k6 processes have exactly 27 minutes 30 seconds of configured load time. Planning allowance, not a measured result, is 15–30 minutes for at least 2,015 serial provisioning calls (including 1,000 BCrypt signups), 20–45 minutes for fixture creation/verification, and 10–25 minutes for per-case login/evidence/validation around k6, for roughly 75–130 minutes end to end. Stop at the first lock, source/target/runtime identity, credential, collision, partial-write, cardinality, ADMIN/inactive/duplicate, token-lifetime, summary, correctness, DB/query/table, resource cadence, or first-rejection gate failure. Preserve all evidence and additive rows; do not cleanup or continue to the next phase.

Reports are written below the ignored path `performance/k6/issue-195/reports/{datasetId}/{fixtureRunId}/{executionRunId}/`. `PERF_EXECUTION_RUN_ID` must be a fresh `EXEC195_*` identifier; the runner atomically creates its directory and refuses every existing directory instead of overwriting or appending stale evidence. Keep separate warmup/measured summaries and normalized evidence-validator records, case windows, CPU/RAM snapshots, target/Compose identity, run metadata, and per-case DB evidence together. Passing an individual evidence validator means only that evidence file is structurally consistent. Boundary activity evidence and a cooperative lock cannot prove the absence of transient frontend/QA/CPU-only shared-stack load, so the final `measurement-classification.json` is fixed to `conditional-not-adoptable` with `automaticAdoption=false`; the runner writes it and exits non-zero. A future automatically adoptable mode requires a separate user-approved exclusive/continuous provenance contract and is not selected here. Do not commit credentials, tokens, raw environment dumps, or report files.

Authoring verification: the first PM-finding contract failed `6/8` before passing `8/8`; the second PM test-only RED was `8 pass / 5 fail` before `13/13`. Subsequent PM audits expanded strict target/token/query/activity/summary/report/table/runtime/resource contracts through `35/35`. The current-develop drift contract then failed `1/1` before `36/36`. The final common integrity audit preserved those 36 contracts and failed all five new source/image/workload, Redis continuity, Rate observation math, full-resource cadence, and first-rejection contracts before `41/41`. Self-review then reproduced two remaining fixture-PostgreSQL continuity and pre-lock rejection-preservation gaps as `2/2` RED, followed by one credential child-inheritance ordering RED, before `43/43` GREEN. Fresh provisioning first failed all six top-level contracts without an implementation, then passed 11 nested/total contracts. The provisioning JWT finding produced `9 pass / 7 fail` RED before targeted `16/16`. Fixture JWT/receipt/child-environment tests then reached `8/8`; the strict absolute-report-root, numeric-loopback, child allowlist, and immutable-identity bundle reproduced `10 assertions / 7 pass / 3 fail` RED before GREEN. Final handoff review reproduced runner fallback and provisioner relative-root acceptance before aligning all three phases to one runtime-required absolute root. Final issue-local verification is fixture `10/10` + provisioning `19/19` + scenario `44/44` = `73/73`. Node/Bash syntax, JSON parse, and `git diff --check` are issue-local gates; the earlier full Gradle `449 tests / 0 failures / 0 errors / 3 skipped` predates this scenario-only adjustment and was not rerun. All orchestration uses only temp shell/Node stubs and does not run Docker, DB, HTTP, seed, or k6. No production Java or REST Docs changed. This verifies tooling only, not a baseline result.

## Hard Stops

- Local Docker only. The k6 and fixture scripts reject remote targets.
- 다른 부하와 병렬 실행 금지. Fixture and measurement share `/tmp/faithlog-performance-{composeProject}.lock`. Operator confirmation alone does not make shared-stack results automatically adoptable; this scenario exits with a conditional classification until the user approves an exclusive/continuous provenance contract.
- Issue #192-#199 test code may be corrected in parallel, but actual load remains PM-controlled and strictly sequential. Scenario-ready tooling never reserves or consumes an actual measurement slot.
- Shared Docker lifecycle is out of scope: do not run `up`, `down`, `build`, rebuild, prune, or volume cleanup from this workflow.
- Docker는 이 개발 세션에서 실행하지 않는다.
- Docker, seed, k6, and DB execution are deferred; this development session must not run them.
- production Java/API/authorization/response/error/transaction/Entity/DB/Flyway/dependency 변경 금지.
- A before report must not claim optimization or resume improvement. Record the result only after the PM's sequential measurement as local-Docker baseline evidence.
