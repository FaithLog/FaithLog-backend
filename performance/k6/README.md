# FaithLog k6 Performance Tests

Issue #90 uses k6 to collect reproducible local performance numbers. Defaults target local Docker Compose only.

## Local Baseline

1. Start the local stack.

```bash
docker compose up -d --build postgres redis app
```

2. Prepare or choose a local test account that can log in. For read scenarios beyond auth and my campus list, the account should already belong to a campus with representative local data.

3. Run the default read baseline.

```bash
BASE_URL=http://localhost:8080 \
PERF_EMAIL=perf.member@example.com \
PERF_PASSWORD=test-only-password \
k6 run performance/k6/read-baseline.js
```

The default load is `VUS=30`, `DURATION=5m`, `THINK_TIME_SECONDS=1`, and `INCLUDE=auth,campuses`.

Docker-based k6 can target the host-mapped app port with `host.docker.internal`.

```bash
mkdir -p build/reports/k6
docker run --rm \
  -v "$PWD:/work" \
  -w /work \
  -e BASE_URL=http://host.docker.internal:8080 \
  -e PERF_EMAIL=perf.member@example.com \
  -e PERF_PASSWORD=test-only-password \
  grafana/k6:latest run --summary-export build/reports/k6/read-baseline.json performance/k6/read-baseline.js
```

## Broader Read Scenario

Use `INCLUDE` to opt into additional read endpoints after local data exists.

```bash
BASE_URL=http://localhost:8080 \
PERF_EMAIL=perf.member@example.com \
PERF_PASSWORD=test-only-password \
CAMPUS_ID=1 \
POLL_ID=1 \
WEEK_START_DATE=2026-06-22 \
YEAR=2026 \
MONTH=6 \
INCLUDE=auth,campuses,devotions,billing,polls,prayers \
k6 run --summary-export build/reports/k6/read-baseline.json performance/k6/read-baseline.js
```

`admin-dashboard` is intentionally separate because it requires a campus manager or service admin account.

```bash
BASE_URL=http://localhost:8080 \
PERF_EMAIL=perf.admin@example.com \
PERF_PASSWORD=test-only-password \
CAMPUS_ID=1 \
WEEK_START_DATE=2026-06-22 \
INCLUDE=auth,campuses,admin-dashboard \
k6 run --summary-export build/reports/k6/admin-dashboard-baseline.json performance/k6/read-baseline.js
```

## Cloud Run Smoke And Baseline

Remote runs must be explicit and must store results separately from local Docker numbers. Start with health-only smoke when no perf account is available.

```bash
BASE_URL=https://faithlog-549871256004.asia-northeast3.run.app \
ALLOW_REMOTE_LOAD=true \
VUS=1 \
DURATION=30s \
INCLUDE=health \
k6 run --summary-export build/reports/k6/cloud-run-health-smoke.json performance/k6/read-baseline.js
```

Authenticated read baselines require a dedicated perf account and should stay read-only unless the PM approves a write scenario.

Auth-heavy runs include login on every iteration. Use them only to measure login, BCrypt, and JWT issuance pressure.

```bash
BASE_URL=https://faithlog-549871256004.asia-northeast3.run.app \
ALLOW_REMOTE_LOAD=true \
PERF_EMAIL=perf.member@example.com \
PERF_PASSWORD=test-only-password \
AUTH_PATTERN=auth-heavy \
VUS=10 \
DURATION=3m \
INCLUDE=auth,campuses,admin-campuses \
k6 run --summary-export build/reports/k6/cloud-run-auth-heavy-vus10-3m.json performance/k6/read-baseline.js
```

Steady-state authenticated read runs log in once during setup and then reuse the same Access Token for read requests. Do not include `auth` in `INCLUDE` for this mode.

```bash
BASE_URL=https://faithlog-549871256004.asia-northeast3.run.app \
ALLOW_REMOTE_LOAD=true \
PERF_EMAIL=perf.member@example.com \
PERF_PASSWORD=test-only-password \
AUTH_PATTERN=steady-state \
CAMPUS_ID=1 \
POLL_ID=1 \
VUS=10 \
DURATION=3m \
INCLUDE=campuses,admin-campuses,admin-dashboard,devotions,billing,polls,prayers \
k6 run --summary-export build/reports/k6/cloud-run-steady-state-read-vus10-3m.json performance/k6/read-baseline.js
```

Campus-dependent scenarios such as `admin-dashboard`, `devotions`, `billing`, `polls`, and `prayers` require `CAMPUS_ID` or an account whose `/api/v1/campuses/me` response has at least one campus. The script fails early instead of silently skipping those scenarios when no campus is available.

If production has no representative data, create only PM-approved `PERF_` prefixed data with the seed script. The script uses actual APIs, caps member creation at 50, writes a local manifest under `build/reports/perf-data/`, and requires `ALLOW_REMOTE_LOAD=true` for non-local targets.

```bash
BASE_URL=https://faithlog-549871256004.asia-northeast3.run.app \
ALLOW_REMOTE_LOAD=true \
PERF_ADMIN_EMAIL=perf.admin@example.com \
PERF_ADMIN_PASSWORD=admin-password-from-secret-store \
PERF_MEMBER_PASSWORD=member-password-from-secret-store \
PERF_DATASET_ID=PERF_YYYYMMDD_CLOUDRUN_A \
PERF_MEMBER_COUNT=30 \
node performance/k6/seed-cloud-run-perf-data.mjs
```

The approved expanded Cloud Run read baseline is capped at `VUS=30` and `DURATION=5m`. Do not exceed that cap, run write-heavy scenarios, or generate payment/charge/notification side effects without separate PM approval.

## Remote Guard

The script fails when `BASE_URL` is not localhost unless `ALLOW_REMOTE_LOAD=true` is set. Do not run high-load tests against Cloud Run or any production database without explicit user approval. For approved remote smoke checks, use low values such as `VUS=1 DURATION=30s`.

## Metrics To Record

Record the run conditions with every result:

- `BASE_URL`
- `VUS`, `DURATION`, `THINK_TIME_SECONDS`, `INCLUDE`
- local machine/runtime and Docker Compose profile
- dataset size, such as campus members, polls, charge items, devotion records, and prayer submissions
- p50, p95, p99, avg, RPS, and failure rate from k6 output
