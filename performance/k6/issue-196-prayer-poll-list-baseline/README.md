# Issue #196 Prayer/Poll Read Baseline Scenario

This directory prepares the **before** scenario for Issue #196. It does not contain a production N+1 fix and it does not claim measured baseline or improvement numbers.

Status: `scenario-ready / not-measured`

## Scope and safety boundary

- Local Docker only. Remote URLs are rejected by seed, shaping, runner, and direct k6 entrypoints.
- The stable current-develop `datasetId` is `issue-196-prayer-poll-list-v2`; every preparation requires a new immutable `fixtureRunId`.
- The fixture creates new campuses and new rows. It never deletes fixture data and never updates/deletes rows that existed before the current `fixtureRunId`.
- `shape-fixture.sh` atomically shapes all eight Poll rows created by the current run: five CUSTOM visibility rows, one current COFFEE row, one current MEAL row, and one archived MEAL row. It verifies exact IDs/campus plus deterministic run titles, rejects an already-shaped manifest, and rolls back all eight updates if any ownership check misses.
- A 0600 `shape-attempted` receipt makes shaping one-shot even if the process stops after the database statement but before manifest persistence. Any shaping failure requires a new `fixtureRunId`.
- Passwords, DB credentials, and Access Tokens are runtime-only. The manifest records IDs and generated test emails, but not credentials or tokens.
- After app/DB/Redis label attestation, seed, shaping, and load all acquire the same canonical `/tmp/faithlog-performance-{actualComposeProject}.lock`. The path has no caller override, and the runner also refuses to start while another k6 process exists. Each entrypoint captures all three containers' full ID, image ID, `StartedAt`, Compose labels/config hash, the published app endpoint, PostgreSQL identity, and Redis process `run_id` before lock acquisition, then requires an exact post-lock match before login, fixture mutation, or k6. The runner repeats this continuity gate before warmup, before/after measured, and before final report creation.
- Seed, shaping, the baseline runner, and the direct k6 entrypoint have no target defaults. They require an explicit numeric loopback `BASE_URL` (`127.0.0.1` or `[::1]`), app/DB/Redis container names, exact Compose service labels, exact image tags and immutable image IDs, source revision, Flyway version, Redis port, credentials, and workload inputs at runtime. `localhost` and implicit host resolution are rejected pending a separate approved resolution rule. A shared validator requires exactly one same-address-family exact/wildcard Docker binding on the requested host port. Seed records the immutable runtime and published target; shaping and baseline require the same identity before touching the fixture or measuring it.
- The runner never starts, stops, rebuilds, or prunes Docker resources. It uses only `inspect`, `logs`, `stats`, and read-only PostgreSQL statistics queries.
- Every measurement requires an explicit immutable `executionRunId`. Reports are written to a new ignored `build/reports/k6/issue-196/{fixtureRunId}/{executionRunId}/` directory by default. Optional `PERF_REPORT_ROOT` may select another local ignored artifact base; the runner still appends `{fixtureRunId}/{executionRunId}` and never deletes, reuses, or overwrites an existing path.
- Sampling can detect observed conflicts but cannot prove that a short transient request never occurred. No exclusive-window boolean or sampling values have user approval as an adoption method yet, so every otherwise-clean report remains `accepted=false`, `automaticAdoption=false`, and `measurementStatus=conditional-not-adoptable` until a separate user decision changes that policy.
- `BASE_URL`, app/DB/Redis container names, expected Compose service labels, expected image tags/IDs, source/Flyway/Redis identity, credentials, date, load, sampling, execution, and mode values are runtime-required approval inputs at their relevant entrypoints. Missing input fails before Docker/API/DB/k6 work; actual labels/images/ports/process identities and the seed manifest must match exactly. Redis currently has no scenario credential because the current Compose source config has no Redis authentication input; the scenario does not invent one.

## Fixture contract

| Area | Primary campus | Isolation campus |
| --- | ---: | ---: |
| ACTIVE members | creator 1 + generated 999 = 1,000 | creator 1 + generated 49 = 50 |
| Prayer groups | 40 × 25 members | 2 × 25 members |
| Prayer submissions | 800 submitted / 200 not submitted | none |
| Measured Poll responses | 800 responded / 200 missing per target Poll | isolation Poll only |
| Poll options | 5 per Poll | 5 |
| Poll comments | 200 on the OPEN target | none |
| Poll templates | 40 × 8 options | none |

