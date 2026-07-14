# Issue #194 PostgreSQL EXPLAIN Before-Baseline Scenario

이 디렉터리는 #192, #193, #195, #196, #197, #198, #199에서 식별된 현재 query flow 재구성 SQL과 미래 최적화 candidate SQL의 실행계획을 분리 수집하기 위한 시나리오 계약이다. 현재 상태는 `scenario-ready / not-measured`이며, 이 변경에서는 PostgreSQL, Docker, `EXPLAIN`, `ANALYZE`를 실행하지 않았다. 현재 inventory에는 실제 Hibernate SQL을 캡처해 1:1 fingerprint로 연결한 `exact-current-production` 항목이 없으므로, 어떤 결과도 production before 성과로 채택할 수 없다.

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
- #192/#193/#195/#196/#197/#198/#199별 JSON artifact의 상대 경로
- inventory SQL에 주입할 `anchors`

구조 예시는 [`cross-issue-report.example.json`](cross-issue-report.example.json)에 있다. 예시 ID와 경로는 측정값이 아니며 실제 실행에 그대로 사용하지 않는다. 각 artifact는 `issueNumber`, `datasetId`, `fixtureRunId`, `memberCount=1000`, `expectedAnchors`, 해당 이슈가 이미 정의한 상태를 포함해야 한다. 현재 #193은 `conditional-shared-stack`, #196과 #199는 `conditional-not-adoptable`만 생성하며 automatic adoption을 금지하므로 #194 runner도 세 이슈를 pending으로 fail closed한다. 과거 도구가 생성하지 않는 `eligible-for-pm-review`, `measured+accepted`, `adoptable` 문자열이나 일반 `approved=true` 필드를 성공 계약으로 간주하지 않는다. 실제 측정·PM/사용자 채택 뒤에는 각 이슈의 정확한 artifact schema와 별도 승인 증거 필드를 연결하는 사용자 승인 계약 변경이 먼저 필요하다. 그 전까지 cross-issue preflight는 Docker/psql/EXPLAIN 전에 의도적으로 중단된다. runner는 cross report가 있는 디렉터리를 기준으로 경로를 resolve하고 절대 경로, `..`, symlink, 디렉터리, 중복 real path를 거부하며 JSON schema/identity/status와 SHA-256을 검증한다.

모든 accepted artifact의 `expectedAnchors`는 cross report의 `anchors`와 byte-semantic하게 정확히 같아야 한다. `anchors.expected_state`에는 accepted fixture가 기록한 member/poll/meal/account/prayer status와 poll title, account nickname, season name, account owner를 넣는다. 이어지는 read-only DB preflight는 이 fixture identity, campus/parent/owner 관계, 공통 ACTIVE member 1,000명 및 각 anchor의 exact-one cardinality를 검사한다. 불일치하면 구조화된 rejected report를 남기고 EXPLAIN을 0회 실행한다.

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
WARM_RUNS
ACTIVITY_SAMPLE_INTERVAL_MS
```

`POSTGRES_CONTAINER`는 실제 측정 대상 PostgreSQL 컨테이너 ID 또는 이름이다. runner는 `docker inspect` 결과의 다음 label을 최종 report에 기록하며, label이 없으면 중단한다.

```text
com.docker.compose.project
com.docker.compose.service
com.docker.compose.project.config_files
com.docker.compose.project.working_dir
```

runner는 pre-lock inspect 뒤 canonical lock을 획득하고 즉시 같은 container를 다시 inspect한다. 이어 DB identity psql 직후 다시 inspect해 container ID/name, image ID/reference, start time, Compose project/service/config/working directory, `POSTGRES_DB`, internal port와 정규화 network identity로 DB identity capture를 양쪽에서 bracket한다. 이 구간에서 하나라도 바뀌면 schema/anchor/EXPLAIN을 0회로 중단한다. 측정 종료에는 planner/schema/DB after-state를 모두 수집한 뒤 마지막 inspect를 수행해 전체 구간을 bracket한다. inspect 결과의 database/network/start time과 psql이 반환한 `inet_server_addr()`, `inet_server_port()`, `current_database()`, `pg_postmaster_start_time()`도 실행 전후 비교한다. 따라서 lock 사이 또는 identity capture 사이 container 교체, 독립 `PGHOST`/`PGPORT`가 다른 로컬·원격 DB를 가리키는 상태, 같은 container의 다른 database 접속을 승인 target 결과로 보고할 수 없다.

실행 전 runner는 symlink를 허용하지 않는 contained file reader로 `inventory.json`, `report-contract.json`, [`source-manifest.json`](source-manifest.json)을 각각 한 번 읽고, 그 동일 immutable bytes를 JSON parse와 provenance SHA-256 양쪽에 사용한다. 현재 Git commit과 dirty 여부, 각 SQL 원문 byte SHA-256, inventory가 참조하는 production source 39개의 SHA-256도 기록한다. manifest의 승인된 content hash와 하나라도 다르거나 worktree가 dirty면 Docker/psql/EXPLAIN 전에 fail closed한다. SQL은 단일 read-only SELECT/WITH 문이어야 하고 모든 backslash/psql meta-command를 금지해 stdin 실행에서 inline `\g`, 로컬 shell 또는 추가 statement로 이탈할 수 없다. normalized/query report에는 `sqlFile` 상대 경로와 `sqlSha256`을 함께 기록하므로 SQL 변경을 plan/index 변화로 오인할 수 없다.

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
WARM_RUNS=APPROVED_INTEGER_1_TO_20 \
ACTIVITY_SAMPLE_INTERVAL_MS=USER_APPROVED_POSITIVE_INTEGER \
performance/k6/postgresql-explain-index-baseline/run-baseline.sh
```

