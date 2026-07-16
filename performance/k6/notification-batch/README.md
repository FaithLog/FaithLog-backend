# Issue #198 notification batch before scenario

상태: `scenario-ready / not-measured`

이 디렉터리는 production Java를 바꾸지 않고 현재 알림 생성·전송 흐름의 `before` 기준선을 준비한다. 지금 단계에서는 fixture, notification job, Docker, DB를 실행하지 않았으며 실제 baseline 숫자나 개선 성과가 없다. 여러 성능 이슈의 `parallel test-code` 보정만 허용하고 `sequential actual-load`는 PM 전용 측정 window에서만 수행한다.

## 최신 develop 계약

`current-develop-contract.json`은 기준 commit `6796ed146244d8f3f5b5dd7048ebe16865084a97`, Flyway V1-V11 SHA-256, #198에 직접 관련된 production source SHA-256을 고정한다. fixture preparation과 runner는 Docker 또는 SQL 전에 `verify-current-develop-contract.mjs`를 실행하며 source/migration/base ancestry drift가 있으면 즉시 종료한다. 실제 target, credential, workload 분포는 이 파일에 넣지 않고 모두 runtime-required로 남긴다.

실제 workload executable은 deployed application container가 아니라 이 attached #198 worktree에서 실행하는 local Gradle test-profile harness다. 따라서 app container/image provenance는 `not-applicable-local-gradle-test-profile`이며 측정 evidence로 승격하지 않는다. runner는 runtime 승인값 `PERF_EXPECTED_HARNESS_HEAD`와 `PERF_EXPECTED_HARNESS_CONTRACT_DIGEST`를 default 없이 요구한다. pre-lock부터 clean HEAD, exact origin/develop와 merge-base, src/main diff 0, 관련 production notification/Flyway source·test harness·Gradle input의 개별 Git blob ID와 deterministic aggregate를 비교한다. `cleanTest testClasses` 뒤에는 `build/classes/java/main`, `build/resources/main`, notification performance test/support classes, `build/resources/test` 전체의 relative path+content SHA-256 digest를 workload 직전/직후/final에 exact 비교한다. symlink, non-regular file, missing root, duplicate path는 fail closed다.

- #200 이후 creation은 현재의 per-target token lookup을 그대로 보존하지만 delivery는 `request-wide bulk token snapshot` 1회다. permanent failure token은 비활성화되고 같은 request의 이후 log token 목록에서 즉시 제거되며 90일 stale token cutoff와 request log ID ascending order를 보존한다.
- #200 COFFEE/MEAL duty reminder의 ACTIVE duty 권한, owned account scope, account+recipient+business-date dedupe와 stale duty 복구/차단 계약은 source identity로 고정하지만, 이 scenario는 scheduler가 넘긴 자동 알림 target 이후 경계만 측정하므로 duty API나 stale duty lifecycle을 호출하지 않는다.
- #201 pagination/archive 계약은 알림 로그 API의 기본 page size를 바꾸지 않았고 이 harness는 HTTP 목록 API를 호출하지 않는다. correctness는 request ID 내부 log를 stable ID ascending ordering으로 조회한다.
- #202 V11은 Data API 역할 deny-all과 direct owner JDBC 경계를 유지한다. `POSTGRES_USER`, `POSTGRES_DB`, `PERF_EXPECTED_POSTGRES_ROLE`은 runtime 필수이며 snapshot의 `current_user`가 승인 role과 다르면 실패한다. `FORCE ROW LEVEL SECURITY`는 허용하지 않는다.
- #206 stable ordering은 charge list offset pagination용 계약이므로 notification workload에는 적용되지 않는다. 이 scenario는 charge pagination이나 archive query를 실행하지 않는다.

## 측정 경계

자동 알림 scheduler가 target user ID 목록을 계산한 다음 호출하는 실제 production 경계인 `NotificationRequestCommandService.requestAutomaticNotification(...)`와 실제 `NotificationDeliveryWorker.processRequest(...)`를 test-only harness에서 직접 호출한다.

이 workload는 HTTP 부하가 아니라 Java harness가 production service를 직접 1회 호출하므로 **k6 v2 Counter/Rate/Trend는 not applicable**이다. 대신 scenario result가 creation → dedupe-replay → delivery 순서, exact count, `scenarioFailureCount=0`, `scenarioFailureRate=0`을 기록한다. p50/p95/p99/max latency ordering은 사용자 승인 sample 수와 cumulative-state 전략이 구현된 뒤에만 summarizer가 계산하며 현재는 fail-closed disabled다.