The five primary CUSTOM Poll visibility cases are:

1. `open`: member/admin visible, `OPEN`.
2. `closed_member_visible`: ended 2 days ago, member/admin visible, `CLOSED`.
3. `closed_admin_only`: ended 5 days ago, member hidden/admin visible, `CLOSED`, anonymous results.
4. `closed_expired`: ended 8 days ago, hidden from member/admin, `CLOSED`.
5. `scheduled_future`: future window, hidden from member/admin, `SCHEDULED`.

The v2 fixture also creates duty actors and type-specific Polls required by current develop:

- one COFFEE creator who is the active COFFEE duty and can manage only their own current COFFEE Poll;
- a second active COFFEE duty who cannot manage that Poll;
- one active MEAL duty who can manage the current and archived MEAL Polls;
- a normal member and service admin, neither of whom receives COFFEE/MEAL duty ownership merely from membership or global role.

## Actual read flows

The top-level runner starts a separate k6 process for every endpoint. Explicit `all` completes Prayer, Poll member, Poll admin, and Poll duty phases in that order. Prayer and Poll traffic, and the three Poll actor modes, are therefore never mixed in one load segment.

| Mode | Endpoint phase | API |
| --- | --- | --- |
| `prayer` | `prayer_current_season` | `GET /api/v1/admin/campuses/{campusId}/prayer-seasons/current` |
| `prayer` | `prayer_groups` | `GET /api/v1/admin/prayer-seasons/{seasonId}/groups` |
| `prayer` | `prayer_assignable` | `GET /api/v1/admin/prayer-seasons/{seasonId}/members/assignable` |
| `prayer` | `prayer_weekly_board_admin` | `GET /api/v1/campuses/{campusId}/prayers/weeks/{weekStartDate}` with service admin |
| `prayer` | `prayer_weekly_board_member` | same API with a normal ACTIVE member |
| `poll-member` | list/detail/results/comments/isolation | member credentials, primary-path foreign Poll 404, and direct isolation-campus 403 |
| `poll-admin` | list/detail/results/comments/isolation | service admin credentials and the common Poll APIs |
| `poll-admin` | missing members | `GET /api/v1/admin/campuses/{campusId}/polls/{pollId}/missing-members` |
| `poll-admin` | template list/detail | `GET /api/v1/admin/campuses/{campusId}/poll-templates[/{templateId}]` |
| `poll-duty` | COFFEE list/detail | common Poll list/detail with the creator and a different active COFFEE duty |
| `poll-duty` | MEAL list/detail | common Poll list/detail with the active MEAL duty |
| `poll-duty` | MEAL management default/archive | `GET /api/v1/campuses/{campusId}/meal/polls?includeArchived={false|true}&page=0&size=100&sort=id%2Cdesc` |
| `poll-duty` | MEAL management forbidden | the same management list with a normal member, expecting `403 MEAL_DUTY_REQUIRED` |

## Correctness gates

Every load response is checked, not merely timed.

