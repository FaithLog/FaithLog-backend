# Issue #196 기도조·투표 목록 before 시나리오 준비 기록

## 상태

- 단계: `scenario-ready`
- 측정: `not-measured`
- baseline 수치: 없음
- 개선 수치: 없음
- production Java/API/권한/응답/오류/트랜잭션/Entity/DB/Flyway/의존성 변경: 없음
- Docker/DB/seed/k6 실행: 하지 않음

## 실제 API 기준 측정 모드

기도와 투표를 같은 부하 구간에 섞지 않는다. 상위 runner가 endpoint별 k6 프로세스를 하나씩 실행하고 explicit `all`에서 `prayer → poll-member → poll-admin → poll-duty` 순서를 보장한다.

### prayer

- 관리자 current season: `GET /api/v1/admin/campuses/{campusId}/prayer-seasons/current`
- 관리자 season groups: `GET /api/v1/admin/prayer-seasons/{seasonId}/groups`
- 관리자 assignable members: `GET /api/v1/admin/prayer-seasons/{seasonId}/members/assignable`
- 관리자 weekly board: `GET /api/v1/campuses/{campusId}/prayers/weeks/{weekStartDate}`
- 일반 멤버 weekly board: 같은 API를 일반 ACTIVE 멤버로 조회

### poll-member

- `GET /api/v1/campuses/{campusId}/polls`
- `GET /api/v1/campuses/{campusId}/polls/{pollId}`
- `GET /api/v1/campuses/{campusId}/polls/{pollId}/results`
- `GET /api/v1/campuses/{campusId}/polls/{pollId}/comments`
- target campus path에 isolation campus Poll ID를 사용한 404 격리 확인
- primary 전용 일반 멤버가 isolation campus path와 Poll ID를 직접 조회할 때 403 격리 확인

### poll-admin

- 위 common list/detail/results/comments를 service ADMIN으로 조회해 7일 visibility를 분리 측정
- `GET /api/v1/admin/campuses/{campusId}/polls/{pollId}/missing-members`
- `GET /api/v1/admin/campuses/{campusId}/poll-templates`
- `GET /api/v1/admin/campuses/{campusId}/poll-templates/{templateId}`
- target campus path에 isolation campus Poll ID를 사용한 404 격리 확인

### poll-duty

- generic Poll list/detail을 normal member, service admin, COFFEE creator, 다른 active COFFEE duty, active MEAL duty로 분리해 #200 `manageableByMe` matrix와 `createdBy` 비노출 확인
- `GET /api/v1/campuses/{campusId}/meal/polls?includeArchived=false&page=0&size=100&sort=id%2Cdesc`
- 같은 MEAL management list의 `includeArchived=true`로 90일 archive 명시 포함 확인
- normal member의 MEAL management list는 `403 MEAL_DUTY_REQUIRED`

## fixture 계약

- stable `datasetId`: `issue-196-prayer-poll-list-v2`
- required immutable `fixtureRunId`: 실행마다 lowercase 8~32자 새 값
- required immutable `executionRunId`: 측정 실행마다 lowercase 8~32자 새 값이며 기존 report 디렉터리 재사용·삭제·덮어쓰기 금지
- primary campus: 캠퍼스 생성자 1명 + 생성 일반 멤버 999명 = ACTIVE 1,000명
- isolation campus: 캠퍼스 생성자 1명 + 생성 일반 멤버 49명 = ACTIVE 50명
- prayer: 40조 × 25명, 제출 800명, 미제출 200명
- measured Poll: 선택지 5개, 응답 800명, 미응답 200명
- comments: 200개
- templates: 40개 × option 8개
- visibility fixtures: OPEN, 종료 2일, 종료 5일, 종료 8일, future SCHEDULED
- duty fixtures: active COFFEE creator, 다른 active COFFEE duty, active MEAL duty와 current COFFEE/current MEAL/91일 archived MEAL Poll