`WARM_RUNS`는 사용자 승인된 1~20 정수로 runtime에 반드시 전달해야 하며 기본값이 없다. 누락 또는 범위 밖 값은 Docker/psql 실행 전에 실패한다.

`ACTIVITY_SAMPLE_INTERVAL_MS`도 기본값 없는 필수 양의 정수다. observer query 빈도와 측정 오버헤드를 결정하므로 실제 값은 측정 창마다 사용자가 승인해 명시적으로 전달해야 한다. runner는 임의 methodology 범위를 정하지 않으며 누락, 0, 음수, 비정수는 Docker/psql 실행 전에 거부하고 선택된 값을 report identity에 기록한다.

runner는 PostgreSQL container의 실제 Compose project/service를 먼저 inspect한 뒤 `/tmp/faithlog-performance-{actualComposeProject}.lock`을 원자적으로 획득한다. #193/#195 등 같은 canonical lock을 사용하는 다른 performance scenario가 같은 Compose project를 사용 중이면 즉시 실패한다. 획득 당시 device/inode를 측정 종료 전과 release 시 다시 확인하며 lock이 사라지거나 교체되면 fail-closed한다. 종료 시 자신이 획득한 빈 directory만 비재귀 `rmdir`로 해제하며, 예상 밖 파일이 있으면 재귀 삭제하지 않고 실패한다. measured psql 또는 observer를 SIGTERM/SIGKILL 뒤에도 reap하지 못하면 살아 있는 child와 병렬 실행되지 않도록 canonical lock을 의도적으로 남기고 수동 조사를 요구한다. 같은 millisecond에 시작한 runner도 서로 다른 CSPRNG suffix report directory를 사용하므로 lock loser의 rejected report가 winner의 파일을 덮어쓰지 않는다. 이 lock을 사용하지 않는 외부 도구가 실행 중이지 않은지는 실행자가 수동 게이트로 별도 확인해야 한다.

## cold-like와 warm cache 구분

이 시나리오는 cache를 비우거나 PostgreSQL/컨테이너를 재시작하지 않는다.

- `cold_like_observation`: 각 SQL의 첫 순차 관찰 1회다. 실제 cold cache 측정이 아니며 report의 `cacheResetPerformed`는 항상 `false`다.
- `warm_cache`: 같은 SQL의 후속 순차 반복이다. 첫 관찰 뒤의 cache 상태를 포함하지만 외부 activity와 autoanalyze 변화가 없을 때만 같은 조건으로 해석한다.

따라서 `cold_like_observation`과 `warm_cache`의 차이를 cold-start 개선 수치로 표현하면 안 된다. 진짜 cold cache 실험은 shared lifecycle/cache 정책 변경이므로 별도 사용자 승인이 필요하다.

## planner-state와 autoanalyze 증거

runner는 전체 inventory 실행 전후에 다음을 기록하고 채택 gate로 사용한다.

- snapshot `capturedAt`, PostgreSQL server version
- cost/cache/work_mem/statistics target와 scan/join enable 설정
- 대상 테이블별 `last_analyze`, `last_autoanalyze`, `n_mod_since_analyze`, live/dead tuple 추정치
- 대상 테이블별 `last_vacuum`, `last_autovacuum`, vacuum/autovacuum count, `relallvisible`
- 성공한 Flyway history와 대상 테이블 column/constraint/index definition의 canonical schema/index fingerprint
- 같은 PostgreSQL instance의 모든 database에서 runner 외 client backend의 database, application name, state, wait, query start
- 전후 `lastAnalyze`/`lastAutoanalyze`/`nModSinceAnalyze` 변화