- Prayer: exact season/campus/status, 40-group and 1,000-member counts, group/member order, 800/200 submission split, member `myGroupId`, admin all-editable behavior, member own-item-only `editable`, and isolation IDs absent.
- Generic Poll list: the current API is an unpaginated array. It must have exact descending ID order, member 3-day vs admin 7-day visibility, OPEN/CLOSED/SCHEDULED expectations, `responded`, Poll type, `manageableByMe`, absence of `createdBy`, and expired/future/isolation exclusion.
- MEAL management list: exact `PageResponse` metadata, explicit `id,desc` ordering, default exclusion of the 90-day archive boundary, explicit archive inclusion, MEAL-only rows, duty ownership, and normal-member denial.
- Poll detail: target campus/ID/status, option count and `sortOrder,id` order, member `myResponse`, admin no-response shape, Poll type, current-develop `manageableByMe`, and absence of `createdBy`.
- Poll results: target/responded/not-responded counts, option order, response count sum, non-anonymous 800 respondents, anonymous zero respondent identities.
- Comments/templates/missing members: exact count and stable order.
- Cross-campus Poll ID through the primary campus path: `404 POLL_NOT_FOUND`.
- A primary-only member querying the isolation campus and its Poll directly: `403 POLL_ACCESS_FORBIDDEN`.
- Exact `startsAt`/`endsAt` values match the atomically shaped manifest. The runner checks all five windows at global preflight and immediately before and after every endpoint phase; crossing a boundary rejects that endpoint report.
- DB snapshots must contain the exact 27-table current-develop set and exact field schema, increasing `capturedAt`, all eight required non-empty planner settings, Flyway version 11, all 27 tables RLS-enabled, zero FORCE RLS/policies, JDBC database-owner continuity, and stable database/server/postmaster identity. Analyze, autoanalyze, vacuum, and autovacuum counts/timestamps must be present (`null` or a valid timestamp) and unchanged. `pg_stat_statements` installed/preloaded/view-available state may be consistently available or unavailable, but malformed or changing state is rejected. PostgreSQL counters are strict nonnegative decimal strings compared with `BigInt`; deltas are also decimal strings, so values beyond `Number.MAX_SAFE_INTEGER` cannot collapse. Cumulative counters must be monotonic, and every table's `n_tup_ins`, `n_tup_upd`, and `n_tup_del` delta must individually equal zero.
- Runtime-integrity and per-container resource sampling interval/max-gap have no defaults and require explicit runtime approval inputs. Given inputs are recorded and used for timestamp order, measured-window boundary coverage, maximum gap, and duration-derived minimum count validation. These checks prepare evidence only and do not enable automatic adoption while the adoption policy is pending.
- Any response correctness failure uses an immutable zero-failure threshold. A non-zero warmup, k6, resource-sampler, activity-sampler, fixture-window, log-capture, or after-DB-snapshot status writes or preserves non-adoptable evidence; missing or malformed latency/throughput/table/resource/activity evidence and any read-path write delta are listed in `rejectionReasons`. Every rejected report stops the sequential runner with a non-zero status.

## Measurement evidence

Each endpoint report includes:

- exact endpoint custom k6 Trend: finite nonnegative `p50 <= p95 <= p99 <= max`, accepting both k6 v2 direct and `values` summary shapes;
- exact endpoint custom request Counter: positive count and positive throughput/second;
- exact endpoint custom failure Rate equal to zero, accepting direct or `values` wrappers and `rate` or `value` while requiring exact agreement when both are present; `passes + fails` must equal the separate request Counter, and zero-failure evidence must be exactly `passes=0`, `fails=requestCount`;
- application/PostgreSQL/Redis CPU and RAM samples whose container set and full immutable IDs are exactly the three attested runtime identities. CPU is finite/nonnegative; memory usage and limit use strict Docker byte units, are safe nonnegative byte counts with `limit > 0` and `used <= limit`, and reported memory percentage is finite in `0..100`. Reports derive canonical memory percent and exact decimal byte totals from parsed usage/limit rather than inventing a tolerance;
- Hibernate SQL log query count and `queriesPerRequest`;
- repeated normalized SQL patterns as loop/N+1 evidence;
- per-table PostgreSQL estimated row counts plus monotonic `seq_scan`, `seq_tup_read`, `idx_scan`, `idx_tup_fetch`, and individually zero write-counter deltas;
- before/after planner and analyze/autoanalyze/vacuum/autovacuum state plus measured-window DB activity and host-port client samples; only the current observer PID, attested app-container client address, measured k6 PID, and Docker proxy processes are excluded, while any other same-name or different session/client makes the report non-adoptable;
- actual app/DB/Redis full container IDs, immutable image IDs, `StartedAt`, PostgreSQL database/address/port/postmaster start time and extension state, Redis `run_id`/port, source revision, Flyway version, image tags, published target port, and Compose `project`, `service`, and `config-hash` labels. Exact continuity is checked before warmup, immediately before measured, immediately after measured, and before report creation.

Query logs and PostgreSQL counters are container-wide. The attested project-scoped canonical lock, runtime activity evidence, disabled scheduler, endpoint-per-process execution, and prohibition on other traffic are part of the evidence contract. The summarizer preserves the first machine-readable rejection as `primaryRejectionReason` and never overwrites an existing report. Even clean evidence remains `automaticAdoption=false` and conditional/non-adoptable. If any condition is not satisfied, do not treat the report as an Issue #196 baseline.

## Runtime preparation (PM exclusive slot only)

