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

### poll-admin

- 위 common list/detail/results/comments를 service ADMIN으로 조회해 7일 visibility를 분리 측정
- `GET /api/v1/admin/campuses/{campusId}/polls/{pollId}/missing-members`
- `GET /api/v1/admin/campuses/{campusId}/poll-templates`
- `GET /api/v1/admin/campuses/{campusId}/poll-templates/{templateId}`
- target campus path에 isolation campus Poll ID를 사용한 404 격리 확인

## fixture 계약

- stable `datasetId`: `issue-196-prayer-poll-list-v1`
- required immutable `fixtureRunId`: 실행마다 8~32자 새 값
- primary campus: 캠퍼스 생성자 1명 + 생성 일반 멤버 999명 = ACTIVE 1,000명
- isolation campus: 캠퍼스 생성자 1명 + 생성 일반 멤버 49명 = ACTIVE 50명
- prayer: 40조 × 25명, 제출 800명, 미제출 200명
- measured Poll: 선택지 5개, 응답 800명, 미응답 200명
- comments: 200개
- templates: 40개 × option 8개
- visibility fixtures: OPEN, 종료 2일, 종료 5일, 종료 8일, future SCHEDULED

Fixture manifest에는 ID와 테스트 이메일만 기록한다. password, Access/Refresh Token, DB credential은 기록하지 않는다. 실패한 fixture는 삭제/수정해 재사용하지 않고 새 `fixtureRunId`로 다시 만든다.

종료 2/5/8일 Poll은 API로 현재 run의 Poll을 생성·마감한 뒤 별도 shaper가 manifest의 정확한 `id + campus_id + title`이 모두 일치하는 세 행만 보정한다. 기존 row와 다른 fixture run의 row는 수정·삭제하지 않는다.

## correctness 계약

- Prayer group/member와 Poll/template option의 안정 정렬
- Prayer target/submitted 개수, `myGroupId`, 관리자 전체 `editable`, 일반 멤버 본인 1건만 `editable`
- primary/isolation campus 데이터 혼입 없음
- member 3일, admin 7일 Poll visibility 및 OPEN/CLOSED/SCHEDULED 상태
- Poll detail `myResponse`, 결과 1,000/800/200, option별 response 합계
- non-anonymous respondent 800명 노출, anonymous respondent identity 0명 노출
- comments 200개 ID 오름차순, templates ID 오름차순/option sortOrder 오름차순
- missing-members 200명 membership 순서
- read 구간 DB write counter delta 0

## endpoint별 evidence 계약

- k6 custom Trend: p50/p95/p99/max
- custom Counter: 요청 수와 초당 throughput
- custom Rate: 실패율
- application/PostgreSQL container CPU/RAM sample
- `org.hibernate.SQL` query count와 `queriesPerRequest`
- normalized repeated SQL과 loop/N+1 신호
- `pg_stat_user_tables` table별 estimated row count와 scan/fetch/write counter delta
- 실제 app image와 Compose project/service/config-hash label

인증은 endpoint 측정 스냅샷 전에 수행하고 발급 token을 메모리 환경변수로만 k6에 전달한다. 따라서 login/BCrypt/JWT 쿼리를 endpoint query count에 포함하지 않는다. app scheduler를 끄고 global runner lock을 확보하지 못하면 측정을 거부한다.

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
2. seed → fixture-owned time shaping → endpoint 순차 k6를 실행한다.
3. endpoint별 raw/report 파일과 dataset/fixture/runtime 조건을 함께 기록한다.
4. baseline evidence를 PM에 보고한다.
5. PM 승인 전 production N+1 수정, schema/index 변경, 성과 수치 작성은 하지 않는다.
