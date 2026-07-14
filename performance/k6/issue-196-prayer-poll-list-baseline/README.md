# Issue #196 Prayer/Poll Read Baseline Scenario

This directory prepares the **before** scenario for Issue #196. It does not contain a production N+1 fix and it does not claim measured baseline or improvement numbers.

Status: `scenario-ready / not-measured`

## Scope and safety boundary

- Local Docker only. Remote URLs are rejected by both seed and k6 scripts.
- The stable `datasetId` is `issue-196-prayer-poll-list-v1`; every preparation requires a new immutable `fixtureRunId`.
- The fixture creates new campuses and new rows. It never deletes fixture data and never updates/deletes rows that existed before the current `fixtureRunId`.
- `shape-fixture.sh` atomically shapes all five Poll rows created by the current run. It verifies exact IDs/campus plus deterministic run titles, rejects an already-shaped manifest, and rolls back all five updates if any ownership check misses.
- A 0600 `shape-attempted` receipt makes shaping one-shot even if the process stops after the database statement but before manifest persistence. Any shaping failure requires a new `fixtureRunId`.
- Passwords, DB credentials, and Access Tokens are runtime-only. The manifest records IDs and generated test emails, but not credentials or tokens.
- After app/DB label attestation, seed, shaping, and load all acquire the same canonical `/tmp/faithlog-performance-{actualComposeProject}.lock`. The path has no caller override, and the runner also refuses to start while another k6 process exists.
- The approved `faithlog-latest` tag is fixed, not caller-overridable. Seed records the immutable app image ID and published target port; shaping and baseline require the same image ID, Compose project/service/config-hash labels, and `BASE_URL` port before touching the fixture or measuring it.
- The runner never starts, stops, rebuilds, or prunes Docker resources. It uses only `inspect`, `logs`, `stats`, and read-only PostgreSQL statistics queries.
- Every measurement requires an explicit immutable `executionRunId`. Reports are written to a new ignored `build/reports/k6/issue-196/{fixtureRunId}/{executionRunId}/` directory, and an existing path is never deleted, reused, or overwritten.
- The operator must explicitly set `EXCLUSIVE_WINDOW_CONFIRMED=true` only after reserving the shared stack from frontend, QA, and other request traffic. Sampling can detect observed conflicts but cannot prove that a short transient request never occurred; this declaration is therefore an additional adoption gate, not a replacement for the canonical lock or activity evidence.

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

The five primary Poll visibility cases are:

1. `open`: member/admin visible, `OPEN`.
2. `closed_member_visible`: ended 2 days ago, member/admin visible, `CLOSED`.
3. `closed_admin_only`: ended 5 days ago, member hidden/admin visible, `CLOSED`, anonymous results.
4. `closed_expired`: ended 8 days ago, hidden from member/admin, `CLOSED`.
5. `scheduled_future`: future window, hidden from member/admin, `SCHEDULED`.

## Actual read flows

The top-level runner starts a separate k6 process for every endpoint. It completes all Prayer phases before Poll member phases, then Poll admin phases. Prayer and Poll traffic is therefore never mixed in one load segment.

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

## Correctness gates

Every load response is checked, not merely timed.

- Prayer: exact season/campus/status, 40-group and 1,000-member counts, group/member order, 800/200 submission split, member `myGroupId`, admin all-editable behavior, member own-item-only `editable`, and isolation IDs absent.
- Poll list: exact descending ID order, member 3-day vs admin 7-day visibility, OPEN/CLOSED/SCHEDULED expectations, `responded`, expired/future/isolation exclusion.
- Poll detail: target campus/ID/status, option count and `sortOrder,id` order, member `myResponse`, admin no-response shape.
- Poll results: target/responded/not-responded counts, option order, response count sum, non-anonymous 800 respondents, anonymous zero respondent identities.
- Comments/templates/missing members: exact count and stable order.
- Cross-campus Poll ID through the primary campus path: `404 POLL_NOT_FOUND`.
- A primary-only member querying the isolation campus and its Poll directly: `403 POLL_ACCESS_FORBIDDEN`.
- Exact `startsAt`/`endsAt` values match the atomically shaped manifest. The runner checks all five windows at global preflight and immediately before and after every endpoint phase; crossing a boundary rejects that endpoint report.
- DB snapshots must contain the exact required table set and exact field schema, increasing `capturedAt`, all eight required non-empty planner settings, and a stable database/server/postmaster identity. Analyze, autoanalyze, vacuum, and autovacuum counts/timestamps must be present (`null` or a valid timestamp) and unchanged. Every numeric counter must be finite/nonnegative, cumulative counters must be monotonic, and every table's `n_tup_ins`, `n_tup_upd`, and `n_tup_del` delta must individually equal zero; missing fields, counter reset, or cross-table/cross-counter cancellation cannot pass.
- Runtime-integrity and per-container resource samples use the recorded 1-second sampling contract with a 2-second maximum gap. Their timestamps must be valid, strictly increasing, inside the measured window, cover both boundaries within the maximum gap, and meet the duration-derived minimum count. A one-sample window or an unsampled middle gap is non-adoptable.
- Any response correctness failure uses an immutable zero-failure threshold. A non-zero warmup, k6, resource-sampler, activity-sampler, fixture-window, log-capture, or after-DB-snapshot status writes or preserves non-adoptable evidence; missing or malformed latency/throughput/table/resource/activity evidence and any read-path write delta are listed in `rejectionReasons`. Every rejected report stops the sequential runner with a non-zero status.

## Measurement evidence

Each endpoint report includes:

- exact endpoint custom k6 Trend: finite nonnegative `p50 <= p95 <= p99 <= max`, accepting both k6 v2 direct and `values` summary shapes;
- exact endpoint custom request Counter: positive count and positive throughput/second;
- exact endpoint custom failure Rate equal to zero;
- application/PostgreSQL container CPU and RAM samples;
- Hibernate SQL log query count and `queriesPerRequest`;
- repeated normalized SQL patterns as loop/N+1 evidence;
- per-table PostgreSQL estimated row counts plus monotonic `seq_scan`, `seq_tup_read`, `idx_scan`, `idx_tup_fetch`, and individually zero write-counter deltas;
- before/after planner and analyze/autoanalyze/vacuum/autovacuum state plus measured-window DB activity and host-port client samples; only the current observer PID, attested app-container client address, measured k6 PID, and Docker proxy processes are excluded, while any other same-name or different session/client makes the report non-adoptable;
- actual app/DB container IDs, immutable image IDs, `StartedAt`, PostgreSQL database/address/port/postmaster start time, app image tag, published target port, and Compose `project`, `service`, and `config-hash` labels. Exact continuity is checked before warmup, immediately before measured, immediately after measured, and before report adoption.

Query logs and PostgreSQL counters are container-wide. The attested project-scoped canonical lock, runtime activity evidence, disabled scheduler, endpoint-per-process execution, and prohibition on other traffic are part of the evidence contract. If any of those conditions are not satisfied, do not treat the report as an Issue #196 baseline.

## Runtime preparation (do not run in this development session)

The PM-approved measurement session must start the existing `faithlog-latest` stack externally. Seed and runner use read-only Docker inspection to verify it; neither changes its lifecycle. The application container must already have:

```text
LOGGING_LEVEL_ORG_HIBERNATE_SQL=DEBUG
SPRING_JPA_PROPERTIES_HIBERNATE_FORMAT_SQL=false
FAITHLOG_SCHEDULER_ENABLED=false
```

Supply all credentials directly in the runtime shell or an approved secret manager. Do not create/source a repository `.env` file.

### 1. Create an immutable fixture

```bash
FIXTURE_RUN_ID=i196-20260714-a \
PERF_ADMIN_EMAIL='<runtime-admin-email>' \
PERF_ADMIN_PASSWORD='<runtime-admin-password>' \
PERF_MEMBER_PASSWORD='<runtime-generated-member-password>' \
node performance/k6/issue-196-prayer-poll-list-baseline/seed-fixture.mjs
```

The seed is create-only and intentionally has no cleanup path. `fixtureRunId` is lowercase-only to prevent case-folded email collisions. If it fails partway, keep the evidence and use a new `fixtureRunId`; do not modify or delete the partial rows without separate user approval.

### 2. Shape only current-run Poll windows

```bash
FIXTURE_RUN_ID=i196-20260714-a \
PERF_DB_USER='<runtime-db-user>' \
PERF_DB_NAME='<runtime-db-name>' \
PERF_DB_PASSWORD='<runtime-db-password>' \
bash performance/k6/issue-196-prayer-poll-list-baseline/shape-fixture.sh
```

### 3. Run endpoint phases sequentially

Warmup and measured VUS/duration have no hidden defaults. The approved measurement session must provide all four values, a new `executionRunId`, and an explicit mode. Every endpoint finishes warmup before measured DB/log/resource/activity evidence begins. Warmup failure starts no measured phase. Fresh tokens are issued for each phase and their JWT `exp` must cover that phase plus a fixed safety margin; raw tokens are neither printed nor persisted.

```bash
FIXTURE_RUN_ID=i196-20260714-a \
EXECUTION_RUN_ID=i196-exec-20260714-a \
WARMUP_VUS='<approved-warmup-vus>' \
WARMUP_DURATION='<approved-warmup-duration>' \
MEASURED_VUS='<approved-measured-vus>' \
MEASURED_DURATION='<approved-measured-duration>' \
EXCLUSIVE_WINDOW_CONFIRMED=true \
PERF_ADMIN_EMAIL='<runtime-admin-email>' \
PERF_ADMIN_PASSWORD='<runtime-admin-password>' \
PERF_MEMBER_PASSWORD='<runtime-generated-member-password>' \
PERF_DB_USER='<runtime-db-user>' \
PERF_DB_NAME='<runtime-db-name>' \
PERF_DB_PASSWORD='<runtime-db-password>' \
bash performance/k6/issue-196-prayer-poll-list-baseline/run-baseline.sh all
```

The mode argument is required. Pass `all` explicitly for the complete sequential scope, or `prayer`, `poll-member`, or `poll-admin` only when the PM explicitly wants that partial scope. Partial reports must not be presented as a complete baseline.

## Static verification

These commands do not seed, run k6, access Docker, or access a database:

```bash
node --test performance/k6/issue-196-prayer-poll-list-baseline/scenario-contract.test.mjs
node --check performance/k6/issue-196-prayer-poll-list-baseline/fixture-contract.mjs
node --check performance/k6/issue-196-prayer-poll-list-baseline/seed-fixture.mjs
node --check performance/k6/issue-196-prayer-poll-list-baseline/scenario.js
node --check performance/k6/issue-196-prayer-poll-list-baseline/token-lifetime.mjs
node --check performance/k6/issue-196-prayer-poll-list-baseline/activity-sample.mjs
node --check performance/k6/issue-196-prayer-poll-list-baseline/summarize-run.mjs
bash -n performance/k6/issue-196-prayer-poll-list-baseline/shape-fixture.sh
bash -n performance/k6/issue-196-prayer-poll-list-baseline/run-baseline.sh
```

This session syntax-checks `scenario.js` without executing k6; the contract test separately fixes its endpoint, metric, sequencing, and correctness markers.