`prepare-runtime.sh` is the only Issue #196 lifecycle entrypoint. It requires the exact current app/DB/Redis identities, the clean detached deploy checkout at the approved source revision, and a clean scenario worktree at the exact approved scenario HEAD. It records the tracked prep/override/filter/seed/shape/runner file list and aggregate SHA-256 before lock acquisition, immediately after app recreation, and immediately before the final manifest. Missing, untracked, dirty, symlinked, or drifted tooling fails closed.

The script acquires the canonical project lock and recreates **only** `app` with the base Compose files plus `runtime-evidence.override.yml`; it never performs `down`, restarts PostgreSQL/Redis, prunes, or rolls back automatically. The new app must have a different container ID, `StartedAt`, and config hash, while the exact DB/Redis identities and port binding remain unchanged. It requires:

```text
LOGGING_LEVEL_ORG_HIBERNATE_SQL=DEBUG
SPRING_JPA_PROPERTIES_HIBERNATE_FORMAT_SQL=false
SPRING_JPA_SHOW_SQL=false
LOGGING_LEVEL_ORG_HIBERNATE_ORM_JDBC_BIND=OFF
LOGGING_LEVEL_ORG_HIBERNATE_ORM_JDBC_EXTRACT=OFF
FAITHLOG_SCHEDULER_ENABLED=false
```

The runner pipes the measured Docker log window through `filter-sql-log.mjs` and persists only `org.hibernate.SQL` statement lines. Any bind/extract logger marker rejects the artifact without echoing the offending value. Credentials, tokens, and bind values are not written to the prep manifest, receipt, or SQL artifact.

Before lifecycle work, a fresh `${PERF_RUNTIME_PREP_REPORT_ROOT}/${PERF_RUNTIME_PREP_ATTEMPT_ID}` directory and 0600 `runtime-prep-attempt.json` are exclusively created. After recreation starts, any health, continuity, environment, tooling, or manifest failure also writes a non-overwritable `runtime-prep-rejected.json` with previous/current app identity, observed DB/Redis identity, failed stage, `reusable=false`, `automaticCleanup=false`, and the manual restore handoff. The failed attempt ID is never reused. Success writes only `runtime-prep-manifest.json` in that attempt namespace; seed, shape, and runner re-attest its tooling digest and full runtime identity.

PM supplies every placeholder below from the approved target handoff. No value is inferred by the script:

```bash
PERF_SCENARIO_WORKTREE='<absolute-clean-scenario-worktree>' \
EXPECTED_SCENARIO_HEAD='<approved-full-scenario-head>' \
PERF_RUNTIME_PREP_ATTEMPT_ID='<fresh-prep-attempt-id>' \
PERF_RUNTIME_PREP_REPORT_ROOT='<absolute-ignored-prep-report-root>' \
PERF_RUNTIME_PREP_MANIFEST='<report-root>/<attempt-id>/runtime-prep-manifest.json' \
PERF_DEPLOY_DIR='<absolute-clean-detached-approved-source-checkout>' \
PERF_BASE_COMPOSE_FILE='<deploy-dir>/docker-compose.yml' \
PERF_BASE_OVERRIDE_FILE='<absolute-approved-current-runtime-override>' \
PERF_COMPOSE_ENV_FILE='<absolute-0600-approved-compose-interpolation-env>' \
PERF_APP_READY_TIMEOUT_SECONDS='<approved-timeout-seconds>' \
BASE_URL='<approved-numeric-loopback-url-on-port-28080>' \
APP_CONTAINER='<approved-current-app-container>' \
DB_CONTAINER='<approved-current-db-container>' \
REDIS_CONTAINER='<approved-current-redis-container>' \
EXPECTED_COMPOSE_PROJECT='<approved-compose-project>' \
EXPECTED_APP_SERVICE='<approved-app-service>' \
EXPECTED_DB_SERVICE='<approved-db-service>' \
EXPECTED_REDIS_SERVICE='<approved-redis-service>' \
EXPECTED_SOURCE_REVISION='<approved-current-develop-revision>' \
EXPECTED_CURRENT_APP_CONTAINER_ID='<pre-prep-full-app-container-id>' \
EXPECTED_CURRENT_APP_IMAGE_ID='<pre-prep-full-app-image-id>' \
EXPECTED_CURRENT_APP_STARTED_AT='<pre-prep-app-started-at>' \
EXPECTED_CURRENT_APP_CONFIG_HASH='<pre-prep-app-config-hash>' \
EXPECTED_DB_CONTAINER_ID='<full-db-container-id>' \
EXPECTED_DB_IMAGE_ID='<full-db-image-id>' \
EXPECTED_DB_STARTED_AT='<db-started-at>' \
EXPECTED_DB_CONFIG_HASH='<db-config-hash>' \
EXPECTED_REDIS_CONTAINER_ID='<full-redis-container-id>' \
EXPECTED_REDIS_IMAGE_ID='<full-redis-image-id>' \
EXPECTED_REDIS_STARTED_AT='<redis-started-at>' \
EXPECTED_REDIS_CONFIG_HASH='<redis-config-hash>' \
bash performance/k6/issue-196-prayer-poll-list-baseline/prepare-runtime.sh
```

