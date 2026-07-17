# Issue #199 관리자 대시보드 before 시나리오

상태: **production-optimized / integration after pending**

채택 상태: **conditional-not-adoptable**. 공유 stack에서 현재 승인된 증거는 measured 전후 `pg_stat_activity` 경계 snapshot뿐이라, 두 snapshot 사이에 시작·종료한 짧은 외부 요청을 완전히 검출한다고 주장하지 않는다. 승인된 continuous provenance 또는 격리 방식이 추가되기 전 runner는 모든 mode evidence를 수집한 뒤 non-zero로 종료하며 실제 baseline으로 채택하지 않는다.

이 디렉터리의 기존 runner는 `GET /api/v1/admin/campuses/{campusId}/dashboard/summary`의 current-develop before 계약을 보존한다. 2026-07-17부터 이력서용 성능 검증은 핵심 병목, 동일 fixture, before/after 3회, failure 0, p50/p95/p99/throughput, SQL 수에 집중하는 lean protocol을 사용한다. 기존 다중 fixture runner를 production 최적화 완료 조건으로 다시 실행하지 않는다.

성능 이슈의 issue-local test/scenario 보정은 서로 병렬로 수행할 수 있지만, shared stack의 실제 HTTP load는 한 서버에 하나씩만 순차 실행한다. 기존 machine gate의 `automaticAdoption=false`는 유지한다.

## 2026-07-17 production 최적화

- 병목: OPEN non-MEAL Poll마다 `countByPollIdAndUserIdIn`을 호출해 Poll 수에 비례하는 N+1이 발생했다.
- RED: ACTIVE member 1명과 OPEN Poll 25개인 동일 HTTP 통합 fixture에서 dashboard summary 1회가 35 JDBC prepared statements를 실행했다.
- GREEN: Poll ID와 ACTIVE user ID 집합을 한 번에 전달하고 `poll_id`별 response count를 단일 grouped query로 조회한다.
- 결과: 단독 focused 실행 10 SQL, 관련 Controller+REST Docs suite 실행 11 SQL 이하. 보수적으로 35→11, 24개 및 68.6% 이상 감소했다.
- 정확성: `openCount=25`, `missingResponseCount=25`, 기존 aggregate/권한/오류/API DTO를 동일 테스트에서 유지했다.
- 범위: Controller, DTO, frontend, Entity, Flyway, index, dependency 변경은 없다.
- HTTP latency/throughput 개선률은 최적화 코드를 integration runtime에 배포하고 동일 조건 before/after 3회를 완료하기 전까지 주장하지 않는다.
- `current-develop-contract.json`은 before 증거다. verifier는 작업 파일이 아니라 승인 base commit의 Git object를 읽어 before hash를 검증하므로 after 소스와 혼동하지 않는다.
- 검증: 관련 Controller+REST Docs GREEN, issue-local Node 46/46, 전체 Gradle 88 suites / 556 tests / failures 0 / errors 0 / skipped 3, build/asciidoctor 성공.

## 실제 API 계약

- Method/path: `GET /api/v1/admin/campuses/{campusId}/dashboard/summary`
- 선택 query: `weekStartDate=YYYY-MM-DD`
- query가 없으면 서버가 `Asia/Seoul` 기준 현재 주 월요일을 사용한다.
- query가 있으면 월요일이어야 하며, 월요일이 아니면 `400 DEVOTION_INVALID_WEEK_START_DATE`다.
- 허용: service `ADMIN`, 대상 campus의 ACTIVE `MINISTER`, `ELDER`, `CAMPUS_LEADER`
- 거부: 일반 `MEMBER`, 다른 campus 관리자, service `MANAGER` 역할만 가진 사용자와 COFFEE/MEAL duty만 가진 ACTIVE `MEMBER`. duty ownership은 dashboard 권한이 아니다. `403 ADMIN_DASHBOARD_ACCESS_FORBIDDEN`을 사용한다.
- #201 pagination/archive는 이 non-paginated dashboard에 적용되지 않는다. query는 `weekStartDate` 하나이고 charge는 기간 제한 없는 전체 `UNPAID` PENALTY/COFFEE다.
- #206 관련 dashboard ordering은 category `PENALTY → COFFEE`, machine DB poll evidence는 `pollId` 오름차순이다. billing page tie-break는 이 endpoint에 적용하지 않는다.

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
3. `src/admin/AdminScreen.tsx`의 초기 `loadAdmin`은 dashboard summary, campus members, `GET duty-assignments?staleOnly=false`, prayer board를 병렬 호출하고 campus detail 기반 invite code 조회도 시작한다. duty sibling은 ACTIVE assignments를 ID 오름차순으로 반환하며 여러 COFFEE 담당자를 허용하지만 #199 workload에는 포함하지 않는다.

