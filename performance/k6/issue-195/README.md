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

## Fixture Contract

`PERF_DATASET_ID` identifies the existing common PERFORMANCE user dataset. `PERF_FIXTURE_RUN_ID` identifies only the additive Issue #195 relationship fixture. They are never interchangeable. Preparation stops unless the dataset search resolves to exactly 1,000 users, every user is ACTIVE with service role `USER`, every name/email contains `datasetId`, and the runtime service admin is outside that dataset. Before login or mutation, fixture preparation requires `BASE_URL` and `APP_CONTAINER_ID`, compares the actual Compose service with the user-approved expected service, and requires the URL origin to match exactly one published app-container port. It then holds the same `/tmp/faithlog-performance-{composeProject}.lock` used by measurement.

The preparation script requires a fresh `ISSUE195_*` run ID and creates:

- 25 new ACTIVE fixture campuses whose names contain `fixtureRunId` and whose region equals `datasetId`;
- one primary campus with exactly 1,000 ACTIVE memberships: the service admin creator plus 999 existing ACTIVE dataset users;
- one isolation campus containing a sentinel dataset user who is deliberately absent from the primary campus;
- 100 new active MEAL assignments and one new active COFFEE assignment in the primary campus.

The script uses only public APIs. It never patches or deletes an existing user, membership, duty, campus, or QA row. It never changes a dataset user's service role or campus role, so it does not increment an existing user's `tokenVersion`. A partial preparation is preserved; retry with a new `fixtureRunId` instead of cleaning up or reusing the old one.

Credentials are required only as runtime environment variables and are not written to manifests or reports.

```bash
BASE_URL=http://localhost:8080 \
PERF_ADMIN_EMAIL=runtime-only-admin@example.com \
PERF_ADMIN_PASSWORD=runtime-only-secret \
PERF_DATASET_ID=PERF_1000_YYYYMMDD_A \
PERF_FIXTURE_RUN_ID=ISSUE195_YYYYMMDD_A \
APP_CONTAINER_ID=actual-app-container-id \
EXPECTED_APP_COMPOSE_SERVICE=user-approved-app-service \
node performance/k6/issue-195/prepare-fixture.mjs
```

Do not run this command until the PM integration session authorizes the shared Docker/DB measurement stage.

## Independent Cases

Each case below uses separate warmup and measured k6 processes. The operator must supply user-approved `WARMUP_VUS`, `WARMUP_DURATION`, `MEASURED_VUS`, and `MEASURED_DURATION`; there are no load defaults or latency thresholds. `MAX_FAILURE_RATE` is also runtime-required and the current exact correctness gate accepts only the explicitly supplied value `0`. The runner executes every process sequentially under the Compose-project common performance lock; another load test or fixture mutation must not run in parallel.

- `admin_users`: first page (`page=0,size=20`), middle page (`page=25,size=20`), large page (`page=0,size=100`), `role=USER`, and dataset name+email search.
- `admin_campuses`: first page (`page=0,size=20`), middle page (`page=1,size=20`), large page (`page=0,size=100`), and exact ACTIVE primary-campus search. Every case is isolated by `fixtureRunId` and `datasetId`.
- `campus_members`: one unpaginated full-list run against exactly 1,000 ACTIVE members.
- `duty_assignments`: one unpaginated full-list run against 101 active assignments with explicit `staleOnly=false`.

Every run emits a scenario/case-specific duration Trend with p50/p95/p99/max, request Counter rate as endpoint throughput, and failure Rate. Correctness checks cover HTTP 200, the success envelope, response shape, strict ascending order, page metadata where applicable, filter results, exact pageable/list cardinality, and campus isolation using the sentinel user.

## Runtime Evidence And Runner

The runner requires `BASE_URL`, the already-running app/PostgreSQL container IDs, and user-approved expected Compose service labels. It reads both containers' actual `com.docker.compose.project` and `com.docker.compose.service` labels, rejects project/service mismatch, and requires `BASE_URL` to be the exact loopback HTTP origin of one app-container published port before lock acquisition. After acquiring the actual-project common lock, it captures immutable app/PostgreSQL container IDs and names, image IDs, `StartedAt`, labels/ports, plus PostgreSQL current database, TCP server address/port, and postmaster start time. That post-lock capture is exact-bound again to the original verified Compose project, approved services/database, and `BASE_URL` published endpoint before report evidence, login, or warmup begins. Each measured case checks this full identity immediately before its DB counter window and after that window, and a final check closes the execution. Name reuse, image/container replacement, restart, port/label drift, database endpoint change, or postmaster restart is non-adoptable.