Operational source provenance remains the PM-approved clean detached deploy checkout at `6796ed146244d8f3f5b5dd7048ebe16865084a97` with an image creation time after that commit. The image has no OCI revision label or `git.properties`, so image-alone cryptographic source proof remains unavailable and is recorded as a limitation rather than silently claimed.

The Compose interpolation file is a PM-provided, absolute, non-symlink regular file with no group/other permissions. The compose child starts from an empty environment and receives only `PATH`, `HOME`, Docker config/temp locations plus `--env-file`; caller credentials, tokens, and `SPRING_*`/JWT/Firebase secret values are not inherited. Before/after `Config.Env` is canonicalized in memory, the five approved instrumentation variables are the only permitted delta, and the manifest stores only equal sanitized SHA-256 digests plus that non-secret allowlist. Supply credentials through the approved runtime-only Compose env file or secret manager; do not create or commit a repository `.env` file.

### 1. Create an immutable fixture

```bash
FIXTURE_RUN_ID=i196-20260714-a \
PERF_SCENARIO_WORKTREE='<same-absolute-scenario-worktree>' \
EXPECTED_SCENARIO_HEAD='<same-approved-scenario-head>' \
RUNTIME_PREP_MANIFEST='<successful-runtime-prep-manifest>' \
PERF_WEEK_START_DATE='<approved-monday-yyyy-mm-dd>' \
BASE_URL='<approved-loopback-base-url>' \
APP_CONTAINER='<approved-app-container>' \
DB_CONTAINER='<approved-db-container>' \
REDIS_CONTAINER='<approved-redis-container>' \
EXPECTED_APP_SERVICE='<approved-app-service-label>' \
EXPECTED_DB_SERVICE='<approved-db-service-label>' \
EXPECTED_REDIS_SERVICE='<approved-redis-service-label>' \
EXPECTED_APP_IMAGE='<approved-exact-app-image>' \
EXPECTED_APP_IMAGE_ID='<approved-immutable-app-image-id>' \
EXPECTED_DB_IMAGE='<approved-exact-db-image>' \
EXPECTED_DB_IMAGE_ID='<approved-immutable-db-image-id>' \
EXPECTED_REDIS_IMAGE='<approved-exact-redis-image>' \
EXPECTED_REDIS_IMAGE_ID='<approved-immutable-redis-image-id>' \
EXPECTED_REDIS_PORT='<approved-redis-port>' \
EXPECTED_FLYWAY_VERSION='<approved-flyway-version>' \
EXPECTED_SOURCE_REVISION='<approved-source-revision>' \
PERF_ADMIN_EMAIL='<runtime-admin-email>' \
PERF_ADMIN_PASSWORD='<runtime-admin-password>' \
PERF_MEMBER_PASSWORD='<runtime-generated-member-password>' \
PERF_DB_USER='<runtime-db-user>' \
PERF_DB_NAME='<runtime-db-name>' \
PERF_DB_PASSWORD='<runtime-db-password>' \
node performance/k6/issue-196-prayer-poll-list-baseline/seed-fixture.mjs
```

The seed is create-only and intentionally has no cleanup path. `fixtureRunId` is lowercase-only to prevent case-folded email collisions. If it fails partway, keep the evidence and use a new `fixtureRunId`; do not modify or delete the partial rows without separate user approval.

### 2. Shape only current-run Poll windows

