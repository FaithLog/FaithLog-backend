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

구조 예시는 [`cross-issue-report.example.json`](cross-issue-report.example.json)에 있다. 예시 ID와 경로는 측정값이 아니며 실제 실행에 그대로 사용하지 않는다. 각 artifact는 `issueNumber`, `datasetId`, `fixtureRunId`, `memberCount=1000`, `expectedAnchors`, 해당 이슈가 이미 정의한 상태를 포함해야 한다. 최신 develop의 #192/#193/#195/#196/#197/#198/#199 도구에는 사용자·PM 채택을 증명하는 별도 approval artifact schema가 없고 automatic adoption도 허용되지 않는다. 따라서 현재 7개 artifact는 모두 pending이며 #194 runner는 파일 경로·JSON·schema·identity를 전체 검사한 뒤 Docker/psql/EXPLAIN 전에 fail closed한다. 과거 도구가 생성하지 않는 `eligible-for-pm-review`, `measured+accepted`, `adoptable` 문자열이나 일반 `approved=true` 필드를 성공 계약으로 간주하지 않는다. 실제 측정·PM/사용자 채택 뒤에는 각 이슈의 정확한 artifact schema와 별도 승인 증거 필드를 연결하는 사용자 승인 계약 변경이 먼저 필요하다. runner는 cross report가 있는 디렉터리를 기준으로 경로를 resolve하고 절대 경로, `..`, symlink, 디렉터리, 중복 real path를 거부하며 JSON schema/identity/status와 SHA-256을 검증한다.