Fixture manifest에는 ID와 테스트 이메일만 기록한다. password, Access/Refresh Token, DB credential은 기록하지 않는다. 실패한 fixture는 삭제/수정해 재사용하지 않고 새 `fixtureRunId`로 다시 만든다.

별도 shaper는 현재 run의 여덟 Poll(5 CUSTOM + current COFFEE + current/archived MEAL)을 하나의 SQL statement에서 함께 보정한다. exact ID/campus와 manifest가 아닌 `fixtureRunId`에서 파생한 title을 모두 확인하고, 하나라도 불일치하면 여덟 UPDATE 전체를 rollback한다. 0600 shape-attempt receipt와 `shapedAt`으로 재실행을 거부하며 shaping 실패 시 새 run을 사용한다. 기존 row와 다른 fixture run의 row는 수정·삭제하지 않는다.

## correctness 계약

- Prayer group/member와 Poll/template option의 안정 정렬
- Prayer target/submitted 개수, `myGroupId`, 관리자 전체 `editable`, 일반 멤버 본인 1건만 `editable`
- primary/isolation campus 데이터 혼입 없음
- member 3일, admin 7일 Poll visibility 및 OPEN/CLOSED/SCHEDULED 상태
- generic Poll list는 current-develop API 그대로 pagination 없는 배열과 exact ID 내림차순을 검증하고, MEAL management list만 `PageResponse`/explicit `id,desc`/90일 archive를 검증
- #200 CUSTOM admin, COFFEE active-duty creator, MEAL active-duty ownership별 `manageableByMe`와 `createdBy` 비노출
- manifest와 응답의 exact `startsAt`/`endsAt`, runner 전체 preflight 및 각 endpoint 직전·직후의 다섯 time window freshness
- Poll detail `myResponse`, 결과 1,000/800/200, option별 response 합계
- non-anonymous respondent 800명 노출, anonymous respondent identity 0명 노출
- comments 200개 ID 오름차순, templates ID 오름차순/option sortOrder 오름차순
- missing-members 200명 membership 순서
- DB before/after의 exact 27-table/field schema, timestamp 증가, 8개 non-empty planner 설정과 database/address/port/postmaster identity 불변, Flyway 11, 27-table RLS enabled, FORCE RLS/policy 0, JDBC database-owner bypass 무영향, pgss available/unavailable state continuity, analyze/autoanalyze/vacuum/autovacuum count·null-or-valid timestamp 불변. PostgreSQL counter는 strict nonnegative decimal string으로 수집해 BigInt exact 비교하며 delta도 decimal string으로 기록한다. cumulative counter monotonic과 table별 `n_tup_ins/upd/del` delta 개별 exact 0을 요구한다.
- runtime/resource sampling interval과 maximum gap은 기본값 없는 runtime 승인 입력이다. 입력값에 따라 measured 시작·종료 boundary coverage, timestamp strict monotonic/window 포함, maximum gap, duration 기반 minimum sample count를 검증하지만 사용자 채택 정책 승인 전에는 clean evidence도 자동 채택하지 않는다.
- correctness failure 0건 고정 gate, warmup/k6/resource/activity sampler/time-window/log/after-DB-snapshot 실패 report의 `accepted=false`/`measurementStatus=rejected`, 필수 latency/throughput/table/resource/activity evidence 및 read-path write delta rejection reason, 모든 rejected report의 runner 비정상 종료

## endpoint별 evidence 계약

