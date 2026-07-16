# Issue #197 경건 제출·retention baseline 시나리오 계약

현재 상태는 `scenario-ready / not-measured`다. 이 디렉터리는 baseline 실행 전 계약만 고정하며, 작성 세션에서는 seed, k6, scheduler, Docker, DB를 실행하지 않는다. production Java/API/권한/트랜잭션/Entity/DB/Flyway/의존성도 변경하지 않는다.

경건 write와 retention은 fixture, runner, report를 공유하지 않는다. 두 runner는 inspect한 실제 Compose project를 key로 한 `/tmp/faithlog-performance-<actualComposeProject>.lock`을 사용해 같은 stack의 다른 FaithLog 부하와 병렬 실행되지 않게 한다.

이 시나리오는 `origin/develop` exact HEAD `6796ed146244d8f3f5b5dd7048ebe16865084a97`(#200, #201, #202, #206 포함)를 기준으로 보정했다. 이 값은 작성 기준일 뿐 실행할 서버 identity를 추측하는 default가 아니다. 실제 baseline은 preparation과 measurement를 분리하는 **two-session / one-load** 정책을 따른다. preparation session은 scenario/test/validator/docs만 다루고, measurement session 하나만 공통 lock을 소유해 fixture·DB·HTTP·k6를 순차 실행한다. 다른 이슈 또는 같은 이슈의 load와 동시에 실행하지 않는다.

실행 전에는 runtime에서 승인한 `APP_SOURCE_WORKTREE`, `EXPECTED_APP_REVISION`, app/DB/Redis image ID, app JAR/API-contract SHA-256, 최신 적용 Flyway version/script/checksum, DB/Redis target host/port를 반드시 제공한다. app image에는 revision/API-contract OCI label이 없으므로 image 단독 revision 증명은 limitation으로 남긴다. 대신 runner는 Compose `project.working_dir`와 같은 realpath의 clean detached checkout, exact HEAD, newest `HEAD@{<iso-strict>}` reflog selector 시각, 그 시각 뒤에 생성된 exact app image, source tree API-contract SHA-256을 pre-lock과 모든 checkpoint에서 다시 확인한다. `%cI` commit 시각과 empty reflog subject는 checkout 시각 근거로 사용하지 않는다. Docker image ID, `/app/app.jar` SHA-256, `flyway_schema_history`, Redis `INFO server`도 exact 비교하고 app의 DB/Redis target을 승인 service/port와 대조한다. `BASE_URL`, `DB_HOST`, `REDIS_HOST`는 DNS 이름이나 암묵적 Unix socket이 아닌 numeric loopback(`127.0.0.1` 또는 `::1`)만 허용한다. 값이 없거나 current develop 서버임을 증명하지 못하면 측정하지 않는다.

current-develop correctness drift는 다음처럼 분리 고정한다.

- #201의 기본 archive visibility는 UNPAID 전체, PAID의 `paidAt` 최근 1개월, WAIVED/CANCELED의 `updatedAt` 최근 1개월 계약이다. retention 후보는 이 visibility cutoff를 사용하지 않고 전년도 `created_at`과 terminal status만 사용한다.
- #200의 stale duty 또는 soft-deleted payment account에 연결된 UNPAID도 retention 후보가 아니다. annual retention은 `PAID`, `WAIVED`, `CANCELED`만 dry-count한다.
- #202 V11 RLS는 Supabase Data API 노출을 막는 계약이며 JDBC backend 동작의 근거로 삼지 않는다. runner correctness는 실제 backend endpoint와 직접 JDBC PostgreSQL evidence로만 판단한다.
- #206 청구 페이징 ID tie-break가 포함된 develop revision을 immutable runtime identity로 고정한다. 이를 #197 endpoint 독립 성능 기여로 해석하지 않는다.

## 파일과 책임

- `devotion-write.js`: 주간 제출 API의 warmup, measured, rollback phase를 각각 독립 k6 실행으로 제공한다.
- `run-devotion-baseline.sh`: runtime workload/JWT/사용자 승인 DB activity signature, 실제 Compose project/service, app published port, scheduler disabled 상태를 확인하고 project-keyed runner lock, lock 전후 app/DB/Redis service·port를 포함한 immutable runtime continuity, exact fixture preflight, measured-only CPU/RAM/DB window, 세 phase, read-only DB correctness 확인을 조립한다.
- `preflight-devotion.sql`: 어떤 write보다 먼저 전체 cohort의 활성 user/membership/campus, 전용 week의 fresh weekly/daily/charge 0행, 계좌와 활성 penalty rule 계산 금액을 읽기 전용으로 확인한다.
- `collect-db-counters.sql`, `lib/validate-db-window.mjs`: warmup 뒤 measured 직전/직후의 DB 인스턴스 전체 database counter, 대상 DB table/query counter와 외부 session, analyze/autoanalyze/vacuum/autovacuum/planner 상태를 strict schema로 비교하고 오염된 run의 baseline 채택을 거부한다.
- `activity-signature.schema.json`, `lib/validate-activity-attribution.mjs`: warmup 결과를 신뢰 signature로 학습하지 않는다. 사용자가 runtime에 별도로 승인한 exact warmup/measured table/query/transaction signature와 두 window를 각각 비교하고, 같은 PostgreSQL 컨테이너의 다른 database activity도 거부한다.
- `lib/runtime-contract.mjs`: 승인 workload와 JWT exp/sub/user coverage, inspected app published port와 `BASE_URL` 동일성을 어떤 write보다 먼저 확인한다.
- `lib/source-image-provenance.mjs`: OCI revision label이 없는 app의 clean detached source/Compose working directory/exact HEAD/newest reflog selector/image creation/API tree digest를 fail-closed로 결속한다.
- `lib/rejection-contract.mjs`: runtime 필수 fresh rejection path에 최초 실패 stage를 mode 600 JSON으로 한 번만 기록하고 `automaticAdoption=false`를 고정한다.
- `lib/validate-k6-summary.mjs`: k6 direct metric과 `metric.values` shape 모두에서 필수 metric, 양의 정수 transaction, 양의 throughput, non-negative·ordered p50/p95/p99/max와 failure gate를 검증한다.
- `runtime-identity.sql`, `lib/validate-runtime-identity.mjs`: app/DB/Redis full container ID, image ID, StartedAt, Compose project/service, app published port, PostgreSQL database/address/port/postmaster/Flyway identity, Redis run ID/version/port를 initial/warmup 직전/measured 직전·직후/final에 exact 비교한다.
- `lib/validate-resource-window.mjs`: runtime 승인 sampling interval/max gap, immutable app/DB/Redis full container ID의 exact set, strict timestamp/CPU percent/RAM bytes sample을 검증하고 measured 시작·종료 전체 coverage가 없는 evidence를 거부한다.
- `verify-devotion.sql`: weekly/daily/charge/rollback row를 읽기 전용으로 검증한다.
- `run-retention-dry-verify.sh`: `faithlog-perf-197-*` 전용 Compose에서만 retention 후보 row를 읽는다. shared/default Compose에서는 즉시 거부한다.
- `retention-dry-verify.sql`: explicit read-only transaction에서 현재 retention predicate와 삭제 순서를 반영한 후보 count만 읽는다.
- `retention-dry-verify.mjs`: 실제 후보 count가 manifest의 `expectedDeleteCounts`와 정확히 같은지 확인하고 `scenario-ready / not-measured` evidence를 만든다.
- `tests/*.test.mjs`: 안전, fixture, metric, correctness 계약과 fake runner fail-closed 동작의 실행형 회귀 테스트다.

## 공통 안전 계약

두 runner 모두 다음을 강제한다.

1. `datasetId`는 `PERFORMANCE_`로 시작하고 `fixtureRunId`는 별도의 `ISSUE197_` 값이다.
2. runtime credential은 저장소 파일과 분리한다. `CREDENTIALS_FILE`은 `build/reports/k6/issue-197/...` 또는 OS 임시 디렉터리에 owner-only mode `600`으로만 둘 수 있으며 커밋하지 않는다. runner는 입력 직후 부모 environment에서 이를 unset하고 fixture/JWT validator와 각 k6 child에만 명시적으로 전달한다.
3. app/DB/Redis container의 실제 `com.docker.compose.project` label은 `EXPECTED_COMPOSE_PROJECT`와, 각 `com.docker.compose.service` label은 default 없는 승인 service와 정확히 같아야 한다. DB/Redis image ID도 runtime 승인값과 같아야 한다. mismatch는 lock, DB, k6, HTTP write 전에 거부하며 evidence에는 실제 project/service를 기록한다.
4. `FAITHLOG_SCHEDULER_ENABLED=false`가 아니면 실행하지 않는다. 실제 FCM 전송, scheduler retention, recovery가 baseline과 겹치지 않는다.
5. runner는 container 생성, 재시작, build, down, prune, volume 조작을 수행하지 않는다.
6. Compose inspect 뒤 실제 project-keyed lock을 원자 획득한다. 실패 시 같은 stack의 다른 부하가 진행 중인 것으로 보고 즉시 종료하며, runner가 직접 획득한 빈 lock만 종료 시 `rmdir`한다. stale/non-empty lock은 자동 삭제하지 않는다.
7. lock 전 검증한 source/image provenance, app image/JAR/API-contract digest와 app/DB/Redis full container ID, image ID, StartedAt을 initial capture와 다시 비교한다. 이후 warmup 직전, measured 직전, measured 직후, final snapshot의 source, Compose, PostgreSQL/Flyway, Redis run identity가 한 field라도 바뀌거나 source가 dirty/attached가 되면 non-zero로 종료한다.
8. `REJECTION_EVIDENCE_FILE`은 fixture run별 fresh ignored report/temp 경로여야 한다. 실패하면 최초 stage/exit code만 mode 600 JSON으로 보존하고 이후 실패가 이를 덮어쓰지 않으며 `automaticAdoption=false`다.
9. 기본 `build/reports/k6/issue-197/<fixtureRunId>` 또는 optional `PERF_REPORT_ROOT/<fixtureRunId>`는 mode 700으로 원자 생성한다. 이미 존재하면 이전 evidence와 결합하거나 덮어쓰지 않고 즉시 거부하므로 devotion과 retention은 서로 다른 fresh `fixtureRunId`를 사용한다. fake/static suite는 invocation별 임시 `PERF_REPORT_ROOT`를 사용하지만 runtime target/workload 입력에는 어떤 fallback도 추가하지 않는다.

## 경건 write fixture 계약

`FIXTURE_MANIFEST`는 `fixture-manifest.schema.json` 의미를 따른다.

- `measuredUserIds`: 정확히 1,000명.
- `warmupUserIds`, `measuredUserIds`, `rollbackUserIds`: 서로 완전히 다른 전용 사용자 집합.
- `referenceDate`: runner를 시작하는 현재 Asia/Seoul 날짜와 정확히 같아야 한다. 오래된 manifest는 실행하지 않는다.
- `warmupWeekStartDate`, `measuredWeekStartDate`: 현재 `referenceDate`보다 뒤인 서로 다른 월요일이다.
- `rollbackWeekStartDate`: `referenceDate`보다 앞선 월요일이다.
- success/warmup campus와 rollback campus는 다르다. success campus에는 soft-delete되지 않은 활성 PENALTY 계좌가 정확히 1개이고 rollback campus에는 0개여야 한다.
- 요청은 7개 `dailyChecks`, `saturdayLateMinutes=5`, `submit=true`를 사용한다. 세 경건 항목은 월~목 4일 true, 나머지 3일 false다.
- `expectedPenaltyAmount`는 fixture에 실제 구성한 활성 penalty rule로 미리 계산한 승인 값이다. script가 금액을 추측하지 않는다.

`CREDENTIALS_FILE`은 다음 runtime-only 구조다. runner는 JWT payload만 로컬에서 안전하게 decode해 `exp`, `sub`, `userId`, `tokenType=ACCESS`, 전체 fixture user coverage를 확인한다. 원문 token은 report/log에 기록하지 않으며 signature와 `tokenVersion`을 별도로 재구현하지 않는다. 실제 API 인증은 기존 서버가 수행한다. 세 phase의 승인 `MAX_DURATION` 합과 승인 safety seconds보다 모든 token의 잔여 TTL이 짧으면 어떤 HTTP/write보다 먼저 실패한다.

```json
{
  "fixtureRunId": "ISSUE197_YYYYMMDD_A",
  "tokens": [
    { "userId": 1, "accessToken": "runtime-only-value" }
  ]
}
```

warmup, measured, rollback은 별도 k6 process와 별도 summary를 사용한다. `BASE_URL`, 세 phase의 `VUS`와 `MAX_DURATION`, `RESOURCE_SAMPLE_INTERVAL_SECONDS`, `RESOURCE_SAMPLE_MAX_GAP_SECONDS`에는 default가 없으며 모두 runtime 필수다. max gap은 sampling interval 이상이어야 한다. 로그인은 측정 요청에 포함하지 않는다. measured 결과는 정확히 1,000개의 주간 제출 transaction을 대상으로 한다.

`ATTRIBUTION_SIGNATURE_FILE`도 runtime 필수이며 `activity-signature.schema.json` 의미를 따른다. runtime validator는 schema와 같은 exact key, JSON type, prefix, measured 1,000명 계약을 dependency 없이 검사한다. `datasetId`, `fixtureRunId`, `databaseName`, 두 cohort 수와 warmup/measured 각각의 application commit/rollback, 8개 대상 table counter delta, `pg_stat_statements` availability 및 normalized query calls/rows를 exact 값으로 고정한다. fresh write table의 production-shaped update delta는 승인 exact 값으로 허용하지만 non-write table update와 모든 delete는 0이어야 한다. runner는 fixture와 DB name 일치를 어떤 write보다 먼저 검증하고, 승인본을 ignored report 경로의 mode `0600` 고정본으로 만든다. 이때 얻은 SHA-256을 shell 변수와 environment evidence에 보존하고, 이후 고정본을 읽은 즉시 사전 digest 및 identity와 exact 재검증한 뒤 같은 digest를 scenario evidence에 기록한다. 따라서 실행 중 원본이나 고정본 교체를 채택하지 않는다. signature에는 default나 warmup 학습값이 없으며, 실제 실행 전에 사용자가 승인하지 않은 signature로 baseline을 채택할 수 없다.

모든 PostgreSQL 누적 counter와 승인 signature delta는 JSON decimal string이며 validator는 `BigInt`로만 monotonic/delta/exact 비교한다.

### 경건 correctness

k6 응답과 read-only SQL evidence를 함께 통과해야 한다.

- 성공 HTTP 200, success envelope, user/week 일치, `submittedAt` 존재.
- 사용자마다 daily row 정확히 7개, measured 전체 7,000개.
- weekly row와 제출 row 각각 1,000개.
- PENALTY charge row 각각 1개, `amount = expectedPenaltyAmount`.
- `paymentCategory=PENALTY`, `sourceType=DEVOTION_RECORD`, weekly source/user별 유일성.
- rollback 전용 사용자는 기존 `DEVOTION_RECORD` PENALTY charge가 0개여야 한다. 요청은 `400 BILLING_REQUIRED_PAYMENT_ACCOUNT_MISSING`이며 weekly/daily/charge row 모두 0개다. charge는 rollback weekly row와 JOIN하지 않고 campus/user에서 직접 세어 고아 source도 검출한다.

### 경건 측정 evidence

measured custom Trend에서 p50, p95, p99, max를 읽는다. iterations rate를 throughput으로, custom failure Rate를 failure rate로 기록한다. direct metric과 `metric.values` export를 모두 허용한다. 세 phase 모두 별도 transaction Counter가 runtime expected total과 exact 일치하는 양의 safe integer여야 하고, Rate의 `passes`/`fails`도 non-negative safe integer로서 합이 해당 Counter와 같아야 한다. installed k6 v2가 내보내는 `rate` 또는 `value`를 허용하되 둘 다 있으면 exact 일치를 강제한다. 이 failure Rate는 실패 여부를 표본으로 추가하므로 zero failure일 때 `rate|value=0`, `passes=0`, `fails=expected total`이어야 한다. throughput은 양의 finite, latency는 finite non-negative이며 `p50 <= p95 <= p99 <= max`여야 한다. 모든 1,000개 measured transaction은 성공해야 한다. transaction count, weekly/daily/charge DB counters, rollback persisted row count, measured phase의 app/PostgreSQL CPU/RAM도 `scenario-evidence.json`에 합친다.

resource sampler는 initial immutable full container ID를 대상으로만 실행한다. app/DB/Redis exact 세 role 각각 첫 sample은 measured 시작 이하, 마지막 sample은 measured 종료 이상이어야 하고 timestamp는 전역·role별로 strictly increasing/unique해야 한다. strict CPU percent/RAM bytes schema, runtime 승인 max gap, container ID binding을 위반하면 `resource-window-evidence.json`이 non-adoptable이며 runner는 non-zero로 종료한다. 한 순간 snapshot, role 누락, short ID, 다른 container sample로는 baseline을 만들 수 없다.

warmup 직전, warmup 뒤 measured 직전, measured 직후 snapshot을 순서대로 기록한다. 각 window의 table scan/fetch/write, application commit/rollback, optional normalized query calls/rows delta는 warmup 관측값에서 유추하지 않고 사용자 승인 signature의 해당 phase exact 값과 독립 비교한다. 따라서 warmup과 measured 양쪽에 같은 비율로 섞인 활동도 승인 signature를 벗어나면 `activity-attribution-evidence.json`을 `contaminated`로 만들고 baseline 채택을 거부한다.

`pg_stat_database`의 인스턴스 전체 database set/counter, 대상 DB의 `pg_stat_user_tables`, 전체 database의 `pg_stat_activity`, planner settings, `stats_reset`, analyze/autoanalyze/vacuum/autovacuum timestamp와 count, `n_mod_since_analyze` delta는 필수다. 8개 table 모두 maintenance field exact schema와 measured 전후 stability를 강제한다. `pg_stat_statements`는 extension/preload가 이미 사용 가능한 경우에만 target `dbid`로 한정하고 동일 normalized query를 role별 row에서 합산해 calls/rows/time/block delta를 남기며 unavailable 이유를 명시한다. reset, extension/config 변경은 하지 않는다. 다른 database의 counter 변화, 외부 active DB session, 선언된 외부 활동, planner/analyze/vacuum 상태 변화, counter reset, 예상 밖 non-write table update/delete 또는 measured insert delta 불일치는 evidence를 `contaminated`로 만든다. database-wide counter에는 각 read-only snapshot transaction 자체가 포함되므로 validator는 대상 DB의 window당 observer commit 1건만 분리하며, 나머지는 승인 signature와 exact 비교한다.

SQL은 bigint 누적 counter를 decimal string으로 직렬화해 JavaScript `Number` 정밀도 경계를 피한다. external session count는 strict non-negative safe integer만 허용하고 `null`, 누락, 문자열, 배열, 객체는 clean 0으로 변환하지 않는다. `pg_stat_statements.totalExecTime`만 finite decimal number로 유지한다.

실제 `pgStatStatements` snapshot은 availability truthiness를 사용하지 않는다. `available`은 boolean만 허용하며 available이면 statement inventory의 exact query/calls/rows/time/block key와 type을, unavailable이면 non-empty reason과 빈 statements를 강제한다. malformed schema나 phase availability drift는 non-adoptable reason으로 남긴다.

runtime identity query는 순수 DB counter window 밖에 둔다. measured-before identity를 확인한 다음 DB before snapshot을 찍고, measured 완료 뒤 DB after snapshot을 먼저 찍은 다음 measured-after identity를 확인한다. app/DB container 교체, image/StartedAt 변경, Compose label/port 변경, PostgreSQL database/address/port/postmaster restart가 있으면 서로 다른 runtime의 evidence를 합치지 않는다.

실행은 PM이 fixture를 준비하고 shared Docker에 다른 부하가 없음을 확인한 뒤에만 한다.

```bash
FIXTURE_MANIFEST=build/reports/k6/issue-197/ISSUE197_YYYYMMDD_A/runtime/devotion-fixture.json \
CREDENTIALS_FILE=build/reports/k6/issue-197/ISSUE197_YYYYMMDD_A/runtime/devotion-credentials.json \
ATTRIBUTION_SIGNATURE_FILE=build/reports/k6/issue-197/ISSUE197_YYYYMMDD_A/runtime/activity-signature.json \
    APP_CONTAINER=faithlog-backend \
    DB_CONTAINER=faithlog-postgres \
    REDIS_CONTAINER=faithlog-redis \
    APP_SOURCE_WORKTREE="${APPROVED_APP_SOURCE_WORKTREE:?}" \
EXPECTED_COMPOSE_PROJECT=actual-compose-label \
    EXPECTED_APP_COMPOSE_SERVICE="${APPROVED_APP_COMPOSE_SERVICE:?}" \
    EXPECTED_DB_COMPOSE_SERVICE="${APPROVED_DB_COMPOSE_SERVICE:?}" \
    EXPECTED_REDIS_COMPOSE_SERVICE="${APPROVED_REDIS_COMPOSE_SERVICE:?}" \
    EXPECTED_APP_REVISION="${APPROVED_APP_REVISION:?}" \
    EXPECTED_APP_IMAGE_ID="${APPROVED_APP_IMAGE_ID:?}" \
    EXPECTED_APP_JAR_SHA256="${APPROVED_APP_JAR_SHA256:?}" \
    EXPECTED_API_CONTRACT_SHA256="${APPROVED_API_CONTRACT_SHA256:?}" \
    EXPECTED_DB_IMAGE_ID="${APPROVED_DB_IMAGE_ID:?}" \
    EXPECTED_REDIS_IMAGE_ID="${APPROVED_REDIS_IMAGE_ID:?}" \
    EXPECTED_FLYWAY_VERSION="${APPROVED_FLYWAY_VERSION:?}" \
    EXPECTED_FLYWAY_SCRIPT="${APPROVED_FLYWAY_SCRIPT:?}" \
    EXPECTED_FLYWAY_CHECKSUM="${APPROVED_FLYWAY_CHECKSUM:?}" \
    DB_HOST="${APPROVED_DB_HOST:?}" \
    REDIS_HOST="${APPROVED_REDIS_HOST:?}" \
    EXPECTED_DB_PORT="${APPROVED_DB_PORT:?}" \
    EXPECTED_REDIS_PORT="${APPROVED_REDIS_PORT:?}" \
    REJECTION_EVIDENCE_FILE=build/reports/k6/issue-197/ISSUE197_YYYYMMDD_A/first-rejection.json \
DB_NAME=faithlog \
DB_USER=faithlog \
BASE_URL="${APPROVED_APP_BASE_URL:?}" \
WARMUP_VUS="${APPROVED_WARMUP_VUS:?}" \
MEASURED_VUS="${APPROVED_MEASURED_VUS:?}" \
ROLLBACK_VUS="${APPROVED_ROLLBACK_VUS:?}" \
WARMUP_MAX_DURATION="${APPROVED_WARMUP_MAX_DURATION:?}" \
MEASURED_MAX_DURATION="${APPROVED_MEASURED_MAX_DURATION:?}" \
ROLLBACK_MAX_DURATION="${APPROVED_ROLLBACK_MAX_DURATION:?}" \
TOKEN_TTL_SAFETY_SECONDS="${APPROVED_TOKEN_TTL_SAFETY_SECONDS:?}" \
RESOURCE_SAMPLE_INTERVAL_SECONDS="${APPROVED_RESOURCE_SAMPLE_INTERVAL_SECONDS:?}" \
RESOURCE_SAMPLE_MAX_GAP_SECONDS="${APPROVED_RESOURCE_SAMPLE_MAX_GAP_SECONDS:?}" \
EXTERNAL_ACTIVITY=none \
performance/k6/issue-197/run-devotion-baseline.sh
```

경건 report 경로는 `build/reports/k6/issue-197/<fixtureRunId>/devotion/`이다. fresh fixture root가 이미 있으면 실행하지 않으며 warmup/measured/rollback raw summary, Docker stats, DB counters, 합성 evidence를 서로 덮어쓰지 않는다.

## Retention isolated dry verification 계약

retention은 경건 write와 완전히 다른 `RETENTION_MANIFEST`, isolated Compose, dataset prefix, report를 사용한다.

- `EXPECTED_COMPOSE_PROJECT`와 실제 label 모두 `faithlog-perf-197-*`여야 한다.
- default 없는 `EXPECTED_APP_COMPOSE_SERVICE`/`EXPECTED_DB_COMPOSE_SERVICE`와 실제 service label이 lock과 DB access 전에 exact match해야 한다.
- `ALLOW_ISOLATED_RETENTION=true`를 명시해야 한다.
- `datasetPrefix`는 별도 `datasetId`와 `fixtureRunId`를 모두 포함해야 한다.
- retention 전용 campus 이름은 `datasetPrefix` literal 문자열로 시작해야 한다(`_`, `%`도 wildcard가 아닌 문자로 비교). isolated DB에는 해당 prefix 밖의 retention 후보 row가 없어야 한다.
- reference instant와 각 정책 cutoff의 경계 안/밖 fixture를 별도로 준비한다.
- manifest는 `DataRetentionCleanupResult`의 11개 field와 같은 `expectedDeleteCounts`를 고정한다.
- expired poll graph count는 response option → response → comment → option → poll 순서를 반영한다.
- expired poll과 겹치는 soft-deleted comment는 별도 30일 soft-delete count에서 다시 세지 않는다.
- annual evidence의 reference instant는 Asia/Seoul 2월 1일로 준비한다. devotion은 전년도 date, charge는 전년도 `created_at`과 terminal status만 후보이며 UNPAID는 보존한다.
- 전년도 weekly 후보에 연도 범위 밖 daily row가 연결되어 있으면 non-cascade FK로 annual transaction이 rollback될 수 있으므로 `annualForeignKeyBlockers=0`을 요구한다.
- project/service를 확인한 pre-lock app/DB container ID, image ID, StartedAt, app published port를 lock 직후 다시 확인한다. candidate SQL 전 PostgreSQL current database/server address/port/postmaster start를 고정하고 SQL 직후 전체 runtime identity를 exact 비교한 뒤에만 scenario evidence를 만든다.

현재 production에는 cron을 바꾸지 않고 외부에서 안전하게 `DataRetentionCleanupService`를 호출해 결과를 받는 manual trigger/test 경로가 없다. 따라서 이번 runner는 SQL candidate count의 dry verification만 수행한다. `FaithLogScheduledJobs`, cleanup service, recovery worker를 호출하지 않으며 실제 row 변경과 FCM 영향은 0이다.

```bash
ALLOW_ISOLATED_RETENTION=true \
RETENTION_MANIFEST=build/reports/k6/issue-197/ISSUE197_YYYYMMDD_R/runtime/retention-fixture.json \
    APP_CONTAINER=faithlog-perf-197-app \
    DB_CONTAINER=faithlog-perf-197-postgres \
    REDIS_CONTAINER=faithlog-perf-197-redis \
    APP_SOURCE_WORKTREE="${APPROVED_APP_SOURCE_WORKTREE:?}" \
EXPECTED_COMPOSE_PROJECT=faithlog-perf-197-YYYYMMDD-r \
    EXPECTED_APP_COMPOSE_SERVICE="${APPROVED_APP_COMPOSE_SERVICE:?}" \
    EXPECTED_DB_COMPOSE_SERVICE="${APPROVED_DB_COMPOSE_SERVICE:?}" \
    EXPECTED_REDIS_COMPOSE_SERVICE="${APPROVED_REDIS_COMPOSE_SERVICE:?}" \
    EXPECTED_APP_REVISION="${APPROVED_APP_REVISION:?}" \
    EXPECTED_APP_IMAGE_ID="${APPROVED_APP_IMAGE_ID:?}" \
    EXPECTED_APP_JAR_SHA256="${APPROVED_APP_JAR_SHA256:?}" \
    EXPECTED_API_CONTRACT_SHA256="${APPROVED_API_CONTRACT_SHA256:?}" \
    EXPECTED_DB_IMAGE_ID="${APPROVED_DB_IMAGE_ID:?}" \
    EXPECTED_REDIS_IMAGE_ID="${APPROVED_REDIS_IMAGE_ID:?}" \
    EXPECTED_FLYWAY_VERSION="${APPROVED_FLYWAY_VERSION:?}" \
    EXPECTED_FLYWAY_SCRIPT="${APPROVED_FLYWAY_SCRIPT:?}" \
    EXPECTED_FLYWAY_CHECKSUM="${APPROVED_FLYWAY_CHECKSUM:?}" \
    DB_HOST="${APPROVED_DB_HOST:?}" \
    REDIS_HOST="${APPROVED_REDIS_HOST:?}" \
    EXPECTED_DB_PORT="${APPROVED_DB_PORT:?}" \
    EXPECTED_REDIS_PORT="${APPROVED_REDIS_PORT:?}" \
    REJECTION_EVIDENCE_FILE=build/reports/k6/issue-197/ISSUE197_YYYYMMDD_R/first-rejection.json \
DB_NAME=faithlog \
DB_USER=faithlog \
performance/k6/issue-197/run-retention-dry-verify.sh
```

retention report 경로는 `build/reports/k6/issue-197/<fixtureRunId>/retention/`이다. 현재 evidence의 p50, p95, p99, max, throughput, failure, CPU, RAM은 모두 `null / not-measured`다. cleanup batch evidence는 현재 코드가 daily/annual 각각 한 transaction에서 무제한 repository operation을 수행한다는 정적 사실과 manual trigger 부재만 기록한다.

실제 retention baseline은 PM이 isolated fixture seed와 안전한 manual trigger/test 경로를 별도로 승인한 뒤에만 추가한다. 그때도 trigger 전후 exact row count, batch별 처리 row 수, transaction 성공/rollback, p50/p95/p99/max, throughput, failure, CPU/RAM을 새 report에 기록해야 한다.

## Current-develop handoff

read-only audit에서 확인한 실행 stack은 Compose project `faithlog-frontend-latest`, services `app`/`postgres`/`redis`, app published port `28080`이다. 승인 source는 `/private/tmp/FaithLog-perf-206-deploy`의 clean detached `6796ed146244d8f3f5b5dd7048ebe16865084a97`, newest HEAD reflog selector `2026-07-16T13:20:28+09:00`이고 app image `sha256:8e0f8d85d697a7d34aabf3703ddb27b4f1af326dec4f7c35556986303b0b816c`는 `2026-07-16T04:22:48.810414883Z`에 생성됐다. source API tree digest는 `2ccb072bd3d5be1c65acd43b99d3a36c27a810c93e86dbe64b19fd89841562bd`다. image-alone revision label 부재는 명시 limitation이며 이 결합 근거를 대체하지 않는다.

fresh namespace 추천값은 아직 실행 승인값이 아니다.

- devotion: `datasetId=PERFORMANCE_1000_20260716_DEVOTION_197_A`, `fixtureRunId=ISSUE197_20260716_DEVOTION_BEFORE_A`, rollback/warmup/measured week는 각각 `2026-07-13`/`2026-07-20`/`2026-07-27`.
- retention: `datasetId=PERFORMANCE_1000_20260716_RETENTION_197_A`, `fixtureRunId=ISSUE197_20260716_RETENTION_DRY_A`, 두 값을 모두 포함한 별도 `datasetPrefix`, reference instant `2027-01-31T15:00:00Z`.

사용자 승인이 필요한 값은 fixture와 runtime credential, exact activity signature, `BASE_URL`, 세 phase VUS/MAX_DURATION, token TTL safety, resource interval/max gap, app JAR SHA-256, DB/Redis image와 Flyway identity, numeric-loopback DB/Redis target이다. observed Compose/source/image/API digest는 승인 후보일 뿐 runner default가 아니다. warmup/rollback VUS 1, measured VUS 30, resource interval 1초/max gap 2초는 추천값이며 승인 전 실행하지 않는다.

devotion 실행 순서는 no-default/JWT/source/runtime preflight → project lock → fresh namespace → initial identity와 read-only cohort preflight → warmup → measured 전 DB snapshot → measured 1,000 제출과 resource sampling → measured 후 DB snapshot/오염 gate → rollback → read-only correctness/final identity/evidence다. 정적 예상은 HTTP 1,002건, 성공 cohort의 committed weekly/daily/charge 9,009행, rollback weekly/daily 8행 시도 후 persisted 0행, repository read call-site 약 15,028회다. 실제 SQL/시간은 측정하지 않았으며 최대 소요시간은 승인된 세 `MAX_DURATION` 합과 pre/post evidence overhead 이하다.

retention은 current shared project에서 project-name guard로 즉시 거부된다. 별도 `faithlog-perf-197-*` isolated Compose와 fresh retention fixture가 준비돼도 현재 순서는 source/runtime preflight → common lock → fresh namespace → initial identity → explicit read-only candidate SQL → post identity → `not-measured` evidence뿐이다. 예상 write/cleanup/FCM/HTTP는 모두 0이며 manual trigger와 batch 성과는 범위 밖이다.

필수 입력 누락, existing report namespace, lock 충돌, source/Compose/image/JAR/API/Flyway/Redis identity 불일치, scheduler enabled, target mismatch, stale fixture, JWT TTL 부족, external activity/session, DB maintenance/planner/counter drift, resource cadence 누락, k6 metric/correctness 실패 중 하나라도 발생하면 최초 rejection을 남기고 즉시 중단한다.

## SQL/query counter 해석 한계

이번 계약은 DB row/transaction counter와 이미 활성화된 경우의 `pg_stat_statements` query delta를 수집한다. shared Docker 재시작, stats reset, extension/config 변경, datasource proxy 추가는 하지 않는다. 따라서 `pg_stat_statements` unavailable이면 그 상태를 승인 signature와 evidence에 명시하고 table/database delta는 반드시 보존한다. 대상 DB counter는 observer transaction을 포함하고, 다른 database의 counter 변화는 동일 컨테이너 CPU/RAM 오염으로 간주해 채택을 거부한다. 실제 baseline 실행 전에는 현재 production 쿼리/트랜잭션 흐름에서 도출한 exact activity signature에 대한 사용자 승인이 별도로 필요하다.

## 작성 세션 정적 검증

```bash
node --test performance/k6/issue-197/tests/*.test.mjs
node --check performance/k6/issue-197/lib/fixture-contract.mjs
    node --check performance/k6/issue-197/lib/runtime-contract.mjs
    node --check performance/k6/issue-197/lib/current-develop-contract.mjs
node --check performance/k6/issue-197/lib/rejection-contract.mjs
node --check performance/k6/issue-197/lib/source-image-provenance.mjs
node --check performance/k6/issue-197/lib/validate-devotion-preflight.mjs
node --check performance/k6/issue-197/lib/validate-k6-summary.mjs
node --check performance/k6/issue-197/lib/validate-db-window.mjs
node --check performance/k6/issue-197/lib/validate-activity-attribution.mjs
node --check performance/k6/issue-197/lib/validate-resource-window.mjs
node --check performance/k6/issue-197/lib/validate-runtime-identity.mjs
node --check performance/k6/issue-197/lib/scenario-contract.mjs
node --check performance/k6/issue-197/retention-dry-verify.mjs
bash -n performance/k6/issue-197/run-devotion-baseline.sh
bash -n performance/k6/issue-197/run-retention-dry-verify.sh
```