#199는 집계 endpoint의 before 비용을 독립 측정해야 한다. `prepare-runtime-token.mjs`가 각 dataset mode 시작 시 1~2번 순서를 재현하고 fresh Access Token을 report/file이 아닌 runner의 shell memory에만 반환한다. 이후 해당 mode의 warmup/measured k6 setup은 HTTP 요청 없이 이 토큰을 받아, iteration에서 3번 fan-out 중 dashboard summary 하나만 호출한다. members/duty/prayer/invite 호출은 실제 프론트 동작 근거로 문서화하지만 #199 latency와 DB counter를 오염시키지 않도록 측정 부하에서 제외한다.

## 입력 manifest와 dataset mode

`input-manifest.example.json`은 구조 예시일 뿐 실제 fixture나 측정 결과가 아니다. 실행 시 별도의 ignored manifest를 만들고 `INPUT_MANIFEST`로 전달한다.

실행 manifest는 schema v2, current develop base, mode별 서로 다른 safe `fixtureRunId`, immutable `fixtureNamespace`의 `preparedAt/expiresAt`을 갖는다. `expiresAt`은 선택 mode 전체의 승인 warmup+measured 시간과 `FIXTURE_EXPIRY_SAFETY_SECONDS`를 덮어야 한다. 이 검사는 time-relative OPEN/recently-CLOSED poll fixture가 실행 도중 노후화되는 것을 Docker inspect/credential 전에 차단하며 임의 freshness tolerance를 만들지 않는다.

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

실행 대상은 PM/frontend가 공유하는 `faithlog-latest`뿐이다. `input-manifest`의 `runtimeTarget`은 승인된 app/PostgreSQL/Redis service label과 app container port를 기록한다.

- `BASE_URL`, `APP_CONTAINER`, `POSTGRES_CONTAINER`, `REDIS_CONTAINER`에는 default가 없으며 사용자가 승인한 실제 target을 실행 시 모두 명시한다.
- `BASE_URL`은 address family를 숨기는 `localhost`가 아니라 `127.0.0.1` 또는 `[::1]` numeric loopback을 사용한다. runner는 app의 실제 published port 중 선택한 address family와 호환되는 binding이 정확히 하나인지 검사하고 host port를 exact 결속한다. IPv4/IPv6 dual-stack 두 행은 선택 family에 하나씩이면 허용한다.
- `CONTAINER_ALIAS`는 report에 stack의 사람이 읽는 별칭을 남기는 optional evidence-only 값이다. Docker inspect/stats, credential bootstrap, lock 또는 target 선택에는 사용하지 않는다.
- container alias를 실제 Compose identity로 간주하지 않는다. credential bootstrap 전에 세 container의 project/service label을 읽고, project 3개가 같으며 service 3개가 manifest의 승인값과 exact match인지 검증한다. label/port가 비었거나 다르면 즉시 중단한다.
- manifest의 app/PostgreSQL/Redis full image ID와 image ref, service/container port는 실제 승인값이 runtime-required다. app container의 datasource host/port/database/user와 Redis host/port도 승인 PostgreSQL/Redis service 및 runtime DB credential에 결속하며 password는 evidence에 남기지 않는다.
- app `sourceProvenance`의 absolute clean detached source worktree, exact current-develop revision, admin+Flyway API tree SHA-256도 manifest 승인값이며 default가 없다. OCI revision label이 없는 image는 Compose `project.working_dir` realpath, newest HEAD reflog selector checkout 시각, 그 뒤의 image creation 시각, exact image ID를 pre-lock/post-lock/measured 전후/final에 다시 결속한다. `image-alone-revision-label-unavailable` limitation은 report에 그대로 남는다.
- 공통 Compose-project lock 전 full container triple을 memory에 캡처하고 lock 직후 snapshot과 exact 비교한다. 각 mode measured 직전·직후 및 correctness/context가 끝난 최종 시점에 app/PostgreSQL/Redis container ID, image ID/ref, `StartedAt`, Compose project/service/config hash, published ports를 최초값과 비교한다.
- PostgreSQL system identifier/postmaster, Flyway V11, V11 RLS의 7개 dashboard table ENABLE/non-FORCE/no-policy/owner JDBC 상태와 expected DB role을 strict 비교한다. Redis는 `run_id`, version, port와 monotonic uptime으로 container 내부 server restart까지 차단한다.