- endpoint exact k6 custom Trend: direct k6 v2와 `values` shape를 모두 지원하는 finite/nonnegative `p50 <= p95 <= p99 <= max`
- endpoint exact custom Counter: positive 요청 수와 positive 초당 throughput
- endpoint exact custom Rate: 실패율 exact 0
- application/PostgreSQL/Redis exact 3-container name/full-ID CPU/RAM sample. RAM은 strict Docker byte unit으로 used/limit를 파싱해 safe nonnegative bytes, `limit > 0`, `used <= limit`, reported percent `0..100`을 요구하고 canonical percent와 exact decimal byte 값을 기록한다.
- `org.hibernate.SQL` query count와 `queriesPerRequest`
- normalized repeated SQL과 loop/N+1 신호
- `pg_stat_user_tables` exact table set별 estimated row count와 monotonic scan/fetch 및 table/counter별 zero write delta
- measured window 전후 planner/analyze/vacuum maintenance 상태와 실행 중 `pg_stat_activity`/host port client sample; 현재 observer PID, attested app-container client address, measured k6 PID와 Docker proxy만 제외하고 같은 이름의 다른 k6/JDBC session을 포함한 외부 DB/HTTP activity 또는 maintenance/planner 변화 시 non-adoptable
- 실제 app/DB/Redis full container ID, immutable image ID, `StartedAt`, PostgreSQL database/address/port/postmaster/Flyway/RLS/JDBC owner/pgss state, Redis `run_id`/port, source revision, image tag, published target port와 Compose project/service/config-hash label. lock 전후와 warmup 전, measured 직전·직후, final report 전 exact continuity를 재검증

각 endpoint는 explicit `WARMUP_VUS/WARMUP_DURATION`의 별도 k6 process를 먼저 끝내고 성공한 경우에만 explicit `MEASURED_VUS/MEASURED_DURATION` process를 시작한다. measured 직전에 token을 다시 발급하고 JWT `exp`가 phase duration과 safety margin을 덮는지 raw token 저장·출력 없이 검증한다. DB/log/resource/activity evidence window는 warmup 뒤에만 연다. caller의 stale token env는 시작 시 제거하고 login/DB child에만 필요한 credential을 inline 전달하며 k6에는 새 token만 전달한다. Docker metadata/summarizer 등 다른 child에는 credential/token을 상속하지 않는다.

실제 app/DB/Redis Compose label 일치를 확인한 뒤 seed/shaper/runner 모두 `/tmp/faithlog-performance-{actualComposeProject}.lock`을 사용한다. lock 전 세 container와 DB/Redis process identity를 승인 snapshot으로 잡고 lock 직후 exact 재검증이 끝나기 전에는 login/API write/Poll UPDATE/k6를 시작하지 않는다. caller lock override는 없다. mode도 runtime 필수이며 `all`은 명시했을 때만 전체 27 endpoint로 확장된다. millisecond RFC3339 Docker log 경계로 login/BCrypt/JWT 쿼리를 endpoint query count에서 분리하고 app scheduler를 끈다.

sampling은 관찰된 외부 activity를 거부할 수 있지만 짧은 transient 요청의 절대 부재를 단독 증명하지 못한다. sampling cadence/max-gap과 exclusive-window 채택 방식은 사용자 미승인 상태다. 입력값은 default 없이 runtime에서 받고 positive 및 `maxGap >= interval`과 실제 coverage에 exact 결속하며 1초/2초 상수를 강제하지 않는다. 따라서 현재 summarizer는 clean evidence에도 `accepted=false`, `automaticAdoption=false`, `measurementStatus=conditional-not-adoptable`과 `adoption-policy-pending-user-approval`을 기록한다. Conditional endpoint는 다음 순차 endpoint로 진행하지만 requested scope 전체 수집 뒤 runner가 exit 2로 끝난다. Rejected/malformed evidence는 최초 endpoint에서 즉시 중단한다.

`BASE_URL`, app/DB/Redis container name, expected Compose service/image tag/immutable ID, source revision, Flyway version, Redis port, credential/workload 값은 기본값 없는 runtime 승인 입력이다. seed/shape/run은 하나라도 없으면 API/Docker/DB 작업 전에 실패하고 direct k6도 자신에게 필요한 target/service/image/workload/credential이 없으면 request 전에 실패한다. `BASE_URL`은 explicit numeric loopback `127.0.0.1` 또는 `[::1]`만 허용하고 `localhost`는 승인된 해석 규칙이 없으므로 거부한다. 공통 validator가 같은 address family의 exact/wildcard Docker binding 중 requested host port와 맞는 항목이 exact 1개인지 확인하며, actual target은 seed manifest의 Compose/runtime identity와 다시 exact 결속한다. Resource evidence는 metadata의 app/DB/Redis exact 3-container name/full-ID set만 허용하고 CPU가 finite/nonnegative가 아니거나 RAM used/limit unit·byte invariant·reported `0..100%`가 잘못됐거나 foreign container row가 있으면 rejected다.