```bash
FIXTURE_RUN_ID=i196-20260714-a \
PERF_SCENARIO_WORKTREE='<same-absolute-scenario-worktree>' \
EXPECTED_SCENARIO_HEAD='<same-approved-scenario-head>' \
BASE_URL='<approved-loopback-base-url>' \
APP_CONTAINER='<approved-app-container>' \
DB_CONTAINER='<approved-db-container>' \
REDIS_CONTAINER='<approved-redis-container>' \
EXPECTED_APP_SERVICE='<approved-app-service-label>' \
EXPECTED_DB_SERVICE='<approved-db-service-label>' \
EXPECTED_REDIS_SERVICE='<approved-redis-service-label>' \
EXPECTED_APP_IMAGE='<approved-exact-app-image>' \
EXPECTED_APP_IMAGE_ID='<approved-immutable-app-image-id>' \
EXPECTED_DB_IMAGE='<approved-exact-db-image>' \
EXPECTED_DB_IMAGE_ID='<approved-immutable-db-image-id>' \
EXPECTED_REDIS_IMAGE='<approved-exact-redis-image>' \
EXPECTED_REDIS_IMAGE_ID='<approved-immutable-redis-image-id>' \
EXPECTED_REDIS_PORT='<approved-redis-port>' \
EXPECTED_FLYWAY_VERSION='<approved-flyway-version>' \
EXPECTED_SOURCE_REVISION='<approved-source-revision>' \
PERF_DB_USER='<runtime-db-user>' \
PERF_DB_NAME='<runtime-db-name>' \
PERF_DB_PASSWORD='<runtime-db-password>' \
bash performance/k6/issue-196-prayer-poll-list-baseline/shape-fixture.sh
```

### 3. Run endpoint phases sequentially

Warmup/measured VUS/duration, sampling interval/max-gap, and target identity have no hidden defaults. The summarizer validates the exact positive runtime sampling values and `maxGap >= interval`; it does not substitute or require 1/2 seconds. A future approved measurement session must provide those values, a new `executionRunId`, and an explicit mode. A clean `conditional-not-adoptable` endpoint report does not abort the explicit sequential scope; the runner collects every requested endpoint and exits 2 only after the scope is complete. A rejected or malformed report still stops at the first failed endpoint. Automatic adoption remains disabled.

`PERF_REPORT_ROOT` controls only the optional local artifact base; it is not a target, workload, or credential fallback. The fake orchestration suite sets it to a temporary directory so tests do not depend on repository `build/reports` permissions or stale artifacts.

```bash
FIXTURE_RUN_ID=i196-20260714-a \
EXECUTION_RUN_ID=i196-exec-20260714-a \
PERF_SCENARIO_WORKTREE='<same-absolute-scenario-worktree>' \
EXPECTED_SCENARIO_HEAD='<same-approved-scenario-head>' \
PERF_REPORT_ROOT='<optional-local-artifact-base>' \
BASE_URL='<approved-loopback-base-url>' \
APP_CONTAINER='<approved-app-container>' \
DB_CONTAINER='<approved-db-container>' \
REDIS_CONTAINER='<approved-redis-container>' \
EXPECTED_APP_SERVICE='<approved-app-service-label>' \
EXPECTED_DB_SERVICE='<approved-db-service-label>' \
EXPECTED_REDIS_SERVICE='<approved-redis-service-label>' \
EXPECTED_APP_IMAGE='<approved-exact-app-image>' \
EXPECTED_APP_IMAGE_ID='<approved-immutable-app-image-id>' \
EXPECTED_DB_IMAGE='<approved-exact-db-image>' \
EXPECTED_DB_IMAGE_ID='<approved-immutable-db-image-id>' \
EXPECTED_REDIS_IMAGE='<approved-exact-redis-image>' \
EXPECTED_REDIS_IMAGE_ID='<approved-immutable-redis-image-id>' \
EXPECTED_REDIS_PORT='<approved-redis-port>' \
EXPECTED_FLYWAY_VERSION='<approved-flyway-version>' \
EXPECTED_SOURCE_REVISION='<approved-source-revision>' \
WARMUP_VUS='<approved-warmup-vus>' \
WARMUP_DURATION='<approved-warmup-duration>' \
MEASURED_VUS='<approved-measured-vus>' \
MEASURED_DURATION='<approved-measured-duration>' \
SAMPLING_INTERVAL_SECONDS='<approved-sampling-interval>' \
SAMPLING_MAX_GAP_SECONDS='<approved-sampling-max-gap>' \
PERF_ADMIN_EMAIL='<runtime-admin-email>' \
PERF_ADMIN_PASSWORD='<runtime-admin-password>' \
PERF_MEMBER_PASSWORD='<runtime-generated-member-password>' \
PERF_DB_USER='<runtime-db-user>' \
PERF_DB_NAME='<runtime-db-name>' \
PERF_DB_PASSWORD='<runtime-db-password>' \
bash performance/k6/issue-196-prayer-poll-list-baseline/run-baseline.sh all
```