부하 강도와 시간은 이 시나리오가 결정하지 않는다. 사용자가 승인한 값을 실행 시 명시해야 한다.

```bash
INPUT_MANIFEST=/absolute/path/to/issue-199-input-manifest.json \
BASE_URL=http://127.0.0.1:<approved-published-port> \
APP_CONTAINER=<approved-app-container> \
POSTGRES_CONTAINER=<approved-postgres-container> \
REDIS_CONTAINER=<approved-redis-container> \
DATASET_MODES=<approved-comma-separated-modes> \
PERF_ADMIN_EMAIL=runtime-only@example.com \
PERF_ADMIN_PASSWORD=runtime-only-secret \
PERF_DB_USER=runtime-only-db-user \
PERF_DB_PASSWORD=runtime-only-db-secret \
PERF_DB_NAME=runtime-only-db-name \
WARMUP_VUS=<approved> \
WARMUP_DURATION=<approved> \
MEASURED_VUS=<approved> \
MEASURED_DURATION=<approved> \
TOKEN_EXPIRY_SAFETY_SECONDS=<approved> \
FIXTURE_EXPIRY_SAFETY_SECONDS=<approved> \
EXTERNAL_ACTIVITY=none \
performance/k6/issue-199-admin-dashboard-baseline/run-baseline.sh
```

`DATASET_MODES`에는 default가 없고 `empty,small,thousand` allowlist의 중복 없는 승인 선택만 허용한다. 선택 mode가 manifest에 없으면 container/credential/bootstrap 전에 중단한다. credential과 Access Token은 runtime memory에서만 사용한다. login credential은 child process에 상시 export하지 않고 bootstrap process에만 전달한다. mode 시작 시 warmup token을 발급하고 DB/API correctness/context보다 먼저 `WARMUP_DURATION + TOKEN_EXPIRY_SAFETY_SECONDS`를 덮는지 검사한다. warmup/correctness 종료 뒤 measured 직전에 다시 fresh JWT를 발급해 동일하게 `MEASURED_DURATION + safety`를 검사한다. JWT 값은 저장·출력하지 않고 memory에서 `exp`만 해석하며, mode 종료 즉시 token variable을 비운다.

runner는 검증된 실제 Compose project label을 안전한 path segment로 제한하고 `/tmp/faithlog-performance-${project}.lock` directory를 원자 획득한 뒤 승인 mode를 runtime 선택 순서대로 직렬 실행한다. #193/#195를 포함해 같은 Compose project를 쓰는 다른 성능 baseline이 lock을 보유하면 credential/bootstrap/DB/k6 전에 실패한다. `EXTERNAL_ACTIVITY`는 자유 서술이 아니라 exact `none`만 허용하지만, 이 operator declaration을 짧은 외부 요청의 완전한 machine proof로 취급하지 않는다. 다른 성능 baseline, frontend QA, 수동 부하, 배포, DB 유지보수와 병렬 실행을 금지한다. shared Docker lifecycle의 `up`, `build`, `restart`, `down`, `rm`, prune을 호출하지 않는다.

각 mode는 다음 순서로 분리된다.