최초 machine-readable rejection은 `primaryRejectionReason`으로 보존하고 report는 exclusive create해 기존 evidence를 덮어쓰지 않는다. clean evidence도 `automaticAdoption=false`, `conditional-not-adoptable`이다. #192~#199 test-code 감사는 병렬 가능하지만 shared stack 실제 load는 PM exclusive window에서 이슈별 순차 실행한다.

## 2026-07-16 read-only handoff 감사

아래 값은 2026-07-16 KST 확인 시점의 후보 runtime snapshot이다. 실행 직전 runner가 모두 다시 exact inspect하므로 하나라도 바뀌면 사용하지 않는다.

| 항목 | read-only 확인값 |
| --- | --- |
| Compose project | `faithlog-frontend-latest` |
| BASE_URL 후보 | `http://127.0.0.1:28080` (`0.0.0.0:28080`/`[::]:28080`, IPv4 validator PASS) |
| app | `faithlog-latest-app`, service `app`, image `faithlog-frontend-latest-app`, ID `sha256:8e0f8d85d697a7d34aabf3703ddb27b4f1af326dec4f7c35556986303b0b816c`, container ID `a7df78b330f457a7fd60a9531362d0f1f063ae7aa6cae5f2d996eb8cb51fe79d`, StartedAt `2026-07-16T04:23:10.082407837Z` |
| PostgreSQL | `faithlog-latest-postgres`, service `postgres`, image `postgres:17`, image ID `sha256:48d29282d2b43c402465c28f8572021b59aaf43574056faaad2fd7bb85ffdd4e`, container ID `81aa74ca1b491b45eb691b3d65de9e42eb47ef64a6bcb961d0b627b030139ae9` |
| Redis | `faithlog-latest-redis`, service `redis`, image `redis:7-alpine`, image ID `sha256:80dd823f4d2bf93dd5e418a0ae2817319a1ba279953e234082e54a5a18306223`, container ID `4109f6525948d12d1e5377fb6160c8955f6c3fcd7816e02786b2dd8031e23de9` |
| DB identity | database/user `faithlog`, TCP `127.0.0.1:5432`, postmaster `2026-07-15T08:32:56.137385Z`, Flyway 11, 27/27 RLS, FORCE RLS/policy 0, JDBC owner 27, pgss coherent unavailable |
| Redis process | run ID `77684c2eb9ea13438a15e81e190b3fec43e60c6c`, Redis 7.4.9, internal port 6379 |

PM이 승인한 operational provenance는 clean detached `/private/tmp/FaithLog-perf-206-deploy` HEAD `6796ed146244d8f3f5b5dd7048ebe16865084a97`과 그 뒤 생성된 app image(`2026-07-16T04:22:48.810414883Z`)의 결속이다. OCI revision label, repo digest, `git.properties`가 없어 image 단독 cryptographic source proof는 제공하지 못한다는 limitation을 유지한다. Runner의 source input과 full container/image/StartedAt/Compose gate는 완화하지 않는다.

현재 app container에는 `FAITHLOG_SCHEDULER_ENABLED=false`만 확인됐고 runner가 요구하는 `LOGGING_LEVEL_ORG_HIBERNATE_SQL=DEBUG`, `SPRING_JPA_PROPERTIES_HIBERNATE_FORMAT_SQL=false` env는 확인되지 않았다. 따라서 PM slot 전에 외부 runtime 준비가 필요하며 이 세션에서는 container lifecycle을 변경하지 않는다.