생성 phase의 현재 동작:

1. 사용자마다 Redis dedupe를 `notificationType + campusId + scopeId + targetUserId + businessDate` key로 예약한다.
2. 사용자마다 `findActiveSendableTokens(userId)`를 호출한다.
3. sendable token이 있으면 PENDING, 없으면 SKIPPED notification log를 사용자별로 저장한다.
4. 하나 이상의 PENDING log가 있으면 request ID를 dispatch한다. test harness는 이 dispatch를 capture해 delivery와 분리한다.

전송 phase의 현재 동작:

1. request ID의 PENDING log를 조회한다.
2. request의 PENDING 사용자 전체 sendable token을 한 번에 조회해 user별 snapshot으로 묶는다.
3. deterministic fake sender로 success, transient-then-success, permanent failure를 재현한다. retry sleep은 production의 `1s → 5s → 30s` 정책을 그대로 사용한다.
4. 사용자별 log를 SENT 또는 FAILED로 갱신한다. delivery 시점에 token이 없으면 SKIPPED다.
5. permanent failure는 해당 dummy token의 failure reason을 기록하고 inactive로 바꾼다.

cron cadence와 upstream target discovery 자체는 duration에 포함하지 않는다. 대상 campus의 ACTIVE member 1,000명을 ID 순서로 고정해 scheduler → request service handoff 이후의 token/log 병목만 측정한다. 이는 Issue #198의 token lookup, log 생성, worker 상태 저장 범위다.

## 외부 전송 hard guard

- Spring profile은 실제 PostgreSQL/Redis를 쓰는 `local`만 허용하고 `test|docker|prod` 및 그 밖의 profile은 즉시 거부한다.
- `PERF_FCM_ADAPTER=fake`가 아니면 즉시 거부한다.
- `FIREBASE_CONFIG_JSON` 또는 `FIREBASE_CONFIG_PATH`가 존재하면 즉시 거부한다.
- Java harness는 `@ActiveProfiles("local")`과 test-only `FakeFcmSendPort`를 사용하며 Firebase class를 참조하지 않는다.
- datasource/Redis/`ddl-auto=validate`/Flyway-off 값을 Spring context 생성 전에 `@DynamicPropertySource`로 고정하고, context-startup `ComposeCoffeeCatalogSeedRunner`는 test mock으로 대체해 dummy token/log 밖의 row mutation을 막는다.
- 사용자가 runtime에 명시한 `PERF_EXPECTED_COMPOSE_PROJECT`와 PostgreSQL/Redis의 실제 `com.docker.compose.project` label이 정확히 같아야 하며, label 누락과 `faithlog-latest` shared stack을 즉시 거부한다.
- Spring의 PostgreSQL/Redis endpoint는 선택된 두 컨테이너의 실제 published host port로 runner가 강제하고 Java harness가 URL/host/port/DB를 다시 검증한다. inherited endpoint와 `ddl-auto=update`는 사용하지 않는다.
- `docker compose up/build/down`, restart, volume 삭제, prune은 어떤 script에도 없다.

따라서 실제 Firebase 또는 external FCM 전송은 구조적으로 실행되지 않는다.

## Dataset과 fixtureRunId

`PERF_DATASET_ID`는 이름이 정확히 datasetId 또는 `<datasetId> Campus`인 기존 PERFORMANCE 1,000명 campus를 식별하고 `PERF_FIXTURE_RUN_ID`는 한 번의 warmup 또는 measured sample을 식별한다. 준비 script는 user/campus/member를 만들거나 수정하지 않는다. 선택된 ACTIVE member가 정확히 1,000명인지 확인한 뒤 아래 test-only dummy token만 저장한다.

- active token immediate success
- active token transient failure 1회 후 success
- active token permanent failure
- active success token과 permanent token을 함께 가진 mixed-token sentinel 사용자 1명
- inactive token only
- no token

각 user category count는 실행자가 명시하며 모두 1 이상이고 합계가 1,000이어야 한다. mixed-token sentinel은 success category의 첫 사용자에게 permanent dummy token 1개를 추가하는 고정 correctness probe이며 category 사용자 수를 바꾸지 않는다. 이 때문에 active token 있음/없음 비율과 injected failure 비율이 manifest에 명확하게 남고, 시나리오가 임의 비율을 결정하지 않는다.