모든 accepted artifact의 `expectedAnchors`는 cross report의 `anchors`와 byte-semantic하게 정확히 같아야 한다. `anchors.expected_state`에는 accepted fixture가 기록한 member/poll/meal/account/prayer status와 poll title, account nickname, season name, account owner를 넣는다. `archive_cutoff`은 #201 archive window를 위한 기본값 없는 RFC3339 instant다. 이어지는 read-only DB preflight는 이 fixture identity, campus/parent/owner 관계, 공통 ACTIVE member 1,000명, 각 anchor의 exact-one cardinality, #200의 ACTIVE poll creator membership/COFFEE duty/account ownership/configuration, V10 coffee template account neutrality를 검사한다. 불일치하면 구조화된 rejected report를 남기고 EXPLAIN을 0회 실행한다.

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
EXPECTED_COMPOSE_PROJECT
EXPECTED_POSTGRES_SERVICE
EXPECTED_POSTGRES_IMAGE_ID
EXPECTED_POSTGRES_IMAGE_REFERENCE
ALLOW_EXPLAIN_ANALYZE=true
WARM_RUNS
ACTIVITY_SAMPLE_INTERVAL_MS
```

`POSTGRES_CONTAINER`는 실제 측정 대상 PostgreSQL 컨테이너 ID 또는 이름이다. `EXPECTED_COMPOSE_PROJECT`, `EXPECTED_POSTGRES_SERVICE`, `EXPECTED_POSTGRES_IMAGE_ID`, `EXPECTED_POSTGRES_IMAGE_REFERENCE`도 승인된 실행 창에 runtime으로 모두 전달해야 하며 fallback은 없다. runner는 inspect 결과와 네 값을 lock 전·후·종료 시점마다 exact 비교하고, 다음 label을 최종 report에 기록하며 label이 없으면 중단한다.

```text
com.docker.compose.project
com.docker.compose.service
com.docker.compose.project.config_files
com.docker.compose.project.working_dir
```

runner는 pre-lock inspect 뒤 canonical lock을 획득하고 즉시 같은 container를 다시 inspect한다. 이어 DB identity psql 직후 다시 inspect해 container ID/name, image ID/reference, start time, Compose project/service/config/working directory, `POSTGRES_DB`, `POSTGRES_USER`, internal port와 정규화 network identity로 DB identity capture를 양쪽에서 bracket한다. 이 구간에서 하나라도 바뀌면 schema/anchor/EXPLAIN을 0회로 중단한다. 측정 종료에는 planner/schema/DB after-state를 모두 수집한 뒤 마지막 inspect를 수행해 전체 구간을 bracket한다. inspect 결과의 database/user/network/start time과 psql이 반환한 `inet_server_addr()`, `inet_server_port()`, `current_database()`, `current_user`, `session_user`, `pg_postmaster_start_time()`도 실행 전후 비교한다. 따라서 lock 사이 또는 identity capture 사이 container 교체, 독립 `PGHOST`/`PGPORT`가 다른 로컬·원격 DB를 가리키는 상태, 다른 database 또는 role 접속을 승인 target 결과로 보고할 수 없다. Redis를 읽거나 결과 identity에 사용하는 시나리오가 아니므로 Redis continuity는 적용 대상이 아니다.

실행 전 runner는 symlink를 허용하지 않는 contained file reader로 `inventory.json`, `report-contract.json`, [`source-manifest.json`](source-manifest.json)을 각각 한 번 읽고, 그 동일 immutable bytes를 JSON parse와 provenance SHA-256 양쪽에 사용한다. 기준 revision은 최신 develop `6796ed146244d8f3f5b5dd7048ebe16865084a97`이며 #200/#201/#202/#206 API·runtime·V9/V10/V11 계약을 포함한다. 현재 Git commit과 dirty 여부, 각 SQL 원문 byte SHA-256, inventory와 current-develop contract가 참조하는 source 50개의 SHA-256도 기록한다. manifest의 승인된 content hash와 하나라도 다르거나 worktree가 dirty면 Docker/psql/EXPLAIN 전에 fail closed한다. SQL은 단일 read-only SELECT/WITH 문이어야 하고 모든 backslash/psql meta-command를 금지해 stdin 실행에서 inline `\g`, 로컬 shell 또는 추가 statement로 이탈할 수 없다. normalized/query report에는 `sqlFile` 상대 경로와 `sqlSha256`을 함께 기록하므로 SQL 변경을 plan/index 변화로 오인할 수 없다.

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
EXPECTED_COMPOSE_PROJECT=approved-compose-project \
EXPECTED_POSTGRES_SERVICE=approved-postgres-service \
EXPECTED_POSTGRES_IMAGE_ID=sha256:approved-image-id \
EXPECTED_POSTGRES_IMAGE_REFERENCE=approved-postgres-image-reference \
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
- decimal-string으로 보존한 PostgreSQL 누적 bigint counter와 BigInt 비교, `pg_stat_statements` extension/view 가용성·version 연속성
- 성공한 Flyway V11 history, 대상 테이블 column/constraint/index definition과 RLS/JDBC role exposure의 canonical schema/index/security fingerprint
- 같은 PostgreSQL instance의 모든 database에서 runner 외 client backend의 database, application name, state, wait, query start
- 전후 `lastAnalyze`/`lastAutoanalyze`/`nModSinceAnalyze` 변화

runner는 수동 `ANALYZE`를 실행하지 않는다. `EXPLAIN ANALYZE` 수집 시각은 각 query run의 `capturedAt`으로 별도 기록한다. 시작 snapshot의 필수 setting/table/schema evidence가 없거나 PostgreSQL instance 어디서든 runner 외 active client backend가 있으면 어떤 EXPLAIN도 실행하지 않는다. 전체 query/phase 측정 구간에는 하나의 연속 read-only observer가 사용자 승인 runtime interval과 window 종료 시점에 instance 전체 `pg_stat_activity`를 sampling한다. 타 role 상세 visibility가 제한돼 `state=NULL`인 client backend도 SQL `IS DISTINCT FROM 'idle'`로 오염에 포함하므로 권한 부족을 clean으로 해석하지 않는다. runner는 psql을 만들기 전에 query별 CSPRNG application name, registration token, label을 observer에 `PREPARE`해 같은 prefix의 다른 session도 첫 관측부터 외부 activity로 구분한다. measured psql이 backend PID와 backend start instant를 공개하면 prepared token에 exact identity를 `BIND`하고, observer가 idle 대기 상태를 포함해 이를 실제 관측한 token ACK 뒤에만 EXPLAIN을 시작한다. psql 종료 뒤에는 token unregister ACK까지 받아 PID 재사용이 과거 측정 identity로 제외되지 않게 하며, 최종 label/count/lifecycle도 예정 workload와 정확히 비교한다. observer 자신의 PID와 등록된 measured backend의 exact PID/application/backend-start만 제외한다. 다른 database의 짧은 client activity는 `other-database-activity-*` reason으로, 같은 database의 외부 activity와 visibility가 제한된 session은 일반 transient reason으로 누적되며 모두 non-adoptable이다. SIGTERM은 승인 interval의 남은 delay를 즉시 깨운 뒤 final sample과 0600 window report를 보존하므로 큰 interval을 임의 상한으로 바꾸지 않는다. observer 전용 graceful bound는 진행 중 sample timeout 2회와 1초 process/report 여유를 합친 5초이며, 일반 measured psql의 기존 reap bound는 바꾸지 않는다. 미등록 active session이 관측되거나 observer 자체가 종료되면 main runner가 진행 중 psql을 즉시 종료해 partial invalid report로 중단한다. 실행 중 planner settings, postmaster, analyze/autoanalyze, vacuum/autovacuum, visibility, schema/index fingerprint, DB/Compose container identity가 바뀌거나 외부 activity가 관측되면 invalid/pending report를 남기고 non-zero 종료한다.

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

### #193 finding-zero production branch handoff

`perf/193-admin-charge-query-optimization-v2`의 finding-zero revision `01faf929c24746e3a300e40d11171fc7d473954d`에는 `AdminChargeAggregationQueryRepository`가 추가됐다. 이 repository는 한 API 호출의 read-only `REPEATABLE_READ` transaction에서 동일한 동적 criteria로 다음 세 SQL을 순서대로 실행한다.

1. 전체 상태별 금액 `summary`
2. 회원별 집계·정렬·`limit/offset`의 `member-page`
3. `count(distinct charge.user_id)`의 `count-distinct`

[`i193-production-candidate-handoff.json`](i193-production-candidate-handoff.json)은 source revision과 4개 production source SHA-256을 고정하고, [`i193-production-candidate-sql.mjs`](i193-production-candidate-sql.mjs)는 repository의 `BASE_FROM`, 동적 predicate 순서, 상태별 합계, 정렬 mapping, empty account scope의 `1 = 0`을 source-derived 형태로 보존한다. 다만 이 코드는 현재 `origin/develop`에 아직 통합되지 않았고 runtime SQL capture도 없으므로 분류는 `exact-production-branch-candidate-not-measured`다. 기존 24개 실행 inventory에 자동 추가되지 않으며 `automaticAdoption=false`, `productionBeforeEligible=false`를 유지한다. 통합 뒤 실제 runtime SQL과 source/hash가 다시 일치한다는 증거 없이 production-before 수치로 사용할 수 없다.

16-case 비교 matrix는 각 API case마다 세 statement를 따로 비교한다. 모든 case는 `size=10`, `sort=createdAt,desc`, `includeArchived=false`이고 page 1만 offset 10이다.

| case | account predicate | 추가 predicate | 비교 SQL |
| --- | --- | --- | --- |
| `my_initial_penalty_unpaid` | manager-owned active PENALTY set | category + status | summary/page/count |
| `my_payment_category` | requester-owned active COFFEE set | category + status | summary/page/count |
| `my_status` | requester-owned active COFFEE set | category + status | summary/page/count |
| `my_user_id` | requester-owned active COFFEE set | category + user | summary/page/count |
| `my_keyword` | requester-owned active COFFEE set | category + leading-wildcard keyword | summary/page/count |
| `my_payment_account_unknown_param_ignored` | requester-owned active COFFEE set | controller가 account param을 bind하지 않음 | summary/page/count |
| `my_pagination_page_0` | requester-owned active COFFEE set | page 0 | summary/page/count |
| `my_pagination_page_1` | requester-owned active COFFEE set | page 1 / offset 10 | summary/page/count |
| `admin_initial_penalty_unpaid` | predicate 없음 | category + status | summary/page/count |
| `admin_payment_category` | predicate 없음 | category + status | summary/page/count |
| `admin_status` | predicate 없음 | category + status | summary/page/count |
| `admin_user_id` | predicate 없음 | category + user | summary/page/count |
| `admin_keyword` | predicate 없음 | category + leading-wildcard keyword | summary/page/count |
| `admin_payment_account` | same-campus singleton | category + account | summary/page/count |
| `admin_pagination_page_0` | same-campus singleton | page 0 | summary/page/count |
| `admin_pagination_page_1` | same-campus singleton | page 1 / offset 10 | summary/page/count |

후보는 DDL이 아니라 실제 before plan을 얻은 뒤 사용자에게 제안할 비교축이다.

| 후보 | 기존 index와 중복 | summary / page / count 적용성 | 쓰기·partial 판단 |
| --- | --- | --- | --- |
| `charge_items(campus_id, payment_category, status, user_id)` | `uk_charge_items_source(campus_id,user_id,payment_category,source_type,source_id)`와 `campus_id` prefix 및 column 일부 중복 | account 없는 campus/category/status 입력을 좁힐 가능성은 있으나 aggregate, group/order, distinct 비용은 각각 plan으로 확인 | status 변경마다 index rewrite; non-MEAL/status partial은 16 case coverage 증명 전 미승인 |
| `charge_items(campus_id, payment_account_id, payment_category, status, user_id)` | 기존 unique와 `campus_id` prefix·column 일부 중복, 두 번째 column만 account로 다름 | my-accounts/singleton account에는 후보, account 없는 admin은 campus prefix만 사용 가능; 세 statement를 분리 확인 | 가장 넓은 후보이고 insert/status update 비용이 큼; runtime account ID partial은 불가, non-MEAL partial도 미승인 |
| `campus_members(campus_id, status, user_id)` | V1 `uk_campus_members_campus_user(campus_id,user_id)`와 강하게 중복 | 기존 unique가 campus+user join probe 뒤 ACTIVE를 filter할 수 있으므로 세 statement 모두 추가 이득을 가정하지 않음 | `conditional-plan-proof-required`; actual plan에서 status 추가 이득과 membership write 비용을 함께 증명할 때만 유지 |

`lower(name/email) LIKE '%keyword%'`는 leading wildcard다. extension 또는 별도 expression/index 설계에 대한 사용자 승인이 없으므로 이번 후보에 포함하지 않는다.

기존 candidate 표는 2026-07-16 scenario-only 단계의 검토 기대치였다. 이후 사용자가 성능 테스트와 backend 최적화를 별도 승인 없이 계속 진행하도록 승인했고, 이력서용 측정은 핵심 병목·동일 조건·Before/After 3회 중심의 간결한 계약으로 축소했다.

### 2026-07-17 실제 plan 결정

[`actual-plan-evidence-20260717.json`](actual-plan-evidence-20260717.json)은 PostgreSQL 17.10, `charge_items` 749,017행과 `campus_members` 50,603행인 current-develop fixture에서 수집한 `EXPLAIN (ANALYZE, BUFFERS)` 결과다. 후보 index는 transaction 안에서만 생성하고 모든 비교 직후 rollback했으며 shared DB는 Flyway V11/index 0 상태를 유지했다. cache reset은 수행하지 않았고 아래 수치는 API latency가 아니라 SQL plan 비교다.

- #192 1,000개 source ID bulk 조회: 기존 unique index `14.155ms / 898 buffers`, `(campus_id,payment_category,source_type,source_id)` 후보 `0.738ms / 46 buffers`.
- #193 exact 5-status 집계: summary `47.668 -> 16.440ms`, member-page `70.738 -> 21.592ms`, count-distinct `62.711 -> 18.165ms`.
- #195 100-user membership projection warm 비교: 기존 `6.199/4.625ms`, `(user_id,id)` 후보 `1.115/0.921ms`.
- 후보 크기: charge source 18MB, charge aggregation 12MB, campus member 1,568kB. 기존 `uk_charge_items_source`는 59MB다.

따라서 V12는 위 세 index만 추가한다. #197 daily lookup은 기존 weekly/date unique로 `0.118ms`, #199 17,000 response grouped count는 기존 poll/user unique로 `29.873ms`였고, #196 group-member 및 #198 1,000-token workload도 기존 index와 cardinality가 충분해 새 index를 추가하지 않는다. leading-wildcard keyword, account-scoped wide index, partial/include index도 근거 부족으로 제외한다.

V12 migration은 별도 임시 PostgreSQL DB에서 V1부터 clean 적용해 세 index의 실제 definition을 확인한다. 공유 DB에는 integration 배포 전까지 V12를 적용하지 않는다.

아래 후보들은 이번 결정에 포함하지 않는다.

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

각 원 이슈의 API-level 정확성 결과는 cross-issue report가 담당하고, #194는 SQL별 `correctnessChecks`와 실행계획을 연결한다. 최신 inventory는 #201 archive cutoff, #200 COFFEE ownership, #206 `createdAt DESC, id DESC` stable ordering과 관련 query variant를 source identity에 묶는다. #202 RLS는 runtime JDBC role이 동일 조회를 계속 수행할 수 있는 schema/security preflight로만 확인한다. #194 runner만으로 API 응답, 권한, 트랜잭션 rollback, 생성 row 수를 새로 증명했다고 보고하지 않는다.

k6 v2 `Counter`/`Rate`/`Trend` 수학, exact HTTP count/failure=0/ordered latency, container CPU/RAM bytes-percent와 resource sampling cadence/window는 이 PostgreSQL diagnostic runner가 생성하는 evidence가 아니다. 이 시나리오는 k6·HTTP·Redis·Docker resource sampler를 실행하거나 load/API 성과를 주장하지 않으므로 해당 공통 체크는 명시적으로 not-applicable이다.

## 정적 검증

DB/Docker 없이 실행할 수 있는 검증만 제공한다.

```bash
node --test \
  performance/k6/postgresql-explain-index-baseline/test/scenario-contract.test.mjs \
  performance/k6/postgresql-explain-index-baseline/test/pm-second-review-contract.test.mjs \
  performance/k6/postgresql-explain-index-baseline/test/pm-third-review-contract.test.mjs \
  performance/k6/postgresql-explain-index-baseline/test/current-develop-contract.test.mjs \
  performance/k6/postgresql-explain-index-baseline/test/i193-production-candidate-handoff.test.mjs
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
node --check performance/k6/postgresql-explain-index-baseline/i193-production-candidate-sql.mjs
bash -n performance/k6/postgresql-explain-index-baseline/run-baseline.sh
```
