# FaithLog Resume Metrics Log

FaithLog를 운영 가능한 프로젝트로 만들면서 이력서에 사용할 수 있는 정량 지표, 테스트 결과, 트러블슈팅 내역을 누적 기록한다.

## 기록 원칙

- 가능한 모든 개선은 수치로 남긴다.
- 테스트가 필요하다고 판단되면 테스트 항목, 이유, 기대 지표를 먼저 적는다.
- 장애, 버그, 성능 저하, 설정 문제는 원인, 해결, 재발 방지, 전후 수치를 함께 기록한다.
- 이력서에 쓸 수 있는 문장 후보는 별도로 남긴다.

## 2026-07-16 - Issue #206 청구 페이징 동률 정렬 안정화

- 발견: #193 current-develop before preflight에서 동일 `created_at` 청구 쌍의 ID 순서가 API 응답마다 asc/desc로 혼재해 offset paging의 중복·누락 가능성과 측정 correctness 실패를 확인했다. #193 D는 1,000 ACTIVE 멤버/35,000 청구 fixture까지만 생성되고 k6 warmup/measured 0, summary 없음으로 거부·보존됐다.
- TDD RED: `BillingPageRequests.chargeItems()`의 모든 허용 primary sort에 동일 방향 `id` secondary order가 있어야 한다는 단위 계약과, 동일 timestamp 두 행을 `size=1`로 두 공개 API에서 조회하는 통합 계약을 추가했다. 기존 구현은 신규 3 tests 중 adminMembers 불변 1개만 통과하고 `2 failures`였다. 실제 응답도 기대한 높은 ID 대신 낮은 ID를 첫 페이지에 반환했다.
- 최소 GREEN: validated primary sort 뒤 같은 방향 `id` sort를 추가했다. 내 청구 목록과 관리자 회원별 상세만 적용하고 관리자 회원 집계, 공통 sort validator, API/DTO/ErrorCode/Flyway/dependency는 변경하지 않았다.
- 최종 검증: `./gradlew test`는 88 suites / 555 tests / 0 failures / 0 errors / 3 skipped, `./gradlew build`와 `./gradlew asciidoctor`는 모두 성공했다. REST Docs 170 snippet groups와 최신 `build/docs/asciidoc/index.html` 생성을 확인했다.
- 프론트 영향: path/query/response shape와 sort parameter 형식이 동일하므로 production API client/type/UI 수정은 필요 없다. 별도 frontend integration mock의 `getMockAdminMemberChargeState`, `getMockMemberChargeList`는 primary 방향과 무관하게 ID ASC tie-break를 사용해 DESC에서 backend와 불일치하므로 mock과 관련 테스트의 최소 수정은 필요하다. 이 backend 작업에서는 frontend 파일을 편집하지 않았다.

## 핵심 지표 후보

| 영역 | 지표 | 측정 방법 | 최신값 | 목표 |
| --- | --- | --- | --- | --- |
| 품질 | 테스트 통과율 | `./gradlew test` | 100% of executed tests (2026-07-16 #206, 555 tests / 0 failures / 0 errors / 3 skipped) | 100% |
| 품질 | Line coverage | `./gradlew test jacocoTestReport` | 94.41% (7,223 / 7,651, 2026-07-14 integration) | 사용자 승인 전 threshold 없음 |
| 품질 | Branch coverage | `./gradlew test jacocoTestReport` | 75.77% (1,113 / 1,469, 2026-07-14 integration) | 사용자 승인 전 threshold 없음 |
| 품질 | Class coverage | `./gradlew test jacocoTestReport` | 97.70% (510 / 522, 2026-07-14 integration) | 사용자 승인 전 threshold 없음 |
| 품질 | Method coverage | `./gradlew test jacocoTestReport` | 89.79% (1,935 / 2,155, 2026-07-14 integration) | 사용자 승인 전 threshold 없음 |
| 품질 | 테스트 코드 파일 수 | `find src/test/java -name '*.java'` | 91 test files (2026-07-15 #200) | 증가 추적 |
| 품질 | 인증/문서 스니펫 묶음 수 | `find build/generated-snippets -mindepth 1 -maxdepth 1 -type d` | 170 snippet groups (2026-07-15 #200) | 증가 추적 |
| 안정성 | 빌드 성공 여부 | `./gradlew build -x test` | 성공 (2026-07-15 #202, 27초) | 성공 |
| API | 응답 시간 | 로컬 Docker Compose + Docker k6 | p50 8.47ms / p95 44.60ms / p99 89.37ms / avg 16.93ms, 295.92 req/s, failure 0.00% (2026-07-07 after #134 prayer/poll read optimization, `PERF_1000_20260707_A`) | local Docker VUS 30, 5m, failure < 1%, p95 중심 |
| 운영 API | Cloud Run steady-state read baseline | Cloud Run + k6 | p50 124.13ms / p95 257.51ms / p99 401.71ms / avg 144.29ms, 130.64 req/s, failure 0.00% (2026-06-24, VUS 30/5m, `PERF_20260624_CLOUDRUN_A`, 사용자 Cloud Run 설정 변경 후; 실제 설정값은 gcloud 부재로 확인 불가) | Cloud Run read-only, failure < 1%, p95 중심 |
| 운영 | 헬스체크 성공률 | Cloud Run `/api/v1/health` smoke | 100.00%, p95 224.61ms, failure 0.00% (2026-06-24, k6 VUS 1/30s, health-only) | 99%+ |
| 유지보수 | 주요 모듈 수 | 패키지/도메인 기준 | 10 top-level modules, 634 Java sources including tests (2026-07-13 #189) | 추적 |
| 데이터 | DB 마이그레이션 수 | `src/main/resources/db/migration` | 11 (Flyway V1-V11, 2026-07-15 #202) | 추적 |

## Daily Monitoring Notes

### 2026-07-15 - Issue #202 Supabase 공개 접근 차단

- Supabase Security Advisor의 26개 `rls_disabled_in_public` Critical과 `payment_accounts.account_number`/`user_fcm_tokens.token`의 `sensitive_columns_exposed` Critical을 확인했다.
- TDD RED로 V11 부재 1건과 Flyway history self-lock 1건을 재현한 뒤, 데이터 DML 없이 application public table RLS, Data API 역할의 schema/table/sequence/function 권한 회수, 미래 Flyway 객체 default privilege 회수를 구현했다.
- 임시 PostgreSQL DB에서 V1-V11 clean migration을 검증했고 기존 DB/volume은 변경하지 않은 채 임시 DB만 삭제했다.
- 운영 Supabase migration 후 public table RLS `26/26`, Data API table grants `0`, schema usage `false`, policy `0`, `SET ROLE anon` 조회 permission denied, active PostgreSQL JDBC sessions `5`를 확인했다.
- Security Advisor 재검사에서 두 Critical 유형은 0건이 되었고, 의도한 deny-all을 설명하는 `rls_enabled_no_policy` INFO만 남았다.
- 전체 `552 tests / 0 failures / 0 errors / 3 skipped`, `build -x test` 27초, AsciiDoc 27초, 최종 V1-V11 clean Flyway 28초와 `git diff --check`를 통과했다.

### 2026-07-14

- #200 PM 전체 diff 재리뷰 finding 8건 보강:
  - TDD: COFFEE 분류 우회·공용 템플릿, 관리자 COFFEE 계좌 필터 읽기, 삭제 계좌 알림·commit/release rollback dedupe·FCM token 일괄조회, duty-gated write·마감 잠금 순서를 4개 test-only 커밋으로 먼저 고정했다. 기존 구현에서 각 focused 범위가 `5 failures`, `1 failure`, `3 + 2 failures`, `4 failures`로 재현됐다.
  - 계약/DB: COFFEE operation 판정을 pollType/chargeGenerationType/paymentCategory의 단일 classifier로 통일하고 혼합 설정 우회를 차단했다. 사용자 승인대로 COFFEE 템플릿을 계좌 중립으로 저장하고, 실제 템플릿 기반 투표 생성에서 요청자의 active owned 계좌를 받는다. V10은 기존 COFFEE template의 `payment_account_id`만 null로 갱신하며 row 삭제나 schema 변경은 없다.
  - 동시성: 투표·템플릿·계좌·청구 상태·미납 알림 및 MEAL command가 담당 해제와 같은 duty row `PESSIMISTIC_WRITE`를 공유하고 `duty -> poll/account/charge` 순서를 사용한다. 수동/기한 마감 동시 테스트에서 잠금 전 managed Poll snapshot이 stale OPEN으로 재사용되는 추가 race를 발견해 projection으로 잠금 scope만 읽은 뒤 duty와 Poll을 순서대로 잠그도록 보강했다.
  - 알림/조회: soft-deleted owned account의 UNPAID도 알림 범위에 포함하고, transaction rollback 시 Redis daily reservation을 보상한다. dispatch는 request의 전체 recipient token을 1회 bulk 조회한다. 캠퍼스 관리자/전역 ADMIN의 COFFEE 계좌 필터 읽기는 전체 campus 범위를 유지하고 담당자 write/필터 ownership은 그대로다.
  - 자체 리뷰 추가 보완: MEAL 계좌 목록이 read-only transaction에서 duty write lock을 잡고, 계좌 비활성화 명령은 반대로 duty/account lock을 잡지 않는 누락을 RED 2건으로 재현했다. 목록은 non-locking duty 조회로 복원하고 비활성화는 `duty -> account` 잠금 순서로 통일했다. 계좌 중립 템플릿과 관리자 COFFEE 필터 범위에 어긋난 오래된 정책 문구도 실제 계약으로 정리했다.
  - 검증: 최종 전체 `483 tests / 0 failures / 0 errors / 3 skipped`, `./gradlew build`, `./gradlew asciidoctor`, `git diff --check`를 통과했다. test suite 83개, test source 88개, REST Docs snippet group 155개, Flyway V1-V10이다. Docker/PostgreSQL/Flyway 실제 적용은 사용자 승인 범위에 따라 실행하지 않았다.
  - 이력서 문장 후보: `다중 COFFEE 담당 권한·미납 알림 전체 diff 리뷰에서 인가·동시성·rollback·N+1 finding을 TDD로 재현하고 duty-first row lock, transaction completion 보상, recipient token bulk 조회, 계좌 중립 템플릿 V10으로 보강해 483개 전체 테스트와 REST Docs 155개 계약을 통과했다.`

- #200 PM 최종 재리뷰 finding 3건 보강:
  - TDD: 레거시 `CUSTOM + OPTION_PRICE + COFFEE` Poll 종료 우회, duty lock 대기 중 회원 PAID를 stale managed ChargeItem이 덮는 경합, 담당 해제의 캠퍼스 전체 UNPAID overfetch를 test-only 커밋 3개로 재현했다. 신규 focused 4 tests는 기존 구현에서 각각 assertion failure 또는 금지된 repository 호출로 RED였다.
  - 최소 수정: Poll close는 세 분류 필드로 COFFEE 권한을 먼저 잠그고 잠긴 Poll의 구성을 다시 검증한다. 청구 상태 변경은 잠금 전 scalar projection만 읽고 `duty -> charge FOR UPDATE` 뒤 최신 campus/category/account ownership/status로 전이를 적용한다. 담당 해제는 owned account ID가 비면 청구 query를 생략하고, 아니면 해당 ID `IN` 범위로만 UNPAID를 조회한다.
  - 자체 리뷰 추가 보완: `pollType=COFFEE`지만 나머지 필드가 불일치한 레거시 Poll을 due scheduler가 CLOSED로 만들고 정산을 생략하는 경계를 신규 1 test RED로 재현했다. 공통 classifier의 consistency 판정을 재사용해 불일치 행은 OPEN으로 유지하고 정상 due 마감은 보존했다.
  - 프론트 계약 자체 리뷰: 일반 Poll 목록/상세가 전체 캠퍼스 투표를 반환하지만 현재 요청자의 관리 가능 필드가 없어 다른 담당자 COFFEE 투표의 write 버튼을 프론트가 안전하게 제어할 수 없는 누락을 발견했다. 사용자 승인에 따라 생성자 사용자 ID는 공개하지 않고 서버 계산 `manageableByMe`만 목록·상세에 추가했다. COFFEE는 ACTIVE duty + 내부 creator, MEAL은 ACTIVE MEAL duty, 그 외 투표는 기존 관리자 command 권한과 동일하게 계산한다.
  - 검증: 신규 5 tests와 Poll/Campus/Billing/동시성/잠금 구조 focused suites가 GREEN이었다. 투표 필드 보강 focused 67 tests도 GREEN이었다. 최종 전체는 83 suites / 500 tests / 0 failures / 0 errors / 3 skipped, build와 asciidoctor 성공, REST Docs HTML 생성, diff check 성공이다. ErrorCode/Flyway/의존성은 이번 최종 수정에서 변경하지 않았고 Docker/PostgreSQL/Flyway 실제 적용은 실행하지 않았다.
  - 통합 게이트: PM finding 0 뒤 최신 API·권한·에러·프론트 UI 명세를 지정된 프론트 세션에 전달하고, 프론트 정적 검증 전 backend 병합/Docker QA를 보류한다. Docker/iOS QA의 디스크 정리는 사용자 승인 안전 범위와 전후 용량 기록을 따른다.

- #200 최종 API 결정 및 전체 diff 자체 리뷰 보강:
  - 사용자 결정 반영: Poll 공개 목록·상세에는 서버 계산 `manageableByMe`만 유지하고 `createdByUserId`는 controller/service DTO, REST Docs, 테스트에서 제거했다. 내부 creator는 COFFEE 소유 권한 계산에만 사용한다. MEAL은 ACTIVE MEAL duty, COFFEE는 ACTIVE 멤버십 + ACTIVE duty + creator, 그 외 Poll은 관리자 command 권한과 동일하다.
  - TDD/인가·동시성: 비활성 캠퍼스 멤버에게 남은 duty row가 Poll capability·due close·settlement를 통과하는 3건, COFFEE 계좌가 duty lock 대기 뒤 stale managed entity로 중복 삭제되는 1건, dispatch lock 전 PENDING snapshot 중복 발송 1건을 RED로 재현했다. ACTIVE membership fail-closed, immutable account scope + duty/account 재검증, dispatch lock 뒤 PENDING 재조회로 수정했다.
  - 알림 복구: Redis dedupe 값을 owner token으로 예약하고 Lua compare-delete로 rollback 보상 소유권을 검증한다. manual/dispatch lock은 owner token 기준 TTL을 갱신하며, recovery는 현재 dispatch lease를 얻지 못하면 PENDING을 FAILED로 덮지 않는다. 발송 worker는 request token bulk lookup을 유지하면서 log/token/retry 경계에서 lease를 갱신한다.
  - 조회/문서: Poll 목록은 실제 포함 유형에 필요한 COFFEE/MEAL duty만 조회하고, 상세는 visibility 계산 결과를 재사용한다. 알림 403 category별 code, 동시 요청 409, Redis 503과 COFFEE/MEAL 담당 해제 미납 409를 HTTP REST Docs snippet/index에 추가했다.
  - 최종 검증: `./gradlew test`는 83 suites / 518 tests / 0 failures / 0 errors / 3 skipped, `./gradlew build`와 `./gradlew asciidoctor` 성공, REST Docs snippet group 161개, HTML 생성, `git diff --check origin/develop...HEAD` 성공이다. Docker/PostgreSQL/Flyway 실적용, push, PR, merge는 실행하지 않았다.

- #200 담당 회원 삭제 우회 PM finding 보강:
  - TDD: ACTIVE COFFEE/MEAL 담당 회원 삭제, owned UNPAID 우회, INACTIVE 멤버의 담당 목록 노출, 재가입 시 stale capability 복원, 회원 삭제와 담당 지정/해제 경합을 test-only RED로 고정했다. 서비스·동시성 신규 6 failures와 REST Docs 신규 2 failures를 production 전에 확인했다.
  - 사용자 결정/API: ACTIVE 담당 배정이 하나라도 남으면 회원 삭제와 stale 담당 재가입을 `409 CAMPUS_MEMBER_ACTIVE_DUTY_CONFLICT`로 거부한다. 서버 자동 revoke/책임 이전은 하지 않고 프론트가 기존 개별 담당 해제를 먼저 수행한다. 소유 계좌 UNPAID가 있으면 기존 category별 409가 담당 해제를 계속 막는다.
  - 동시성/조회: immutable member lock scope 뒤 `campus -> duty -> member` 순서로 회원 삭제·재가입을 직렬화하고, 담당 목록 query는 ACTIVE assignment와 ACTIVE membership을 DB에서 함께 결속한다.
  - 최종 검증: campus focused와 전체 `83 suites / 526 tests / 0 failures / 0 errors / 3 skipped`, `./gradlew build`, `./gradlew asciidoctor`, REST Docs 163 groups/HTML, 전체 diff check가 성공했다. DB/Flyway/의존성 변경은 없고 Docker/PostgreSQL/Flyway 실적용, push, PR, merge는 수행하지 않았다.

- #200 stale 담당 복구·계정 탈퇴 우회·역할 변경 경합 최종 보강:
  - TDD: 기본 담당 목록에 숨겨진 stale assignment의 공개 API 복구 불가, ACTIVE 담당 사용자의 계정 탈퇴 성공, 역할 변경의 stale managed membership이 성공한 회원 삭제를 되살릴 수 있는 경합을 REST Docs/Controller/실제 2-transaction 테스트 4건으로 재현했다. 최종 보정된 역할 경합 테스트는 이전 production에서 삭제 future가 즉시 완료되어 expected timeout assertion이 실패하는 실제 RED를 확인했다.
  - 사용자 결정/API: 기존 `GET /api/v1/admin/campuses/{campusId}/duty-assignments`에 optional `staleOnly=true`를 추가했다. 기본값은 `false`이고 `true`이면 INACTIVE 멤버십에 남은 ACTIVE 담당만 기존 응답 구조로 반환한다. 관리자는 응답의 assignment ID로 기존 해제 API를 호출한 뒤 재가입을 완료한다. ACTIVE COFFEE/MEAL 담당이 하나라도 남은 `DELETE /api/v1/users/me`는 `409 CAMPUS_MEMBER_ACTIVE_DUTY_CONFLICT`로 차단하며 자동 해제하지 않는다.
  - 동시성: 계정 탈퇴는 immutable user/member scope를 읽은 뒤 campus ID 오름차순으로 `campus -> duty -> member -> user` 잠금을 획득해 담당 지정·해제와 직렬화한다. 캠퍼스 역할 변경도 `campus -> member` 잠금 뒤 최신 ACTIVE membership에만 적용해 삭제 후 stale flush가 상태를 ACTIVE로 복원하지 못하게 했다.
  - 최종 검증: `./gradlew test` 83 suites / 530 tests / 0 failures / 0 errors / 3 skipped, `./gradlew build` 11초, `./gradlew asciidoctor` 10초, REST Docs 165 groups/HTML 및 신규 2 snippet group 생성, 전체 diff check 성공이다. DB/Flyway/의존성 변경과 Docker/PostgreSQL/Flyway 실적용, push, PR, merge는 없다.

- #200 user-first 생명주기 잠금·stale 미납 복구 최종 보강:
  - TDD: 관리자 직접 stale 멤버 재활성화, 잘못된 `staleOnly`, 담당 목록 user N+1, MEAL 오류 문구, 전역 ADMIN stale COFFEE 미납 복구 5건을 test-only RED로 고정했다. 이어 실제 2-transaction 테스트에서 탈퇴 중 신규 가입 성공, 로그인 stale flush의 탈퇴 계정 부활, 캠퍼스 역할 강등 뒤 회원 삭제 성공 3건을 추가 RED로 재현했다.
  - 사용자 결정/API: lifecycle writer는 user ID 오름차순의 user row를 먼저 잠그고 `user -> campus -> duty -> member` 순서로 직렬화한다. `staleOnly=garbage`는 `400 GLOBAL_VALIDATION_FAILED`다. INACTIVE 멤버십에 ACTIVE COFFEE/MEAL 담당이 남은 계좌 소유자의 UNPAID만 서비스 전역 ADMIN이 기존 status PATCH로 명시 처리할 수 있으며 정상 담당자/일반 관리자 우회는 없다.
  - 성능/인가: 담당 목록은 assignment user ID를 한 번에 bulk 조회하고, 담당 지정·해제와 회원 삭제는 캠퍼스 잠금 이후 최신 requester 멤버십/역할을 재검증한다. 관리자 직접 회원 추가도 stale ACTIVE 담당이 있으면 `409 CAMPUS_MEMBER_ACTIVE_DUTY_CONFLICT`를 유지한다.
  - 최종 검증: `./gradlew test` 84 suites / 539 tests / 0 failures / 0 errors / 3 skipped, `./gradlew build` 12초, `./gradlew asciidoctor` 19초, REST Docs 167 groups/HTML, test source 89개, 전체 diff check 성공이다. DB/Flyway/의존성 변경과 Docker/PostgreSQL/Flyway 실적용, push, PR, merge는 없다.

- #200 stale 복구·전역 ADMIN 최신 권한 최종 보강:
  - TDD/리뷰: stale 복구의 최신 PAID 보존, duty/member 교착, 동시 마지막 ADMIN 강등, requester ADMIN 강등, USER→ADMIN 승격 뒤 stale 자기 강등을 실제 동시성 테스트로 고정했다. 최종 두 권한 경합은 focused 19 tests 중 2 failures로 RED를 확인한 뒤 GREEN으로 전환했다.
  - 잠금/인가: stale 복구는 requester user를 먼저 잠그고 `user -> campus -> duty -> member -> charge` 순서를 사용하며 locked charge의 UNPAID를 재검증한다. 전역 역할 변경은 최소 user ID row를 DB 공통 mutex로 사용한 뒤 requester/target 최신 역할과 마지막 ACTIVE ADMIN 수를 판단한다.
  - API/문서: `staleOnly` 오류 변환을 해당 controller로 제한해 unrelated type mismatch 계약 변경을 제거했다. COFFEE/MEAL 일반 상태 변경 권한을 실제 production과 일치시켰고 stale COFFEE 일반 관리자 403, ACTIVE MEAL service ADMIN 404, stale MEAL 성공 REST Docs를 추가했다.
  - 최종 검증: `./gradlew test` 86 suites / 548 tests / 0 failures / 0 errors / 3 skipped, `./gradlew build` 12초, `./gradlew asciidoctor` 14초, REST Docs 170 groups/HTML, test source 91개, 전체 diff check 성공이다. Docker/PostgreSQL/Flyway 실적용, push, PR, merge는 수행하지 않았다.

- #188/#189/#190 통합 검증:
  - 이력 보존: 최신 `origin/develop` `c7761da`에서 `integration/188-190-devotion-meal-billing`을 만들고 #188 `26bcc7f`, #189 `df94038`, #190 `bd9f604`를 각각 merge commit으로 병합했다. 문서 충돌은 세 기능의 승인 계약을 union으로 유지했고, Billing repository/service 충돌은 #188 weekly bulk query, #189 MEAL 격리, #190 charge/source-key `PESSIMISTIC_WRITE`와 Devotion reopen을 함께 보존했다.
  - 통합 리뷰 RED/GREEN: 재오픈된 weekly row를 잠그지 않아 동시 0원 재제출 두 건이 모두 제출 검사를 통과할 수 있는 race를 결정적 latch 테스트로 재현했다. RED `1 test / 1 failure` 뒤 취소 재오픈과 재제출 양쪽이 같은 weekly row `PESSIMISTIC_WRITE`를 사용하도록 보강했고, 신규 회귀와 #190 전체 재제출 통합 class가 GREEN이었다. production API/DTO/ErrorCode/Flyway는 바꾸지 않았다.
  - 비Docker 전체 검증: `449 tests / 0 failures / 0 errors / 3 skipped`, `./gradlew build`, `./gradlew asciidoctor`, `git diff --check`가 성공했다. JaCoCo는 line 94.41%, branch 75.77%, class 97.70%, method 89.79%이며 새 성과로 과장하지 않고 통합 시점 관찰값으로만 기록한다. test source 85개, 전체 Java source/test 646개, REST Docs snippet group 151개이고 `index.adoc`이 참조한 150개 group 누락은 0개다.
  - Docker/Flyway 복구 검증: 사용자가 승인한 재생성 가능 build/node_modules 정리 뒤 host Data volume 가용 공간 12GiB에서 Docker Desktop 29.6.1 engine을 복구했다. 격리 project `faithlog-qa-188-190-20260714`로 current source image `sha256:c65cff97bdccd1bc88e1e06d5427e81b7951fce57cc68c750f36c10bffa94a04`를 build하고 PostgreSQL 17, Redis 7, backend를 기동했다. 빈 `faithlog` DB는 V1→V8을 순서대로 적용했고 별도 `faithlog_upgrade` DB는 V1→V7 target 적용 후 V8만 upgrade해 두 history 모두 8/8 success였다. PostgreSQL/Redis는 healthy/PONG, backend `GET /api/v1/health`는 200/UP이다.
  - 실제 HTTP A/B/C/D: secret/token을 출력·저장하지 않은 격리 fixture `1783981848`, campus `1`에서 45개 검증 step이 실패 0으로 통과했다. #188은 ACTIVE 7명을 submitted 3/missing 4로 분리하고 PAID+UNPAID 10,400원 합계와 WAIVED/CANCELED 제외, non-Monday 400, 일반 멤버 403, XLSX MIME/filename/2 sheets/실제 archive open을 확인했다. #189는 MEAL duty 2명 role 불변, 본인 계좌 격리, OPEN/SINGLE 서버 시작 poll, 사용자 option/`optionIds`, close 후 charge 0, GROUP_TOTAL 10,000÷3=3,334원·actual 10,002원, 다른 duty 계좌 ID 비노출, 재정산 409, 8일 CLOSED poll의 30일 관리 목록 노출을 확인했다. 추가 fixture `rollback-1783982391`은 첫 MEAL charge write 뒤 두 번째 source-key 충돌을 실제 HTTP 409로 유발하고 settlement/group/첫 charge가 모두 0건으로 rollback되며 선행 충돌 row 1건만 유지됨을 확인했다. #190은 관리자 UNPAID→PAID/`paidAt`, terminal→PAID 409, DEVOTION_RECORD cancel의 submittedAt null/daily 7건 보존, 양수 재제출 동일 charge row UNPAID 재사용, 0원 재제출 CANCELED 유지와 source-key 중복 0을 확인했다.
  - 연결/동시성: cancel 직후 #188 JSON에서 대상이 missing으로 이동하고 합계에서 제외됐으며 실제 Excel 미제출 행은 3 cells였다. 양수 재제출 뒤 같은 charge ID/5,200원/UNPAID로 submitted에 복귀하고 합계가 복원됐으며 Excel 제출 행은 11 cells였다. 동일 charge에 실제 HTTP PAID/CANCELED를 동시에 보낸 fixture `concurrent-1783982008`은 200 1건/409 1건, 최종 CANCELED, daily 7건/source row 1건으로 직렬화됐다. MEAL 정산 후 PENALTY 합계는 10,400원으로 불변이었다.
  - CI 시간 정밀도 보강: PR #191의 Linux CI에서 생성 응답의 nanosecond `endsAt`과 DB 재조회 값의 정밀도 차이로 REST Docs 테스트 1건이 실패했다. 종료 API가 값을 변경한 회귀가 아니므로 테스트가 생성 응답이 아닌 생성 직후 DB 저장값을 기준으로 close 전후 불변을 비교하도록 보강했다. 실패 단일 테스트와 전체 `449 tests / 0 failures / 0 errors / 3 skipped`를 다시 통과했다.
  - 프론트 read-only 계약 대조: `/Users/josephuk77/Documents/FeithLog-frontend`의 clean `develop` `aba1ab0`에서 #188 신규 weekly JSON/export 호출부, #189 MEAL enum·duty/account/poll/settlement client가 없고, #190 `AdminWritableChargeStatus`와 관리자 UI가 PAID를 차단함을 확인했다. 기존 user devotion GET/PUT, 본인 납부 PATCH, 기존 admin missing 호출은 현재 계약과 일치한다. 백엔드나 프론트 계약은 임의 변경하지 않고 PM integration finding으로 남긴다.
  - 실행 상태/정리 결정: 최신 사용자 지시에 따라 compose down과 builder prune을 실행하지 않았다. backend `28080`, PostgreSQL `25432`, Redis `26379`를 계속 실행 중이며 최종 host 가용 공간은 7.8GiB, Docker image 2.107GB/volume 502.8MB/build cache 1.729GB 관찰값이다. image/volume/source/DB 삭제는 0건이다. browse 계층은 Playwright headless shell 미설치로 시작하지 못했지만 동일 localhost endpoint의 실제 curl HTTP와 XLSX binary 검증은 완료했다.
  - 리뷰 잔여 후보: V8 `meal_poll_charge_groups(settlement_id, id)` 조회 인덱스, MEAL 정산의 응답자별 반복 조회/저장, MEAL 청구 대상 사용자 bulk lookup, #188의 Billing projection port 경계는 성능·아키텍처·스키마 결정이 필요해 통합 세션이 임의 변경하지 않았다. PM이 별도 반영 여부를 결정해야 한다.
  - 이력서 문장 후보: `세 기능 브랜치를 merge commit으로 통합하고 경건 재제출 race를 row lock으로 해소해 449개 전체 테스트·151개 REST Docs 스니펫을 통과했으며, 격리 PostgreSQL V1→V8 clean/V7→V8 upgrade와 실제 HTTP 45-step 연결 QA를 실패 0건으로 검증했다.`

### 2026-07-13

- #188 관리자 주차별 사용자 경건·벌금 조회 및 Excel 다운로드:
  - 제품 기준: 현재 ACTIVE 캠퍼스 멤버를 제출/미제출로 분리한다. 실제 저장 `PENALTY` 청구 id/amount/status를 표시하고 과거 금액을 현재 규칙으로 재계산하지 않는다. 사용자 확정에 따라 `totalPenaltyAmount`는 `PAID + UNPAID`이고, `WAIVED/CANCELED`는 행에는 표시하되 합계에서 제외한다.
  - API/권한: JSON 조회와 XLSX export 2개 endpoint를 추가했다. service `ADMIN` 또는 해당 캠퍼스 ACTIVE `MINISTER/ELDER/CAMPUS_LEADER`만 허용하며 MEMBER와 다른 캠퍼스 관리자는 403, non-Monday는 400이다. read-only transaction 안에서 ACTIVE members/users/weekly records/daily checks/charges를 bulk 조회하고 repository-call characterization으로 멤버 수 비례 조회를 차단했다.
  - Excel/의존성: JSON과 동일 query result를 `주간 요약`, `일별 상세` 2개 시트로 렌더링하고 제출자를 먼저, 미제출자를 별도 하단 구역에 둔다. direct dependency는 `org.apache.poi:poi-ooxml:5.5.1` 1개이며 runtime에 POI 5.5.1 3개 artifact, XMLBeans 5.3.0, Commons Compress 1.28.0 등이 해석됐다. 추가 전 runtime에는 Apache POI가 없었다.
  - TDD RED/GREEN: JSON endpoint 부재로 controller 3 tests가 expected 200/actual 404, Excel endpoint 부재로 단일 test가 404, REST Docs index include 누락으로 1 test가 assertion failure였다. 구현 후 JSON/bulk/structure focused, Excel, REST Docs/index가 각각 GREEN이었다.
  - 검증 수치: Devotion/Billing/Admin focused `82 tests / 0 failures / 0 errors / 0 skipped`, 전체 `420 tests / 0 failures / 0 errors / 3 skipped`, `./gradlew build`, `./gradlew asciidoctor`, `git diff --check` 성공. test source 81개, REST Docs snippet group 126개다.
  - REST Docs: JSON 전체 response field와 Excel `Content-Type`/`Content-Disposition`/filename을 문서화하고 `index.adoc` include를 테스트로 고정했다. XLSX binary에는 pretty-print를 적용하지 않았으며 POI 테스트가 실제 workbook의 2 sheets/header/order/value를 연다.
  - PM finding 대응: 벌금 합계는 현재 개발 대화에서 사용자가 “각자 주차별이 페이드랑 언페이드랑 합산할게”라고 직접 선택한 `PAID + UNPAID` 기준임을 확인해 decision log에 승인 근거를 명시했다. 전역 `AGENTS.md`와 Codex Hook에서는 일회성 #188/#189/#190 번호·통합 브랜치·Docker 순서를 제거하고, 일반 PM 전체 diff 리뷰/finding 재검증 게이트만 유지했다. 이번 통합 절차는 `docs/decision-log.md`에만 남겼다.
  - PM finding 후 재검증: PM 독립 실행과 동일한 4개 focused class는 `18 tests / 0 failures / 0 errors / 0 skipped`, 전체는 `420 tests / 0 failures / 0 errors / 3 skipped`, build와 asciidoctor 및 diff check는 성공했다. 첫 focused 재실행은 macOS JVM Byte Buddy 동적 attach 보조 프로세스에서 정지해 해당 실행만 중단했고, 저장소 변경 없이 기존 resolved Byte Buddy agent를 실행 시점에 선로딩해 focused 32초·전체 1분 38초로 통과했다.
  - Docker 이관: 최신 사용자 결정으로 feature 세션의 Docker build/up/API QA는 실행하지 않고 #188/#189/#190 통합 후 `integration/188-190-devotion-meal-billing`에서 한 번 수행한다. 이전 시도에서는 public image pull과 Docker 내부 `bootJar` 4분 38초 성공 뒤 overlay2/containerd `input/output error`가 발생했고 host available space 561MiB를 관찰했다. 이 사실은 기록하되 feature blocker로 집계하지 않으며 파일/Docker 데이터 삭제는 수행하지 않았다.
  - DB/회귀: Entity/DB/Flyway/ErrorCode/기존 API/기존 경건 제출·벌금 생성·납부·정산 동작은 변경하지 않았다. Controller Entity 반환과 Swagger annotation 추가도 없다.
  - 이력서 문장 후보: `관리자 주차별 경건·실제 벌금 조회와 2-sheet XLSX export를 동일 bulk query model로 구현해 campus 격리와 N+1 회귀를 고정하고, 82개 focused·420개 전체 테스트와 REST Docs 126개 스니펫 기준으로 권한·집계·파일 계약을 검증했다.`
- #189 밥 담당과 투표 항목 그룹별 후청구:
  - TDD RED: production 수정 전에 MEAL enum/V8, 본인 계좌 API, 서버 시각 즉시 OPEN poll, poll 단위 후청구 API, 정수 exact calculator 계약을 추가했다. 최초 실행은 각각 enum/V8 2 failures, account endpoint 실패, `createdAt` compile 실패, charges 404, calculator compile 실패로 요구사항 부재를 확인했다.
  - 구현: 한 캠퍼스의 ACTIVE MEAL duty 수 제한을 없애고 사용자별 idempotent 배정과 DB partial unique를 적용했다. 모든 MEAL 운영 API는 service ADMIN/캠퍼스 관리자 여부와 무관하게 active same-campus MEAL duty를 요구하며, 담당자는 본인 소유 ACTIVE MEAL 계좌만 관리한다. MEAL poll은 같은 서버 `Clock` instant로 `startsAt/createdAt`을 기록하고 `SINGLE/OPEN`으로 즉시 생성하며 미래 `endsAt` 외 billing/time 필드는 거부한다.
  - 정산/격리: CLOSED poll의 최종 `poll_response_options`를 서버에서 다시 집계하고, poll 공통 본인 계좌 1개와 option별 `PER_MEMBER`/`GROUP_TOTAL`을 한 batch로 처리한다. GROUP_TOTAL은 quotient/remainder 기반 ceiling으로 10,000원÷3명을 3,334원씩 청구해 requested 10,000원, actual 10,002원, rounding 2원으로 분리한다. poll pessimistic lock, settlement/charge DB unique, 단일 transaction으로 재청구 409·경합 중복·부분 저장을 차단하며 다른 담당자에게 계좌 ID를 노출하지 않는다.
  - 코드리뷰 RED/GREEN: 일반 관리자 close·missing-members·charge status·dashboard와 generic poll/template의 MEAL 우회, response/option/close/settlement 사이의 poll lock 불일치, 정산된 poll 30일 보존 삭제 FK 충돌, 요청 list null 요소를 회귀 테스트로 고정했다. 기존 production에서 API/구조 묶음은 `82 tests / 6 failures`, 보존 삭제는 `1 test / 1 failure`로 RED였고, 공용 pessimistic lock·404 비노출·dashboard 제외·V8 cascade·Bean Validation 보강 후 회귀 묶음 `90 tests / 0 failures`로 GREEN이 됐다. 재리뷰에서 수동 close의 `endsAt` 변경을 `1 test / 1 failure`로 추가 재현해 상태만 CLOSED로 바꾸도록 수정했고, 실제 settlement/group/첫 charge write 뒤 unique 충돌 rollback, 전용 close 404, role 불변, PER_MEMBER/source/account snapshot을 보강했다. 마지막 DB 리뷰의 무정렬 false-green도 구조 테스트 RED 후 settlement 응답 option을 ID 오름차순으로 고정해 첫 charge INSERT 이후 rollback 경로를 결정적으로 만들었다. PM 재리뷰에서는 persisted template target 인가보다 MEAL request validation이 먼저 실행되는 회귀를 unit/HTTP `2 tests / 2 failures`로 재현해 인가 우선 403과 권한 있는 manager의 unsupported 400을 고정했다. 목록 계약은 35일 false-green 대신 7일 초과·30일 retention 범위 내인 8일 CLOSED poll로 정정하고, 31일 settled MEAL cascade 삭제 회귀는 유지했다.
  - 전체 검증: Duty/Poll/Billing/Flyway/REST Docs focused `188 tests / 0 failures / 0 errors / 2 skipped`, 전체 `429 tests / 0 failures / 0 errors / 3 skipped`, `./gradlew build`, `./gradlew asciidoctor`, `git diff --check`가 성공했다. test source는 80개, Java source/test는 634개, REST Docs snippet group은 147개이며 MEAL 관련 group 23개와 렌더된 `index.html`을 확인했다.
  - Docker 결정: 이 feature에서는 Docker build/up/API QA를 실행하지 않았다. 사용자 최신 결정에 따라 실제 PostgreSQL Flyway clean/upgrade와 #188/#189/#190 연결 HTTP QA는 세 feature PM 승인 후 `integration/188-190-devotion-meal-billing`에서 한 번 수행한다.
  - 제약/영향: GitHub token에 `read:project` scope가 없어 Issue #189 Project 카드 존재/상태 확인과 In Progress 이동은 수행하지 못했다. V1-V7, 기존 COFFEE/PENALTY 의미, dependency, Swagger annotation, Controller Entity 반환은 변경하지 않았고 push/PR/merge도 수행하지 않았다.
  - 이력서 문장 후보: `다수 MEAL 담당자·본인 계좌·즉시 OPEN 투표와 option group별 후청구를 정규화하고 exact ceiling 산술·pessimistic lock·DB unique·단일 transaction으로 1회성 정산을 보장했으며, 429개 전체 테스트와 147개 REST Docs 계약으로 권한·격리·rollback을 검증했다.`
- #190 벌금 취소 후 경건 재제출과 관리자 납부 완료:
  - TDD RED: production 수정 전에 관리자 `UNPAID -> PAID`, 서버 `paidAt`, terminal conflict, PENALTY cancel/reopen, daily 보존, 양수 동일 row 재사용, 0원 CANCELED 유지, category/source/campus/권한/rollback, 사용자 납부 회귀, REST Docs 계약을 추가했다. 신규 통합 테스트 최초 실행은 `7 tests / 6 failures / 0 errors / 0 skipped`였고 영향 제외 경로 1건만 기존 동작으로 통과했다.
  - 관리자 상태 전이: 기존 `PATCH /api/v1/admin/charges/{chargeItemId}/status`에서 관리 가능한 category의 `UNPAID -> PAID`를 허용하고 `paidAt`을 서버 현재 시각으로 기록한다. `PAID/WAIVED/CANCELED -> PAID`는 기존 `409 BILLING_CHARGE_STATUS_TRANSITION_CONFLICT`를 사용하며 관리자 권한과 401/403, 사용자 본인 `납부했어요` 흐름을 유지했다.
  - 취소/재오픈 경계: `UNPAID PENALTY + DEVOTION_RECORD` 취소 시 Billing transaction owner가 application port를 호출하고 Devotion adapter가 same-campus/same-user/source weekly record를 검증해 `submittedAt`만 null로 만든다. daily checks는 보존하며 mismatch나 adapter 실패 시 charge 취소까지 rollback된다. `WAIVED`, `COFFEE`, `POLL_RESPONSE`는 재오픈하지 않는다.
  - 재제출: 현재 활성 벌금 규칙을 다시 계산한다. 양수이면 기존 CANCELED unique-source charge row를 같은 ID로 `UNPAID` 재활성화하면서 amount, 계좌 FK/snapshot, title/reason/dueDate를 갱신한다. 0원이면 CANCELED row를 유지하고 신규 row를 만들지 않는다. #182의 양수 domain/DB 불변식은 유지했다.
  - PM 리뷰 RED/GREEN: 같은 청구에 대한 관리자 취소와 사용자 납부가 동시에 모두 성공하는 lost update를 deterministic latch 통합 테스트로 재현했다. 두 요청이 실제로 모두 성공해 RED였고, 사용자·관리자 상태 쓰기가 동일 charge row의 `PESSIMISTIC_WRITE` 잠금을 거치도록 고쳐 후행 요청이 커밋된 상태를 다시 읽고 기존 상태별 전이 규칙을 적용하게 했다. 관리자 `ADMIN`·`ELDER`·`CAMPUS_LEADER` PAID, 만료 토큰 401, 다른 캠퍼스 관리자 403, terminal-to-PAID 409 REST Docs와 문서 index 회귀도 보강했다.
  - PM 재검토 RED/GREEN: 양수 재제출이 source key로 읽은 CANCELED row를 잠그지 않아 동시 사용자 납부의 PAID를 stale UNPAID로 덮어쓸 수 있음을 repository 진입 latch 테스트로 재현했다. PENALTY와 COFFEE의 기존 source charge 조회·갱신도 `PESSIMISTIC_WRITE`로 직렬화하고, 두 동시성 테스트 모두 실제 잠금 조회 호출 전·완료 후 latch를 분리해 선행 transaction 해제 전에는 조회가 완료되지 않음을 검증하도록 보강했다.
  - 검증: Billing·Devotion·관리자 Controller·REST Docs focused `148 tests / 0 failures / 0 errors / 0 skipped`, 전체 `425 tests / 0 failures / 0 errors / 3 skipped`, `./gradlew --no-daemon --max-workers=1 build`, `./gradlew --no-daemon --max-workers=1 asciidoctor`, `git diff --check`가 성공했다. test source는 80개, REST Docs snippet group은 126개다.
  - Docker: 사용자 최신 결정으로 feature 세션의 Docker build/up/API QA를 중단했다. #188/#189/#190 승인 후 `integration/188-190-devotion-meal-billing`에서 세 기능 연결 QA를 한 번 수행한다. 결정 전 격리 build/up 시 Docker Desktop daemon이 중단되어 실제 HTTP 검증은 수행하지 않았고, destructive cleanup이나 volume 삭제는 실행하지 않았다.
  - 영향: 기존 API path/request/response DTO와 Controller, ErrorCode 추가, DB schema/Flyway V1-V7, dependency, SecurityConfig 변경은 0건이다. 제거된 ErrorCode는 더 이상 유효하지 않은 관리자 PAID 금지 전용 상수 1건이며, terminal conflict는 기존 code를 재사용한다. Billing Entity가 Devotion Entity를 직접 참조하지 않는다.
   - 이력서 문장 후보: `벌금 취소와 경건 재제출을 application port 기반 단일 transaction으로 연결하고 상태 전이와 source charge 재활성화를 row lock으로 직렬화해 daily 기록 보존·rollback·terminal conflict를 보장했으며, 148개 focused·425개 전체 테스트와 REST Docs로 검증했다.`

- #186 Spring Security 취약 버전 maintenance upgrade와 보안 헤더 회귀:
  - 기준/선택: 최신 `origin/develop` `f3e81fb9`에서 Spring Boot plugin/BOM만 `3.5.0 -> 3.5.15`로 올렸다. 공식 Boot 3.5.15가 관리하는 Spring Security config/core/crypto/web/test `6.5.11`을 사용하며 개별 Security override와 eager-header workaround는 추가하지 않았다.
  - TDD RED/GREEN: test-only commit `cc0aa8b`에서 dependency contract와 200/401/403/404 기본 헤더 테스트를 먼저 추가했다. 최초 5 tests 중 헤더 4건은 통과하고 Security 6.5.0 module 때문에 contract 1건이 실패했다. 업그레이드 후 모두 GREEN이며 runtime/test `dependencyInsight`의 Security 6.5.0-6.5.10 잔여는 0건이다.
  - PM review contract 보강: 기존 test JVM JAR manifest scan은 안전한 test dependency가 취약 production runtime을 가릴 수 있고 qualifier를 제거해 RC를 정식 release로 인정했다. test-only commit `d5fec90`에서 취약 runtime+안전 test runtime false-green과 `6.5.11-RC1` 허용을 `3 tests / 2 failures`로 재현했다. `b266272`는 Gradle 실제 `runtimeClasspath`/`testRuntimeClasspath` resolved artifacts를 독립 manifest와 Test task input으로 전달하고, runtime config/core/crypto/web 및 test config/core/crypto/web/test 전체에 `>=6.5.11`과 숫자 3-part 정식 release만 허용한다. RC/M/SNAPSHOT/.Final/+build/2-part/4-part를 거부하며 contract는 `10 tests / 0 failures`로 GREEN이다.
  - 최종 manifest: runtime은 `config/core/crypto/web=6.5.11`, test runtime은 `config/core/crypto/test/web=6.5.11`이며 취약 범위 잔여 0건이다. PM 독립 구조 리뷰도 두 false-green finding 해소와 새 blocking finding 0건을 확인했다.
  - resolved graph: runtime 좌표는 208개에서 209개로 바뀌었고 81개 버전 변경, 1개 추가, 제거 0이다. 주요 전이는 Spring Framework 6.2.7→6.2.19, Spring Data 3.5.0→3.5.12, Hibernate 6.6.15→6.6.53, Jackson BOM 2.19.0→2.21.4, Lettuce 6.5.5→6.6.0, PostgreSQL JDBC 42.7.5→42.7.11이다. 전체 좌표 diff는 `docs/security/186-spring-security-maintenance-upgrade.md`에 기록했다.
  - 테스트/빌드: 기존 auth/login/refresh/logout/withdrawal/tokenVersion role invalidation/FCM/REST Docs focused 59 범위는 contract 사례 9건 증가 후 `68 tests / 0 failures / 0 errors / 0 skipped`, 전체 `413 tests / 0 failures / 0 errors / 3 skipped`, 실제 Redis Lua 통합 `1 test / 0 failures`를 통과했다. `./gradlew build`, `./gradlew asciidoctor`, `git diff --check`도 성공했다. test source는 78개, REST Docs snippet group은 124개다.
  - Docker HTTP QA: 격리 project `faithlog-qa-186-20260713`에서 PostgreSQL/Redis healthy, backend Boot 3.5.15 health 200/UP을 확인했다. signup/login, Access 1,800초/Refresh 1,209,600초, 200/401/403 기본 non-HSTS 헤더, refresh rotation, old-token reuse session revoke, logout access/refresh 무효화를 통과했다. 실제 Redis 동시 Lua는 ROTATED 1/REJECTED 1과 1,209,660초 marker TTL 경계를 통과했다.
  - 404 한계/결정: MockMvc 미등록 경로는 404와 승인 헤더를 검증하지만 실제 servlet ERROR dispatch를 대표하지 않는다. 별도 Boot 3.5.0 baseline과 3.5.15 Docker 모두 valid token + unmatched path가 `401 AUTH_UNAUTHORIZED`였으므로 #186 회귀가 아니다. PM A안대로 `DispatcherType.ERROR`/`/error` 및 SecurityConfig는 수정하지 않았고 실제 HTTP 404 성공으로 집계하지 않는다. 별도 Issue도 생성하지 않았다.
  - Docker 정리: 진단용 baseline 컨테이너와 compose 컨테이너/network만 종료·제거하고 named volume은 보존했다. `down -v`, volume/image/system prune은 실행하지 않았으며 마지막 Docker 명령 `docker builder prune -f`로 1.4GB를 회수했다.
  - PM review 후 Docker: 보강 범위가 test/build-script의 dependency contract에만 한정되고 production 동작 변경이 없어 Docker는 재실행하지 않았다.
  - 영향: production Java/API mapping/DTO/ErrorCode/status/message/SecurityConfig/DB/Entity/Flyway/Cloud Run·GCP·Supabase·Upstash·Firebase diff는 0이다. #176 rotate-or-revoke/fail-closed, #76 tokenVersion, logout/withdrawal/FCM, 401/403 구분을 유지했고 실제 access/refresh token 값은 QA 출력·저장소 문서에 남기지 않았다. `docs/decision-log.md` 변경, push, PR도 수행하지 않았다.
  - 이력서 문장 후보: `Spring Boot 3.5.0→3.5.15 managed BOM으로 취약 Spring Security 6.5.0을 6.5.11로 교체하고 production/test resolved graph 독립 계약과 정식 release 검증으로 false-green을 차단했으며, 209개 runtime 좌표의 81개 전이·413개 전체 테스트·실제 Redis Lua·격리 Docker 인증/헤더 QA로 회귀를 검증했다.`

- #183 COFFEE 옵션 가격 backend catalog authority 고정:
  - TDD RED: production 수정 전에 direct COFFEE Poll null `menuId`, COFFEE template create/update null `menuId`, client `content`/`priceAmount` override, REST Docs 400 계약을 먼저 추가했고 `4 tests / 4 failures`로 catalog 우회가 저장 경로에 남아 있음을 확인했다.
  - 구현: direct Poll은 요청 `pollType`, template create는 요청 `pollType`, persisted template update는 #179와 동일하게 저장된 `template.pollType()`을 resolver에 전달한다. COFFEE는 active catalog `menuId`를 필수로 하고 `content = catalog.name`, `composeMenuCode = catalog.menuCode`, `priceAmount = catalog.priceAmount`만 snapshot한다. null은 승인된 `400 POLL_COFFEE_OPTION_MENU_REQUIRED`, not-found/inactive는 기존 오류를 재사용한다. 비-COFFEE custom content/0원 경로와 사용자 COFFEE option-add 계약은 분리해 유지했다.
  - 흐름 검증: direct/template create/update의 null·missing·inactive 거부와 row/charge 불변, client override 무시, template→자동 생성 Poll snapshot 일치, 응답 시 charge 미생성, 수동 close/스케줄러 CLOSED 정산의 catalog 제목·금액 일치, `optionIds`/`poll_response_options`, #179 persisted-target authorization, #182 `ChargeItem amount > 0` 회귀를 검증했다.
  - 테스트/문서: Poll/template/catalog/Batch/REST Docs focused 64 tests GREEN 뒤 #179/#182를 포함한 Poll·Billing·Batch·REST Docs 집중 회귀 `87 tests / 0 failures / 0 errors / 0 skipped`, 전체 `399 tests / 0 failures / 0 errors / 3 skipped`를 통과했다. `./gradlew build`, `./gradlew asciidoctor`, `git diff --check`도 성공했고 test source 76개, REST Docs snippet group 124개를 확인했다.
  - Docker API QA: #183 전용 PostgreSQL/Redis/app 컨테이너·DB·포트·named volume에서 Flyway V1-V7, Hibernate validate, health UP 후 catalog 준비, direct/template/update override, null/inactive 400 및 row 불변, template 자동 생성, response-only, scheduler close, settlement title/amount의 11개 시나리오를 통과했다. volume을 삭제하지 않고 `docker compose down`했으며 마지막 Docker 명령 `docker builder prune -f`에서 696.7MB를 회수했다.
  - 영향: API mapping과 정상 request/response DTO, Controller, `optionIds`, `poll_response_options`, 권한 의미/순서, DB/Flyway, dependency, non-COFFEE 동작, #182 Billing/Flyway는 변경하지 않았다. client 입력은 로그/오류에 echo하지 않았고 실제 secret/token/개인정보 기록, push/PR은 수행하지 않았다.
  - 이력서 문장 후보: `COFFEE Poll/template 옵션의 이름·코드·가격 권한을 backend catalog로 고정하고 client override와 null/inactive 우회를 차단했으며, persisted-target 인가 순서를 보존한 채 4개 RED 실패·87개 집중 회귀·399개 전체 테스트·11개 격리 Docker API/스케줄러 정산 시나리오로 검증했다.`

- #182 경건 벌금 overflow와 음수 청구 차단:
  - TDD RED: production 수정 전에 saturdayLateMinutes 1,440 성공·1,441/음수 실패, 규칙 곱셈/항목 합산/저장 범위 overflow, weekly/daily/charge rollback, ChargeItem create/update 0·음수 거부, V7 migration과 DB CHECK 테스트를 추가했다. 격리 재실행은 `57 tests / 11 failures`로 요구사항별 실패를 확인했고 테스트 전용 rollback fixture는 `@DirtiesContext`로 격리했다.
  - 계산/트랜잭션: 입력 범위를 `0..1,440`으로 제한하고 기존 `DEVOTION_INVALID_SATURDAY_LATE_MINUTES` 400을 재사용했다. 계산은 `Math.multiplyExact`/`Math.addExact` 기반 `long`으로 수행하고 최종 PostgreSQL `INTEGER` 범위를 검사한다. overflow/저장범위 초과는 `400 DEVOTION_FINE_AMOUNT_OUT_OF_RANGE`로 변환되며 weekly 생성, 7개 daily upsert, submit, Billing 호출이 동일 transaction에서 rollback된다. 금액 저장형은 `double`이 아니라 기존 `INTEGER`를 유지한다.
  - Billing/DB: `ChargeItem` create와 unpaid update가 `amount > 0`을 강제한다. V1-V6 수정 없이 V7 `ck_charge_items_amount_positive`를 추가했고 migration 시 즉시 validate한다. legacy 0·음수 row가 있으면 migration은 fail-closed로 rollback되며 기존 row를 자동 수정하거나 삭제하지 않는다.
  - PostgreSQL: clean V1→V7은 constraint validated 상태와 0/음수 CHECK 거부를 통과했다. 별도 V6 fixture에 legacy 0원 row를 넣은 경로는 V7 migration 실패, legacy row 보존, `flyway_schema_history` V7 미기록을 통과했다.
  - 전체 검증: focused 57 tests GREEN, 전체 `396 tests / 0 failures / 0 errors / 3 skipped`, `./gradlew build`, `./gradlew asciidoctor`, `git diff --check`가 성공했다. test source는 76개, REST Docs snippet group은 123개, Flyway는 V1-V7이다.
  - Docker HTTP QA: 격리 project `faithlog-qa-182-20260713`에서 PostgreSQL/Redis healthy, Flyway 7 migrations, Hibernate validate, app health 200을 확인했다. 1,440은 200, 1,441/음수는 각각 400, 저장범위 초과는 400과 weekly/daily/charge `0→0`, 0원은 charge `0→0`, 정상 2,500원 PENALTY는 charge `0→1`, dashboard unpaid 2,500원을 확인했다. 같은 project를 volume 삭제 없이 `docker compose down`했고 마지막 Docker 명령 `docker builder prune -f`로 696.6MB를 회수했다.
  - 영향: API mapping과 정상 request/response DTO, 권한, 벌금 공식, COFFEE 정산 흐름은 변경하지 않았다. 실제 운영 DB 조회/수정/삭제, `down -v`, volume/image/system prune, secret/token 기록, push/PR은 수행하지 않았다.
  - 이력서 문장 후보: `경건 벌금 계산을 long exact arithmetic과 INTEGER 범위 검증으로 보강하고 Billing·Flyway 양수 불변식을 3중 적용해 음수 청구와 dashboard 상쇄를 차단했으며, 11개 RED 실패·396개 전체 테스트·clean/legacy PostgreSQL migration·격리 Docker HTTP rollback QA로 검증했다.`

- #160 입력 검증과 민감정보 노출 읽기 전용 보안 감사:
  - 최신 `origin/develop` `52e0b4ae` 기준 21개 Controller·80개 endpoint·36개 request DTO·57개 response DTO·123개 path/query binding·4개 page/sort parser·56개 persistence constraint 파일을 입력 surface → validation → normalization → persistence constraint → error status와 민감 필드 → 저장 위치 → 응답 DTO → 로그/문서 → 허용 역할 순서로 대조했다.
  - confirmed finding은 Critical 0 / High 0 / Medium 2 / Low 0이다. 경건 제출의 상한 없는 토요일 지각 시간이 `int` 산술을 overflow시켜 음수 `UNPAID` 벌금과 캠퍼스 미납 합계 왜곡으로 이어지는 경로를 신뢰도 10/10, COFFEE Poll/template의 null `menuId` 옵션이 client 가격을 catalog 검증 없이 snapshot·정산하는 경로를 신뢰도 9/10으로 독립 정적 검증했다. 최소 확정 영향은 각각 본인 음수 청구·재무 집계 무결성 훼손과 응답 회원의 위조 COFFEE 청구이며, 자동 환불·자동 출금·선택 없는 청구·cross-campus 영향은 주장하지 않았다.
  - false positive/의도 정책 9개와 confidence 8/10 미만 unverified/hardening 3개를 confirmed와 분리했다. #157/#158/#159 및 #176/#179 수정은 중복 finding으로 집계하지 않았고 후속 수정 Issue도 생성하지 않았다.
  - PM 독립 재검증은 격리 `GRADLE_USER_HOME=/private/tmp/faithlog-gradle-160-review`, 단일 Gradle 실행, `--no-parallel --rerun-tasks` 조건에서 감사 문서의 16개 focused test class 전부를 재실행해 `BUILD SUCCESSFUL`, 16 suites / 138 tests / 0 failures / 0 errors / 0 skipped를 확인했다. counted manifest와 두 confirmed finding도 실제 코드 경로와 일치한다고 기계 검증했다.
  - 초기 감사 세션에서 먼저 관찰된 기본 Gradle cache metadata 읽기 실패와 격리 실행 간 XML 동시 쓰기 충돌은 코드·테스트 실패가 아닌 실행환경 concern으로 최종 정리했다. 따라서 #160의 최종 검증 상태는 focused 재실행 성공이다.
  - 실제 secret/token/개인정보/계좌번호/기도제목/알림 본문 값을 출력하거나 문서화하지 않았다. current tree와 좁은 history의 high-signal secret prefix 후보 및 generated high-signal prefix 파일은 각각 0건이었고, production/test/config/DB/Flyway/인프라 변경, Docker, push, PR은 모두 0건이다. 새 제품 결정을 만들지 않아 `docs/decision-log.md`는 변경하지 않았다.
  - 이력서 문장 후보: `21개 Controller·80개 endpoint의 36개 입력 DTO와 57개 응답 DTO를 validation·persistence·오류·민감정보 경계까지 추적해, 벌금 int overflow에 의한 음수 청구와 COFFEE catalog 우회 가격 정산 2건을 신뢰도 10/10·9/10으로 식별하고 PM 독립 재검증 16 suites·138 tests BUILD SUCCESSFUL로 회귀 상태를 확정했다.`

- #179 COFFEE duty 투표 템플릿 수정 권한 대상 고정:
  - TDD RED: production 수정 전에 persisted `CUSTOM`, `WED_SERVICE`, `SATURDAY_LEADER` 템플릿 각각에 active COFFEE duty가 owned active COFFEE 계좌를 포함한 COFFEE request body를 보내는 회귀 테스트 3건을 추가했다. 최초 실행은 `3 tests / 3 failures`로, 세 유형 모두 403 없이 수정 경로를 통과하는 F-159-01을 재현했다. 각 테스트는 거부 후 title, selection, charge generation, payment category/account, user-option 설정, 자동 생성 스케줄/시간, option rows 전체가 원본과 동일한지 record equality로 검증한다.
  - 권한 경계 수정: `PollTemplateCommandService.updateTemplate`는 same-campus 404 확인 후 요청 body와 무관하게 persisted `template.pollType()`만으로 먼저 권한을 판단한다. persisted `COFFEE`만 `requireCoffeeTemplateManager`를 사용하고, persisted `CUSTOM/WED_SERVICE/SATURDAY_LEADER`는 `requireTemplateManager`를 사용한다. 권한 확인 뒤 기존 요청 billing/account 검증을 별도로 실행해 active same-campus COFFEE account와 requester owner 조건을 유지했다.
  - 회귀 범위: 비-COFFEE 3종 duty 403 및 완전 불변, persisted COFFEE duty 성공, null/다른 사용자/비활성/타 campus/PENALTY 계좌 거부, campus manager와 service ADMIN 성공, cross-campus `POLL_TEMPLATE_NOT_FOUND` 404, HTTP `POLL_TEMPLATE_MANAGE_FORBIDDEN` 403과 기존 message를 검증했다. Poll focused 4 classes는 61 tests / 0 failures / 0 errors / 0 skipped다.
  - 전체 검증: `./gradlew test` 386 tests / 0 failures / 0 errors / 2 skipped, `./gradlew build`, `./gradlew asciidoctor`, `git diff --check`가 성공했다. test source 75개와 전체 Java source/test 588개는 유지됐고 REST Docs snippet group은 123개다.
  - Docker HTTP QA: 격리 project `faithlog-qa-179-20260713`의 새 image에서 PostgreSQL/Redis healthy와 app health 200을 확인했다. campus manager 비-COFFEE update 200, duty의 persisted 비-COFFEE+COFFEE-body update 403/`POLL_TEMPLATE_MANAGE_FORBIDDEN`, cross-campus update 404/`POLL_TEMPLATE_NOT_FOUND`, duty의 persisted COFFEE create 201/update 200, 거부 후 template 필드와 option rows 불변을 실제 HTTP로 검증했다. 응답 token 값은 출력하거나 기록하지 않았다.
  - Docker 정리: 같은 project를 `docker compose down`으로 volume 삭제 없이 종료했다. `down -v`, named volume 삭제, system/image/volume prune은 실행하지 않았고 마지막 Docker 명령 `docker builder prune -f`에서 dangling build cache 696.6MB를 회수했다.
  - 영향: production 변경은 Poll template command service 1개에 한정된다. API mapping/path/request/response DTO, HTTP status/code/message, Controller/Entity, DB/Flyway, Poll 생성/응답/결과/댓글/정산, Swagger annotation, dependency 변경은 0건이다. 새 제품 결정을 만들지 않아 `docs/decision-log.md`는 변경하지 않았다.
  - 이력서 문장 후보: `요청 body가 기존 객체의 권한 등급을 재분류하던 COFFEE duty BFLA를 persisted pollType 기반 선행 인가로 차단하고, 비-COFFEE 3종 무단 수정·완전 불변·계좌 owner/campus/type 회귀를 61개 Poll focused 및 386개 전체 테스트와 격리 Docker HTTP QA로 검증했다.`

### 2026-07-12

- #176 Refresh Token Rotation 원자성 및 동시 재사용 차단:
  - TDD RED: production 수정 전 동일 old refresh를 2개 thread에서 barrier로 동시에 호출하는 테스트를 추가했다. `./gradlew test --tests 'com.faithlog.user.controller.AuthRefreshControllerTest.concurrent_refresh_with_same_old_token_allows_exactly_one_rotation'`는 실제 `[200, 200]`, 기대 `[200, 401]`로 `1 test / 1 failure`였고, 분리된 Redis GET→SET race를 재현했다.
  - PM review RED: 실제 Redis integration test에서 수동 revoke 호출을 제거하고 두 `rotate()`만으로 session 폐기를 기대하자 refresh key가 남아 `expected false, actual true`로 `1 test / 1 failure`가 발생했다. 이는 CAS reject와 별도 revoke Lua 사이의 보안 상태 경합을 재현했다.
  - 원자 전이: `RefreshTokenStore.rotate`가 rotation TTL과 revocation TTL을 함께 받고 `ROTATED/REJECTED` 결과를 반환한다. production Redis adapter의 단일 Lua는 marker가 이미 있으면 TTL 연장 없이 reject하고, expected JTI가 일치하면 new JTI+14일 TTL로 교체하며, mismatch면 같은 실행 안에서 refresh key 삭제와 14일+60초 marker 저장을 완료한다. test adapter도 synchronized 한 임계구역에서 같은 rotate-or-revoke 전이를 재현한다.
  - 재사용 대응: application service는 `REJECTED` 후 두 번째 Redis write를 호출하지 않고 즉시 `401 AUTH_UNAUTHORIZED`를 반환한다. filter용 `SessionRevocationChecker`만 별도 read 책임으로 유지한다. `JwtAuthenticationFilter`는 access blacklist와 tokenVersion 사이에 session marker를 확인하고 Redis 예외 시 principal을 만들지 않는다. 일반 logout 의미는 확대하지 않았다.
  - 범위 테스트: 같은 old refresh 동시 요청은 성공 1/401 1, loser code `AUTH_UNAUTHORIZED`, winner access/refresh 후속 401, 동일 user의 다른 session access/refresh 200, 다른 user session access/refresh 200, 새 session 재로그인 200을 검증했다. 정상 단일 rotation, 순차 reuse, access/refresh type mismatch, Redis rotation/filter fail-closed, logout/withdrawal/service role/campus role/FCM/REST Docs 회귀도 통과했다.
  - 실제 Redis: 격리 Redis 7에서 수동 revoke 없이 두 thread의 `rotate()`만 실행해 `ROTATED 1 / REJECTED 1`, refresh key 삭제, marker 존재, TTL 1,209,600+60초 범위, 후속 rotation 거절을 확인했다.
  - 전체 검증: `./gradlew test`는 380 tests / 0 failures / 0 errors / 2 skipped, `./gradlew build`와 `./gradlew asciidoctor` 성공, REST Docs의 기존 refresh success/reused-token 401 계약을 재생성했다. test source 75개, 전체 Java source/test 588개다.
  - Docker HTTP QA: PM 수정 후 격리 project `faithlog-qa-176-fix`에서 PostgreSQL/Redis healthy와 backend health `UP`을 확인했다. 동일 refresh 병렬 요청 성공 1/401 1, winner access/refresh 401, 같은 user 다른 session 200, 다른 user session 200, 재로그인 새 session 200을 실제 production Redis adapter로 검증했다. token 값은 출력하지 않았다. 같은 project를 `docker compose down`으로 volume 삭제 없이 종료했다.
  - Docker build/cache: Docker Hub credential helper가 base-image metadata 조회에서 정체돼 새 image build는 완료되지 않았다. 이미 검증된 로컬 FaithLog runtime image에 현재 `./gradlew build` 산출 bootJar를 read-only mount하는 임시 compose override로 동일 docker profile QA를 수행했다. 모든 Docker 작업의 마지막 명령은 `docker builder prune -f`였고 회수 대상 build cache는 0B였다. volume/image/system prune과 `down -v`는 실행하지 않았다.
  - 영향: API 경로/요청·응답 DTO/ErrorCode/status, access 1,800초, refresh 1,209,600초, logout/withdrawal/role/FCM 의미, Controller/Entity, DB/Flyway, dependency 변경은 0건이다. raw access/refresh token Redis 저장과 문서/로그 출력도 0건이다.
  - 이력서 문장 후보: `단일 Redis Lua rotate-or-revoke CAS로 Refresh Token Rotation을 원자화해 동일 credential 병렬 요청을 2개 성공에서 정확히 1개 성공/1개 401로 차단하고, mismatch 감지와 session-scoped access·refresh 폐기 사이 경합 창을 제거해 380개 테스트와 실제 Redis/Docker HTTP QA로 검증했다.`

- #159 캠퍼스 격리와 IDOR/BOLA 읽기 전용 보안 감사:
  - 최신 `origin/develop` `5b078b5f` 기준으로 21개 Controller의 80개 endpoint, 21개 객체 식별자 범주, 56개 authorization service/policy/support 파일, 25개 repository 파일을 Controller → parent/tenant/owner guard → repository predicate 순서로 다시 대조했다. PM 리뷰에서 기존 문서가 누락한 penalty `ruleId`, global coffee catalog `brandId`/`menuId`, campus `inviteCode`, devotion `recordDate` 4개 범주를 보강했다. pageable/filter/sort/keyword 경로의 campus predicate, service/campus role, COFFEE duty, 본인 principal 고정, 익명 Poll identity 숨김, Prayer read/write 분리, Billing account-owner, FCM owner, Notification campus scope를 포함했다. #176은 JWT/Refresh Redis 경계만 변경했으며 #159 finding으로 중복 집계하지 않았다.
  - confirmed finding은 Critical 0 / High 0 / Medium 1 / Low 0이다. active COFFEE duty 사용자가 persisted non-COFFEE template을 대상으로 request body의 `paymentCategory=COFFEE`를 권한 분기에 주입하면 update guard를 통과해 제목·선택 방식·옵션·자동 생성 스케줄을 변경할 수 있는 BFLA를 정적 코드 추적 2회로 검증해 신뢰도 10/10으로 확정했다. 최소 확정 영향은 같은 캠퍼스 비-COFFEE template 무단 변경이며, 자동 생성 활성 시 후속 Poll 전파가 조건부 최대 영향이다. `pollType=COFFEE`가 필요한 정산 때문에 청구 생성 영향은 확정 범위에서 제외했다.
  - false positive/의도 정책 8개와 운영·정책 미확인 4개를 중복 없이 분리했다. #157 F-157-01과 #158 F-158-01/#176 수정은 중복 finding으로 집계하지 않았고, 후속 수정 Issue도 생성하지 않았다.
  - 최신 develop에서 기존 focused test 13 classes를 재실행해 172 tests / 0 failures / 0 errors / 0 skipped, `BUILD SUCCESSFUL`을 확인했다. 실제 secret/token/개인정보/계좌번호/기도제목 값 출력·기록, production/test/config/DB/Flyway 수정, Docker, push, PR은 모두 0건이다.
  - 이력서 문장 후보: `21개 Controller·80개 endpoint의 21개 객체 식별자를 56개 권한 경계와 25개 repository predicate까지 추적하고 172개 focused test로 검증해, COFFEE duty가 request body로 비-COFFEE template 권한을 재분류하는 BFLA 1건을 신뢰도 10/10으로 식별했다.`

- #158 JWT와 세션 수명주기 읽기 전용 보안 감사:
  - 최신 `origin/develop` `634d19c7` 기준 production/config/schema 47개 파일, focused test 8개 파일, 인증·role·FCM API 10개, Redis 인증 흐름 5개를 JWT signature/type/expiration/JTI, refresh allowlist/rotation, access blacklist, `tokenVersion`, logout/탈퇴/FCM cleanup 기준으로 대조했다.
  - confirmed finding은 Critical 0 / High 0 / Medium 1 / Low 0이다. Redis의 refresh current JTI 확인(GET)과 새 JTI 저장(SET)이 분리돼 동일 old refresh의 동시 요청이 모두 성공하고 서로 다른 access token이 기본 최대 1,800초 유효해질 수 있는 replay 경로를 독립 검증 포함 신뢰도 10/10으로 확정했다. 공격자 SET이 current winner이면 refresh가 14일 TTL로 남고 차단 전 성공적인 후속 회전마다 TTL이 다시 14일로 설정돼 sliding session persistence가 가능하다. 정상 client의 stale refresh mismatch, logout, 회원탈퇴가 종료 조건이며 `tokenVersion` 변경은 기존 access만 무효화한다. 수정 Issue는 생성하지 않았다.
  - access 30분, refresh 14일, access blacklist 남은 수명+60초, refresh allowlist 만료까지의 2개 Redis key TTL을 확인했다. service/campus role 변경은 `tokenVersion` 증가로 기존 access를 즉시 401 처리하고 refresh는 최신 role/version으로 회전하며, 회원탈퇴는 active=false/version 증가·refresh 전체 삭제·현재 access blacklist·FCM/membership 전체 비활성화를 수행한다.
  - logout은 현재 승인 정책대로 제시 access JTI 1개와 current refresh session만 폐기한다. refresh 전후 access가 겹치면 과거 access가 자체 만료까지 남는 동작은 제품 정책 재확인 항목으로 분리했으며, #157 F-157-01은 중복 finding 없이 탈퇴 후 session cleanup 영향만 참조했다.
  - focused 검증은 2개 Gradle 명령으로 8 classes, 합계 33 tests / 0 failures / 0 errors / 0 skipped를 확인했다. 실제 secret/token 값 출력·기록, production/test/config/DB/Flyway 수정, Docker, push, PR은 모두 0건이다.

- #157 위협 모델과 권한 행렬 읽기 전용 보안 감사:
  - 최신 `origin/develop` `d9d9f250` 기준으로 21개 Controller의 80개 endpoint를 전수 인벤토리화했고, `SecurityConfig`의 application `permitAll` 4개와 authenticated endpoint 76개를 service role, campus role, COFFEE duty, tenant/owner guard에 대조했다.
  - 모바일 앱→Cloud Run→Supabase PostgreSQL/Upstash Redis/Firebase FCM과 내부 Scheduler를 7개 신뢰 경계로 모델링하고, 개인정보·password hash·JWT/refresh identifier·계좌·경건·기도·익명 투표·FCM을 11개 보호 자산 범주, path/body/token 식별자를 18개 공격 표면 범주로 분류했다.
  - confirmed finding은 Critical 0 / High 0 / Medium 1 / Low 0이다. 마지막 active service ADMIN 역할 강등은 차단하지만 본인 탈퇴에는 동일 guard가 없어 관리자 control plane이 잠길 수 있는 경로를 코드와 독립 검증으로 확정했다(신뢰도 10/10). 코드 수정과 후속 Issue 생성은 하지 않았다.
  - 실제 key 형식의 현재 tree/좁은 history 후보 0건, user-controlled outbound URL/webhook/file upload/WebSocket/LLM surface 0건을 확인했다. Docker non-root, GitHub Actions SHA pin, repository secret scanner는 LOW hardening 후보로 분리했으며 운영 콘솔 12개 항목은 미확인 체크리스트로 남겼다.
  - focused 검증은 탈퇴·마지막 ADMIN 강등·role token invalidation·refresh 재사용·FCM owner 테스트 5개 class, 19 tests / 0 failures / 0 errors / 0 skipped로 성공했다.
  - 문서-only 감사로 Docker QA는 실행하지 않았다. secret 값, 개인정보, 계좌번호, token을 출력하거나 문서화하지 않았고 production/test/config/DB/Flyway/운영 인프라는 변경하지 않았다.

- #156 User와 Auth 유스케이스 책임 분리:
  - 작업 기준: Issue #156 `[Refactor] 10 User와 Auth 유스케이스 책임 분리`, 브랜치 `chore/156-user-auth-usecase-separation`, 별도 Codex worktree, 최신 `origin/develop` `c5286cd` 기준.
  - TDD 증거: production 수정 전 signup/login/refresh/logout/users-me/withdrawal의 전용 transaction, Controller 직접 연결, thin facade, token/campus/session/soft-delete support와 FCM port, 순환 의존 및 Redis/JWT library/BCrypt 구현 누출 금지를 검사하는 구조 테스트 6건을 추가했다. 최초 실행은 전용 경계 부재로 `6 tests / 5 failures` RED였고 책임 이동 후 모두 GREEN이 됐다. 변경 전 auth/user·role invalidation·FCM focused baseline도 성공했다.
  - 책임 분리: 6개 public use case를 `SignupCommandService`, `LoginCommandService`, `RefreshTokenRotationService`, `LogoutCommandService`, `UserMeQueryService`, `AccountWithdrawalCommandService`로 분리했다. `AuthTokenIssuanceSupport`가 JWT provider 발급과 hashed identifier allowlist 저장, `CampusMembershipQuerySupport`가 ACTIVE membership 결과 조립, `UserSessionRevocationSupport`가 logout/withdrawal의 기존 blacklist·allowlist 순서와 TTL, `AccountSoftDeletionSupport`가 membership 비활성화와 익명화 soft-delete를 담당한다. Withdrawal 전체 FCM 비활성화는 신규 application port를 통해 기존 `FcmTokenCommandService`에 연결했다.
  - facade 정량 변화: `AuthService`는 188→55줄(-133, -70.7%), `UserAccountService`는 103→19줄(-84, -81.6%)의 repository/transaction/BusinessException/business-rule-free compatibility delegate로 축소했다. 신규 public use case service 6개, package-private support 4개, FCM application port 1개, 구조 테스트 source 1개를 추가했다. 전체 test source는 74개, 전체 Java source/test는 583개다. 이 수치는 추출 class를 포함한 전체 코드 감소가 아니라 facade 책임 축소 수치다.
  - 정책 보존: signup 중복/BCrypt/USER·active 기본값, login 자격·inactive/deleted 차단, access 30분/refresh 14일과 정확한 claims, raw refresh 미저장, rotation sessionId/재사용 거절/최신 role·tokenVersion, logout access blacklist/refresh 제거/optional FCM, users/me ACTIVE 다중 campus, withdrawal 검증·순서·익명화·tokenVersion/session/FCM, 삭제 사용자 접근 차단을 유지했다. API/DTO/HTTP/ErrorCode/message, Entity/DB/Flyway, JWT provider/filter/security config, Redis adapter/key/TTL/hash/allowlist/blacklist, repository query, dependency 변경은 0건이다.
  - 검증: auth/user·role token invalidation·notification FCM focused 성공, 전체 `./gradlew test` `374 tests / 0 failures / 0 errors / 1 skipped`, `./gradlew build`와 `./gradlew asciidoctor` 성공, `git diff --check` 성공. Swagger annotation 추가, Controller Entity 반환, application service RedisTemplate/JWT builder/BCrypt 구현 직접 의존, service cycle은 모두 0건이다.
  - 격리 Docker QA: `faithlog-qa-156-user-auth-20260712` project에서 PostgreSQL/Redis `healthy`, app `/api/v1/health` `200 + data.status=UP`을 확인했다. `signup 201 -> login 200 -> users/me 200 -> refresh rotation 200 -> 기존 refresh 재사용 401 -> logout 200 -> logout access 401 -> rotated refresh 401`과 별도 계정의 `signup 201 -> login 200 -> 회원 탈퇴 200 -> 기존 access 401 -> 재로그인 401(AUTH_INVALID_CREDENTIALS)` 흐름이 모두 통과했다. 같은 project를 volume 삭제 없이 `docker compose down`해 컨테이너와 network만 제거했다.
  - Docker build/cache: daemon 미실행으로 첫 QA 진입이 실패해 Docker Desktop을 시작했다. 이후 첫 image build는 stale Alpine cache의 `xargs: echo: Exec format error`로 실패했고 허용된 `docker builder prune -f`로 1.877MB를 회수한 뒤 한 번 재시도해 성공했다. 시작 시 Build Cache는 1.617GB(회수 가능 367B), 최종 정리에서 696.5MB 회수 후 Build Cache는 1.748GB/회수 가능 0B였으며, 모든 Docker 작업의 마지막 명령 `docker builder prune -f`는 추가 회수 0B로 끝났다. system/image/volume prune, `down -v`, named volume 삭제는 실행하지 않았다.
  - 보안 후속 감사: 기존 회원 탈퇴에는 마지막 active service ADMIN 보호가 없다. 위치는 withdrawal application flow이며 영향은 마지막 ADMIN 탈퇴 시 서비스 관리자 기능의 운영 잠금 가능성이다. 리팩터링의 동작 변경 금지에 따라 수정하지 않았고 후속 보안 감사 항목(MEDIUM)으로만 기록했다.
  - 환경/도구 제약: Issue #156의 `projectItems`가 비어 Project 상태를 변경할 수 없었다. `pm-dev`는 비활성 보관 경로에만 있고 저장소 `.harness`, 활성 `pm-harness` canonical script가 없어 임의 생성 없이 FaithLog TDD gate를 적용했다. push/PR은 금지 지시에 따라 수행하지 않았다.
  - 이력서 문장 후보: `Auth/User의 6개 공개 유스케이스를 전용 Service와 4개 응집 support로 분리해 188줄/103줄 통합 Service를 55줄/19줄 호환 facade로 70.7%/81.6% 축소하고, 6개 구조 게이트·374개 전체 테스트·격리 Docker API QA로 JWT·Redis rotation·FCM·soft-delete 계약 무변경을 검증했다.`

### 2026-07-11

- #155 Batch와 Scheduler 책임 분리:
  - 작업 기준: Issue #155 `[Refactor] 09 Batch와 Scheduler 책임 분리`, 브랜치 `chore/155-batch-scheduler-usecase-separation`, 별도 Codex worktree, 최신 `origin/develop` `66e8d7c` 기준.
  - TDD 증거: production 수정 전 scheduler trigger, Poll create/close/settlement, 세 자동 알림, FCM cleanup, retention, PENDING recovery의 전용 경계와 transaction/SDK 누출/순환 의존을 검사하는 구조 테스트 5건을 추가해 `5 tests / 5 failures` RED를 확인했다. 구현 후 신규 구조 테스트와 #152/#154 구조 회귀가 GREEN이 됐고, scheduler disabled Context 및 커피 due close 재실행 시 정산 row 정확히 1건 characterization을 추가했다.
  - 책임 분리: `ScheduledPollCreationService`가 active+auto template 탐색, `ScheduledPollWindow`, Redis lock, `TransactionTemplate`, `ScheduledPollFactory` 호출을 소유한다. `DueCoffeePollClosureService`가 due OPEN COFFEE 조회, poll lock, CLOSED 전환 후 `CoffeePollSettlementCommandService` 호출을 같은 transaction에서 소유한다. `DevotionMissingNotificationService`, `PollMissingNotificationService`, `PaymentUnpaidNotificationService`는 각 대상·scope·lock orchestration을 소유하고 `NotificationRequestCommandService`의 PENDING/SKIPPED·dedup·dispatch 경계에 연결된다. `FcmTokenCleanupService`는 90일 stale cutoff와 write transaction을 직접 소유한다.
  - Scheduler/facade 정량 변화: `FaithLogScheduledJobs`는 기존 8개 cron/fixedDelay trigger에서 전용 job service만 호출한다. `PollAutomationService`는 121→29줄(-92, -76.0%), `AutomaticNotificationService`는 296→34줄(-262, -88.5%)의 repository/transaction/lock/business-rule-free compatibility delegate로 축소했다. 신규 전용 Poll/notification job service는 5개, 신규 test source는 2개이며 전체 test source는 73개, 전체 Java source/test는 571개다. 이 수치는 추출 class를 포함한 전체 코드 감소가 아니라 facade 책임 축소 수치다.
  - 정책 보존: Asia/Seoul, 8개 scheduler property/cron/fixedDelay와 enable flag, due/template/week 중복 방지, template/option snapshot, 자동 생성 OPEN, due coffee CLOSED→정산 순서와 멱등성, 월요일부터 11시 경건 미제출, 5/3/2/1시간 투표 미응답과 CUSTOM, 12시 미납, Redis lock/dedup/fail-closed/manual 분리, 90일 stale FCM, 10분 PENDING 1회 recovery 후 FAILED, 04:30 retention과 14일/30일/1년/2월 1일 정책 및 삭제 순서를 유지했다. API/DTO/HTTP/ErrorCode/auth, Entity/DB/Flyway/repository query, config, TTL/retry/retention, dependency 변경 0건이다.
  - 검증: Batch focused 성공, Batch/Notification/Poll/Billing/Devotion/User 연결 조합 `283 tests / 0 failures / 0 errors / 0 skipped`, 전체 `./gradlew test` `368 tests / 0 failures / 0 errors / 1 skipped`, `./gradlew build`와 `./gradlew asciidoctor` 성공, `git diff --check` 성공. Swagger annotation 추가, Controller Entity 반환, batch/scheduler RedisTemplate/Firebase SDK 직접 의존, 서비스 순환 의존은 모두 0건이다.
  - 환경/도구 제약: GitHub token에 `read:project` scope가 없어 Issue #155 Project 카드 존재 여부와 `In Progress` 이동을 확인하지 못했다. 현재 파일시스템에 `pm-dev/SKILL.md`, 저장소 `.harness`, `harness.yaml`이 없어 임의 생성 없이 FaithLog TDD gate를 적용했다. 호스트 Data 볼륨이 100%이고 가용 공간이 1.8GiB이며 sandbox의 Docker socket 접근도 거부돼, 삭제/prune 우회 없이 Docker QA를 생략하고 원격 Docker CI가 필요하다고 판단했다. push/PR도 금지 지시에 따라 수행하지 않았다.
  - 이력서 문장 후보: `Batch/Scheduler의 Poll·자동 알림·FCM cleanup 책임을 6개 전용 use case로 분리하고 121줄/296줄 통합 Service를 29줄/34줄 호환 facade로 76.0%/88.5% 축소했으며, 5개 구조 게이트·368개 전체 테스트로 스케줄·정산·Redis fail-closed·retention 정책 무변경을 보장했다.`

- #154 Notification 발송과 FCM 책임 분리:
  - 작업 기준: Issue #154 `[Refactor] 08 Notification 발송과 FCM 책임 분리`, 브랜치 `chore/154-notification-fcm-usecase-separation`, 별도 Codex worktree, 최신 `origin/develop` `467bf1c` 기준.
  - TDD 증거: production 수정 전 notification service/controller/Redis·FCM adapter, 자동 알림/recovery/cleanup batch, logout 연결 focused 기준선을 실행해 GREEN을 확인했다. token command, notification request command, delivery worker, log query의 transaction/async 책임, Controller/batch 직접 연결, thin facade, 순환 의존 방지, Redis/Firebase SDK 누출 방지를 요구하는 구조 테스트를 추가했고 최초 6 tests 중 5 failures RED를 확인한 뒤 책임 이동으로 7 tests GREEN을 만들었다.
  - 책임 분리: `FcmTokenCommandService`가 idempotent upsert, 동일 client token 교체, token ownership 이전, 사용자 요청 비활성화, logout current-device port, 90일 stale cleanup의 네 public write transaction을 소유한다. `NotificationRequestCommandService`가 관리자 요청 권한/대상 계산/manual lock과 자동 business dedup, PENDING/SKIPPED log 생성, after-commit dispatch를 두 public write transaction으로 소유한다. `NotificationDeliveryWorker`, `NotificationLogQueryService`, `NotificationDeduplicationService`, `NotificationLockService`의 기존 전용 경계는 유지했다.
  - 연결과 정량 변화: FCM/Admin Controller와 automatic/cleanup batch는 전용 command 서비스를 직접 호출한다. `FcmTokenService`는 105→33줄(-72, -68.6%), `NotificationService`는 205→20줄(-185, -90.2%)의 repository/transaction/BusinessException/business-rule-free compatibility delegate로 축소했고, `AutomaticNotificationService`는 358→296줄(-62, -17.3%)로 notification repository/dispatch 직접 의존을 제거했다. 신규 구조 테스트 7개를 포함해 test source는 71개, 전체 Java source/test는 563개다. 이 수치는 추출 class를 포함한 전체 코드 감소가 아니라 facade/orchestrator 책임 축소 수치다.
  - 정책 보존: API mapping/query/DTO/HTTP/ErrorCode/message, 관리자 권한, FCM active ownership/upsert/flush 순서와 logout row 삭제, DB source-of-truth, PENDING/SENT/FAILED/SKIPPED, transient retry 3회와 `1s -> 5s -> 30s`, permanent invalid-token 비활성화, stale PENDING 1회 recovery/FAILED, Redis 25시간/8일 dedup과 10분 lock/fail-closed/manual 분리를 유지했다. Entity/DB/Flyway/repository query/scheduler/config/dependency 변경 0건, Swagger annotation 추가 0건, Controller Entity 반환 0건, application service Redis/Firebase SDK 누출 0건, 서비스 순환 의존 0건을 확인했다.
  - 검증: notification service/controller/REST Docs/FCM·Redis adapter와 automatic/recovery/cleanup/logout focused 묶음 성공, 전체 `./gradlew test` 362 tests / 0 failures / 0 errors / 1 skipped(실행된 테스트 통과율 100%), `./gradlew build`와 `./gradlew asciidoctor` 성공, `git diff --check` 성공. PR/push 금지 지시로 GitHub CI는 실행하지 않았다.
  - Docker/도구 제약: 호스트 Data 볼륨이 99% 사용되고 가용 공간이 2.1GiB여서 격리 Docker image build/QA를 실행하지 않았다. system/image/volume prune, named volume 삭제, 금지된 파일 삭제로 우회하지 않았으며 원격 Docker CI가 필요하다. Issue #154의 `projectItems`가 비어 Project 카드를 `In Progress`로 변경할 수 없었다. `pm-dev`는 비활성 보관 경로에만 있고 저장소 `.harness`, `harness.yaml`, custom agents, 활성 canonical scripts가 없어 임의 생성 없이 FaithLog TDD gate를 적용했으며 `.harness` 보고서는 생성되지 않았다.
  - 이력서 문장 후보: `Notification의 FCM token command와 관리자·자동 요청 command를 분리해 105줄/205줄 통합 Service를 33줄/20줄 호환 facade로 각각 68.6%/90.2% 축소하고, 7개 구조 회귀 테스트·362개 전체 테스트로 API·DB·권한·retry·Redis fail-closed 정책 무변경을 보장했다.`

- #153 Prayer 유스케이스 책임 분리:
  - 작업 기준: Issue #153 `[Refactor] 07 Prayer 유스케이스 책임 분리`, 브랜치 `chore/153-prayer-usecase-separation`, 별도 Codex worktree, 최신 `origin/develop` `f6e3c2e` 기준.
  - TDD 증거: 기존 Prayer service/동시성/REST Docs focused 테스트를 먼저 실행해 GREEN을 확인했다. season command/query, group command/query, board query, 조별/본인 submission command의 직접 transaction, Controller 직접 연결, thin compatibility facade, 전용 서비스 간 의존 금지를 요구하는 구조 테스트 5건을 production 수정 전에 추가했고 5 tests / 5 failures RED를 확인한 뒤 책임 이동으로 GREEN을 만들었다. PM 리뷰에서는 다중 제출 서비스명을 새 이름 기준으로 먼저 바꿔 다시 5 tests / 5 failures RED를 확인하고 production rename 후 GREEN을 확인했다.
  - 책임 분리: 11개 public 유스케이스를 `PrayerSeasonCommandService`, `PrayerSeasonQueryService`, `PrayerGroupCommandService`, `PrayerGroupQueryService`, `PrayerWeekBoardQueryService`, `PrayerGroupSubmissionCommandService`, `MyPrayerSubmissionCommandService` 7개 전용 서비스로 분리했다. `PrayerGroupSubmissionCommandService`는 일반 ACTIVE 멤버의 자기 활성 조 다중 입력과 관리자 전체 조 입력을 모두 포함하는 기존 권한 범위를 정확히 표현한다. 공통 권한은 `PrayerAccessSupport`, 활성 조·조원 조회와 조 결과는 `PrayerTargetMemberSupport`, 보드 결과 조립은 `PrayerBoardAssembler`가 담당한다.
  - 호환 경계와 정량 변화: 두 Prayer Controller는 전용 서비스를 직접 호출하고 `PrayerService`는 606→90줄(-516, -85.1%)의 repository/transaction/BusinessException/business-rule-free delegate로 축소했다. 이 수치는 추출 class를 포함한 전체 코드 감소가 아니라 compatibility facade 책임 축소 수치다. 신규 구조 테스트 5개를 추가해 test source는 70개, 전체 Java source/test는 559개가 됐다.
  - 정책 보존: API mapping/request-response/HTTP/ErrorCode/message, 캠퍼스 관리자/일반 ACTIVE 멤버 권한, ACTIVE+null endDate 시즌, 과거 row 보존, 조원 전체 교체와 같은 시즌 중복 409, ACTIVE 멤버·정렬, 월요일/미래 주차, GET 무생성, nullable content, 사람별 row, optimistic version, 조별 다중 제출 all-or-nothing rollback을 유지했다. Entity/DB/Flyway/repository/의존성 변경 0건, Swagger annotation 추가 0건, Controller Entity import 0건, 서비스 순환 의존 0건을 확인했다.
  - 검증: PM 리뷰 rename 이후 Prayer focused service/동시성/REST Docs/구조 23 tests / 0 failures / 0 errors / 0 skipped, Campus 연결을 포함한 Billing/Devotion/Poll/Prayer/Batch 조합 260 tests / 0 failures / 0 errors / 0 skipped, 전체 `./gradlew test` 355 tests / 0 failures / 0 errors / 1 skipped(실행된 테스트 통과율 100%), `./gradlew build`와 `./gradlew asciidoctor` 성공, `git diff --check` 성공. GitHub CI는 PR/push 금지 지시로 실행하지 않았다.
  - 검증 환경 분리 기록: PM 세션의 독립 Gradle 실행이 같은 worktree의 `build/classes`를 동시에 갱신해 삭제/쓰기 경합 1회와 손상된 class/XML 결과 1회를 만들었다. 이는 코드 실패 집계에서 제외했다. PM 실행 중단과 worker 0개 확인 후 `./gradlew clean`으로 생성물만 정리하고 단독 `./gradlew test`와 `./gradlew build`를 각각 재실행해 위 최종 성공 수치를 확인했다.
  - Docker QA: `faithlog-qa-153-prayer` 격리 compose build 1차는 Docker BuildKit `metadata_v2.db`/`snapshots.db` I/O 오류, daemon 복구 후 2차는 호스트 가용 공간 116MiB·100% 사용 상태의 `no space left on device`로 중단됐다. 두 실행 모두 같은 project를 volume 삭제 없이 down 처리했다. Android Emulator는 범위 밖이라 종료하지 않았고 system/image/volume prune과 named volume 삭제도 실행하지 않았다. 마지막 Docker 명령은 지시된 `docker builder prune -f`였지만 가용 공간은 116MiB로 변하지 않아 image/health 검증은 외부 환경 제약으로 미완료다.
  - 도구 제약: Issue #153의 `projectItems`가 비어 있어 Project 카드 상태를 변경하지 못했다. `pm-dev`는 비활성 보관 경로에만 있고 저장소에 `harness.yaml`, `.harness`, custom agents, 활성 `dev_gate.py`가 없어 누락 파일 생성이나 품질 완화 없이 FaithLog TDD/검증 기준으로 진행했다. `.harness` 보고서는 생성되지 않았다.
  - 이력서 문장 후보: `Prayer의 11개 유스케이스를 조별 다중 제출을 포함한 7개 응집 Service와 3개 package-private support로 분리해 606줄 통합 Service를 90줄 호환 facade로 85.1% 축소하고, 5개 구조 회귀 테스트·355개 전체 테스트·260개 연관 도메인 테스트로 API·DB·권한·optimistic locking·all-or-nothing 동작 무변경을 보장했다.`

### 2026-07-10

- #152 Poll 템플릿과 커피 정산 책임 분리:
  - 작업 기준: Issue #152 `[Refactor] 06 Poll 템플릿과 커피 정산 책임 분리`, 브랜치 `chore/152-poll-template-coffee-settlement-separation`, 별도 Codex worktree, 최신 `origin/develop` `d7ae1d6` 기준.
  - TDD 증거: 변경 전 Poll/template/catalog/settlement/REST Docs/Batch focused 테스트를 먼저 실행해 GREEN을 확인했다. template command/query의 직접 transaction, Controller 직접 연결, thin compatibility facade, option snapshot support, 자동 생성 factory 위임, settlement command/support, 순환 의존 금지를 요구하는 구조 테스트 6건을 production 수정 전에 추가했고 6 tests / 6 failures RED를 확인한 뒤 책임 이동으로 GREEN을 만들었다. 자동 생성 snapshot 전체 필드와 비활성 auto template 제외 characterization도 보강했다.
  - 책임 분리: 템플릿 command 3개는 `PollTemplateCommandService`, query 2개는 `PollTemplateQueryService`가 기존 transaction을 직접 소유하고, `PollTemplateOptionSupport`가 메뉴 snapshot resolve와 option save/replace/ordered result를 응집한다. `AdminPollTemplateController`는 전용 서비스를 직접 호출하며 `PollTemplateService`는 repository/transaction/BusinessException/business-rule-free delegate다. 이미 응집된 `CoffeeCatalogService`는 변경하지 않았다.
  - 자동 생성/정산 경계: `ScheduledPollFactory`가 동일 campus/template/week 중복 확인과 template option의 Poll/PollOption snapshot 복사를 소유하고, `PollAutomationService`는 due 탐색·Asia/Seoul window·Redis lock·`TransactionTemplate`·close/settlement orchestration을 유지한다. `CoffeePollSettlementSupport`는 CLOSED/COFFEE/OPTION_PRICE/COFFEE eligibility와 duty/response/option 조회를 기존 순서로 조립하고, `CoffeePollSettlementCommandService`가 all-or-nothing transaction과 Billing port 호출을 직접 소유한다. 기존 settlement service는 수동 close와 scheduler 호출을 유지하는 thin delegate다.
  - 정량 변화: `PollTemplateService` 218→42줄(-176, -80.7%), `PollAutomationService` 207→121줄(-86, -41.5%), `CoffeePollSettlementService` 130→17줄(-113, -86.9%)로 orchestration/facade 책임을 축소했다. 템플릿 5개 public use case는 2개 전용 Service에 분리됐고, 신규 구조 테스트는 6개다. 전체 Java source/test 파일은 548개, test file은 69개다. 추출된 전용 class의 추가 줄 수를 제외한 facade/orchestrator 감소 수치이며 전체 코드 감소로 해석하지 않는다.
  - 정책 보존: API mapping/query/DTO/HTTP/ErrorCode/message, campus scope 은닉, 관리자/커피 담당자와 본인 active COFFEE 계좌, template 설정·sortOrder·menu code/name/price snapshot, active/due/주차 중복, lock key/fail-closed, scheduler 설정, settlement eligibility/source/snapshot/dueDate, UNPAID 멱등성, terminal 보호, 한 poll rollback을 그대로 유지했다. Entity/DB/Flyway/repository/의존성 변경 0건, Swagger annotation 추가 0건, Controller Entity import 0건, 서비스 순환 의존 0건을 확인했다.
  - 검증: Poll/template/catalog/settlement/REST Docs/Batch focused 62 tests / 0 failures, #165 원본 Billing/Devotion/Poll/Batch 4-domain 조합 204 tests / 0 failures / 0 errors / 0 skipped, 전체 `./gradlew test` 350 tests / 0 failures / 0 errors / 1 skipped(실행된 테스트 통과율 100%), `./gradlew build`와 `./gradlew asciidoctor` 성공, `git diff --check` 성공. GitHub CI는 PR/push 금지 지시로 실행하지 않았다.
  - Docker QA: `faithlog-qa-152-poll` 격리 compose project에서 clean app image build, PostgreSQL/Redis `healthy`, backend `/api/v1/health`의 `data.status=UP`, 동일 project의 volume 삭제 없는 compose down을 확인했다. 마지막 Docker 명령으로 `docker builder prune -f`만 실행해 미사용 build cache 696.2MB를 정리했다.
  - 도구 제약: Issue #152의 `projectItems`가 비어 있어 Project 카드 상태를 변경하지 못했다. 비활성 보관된 `pm-dev` 원문의 `dev_gate.py`는 `harness.yaml`, `.harness` 기획/정책, custom agent 설정 부재로 실패했고 PM 승인에 따라 저장소 규칙을 실제 gate로 사용했다. `score_code.py`는 critical/security finding 0건의 보고서를 생성했지만 specialist/overall score가 없어 `passed=false`; `review_gate.py`도 Harness quality/TDD 파일 부재로 실패했다. 생성된 `.harness/` 보고서는 untracked로 보존했고 커밋하거나 삭제하지 않았다.
  - 이력서 문장 후보: `Poll template의 5개 command/query와 자동 생성·커피 정산 책임을 전용 Service/Support/Factory로 분리해 기존 통합 Service를 최대 86.9% 축소하고, 6개 구조 회귀 테스트·350개 전체 테스트·204개 4-domain 테스트·격리 Docker health 검증으로 API·DB·권한·스케줄·정산 동작 무변경을 보장했다.`

- #151 Poll 핵심 유스케이스 책임 분리:
  - 작업 기준: Issue #151 `[Refactor] 05 Poll 핵심 유스케이스 책임 분리`, 브랜치 `chore/151-poll-core-usecase-separation`, 별도 Codex worktree, 최신 `origin/develop` `0d099ed` 기준.
  - TDD 증거: 기존 Poll service/REST Docs/Batch focused 테스트를 먼저 실행해 GREEN을 확인했다. 전용 서비스별 public 책임과 직접 transaction, Controller 직접 연결, 호환 facade의 repository/transaction/business-rule 미소유, 전용 서비스 간 의존 금지를 요구하는 구조 테스트 5건을 production 수정 전에 추가했고 5 tests / 5 failures RED를 확인한 뒤 책임 이동으로 GREEN을 만들었다.
  - 책임 분리: 생성은 `PollCreationCommandService`, 관리자 종료는 `PollStatusCommandService`, 응답과 `optionIds` 검증은 `PollResponseCommandService`, 목록/상세는 `PollQueryService`, 결과/미응답자는 `PollResultQueryService`, 댓글 command/query는 `PollCommentCommandService`/`PollCommentQueryService`, 사용자 선택지 추가는 `PollUserOptionCommandService`가 소유한다. 13개 public 유스케이스가 8개 전용 서비스에서 기존 write/read-only transaction 경계를 직접 소유한다.
  - 호환·공통 경계: 두 Poll Controller는 전용 서비스를 직접 호출한다. `PollService`는 578줄에서 103줄의 repository/transaction/`BusinessException`/business-rule-free delegate로 축소했다. 여러 유스케이스가 실제로 공유하는 현재 시간 OPEN 동기화·공개기간, campus-scoped lookup, 결과 조립만 package-private `PollStatusSynchronizer`, `PollLookupSupport`, `PollResultAssembler`로 추출했고 전용 서비스끼리의 의존은 만들지 않았다. `PollTemplateService`, `CoffeePollSettlementService`, Batch 책임은 변경하지 않았다.
  - 정책 보존: API mapping/query/DTO/HTTP/ErrorCode/message, ACTIVE campus scope와 관리자/커피 담당자 권한, `SCHEDULED -> OPEN`, 3일/7일 공개기간, close와 coffee settlement의 한 transaction side effect, SINGLE/MULTIPLE·중복·타 poll option 검증 순서, 같은 선택지 재저장, 익명/비익명 결과, 미응답자, 댓글 소유권/마감, 일반·커피 사용자 옵션 snapshot, 응답 직후 COFFEE charge 미생성, repository bulk 조회 의미를 그대로 유지했다. Entity/DB/Flyway/의존성 변경 0건, Swagger 문서 annotation 0건, Controller Entity 직접 반환 0건을 확인했다.
  - 검증: Poll focused service/Controller/REST Docs/Batch와 구조 테스트 성공, #165 원본 Billing/Devotion/Poll/Batch 조합 성공(197 tests / 0 failures / 0 errors / 0 skipped), 전체 `./gradlew test` 성공(343 tests / 0 failures / 0 errors / 1 skipped), `./gradlew build` 성공, `./gradlew asciidoctor` 성공, `git diff --check` 성공. 첫 전체 실행에서 새 helper의 `*Policy` 이름이 패키지 구조 검사 1건을 실패시켜 `PollLookupSupport`로 이름을 바로잡은 뒤 전체 GREEN을 확인했다.
  - Docker QA: `faithlog-qa-151-poll` 격리 compose project에서 clean app image build, PostgreSQL/Redis `healthy`, backend `/api/v1/health`의 `data.status=UP`, 동일 project의 volume 삭제 없는 compose down을 확인했다. 마지막 Docker 명령으로 `docker builder prune -f`만 실행해 미사용 build cache 696.1MB를 정리했다.
  - 도구 제약: GitHub Issue #151의 `projectItems`는 비어 있었고 CLI token에 `read:project` scope가 없어 Project 카드 조회/연결/`In Progress` 변경을 수행하지 못했다. 비활성 보관된 `pm-dev` 원문에 따라 `dev_gate.py`를 실행했지만 저장소에 `harness.yaml`, `.harness` 기획/정책 파일, custom agent 설정이 없어 실패했다. `score_code.py`는 critical/security finding 0건의 `.harness/reports/review-score.*`를 생성했지만 specialist와 overall score가 없어 `passed=false`였고, `review_gate.py`도 quality/TDD harness 파일과 evidence 부재로 실패했다. 생성된 `.harness/` 보고서는 승인 없이 커밋하거나 삭제하지 않고 untracked로 보존했다.
  - 이력서 문장 후보: `Poll의 13개 유스케이스를 8개 응집 Service로 분리하고 578줄 호환 facade를 103줄 delegate로 축소했으며, 5개 구조 회귀 테스트·343개 전체 테스트·격리 Docker health 검증으로 API·DB·권한·익명성·정산 동작 무변경을 보장했다.`

- #150 Devotion 유스케이스 책임 분리:
  - 작업 기준: Issue #150 `[Refactor] 04 Devotion 유스케이스 책임 분리`, 브랜치 `chore/150-devotion-usecase-separation`, 별도 Codex worktree, `origin/develop` `f55f16c` 기준.
  - TDD 증거: 기존 Devotion focused characterization을 먼저 실행해 GREEN을 확인했다. 전용 서비스별 public 유스케이스, 직접 write/read-only 트랜잭션, Controller 직접 연결, 호환 facade의 repository/transaction/business-rule 미소유, 전용 서비스 간 순환 의존 금지를 요구하는 구조 테스트 5건을 production 수정 전에 추가했고 5 tests / 5 failures RED를 확인한 뒤 책임 이동으로 GREEN을 만들었다.
  - 책임 분리: 일별 저장은 `DailyDevotionCommandService`, 주간 draft/final submit과 벌금 계산·Billing port orchestration은 `WeeklyDevotionCommandService`, 본인 주간 조회는 `MyWeeklyDevotionQueryService`, 관리자 미제출 조회는 `MissingDevotionMemberQueryService`가 소유한다. 기존 월간 경계는 `DevotionMonthlySummaryQueryService`에 유지하고, 벌금 규칙 create/update는 `PenaltyRuleCommandService`, 목록은 `PenaltyRuleQueryService`로 분리했다. 8개 public 유스케이스가 7개 전용 서비스에서 기존 트랜잭션 경계를 직접 소유한다.
  - 호환 경계: 네 Devotion/PenaltyRule Controller는 전용 서비스를 직접 호출한다. `DevotionService`는 325줄에서 48줄, `PenaltyRuleService`는 130줄에서 34줄의 repository/transaction/BusinessException/business-rule-free delegate로 축소했다. 공통 helper Service를 추가하지 않고 각 유스케이스 서비스가 기존 private helper와 검증·호출 순서를 소유해 서비스 간 의존을 만들지 않았다.
  - 정책 보존: daily의 제출 완료 거부와 weekly row/daily upsert, weekly 검증 순서·7일 row·draft/final submit·재제출 거부, 0원 charge/account skip, 양수 벌금 source/snapshot/dueDate 계약과 all-or-nothing rollback, 빈/부분 주간 7일 합성, 관리자 미제출과 권한, 월 경계 집계, penalty rule 권한·campus pessimistic lock·active 교체 순서를 그대로 유지했다. API mapping/DTO/HTTP/ErrorCode/message, Entity, DB/Flyway, 의존성 변경 0건, Swagger 문서 annotation 0건, Controller Entity 직접 반환 0건, 서비스 순환 의존 0건을 확인했다.
  - 검증: Devotion focused service/Controller/REST Docs와 구조 테스트 성공, #165 원래 Billing/Devotion/Poll/Batch 4-domain 조합 성공, 전체 `./gradlew test` 성공(338 tests / 0 failures / 0 errors / 1 skipped), `./gradlew build` 성공, `./gradlew asciidoctor` 성공, `git diff --check` 성공. `faithlog-qa-150-devotion` 격리 compose project에서 clean image build, PostgreSQL/Redis `healthy`, backend `/api/v1/health`의 `data.status=UP`, volume 삭제 없는 compose down을 확인했고 마지막 Docker 명령으로 `docker builder prune -f`만 실행해 미사용 build cache 699.6MB를 정리했다.
  - 도구 제약: GitHub Issue #150은 Project 연결이 비어 있었고 CLI token에 `read:project` scope가 없어 카드 조회/연결/`In Progress` 변경을 수행하지 못했다. 비활성 보관된 `pm-dev` 원문에 따라 `dev_gate.py`를 실행했지만 저장소에 `harness.yaml`, `.harness` 기획/정책 파일, custom agent 설정이 없어 실패했으며 #150 범위 밖 harness 파일은 생성하지 않았다. `score_code.py`는 critical/security finding 0건의 `.harness/reports/review-score.*`를 생성했지만 specialist와 overall score가 없어 `passed=false`였고, `review_gate.py`도 공통 quality/TDD harness 파일과 evidence 부재로 실패했다. 생성된 `.harness/` 보고서는 승인 없이 커밋하거나 삭제하지 않고 untracked로 보존했다.
  - 이력서 문장 후보: `Devotion의 8개 유스케이스를 7개 응집 Service로 분리하고 repository-free 호환 facade와 5개 구조 회귀 테스트를 도입해, 338개 전체 테스트·4-domain 회귀·격리 Docker health 검증으로 API·DB·권한·벌금 동작 무변경을 보장했다.`

- #165 Devotion 테스트 순서 오염과 Context 격리:
  - 수정 전 재현: 최신 `origin/develop` `0cf7c5f`에서 Billing/Devotion/Poll/Batch 4-domain 명령을 실행해 187 tests / 10 failures를 확인했다. 실패는 모두 `DevotionServiceTest`였고 대표 오염값은 charge count `expected 0, actual 7`, `expected 1, actual 8`, daily check count `expected 0, actual 66`이었다. 같은 `DevotionServiceTest` 단독 실행은 성공했다.
  - 최소 원인: `BillingQueryServiceTest`가 일반 service Context를 먼저 캐시하고, 별도 REST Docs Context의 `DevotionApiRestDocsTest`가 고정 이름 H2에 29 daily rows와 3 charge rows를 커밋한 뒤, `DevotionServiceTest`가 첫 Context를 재사용하는 41-test 조합에서 동일 10 failures를 재현했다. 서로 다른 Spring Context가 `jdbc:h2:mem:faithlog-test` 하나를 공유한 것이 원인이었다.
  - TDD/수정: 고정 H2 URL을 거부하고 `${random.uuid}` 기반 이름을 요구하는 `TestDatabaseIsolationConfigTest`를 먼저 추가해 1 test / 1 failure RED를 확인했다. `application-test.yml`의 H2 이름만 Context별 고유값으로 바꾸고 PostgreSQL compatibility option과 `create-drop` 정책은 유지했다. class-wide `@DirtiesContext`, repository cleanup, assertion 완화, 테스트 삭제/비활성화는 사용하지 않았다.
  - 검증: 최소 3클래스 조합 41 tests / 0 failures, 원래 4-domain 명령 187 tests / 0 failures, `--rerun-tasks` 강제 반복 187 tests / 0 failures, 단독 `DevotionServiceTest` 성공, 전체 `./gradlew test` 333 tests / 0 failures / 0 errors / 1 skipped, `./gradlew build` 성공. Production 파일, API/DTO/ErrorCode/Entity/DB/Flyway 변경은 0건이다.
  - 이력서 문장 후보: `고정 이름 H2를 공유하던 Spring Test Context 간 fixture 누수를 최소 41-test 순서 조합으로 재현하고 Context별 DB 격리와 구조 회귀 테스트를 도입해, 187개 연관 테스트의 10건 순서 의존 실패를 0건으로 제거하고 333개 전체 테스트를 안정화했다.`

- #149 Billing 조회와 집계 책임 분리:
  - 작업 기준: Issue #149 `[Refactor] 03 Billing 조회와 집계 책임 분리`, 브랜치 `chore/149-billing-query-aggregation-separation`, 별도 Codex worktree, 최신 `origin/develop` `f2b6660` 기준.
  - TDD 증거: 본인 청구 목록의 `paymentCategory + status + page + size + sort` 조합 characterization을 먼저 추가해 기존 구현에서 GREEN을 확인했다. 이어 세 전용 Query Service, 각 public read-only 트랜잭션, Controller/Devotion adapter 직접 연결, 두 호환 façade의 무규칙성, 서비스 순환 의존 금지를 요구하는 구조 테스트 5건을 실행해 5 failures RED를 확인한 뒤 최소 책임 이동으로 GREEN을 만들었다.
  - 책임 분리: 본인 목록/요약 2개를 `MyChargeQueryService`, 관리자 캠퍼스/내 계좌 집계와 회원 상세 3개를 `AdminChargeQueryService`, 회원 계좌 목록·관리자 계좌 목록 두 overload·활성 PENALTY 계좌 검증 4개를 `PaymentAccountQueryService`로 분리했다. 9개 public 조회 surface가 각각 기존 `@Transactional(readOnly = true)` 경계를 직접 소유한다.
  - 호환 경계: 두 Billing Controller와 Devotion Billing adapter는 전용 Query Service로 직접 연결했다. `BillingQueryService`는 3개 전용 서비스만 delegate하는 repository/transaction/BusinessException/business-rule-free façade로 축소했고, #148 `BillingService`도 legacy 계좌 조회를 `PaymentAccountQueryService`에 직접 delegate해 기존 무규칙 façade 조건을 유지했다. 공통 helper Service를 새로 만들지 않고 각 유스케이스 서비스가 기존 private helper와 호출 순서를 소유해 서비스 간 의존을 추가하지 않았다.
  - 정책 보존: 본인 status/category/page/size/sort와 월별 paidAt/createdAt 기준, 관리자 summary/members/detail 조립과 정렬, `paymentAccountId`, my-accounts PENALTY 공유·COFFEE owner 범위, 일반 MEMBER/캠퍼스 관리자/전역 ADMIN/활성 COFFEE 담당자 권한, active/inactive/soft-delete 계좌 필터, ErrorCode와 사용자 메시지, repository query 의미를 그대로 유지했다. API mapping/query parameter/request-response DTO/HTTP status 변경 0건, Billing Entity·DB·Flyway·의존성 변경 0건, Swagger 문서 annotation 0건, Controller Entity 직접 반환 0건, 서비스 순환 의존 0건을 확인했다.
  - 검증: Billing service/query/구조 focused 테스트, Billing Controller/REST Docs, Devotion/Poll/Batch 연결 테스트를 분리 실행해 성공했다. 최종 `./gradlew test` 성공(332 tests / 0 failures / 0 errors / 1 skipped), `./gradlew build` 성공, `./gradlew asciidoctor` 성공, `git diff --check` 성공. Devotion/Poll/Batch 88-test 묶음에서 기존 `DevotionServiceTest` 10건 순서 오염이 재현됐고, 깨끗한 `origin/develop`도 동일 테스트·동일 실제값으로 실패했으며 `DevotionServiceTest` 단독은 성공했다. #149 신규 오염이 아니므로 테스트를 수정하거나 성과로 집계하지 않았다.
  - Docker QA: `faithlog-qa-149-billing-query` 격리 compose project에서 clean app image build, PostgreSQL/Redis `healthy`, backend `/api/v1/health`의 `data.status=UP`, 동일 project compose down까지 성공했다. 기존 중지 컨테이너와 named volume은 건드리지 않았고 volume 삭제 없이 종료했다. 마지막 Docker 명령으로 `docker builder prune -f`만 실행해 미사용 build cache 695.8MB를 정리했으며 `docker system prune`, volume/image prune, named volume 삭제는 실행하지 않았다.
  - 도구 제약: GitHub Issue #149의 `projectItems`는 비어 있었고 CLI token에 `read:project` scope가 없어 Project 카드 상태를 조회하거나 변경하지 못했다. `pm-dev` dev gate는 `harness.yaml`, `.harness` 정책/기획 파일, custom agent 설정 부재로 실패했다. `score_code.py`는 critical/security finding 0건의 보고서를 생성했지만 specialist와 overall score가 없어 `passed=false`였고, `review_gate.py`도 공통 harness/quality/TDD evidence 부재로 실패했다. 생성된 `.harness/` 보고서는 승인 없이 커밋하거나 삭제하지 않고 untracked로 보존했다.
  - 이력서 문장 후보: `Billing의 9개 조회·집계 surface를 본인 청구·관리자 집계·계좌 조회 3개 응집 Query Service로 분리하고, 5개 구조 회귀 테스트와 332개 전체 테스트·격리 Docker health 검증으로 API·DB·권한·집계 동작 무변경을 보장했다.`

- #148 Billing 계좌와 청구 명령 책임 분리:
  - 작업 기준: Issue #148 `[Refactor] 02 Billing 계좌와 청구 명령 책임 분리`, 브랜치 `chore/148-billing-command-usecase-separation`, 별도 Codex worktree, 최신 `origin/develop` `0c9fd62` 기준.
  - TDD 증거: 신규 계좌 insert 실패 시 기존 PENALTY 계좌의 deactivate + flush가 함께 rollback되는 테스트와 COFFEE 청구의 UNPAID 갱신/terminal 보존 characterization 테스트를 먼저 추가해 기존 구현에서 2건 GREEN을 확인했다. 이어 전용 서비스, 직접 트랜잭션, production caller 연결, repository-free façade를 요구하는 구조 테스트를 실행해 3 tests / 3 failures RED를 확인하고 테스트 전용 커밋 본문에 명령과 실패 원인을 기록했다. PM 리뷰에서 신규 rollback 테스트의 `NOT_SUPPORTED` fixture 격리 누락을 발견한 뒤 `AFTER_METHOD`를 요구하는 구조 테스트를 먼저 실행해 1 test / 1 failure RED를 확인하고 annotation 보강 후 GREEN을 확인했다.
  - 책임 분리: 계좌 생성·비활성화·PENALTY 활성화·soft delete를 `PaymentAccountCommandService`, PENALTY/COFFEE 생성·갱신을 `ChargeCreationService`, 본인 납부 완료·관리자 상태 변경을 `ChargeStatusCommandService`로 분리했다. 8개 public 명령 메서드는 각각 기존 write `@Transactional` 경계를 직접 소유한다.
  - 호환 경계: Controller와 Devotion/Poll adapter는 전용 서비스로 직접 연결했다. 기존 `BillingService`는 3개 명령 서비스와 `BillingQueryService`만 delegate하는 repository/transaction/business-rule-free façade로 제한했다. 사용자는 #149 최적화·응답 변경 없이 계좌 조회 3개 surface를 기존 `BillingQueryService`로 1:1 이동하는 최소 호환 연결을 최종 승인했다.
  - 정책 보존: campus pessimistic lock, PENALTY/COFFEE owner 범위, 기존 active deactivate 후 flush와 신규 insert 순서, PENALTY 미납 청구 재연결과 snapshot, COFFEE 기존 청구 비재연결, soft delete, terminal charge 차등 처리, unique 충돌 전파, Devotion/Poll 외부 트랜잭션 rollback을 그대로 유지했다.
  - 검증: BillingServiceTest 단독, Billing 전체, 분리 실행한 Devotion/Poll settlement/Batch 연결 테스트가 모두 성공했고, 최종 `./gradlew test`도 성공했다(326 tests / 0 failures / 0 errors / 1 skipped). `./gradlew build` 성공, `./gradlew asciidoctor` 성공, `git diff --check` 성공. PM의 광범위 순서 재현 명령은 #148 브랜치 180 tests / 10 failures, 깨끗한 `origin/develop` 175 tests / 동일 10 failures였고 모두 기존 Poll/Devotion 비트랜잭션 fixture와 context 재사용으로 인한 `DevotionServiceTest` 오염이므로 #148 production 회귀가 아님을 대조 확인했다. 이번 변경에서는 신규 rollback 테스트만 `DirtiesContext(AFTER_METHOD)`로 격리했으며 기존 10건을 해결했다고 집계하지 않는다. Controller mapping, request/response DTO, ErrorCode, Billing Entity, DB/Flyway 변경 0건, Swagger 문서 annotation 0건, Controller Entity 직접 반환 0건, Billing의 Devotion/Poll 역의존 및 서비스 순환 의존 추가 0건을 확인했다.
  - Docker QA: `faithlog-qa-148-billing-command` 격리 compose project에서 clean app image build, PostgreSQL/Redis `healthy`, backend `/api/v1/health`의 `data.status=UP`, 동일 project compose down까지 성공했다. 정리 전후 실행 중인 컨테이너는 0개였고 기존 중지 컨테이너 3개와 named volume 15개는 건드리지 않았다. buildx builder는 명시적으로 사용하지 않았으며 `docker builder prune -f`로 미사용 cache 2.084GB를 정리했다. Build Cache는 28개/2.477GB/회수 가능 2.084GB에서 9개/393.3MB/회수 가능 0B로 줄었다. `docker system prune`, volume/image prune, named volume 삭제는 실행하지 않았다.
  - 도구 제약: `./gradlew asciidoctor` 첫 실행은 sandbox의 Gradle wrapper lock 접근 제한으로 실패했고 승인 경로 재실행에서 성공했다. GitHub Issue #148의 `projectItems`는 비어 있었고 CLI token에 `read:project` scope가 없어 Project 카드 상태를 조회하거나 변경하지 못했다. `pm-dev`의 `score_code.py`는 `.harness/reports/review-score.json`과 `.md`를 생성했지만 저장소에 `harness.yaml` specialist 설정이 없어 overall 누락/`passed=false`였고 critical/security finding은 0건이었다. `review_gate.py`는 `harness.yaml`, quality/TDD policy와 evidence 파일 부재로 실패했다. 생성된 `.harness/` 보고서는 승인 없이 커밋하거나 삭제하지 않고 untracked로 보존했다.
  - 이력서 문장 후보: `Billing의 8개 트랜잭션 명령을 계좌·청구 생성·상태 변경 3개 응집 Service로 분리하고 repository-free 호환 façade와 구조 회귀 테스트를 도입해, 326개 전체 테스트와 격리 Docker health 검증으로 API·DB·권한·정산 동작 무변경을 보장했다.`

- #147 Campus/Admin 유스케이스 책임 분리:
  - 작업 기준: Issue #147 `[Refactor] 01 Campus와 Admin 유스케이스 책임 분리`, 브랜치 `chore/147-campus-admin-usecase-separation`, 최신 `origin/develop` `8314059` 기준 전용 Codex worktree.
  - TDD 증거: 캠퍼스 수정 권한/필드/초대코드 보존 characterization test를 먼저 추가해 기존 구현에서 GREEN을 확인했다. 이어 책임 Service 부재와 기존 façade의 repository/transaction 소유를 검출하는 구조 테스트 4건을 추가해 4 failures RED를 확인한 뒤 최소 이동으로 GREEN을 만들었다.
  - 책임 분리: Campus 6개(`CampusCreationService`, `CampusJoinService`, `CampusQueryService`, `CampusUpdateService`, `CampusMemberManagementService`, `CampusDutyAssignmentService`)와 Admin 3개(`AdminUserManagementService`, `AdminCampusManagementService`, `AdminDashboardQueryService`) 유스케이스 Service로 분리했다. 기존 `CampusService`, `AdminManagementService`, `AdminDashboardService`는 다른 내부 호출과 테스트 호환을 위한 repository-free façade로 제한했다.
  - 트랜잭션/권한 보존: 기존 `CampusService` 12개, `AdminManagementService` 5개, `AdminDashboardService` 1개의 `@Transactional` 경계를 각각 새 책임 Service로 1:1 이동했다. Campus 공통 활성 사용자/관리자 검증은 `CampusAccessPolicy`에 모으고 `CampusRolePolicy`, `AdminAccessPolicy`, repository port 방향과 검증 순서를 유지했다.
  - 검증: admin/campus focused 47 tests 성공, `./gradlew test` 성공(320 tests / 0 failures / 0 errors / 1 skipped), `./gradlew build` 성공, `./gradlew asciidoctor` 성공, `git diff --check` 성공. Controller mapping annotation 변경 0건, request DTO/DB/Flyway/ErrorCode 변경 0건, Swagger 문서 annotation 0건, Controller domain Entity import 0건을 확인했다.
  - Docker QA: `faithlog-qa-147-codex` 격리 compose project에서 app image build, PostgreSQL/Redis `healthy`, backend `/api/v1/health`의 `data.status=UP`, compose down까지 성공했다. Docker volume은 삭제하지 않았다.
  - 도구 제약: `dev_gate.py`는 저장소 `harness.yaml`, `.harness/*`, `.codex/agents/*`, `docs/00-index.md` 부재로 실패했다. `score_code.py`는 실행됐지만 `harness.yaml` specialist/overall score가 없어 `passed=false` 보고서를 생성했고, `review_gate.py`도 quality/TDD harness 파일 부재로 실패했다. GitHub Issue #147의 연결 `projectItems`는 비어 있었고 로컬 `gh` token이 invalid라 Project 카드 상태 변경은 수행하지 못했다.
  - 이력서 문장 후보: `Campus/Admin의 18개 트랜잭션 유스케이스를 9개 응집 Service로 분리하고 repository-free 호환 façade와 구조 회귀 테스트를 도입해, 320개 전체 테스트와 격리 Docker health 검증으로 API·DB·권한 동작 무변경을 보장했다.`

- #145 DDD 도메인 내부 MVC 패키지 구조 정리:
  - 작업 기준: Issue #145 `[Refactor] DDD 도메인 내부 MVC 패키지 구조 정리`, 브랜치 `chore/145-ddd-mvc-package-structure`, 최신 `origin/develop` 기준 전용 Codex worktree.
  - TDD 실패 확인: 신규 의존성 없이 `DomainPackageStructureTest`를 먼저 추가했다. 기존 구조에서 운영/테스트 패키지 규칙 2건이 실패하는 RED를 확인한 뒤 도메인 단위로 이동했다.
  - 이동 범위: Java 443개 파일을 새 책임 패키지로 이동했다. 도메인별 이동 수는 admin 29, batch 14, billing 58, campus 51, devotion 53, notification 56, poll 88, prayer 47, user 45, global 2다. tracked Java 경로와 package/import에서 legacy `application`, `presentation`, `infrastructure/jpa` 잔존은 0건이다.
  - 구조 보존: 운영 Java는 package/import와 JPQL constructor의 FQCN 경로를 제외한 본문 multiset이 `origin/develop`과 일치했다. 테스트도 package/import와 순서 격리 annotation을 제외한 기존 본문이 일치했다. API path/JSON/HTTP/error 계약, 비즈니스 로직, 인증/인가, 스케줄, 트랜잭션, Flyway, Gradle 의존성은 변경하지 않았다.
  - 트러블슈팅: 테스트 패키지 이름이 `application/presentation`에서 `service/controller`로 바뀌면서 클래스 실행 순서가 달라졌다. 비트랜잭션 controller 테스트 데이터가 전역 count를 검증하는 admin/billing service 테스트보다 먼저 실행돼 5건이 실패했다. 두 service 통합 테스트 class 시작 전에 Spring context를 재생성하도록 격리를 보강했고 생산 코드는 변경하지 않았다.
  - 검증: `./gradlew test` 성공(315 tests / 0 failures / 0 errors / 1 skipped), `./gradlew build` 성공, `./gradlew asciidoctor` 성공, `git diff --check` 성공. Swagger 문서 annotation 0건, Controller의 domain Entity import 0건, 새 구조 테스트 2건 GREEN을 확인했다.
  - Docker QA: `faithlog-qa-145-20260710` 격리 compose project에서 app image build, PostgreSQL/Redis health, backend `/api/v1/health`의 `status=UP`, compose down까지 성공했다. Docker volume은 정책에 따라 삭제하지 않았다.
  - 이력서 문장 후보: `9개 도메인과 global의 Java 443개 파일을 DDD 최상위 경계 + MVC 책임 패키지로 재배치하고, 의존성 추가 없는 구조 회귀 테스트와 315개 전체 테스트·Docker health 검증으로 API/DB/비즈니스 동작 무변경을 보장했다.`

### 2026-07-09

- #142 투표 조회 상태 시간 동기화:
  - 작업 기준: Issue #142 `[Fix] 투표 조회 상태 시간 동기화`, 브랜치 `fix/142-poll-status-time-sync-dev`, worktree `/Users/josephuk77/.codex/worktrees/9dfb/FaithLog`.
  - TDD 실패 확인: `PollServiceTest.current_scheduled_poll_opens_on_member_list_detail_and_response_with_campus_scope`를 먼저 추가하고 `./gradlew test --tests com.faithlog.poll.application.PollServiceTest`를 실행해 목록 결과에 현재 기간 `SCHEDULED` 투표가 `OPEN`으로 포함되지 않는 실패를 확인했다.
  - 구현 범위: `PollService`에서 campus path scope로 조회한 poll에 대해서만 `SCHEDULED && startsAt <= now < endsAt`이면 `poll.open()`으로 동기화한다. 사용자 목록, 상세/결과/댓글 목록 조회와 응답/댓글/사용자 옵션 open 검증 전에 적용했다.
  - 회귀 방지: DB `timestamptz`/UTC 저장은 변경하지 않았고 `Instant` 비교를 유지했다. `CLOSED` 자동 저장, 커피 정산, 알림 같은 close side effect는 조회 동기화에서 호출하지 않았다. 시작 전 예약 투표는 사용자 목록에서 숨기고, 종료 후 사용자 3일/관리자 7일 노출 정책과 마감 투표 응답 거부 계약을 유지했다.
  - 검증: focused PollService 테스트 성공, `./gradlew test --tests com.faithlog.poll.presentation.PollApiRestDocsTest` 성공, `./gradlew test` 성공(313 tests / 0 failures / 0 errors / 1 skipped), `./gradlew build` 성공, `git diff --check` 성공.
  - Docker QA: `scripts/qa_docker_compose_isolated.sh --suffix 142-poll-status-sync`로 app image build, PostgreSQL/Redis health, backend health `UP`, compose down까지 성공했다. 스크립트 정책에 따라 Docker volume은 삭제하지 않았다.
  - 정적 확인: Swagger 문서화 annotation 검색은 정책 문서의 금지 예시만 매칭됐고 `src` 신규 annotation 추가는 없었다. API request/response shape 변경과 Controller Entity 직접 반환 추가는 없다.

- #139 서버 DB 세션 시간대 설정 정리:
  - 작업 기준: Issue #139 `[Fix] 서버 DB 세션 시간대 설정 정리`, 브랜치 `fix/139-timezone-config`, worktree `/private/tmp/FaithLog-139-timezone-config`.
  - 문제 확인: Cloud Run health 응답의 `timestamp`가 `Z` UTC 기준으로 내려왔고, Dockerfile `TZ` 설정만으로는 repository-based Cloud Run 서버리스 배포의 Spring/JDBC/DB session timezone을 보장하지 못한다.
  - 확정 정책: DB 저장은 `Instant` + PostgreSQL `TIMESTAMPTZ`/UTC 기준을 유지한다. Issue #139에서는 전체 API timestamp 응답 계약을 `+09:00`으로 일괄 변경하지 않고, Spring/Jackson/Hibernate/JDBC session 기준만 `Asia/Seoul`로 명시한다.
  - TDD 실패 확인: `TimeZoneConfigurationTest`를 먼저 추가하고 `./gradlew test --tests com.faithlog.deploy.TimeZoneConfigurationTest` 실행 시 `spring.jackson.time-zone` 미설정으로 실패를 확인했다.
  - 구현 범위: `application.yml`에 `app.time-zone=Asia/Seoul`, `spring.jackson.time-zone=Asia/Seoul`, `spring.jpa.properties.hibernate.jdbc.time_zone=Asia/Seoul`, `spring.datasource.hikari.connection-init-sql=SET TIME ZONE 'Asia/Seoul'`을 추가했다. 애플리케이션 시작 시 JVM default timezone도 `app.time-zone` 기준으로 고정한다. DB schema와 기존 데이터는 변경하지 않았다.
  - focused 검증: `./gradlew test --tests com.faithlog.deploy.TimeZoneConfigurationTest` 성공.
  - 전체 검증: `./gradlew test` 성공, `./gradlew build` 성공, `git diff --check` 성공. Docker isolated QA `scripts/qa_docker_compose_isolated.sh --suffix 139-timezone`에서 app image build, PostgreSQL/Redis health, backend health `UP`, compose down까지 성공했다.
  - 확인 사항: `/api/v1/health`의 envelope `timestamp`는 `ApiResponse.timestamp` 타입이 `Instant`라 여전히 `Z` UTC 문자열로 직렬화된다. Issue #139의 승인 범위는 전체 API timestamp 응답 계약 변경이 아니라 Spring/Jackson/Hibernate/JDBC session timezone 명시다.
  - CI 재현/보강: PR #140 GitHub Actions가 UTC JVM 환경에서 경건생활 `LocalDate` 응답이 하루 전으로 밀리는 실패를 냈다. `TZ=UTC ./gradlew test --tests com.faithlog.devotion.presentation.DevotionControllerTest --tests com.faithlog.devotion.presentation.DevotionApiRestDocsTest`로 재현했고, JVM default timezone 고정 후 UTC 조건에서도 경건생활 테스트 묶음이 통과하도록 보강했다.

### 2026-07-08

- #136 운영 데이터 보관 기간과 정리 배치 구현:
  - 작업 기준: Issue #136 `[Build] 운영 데이터 보관 기간과 정리 배치 구현`, 브랜치 `build/136-data-retention-cleanup`, worktree `/Users/josephuk77/.codex/worktrees/7d03/FaithLog`.
  - 사용자 확정 정책: 모든 정리 배치는 `Asia/Seoul` 기준 04:30에 실행한다. `prayer_submissions` 1년 보관 기준과 `charge_items` 전년도 완료 데이터 기준은 모두 `created_at`으로 확정했다. 신규 user-facing API와 DB schema 변경은 추가하지 않았다.
  - TDD 실패 확인: 구현 전 `DataRetentionCleanupServiceTest`, scheduler cron 검증, FCM logout row-delete 테스트를 먼저 추가하고 `./gradlew test --tests com.faithlog.batch.application.DataRetentionCleanupServiceTest --tests com.faithlog.batch.scheduler.FaithLogSchedulerConfigTest --tests com.faithlog.notification.application.FcmTokenServiceTest`를 실행해 `DataRetentionCleanupService`/`DataRetentionCleanupResult` 부재로 `compileTestJava` 실패를 확인했다.
  - 구현 범위: `notification_logs` 14일 초과, `polls.ends_at` 30일 초과 poll graph(`poll_response_options`, `poll_responses`, `poll_comments`, `poll_options`, `polls`), soft deleted `poll_comments.deleted_at` 30일 초과, `prayer_submissions.created_at` 1년 초과를 daily cleanup으로 정리한다. 매년 2월 1일에는 전년도 `devotion_daily_checks.record_date`, `weekly_devotion_records.week_start_date`, terminal `charge_items.created_at`만 삭제하며 `UNPAID`는 보존한다.
  - 중복 실행 방지: 기존 Redis lock 추상화인 `NotificationLockService`를 재사용해 daily/annual retention lock을 획득하지 못하면 fail-closed로 0건 처리한다.
  - 로그아웃 FCM token 정책: `clientInstanceId` 또는 `fcmToken`이 제공된 logout path에서 현재 기기 active `user_fcm_tokens` row를 inactive 처리하지 않고 실제 삭제하도록 변경했다. 입력이 없어도 logout 성공과 refresh/access token 무효화 정책은 유지한다.
  - focused 검증: retention/scheduler/FCM/auth refresh/logout 회귀 묶음 `./gradlew test --tests com.faithlog.batch.application.DataRetentionCleanupServiceTest --tests com.faithlog.batch.scheduler.FaithLogSchedulerConfigTest --tests com.faithlog.notification.application.FcmTokenServiceTest --tests com.faithlog.user.presentation.AuthLogoutFcmPersistenceTest --tests com.faithlog.user.presentation.AuthLogoutControllerTest --tests com.faithlog.user.presentation.AuthRefreshControllerTest` 성공.
  - 전체 검증: `./gradlew test` 성공(310 tests / 0 failures / 0 errors / 1 skipped), `./gradlew build` 성공, `./gradlew asciidoctor` 성공. `./gradlew asciidoctor` 최초 실행은 sandbox의 Gradle wrapper lock 접근 제한으로 실패했고 승인 경로 재실행에서 성공했다.
  - Docker QA: `docker compose up -d postgres redis app`로 app image build와 컨테이너 기동을 확인했다. 호스트 `localhost:8080` health curl은 sandbox/포트 접근 제약으로 연결 실패했지만, 컨테이너 내부 `docker exec faithlog-backend wget -qO- http://127.0.0.1:8080/api/v1/health`는 `status=UP`을 반환했다. QA 후 `docker compose down`으로 컨테이너와 네트워크를 정리했다. 운영 Supabase/Upstash 데이터는 건드리지 않았다.

### 2026-07-07

- #134 1000명 기준 기도제목·투표 결과 조회 성능 최적화:
  - 작업 기준: Issue #134 `[Perf] 1000명 기준 기도제목·투표 결과 조회 성능 최적화`, Project `FaithLog Backend Kanban` Status `In Progress`, 브랜치 `perf/134-1000-user-read-optimization`, worktree `/Users/josephuk77/.codex/worktrees/134-1000-user-read-optimization/FaithLog`.
  - 구현 전 evidence: Issue #134 baseline `PERF_1000_20260707_A`, local Docker steady-state read `VUS=30`, `DURATION=5m`, `THINK_TIME_SECONDS=1`, 활성 멤버 1001명, 경건 주간 1001건, 일별 체크 7007건, 청구 1001건, 투표 응답 1550건, 기도제목 1000건. 전체 63,877 requests, failure 0.00%, 211.88 req/s, avg 57.12ms, p95 192.65ms, p99 290.45ms. 병목은 `prayer_weekly_board` p95 316.82ms / p99 417.07ms, `poll_results` p95 244.84ms / p99 340.02ms, `admin_dashboard_summary` p95 138.50ms였다.
  - TDD/회귀 evidence: `PrayerServiceTest.weekly_board_fetches_member_profiles_without_per_member_user_lookup`, `PollServiceTest.poll_results_fetch_respondents_without_per_response_user_lookup`를 추가해 25명 샘플에서 Hibernate `prepareStatementCount <= 12`를 검증했다. 기존 구현은 기도 보드 member/user 조회와 비익명 poll respondent 조회가 사용자 수에 비례해 user lookup을 반복하는 구조였다.
  - 구현 범위: API path/request/response 계약 변경 없이 `CampusUserLookupPort.findCampusUsersByIds` batch lookup을 추가했다. 기도 주간 보드는 target member user profile을 한 번에 읽어 조립하고, 투표 결과는 비익명 응답자 user profile을 한 번에 읽는다. 투표 결과 `targetMemberCount`는 active member 엔티티 목록 materialization 대신 `countByCampusIdAndStatus` count query로 계산한다.
  - DB/Flyway: 기존 V1은 수정하지 않았고 새 migration도 추가하지 않았다. 현재 조회 경로는 `prayer_weeks(campus_id, season_id, week_start_date)`, `prayer_submissions(prayer_week_id, user_id)`, `poll_responses(poll_id, user_id)`, `poll_response_options(response_id, option_id)` unique index/constraint 경로를 이미 활용할 수 있어, 이번 1차 개선은 코드-only batch query로 제한했다.
  - k6 smoke: local Docker app rebuild 후 `VUS=1`, `DURATION=10s`, steady-state read, `CAMPUS_ID=60`, `POLL_ID=35`, `WEEK_START_DATE=2026-06-22`로 실행. 109 requests, failure 0.00%, p95 46.35ms, `prayer_weekly_board` p95 55.77ms, `poll_results` p95 38.19ms. 리포트: `build/reports/k6/issue-134-after-smoke.json`.
  - k6 재측정: local Docker `PERF_1000_20260707_A`, `VUS=30`, `DURATION=5m`, `THINK_TIME_SECONDS=1`, `AUTH_PATTERN=steady-state`, `INCLUDE=campuses,admin-campuses,admin-dashboard,devotions,billing,polls,prayers`, `CAMPUS_ID=60`, `POLL_ID=35`. 전체 89,173 requests, failure 0.00%, 295.92 req/s, avg 16.93ms, p50 8.47ms, p95 44.60ms, p99 89.37ms, max 2.47s. 리포트: `build/reports/k6/issue-134-after-vus30-5m.json`.
  - 개선 결과: 전체 avg 57.12ms -> 16.93ms(70.36% 감소), p95 192.65ms -> 44.60ms(76.85% 감소), p99 290.45ms -> 89.37ms(69.23% 감소), throughput 211.88 req/s -> 295.92 req/s(39.66% 증가). `prayer_weekly_board` p95 316.82ms -> 76.96ms(75.71% 감소), p99 417.07ms -> 165.64ms(60.28% 감소). `poll_results` p95 244.84ms -> 51.19ms(79.09% 감소), p99 340.02ms -> 108.48ms(68.10% 감소). `admin_dashboard_summary` p95 138.50ms -> 61.64ms(55.49% 감소).
  - 이력서 문장 후보: `local Docker 1000명 데이터셋(PERF_1000_20260707_A)에서 기도제목 주간 보드와 투표 결과 조회의 per-user lookup을 batch query로 최적화해 k6 VUS 30/5분 steady-state read 기준 prayer board p95를 316.82ms에서 76.96ms로 75.71%, poll results p95를 244.84ms에서 51.19ms로 79.09% 단축하고 failure 0.00%를 유지했다.`

### 2026-07-06

- #131 회원 탈퇴와 계정 소프트 삭제 구현:
  - 작업 기준: Issue #131 `[Feat] 회원 탈퇴와 계정 소프트 삭제 구현`, 브랜치 `feat/131-account-deletion-soft-delete`, worktree `/private/tmp/FaithLog-131`.
  - TDD 실패 확인: 구현 전 `UserDeletionControllerTest`를 먼저 추가하고 `./gradlew test --tests com.faithlog.user.presentation.UserDeletionControllerTest`를 실행해 `User.deletedAt()` 등 탈퇴 구현 부재로 `compileTestJava` 실패를 확인했다.
  - 구현 범위: `DELETE /api/v1/users/me`를 추가해 현재 비밀번호와 확인 문구 `회원탈퇴`를 검증한다. 성공 시 `users.is_active=false`, `users.deleted_at=now()`, email/name/password anonymization, `tokenVersion` 증가, campus membership `INACTIVE`, active FCM token 비활성화, refresh session 삭제, current access token blacklist를 한 트랜잭션으로 처리한다.
  - 문서화: Spring REST Docs에 회원 탈퇴 성공/비밀번호 불일치 계약을 추가하고 `src/docs/asciidoc/index.adoc`, `docs/decision-log.md`, `docs/backend-implementation-policy.md`를 갱신했다. Flyway V6에서 `users.deleted_at` 컬럼을 추가한다.
  - 검증: focused deletion/controller/REST Docs 테스트 성공, `./gradlew test` 성공(298 tests / 0 failures / 0 errors / 1 skipped), `./gradlew build` 성공, `./gradlew asciidoctor` 성공. `./gradlew asciidoctor` 최초 실행은 sandbox의 Gradle wrapper lock 접근 제한으로 실패했고 승인 경로 재실행에서 성공했다. `git diff --check` 성공, Swagger 문서화 annotation 검색 0건, REST Docs snippet group 122개를 확인했다.

### 2026-07-04

- #128 FCM 토큰 upsert와 로그아웃 비활성화 무결성 보강:
  - 작업 기준: Issue #128 `[Fix] FCM 토큰 upsert와 로그아웃 비활성화 무결성 보강`, 브랜치 `fix/128-fcm-token-upsert-integrity`.
  - TDD 실패 확인: 기존 `FcmTokenServiceTest`는 같은 token을 다른 user가 등록할 때 같은 row의 ownership을 이동하는 동작을 기대했다. 이를 이전 ownership inactive 이력 유지 + 현재 user active row 생성 정책으로 변경한 뒤 focused 테스트가 실패하는 것을 확인했다.
  - 구현 범위: FCM 등록은 active `userId + clientInstanceId + token`이 이미 있으면 같은 row를 반환하고 metadata를 갱신한다. 같은 user/client의 다른 active token은 inactive 처리하고, 같은 active token의 다른 user/client ownership도 inactive 처리한 뒤 새 active row를 저장한다. 응답 DTO에는 등록된 `token`을 포함한다.
  - DB 마이그레이션: V5에서 기존 `uk_user_fcm_tokens_token` 전체 unique constraint를 제거하고 active-only unique index `uk_user_fcm_tokens_active_token`, `uk_user_fcm_tokens_active_user_client`를 추가했다. 마이그레이션 적용 전 기존 active user/client 중복이 있으면 최신 row만 active로 유지하고 나머지는 inactive 처리한다.
  - 검증: focused FCM service/controller/REST Docs/Flyway migration 테스트 성공, `./gradlew test` 성공, `./gradlew build` 성공, `./gradlew asciidoctor` 성공. `./gradlew asciidoctor` 최초 실행은 sandbox의 Gradle wrapper lock 접근 제한으로 실패했고 승인 경로 재실행에서 성공했다.
  - Docker/Flyway/API QA: Docker app image build는 Docker Hub `eclipse-temurin` metadata 조회가 2회 연속 `DeadlineExceeded`로 실패해 app-container health QA는 완료하지 못했다. 대신 로컬 캐시가 있는 Docker Postgres/Redis를 띄우고 실제 PostgreSQL `PostgresFlywayMigrationTest`를 `FAITHLOG_RUN_POSTGRES_FLYWAY_TEST=true`로 실행해 V1-V5 migration 및 active FCM unique index 존재를 검증했다. 이후 로컬 Spring 앱을 Docker Postgres/Redis에 연결해 실제 HTTP API로 signup/login, FCM token A 등록, 같은 user/client token B 등록으로 A inactive, 같은 token 재등록 같은 tokenId 반환, 다른 user가 token B 등록 시 이전 ownership inactive + 새 active row 생성, logout by `clientInstanceId`/`fcmToken` 후 current token inactive를 확인했다. DB 조회 결과 QA token 3건은 모두 기대한 이유로 inactive이며 `deactivated_at`이 존재했다. QA 후 app process 중지와 `docker compose down` 완료.

### 2026-07-02

- #125 계좌 활성 전환과 정산 API 계약 보강:
  - 작업 기준: Issue #125 `[Fix] 계좌 활성 전환과 정산 API 계약 보강`, 브랜치 `fix/125-payment-account-activation-contract`, worktree `/Users/josephuk77/.codex/worktrees/6840/FaithLog`.
  - TDD 실패 확인: 구현 전 `BillingServiceUnitTest`에 `PENALTY` 교체 등록 시 기존 active 비활성화 후 repository `flush()`가 새 계좌 `save()`보다 먼저 호출되어야 한다는 순서 테스트를 추가했다. 최초 실행은 `PaymentAccountRepositoryPort.flush()` 부재로 `compileTestJava` 실패했다.
  - 구현 범위: `BillingService.createPaymentAccount`에서 기존 active 계좌를 비활성화한 경우 즉시 repository `flush()`를 호출한 뒤 새 active 계좌를 저장하도록 보강했다. `activatePenaltyPaymentAccount`도 기존 active `PENALTY`를 inactive 처리하고 flush한 뒤 선택 계좌를 active로 전환하도록 수정했다.
  - 회귀 방지: `COFFEE` 계좌는 기존처럼 `campusId + accountType + ownerUserId` 기준으로 같은 owner의 이전 active만 비활성화한다. `PENALTY` owner 생략 시 requester 저장, inactive 목록 유지, activate/delete soft delete, `charges/my-accounts` PENALTY/COFFEE 분리 정책은 기존 계약을 유지했다. DB 스키마 변경과 Swagger 문서화 annotation은 추가하지 않았다.
  - 검증: `./gradlew test --tests com.faithlog.billing.application.BillingServiceUnitTest` 성공, focused billing service/query/controller/REST Docs 테스트 성공, `./gradlew test` 성공(293 tests / 0 failures / 0 errors / 1 skipped), `./gradlew build` 성공, `./gradlew asciidoctor` 성공. `./gradlew asciidoctor` 최초 실행은 sandbox의 `~/.gradle` wrapper lock 접근 제한으로 실패했고 승인 경로 재실행에서 성공했다.
  - Docker/API QA: `scripts/qa_docker_compose_isolated.sh --suffix 125-flush-health`로 Docker isolated health `UP` 확인 후 stack down 완료. 추가 compose project `faithlog-qa-125-api`에서 실제 API A-F를 실행하고 stack down 완료했다. 확인 결과: 기존 active PENALTY가 있는 상태에서 새 PENALTY 등록 201, 기존 inactive/새 active 목록 확인, inactive PENALTY activate 200, active delete 409, inactive delete 204, COFFEE owner별 active 2건 유지, manager `my-accounts` PENALTY 3500/COFFEE 1800, coffee duty `my-accounts` COFFEE 2200, 무토큰 401 `AUTH_UNAUTHORIZED`, 일반 MEMBER 403 `BILLING_CHARGE_LIST_FORBIDDEN`.
  - 트러블슈팅: Docker API QA 중 보조 DB 상태 출력용 `string_agg` SQL의 콜론 quoting이 잘못되어 DB 진단 문자열만 비어 있었다. 동일 상태는 API list 응답의 `isActive`, `deactivatedAt`, soft-deleted 제외 결과로 확인했다.

- #122 PENALTY 계좌 owner와 내 계좌 정산 조회 정책 보강:
  - 작업 기준: Issue #122 `[Fix] PENALTY 계좌 owner와 내 계좌 정산 조회 정책 보강`, 브랜치 `fix/122-penalty-owner-my-accounts`, worktree `/Users/josephuk77/.codex/worktrees/6dd8/FaithLog`.
  - TDD 실패 확인: 구현 전 `BillingServiceTest`, `BillingQueryServiceTest`, `BillingControllerTest`, `BillingApiRestDocsTest`에 PENALTY owner 기본값, 명시 owner 저장, campus manager/service ADMIN의 active PENALTY 포함, legacy `ownerUserId=null` active PENALTY 포함, COFFEE owner 제한, active COFFEE 담당자 범위, MEMBER 403, REST Docs 정책 테스트를 추가했다. 최초 focused billing 테스트는 54 tests 중 5 failures로 실패했다.
  - 구현 범위: `BillingService.createPaymentAccount`에서 `PENALTY` 생성 시 `ownerUserId` 생략이면 requester userId를 저장하고, 명시 값은 메타데이터로 그대로 저장하도록 보강했다. `BillingQueryService.listAdminCampusChargesForMyAccounts`는 campus manager/service ADMIN에 대해 active PENALTY 계좌를 owner와 무관하게 후보에 포함하고, COFFEE는 requester-owned active 계좌만 포함하도록 분리했다.
  - 권한/정책 회귀 방지: active COFFEE 담당자는 기존처럼 본인 active COFFEE 계좌 범위만 조회 가능하고 PENALTY 데이터는 403으로 유지한다. 일반 MEMBER의 admin `my-accounts` 접근은 403, 인증 없음은 401을 유지한다. COFFEE inactive 계좌 activate API, 운영 데이터 backfill, API 경로 변경, Swagger annotation은 추가하지 않았다.
  - 문서화: `docs/decision-log.md`, `docs/backend-implementation-policy.md`, Spring REST Docs 설명, `src/docs/asciidoc/index.adoc`에 PENALTY owner 메타데이터와 `my-accounts` PENALTY 포함 정책을 기록했다. snippet group 수는 120개로 유지했다.
  - 검증: focused billing service/query/controller/REST Docs 테스트 성공, `./gradlew test` 성공(291 tests / 0 failures / 0 errors / 1 skipped), `./gradlew build` 성공, `./gradlew asciidoctor` 성공, Docker compose health `UP` 확인, Docker API QA 성공.
  - Docker/API QA: `scripts/qa_docker_compose_isolated.sh --suffix 122-penalty-owner`로 app health `UP` 확인 후 stack down 완료. 추가 격리 compose project `faithlog-qa-122-api`에서 회원가입, test-only DB role 승격, 캠퍼스 생성, PENALTY 계좌 owner 생략 생성(`defaultOwner=managerId`), owner 명시 생성(`explicitOwner=memberId`), manager `GET /charges/my-accounts?paymentCategory=PENALTY` 200, member `GET /charges/my-accounts` 403을 확인하고 `docker compose -p faithlog-qa-122-api down`으로 정리했다.
  - 트러블슈팅: `./gradlew asciidoctor` 최초 실행은 sandbox의 `~/.gradle` wrapper lock 접근 제한으로 실패해 승인 경로로 재실행했다. API QA 첫 검증 스크립트는 psql `UPDATE ... returning` 출력에 `UPDATE 1`이 함께 붙어 shell assertion만 실패했으며, DB select 기반으로 파싱을 고쳐 재검증했다.

### 2026-07-01

- #119 캠퍼스 관리자 벌금 계좌와 정산 조회 권한 회귀 검증:
  - 작업 기준: Issue #119 `[Fix] 캠퍼스 관리자 벌금 계좌와 정산 조회 권한 수정`, 브랜치 `fix/119-admin-billing-campus-role-permission`, worktree `/private/tmp/faithlog-admin-billing-permission`.
  - QA 재현 범위: 프론트 QA에서 `POST /api/v1/admin/campuses/{campusId}/payment-accounts` PENALTY 등록과 `GET /api/v1/admin/campuses/{campusId}/charges`가 campus 관리자 role에서 `401 AUTH_UNAUTHORIZED`로 보이는 문제를 검증했다.
  - 확인 결과: 최신 `develop`의 production authorization 정책은 이미 campus role `MINISTER`, `ELDER`, `CAMPUS_LEADER` 및 service-level `ADMIN`을 허용하고, 일반 `MEMBER`는 403, 무토큰/무효 토큰은 401로 분리되어 있다.
  - 보강 테스트: `users.role = USER`이지만 campus role이 `MINISTER`인 사용자가 role 변경 후 fresh login token으로 PENALTY 계좌 등록과 관리자 정산 조회에 성공하는 HTTP 회귀 테스트를 추가했다. 같은 API에 대해 일반 `MEMBER`는 각각 `403 BILLING_PAYMENT_ACCOUNT_MANAGE_FORBIDDEN`, `403 BILLING_CHARGE_LIST_FORBIDDEN`, 토큰 없음은 `401 AUTH_UNAUTHORIZED`로 고정했다.
  - 원인 분석: campus role 변경 시 #76 정책에 따라 대상 사용자의 `users.token_version`이 증가한다. 따라서 권한 변경 직후 refresh/login 없이 기존 access token을 그대로 쓰면 `AUTH_UNAUTHORIZED` 401이 정상적으로 발생할 수 있다. 프론트는 campus role 변경/권한 상승 이후 401을 받으면 refresh token으로 access token을 재발급하거나 재로그인해야 최신 campus 권한이 반영된다.
  - 검증: `./gradlew test --tests com.faithlog.billing.presentation.BillingControllerTest` 성공, `./gradlew test` 성공, `./gradlew build` 성공, `git diff --check` 성공.

- #116 벌금 계좌 활성화와 삭제 정책 구현:
  - 작업 기준: Issue #116 `[Fix] 벌금 계좌 활성화와 삭제 정책 구현`, Project `FaithLog Backend Kanban` Status/Kanban Status `In Progress`, 브랜치 `fix/116-penalty-account-policy`, worktree `/Users/josephuk77/.codex/worktrees/f6ed/FaithLog`.
  - TDD 실패 확인: 구현 전 `BillingServiceTest`, `BillingControllerTest`, `BillingApiRestDocsTest`에 PENALTY active/inactive 목록, idempotent activate, COFFEE activate 거부, active delete 409, inactive soft delete, soft deleted 목록/activate 제외, terminal charge snapshot 보존 테스트를 추가했다. focused billing 테스트 최초 실행은 `deletePaymentAccount`, `activatePenaltyPaymentAccount`, `deletedAt` 부재 등 15개 compile error로 실패했다.
  - 구현 범위: `payment_accounts.deleted_at` soft-delete 필드를 추가하고 V4 Flyway migration으로 반영했다. admin payment account list는 기본 active-only로 바꾸고 `accountType`, `includeInactive` query를 추가했다. `includeInactive=true`는 active + inactive를 반환하되 soft deleted 계좌는 항상 제외한다.
  - API 계약: `PATCH /api/v1/admin/campuses/{campusId}/payment-accounts/{paymentAccountId}/activate`는 PENALTY 전용이며 이미 active인 대상은 idempotent 성공이다. `COFFEE` activate는 `400 BILLING_PAYMENT_ACCOUNT_ACTIVATE_UNSUPPORTED`로 실패한다. `DELETE /api/v1/admin/campuses/{campusId}/payment-accounts/{paymentAccountId}`는 inactive 계좌만 soft delete하고 active 계좌는 `409 BILLING_PAYMENT_ACCOUNT_ACTIVE_DELETE_FORBIDDEN`으로 실패한다.
  - 회귀 방지: #114 COFFEE 사용자별 active 계좌 기준은 유지했고, billing query의 owner 계좌 조회도 soft deleted 계좌를 제외하도록 보강했다. 기존 청구에 연결된 계좌를 soft delete해도 `charge_items.payment_account_id`와 snapshot은 변경하지 않는다.
  - Spring REST Docs: `payment-account-admin-list-success`, `payment-account-admin-list-include-inactive-success`, `payment-account-activate-success`, `payment-account-delete-success` snippets를 추가하고 `src/docs/asciidoc/index.adoc`에 query parameter와 신규 endpoint 섹션을 반영했다. Swagger 문서화 annotation은 추가하지 않았다.
  - 검증: PM 세션에서 focused billing service/controller/REST Docs 테스트 41개 성공 확인. Codex 세션에서 `./gradlew test` 성공, `./gradlew build` 성공, `./gradlew asciidoctor` 성공(최초 sandbox Gradle wrapper lock 실패 후 승인 경로 재실행 성공), `git diff --check` 성공, Docker compose health `UP` 확인, 실제 API QA 성공.
  - Docker/API QA: 기본 `docker compose`로 Postgres/Redis/app을 올리고 컨테이너 내부 `GET /api/v1/health` 200/`UP` 확인. 실제 API로 MANAGER 테스트 계정 생성/승격, 캠퍼스 생성, PENALTY 계좌 2개 등록, 기본 active-only 목록 1건, `includeInactive=true` 목록 2건, inactive activate, active delete 409, inactive soft delete, soft-deleted 계좌 목록 제외를 확인했다. QA 결과 `QA116_API_FLOW=PASS`를 확인했고, QA 후 `docker compose down`으로 컨테이너와 네트워크를 정리했다.
  - 트러블슈팅: QA 전용 compose project는 `docker-compose.yml`의 고정 `container_name` 때문에 기존 컨테이너와 충돌해 중단됐다. 기본 compose 재검증 중 기존 `faithlog_postgres-data` volume의 Postgres password와 현재 compose env가 달라 app boot가 실패했으나, 볼륨 삭제 없이 로컬 컨테이너의 `faithlog` DB role password를 compose 기본값으로 맞춘 뒤 health/API QA를 완료했다.

- #114 사용자별 커피 계좌와 커피투표 정산 권한 정리:
  - 작업 기준: Issue #114 `[Fix] 사용자별 커피 계좌와 커피투표 정산 권한 정리`, Project `FaithLog Backend Kanban` Status/Kanban Status `In Progress`, 브랜치 `fix/114-coffee-account-owner`, worktree `FaithLog-worktrees/fix-114-coffee-account-owner`.
  - TDD 실패 확인: 구현 전 `BillingServiceTest`와 `PollServiceTest`에 사용자별 COFFEE active 계좌 분리, 본인 소유 COFFEE 계좌 제한, 관리자/담당자 COFFEE poll/template 본인 계좌 요구 테스트를 추가했고 focused 테스트가 4 failures로 실패하는 것을 확인했다.
  - 구현 범위: COFFEE 계좌 active 기준을 `campusId + accountType + ownerUserId`로 분리하고, 같은 사용자의 새 COFFEE 계좌 등록 시 본인 이전 active 계좌만 비활성화하도록 변경했다. PENALTY 계좌는 캠퍼스 단위 active 1개와 기존 unpaid PENALTY charge 재연결 정책을 유지했다.
  - 권한 보강: COFFEE 계좌 생성은 requester 본인 소유만 허용하고 다른 `ownerUserId`는 `403 BILLING_PAYMENT_ACCOUNT_OWNER_FORBIDDEN`으로 고정했다. 캠퍼스 관리자와 active COFFEE 담당자는 본인 COFFEE 계좌를 등록할 수 있고, 일반 MEMBER는 active COFFEE 담당자가 아니면 403이다. COFFEE 담당자 단독 PENALTY 계좌 생성/비활성화는 403이다.
  - 커피투표/정산: 캠퍼스 관리자와 active COFFEE 담당자 모두 COFFEE poll/template을 만들 수 있지만, requester 본인 소유 active COFFEE `paymentAccountId`가 필수다. null/비활성/타 캠퍼스/PENALTY/타인 COFFEE 계좌는 실패한다. 종료 정산으로 생성되는 COFFEE charge는 poll의 `paymentAccountId`에 연결되고, 새 COFFEE 계좌 등록 후 기존 미납 COFFEE charge는 재연결되지 않는다.
  - 정산 조회: 캠퍼스 관리자(`MINISTER`, `ELDER`, `CAMPUS_LEADER`)와 전역 `ADMIN`의 admin charge 접근을 보강했고, 권한 없는 인증 사용자는 403, 인증 실패는 401 계약을 유지했다. `paymentAccountId` COFFEE 필터는 캠퍼스 관리자/담당자 모두 본인 계좌로 제한하고 전역 `ADMIN`은 전체 접근 가능하도록 했다.
  - DB/Flyway: `V3__split_active_coffee_payment_account_owner_scope.sql`을 추가해 기존 active payment account 인덱스를 PENALTY campus 단위와 COFFEE owner 단위 partial unique index로 분리했다. V1/V2는 수정하지 않았다.
  - 문서화: `docs/decision-log.md`, `docs/backend-implementation-policy.md`, Spring REST Docs 스니펫 설명, `src/docs/asciidoc/index.adoc`를 #114 정책으로 갱신했다. 새 캠퍼스 생성 시 기본 COFFEE 템플릿/반복투표 미생성 정책은 기존 테스트로 재검증했다.
  - 검증: 구현 전 실패 테스트 확인 후 focused billing/poll service 테스트 성공, focused billing/poll controller/REST Docs 테스트 성공, `./gradlew test` 성공(280 tests / 0 failures / 0 errors / 1 skipped), `./gradlew build` 성공, `./gradlew asciidoctor` 성공. `./gradlew asciidoctor` 최초 실행은 샌드박스의 `~/.gradle` lock 파일 접근 제한으로 실패했고 승인 경로 재실행에서 성공했다.
  - Docker/API QA: Docker daemon socket(`unix:///Users/josephuk77/.docker/run/docker.sock`)이 없어 `docker ps`가 sandbox/승인 경로 모두 실패했다. 따라서 compose health와 실제 Docker API QA는 수행하지 못했고, Spring Boot 통합 테스트와 REST Docs 계약 테스트로 필수 정책을 검증했다.

- #112 계좌 기준 정산 조회와 커피 투표 권한 정책 정리:
  - 작업 기준: Issue #112 `[Fix] 계좌 기준 정산 조회와 커피 투표 권한 정책 정리`, 브랜치 `fix/112-billing-account-scope-coffee-poll-policy`.
  - TDD 실패 확인: 구현 전 billing/campus/poll focused 테스트를 먼저 추가했고, `./gradlew test --tests com.faithlog.billing.application.BillingQueryServiceTest --tests com.faithlog.billing.application.BillingServiceTest --tests com.faithlog.campus.application.CampusServiceTest --tests com.faithlog.poll.application.PollServiceTest`가 `AdminCampusChargeListQuery.paymentAccountId`, `BillingService.listAdminPaymentAccounts`, `PaymentAccountResult.createdAt/deactivatedAt` 부재로 `compileTestJava` 실패했다.
  - 구현 범위: admin campus charge summary에 optional `paymentAccountId` 필터를 추가하고 기존 `paymentCategory`, `status`, `userId`, `keyword`, pagination 필터와 조합되도록 했다. `GET /api/v1/admin/campuses/{campusId}/charges/my-accounts`와 `GET /api/v1/admin/campuses/{campusId}/payment-accounts`를 추가해 owner 계좌 기준 집계와 관리자/담당자용 계좌 메타데이터 조회를 분리했다.
  - 권한 보강: PENALTY 계좌/청구 조회는 캠퍼스 관리자 또는 전역 ADMIN으로 유지하고, active COFFEE duty USER는 본인 활성 COFFEE 계좌/청구 범위로 제한했다. COFFEE poll 및 COFFEE poll template 생성/수정은 현재 active COFFEE duty만 가능하도록 제한했고, 비담당 캠퍼스 관리자와 전역 ADMIN의 COFFEE 생성 요청은 403으로 차단했다.
  - 자동 생성 정책: 신규 캠퍼스 생성 시 default COFFEE poll template과 반복 poll이 자동 생성되지 않도록 캠퍼스 생성 side effect 연결을 제거했다. 기존 캠퍼스에 이미 생성된 자동 커피 템플릿은 이번 작업에서 삭제/비활성화하지 않는다.
  - Spring REST Docs: admin charge summary query parameter에 `paymentAccountId`를 문서화하고, admin payment account list 및 my-accounts charge summary snippets와 `src/docs/asciidoc/index.adoc` 섹션을 추가했다. Swagger 문서화 annotation은 추가하지 않았다.
  - 검증: focused service 테스트 성공, focused billing controller/REST Docs 테스트 성공, `./gradlew test` 성공(276 tests / 0 failures / 0 errors / 1 skipped), `./gradlew build` 성공, `./gradlew asciidoctor` 성공, `git diff --check` 성공. forbidden-term 검색은 정책 문서의 금지 목록과 기존 내부 변수/도메인 메서드명만 검출되어 API 요청 계약 회귀는 없었다. Spring REST Docs snippet group은 117개로 증가했다.
  - Docker/API QA: Spring Boot 통합 테스트와 REST Docs 계약 테스트로 필수 정책을 검증했고, 별도 Docker compose 실제 API QA는 수행하지 않았다.

### 2026-06-30

- #109 경건생활 벌금 0원 청구 생성 방지:
  - 작업 기준: Issue #109 `[Fix] 경건생활 벌금 0원 청구 생성 방지`, 브랜치 `fix/109-zero-penalty-charge-skip`.
  - TDD 실패 확인: 구현 전 `DevotionServiceTest`에 0원 벌금 제출 시 charge 미생성, 활성 PENALTY 계좌 없이 0원 제출 성공 테스트를 추가했고, `./gradlew test --tests 'com.faithlog.devotion.application.DevotionServiceTest'`가 19 tests 중 2 failures로 실패했다. 실패 원인은 기존 코드가 제출 전 활성 PENALTY 계좌를 선확인하고 0원이어도 billing port를 호출하는 구조였다.
  - 구현 범위: `DevotionService.createPenaltyCharge(...)`에서 계산된 `totalAmount == 0`이면 billing port 호출 전 반환하도록 변경했다. 1원 이상 벌금은 기존 `BillingService.createPenaltyCharge(...)` 경로를 유지해 활성 계좌 확인, 계좌 snapshot 저장, 기존 #33 벌금 자동 생성 정책을 보존했다.
  - 회귀 보강: 양수 벌금 + 활성 PENALTY 계좌 없음 케이스는 계속 `BILLING_REQUIRED_PAYMENT_ACCOUNT_MISSING`으로 실패하도록 service/docs 테스트 fixture를 양수 벌금 조건으로 고정했다.
  - 문서화: `docs/decision-log.md`, `docs/backend-implementation-policy.md`, `docs/codex/FAITHLOG_CODEX_HOOK.md`에 0원 벌금 청구 미생성 및 0원 제출 시 활성 PENALTY 계좌 미요구 정책을 기록했다.
  - 검증: focused devotion service/docs 테스트 성공, `./gradlew test` 성공(269 tests / 0 failures / 0 errors / 1 skipped), `./gradlew build` 성공, `git diff --check` 성공. Swagger 문서화 annotation 추가 0건, #109 변경 파일 기준 금지어/단수 `optionId` 추가 없음.
  - Docker QA: 코드 변경이 서비스 분기와 테스트 보강에 한정되어 있고 동일 흐름을 Spring Boot 통합 테스트와 REST Docs 테스트로 검증했으므로 별도 Docker API QA는 수행하지 않았다.

- #106 기도 운영 기간과 기도조 관리 조회 API 보강:
  - 작업 기준: Issue #106 `[Feat] 기도 운영 기간과 기도조 관리 조회 API 보강`, Project `FaithLog Backend Kanban` Status/Kanban Status `In Progress`, 브랜치 `feat/106-prayer-season-group-management`.
  - TDD 실패 확인: 구현 전 `PrayerServiceTest`에 current season, groups, assignable members, duplicate active group assignment, enhanced board, `/me` save 테스트를 추가했고, 신규 service 메서드/result/command/ErrorCode 부재로 `compileTestJava` 27 errors 실패를 확인했다.
  - 구현 범위: admin current season 조회, season groups 조회, assignable members 조회, 같은 season active group 중복 배정 409 검증, weekly board `currentSeason/myGroupId/seasonId/submitted/editable` 보강, 사용자 본인 `/me` 기도제목 저장 API를 추가했다.
  - API 계약: current season은 `status=ACTIVE` 및 `endDate=null` 기준이고 없으면 `data=null`이다. 운영 중 season이 없으면 weekly board는 빈 응답을 반환한다. `/me` 저장은 기존 nullable content 정책을 유지하고 보강된 weekly board 응답을 반환한다.
  - Spring REST Docs: `prayer-season-current-success`, `prayer-season-current-member-forbidden`, `prayer-season-groups-get-success`, `prayer-season-assignable-members-get-success`, `prayer-group-members-duplicate-assignment-conflict`, `prayer-my-submission-save-success` snippets를 추가하고 `src/docs/asciidoc/index.adoc`에 반영했다.
  - 검증: focused prayer service/docs 테스트 성공, prayer package 전체 테스트 성공, `./gradlew test` 성공(267 tests / 0 failures / 0 errors / 1 skipped), `./gradlew build` 성공, `./gradlew asciidoctor` 성공, `git diff --check` 성공.
  - Docker smoke: 격리 project `faithlog-qa-106`으로 `scripts/qa_docker_compose_isolated.sh --project-name faithlog-qa-106` 실행, app health `UP` 확인, 같은 project만 `docker compose -p faithlog-qa-106 down`으로 정리했다. 다단계 API QA는 별도 관리자 role/fixture 세팅이 필요해 이번 검증에서는 수행하지 않았다.

- #104 커피 담당자 계좌 권한과 커피 투표 정산 흐름 보강:
  - 작업 기준: Issue #104 `[Fix] 커피 담당자 계좌 권한과 커피 투표 정산 흐름 보강`, Project `FaithLog Backend Kanban` Status/Kanban Status `In Progress`, 브랜치 `fix/104-coffee-duty-settlement-flow`.
  - PM 결정 기록: 커피 외 투표의 `menuId`는 400으로 금지하고, COFFEE 투표 사용자 옵션 추가는 `menuId` 전용 계약으로 고정했다. `docs/decision-log.md`에 2026-06-30 결정으로 기록했다.
  - TDD 실패 확인: 구현 전 `PollServiceTest`에 COFFEE close 정산 트리거와 COFFEE 사용자 옵션 `menuId` snapshot 테스트를 추가했고, `AddPollOptionCommand` 시그니처와 신규 ErrorCode 부재로 `compileTestJava` 실패를 확인했다.
  - 구현 범위: `PATCH /api/v1/admin/campuses/{campusId}/polls/{pollId}/close`에서 COFFEE 투표만 종료 후 `CoffeePollSettlementService`를 호출한다. CUSTOM 등 커피 외 투표는 종료만 수행한다. `POST /api/v1/campuses/{campusId}/polls/{pollId}/options`는 COFFEE 투표에서 `menuId` 기반 메뉴명/코드/가격 snapshot을 저장하고, 커피 외 투표의 `menuId` 및 COFFEE 투표의 content-only 요청을 400으로 차단한다.
  - 회귀 고정: #100 계좌/투표/정산 권한 테스트 묶음과 #39 정산 멱등/terminal 보호 테스트 묶음을 함께 실행해 active COFFEE duty USER의 COFFEE 범위 허용, PENALTY/CUSTOM/다른 캠퍼스 403 차단, close 후 중복 charge 방지, terminal charge 보호를 확인했다.
  - Spring REST Docs: 사용자 옵션 추가 request에 `content`/`menuId` 선택 필드를 문서화하고, `poll-user-option-add-coffee-menu-success`, `poll-user-option-add-menu-not-allowed-error`, `poll-user-option-add-coffee-content-only-error`, `poll-close-coffee-settlement-success` snippets를 추가했다. `src/docs/asciidoc/index.adoc`의 오래된 "content만 허용" 및 "close는 정산 안 함" 문구를 #104 정책으로 갱신했다.
  - 검증: focused poll service/docs 테스트 성공, billing/poll 권한 확장 focused 테스트 성공, `./gradlew test` 성공(259 tests / 0 failures / 0 errors / 1 skipped), `./gradlew build` 성공, `./gradlew asciidoctor` 성공, `git diff --check` 성공.
  - Docker/API QA: 격리 project `faithlog-qa104api`에서 health `UP` 확인 후 실제 HTTP API로 회원가입/로컬 QA용 MANAGER role 부여/캠퍼스 생성/ACTIVE 멤버 가입/COFFEE 담당 지정/COFFEE 계좌 등록 및 비활성화/PENALTY 계좌 등록 403/PENALTY 계좌 비활성화 403/다른 캠퍼스 COFFEE 계좌 등록 403/COFFEE 투표 생성/CUSTOM 투표 생성 403/일반 멤버 menuId 옵션 추가 snapshot(`AMERICANO_HOT`, 1,500원)/content-only 400/응답 저장/close 후 COFFEE charge 1건 1,500원 생성/중복 close 409/COFFEE admin charge 조회 200/PENALTY admin charge 조회 403/비멤버 403/무인증 401을 검증했다. QA 종료 시 `docker compose -p faithlog-qa104api down`으로 같은 project만 정리했고 volume은 삭제하지 않았다.

### 2026-06-29

- #100 커피 담당자 전용 권한과 내 담당 상태 조회:
  - 작업 기준: Issue #100 `[Feat] 커피 담당자 전용 권한과 내 담당 상태 조회 구현`, Project `FaithLog Backend Kanban` Status/Kanban Status `In Progress`, 브랜치 `feat/100-coffee-duty-access`.
  - TDD 실패 확인: 구현 전 focused test 묶음이 `CampusService.getMyCoffeeDutyAssignment(Long, Long)` 부재로 `compileTestJava` 실패했다.
  - 구현 범위: 로그인 응답과 `GET /api/v1/users/me`에 ACTIVE campusMemberships를 포함하고, `GET /api/v1/campuses/{campusId}/duty-assignments/me`를 추가했다. 활성 `COFFEE` 담당자 USER는 본인 캠퍼스의 `COFFEE` 계좌 등록/비활성화, `COFFEE` 투표 생성/마감/미응답자 조회, `paymentCategory=COFFEE` admin charge 조회만 사용할 수 있게 했다.
  - 제한 검증: `PENALTY` 계좌/청구, `CUSTOM` 등 커피 외 투표, 캠퍼스 멤버 관리, 관리자 대시보드, 서비스 ADMIN API는 커피 담당자 USER에게 계속 403으로 차단했다. 다른 캠퍼스 `COFFEE` 담당자의 접근도 403으로 차단했다.
  - 계약/문서: Spring REST Docs에 my-duty, coffee-duty 계좌 등록/비활성화, coffee-duty 투표 생성 스니펫을 추가하고 `src/docs/asciidoc/index.adoc`에 반영했다. Swagger 문서화 annotation 추가는 0건으로 확인했다.
  - #97 옵션 추가 리스크 보고: 현재 `POST /api/v1/campuses/{campusId}/polls/{pollId}/options`는 `{ "content": "새 항목" }`만 받으므로 사용자 추가 옵션이 `composeMenuCode=null`, `priceAmount=0`으로 저장된다. 커피 실주문/정산에 쓰려면 메뉴 카탈로그 기반 API/schema 결정을 별도로 받아야 한다.
  - 검증: focused service/controller 테스트 성공, REST Docs focused 테스트 성공, `./gradlew test` 성공(256 tests / 0 failures / 0 errors / 1 skipped), `./gradlew build` 성공, `./gradlew asciidoctor` 성공, `git diff --check` 성공.
  - Docker/API QA: 기본 `docker compose up -d --build postgres redis app`는 기존 local named volume credential mismatch로 `FATAL: password authentication failed for user "faithlog"`가 발생해 볼륨 삭제 없이 중단했다. 이후 별도 compose override/project `faithlog-qa100`로 격리된 Postgres/Redis/app을 올려 `GET /api/v1/health` `UP` 확인, 실제 HTTP API로 회원가입/ACTIVE 멤버 가입/COFFEE 담당 지정/로그인 및 users-me 멤버십/duty me/COFFEE 계좌 등록 및 비활성화/COFFEE 투표 생성/결과 및 COFFEE 청구 조회/커피 외 관리자 API 403/다른 캠퍼스 담당자 403을 검증했다. QA 스택은 `docker compose ... down`으로 정리했다.
  - PM 리뷰 보강: 기본 COFFEE 템플릿의 `allowUserOptionAdd=true`, 기본 COFFEE 템플릿 기반 poll의 true 복사, 직접 COFFEE poll omission/null 기본 true, 명시 false override 유지, CUSTOM omission 기본 false를 테스트로 고정했다.
  - PM 리뷰 보강 검증: 보강 테스트 RED 확인 후 수정, `./gradlew test --tests com.faithlog.poll.application.PollServiceTest` 성공, `./gradlew test --tests com.faithlog.poll.presentation.PollApiRestDocsTest` 성공, `./gradlew test` 성공(258 tests / 0 failures / 0 errors / 1 skipped), `./gradlew build` 성공, `./gradlew asciidoctor` 성공.

- #97 Flyway V2 migration 보강:
  - PM 결정: Supabase/Cloud Run 운영 DB에서 `V1__initial_schema.sql`이 이미 적용될 수 있으므로 #97 schema 변경은 V1 수정이 아니라 새 Flyway 버전으로 분리한다.
  - 변경 범위: `V1__initial_schema.sql`에서 #97 추가분(`poll_templates.allow_user_option_add`, `polls.allow_user_option_add`, `poll_options.user_added`, `poll_options.created_by_user_id`, `fk_poll_options_created_by_user`)을 제거하고, `V2__add_poll_user_option_fields.sql`을 추가했다.
  - migration 안전성: 기존 row가 있는 운영 DB에도 적용 가능하도록 boolean column은 `BOOLEAN NOT NULL DEFAULT FALSE`로 추가한다.
  - 검증: focused poll test 성공, `./gradlew test` 성공(249 tests / 0 failures / 0 errors / 1 skipped), `./gradlew build` 성공, `./gradlew asciidoctor` 성공.

- #97 PR #98 CI Flyway 테스트 보강:
  - 실패 원인: V2 migration 추가 후 GitHub Actions `PostgresFlywayMigrationTest`의 `result.migrationsExecuted == 1` 고정 assertion이 실제 실행 수 2와 맞지 않아 실패했다.
  - 보강 범위: 다중 migration 기준으로 `migrationsExecuted >= 2`, 현재 Flyway version `2` 이상, #97 신규 컬럼 4개와 `fk_poll_options_created_by_user` FK 존재를 PostgreSQL `information_schema`로 검증한다.
  - 실제 PostgreSQL 검증: 임시 `postgres:17` 컨테이너(`localhost:55432`, `faithlog_test`)에서 `FAITHLOG_RUN_POSTGRES_FLYWAY_TEST=true FLYWAY_TEST_JDBC_URL=jdbc:postgresql://localhost:55432/faithlog_test ./gradlew test --tests com.faithlog.deploy.PostgresFlywayMigrationTest --rerun-tasks` 성공.
  - 전체 검증: `./gradlew test` 성공(249 tests / 0 failures / 0 errors / 1 skipped), `./gradlew build` 성공, `git diff --check` 성공.

- #97 투표 종료와 사용자 항목 추가 구현:
  - 작업 기준: Issue #97 `[Feat] 투표 종료와 사용자 항목 추가 구현`, 브랜치 `feat/97-poll-close-user-option`.
  - TDD 실패 확인: 선행 커밋 `de0e40f test: #97 투표 종료와 사용자 항목 추가 실패 테스트 추가` 이후 `./gradlew test --tests com.faithlog.poll.application.PollServiceTest --tests com.faithlog.poll.presentation.PollApiRestDocsTest`가 `compileTestJava` 31 errors로 실패했다. 실패 원인은 `closePoll`, `AddPollOptionCommand`, `allowUserOptionAdd`, 신규 `ErrorCode`, REST Docs descriptor 미구현이었다.
  - 구현 범위: 관리자 투표 종료 API `PATCH /api/v1/admin/campuses/{campusId}/polls/{pollId}/close`, 사용자 옵션 추가 API `POST /api/v1/campuses/{campusId}/polls/{pollId}/options`, `poll_templates.allow_user_option_add`, `polls.allow_user_option_add`, `poll_options.user_added`, `poll_options.created_by_user_id`, 신규 ErrorCode 3개를 추가했다.
  - API 계약 유지: 투표 응답 request/response의 `optionIds` 배열과 `poll_response_options` 구조를 유지했다. 사용자 추가 옵션도 기존 응답 API에서 `optionIds`로 선택된다.
  - Spring REST Docs: 신규/변경 API snippets를 추가하고 `src/docs/asciidoc/index.adoc`에 `poll-create-with-user-option-add-success`, `poll-user-option-add-success`, `poll-user-option-add-duplicate-error`, `poll-close-success`, `poll-close-invalid-state-error` include를 추가했다.
  - focused 검증: `./gradlew test --tests com.faithlog.poll.application.PollServiceTest --tests com.faithlog.poll.presentation.PollApiRestDocsTest` 성공.
  - 전체 검증: `./gradlew test` 성공(249 tests / 0 failures / 0 errors / 1 skipped), `./gradlew build` 성공, `./gradlew asciidoctor` 성공, `git diff --check` 성공.
  - 정적 확인: Swagger 문서화 annotation 검색 0건. 금지어 검색은 허용된 내부 `optionId` 변수/접근자만 매칭됐고 API 요청 필드 단수 `optionId`는 추가하지 않았다. Controller는 DTO 응답을 반환하고 Entity 직접 반환을 추가하지 않았다.
  - 산출물: REST Docs snippet group은 101개로 확인됐고 `build/docs/asciidoc/index.html`이 생성됐다.
  - Docker QA: `QA_COMPOSE_PROJECT=faithlog-qa-97 ./scripts/qa_docker_compose_isolated.sh`를 실행했지만, script guard가 기존 `faithlog-postgres` 컨테이너를 compose project `faithlog` 소유로 감지해 중단했다. 정책상 기존 개발/PM stack을 임의로 중지하지 않으므로 Docker health smoke는 미수행으로 기록한다.

### 2026-06-24

- #94 Cloud Run 실서버 성능 측정과 병목 개선 시작:
  - 작업 기준: Issue #94 `[Perf] Cloud Run 실서버 성능 측정과 병목 개선`, Project `FaithLog Backend Kanban` Status `In Progress`, 브랜치 `perf/94-cloud-run-performance-tuning`.
  - 시작 기준: `origin/develop` 최신 커밋 `dd32bff perf: #90 성능 테스트와 커버리지 측정 기반 정리`에서 브랜치를 생성했다.
  - 대상 서버: `https://faithlog-549871256004.asia-northeast3.run.app`.
  - Cloud Run 설정 확인: 로컬 환경에 `gcloud`가 없어 CPU, memory, concurrency, min/max instances, timeout, service account, revision/image tag는 확인하지 못했다.
  - 도구 준비: 사용자 요청으로 Homebrew를 통해 로컬 `k6 v2.0.0`을 설치했다.
  - k6 보강: `performance/k6/read-baseline.js`에 인증 없는 `INCLUDE=health` 경로와 `endpoint_health` Trend를 추가했다. `INCLUDE=health`만 사용할 때는 `PERF_EMAIL`/`PERF_PASSWORD` 없이 `/api/v1/health`만 호출한다.
  - README 보강: Cloud Run health smoke, authenticated read baseline 예시, Cloud Run 결과 파일 분리 규칙, 확장 baseline 상한(`VUS=30`, `DURATION=5m`)을 `performance/k6/README.md`에 추가했다.
  - k6 Cloud Run health smoke 조건: base URL `https://faithlog-549871256004.asia-northeast3.run.app`, `ALLOW_REMOTE_LOAD=true`, `VUS=1`, `DURATION=30s`, `INCLUDE=health`, summary export `build/reports/k6/cloud-run-health-smoke.json`.
  - k6 Cloud Run health smoke 결과: 28 requests, 28 iterations, checks 56/56 성공, failure 0.00%, throughput 0.91696 req/s, avg 85.30ms, p50 63.24ms, p90 213.92ms, p95 224.61ms, p99 237.63ms, max 240.66ms.
  - Python urllib 보조 smoke 결과: `/api/v1/health` 30 samples, 30 successes, HTTP 200 30건, avg 284.04ms, p50 273.94ms, p95 327.55ms, p99 421.98ms, max 453.34ms.
  - authenticated read baseline 준비: PM이 제공한 관리자 계정을 런타임 환경 변수로만 사용했고, 비밀번호는 문서/커밋에 기록하지 않았다.
  - 운영 데이터셋 확인: `/api/v1/campuses/me`는 0건, `GET /api/v1/admin/campuses?page=0&size=10&sort=createdAt,desc`도 `totalElements=0`이었다. 운영 DB에 캠퍼스 데이터가 없어 campus-dependent 시나리오(`admin-dashboard`, `devotions`, `billing`, `polls`, `prayers`)는 대표 성능 측정 대상이 아니다.
  - 1차 authenticated baseline 관찰: `INCLUDE=auth,campuses,admin-dashboard,devotions,billing,polls`로 실행했지만 campusId가 없어 실제로는 `auth_login`과 `campuses_me`만 측정됐다. 이 silent skip을 막기 위해 `read-baseline.js`에 campus-dependent include 검증을 추가했다.
  - k6 보강 추가: 서비스 ADMIN 계정으로 캠퍼스 목록을 읽을 수 있도록 `INCLUDE=admin-campuses`와 `endpoint_admin_campuses` Trend를 추가했다. campus-dependent 시나리오는 `CAMPUS_ID`가 없으면 setup 단계에서 실패하도록 바꿨다.
  - k6 Cloud Run authenticated read baseline 조건: base URL `https://faithlog-549871256004.asia-northeast3.run.app`, `ALLOW_REMOTE_LOAD=true`, `VUS=10`, `DURATION=3m`, `THINK_TIME_SECONDS=1`, `INCLUDE=auth,campuses,admin-campuses`, summary export `build/reports/k6/cloud-run-read-baseline-admin-vus10-3m.json`.
  - k6 Cloud Run authenticated read baseline 전체 결과: 2,504 requests, 834 iterations, checks 5,008/5,008 성공, failure 0.00%, throughput 13.73 req/s, avg 388.53ms, p50 164.45ms, p90 971.09ms, p95 1,179.85ms, p99 1,554.33ms, max 7,811.97ms.
  - endpoint별 Cloud Run baseline: `auth_login` avg 801.51ms / p50 748.77ms / p95 1,450.59ms / p99 1,633.47ms / max 2,005.28ms, `campuses_me` avg 195.65ms / p50 103.31ms / p95 666.09ms / p99 985.75ms / max 7,811.97ms, `admin_campuses` avg 168.33ms / p50 98.99ms / p95 520.00ms / p99 990.24ms / max 4,036.25ms.
  - Cloud Run 병목 TOP 3(p95 기준): 1) `auth_login` 1,450.59ms, 2) `campuses_me` 666.09ms, 3) `admin_campuses` 520.00ms.
  - #90 local Docker 비교 기준: #90 개선 후 local Docker read baseline은 `VUS=30`, `DURATION=5m`, p95 906.29ms, p99 1,371.26ms, failure 0.00%; #94 health-only smoke는 시나리오가 달라 직접 개선율 비교 대상으로 사용하지 않는다.
  - 현재 판단: health-only p95 224.61ms, authenticated read p95 1,179.85ms, failure 0.00%다. 운영 DB가 empty dataset이라 DB/index 병목 evidence는 부족하고, VUS 10에서 `auth_login`이 p95 기준 최상위 병목이다. 다만 인증 보안 비용/BCrypt/JWT 정책은 임의 변경 금지 대상이므로 코드 변경 대상으로 삼지 않는다.
  - Cloud Run 리소스 판단: failure 0.00%이고 read-only empty dataset에서 p95 1.18s 수준이라 CPU/RAM/concurrency/min instances 증설 근거는 아직 부족하다. `gcloud` 접근 또는 Cloud Run metrics와 대표 데이터셋 read baseline이 필요하다.
  - 검증: `node --check performance/k6/read-baseline.js` 성공, `git diff --check` 성공, `./gradlew test` 성공(243 tests / 0 failures / 0 errors / 1 skipped), `./gradlew build` 성공, JaCoCo line 94.76% / branch 73.08% / class 97.63% / method 90.59%.
  - PM 승인 업데이트: 운영 Cloud Run 측정을 위해 `PERF_` prefix가 붙은 소규모 대표 PERF 데이터셋 생성을 승인받았다. 기존 운영 데이터 수정/삭제, prefix 없는 데이터 생성, 대량 write, 알림 대량 발송, 청구/결제 상태 대량 변경은 계속 금지된다.
  - PERF 데이터셋 생성 조건: base URL `https://faithlog-549871256004.asia-northeast3.run.app`, dataset id `PERF_20260624_CLOUDRUN_A`, `PERF_MEMBER_COUNT=30`, 실제 운영 API 우선 사용, 자격 증명은 런타임 환경 변수로만 사용했다. manifest는 ignored 경로 `build/reports/perf-data/PERF_20260624_CLOUDRUN_A.json`에 저장했고, 비밀번호/토큰/운영 계정 credential은 문서/커밋에 남기지 않는다.
  - PERF 데이터셋 개요: campus id `1`, campus name `PERF_20260624_CLOUDRUN_A Campus`, active members 31명(관리자 1명 + PERF test members 30명), payment accounts 2개(`PENALTY`, `COFFEE`), weekly devotion final submissions 13건, generated unpaid `PENALTY` amount 7,500, poll 2건(`OPEN` 1건, closed-target poll 1건은 생성 응답상 `SCHEDULED`), open poll responses 20건, prayer groups 3개와 prayer submissions 샘플을 생성했다. 생성 데이터는 `PERF_20260624_CLOUDRUN_A` prefix와 manifest로 식별 가능하다.
  - PERF 데이터셋 정리 상태: 기존 운영 데이터는 수정/삭제하지 않았고, 테스트 데이터 삭제도 직접 수행하지 않았다. API에 완전한 cascade cleanup이 없으므로 정리는 `PERF_20260624_CLOUDRUN_A` prefix와 manifest의 id 목록 기준으로 PM 승인 후 DB/Supabase 정리 계획을 세워 진행해야 한다.
  - k6 Cloud Run PERF dataset VUS 10/3m 조건: `ALLOW_REMOTE_LOAD=true`, `VUS=10`, `DURATION=3m`, `THINK_TIME_SECONDS=1`, `CAMPUS_ID=1`, `POLL_ID=1`, `INCLUDE=auth,campuses,admin-campuses,admin-dashboard,devotions,billing,polls,prayers`, summary export `build/reports/k6/cloud-run-perf-dataset-vus10-3m.json`.
  - k6 Cloud Run PERF dataset VUS 10/3m 전체 결과: 2,523 requests, 194 iterations, checks 5,046/5,046 성공, failure 0.00%, throughput 13.71 req/s, avg 646.90ms, p50 339.05ms, p90 1,918.56ms, p95 2,500.16ms, p99 3,779.87ms, max 23,677.76ms.
  - VUS 10/3m endpoint TOP 3(p95): 1) `auth_login` 4,094.62ms / p99 4,241.72ms, 2) `campuses_me` 3,254.32ms / p99 3,821.28ms, 3) `campus_detail` 1,222.19ms / p99 2,179.03ms. 그 다음은 `prayer_weekly_board` p95 1,017.06ms, `poll_results` p95 805.84ms, `admin_dashboard_summary` p95 776.29ms였다.
  - k6 Cloud Run PERF dataset VUS 30/5m 조건: VUS 10/3m과 동일한 endpoint set/dataset, `VUS=30`, `DURATION=5m`, summary export `build/reports/k6/cloud-run-perf-dataset-vus30-5m.json`.
  - k6 Cloud Run PERF dataset VUS 30/5m 전체 결과: 11,825 requests, 913 completed iterations, 15 interrupted iterations, checks 23,620/23,650 성공, failure 0.13%, throughput 35.76 req/s, avg 661.72ms, p50 289.12ms, p90 1,511.05ms, p95 2,124.40ms, p99 4,177.66ms, max 41,856.60ms.
  - VUS 30/5m endpoint TOP 3(p95): 1) `auth_login` 4,850.26ms / p99 18,679.02ms, 2) `campuses_me` 3,926.57ms / p99 18,485.26ms, 3) `campus_detail` 2,106.92ms / p99 3,090.14ms. 그 다음은 `admin_campuses` p95 1,884.17ms, `prayer_weekly_board` p95 1,835.86ms, `poll_results` p95 1,519.57ms, `admin_dashboard_summary` p95 1,514.43ms였다.
  - VUS 30/5m 실패 분석: k6 콘솔에 `read: can't assign requested address`가 반복되어 로컬 클라이언트 socket/ephemeral port exhaustion 의심이 있다. 실패는 `auth_login` status/access-token check 14건과 `prayer_weekly_board` 1건으로 집계됐고, Cloud Run 서버 429/5xx evidence로 단정하지 않는다. 임계값 `http_req_failed rate<0.01` 자체는 0.13%로 통과했다.
  - empty dataset 대비 PERF dataset 비교: VUS 10/3m 기준 empty dataset은 auth/campuses/admin-campuses만 측정되어 전체 p95 1,179.85ms, throughput 13.73 req/s였다. PERF dataset은 campus-dependent read API까지 포함해 p95 2,500.16ms, throughput 13.71 req/s였다. 시나리오와 dataset이 달라 성능 개선/악화율로 해석하지 않고 대표 read workload 기준선으로만 사용한다.
  - 병목 판단: p95/p99 기준 최상위는 `auth_login`과 `campuses_me`다. `auth_login`은 BCrypt/JWT 보안 정책과 직접 연결되어 성능 이유로 임의 변경 금지다. `campuses_me`는 #90에서 local Docker N+1을 이미 projection으로 개선했지만 Cloud Run에서는 인증/DB/네트워크/Cloud Run 리소스 영향을 분리할 추가 evidence가 부족하다. API 계약 변경 없는 추가 코드 개선은 로그/DB query/Cloud Run metrics 확인 후 판단한다.
  - Cloud Run 리소스 판단 업데이트: VUS 30/5m에서 throughput은 35.76 req/s, failure 0.13%로 허용 범위지만 p95 2.12s, p99 4.18s이고 `auth_login`/`campuses_me` tail latency가 길다. `gcloud`가 없어 CPU, memory, concurrency, min/max instances, timeout, service account, revision/image tag는 확인하지 못했다. 지금 단계에서 직접 증설하지 않고, Cloud Run metrics 확인 후 min instances 1 적용 여부와 CPU/concurrency 조정을 검토하는 것을 추천한다.
  - 코드 수정 상태: production API 성능 병목 evidence만으로는 DB schema/index/Flyway 또는 인증 정책 변경을 승인 없이 진행할 수 없어 앱 코드 최적화는 하지 않았다. 대신 k6 측정 도구에 health-only, admin-campuses, campus-dependent include guard, PERF dataset seed script, Cloud Run README 예시를 추가했다.
  - 개선 전/후 수치: #94에서 production 코드 최적화는 적용하지 않았으므로 동일 조건 개선 전/후 개선율은 산출하지 않는다. 현재 수치는 Cloud Run `PERF_20260624_CLOUDRUN_A` 기준선이다.
  - 이력서 문장 후보: `Cloud Run 운영 URL 기준으로 PERF_ prefix 대표 데이터셋(캠퍼스 1개, active members 31명, devotion submissions 13건, poll responses 20건, prayer groups 3개)을 API로 구성하고, k6 VUS 30/5분 read 중심 부하에서 11,825 requests, 35.76 req/s, failure 0.13%, p95 2,124.40ms를 측정해 auth_login/campuses_me/campus_detail 병목 우선순위를 도출했다.`
  - 다음 필요 입력: Cloud Run metrics 또는 로그 접근이 가능하면 CPU/RAM/concurrency/min instances와 tail latency 상관관계를 확인한다. DB 직접 EXPLAIN/인덱스 검토가 필요하면 PM 승인 후 별도 evidence 수집을 진행한다. PERF 데이터셋 정리는 prefix/manifest 기준 cleanup 계획을 승인받은 뒤 수행한다.
  - PM 정정 업데이트: `PERF_20260624_CLOUDRUN_A`는 당장 삭제하지 않고 보존한다. 현재 Cloud Run 설정은 CPU 1, memory 1GiB, concurrency 80, min instances 0, max instances 3이다. 이후 #94 Cloud Run 결과는 `min instances=0 baseline`으로 명확히 기록한다.
  - 해석 수정: min instances가 0이므로 cold start 또는 instance scale-up outlier가 p95/p99/max에 섞일 가능성이 있다. 이전의 "min instances 1 적용 중" 가정은 폐기한다. 다만 반복마다 `auth_login`을 수행한 기존 결과는 실제 모바일 사용 패턴보다 BCrypt/JWT 발급 부하를 과대표현할 수 있으므로 auth-heavy와 steady-state read를 분리한다.
  - k6 보강: `performance/k6/read-baseline.js`에 `AUTH_PATTERN=auth-heavy|steady-state`를 추가했다. `auth-heavy`는 반복마다 login을 포함하고, `steady-state`는 setup login 토큰을 재사용하며 `INCLUDE`에 `auth`가 섞이면 실패한다. setup login은 `endpoint_setup_auth_login`으로 분리해 반복 login metric과 구분한다.
  - Cloud Run steady-state VUS 10/3m 조건: Cloud Run `min instances=0 baseline`(CPU 1 / memory 1GiB / concurrency 80 / min 0 / max 3), dataset `PERF_20260624_CLOUDRUN_A`, `AUTH_PATTERN=steady-state`, `ALLOW_REMOTE_LOAD=true`, `VUS=10`, `DURATION=3m`, `THINK_TIME_SECONDS=1`, `CAMPUS_ID=1`, `POLL_ID=1`, `INCLUDE=campuses,admin-campuses,admin-dashboard,devotions,billing,polls,prayers`, summary export `build/reports/k6/cloud-run-steady-state-read-vus10-3m.json`.
  - Cloud Run steady-state VUS 10/3m 전체 결과: 6,841 requests, 570 iterations, checks 13,682/13,682 성공, failure 0.00%, throughput 33.32 req/s, avg 184.42ms, p50 116.65ms, p90 241.98ms, p95 309.43ms, p99 554.23ms, max 24,292.42ms. setup login 1회는 22,683.42ms로 길었고, 첫 read 일부에도 21~24초 max outlier가 나타나 min=0 cold start/scale-up 가능성을 시사한다.
  - Cloud Run steady-state VUS 10/3m endpoint TOP 3(p95, setup login 제외): 1) `prayer_weekly_board` 502.58ms / p99 588.71ms, 2) `poll_results` 474.22ms / p99 625.62ms, 3) `admin_dashboard_summary` 318.15ms / p99 538.55ms. 기존 auth-heavy VUS 10 TOP 3였던 `auth_login` 4,094.62ms, `campuses_me` 3,254.32ms, `campus_detail` 1,222.19ms와 달리 read API p95는 대부분 500ms 이하로 분리됐다.
  - Cloud Run steady-state VUS 30/5m 조건: VUS 10/3m과 동일한 dataset/auth pattern/endpoint set, `VUS=30`, `DURATION=5m`, summary export `build/reports/k6/cloud-run-steady-state-read-vus30-5m.json`. 직전 VUS 10 실행으로 서비스가 warmed 상태였을 수 있으므로 setup login 629.21ms와 read max 1.55s는 cold-start 없는 warmed baseline 성격이 강하다.
  - Cloud Run steady-state VUS 30/5m 전체 결과: 37,789 requests, 3,149 iterations, checks 75,578/75,578 성공, failure 0.00%, throughput 124.67 req/s, avg 155.40ms, p50 131.89ms, p90 232.89ms, p95 285.97ms, p99 446.20ms, max 1,548.43ms. 기존 auth-heavy VUS 30/5m은 11,825 requests, throughput 35.76 req/s, failure 0.13%, p95 2,124.40ms, p99 4,177.66ms였다.
  - Cloud Run steady-state VUS 30/5m endpoint TOP 3(p95, setup login 제외): 1) `prayer_weekly_board` 385.91ms / p99 617.00ms, 2) `poll_results` 338.68ms / p99 494.22ms, 3) `admin_dashboard_summary` 278.31ms / p99 447.81ms. `campuses_me`는 p95 235.45ms / p99 376.85ms로, auth-heavy의 p95 3,926.57ms / p99 18,485.26ms와 크게 달랐다.
  - auth-heavy vs steady-state 해석: 실제 사용 패턴에 가까운 steady-state read는 VUS 30/5m에서 p95 285.97ms, p99 446.20ms, failure 0.00%로 충분히 낮다. 기존 VUS 30 auth-heavy p95 2,124.40ms와 `auth_login` p95 4,850.26ms는 코드 전체 read 병목이라기보다 반복 로그인/BCrypt/JWT 발급과 CPU 1 환경에서의 동시 처리 경쟁 및 min=0 outlier가 섞인 별도 병목으로 분리한다.
  - Cloud Run 추천 후보: 설정 변경은 하지 않았다. 1차 추천은 비용/운영 승인 후 `min instances=1` 적용 및 동일 조건 재측정이다. 이유는 현재 baseline이 min 0이고 VUS 10 steady-state setup login 22.68초와 첫 read 20초대 max outlier가 cold start/scale-up 영향일 가능성이 있기 때문이다. 로그인 동시성이 제품상 중요하다면 CPU 2 검토도 후보지만, BCrypt cost/security policy 자체는 변경하지 않는다. steady-state warmed read p95가 낮아 concurrency 80을 즉시 낮춰야 한다는 근거는 아직 부족하며, queueing evidence가 Cloud Run metrics에서 확인될 때 concurrency 20~40 검토를 제안한다.
  - 개선 전/후 수치 업데이트: 앱 코드/API 계약/DB schema 변경은 없으므로 코드 개선 전후율이 아니라 시나리오 분리 효과로 기록한다. VUS 30 기준 auth-heavy 대비 steady-state는 p95 2,124.40ms -> 285.97ms(86.54% 낮음), p99 4,177.66ms -> 446.20ms(89.32% 낮음), throughput 35.76 req/s -> 124.67 req/s(248.60% 높음), failure 0.13% -> 0.00%다. 이는 "로그인 반복 부하 제거 후 실제 read steady-state" 비교이며 코드 최적화 효과가 아니다.
  - 재검증: `node --check performance/k6/read-baseline.js` 성공, `node --check performance/k6/seed-cloud-run-perf-data.mjs` 성공, `git diff --check` 성공, 제공된 운영 계정/비밀번호 문자열 narrow scan 매칭 없음, `./gradlew test` 성공, `./gradlew build` 성공.
  - 이력서 문장 후보 업데이트: `Cloud Run min instances=0 baseline(CPU 1, memory 1GiB, concurrency 80, max 3)에서 PERF_ 대표 데이터셋과 k6 VUS 30/5분 측정을 auth-heavy와 steady-state로 분리해, 반복 로그인 포함 시 p95 2,124.40ms/35.76 req/s였던 결과가 token reuse read 시나리오에서는 p95 285.97ms/124.67 req/s/failure 0.00%임을 검증하고 로그인 CPU-bound 부하와 read API 성능을 분리 진단했다.`
  - Cloud Run 사용자 설정 변경 후 재측정: `gcloud`가 없어 실제 변경 후 CPU/RAM/concurrency/min/max instances 값은 직접 확인하지 못했다. 사용자가 Cloud Run 설정을 수정했다는 전제에서 동일 `PERF_20260624_CLOUDRUN_A` dataset과 동일 k6 조건으로 재측정했다.
  - 변경 후 steady-state VUS 10/3m 결과: 8,557 requests, 713 iterations, failure 0.00%, throughput 46.68 req/s, avg 123.34ms, p50 104.58ms, p95 211.04ms, p99 301.55ms, max 814.93ms. setup login은 532.66ms로, 변경 전 22,683.42ms outlier가 재현되지 않았다.
  - 변경 후 steady-state VUS 30/5m 결과: 39,577 requests, 3,298 iterations, failure 0.00%, throughput 130.64 req/s, avg 144.29ms, p50 124.13ms, p95 257.51ms, p99 401.71ms, max 828.99ms. 변경 전 VUS 30 steady-state의 max 1,548.43ms보다 낮고 failure는 동일하게 0.00%다.
  - 변경 후 auth-heavy VUS 10/3m 결과: 2,991 requests, 230 iterations, failure 0.00%, throughput 15.98 req/s, avg 545.19ms, p50 216.13ms, p95 2,513.85ms, p99 4,229.95ms, max 4,971.28ms. `auth_login` p95는 4,499.83ms, p99 4,821.23ms다. 변경 전 auth-heavy VUS 10의 max 23,677.76ms outlier는 사라졌지만, 로그인 반복 부하 자체는 여전히 p95 4~5초 영역이다.
  - 변경 후 auth-heavy VUS 30/5m 보류: 변경 후 auth-heavy VUS 10만으로도 반복 로그인/BCrypt/JWT 발급 병목이 유지됨을 확인했다. VUS 30 auth-heavy는 운영 로그인 부하와 비용이 크고 이전 실행에서 로컬 socket exhaustion이 섞였으므로 PM 추가 승인 전 보류한다.

| 시나리오 | Before p95 | After p95 | Before p99 | After p99 | Before max | After max | Before failure | After failure | Before throughput | After throughput |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| steady-state VUS 10/3m | 309.43ms | 211.04ms | 554.23ms | 301.55ms | 24,292.42ms | 814.93ms | 0.00% | 0.00% | 33.32 req/s | 46.68 req/s |
| steady-state VUS 30/5m | 285.97ms | 257.51ms | 446.20ms | 401.71ms | 1,548.43ms | 828.99ms | 0.00% | 0.00% | 124.67 req/s | 130.64 req/s |
| auth-heavy VUS 10/3m | 2,500.16ms | 2,513.85ms | 3,779.87ms | 4,229.95ms | 23,677.76ms | 4,971.28ms | 0.00% | 0.00% | 13.71 req/s | 15.98 req/s |
| auth-heavy VUS 30/5m | 2,124.40ms | 보류 | 4,177.66ms | 보류 | 41,856.60ms | 보류 | 0.13% | 보류 | 35.76 req/s | 보류 |

  - 변경 후 endpoint 비교: steady-state VUS 30 기준 `prayer_weekly_board` p95 385.91ms -> 356.97ms, `poll_results` p95 338.68ms -> 309.49ms, `admin_dashboard_summary` p95 278.31ms -> 238.37ms, `campuses_me` p95 235.45ms -> 204.68ms로 주요 read endpoint가 전반적으로 소폭 개선됐다.
  - 변경 후 인프라 판단: steady-state read는 VUS 30/5m에서 p95 257.51ms, p99 401.71ms, max 828.99ms, failure 0.00%로 충분히 안정적이다. 첫 setup login 22.68초와 첫 read 20초대 outlier가 사라졌으므로 사용자가 적용한 Cloud Run 설정은 cold start/scale-up outlier 완화에 효과가 있었던 것으로 보인다. 추가 CPU/RAM/concurrency 조정은 steady-state read 기준으로는 당장 필요하다는 evidence가 부족하다. 다만 auth-heavy VUS 10에서 `auth_login` p95 4.50s가 남아 있으므로 로그인 동시성이 중요한 제품 요구라면 CPU 2 또는 로그인 endpoint 전용 scaling/queueing metrics 확인을 후속 후보로 둔다. BCrypt cost/security policy는 변경하지 않는다.
  - #76 `token_version` 운영 문의 분석: 코드 기준 service role 변경은 `AdminManagementService.changeUserRole` -> `User.changeRole` -> `increaseTokenVersion()` 경로로 증가한다. campus role 변경은 `CampusService.changeCampusRole`에서 실제 campus role이 바뀔 때 `userTokenVersionPort.increaseTokenVersion(targetMember.userId())`를 호출하고, `UserRepository`가 `CampusUserTokenVersionPort`를 구현해 `findById(userId).ifPresent(User::increaseTokenVersion)`로 증가시킨다.
  - `token_version`이 증가하지 않는 정상 no-op 조건: service role PATCH의 target role이 현재 role과 같으면 `User.changeRole`이 return하므로 증가하지 않는다. campus role PATCH의 target campus role이 현재 role과 같으면 `CampusService`의 `if (targetMember.campusRole() != command.campusRole())` 밖이라 증가하지 않는다. DB 직접 수정, 테스트의 `ReflectionTestUtils`, 또는 운영 DB 수동 변경은 token version 증가 로직을 거치지 않는다.
  - `token_version` 테스트 보강: `RoleTokenInvalidationIntegrationTest`에 service role 변경 후 DB `users.token_version = old + 1`, campus role 변경 후 DB `users.token_version = old + 1` 직접 assertion을 추가했다. 집중 테스트 `./gradlew test --tests com.faithlog.global.security.RoleTokenInvalidationIntegrationTest` 성공.
  - 변경 후 재검증: `node --check performance/k6/read-baseline.js` 성공, `node --check performance/k6/seed-cloud-run-perf-data.mjs` 성공, `git diff --check` 성공, 제공된 운영 계정/비밀번호 문자열 narrow scan 매칭 없음, `./gradlew test --tests com.faithlog.global.security.RoleTokenInvalidationIntegrationTest` 성공, `./gradlew test` 성공, `./gradlew build` 성공.

### 2026-06-23

- #90 성능 테스트와 테스트 커버리지 측정 및 개선 시작:
  - 작업 기준: Issue #90 `[Perf] 성능 테스트와 테스트 커버리지 측정 및 개선`, Project `FaithLog Backend Kanban` Status `In Progress`, 브랜치 `perf/90-performance-coverage-metrics`.
  - 시작 기준: `origin/main` 최신 커밋 `201c7b7 build: #25 Cloud Run CD와 v0.1.0 배포 버전 명시`에서 브랜치를 생성했다.
  - baseline 테스트 이슈: 변경 전 `./gradlew test`가 `PollServiceTest.create_poll_without_template_uses_direct_options_and_template_poll_copies_snapshots`에서 1건 실패했다. 원인은 테스트 fixture가 `2026-06-23T00:00:00Z`~`09:00:00Z`로 고정되어 현재 날짜와 겹치면서 생성 직후 `OPEN` 처리되는 시간 의존성 실패였다.
  - baseline 복구: 해당 fixture를 `2036-06-23T00:00:00Z`~`09:00:00Z`로 바꿔 `SCHEDULED` 의도를 유지했다. 단일 재현 테스트 성공 후 `./gradlew test` 성공(242 tests / 0 failures / 0 errors / 1 skipped).
  - JaCoCo 설정: Gradle `jacoco` plugin과 `jacocoTestReport` HTML/XML report 설정을 추가했다. coverage threshold는 사용자 승인된 목표가 없어 추가하지 않았다.
  - JaCoCo 검증: `./gradlew test jacocoTestReport` 성공. 리포트 경로는 `build/reports/jacoco/test/html/index.html`, `build/reports/jacoco/test/jacocoTestReport.xml`.
  - 커버리지 baseline: line 94.75% (5,058/5,338), branch 73.08% (646/884), class 97.62% (369/378), method 90.58% (1,395/1,540).
  - k6 준비: `performance/k6/read-baseline.js`와 `performance/k6/README.md`를 추가했다. 기본 target은 `http://localhost:8080`, 기본 부하는 `VUS=30`, `DURATION=5m`, 기본 endpoint는 `auth,campuses`이며, 원격 URL은 `ALLOW_REMOTE_LOAD=true` 없이는 실행을 막는다. Docker 기반 k6 실행을 위해 `host.docker.internal` 같은 Docker-local hostname도 local target으로 허용했다.
  - k6 실행 상태: 로컬 `k6` 바이너리는 없음. `grafana/k6:latest` Docker image도 로컬에 없고, `docker pull grafana/k6:latest`는 Docker credential helper 응답 지연으로 중단했다. Homebrew 설치 경로는 있으나 로컬 개발 도구 추가는 사용자 승인 전 진행하지 않았다.
  - Docker k6 smoke: `docker compose up -d --build postgres redis app`로 local stack을 올리고 Docker k6로 `VUS=1`, `DURATION=10s`, `INCLUDE=auth,campuses,admin-dashboard,devotions,billing,polls` smoke를 실행했다. 결과는 65 requests, 7 iterations, failure 0.00%, avg 48.59ms, p50 20.71ms, p95 162.95ms, p99 286.10ms, 6.37 req/s. 리포트 경로는 `build/reports/k6/docker-smoke.json`.
  - Docker k6 baseline 조건: local Docker Compose profile `docker`, PostgreSQL 17, Redis 7, `faithlog-app` current branch image, `SPRING_JPA_HIBERNATE_DDL_AUTO=update`, `SPRING_FLYWAY_ENABLED=false`, base URL `http://host.docker.internal:8080`, `VUS=30`, `DURATION=5m`, `THINK_TIME_SECONDS=1`, `INCLUDE=auth,campuses,admin-dashboard,devotions,billing,polls`, local test account `perf90-manager@example.com`, campusId `49`. 측정 당시 dataset은 users 164, campuses 49, campus_members 123, polls 34, charge_items 22, weekly_devotion_records 20, devotion_daily_checks 65, prayer_submissions 12.
  - Docker k6 baseline 전체 결과: 29,189 requests, 3,243 iterations, checks 58,378/58,378 성공, failure 0.00%, avg 199.83ms, p50 43.51ms, p95 917.06ms, p99 1,756.82ms, max 6,811.90ms, 95.87 req/s. endpoint별 Trend metric 포함 리포트 경로는 `build/reports/k6/read-baseline-local-docker-endpoints.json`.
  - Docker k6 endpoint별 baseline: `auth_login` avg 756.60ms / p50 589.66ms / p95 1,910.22ms / p99 4,320.95ms, `campuses_me` avg 582.49ms / p50 511.15ms / p95 1,381.89ms / p99 2,862.35ms, `campus_detail` avg 93.63ms / p50 40.39ms / p95 347.43ms / p99 1,009.41ms, `admin_dashboard_summary` avg 75.68ms / p50 43.11ms / p95 217.30ms / p99 633.08ms, `devotion_weekly_read` avg 66.04ms / p50 38.96ms / p95 185.21ms / p99 639.43ms, `devotion_monthly_summary` avg 60.81ms / p50 35.18ms / p95 165.90ms / p99 663.81ms, `billing_my_charges` avg 61.30ms / p50 34.87ms / p95 173.54ms / p99 586.71ms, `billing_my_summary` avg 53.69ms / p50 28.05ms / p95 163.11ms / p99 556.18ms, `poll_list` avg 48.16ms / p50 20.76ms / p95 136.12ms / p99 589.70ms.
  - 병목 후보 evidence: 동일 조건 baseline에서 `auth_login`과 `campuses_me`가 가장 높은 p95/p99를 보였다. `auth_login`은 인증/보안 동작에 걸친 변경 가능성이 있어 승인 전 기능 코드 변경을 보류한다. `campuses_me`는 읽기 API 병목 후보로 쿼리/DTO/projection/N+1 evidence를 추가 수집한 뒤 개선 여부를 판단한다.
  - PM 확정 기준: #90 성능 기준은 local Docker `VUS=30`, `DURATION=5m`, failure `<1%`, 주요 개선 지표 p95로 고정한다. 이력서/포트폴리오 문장은 반드시 local Docker 기준과 dataset 크기를 함께 명시한다. `auth_login`은 보안 검토 후속 후보로 남기고, #90 1차 개선 대상은 `campuses_me`로 확정했다. API 계약 변경, DB schema/index 변경, Cloud Run 고부하, write API load test 확장은 #90에서 제외한다.
  - `campuses_me` 병목 재현: `CampusService.getMyCampuses`가 active membership 목록 조회 후 membership마다 `CampusRepository.findById`를 호출하는 구조라 3개 가입 사용자 기준 user 조회 1회 + membership 조회 1회 + campus 단건 조회 3회로 N+1이 발생했다. 신규 회귀 테스트 `CampusServiceTest.getMyCampuses_fetches_memberships_and_campuses_without_per_membership_lookup`는 최적화 전 `./gradlew test --tests com.faithlog.campus.application.CampusServiceTest.getMyCampuses_fetches_memberships_and_campuses_without_per_membership_lookup`에서 statement count 제한 초과로 실패했다.
  - `campuses_me` 개선 내용: API path/request/response/error code 변경 없이 `CampusMembershipRow` projection과 `CampusMemberRepository.findMembershipRowsByUserIdAndStatusOrderByIdDesc` JPQL join 조회를 추가했다. service는 projection을 `CampusMembershipResult`로 매핑하도록 바꿔 active membership + campus 응답 데이터를 한 번의 repository query로 조회한다. DB schema, index, Flyway migration, Entity schema 변경은 하지 않았다.
  - `campuses_me` 회귀 검증: 신규 query-count 테스트 성공, `./gradlew test --tests 'com.faithlog.campus.*'` 성공. 테스트는 3개 가입 사용자 조회가 membership별 campus 단건 조회 없이 제한된 statement count 안에서 끝나는지 검증한다. 최종 `./gradlew test jacocoTestReport`는 243 tests / 0 failures / 0 errors / 1 skipped로 성공했고, 최신 coverage는 line 94.76% (5,062/5,342), branch 73.08% (646/884), class 97.63% (370/379), method 90.59% (1,396/1,541)이다.
  - Docker k6 재측정 조건: baseline과 동일한 local Docker dataset(users 164, campuses 49, campus_members 123, polls 34, charge_items 22, weekly_devotion_records 20, devotion_daily_checks 65, prayer_submissions 12), base URL `http://host.docker.internal:8080`, `VUS=30`, `DURATION=5m`, `THINK_TIME_SECONDS=1`, `INCLUDE=auth,campuses,admin-dashboard,devotions,billing,polls`. 리포트 경로는 `build/reports/k6/read-after-campuses-me-local-docker.json`.
  - Docker k6 재측정 전체 결과: 29,036 requests, 3,226 iterations, checks 58,072/58,072 성공, failure 0.00%, avg 199.41ms, p50 64.66ms, p95 906.29ms, p99 1,371.26ms, max 6,456.37ms, 95.53 req/s. 전체 p95는 917.06ms에서 906.29ms로 1.17% 개선됐다.
  - `campuses_me` 개선 전후: avg 582.49ms -> 522.84ms(10.24% 개선), p50 511.15ms -> 461.72ms(9.67% 개선), p95 1,381.89ms -> 1,170.56ms(15.29% 개선), p99 2,862.35ms -> 1,828.76ms(36.11% 개선), max 5,982.94ms -> 4,653.41ms(22.22% 개선). failure는 전후 모두 0.00%.
  - 이력서 문장 후보: `local Docker dataset(users 164, campus_members 123) 기준 k6 VUS 30/5분 부하 테스트를 구축하고, /api/v1/campuses/me N+1 조회를 JPQL projection으로 개선해 p95 응답 시간을 1,381.89ms에서 1,170.56ms로 15.29%, p99를 2,862.35ms에서 1,828.76ms로 36.11% 단축했다.`
  - 후속 후보: `auth_login`은 p95 1,910.22ms baseline으로 가장 느린 후보였지만 password hash/security cost 정책과 연결되므로 별도 보안 검토 이슈로 분리한다. write API load test는 fixture/멱등성/부작용 관리가 필요하므로 후속 이슈로 분리한다. `campuses_me` 추가 개선은 운영 규모 dataset과 EXPLAIN 근거를 확보한 뒤 index/Flyway migration 승인 여부를 PM에 다시 질문한다.

### 2026-06-22

- #46 Flyway 마이그레이션과 Supabase/Cloud Run 배포 DB 설정 정리:
  - 브랜치: `build/46-flyway-supabase-deploy-db`
  - PM 결정: 새 Supabase PostgreSQL DB 기준으로 시작. Google Cloud Run 컨테이너 런타임 기준으로 문서화하며, Nginx/Certbot/직접 80/443 포트 구성은 진행하지 않는다. 실제 GCP 프로젝트/리전/서비스명/Artifact Registry/secret 등록은 후속 PM 승인 전까지 placeholder와 env 계약만 남긴다.
  - 구현 범위: Flyway 의존성 추가, `V1__initial_schema.sql` 초기 스키마 1개 추가, Notion ERD `Ref` 관계 FK 반영, `charge_items.source_id` polymorphic reference FK 제외, `application-prod.yml` 제거 및 `application-prod.example.yml`/`.env.example` placeholder 계약 정리.
  - 배포 계약: Cloud Run `PORT`, Supabase datasource env, Hikari max pool size, Flyway enabled, JPA validate, Redis, JWT, Firebase Admin JSON/path, springdoc 비활성화 env를 문서화. `docker-compose.yml` app env passthrough도 보강했다.
  - TDD 실패 확인: 구현 전 migration directory/dependency 부재 확인 및 `FlywayMigrationContractTest` 실패를 먼저 확인. compose env passthrough 누락은 신규 계약 테스트 실패로 재현한 뒤 수정했다.
  - 재검증: `./gradlew test` 성공(241 tests / 0 failures / 0 errors / 1 skipped), `./gradlew build` 성공, `./gradlew asciidoctor` 성공.
  - PostgreSQL/Flyway QA: `FAITHLOG_RUN_POSTGRES_FLYWAY_TEST=true`로 Docker PostgreSQL clean/migrate 테스트 성공. `flyway_schema_history`에 version `1`, description `initial schema`, success `true` 확인.
  - Docker QA: `faithlog-qa-46-migration` compose project로 postgres/redis/app 기동. 새 Docker image build `bootJar` 성공, app env passthrough 확인(`SPRING_FLYWAY_ENABLED=true`, springdoc false, scheduler false, `PORT=8080`), app 로그에서 Flyway schema version 1 확인, `GET /api/v1/health` 응답 `status=UP` 확인.
  - Secret/금지어 검사: `.env` 및 Firebase key JSON 파일 없음. 실제 Supabase URL/DB password/JWT/Firebase secret 원문 없음. 금지어 검색은 Hook 문서의 금지어 목록 자체, decision/policy 과거 설명, 내부 `optionId` 도메인 식별자만 확인.
  - 코드베이스 수치: Java 소스 421개, 테스트 파일 56개, Flyway migration 1개.
  - PM 보강 결정: 배포 Redis는 Upstash Redis를 사용하고, local/docker/test는 외부 Supabase/Upstash에 의존하지 않도록 분리한다. Dockerfile은 하나만 유지하고 `local`/`docker`/`test`/`prod` profile과 env로 런타임을 분리한다.
  - PM 보강 구현: `application-docker.yml`, `.env.local.example`, `.env.docker.example`, `.env.prod.example` 추가. `docker-compose.yml` 기본 profile을 `docker`로 변경하고 Docker Redis/PostgreSQL만 바라보게 유지. prod example/docs에 `SPRING_DATA_REDIS_HOST`, `SPRING_DATA_REDIS_PORT`, `SPRING_DATA_REDIS_PASSWORD`, `SPRING_DATA_REDIS_SSL_ENABLED` Upstash 계약을 추가했다.
  - PM 보강 TDD 실패 확인: `FlywayMigrationContractTest`에 Upstash/env split 계약 테스트를 추가한 뒤 문서/env/profile 부재로 2 tests failed를 먼저 확인했다.
  - PM 보강 재검증: `./gradlew test` 성공(242 tests / 0 failures / 0 errors / 1 skipped), `./gradlew build` 성공, `./gradlew asciidoctor` 성공, `git diff --check` 성공.
  - PM 보강 Docker QA: `faithlog-qa-46-upstash` compose project에서 Docker PostgreSQL + Docker Redis만 사용해 app 기동. 컨테이너 env는 `SPRING_PROFILES_ACTIVE=docker`, Redis host `redis`, Flyway true, JPA validate로 확인. Flyway V1 clean migration 성공, app started, `GET /api/v1/health` `UP`, QA 컨테이너/network 정리 완료.
  - PM 보강 secret scan: 실제 `.env`/Firebase key JSON 없음. 실제 Supabase URL, Upstash URL/password/token, DB password, JWT secret, Firebase secret 원문 없음. 검색 결과는 docs/test의 placeholder 문자열과 Hook 금지어 목록 자체만 확인.

- #84 QA Docker Compose 격리:
  - 작업 기준: Issue #84 `[Chore] QA Docker Compose 격리 실행 스크립트와 문서 정리`, Project `FaithLog Backend Kanban` Status `In Progress`.
  - 변경: `scripts/qa_docker_compose_isolated.sh` 추가. QA 전용 compose project name을 자동 생성하거나 `QA_COMPOSE_PROJECT`로 지정해 `postgres`, `redis`, `app`을 `docker compose -p <projectName> up -d --build`로 기동하고, app 내부 `/api/v1/health` 확인 후 같은 project name으로 `docker compose -p <projectName> down`을 수행한다.
  - 정책 기록: 기본 QA 절차에서 volume 삭제 플래그, 직접 volume 삭제, system-wide volume prune을 사용하지 않는 기준을 `docs/backend-implementation-policy.md`, `docs/decision-log.md`, `docs/wiki/troubleshooting.md`, `README.md`에 기록.
  - 검증: `bash -n scripts/qa_docker_compose_isolated.sh` 성공, `scripts/qa_docker_compose_isolated.sh --help` 성공, `git diff --check` 성공, `./gradlew test` 성공(236 tests / 0 failures / 0 errors / 0 skipped).
  - Docker QA: `QA_COMPOSE_PROJECT=faithlog-qa-84 ./scripts/qa_docker_compose_isolated.sh` 성공. Docker build `bootJar` 성공, `faithlog-qa-84_postgres-data`와 `faithlog-qa-84_redis-data`가 생성됐고 `postgres`/`redis` healthy, `app` started, app 컨테이너 내부 `GET /api/v1/health` 응답 `status=UP` 확인.
  - 종료 확인: 스크립트가 `docker compose -p faithlog-qa-84 down`으로 컨테이너와 network를 제거했고 `docker ps -a` 결과 남은 컨테이너 없음. `docker volume ls`에는 QA 전용 volume과 기존 default volume이 함께 남아 있어 volume 삭제 없이 격리 실행됐음을 확인.
  - 삭제 명령 확인: `docker compose down -v`, `docker volume rm`, `docker system prune --volumes`는 실행하지 않음.

- #79 투표 목록 조회 응답 여부 N+1 쿼리 개선:
  - 브랜치: `perf/79-poll-list-response-n-plus-one`
  - 구현 범위: `PollService.listPolls`가 visible poll 목록을 만든 뒤 `PollResponseRepository.findByPollIdInAndUserId(...)`로 현재 사용자 응답을 1회 bulk 조회하고, `respondedPollIds` set으로 `PollListItemResult.responded`를 계산하도록 변경했다.
  - API 계약: `GET /api/v1/campuses/{campusId}/polls` 경로, request/response field, 권한 정책, 진행 중/지난 투표 노출 정책, 일반 사용자 3일/관리자 7일 공개 기간 정책 변경 없음.
  - TDD 실패 확인: 구현 전 `./gradlew test --tests 'com.faithlog.poll.application.PollServiceTest.poll_list_marks_current_user_responses_without_per_poll_response_lookup'`가 `NeverWantedButInvoked`로 실패해 기존 `findByPollIdAndUserId` 단건 조회가 목록 poll마다 호출되는 것을 확인했다.
  - 회귀 테스트: 여러 poll 중 현재 사용자가 응답한 poll만 `responded=true`로 표시되는지 검증하고, 목록 조회 중 `findByPollIdInAndUserId` 1회 호출 및 기존 `findByPollIdAndUserId` 미호출을 검증했다.
  - 재검증: 신규 단일 테스트 성공, `./gradlew test --tests 'com.faithlog.poll.*'` 성공, `./gradlew test` 성공(236 tests / 0 failures / 0 errors / 0 skipped), `./gradlew build` 성공, `./gradlew asciidoctor` 성공.
  - 정적 검사: `git diff --check` 성공, Swagger 문서화 annotation 검색 0건, Controller Entity 직접 반환 신규 추가 없음, 금지어 검색은 기존 정책 문서 예시와 내부 `optionId` 식별자만 확인.
  - Docker 검증: `docker compose up -d --build postgres redis app` 성공, 컨테이너 내부 `GET /api/v1/health` 응답 `status=UP` 확인, `docker compose down` 성공.
  - 코드베이스 수치: Java 소스 421개, 테스트 파일 54개, REST Docs snippet group 96개.

- #76 역할 변경 시 기존 Access Token 무효화 정책 분리:
  - 정책 결정: MVP에서는 role 변경 후 이미 발급된 Access Token을 즉시 무효화하지 않고 기존 30분 TTL까지 허용한다.
  - 보안 강화 이슈: `[Security] 역할 변경 시 기존 Access Token 무효화 정책 구현`을 별도 추적 이슈로 생성했다.
  - 향후 검토 범위: tokenVersion, 세션 무효화, Redis blacklist/session 확장, service-level role 변경과 campus role 변경의 처리 기준.
  - 문서화 범위: `docs/decision-log.md`, `docs/backend-implementation-policy.md`, `docs/codex/FAITHLOG_CODEX_HOOK.md`.
  - 검증 계획: 문서-only 작업이므로 `./gradlew test`는 생략하고, `git diff --check`와 정책 문구 검색으로 검증한다.

- #76 역할 변경 시 기존 Access Token 무효화 구현:
  - 브랜치: `feat/76-role-token-invalidation`
  - 승인 정책: PM 확인 후 `users.token_version` 기반 즉시 무효화로 확정. Access Token에는 `tokenVersion` claim을 포함하고, 인증 필터는 token의 `userId/tokenVersion`과 DB의 현재 값을 비교한다. tokenVersion 불일치는 기존 `AUTH_UNAUTHORIZED` 정책을 재사용한다.
  - 구현 범위: `User.tokenVersion` 추가, Access Token claim 추가, `AccessTokenVersionChecker` port/adapter 추가, 인증 필터 tokenVersion 검증, service-level role 변경과 campus role 변경 시 대상 user tokenVersion 증가, refresh 재발급 시 최신 role/tokenVersion 반영.
  - TDD 실패 확인: 구현 전 `./gradlew test --tests com.faithlog.global.security.RoleTokenInvalidationIntegrationTest`가 신규 테스트 2건에서 Access Token `tokenVersion` claim 부재로 실패했다.
  - 회귀 테스트 범위: service role 변경 후 기존 Access Token 401, refresh 후 최신 role/tokenVersion 반영, campus role 변경 후 기존 Access Token 401, refresh 후 최신 campus role 반영, role 무효화 후 logout blacklist/refresh allowlist 충돌 없음.
  - Docker/API QA: `docker compose up -d --build postgres redis app` 후 실제 API로 signup/login, service `ADMIN` role 변경, 기존 token 401/`AUTH_UNAUTHORIZED`, refresh 후 `MANAGER` 반영, campus role `ELDER` 변경 후 기존 token 401, refresh 후 `myCampusRole=ELDER`, logout 후 access 401 및 refresh 401을 확인했다.
  - Docker QA 보강: local Docker profile에서 공개 auth endpoint가 401이 되던 문제를 `PathPatternRequestMatcher` 명시 matcher로 수정했고, 기존 Postgres volume에 `users.token_version`을 추가할 때 null 제약 실패가 나지 않도록 `bigint default 0` column definition을 보강했다.
  - 재검증: #76 집중 테스트 성공, 인증/admin/campus 관련 집중 테스트 성공, `./gradlew test` 성공(235 tests / 0 failures / 0 errors / 0 skipped), `./gradlew build` 성공, `./gradlew asciidoctor` 성공, `git diff --check` 성공.
  - 정적 검사: Swagger 문서화 annotation 검색 0건. Controller Entity 직접 반환 신규 추가 없음. 금지어 검색은 기존 내부 poll option 식별자와 정책 문서 예시만 확인.
  - 코드베이스 수치: Java 소스 421개, 테스트 파일 53개, REST Docs snippet group 96개.

- #74 전체 QA 후 정책 문서 정합성 정리:
  - 브랜치: `docs/74-policy-doc-consistency`
  - repo 문서 정리 범위: `docs/backend-implementation-policy.md`, `docs/codex/FAITHLOG_CODEX_HOOK.md`, `docs/decision-log.md`, `docs/resume-metrics.md`
  - 정책 정합성: Last active service `ADMIN` 강등 금지는 `409 ADMIN_LAST_ADMIN_DEMOTION_FORBIDDEN` 기준으로 확정 기록했고, 컴포즈커피 메뉴 seed는 공식 자료 우선 + 공식 검증 불가 시 사용자 승인 최신 자료 허용 기준으로 통일했다.
  - Notion 정리: 최종 기획서/API/ERD/통합 문서의 stale Last ADMIN, notification retry, old poll endpoint, invite-code refresh, campus member inactive endpoint, old ERD duplicate를 최신 기준 또는 Legacy 표시로 정리했다.
  - 검증 계획: 문서-only 작업이므로 `./gradlew test`는 생략하고, `git diff --check`와 정책/stale 문구 검색으로 검증한다.

- #72 전체 QA 발견 이슈 보강:
  - 브랜치: `fix/72-qa-issues`
  - 구현 범위: 관리자 직접 Poll 생성 시 `startsAt <= now < endsAt`인 현재 기간 poll을 생성 직후 `OPEN`으로 전환. 직접 선택지 생성과 템플릿 기반 생성 모두 보강했고, 아직 시작 전 poll은 `SCHEDULED`를 유지하며 scheduler의 보정/자동 전환 역할은 유지했다.
  - TDD 실패 확인: 구현 전 신규 `PollServiceTest` 회귀 테스트가 현재 기간 CUSTOM direct poll 및 템플릿 기반 COFFEE poll 생성 직후 status `SCHEDULED`로 실패하는 것을 확인.
  - 회귀 테스트 범위: 현재 기간 CUSTOM poll 생성 후 detail/response/results/comment CRUD 가능, 현재 기간 COFFEE template poll 응답 직후 `COFFEE charge_items` 0건, close/settlement 후 `COFFEE charge_items` 1건 유지, future poll `SCHEDULED`, ended poll response 기존 `POLL_CLOSED` 계약 유지.
  - REST Docs 보강: `src/docs/asciidoc/index.adoc`에 #57 monthly-summary, penalty-rules, poll results/comments/missing-members, prayer season/group/week/submission snippet include를 추가했다.
  - 문서 정합성: repo decision/policy/hook 문서와 Notion API/기획서/ERD의 stale coffee billing/old role hierarchy 문구를 최신 기준으로 정리. 커피 청구는 응답 시점이 아니라 CLOSED 커피 투표 정산 기준으로 생성한다는 기준을 재확인했다.
  - 재검증: `./gradlew test` 성공(232 tests / 0 failures / 0 errors / 0 skipped), `./gradlew build` 성공, `./gradlew asciidoctor` 성공, `git diff --check origin/develop...HEAD` 성공.
  - 정적 검사: Swagger 문서화 annotation 검색 0건. Controller Entity 직접 반환 검색은 DTO `PollResponse` 명칭만 확인됐고 Entity 직접 반환 신규 추가 없음. 금지어 검색은 hook/policy 문서의 검사 기준과 기존 내부 `optionId` 변수/도메인 식별자만 확인.
  - Docker/API QA: `docker compose up -d --build postgres redis app` 성공, `GET /api/v1/health` 200/`UP` 확인. 실제 API로 signup/login, MANAGER 승격, 캠퍼스 생성, 멤버 가입, COFFEE 담당자 지정, COFFEE 계좌 생성, 현재 기간 CUSTOM direct poll 생성 직후 `OPEN`, detail/response/results/comment CRUD 성공, 현재 기간 template-based poll 생성 직후 `OPEN`, future direct poll `SCHEDULED`, 현재 기간 COFFEE direct poll 생성 직후 `OPEN`, 응답 직후 COFFEE charge 0건, `ends_at` 과거 보정 후 scheduler close/settlement로 poll `CLOSED` 및 COFFEE charge 1건 생성을 확인했다. `docker compose down` 성공.
  - 코드베이스 수치: Java 소스 418개, 테스트 파일 52개, REST Docs snippet group 96개.

### 2026-06-21

- #23 캠퍼스 관리자 대시보드 통합 조회 API 구현:
  - 브랜치: `feat/23-campus-admin-dashboard-summary`
  - 구현 API: `GET /api/v1/admin/campuses/{campusId}/dashboard/summary`
  - 구현 범위: 캠퍼스 기본 정보, ACTIVE/비ACTIVE/관리자 멤버 수, `weekly_devotion_records.submitted_at` 기준 주간 제출/미제출/제출률, `charge_items.status=UNPAID` 기준 총액/미납자/`PENALTY`·`COFFEE` 카테고리별 금액, OPEN 투표 수, 최근 7일 CLOSED 투표 수, OPEN 투표 미응답자 수 총합.
  - 권한 정책: 서비스 전역 `ADMIN`은 모든 캠퍼스 접근 가능. 캠퍼스 내부 `ACTIVE + MINISTER/ELDER/CAMPUS_LEADER`만 해당 캠퍼스 조회 가능. 일반 `MEMBER`, 다른 캠퍼스 관리자, 전역 `MANAGER` 단독 사용자는 403/`ADMIN_DASHBOARD_ACCESS_FORBIDDEN`.
  - TDD 실패 확인: 구현 전 `./gradlew test --tests com.faithlog.admin.presentation.AdminDashboardControllerTest`가 신규 `/dashboard/summary` endpoint 부재로 3 tests failed 상태를 먼저 확인.
  - REST Docs 결과: `admin-dashboard-summary-success`, `admin-dashboard-summary-invalid-week-start-date` snippet 추가 및 `src/docs/asciidoc/index.adoc`의 `Campus Admin Dashboard` 섹션에 연결.
  - 재검증: dashboard controller/REST Docs 테스트 성공, `./gradlew test` 성공(228 tests / 0 failures / 0 errors / 0 skipped), `./gradlew build` 성공, `./gradlew asciidoctor` 성공.
  - 정적 검사: `git diff --check` 성공, Swagger 문서화 annotation 검색 0건, Controller Entity 직접 반환 신규 추가 없음, 금지어 검색은 허용된 hook/policy 문서 예시와 기존 poll option 내부 식별자만 확인.
  - Docker/API QA: `docker compose up -d --build postgres redis app` 성공, `GET /actuator/health` 200/`{"status":"UP"}` 확인. 실제 API와 Postgres fixture로 캠퍼스/멤버/권한, 경건생활 제출 3명·미제출 1명, 미납 `PENALTY 32000`·`COFFEE 22000`, OPEN poll 2건·최근 CLOSED poll 1건·미응답 총합 5명을 준비해 summary 응답을 검증했다. 서비스 `ADMIN` 200, `MEMBER`/다른 캠퍼스 관리자/전역 `MANAGER` 단독 403, non-Monday `weekStartDate` 400을 확인했고 `docker compose down` 성공.
  - 코드베이스 수치: Java 소스 418개, 테스트 파일 52개, REST Docs snippet group 96개.

- #24 배치와 스케줄러 기초 구현 시작:
  - 브랜치: `feat/24-batch-scheduler`
  - 구현 범위: Spring `@Scheduled` runner 설정, `faithlog.scheduler.enabled` 제어 플래그, `Asia/Seoul` 기준 PollTemplate 주간 자동 생성 application service, Redis scheduled lock 연결, 같은 캠퍼스/템플릿/주차 중복 생성 방지, OPEN 커피 Poll 마감 후 #39 `CoffeePollSettlementService` 호출, 90일 stale FCM token 비활성화 배치.
  - TDD 실패 확인: 구현 전 `./gradlew test --tests 'com.faithlog.batch.application.*'`가 `PollAutomationService`, `FcmTokenCleanupService`, `Poll.createdBy()` 부재로 `compileTestJava` 실패.
  - 집중 재검증: `./gradlew test --tests 'com.faithlog.batch.application.*'` 성공, 신규 batch application 테스트 6개 통과.
  - 전체 재검증: `./gradlew test` 성공(204 tests / 0 failures / 0 errors / 0 skipped), `./gradlew build` 성공.
  - 코드베이스 수치: Java 소스 373개, 테스트 파일 45개, REST Docs snippet group 83개.
  - PM 검증 보강 구현 범위: 자동 알림 3종(`DEVOTION_MISSING`, poll missing 5/3/2/1시간, `PAYMENT_UNPAID`), `notification_logs` 기반 오래 남은 `PENDING` 재처리, scheduler cron/fixedDelay 설정 노출, scheduler 활성화 시 `TaskExecutor` bean 충돌 수정.
  - 자동 알림 정책: 시스템 발송으로 `notification_logs`를 만들고, #41 `NotificationLockService` scheduled lock과 `NotificationDeduplicationService` business dedup을 사용한다. Redis 장애 시 자동 알림은 fail-closed로 로그를 만들지 않는다. 수동 버튼 알림은 자동 business dedup과 섞이지 않음을 테스트로 검증.
  - PENDING 재처리 정책: 10분 이상 `PENDING` request를 Redis lock으로 request 단위 1회 재처리하고, worker 처리 뒤에도 남은 PENDING은 `FAILED/PENDING_REPROCESS_FAILED`로 닫는다. worker 예외는 job 단위에서 삼키고 상태를 정리한다.
  - TDD 실패 확인: 구현 전 `./gradlew test --tests 'com.faithlog.batch.application.AutomaticNotificationServiceTest' --tests 'com.faithlog.batch.application.PendingNotificationRecoveryServiceTest'`가 `AutomaticNotificationService`, `PendingNotificationRecoveryService` 부재로 `compileTestJava` 실패. Docker QA 첫 시도에서 scheduler 활성화로 `applicationTaskExecutor`/`taskScheduler` 주입 충돌이 발생했고, `FaithLogSchedulerConfigTest`를 추가해 회귀 방지.
  - 집중 재검증: `./gradlew test --tests 'com.faithlog.batch.application.AutomaticNotificationServiceTest'` 성공, `./gradlew test --tests 'com.faithlog.batch.application.PendingNotificationRecoveryServiceTest'` 성공, `./gradlew test --tests 'com.faithlog.batch.scheduler.FaithLogSchedulerConfigTest'` 성공, `./gradlew test --tests 'com.faithlog.batch.*'` 성공.
  - 전체 재검증: `./gradlew test` 성공(214 tests / 0 failures / 0 errors / 0 skipped), `./gradlew build` 성공, `./gradlew asciidoctor` 성공, `git diff --check origin/develop...HEAD` 성공.
  - 정적 검사: Swagger 문서화 annotation 검색 0건, Controller Entity 직접 반환 의심 검색에서 신규 Entity 반환 없음 확인.
  - Docker QA: `docker compose up -d --build postgres redis app` 성공, 컨테이너 내부 `GET /actuator/health` `{"status":"UP"}` 확인. 호스트 `curl localhost:8080`은 현재 Codex 네트워크에서 연결 실패했지만 컨테이너 내부 health는 정상.
  - Docker scheduler QA: Postgres fixture로 due `PollTemplate` 1건과 11분 경과 `PENDING notification_logs` 1건을 삽입한 뒤 scheduler 1사이클 후 template 기반 `OPEN` poll 1건 생성 확인, PENDING log는 worker 재처리로 `SKIPPED/NO_ACTIVE_FCM_TOKEN` 전환 확인. 추가 1사이클 후 같은 template poll count가 1로 유지되어 같은 주차 중복 생성 방지 확인.
  - Docker QA 한계: 경건생활 미제출 11:00, 미납 12:00 cron은 현재 검증 시점이 일요일 오전이라 실제 cron 발화 대신 service 테스트로 월요일부터 대상 주차 계산, dedup, Redis fail-closed를 검증했다.
  - PM 보강 후 코드베이스 수치: Java 소스 375개, 테스트 파일 48개, REST Docs snippet group 83개.

- #45 조별 기도제목 조회와 입력 구현:
  - 브랜치: `feat/45-prayer-group-weekly-board`
  - 구현 범위: 활성 캠퍼스/시즌/기도조/주차 기준 조별 기도제목 조회, 조원별 `prayer_submissions` row 저장, nullable content 저장, optimistic version 충돌 처리, ACTIVE 시즌 중복 방지, 기도조 생성/수정/멤버 전체 교체, 일반 ACTIVE 멤버 자기 활성 기도조 저장 권한, 캠퍼스 관리자 전체 조 저장 권한.
  - API 범위: `POST /api/v1/admin/campuses/{campusId}/prayer-seasons`, `PATCH /api/v1/admin/prayer-seasons/{seasonId}/close`, `POST /api/v1/admin/prayer-seasons/{seasonId}/groups`, `PATCH /api/v1/admin/prayer-groups/{groupId}`, `PUT /api/v1/admin/prayer-groups/{groupId}/members`, `GET /api/v1/campuses/{campusId}/prayers/weeks/{weekStartDate}`, `PUT /api/v1/campuses/{campusId}/prayers/weeks/{weekStartDate}/submissions`.
  - TDD 실패 확인: 구현 전 `./gradlew test --tests com.faithlog.prayer.application.PrayerServiceTest`가 prayer domain/service/repository 부재로 `compileTestJava` 63 errors 실패. API 구현 전 `./gradlew test --tests com.faithlog.prayer.presentation.PrayerApiRestDocsTest`가 첫 `POST /api/v1/admin/campuses/{campusId}/prayer-seasons` route 부재로 assertion 실패.
  - 집중 재검증: `./gradlew test --tests com.faithlog.prayer.application.PrayerServiceTest` 성공, `./gradlew test --tests com.faithlog.prayer.presentation.PrayerApiRestDocsTest` 성공.
  - 전체 재검증: `./gradlew test` 성공(223 tests / 0 failures / 0 errors / 0 skipped), `./gradlew build` 성공, `./gradlew asciidoctor` 성공.
  - Docker/API QA: `docker compose up -d --build app` 성공, `GET /api/v1/health` 200/`UP` 확인, 실제 API로 MANAGER 승격 테스트 계정 생성, 캠퍼스 생성, 멤버 초대 가입, 시즌 생성, 중복 ACTIVE 시즌 409/`PRAYER_ACTIVE_SEASON_ALREADY_EXISTS`, 기도조 생성, 멤버 전체 교체 inactive/reactivate, 전체 조별 주간 조회, 일반 멤버 자기 조 저장, 일반 멤버 다른 조 저장 403/`PRAYER_SUBMISSION_FORBIDDEN`, 관리자 전체 조 저장, version 충돌 409/`PRAYER_SUBMISSION_CONFLICT`, 화요일 weekStartDate 400/`PRAYER_INVALID_WEEK_START_DATE`, 다음 월요일 `2026-06-29` 사전 저장을 확인했다.
  - DB QA: GET 전후 `prayer_weeks`/`prayer_submissions` row count가 `1/2 -> 1/2`로 유지되어 조회 row 미생성 확인. PUT 후 row count가 `2/4`로 증가해 필요한 week/submission row 생성 확인. version 충돌 batch는 `prayer_submissions` count `4 -> 4`, 충돌 batch의 다른 멤버 row `0`으로 rollback 확인.
  - 정적 검사: `NO_MEETING`은 `src/main/java`와 `src/test/java`에서 0건. 금지 패턴 검색은 기존 hook/policy 문서와 기존 poll option 식별자만 확인됐고 #45 신규 prayer 구현에서는 추가 없음.
  - 코드베이스 수치: Java 소스 415개, 테스트 파일 49개, REST Docs snippet group 94개.
  - PM 코드 리뷰 보강: `prayer_submissions` 기존 row 업데이트를 `id + expectedVersion` 조건부 update로 변경해 실제 동시수정 lost update를 차단했다. 두 트랜잭션이 같은 version 1을 읽고 동시에 update를 시도할 때 1건만 성공하고 1건은 `PRAYER_SUBMISSION_CONFLICT`로 처리되는 동시성 테스트를 추가했다.
  - PM 코드 리뷰 TDD 실패 확인: 구현 전 `./gradlew test --tests com.faithlog.prayer.application.PrayerSubmissionConcurrencyTest`가 `updateContentIfVersionMatches` repository method 부재로 `compileTestJava` 실패.
  - PM 코드 리뷰 재검증: 신규 동시성 테스트 성공, `PrayerServiceTest` 성공, `PrayerApiRestDocsTest` 성공, `./gradlew test` 성공(224 tests / 0 failures / 0 errors / 0 skipped), `./gradlew build` 성공, `./gradlew asciidoctor` 성공, `git diff --check origin/develop...HEAD` 성공.
  - PM 코드 리뷰 Docker/API QA: `docker compose up -d --build app` 성공, `GET /api/v1/health` 200/`UP`, 실제 API에서 version 1 업데이트 성공 후 stale version 1 재업데이트가 409/`PRAYER_SUBMISSION_CONFLICT`로 실패하고 최종 content/version이 `second`/`2`로 유지됨을 확인, `docker compose down` 성공.
  - PM 확인 필요: CLOSED 시즌의 기도조 생성/수정/조원 교체 차단 여부는 제품/API 정책 결정 전이라 구현하지 않았다.
  - PM 보강 후 코드베이스 수치: Java 소스 415개, 테스트 파일 50개, REST Docs snippet group 94개.
  - PM 재검증 보강: `PrayerSubmissionConcurrencyTest`가 남긴 `prayer_weeks`/`prayer_submissions` row 때문에 `PrayerApiRestDocsTest`의 전체 count 0 가정이 깨진 문제를 수정했다. REST Docs 테스트는 GET 전후 baseline count 불변 검증으로 바꾸고, 동시성 테스트는 자신이 만든 prayer week/submission row만 `@AfterEach`에서 정리한다.
  - PM 재검증 결과: 지정 #45 테스트 묶음 성공, `./gradlew test` 성공(224 tests / 0 failures / 0 errors / 0 skipped), `./gradlew build` 성공, `git diff --check origin/develop...HEAD` 성공, `./gradlew asciidoctor` 성공. asciidoctor 최초 샌드박스 실행은 Gradle wrapper lock 권한 문제로 실패했고 권한 상승 재실행으로 성공.

### 2026-06-20

- #41 Redis 알림 중복 방지와 알림 락 구현:
  - 브랜치: `feat/41-notification-redis-dedup-lock`
  - 구현 범위: `NotificationDeduplicationPort`, `NotificationLockPort`, Redis adapter, 자동 알림 dedup application service, notification dispatch lock, 수동 관리자 알림 실행 lock, Redis 장애 정책 테스트.
  - Redis key/TTL: 자동 dedup key는 `notification:dedup:{notificationType}:{campusId}:{scopeId}:{targetUserId}:{businessDate}`, 일 단위 TTL 25시간, 주차 단위 TTL 8일. 실행 lock key는 `notification:lock:{jobName}:{campusId}:{scopeId}`, 기본 TTL 10분, 긴 batch 작업은 custom TTL 지정 가능.
  - 장애 정책: 자동/스케줄 알림은 Redis 장애 시 fail-closed로 reserve/acquire 실패 처리. 수동 관리자 알림 API는 Redis lock 장애 시 `NOTIFICATION_REDIS_UNAVAILABLE` / 503으로 실패하고 `notification_logs`를 만들지 않음.
  - 수동/자동 분리: 수동 관리자 알림은 자동 business dedup으로 막지 않고, 동일 조건의 수동 알림도 별도 `request_id`와 `notification_logs`를 생성할 수 있음을 테스트로 검증.
  - #40 정책 보존: 기존 `POST /api/v1/admin/campuses/{campusId}/notifications`, `GET /api/v1/admin/campuses/{campusId}/notification-logs` 경로 유지. `notification_logs`의 `PENDING` / `SENT` / `FAILED` / `SKIPPED` 상태와 비동기 FCM worker/retry 정책 유지.
  - TDD 실패 확인: 구현 전 `./gradlew test --tests com.faithlog.notification.application.NotificationDeduplicationServiceTest --tests com.faithlog.notification.application.NotificationLockServiceTest --tests com.faithlog.notification.infrastructure.redis.RedisNotificationConcurrencyAdapterTest`가 신규 port/service/adapter와 `NOTIFICATION_REDIS_UNAVAILABLE` 부재로 `compileTestJava` 52 errors 실패.
  - 재검증: 신규 #41 테스트 묶음 성공, `./gradlew test --tests 'com.faithlog.notification.*'` 성공(37 tests / 0 failures), `./gradlew test` 성공(198 tests / 0 failures / 0 errors / 0 skipped), `./gradlew build` 성공, `./gradlew asciidoctor` 성공. asciidoctor 최초 샌드박스 실행은 Gradle wrapper lock 권한 문제로 실패했고, 권한 상승 재실행으로 성공.
  - Docker/API QA: `docker compose up -d --build postgres redis app` 성공, postgres/redis healthy 및 backend started 확인, 컨테이너 내부/호스트 `GET /actuator/health` 모두 `{"status":"UP"}` 확인. 실제 API QA로 signup/login, MANAGER 승격, 캠퍼스 생성, invite join, FCM token 등록, 관리자 CUSTOM 알림 2회 발송을 확인했고 두 요청 모두 `202 Accepted`, `queuedCount=1`, 서로 다른 `notificationRequestId`, 첫 요청 로그 1건 `SENT` 확인. `docker compose down` 성공.
  - 코드베이스 수치: Java 소스 369개, 테스트 파일 42개, REST Docs snippet group 83개.

- #40 FCM 토큰 등록과 알림 발송 로그 구현:
  - 브랜치: `feat/40-fcm-token-notification-log`
  - 구현 API: `POST /api/v1/users/me/fcm-tokens`, `DELETE /api/v1/users/me/fcm-tokens/{tokenId}`, `POST /api/v1/admin/campuses/{campusId}/notifications`, `GET /api/v1/admin/campuses/{campusId}/notification-logs`.
  - 구현 범위: `user_fcm_tokens` idempotent upsert/soft deactivate, 로그아웃 FCM 비활성화 port 실제 연결, `notification_logs` request_id 기반 PENDING/SENT/FAILED/SKIPPED 상태 기록, 관리자 알림 대상 계산, 90일 stale token 제외, FCM port/adapter 분리와 no-op 기본 adapter, commit 이후 `TaskExecutor` 기반 비동기 dispatch 연결.
  - 재시도 정책: transient failure는 토큰 단위 최대 3회 즉시 재시도(`1초 -> 5초 -> 30초`), permanent failure는 재시도 없이 token 비활성화, 오래 남은 PENDING 자동 재처리 스케줄러는 #40 범위에서 제외.
  - TDD 실패 확인: 구현 전 notification domain/service/controller/REST Docs 테스트가 미구현 클래스와 endpoint 부재로 실패하는 상태를 먼저 확인한 뒤 기능 코드를 추가.
  - 검증 범위: FCM 토큰 재등록 timestamp/appVersion 갱신, clientInstanceId token 교체, token 소유자 이전, owner 검증 deactivate, DELETE 204, logout port 연결, 90일 stale 제외, 관리자 권한 검증, 202 Accepted, 동일 request_id PENDING logs, commit 이후 비동기 dispatch 호출, CUSTOM target 필수, 자동 대상 계산, token 없음 SKIPPED, worker SENT/FAILED, 토큰별 실패 격리, transient retry, permanent failure token 비활성화, notification log requestId 필터.
  - REST Docs 결과: `notification-register-fcm-token`, `notification-deactivate-fcm-token`, `notification-send-admin-notification`, `notification-list-notification-logs` snippets 추가, 전체 snippet group 83개.
  - 재검증: notification 대상 테스트 성공, `./gradlew test` 성공(181 tests / 0 failures / 0 errors / 0 skipped), `./gradlew build` 성공, `./gradlew asciidoctor` 성공. asciidoctor 최초 샌드박스 실행은 Gradle wrapper lock 권한 문제로 실패했고, 권한 상승 재실행으로 성공.
  - Docker 검증: `docker compose up -d --build postgres redis app` 성공, 컨테이너 내부와 호스트 `GET /actuator/health` 모두 `{"status":"UP"}` 확인, 실제 API QA로 FCM token 등록/재등록 upsert/비활성화 204/새 token 등록/관리자 알림 발송 202/no-op FCM adapter 기반 백그라운드 worker `SENT` 갱신/requestId 로그 조회/서비스 MANAGER 단독 권한 실패 403 확인, `docker compose down` 성공.
  - 정적 검사: `git diff --check` 성공, Swagger 문서화 어노테이션 신규 추가 없음, Controller Entity 직접 반환 없음, 사용하지 않는 #40 legacy notification 경로 신규 추가 없음.
  - 코드베이스 수치: Java 소스 350개, 테스트 파일 36개, REST Docs snippet group 83개.
  - PM 검증 보강: Firebase Admin SDK 기반 `FirebaseMessaging.send(...)` adapter 추가, `local`/`test` profile 전용 NoOp fallback 분리, 그 외 profile credential 누락 시 startup fail-fast 처리, `UNREGISTERED`/token-not-registered/payload-valid invalid token permanent 매핑, rate limit/timeout/temporary error transient 매핑 테스트 추가.
  - PM 검증 보강: `NotificationDeliveryWorker`가 PENDING log/token snapshot 조회와 상태 저장만 짧은 transaction으로 수행하고, FCM 외부 호출 및 retry backoff는 DB transaction 밖에서 수행하도록 변경.
  - PM 검증 보강: `src/docs/asciidoc/index.adoc`에 #40 Notification API 4개 section과 snippet include 연결.
  - PM 보강 재검증: `./gradlew test --rerun-tasks` 성공(186 tests / 0 failures / 0 errors / 0 skipped), `./gradlew build` 성공, `./gradlew asciidoctor` 성공, `git diff --check` 성공, Swagger 문서화 어노테이션/ Firebase secret 검색 0건, Docker compose build/up health `UP`, `docker compose down` 성공.
  - PM 보강 후 코드베이스 수치: Java 소스 358개, 테스트 파일 38개, REST Docs snippet group 83개.

- #39 커피 투표 기반 청구 자동 생성 구현:
  - 브랜치: `feat/39-coffee-poll-charge-automation`
  - 구현 범위: 종료된 커피 투표 정산 application service, Poll-to-Billing port/adapter, Billing COFFEE create-or-update, 정책 문서/decision log 동기화.
  - 정산 기준: `poll_type=COFFEE`, `charge_generation_type=OPTION_PRICE`, `payment_category=COFFEE`, `status=CLOSED`, 최종 `poll_responses` + `poll_response_options`, `poll_options.price_amount`/`content` snapshot.
  - 청구 기준: `paymentCategory=COFFEE`, `sourceType=POLL_RESPONSE`, `sourceId=poll_responses.id`, `dueDate=null`, `title=poll_options.content`, `reason=컴포즈커피 주문`, `polls.payment_account_id`의 active same-campus COFFEE account snapshot.
  - TDD 실패 확인: 구현 전 `./gradlew test --tests com.faithlog.poll.application.PollServiceTest`가 `CoffeePollSettlementService`와 `POLL_SETTLEMENT_NOT_CLOSED` 부재로 `compileTestJava` 실패.
  - 검증 범위: OPEN 커피 투표 응답 시 charge 미생성, CLOSED 정산 시 charge 생성, non-CLOSED 실패, non-coffee poll no-op, source/category/amount/dueDate/account snapshot 검증, UNPAID 멱등 갱신/중복 방지, terminal charge 미덮어쓰기, 담당자 없음 실패, 계좌 없음/비활성/타입 불일치 실패 및 row 미생성, 중간 실패 시 poll 정산 전체 rollback.
  - REST Docs 결과: 기존 응답 API에 `coffee-poll-response-upsert-no-charge-success` snippet 추가. 별도 사용자용 커피 청구 생성 API는 추가하지 않음.
  - 재검증: `./gradlew test --tests com.faithlog.poll.application.PollServiceTest` 성공, `./gradlew test --tests com.faithlog.poll.presentation.PollApiRestDocsTest` 성공, `./gradlew test --tests com.faithlog.billing.application.BillingServiceTest` 성공, `./gradlew test` 성공(161 tests / 0 failures / 0 errors / 0 skipped), `./gradlew build` 성공, `./gradlew asciidoctor` 성공.
  - Docker 검증: `docker compose up -d --build postgres redis app` 성공, 컨테이너/호스트 health `status=UP`, 실제 API QA로 캠퍼스/커피 담당자/COFFEE 계좌/커피 투표 생성 후 OPEN 응답 저장 시 `poll_response_options` 1건 저장 및 `COFFEE charge_items` 0건 확인, `docker compose down` 성공.
  - 제외 범위 준수: 투표 응답 시점 청구 생성 금지, 별도 사용자용 커피 청구 생성 API 미구현, #24 Scheduler/Batch 연결 미구현, 커피 주문 취합/발주 API 및 결제 API 미구현, 점심/LUNCH 기능 미구현, Swagger 문서화 어노테이션 추가 없음.
  - 코드베이스 수치: Java 소스 315개, 테스트 파일 31개, REST Docs snippet group 79개.

- #38 투표 응답과 결과 조회 구현:
  - 브랜치: `feat/38-poll-response-result`
  - 구현 API: `GET /api/v1/campuses/{campusId}/polls`, `GET /api/v1/campuses/{campusId}/polls/{pollId}`, `PUT /api/v1/campuses/{campusId}/polls/{pollId}/responses/me`, `GET /api/v1/campuses/{campusId}/polls/{pollId}/results`, `GET /api/v1/admin/campuses/{campusId}/polls/{pollId}/missing-members`, 투표 댓글 CRUD.
  - 구현 모델: `PollResponse`, `PollResponseOption`, `PollComment`; 응답 묶음은 `poll_responses`, 선택지는 `poll_response_options`에 저장.
  - TDD 실패 확인: 구현 전 `./gradlew test --tests com.faithlog.poll.application.PollServiceTest`가 `PollResponseRepository`, `RespondToPollCommand`, `PollResultView`, poll 상세 error code 등 부재로 `compileTestJava` 실패.
  - 검증 범위: SINGLE 1개 선택, MULTIPLE 1개 이상 선택, 빈/중복/다른 poll option 실패, 기존 OPEN 응답 수정, CLOSED 응답 차단, 비익명 결과 응답자 노출, 익명 결과 응답자 식별 정보 숨김, 일반 3일/관리자 7일 visibility window, 공개 기간 만료 후 직접 조회 미노출, ACTIVE 멤버 기준 미참여자 조회, 댓글 작성/수정/삭제 권한, CLOSED 댓글 write 차단, soft delete 표시.
  - REST Docs 결과: #38 poll response/result/comment/missing-member snippets 추가, 전체 snippet group 77개.
  - 확정 ErrorCode 계약: `POLL_NOT_FOUND`, `POLL_OPTION_NOT_FOUND`, `POLL_RESPONSE_INVALID_SELECTION_COUNT`, `POLL_RESPONSE_DUPLICATE_OPTION`, `POLL_CLOSED`, `POLL_ACCESS_FORBIDDEN`, `POLL_ADMIN_FORBIDDEN`, `POLL_COMMENT_NOT_FOUND`, `POLL_COMMENT_FORBIDDEN`.
  - 재검증: `./gradlew test --tests com.faithlog.poll.application.PollServiceTest --tests com.faithlog.poll.presentation.PollApiRestDocsTest` 성공, `./gradlew test` 성공(152 tests / 0 failures / 0 errors / 0 skipped), `./gradlew build` 성공, `./gradlew asciidoctor` 성공. asciidoctor 최초 샌드박스 실행은 Gradle wrapper lock 권한 문제로 실패했고, 권한 상승 재실행으로 성공.
  - Docker 검증: `docker compose build app` 성공, `docker compose up -d` 성공, postgres/redis healthy, backend container started, 컨테이너 내부 `GET /actuator/health` 응답 `{"status":"UP"}` 확인. 현재 Codex host에서 `curl localhost:8080`/`127.0.0.1:8080`은 연결 실패했지만 컨테이너 내부 health는 정상.
  - 제외 범위 준수: #39 커피 청구 자동 생성/갱신 및 `charge_items.source_type=POLL_RESPONSE` 연결 미구현, #24 Scheduler/Batch 미구현, #37 투표 생성/템플릿 재구현 없음, 선택지 단위 결과 API 미구현, 익명 댓글 미구현, 점심 투표/주문/청구 미구현, Swagger 문서화 어노테이션 추가 없음.
  - 코드베이스 수치: Java 소스 310개, 테스트 파일 30개.

### 2026-06-19

- #61 서비스 ADMIN 유저와 캠퍼스 관리 구현:
  - 브랜치: `feat/61-service-admin-user-campus-management`
  - 구현 API: `GET /api/v1/admin/users`, `GET /api/v1/admin/users/{userId}`, `PATCH /api/v1/admin/users/{userId}/role`, `GET /api/v1/admin/campuses`, `POST /api/v1/admin/campuses/{campusId}/members`, 기존 `GET/PATCH /api/v1/admin/campuses/{campusId}/members...`, `PATCH /api/v1/campuses/{campusId}` 권한 회귀 검증.
  - 확정 계약: 사용자 목록/상세 응답 필드, 캠퍼스 목록 응답 필드, sort 허용 필드, 마지막 ADMIN 판정 기준(`users.role = ADMIN` and `users.is_active = true`), 이미 ACTIVE 소속 직접 추가 시 `CAMPUS_ALREADY_JOINED` 400.
  - TDD 실패 확인: 구현 전 `./gradlew test --tests com.faithlog.admin.presentation.AdminManagementControllerTest`가 새 관리자 endpoint 미구현으로 4 tests / 4 failed 실패.
  - 구현 구조: `admin` application/presentation 패키지를 추가하고 `AdminAccessPolicy`, 검색 criteria, result DTO, repository port를 분리. Controller는 Entity를 반환하지 않고 request/response DTO와 `PageResponse`를 사용.
  - 검증 범위: `ADMIN` 전용 `/api/v1/admin/**`, `USER`/`MANAGER` 접근 거부, 멤버십 없는 서비스 `ADMIN` 전체 조회/수정, 사용자 검색/role/page/size/sort, 사용자 상세 캠퍼스 소속 상태, `users.role` 단독 변경, 마지막 ADMIN 강등 차단, 캠퍼스 검색/운영상태 필터/page/size/sort, `is_active` 기반 `ACTIVE`/`PAUSED`, 직접 멤버 추가, `INACTIVE` 재활성화, ACTIVE 중복 추가 실패, 캠퍼스 역할 same-level 정책 유지, 캠퍼스 수정 권한.
  - REST Docs 결과: `admin-users-list-success`, `admin-user-detail-success`, `admin-user-role-change-success`, `admin-campuses-list-success`, `admin-campus-member-add-success`, `campus-update-success` snippets 추가, 전체 snippet group 57개.
  - 재검증: `AdminManagementServiceTest`, `AdminManagementControllerTest`, `AdminManagementApiRestDocsTest` 성공, `./gradlew test` 성공(138 tests / 0 failures / 0 errors / 0 skipped), `./gradlew build` 성공, `./gradlew asciidoctor` 성공. asciidoctor 최초 샌드박스 실행은 Gradle wrapper lock 권한 문제로 실패했고, 권한 상승 재실행으로 성공.
  - Docker 검증: `docker compose build app` 2회 시도 모두 `eclipse-temurin:21-jdk-alpine`/`21-jre-alpine` 베이스 이미지 metadata 조회 단계에서 `DeadlineExceeded: context deadline exceeded`로 실패. 컨테이너는 시작되지 않았고 `docker ps` 실행 결과 running container 0개.
  - 제외 범위 준수: audit log 저장/조회, `INVITED` 캠퍼스 상태, 신규 DB schema/enum, Swagger 문서화 어노테이션 추가 없음.
  - 코드베이스 수치: Java 소스 231개, 테스트 파일 28개.
- #57 내 월간 경건생활 통계 조회 구현:
  - 브랜치: `feat/57-my-monthly-devotion-summary`
  - 구현 API: `GET /api/v1/campuses/{campusId}/devotions/me/monthly-summary?year={year}&month={month}`
  - 구현 흐름: 현재 로그인한 사용자의 ACTIVE 캠퍼스 멤버십을 검증한 뒤, 선택 월의 첫날부터 마지막 날까지 `devotion_daily_checks.record_date` 기준으로 월간 합계와 주차별 부분 집계를 계산.
  - 구현 서비스/DTO: `DevotionMonthlySummaryQueryService`, `GetMyMonthlyDevotionSummaryQuery`, `MyMonthlyDevotionSummaryResult`, `MyMonthlyDevotionSummaryResponse`.
  - TDD 실패 확인: 구현 전 `./gradlew test --tests com.faithlog.devotion.application.DevotionServiceTest --tests com.faithlog.devotion.presentation.DevotionControllerTest --tests com.faithlog.devotion.presentation.DevotionApiRestDocsTest`가 `DevotionMonthlySummaryQueryService`, `GetMyMonthlyDevotionSummaryQuery`, `MyMonthlyDevotionSummaryResult`, `DEVOTION_INVALID_YEAR_MONTH` 부재로 `compileTestJava` 실패.
  - 검증 범위: `year/month` 검증, 비멤버 접근 거부, 본인 데이터만 조회, 월 경계 주차의 선택 월 날짜만 부분 집계, 토요일 날짜가 선택 월에 포함될 때만 `saturdayLateMinutes` 포함, 공통 `ApiResponse` envelope, Controller Entity 미반환.
  - REST Docs 결과: `devotion-my-monthly-summary-success`, `devotion-invalid-year-month` snippets 추가, 전체 snippet group 51개.
  - 재검증: #57 대상 테스트 묶음 성공, `./gradlew test` 성공(130 tests / 0 failures / 0 errors / 0 skipped), `./gradlew build` 성공, `./gradlew asciidoctor` 성공. asciidoctor 최초 샌드박스 실행은 Gradle wrapper lock 권한 문제로 실패했고, 권한 상승 재실행으로 성공.
  - Docker 검증: `docker compose up -d --build postgres redis app` 성공, postgres/redis healthy, app started, 컨테이너 내부 `GET /api/v1/health` 응답 `status=UP` 확인, `docker compose down` 성공. 호스트 `curl localhost:8080`은 현재 세션 네트워크에서 연결 실패했지만 컨테이너 내부 health는 정상.
  - 제외 범위 준수: #31 하루 체크/주간 제출 구현, #32 벌금 규칙/계산, #33 벌금 청구 자동 생성, 관리자 경건생활 집계 API, 신규 DB 스키마, Swagger 문서화 어노테이션 변경 없음.
  - 코드베이스 수치: Java 소스 207개, 테스트 파일 26개.
- #57 PM 리뷰 보강 - 월간 경건생활 연도 검증:
  - 문제: Docker QA에서 `GET /api/v1/campuses/10/devotions/me/monthly-summary?year=0&month=6` 요청이 `200 OK`로 성공하고 `year: 0`, 합계 0 응답을 반환하는 입력 검증 누락 확인.
  - TDD 실패 확인: 구현 전 `./gradlew test --tests com.faithlog.devotion.application.DevotionServiceTest --tests com.faithlog.devotion.presentation.DevotionControllerTest`가 27 tests / 2 failed로 실패. 서비스는 `year=0` 또는 음수 year 요청을 `DEVOTION_INVALID_YEAR_MONTH`로 거부하지 않았고, 컨트롤러도 `year=0&month=6` 요청을 400으로 반환하지 못함.
  - 수정: `DevotionMonthlySummaryQueryService.yearMonth()`에서 `year <= 0`을 먼저 검증해 `DEVOTION_INVALID_YEAR_MONTH` 400 계약을 적용하고, `month < 1 || month > 12`는 기존 `YearMonth.of` 검증으로 동일 에러 코드를 유지.
  - 추가 테스트: 서비스 `year=0`, 음수 year 실패 케이스, 컨트롤러 `year=0` 400 케이스, REST Docs query parameter 설명의 `year` 1 이상 및 `month` 1~12 범위 명시.
  - 재검증: 대상 테스트 묶음 성공. 병렬 Gradle 실행 중 test result writer의 `build/test-results/test/TEST-*.xml` 파일 쓰기 충돌이 있었으나, daemon 중지 후 `./gradlew --no-daemon test` 성공. `./gradlew --no-daemon build`, `./gradlew --no-daemon asciidoctor`, `git diff --check` 성공.
- #33 경건생활 제출 시 벌금 청구 자동 생성 구현:
  - 브랜치: `feat/33-devotion-penalty-charge-automation`
  - 구현 흐름: `PUT /api/v1/campuses/{campusId}/devotions/me/weeks/{weekStartDate}` 첫 `submit=true`에서 주간 요약 저장 후 `PENALTY` 청구 1건 자동 생성.
  - 청구 기준: `paymentCategory=PENALTY`, `sourceType=DEVOTION_RECORD`, `sourceId=weekly_devotion_records.id`, `status=UNPAID`, 활성 PENALTY 계좌 및 계좌 snapshot 저장.
  - TDD 실패 확인: 구현 전 `./gradlew test --tests com.faithlog.devotion.application.DevotionServiceTest`가 12 tests / 2 failed로 실패. 첫 제출 청구 미생성과 활성 계좌 없음 요청이 제출/일별 저장을 진행하는 문제를 확인. 중복 제출 에러 테스트는 `DEVOTION_WEEKLY_ALREADY_SUBMITTED` 부재로 `compileTestJava` 실패를 먼저 확인.
  - 수정: Devotion 애플리케이션 계층에 `DevotionPenaltyChargePort`/command를 두고 Billing 어댑터가 기존 `BillingService.createPenaltyCharge`를 호출하도록 분리. Devotion 도메인은 Billing Entity를 직접 참조하지 않는다.
  - 검증 범위: 첫 제출 청구 생성, `submit=false` 청구 미생성/미갱신, 제출 후 `submit=false` 저장 실패, 중복 `submit=true` 실패, 활성 PENALTY 계좌 없음 전체 실패 및 row 미생성, `weekly_devotion_records.id` sourceId 검증, account snapshot 검증, 음수 `saturdayLateMinutes` 차단 유지.
  - API 에러 계약: 계좌 없음 `BILLING_REQUIRED_PAYMENT_ACCOUNT_MISSING` 400 `관리자에게 문의하세요`, 중복 제출 `DEVOTION_WEEKLY_ALREADY_SUBMITTED` 409 `이미 제출된 주간 경건생활은 수정할 수 없습니다.`
  - API 응답 계약: `PUT /api/v1/campuses/{campusId}/devotions/me/weeks/{weekStartDate}` 성공 응답은 기존 `WeeklyDevotionResponse` 구조를 유지하고, `generatedCharges` 같은 청구 요약 필드는 추가하지 않음. 생성된 청구 확인은 기존 청구 조회 API를 사용.
  - REST Docs 결과: `devotion-missing-penalty-account`, `devotion-weekly-already-submitted` snippets 추가, 전체 snippet group 48개.
  - 재검증: `./gradlew test --tests com.faithlog.devotion.application.DevotionServiceTest` 성공, `./gradlew test --tests com.faithlog.devotion.presentation.DevotionControllerTest --tests com.faithlog.devotion.presentation.DevotionApiRestDocsTest` 성공, `./gradlew test` 성공(121 tests / 0 failures / 0 errors / 0 skipped).
  - 코드베이스 수치: Java 소스 203개, 테스트 파일 26개.
- #33 PM 리뷰 보강 - 제출 완료 주차 일별 체크 차단:
  - TDD 실패 확인: 구현 전 `./gradlew test --tests com.faithlog.devotion.application.DevotionServiceTest --tests com.faithlog.devotion.presentation.DevotionControllerTest --tests com.faithlog.devotion.presentation.DevotionApiRestDocsTest`가 32 tests / 3 failed로 실패. 서비스/컨트롤러/REST Docs가 제출 완료 주차 일별 체크를 아직 허용하는 문제를 확인.
  - 수정: `updateDailyCheck`에서 해당 `recordDate`의 주차 weekly row를 먼저 조회하고 `submittedAt`이 있으면 `DEVOTION_WEEKLY_ALREADY_SUBMITTED`로 차단한 뒤, 미제출 주차에만 weekly/daily row를 생성 또는 수정하도록 변경.
  - 검증 범위: 주간 제출 후 같은 주차 일별 체크 실패, 기존 daily row/weekly summary/charge count 불변, HTTP 409와 error code 계약, REST Docs 에러 snippet.
  - 재검증: 대상 테스트 묶음 성공, `./gradlew cleanTest test --no-parallel --max-workers=1` 성공(124 tests / 0 failures / 0 errors / 0 skipped), `./gradlew test` 성공, `./gradlew build` 성공, `./gradlew asciidoctor` 성공.
  - REST Docs 결과: `devotion-daily-check-already-submitted-week` snippet 추가, 전체 snippet group 49개.
- #32 경건생활 벌금 규칙과 벌금 계산 구현:
  - 브랜치: `feat/32-devotion-penalty-rules`
  - 구현 API: `GET /api/v1/campuses/{campusId}/penalty-rules`, `POST /api/v1/admin/campuses/{campusId}/penalty-rules`, `PATCH /api/v1/admin/penalty-rules/{ruleId}`
  - 구현 모델/서비스: `PenaltyRule`, `PenaltyRuleType`, `PenaltyCalculationType`, `PenaltyRuleRepository`, `PenaltyRuleService`, `DevotionFineCalculator`, 벌금 계산 input/item/result records.
  - TDD 실패 확인: 구현 전 `./gradlew test --tests 'com.faithlog.devotion.domain.DevotionFineCalculatorTest' --tests 'com.faithlog.devotion.application.PenaltyRuleServiceTest' --tests 'com.faithlog.devotion.presentation.PenaltyRuleApiRestDocsTest'`가 `PenaltyRule`, enum, repository, service, calculator, #32 error code 부재로 `compileTestJava` 실패. 구현 후 최초 runtime 검증에서 계산 테스트 기대값 산술 오류(3,100원 기대)를 발견하고 승인된 공식 기준에 맞춰 3,400원으로 테스트를 보정.
  - 검증 범위: 큐티/기도/말씀 부족 일수 `max(requiredCount - checkedCount, 0)`, `MISSING_COUNT` 금액, 토요 지각 0분 0원, 1분 이상 `baseAmount + minutes * amountPerUnit`, inactive rule 계산 제외, 같은 campus/ruleType ACTIVE 자동 교체, 수정/비활성화, 잘못된 rule/calculation 조합 400, 음수 기준값/금액 400, 관리자 권한, Controller DTO 응답.
  - 제외 범위 준수: #31 하루 체크/주간 제출 흐름 변경 없음, #33 `PENALTY charge_items` 생성/갱신 연결 없음, #34/#35 청구/계좌 상태 변경 없음, #57 월간 통계 및 벌금 미리보기 API/관리자 수동 청구 API/Flyway migration 추가 없음.
  - 재검증: #32 대상 테스트 묶음 성공, `./gradlew test` 성공(114 tests / 0 failures / 0 errors / 0 skipped), `./gradlew build` 성공.
  - Docker 검증: `docker compose up -d --build postgres redis app` 성공, `GET /api/v1/health` 200 `status=UP`, Docker Postgres QA 계정 1건을 `MANAGER`로 바꿔 캠퍼스 생성 후 #32 POST/GET/PATCH API 성공, `docker compose down` 성공.
  - REST Docs 결과: `penalty-rule-create-success`, `penalty-rule-list-success`, `penalty-rule-update-success`, `penalty-rule-invalid-type-pair`, `penalty-rule-invalid-negative-value` snippets 생성, 전체 snippet group 46개.
  - 코드베이스 수치: Java 소스 200개, 테스트 파일 25개.
- #31 주간 경건생활 제출과 일별 체크 구현:
  - 브랜치: `feat/31-weekly-devotion-daily-check`
  - 구현 API: `PUT /api/v1/campuses/{campusId}/devotions/me/days/{recordDate}`, `PUT /api/v1/campuses/{campusId}/devotions/me/weeks/{weekStartDate}`, `GET /api/v1/campuses/{campusId}/devotions/me/weeks/{weekStartDate}`, `GET /api/v1/admin/campuses/{campusId}/devotions/missing?weekStartDate={weekStartDate}`
  - 구현 모델/서비스: `WeeklyDevotionRecord`, `DevotionDailyCheck`, `DevotionService`, Command/Result DTO, member/admin devotion controllers
  - TDD 실패 확인: 구현 전 `./gradlew test --tests com.faithlog.devotion.application.DevotionServiceTest --tests com.faithlog.devotion.presentation.DevotionControllerTest`가 devotion 도메인/서비스/리포지토리 클래스 부재로 `compileTestJava` 실패.
  - 검증 범위: 하루 체크 daily/weekly row 생성 및 수정, 하루 체크의 `submittedAt`/청구 미변경, 주간 PUT 7일치 생성/수정, `dailyChecks` 필드, 제출 시 누락 날짜 false 기본값, 월요일 검증 400, `submit=true` 제출시각/요약값 갱신, 본인 주간 조회, 관리자 미제출자 조회 `submittedAt` 기준, ACTIVE 캠퍼스 멤버 권한, 캠퍼스 관리자 권한.
  - 제외 범위: 월간 경건생활 통계는 #57, 실제 `PENALTY charge_items` 생성/갱신은 #33으로 유지. #31 테스트는 `charge_items`가 생성되지 않음을 검증.
  - 재검증: #31 서비스/컨트롤러/REST Docs 테스트 성공, `./gradlew test` 성공(92 tests / 0 failures / 0 errors / 0 skipped), `./gradlew build` 성공, `./gradlew asciidoctor` 성공. asciidoctor 최초 샌드박스 실행은 Gradle wrapper lock 권한 문제로 실패했고, 권한 상승 재실행으로 성공.
  - Docker 검증: `docker compose build app` 성공, `docker compose up -d postgres redis app` 성공, postgres/redis healthy, app started, 컨테이너 내부 `GET /api/v1/health` 응답 `status=UP` 확인, `docker compose down` 성공. 호스트 `curl localhost:8080`은 현재 세션에서 연결 실패했지만 컨테이너 내부 health는 정상.
  - REST Docs 결과: devotion snippets 5개 묶음 추가(`devotion-daily-check-success`, `devotion-weekly-save-submit-success`, `devotion-my-week-success`, `devotion-admin-missing-success`, `devotion-invalid-week-start-date`), 전체 snippet group 37개.
  - 코드베이스 수치: Java 소스 183개, 테스트 파일 22개.
- #31 PM 리뷰 입력 검증 보강:
  - TDD 실패 확인: 검증 구현 전 `./gradlew test --tests com.faithlog.devotion.application.DevotionServiceTest --tests com.faithlog.devotion.presentation.DevotionControllerTest --tests com.faithlog.devotion.presentation.DevotionApiRestDocsTest`가 새 `DEVOTION_*` ErrorCode 부재로 `compileTestJava` 실패.
  - 수정: 주간 PUT에서 `dailyChecks[].recordDate`가 `weekStartDate`부터 6일 뒤까지만 허용되도록 검증하고, `saturdayLateMinutes < 0` 요청을 도메인 에러 코드로 400 처리.
  - 추가 에러 코드: `DEVOTION_DAILY_CHECK_DATE_OUT_OF_WEEK`, `DEVOTION_INVALID_SATURDAY_LATE_MINUTES`.
  - 추가 테스트: 서비스 검증 2개, 컨트롤러 400 응답 2개, REST Docs 에러 계약 2개.
  - 재검증: 대상 테스트 묶음 성공, `./gradlew test --rerun-tasks` 성공(98 tests / 0 failures / 0 errors / 0 skipped), `./gradlew build` 성공.
  - REST Docs 결과: `devotion-daily-check-date-out-of-week`, `devotion-invalid-saturday-late-minutes` snippet 추가, 전체 snippet group 39개.
- #31 빈 주간 경건생활 조회 기본 응답 결정 반영:
  - 문서 결정 포함: `origin/docs/31-devotion-planning-sync`의 `52b82b2 docs: #31 빈 주간 경건생활 조회 기본값 결정 기록`을 개발 브랜치에 병합.
  - TDD 실패 확인: 구현 전 `./gradlew test --tests com.faithlog.devotion.application.DevotionServiceTest --tests com.faithlog.devotion.presentation.DevotionControllerTest --tests com.faithlog.devotion.presentation.DevotionApiRestDocsTest`가 19 tests / 3 failed로 실패. 서비스는 기존 `DEVOTION_WEEKLY_RECORD_NOT_FOUND` 예외를 던졌고 HTTP/REST Docs는 기본 응답 기대에서 실패.
  - 수정: 본인 주간 조회에서 weekly row가 없으면 DB row를 생성하지 않고 월요일-일요일 7일치 false 기본 응답을 반환. `weeklyRecordId`, `submittedAt`, `dailyChecks[].id`는 null로 응답에 포함.
  - 추가 테스트: 서비스 기본 조회/DB 미생성 1개, 컨트롤러 200 기본 응답/DB count 불변 1개, REST Docs 기본 성공 계약 1개.
  - 재검증: 대상 테스트 묶음 성공, `./gradlew test --rerun-tasks` 성공(101 tests / 0 failures / 0 errors / 0 skipped), `./gradlew build` 성공.
  - REST Docs 결과: `devotion-my-week-default-success` snippet 추가, 전체 snippet group 40개.
- #31 Docker QA 부분 주간 조회 7일 합성 수정:
  - QA 증거: 빈 주간 조회는 7일 기본 응답과 DB row 미생성을 만족했지만, 하루 체크 후 주간 조회가 저장된 1일치(`2026-06-17`)만 반환.
  - TDD 실패 확인: 구현 전 `./gradlew test --tests com.faithlog.devotion.application.DevotionServiceTest --tests com.faithlog.devotion.presentation.DevotionControllerTest --tests com.faithlog.devotion.presentation.DevotionApiRestDocsTest`가 22 tests / 3 failed로 실패. 부분 주간 조회가 7일이 아니라 저장된 daily row만 반환하는 문제 확인.
  - 수정: 본인 주간 조회 결과 생성 시 weekly row가 있어도 월요일-일요일 7일치 `dailyChecks`를 합성. 저장된 날짜는 DB id/체크값을 사용하고, 누락 날짜는 `id = null`, false 기본값으로 반환. 조회 중 누락 daily row는 생성하지 않음.
  - 추가 테스트: 서비스 부분 주간 조회/DB 미생성 1개, 컨트롤러 부분 주간 7일 응답 1개, REST Docs 부분 주간 성공 계약 1개.
  - 재검증: 대상 devotion 테스트 묶음 성공, `./gradlew test --rerun-tasks` 성공(104 tests / 0 failures / 0 errors / 0 skipped), `./gradlew build` 성공.
  - REST Docs 결과: `devotion-my-week-partial-success` snippet 추가, 전체 snippet group 41개.
- #55 공통 에러 코드와 요청 검증 구조 리팩토링:
  - 브랜치: `refactor/55-error-validation-structure`
  - TDD 실패 확인: 세부 error code, 잘못된 `page`/`size`/`sort` 400 응답, Bean Validation 실패 code, billing/campus/auth 주요 예외 code, REST Docs 대표 에러 응답 계약 테스트를 먼저 추가. 구현 전 대상 테스트 묶음은 28 tests / 12 failed로 실패.
  - 수정: 글로벌 `ErrorCode`를 `AUTH_*`, `CAMPUS_*`, `BILLING_*`, `GLOBAL_*` 세부 code로 확장하고, `BusinessException`/`GlobalExceptionHandler`/Security entry point가 세부 code를 응답하도록 정리. `PageSortRequestValidator` 공통 컴포넌트로 page/size/sort 검증을 분리해 잘못된 값 자동 보정을 제거. `CampusRolePolicy`, `BillingAccessPolicy`, `ChargeStatusPolicy`로 비즈니스 규칙 검증을 분리.
  - 추가 운영 장치: `.githooks/commit-msg`를 추가해 `<분류>: #<이슈번호> <한국어 작업 요약>` 커밋 메시지 형식을 강제하고, 잘못된 메시지 실패/올바른 메시지 성공을 수동 검증.
  - 재검증: 수정된 테스트 묶음 44 tests 성공, `./gradlew test` 성공(82 tests / 0 failures / 0 errors / 0 skipped), `./gradlew asciidoctor` 성공. asciidoctor 최초 샌드박스 실행은 Gradle wrapper lock 권한 문제로 실패했고, 권한 상승 재실행으로 성공.
  - REST Docs 결과: 대표 에러 응답 snippet `error-response-detailed-code` 추가, 전체 snippet group 32개.
- #36 PM 재검토 sort 허용 목록 버그 수정:
  - 문제: 관리자 캠퍼스 청구 회원별 집계 조회에서 `sort=amount`, `sort=status`, `sort=paymentCategory` 같은 charge item 전용 정렬 필드가 유효 요청처럼 통과했지만 실제 정렬은 `latestChargeCreatedAt` default로 처리될 수 있었다.
  - TDD 실패 확인: `BillingControllerTest`에 `sort=amount,asc`는 `400 Bad Request`와 `지원하지 않는 정렬 기준입니다.`를 반환하고, `sort=unpaidAmount,desc`는 정상 동작해야 하는 테스트를 먼저 추가. 구현 수정 전 `./gradlew test --tests com.faithlog.billing.presentation.BillingControllerTest`가 8 tests / 1 failed로 실패.
  - 수정: `BillingPageRequests.adminMembers()` 허용 정렬 목록을 실제 회원 집계 comparator가 지원하는 `createdAt`, `userId`, `name`, `email`, `totalAmount`, `unpaidAmount`, `paidAmount`, `waivedAmount`, `canceledAmount`로 제한. 내 청구/관리자 회원별 상세 charge item 목록 정렬 허용 목록은 유지.
  - 재검증: `./gradlew test --tests 'com.faithlog.billing.*'` 성공, `./gradlew test` 성공(79 tests / 0 failures / 0 errors / 0 skipped), `./gradlew build` 성공, `./gradlew asciidoctor` 성공. asciidoctor는 샌드박스 wrapper lock 실패 후 권한 상승 재실행으로 성공.
- #36 PM 재검토 sort direction 검증 보강:
  - 문제: `sort=name,wrong`, `sort=createdAt,ascending`, `sort=unpaidAmount,foo` 같은 잘못된 direction 값이 조용히 `DESC`로 처리될 수 있었다.
  - TDD 실패 확인: `sort=unpaidAmount,wrong`과 `sort=createdAt,ascending`이 `400 Bad Request`와 `지원하지 않는 정렬 방향입니다.`를 반환해야 하는 컨트롤러 테스트를 먼저 추가. 구현 수정 전 `./gradlew test --tests com.faithlog.billing.presentation.BillingControllerTest`가 8 tests / 1 failed로 실패.
  - 수정: `BillingPageRequests.sort()`에서 direction이 없으면 기본 `DESC`를 유지하고, direction이 있으면 `asc` 또는 `desc`만 허용하도록 검증을 추가. 검증 로직은 page request helper 안에 유지.
  - 재검증: `./gradlew test --tests 'com.faithlog.billing.*'` 성공, `./gradlew test` 성공(79 tests / 0 failures / 0 errors / 0 skipped), `./gradlew build` 성공, `./gradlew asciidoctor` 성공. asciidoctor는 샌드박스 wrapper lock 실패 후 권한 상승 재실행으로 성공.
- #36 PM 재검토 sort 형식 검증 보강:
  - 문제: `sort=createdAt,desc,extra`, `sort=unpaidAmount,asc,ignored`처럼 `property,direction`보다 긴 malformed sort가 앞의 두 토큰만 사용되어 정상처럼 처리될 수 있었다.
  - TDD 실패 확인: malformed sort가 `400 Bad Request`와 `지원하지 않는 정렬 형식입니다.`를 반환해야 하는 컨트롤러 테스트를 먼저 추가. 구현 수정 전 `./gradlew test --tests com.faithlog.billing.presentation.BillingControllerTest`가 8 tests / 1 failed로 실패.
  - 수정: `BillingPageRequests.sort()`에서 comma token이 2개를 초과하면 `INVALID_REQUEST`로 차단. `sort=createdAt`, `sort=createdAt,asc`, `sort=createdAt,desc` 정상 동작은 유지.
  - 재검증: `./gradlew test --tests 'com.faithlog.billing.*'` 성공, `./gradlew test` 성공(79 tests / 0 failures / 0 errors / 0 skipped), `./gradlew build` 성공, `./gradlew asciidoctor` 성공. asciidoctor는 샌드박스 wrapper lock 실패 후 권한 상승 재실행으로 성공.

### 2026-06-18

- #36 청구 목록 조회와 캠퍼스별 집계 구현 검증:
  - 브랜치: `feat/36-charge-list-campus-summary`
  - 구현 API: `GET /api/v1/campuses/{campusId}/charges/me`, `GET /api/v1/campuses/{campusId}/charges/me/summary`, `GET /api/v1/admin/campuses/{campusId}/charges`, `GET /api/v1/admin/campuses/{campusId}/members/{userId}/charges`
  - 구현 모델/서비스: `BillingQueryService`, `ChargeSearchCriteria`, 조회 Query/Result records, 목록 전용 `ChargeListItemResponse`, 관리자 campus/member response DTO
  - 확정 계약: `startDate`/`endDate` 제거, 백엔드는 전체 이력 보존 + pagination/sort/filter 조회, 앱 기본 화면에서 최근 납부 항목 노출 UX 처리. 월별 요약은 `monthlyPaidAmount = paidAt 기준`, `monthlyUnpaidAmount/monthlyTotalChargeAmount/monthlyByCategory = createdAt 기준`.
  - TDD 실패 확인:
    - `./gradlew test --tests com.faithlog.billing.application.BillingQueryServiceTest` 실패: `BillingQueryService`, `MyChargeListQuery`, `MyChargesResult`, admin query/result records 부재로 `compileTestJava` 실패.
    - `./gradlew test --tests com.faithlog.billing.presentation.BillingControllerTest` 실패: 새 `GET /api/v1/campuses/{campusId}/charges/me` endpoint 미구현으로 기대 200 assertion 실패.
  - 테스트 결과: `./gradlew test --tests com.faithlog.billing.application.BillingQueryServiceTest` 성공, `./gradlew test --tests com.faithlog.billing.presentation.BillingControllerTest` 성공, `./gradlew test --tests com.faithlog.billing.presentation.BillingApiRestDocsTest` 성공, `./gradlew test` 성공(77 tests / 0 failures / 0 errors / 0 skipped)
  - 빌드/문서 결과: `./gradlew build` 성공, `./gradlew asciidoctor` 성공. asciidoctor 최초 샌드박스 실행은 Gradle wrapper lock 권한 문제로 실패했고, 권한 상승 재실행으로 성공.
  - Docker 검증: `docker compose build app` 성공, `docker compose up -d postgres redis app` 성공, postgres/redis healthy, app started, 컨테이너 내부 `GET /api/v1/health` 응답 `status=UP` 확인, `docker compose down` 성공. 호스트 `curl localhost:8080`은 현재 세션 네트워크에서 연결 실패했지만 컨테이너 내부 health는 정상.
  - REST Docs 결과: charge query snippets 4개 묶음 추가(`charge-my-list-success`, `charge-my-summary-success`, `charge-admin-campus-summary-success`, `charge-admin-member-detail-success`), 전체 snippet group 31개.
  - 검증 범위: 본인 ACTIVE 캠퍼스 멤버 청구 목록/요약, item `account` snapshot 객체와 `source` 객체, `paymentCategory`/`status` 필터, page/size/sort 기본값, 관리자 campus `summary + members[]` 집계만 반환, 관리자 회원별 상세 대상 회원 `userId/name/email`, 전역 `ADMIN` 허용, 서비스 `MANAGER` 단독 권한 거부, 일반 `MEMBER` 관리자 조회 거부.
  - 코드베이스 수치: Java 소스 157개, 테스트 파일 20개.
  - 금지어 검사: 실제 소스/테스트/API 문서에서 금지어 위반 0건. Swagger 문서화 어노테이션 추가 0건.
  - PM 재검증 보강: 관리자 캠퍼스 청구 조회 `status=UNPAID` 필터 회귀 테스트를 추가해 미납 청구가 있는 회원만 `members[]`에 포함되고, paid/waived/canceled만 있는 회원은 제외되며, 개별 `items`는 반환되지 않는 계약을 고정.
  - PM 재검증 문서화: GitHub Issue #36, Notion `16.1`, `16.4`, `16.5`, `API 설계`, `FaithLog 통합 기획서·ERD·API 설계`를 `startDate`/`endDate` 미사용 정책과 `summary + members`/회원별 상세 분리 기준으로 동기화.
  - PM 재검증 결과: `./gradlew test --tests 'com.faithlog.billing.*'` 성공, `./gradlew test` 성공(78 tests / 0 failures / 0 errors / 0 skipped), `./gradlew build` 성공, `./gradlew asciidoctor` 성공. asciidoctor는 샌드박스 wrapper lock 실패 후 권한 상승 재실행으로 성공.

- #35 청구 납부 완료, 면제, 취소 상태 관리 구현 검증:
  - 브랜치: `feat/35-charge-paid-waive-cancel`
  - 구현 API: `PATCH /api/v1/campuses/{campusId}/charges/me/{chargeItemId}/paid`, `PATCH /api/v1/admin/charges/{chargeItemId}/status`
  - 구현 모델/서비스: `ChargeItem.markPaid(Instant)`, `ChargeItem.waive`, `ChargeItem.cancel`, `ChargeItem.reopenAsUnpaid`, `BillingService.completeMyChargePayment`, `BillingService.changeChargeStatus`
  - TDD 실패 확인: 구현 전 `./gradlew test --tests 'com.faithlog.billing.*'`가 `CompleteChargePaymentCommand`, `ChangeChargeStatusCommand`, `paidAt`, 상태 전이 메서드 부재로 `compileTestJava` 실패.
  - 테스트 결과: `./gradlew test --tests 'com.faithlog.billing.*'` 성공, `./gradlew test` 성공, 67 tests / 0 failures / 0 errors / 0 skipped
  - 빌드 결과: `./gradlew build` 성공
  - 문서 렌더 결과: `./gradlew asciidoctor` 성공. 최초 샌드박스 실행은 Gradle wrapper lock 파일 권한 문제로 실패했고, 권한 상승 재실행으로 성공.
  - REST Docs 결과: charge status snippets 2개 묶음 추가 (`charge-my-paid-success`, `charge-admin-status-change-success`), 전체 snippet group 27개
  - 검증 범위: 본인 `UNPAID` 청구 즉시 `PAID`, `paidAt` 요청값 저장, `paidAt` 생략 시 서버 시간 저장, body 생략/빈 JSON 허용, 타인 청구/다른 캠퍼스/비활성 멤버 거부, terminal 청구 재납부 `409`, 관리자 `UNPAID -> WAIVED/CANCELED`, `PAID/WAIVED/CANCELED -> UNPAID`, `PAID -> UNPAID` 시 `paidAt` null, 관리자 `PAID` 처리 금지, 일반 `MEMBER`와 서비스 `MANAGER` 단독 권한 거부.
  - 코드베이스 수치: Java 소스 134개, 실구현 Java 파일 107개, 테스트 파일 19개.
  - 금지어 검사: 실제 소스/테스트/API 문서에서 금지어 위반 0건. 단수 API 필드 `optionId` 검색은 Hook 문서의 검사 예시 1건만 확인.
  - 범위 분리: 경건생활 자동 청구 연결은 #33, 커피 자동 청구 연결은 #39로 유지.
  - PM 리뷰 테스트 보강:
    - 추가 범위: 전역 `ADMIN`의 캠퍼스 멤버십 없는 관리자 청구 상태 변경 성공, `ELDER`/`CAMPUS_LEADER`의 관리자 청구 상태 변경 성공, `INACTIVE` 멤버의 본인 청구 납부 완료 API `403`.
    - 재검증: `./gradlew test --tests 'com.faithlog.billing.*'` 성공, `./gradlew test` 성공(70 tests / 0 failures / 0 errors / 0 skipped), `./gradlew build` 성공.

- #34 계좌와 청구 항목 관리 구현 검증:
  - 브랜치: `feat/34-payment-account-charge-item`
  - 구현 API: `GET /api/v1/campuses/{campusId}/payment-accounts`, `POST /api/v1/admin/campuses/{campusId}/payment-accounts`, `PATCH /api/v1/admin/payment-accounts/{accountId}/deactivate`
  - 구현 모델/서비스: `PaymentAccount`, `ChargeItem`, `PaymentCategory(PENALTY/COFFEE)`, `ChargeSourceType(DEVOTION_RECORD/POLL_RESPONSE)`, `ChargeStatus(UNPAID/PAID/WAIVED/CANCELED)`, `BillingService.createPenaltyCharge`
  - TDD 실패 확인: 구현 전 `./gradlew test --tests 'com.faithlog.billing.*'`가 billing 도메인/서비스/리포지토리 클래스 부재로 `compileTestJava` 실패. terminal 상태 보강 전 `markWaived`, `markCanceled` 부재 실패도 별도 확인.
  - 테스트 결과: `./gradlew test` 성공, 56 tests / 0 failures / 0 errors / 0 skipped
  - 빌드 결과: `./gradlew build` 성공
  - 문서 렌더 결과: `./gradlew asciidoctor` 성공. 최초 샌드박스 실행은 Gradle wrapper lock 파일 권한 문제로 실패했고, 권한 상승 재실행으로 성공.
  - Docker 검증: `docker compose build app` 시도 중 Docker daemon 응답 `Docker Desktop is unable to start`로 중단. 앱 이미지 빌드 전 로컬 Docker Desktop 상태 문제이며 코드 검증으로 완료하지 못함.
  - REST Docs 결과: payment account snippets 3개 묶음 추가 (`payment-account-create-success`, `payment-account-list-success`, `payment-account-deactivate-success`), 전체 snippet group 25개
  - 검증 범위: 캠퍼스별 accountType 활성 계좌 1개 정책, 새 활성 계좌 등록 시 기존 활성 계좌 자동 비활성화, ACTIVE 멤버 조회 허용, 비멤버/INACTIVE 멤버 조회 거부, 계좌 등록/비활성화 관리자 권한, 계좌번호 전체 노출, 일반 멤버 응답의 관리용 메타데이터 미노출, UNPAID 청구 재연결/snapshot 갱신, PAID/WAIVED/CANCELED snapshot 유지, 활성 PENALTY 계좌 누락 시 `관리자에게 문의하세요`, 계좌 누락 시 charge row 미생성, 청구 생성 시 snapshot 저장, 중복 청구 unique 제약.
  - 코드베이스 수치: Java 소스 129개, 실구현 Java 파일 102개, 테스트 파일 19개.
  - 금지어 검사: 실제 소스/README에서 금지어 및 단수 API 필드 `optionId` 위반 0건. Swagger 문서화 어노테이션 추가 0건.
  - 범위 분리: 실제 경건생활 제출 PENALTY 연결은 #33, 커피 투표 응답 COFFEE 연결은 #39로 유지.
  - PM 리뷰 수정:
    - 실패 확인: `./gradlew test --tests 'com.faithlog.billing.*'`가 4건 실패. 서비스 ADMIN 계좌 조회가 `BusinessException`/HTTP assertion failure로 실패했고, 같은 source의 `createPenaltyCharge` 재실행 2건이 unique constraint `DataIntegrityViolationException`으로 실패.
    - 수정 내용: 서비스 `ADMIN`의 계좌 조회 허용, 기존 `UNPAID` PENALTY 청구 create-or-update, 최신 active PENALTY 계좌 snapshot/title/reason/amount/dueDate 갱신. terminal 청구 재제출 정책은 아직 사용자 확정 전이므로 #34 foundation에서는 데이터 훼손 방지용 구현 가드로 덮어쓰기를 막고 명확한 예외를 반환.
    - 재검증: `./gradlew test --tests 'com.faithlog.billing.*'` 성공, `./gradlew test` 성공(59 tests / 0 failures / 0 errors / 0 skipped), `./gradlew build` 성공, `./gradlew asciidoctor` 성공.
    - Docker 재검증: `docker compose build app`가 Docker daemon 응답 `Docker Desktop is unable to start`로 동일하게 중단.

- #30 캠퍼스 멤버 역할/커피 담당자 관리 구현 검증:
  - 브랜치: `feat/30-campus-member-role-duty-assignment`
  - 구현 API: `GET /api/v1/admin/campuses/{campusId}/members`, `PATCH /api/v1/admin/campuses/{campusId}/members/{campusMemberId}/campus-role`, `GET /api/v1/admin/campuses/{campusId}/duty-assignments`, `PUT /api/v1/admin/campuses/{campusId}/duty-assignments/coffee`, `DELETE /api/v1/admin/campuses/{campusId}/duty-assignments/coffee/{assignmentId}`
  - 테스트 결과: `./gradlew test` 성공, 47 tests / 0 failures / 0 errors / 0 skipped
  - 빌드 결과: `./gradlew build` 성공
  - REST Docs 결과: `./gradlew asciidoctor` 성공, Spring REST Docs snippet group 22개 생성, admin campus snippets 5개 추가
  - 역할 변경 검증: `MINISTER`/`ELDER`/`CAMPUS_LEADER`의 same-level assignment, 상위 역할 변경/부여 거부, `MEMBER` 권한 없음, 전역 `ADMIN` 전체 변경 허용, 전역 `MANAGER` 단독 권한 없음, 마지막 관리 역할 `MEMBER` downgrade 허용.
  - 커피 담당자 검증: `CampusDutyAssignment + DutyType.COFFEE`로 분리, 새 담당자 지정 시 기존 활성 담당자 inactive 처리, 활성 담당자 목록 1명 유지, 해제 시 inactive/revoked 처리, non-`MEMBER` 캠퍼스 역할 및 전역 `ADMIN` 관리 허용.
  - 동시성 보강: concurrent `PUT /duty-assignments/coffee` 상황을 재현하는 `CampusDutyAssignmentConcurrencyTest` 추가. 구현 전에는 active row 중복으로 `NonUniqueResultException` 실패를 확인했고, 캠퍼스 row `PESSIMISTIC_WRITE` lock 적용 후 동시 12개 지정 요청에서도 active coffee assignment 1개 유지 검증.
  - 코드베이스 수치: Java 소스 110개, 실구현 Java 파일 83개, 테스트 소스 14개, 테스트 리소스 1개.
  - Docker 검증: `docker compose build app` 성공, `docker compose up -d postgres redis app` 성공, postgres/redis healthy, app container started, `GET /api/v1/health` 응답 `status=UP` 확인.
  - 문서 동기화: `docs/codex/FAITHLOG_CODEX_HOOK.md`, `docs/backend-implementation-policy.md`, GitHub Issue #30, Notion role/coffee/API 통합 문서를 same-level assignment 기준으로 갱신.
  - 금지어 검사: 실제 소스/테스트/API 문서에서 금지어 및 단수 API 필드 `optionId` 위반 0건. 허용된 정책 문서 예시만 검색됨.
  - 제품/아키텍처 결정 기록: same-level campus role assignment와 coffee duty non-`MEMBER` permission을 `docs/decision-log.md`에 기록.

- #29 캠퍼스 생성/초대코드 가입 구현 검증:
  - 브랜치: `feat/29-campus-create-join`
  - 구현 API: `POST /api/v1/campuses`, `POST /api/v1/campuses/join`, `GET /api/v1/campuses/me`, `GET /api/v1/campuses/{campusId}`, `DELETE /api/v1/campuses/{campusId}/members/{membershipId}`
  - 테스트 결과: `./gradlew test` 성공, 37 tests / 0 failures / 0 errors / 0 skipped
  - 빌드 결과: `./gradlew build` 성공
  - REST Docs 결과: `./gradlew asciidoctor` 성공, Spring REST Docs snippet group 17개 생성
  - 캠퍼스 관련 신규/변경 테스트 파일: 3개 (`CampusControllerTest`, `CampusServiceTest`, `CampusApiRestDocsTest`)
  - 캠퍼스 멤버 삭제 검증: 일반 `MEMBER` 삭제 거부, `ELDER` 삭제 허용, 서비스 `ADMIN` 캠퍼스 미가입 삭제 허용, 삭제 시 `INACTIVE` 전이, 삭제 후 초대코드 재가입 시 기존 멤버십 `ACTIVE + MEMBER` 재활성화.
  - Java 소스 수: 96개, 실구현 Java 파일 69개, `package-info.java` 27개
  - Docker PostgreSQL 검증: `docker compose up -d postgres redis app` 이미지 빌드와 postgres/redis healthcheck 성공. 기존 로컬 Docker volume의 `faithlog` role 비밀번호가 compose 네트워크 접속 기준과 어긋난 상태를 `ALTER USER faithlog WITH PASSWORD 'faithlog'`로 정리한 뒤, `docker compose up -d --force-recreate app` 및 `GET /api/v1/health` 200 검증 완료.
  - 금지어 검사: 실제 소스/테스트/API 문서에서 금지어 및 단수 API 필드 `optionId` 위반 0건. `CampusRole` 검사 결과는 최종 enum 값 `MINISTER`, `ELDER`, `CAMPUS_LEADER`, `MEMBER` 구조로 정상.
  - 제품/아키텍처 결정 기록: 캠퍼스 생성은 `PaymentAccount`와 `penalty_rules`를 만들지 않으며, `GET /campuses/me`와 상세 조회 응답 계약/오류 메시지를 `docs/decision-log.md`에 기록.

- 브랜치/작업트리:
  - 현재 브랜치: `feat/28-auth-refresh-logout-redis`
  - `git status --short --branch`: `docs/resume-metrics.md`와 wiki 문서 변경/추가가 남아 있던 상태에서 검증했고, 이 중 `docs/resume-metrics.md`는 별도 docs 커밋 대상으로 정리
  - `develop` 대비 추가 커밋: 6개 (`0b7cc7a`, `f14ffb7`, `ea5bd3d`, `59d89b0`, `3885808`, `9a910ba`)
- 변경 범위 수치:
  - `git diff --stat origin/develop..HEAD`: 33 files changed, 1,099 insertions, 12 deletions
  - 앱 코드 변경 파일: 22개
  - 테스트 코드 변경 파일: 7개
  - 프로젝트 문서 변경 파일: 3개 (`docs/backend-implementation-policy.md`, `docs/codex/FAITHLOG_CODEX_HOOK.md`, `docs/decision-log.md`)
  - API 문서 변경 파일: 1개 (`src/docs/asciidoc/index.adoc`)
  - 변경 모듈: 2개 (`global`, `user`)
  - 의존성/DB 마이그레이션 변경: 0건
- 코드베이스 구조 수치:
  - `src/main/java/com/faithlog` top-level 모듈: 7개 (`billing`, `campus`, `devotion`, `global`, `notification`, `poll`, `user`)
  - Java 소스 파일: 74개
  - 실구현 Java 파일(`package-info.java` 제외): 47개
  - `package-info.java`: 27개
  - 테스트 소스 파일: 11개
  - 테스트 리소스 파일: 1개
  - 테스트 스위트: 7개
  - 테스트 케이스: 21개
  - DB 마이그레이션 파일: 0개 (Flyway deferred)
- 검증 신호:
  - `./gradlew test --rerun-tasks`: 성공, 20초, 5 tasks executed, 21 tests / 0 failures / 0 errors / 0 skipped
  - `./gradlew asciidoctor --rerun-tasks`: 성공, 39초, 6 tasks executed, REST Docs snippets 재생성
  - `./gradlew build`: 성공, 5초, 3 tasks executed / 5 tasks up-to-date
  - 빌드 아티팩트: `build/libs/faithlog-backend-0.0.1-SNAPSHOT.jar`, `build/libs/faithlog-backend-0.0.1-SNAPSHOT-plain.jar`
  - Spring REST Docs 스니펫 묶음: 10개
  - 생성 문서 확인: `build/docs/asciidoc/index.html` 91,078 bytes
- 운영/문서 신호:
  - 인증 상세 계약 문서 파일 유지: `src/docs/asciidoc/index.adoc`
  - 코드상 헬스 엔드포인트 존재: `GET /api/v1/health`
  - 응답 시간/헬스 성공률은 측정 대상 환경이 승인되지 않아 오늘도 수치 미기록
- 오늘 리스크/관찰:
  - Gradle 실행은 성공했지만 deprecated feature 경고가 남아 있어 Gradle 9 업그레이드 전에 원인 정리가 필요하다.
  - 현재 브랜치 변경은 인증(`user`, `global`)에 집중되어 있고 나머지 5개 top-level 모듈은 이번 브랜치에서 직접 변경되지 않았다.
  - #40의 실제 FCM port 구현이 추가될 때 NoOp FCM adapter가 충돌하거나 우선 사용되지 않도록 `@ConditionalOnMissingBean(CurrentDeviceFcmTokenDeactivationPort.class)` 기반 configuration bean으로 정리했다.
- 오늘 테스트 후보:
  - `./gradlew test --warning-mode all`
  - 이유: 오늘 `test`/`build` 모두 Gradle deprecated feature 경고를 출력했지만 원인 플러그인/스크립트가 식별되지 않았다.
  - 기대 지표: 경고 발생 항목 수, 소유 위치(빌드 스크립트/플러그인), Gradle 9 호환성 정리 backlog
  - `docker compose up -d postgres redis app` 후 `curl /api/v1/health` 반복 측정
  - 이유: 헬스 엔드포인트는 존재하지만 승인된 일일 측정 대상이 아직 없다.
  - 기대 지표: 앱 기동 성공 여부, HTTP 200 여부, 응답 시간(ms), 연속 성공률(%)

### 2026-06-17

- 브랜치/작업트리:
  - 현재 브랜치: `chore/codex-hook-dev-rules`
  - `git status --short --branch`: 워크트리 변경 0건
  - `develop` 대비 최근 커밋: 4개 (`9e0a6b0`, `65c6ba0`, `6845738`, `a5289e4`)
- 변경 범위 수치:
  - `develop..HEAD` diff: 파일 6개, 추가 1,179라인, 삭제 0라인
  - 앱 코드 변경 파일: 0개
  - 문서/운영 규칙 변경 파일: 6개 (`AGENTS.md`, `README.md`, `docs/*`)
- 코드베이스 구조 수치:
  - `src/main/java/com/faithlog` top-level 모듈: 7개 (`billing`, `campus`, `devotion`, `global`, `notification`, `poll`, `user`)
  - Java 소스 파일: 36개
  - 실구현 Java 파일(`package-info.java` 제외): 9개
  - `package-info.java`: 27개
  - 테스트 파일: 1개
  - 테스트 리소스 파일: 1개
  - DB 마이그레이션 파일: 0개 (Flyway deferred)
- 의존성/설정 관찰:
  - `build.gradle.kts`에서 Flyway 런타임 의존성 제거
  - 핵심 런타임 의존성 유지: Spring Boot 3.5.0, Java 21, JPA, Redis, Security, PostgreSQL, Firebase Admin, JWT
  - Netty override 유지: `4.1.135.Final`
- 운영 신호:
  - 코드상 헬스 엔드포인트 존재: `GET /api/v1/health`
  - GitHub Actions workflow 파일: 2개 (`ci.yml`, `project-docs-check.yml`)
  - `ci.yml` 로컬 기준 품질 게이트 job: 2개 (`spring-boot`, `docker`)
  - Docker Compose 로컬 서비스: 5개 (`postgres`, `redis`, `pgadmin`, `redis-commander`, `app`)
  - Docker Compose 명시 healthcheck: 2개 (`postgres`, `redis`)
  - 응답 시간/헬스 성공률은 측정 대상 환경이 승인되지 않아 오늘 수치 미기록
- 오늘 테스트 후보:
  - `docker compose up -d postgres redis` 후 앱 기동 + `curl /api/v1/health` 측정
  - 이유: 현재 엔드포인트는 존재하지만 승인된 런타임 기준의 일별 헬스/지연시간 기준선이 없다
  - 기대 지표: 앱 기동 성공 여부, HTTP 200 여부, 응답 시간(ms), 연속 성공률(%)

### 2026-06-16

- 자동화 목표: 매일 오전 6시에 프로젝트 상태를 확인하고, 수치화 가능한 변경과 개선 포인트를 보고한다.
- 기록 위치: 이 파일. Obsidian Vault 경로를 받으면 Vault 내부 문서로 옮기거나 동기화한다.
- 다음 테스트 후보:
  - `./gradlew test`로 기본 테스트 통과율 확보
  - `./gradlew build`로 배포 전 빌드 안정성 확보
  - 헬스체크 엔드포인트 기준 운영 상태 지표 정의
- 기준선 수치:
  - `./gradlew test`: 성공, 18초, 5개 Gradle task up-to-date
  - `./gradlew build`: 성공, 3초, 8개 Gradle task up-to-date
  - 테스트 코드 파일: 1개 (`FaithLogApplicationTests.java`)
  - 테스트 리소스 파일: 1개 (`application-test.yml`)
  - DB 마이그레이션: 0개 (Flyway deferred)
- 기획 정합성 보정 수치:
  - 핵심 정책 반영 이슈: 7개 (#21, #27, #28, #31, #38, #39, #40)
  - 수동 `칸반 상태:` 제거 이슈: 21개 범위 검증 중 14개 직접 정리, 최종 잔여 0개
  - 코드 충돌 확인: `refresh_tokens` 테이블/Entity 없음, 현 시점 삭제 작업 불필요
- Project Board 정합성 보정 수치:
  - GitHub Project Board 필드 수정: 24개
  - #39 Priority: P1 -> P0
  - #16 Kanban Status: Code Review -> Done
  - #23~#26 누락된 Priority/Estimate/Work Type/Epic/Release/Domain 필드 보강
  - #23, #24 Domain은 single-select 제약과 혼합 도메인 표기 때문에 pending decision으로 남김
- Codex Hook 세팅 수치:
  - GitHub Issue 생성: 1개 (#43)
  - GitHub Project 카드 연결: 1개
  - Project 카드 상태: In Progress
  - 개발 규칙 문서 추가: 1개 (`docs/codex/FAITHLOG_CODEX_HOOK.md`)
  - README Codex Hook 링크 추가: 1개
  - 금지어 검색 결과: 허용 문서 외 0건
- Agent 규칙 정리 수치:
  - Agent 규칙 파일 단일화: 2개(`AGENT.md`, `AGENTS.md`) -> 1개(`AGENTS.md`)
  - 문서 우선순위 명시: 사용자 최신 결정 > decision-log > Notion > Hook > backend policy > 기존 코드
  - 단수 `optionId` 검사 기준 추가: 1개 명령 패턴
  - `AGENT.md` 활성 로딩 참조: 0건
- 투표 템플릿 정책 정리 수치:
  - 기본 제공 템플릿: 1개(커피)
  - 관리자 생성 템플릿 범주: 3개(수요예배, 토요목자모임, 커스텀)
  - 반영 대상: #37 투표 템플릿/투표 생성 기획
- 투표 자동 생성 정책 정리 수치:
  - 자동 생성 설정 가능 대상: 관리자 생성 PollTemplate
  - 실행 담당 이슈: #24 Scheduler/Batch
  - 중복 방지 기준: campus + template + week 단위 필요
- 커피 투표 시간 설정 정책 수치:
  - 커피 담당자가 설정하는 시간 필드: 2개(자동 생성 시간, 마감 시간)
  - 적용 대상: 기본 커피 PollTemplate
  - 칼럼명 기준: Notion ERD

## Troubleshooting Log

| 날짜 | 문제 | 원인 | 해결 | 전후 수치 | 재발 방지 |
| --- | --- | --- | --- | --- | --- |
| 2026-06-19 | #33 전체 테스트 XML 결과 파일 쓰기 실패 | 기본 전체 테스트 실행 중 일부 `build/test-results/test/TEST-*.xml`이 0바이트로 남아 Gradle 테스트 결과 XML 작성이 실패 | `cleanTest` 후 단일 워커(`--no-parallel --max-workers=1`)로 전체 테스트를 재실행하고, 이후 요청 명령 `./gradlew test`를 다시 실행해 성공 확인 | 전: `./gradlew test` 코드 실패 없이 XML write error, 후: 124 tests / 0 failures / 0 errors / 0 skipped 및 `./gradlew test` 성공 | 동일 증상 재발 시 산출물 정리 후 단일 워커 전체 테스트로 검증 |
| 2026-06-18 | 샌드박스에서 `./gradlew asciidoctor` 실행 실패 | `~/.gradle/wrapper` 락 파일이 샌드박스 쓰기 범위 밖에 있어 Gradle wrapper가 `.zip.lck` 파일을 열지 못함 | 권한 상승으로 동일 명령 재실행 후 성공 | 전: `./gradlew asciidoctor` 즉시 실패, 후: 3초 성공 + `build/docs/asciidoc/index.html` 생성 확인 | Gradle 기반 문서 생성 검증은 샌드박스 실패 시 권한 상승 재시도 |
| 2026-06-17 | 샌드박스에서 Gradle wrapper lock 파일 접근 실패 | `~/.gradle/wrapper` 락 파일이 샌드박스 쓰기 범위 밖에 있어 `./gradlew test`가 `FileNotFoundException`으로 중단 | 권한 상승으로 동일 명령 재실행 후 성공 | 전: 테스트 실행 실패, 후: `./gradlew test` 21.29초 성공 / `./gradlew build` 7.58초 성공 | 자동화 리포트에서 Gradle 검증은 필요 시 권한 상승 재시도 |
| TBD | TBD | TBD | TBD | TBD | TBD |

## Test Runs

| 날짜 | 명령/방법 | 결과 | 주요 수치 | 후속 조치 |
| --- | --- | --- | --- | --- |
| 2026-07-14 | #200 담당자 cardinality RED | 실패 확인 | 기존 구현 기준 focused 28 tests 중 승인된 다중 ACTIVE·동일 사용자 idempotency·동시 추가 계약 3건 실패 | 권한·소유권·DB unique 제약 최소 구현 |
| 2026-07-14 | #200 권한·해제·미납 알림 RED | 실패 확인 | expanded focused 35 tests 중 새 전용 권한, 소유 청구, 해제 충돌, body 없는 202 알림·dedupe 계약 10건 실패 | production/API/Redis dedupe/Flyway 구현 |
| 2026-07-14 | #200 미납 상세·해제 동시성 보완 RED/GREEN | 성공 | focused 13 tests에서 청구 title별 상세/5종 제한 3건과 COFFEE·MEAL 해제 잠금 3건이 RED였고, 동일 ACTIVE 담당 배정 `PESSIMISTIC_WRITE`와 상세 본문 구현 후 13/13 GREEN | 전체 회귀·build·asciidoctor 재검증 |
| 2026-07-14 | #200 자체 리뷰 알림 토큰 N+1 보완 | 성공 | 수신자 반복문 내부 FCM token 조회를 발견해 bulk lookup 구조 테스트 8 tests 중 신규 1건 RED 확인 후 다중 user ID 1회 조회로 변경, notification 전체 focused GREEN | 최종 전체 회귀 재검증 |
| 2026-07-14 | #200 COFFEE 자동 생성 제외 RED/GREEN | 성공 | `PollAutomationServiceTest` 6 tests 중 신규 제외 계약 1건 RED 확인 후 scheduler COFFEE 필터로 6/6 GREEN. 수동 생성 COFFEE 투표의 예정 마감·정산 유지 | 전체 회귀 검증으로 확대 |
| 2026-07-14 | #200 full regression/build/docs | 성공 | 자체 리뷰 보완 포함 최종 `./gradlew test` 466 tests / 0 failures / 0 errors / 3 skipped, `./gradlew build` 성공, `./gradlew asciidoctor` 성공, REST Docs snippet group 153개, `git diff --check` 성공 | PM 전체 diff 리뷰 요청. Docker/PostgreSQL 실마이그레이션은 사용자 금지에 따라 미실행 |
| 2026-07-14 | #200 PM 재리뷰 후 자체 리뷰 추가 RED/GREEN | 성공 | MEAL 계좌 read-only duty write lock과 deactivate duty/account lock 누락을 focused 8 tests 중 2 failures로 재현. 최소 수정 후 focused GREEN, 최종 `483 tests / 0 failures / 0 errors / 3 skipped`, build/asciidoctor/diff check 성공, snippet group 155개 | 최신 `origin/develop...HEAD` PM 재리뷰 요청. Docker/PostgreSQL/Flyway 실적용은 금지에 따라 미실행 |
| 2026-07-14 | #200 PM 3차 재리뷰 7 findings RED/GREEN | 성공 | 회원 상세 소유권, 레거시 혼합 template, 공유 template 동시성 3종, 담당 해제/재지정 2종, rollback/retry 간극, 영구 실패 token 재사용, 소유 계좌 DB query 범위를 finding별 RED로 재현. `campus -> duty -> entity` 및 `duty -> template` 잠금, transaction completion 뒤 dedupe/lock 정리, scoped `IN` query와 mutable token snapshot으로 GREEN | 최종 전체 회귀·문서·PM 재리뷰 |
| 2026-07-14 | #200 최종 full regression/build/docs | 성공 | `./gradlew test` 495 tests / 0 failures / 0 errors / 3 skipped, `./gradlew build` 성공, `./gradlew asciidoctor` 성공, REST Docs snippet group 155개, test suite 83개, `git diff --check` 성공. V10은 새 버전 없이 정상 COFFEE 계좌 중립화와 레거시 혼합 row 비활성 격리를 함께 수행 | 최신 `origin/develop...HEAD` PM finding 0 재리뷰 요청. Docker/PostgreSQL/Flyway 실적용은 사용자 금지에 따라 미실행 |
| 2026-07-14 | #200 PM 최종 3 findings와 자체 리뷰 RED/GREEN | 성공 | 레거시 혼합 Poll 종료, 청구 최신 상태 경합, 담당 해제 overfetch 4 tests와 불일치 COFFEE due 마감 1 test를 RED 재현 후 projection·duty/charge lock·owned account scoped query·classifier fail-closed로 보강. 최종 83 suites / 500 tests / 0 failures / 0 errors / 3 skipped, build/asciidoctor/REST Docs/diff check 성공 | 최신 전체 diff PM finding 0 재리뷰 및 프론트 계약 전달. 프론트 정적 검증 전 backend 병합/Docker QA 보류 |
| 2026-07-15 | #200 최종 API 결정·전체 diff 자체 리뷰 RED/GREEN | 성공 | `manageableByMe` 단일 공개 필드로 정정하고 비활성 duty authorization 3건, stale COFFEE account 1건, dispatch stale snapshot 1건, lock lease/dedupe owner/recovery/docs 경계를 RED 후 보강. 83 suites / 518 tests / 0 failures / 0 errors / 3 skipped, build/asciidoctor, REST Docs 161 groups, diff check 성공 | 최신 전체 diff PM 재리뷰. frontend 정적 검증 전 merge/Docker 금지 유지 |
| 2026-07-15 | #200 담당 회원 삭제 우회 RED/GREEN | 성공 | active COFFEE/MEAL 담당 삭제·미납 우회·stale 목록/재가입·지정/해제 경합 6 tests와 회원 삭제/재가입 409 REST Docs 2 tests를 RED로 재현. `CAMPUS_MEMBER_ACTIVE_DUTY_CONFLICT`, `campus -> duty -> member` 잠금, ACTIVE membership 결속 조회로 보강. 최종 83 suites / 526 tests / 0 failures / 0 errors / 3 skipped, build/asciidoctor, REST Docs 163 groups, diff check 성공 | 최신 전체 diff PM 재리뷰. frontend 명세에 회원 삭제/재가입 409와 담당 해제 선행 UI 추가 |
| 2026-07-15 | #200 stale 담당 복구·탈퇴 우회·역할 경합 RED/GREEN | 성공 | `staleOnly=true` 복구 조회, ACTIVE 담당 계정 탈퇴 409, 역할 변경/회원 삭제 직렬화 4 tests RED 후 GREEN. stale 조회→기존 담당 해제→재가입 실제 API 흐름 통과. 최종 83 suites / 530 tests / 0 failures / 0 errors / 3 skipped, build/asciidoctor, REST Docs 165 groups | 최신 전체 diff PM finding 0 재리뷰 및 frontend 최종 명세 전달. merge/Docker는 PM 승인 전 금지 |
| 2026-07-15 | #200 user-first 생명주기·stale 미납 복구 RED/GREEN | 성공 | 8 residual findings를 test-only RED로 재현하고 user ID/campus ID 오름차순 `user -> campus -> duty -> member`, post-lock 인가 재검증, stale ADMIN 명시 복구, bulk user 조회, invalid query 400로 보강. 최종 84 suites / 539 tests / 0 failures / 0 errors / 3 skipped, build/asciidoctor, REST Docs 167 groups | origin/develop...HEAD 전체 diff finding 0 재리뷰. frontend 전달/merge/Docker는 PM 승인 게이트 유지 |
| 2026-07-15 | #200 stale 복구·ADMIN 최신 권한 RED/GREEN | 성공 | 최신 PAID 덮기·member/duty 교착·마지막 ADMIN 강등·stale requester/target 역할 경합을 보강하고 staleOnly 오류 범위를 controller로 제한. 최종 86 suites / 548 tests / 0 failures / 0 errors / 3 skipped, build/asciidoctor, REST Docs 170 groups | 최신 전체 diff finding 0 재리뷰. frontend 전달/merge/Docker는 PM 승인 게이트 유지 |
| 2026-06-19 | #61 TDD 실패 확인 | 실패 확인 | 구현 전 `./gradlew test --tests com.faithlog.admin.presentation.AdminManagementControllerTest`가 4 tests / 4 failed로 실패. 서비스 ADMIN 관리 endpoint와 role 변경 PATCH 미구현 확인 | admin application/presentation/port 계층 구현 |
| 2026-06-19 | #61 focused admin tests | 성공 | `AdminManagementServiceTest`, `AdminManagementControllerTest`, `AdminManagementApiRestDocsTest` 성공. 사용자/캠퍼스 검색, 마지막 ADMIN 보호, 직접 멤버 추가/재활성화, REST Docs 계약 검증 | 전체 회귀 테스트로 확대 |
| 2026-06-19 | #61 full regression/build/docs | 성공 | `./gradlew test` 성공(138 tests / 0 failures / 0 errors / 0 skipped), `./gradlew build` 성공, `./gradlew asciidoctor` 성공, REST Docs snippet group 57개 | PM 리뷰 요청 |
| 2026-06-19 | #61 Docker validation | 실패 | `docker compose build app` 2회 모두 Docker Hub metadata 조회 단계에서 `DeadlineExceeded` 실패. `docker ps` 기준 running container 0개 | Docker 네트워크/registry 접근 가능 상태에서 재시도 필요 |
| 2026-06-19 | #33 PM review daily check submitted-week guard | 성공 | 구현 전 대상 테스트 32 tests / 3 failed 확인. 수정 후 대상 테스트 성공, `./gradlew cleanTest test --no-parallel --max-workers=1` 성공(124 tests / 0 failures / 0 errors / 0 skipped), `./gradlew test` 성공, `./gradlew build` 성공, `./gradlew asciidoctor` 성공, REST Docs snippet group 49개 | PM 검증 요청 |
| 2026-06-18 | #36 TDD 실패 확인 | 실패 확인 | Query service 테스트는 missing class 15개로 `compileTestJava` 실패, Controller 테스트는 새 조회 endpoint 미구현으로 HTTP 200 assertion 실패 | 조회 Query Service, Result/Response DTO, Controller endpoint 구현 |
| 2026-06-18 | #36 focused query/controller/docs tests | 성공 | `BillingQueryServiceTest`, `BillingControllerTest`, `BillingApiRestDocsTest` 각각 성공 | 전체 테스트로 확대 |
| 2026-06-18 | #36 full regression/build/docs/docker | 성공 | `./gradlew test` 성공, 77 tests / 0 failures / 0 errors / 0 skipped; `./gradlew build` 성공; `./gradlew asciidoctor` 성공; Docker compose app 내부 health `UP` | PM 리뷰 전 브랜치 push 여부 확인 필요 |
| 2026-06-18 | #36 PM revalidation unpaid filter/docs sync | 성공 | 관리자 캠퍼스 조회 `status=UNPAID` 회귀 테스트 추가. 구현 변경 전 `BillingControllerTest` 성공으로 기존 동작이 계약을 이미 만족함을 확인. 최종 `./gradlew test` 성공, 78 tests / 0 failures / 0 errors / 0 skipped; `./gradlew build` 성공; `./gradlew asciidoctor` 성공 | PM 재검토 후 push/PR 여부 확인 필요 |
| 2026-06-19 | #36 PM re-review admin member summary sort guard | 성공 | 구현 수정 전 `BillingControllerTest` 실패 확인(8 tests / 1 failed). 수정 후 `./gradlew test --tests 'com.faithlog.billing.*'` 성공, `./gradlew test` 성공(79 tests / 0 failures / 0 errors / 0 skipped), `./gradlew build` 성공, `./gradlew asciidoctor` 성공 | PM 재검토 후 push/PR 여부 확인 필요 |
| 2026-06-19 | #36 PM re-review sort direction guard | 성공 | 구현 수정 전 `BillingControllerTest` 실패 확인(8 tests / 1 failed). 수정 후 `./gradlew test --tests 'com.faithlog.billing.*'` 성공, `./gradlew test` 성공(79 tests / 0 failures / 0 errors / 0 skipped), `./gradlew build` 성공, `./gradlew asciidoctor` 성공 | PM 재검토 후 push/PR 여부 확인 필요 |
| 2026-06-19 | #36 PM re-review malformed sort token guard | 성공 | 구현 수정 전 `BillingControllerTest` 실패 확인(8 tests / 1 failed). 수정 후 `./gradlew test --tests 'com.faithlog.billing.*'` 성공, `./gradlew test` 성공(79 tests / 0 failures / 0 errors / 0 skipped), `./gradlew build` 성공, `./gradlew asciidoctor` 성공 | PM 재검토 후 push/PR 여부 확인 필요 |
| 2026-06-18 | #35 PM review permission regression tests | 성공 | 전역 `ADMIN` 무멤버십 상태 변경, `ELDER`/`CAMPUS_LEADER` 상태 변경, `INACTIVE` 멤버 본인 납부 `403` 테스트 추가 | PR 전 권한 회귀 테스트 유지 |
| 2026-06-18 | #35 PM review focused tests | 성공 | `./gradlew test --tests 'com.faithlog.billing.*'` 성공 | 전체 테스트로 확대 |
| 2026-06-18 | #35 PM review full regression/build | 성공 | `./gradlew test` 성공, 70 tests / 0 failures / 0 errors / 0 skipped; `./gradlew build` 성공 | PR 생성 전 최종 확인 |
| 2026-06-18 | #35 TDD 실패 확인 | 실패 확인 | `./gradlew test --tests 'com.faithlog.billing.*'` 실패: `CompleteChargePaymentCommand`, `ChangeChargeStatusCommand`, `paidAt`, 상태 전이 메서드 부재로 `compileTestJava` 실패 | 청구 상태 전이 도메인/서비스/API 최소 구현 |
| 2026-06-18 | #35 billing 집중 테스트 | 성공 | `./gradlew test --tests 'com.faithlog.billing.*'` 성공, billing 테스트 20개 통과 | 전체 테스트로 확대 |
| 2026-06-18 | #35 full regression | 성공 | `./gradlew test` 성공, 67 tests / 0 failures / 0 errors / 0 skipped | build/asciidoctor 확인 |
| 2026-06-18 | #35 build/docs | 성공 | `./gradlew build` 성공, `./gradlew asciidoctor` 성공. asciidoctor 최초 샌드박스 실행은 Gradle wrapper lock 권한 문제로 실패 후 권한 상승 재실행 성공 | PR 전 동일 검증 유지 |
| 2026-06-18 | `./gradlew test --rerun-tasks` | 성공 | 20초, 5 tasks executed, 21 tests / 0 failures / 0 errors / 0 skipped, 테스트 통과율 100% | `--warning-mode all`로 deprecated feature 원인 식별 필요 |
| 2026-06-18 | #30 TDD 실패 확인 | 실패 확인 | `./gradlew test --tests com.faithlog.campus.application.CampusServiceTest --tests com.faithlog.campus.presentation.CampusControllerTest --tests com.faithlog.campus.presentation.CampusApiRestDocsTest`가 구현 전 `DutyType`, `ChangeCampusRoleCommand`, `AssignCoffeeDutyCommand`, `DutyAssignmentResult`, 서비스 메서드 부재로 `compileTestJava` 실패 | 최소 구현 후 동일 테스트 통과 |
| 2026-06-18 | #30 커피 담당자 동시 지정 실패 확인 | 실패 확인 | `./gradlew test --tests com.faithlog.campus.application.CampusDutyAssignmentConcurrencyTest`가 구현 전 active row 중복으로 `NonUniqueResultException` 실패 | 캠퍼스 row pessimistic lock 적용 후 동일 테스트 통과 |
| 2026-06-18 | #30 집중 테스트 | 성공 | `CampusServiceTest`, `CampusControllerTest`, `CampusApiRestDocsTest` 통과 | 전체 테스트로 확대 |
| 2026-06-18 | #30 `./gradlew test` | 성공 | 47 tests / 0 failures / 0 errors / 0 skipped, 테스트 통과율 100% | Docker 검증 완료 |
| 2026-06-18 | #30 `./gradlew build` | 성공 | bootJar/build 성공, Gradle deprecated feature 경고 유지 | Gradle 9 호환성 경고는 별도 정리 |
| 2026-06-18 | #30 `./gradlew asciidoctor` | 성공 | REST Docs snippet group 22개, admin campus snippets 5개 추가, 샌드박스 Gradle wrapper lock 실패 후 권한 상승 재실행 성공 | 신규 관리자 API마다 REST Docs 테스트 유지 |
| 2026-06-18 | #30 Docker validation | 성공 | `docker compose build app` 성공, `docker compose up -d postgres redis app` 성공, postgres/redis healthy, app started, `curl http://localhost:8080/api/v1/health` 성공 | 컨테이너는 검증 후 실행 상태 유지 |
| 2026-06-18 | #34 TDD 실패 확인 | 실패 확인 | 구현 전 billing 도메인/서비스/리포지토리 부재로 `./gradlew test --tests 'com.faithlog.billing.*'` `compileTestJava` 실패. terminal 상태 보강 전 `markWaived`, `markCanceled` 부재 실패 확인 | 최소 구현 및 terminal 상태 전이 추가 후 동일 범위 통과 |
| 2026-06-18 | #34 billing 집중 테스트 | 성공 | `./gradlew test --tests 'com.faithlog.billing.*'` 성공, billing service/controller/REST Docs 테스트 통과 | 전체 테스트로 확대 |
| 2026-06-18 | #34 `./gradlew test` | 성공 | 56 tests / 0 failures / 0 errors / 0 skipped, 테스트 통과율 100% | `./gradlew build`, `asciidoctor` 추가 확인 |
| 2026-06-18 | #34 `./gradlew build` | 성공 | bootJar/build 성공, Gradle deprecated feature 경고 유지 | Docker 검증 시도 |
| 2026-06-18 | #34 `./gradlew asciidoctor` | 성공 | payment account snippets 3개 묶음, 전체 snippet group 25개, 샌드박스 Gradle wrapper lock 실패 후 권한 상승 재실행 성공 | 신규 billing API 문서 include 유지 |
| 2026-06-18 | #34 Docker validation | 실패 | `docker compose build app`가 Docker daemon 응답 `Docker Desktop is unable to start`로 중단 | Docker Desktop 실행 가능 상태에서 재시도 필요 |
| 2026-06-18 | #34 PM review failure tests | 실패 확인 | `./gradlew test --tests 'com.faithlog.billing.*'` 실패: service ADMIN 계좌 조회 403 계열 실패, HTTP ADMIN 조회 assertion 실패, penalty charge rerun 2건 unique constraint 실패 | 관리자 조회 권한과 UNPAID 청구 create-or-update 구현 |
| 2026-06-18 | #34 PM review focused tests | 성공 | `./gradlew test --tests 'com.faithlog.billing.*'` 성공, billing 테스트 12개 통과 | 전체 테스트로 확대 |
| 2026-06-18 | #34 PM review full regression | 성공 | `./gradlew test` 성공, 59 tests / 0 failures / 0 errors / 0 skipped | build/asciidoctor 확인 |
| 2026-06-18 | #34 PM review build/docs | 성공 | `./gradlew build` 성공, `./gradlew asciidoctor` 성공. asciidoctor 최초 샌드박스 실행은 Gradle wrapper lock 권한 문제로 실패 후 권한 상승 재실행 성공 | Docker Desktop 상태 복구 후 Docker 재검증 필요 |
| 2026-06-18 | #29 `./gradlew test` | 성공 | 31 tests / 0 failures / 0 errors / 0 skipped, 테스트 통과율 100% | 캠퍼스 멤버 관리 이슈에서 권한 테스트 추가 |
| 2026-06-18 | #29 `./gradlew build` | 성공 | bootJar/build 성공, Gradle deprecated feature 경고 유지 | Gradle 9 호환성 경고는 별도 정리 |
| 2026-06-18 | #29 `./gradlew asciidoctor` | 성공 | REST Docs snippet group 16개, 캠퍼스 API snippets 6개 추가 | 신규 API마다 REST Docs 테스트 유지 |
| 2026-06-18 | #29 Docker PostgreSQL validation | 성공 | `docker compose up -d postgres redis app` 이미지 빌드 성공, postgres/redis healthy, 기존 로컬 Docker volume의 `faithlog` role password 재설정 후 app 기동 및 `GET /api/v1/health` 200 확인 | Docker volume을 삭제하지 않고 credential mismatch를 복구하는 절차를 troubleshooting에 유지 |
| 2026-06-18 | `./gradlew asciidoctor --rerun-tasks` | 성공 | 39초, 6 tasks executed, REST Docs 스니펫 10개, HTML 1개 생성 | 새 API 추가 시 문서 스니펫 수와 HTML 생성 여부 비교 |
| 2026-06-18 | `./gradlew build` | 성공 | 5초, 3 tasks executed / 5 tasks up-to-date, 빌드 성공률 기준선 100%, JAR 2개 유지 | 앱 코드 변경이 생기면 오늘 수치와 비교 |
| 2026-06-18 | Branch monitoring audit | 성공 | `origin/develop` 대비 6커밋, 33파일, +1,099/-12, 앱 코드 22파일, 테스트 코드 7파일, DB migration 0개 | 헬스/응답시간 측정 대상 환경 결정 필요 |
| 2026-06-16 | `./gradlew test` | 성공 | 18초, 5개 task up-to-date, 테스트 통과율 100% | 기능별 테스트 수 확대 |
| 2026-06-16 | `./gradlew build` | 성공 | 3초, 8개 task up-to-date, 빌드 성공률 기준선 100% | 배포 전 빌드 체크 유지 |
| 2026-06-16 | GitHub issue policy audit | 성공 | #17~#41 `칸반 상태:` 잔여 0개, 핵심 이슈 7개 정책 반영 | Project Board 조회에는 `read:project` scope 필요 |
| 2026-06-16 | GitHub Project Board audit | 성공 | Project Board 필드 24개 수정, #39 P0 반영, #16 상태 필드 일치 | #23/#24 Domain 결정 필요 |
| 2026-06-16 | Codex Hook validation | 성공 | `./gradlew test` 13초 성공, 금지어 검색 0건, AGENTS/Hook 문서 존재 확인 | 문서-only 작업이라 신규 테스트 없음 |
| 2026-06-16 | Agent rule consolidation validation | 성공 | `AGENT.md` 활성 로딩 참조 0건, `AGENTS.md` 단일 기준화, 단수 `optionId` 검사 명령 결과 Hook 문서 예시 1건만 확인 | Obsidian 최종 경로 결정 필요 |
| 2026-06-16 | PR readiness validation | 성공 | `./gradlew test` 4초 성공, 5개 task up-to-date | 문서-only PR로 기능 테스트 추가 없음 |
| 2026-06-16 | Poll template planning validation | 성공 | 기본 템플릿 1개, 관리자 생성 템플릿 범주 3개로 정책 확정 | #37 구현 시 seed/admin 생성 테스트 필요 |
| 2026-06-16 | Poll template policy PR validation | 성공 | `./gradlew test` 9초 성공, 5개 task up-to-date | #37 구현 시 기본 커피 템플릿 테스트 필요 |
| 2026-06-16 | Poll auto-generation planning validation | 성공 | #37 설정 저장, #24 스케줄러 실행으로 책임 분리 | 자동 생성 요일/시간/마감 필드 테스트 필요 |
| 2026-06-16 | Poll auto-generation policy PR validation | 성공 | `./gradlew test` 3초 성공, 5개 task up-to-date | #24 구현 시 중복 생성 방지 테스트 필요 |
| 2026-06-16 | Coffee poll timing planning validation | 성공 | 커피 담당자 설정 시간 2개 확정 | #37 구현 시 Notion ERD 칼럼명 확인 필요 |
| 2026-06-16 | Coffee poll timing PR validation | 성공 | `./gradlew test` 4초 성공, 5개 task up-to-date | #37/#24 구현 시 자동 생성/마감 시간 테스트 필요 |
| 2026-06-17 | `./gradlew test` | 성공 | 21.29초, 5개 task up-to-date, 테스트 통과율 100% | 기능 테스트 추가 전까지 smoke baseline 유지 |
| 2026-06-17 | `./gradlew build` | 성공 | 7.58초, 8개 task up-to-date, 빌드 성공률 기준선 100% | 앱 코드 변경 시 build baseline 비교 지속 |
| 2026-06-17 | Repo monitoring audit | 성공 | 워크트리 변경 0건, `develop` 대비 4커밋/6파일/1,179라인 문서 변경, 앱 코드 변경 0개 | 헬스/응답시간 측정 대상 환경 결정 필요 |
| 2026-06-17 | `./gradlew test` 재검증 | 성공 | 31초, 5개 task up-to-date, 테스트 통과율 100% | 현재 브랜치는 문서-only 상태라 기능 테스트 확대 전 smoke baseline 유지 |
| 2026-06-17 | `./gradlew build` 재검증 | 성공 | 8초, 8개 task up-to-date, 빌드 성공률 기준선 100% | 앱 코드 변경이 생기면 오늘 수치와 비교 |
| 2026-06-17 | Local repo structure audit 재검증 | 성공 | 실구현 Java 9개, top-level 모듈 7개, CI workflow 2개, Docker Compose 서비스 5개, 마이그레이션 0개 | 헬스 체크 기준 환경 승인 전까지 운영 지표는 보류 |
| 2026-06-17 | Flyway runtime removal validation | 성공 | `./gradlew test` 35초 성공, `./gradlew build` 26초 성공, `runtimeClasspath` Flyway 항목 0개, active migration file 0개 | 최종 도메인 모델 안정화 후 Flyway migration consolidation task로 재도입 |
| 2026-06-17 | #27 auth JWT TDD validation | 성공 | `./gradlew test` 18초 성공, 테스트 파일 1개 -> 4개, 회원가입/로그인/JWT claim/Bearer `/users/me` 검증 추가 | #28 refresh/logout/Redis rotation 구현 시 인증 테스트 확장 |
| 2026-06-17 | #27 PM review security fix validation | 성공 | `./gradlew test` 16초 성공, `./gradlew build` 5초 성공, refresh token Bearer 인증 401 테스트 추가, 비활성 사용자 `/users/me` 401 테스트 추가 | #28 Redis allowlist/blacklist 구현 시 tokenType 검증 유지 |
| 2026-06-17 | #27 Docker validation | 부분 성공 | `docker compose build` 성공, `docker compose up -d postgres redis app` 후 postgres/redis healthy, app은 기존 Postgres volume credential mismatch로 `FATAL: password authentication failed for user "faithlog"` 종료 | Docker volume credential 정리 또는 승인된 DB 초기화 후 앱 헬스체크 재검증 |
| 2026-06-17 | #27 Docker local ddl-auto update validation | 성공 | `docker compose build app` 성공, `docker compose up -d app` 성공, `GET /api/v1/health` 200, Hibernate가 local Docker DB에 `users` 테이블 자동 생성 | 최종 Flyway migration consolidation 전까지 local Docker 개발 검증은 `SPRING_JPA_HIBERNATE_DDL_AUTO=update` 기본값 사용 |
| 2026-06-17 | #27 auth REST Docs validation | 성공 | `./gradlew test --tests '*AuthApiRestDocsTest'` 성공, `./gradlew asciidoctor` 성공, `./gradlew test --rerun-tasks` 11초 성공, `./gradlew build` 5초 성공, 인증 API snippets 6개 묶음과 `build/docs/asciidoc/index.html` 생성 | 새 API/변경 API는 Spring REST Docs 테스트로 상세 계약 문서화 |
| 2026-06-17 | #27 CI test profile override fix | 성공 | PR #47 Backend CI 실패 원인 확인, CI env 재현 실패 확인, 수정 후 `./gradlew test --tests '*AuthServiceTest'` 성공, `./gradlew test --rerun-tasks` 11초 성공, `./gradlew build` 2초 성공 | GitHub Actions 재실행 후 원격 check 통과 확인 |
| 2026-06-22 | #81 Gradle/test deprecation baseline | 성공 | `./gradlew test --warning-mode all` 성공. Problems report 기준 12 warnings: Asciidoctor Gradle plugin 내부 `StartParameter.isConfigurationCacheRequested` 1건, Spring Boot `@MockBean` removal warning 11건 | 안전하게 수정 가능한 테스트 deprecation 11건부터 정리 |
| 2026-06-22 | #81 `@MockBean` deprecation cleanup | 성공 | Spring Boot deprecated `@MockBean` 11건을 `org.springframework.test.context.bean.override.mockito.MockitoBean`으로 교체. 수정 후 `./gradlew test --warning-mode all` 성공, Problems report 1 warning으로 감소 | 남은 1건은 `org.asciidoctor.jvm.convert` 4.0.5 최신 버전 내부 Gradle API 사용으로 #82에서 후속 추적 |
| 2026-06-22 | #81 final validation | 성공 | `./gradlew test` 성공(236 tests / 0 failures / 0 errors / 0 skipped), `./gradlew build` 성공, `./gradlew build --warning-mode all` 성공, `./gradlew asciidoctor` 성공, `git diff --check` 성공 | Docker QA는 앱/빌드 설정 및 런타임 동작 변경이 없어 생략 |

## Resume Bullet Candidates

- 2026-07-15 #201 목록 페이징 메타데이터·이전 기록 조회
  - 청구 조회 5개 응답의 기존 `members`/`items`를 유지하면서 `page`, `size`, `totalElements`, `totalPages`를 추가해 클라이언트가 다음 페이지를 정확히 판단하도록 보강.
  - 미납은 기간 제한 없이 유지하고, `includeArchived=false`에서 PAID는 `paidAt`, WAIVED/CANCELED는 `updatedAt` 기준 최근 1개월만 노출하며 `includeArchived=true`로 과거 완료 기록을 조회하도록 DB 조건을 적용.
  - MEAL 관리 투표는 진행 중인 OPEN/SCHEDULED를 모두 유지하고 CLOSED는 최근 90일만 기본 노출하며, 이전 기록 전환 시 과거 마감 투표까지 조회하도록 구성.
  - 관리자 사용자·캠퍼스·정산·알림 로그는 20개, 사용자·담당자 모바일 목록은 10개 기본 크기로 분리하고 최대 100개 제한은 유지.
  - TDD RED 26건 중 6건 실패를 확인한 뒤 전체 551 tests(실패 0, skipped 3), focused 회귀, `build -x test`, REST Docs 170개 snippet group 및 AsciiDoc HTML 생성을 검증. DB migration과 데이터 삭제는 없음.
  - local `develop` fast-forward 후 기존 앱 컨테이너만 새 이미지로 교체하고 동일 `127.0.0.1:28080`에서 health `UP`을 확인. PostgreSQL/Redis 컨테이너와 named volume은 유지했으며 Flyway schema v10이 최신임을 검증.
  - 수정된 프론트의 iOS Simulator를 재빌드 없이 새로고침해 관리자 청구·MEAL 투표의 `includeArchived=false/true`, 20/10 페이지 크기와 메타데이터를 실서버에서 확인하고 기존 관리자 정산 로딩 오류 해소를 검증.

- 2026-07-14 #200 다중 커피 담당자·소유권·미납 알림
  - 캠퍼스별 ACTIVE COFFEE 담당자를 단일 교체에서 다중 additive/idempotent 지정으로 전환하고, `(campus_id, duty_type, user_id)` active partial unique migration으로 동시 중복을 방지.
  - COFFEE command를 ACTIVE 담당자와 투표/계좌/청구 소유권으로 제한하고, 비활성 계좌까지 포함한 UNPAID 청구가 있으면 COFFEE/MEAL 담당 해제를 409로 차단.
  - 요청 body 없이 담당자 소유 계좌의 전체 UNPAID를 계좌·수신자별 합산하는 202 알림 API 2개를 추가하고, Asia/Seoul 일자 Redis dedupe·토큰 없음 skip·dispatch 실패 rollback/retry를 검증.
  - 알림 본문에 청구 title별 건수·금액을 최대 5종까지 표시하고 전체 합계를 유지했으며, 담당 해제와 COFFEE/MEAL 정산이 동일 배정 행 잠금을 공유해 동시 요청에서도 미납 해제 차단을 보장.
  - 자체 리뷰에서 수신자별 FCM token N+1 조회를 발견해 다중 user ID bulk query 1회로 축소하고 구조 회귀 테스트를 추가.
  - COFFEE 투표를 scheduler 자동 생성에서 제외하되 수동 생성 투표의 예정 마감·정산을 유지하고, 담당 회원 삭제 우회까지 차단한 전체 526 tests와 REST Docs 163개 snippet group으로 검증. Docker/실데이터 성과 수치는 측정하지 않음.

- 2026-06-20 #38 PM 검토 보완
  - 컨트롤러 `@NotEmpty`로 빈 `optionIds`가 `GLOBAL_VALIDATION_FAILED`로 뭉개지던 경로를 제거하고, 서비스 검증에서 `POLL_RESPONSE_INVALID_SELECTION_COUNT` 계약을 반환하도록 보정.
  - `SCHEDULED`/future poll의 응답·댓글 write와 일반 ACTIVE 멤버 목록/상세 노출을 차단하고, 같은 선택지 재저장 시 `(response_id, option_id)` unique 충돌이 나지 않도록 response option bulk delete를 적용.
  - TDD 실패 확인: 대상 테스트 4건 실패로 이슈 재현 후 수정.

- 2026-06-19 #37 투표 템플릿과 투표 생성 구현
  - 사용자 승인 seed 기준: 공식 사이트 접근 차단 후 사용자가 제공한 `컴포즈커피 메뉴 가격 2026년 최신 버전` 목록을 승인된 seed 원천으로 기록.
  - 재삽입 가능한 seed 원천: `src/main/resources/seed/compose-coffee-menu-2026.csv`, 설명 문서 `docs/seed/compose-coffee-menu-2026.md`.
  - 구현 범위: `CoffeeBrand`, `CoffeeMenuCatalog`, `PollTemplate`, `PollTemplateOption`, `Poll`, `PollOption`, 커피 카탈로그 조회 API, 템플릿 생성/수정/비활성화/조회 API, 직접 선택지 기반 투표 생성 API, 템플릿 옵션 snapshot 복사.
  - TDD 실패 확인: `./gradlew test --tests 'com.faithlog.poll.application.PollServiceTest'` 최초 실행이 poll domain/service/repository 미구현으로 `compileTestJava` 97 errors 실패.
  - 재검증: poll 대상 테스트 성공, `./gradlew test --rerun-tasks` 성공(144 tests / 0 failures / 0 errors / 0 skipped), `./gradlew build` 성공, `./gradlew asciidoctor` 성공. REST Docs snippet group 65개 확인. `docker compose build app` 성공, `docker compose up -d postgres redis app` 후 `GET /api/v1/health` `UP` 확인, `docker compose down` 완료.

- Spring Boot 기반 FaithLog 프로젝트의 테스트 기준선을 수립하고, `./gradlew test` 기준 테스트 통과율 100%를 확보.
- `./gradlew build` 기준 빌드 성공 상태를 확보해 배포 전 안정성 검증 기준선을 수립.
- FaithLog 백엔드의 일일 모니터링 기준선을 정리해 7개 도메인 모듈, 74개 Java 소스, Flyway deferred 상태, 100% 테스트/빌드 성공 상태를 지속 추적할 수 있게 함.
- GitHub Issues #17~#41의 기획/구현 기준을 최신 백엔드 정책과 정합화하고, 수동 칸반 상태 잔여 0개로 Project Board 중심 운영 기준을 정리.
- GitHub Project Board의 누락/불일치 필드 24개를 정리해 이슈 본문과 칸반 운영 데이터의 정합성을 개선.
- Codex Hook 개발 규칙을 문서화하고 GitHub Issue #43 및 Project 카드와 연결해 TDD/보안/아키텍처/Obsidian 기록 기준을 표준화.
- Codex Agent 규칙 파일을 2개에서 1개로 단일화하고, 사용자 결정 우선순위와 금지 필드 검사 기준을 문서화해 개발 규칙 위반 가능성을 낮춤.
- 투표 템플릿 정책을 기본 제공 1개와 관리자 생성 3개 범주로 분리해 초기 데이터와 운영 권한 기준을 명확화.
- 투표 자동 생성 책임을 템플릿 설정과 스케줄러 실행으로 분리해 반복 운영 자동화 설계 기준을 명확화.
- 커피 담당자가 자동 생성 시간과 마감 시간을 설정하도록 투표 운영 권한과 반복 생성 정책을 구체화.
- 회원가입/로그인/JWT 인증 흐름을 TDD로 구현하고, Bearer 인증 `/api/v1/users/me`와 JWT 필수 claim 검증을 포함한 테스트 파일을 1개에서 4개로 확대.
- Swagger/springdoc은 API 탐색용으로 유지하면서, 회원가입/로그인/내 정보 조회의 상세 계약을 Spring REST Docs 문서 생성 테스트로 검증하도록 확장.
- Redis allowlist/blacklist 기반 refresh/logout 흐름을 추가하고, 인증 테스트 스위트를 7개·21개 케이스까지 확장해 토큰 회전과 로그아웃 계약을 검증.
- 인증 문서화를 Spring REST Docs 중심으로 유지하면서 스니펫 묶음 10개와 AsciiDoc 계약 문서를 계속 생성 가능한 상태로 검증.
- 캠퍼스 생성/초대코드 가입 API 4개를 TDD로 구현하고, 테스트 스위트를 31개 케이스와 Spring REST Docs 스니펫 16개 묶음까지 확장해 권한/멤버십/초대코드 노출 계약을 검증.

<!-- daily-resume-monitor:start:resume-metrics:2026-06-16 -->
### 2026-06-16 Automated Resume Monitor

- Evidence source: `docs/prompts/daily-resume-monitor.md` read at runtime.
- Commits reviewed: 4
- Changed files reviewed: 6
- Dependency/config changes reviewed: 0
- DB migration changes reviewed: 0
- Local test result: 1 tests, 0 failures/errors. Measurement method: Gradle XML under `build/test-results/test`. Confidence: verified.
- Build artifacts present locally. Measurement method: `build/libs/*.jar`. Confidence: partially verified.

Metric candidates:
- Health check success rate: measure against a user-approved local or deployed URL with repeated requests.
- API response time: measure with a user-approved endpoint and command so daily values are comparable.
<!-- daily-resume-monitor:end:resume-metrics:2026-06-16 -->

<!-- daily-resume-monitor:start:resume-metrics:2026-06-17 -->
### 2026-06-17 Automated Resume Monitor

- Evidence source: `docs/prompts/daily-resume-monitor.md` read at runtime.
- Commits reviewed: 5
- Changed files reviewed: 32
- Dependency/config changes reviewed: 0
- DB migration changes reviewed: 0
- Local test result: 21 tests, 0 failures/errors/skips. Measurement method: Gradle XML under `build/test-results/test`. Confidence: verified.
- Local build result: `./gradlew build` success in 25s with 8 up-to-date tasks. Confidence: verified.
- API contract docs: `./gradlew asciidoctor` success in 3s, 10 snippet directories, `build/docs/asciidoc/index.html` present. Confidence: verified.

Metric candidates:
- Health check success rate: run `docker compose up -d postgres redis app` and repeat `curl /api/v1/health` against one approved runtime.
- API response time: measure `GET /api/v1/health` or another user-approved endpoint with a fixed local or deployed target.
<!-- daily-resume-monitor:end:resume-metrics:2026-06-17 -->

### 2026-07-16 Issue #193 actual-before attempt B rejected evidence

- 실행 식별자: `I193_BEFORE_20260716_B / I193_FIXTURE_20260716_B / EXEC193_BEFORE_20260716_B`.
- 검증된 준비 범위: 사용자 승인 계정 2개, 임시 ADMIN 로그인, immutable app/PostgreSQL/Redis 및 DB/binding continuity, canonical lock, quiet boundary, fresh fixture prepare.
- fixture 결과: campus ID 17, ACTIVE membership 1,000개, charge item 35,000개 COMMIT.
- 거부 원인: `psql -c` SQL의 `:'dataset_id'`가 psql variable substitution을 거치지 않아 PostgreSQL `syntax error at or near ":"` 발생.
- 측정 결과: k6 warmup 0건, measured 0건, summary 없음. 따라서 baseline, latency, throughput 또는 개선 성과로 집계하지 않는다.
- 복구/보존: 외부 cleanup trap이 임시 ADMIN을 USER로 복구했고 memory-only credential은 폐기됐다. B namespace/DB rows/report는 partial rejected evidence로 보존하며 재사용하지 않는다.
- 재발 방지: dataset binding SQL을 issue-local read-only stdin 파일로 분리하고 `psql -v dataset_id=...`로만 치환한다. fake psql harness가 `-c` 실패와 stdin 성공을 고정한다.

### 2026-07-16 Issue #193 actual-before attempt C rejected evidence

- 실행 식별자: `I193_BEFORE_20260716_C / I193_FIXTURE_20260716_C / EXEC193_BEFORE_20260716_C`.
- 검증된 준비 범위: dataset binding, immutable app/PostgreSQL/Redis 및 DB/binding continuity, canonical lock, quiet boundary, fresh fixture prepare, ADMIN/duty 인증과 member-detail expectation 직전 preflight.
- fixture 결과: campus ID 19, ACTIVE membership 1,000개, charge item 35,000개 COMMIT.
- 거부 원인: 실제 SQL의 `createdAt`은 `...37.542110`, `...37.542109`, `...37.542108` 순으로 정확했지만 JS `Date`가 millisecond로 절삭해 서로 다른 microsecond instant를 동률로 오판하고 전체 ID 내림차순을 요구했다.
- 측정 결과: k6 warmup 0건, measured 0건, summary 없음. 따라서 baseline, latency, throughput 또는 개선 성과로 집계하지 않는다.
- 복구/보존: 외부 cleanup trap이 임시 ADMIN을 USER로 복구했고 memory-only credential은 폐기됐다. C namespace/DB rows/report는 partial rejected evidence로 보존하며 재사용하지 않는다.
- 재발 방지: RFC3339 timestamp를 최대 9자리 fraction 및 `Z`/offset까지 strict parse한 lossless epoch nanoseconds로 비교하고 exact instant tie에서만 ID 내림차순을 적용한다. malformed/date-only/invalid calendar/`24:00`은 fail-closed며 설치된 k6의 정적 `inspect`로 module 호환성을 검증한다.

### 2026-07-16 Issue #193 actual-before attempt D rejected evidence

- 실행 식별자: `I193_BEFORE_20260716_D / I193_FIXTURE_20260716_D / EXEC193_BEFORE_20260716_D`.
- fixture 결과: campus ID 21, ACTIVE membership 1,000개, charge item 35,000개 COMMIT.
- 거부 원인: 요청은 `sort=createdAt,desc`였고 DB의 일부 charge item은 `created_at`이 exact tie였다. current develop API 응답은 해당 status pair들에서 ID 오름차순과 내림차순이 혼재했다. Spring `Pageable`에는 primary `createdAt` sort만 있어 tie를 안정화하는 secondary sort가 없다. PM 독립 대조 결과 이는 scenario 오판이 아니라 실제 API pagination/order 경계다.
- 측정 결과: correctness preflight에서 중단되어 k6 warmup 0건, measured 0건, summary 없음. 따라서 baseline, latency, throughput 또는 개선 성과로 집계하지 않는다.
- 보존: D namespace/DB rows/report는 partial rejected evidence로만 보존하며 재사용하지 않는다. 후속 E는 별도 fresh namespace로만 실행됐다.
- 후속 해결: 사용자가 모든 charge-item pageable endpoint에 primary sort와 같은 방향의 `id` secondary sort를 자동 추가하는 정책(`createdAt,desc → id,desc`, `createdAt,asc → id,asc`)을 승인했고, Issue #206/PR #207이 develop commit `6796ed146244d8f3f5b5dd7048ebe16865084a97`로 병합됐다. Scenario는 exact tie의 `id,desc` 검증을 유지하며 fixture timestamp를 인위적으로 벌리지 않는다. D 자체는 계속 rejected evidence이며, E는 별도 fresh namespace로 실행된 뒤 아래 validator 사유로 rejected됐다.

### 2026-07-16 Issue #193 actual-before attempt E rejected evidence

- 실행 식별자: `I193_BEFORE_20260716_E / I193_FIXTURE_20260716_E / EXEC193_BEFORE_20260716_E`.
- fixture 결과: campus ID 23, ACTIVE membership 1,000개, charge item 35,000개 COMMIT.
- warmup 결과: 5 iterations, HTTP request 80개, HTTP failure 0. 이 warmup은 성능 baseline이나 성과 수치로 채택하지 않는다.
- 거부 원인: 실제 k6 v2 custom Rate의 무오류 shape `{"passes":0,"fails":5,"value":0}`에서 `passes`는 true count, `fails`는 false/total count지만 validator가 존재하지 않는 `rate` 필드를 읽어 16개 case를 모두 거부했다.
- 미실행 범위: measured k6 0건, measured DB/resource boundary 0건, measured summary/classification 없음. `accepted=false`와 `automaticAdoption=false` 경계를 유지한다.
- 복구/보존: 측정 계정 15016/15017은 모두 USER로 복구됐고 canonical lock 제거를 확인했다. E namespace/DB rows/report는 partial rejected evidence로 보존하며 재사용하지 않는다.
- 재발 방지: direct metric과 `metric.values` wrapper를 모두 지원하되 `value=0`, `passes=0`, `fails=expected count`만 무오류로 허용한다. positive value, passes 증가, fails/count mismatch, malformed/nonfinite field는 warmup/measured 양 phase에서 fail-closed다.
- 후속 F는 PM 독립 리뷰와 사용자 승인 후 별도 fresh namespace로 실행됐고 아래 resource validator 사유로 rejected됐다.

### 2026-07-16 Issue #193 actual-before attempt F rejected evidence

- 실행 식별자: `I193_BEFORE_20260716_F / I193_FIXTURE_20260716_F / EXEC193_BEFORE_20260716_F`.
- report: `build/reports/k6/issue-193/I193_BEFORE_20260716_F/I193_FIXTURE_20260716_F/EXEC193_BEFORE_20260716_F`.
- fixture 결과: campus ID 25, ACTIVE membership 1,000개, charge item 35,000개 COMMIT.
- warmup 결과: 5 iterations, HTTP request 80개, failure 0. 이 warmup은 성능 baseline이나 성과 수치로 채택하지 않는다.
- 거부 원인: measured load 전 첫 resource normalization에서 Docker Desktop의 정상 반올림 표기 `501.7MiB`를 exact integer bytes 한 점으로 환산하려 해 `Docker memory size must resolve to an integer byte value`로 중단됐다.
- 미실행 범위: measured k6 summary 0건, adoption/classification 없음. baseline, latency, throughput 또는 개선 성과로 집계하지 않는다.
- 복구/보존: 측정 계정 15018/15019는 모두 USER로 복구됐고 canonical lock free와 running k6 없음이 확인됐다. F namespace/DB rows/report는 partial rejected evidence로 보존하며 재사용하지 않는다.
- 재발 방지: scalar byte 수치를 제거하고 `MemUsage` used/limit를 표시 정밀도의 inclusive integer-byte min/max decimal-string으로, `MemPerc`를 exact rational rounding interval로 보존한다. 가능한 used/limit ratio와 MemPerc 구간 교집합, full ID/role/set, positive limit, used≤limit, CPU, percent 0..100, safe magnitude와 unit schema를 `BigInt` 기반으로 fail-closed 검증한다.
- 후속 G는 PM 독립 리뷰와 사용자 승인 후 별도 fresh namespace로 실행됐고 아래 resource cadence validator 사유로 rejected됐다.

### 2026-07-16 Issue #193 actual-before attempt G rejected evidence

- 실행 식별자: `I193_BEFORE_20260716_G / I193_FIXTURE_20260716_G / EXEC193_BEFORE_20260716_G`.
- fixture 결과: campus ID 27, ACTIVE membership 1,000개, charge item 35,000개 COMMIT.
- warmup 결과: 5 iterations, HTTP request 80개, failure 0. 이 warmup은 성능 baseline이나 성과 수치로 채택하지 않는다.
- measured 진행: 16 cases 각각 request count 239, custom failure value 0. Resource sample 90개가 `2026-07-16T04:54:06.025Z`부터 `2026-07-16T04:57:16.941Z`까지 수집됐고 관찰 gap은 최소 1.869초, 최대 4.807초였다.
- 거부 원인: blocking `docker stats --no-stream` capture가 약 1.37~4.31초 걸리는데 runner가 각 capture 뒤 nominal interval의 절반인 0.5초 sleep을 추가하고, nominal `DOCKER_STATS_SAMPLING_INTERVAL_SECONDS=1`을 maximum gap으로도 사용해 1초 gate를 구조적으로 만족할 수 없었다.
- evidence 경계: measured summary, counter-after, measurement-state-after, PostgreSQL-after는 존재하지만 resource validator에서 중단됐다. runtime-final과 adoption/classification은 생성되지 않았으므로 G의 measured 요청 수, latency, throughput, resource 수치는 baseline 또는 개선 성과로 채택하지 않는다.
- 복구/보존: 측정 계정 15020/15021은 모두 USER로 복구됐고 canonical lock free와 running k6 없음이 확인됐다. G namespace/DB rows/report는 partial rejected evidence로 보존하며 재사용하지 않는다.
- 재발 방지: runtime-required `DOCKER_STATS_MAX_GAP_SECONDS=5`를 별도 추가하고 nominal interval 1초와 함께 run conditions/validator output에 기록한다. Sampler는 blocking capture를 back-to-back으로 수행하고 unconditional post-capture sleep을 제거한다. maxGap 누락·비정상 값·nominal 미만·실제 gap 초과는 fail-closed다.
- 후속 H는 PM 독립 리뷰와 사용자 승인 후 별도 fresh namespace로 실행됐고 아래 pre-boundary cumulative stats 사유로 rejected됐다.

### 2026-07-16 Issue #193 actual-before attempt H rejected evidence

- 실행 식별자: `I193_BEFORE_20260716_H / I193_FIXTURE_20260716_H / EXEC193_BEFORE_20260716_H`.
- 실행 범위: fresh campus fixture COMMIT, measured 16 cases 각각 request count 320, HTTP/custom failure 0, resource validation 통과.
- 거부 원인: measurement-integrity에서 `users.nModSinceAnalyze`가 before 51, after 52로 보여 중단됐다. Fresh measured ADMIN login의 `last_login_at=2026-07-16T05:07:13.053898Z` UPDATE 후 약 478ms인 `measurement-state-before capturedAt=2026-07-16T05:07:13.531998Z`에 단일 snapshot을 채택해 PostgreSQL cumulative stats flush가 아직 반영되지 않은 false contamination이었다. Measured GET workload 자체의 users write로 해석하지 않는다.
- evidence 경계: runtime-final과 adoption/classification은 생성되지 않았다. H의 request count, latency, throughput, resource 수치는 baseline 또는 개선 성과로 채택하지 않는다.
- 복구/보존: 측정 계정 15022/15023은 모두 USER로 복구됐고 canonical lock free와 running k6 없음이 확인됐다. H namespace/DB rows/report는 partial rejected evidence로 보존하며 재사용하지 않는다.
- 재발 방지: issue-local 상수로 최대 5회 `capture → 1초 sleep → capture`를 수행한다. DB identity/postmaster/stats reset, planner settings, 4개 table maintenance state가 exact 일치한 pair의 두 번째 snapshot만 counter-before/window 이전 `measurement-state-before.json`으로 사용한다. 안정화되지 않으면 fail-closed하며 measured 이후 기존 exact continuity는 유지한다.
- 후속 I는 PM 독립 리뷰와 사용자 승인 후 별도 fresh namespace로 실행됐고 아래 measured-window autoanalyze 사유로 rejected됐다.

### 2026-07-16 Issue #193 actual-before attempt I rejected evidence

- 실행 식별자: `I193_BEFORE_20260716_I / I193_FIXTURE_20260716_I / EXEC193_BEFORE_20260716_I`.
- 실행 범위: measured 16 cases 완료, failure 0. Pre-boundary users `nModSinceAnalyze`는 59→59로 안정적이었다.
- 거부 원인: `charge_items` before는 `nModSinceAnalyze=70000`, `lastAutoanalyze=2026-07-16T04:54:17.632105Z`, `autoanalyzeCount=10`이었지만 after는 `nModSinceAnalyze=0`, `lastAutoanalyze=2026-07-16T05:20:17.275926Z`, `autoanalyzeCount=11`이었다. Measured start 후 autoanalyze가 실행돼 기존 strict maintenance continuity가 measurement-integrity를 거부했다.
- 채택 경계: I의 latency, throughput, resource 수치는 baseline 또는 개선 성과로 채택하지 않는다.
- 복구/보존: 측정 계정 15024/15025는 모두 USER로 복구됐고 canonical lock free가 확인됐다. I namespace/DB rows/report는 partial rejected evidence로 보존하며 재사용하지 않는다.
- 승인된 재발 방지: fixture COMMIT 직후, expectations/preflight/warmup 전에 별도 stdin SQL로 exact `ANALYZE campus_members, payment_accounts, charge_items;`를 실행하고 completion marker를 남긴다. users, 다른 table, VACUUM, reset/config/extension, schema/index/Flyway는 변경하지 않는다. Measured before/after analyze/autoanalyze continuity는 완화하지 않는다.
- 후속 J는 PM 독립 리뷰와 사용자 승인 후 별도 fresh namespace로 실행됐고 아래 measured-window insert-triggered autovacuum 사유로 rejected됐다.

### 2026-07-16 Issue #193 actual-before attempt J rejected evidence

- 실행 식별자: `I193_BEFORE_20260716_J / I193_FIXTURE_20260716_J / EXEC193_BEFORE_20260716_J`.
- 실행 범위: 승인된 exact 3-table ANALYZE를 warmup 전에 완료했고 measured 16 cases를 failure 0으로 완료했다. `charge_items` analyzeCount/autoanalyzeCount/lastAnalyze는 안정적이어서 I의 autoanalyze 문제는 해소됐다.
- 거부 원인: `campus_members` before는 `lastAnalyze=2026-07-16T05:31:18.323492Z`, `autovacuumCount=5`, `lastAutovacuum=2026-07-16T02:50:09.214337Z`, `nModSinceAnalyze=0`이었지만 after는 `autovacuumCount=6`, `lastAutovacuum=2026-07-16T05:32:16.798376Z`, `nModSinceAnalyze=0`이었다. Measurement-state before `2026-07-16T05:31:31.009787Z`, after `2026-07-16T05:34:40.811198Z` 사이에서 insert-triggered autovacuum이 measured 시작 약 45초 뒤 실행돼 strict integrity가 거부했다.
- read-only 근거: `autovacuum_naptime=60s`, `autovacuum_vacuum_insert_threshold=1000`, `autovacuum_vacuum_insert_scale_factor=0.2`. J autovacuum 완료 뒤 `campus_members.n_ins_since_vacuum=0`; 현재 `charge_items.n_ins_since_vacuum=105000`, live row 534011이므로 다음 fresh 35,000-row fixture도 insert-triggered vacuum을 유발할 수 있다.
- 채택 경계: J의 latency, throughput, resource 수치는 baseline 또는 개선 성과로 채택하지 않는다.
- 복구/보존: 측정 계정 15026/15027은 모두 USER로 복구됐고 canonical lock free와 running k6 없음이 확인됐다. J namespace/DB rows/report는 partial rejected evidence로 보존하며 재사용하지 않는다.
- 승인된 재발 방지: 기존 ANALYZE-only command를 fixture COMMIT 직후 exact `VACUUM (ANALYZE) campus_members, payment_accounts, charge_items;`로 교체한다. Immutable PostgreSQL ID의 psql stdin으로 transaction 밖에서 실행하고 dataset binding/expectations/preflight/warmup보다 앞서 `fixture-vacuum-analyze.txt`와 exact table completion marker를 남긴다. `users`/다른 table, `VACUUM FULL`, `FREEZE`, 데이터 삭제/schema/index/Flyway/config/reset/extension 변경은 없고 measured before/after strict maintenance continuity도 유지한다.
- Fresh K 제안: `I193_BEFORE_20260716_K / I193_FIXTURE_20260716_K / EXEC193_BEFORE_20260716_K`, report `build/reports/k6/issue-193/I193_BEFORE_20260716_K/I193_FIXTURE_20260716_K/EXEC193_BEFORE_20260716_K`. 개발 세션은 실제 Docker/DB/k6를 실행하지 않으며 PM finding 0 뒤 PM만 단독 실행한다.

### 2026-07-16 Issue #193 actual-before attempt K rejected evidence

- 실행 식별자: `I193_BEFORE_20260716_K / I193_FIXTURE_20260716_K / EXEC193_BEFORE_20260716_K`.
- 실행 범위: campus/fixture와 exact 3-table VACUUM(ANALYZE), warmup, measured 16 cases를 완료했다. Measured HTTP 6,000건, failure 0이며 exact window는 `2026-07-16T05:48:51Z`~`2026-07-16T05:52:00Z` 부근이다.
- 거부 원인: `charge_items`, `campus_members`, `payment_accounts`는 `nModSinceAnalyze=0`과 maintenance state가 안정적이었고 users analyze/vacuum/auto-maintenance timestamp/count도 안정적이었다. 그러나 users `nModSinceAnalyze`가 before 72, after 73으로 바뀌어 strict validator가 거부했다. Fresh measured login의 `last_login_at` UPDATE 통계가 기존 1초 stable pair 72/72 뒤에 늦게 반영된 false contamination이며 measured GET workload write로 해석하지 않는다.
- 채택 경계: K latency/throughput/resource 수치는 baseline 또는 개선 성과로 채택하지 않는다.
- 복구/보존: 측정 계정 15028/15029는 모두 USER로 복구됐고 canonical lock free와 running k6 없음이 확인됐다. K namespace/DB rows/report는 partial rejected evidence로 보존하며 재사용하지 않는다.
- 재발 방지: fresh measured login 직후, before evidence/stable-pair/counter/window보다 앞서 immutable PostgreSQL ID의 stdin으로 exact `VACUUM (ANALYZE) users;`를 실행하고 users-only completion marker를 남긴다. 다른 table, `VACUUM FULL`, `FREEZE`, 데이터/schema/index/Flyway/config/reset/extension 변경은 없고 fixture 3-table VACUUM 및 measured strict continuity는 유지한다.
- Fresh L 제안: `I193_BEFORE_20260716_L / I193_FIXTURE_20260716_L / EXEC193_BEFORE_20260716_L`, report `build/reports/k6/issue-193/I193_BEFORE_20260716_L/I193_FIXTURE_20260716_L/EXEC193_BEFORE_20260716_L`. 개발 세션 actual 실행은 금지한다.
