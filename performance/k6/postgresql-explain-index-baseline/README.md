# Issue #194 PostgreSQL EXPLAIN Before-Baseline Scenario

이 디렉터리는 #192, #193, #195, #196, #197, #198, #199에서 식별된 핵심 SQL의 production index 추가 전 실행계획을 수집하기 위한 시나리오 계약이다. 현재 상태는 `scenario-ready / not-measured`이며, 이 변경에서는 PostgreSQL, Docker, `EXPLAIN`, `ANALYZE`를 실행하지 않았다.

## 범위와 금지 사항

- 공통 1,000명 fixture의 기존 row만 조회한다.
- SQL inventory는 `SELECT` 또는 read-only CTE만 허용한다.
- 각 측정은 `BEGIN READ ONLY` 안에서 `EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON)`로 순차 실행하고 rollback한다.
- Flyway, index, HypoPG, PostgreSQL extension, Java/API/권한/응답/ErrorCode/트랜잭션/Entity/의존성을 변경하지 않는다.
- shared Docker lifecycle 명령(`up`, `down`, `restart`, `build`, `prune`)을 실행하지 않는다. runner가 사용하는 Docker 명령은 실제 Compose identity를 남기기 위한 read-only `docker inspect`뿐이다.
- fixture 정리, 기존 row 수정/삭제, planner 설정 변경, 수동 `ANALYZE`, cache reset을 수행하지 않는다.
- 다른 k6/성능/DB 시나리오와 병렬 실행하지 않는다.

## 입력 계약

`datasetId`는 논리 데이터셋을, `fixtureRunId`는 그 데이터셋을 만든 구체적인 fixture 실행을 식별한다. 두 값은 같다고 가정하지 않으며 별도 필드로 비교한다.

`CROSS_ISSUE_REPORT`는 다음을 포함한 JSON 파일이어야 한다.

- `datasetId`, `fixtureRunId`, `memberCount=1000`
- #192/#193/#195/#196/#197/#198/#199별 report 경로
- inventory SQL에 주입할 `anchors`

구조 예시는 [`cross-issue-report.example.json`](cross-issue-report.example.json)에 있다. 예시 ID와 경로는 측정값이 아니며 실제 실행에 그대로 사용하지 않는다. runner는 환경 변수의 `DATASET_ID`/`FIXTURE_RUN_ID`와 report 내부 값을 일치 검증하고 report 파일 SHA-256을 최종 보고서에 기록한다.

PostgreSQL credential은 아래 런타임 환경 변수로만 전달한다. 파일, command argument, report에는 credential 값을 저장하지 않는다.

```text
PGHOST
PGPORT
PGDATABASE
PGUSER
PGPASSWORD
```

추가 필수 입력:

```text
DATASET_ID
FIXTURE_RUN_ID
CROSS_ISSUE_REPORT
POSTGRES_CONTAINER
ALLOW_EXPLAIN_ANALYZE=true
```

`POSTGRES_CONTAINER`는 실제 측정 대상 PostgreSQL 컨테이너 ID 또는 이름이다. runner는 `docker inspect` 결과의 다음 label을 최종 report에 기록하며, label이 없으면 중단한다.

```text
com.docker.compose.project
com.docker.compose.service
com.docker.compose.project.config_files
com.docker.compose.project.working_dir
```

## 실행 전 수동 게이트

실제 baseline 수집 승인이 내려온 뒤에도 다음을 먼저 확인한다.

1. cross-issue report가 정확히 같은 1,000명 dataset과 fixture run을 가리킨다.
2. `POSTGRES_CONTAINER`가 승인된 `faithlog-latest` PostgreSQL이며 실제 Compose label이 기대한 project/service다.
3. 다른 k6, fixture, scheduler 수동 trigger, DB 측정이 실행 중이지 않다.
4. shared stack을 재시작하거나 planner/cache 상태를 인위적으로 바꾸지 않았다.
5. 외부 FCM, write API, cleanup job은 이 runner와 무관하며 실행하지 않는다.

승인된 측정 창에서만 다음 형태로 실행한다. 이 문서의 값은 placeholder다.

```bash
DATASET_ID=PERF_1000_APPROVED \
FIXTURE_RUN_ID=fixture-approved-run \
CROSS_ISSUE_REPORT=/absolute/path/to/cross-issue-report.json \
POSTGRES_CONTAINER=approved-postgres-container \
PGHOST=127.0.0.1 \
PGPORT=5432 \
PGDATABASE=faithlog \
PGUSER=runtime-user \
PGPASSWORD='runtime-secret-only' \
ALLOW_EXPLAIN_ANALYZE=true \
WARM_RUNS=3 \
performance/k6/postgresql-explain-index-baseline/run-baseline.sh
```