재측정 시 선택 사용자에 속한 이전 `PERFORMANCE_198_DUMMY:` exact prefix의 active token을 inactive로 바꾼 뒤 새로운 fixtureRunId dummy token을 추가한다. 즉, 과거 #198 fixture token은 수정하지만 실제 token과 다른 이슈 fixture token은 수정하지 않는다. SQL wildcard `LIKE`를 사용하지 않으므로 `PERFORMANCEX198XDUMMY:` 같은 near-prefix 실제 token은 dummy로 분류하거나 수정하지 않는다. 선택 사용자에 dummy가 아닌 active token이 하나라도 있으면 preparation이 fail closed한다. permanent cohort를 먼저 배치하고 뒤에 success cohort가 오도록 해 permanent failure 이후에도 후속 사용자가 SENT가 되는지를 관찰한다. 생성되는 notification log의 title/body에도 fixtureRunId와 test-only 표식을 넣는다. token 원문, 비밀번호, credential은 report에 쓰지 않는다.

manifest에는 실제 Compose project와 PostgreSQL database도 기록한다. runner는 현재 guard가 확인한 project/database와 manifest가 정확히 일치하지 않으면 실행을 거부하므로, 다른 전용 stack에서 fixture manifest를 재사용할 수 없다.

ignored 산출물:

```text
build/reports/k6/notification-batch/
  fixtures/
    rejections/<fixtureRunId>.json
    <fixtureRunId>/
      manifest.json
      fixture-status.json
      runtime-identity-locked.json
      runtime-identity-before-fixture.json
      runtime-identity-after-fixture.json
      runtime-continuity-pre-fixture.json
      runtime-continuity-report.json
  rejections/<runId>.json
  runs/<runId>/
    environment.json
    run-status.json
    scenario-result.json
    verification-report.json
    runtime-continuity-report.json
    runtime-continuity-post-lock.json
    runtime-continuity-pre-workload.json
    runtime-identity-locked.json
    runtime-identity-initial.json
    runtime-identity-before.json
    runtime-identity-after.json
    runtime-identity-final.json
    evidence-window.json
    postgres-before.json
    postgres-after.json
    pgss-before.json
    pgss-after.json
    redis-before.json
    redis-after.json
    docker-stats.csv
    harness-source-{prelock,locked,preworkload,postworkload,final}.json
    harness-artifact-{preworkload,postworkload,final}.json
    harness-provenance-report.json
    gradle-harness-build.log
    gradle-scenario.log
```

## Fixture preparation — 지금은 실행 금지

아래는 PM이 별도 실행을 승인한 뒤 dedicated non-shared Compose project에서만 사용한다. count 값은 승인된 분포를 runtime에 넣고 저장소에 credential을 기록하지 않는다.

```bash
ALLOW_NOTIFICATION_BATCH_BASELINE=true \
PERF_REPORT_ROOT=<optional-fresh-exclusive-report-root> \
PERF_SPRING_PROFILE=local \
PERF_FCM_ADAPTER=fake \
PERF_EXPECTED_COMPOSE_PROJECT=<user-approved-dedicated-project> \
PERF_EXPECTED_POSTGRES_ROLE=<user-approved-direct-owner-role> \
POSTGRES_CONTAINER=<dedicated-postgres-container> \
REDIS_CONTAINER=<dedicated-redis-container> \
PERF_EXPECTED_POSTGRES_CONTAINER_ID=<full-container-id> \
PERF_EXPECTED_REDIS_CONTAINER_ID=<full-container-id> \
PERF_EXPECTED_POSTGRES_SERVICE=<compose-service> \
PERF_EXPECTED_REDIS_SERVICE=<compose-service> \
PERF_EXPECTED_POSTGRES_IMAGE_ID=<immutable-image-id> \
PERF_EXPECTED_REDIS_IMAGE_ID=<immutable-image-id> \
POSTGRES_USER=<user-approved-direct-owner-role> \
POSTGRES_PASSWORD=<runtime-only-secret> \
POSTGRES_DB=<user-approved-dedicated-database> \
PERF_REDIS_AUTH_MODE=<none-or-password> \
PERF_MEMBER_COUNT=1000 \
PERF_DATASET_ID=PERFORMANCE_<dataset> \
PERF_FIXTURE_RUN_ID=<fresh-fixture-run-id> \
PERF_SAMPLE_KIND=warmup \
PERF_CAMPUS_ID=<performance-campus-id> \
PERF_SUCCESS_COUNT=<positive-count> \
PERF_TRANSIENT_COUNT=<positive-count> \
PERF_PERMANENT_COUNT=<positive-count> \
PERF_INACTIVE_COUNT=<positive-count> \
PERF_NO_TOKEN_COUNT=<positive-count> \
bash performance/k6/notification-batch/prepare-fixtures.sh
```