Fresh 후보는 dataset `issue-196-prayer-poll-list-v2`, fixture `i196-20260716-a`, execution `i196-exec-20260716-a`다. 해당 local report 경로는 없고 DB의 campus/user/Poll/Prayer season/Poll template namespace count가 모두 0임을 SELECT-only로 확인했다. 이 freshness는 확인 시점 snapshot이며 seed 직전에 다시 확인한다.

승인이 필요한 값과 제안값을 분리한다.

- 승인 필수: 위 runtime exact identity를 계속 사용할지, 세 app evidence env가 준비된 새 identity, admin/member/DB credentials, `PERF_WEEK_START_DATE`, warmup/measured VUS·duration, sampling interval/max-gap, explicit mode, actual measurement slot.
- 비적용 제안: `PERF_WEEK_START_DATE=2026-07-13`, `WARMUP_VUS=2`, `WARMUP_DURATION=30s`, `MEASURED_VUS=5`, `MEASURED_DURATION=2m`, `SAMPLING_INTERVAL_SECONDS=1`, `SAMPLING_MAX_GAP_SECONDS=3`, mode `all`. 이는 default나 승인값이 아니며 사용자 승인 전 실행하지 않는다.

승인 후 예상 순서와 side effect는 다음과 같다.

1. identity/credential/namespace read-only preflight, 약 2~5분, write 0.
2. seed, 약 30~90분 추정. 1,048명 signup/login/join과 800명 Poll 응답 등 5,700건을 넘는 HTTP operation으로 fixture-owned row와 Redis session을 생성한다. 기존 row 삭제·수정은 하지 않는다. 최초 실패 시 partial fixture를 보존하고 새 fixture ID로만 재시도한다.
3. shape, 약 1~3분. exact current-run Poll 8개만 한 transaction으로 UPDATE하고 attempt receipt/manifest를 기록한다. 실패 시 즉시 중단하고 같은 fixture를 재사용하지 않는다.
4. explicit `all`: `prayer -> poll-member -> poll-admin -> poll-duty`, 27 endpoint별 warmup 후 measured를 순차 실행한다. 위 제안값이면 pure phase time 67.5분이며 identity/log/resource overhead를 포함해 약 75~90분을 예상한다. 각 endpoint마다 warmup/measured 전 5 actor login으로 Redis session write가 발생하지만 measured DB window 밖이다. Target workload는 GET이며 measured DB write delta는 exact 0이어야 한다.
5. report 검토, 약 15~30분. Conditional report는 전부 수집하되 automatic adoption은 하지 않는다. Warmup, identity, k6, correctness, DB write, sampling, external activity, report schema 중 첫 실패에서 뒤 endpoint를 실행하지 않는다.

전체 예상은 승인 후 약 2~3.5시간이다. 이는 planning estimate이며 baseline 또는 성능 성과 수치가 아니다.

## 정적 코드에서 확인한 측정 후보

아래는 measurement 전 코드 구조 관찰이며 실제 baseline 결론이 아니다.

- Prayer season groups는 group 반복 안에서 active member 조회, member 반복 안에서 user 조회를 수행한다.
- Prayer assignable은 ACTIVE campus member 반복 안에서 user 조회를 수행한다.
- Prayer weekly board는 group/member/user bulk 조립 경로를 사용하므로 동일 fixture에서 대조 대상으로 측정한다.
- Poll comments는 comment 반복 안에서 user 조회를 수행한다.
- Poll template list는 template 반복 안에서 option 조회를 수행한다.
- Poll missing-members는 missing member 반복 안에서 user 조회를 수행한다.
- Poll list/detail/results는 현재 bulk query/Map 조립 경로를 포함하므로 응답 correctness와 query count를 별도 endpoint에서 고정한다.

## 후속 게이트

1. PM 승인 측정 세션에서 사용자가 지정한 exact app image와 실제 Compose label을 확인한다.
2. 새 `fixtureRunId`로 seed → fixture-owned time shaping → 새 `executionRunId`와 explicit mode의 warmup/측정 endpoint 순차 k6를 실행한다.
3. endpoint별 raw/report 파일과 dataset/fixture/runtime 조건을 함께 기록한다.
4. baseline evidence를 PM에 보고한다.
5. PM 승인 전 production N+1 수정, schema/index 변경, 성과 수치 작성은 하지 않는다.

