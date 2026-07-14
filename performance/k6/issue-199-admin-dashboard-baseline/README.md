# Issue #199 관리자 대시보드 before 시나리오

상태: **scenario-ready/not-measured**

이 디렉터리는 `GET /api/v1/admin/campuses/{campusId}/dashboard/summary`의 production 집계 구현을 변경하기 전에 사용할 측정 계약만 준비한다. 이번 개발 세션에서는 seed, k6, Docker, PostgreSQL 명령을 실행하지 않았고 실제 baseline 수치나 개선 성과를 기록하지 않는다. production Java/API/권한/응답/오류/트랜잭션/Entity/DB/Flyway/의존성 변경도 없다.

## 실제 API 계약

- Method/path: `GET /api/v1/admin/campuses/{campusId}/dashboard/summary`
- 선택 query: `weekStartDate=YYYY-MM-DD`
- query가 없으면 서버가 `Asia/Seoul` 기준 현재 주 월요일을 사용한다.
- query가 있으면 월요일이어야 하며, 월요일이 아니면 `400 DEVOTION_INVALID_WEEK_START_DATE`다.
- 허용: service `ADMIN`, 대상 campus의 ACTIVE `MINISTER`, `ELDER`, `CAMPUS_LEADER`
- 거부: 일반 `MEMBER`, 다른 campus 관리자, service `MANAGER` 역할만 가진 사용자. `403 ADMIN_DASHBOARD_ACCESS_FORBIDDEN`을 사용한다.

응답 정확성은 아래를 모두 exact match로 검증한다.

- `members`: `activeCount`, `inactiveCount`, `adminCount`
- `devotion`: `weekStartDate`, `submittedCount`, `missingCount`, `submitRate`
- `charges`: `UNPAID` status basis의 `unpaidAmount`, `unpaidMemberCount`, `PENALTY`/`COFFEE` category 합계. `MEAL`은 현재 dashboard 집계에서 제외된다.
- `polls`: non-MEAL `openCount`, 최근 7일 `recentlyClosedCount`, OPEN poll별 ACTIVE member response count로 계산한 `missingResponseCount`
- campus isolation: campus-scoped manager credential로 `isolationCampusId` 요청이 403인지 검증한다. service `ADMIN`은 모든 campus 접근이 허용되므로 isolation actor로 사용하지 않는다.

## 프론트 초기 진입 순서 근거

FaithLog frontend `develop`의 `aba1ab07bcb54c1df85ecf53238f4cb0484c2df3`에서 확인했다.

1. `POST /api/v1/auth/login`
2. `src/auth/session.ts`의 `establishSession`이 `GET /api/v1/users/me`와 `GET /api/v1/campuses/me`를 `Promise.all`로 병렬 호출한다.
3. `src/admin/AdminScreen.tsx`의 초기 `loadAdmin`은 dashboard summary, campus members, duty assignments, prayer board를 병렬 호출하고 campus detail 기반 invite code 조회도 시작한다.

#199는 집계 endpoint의 before 비용을 독립 측정해야 한다. `prepare-runtime-token.mjs`가 각 dataset mode 시작 시 1~2번 순서를 재현하고 fresh Access Token을 report/file이 아닌 runner의 shell memory에만 반환한다. 이후 해당 mode의 warmup/measured k6 setup은 HTTP 요청 없이 이 토큰을 받아, iteration에서 3번 fan-out 중 dashboard summary 하나만 호출한다. members/duty/prayer/invite 호출은 실제 프론트 동작 근거로 문서화하지만 #199 latency와 DB counter를 오염시키지 않도록 측정 부하에서 제외한다.

## 입력 manifest와 dataset mode

`input-manifest.example.json`은 구조 예시일 뿐 실제 fixture나 측정 결과가 아니다. 실행 시 별도의 ignored manifest를 만들고 `INPUT_MANIFEST`로 전달한다.

- `datasetId`: 공통 데이터셋의 수명과 identity
- 각 mode의 `fixtureRunId`: #199 실행 단위 identity
- `empty`: 관리자 1명만 있는 domain-empty campus
- `small`: 작은 기능 데이터 campus
- `thousand`: ACTIVE member 정확히 1,000명인 공통 campus