The mode argument is required. Pass `all` explicitly for the complete sequential scope, or `prayer`, `poll-member`, `poll-admin`, or `poll-duty` only when the PM explicitly wants that partial scope. Partial reports must not be presented as a complete baseline.

### PM execution and stop/restore handoff

One exclusive slot runs, in order: read-only target re-attestation → app-only runtime prep → fresh namespace seed → one-shot shape → explicit `all` mode (Prayer, Poll member, Poll admin, Poll duty; 27 endpoints sequentially). Expected elapsed time is about 2–3.5 hours, driven by the separately approved warmup/measured values. Runtime prep writes the app lifecycle plus its receipts; seed creates only fresh fixture rows; shape updates only the eight rows owned by that fresh fixture; endpoint phases are read-only apart from application/runtime statistics. Stop immediately on the first prep rejection, seed/shape nonzero, runtime/tooling continuity drift, credential/bind logger evidence, rejected/malformed report, or operational child failure. Preserve every receipt/report and do not reuse IDs.

No automatic cleanup or rollback is authorized. After a partial prep failure, follow the rejection receipt's restore handoff: recreate only the app from the same approved base Compose files without `runtime-evidence.override.yml`, then re-attest the restored app and unchanged PostgreSQL/Redis. After a successful baseline, PM may either retain the instrumented app for the final integration measurement or perform the same app-only restore; `down`, DB/Redis recreation, and prune remain forbidden.

## Static verification

These commands do not seed, run k6, access Docker, or access a database:

```bash
node --test performance/k6/issue-196-prayer-poll-list-baseline/scenario-contract.test.mjs
node --check performance/k6/issue-196-prayer-poll-list-baseline/fixture-contract.mjs
node --check performance/k6/issue-196-prayer-poll-list-baseline/seed-fixture.mjs
node --check performance/k6/issue-196-prayer-poll-list-baseline/scenario.js
node --check performance/k6/issue-196-prayer-poll-list-baseline/token-lifetime.mjs
node --check performance/k6/issue-196-prayer-poll-list-baseline/activity-sample.mjs
node --check performance/k6/issue-196-prayer-poll-list-baseline/redis-runtime-identity.mjs
node --check performance/k6/issue-196-prayer-poll-list-baseline/validate-published-target.mjs
node --check performance/k6/issue-196-prayer-poll-list-baseline/validate-runtime-identity.mjs
node --check performance/k6/issue-196-prayer-poll-list-baseline/summarize-run.mjs
node --check performance/k6/issue-196-prayer-poll-list-baseline/k6-rate-contract.mjs
node --check performance/k6/issue-196-prayer-poll-list-baseline/k6-rate-shape-probe.js
node --check performance/k6/issue-196-prayer-poll-list-baseline/runtime-prep-contract.mjs
node --check performance/k6/issue-196-prayer-poll-list-baseline/runtime-env-attestation.mjs
node --check performance/k6/issue-196-prayer-poll-list-baseline/tooling-provenance.mjs
node --check performance/k6/issue-196-prayer-poll-list-baseline/filter-sql-log.mjs
bash -n performance/k6/issue-196-prayer-poll-list-baseline/prepare-runtime.sh
bash -n performance/k6/issue-196-prayer-poll-list-baseline/shape-fixture.sh
bash -n performance/k6/issue-196-prayer-poll-list-baseline/run-baseline.sh
node --test performance/k6/issue-196-prayer-poll-list-baseline/runtime-prep-orchestration.test.mjs
node --test performance/k6/issue-196-prayer-poll-list-baseline/k6-rate-contract.test.mjs
```

This session syntax-checks `scenario.js` without executing k6; the contract test separately fixes its endpoint, metric, sequencing, correctness, current-develop source/Flyway/RLS identity, app/DB/Redis continuity, pgss state, BigInt counter, and resource evidence markers. Test-code auditing across performance issues may run in parallel, but actual shared-stack seed/load measurement remains PM-controlled and strictly sequential.