measured sample은 새 `PERF_FIXTURE_RUN_ID`와 `PERF_SAMPLE_KIND=measured`로 별도 준비한다. preparation은 measurement runner 안에서 자동 호출되지 않는다.

## Single-sample runner — 지금은 실행 금지

```bash
ALLOW_NOTIFICATION_BATCH_BASELINE=true \
PERF_REPORT_ROOT=<optional-fresh-exclusive-report-root> \
PERF_SPRING_PROFILE=local \
PERF_FCM_ADAPTER=fake \
PERF_EXPECTED_COMPOSE_PROJECT=<user-approved-dedicated-project> \
PERF_EXPECTED_POSTGRES_ROLE=<user-approved-direct-owner-role> \
POSTGRES_CONTAINER=<dedicated-postgres-container> \
REDIS_CONTAINER=<dedicated-redis-container> \
PERF_EXPECTED_POSTGRES_CONTAINER_ID=<full-container-id> \
PERF_EXPECTED_REDIS_CONTAINER_ID=<full-container-id> \
PERF_EXPECTED_POSTGRES_SERVICE=<compose-service> \
PERF_EXPECTED_REDIS_SERVICE=<compose-service> \
PERF_EXPECTED_POSTGRES_IMAGE_ID=<immutable-image-id> \
PERF_EXPECTED_REDIS_IMAGE_ID=<immutable-image-id> \
POSTGRES_USER=<user-approved-direct-owner-role> \
POSTGRES_PASSWORD=<runtime-only-secret> \
POSTGRES_DB=<user-approved-dedicated-database> \
PERF_REDIS_AUTH_MODE=<none-or-password> \
PERF_BUSINESS_DATE=<YYYY-MM-DD> \
PERF_DOCKER_STATS_SAMPLE_INTERVAL_SECONDS=<user-approved-1-to-60> \
PERF_DOCKER_STATS_MAX_GAP_MILLISECONDS=<user-approved-cadence-to-300000> \
PERF_EXPECTED_HARNESS_HEAD=<approved-final-40hex-head> \
PERF_EXPECTED_HARNESS_CONTRACT_DIGEST=<approved-final-64hex-contract-digest> \
RUN_ID=<fresh-run-id> \
MANIFEST_PATH=build/reports/k6/notification-batch/fixtures/<fixtureRunId>/manifest.json \
bash performance/k6/notification-batch/run-before.sh
```

`PERF_REPORT_ROOT`는 생략하면 기존 ignored `build/reports/k6/notification-batch`를 사용한다. 지정할 때도 매 fixtureRunId/runId 디렉터리는 exclusive create되므로 이미 존재하거나 거부 이력이 있는 namespace를 재사용하지 않는다.

fixture preparation과 runner는 #198 전용 host-global `/tmp/faithlog-performance-global.lock`과 저장소 performance runner의 canonical `/tmp/faithlog-performance-${actualComposeProject}.lock`을 같은 순서로 함께 획득한다. 실제 Compose project 검증 뒤 canonical lock 획득에 실패하면 fixture SQL 또는 Gradle을 시작하지 않는다. 따라서 같은 Compose project를 쓰는 다른 issue runner와 상호 배제되며, global lock은 서로 다른 #198 worktree/project의 동시 실행까지 막는 추가 안전장치일 뿐 canonical project lock을 대체하지 않는다. 별도 project가 실제로 같은 외부 자원을 공유하는 구성은 lock만으로 판별할 수 있으므로 실행자는 병렬 부하 부재를 계속 확인한다. runner는 clean index/worktree만 허용하고 run ID가 이미 있으면 overwrite하지 않고 즉시 거부한다.

lock 획득 직후 fixture prep과 runner는 published host port를 포함한 PostgreSQL/Redis container `.Id`, image ID, `.State.StartedAt`, Compose project/service/config hash와 server identity를 다시 읽고 post-lock truth로 고정한다. pre-lock 값은 canonical lock 이름을 찾는 project discovery에만 사용한다. runner는 locked/initial/before/after/final 다섯 시점, fixture prep은 locked/before-fixture/after-fixture 세 시점을 exact 비교하므로 guard와 lock 사이, SQL/workload 직전, fixture mutation 도중의 same-name 교체도 실패한다. PostgreSQL은 current database, server address/port, postmaster start time을, Redis는 run ID, port, monotonic uptime을 함께 고정한다. 같은 container 이름과 image/project를 유지한 재생성도 container ID, host port 또는 server identity가 달라 실패한다.

