# Issue #196 Prayer/Poll Read Baseline Scenario

This directory prepares the **before** scenario for Issue #196. It does not contain a production N+1 fix and it does not claim measured baseline or improvement numbers.

Status: `scenario-ready / not-measured`

## Scope and safety boundary

- Local Docker only. Remote URLs are rejected by both seed and k6 scripts.
- The stable `datasetId` is `issue-196-prayer-poll-list-v1`; every preparation requires a new immutable `fixtureRunId`.
- The fixture creates new campuses and new rows. It never deletes fixture data and never updates/deletes rows that existed before the current `fixtureRunId`.
- `shape-fixture.sh` changes only three Poll rows created by the current run, guarded by the exact `id + campus_id + title` recorded in its manifest. It exists only to reproduce 2-day, 5-day, and 8-day visibility windows.
- Passwords, DB credentials, and Access Tokens are runtime-only. The manifest records IDs and generated test emails, but not credentials or tokens.
- The shared lock `/tmp/faithlog-performance-global.lock` blocks parallel seed, shaping, and load runs. The runner also refuses to start while another k6 process exists.
- The runner never starts, stops, rebuilds, or prunes Docker resources. It uses only `inspect`, `logs`, `stats`, and read-only PostgreSQL statistics queries.
- Reports are written under ignored `build/reports/k6/issue-196/{fixtureRunId}/`.

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
| `poll-member` | list/detail/results/comments/isolation | member credentials and the common Poll APIs |
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
- Read measurement must produce zero `n_tup_ins + n_tup_upd + n_tup_del` delta. A non-zero write delta fails report generation.

## Measurement evidence

Each endpoint report includes:

- custom k6 Trend: p50, p95, p99, max;
- custom request Counter: count and throughput/second;
- custom failure Rate;
- application/PostgreSQL container CPU and RAM samples;
- Hibernate SQL log query count and `queriesPerRequest`;
- repeated normalized SQL patterns as loop/N+1 evidence;
- per-table PostgreSQL estimated row counts plus `seq_scan`, `seq_tup_read`, `idx_scan`, `idx_tup_fetch`, and write-counter deltas;
- actual Docker image and Compose `project`, `service`, and `config-hash` labels.

Query logs and PostgreSQL counters are container-wide. The fixed global lock, disabled scheduler, endpoint-per-process execution, and prohibition on other traffic are part of the evidence contract. If any of those conditions are not satisfied, do not treat the report as an Issue #196 baseline.

## Runtime preparation (do not run in this development session)

The PM-approved measurement session must start the existing `faithlog-latest` stack externally. The runner will only verify and record it. The application container must already have:

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

The seed is create-only and intentionally has no cleanup path. If it fails partway, keep the evidence and use a new `fixtureRunId`; do not modify or delete the partial rows without separate user approval.

### 2. Shape only current-run Poll windows

```bash
FIXTURE_RUN_ID=i196-20260714-a \
PERF_DB_USER='<runtime-db-user>' \
PERF_DB_NAME='<runtime-db-name>' \
PERF_DB_PASSWORD='<runtime-db-password>' \
bash performance/k6/issue-196-prayer-poll-list-baseline/shape-fixture.sh
```

### 3. Run endpoint phases sequentially

`VUS` and `DURATION` have no hidden default. The approved measurement session must provide them explicitly.

```bash
FIXTURE_RUN_ID=i196-20260714-a \
VUS='<approved-vus>' \
DURATION='<approved-duration>' \
EXPECTED_APP_IMAGE=faithlog-latest \
PERF_ADMIN_EMAIL='<runtime-admin-email>' \
PERF_ADMIN_PASSWORD='<runtime-admin-password>' \
PERF_MEMBER_PASSWORD='<runtime-generated-member-password>' \
PERF_DB_USER='<runtime-db-user>' \
PERF_DB_NAME='<runtime-db-name>' \
PERF_DB_PASSWORD='<runtime-db-password>' \
bash performance/k6/issue-196-prayer-poll-list-baseline/run-baseline.sh all
```

Use `prayer`, `poll-member`, or `poll-admin` instead of `all` only when the PM explicitly wants a partial rerun. Partial reports must not be presented as a complete baseline.

## Static verification

These commands do not seed, run k6, access Docker, or access a database:

```bash
node --test performance/k6/issue-196-prayer-poll-list-baseline/scenario-contract.test.mjs
node --check performance/k6/issue-196-prayer-poll-list-baseline/fixture-contract.mjs
node --check performance/k6/issue-196-prayer-poll-list-baseline/seed-fixture.mjs
node --check performance/k6/issue-196-prayer-poll-list-baseline/scenario.js
node --check performance/k6/issue-196-prayer-poll-list-baseline/summarize-run.mjs
bash -n performance/k6/issue-196-prayer-poll-list-baseline/shape-fixture.sh
bash -n performance/k6/issue-196-prayer-poll-list-baseline/run-baseline.sh
```

This session syntax-checks `scenario.js` without executing k6; the contract test separately fixes its endpoint, metric, sequencing, and correctness markers.