`WARM_RUNS`는 1~20 범위이며 기본값은 3이다. runner는 공통 `performance/k6/.faithlog-performance-runner.lock`을 원자적으로 획득한다. 같은 공통 lock을 따르는 performance runner가 이미 있으면 즉시 실패한다. 아직 이 공통 lock을 사용하지 않는 외부 도구가 실행 중이지 않은지는 실행자가 수동 게이트로 별도 확인해야 한다.

## cold-like와 warm cache 구분

이 시나리오는 cache를 비우거나 PostgreSQL/컨테이너를 재시작하지 않는다.

- `cold_like_observation`: 각 SQL의 첫 순차 관찰 1회다. 실제 cold cache 측정이 아니며 report의 `cacheResetPerformed`는 항상 `false`다.
- `warm_cache`: 같은 SQL의 후속 순차 반복이다. 첫 관찰 뒤의 cache 상태를 포함하지만 외부 activity와 autoanalyze 변화가 없을 때만 같은 조건으로 해석한다.

따라서 `cold_like_observation`과 `warm_cache`의 차이를 cold-start 개선 수치로 표현하면 안 된다. 진짜 cold cache 실험은 shared lifecycle/cache 정책 변경이므로 별도 사용자 승인이 필요하다.

## planner-state와 autoanalyze 증거

runner는 전체 inventory 실행 전후에 다음을 기록한다.

- snapshot `capturedAt`, PostgreSQL server version
- cost/cache/work_mem/statistics target와 scan/join enable 설정
- 대상 테이블별 `last_analyze`, `last_autoanalyze`, `n_mod_since_analyze`, live/dead tuple 추정치
- 같은 database에서 runner 외 active session의 application name, state, wait, query start
- 전후 `lastAnalyze`/`lastAutoanalyze`/`nModSinceAnalyze` 변화

runner는 수동 `ANALYZE`를 실행하지 않는다. `EXPLAIN ANALYZE` 수집 시각은 각 query run의 `capturedAt`으로 별도 기록한다. 전후 autoanalyze 또는 external activity 변화가 관찰되면 해당 구간은 동일 planner-state 비교로 확정하지 않고 pending evidence로 보고한다.

## plan 정규화 및 비교 계약

[`normalize-plan.mjs`](normalize-plan.mjs)는 FORMAT JSON을 다음 두 부분으로 나눈다.

- `structure`: node/relation/index/join/scan/sort/group/condition 구조. literal 숫자와 문자열을 placeholder로 정규화한다.
- `metrics`: root estimated/actual rows와 loops, node별 rows/loops, Seq/Index/Index Only/Bitmap scan 수, shared hit/read, rows removed, sort method/space, planning/execution time.

`planHash`는 timing, actual rows, loops, buffer 수치를 제외한 정규화 `structure`의 SHA-256이다. 같은 구조에서 runtime 숫자만 바뀌면 hash는 같아야 한다. hash가 바뀐 비교는 단순 latency 차이가 아니라 plan shape 변경으로 따로 보고한다.

## SQL inventory와 후보 기록

[`inventory.json`](inventory.json)은 각 SQL을 원 이슈와 연결하고 다음을 명시한다.

- 기대 predicate column
- order column
- join column
- 검토할 candidate index 형태
- row count/campus isolation/status/rounding 등 correctness check

candidate는 검토 기대치일 뿐 DDL 승인이나 성능 개선 결론이 아니다. 특히 아래 Issue #194 후보는 실제 before evidence 후 PM에 제안할 수 있을 뿐, 이 시나리오에서는 생성하지 않는다.

- `polls(status, ends_at, id)`와 `COFFEE + OPEN` partial 방향
- `weekly_devotion_records(campus_id, week_start_date, user_id)`
- `charge_items`의 campus/status/user 및 campus/account/status/user 방향
- `notification_logs(send_status, created_at, id)`
- `meal_poll_charge_groups(settlement_id, id)`

## report와 정확성 계약

raw/normalized/final report는 issue-local ignored 경로 `performance/k6/postgresql-explain-index-baseline/reports/`에만 생성한다. 파일 권한은 0600으로 만들며 credential 값을 포함하지 않는다.

비교 가능한 baseline의 identity는 최소한 다음이 모두 같아야 한다.

```text
datasetId + fixtureRunId + queryId + phase + planHash
+ Compose project/service + PostgreSQL container ID
```

각 원 이슈의 API-level 정확성 결과는 cross-issue report가 담당하고, #194는 SQL별 `correctnessChecks`와 실행계획을 연결한다. #194 runner만으로 API 응답, 권한, 트랜잭션 rollback, 생성 row 수를 새로 증명했다고 보고하지 않는다.

## 정적 검증

DB/Docker 없이 실행할 수 있는 검증만 제공한다.

```bash
node --test performance/k6/postgresql-explain-index-baseline/test/scenario-contract.test.mjs
node --check performance/k6/postgresql-explain-index-baseline/run-baseline.mjs
node --check performance/k6/postgresql-explain-index-baseline/normalize-plan.mjs
bash -n performance/k6/postgresql-explain-index-baseline/run-baseline.sh
```
