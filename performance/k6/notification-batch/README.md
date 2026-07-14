# Issue #198 notification batch before scenario

상태: `scenario-ready / not-measured`

이 디렉터리는 production Java를 바꾸지 않고 현재 알림 생성·전송 흐름의 `before` 기준선을 준비한다. 지금 단계에서는 fixture, notification job, Docker, DB를 실행하지 않았으며 실제 baseline 숫자나 개선 성과가 없다.

## 측정 경계

자동 알림 scheduler가 target user ID 목록을 계산한 다음 호출하는 실제 production 경계인 `NotificationRequestCommandService.requestAutomaticNotification(...)`와 실제 `NotificationDeliveryWorker.processRequest(...)`를 test-only harness에서 직접 호출한다.

생성 phase의 현재 동작:

1. 사용자마다 Redis dedupe를 `notificationType + campusId + scopeId + targetUserId + businessDate` key로 예약한다.
2. 사용자마다 `findActiveSendableTokens(userId)`를 호출한다.
3. sendable token이 있으면 PENDING, 없으면 SKIPPED notification log를 사용자별로 저장한다.
4. 하나 이상의 PENDING log가 있으면 request ID를 dispatch한다. test harness는 이 dispatch를 capture해 delivery와 분리한다.

전송 phase의 현재 동작:

1. request ID의 PENDING log를 조회한다.
2. log 사용자마다 sendable token을 다시 조회한다.
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

재측정 시 이전 `PERFORMANCE_198_DUMMY:` active token만 inactive로 바꾼 뒤 새로운 fixtureRunId dummy token을 추가한다. 실제 token 또는 다른 fixture token은 수정하지 않으며, 선택 사용자에 dummy가 아닌 active token이 하나라도 있으면 preparation이 fail closed한다. permanent cohort를 먼저 배치하고 뒤에 success cohort가 오도록 해 permanent failure 이후에도 후속 사용자가 SENT가 되는지를 관찰한다. 생성되는 notification log의 title/body에도 fixtureRunId와 test-only 표식을 넣는다. token 원문, 비밀번호, credential은 report에 쓰지 않는다.

manifest에는 실제 Compose project와 PostgreSQL database도 기록한다. runner는 현재 guard가 확인한 project/database와 manifest가 정확히 일치하지 않으면 실행을 거부하므로, 다른 전용 stack에서 fixture manifest를 재사용할 수 없다.

ignored 산출물:

```text
build/reports/k6/notification-batch/
  fixtures/<fixtureRunId>/manifest.json
  runs/<runId>/
    environment.json
    run-status.json
    scenario-result.json
    verification-report.json
    postgres-before.json
    postgres-after.json
    redis-commandstats-before.txt
    redis-commandstats-after.txt
    docker-stats.csv
    gradle-scenario.log
```

## Fixture preparation — 지금은 실행 금지

아래는 PM이 별도 실행을 승인한 뒤 dedicated non-shared Compose project에서만 사용한다. count 값은 승인된 분포를 runtime에 넣고 저장소에 credential을 기록하지 않는다.

```bash
ALLOW_NOTIFICATION_BATCH_BASELINE=true \
PERF_SPRING_PROFILE=local \
PERF_FCM_ADAPTER=fake \
PERF_EXPECTED_COMPOSE_PROJECT=<user-approved-dedicated-project> \
POSTGRES_CONTAINER=<dedicated-postgres-container> \
REDIS_CONTAINER=<dedicated-redis-container> \
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
PERF_SPRING_PROFILE=local \
PERF_FCM_ADAPTER=fake \
PERF_EXPECTED_COMPOSE_PROJECT=<user-approved-dedicated-project> \
POSTGRES_CONTAINER=<dedicated-postgres-container> \
REDIS_CONTAINER=<dedicated-redis-container> \
PERF_BUSINESS_DATE=<YYYY-MM-DD> \
RUN_ID=<fresh-run-id> \
MANIFEST_PATH=build/reports/k6/notification-batch/fixtures/<fixtureRunId>/manifest.json \
bash performance/k6/notification-batch/run-before.sh
```