Immediately before each case's warmup, the runner obtains a fresh access token outside that case's DB counter window. It decodes only the in-memory JWT `exp` and requires remaining lifetime to cover the approved warmup duration, measured duration, and runtime-required approved safety margin. After the before snapshot, a DB/API-free validator checks the same token again immediately before k6 and requires remaining lifetime to be strictly greater than measured duration plus the margin. The token itself is never written or printed. Only that case's warmup and measured k6 processes receive it, and the shell clears it when the case ends. API admin credentials and PostgreSQL user/database/password values remain unexported shell variables. The login child receives only the API credentials, and k6 receives none of the DB credentials. PostgreSQL password forwarding uses a command-scoped Docker client environment plus value-free `docker exec -e PGPASSWORD`, so the secret is absent from Docker CLI argv and reports/logs.

Every warmup/measured summary is fail-closed validated for the exact case duration/request/failure metrics. Both direct k6 v2 metrics and `metric.values` shapes are accepted. Request count must be a positive safe integer, throughput must be finite/positive, latency values must be finite/non-negative and satisfy `p50 <= p95 <= p99 <= max`, and failure must be exactly zero. The DB integrity gate repeats the positive-safe-integer request-count contract before deriving its transaction expectation. No latency or throughput target threshold is introduced.

After each case's warmup, the measured case gets immediately-before/after read-only table counters plus current-database-only, non-truncated `pg_stat_statements` inventories. Each phase always writes an exact machine-readable availability record with schema version 1, its `before`/`after` phase, `available`/`unavailable` status, and the exact relation or null. Only two valid unavailable records with no NDJSON snapshots produce the optional `query-evidence-unavailable` result. Availability changing across the window, malformed/missing markers, an available marker without its snapshot, or an unavailable marker coexisting with a snapshot is `non-adoptable`. When both phases are available, each NDJSON file must parse line-by-line and contain at least one production query after the marked observer SQL is excluded. An empty/observer-only inventory is `available-query-snapshot-empty`; malformed JSON is `available-query-snapshot-malformed`, and both produce a machine-readable non-adoptable result. Every query row preserves PostgreSQL's `(userid, dbid, queryid, toplevel)` identity; duplicate or malformed identities fail closed instead of being overwritten, and each identity's counters are compared independently before summing endpoint calls. PostgreSQL cumulative `calls` and `rows` values are collected as decimal strings and compared/subtracted with strict `BigInt`, so values above JavaScript's safe-integer boundary remain lossless in the report. `total_exec_time` remains a separately validated finite decimal number. Table counters must contain the required 1,000-member/25-campus/101-duty role/type/isolation expectations and remain byte-semantically equal as parsed metrics. A missing before/after query, counter regression, after-only cumulative query, or negative/invalid counter is `non-adoptable`; verified unchanged non-empty snapshots remain explicit zero-call evidence. The workflow never installs the extension or changes PostgreSQL configuration.

The last before observer transaction and first after observer transaction delimit a machine-readable DB integrity window. The validator parses the actual `faithlog-issue195-observer-{case}-{before|after}` identity and exact-binds its stable case to the runner's `EVIDENCE_CASE`, measured `scenario-case`, and `issue195_{sanitized scenario}_{sanitized case}` metric. Mixing evidence from another case is non-adoptable. It also requires a complete, typed evidence schema: current database, all nine planner settings, all four target tables and their analyze/autoanalyze/vacuum/autovacuum counters and timestamps, lossless decimal-string transaction counters, observer overhead, and the measured adoption record. Missing, null-coerced, malformed, array, object, or non-finite evidence fails closed. The gate excludes only its own PostgreSQL backend PID, so another session with the same `application_name` is still external activity. It requires zero external active sessions at both boundaries, exact planner and maintenance stability, zero rollback delta, and uses strict `BigInt` for `pg_stat_database.xact_commit delta = (measured request count × 2) + one documented before-observer commit`. The code-derived factor 2 is the request authentication `tokenVersion` repository transaction plus the endpoint service read transaction; a production transaction-boundary change therefore fails closed until this scenario contract is reviewed. Extra DB transactions, vacuum/analyze activity, or planner drift make the case non-adoptable. The runtime-integrity observer SQL has a stable marker and is excluded both when collecting and when deriving `pg_stat_statements` deltas, including its first appearance after a stats reset. `case-windows.ndjson` and the redacted application log remain attribution evidence. PostgreSQL statistics are never reset.

Each measured endpoint case also records app/PostgreSQL `docker stats --no-stream` snapshots immediately before and after its k6 process, including normalized finite non-negative CPU percentage and memory bytes. A fail-closed adoption validator requires the exact scenario/case, one before and one after snapshot, exact initial immutable container ID/name for both roles, strict persisted schema, and `before < measured-start <= measured-end < after` event ordering. These are exactly two validated boundary observations, not continuous sampling or a claim about in-run peak CPU/RAM. Runtime admin email/password values are redacted before the application log is written.