runner는 수동 `ANALYZE`를 실행하지 않는다. `EXPLAIN ANALYZE` 수집 시각은 각 query run의 `capturedAt`으로 별도 기록한다. 시작 snapshot의 필수 setting/table/schema evidence가 없거나 PostgreSQL instance 어디서든 runner 외 active client backend가 있으면 어떤 EXPLAIN도 실행하지 않는다. 전체 query/phase 측정 구간에는 하나의 연속 read-only observer가 사용자 승인 runtime interval과 window 종료 시점에 instance 전체 `pg_stat_activity`를 sampling한다. 타 role 상세 visibility가 제한돼 `state=NULL`인 client backend도 SQL `IS DISTINCT FROM 'idle'`로 오염에 포함하므로 권한 부족을 clean으로 해석하지 않는다. runner는 psql을 만들기 전에 query별 CSPRNG application name, registration token, label을 observer에 `PREPARE`해 같은 prefix의 다른 session도 첫 관측부터 외부 activity로 구분한다. measured psql이 backend PID와 backend start instant를 공개하면 prepared token에 exact identity를 `BIND`하고, observer가 idle 대기 상태를 포함해 이를 실제 관측한 token ACK 뒤에만 EXPLAIN을 시작한다. psql 종료 뒤에는 token unregister ACK까지 받아 PID 재사용이 과거 측정 identity로 제외되지 않게 하며, 최종 label/count/lifecycle도 예정 workload와 정확히 비교한다. observer 자신의 PID와 등록된 measured backend의 exact PID/application/backend-start만 제외한다. 다른 database의 짧은 client activity는 `other-database-activity-*` reason으로, 같은 database의 외부 activity와 visibility가 제한된 session은 일반 transient reason으로 누적되며 모두 non-adoptable이다. SIGTERM은 승인 interval의 남은 delay를 즉시 깨운 뒤 final sample과 0600 window report를 보존하므로 큰 interval을 임의 상한으로 바꾸지 않는다. 미등록 active session이 관측되거나 observer 자체가 종료되면 main runner가 진행 중 psql을 즉시 종료해 partial invalid report로 중단한다. 실행 중 planner settings, postmaster, analyze/autoanalyze, vacuum/autovacuum, visibility, schema/index fingerprint, DB/Compose container identity가 바뀌거나 외부 activity가 관측되면 invalid/pending report를 남기고 non-zero 종료한다.

시작 gate와 runtime 도중 실패도 ignored report 디렉터리의 `baseline-report.json`에 0600으로 보존한다. `invalid-pending-start-integrity`는 `queryRunCount=0`, runtime 실패는 완료된 partial count와 machine-readable reason을 기록한다. credential 값과 raw stderr/error text는 report에 기록하지 않는다.

## plan 정규화 및 비교 계약

[`normalize-plan.mjs`](normalize-plan.mjs)는 FORMAT JSON을 다음 두 부분으로 나눈다.

- `structure`: 재귀 parent/children topology와 node/relation/index/join/scan/sort/group/condition 구조. literal 숫자와 문자열을 placeholder로 정규화한다. 편의상 flat node 목록도 함께 제공하지만 hash는 topology를 포함한다.
- `metrics`: root estimated/actual rows와 loops, node별 rows/loops, Seq/Index/Index Only/Bitmap scan 수, shared hit/read, rows removed, sort method/space 사용량과 Memory/Disk type, planning/execution time.

`planHash`는 timing, actual rows, loops, buffer 수치를 제외한 정규화 recursive `structure`의 SHA-256이다. 같은 topology에서 runtime 숫자만 바뀌면 hash는 같아야 하고, flat DFS node 순서가 같더라도 parent/child topology가 다르면 hash가 달라야 한다. hash가 바뀐 비교는 단순 latency 차이가 아니라 plan shape 변경으로 따로 보고한다.

## SQL inventory와 후보 기록

[`inventory.json`](inventory.json)은 각 SQL을 원 이슈와 연결하고 다음을 명시한다.

- 기대 predicate column
- order column
- join column
- 검토할 candidate index 형태
- row count/campus isolation/status/rounding 등 correctness check
- evidence class, production source reference, production-before eligibility

evidence class는 다음 세 가지다.