1. 프론트 login → users/me·campuses/me 순서 재현, warmup runtime-only token 준비와 warmup duration+safety TTL gate
2. DB machine-readable correctness + API exact correctness/campus isolation + DB context 사전 검증
3. warmup k6와 failure/request/latency/throughput 채택 게이트
4. warmup 후 correctness/context 재검증
5. measured fresh JWT 발급과 exp 잔여 수명 gate 후 source/image provenance와 immutable container/PostgreSQL identity의 schema·최초값 연속성을 검증하고, exact identity/mode/boundary가 결속된 Docker CPU/RAM before snapshot을 검증한 뒤 마지막 별도 DB pure counter/activity/planner snapshot
6. setup traffic 없이 dashboard summary만 measured k6 실행 및 채택 게이트
7. 첫 DB 작업으로 pure counter/activity/planner snapshot을 수집하고 Docker CPU/RAM after snapshot, source/image provenance, immutable runtime identity를 재수집·검증
8. DB/API correctness와 DB context 사후 검증
9. correctness/context 뒤 fresh final source/image provenance와 app/PostgreSQL/Redis identity, DB counter/schema/delta/activity/planner/analyze/pgss continuity gate
10. 모든 mode가 끝나면 boundary-only external activity coverage 때문에 conditional-not-adoptable로 non-zero 종료

warmup과 measured 결과는 별도 디렉터리에 둔다. `<datasetId>/<fixtureRunId>/<mode>`가 이미 존재하면 stale evidence를 삭제하거나 덮어쓰지 않고 fail closed한다. cache reset은 하지 않으므로 첫 observation을 진짜 cold-cache 결과라고 부르지 않는다. environment report는 `cacheResetPerformed=false`, boundary-only external activity coverage, endpoint↔container identity, 실제 Compose label을 함께 남긴다.

## 수집 metric과 evidence

k6 custom metric:

- `admin_dashboard_duration`: p50, p95, p99, max
- `admin_dashboard_requests`: count와 rate를 통한 throughput
- `admin_dashboard_failure_rate`: HTTP/envelope/정확성 실패율

warmup과 measured 모두 `admin_dashboard_failure_rate == 0`, request count가 양의 safe integer, throughput이 양의 finite number, latency가 finite/non-negative이고 `p50 <= p95 <= p99 <= max`임을 `validate-k6-summary.mjs`로 강제한다. k6 summary의 metric direct shape와 `values` shape를 모두 같은 규칙으로 검증한다. failure rate에는 k6 `rate==0` threshold도 중복 적용한다. 사용자가 승인하지 않은 latency/throughput 품질 threshold는 만들지 않는다.

Docker:

- app/PostgreSQL/Redis의 `docker stats --no-stream --no-trunc` CPU/RAM 전후 snapshot
- `validate-docker-resources.mjs`가 before/after 각각 dataset mode, boundary, 동일 `sampledAt`, `one-no-stream-snapshot-per-boundary`, 정확히 3개 component, full container ID와 name을 runtime identity에 exact match한다. CPU는 multi-core 100% 초과를 허용하며 finite/non-negative다. RAM은 safe bytes, positive limit, `used <= limit`을 강제하고 canonical `used/limit*100`을 보고한다. raw `MemPerc`는 보조 증거이며 canonical과 exact 불일치하면 사용자 승인 tolerance가 없으므로 conditional/non-zero다.
- 이 값은 두 경계 시점의 snapshot일 뿐 continuous sampling이나 peak CPU/RAM이라고 주장하지 않는다.

PostgreSQL read-only evidence:

- `collect-db-counters.sql`: app table을 조회하지 않는 `pg_stat_database`, `pg_stat_user_tables`, `pg_settings`, `pg_stat_activity` machine snapshot. 모든 사전 검증/bootstrap 뒤 마지막 DB invocation과 measured 뒤 첫 DB invocation으로 실행한다. observer는 `pid <> pg_backend_pid()`로 현재 snapshot connection만 제외하므로 같은 `faithlog-issue199-observer` application name을 사용한 다른 session도 외부 활동으로 집계한다. coverage는 `boundary-snapshot-only`로 기록한다.
- `collect-runtime-identity.sql` + `validate-runtime-continuity.mjs`: container immutable identity와 PostgreSQL postmaster identity를 최초/mode pre/mode post에 exact 비교한다. container 또는 DB 재생성·재시작, malformed/missing identity evidence는 non-zero다.
- `collect-correctness-evidence.sql`: dashboard 관련 exact aggregate를 한 줄 JSON으로 만들며 pure counter window 밖에서만 실행한다.
- `validate-db-correctness.mjs`: summary 전체와 OPEN poll별 실제 `pollId/responseCount` 집합을 manifest와 exact 비교하고, DB-derived aggregate `missingResponseCount`도 함께 고정한다.
- `collect-db-evidence.sql`: row count, analyze/autoanalyze, planner/query context를 pure counter window 밖에서 기록한다.
- `pg_settings`와 `plannerContext`: snapshot을 수행하는 observer psql session의 planner state다. measured app connection/session의 planner state를 증명하는 evidence로 해석하지 않는다.
- `pg_stat_statements`는 strict available/unavailable machine state로 기록하고, available이면 extension version과 stats reset을 exact 비교한다. available↔unavailable 또는 reset 변화는 contaminated이며, stable unavailable 자체는 허용한다.

runner가 직접 만드는 application table 접근 관점에서 pre/post pure DB counter 사이에는 measured dashboard summary 요청만 존재한다. login, users/me, campuses/me, isolation/API correctness, fixture aggregate/context SQL과 runtime identity SQL은 모두 pre snapshot 전에 끝나거나 post snapshot 뒤에 시작한다. 외부 요청 선언 또는 snapshot 시 외부 non-idle DB session, planner/analyze/vacuum 변화, stats reset, counter 역행/누락이 있으면 baseline은 채택되지 않는다. 다만 짧은 외부 요청은 두 경계 snapshot 사이에 완전히 들어올 수 있으므로, 현재 evidence만으로 “외부 요청이 없었다”를 증명하지 않으며 승인된 연속 provenance/격리가 없으면 baseline은 항상 conditional/non-adoptable이다.

단, `pg_stat_database`의 database-wide `xact_*`/tuple delta에는 counter snapshot observer 자체의 read-only transaction/tuple overhead가 포함될 수 있다. 그러므로 이 delta를 dashboard의 exact query count로 해석하지 않는다. 반면 `pg_stat_user_tables` app-table counter는 snapshot이 통계 view만 읽고 application table을 직접 접근하지 않으므로 measured window의 app-table scan/index 변화와 구분해 해석한다. 각 `db-counters-*.json`에는 `databaseWideCountersIncludeSnapshotTransaction=true`, `databaseWideDeltaIsExactQueryCount=false`, `appTableCountersReadApplicationTables=false`를 기록한다.

## report 경로

모든 산출물은 이 이슈 전용 ignored 경로에만 기록한다.

```text
performance/k6/issue-199-admin-dashboard-baseline/reports/
  <datasetId>/<fixtureRunId>/<mode>/
    environment.json
    source-image-provenance-initial.json
    source-image-provenance-final.json
    runtime-identity-initial.json
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
    measured/db-window-adoption-gate.json
    measured/runtime-identity-before.json
    measured/runtime-identity-after.json
    measured/source-image-provenance-before.json
    measured/source-image-provenance-after.json
    measured/runtime-continuity-pre-gate.json
    measured/runtime-continuity-gate.json
    runtime-identity-final.json
    final-adoption-gate.json
    measured/docker-stats-before.jsonl
    measured/docker-stats-after.jsonl
    measured/docker-resource-before-gate.json
    measured/docker-resource-after-gate.json
```

현재 report에는 baseline 숫자가 없다. 실제 실행은 PM이 shared fixture 준비 상태, 다른 활동 중지, 부하 강도/시간을 승인한 뒤 별도 단계에서 수행한다.

## Current-develop handoff