```bash
BASE_URL=http://localhost:8080 \
PERF_ADMIN_EMAIL=runtime-only-admin@example.com \
PERF_ADMIN_PASSWORD=runtime-only-secret \
PERF_DATASET_ID=PERF_1000_YYYYMMDD_A \
PERF_FIXTURE_RUN_ID=ISSUE195_YYYYMMDD_A \
PERF_EXECUTION_RUN_ID=EXEC195_YYYYMMDD_A \
CAMPUS_ID=primary-campus-id \
ISOLATION_CAMPUS_ID=isolation-campus-id \
ISOLATION_USER_ID=isolation-user-id \
APP_CONTAINER_ID=actual-app-container-id \
EXPECTED_APP_COMPOSE_SERVICE=user-approved-app-service \
POSTGRES_CONTAINER_ID=actual-postgres-container-id \
EXPECTED_POSTGRES_COMPOSE_SERVICE=user-approved-postgres-service \
POSTGRES_USER=runtime-only-user \
POSTGRES_DB=runtime-only-db \
POSTGRES_PASSWORD=runtime-only-secret \
WARMUP_VUS=user-approved-value \
WARMUP_DURATION=user-approved-value \
MEASURED_VUS=user-approved-value \
MEASURED_DURATION=user-approved-value \
MAX_FAILURE_RATE=0 \
TOKEN_SAFETY_MARGIN_SECONDS=user-approved-seconds \
performance/k6/issue-195/run-baseline.sh
```

Reports are written below the ignored path `performance/k6/issue-195/reports/{datasetId}/{fixtureRunId}/{executionRunId}/`. `PERF_EXECUTION_RUN_ID` must be a fresh `EXEC195_*` identifier; the runner atomically creates its directory and refuses every existing directory instead of overwriting or appending stale evidence. Keep separate warmup/measured summaries and normalized evidence-validator records, case windows, CPU/RAM snapshots, target/Compose identity, run metadata, and per-case DB evidence together. Passing an individual evidence validator means only that evidence file is structurally consistent. Boundary activity evidence and a cooperative lock cannot prove the absence of transient frontend/QA/CPU-only shared-stack load, so the final `measurement-classification.json` is fixed to `conditional-not-adoptable` with `automaticAdoption=false`; the runner writes it and exits non-zero. A future automatically adoptable mode requires a separate user-approved exclusive/continuous provenance contract and is not selected here. Do not commit credentials, tokens, raw environment dumps, or report files.

Authoring verification: the first PM-finding contract failed `6/8` before passing `8/8`; the second PM test-only RED was `8 pass / 5 fail` before `13/13`. The third PM review added seven target/token/query/activity/summary/report/table adoption contracts; test-only RED was `13 pass / 7 fail` before `20/20`. The fourth PM review added collector observer identity, strict evidence schema, observer-query exclusion, and vacuum/autovacuum contracts; test-only RED preserved the existing 20 passes and failed only the four new tests before `24/24`. The fifth PM review added composite query identity, exact case binding, and runtime continuity contracts; test-only RED preserved the existing 24 passes and failed only the three new tests before `27/27`. The sixth PM review added safe-integer/ordered summary, post-lock target rebinding, Docker argv credential isolation, and resource-boundary adoption contracts; targeted test-only RED was `4 tests / 4 failures` before the full suite passed `30/30`. The seventh PM review added PID-only self-exclusion, conditional shared-stack classification, and lossless PostgreSQL cumulative-counter contracts; targeted test-only RED was `3 tests / 3 failures` before the full suite passed `33/33`. The eighth PM review added exact before/after query-availability continuity and marker/snapshot consistency; targeted test-only RED was `1 test / 1 failure` before the full suite passed `34/34`. The ninth PM review added strict non-empty production NDJSON and machine-readable parse-error contracts; targeted test-only RED was `1 test / 1 failure` before the full suite passed `35/35`. The current-develop drift contract then failed `1/1` before the scenario manifest and docs were aligned for `36/36`. Node/Bash syntax, JSON parse, and `git diff --check` are issue-local gates; the earlier full Gradle `449 tests / 0 failures / 0 errors / 3 skipped` predates this scenario-only adjustment and was not rerun. All orchestration uses only temp shell/Node stubs and does not run Docker, DB, seed, or k6. No production Java or REST Docs changed. This verifies tooling only, not a baseline result.

## Hard Stops

- Local Docker only. The k6 and fixture scripts reject remote targets.
- 다른 부하와 병렬 실행 금지. Fixture and measurement share `/tmp/faithlog-performance-{composeProject}.lock`. Operator confirmation alone does not make shared-stack results automatically adoptable; this scenario exits with a conditional classification until the user approves an exclusive/continuous provenance contract.
- Issue #192-#199 test code may be corrected in parallel, but actual load remains PM-controlled and strictly sequential. Scenario-ready tooling never reserves or consumes an actual measurement slot.
- Shared Docker lifecycle is out of scope: do not run `up`, `down`, `build`, rebuild, prune, or volume cleanup from this workflow.
- Docker는 이 개발 세션에서 실행하지 않는다.
- Docker, seed, k6, and DB execution are deferred; this development session must not run them.
- production Java/API/authorization/response/error/transaction/Entity/DB/Flyway/dependency 변경 금지.
- A before report must not claim optimization or resume improvement. Record the result only after an authorized measurement as local-Docker baseline evidence.