fixture preparation과 runner는 Compose project와 무관한 host-global `/tmp/faithlog-performance-global.lock`을 공유해 서로 다른 #198 project/worktree도 동시에 실행하지 못하게 한다. 기존 다른 k6/frontend/Docker QA entrypoint는 이 lock을 아직 획득하지 않으므로, 실행자는 시작 전 다른 병렬 부하가 없음을 별도로 확인해야 한다. 즉 이 lock은 #198 스크립트 간 fail-closed이고 저장소 전체 QA의 강제 mutex라고 과장하지 않는다. runner는 clean index/worktree만 허용하고 실제 Compose label/host port/image ID/git commit/businessDate, PostgreSQL counter delta, Redis `INFO commandstats` delta, PostgreSQL/Redis `docker stats` peak, Java process CPU duration/heap delta를 기록한다. run ID가 이미 있으면 overwrite하지 않고 즉시 거부한다.

PostgreSQL/Redis counter와 Docker stats의 window는 Gradle 실행, Spring startup, measured method, correctness replay/postflight를 모두 포함하는 `gradle-spring-harness-lifecycle`이다. creation/delivery phase duration 또는 per-user Hibernate prepared statements와 직접 대응시키지 않으며 harness-wide 보조 evidence로만 집계한다. verifier는 이 window에 다른 부하가 없다는 실행 전제 아래 request marker log 총수 증가, active dummy-token 감소, retained Redis key 증가와 PostgreSQL notification log/token physical insert·update를 logical write와 exact count로 맞춘다. PostgreSQL 또는 Redis counter의 under/over-count를 모두 거부하는 합성 계약 테스트를 함께 둔다. Redis SET은 creation+replay 2,000회와 delivery lock 1회의 exact count를 요구한다.

## p50/p95/p99/max 집계 — 지금은 실행 금지

한 fixtureRunId는 생성 1회와 delivery 1회의 독립 sample이다. 현재 runner는 sample마다 `--no-daemon` test JVM을 새로 시작하므로 `cold-jvm-per-sample`이고, warmup은 PostgreSQL/Redis 외부 cache만 데우며 JVM JIT/Spring bean/connection pool을 measured sample과 공유하지 않는다. 또한 sample마다 이전 dummy token/log/dedupe history가 남으므로 pre-run token/log cardinality, table relation bytes, Redis DBSIZE도 workload fingerprint에 포함한다. 이 값이 다르면 summarizer는 percentile 집계를 거부한다. 실제 baseline 전에 사용자는 cold-JVM 또는 same-JVM 모델과 함께, 동일 DB/Redis snapshot 복원 또는 fixture-only cleanup 정책을 승인해야 한다. 이 세션은 snapshot/cleanup을 실행하거나 임의 선택하지 않는다. warmup과 measured fixtureRunId 목록을 명시한 파일을 만들고, 검증된 run directory만 집계한다. 반복 횟수는 baseline 실행 승인 때 사용자가 결정하며 script가 조용히 정하지 않는다.

```bash
RUN_DIRS_FILE=<approved-run-directory-list.txt> \
OUTPUT_PATH=build/reports/k6/notification-batch/before-summary.json \
node performance/k6/notification-batch/summarize-before.mjs
```

summary는 creation/delivery 각각 다음을 만든다.

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

- batch size 후보, bulk token query, NotificationLog batch insert/update, Redis pipeline/Lua는 적용하지 않는다.
- production Java/API/권한/응답/오류/트랜잭션/Entity/DB/Flyway/의존성을 변경하지 않는다.
- shared Docker lifecycle과 운영 profile을 사용하지 않는다.
- 실제 baseline 수치와 개선 성과를 기록하지 않는다.