- `exact-current-production`: 실제 Hibernate SQL을 캡처하고 fingerprint로 1:1 연결한 SQL. 이 class만 production before 성과 후보가 될 수 있다.
- `reconstructed-current-query`: 현재 Repository/query flow를 사람이 재구성한 진단 SQL. query shape 참고용이며 production before latency/개선 수치에 사용할 수 없다.
- `synthetic-candidate`: 여러 현재 query와 Java 집계를 future join/CTE/aggregate 형태로 합성한 hypothetical SQL. candidate 실행계획 참고용이며 production before 성과에 사용할 수 없다.

현재 inventory는 reconstructed 2개와 synthetic 22개뿐이고 모든 `productionBeforeEligible=false`다. 특히 #193 DB filter/page, #197 두 cleanup DELETE를 합친 read-only count surrogate, #199 dashboard CTE는 합성 candidate로 분리된다. report contract의 production-before activation도 `false`이므로 runner와 `evidence-contract.mjs`는 임의 fingerprint나 reconstructed/synthetic 승격을 실행 전에 거부한다. 실제 Hibernate SQL capture artifact와 실행 SQL shape를 1:1 검증하는 별도 사용자 승인 계약 없이는 activation을 켤 수 없다.

candidate는 검토 기대치일 뿐 DDL 승인이나 성능 개선 결론이 아니다. 특히 아래 Issue #194 후보는 실제 before evidence 후 PM에 제안할 수 있을 뿐, 이 시나리오에서는 생성하지 않는다.

- `polls(status, ends_at, id)`와 `COFFEE + OPEN` partial 방향
- `weekly_devotion_records(campus_id, week_start_date, user_id)`
- `charge_items`의 campus/status/user 및 campus/account/status/user 방향
- `notification_logs(send_status, created_at, id)`
- `meal_poll_charge_groups(settlement_id, id)`

## report와 정확성 계약

raw/normalized/final report는 issue-local ignored 경로 `performance/k6/postgresql-explain-index-baseline/reports/`에만 생성한다. 파일 권한은 0600으로 만들며 credential 값을 포함하지 않는다.

정상 planner integrity라도 현재 inventory 결과 status는 `measured-diagnostic-not-production-baseline`이다. 향후 실제 Hibernate SQL capture와 fingerprint가 추가된 exact 항목만 별도 승인 후 `measured-before-index-baseline` 후보가 될 수 있다.

비교 가능한 baseline의 identity는 최소한 다음이 모두 같아야 한다.

```text
datasetId + fixtureRunId + queryId + phase + runNumber + planHash
+ Compose project/service + PostgreSQL container ID
+ SQL byte hash + inventory/report-contract/source-manifest hash + production source hashes
+ Git commit/clean state + schema/index fingerprint
```

각 원 이슈의 API-level 정확성 결과는 cross-issue report가 담당하고, #194는 SQL별 `correctnessChecks`와 실행계획을 연결한다. #194 runner만으로 API 응답, 권한, 트랜잭션 rollback, 생성 row 수를 새로 증명했다고 보고하지 않는다.

## 정적 검증

DB/Docker 없이 실행할 수 있는 검증만 제공한다.

```bash
node --test \
  performance/k6/postgresql-explain-index-baseline/test/scenario-contract.test.mjs \
  performance/k6/postgresql-explain-index-baseline/test/pm-second-review-contract.test.mjs
node --check performance/k6/postgresql-explain-index-baseline/run-baseline.mjs
node --check performance/k6/postgresql-explain-index-baseline/normalize-plan.mjs
node --check performance/k6/postgresql-explain-index-baseline/runtime-contract.mjs
node --check performance/k6/postgresql-explain-index-baseline/evidence-contract.mjs
node --check performance/k6/postgresql-explain-index-baseline/anchor-contract.mjs
node --check performance/k6/postgresql-explain-index-baseline/cross-issue-contract.mjs
node --check performance/k6/postgresql-explain-index-baseline/schema-contract.mjs
node --check performance/k6/postgresql-explain-index-baseline/source-identity.mjs
node --check performance/k6/postgresql-explain-index-baseline/runner-safety-contract.mjs
node --check performance/k6/postgresql-explain-index-baseline/activity-monitor-worker.mjs
node --check performance/k6/postgresql-explain-index-baseline/activity-monitor-contract.mjs
node --check performance/k6/postgresql-explain-index-baseline/rejected-report.mjs
node --test performance/k6/postgresql-explain-index-baseline/test/pm-third-review-contract.test.mjs
bash -n performance/k6/postgresql-explain-index-baseline/run-baseline.sh
```