## TDD와 정적 검증

- 최초 scenario 계약: production 변경 전 `7 tests / 7 failures` RED 후 1차 `8 tests / 0 failures` GREEN
- PM finding 재현: test-only commit에서 `10 tests / 3 pass / 7 fail` RED
- PM 2차 finding 재현: test-only commit에서 `12 tests / 9 pass / 3 fail` RED
- PM 3차 finding 재현: test-only commit에서 `13 tests / 10 pass / 3 fail` RED
- PM 4차 finding 재현: test-only commit에서 `14 tests / 11 pass / 3 fail` RED
- PM 5차 finding 재현: test-only commit에서 `16 tests / 11 pass / 5 fail` RED
- 최신 develop drift RED: `962e0e3`에서 `18 tests / 16 pass / 2 fail`, 공통 감사 RED: `6ecd59b`에서 source/DB/Redis/pgss/resource 신규 계약 실패를 test-only로 고정
- 최종 계약: pending automatic adoption, seed/shape/run/direct k6 runtime-required target, current-develop pagination/archive/#200/#202 ordering, numeric loopback 결속, lock 전후 app/DB/Redis와 PostgreSQL/Redis process continuity, pgss 두 정상 state와 drift, k6 v2 direct/values 수학, strict memory/full ID/cadence, decimal-string/BigInt, namespace/rejection 보존, conditional 전체 순차 수집과 first rejected/operational-failure stop fake/static evidence를 포함한 `23 tests / 0 failures` GREEN
- report artifact는 기본 ignored 경로 또는 optional `PERF_REPORT_ROOT` 아래에 항상 `{fixtureRunId}/{executionRunId}`를 붙인다. 실행형 fake 계약은 temp base를 사용해 repository `build/reports` 권한이나 잔존 artifact에 의존하지 않는다.
- Node/Bash syntax와 `git diff --check`를 수행한다. 이 검증은 실제 seed/k6/Docker/DB를 실행하지 않는다.

## 2026-07-17 h05와 기도조 목록 최적화

- `h05`는 Prayer 5개와 Poll member 3개 경로의 HTTP/SQL 작업까지 완료했으며 모두 HTTP/custom failure `0`이었다.
- `prayer_groups` before는 2,035 requests, p50 250.586 ms, p95 536.956 ms, p99 925.874 ms, max 2,798.857 ms, throughput 16.927 requests/s였다. 수집 SQL은 1,193,245개, 요청당 약 586개였고 멤버별 `users.id` 단건 조회가 지배적이었다.
- `poll_member_results`는 26,798 requests, p95 29.907 ms, throughput 226.976 requests/s, 요청당 SQL 10개, failure `0`으로 완료됐다. 이후 host wall clock이 runtime/세 resource stream에서 동시에 약 1초 역행해 evidence classification이 거부됐다. 서버/정합성 실패가 아니므로 evidence는 보존하고 전체 27 endpoint를 재실행하지 않는다.
- focused integration RED는 25명·2개 조 목록에 32 prepared statements를 재현했다. Production은 모든 조의 활성 멤버를 1회, 모든 사용자 profile을 1회 조회하도록 변경했고 최대 7 statements 계약을 통과했다.
- API, DTO, 권한, ErrorCode, transaction, 조 정렬, 멤버 ID 정렬, Flyway, index, dependency, frontend 변경은 없다. Prayer service/structure/REST Docs 회귀와 전체 `./gradlew test build asciidoctor` 556 tests(0 failures/errors, 3 skipped), issue-local Node 53/53이 GREEN이다.
- 사용자 결정에 따라 이후 이력서 측정은 병목 endpoint 1~3개만 같은 fixture/workload로 before 3회와 after 3회 수행한다. failure `0`과 correctness를 전제로 p50/p95/p99, throughput, SQL count를 비교하며 after 전에는 개선률을 주장하지 않는다.
