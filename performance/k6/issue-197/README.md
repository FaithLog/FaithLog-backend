# Issue #197 경건 제출·retention baseline 시나리오 계약

현재 상태는 `scenario-ready / not-measured`다. 이 디렉터리는 baseline 실행 전 계약만 고정하며, 작성 세션에서는 seed, k6, scheduler, Docker, DB를 실행하지 않는다. production Java/API/권한/트랜잭션/Entity/DB/Flyway/의존성도 변경하지 않는다.

경건 write와 retention은 fixture, runner, report를 공유하지 않는다. devotion prepare와 두 runner는 inspect한 실제 Compose project를 key로 한 `/tmp/faithlog-performance-<actualComposeProject>.lock`을 사용해 같은 stack의 다른 FaithLog 부하와 병렬 실행되지 않게 한다.

이 시나리오는 `origin/develop` exact HEAD `6796ed146244d8f3f5b5dd7048ebe16865084a97`(#200, #201, #202, #206 포함)를 기준으로 보정했다. 이 값은 작성 기준일 뿐 실행할 서버 identity를 추측하는 default가 아니다. 실제 baseline은 preparation과 measurement를 분리하는 **two-session / one-load** 정책을 따른다. preparation session은 scenario/test/validator/docs만 다루고, measurement session 하나만 공통 lock을 소유해 fixture·DB·HTTP·k6를 순차 실행한다. 다른 이슈 또는 같은 이슈의 load와 동시에 실행하지 않는다.

실행 전에는 runtime에서 승인한 `APP_SOURCE_WORKTREE`, `EXPECTED_APP_REVISION`, app/DB/Redis image ID, app JAR/API-contract SHA-256, 최신 적용 Flyway version/script/checksum, DB/Redis target host/port를 반드시 제공한다. app image에는 revision/API-contract OCI label이 없으므로 image 단독 revision 증명은 limitation으로 남긴다. 대신 runner는 Compose `project.working_dir`와 같은 realpath의 clean detached checkout, exact HEAD, newest `HEAD@{<iso-strict>}` reflog selector 시각, 그 시각 뒤에 생성된 exact app image, source tree API-contract SHA-256을 pre-lock과 모든 checkpoint에서 다시 확인한다. `%cI` commit 시각과 empty reflog subject는 checkout 시각 근거로 사용하지 않는다. Docker image ID, `/app/app.jar` SHA-256, `flyway_schema_history`, Redis `INFO server`도 exact 비교하고 app의 DB/Redis target을 승인 service/port와 대조한다. `BASE_URL`, `DB_HOST`, `REDIS_HOST`는 DNS 이름이나 암묵적 Unix socket이 아닌 numeric loopback(`127.0.0.1` 또는 `::1`)만 허용한다. 값이 없거나 current develop 서버임을 증명하지 못하면 측정하지 않는다.

current-develop correctness drift는 다음처럼 분리 고정한다.

- #201의 기본 archive visibility는 UNPAID 전체, PAID의 `paidAt` 최근 1개월, WAIVED/CANCELED의 `updatedAt` 최근 1개월 계약이다. retention 후보는 이 visibility cutoff를 사용하지 않고 전년도 `created_at`과 terminal status만 사용한다.
- #200의 stale duty 또는 soft-deleted payment account에 연결된 UNPAID도 retention 후보가 아니다. annual retention은 `PAID`, `WAIVED`, `CANCELED`만 dry-count한다.
- #202 V11 RLS는 Supabase Data API 노출을 막는 계약이며 JDBC backend 동작의 근거로 삼지 않는다. runner correctness는 실제 backend endpoint와 직접 JDBC PostgreSQL evidence로만 판단한다.
- #206 청구 페이징 ID tie-break가 포함된 develop revision을 immutable runtime identity로 고정한다. 이를 #197 endpoint 독립 성능 기여로 해석하지 않는다.

## 파일과 책임

- `devotion-write.js`: 주간 제출 API의 warmup, measured, rollback phase를 각각 독립 k6 실행으로 제공한다.
- `run-devotion-prepare.sh`, `lib/devotion-prepare.mjs`: read-only namespace 확인 뒤 fresh report/secret namespace를 배타 예약하고, public API의 create-only 한 경로로 warmup 1명·measured 1,000명·rollback 1명을 준비한다. success와 rollback campus에 같은 네 penalty rule을 만들되 활성 PENALTY 계좌는 success campus에만 만든다. 마지막에 1,002개 access token을 mode 600 runtime-only 파일로 만들고 exact preflight와 installed k6 inspect까지 수행한다.
- `preflight-devotion-namespace.sql`: campus name 두 개와 deterministic fixture email prefix가 DB에 0행인지 local namespace 예약과 어떤 HTTP write보다 먼저 확인한다.
- `run-devotion-baseline.sh`: runtime workload/JWT, 실제 Compose project/service, app published port, scheduler disabled 상태를 확인하고 project-keyed runner lock, lock 전후 app/DB/Redis service·port를 포함한 immutable runtime continuity, exact fixture preflight, measured-only CPU/RAM/DB supporting window, 세 phase, read-only DB correctness 확인을 조립한다.
- `preflight-devotion.sql`: 어떤 write보다 먼저 전체 cohort의 활성 user/membership/campus, 전용 week의 fresh weekly/daily/charge 0행, 계좌와 활성 penalty rule 계산 금액을 읽기 전용으로 확인한다.
- `collect-db-counters.sql`, `lib/validate-db-window.mjs`: warmup 뒤 measured 직전/직후의 DB 인스턴스 전체 database counter, 대상 DB table/query counter와 외부 session, analyze/autoanalyze/vacuum/autovacuum/planner 상태를 strict schema로 비교하고 오염된 run의 baseline 채택을 거부한다.
- `lib/runtime-contract.mjs`: 승인 workload와 JWT exp/sub/user coverage, inspected app published port와 `BASE_URL` 동일성을 어떤 write보다 먼저 확인한다.
- `lib/source-image-provenance.mjs`: OCI revision label이 없는 app의 clean detached source/Compose working directory/exact HEAD/newest reflog selector/image creation/API tree digest를 fail-closed로 결속한다. digest inventory는 current revision의 `com/faithlog/devotion`, `com/faithlog/notification`, `com/faithlog/batch/infrastructure/scheduler`, `com/faithlog/batch/service`, `db/migration` 다섯 tree가 각각 실제로 존재해야 하며 하나라도 비면 capture를 거부한다.
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

prepare와 두 runner 모두 다음을 강제한다.

1. `datasetId`는 `PERFORMANCE_`로 시작하고 `fixtureRunId`는 별도의 `ISSUE197_` 값이다.
2. runtime credential은 저장소 파일과 분리한다. `CREDENTIALS_FILE`은 `build/reports/k6/issue-197/...` 또는 OS 임시 디렉터리에 owner-only mode `600`으로만 둘 수 있으며 커밋하지 않는다. runner는 입력 직후 부모 environment에서 이를 unset하고 fixture/JWT validator와 각 k6 child에만 명시적으로 전달한다.
3. app/DB/Redis container의 실제 `com.docker.compose.project` label은 `EXPECTED_COMPOSE_PROJECT`와, 각 `com.docker.compose.service` label은 default 없는 승인 service와 정확히 같아야 한다. DB/Redis image ID도 runtime 승인값과 같아야 한다. mismatch는 lock, DB, k6, HTTP write 전에 거부하며 evidence에는 실제 project/service를 기록한다.
4. `FAITHLOG_SCHEDULER_ENABLED=false`가 아니면 실행하지 않는다. 실제 FCM 전송, scheduler retention, recovery가 baseline과 겹치지 않는다.
5. runner는 container 생성, 재시작, build, down, prune, volume 조작을 수행하지 않는다.
6. Compose inspect 뒤 실제 project-keyed lock을 원자 획득한다. 실패 시 같은 stack의 다른 부하가 진행 중인 것으로 보고 즉시 종료하며, runner가 직접 획득한 빈 lock만 종료 시 `rmdir`한다. stale/non-empty lock은 자동 삭제하지 않는다.
7. lock 전 검증한 source/image provenance, app image/JAR/API-contract digest와 app/DB/Redis full container ID, image ID, StartedAt을 initial capture와 다시 비교한다. 이후 warmup 직전, measured 직전, measured 직후, final snapshot의 source, Compose, PostgreSQL/Flyway, Redis run identity가 한 field라도 바뀌거나 source가 dirty/attached가 되면 non-zero로 종료한다.
8. `REJECTION_EVIDENCE_FILE`은 fixture run별 fresh ignored report/temp 경로여야 한다. 실패하면 최초 stage/exit code만 mode 600 JSON으로 보존하고 이후 실패가 이를 덮어쓰지 않으며 `automaticAdoption=false`다.
9. baseline의 기본 `build/reports/k6/issue-197/<fixtureRunId>` 또는 optional `PERF_REPORT_ROOT/<fixtureRunId>`와 prepare의 필수 `PREPARE_REPORT_ROOT/<fixtureRunId>`, `RUNTIME_SECRET_ROOT/<fixtureRunId>`는 mode 700으로 배타 생성한다. 이미 존재하면 이전 evidence와 결합하거나 덮어쓰지 않고 즉시 거부한다. prepare의 partial namespace는 자동 정리·재사용하지 않고 새 `fixtureRunId`로 다시 시작한다.

## 경건 write fixture 계약

### 단일 prepare 경로

준비 순서는 **fresh namespace read-only 확인 → report/secret namespace 배타 예약 → create-only API seed → 1,002명 최종 login/token manifest → exact DB preflight → installed k6 inspect** 하나뿐이다. calibration dataset, duplicate workload, warmup 비례 추정, cleanup/reuse 경로는 없다.

`PREPARE_INPUT_FILE`과 `RUNTIME_SECRET_ROOT`는 OS temp directory 아래 absolute path와 owner-only mode가 필수다. `PREPARE_INPUT_FILE` exact key는 `adminEmail`, `adminPassword`, `fixtureUserPassword`, `penaltyAccount`, `penaltyRules`이며 service `ADMIN` 계정, 공통 fixture password, success campus의 PENALTY 계좌 한 개와 두 campus에 동일하게 적용할 네 rule을 담는다. password/account/token 원문은 report, stdout/stderr, argv에 기록하지 않는다. 네 rule은 `QUIET_TIME`/`PRAYER`/`BIBLE_READING`의 `MISSING_COUNT(requiredCount=7)`와 `SATURDAY_LATE`의 `LATE_MINUTE` exact set이며, `expectedPenaltyAmount`는 fixed request(각 항목 4회, 지각 5분)로 계산한다.

prepare API 호출은 관리자 login 1, campus create 2, PENALTY account create 1, rule create 8(각 campus 4), signup 1,002, service-admin membership add 1,002, fixture user 최종 login 1,002로 정확히 3,018건이다. create-only business resource의 최소 직접 행은 campus 2, campus creator membership 2, payment account 1, rule 8, user 1,002, cohort membership 1,002로 합계 2,017행이며 login은 user `lastLoginAt`과 기존 token/session 저장 동작을 수행한다. 이 preparation write는 measured window 밖이며 baseline transaction으로 귀속하지 않는다.

`PREPARE_MAX_DURATION_SECONDS`도 default 없는 승인 필수 입력이다. runtime input loop에서 Docker inspect, namespace query, local reservation보다 먼저 누락을 거부하고, 실행 시작 시점부터 승인 시간 뒤까지 Asia/Seoul 날짜가 바뀌면 `referenceDate` 일관성을 위해 write 전에 거부한다. service ADMIN access token은 이 시간과 `TOKEN_TTL_SAFETY_SECONDS` 합보다 긴 잔여 TTL이 있어야 한다. 추천 후보는 `900`초이며 실제 승인값은 PM이 실행 직전에 결정한다.

각 단계 실패 시 `preparation-receipt.json`에는 최초 안전한 stage/status/code와 완료 count만 남긴다. response body, Authorization, access/refresh token, password는 남기지 않는다. partial fixture와 namespace는 `cleanupAllowed=false`, `reuseAllowed=false`, `automaticAdoption=false`이며 자동 삭제하지 않는다.

`FIXTURE_MANIFEST`는 `fixture-manifest.schema.json` 의미를 따른다.

- `measuredUserIds`: 정확히 1,000명.
- `warmupUserIds`, `measuredUserIds`, `rollbackUserIds`: 서로 완전히 다른 전용 사용자 집합.
- `referenceDate`: runner를 시작하는 현재 Asia/Seoul 날짜와 정확히 같아야 한다. 오래된 manifest는 실행하지 않는다.
- `warmupWeekStartDate`, `measuredWeekStartDate`: 현재 `referenceDate`보다 뒤인 서로 다른 월요일이다.
- `rollbackWeekStartDate`: `referenceDate`보다 앞선 월요일이다.
- success/warmup campus와 rollback campus는 다르다. 두 campus 모두 동일한 활성 penalty rule 4개와 같은 positive 계산 금액을 가져야 한다. success campus에는 soft-delete되지 않은 활성 PENALTY 계좌가 정확히 1개이고 rollback campus에는 0개여야 한다.
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

모든 PostgreSQL 누적 counter는 JSON decimal string이며 validator는 `BigInt`로만 monotonic/delta를 비교한다.

### 경건 correctness

k6 응답과 read-only SQL evidence를 함께 통과해야 한다.

- 성공 HTTP 200, success envelope, user/week 일치, `submittedAt` 존재.
- 사용자마다 daily row 정확히 7개, measured 전체 7,000개.
- weekly row와 제출 row 각각 1,000개.
- PENALTY charge row 각각 1개, `amount = expectedPenaltyAmount`.
- `paymentCategory=PENALTY`, `sourceType=DEVOTION_RECORD`, weekly source/user별 유일성.
- rollback 전용 사용자는 기존 `DEVOTION_RECORD` PENALTY charge가 0개여야 한다. rollback campus의 동일 네 rule이 fixed request를 positive `expectedPenaltyAmount`로 계산한 뒤 활성 PENALTY 계좌 조회에서 `400 BILLING_REQUIRED_PAYMENT_ACCOUNT_MISSING`이 발생해야 한다. 이 예외는 같은 transaction에서 앞서 생성한 weekly 1행·daily 7행·submit 변경을 rollback하며 final direct SQL에서 weekly/daily/charge persisted 0을 강제한다. charge는 rollback weekly row와 JOIN하지 않고 campus/user에서 직접 세어 고아 source도 검출한다.

### 경건 측정 evidence

measured custom Trend에서 p50, p95, p99, max를 읽는다. iterations rate를 throughput으로, custom failure Rate를 failure rate로 기록한다. direct metric과 `metric.values` export를 모두 허용한다. 세 phase 모두 별도 transaction Counter가 runtime expected total과 exact 일치하는 양의 safe integer여야 하고, Rate의 `passes`/`fails`도 non-negative safe integer로서 합이 해당 Counter와 같아야 한다. installed k6 v2가 내보내는 `rate` 또는 `value`를 허용하되 둘 다 있으면 exact 일치를 강제한다. 이 failure Rate는 실패 여부를 표본으로 추가하므로 zero failure일 때 `rate|value=0`, `passes=0`, `fails=expected total`이어야 한다. throughput은 양의 finite, latency는 finite non-negative이며 `p50 <= p95 <= p99 <= max`여야 한다. 모든 1,000개 measured transaction은 성공해야 한다. transaction count, weekly/daily/charge DB counters, rollback persisted row count, measured phase의 app/PostgreSQL/Redis CPU/RAM도 `scenario-evidence.json`에 합친다.

resource sampler는 initial immutable full container ID를 대상으로만 실행한다. measured 전에는 한 번의 `docker stats --no-stream --no-trunc`가 app/DB/Redis 세 full ID를 `.ID`로 함께 읽고, measured 동안에는 process 시작 overhead를 반복하지 않는 한 개의 multi-container `docker stats --no-trunc` stream이 exact 3-row snapshot을 만든다. no-stream snapshot은 control-free 세 행만 허용한다. installed Docker stream은 첫 snapshot의 첫 data row에 `ESC[H`, 모든 data row 끝에 `ESC[K`를 붙이고, 다음 snapshot부터는 완전한 snapshot 사이의 standalone `ESC[K` 한 행과 첫 data row의 `ESC[JESC[H`를 반복한다. stream parser는 이 exact physical-line state transition만 소비한다. separator 누락·중복·순서 변경, initial/recurring prefix 교환, `ESC[2J`, unknown/mid-field control byte, malformed CSI, prefix/suffix 주변 text는 거부한다. framing 제거 뒤 기존 full ID/CPU/RAM 검증을 그대로 수행한다. 한 snapshot의 세 role은 동일 capture timestamp를 공유하고 snapshot timestamp는 strictly increasing/unique하다. stop marker가 생긴 뒤의 다음 완전한 snapshot을 final boundary로 기록한 뒤에만 sampler가 정상 종료한다. 각 role의 첫 sample은 measured 시작 이하, 마지막 sample은 measured 종료 이상이어야 하며 runtime 승인 max gap deadline, strict CPU percent/RAM bytes schema, exact identity를 위반하거나 stream이 중단·정지·불완전 종료되면 `resource-window-evidence.json` 이전에 non-zero로 실패한다. sampling interval/max gap은 완화하지 않으며 한 순간 snapshot, role 누락, short ID, 다른 container sample로는 baseline을 만들 수 없다.

warmup 뒤 measured 직전과 measured 직후 snapshot만 DB supporting window로 사용한다. source-attributable acceptance gate는 fixture-owned direct SQL이다. measured 완료와 DB after snapshot 뒤, rollback 전에 exact measured user ID 1,000명·success campus·measured week로 weekly 1,000/서로 다른 user 1,000/submitted 1,000, weekly에 직접 연결된 daily 7,000/서로 다른 user 1,000/각 user 7행/정확한 월~일 날짜 7,000, `charge.source_id=weekly.id`와 동일 user/campus의 PENALTY+DEVOTION_RECORD charge 1,000/서로 다른 user·source 1,000/중복 0/각 승인 금액/합계 `expectedPenaltyAmount * 1,000`을 검증한다. fresh success campus의 해당 source charge는 warmup 1 + measured 1,000이어야 한다. rollback 뒤 같은 SQL을 다시 실행해 rollback weekly/daily/charge persisted 0을 포함한 전체 cardinality를 재검증한다.

`pg_stat_database`/`pg_stat_user_tables`/`pg_stat_statements` 누적치는 cross-backend 비동기 publication을 포함하므로 exact request acceptance gate가 아니다. 모든 BigInt counter의 strict schema와 monotonicity를 유지하고 관찰된 양수 delta는 `sourceUnattributedDeltas`에 원문 그대로 기록한다. target insert가 direct cardinality보다 늦게 publication되거나 users/다른 database/DB-wide read-write counter가 증가해도 tolerance, sleep, subtraction, background estimate 없이 supporting evidence로만 남기며 shared-stack 결과는 계속 conditional이다. malformed counter, regression, `stats_reset`, planner 변경, analyze/vacuum maintenance drift, external active session 또는 명시된 external activity는 fail-closed다.

`pg_stat_database`의 인스턴스 전체 database set/counter, 대상 DB의 `pg_stat_user_tables`, 전체 database의 `pg_stat_activity`, planner settings, `stats_reset`, analyze/autoanalyze/vacuum/autovacuum timestamp와 count, `n_mod_since_analyze` delta는 필수다. 8개 table 모두 maintenance field exact schema와 measured 전후 stability를 강제한다. `pg_stat_statements`는 extension/preload가 이미 사용 가능한 경우 target `dbid`의 normalized query calls/rows/time/block delta를 runtime-observed supporting evidence로 남기고 unavailable 이유를 명시한다. reset, extension/config 변경은 하지 않는다. 다른 database와 비-fixture table의 양수 counter 변화는 source-unattributed로 보존하고 conditional classification을 유지한다. 외부 active DB session, planner/analyze/vacuum drift, counter reset 또는 counter regression은 `contaminated`로 종료한다.

shared-stack before는 correctness와 HTTP/custom Counter를 모두 통과해도 최종 `status=conditional-not-adoptable`, `automaticAdoption=false`다. DB-wide transaction/query exact signature를 사전 추측하거나 warmup에서 학습하지 않는다.

SQL은 bigint 누적 counter를 decimal string으로 직렬화해 JavaScript `Number` 정밀도 경계를 피한다. external session count는 strict non-negative safe integer만 허용하고 `null`, 누락, 문자열, 배열, 객체는 clean 0으로 변환하지 않는다. `pg_stat_statements.totalExecTime`만 finite decimal number로 유지한다.

실제 `pgStatStatements` snapshot은 availability truthiness를 사용하지 않는다. `available`은 boolean만 허용하며 available이면 statement inventory의 exact query/calls/rows/time/block key와 type을, unavailable이면 non-empty reason과 빈 statements를 강제한다. malformed schema나 phase availability drift는 non-adoptable reason으로 남긴다.

runtime identity query는 순수 DB counter window 밖에 둔다. measured-before identity를 확인한 다음 DB before snapshot을 찍고, measured 완료 뒤 DB after snapshot을 먼저 찍은 다음 measured-after identity를 확인한다. 그 identity와 exact direct cardinality SQL 직후의 `measuredCardinalityAfter` identity를 다시 exact 비교한 뒤에만 rollback으로 진행한다. PostgreSQL `inet_server_addr()::text` evidence는 실제 원문을 보존한다. 비교할 때만 IPv4 `/32`, IPv6 `/128` host CIDR과 IPv6 축약형을 lossless canonicalize해 explicit `DB_HOST=127.0.0.1|::1`과 같은 loopback인지 확인한다. 다른 IP/loopback, 외부 주소, non-host CIDR, CIDR이 붙은 runtime input은 거부한다. app/DB container 교체, image/StartedAt 변경, Compose label/port 변경, PostgreSQL database/address/port/postmaster restart가 있으면 서로 다른 runtime의 evidence를 합치지 않는다.

실행은 PM이 candidate namespace가 비어 있고 shared Docker의 actual load가 0임을 확인한 뒤 prepare와 baseline을 같은 승인 runtime 입력으로 순차 실행한다.

```bash
DATASET_ID="${APPROVED_DATASET_ID:?}" \
FIXTURE_RUN_ID="${APPROVED_FIXTURE_RUN_ID:?}" \
PREPARE_REPORT_ROOT="${APPROVED_PREPARE_REPORT_ROOT:?}" \
RUNTIME_SECRET_ROOT="${APPROVED_RUNTIME_SECRET_ROOT:?}" \
PREPARE_INPUT_FILE="${APPROVED_PREPARE_INPUT_FILE:?}" \
APP_CONTAINER="${APPROVED_APP_CONTAINER:?}" DB_CONTAINER="${APPROVED_DB_CONTAINER:?}" REDIS_CONTAINER="${APPROVED_REDIS_CONTAINER:?}" \
APP_SOURCE_WORKTREE="${APPROVED_APP_SOURCE_WORKTREE:?}" \
EXPECTED_COMPOSE_PROJECT="${APPROVED_COMPOSE_PROJECT:?}" \
EXPECTED_APP_COMPOSE_SERVICE="${APPROVED_APP_COMPOSE_SERVICE:?}" \
EXPECTED_DB_COMPOSE_SERVICE="${APPROVED_DB_COMPOSE_SERVICE:?}" \
EXPECTED_REDIS_COMPOSE_SERVICE="${APPROVED_REDIS_COMPOSE_SERVICE:?}" \
EXPECTED_APP_REVISION="${APPROVED_APP_REVISION:?}" EXPECTED_APP_IMAGE_ID="${APPROVED_APP_IMAGE_ID:?}" \
EXPECTED_APP_JAR_SHA256="${APPROVED_APP_JAR_SHA256:?}" EXPECTED_API_CONTRACT_SHA256="${APPROVED_API_CONTRACT_SHA256:?}" \
EXPECTED_DB_IMAGE_ID="${APPROVED_DB_IMAGE_ID:?}" EXPECTED_REDIS_IMAGE_ID="${APPROVED_REDIS_IMAGE_ID:?}" \
EXPECTED_FLYWAY_VERSION="${APPROVED_FLYWAY_VERSION:?}" EXPECTED_FLYWAY_SCRIPT="${APPROVED_FLYWAY_SCRIPT:?}" \
EXPECTED_FLYWAY_CHECKSUM="${APPROVED_FLYWAY_CHECKSUM:?}" \
DB_HOST="${APPROVED_DB_HOST:?}" REDIS_HOST="${APPROVED_REDIS_HOST:?}" \
EXPECTED_DB_PORT="${APPROVED_DB_PORT:?}" EXPECTED_REDIS_PORT="${APPROVED_REDIS_PORT:?}" \
DB_NAME="${APPROVED_DB_NAME:?}" DB_USER="${APPROVED_DB_USER:?}" BASE_URL="${APPROVED_APP_BASE_URL:?}" \
WARMUP_VUS="${APPROVED_WARMUP_VUS:?}" MEASURED_VUS="${APPROVED_MEASURED_VUS:?}" ROLLBACK_VUS="${APPROVED_ROLLBACK_VUS:?}" \
WARMUP_MAX_DURATION="${APPROVED_WARMUP_MAX_DURATION:?}" MEASURED_MAX_DURATION="${APPROVED_MEASURED_MAX_DURATION:?}" \
ROLLBACK_MAX_DURATION="${APPROVED_ROLLBACK_MAX_DURATION:?}" \
PREPARE_MAX_DURATION_SECONDS="${APPROVED_PREPARE_MAX_DURATION_SECONDS:?}" TOKEN_TTL_SAFETY_SECONDS="${APPROVED_TOKEN_TTL_SAFETY_SECONDS:?}" \
RESOURCE_SAMPLE_INTERVAL_SECONDS="${APPROVED_RESOURCE_SAMPLE_INTERVAL_SECONDS:?}" \
RESOURCE_SAMPLE_MAX_GAP_SECONDS="${APPROVED_RESOURCE_SAMPLE_MAX_GAP_SECONDS:?}" EXTERNAL_ACTIVITY=none \
REJECTION_EVIDENCE_FILE="${APPROVED_PREPARE_REPORT_ROOT:?}/${APPROVED_FIXTURE_RUN_ID:?}/first-rejection.json" \
performance/k6/issue-197/run-devotion-prepare.sh
```

prepare 성공 뒤 `FIXTURE_MANIFEST=<prepare report>/<fixtureRunId>/devotion-fixture.json`, `CREDENTIALS_FILE=<runtime secret>/<fixtureRunId>/devotion-credentials.json`을 아래 baseline에 전달한다. `PERF_REPORT_ROOT`는 prepare root와 다른 fresh base를 사용한다.

```bash
FIXTURE_MANIFEST="${APPROVED_PREPARE_REPORT_ROOT:?}/${APPROVED_FIXTURE_RUN_ID:?}/devotion-fixture.json" \
CREDENTIALS_FILE="${APPROVED_RUNTIME_SECRET_ROOT:?}/${APPROVED_FIXTURE_RUN_ID:?}/devotion-credentials.json" \
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

read-only audit에서 확인한 실행 stack은 Compose project `faithlog-frontend-latest`, services `app`/`postgres`/`redis`, app published port `28080`이다. 승인 source는 `/private/tmp/FaithLog-perf-206-deploy`의 clean detached `6796ed146244d8f3f5b5dd7048ebe16865084a97`, newest HEAD reflog selector `2026-07-16T13:20:28+09:00`이고 app image `sha256:8e0f8d85d697a7d34aabf3703ddb27b4f1af326dec4f7c35556986303b0b816c`는 `2026-07-16T04:22:48.810414883Z`에 생성됐다. corrected source API tree inventory는 위 다섯 current path의 154개 revision entry이며 SHA-256은 `625bc9e8f83561c67f8f8d5bc26c68bdf172c191d57fec01aca4423e7c2b2a9d`다. 이전 `2ccb072b...` 값은 존재하지 않는 구 Java 경로 세 개가 비어 migration tree만 해시한 값이므로 승인 입력으로 사용하지 않는다. image-alone revision label 부재는 명시 limitation이며 이 결합 근거를 대체하지 않는다.

fresh namespace는 DB와 filesystem의 read-only zero 확인 뒤에만 예약한다. 실패한 namespace는 evidence를 보존하고 cleanup/reuse하지 않는다.

- devotion C (`datasetId=PERFORMANCE_1000_20260717_DEVOTION_197_C`, `fixtureRunId=ISSUE197_20260717_DEVOTION_BEFORE_C`)는 fixture API write 전 runtime identity에서 PostgreSQL actual `127.0.0.1/32`와 승인 `DB_HOST=127.0.0.1`의 표기 차이로 rejected됐다. fixture HTTP와 k6 load는 0이고 임시 ADMIN USER는 원복됐다. C report/evidence는 보존하되 cleanup/reuse하지 않는다.
- devotion D (`datasetId=PERFORMANCE_1000_20260717_DEVOTION_197_D`, `fixtureRunId=ISSUE197_20260717_DEVOTION_BEFORE_D`)는 runtime/namespace/API seed/DB preflight를 통과했다. preparation receipt는 `prepared`, HTTP 3,014건, campus 2/account 1/rule 4/user 1,002/membership 1,002/token 1,002, `expectedPenaltyAmount=2250`을 기록했다. 그러나 마지막 installed k6 v2 inspect에서 OS environment assignment가 `__ENV`로 전달되지 않아 `open()` empty filename, exit 107로 rejected됐다. measured k6 load는 0이고 임시 ADMIN USER는 원복됐다. D report/fixture는 read-only 보존하며 cleanup/reuse하지 않는다.
- devotion E (`datasetId=PERFORMANCE_1000_20260717_DEVOTION_197_E`, `fixtureRunId=ISSUE197_20260717_DEVOTION_BEFORE_E`)는 fixture prepare와 measured 1,000건을 완료했고 HTTP/check/custom failure는 0이었다. 다만 serial/blocking resource sampler가 app→DB→Redis를 약 2초씩 따로 읽어 raw sample이 6행뿐이었고 app same-role gap이 승인 max gap 2초를 초과해 stage `measured`에서 rejected됐다. p50 42.58ms, p95 101.28ms, p99 154.74ms, max 196.32ms, throughput 592.299163/s는 rejected run의 diagnostic-only 값이며 baseline/adoptable 성과가 아니다. E report/fixture는 read-only 보존하고 credentials는 제거됐으며 임시 ADMIN USER는 원복됐다. cleanup/reuse하지 않는다.
- devotion F (`datasetId=PERFORMANCE_1000_20260717_DEVOTION_197_F`, `fixtureRunId=ISSUE197_20260717_DEVOTION_BEFORE_F`)는 fixture prepare와 measured 1,000건을 완료했고 failure는 0이었다. 그러나 installed Docker stream이 첫 행의 full ID 앞에 `ESC[H`, 모든 행 끝에 `ESC[K`를 붙였고 strict parser가 row 0 ID를 거부해 sampler가 measured 종료 전에 non-zero로 종료했다. p50 79.56ms, p95 222.09ms, p99 364.84ms, max 537.42ms, throughput 300.159325/s는 rejected run의 diagnostic-only 값이며 baseline/adoptable 성과가 아니다. F report/fixture는 read-only 보존하고 credentials는 제거됐으며 임시 ADMIN USER는 원복됐다. cleanup/reuse하지 않는다.
- devotion G (`datasetId=PERFORMANCE_1000_20260717_DEVOTION_197_G`, `fixtureRunId=ISSUE197_20260717_DEVOTION_BEFORE_G`)는 fixture prepare와 measured 1,000건을 완료했고 failure는 0이었다. 그러나 Docker stream의 snapshot 사이 standalone `ESC[K`와 recurring first-row `ESC[JESC[H`를 기존 3-physical-line grouping이 다음 snapshot row 0으로 오인해 sampler가 final boundary 전에 non-zero로 종료했다. p50 43.42ms, p95 102.72ms, p99 160.66ms, max 249.91ms, throughput 596.255989/s는 rejected run의 diagnostic-only 값이며 baseline/adoptable 성과가 아니다. G report/fixture는 read-only 보존하고 credentials는 제거됐으며 임시 ADMIN USER는 원복됐다. cleanup/reuse하지 않는다.
- devotion H (`datasetId=PERFORMANCE_1000_20260717_DEVOTION_197_H`, `fixtureRunId=ISSUE197_20260717_DEVOTION_BEFORE_H`)는 prepare와 warmup 1, measured 1,000을 완료했고 HTTP/check/custom failure는 0이었다. direct read-only 확인은 campus 82, measured week `2026-07-27`에서 weekly 1,000/distinct user 1,000, weekly에 연결된 daily 7,000/distinct user 1,000, source weekly+동일 user에 연결된 PENALTY/DEVOTION_RECORD charge 1,000/distinct user 1,000/각 2,250원/합계 2,250,000원, success campus 전체 warmup+measured charge 1,001을 확인했다. 그러나 비동기 publication 중인 누적 counter가 target insert 595/4,165/595, DB `tup_inserted=5,355`만 보인 동시에 users update 9와 다른 database read delta를 exact-zero gate가 오염으로 처리해 stage `measured`에서 exit 1로 rejected됐다. rollback은 실행되지 않았다. p50 46.86ms, p95 103.30ms, p99 130.02ms, max 189.19ms, throughput 549.373879/s는 diagnostic-only이며 baseline/adoptable 성과가 아니다. H report/fixture는 영구 read-only/non-reusable이고 credentials/runtime secret은 제거됐으며 임시 ADMIN은 USER로 원복됐다.
- devotion I (`datasetId=PERFORMANCE_1000_20260717_DEVOTION_197_I`, `fixtureRunId=ISSUE197_20260717_DEVOTION_BEFORE_I`)는 prepare, warmup 1, measured 1,000, measured direct cardinality와 runtime continuity를 통과했다. rollback 1 request는 HTTP success-class response를 반환해 expected `400`/`BILLING_REQUIRED_PAYMENT_ACCOUNT_MISSING`/`success=false` check 0/3, custom failure 100%, k6 exit 99로 stage `rollback`에서 rejected됐고 final cardinality는 미도달했다. current source는 active-rule 계산 합계가 0이면 payment-account 조회 전에 성공 반환하며 I rollback campus에는 계좌뿐 아니라 rule도 0개였으므로 production 결함이 아니라 stale fixture expectation이었다. p50 50.14ms, p95 110.04ms, p99 145.10ms, max 187.10ms, throughput 525.637993/s는 diagnostic-only다. I report/fixture는 영구 read-only/non-reusable이고 credentials/runtime secret은 제거됐으며 임시 ADMIN은 USER로 원복됐다.
- devotion J (`datasetId=PERFORMANCE_1000_20260717_DEVOTION_197_J`, `fixtureRunId=ISSUE197_20260717_DEVOTION_BEFORE_J`)는 current-develop shared stack의 유효한 conditional before다. warmup 1과 measured 1,000은 failure 0이고, measured p50 65.0935ms, p95 145.86005ms, p99 180.06974ms, max 272.145ms, throughput 400.1357260382722/s다. rollback 1은 `400 BILLING_REQUIRED_PAYMENT_ACCOUNT_MISSING`이며 final weekly/daily/charge persisted 0이다. measured direct SQL은 weekly 1,000, daily 7,000, PENALTY/DEVOTION_RECORD charge 1,000, duplicate 0, 각 2,250원/합계 2,250,000원을 exact 검증했다. app max CPU/RAM은 314.19%/715,548,262 bytes, DB는 163.31%/288,568,115 bytes, Redis는 6.77%/21,820,867 bytes다. J는 `status=conditional-not-adoptable`, `automaticAdoption=false`이고 cumulative DB/table/pgss는 publication lag가 있는 supporting-only evidence이므로 병목 SQL 횟수 근거로 사용하지 않는다. J report/fixture는 영구 read-only이며 수정·삭제·cleanup·재사용하지 않는다.
- retention: `datasetId=PERFORMANCE_1000_20260716_RETENTION_197_A`, `fixtureRunId=ISSUE197_20260716_RETENTION_DRY_A`, 두 값을 모두 포함한 별도 `datasetPrefix`, reference instant `2027-01-31T15:00:00Z`.

runtime 필수 입력은 fresh dataset/fixture ID, prepare report/secret/input 경로, service ADMIN credential과 fixture 공통 password를 담은 mode 600 input, `BASE_URL`, prepare 최대시간, 세 phase VUS/MAX_DURATION, token TTL safety, resource interval/max gap, app JAR SHA-256, DB/Redis image와 Flyway identity, numeric-loopback DB/Redis target이다. observed Compose/source/image/API digest는 승인 후보일 뿐 runner default가 아니다. prepare 최대시간 900초, warmup/rollback VUS 1, measured VUS 30, resource interval 1초/max gap 2초는 추천값이며 runner에 default가 없다.

devotion 순서는 prepare no-default/source/runtime check → common lock → DB namespace 0 확인 → local namespace 예약 → create-only API 3,018건 → token/manifest → exact preflight/k6 inspect → baseline no-default/JWT/source/runtime preflight → common lock → warmup → measured 전 DB snapshot → measured 1,000 제출/resource sampling → measured 후 supporting DB gate → rollback → correctness/final identity/conditional evidence다. installed k6 v2 init에 필요한 `BASE_URL`, `FIXTURE_MANIFEST`, `CREDENTIALS_FILE`, `PHASE`, `VUS`, `MAX_DURATION`은 prepare inspect와 baseline의 각 phase에 명시적 `-e`로 전달한다. credential/token 원문은 argv에 두지 않고 runtime-only 파일 경로만 전달한다. baseline HTTP는 1,002건, 성공 cohort의 committed weekly/daily/charge는 warmup 9행 + measured 9,000행이며 rollback은 persisted 0행이다. 실제 소요시간은 미측정이며 prepare는 3,018개 순차 API 왕복과 bcrypt 2,005회, baseline은 승인된 세 `MAX_DURATION` 합과 evidence overhead 이하로만 계획한다.

retention은 current shared project에서 project-name guard로 즉시 거부된다. 별도 `faithlog-perf-197-*` isolated Compose와 fresh retention fixture가 준비돼도 현재 순서는 source/runtime preflight → common lock → fresh namespace → initial identity → explicit read-only candidate SQL → post identity → `not-measured` evidence뿐이다. 예상 write/cleanup/FCM/HTTP는 모두 0이며 manual trigger와 batch 성과는 범위 밖이다.

필수 입력 누락, existing report namespace, lock 충돌, source/Compose/image/JAR/API/Flyway/Redis identity 불일치, scheduler enabled, target mismatch, stale fixture, JWT TTL 부족, external activity/session, DB maintenance/planner/counter drift, resource cadence 누락, k6 metric/correctness 실패 중 하나라도 발생하면 최초 rejection을 남기고 즉시 중단한다.

## Production devotion optimization handoff

current source call graph에서 weekly submit 한 건은 동일 `weeklyRecordId`의 daily row를 날짜별로 7회 조회하고, summary 계산을 위해 같은 7행을 다시 1회 조회했다. 이 구조적 근거는 cumulative DB counter 추정이 아니라 `WeeklyDevotionCommandService`의 repository 호출 경로에서 직접 확인했다.

최적화는 weekly lock과 transaction 안에서 daily 7행을 한 번 bulk load하고 `recordDate` Map으로 기존 행을 갱신하거나 누락 행을 생성한다. 누락 행은 repository `saveAll` 한 번으로 전달하며, 같은 7개 entity로 weekly summary와 기존 response를 만든다. 따라서 daily repository read 호출은 요청당 8회에서 1회, 개별 `save` 호출은 7회에서 `saveAll` 1회로 바뀐다. `IDENTITY` entity의 실제 insert/update SQL 수가 7행보다 줄었다고 주장하지 않으며, after latency/CPU/throughput은 아직 측정하지 않았다.

API path/request/response, ACTIVE membership authorization, ErrorCode, weekly pessimistic lock, transaction/rollback, submitted state, daily 7행, penalty amount/source/account snapshot, charge uniqueness는 변경하지 않는다. frontend 변경, Flyway/index/dependency 변경은 없다. 인덱스는 #194 EXPLAIN 근거가 생긴 뒤 별도로 판단한다. after는 수정 서버를 별도 integration runtime에 배포한 뒤 PM의 단일 load로만 측정한다.

J before runtime의 approved revision/digest `6796ed146244d8f3f5b5dd7048ebe16865084a97` / `625bc9e8f83561c67f8f8d5bc26c68bdf172c191d57fec01aca4423e7c2b2a9d`는 J evidence에 그대로 결속한다. 최적화가 반영된 after source의 같은 154-entry API tree digest는 `ce0393dc632abba1e1e629785d5305fbbbbc638e80e1cc461d3ba600831be626`다. integration image/JAR/final revision은 새 배포에서 별도로 산출하고 이 digest와 함께 runtime input으로 명시해야 하며, before identity를 after에 재사용하지 않는다.

## SQL/query counter 해석 한계

이번 계약은 exact direct business cardinality/HTTP/custom Counter와 DB supporting counter를 분리한다. shared Docker 재시작, stats reset, extension/config 변경, datasource proxy 추가는 하지 않는다. `pg_stat_statements` unavailable이면 strict reason과 빈 inventory를 남기고 table/database delta는 반드시 보존한다. 대상·다른 database counter는 observer, 다른 backend, 비동기 publication을 포함할 수 있으므로 exact request attribution이나 subtraction을 하지 않고 `sourceUnattributedDeltas`로 기록한다. clean run도 shared-stack에서는 `conditional-not-adoptable`이다.

## 작성 세션 정적 검증

```bash
node --test performance/k6/issue-197/tests/*.test.mjs
node --check performance/k6/issue-197/lib/fixture-contract.mjs
node --check performance/k6/issue-197/lib/runtime-contract.mjs
node --check performance/k6/issue-197/lib/current-develop-contract.mjs
node --check performance/k6/issue-197/lib/devotion-prepare.mjs
node --check performance/k6/issue-197/lib/rejection-contract.mjs
node --check performance/k6/issue-197/lib/source-image-provenance.mjs
node --check performance/k6/issue-197/lib/validate-devotion-preflight.mjs
node --check performance/k6/issue-197/lib/validate-k6-summary.mjs
node --check performance/k6/issue-197/lib/validate-db-window.mjs
node --check performance/k6/issue-197/lib/validate-resource-window.mjs
node --check performance/k6/issue-197/lib/validate-runtime-identity.mjs
node --check performance/k6/issue-197/lib/scenario-contract.mjs
node --check performance/k6/issue-197/retention-dry-verify.mjs
bash -n performance/k6/issue-197/run-devotion-baseline.sh
bash -n performance/k6/issue-197/run-devotion-prepare.sh
bash -n performance/k6/issue-197/run-retention-dry-verify.sh
```