read-only audit 기준 source 후보는 `/private/tmp/FaithLog-perf-206-deploy`의 clean detached `6796ed146244d8f3f5b5dd7048ebe16865084a97`, newest HEAD reflog selector `2026-07-16T13:20:28+09:00`이다. #199 admin+Flyway tree digest는 `437ec1047027499e9804c8edb693fe9b3b7a0de3d26402de94618bc00f10058a`다. 기존 운영 provenance에서 확인된 app image 후보 `sha256:8e0f8d85d697a7d34aabf3703ddb27b4f1af326dec4f7c35556986303b0b816c`의 creation 시각은 `2026-07-16T04:22:48.810414883Z`로 checkout 뒤다. Compose project `faithlog-frontend-latest`, service `app`/`postgres`/`redis`, app host port `28080`도 관찰 후보일 뿐 runner default나 새 실행 승인이 아니다. OCI revision label 부재는 명시 limitation이며 source/image 결합 증거를 대체하지 않는다.

fresh ID 추천안은 아직 승인값이 아니다.

- `datasetId=PERFORMANCE_SHARED_1000_20260716_ADMIN199_A`
- `fixtureNamespace.namespaceId=ISSUE199_20260716_ADMIN_BEFORE_A`
- `empty.fixtureRunId=ISSUE199_20260716_ADMIN_EMPTY_A`
- `small.fixtureRunId=ISSUE199_20260716_ADMIN_SMALL_A`
- `thousand.fixtureRunId=ISSUE199_20260716_ADMIN_THOUSAND_A`

실행 전 사용자 승인이 필요한 exact 입력은 위 fresh ID와 namespace 준비/만료 시각, 선택 `DATASET_MODES`, 여섯 shared fixture reference, 두 campus ID와 `weekStartDate`, campus-scoped manager credential, DB credential/name, numeric-loopback `BASE_URL`, 세 container name, 세 service/container port/full image ID/image ref, source provenance path/revision/digest, warmup/measured VUS와 duration, 두 expiry safety, `EXTERNAL_ACTIVITY=none`이다. 현재 boundary-only resource evidence에는 interval/max-gap 입력이 없고 automatic adoption도 없다. continuous resource window를 채택 근거로 추가하려면 sampling interval/max gap과 구현 범위를 별도 승인해야 한다.

추천값은 실행 승인과 분리한다. 기존 성능 비교의 시작 후보로 warmup VUS 1, measured VUS 30을 추천하지만 duration과 expiry safety는 승인값을 추측하지 않는다. continuous resource window가 별도 승인될 경우에만 #197과 같은 interval 1초/max gap 2초를 후보로 추천한다. latency/throughput threshold는 추천하거나 생성하지 않는다.

실행 순서는 no-default current-develop/source/runtime preflight → common project lock → fresh immutable report namespace → mode별 warmup token/correctness/context → warmup → measured fresh token → source/runtime/resource/DB before → measured dashboard GET만 → DB/resource/source/runtime after → correctness/context/final continuity → conditional-not-adoptable rejection 보존이다. 선택 mode마다 고정 HTTP overhead는 login/users-me/campuses-me 2회로 6건, before/pre-measured/after의 target+other-campus correctness 6건이며 k6 request 수는 승인 duration 동안의 실제 throughput으로만 결정된다. 예상 PostgreSQL/fixture write는 0건이고 login 때문에 Redis refresh-session write는 mode당 2건이며 runner cleanup은 0건이다. report 파일만 issue-local ignored namespace에 새로 기록한다. 최대 부하 시간은 선택 mode 수 × (`WARMUP_DURATION + MEASURED_DURATION`)이고 pre/post 검증 overhead는 별도다.

필수 입력 누락, existing namespace, common lock 충돌, source dirty/attached/revision·reflog·digest·image creation 불일치, Compose working dir/label/service/port/image/container identity 불일치, credential/JWT/fixture expiry 부족, correctness/403 실패, k6 failure/metric 수학 오류, PostgreSQL/Flyway/RLS/pgss/Redis continuity 변화, DB maintenance/activity/counter drift, resource schema/identity 불일치 중 하나라도 발생하면 최초 machine-readable rejection을 남기고 즉시 중단한다. 현재 외부 활동과 resource coverage가 boundary-only이므로 다른 검증이 모두 성공해도 최종 상태는 `conditional-not-adoptable`, exit non-zero다.