`thousand.fixtureReferences`는 아래 여섯 공통 fixture manifest의 `fixtureRunId`와 `manifestPath`를 참조한다.

- devotion
- penalty
- coffee
- meal
- poll
- prayer

#199에는 seed script가 없고 seed를 수행하지 않는다. 입력 manifest와 참조 manifest를 읽기만 하며, 공통 1,000명이나 domain fixture를 중복 생성·수정·삭제하지 않는다. `verify-summary.mjs`는 모든 참조 manifest의 `datasetId`가 입력 manifest와 같고 각 `fixtureRunId`가 선언과 일치하는지 실행 전에 확인한다.

## 향후 승인 후 실행 계약

실행 대상은 PM/frontend가 공유하는 `faithlog-latest`뿐이다.

- backend 기본 주소: `http://127.0.0.1:28080`
- 기본 container alias: `faithlog-latest-app`, `faithlog-latest-postgres`, `faithlog-latest-redis`
- alias를 실제 Compose project name으로 간주하지 않는다. runner가 credential bootstrap 전에 각 container의 `com.docker.compose.project`와 `com.docker.compose.service` label을 읽고, app/PostgreSQL/Redis project가 모두 같은지 검증한 뒤 environment report에 기록한다. label이 비었거나 project가 다르면 즉시 중단한다.

부하 강도와 시간은 이 시나리오가 결정하지 않는다. 사용자가 승인한 값을 실행 시 명시해야 한다.

```bash
INPUT_MANIFEST=/absolute/path/to/issue-199-input-manifest.json \
PERF_ADMIN_EMAIL=runtime-only@example.com \
PERF_ADMIN_PASSWORD=runtime-only-secret \
PERF_DB_USER=runtime-only-db-user \
PERF_DB_PASSWORD=runtime-only-db-secret \
PERF_DB_NAME=runtime-only-db-name \
WARMUP_VUS=<approved> \
WARMUP_DURATION=<approved> \
MEASURED_VUS=<approved> \
MEASURED_DURATION=<approved> \
EXTERNAL_ACTIVITY='none; frontend QA stopped; no deploy or manual DB work' \
performance/k6/issue-199-admin-dashboard-baseline/run-baseline.sh
```

credential과 Access Token은 runtime memory에서만 사용한다. login credential은 child process에 상시 export하지 않고 각 mode의 bootstrap process에만 전달한다. mode마다 login → users/me·campuses/me로 fresh token을 발급하고, 해당 mode의 검증/k6 process에만 전달한 뒤 mode 종료 즉시 shell variable을 비운다. manifest, README, report, log, git에는 저장하지 않는다.

runner는 검증된 실제 Compose project label을 안전한 path segment로 제한하고 `/tmp/faithlog-performance-${project}.lock` directory를 원자 획득한 뒤 `empty -> small -> thousand`를 순차 실행한다. #193/#195를 포함해 같은 Compose project를 쓰는 다른 성능 baseline이 lock을 보유하면 credential/bootstrap/DB/k6 전에 실패한다. 다른 성능 baseline, frontend QA, 수동 부하, 배포, DB 유지보수와 병렬 실행을 금지한다. shared Docker lifecycle의 `up`, `build`, `restart`, `down`, `rm`, prune을 호출하지 않는다.

각 mode는 다음 순서로 분리된다.

1. 프론트 login → users/me·campuses/me 순서 재현 및 runtime-only token 준비
2. DB machine-readable correctness + API exact correctness/campus isolation + DB context 사전 검증
3. warmup k6와 failure/request/latency/throughput 채택 게이트
4. warmup 후 correctness/context 재검증
5. 마지막 별도 DB pure counter snapshot과 Docker CPU/RAM snapshot
6. setup traffic 없이 dashboard summary만 measured k6 실행 및 채택 게이트
7. Docker CPU/RAM snapshot과 첫 DB 작업인 pure counter snapshot
8. DB/API correctness와 DB context 사후 검증