Docker stats뿐 아니라 fixture SQL, PostgreSQL/Redis snapshot, runtime server identity query도 container 이름이 아니라 locked immutable container ID를 대상으로 사용한다. 각 resource sample 직전에 실제 `.Id`를 다시 읽어 expected ID와 대조한 값만 CSV에 기록한다. Redis `cmdstat_set` 누락은 0으로 보정하지 않고 capture 단계에서 실패한다. fixture prep과 runner는 lock 획득 전에 HUP/INT/TERM/EXIT cleanup을 설치한다. runner는 background sampler를 종료·wait하고 marker를 지운 뒤 자신이 실제 획득한 두 lock만 해제하며, 다른 runner가 선점한 canonical lock은 절대 제거하지 않는다.

runner는 `before` identity 직후 `locked,initial,before`를 즉시 비교하고 통과한 경우에만 sampler와 Gradle workload를 시작한다. 최종 비교도 inherited `RUNTIME_IDENTITY_PHASES`를 사용하지 않고 `locked,initial,before,after,final` exact phase를 명시하므로 실행자가 phase 검사를 축소할 수 없다.

PostgreSQL/Redis counter와 Docker stats의 window는 Gradle 실행, Spring startup, measured method, correctness replay/postflight를 모두 포함하는 `gradle-spring-harness-lifecycle`이다. creation/delivery phase duration 또는 per-user Hibernate prepared statements와 직접 대응시키지 않으며 harness-wide 보조 evidence로만 집계한다. PostgreSQL snapshot은 exact current database, non-null `stats_reset`, ordered `capturedAt`, database counter set과 `campus_members|user_fcm_tokens|notification_logs` exact table set을 요구한다. PostgreSQL cumulative bigint와 Redis DBSIZE/SET은 canonical decimal string으로 저장하고 BigInt로만 차감한다. `pg_stat_statements`는 extension을 생성하거나 reset하지 않고 available이면 database/stats-reset/row counters의 continuity를, unavailable이면 동일한 `extension-not-installed` evidence를 before/after 모두 요구한다. Redis snapshot은 exact run ID/uptime/port/DBSIZE/SET count schema를 요구한다. 누락·null·number/malformed-string counter, reset, 감소, under/over-count는 실패한다. Docker CSV는 full immutable ID, CPU percent, RAM used/limit bytes와 RAM percent, approved runtime cadence와 maximum gap, PostgreSQL+Redis exact 두 row의 sample instant가 최소 2개 있어야 하고 workload 시작 전부터 종료 후까지 덮어야 한다. 다른 container, mixed identity, 한 시점 sample, 순서·maximum-gap·coverage 위반은 실패한다. Redis SET은 creation+replay 2,000회와 delivery lock 1회의 exact count를 요구한다.

모든 verified/failed/prepared 결과는 `accepted=false`, `automaticAdoption=false`다. 실패 시 ignored `rejections/` 아래 first machine-readable rejection을 exclusive-create로 한 번만 기록해 뒤의 cleanup/verification 실패가 최초 stage와 reason을 덮어쓰지 않는다. credential/token 원문은 rejection에 포함하지 않는다.

## p50/p95/p99/max 집계 — 지금은 실행 금지

한 fixtureRunId는 생성 1회와 delivery 1회의 독립 sample이다. 현재 runner는 sample마다 `--no-daemon` test JVM을 새로 시작하므로 `cold-jvm-per-sample`이고, warmup은 PostgreSQL/Redis 외부 cache만 데우며 JVM JIT/Spring bean/connection pool을 measured sample과 공유하지 않는다. sample마다 이전 dummy token/log/dedupe history가 남으므로 additive fixture만으로는 두 번째 sample의 pre-run token/log cardinality, relation bytes, Redis DBSIZE가 첫 sample과 같을 수 없다.

따라서 현재 summarizer는 fail-closed disabled다. `EXPECTED_WARMUP_SAMPLES`와 `EXPECTED_MEASURED_SAMPLES`를 runtime 필수값으로 받아 각각 정확히 일치하는지, 모든 sampleKind가 `warmup|measured`인지, fixtureRunId가 전체에서 유일한지 먼저 검증한다. warmup은 최소 1개, measured는 percentile을 위해 최소 2개이며 단일 measured 또는 warmup 0은 허용하지 않는다. `CUMULATIVE_STATE_STRATEGY`도 `snapshot-restore|fixture-only-cleanup` 중 하나를 runtime에 명시해야 하지만, 어느 전략도 아직 사용자 승인·구현되지 않았으므로 count 검증을 통과해도 non-zero로 종료하고 summary를 만들지 않는다. 이 세션은 snapshot/cleanup을 실행하거나 임의 선택하지 않는다.

