# Issue #196 기도조·투표 목록 before 시나리오 준비 기록

## 상태

- 단계: `scenario-ready`
- 측정: `not-measured`
- baseline 수치: 없음
- 개선 수치: 없음
- production Java/API/권한/응답/오류/트랜잭션/Entity/DB/Flyway/의존성 변경: 없음
- Docker/DB/seed/k6 실행: 하지 않음

## 실제 API 기준 측정 모드

기도와 투표를 같은 부하 구간에 섞지 않는다. 상위 runner가 endpoint별 k6 프로세스를 하나씩 실행하고 `prayer → poll-member → poll-admin` 순서를 보장한다.

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

## fixture 계약

- stable `datasetId`: `issue-196-prayer-poll-list-v1`
- required immutable `fixtureRunId`: 실행마다 lowercase 8~32자 새 값
- required immutable `executionRunId`: 측정 실행마다 lowercase 8~32자 새 값이며 기존 report 디렉터리 재사용·삭제·덮어쓰기 금지
- primary campus: 캠퍼스 생성자 1명 + 생성 일반 멤버 999명 = ACTIVE 1,000명
- isolation campus: 캠퍼스 생성자 1명 + 생성 일반 멤버 49명 = ACTIVE 50명
- prayer: 40조 × 25명, 제출 800명, 미제출 200명
- measured Poll: 선택지 5개, 응답 800명, 미응답 200명
- comments: 200개
- templates: 40개 × option 8개
- visibility fixtures: OPEN, 종료 2일, 종료 5일, 종료 8일, future SCHEDULED

Fixture manifest에는 ID와 테스트 이메일만 기록한다. password, Access/Refresh Token, DB credential은 기록하지 않는다. 실패한 fixture는 삭제/수정해 재사용하지 않고 새 `fixtureRunId`로 다시 만든다.

별도 shaper는 현재 run의 다섯 Poll을 하나의 SQL statement에서 함께 보정한다. exact ID/campus와 manifest가 아닌 `fixtureRunId`에서 파생한 title을 모두 확인하고, 하나라도 불일치하면 다섯 UPDATE 전체를 rollback한다. 0600 shape-attempt receipt와 `shapedAt`으로 재실행을 거부하며 shaping 실패 시 새 run을 사용한다. 기존 row와 다른 fixture run의 row는 수정·삭제하지 않는다.

## correctness 계약

- Prayer group/member와 Poll/template option의 안정 정렬
- Prayer target/submitted 개수, `myGroupId`, 관리자 전체 `editable`, 일반 멤버 본인 1건만 `editable`
- primary/isolation campus 데이터 혼입 없음
- member 3일, admin 7일 Poll visibility 및 OPEN/CLOSED/SCHEDULED 상태
- manifest와 응답의 exact `startsAt`/`endsAt`, runner 전체 preflight 및 각 endpoint 직전·직후의 다섯 time window freshness
- Poll detail `myResponse`, 결과 1,000/800/200, option별 response 합계
- non-anonymous respondent 800명 노출, anonymous respondent identity 0명 노출
- comments 200개 ID 오름차순, templates ID 오름차순/option sortOrder 오름차순
- missing-members 200명 membership 순서
- DB before/after의 exact required table set, timestamp 증가, planner 설정과 analyze/autoanalyze 상태 불변, 각 cumulative counter의 finite/nonnegative/monotonic 및 table별 `n_tup_ins/upd/del` delta 개별 exact 0
- correctness failure 0건 고정 gate, warmup/k6/resource/activity sampler/time-window/log/after-DB-snapshot 실패 report의 `accepted=false`/`measurementStatus=rejected`, 필수 latency/throughput/table/resource/activity evidence 및 read-path write delta rejection reason, 모든 rejected report의 runner 비정상 종료

## endpoint별 evidence 계약

- endpoint exact k6 custom Trend: direct k6 v2와 `values` shape를 모두 지원하는 finite/nonnegative `p50 <= p95 <= p99 <= max`
- endpoint exact custom Counter: positive 요청 수와 positive 초당 throughput
- endpoint exact custom Rate: 실패율 exact 0
- application/PostgreSQL container CPU/RAM sample
- `org.hibernate.SQL` query count와 `queriesPerRequest`
- normalized repeated SQL과 loop/N+1 신호
- `pg_stat_user_tables` exact table set별 estimated row count와 monotonic scan/fetch 및 table/counter별 zero write delta
- measured window 전후 planner/analyze 상태와 실행 중 `pg_stat_activity`/host port client sample; 현재 observer PID, attested app-container client address, measured k6 PID와 Docker proxy만 제외하고 같은 이름의 다른 k6/JDBC session을 포함한 외부 DB/HTTP activity 또는 autoanalyze/planner 변화 시 non-adoptable
- 실제 app image tag/immutable image ID, published target port와 Compose project/service/config-hash label

각 endpoint는 explicit `WARMUP_VUS/WARMUP_DURATION`의 별도 k6 process를 먼저 끝내고 성공한 경우에만 explicit `MEASURED_VUS/MEASURED_DURATION` process를 시작한다. measured 직전에 token을 다시 발급하고 JWT `exp`가 phase duration과 safety margin을 덮는지 raw token 저장·출력 없이 검증한다. DB/log/resource/activity evidence window는 warmup 뒤에만 연다. caller의 stale token env는 시작 시 제거하고 login/DB child에만 필요한 credential을 inline 전달하며 k6에는 새 token만 전달한다. Docker metadata/summarizer 등 다른 child에는 credential/token을 상속하지 않는다.

실제 app/DB Compose label 일치를 확인한 뒤 seed/shaper/runner 모두 `/tmp/faithlog-performance-{actualComposeProject}.lock`을 사용한다. caller lock override는 없다. mode도 runtime 필수이며 `all`은 명시했을 때만 전체 19 endpoint로 확장된다. millisecond RFC3339 Docker log 경계로 login/BCrypt/JWT 쿼리를 endpoint query count에서 분리하고 app scheduler를 끈다.

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

1. PM 승인 측정 세션에서 `faithlog-latest`와 실제 Compose label을 확인한다.
2. 새 `fixtureRunId`로 seed → fixture-owned time shaping → 새 `executionRunId`와 explicit mode의 warmup/측정 endpoint 순차 k6를 실행한다.
3. endpoint별 raw/report 파일과 dataset/fixture/runtime 조건을 함께 기록한다.
4. baseline evidence를 PM에 보고한다.
5. PM 승인 전 production N+1 수정, schema/index 변경, 성과 수치 작성은 하지 않는다.

## TDD와 정적 검증

- 최초 scenario 계약: production 변경 전 `7 tests / 7 failures` RED 후 1차 `8 tests / 0 failures` GREEN
- PM finding 재현: test-only commit에서 `10 tests / 3 pass / 7 fail` RED
- 최종 계약: lock 선점 및 warmup 실패 부작용 0건, stale token/child credential scope, 다른 k6 PID, reversed percentile, existing report 보존 fake evidence를 포함한 `12 tests / 0 failures` GREEN
- Node/Bash syntax와 `git diff --check`를 수행한다. 이 검증은 실제 seed/k6/Docker/DB를 실행하지 않는다.