warmup과 measured 결과는 별도 디렉터리에 둔다. cache reset은 하지 않으므로 첫 observation을 진짜 cold-cache 결과라고 부르지 않는다. environment report는 `cacheResetPerformed=false`, external activity 선언, 실제 Compose label을 함께 남긴다. `last_analyze`, `last_autoanalyze`, `n_mod_since_analyze`, `analyze_count`, `autoanalyze_count`를 전후 비교해 측정 중 planner 통계 상태 변화도 확인한다.

## 수집 metric과 evidence

k6 custom metric:

- `admin_dashboard_duration`: p50, p95, p99, max
- `admin_dashboard_requests`: count와 rate를 통한 throughput
- `admin_dashboard_failure_rate`: HTTP/envelope/정확성 실패율

warmup과 measured 모두 `admin_dashboard_failure_rate == 0`, request count/rate > 0, p50/p95/p99/max 값 존재를 `validate-k6-summary.mjs`로 강제한다. failure rate에는 k6 `rate==0` threshold도 중복 적용한다. 사용자가 승인하지 않은 latency/throughput 품질 threshold는 만들지 않는다.

Docker:

- app/PostgreSQL/Redis의 `docker stats --no-stream` CPU/RAM 전후 snapshot

PostgreSQL read-only evidence:

- `collect-db-counters.sql`: app table을 조회하지 않는 `pg_stat_database`와 `pg_stat_user_tables` pure snapshot. 모든 사전 검증 뒤 마지막 DB invocation과 measured 뒤 첫 DB invocation으로 실행한다. report JSON의 `observerOverhead`가 해석 경계를 함께 기록한다.
- `collect-correctness-evidence.sql`: dashboard 관련 exact aggregate를 한 줄 JSON으로 만들며 pure counter window 밖에서만 실행한다.
- `validate-db-correctness.mjs`: summary 전체와 OPEN poll별 실제 `pollId/responseCount` 집합을 manifest와 exact 비교하고, DB-derived aggregate `missingResponseCount`도 함께 고정한다.
- `collect-db-evidence.sql`: row count, analyze/autoanalyze, planner/query context를 pure counter window 밖에서 기록한다.
- `pg_settings` planner state
- 설치된 경우 `pg_stat_statements`; 없으면 unavailable 상태를 명시

따라서 application table 접근 관점에서 pre/post pure DB counter 사이에는 measured dashboard summary 요청만 존재한다. login, users/me, campuses/me, isolation/API correctness, fixture aggregate/context SQL은 모두 pre snapshot 전에 끝나거나 post snapshot 뒤에 시작한다.

단, `pg_stat_database`의 database-wide `xact_*`/tuple delta에는 counter snapshot observer 자체의 read-only transaction/tuple overhead가 포함될 수 있다. 그러므로 이 delta를 dashboard의 exact query count로 해석하지 않는다. 반면 `pg_stat_user_tables` app-table counter는 snapshot이 통계 view만 읽고 application table을 직접 접근하지 않으므로 measured window의 app-table scan/index 변화와 구분해 해석한다. 각 `db-counters-*.json`에는 `databaseWideCountersIncludeSnapshotTransaction=true`, `databaseWideDeltaIsExactQueryCount=false`, `appTableCountersReadApplicationTables=false`를 기록한다.

## report 경로

모든 산출물은 이 이슈 전용 ignored 경로에만 기록한다.

```text
performance/k6/issue-199-admin-dashboard-baseline/reports/
  <datasetId>/<fixtureRunId>/<mode>/
    environment.json
    api-correctness-before.json
    api-correctness-pre-measured.json
    api-correctness-after.json
    db-correctness-before.json
    db-correctness-pre-measured.json
    db-correctness-after.json
    db-context-before.txt
    db-context-pre-measured.txt
    db-context-after.txt
    warmup/summary.json
    warmup/adoption-gate.json
    warmup/k6.log
    measured/summary.json
    measured/adoption-gate.json
    measured/k6.log
    measured/db-counters-before.json
    measured/db-counters-after.json
    measured/docker-stats-before.jsonl
    measured/docker-stats-after.jsonl
```

현재 report에는 baseline 숫자가 없다. 실제 실행은 PM이 shared fixture 준비 상태, 다른 활동 중지, 부하 강도/시간을 승인한 뒤 별도 단계에서 수행한다.