권고는 isolated performance stack의 전체 상태 동등성을 가장 직접적으로 증명하는 `snapshot-restore`다. 다만 snapshot 생성·복구 lifecycle의 별도 사용자 승인이 선행되어야 한다. `fixture-only-cleanup`은 변경 범위가 좁지만 누락된 side effect가 sample 간 누적될 위험이 있다. exact warmup/measured 횟수는 실제 측정 window 비용과 percentile 정책을 사용자가 결정할 때까지 미설정 상태를 유지한다.

```bash
RUN_DIRS_FILE=<approved-run-directory-list.txt> \
OUTPUT_PATH=build/reports/k6/notification-batch/before-summary.json \
EXPECTED_WARMUP_SAMPLES=<user-approved-exact-count> \
EXPECTED_MEASURED_SAMPLES=<user-approved-exact-count-at-least-2> \
CUMULATIVE_STATE_STRATEGY=<snapshot-restore-or-fixture-only-cleanup> \
node performance/k6/notification-batch/summarize-before.mjs
```

위 명령은 현재 의도적으로 실패한다. 사용자가 하나의 cumulative-state 전략과 실행 모델을 승인하고, 해당 restore/cleanup 구현이 매 sample의 동일 pre-run fingerprint를 증명한 뒤에만 fail-closed gate를 제거할 수 있다. 그 이후 summary는 creation/delivery 각각 다음을 만든다.

- 전체 duration p50/p95/p99/max
- throughput p50/p95/p99/max
- Hibernate prepared statement count와 `prepared statements / 1,000 targets`인 per-user DB calls
- Java process CPU duration/heap delta와 container CPU/RAM raw samples
- log/token insert/update count
- `providerFakeFailureRate = permanent fake send failures / fake send attempts`
- `finalLogFailureRate = final FAILED logs / PENDING logs`

injected permanent failure는 현재 partial failure 정책을 검증하기 위한 데이터이며 harness 실패로 해석하지 않는다. failed run은 `run-status.json`에 남지만 percentile 집계 입력은 correctness를 통과한 verified run으로 제한한다.

## Correctness 계약

각 sample은 다음을 통과해야 `verification-report.json`이 생성된다.

- target ACTIVE member 정확히 1,000명, user 중복 0
- creation log 정확히 1,000개
- PENDING = success + transient + permanent
- SKIPPED = inactive + no-token
- delivery SENT = success + transient
- delivery FAILED = permanent
- permanent dummy token inactive/update count = permanent-only users + mixed-token sentinel 1
- exact dedupe replay 생성 log 0
- scheduler가 넘긴 same-campus ACTIVE 1,000명 목록과 request log 사용자 집합이 정확히 일치
- fixture 외 token mutation 0
- 앞쪽 permanent failure 뒤에 배치된 success 사용자가 실제 SENT가 되는 partial failure continuation
- success+permanent mixed-token 사용자의 log는 SENT이고 permanent dummy token만 inactive가 되는 사용자 내부 partial failure

현재 campus isolation은 scheduler가 같은 campus ACTIVE member ID만 command에 전달한다는 production 계약에 의존한다. 코드 조사상 request service 자체는 전달된 target ID의 campus membership을 다시 검증하지 않는다. harness는 datasetId와 campus name을 DB에서 다시 묶고 same-campus ACTIVE member 1,000명 목록과 request log 사용자 집합의 exact equality만 실행 검증한다. cross-campus sentinel을 실제로 생성하지 않으므로 코드 관찰을 runtime correctness 결과로 기록하지 않으며 production 권한/트랜잭션 의미도 바꾸지 않는다.

## 변경하지 않는 범위

- batch size 후보, creation token bulk query, NotificationLog batch insert/update, Redis dedupe pipeline/Lua는 적용하지 않는다. #200에서 이미 적용된 delivery request-wide bulk snapshot은 current before 동작으로 보존한다.
- production Java/API/권한/응답/오류/트랜잭션/Entity/DB/Flyway/의존성을 변경하지 않는다.
- shared Docker lifecycle과 운영 profile을 사용하지 않는다.
- 실제 baseline 수치와 개선 성과를 기록하지 않는다.
