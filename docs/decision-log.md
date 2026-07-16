# FaithLog Decision Log

This file records user-approved project decisions so Codex does not rely on guesses later.

## Rules

- Every product, architecture, data, deployment, test-strategy, or resume-metric decision must come from the user.
- If Codex is unsure, Codex must ask before implementation.
- Record the decision date, context, options if relevant, the user's decision, and the implementation impact.

## Decisions

### 2026-07-16 - Issue #195 Current-Develop Member List Scenario Boundary

- Context: 사용자는 성능 이슈 #192-#199의 test/scenario code는 병렬 보정하되 실제 measurement slot은 PM만 순차 사용하도록 결정했다. 기존 #195 before 시나리오는 최신 `origin/develop`의 #200 권한/담당자 조회, #201 page/archive, #202 RLS, #206 ordering 계약을 다시 대조해야 한다.
- Decision: #195는 `scenario-ready/not-measured` 상태에서 관리자 사용자·캠퍼스 목록과 캠퍼스 멤버·담당자 목록 시나리오만 유지한다. 관리자 목록은 기본 `size=20`, 최대 `size=100`을 분리하고, 멤버 목록은 ACTIVE membership과 ID 오름차순, 담당자 목록은 명시적 `staleOnly=false`와 ACTIVE assignment/ACTIVE membership 및 ID 오름차순을 검증한다. 네 endpoint 모두 지원하지 않는 `includeArchived`를 보내지 않는다.
- Decision: #200 반영 후 담당자 목록의 사용자 조회는 이미 bulk이고 캠퍼스 멤버 목록은 per-member인 current-develop 상태를 그대로 기록한다. #202는 `FORCE ROW LEVEL SECURITY`를 사용하지 않는 owner-JDBC 경계만 정적으로 기록하고 실제 연결 연속성은 승인된 측정까지 미검증으로 둔다. #206 billing tie-break는 #195 endpoint에 적용하지 않으며, #195는 각 API의 명시적 ID 오름차순 계약만 검증한다.
- Impact: production Java/API/권한/응답/오류/트랜잭션/Entity/DB/Flyway/dependency를 변경하지 않는다. test code는 병렬 보정할 수 있지만 fixture/HTTP/DB/Docker/k6 actual load는 PM exclusive window에서만 순차 수행한다. 수치와 개선 성과는 실제 측정 전까지 기록하지 않는다.
- Decision: 실제 fixture/measurement는 manifest의 exact `origin/develop` commit과 runtime-required app/PostgreSQL/Redis Compose service·full image ID·container identity, credential, VUS/duration/cardinality, resource boundary maximum gap, k6 binary를 default 없이 받아야 한다. Fixture는 app/PostgreSQL/Redis를 pre-lock/post-lock/final에, measurement는 세 container와 PostgreSQL server/postmaster를 post-lock, 각 case 전후, final에 exact 연속성 검증한다. Redis local Compose에 없는 password를 발명하지 않는다.
- Decision: k6 direct/`metric.values` summary는 Counter count와 Rate `passes + fails`가 같고 `fails=0`, `rate=0`이어야 한다. Resource evidence는 app/PostgreSQL/Redis exact set과 full immutable identity, CPU percent/memory bytes, runtime-approved boundary cadence를 검증한다. Report를 source/target preflight 전에 fresh execution path로 생성하고 이후 최초 실패는 secret-free `first-rejection.json`을 exclusive create해 후속 실패가 덮어쓰지 않는다. 최종 classification은 계속 `automaticAdoption=false`다.
- Decision: 준비 조건이 충족되면 성능 부하는 별도 사용자 재승인 없이 PM이 한 서버 한 load로 순차 실행한다. Workload 값은 script default로 만들지 않고 PM handoff가 runtime input으로 명시한다. 현재 추천값은 warmup `1 VU/30s`, measured `10 VU/2m`, failure `0`, token safety `120s`, resource boundary maximum gap `10s`이며 측정값이나 자동 채택 성과로 기록하지 않는다.
- Decision: #192의 999 ACTIVE USER + 1 ADMIN dataset은 정확히 1,000 ACTIVE USER/admin-excluded인 #195 계약에 재사용하지 않는다. Fresh `PERF_1000_20260716_195_A` namespace는 공개 signup만으로 1,000 ACTIVE USER를 additive 생성하고, collision/partial/ADMIN/inactive/duplicate는 cleanup 없이 ID 전체를 폐기한다. Provisioning은 signup 직후 ADMIN을 다시 로그인하고, JWT `exp`가 runtime safety margin 이상인지 검증하며 verification page/detail마다 재검사해 부족할 때만 refresh한다. Credential/token/expiry 원문은 report/log/argv/commit에 남기지 않고 refresh 횟수만 manifest에 허용한다.
- Decision: #195 relationship fixture도 `TOKEN_SAFETY_MARGIN_SECONDS`와 absolute `PERF_REPORT_ROOT`를 default 없이 요구하고 모든 authenticated request 직전에 JWT `exp` margin을 검사한다. Report directory는 Docker/API보다 먼저 exclusive reserve하며, 이후 최초 실패는 partial mutation count와 refresh count만 담은 secret-free receipt로 보존한다. Runtime target은 numeric loopback과 app published port를 exact bind하고, app/PostgreSQL/Redis 모두 full 64-hex container/image identity, name, `StartedAt`, Compose project/service, ports schema를 fail-closed 검증한다. Docker inspect child에는 명시적인 non-secret environment allowlist만 전달한다.

### 2026-07-16 - Issue #206 Stable Charge Item Pagination Ordering

- Context: Issue #193 current-develop before preflight에서 `sort=createdAt,desc`로 조회한 회원별 청구 상세의 동일 `created_at` 행이 일부는 ID 오름차순, 일부는 내림차순으로 반환됐다. primary 정렬만 있는 offset paging은 동률 행의 페이지 간 중복·누락 가능성이 있어 정확한 baseline 검증을 중단했다.
- Decision: `BillingPageRequests.chargeItems()`를 사용하는 내 청구 목록과 관리자 회원별 청구 상세는 기존 primary property/direction을 유지하고, primary 값이 같으면 같은 방향의 `id` secondary sort를 자동 적용한다. 모든 기존 허용 primary sort에 적용하며 클라이언트가 `id`를 직접 sort parameter로 지정하는 새 계약은 만들지 않는다.
- Impact: API path/query 형식, page/size/filter/권한, 응답 DTO, ErrorCode, 관리자 회원 집계의 기존 `userId` tie-break, DB/Flyway/dependency는 변경하지 않는다. production frontend API client/type/UI 수정은 필요 없다. 다만 integration mock의 `getMockAdminMemberChargeState`, `getMockMemberChargeList`는 primary 방향과 무관하게 ID ASC tie-break를 사용하므로 DESC 계약에 맞춘 별도 최소 수정과 테스트 갱신이 필요하다.

### 2026-07-15 - Issue #202 Supabase Data API Deny-All Security Boundary

- Context: Supabase Security Advisor reported `rls_disabled_in_public` for every `public` table and `sensitive_columns_exposed` for `payment_accounts.account_number` and `user_fcm_tokens.token`. A privilege audit confirmed that `anon`, `authenticated`, and `service_role` had CRUD grants while all 26 public tables had RLS disabled. FaithLog uses direct PostgreSQL JDBC as `postgres` and does not use Supabase Auth, PostgREST, GraphQL, an anon key, or a service-role key.
- Decision: Supabase Data API is not an approved FaithLog application interface. Keep it disabled where the project setting is available and enforce database-level deny-all defense in depth: enable RLS on every public table, create no permissive policies, revoke public/Data API role access to the public schema and all current tables, sequences, and functions, and revoke the Flyway execution role's matching default privileges for future objects. Do not use `FORCE ROW LEVEL SECURITY`, so the direct JDBC owner remains the application path.
- Decision: Flyway V11 excludes `flyway_schema_history` from its RLS loop because Flyway locks that table while a migration runs. Hosted Supabase application must enable RLS on `flyway_schema_history` as a separate DDL statement in the same approved security operation. V11 still revokes all Data API table privileges, including the history table. No application row is inserted, updated, or deleted.
- Impact: Production verification requires 26/26 public tables with RLS, zero public table grants and schema usage for `anon`/`authenticated`/`service_role`, zero RLS policies, an expected permission-denied query after `SET ROLE anon`, direct `postgres` JDBC continuity, and zero Critical Supabase Security Advisor findings. Any future table created outside the `postgres` Flyway path requires the same audit because managed `supabase_admin` default privileges are outside the application migration owner's control.

### 2026-07-15 - Issue #201 Pagination Metadata And Archived Record Visibility

- Context: 청구 목록 응답 5개는 `members` 또는 `items`만 반환해 프론트가 다음 페이지 존재 여부를 정확히 판단할 수 없었다. 모바일 목록에 오래된 완료 청구와 마감 투표가 계속 쌓이지만, 사용자는 미납과 진행 중 데이터는 기간과 무관하게 확인해야 한다.
- Decision: 관리자 사용자·캠퍼스·정산·알림 로그 화면의 기본 페이지 크기는 20을 유지하고, 사용자·담당자 모바일 목록은 기본 10을 사용한다. 모든 목록의 최대 `size`는 기존 100을 유지한다.
- Decision: 관리자 캠퍼스 청구, 관리자 담당 계좌 청구, 관리자 회원 청구 상세, 회원 본인 청구, MEAL 담당 계좌 청구 응답은 기존 `members` 또는 `items`를 유지하면서 `page`, `size`, `totalElements`, `totalPages`를 필수로 추가한다.
- Decision: 위 청구 5개 조회에 `includeArchived=false`를 추가한다. 기본 조회는 생성 시점과 무관하게 모든 `UNPAID`를 포함하고, `PAID`는 `paidAt`, `WAIVED`와 `CANCELED`는 `updatedAt` 기준 최근 1개월만 포함한다. `includeArchived=true`는 이전 완료 기록을 포함한다.
- Decision: MEAL 담당 투표 목록에 `includeArchived=false`를 추가한다. `OPEN`과 `SCHEDULED`는 기간 제한 없이 포함하고 `CLOSED`는 `endsAt` 기준 최근 90일만 포함한다. `includeArchived=true`는 이전 마감 투표를 포함한다.
- Decision: 과거 청구와 투표 row는 삭제하지 않는다. 프론트는 기본 조회와 분리된 `이전 기록 보기` 동작으로 `includeArchived=true`를 보내고 필터 변경 시 0페이지부터 다시 조회한다.
- Impact: API path와 기존 목록 필드, 정렬, 최대 페이지 크기, 권한은 유지한다. REST Docs와 프론트 타입/runtime validation/페이지 이동 UI를 함께 갱신하며, 관리자 화면은 `size=20`, 사용자·담당자 화면은 `size=10`을 명시한다.

### 2026-07-14 - Issue #200 Coffee Duty Ownership And Duty Charge Reminders

- Context: 기존 COFFEE 담당은 캠퍼스별 1명만 활성화할 수 있고 새 지정이 이전 담당을 자동 해제했다. service ADMIN과 캠퍼스 관리자는 담당 여부와 무관하게 COFFEE command를 수행할 수 있었으며, 일반 `PAYMENT_UNPAID` 알림은 PENALTY/COFFEE/MEAL 미납을 캠퍼스 단위로 함께 조회했다.
- Decision: 같은 캠퍼스에 여러 ACTIVE COFFEE 담당자를 허용하고 동일 캠퍼스·담당 유형·사용자의 ACTIVE 중복만 금지한다. 기존 COFFEE 지정 `PUT` API는 경로를 유지하되 다른 담당자를 해제하지 않는 idempotent 추가 의미로 변경한다. 관리자는 COFFEE 현황을 읽을 수 있지만 투표·템플릿·계좌·청구의 생성, 수정, 마감, 정산과 알림 발송은 ACTIVE COFFEE 담당자만 수행한다. 각 담당자는 본인이 만든 투표와 본인 소유 계좌 및 해당 계좌 청구만 변경할 수 있고, COFFEE 템플릿은 같은 캠퍼스 ACTIVE COFFEE 담당자들이 공동 관리한다.
- Decision: COFFEE 또는 MEAL 담당자 본인 소유 계좌에 UNPAID 청구가 하나라도 있으면 담당 해제를 `409`로 거부한다. 비활성 계좌의 미납도 포함하며 PAID, WAIVED, CANCELED는 해제를 막지 않는다.
- Decision: COFFEE/MEAL 담당 청구 알림 API는 charge ID 선택을 받지 않고 요청 담당자의 소유 계좌에 연결된 해당 category의 모든 UNPAID 청구를 조회한다. 계좌와 수신자별 미납 합계를 서버 고정 제목·본문으로 발송하고, 같은 담당 계좌·수신자·영업일의 `PAYMENT_UNPAID` 알림은 하루 1회만 생성한다. 다른 캠퍼스·category·담당자 계좌와 미납이 아닌 청구는 대상에서 제외한다.
- Decision: 알림 title은 각각 `커피 미납 청구 안내`, `밥 미납 청구 안내`로 고정한다. body는 수신자의 청구 `title`별 건수와 금액을 최초 청구 ID 순서로 묶어 최대 5종까지 표시하고, 초과 항목은 `외 N종`으로 줄인 뒤 전체 미납 합계를 항상 표시한다. 예시는 `커피 미납: 아이스 아메리카노 2건 3600원, 카페라떼 1건 2900원 / 총 6500원입니다. 확인 후 납부해 주세요.`이다. 알림 API 경로와 body 없는 요청, `202 Accepted`의 `notificationRequestId`/`queuedCount`/`skippedCount` 계약은 변경하지 않으며, 일일 기준은 `Asia/Seoul`이다. 일일 중복과 활성 FCM 토큰 없음은 `skippedCount`에 포함한다.
- Decision: COFFEE 투표는 scheduler 자동 생성 대상에서 제외한다. 기존 COFFEE 템플릿과 API 필드는 삭제하거나 변경하지 않고, ACTIVE COFFEE 담당자가 투표를 수동 생성한다. 수동 생성된 COFFEE 투표의 예정 마감과 CLOSED 정산 동작은 유지한다.
- Decision: 공동 관리 COFFEE 템플릿은 계좌 중립으로 저장한다. 기존 `paymentAccountId` 요청/응답 필드는 호환을 위해 유지하되 COFFEE 템플릿 create/update에서는 요청값을 저장하지 않고 null을 반환한다. 템플릿 기반 COFFEE 투표를 실제 생성할 때 기존 투표 생성 요청의 `paymentAccountId`로 요청자 본인 소유 ACTIVE COFFEE 계좌를 지정한다. 정상 COFFEE 템플릿의 기존 계좌 연결은 V10 migration에서 null로 정리한다. `pollType`, `chargeGenerationType`, `paymentCategory` 중 일부만 COFFEE 의미를 가진 레거시 혼합 row는 삭제하거나 새 migration을 추가하지 않고 같은 V10에서 row를 보존한 채 `is_active=false`, `auto_create_enabled=false`, `payment_account_id=null`로 격리한다.
- Decision: 담당 해제와 COFFEE/MEAL 정산은 같은 ACTIVE 담당자 배정 행의 `PESSIMISTIC_WRITE` 잠금을 공유한다. 정산이 먼저면 해제가 새 UNPAID를 확인해 409를 반환하고, 해제가 먼저면 정산은 ACTIVE 담당자 검증에 실패한다.
- Decision: 위 직렬화 계약은 정산뿐 아니라 투표·템플릿·계좌·청구 상태·미납 알림의 모든 duty-gated command에 적용한다. 캠퍼스 row를 함께 사용하는 사용자 요청 command는 requester user row를 먼저 잠근 뒤 `user -> campus -> duty -> entity` 순서를 사용하고, 캠퍼스 row를 사용하지 않는 기존 command는 `duty -> poll/template/account/charge` 순서를 사용한다. 공유 COFFEE 템플릿 update/deactivate/create-from-template은 같은 template row `PESSIMISTIC_WRITE`로 직렬화한다. 미납 알림의 Redis 일일 중복 예약은 DB transaction이 commit되지 않으면 먼저 해제하고 그 뒤 manual lock을 해제한다. commit 뒤 manual lock 해제만 실패하면 이미 커밋된 PENDING log와 일일 dedupe는 보존한다.
- Decision: 일반 ACTIVE COFFEE 담당자의 회원별 COFFEE 청구 상세와 알림 조회는 requester 본인 소유 payment account ID 집합을 DB query 조건으로 사용한다. 캠퍼스 관리자와 전역 ADMIN의 기존 전체 read는 유지한다. 알림 실제 dispatch는 request 전체 token bulk snapshot을 사용하되 영구 실패로 비활성화한 token은 같은 request의 이후 log에서도 즉시 제거한다.
- Decision: 투표 목록과 상세 응답에는 서버 계산 `manageableByMe`만 추가하고 내부 `Poll.createdBy` 및 생성자 사용자 ID는 클라이언트에 공개하지 않는다. COFFEE는 현재 요청자가 ACTIVE COFFEE 담당자이면서 해당 투표 생성자일 때만 true이고, MEAL은 실제 전용 command와 동일하게 ACTIVE MEAL 담당자일 때만 true다. 그 외 일반 투표는 기존 관리자 command 권한과 동일하게 계산한다. 프론트는 역할이나 생성자 ID를 재조합하지 않고 이 필드로 관리 버튼을 제어한다.
- Decision: ACTIVE COFFEE 또는 MEAL 담당 배정이 하나라도 남은 캠퍼스 회원은 먼저 개별 담당 해제를 완료해야 하며, 회원 삭제는 `409 CAMPUS_MEMBER_ACTIVE_DUTY_CONFLICT`로 거부한다. 과거 데이터에 INACTIVE 멤버십과 ACTIVE 담당 배정이 함께 남아 있으면 담당 목록에서 숨기고, 같은 사용자의 재가입도 동일한 409로 거부해 과거 capability를 자동 복원하지 않는다. 자동 담당 해제나 책임 이전은 하지 않으며 관리자가 기존 담당 해제 API를 사용한다. 해당 담당자 소유 계좌에 UNPAID가 있으면 기존 COFFEE/MEAL category별 409가 담당 해제를 계속 차단한다. 회원 삭제·재가입과 담당 지정·해제는 `campus -> duty -> member` 순서로 직렬화한다.
- Decision: 과거 INACTIVE 멤버십에 남은 ACTIVE 담당의 복구는 기존 관리자 담당 목록에 optional `staleOnly=true` query를 추가해 제공한다. 기본값은 `false`라 정상 목록은 기존처럼 ACTIVE 멤버십 담당만 반환하고, `true`이면 stale 담당만 기존 응답 구조로 반환해 관리자가 `assignmentId`로 기존 해제 API를 호출한다. 어느 캠퍼스든 ACTIVE COFFEE/MEAL 담당이 남은 사용자의 `DELETE /api/v1/users/me`도 `409 CAMPUS_MEMBER_ACTIVE_DUTY_CONFLICT`로 차단하고 자동 해제하지 않는다.
- Decision: 계정 탈퇴, 로그인, 신규 가입, 캠퍼스 생성, 관리자 직접 회원 추가, 서비스 역할 변경, 캠퍼스 역할 변경과 회원 삭제는 동일 user row 잠금을 공유한다. 여러 사용자를 잠글 때는 user ID 오름차순, 계정 탈퇴가 여러 캠퍼스를 처리할 때는 campus ID 오름차순을 사용하며 전체 순서는 `user -> campus -> duty -> member`다. 캠퍼스 잠금 대기 뒤에는 requester의 최신 ACTIVE 멤버십과 캠퍼스 관리 역할을 다시 검증한다. 이 순서로 탈퇴 중 신규 가입과 stale User flush에 의한 계정 부활, 권한 강등 뒤 회원 삭제 성공을 차단한다.
- Decision: INACTIVE 캠퍼스 멤버십에 같은 category의 ACTIVE COFFEE/MEAL 담당이 남고 그 담당자 소유 계좌에 UNPAID가 있는 복구 불가 상태에서는 서비스 전역 `ADMIN`만 기존 `PATCH /api/v1/admin/charges/{chargeItemId}/status`로 해당 청구를 `PAID`, `WAIVED`, `CANCELED` 중 하나로 명시 처리할 수 있다. 정상 ACTIVE 담당자 청구, 다른 담당자 계좌, 비담당 계좌, 일반 캠퍼스 관리자에는 이 예외를 적용하지 않고 자동 상태 변경도 하지 않는다. 잘못된 `staleOnly` 값은 `400 GLOBAL_VALIDATION_FAILED`로 반환한다.
- Decision: stale 담당 청구 복구는 requester user를 먼저 잠그고 `user -> campus -> duty -> member -> charge` 순서로 직렬화하며, 잠긴 청구가 여전히 UNPAID일 때만 terminal 상태로 처리한다. 복구 요청의 `UNPAID` target은 409로 거부한다. 전역 역할 변경은 가장 작은 user row를 공통 직렬화 지점으로 먼저 잠근 뒤 requester/target의 최신 역할을 잠금 조회해 마지막 ACTIVE `ADMIN` 1명 불변식을 판단한다. `staleOnly`의 잘못된 값만 controller 범위에서 `400 GLOBAL_VALIDATION_FAILED`로 변환하고 다른 API의 path/query type 오류 계약은 전역 변경하지 않는다.
- Decision: PM finding 0과 backend 전체 test/build/asciidoctor/diff check 뒤 최신 API·권한·ErrorCode·프론트 UI 변경 명세를 PM 세션에 전달한다. PM은 지정된 프론트 작업 세션에 명세를 전달하며, 프론트 수정과 정적 검증이 끝나기 전에는 backend develop 병합 또는 Docker QA를 시작하지 않는다. 양쪽 준비 뒤 PM이 backend 통합, Docker Compose 서버 기동, 수정 frontend의 iOS Simulator 설치와 연결 E2E QA를 수행한다. 개발 세션은 push, PR, merge, Docker를 수행하지 않는다.
- Decision: 통합 Docker/iOS QA 중 디스크가 부족하면 재생성 가능한 backend/frontend 프로젝트 build 산출물, 해당 프로젝트 iOS DerivedData/Expo bundle cache, Docker BuildKit builder cache 순서로 정리할 수 있다. Docker named volume, PostgreSQL/Redis 데이터, `docker volume/system prune`, 소스·문서·인증서·키·사용자 파일 삭제는 금지한다. `node_modules`와 전역 Gradle dependency cache는 앞선 정리로 부족할 때 현재 용량과 후보를 PM에 먼저 보고하며, 정리 전후 `df`/`du`와 실제 삭제 범위를 QA 보고에 기록한다.
- Impact: 기존 관리자 범용 알림 API와 scheduler 알림은 유지하고 담당 청구 전용 application boundary/API를 추가한다. CampusDutyAssignment repository 조회는 사용자별 활성 여부를 기준으로 통일하고, active duty unique 제약은 `(campus_id, duty_type, user_id)`로 변경한다. 알림 endpoint/요청/202 응답 DTO와 DB schema는 추가 승인으로 변경되지 않으며 API, ErrorCode, Flyway, REST Docs와 프론트 연동 계약을 함께 갱신한다.

### 2026-07-13 - Issue #190 Penalty Cancel Resubmission And Admin Paid

- Context: 기존 관리자 청구 상태 변경은 `PAID`를 금지했고, `PENALTY + DEVOTION_RECORD` 청구 취소가 source weekly record를 재오픈하지 않아 사용자가 잘못 제출한 경건생활을 수정·재제출할 수 없었다. 기존 unique source key 때문에 CANCELED 청구가 있는 weekly record를 다시 제출하면 양수 벌금 upsert도 terminal charge 오류로 실패했다.
- Decision: 기존 `PATCH /api/v1/admin/charges/{chargeItemId}/status`에서 관리 가능한 모든 category의 `UNPAID -> PAID`를 허용하고 서버 현재 시각을 `paidAt`으로 저장한다. terminal 상태에서 `PAID`로의 전환은 `409 BILLING_CHARGE_STATUS_TRANSITION_CONFLICT`로 거부하며 기존 관리자 권한과 401/403 구분, 사용자 `납부했어요` API 의미를 유지한다.
- Decision: `UNPAID`인 `PENALTY + DEVOTION_RECORD` 청구를 `CANCELED`로 전환할 때 Billing transaction owner가 application port를 호출하고 Devotion adapter가 같은 campus/user/source weekly record를 검증한 뒤 `submittedAt`만 null로 만든다. daily checks는 보존하고, `WAIVED`, 다른 category/source는 재오픈하지 않으며 검증 실패 시 charge 취소도 rollback한다.
- Decision: 재제출은 현재 활성 벌금 규칙으로 계산한다. 양수이면 기존 CANCELED source charge row를 같은 ID로 `UNPAID` 재활성화해 amount/title/reason/dueDate/계좌와 snapshot을 갱신하고 `paidAt`을 null로 둔다. 0원이면 기존 row를 CANCELED로 유지하고 새 row를 만들지 않는다. #182 `amount > 0` 및 V7 CHECK를 유지한다.
- Impact: API path/envelope/DTO, Controller Entity 비반환, 권한, DB schema/Flyway V1-V7, dependency, COFFEE terminal 보존 정책은 변경하지 않는다. Billing Entity는 Devotion Entity를 참조하지 않고 Billing port와 Devotion adapter 경계를 사용한다. 같은 청구의 사용자·관리자 상태 쓰기와 PENALTY/COFFEE 기존 source charge 갱신·재활성화는 동일 row write lock으로 직렬화해 뒤 요청이 커밋된 상태를 다시 읽고 기존 상태별 전이 규칙을 적용하도록 한다.

### 2026-07-13 - Issue #188 Weekly Penalty Total Status Basis

- Context: Issue #188 returns each active member's actual weekly `PENALTY` charge amount and status and also exposes `totalPenaltyAmount`. The Issue, previous decisions, and Notion did not define which charge statuses contribute to that total.
- User approval evidence: In the current Issue #188 development conversation, the user explicitly answered, "각자 주차별이 페이드랑 언페이드랑 합산할게", selecting the `UNPAID + PAID` basis rather than an agent recommendation being treated as approval.
- Decision: Each member row displays the actual charge `amount` and `status` for the weekly devotion record regardless of whether it is paid. `totalPenaltyAmount` sums charges whose status is `UNPAID` or `PAID`. A weekly member charge has only one current status, so it contributes to exactly one of those status buckets. `WAIVED` and `CANCELED` charges remain visible with their stored amount and status but do not contribute to `totalPenaltyAmount`.
- Impact: The JSON API and Excel export use the same query model and identical `PAID + UNPAID` total basis. Historical amounts are read from `charge_items` and are never recalculated from current penalty rules.

### 2026-07-13 - Development Completion Requires Independent PM Review And Integration Branch

- Context: The user established one completion and review workflow beginning with Issue #188 and applying to every later development issue.
- Decision: A feature development session completes implementation, focused/full tests, `./gradlew test`, `./gradlew build`, `./gradlew asciidoctor`, `git diff --check`, REST Docs, repository documentation, Obsidian records, and small work-unit commits, but does not run Docker build/up/API QA and does not push, create a PR, or merge. It then sends the source PM session a detailed review report covering the entire `origin/develop...HEAD` range. The PM session independently reviews the full diff. Each PM finding must be reproduced or evidence-checked, minimally fixed, fully reverified, committed, and reported again.
- Decision: For the parallel #188/#189/#190 work, all three feature branches must reach zero PM findings and pass every required non-Docker verification before the PM session creates `integration/188-190-devotion-meal-billing` from the latest `origin/develop` and merges the three approved feature branches there. Development sessions do not create a PR or merge into `develop`. CI failures or integration conflicts keep the affected issue open and require correction, re-verification, and re-review.
- Decision: Isolated PostgreSQL/Redis/backend Docker build/up/health and connected real API QA for all three features run once on the integration branch. After QA, only that compose project is stopped with `down`, preserving volumes, and the final Docker command is `docker builder prune -f`. `down -v`, named volume deletion, and Docker system/image/volume prune are prohibited.
- Impact: Development-session completion reports include repository identity and cleanliness, all commits/diff ranges, API/authorization/transaction/DB/Flyway/dependency changes, RED/GREEN evidence, focused/full/build/asciidoctor/diff-check results, the explicit Docker deferral, regression and unchanged scope, REST Docs/index, repository/Obsidian records, and all risks, pending decisions, and unverified items. A prior #188 Docker attempt failed during Docker Desktop storage operations with only 561MiB host space available; this observation is retained but is not a feature completion blocker under the superseding decision. No file or Docker data deletion is authorized. PM approval is a mandatory integration gate and does not authorize a development session to push, open a PR, or merge. An issue is final only after the PM integration branch includes it and the Issue is closed or completion is confirmed.

### 2026-07-13 - Issue #183 COFFEE Option Catalog Authority

- Context: Issue #160 F-160-02 confirmed that direct COFFEE Poll and COFFEE PollTemplate create/update accepted an option with `menuId = null`, allowing client `content` and `priceAmount` to become the persisted snapshot and later CLOSED settlement charge source.
- Decision: Every option in a `COFFEE` Poll or persisted `COFFEE` PollTemplate create/update requires an active backend `CoffeeMenuCatalog` `menuId`. Missing IDs fail with `400 POLL_COFFEE_OPTION_MENU_REQUIRED` and message `커피 투표 선택지는 menuId가 필요합니다.`. Missing and inactive catalog rows continue to use `POLL_MENU_NOT_FOUND` and `POLL_MENU_INACTIVE`.
- Decision: For COFFEE options, the server always snapshots `content = catalog.name`, `composeMenuCode = catalog.menuCode`, and `priceAmount = catalog.priceAmount`. Existing request DTO fields remain for frontend compatibility, but supplied COFFEE `content` and `priceAmount` are ignored rather than treated as authority. Non-COFFEE custom content/zero-price behavior and the existing user COFFEE option-add `menuId`-only contract remain unchanged.
- Impact: Direct Poll, template create, and persisted-template update share the catalog resolver while preserving #179 persisted-target authorization order. Template-to-Poll automatic creation and CLOSED settlement continue copying/using saved snapshots. API paths, normal response DTOs, `optionIds`, `poll_response_options`, authorization, DB/Flyway, Billing positive-amount invariants, and dependencies remain unchanged.

### 2026-07-13 - Issue #182 Devotion Fine Range And Positive Charge Integrity

- Context: Issue #160 F-160-01 confirmed that unbounded `saturdayLateMinutes`, unchecked `int` arithmetic, and the absence of Billing/database positive-amount invariants could persist a negative `UNPAID` PENALTY charge and distort dashboard totals.
- Decision: Weekly devotion requests accept `saturdayLateMinutes` only from 0 through 1,440 inclusive. Values outside that range reuse `DEVOTION_INVALID_SATURDAY_LATE_MINUTES` with HTTP 400 and message `saturdayLateMinutes는 0 이상 1,440 이하이어야 합니다.`. Fine calculation uses `long` with explicit exact multiplication and addition. Arithmetic overflow or a total outside the persisted PostgreSQL `INTEGER` range fails with `DEVOTION_FINE_AMOUNT_OUT_OF_RANGE`, HTTP 400, and `계산된 벌금 금액이 허용 범위를 초과했습니다.`.
- Decision: Billing creates and updates only charges whose `amount > 0`; a calculated total of exactly 0 continues to skip PENALTY charge creation. Flyway V7 adds `ck_charge_items_amount_positive` and validates it during migration. If any legacy zero/negative row exists, V7 fails closed and rolls back without modifying or deleting historical data. Existing invalid production data must not be modified or deleted without a separate PM decision.
- Impact: Weekly submission, seven daily rows, fine calculation, and Billing remain in one rollback boundary. API paths, successful request/response DTOs, penalty formula, charge storage type (`INTEGER`), dashboard response, and normal PENALTY/COFFEE behavior remain unchanged. V1-V6 migrations are not edited.

### 2026-07-12 - Issue #176 Atomic Refresh Rotation And Reuse Session Revocation

- Context: Issue #158 confirmed that refresh current-JTI validation and replacement were separate Redis GET and SET operations, so two parallel requests using the same old refresh token could both issue token pairs. Issue #176 fixes this race without changing the public auth API, token response, token lifetime, logout meaning, role invalidation, withdrawal, or FCM behavior.
- Decision: One Redis Lua/CAS operation handles the entire refresh state transition. When `expectedRefreshJti` matches, it replaces the value with `newRefreshJti + rotation TTL`. On mismatch, the same script deletes the affected `userId + sessionId` refresh key and stores `auth:session:revoked:{userId}:{sessionId}` with a fixed marker and revocation TTL before returning rejection. If the marker already exists, the script rejects without extending its TTL. Exactly one request using the same old refresh token may win, and the loser or sequential reuse returns `401 AUTH_UNAUTHORIZED`. Other sessions belonging to the same user and sessions belonging to other users remain valid. Redis failures fail closed.
- Decision: Keep the session revocation marker for the configured refresh-token validity plus a 60-second safety margin. With the current approved configuration this is `1,209,600 + 60` seconds. Login creates a new UUID session ID and is not blocked by an older session marker. Do not expand normal logout into session-marker revocation; Issue #176 session revocation is limited to refresh reuse/CAS-loser detection.
- Impact: `RefreshTokenStore` exposes one atomic rotate-or-revoke result contract with both rotation and revocation TTLs. The production adapter uses one Redis Lua execution, and the test adapter reproduces the same synchronized state transition. Only the session revocation checker remains separate for `JwtAuthenticationFilter`; no second application-level revocation write is performed after rejection. API paths, request/response DTOs, `AUTH_UNAUTHORIZED`, access 1,800 seconds, refresh 1,209,600 seconds, raw-token non-storage, Entity/DB/Flyway, role invalidation, withdrawal, logout, and FCM contracts remain unchanged.

### 2026-07-12 - Issue #156 User And Auth Use Case Separation

- Context: After Issue #145 established the domain-first MVC package structure and Issues #147-#155 separated the other application use cases, `AuthService` still combined signup, login, JWT/Refresh allowlist issuance, refresh rotation, logout revocation, and current-user membership queries. `UserAccountService` still combined withdrawal validation, campus/FCM deactivation, session revocation, and account soft deletion.
- Decision: Split the public boundaries into `SignupCommandService`, `LoginCommandService`, `RefreshTokenRotationService`, `LogoutCommandService`, `UserMeQueryService`, and `AccountWithdrawalCommandService`. Keep token issuance/allowlist storage in `AuthTokenIssuanceSupport`, ACTIVE campus membership result assembly in `CampusMembershipQuerySupport`, logout/withdrawal session revocation in `UserSessionRevocationSupport`, and withdrawal persistence in `AccountSoftDeletionSupport`. Controllers call the dedicated services directly, while `AuthService` and `UserAccountService` remain repository-free, transaction-free, `BusinessException`-free compatibility delegates. Each public use case directly owns its previous write or read-only transaction boundary.
- Decision: Keep the existing current-device logout FCM port. Add `UserFcmTokenDeactivationPort` for withdrawal-wide FCM deactivation and implement it in the existing `FcmTokenCommandService`, preserving the previous active-row deactivation behavior inside the withdrawal transaction. Preserve the current account-withdrawal policy without adding last-active-ADMIN withdrawal protection; the existing gap is recorded for a follow-up security audit instead of changing behavior in this refactor.
- Impact: Signup/login validation, BCrypt behavior, JWT claims/TTL/signature, Refresh allowlist and rotation, logout blacklist/allowlist/optional FCM behavior, ACTIVE campus membership results, withdrawal validation/order/anonymization/tokenVersion/session/FCM behavior, API/DTO/status/ErrorCode/message, security filter chain, Redis keys/TTL/hash semantics, Entity/DB/Flyway/repository query meaning, and dependencies remain unchanged.

### 2026-07-11 - Issue #155 Batch And Scheduler Use Case Separation

- Context: After Issues #152 and #154 separated Poll template/coffee settlement and Notification/FCM commands, `PollAutomationService` still combined scheduled template discovery, Asia/Seoul window calculation, Redis locking, transactional Poll creation, due coffee close, and settlement orchestration. `AutomaticNotificationService` still combined devotion, poll, and unpaid target discovery with three different scheduled notification jobs, while stale FCM cleanup remained a public command on the user-owned token command service.
- Decision: Split scheduled Poll creation into `ScheduledPollCreationService` and due coffee close/settlement into `DueCoffeePollClosureService`. Split automatic notification jobs into `DevotionMissingNotificationService`, `PollMissingNotificationService`, and `PaymentUnpaidNotificationService`. Move the existing 90-day stale-token repository transaction into `FcmTokenCleanupService`. Keep `PollAutomationService` and `AutomaticNotificationService` only as repository-free, transaction-free, lock-free compatibility delegates. `FaithLogScheduledJobs` calls the dedicated job services directly, and the existing `DataRetentionCleanupService` and `PendingNotificationRecoveryService` remain their already dedicated TransactionTemplate boundaries.
- Impact: Existing Asia/Seoul calculations, cron/fixedDelay/property keys, scheduler enable flag, due/template/week duplicate prevention, template and option snapshots, OPEN creation, CLOSED-before-coffee-settlement order, Billing idempotency, automatic notification times/targets/dedup/locks/fail-closed behavior, CUSTOM reminders, 90-day stale-token cutoff, 10-minute PENDING recovery, retention periods/February 1 policy/deletion order, repository query meaning, transactions, entities, DB/Flyway, API/DTO/HTTP/ErrorCode/auth, retries, TTLs, and dependencies remain unchanged.

### 2026-07-11 - Issue #154 Notification And FCM Use Case Separation

- Context: After Issue #145 established the domain-first MVC package structure and Issues #147-#153 separated the preceding application use cases, `FcmTokenService` still combined token upsert, user-requested deactivation, logout current-device port implementation, and stale-token cleanup persistence. `NotificationService` combined administrator request authorization, target resolution, PENDING/SKIPPED log creation, dispatch, and manual Redis locking, while `AutomaticNotificationService` duplicated log creation and dispatch infrastructure calls.
- Decision: Move FCM token registration, user deactivation, current-device logout deactivation, and stale-token cleanup into `FcmTokenCommandService`, which implements `CurrentDeviceFcmTokenDeactivationPort` and directly owns each write transaction. Move administrator and automatic notification request acceptance, PENDING/SKIPPED log creation, automatic business dedup reservation, and after-commit dispatch invocation into `NotificationRequestCommandService`, with both public request use cases directly owning their write transactions. Keep `NotificationDeliveryWorker`, `NotificationLogQueryService`, `NotificationDeduplicationService`, and `NotificationLockService` as the existing dedicated delivery/query/Redis boundaries.
- Decision: `FcmTokenController`, `AdminNotificationController`, `AutomaticNotificationService`, and `FcmTokenCleanupService` call the dedicated command services directly. Keep `FcmTokenService` and `NotificationService` only as repository-free, transaction-free, `BusinessException`-free compatibility delegates. The async dispatch adapter continues to invoke `NotificationDeliveryWorker` after transaction commit, and pending recovery continues to call the worker directly.
- Impact: API paths/query/request-response/status, ErrorCodes/messages, authorization, active-token ownership/upsert/deactivation order, logout deletion behavior, DB source-of-truth, PENDING/SENT/FAILED/SKIPPED transitions, retry count and `1s -> 5s -> 30s` backoff, invalid-token deactivation, recovery policy, Redis dedup/lock keys/TTL/fail-closed behavior, repository query meaning/order, entities, DB/Flyway, scheduler settings, and dependencies remain unchanged.

### 2026-07-11 - Issue #153 Prayer Use Case Service Separation

- Context: After Issue #145 established the domain-first MVC package structure and Issues #147-#152 separated Campus/Admin, Billing, Devotion, and Poll use cases, the 606-line `PrayerService` still combined season commands/queries, group commands/queries, weekly board reads, administrator multi-member submission writes, current-user submission writes, access checks, target-member loading, and result assembly.
- Decision: Split the Prayer application boundary into `PrayerSeasonCommandService`, `PrayerSeasonQueryService`, `PrayerGroupCommandService`, `PrayerGroupQueryService`, `PrayerWeekBoardQueryService`, `PrayerGroupSubmissionCommandService`, and `MyPrayerSubmissionCommandService`. The group-submission name reflects that normal ACTIVE members may save multiple submissions within their own active prayer group, while campus managers and service ADMIN retain the existing all-group scope. Keep unchanged common campus/user authorization in package-private `PrayerAccessSupport`, active group/member loading and group result assembly in `PrayerTargetMemberSupport`, and weekly board result assembly in `PrayerBoardAssembler`. Each moved public use case directly owns its previous write or read-only transaction boundary.
- Decision: Prayer Controllers call the dedicated services directly. Keep `PrayerService` only as a repository-free, transaction-free, `BusinessException`-free compatibility delegate. Dedicated services do not depend on one another or on the compatibility facade.
- Impact: API paths/request-response/status, ErrorCodes/messages, campus role authorization, `ACTIVE + endDate null` current-season policy, group replacement and same-season assignment conflict order, ACTIVE member scope and sorting, Monday/future-week behavior, GET no-write behavior, nullable content, person-level rows, optimistic version checks, administrator all-or-nothing rollback, entities, DB/Flyway, repository query meaning/order, Swagger annotations, and dependencies remain unchanged.

### 2026-07-10 - Issue #152 Poll Template And Coffee Settlement Responsibility Separation

- Context: After Issue #151 separated the Poll core use cases, `PollTemplateService` still combined three template commands, two template queries, authorization/account validation, option snapshot persistence, and result assembly. `PollAutomationService` also owned scheduled template-to-Poll/PollOption copying, while `CoffeePollSettlementService` combined eligibility validation, response/option loading, and Billing port orchestration.
- Decision: Split template writes into `PollTemplateCommandService` and reads into `PollTemplateQueryService`, with each of the five public use cases directly owning its previous write or read-only transaction. Keep template option snapshot resolution, save/replace, and ordered result loading together in package-private `PollTemplateOptionSupport`. Keep `PollTemplateService` as a repository-free, transaction-free, business-rule-free compatibility delegate, and connect `AdminPollTemplateController` directly to the command/query services. `CoffeeCatalogService` remains unchanged because its two read-only catalog queries are already cohesive.
- Decision: Move scheduled template duplication and same-week duplicate prevention into package-private `ScheduledPollFactory`, while `PollAutomationService` retains due-target discovery, Asia/Seoul scheduling window calculation, Redis lock, `TransactionTemplate`, close, and settlement orchestration. Move closed-coffee-poll eligibility and response/option assembly into package-private `CoffeePollSettlementSupport`; `CoffeePollSettlementCommandService` directly owns the all-or-nothing transaction and Billing port calls. Keep `CoffeePollSettlementService` as a thin compatibility delegate so manual close and scheduler callers remain connected in the same way.
- Impact: API paths/query/request-response/status, ErrorCodes/messages, authorization and campus-scope hiding, template configuration and option snapshots, active-template scheduling, Asia/Seoul timing, lock keys/fail-closed behavior, same-week duplicate prevention, settlement eligibility, response/option snapshots, Billing idempotency and terminal-charge protection, repository-call order, outer transaction rollback, entities, DB/Flyway, scheduler configuration, and dependencies remain unchanged.

### 2026-07-10 - Issue #151 Poll Core Use Case Service Separation

- Context: After Issue #145 established the domain-first MVC package structure and Issues #147-#150 separated Campus/Admin, Billing, and Devotion use cases, the 578-line `PollService` still combined poll creation, time-based status synchronization, administrator close and coffee settlement orchestration, response writes, member reads, result/missing-member reads, comment commands/queries, and user-option commands.
- Decision: Split the Poll application boundary into `PollCreationCommandService`, `PollStatusCommandService`, `PollResponseCommandService`, `PollQueryService`, `PollResultQueryService`, `PollCommentCommandService`, `PollCommentQueryService`, and `PollUserOptionCommandService`. Keep time-window synchronization, campus-scoped lookup/visibility, and result assembly in package-private shared helpers because these unchanged rules are used by multiple separated use cases. Each moved public method directly owns its previous write or read-only transaction boundary.
- Decision: Poll Controllers call the dedicated services directly. Keep `PollService` only as a repository-free, transaction-free, `BusinessException`-free compatibility delegate for existing internal tests and callers. Dedicated use case services do not depend on one another, and `PollTemplateService`, `CoffeePollSettlementService`, and Scheduler/Batch responsibilities remain unchanged.
- Impact: API paths/query parameters/request-response JSON/status, ErrorCodes/messages, campus and administrator/coffee-duty authorization, current-window `SCHEDULED -> OPEN` synchronization, 3-day/7-day visibility, close-to-settlement transaction behavior, `optionIds` validation order and `poll_response_options`, anonymous result identity hiding, missing-member scope, comment ownership/window rules, user-option snapshots, repository bulk-query behavior, entities, DB/Flyway, Swagger annotations, and dependencies remain unchanged.

### 2026-07-10 - Issue #150 Devotion Use Case Service Separation

- Context: After Issue #145 established the domain-first MVC package structure and Issues #147-#149 separated Campus/Admin and Billing use cases, `DevotionService` still combined daily writes, weekly draft/final submission, member weekly reads, administrator missing-member reads, fine calculation, and Billing port orchestration. `PenaltyRuleService` also combined member reads with administrator create/update commands.
- Decision: Split the Devotion application boundary into `DailyDevotionCommandService`, `WeeklyDevotionCommandService`, `MyWeeklyDevotionQueryService`, and `MissingDevotionMemberQueryService`. Keep the existing `DevotionMonthlySummaryQueryService` boundary unchanged. Split penalty-rule responsibilities into `PenaltyRuleCommandService` and `PenaltyRuleQueryService`. Weekly final submission keeps fine calculation and `DevotionPenaltyChargePort` orchestration together in `WeeklyDevotionCommandService` so submission and charge creation remain one transaction.
- Decision: Controllers call the dedicated services directly. Keep `DevotionService` and `PenaltyRuleService` only as repository-free, transaction-free, business-rule-free compatibility delegates. Each moved public use case directly owns its previous write or read-only transaction boundary, and dedicated services do not depend on one another or on compatibility facades.
- Impact: Validation and repository-call order, Monday/date/member/administrator checks, seven daily rows, one-time submission, zero-amount charge skip, positive penalty charge contract and rollback, monthly boundary aggregation, penalty-rule campus lock and active replacement, API paths/JSON/status, ErrorCodes/messages, authorization, entities, DB/Flyway, Swagger annotations, and dependencies remain unchanged.

### 2026-07-10 - Issue #165 Spring Test Context H2 Isolation Policy

- Context: Billing, Devotion, Poll, and Batch tests shared the fixed `jdbc:h2:mem:faithlog-test` database across different cached Spring Contexts. A non-transactional REST Docs context could commit Devotion fixtures after a service-test context had already initialized, and the reused service context then observed 7 unrelated charge rows and 66 unrelated daily-check rows. The exact four-domain command failed 10 `DevotionServiceTest` assertions while the same test class passed alone.
- Decision: Keep the existing test-only H2 PostgreSQL compatibility options and `ddl-auto=create-drop`, but include `${random.uuid}` in the test datasource name so each distinct Spring Context owns a separate in-memory database. Add a source-structure regression test that rejects the fixed shared URL. Do not add unconditional class-wide `@DirtiesContext`, repository cleanup coupled to every REST Docs fixture, or Production changes.
- Impact: Transactional rollback and intentionally committed test fixtures remain unchanged within one Spring Context, while different Context cache keys can no longer exchange database state. API, DTO, ErrorCode, authorization, transaction policy, Entity, DB schema, Flyway, and all `src/main` files remain unchanged.

### 2026-07-10 - Issue #149 Billing Query And Aggregation Use Case Service Separation

- Context: Issue #148 separated Billing commands but left member charge reads, administrator charge aggregation, payment-account reads, and penalty-account validation in one 673-line `BillingQueryService`. Issue #149 requires separating those read responsibilities without changing API, repository query, authorization, transaction, aggregation, sorting, or exception behavior.
- Decision: Split the nine public query surfaces into `MyChargeQueryService`, `AdminChargeQueryService`, and `PaymentAccountQueryService`. Each moved public method directly owns the same `@Transactional(readOnly = true)` boundary. Keep result assembly and access helpers private to the service that owns the corresponding use case instead of introducing a cross-service dependency or a new shared business-policy abstraction.
- Decision: Controllers and the Devotion Billing adapter call the dedicated Query Service directly. Keep `BillingQueryService` as a repository-free, transaction-free, business-rule-free compatibility delegate, and connect the Issue #148 `BillingService` compatibility façade directly to `PaymentAccountQueryService` for its legacy account-query methods.
- Impact: Member status/category/page/size/sort behavior, monthly summary bases, administrator summary/member aggregation, `paymentAccountId`, my-accounts PENALTY/COFFEE owner scope, account active/inactive/soft-delete filters, role and duty authorization, ErrorCodes/messages, repository call meaning, response ordering, API paths/JSON/status, DB/Flyway, and dependencies remain unchanged.

### 2026-07-10 - Issue #148 Billing Command Use Case Service Separation

- Context: After Issue #145 established the domain-first MVC package structure and Issue #147 separated Campus/Admin use cases, `BillingService` still owned payment account commands, charge creation, charge status changes, and legacy payment-account reads in one class. Issue #148 requires command responsibility separation without changing Billing policies, transaction results, API contracts, or persistence behavior.
- Decision: Split the eight Billing command use cases into `PaymentAccountCommandService`, `ChargeCreationService`, and `ChargeStatusCommandService`. Each moved public method directly owns the same write `@Transactional` boundary. Controllers call the payment-account and charge-status services directly, while Devotion and Poll Billing adapters call `ChargeCreationService` directly.
- Decision: Keep `BillingService` as a repository-free, transaction-free thin compatibility delegate that does not own business rules. The user approved mechanically moving `listPaymentAccounts`, both `listAdminPaymentAccounts` overloads, and `requireActivePenaltyAccount` into the existing `BillingQueryService` as the minimum compatibility wiring, preserving the same read-only transactions, validation order, repository calls, ErrorCodes, and messages without implementing Issue #149 query redesign.
- Impact: Campus lock, owner scope, active-account replacement, deactivate-then-flush-before-insert ordering, PENALTY unpaid-charge reconnection, account snapshots, soft delete, terminal-charge behavior, unique-conflict propagation, and Devotion/Poll outer transaction rollback remain unchanged. API paths, request/response JSON, HTTP/ErrorCode/message contracts, DB schema, Billing entities, Flyway migrations, authorization, pagination/query behavior, and dependencies do not change.

### 2026-07-10 - Issue #147 Campus And Admin Use Case Service Separation

- Context: After Issue #145 established the domain-first MVC package structure, `CampusService` still owned creation, invite-code join, query, update, member management, role management, and duty assignment, while `AdminManagementService` mixed service-admin user and campus management. `AdminDashboardService` also kept its query result contract nested in the implementation class.
- Decision: Split Campus application responsibilities into `CampusCreationService`, `CampusJoinService`, `CampusQueryService`, `CampusUpdateService`, `CampusMemberManagementService`, and `CampusDutyAssignmentService`. Split service-admin responsibilities into `AdminUserManagementService` and `AdminCampusManagementService`, and expose the dashboard aggregate read boundary as `AdminDashboardQueryService`. Keep `CampusService`, `AdminManagementService`, and `AdminDashboardService` only as repository-free compatibility facades for existing internal callers and test fixtures. Centralize the unchanged active-user/campus-manager lookup sequence in `CampusAccessPolicy`, retain `CampusRolePolicy` and `AdminAccessPolicy`, and keep dashboard result models under `admin/service/result`.
- Impact: Controllers call the use-case-specific services directly. Existing 12 Campus, 5 Admin management, and 1 dashboard transaction annotations move one-for-one to the new services with the same read-only/write mode and validation order. API paths, request/response JSON, HTTP/ErrorCode/message contracts, DB/Flyway, authorization rules, repository query semantics, side effects, and transaction results do not change.

### 2026-07-10 - Issue #145 Domain-First MVC Package Structure

- Context: Domain packages already provided the DDD top-level boundaries, but services, commands, queries, results, policies, and ports were mixed directly under `application`, while controllers and DTOs were grouped under `presentation`. The mixed layout made responsibility discovery inconsistent across domains.
- Decision: Keep `admin`, `batch`, `billing`, `campus`, `devotion`, `notification`, `poll`, `prayer`, and `user` as the top-level domain boundaries. Inside each domain, use only the needed `controller`, `service`, `domain`, and `infrastructure` responsibility packages. Split request/response DTOs under `controller/dto`, commands/queries/results/policies/ports under `service`, entities/types under `domain`, and repositories/external adapters under accurately named `infrastructure` packages. Keep `global` focused on shared configuration, security, exception, response, and controller concerns.
- Impact: Issue #145 changes only Java source/test paths, package declarations, imports, package-info declarations, and architecture documentation. API paths, request/response JSON, HTTP/error contracts, DB schema/Flyway migrations, business logic, authentication/authorization, schedules, transactions, and dependencies remain unchanged. A source-tree structure test enforces the new layout without adding ArchUnit or another library.

### 2026-07-09 - Issue #142 Poll Status Time Synchronization

- Context: Production poll list queries hid polls whose `starts_at <= now < ends_at` but `polls.status = SCHEDULED`, because visibility and response validation required `OPEN`.
- Decision: Keep PostgreSQL `timestamptz` storage as UTC and compare poll windows with `Instant`/UTC. When a poll is `SCHEDULED` and currently in its active period, synchronize it to `OPEN` at campus-scoped read/detail/result/comment-list and open-write validation boundaries before applying visibility or response rules. Do not automatically persist `CLOSED` during read synchronization, and do not call coffee settlement, notification, or other close side effects from this synchronization.
- Impact: User poll list/detail/result/comment reads and response/comment/user-option open checks can expose and use current scheduled rows after `OPEN` synchronization. Future scheduled polls remain hidden from member active lists. Ended polls keep existing user 3-day and admin 7-day visibility windows, and closed-poll response rejection remains unchanged.

### 2026-07-09 - Issue #139 Server Timezone Configuration Policy

- Context: Cloud Run health response timestamps and database dashboard views appeared as UTC (`Z`) rather than Korea time. Docker `TZ` settings are not sufficient for repository-based Cloud Run deployment, and timestamp persistence affects API/database contracts.
- Decision: Keep database persistence as `Instant` + PostgreSQL `TIMESTAMPTZ`/UTC. Configure the Spring application, JVM default timezone, Hibernate JDBC binding, and PostgreSQL session timezone with `Asia/Seoul` in application configuration. Do not change existing DB column types, existing data, or all API response timestamp contracts in Issue #139.
- Impact: `app.time-zone`, `spring.jackson.time-zone`, `spring.jpa.properties.hibernate.jdbc.time_zone`, and Hikari `connection-init-sql` now use `Asia/Seoul`. Supabase/PostgreSQL can still store instants in UTC internally, while server-side session/display calculations run with the Korea timezone. The JVM default timezone is also fixed on startup so `LocalDate` persistence does not shift by one day in UTC Cloud Run/CI environments.

### 2026-07-08 - Issue #136 Data Retention Cleanup Timestamp Basis

- Context: Issue #136 defines operational data retention cleanup for `prayer_submissions` and `charge_items`, but the issue text did not name the exact timestamp column for those two tables. Codex asked the user before implementation because retention deletion criteria affect production data safety.
- Decision: Use `created_at` as the timestamp basis for both `prayer_submissions` 1-year cleanup and annual `charge_items` cleanup. Annual `charge_items` cleanup deletes only terminal statuses `PAID`, `WAIVED`, and `CANCELED`; `UNPAID` is never deleted by the retention job.
- Impact: The #136 cleanup service uses `prayer_submissions.created_at < now(Asia/Seoul) - 1 year` for daily cleanup and `charge_items.created_at` within the previous calendar year for the February 1 annual cleanup. No DB schema change or user-facing API is added.

### 2026-07-06 - Issue #131 Account Deletion Soft Delete Policy

- Context: App Store Review rejected the iOS app under Guideline 5.1.1(v) because the app supports account creation but did not provide in-app account deletion. The user approved adding an in-app account deletion flow and keeping backend referential integrity through soft delete.
- Decision: Implement `DELETE /api/v1/users/me` for the authenticated user. The request requires the current password and the confirmation text `회원탈퇴`. Account deletion is soft delete plus privacy anonymization: set `users.is_active = false`, set `users.deleted_at = now()`, replace email with `deleted_user_{id}@deleted.faithlog.local`, replace name with `탈퇴한 사용자`, and replace the password hash with an unusable random hash. The existing email becomes available for re-signup. Deactivate the user's campus memberships by setting `campus_members.status = INACTIVE`, deactivate all active FCM tokens for the user, delete refresh token sessions, blacklist the current access token, and increase `users.token_version` so previously issued access tokens fail.
- Impact: Existing devotion, prayer, poll, comment, charge, and notification records are retained for FK and service-history integrity, but user-facing screens should display deleted users as `탈퇴한 사용자`. The API uses detailed errors `USER_DELETE_PASSWORD_MISMATCH`, `USER_DELETE_CONFIRM_TEXT_INVALID`, and `USER_ALREADY_DELETED`. Spring REST Docs must document the deletion API, and Flyway adds `users.deleted_at`.

### 2026-07-02 - Issue #122 Penalty Owner Metadata And My Accounts Settlement Policy

- Context: Issue #122 clarifies how `PENALTY` payment account `ownerUserId` should behave after the frontend moved settlement screens to `GET /api/v1/admin/campuses/{campusId}/charges/my-accounts`.
- Decision: `COFFEE` payment accounts remain strongly separated by owner. `PENALTY` payment accounts are campus-shared accounts, so `ownerUserId` is registration/management metadata and is not a mandatory settlement filter for campus managers or service-level `ADMIN`.
- Decision: Creating a `PENALTY` payment account stores the supplied `ownerUserId` when present. If `ownerUserId` is omitted, the requester user ID is stored. `COFFEE` creation keeps the existing requester-owned policy: omitted owner defaults to requester, and a different `ownerUserId` fails with `403 BILLING_PAYMENT_ACCOUNT_OWNER_FORBIDDEN`.
- Decision: `GET /api/v1/admin/campuses/{campusId}/charges/my-accounts` includes active `PENALTY` accounts for campus managers (`MINISTER`, `ELDER`, `CAMPUS_LEADER`) and service-level `ADMIN` regardless of owner match, including legacy active `PENALTY` accounts where `ownerUserId = null`. `COFFEE` remains limited to requester-owned active `COFFEE` accounts. Active `COFFEE` duty users remain limited to their own active `COFFEE` account scope. Normal `MEMBER`/`USER` access stays `403`, and missing or expired authentication stays `401`.
- Decision: Issue #122 does not add a COFFEE inactive account activate API, does not backfill existing production data, does not change API paths, and does not add Swagger documentation annotations.
- Impact: Billing creation/query services, Spring REST Docs snippets, `src/docs/asciidoc/index.adoc`, and regression tests must reflect the owner-metadata distinction between `PENALTY` and `COFFEE`.

### 2026-07-01 - Issue #116 Penalty Payment Account Activation And Soft Delete Policy

- Context: Issue #116 implements the front-end contract for showing the active `PENALTY` account at the top and inactive `PENALTY` accounts in a lower management list with activate/delete actions.
- Decision: A campus may have at most one active `PENALTY` account. Creating a new `PENALTY` account or activating an inactive `PENALTY` account deactivates the previous active `PENALTY` account for that campus. `PATCH /api/v1/admin/campuses/{campusId}/payment-accounts/{paymentAccountId}/activate` is `PENALTY`-only; if the target `PENALTY` account is already active, the API is idempotent and returns the current account as success.
- Decision: `COFFEE` account activation is not part of Issue #116. A `COFFEE` account sent to the activate API fails with `400 BILLING_PAYMENT_ACCOUNT_ACTIVATE_UNSUPPORTED`. Issue #114's `campusId + accountType + ownerUserId` active COFFEE account policy remains unchanged.
- Decision: Admin payment account list defaults to active accounts only. `GET /api/v1/admin/campuses/{campusId}/payment-accounts` accepts optional `accountType` and `includeInactive`; `includeInactive=true` returns active + inactive accounts. Soft deleted accounts are always hidden, and no `includeDeleted=true` API is added. Admin account responses include `id`, `campusId`, `accountType`, `nickname`, `bankName`, `accountNumber`, `accountHolder`, `ownerUserId`, `isActive`, `createdAt`, and `deactivatedAt`; `deletedAt` is not exposed.
- Decision: Payment account delete is a soft delete and only inactive accounts can be deleted. Deleting an active account fails with `409 BILLING_PAYMENT_ACCOUNT_ACTIVE_DELETE_FORBIDDEN`. Existing `charge_items.payment_account_id` and account snapshot fields remain unchanged when an inactive account is soft deleted.
- Impact: Issue #116 adds `payment_accounts.deleted_at` with Flyway V4, new activate/delete admin APIs, Spring REST Docs snippets and `index.adoc` sections, focused service/controller/docs tests, and keeps controller responses DTO-based without Swagger documentation annotations.

### 2026-07-01 - Issue #114 User-Owned Coffee Account And Coffee Poll Settlement Permission Policy

- Context: Issue #114 follows up Issue #112 after QA found that campus-level active COFFEE account replacement could deactivate another user's coffee account, relink existing unpaid COFFEE charges to the newest account, and block campus managers from creating paid coffee polls even when they have their own account.
- Decision: `PENALTY` payment accounts keep the existing campus-level active uniqueness policy. `COFFEE` payment accounts are user-owned and active uniqueness is scoped by `campusId + accountType + ownerUserId`. Creating a new COFFEE account deactivates only the requester's previous active COFFEE account; it must not deactivate another user's COFFEE account.
- Decision: COFFEE account creation is requester-owned. If a COFFEE account create request includes a different `ownerUserId`, the API must reject it with `403 BILLING_PAYMENT_ACCOUNT_OWNER_FORBIDDEN`. Campus managers and active COFFEE duty assignees may create their own COFFEE accounts. Normal members without active COFFEE duty may not create COFFEE accounts. PENALTY account management remains limited to campus managers or service-level `ADMIN`; a COFFEE duty user alone cannot create or deactivate PENALTY accounts.
- Decision: COFFEE poll and COFFEE poll template creation/update may be performed by campus managers or active COFFEE duty assignees, but the selected `paymentAccountId` is required and must be an active same-campus COFFEE account owned by the requester. A null account, inactive account, account from another campus, PENALTY account, or another user's COFFEE account must fail clearly with the billing account error contract.
- Decision: Closed COFFEE poll settlement must use the poll's selected `paymentAccountId` and save that account snapshot to generated COFFEE charges. Creating a newer COFFEE account must not relink existing unpaid COFFEE charges that came from an earlier poll/account. PENALTY account replacement keeps its existing unpaid charge relink behavior.
- Decision: Admin charge summary access remains available to campus managers (`MINISTER`, `ELDER`, `CAMPUS_LEADER`) and service-level `ADMIN`; unauthorized authenticated users receive `403`, while only authentication failure or expired tokens receive `401`. COFFEE `paymentAccountId` filtering for campus managers and COFFEE duty users is limited to their own COFFEE account; service-level `ADMIN` can access all.
- Decision: New campus creation must continue not to auto-create a default COFFEE poll template or recurring coffee poll. Existing historical default templates are not deleted or deactivated by this issue.
- Impact: Issue #114 adds a Flyway V3 partial unique-index migration for active account scope, updates Spring REST Docs and `index.adoc`, and supersedes the Issue #112 decision that COFFEE poll/template creation required the current active COFFEE duty assignee.

### 2026-07-01 - Issue #112 Billing Account Scope And Coffee Poll Permission Policy

- Context: Issue #112 fixes admin billing summaries so they can be scoped by payment account, and tightens COFFEE poll/template creation so campus manager roles alone cannot create paid coffee flows.
- Decision: `GET /api/v1/admin/campuses/{campusId}/charges` adds optional query parameter `paymentAccountId`. When present, the summary and `members[]` aggregation must include only charge items linked to that account, and it must compose with existing `paymentCategory`, `status`, `userId`, `keyword`, `page`, `size`, and `sort` filters.
- Decision: Add `GET /api/v1/admin/campuses/{campusId}/charges/my-accounts` to aggregate only active payment accounts owned by the current user. Add `GET /api/v1/admin/campuses/{campusId}/payment-accounts` for manager-facing account metadata including `ownerUserId`, `isActive`, `createdAt`, and `deactivatedAt`.
- Decision: `PENALTY` account and settlement views remain limited to service-level `ADMIN` or campus managers (`MINISTER`, `ELDER`, `CAMPUS_LEADER`). Active `COFFEE` duty users can use only COFFEE-scoped account and charge views, and cannot query PENALTY data through admin billing APIs.
- Decision: Creating `pollType=COFFEE`, `paymentCategory=COFFEE`, or `chargeGenerationType=OPTION_PRICE` with `paymentCategory=COFFEE` is allowed only to the current active `DutyType.COFFEE` assignee. A campus manager or service-level `ADMIN` who is not the active COFFEE duty assignee must receive `403` for COFFEE poll/template creation or update. A selected `paymentAccountId` must be an active same-campus COFFEE account usable by the requester.
- Decision: New campus creation must not automatically create a default COFFEE poll template or recurring coffee poll. Existing auto-created COFFEE templates in existing campuses are not deleted or deactivated in this issue; cleanup, if needed, belongs to a separate issue.
- Impact: Issue #112 must not change DB schema. It uses existing `payment_accounts.owner_user_id`, `charge_items.payment_account_id`, and `campus_duty_assignments`. Spring REST Docs must cover the new query parameter and new admin billing/account APIs. Swagger documentation annotations remain prohibited.

### 2026-06-30 - Issue #109 Zero Penalty Devotion Submission Charge Policy

- Context: Issue #109 fixes weekly devotion final submission creating or exposing `UNPAID` `PENALTY` charge rows even when the calculated penalty total is 0 KRW.
- Decision: When a weekly devotion request is submitted with `submit = true` and the calculated penalty `totalAmount` is 0, the backend must not create a `charge_items` row for `paymentCategory = PENALTY`. A 0 KRW `UNPAID` penalty charge must not appear in member or admin charge lists.
- Decision: Active `PENALTY` payment account lookup is required only when the calculated penalty amount is greater than 0. If the calculated amount is 0, weekly submission succeeds even when the campus has no active `PENALTY` payment account.
- Impact: Issue #109 must keep the existing weekly devotion response shape, DB schema, `submit = false` draft behavior, duplicate final submission blocking, submitted-week daily edit blocking, and positive penalty charge creation behavior. Tests must cover zero penalty with and without an active `PENALTY` account plus positive penalty charge regression.

### 2026-06-30 - Issue #106 Prayer Season And Group Management API Contract

- Context: Issue #106 extends the prayer request MVP with admin-facing current season/group management reads, assignable member lookup, duplicate active-group assignment validation, weekly board response fields, and a current-user prayer submission API.
- Decision: The backend current prayer season lookup uses `status = ACTIVE` and `endDate = null` together. A closed season is represented by `status = CLOSED` with non-null `endDate`, is excluded from current-season lookup, and is excluded from weekly board groups. When no current season exists, weekly board lookup succeeds with `currentSeason = null`, `myGroupId = null`, `submittedCount = 0`, `targetMemberCount = 0`, and `groups = []`.
- Decision: Within the same season, one `userId` may belong to only one active prayer group. Replacing a group's members may keep or reactivate members already in that same group, and the same user may be assigned again in a different season. Assigning a user already active in another group of the same season fails with `409 PRAYER_GROUP_MEMBER_ALREADY_ASSIGNED`.
- Decision: `GET /api/v1/campuses/{campusId}/prayers/weeks/{weekStartDate}` now returns `currentSeason`, `myGroupId`, group `seasonId`, member `submitted`, member `editable`, and existing `version`. Campus managers and service ADMIN can edit all board items; normal ACTIVE members see only their own current-group item as editable.
- Decision: `PUT /api/v1/campuses/{campusId}/prayers/weeks/{weekStartDate}/me` saves only the authenticated user's own prayer submission in the current active season and returns the enhanced weekly board response. The request body is `{ "content": "..." }`; `content` keeps the existing nullable `prayer_submissions.content` policy.
- Impact: Issue #106 must add Spring REST Docs snippets and `src/docs/asciidoc/index.adoc` sections for current season, season groups, assignable members, duplicate assignment conflict, weekly board enhancements, and `/me` save. Swagger documentation annotations remain prohibited.

### 2026-06-30 - Issue #104 User-Added Poll Option Menu Contract

- Context: Issue #104 fixes the coffee QA follow-up where user-added COFFEE poll options were being saved from free-text content with `composeMenuCode = null` and `priceAmount = 0`, which made closed coffee poll settlement unable to charge the real menu price.
- Decision: `POST /api/v1/campuses/{campusId}/polls/{pollId}/options` keeps the existing `{ "content": "..." }` contract for CUSTOM and other non-COFFEE polls. If `menuId` is present for a non-COFFEE poll, including when `content` is also present, the API must return `400`.
- Decision: For COFFEE polls, `menuId` is the only allowed user-added option contract. The server looks up the active coffee menu catalog row and stores snapshots in `poll_options`: `content = menu.name`, `composeMenuCode = menu.menuCode`, and `priceAmount = menu.priceAmount`. A COFFEE poll request that sends only `content` must return `400`.
- Impact: Issue #104 must add failing tests before implementation, add or reuse domain-prefixed poll error codes for invalid option-add contracts, update Spring REST Docs for the expanded request body, and preserve the existing `optionIds` poll response contract. Swagger documentation annotations remain prohibited.

### 2026-06-29 - Issue #100 Coffee Duty Access And My Duty Status Contract

- Context: Frontend login and `GET /api/v1/users/me` responses were returning empty `campusMemberships` even when users had ACTIVE campus memberships, and normal `USER` accounts assigned as active `DutyType.COFFEE` could not create coffee polls or manage coffee payment accounts because existing policies required campus administrator roles.
- Decision: Login response and `GET /api/v1/users/me` must include the user's actual ACTIVE campus memberships using the same membership fields used by `GET /api/v1/campuses/me`: `membershipId`, `campusId`, `campusName`, `region`, `campusRole`, and `status`.
- Decision: Add `GET /api/v1/campuses/{campusId}/duty-assignments/me` for ACTIVE campus members to check their own COFFEE duty status. The response returns `userId`, `campusId`, `dutyType=COFFEE`, and `isActive`. A non-duty ACTIVE member receives `200 OK` with `isActive=false`; non-members or inactive members are forbidden.
- Decision: Active COFFEE duty assignees may use only COFFEE-scoped functions without becoming campus managers: create/deactivate `accountType=COFFEE` payment accounts in their own campus, create/manage `pollType=COFFEE` polls, and query admin charge views only when `paymentCategory=COFFEE`. `PENALTY` accounts/charges, campus member management, devotion admin APIs, service admin APIs, and coffee-external poll types keep the existing campus administrator or service administrator permissions.
- Decision: Coffee polls should be user-option-add enabled by default. Default COFFEE poll templates are provisioned with `allowUserOptionAdd=true`, and direct `pollType=COFFEE` creation defaults omitted/null `allowUserOptionAdd` to `true` regardless of whether the requester is the active COFFEE duty assignee. Explicit `allowUserOptionAdd=false` remains an override. Other direct poll types still default omitted `allowUserOptionAdd` to `false`.
- Pending follow-up: The existing #97 user option API `POST /api/v1/campuses/{campusId}/polls/{pollId}/options` accepts only `{ "content": "새 항목" }`, so user-added options are saved with `composeMenuCode=null` and `priceAmount=0`. Because coffee settlement uses option price snapshots, coffee poll user-added options may need a separate menu-catalog-based API decision before they are safe for real paid coffee ordering.
- Impact: #100 tests and REST Docs must cover membership responses, the new my-duty endpoint, COFFEE-only account/poll/charge access for duty assignees, and denial of coffee-external manager functions. Swagger documentation annotations remain prohibited.

### 2026-06-29 - Issue #97 Flyway V2 Migration Split

- Context: PR #98 originally placed #97 schema changes into `V1__initial_schema.sql`, but the project now has Supabase/Cloud Run deployment databases and an operational Flyway baseline where V1 may already have been applied.
- Decision: Do not modify already-applicable `V1__initial_schema.sql` for #97 feature schema changes. Keep V1 at its pre-#97 shape and add #97 poll user-option columns and foreign key through a new Flyway migration version, `V2__add_poll_user_option_fields.sql`.
- Impact: #97 schema changes are deployable to existing databases through Flyway V2 while preserving clean database setup through V1 followed by V2. Existing rows are handled safely by `BOOLEAN NOT NULL DEFAULT FALSE` for the new boolean columns.

### 2026-06-29 - Issue #97 Poll Close And User Option Add Contract

- Context: Issue #97 adds manual poll closing and user-added poll options. The work changes API behavior and schema, so the PM session recorded explicit user-approved decisions before implementation.
- Decision: Add an admin-only close endpoint `PATCH /api/v1/admin/campuses/{campusId}/polls/{pollId}/close` that only changes an OPEN poll to CLOSED. It must not run coffee settlement, send notifications, or create charges. Closing a SCHEDULED or already CLOSED poll returns `409 POLL_CLOSE_NOT_ALLOWED` with `종료할 수 없는 투표 상태입니다.`, and the close operation pulls `endsAt` forward to the current time so visibility/result/response policies remain based on the actual close time.
- Decision: Store the user-option-add setting on both `poll_templates.allow_user_option_add` and `polls.allow_user_option_add`. Template-based poll creation copies the template value. Direct poll creation accepts optional `allowUserOptionAdd`; null or omission means false.
- Decision: Add a separate user option API `POST /api/v1/campuses/{campusId}/polls/{pollId}/options` with request `{ "content": "새 항목" }`. Only ACTIVE campus members may use it, only while the poll is OPEN and in its active time window, and only when `polls.allow_user_option_add = true`. The server trims content and rejects case-insensitive duplicate option names with `400 POLL_OPTION_DUPLICATE_CONTENT`. Disabled option add returns `403 POLL_USER_OPTION_ADD_DISABLED`.
- Impact: #97 adds `poll_options.user_added` and `poll_options.created_by_user_id` so user-added options are traceable while preserving the existing poll response `optionIds` contract and `poll_response_options` structure. Detailed API contracts are covered by Spring REST Docs snippets without Swagger documentation annotations.

### 2026-06-24 - Issue #94 Cloud Run Performance Test Scope

- Context: Issue #94 measures FaithLog backend performance against the live Cloud Run service URL `https://faithlog-549871256004.asia-northeast3.run.app`. Because the target is a production server and may use production data/services, load scope must be explicit before measurement or tuning.
- Decision: Approved Cloud Run performance testing is read-centered only. The allowed progression is smoke `VUS=1`, `DURATION=30s`; baseline `VUS=10`, `DURATION=3m`; and expanded baseline up to `VUS=30`, `DURATION=5m`. VUS above 30, duration above 10 minutes, write-heavy load, and bulk payment/charge/notification side effects require separate PM approval.
- Decision: Remote k6 runs must require an explicit remote-load flag such as `ALLOW_REMOTE_LOAD=true`, and Cloud Run result files must be kept separate from local Docker result files. Authenticated read tests require a dedicated perf account and should not reuse arbitrary production users.
- Decision: Do not change API contracts, authentication security cost/policy, Cloud Run CPU/RAM/concurrency/min instances, DB schema, Flyway migrations, or indexes during #94 without evidence and PM approval. If Cloud Run resource sizing appears to be the bottleneck, report the evidence and recommended values instead of changing the service.
- Impact: #94 may collect Cloud Run health/read baseline evidence, compare it with #90 local Docker numbers, improve k6 measurement tooling, document measured results, and implement API-contract-preserving code/query optimizations only after bottleneck evidence exists.

### 2026-06-24 - Issue #94 Production PERF Dataset Scope

- Context: The initial Cloud Run baseline found an empty production dataset, so campus-dependent read APIs such as admin dashboard, devotions, billing, polls, and prayers could not be measured meaningfully.
- Decision: PM approved creating a small production test dataset for Cloud Run performance measurement, limited to records with a `PERF_` prefix or another clear test identifier. The initial dataset scope is one PERF campus, 30 to 50 or fewer test members, devotion weekly/daily samples, `PENALTY`/`COFFEE` charge samples, OPEN/CLOSED poll and response samples, and prayer season/group/submission samples.
- Decision: Existing production data must not be modified or deleted. Data should be created through actual APIs first. Direct database or Supabase manipulation requires a separate PM question. Bulk write-heavy load, notification blasts, and bulk charge/payment state changes remain prohibited.
- Decision: Test credentials, passwords, tokens, Firebase JSON, DB/Redis passwords, and other secrets must not be committed or documented. The created dataset identifiers, counts, and cleanup approach should be recorded in repository docs or a separate traceable report.
- Impact: #94 may create a small `PERF_` dataset with API calls, measure read-centered VUS 10/3m and VUS 30/5m Cloud Run baselines against it, and record the resulting bottleneck evidence before any production tuning recommendation.

### 2026-06-24 - Issue #94 Cloud Run Auth Pattern Split

- Context: The first PERF dataset Cloud Run read baseline included `auth_login` on every k6 iteration. That is useful for login/BCrypt/JWT issuance pressure, but it can overstate latency versus the mobile app's normal pattern of logging in once and reusing an Access Token for multiple read requests.
- Decision: Preserve `PERF_20260624_CLOUDRUN_A` for now instead of deleting it. Treat current Cloud Run resource settings as the `min instances=0 baseline`: CPU 1, memory 1GiB, concurrency 80, min instances 0, max instances 3.
- Decision: Split #94 measurements into two explicit patterns. `auth-heavy` includes login on every iteration to measure login/BCrypt/JWT pressure. `steady-state` logs in once during setup and reuses the Access Token for read API iterations to approximate normal app usage within the 3 to 5 minute token-validity window.
- Decision: Do not change BCrypt cost/security policy, API contracts, Cloud Run settings, DB schema, Flyway migrations, or indexes without further PM approval. The first infrastructure recommendation candidate is to apply min instances 1 and rerun the same conditions, because min instances 0 can mix cold start or instance scale-up outliers into p95/p99/max.
- Impact: #94 k6 tooling must make the auth pattern explicit, and resume metrics must report auth-heavy and steady-state read results separately under the Cloud Run min instances 0 baseline.

### 2026-06-23 - Issue #90 Local Docker Performance Measurement Scope

- Context: Issue #90 measures backend coverage and API performance for resume/portfolio metrics. The first Docker k6 baseline identified `auth_login` and `campuses_me` as bottleneck candidates, but authentication performance touches password hash/security policy.
- Decision: Use local Docker as the only #90 performance basis: `VUS=30`, `DURATION=5m`, failure rate `< 1%`, and p95 response time as the primary improvement metric. p50, p99, avg, RPS, and dataset size are supporting metrics. Resume/portfolio statements must explicitly say the numbers are measured on the local Docker dataset and must not imply production or Cloud Run performance.
- Decision: Optimize `campuses_me` first. Do not optimize `auth_login` in #90 because password hash/security cost and authentication policy require separate security review. Keep `auth_login` as a measured bottleneck candidate for follow-up.
- Decision: Do not change API contracts for #90 performance work: no path, request, response, or ErrorCode changes. DTO/projection/bulk query/repository/service internal optimizations are allowed.
- Decision: Do not change DB schema or indexes yet. First collect Hibernate SQL/query count/EXPLAIN-style evidence. If index evidence is clear, ask PM before Flyway migration, DDL, or Entity schema changes.
- Decision: Do not expand #90 to write API load tests. Write scenarios require fixture, idempotency, and side-effect management and should be recorded as a follow-up candidate. Do not run high-load tests against Cloud Run; before further approval, Cloud Run is smoke-only.
- Impact: #90 may add JaCoCo, k6 scripts, measurement docs, read API performance evidence, and code-only/query-only `campuses_me` optimization. It must not add schema migrations, production load tests, write-load scenarios, or authentication security tuning.

### 2026-06-22 - Issue #46 Cloud Run Supabase Flyway Baseline

- Context: Issue #46 reintroduces Flyway after the main MVP feature model stabilized and updates deployment from a direct server model to Google Cloud Run. The user confirmed a new Supabase database, approved the recommended Supabase/config/documentation approach, and later approved the recommended FK and Cloud Run scope.
- Decision: Use a new Supabase PostgreSQL database with a V1 initial Flyway schema. Apply Notion ERD `Ref` relationships as database foreign keys in V1, without adding delete-cascade behavior. Exclude only polymorphic source references such as `charge_items.source_id`, which can point to different source tables depending on `source_type`. Include code-approved columns that are newer than the Notion ERD, such as `users.token_version`, `poll_templates.is_default`, and `coffee_menu_catalog.category`.
- Decision: Deploy target is Google Cloud Run container runtime. Do not implement direct-server proxy, direct 80/443 port, or certificate renewal tooling in #46. HTTPS and custom domain behavior should use Google Cloud managed flows. This issue prepares Docker image build/push/deploy documentation and environment-variable contracts only; real GCP project, region, service name, Artifact Registry repository, and secret registration require later PM confirmation.
- Decision: Keep actual Supabase, JWT, Firebase, and database secret values out of repository files, logs, docs, issues, commits, and PR text. Document placeholders and environment-variable names only. Use direct Supabase connection for migration/schema inspection where available, and document pooler-based application traffic guidance for Cloud Run with conservative Hikari pool sizing.
- Impact: #46 may add Flyway dependencies, `src/main/resources/db/migration/V1__initial_schema.sql`, Cloud Run/Supabase deployment docs, prod/example environment contracts, and PostgreSQL migration verification. Existing-data Supabase migration or baseline requires a separate PM-approved plan.

### 2026-06-22 - Issue #46 Upstash Redis And Environment Split

- Context: After the initial Flyway/Supabase/Cloud Run work, the user confirmed that production Redis should use Upstash Redis while local development and Docker QA continue using Docker/local Redis. The user also asked whether Dockerfiles should be split.
- Decision: Keep one shared `Dockerfile` for the application image. Split behavior by Spring profile and environment variables instead: `local`, `docker`, `test`, and `prod`.
- Decision: Use `SPRING_DATA_REDIS_HOST`, `SPRING_DATA_REDIS_PORT`, `SPRING_DATA_REDIS_PASSWORD`, and `SPRING_DATA_REDIS_SSL_ENABLED` for Upstash Redis in `prod`. Do not use Upstash defaults in local/docker/test.
- Decision: Add profile-specific env examples: `.env.local.example`, `.env.docker.example`, and `.env.prod.example`. These files contain only dummy or placeholder values and must not be copied with real secrets into the repository.
- Impact: Docker Compose defaults to `SPRING_PROFILES_ACTIVE=docker` and uses Docker PostgreSQL/Redis. Cloud Run uses `prod` with Supabase PostgreSQL and Upstash Redis injected through safe environment/secret mechanisms.

### 2026-06-22 - Issue #84 QA Docker Compose Project Isolation

- Context: Full QA and Docker QA can be polluted by named volumes left from previous development or PM worktree runs. Earlier Docker troubleshooting showed that an existing Postgres volume credential mismatch could break app startup, but deleting volumes by default is risky because it can erase local development data.
- Decision: QA must use a dedicated Docker Compose project name such as `faithlog-qa-<suffix>` instead of treating `docker compose down -v` as the default reset mechanism. The suffix may be a date, issue number, random string, or another traceable low-collision value. QA shutdown should run only `docker compose -p <projectName> down` for the same project. Volume deletion is not part of the default QA path and requires explicit user approval as a separate cleanup procedure.
- Impact: Issue #84 adds `scripts/qa_docker_compose_isolated.sh` and documentation for QA project-name isolation. Existing development/PM worktree Docker volumes must not be deleted or reset by QA automation. Because the current compose file has fixed `container_name` values, the script detects existing FaithLog containers and stops instead of touching a running or stopped stack from another project.

### 2026-06-22 - Issue #76 tokenVersion-Based Role Token Invalidation

- Context: Issue #76 was reopened for implementation after the MVP policy documentation PR merged. Because this changes authentication/security behavior, the PM session explicitly approved the final implementation mechanism before code changes.
- Decision: Implement role-change Access Token invalidation with `users.token_version`. Access Tokens include a `tokenVersion` claim. The authentication filter compares the token `userId/tokenVersion` with the current persisted user `tokenVersion`; mismatch or missing version is handled through the existing authentication failure policy (`AUTH_UNAUTHORIZED`) without adding a new ErrorCode. Service-level role changes increment the target user's token version. Campus role changes also increment the target user's token version because campus roles directly affect API authorization. Refresh Token reissue keeps the existing Redis rotation/allowlist policy and issues a new Access Token from the latest persisted role and token version.
- Impact: Existing Access Tokens issued before a real service-level or campus role change become unusable immediately after the version increment. Access Token TTL remains 30 minutes, Refresh Token TTL/rotation/logout blacklist behavior remains unchanged, user-facing API paths do not change, and Swagger documentation annotations must not be added.

### 2026-06-22 - Issue #76 Role Change Access Token Invalidation Policy

- Context: Full QA raised a security policy question because Access Tokens include the `role` claim. If a service-level role or campus permission changes, an already issued Access Token can still carry the previous claim until it expires.
- Decision: For the MVP, do not immediately invalidate already issued Access Tokens when roles change. The current Access Token TTL is 30 minutes, so the accepted risk window is limited. Refresh Token reissue must still create new Access Tokens from the latest persisted user role. Immediate invalidation of role-stale Access Tokens is tracked as Issue #76, `[Security] 역할 변경 시 기존 Access Token 무효화 정책 구현`, because tokenVersion, session invalidation, or Redis blacklist/session expansion affects authentication architecture and should be designed separately.
- Impact: Current role-management features must not silently add partial token invalidation behavior. Future #76 work must decide and test the final invalidation mechanism for service-level role changes and campus role changes without breaking #28 refresh/logout Redis rotation.
- Status: Superseded by the later 2026-06-22 decision `Issue #76 tokenVersion-Based Role Token Invalidation`, which implements immediate invalidation through `users.token_version`.

### 2026-06-22 - Issue #74 Policy Documentation Consistency Cleanup

- Context: Full QA found stale policy wording across repository docs and Notion/API design pages. The stale text included Last `ADMIN` protection as a pending question, Compose Coffee seed source wording that did not mention the user-approved #37 override, older poll endpoint lists, notification no-retry wording, and obsolete campus member/invite-code API entries.
- Decision: Treat Issue #61 and its implementation as the final Last active service-level `ADMIN` policy: demoting the only active service-level `ADMIN` to `USER` or `MANAGER` fails with `409 ADMIN_LAST_ADMIN_DEMOTION_FORBIDDEN`, counting only `users.role = ADMIN` and `users.is_active = true`. Treat Compose Coffee seed sourcing as official-first, with an explicit user-approved latest source allowed when official verification is blocked or impossible. The actual #37 implementation uses the user-approved 2026 Compose Coffee menu/price source. Treat the current REST Docs and latest decision log as the source of truth for poll, notification, campus member, and invite-code API wording; older Notion/API sections must be updated or marked Legacy.
- Impact: Documentation cleanup must not change source code, DB schema, API paths, features, or Swagger annotations. Future work should not reintroduce response-time `COFFEE` charge creation, old single `optionId` request fields, admin-only poll result endpoints, `/polls/active`, campus member status patch, invite-code refresh, notification no-retry wording for #40+, or Last `ADMIN` pending-question text.

### 2026-06-22 - Issue #72 Direct Poll Creation Current Window Status

- Context: Full QA found that administrator direct poll creation left polls in `SCHEDULED` even when the creation-time current instant was inside `startsAt <= now < endsAt`, causing detail/response/results/comment APIs to behave as if the poll was not open.
- Decision: When an administrator creates a poll directly and the creation-time current instant satisfies `startsAt <= now < endsAt`, the poll must be `OPEN` immediately. `SCHEDULED` remains the status for polls whose start time is still in the future. Scheduler/Batch continues to provide automatic correction/transition for scheduled flows and does not replace this direct-create rule.
- Impact: Direct custom polls and template-based coffee polls created for the current time window must be usable immediately for detail, response, results, and OPEN-only comment writes. Coffee poll response still only saves `poll_responses`/`poll_response_options`; `COFFEE` charge rows are generated by closed coffee poll settlement after the poll is closed.

### 2026-06-21 - Issue #23 Campus Admin Dashboard Summary Contract

- Context: Issue #23 was verified against the latest Notion API design and GitHub issue text. The scope is a campus-internal admin dashboard summary API, not the service-level `ADMIN` user/campus management API implemented by Issue #61.
- Decision: Issue #23 uses `GET /api/v1/admin/campuses/{campusId}/dashboard/summary`. The primary users are `ACTIVE` campus members with `campus_members.campus_role` of `MINISTER`, `ELDER`, or `CAMPUS_LEADER`. Service-level `ADMIN` may access every campus dashboard. Plain `MEMBER`, managers of other campuses, and users with only the global `MANAGER` role may not access the dashboard.
- Decision: `weekStartDate` is an optional query parameter. If omitted, the server calculates the current week's Monday using `Asia/Seoul`. If provided, `weekStartDate` must be a Monday; invalid dates or non-Monday dates return 400. The member summary includes `activeCount`, `inactiveCount`, and `adminCount`. The devotion summary uses `weekly_devotion_records.submitted_at` for the selected week and includes submitted count, missing count, and submit rate. The billing summary includes total unpaid amount, unpaid member count, and category-level unpaid amounts for `PENALTY` and `COFFEE`. The poll summary includes open poll count, recently closed poll count for the last 7 days, and missing-response count only; detailed missing-member lists remain in the Issue #38 API.
- Impact: Issue #23 implementation must not reimplement #61 service-admin management, #31 devotion missing detail, #36 billing detail, #38 poll result/missing-member detail, or #40 notification log APIs. REST Docs tests must lock the summary response fields, permission matrix, optional `weekStartDate` default behavior, non-Monday validation, category-level billing summary, and 7-day recently closed poll rule.

### 2026-06-20 - Issue #41 Notification Redis Dedup And Lock Policy

- Context: Issue #41 adds Redis-based duplicate prevention and execution locks on top of the Issue #40 notification flow without adding new notification APIs or moving source-of-truth data into Redis.
- Decision: FCM token source of truth remains `user_fcm_tokens`, and notification history source of truth remains `notification_logs`. Redis is used only for notification deduplication and execution locking. Automatic notifications use the business dedup key `notificationType + campusId + scopeId + targetUserId + businessDate`, with 25-hour TTL for daily jobs and 8-day TTL for weekly jobs. Notification execution locks use `notification:lock:{jobName}:{campusId}:{scopeId}`, default to 10 minutes, and allow custom TTLs for longer batch jobs.
- Decision: Redis failures are fail-closed for automatic/scheduled notifications, so the automatic send/enqueue is skipped. Manual admin notification requests do not use the automatic business dedup key, but they do require Redis lock availability; if Redis is unavailable, the admin API fails clearly instead of sending without duplicate protection.
- Impact: Issue #41 implementation must expose reusable application ports/services for #24 Scheduler/Batch, keep Redis adapters under `notification/infrastructure/redis`, keep application services independent of `RedisTemplate`, preserve the #40 async `notification_logs` PENDING/SENT/FAILED/SKIPPED policy, and continue using the existing admin notification API paths.

### 2026-06-20 - Issue #40 Notification Async Retry Policy

- Context: Local backend policy still contained the older "no notification retry" MVP wording, while the latest current conversation, GitHub Issue #40, and Notion 2026-06-20 notification pages define asynchronous notification delivery with token-level retry.
- Decision: Issue #40 creates per-target `notification_logs` rows with one server-generated `request_id` and `PENDING` status before background FCM delivery. The admin send API returns `202 Accepted` with `notificationRequestId`, `queuedCount`, and `skippedCount`. Transient failures retry per token up to 3 times with `1s -> 5s -> 30s` intervals. Permanent failures such as `UNREGISTERED`, token-not-registered, and payload-valid invalid-token responses do not retry and deactivate the affected FCM token. Old `PENDING` log reprocessing after server restart remains out of #40 scope.
- Impact: `docs/backend-implementation-policy.md`, implementation, tests, and REST Docs must follow the retry policy. Older local "notification sends do not retry automatically in MVP" wording is superseded for #40.

### 2026-06-20 - Issue #39 Coffee Poll Charge Settlement Timing

- Context: Issue #39 originally had older local wording that could imply `COFFEE` charges are created during poll response writes, while the latest user decision and Notion/API pages moved charge generation to closed-poll settlement.
- Decision: `PUT /api/v1/campuses/{campusId}/polls/{pollId}/responses/me` only saves the current response and `poll_response_options`. `COFFEE` charge rows are generated or updated by a closed coffee poll settlement application service after the poll is `CLOSED`, using final responses, `poll_options.price_amount`/`content` snapshots, `sourceType = POLL_RESPONSE`, `sourceId = poll_responses.id`, `paymentCategory = COFFEE`, and `dueDate = null`.
- Decision: Settlement target polls are `poll_type = COFFEE`, `charge_generation_type = OPTION_PRICE`, `payment_category = COFFEE`, and `status = CLOSED`. Settlement is idempotent, keeps terminal `PAID`/`WAIVED`/`CANCELED` `COFFEE` charges unchanged, requires an active `DutyType.COFFEE` assignee and a valid active same-campus `COFFEE` account from `polls.payment_account_id`, and runs all-or-nothing in one transaction.
- Impact: Issue #39 implementation must use a Poll application port/adapter to Billing so Poll domain code does not directly manipulate Billing entities. Scheduler/Batch invocation remains Issue #24 scope, and no separate user-facing coffee charge creation API is added.

### 2026-06-20 - Issue #38 Poll PM Review Contract Clarifications

- Context: PM review found that empty `optionIds` could be intercepted by controller validation, SCHEDULED/future polls could be writable and visible, and response option replacement could hit the `(response_id, option_id)` unique constraint when the same options were saved again.
- Decision: Empty or missing `optionIds` must be validated in the poll service and return `POLL_RESPONSE_INVALID_SELECTION_COUNT`. Poll response/comment writes are allowed only when `polls.status = OPEN` and `starts_at <= now <= ends_at`; SCHEDULED/future polls are hidden from member list/detail and direct lookup returns `POLL_NOT_FOUND`. Not-open write attempts continue to use the existing `POLL_CLOSED` / 409 / `마감된 투표에는 응답하거나 댓글을 작성할 수 없습니다.` contract instead of introducing a new ErrorCode in this fix.
- Impact: Issue #38 service and REST Docs tests must cover empty selection errors, SCHEDULED/future visibility blocking, SCHEDULED write blocking, and same-option response resave without duplicate rows or unique constraint failures.

### 2026-06-20 - Issue #38 Poll ErrorCode Contract

- Context: Issue #38 needed detailed poll error codes for response validation, campus scope mismatch, visibility window expiration, poll comment authorization, and admin missing-member authorization. The implementation policy requires domain-prefixed detailed codes instead of broad `INVALID_REQUEST`, `NOT_FOUND`, or `FORBIDDEN` codes.
- Decision: Use `POLL_NOT_FOUND` / 404 / `투표를 찾을 수 없습니다.` for campus mismatch, visibility window expiration, and direct lookup data non-exposure. Use `POLL_OPTION_NOT_FOUND` / 404 / `투표 선택지를 찾을 수 없습니다.` for nonexistent options or options belonging to another poll. Use `POLL_RESPONSE_INVALID_SELECTION_COUNT` / 400 / `투표 선택 개수가 올바르지 않습니다.` for empty `optionIds`, `SINGLE` requests not containing exactly one option, and `MULTIPLE` requests containing fewer than one option. Use `POLL_RESPONSE_DUPLICATE_OPTION` / 400 / `중복된 투표 선택지가 포함되어 있습니다.` for duplicate option IDs. Use `POLL_CLOSED` / 409 / `마감된 투표에는 응답하거나 댓글을 작성할 수 없습니다.` for closed poll response/comment write attempts. Use `POLL_ACCESS_FORBIDDEN` / 403 / `투표 접근 권한이 없습니다.` for non-ACTIVE campus member access. Use `POLL_ADMIN_FORBIDDEN` / 403 / `투표 관리 권한이 없습니다.` for missing-member admin authorization failures. Use `POLL_COMMENT_NOT_FOUND` / 404 / `투표 댓글을 찾을 수 없습니다.` and `POLL_COMMENT_FORBIDDEN` / 403 / `투표 댓글 수정/삭제 권한이 없습니다.` for comment lookup and edit/delete authorization.
- Impact: Issue #38 implementation and REST Docs must use these detailed codes/status/messages for poll exceptions. New poll exceptions must not be collapsed into broad global error codes.

### 2026-06-19 - Issue #37 Compose Coffee Seed Source Override

- Context: Issue #37 originally required seeding Compose Coffee menu prices only from official Compose Coffee menu boards, official app data, or official menu images. During development, official web access was blocked and the user provided a 2026 Compose Coffee menu/price text list from a blog source after being told the official-source requirement was blocking implementation.
- Decision: For Issue #37, use the user-provided 2026 Compose Coffee menu/price list as the approved seed source. Record that this is a user-approved override of the earlier official-source-only seed constraint for this implementation session.
- Impact: `coffee_menu_catalog` seed data for Issue #37 should reflect the user-provided menu names and prices. The implementation record must mention that the seed source was user-provided and not independently verified from an official Compose Coffee endpoint.

### 2026-06-19 - Issue #61 Service Admin User And Campus Management API Contract

- Context: Issue #61 implements service-level `ADMIN` user and campus management APIs. The issue had approved behavior but left several REST Docs contract details open, including response field names, allowed sort fields, and the last-admin protection query basis.
- Decision: `GET /api/v1/admin/users` uses query parameters `name`, `email`, `userId`, `role`, `page`, `size`, and `sort`. User list items return `userId`, `name`, `email`, `role`, `campusCount`, and `campuses[]`; user detail returns `userId`, `name`, `email`, `role`, `isActive`, and `campuses[]`. User campus items use `membershipId`, `campusId`, `campusName`, `region`, `campusRole`, and `status`. `GET /api/v1/admin/campuses` uses query parameters `name`, `region`, `status`, `page`, `size`, and `sort`, and returns `campusId`, `name`, `region`, `isActive`, `status`, `memberCount`, and `adminCount`. Allowed user sort fields are `id`, `name`, `email`, `role`, and `createdAt`. Allowed campus sort fields are `id`, `name`, `region`, and `createdAt`. The last service-level `ADMIN` protection counts only users where `users.role = ADMIN` and `users.is_active = true`.
- Impact: Issue #61 REST Docs and tests must lock these names and sort contracts. Aggregate fields such as `memberCount` and `adminCount` are not sortable in this MVP. Last-admin demotion must fail when the target is the only active service-level `ADMIN`.

### 2026-06-19 - Issue #61 Service Admin Direct Member Add Duplicate Policy

- Context: Issue #61 allows service-level `ADMIN` to add users to campuses directly without invite codes and reactivates an existing `INACTIVE` membership as `ACTIVE + MEMBER`. The remaining open behavior was how to handle a direct add request when the same user already has an `ACTIVE` membership in that campus.
- Decision: If service-level `ADMIN` directly adds a user who already has an `ACTIVE` membership in the target campus, the API fails with `CAMPUS_ALREADY_JOINED`, HTTP `400 Bad Request`, and the existing user-facing message `이미 가입된 캠퍼스입니다.`
- Impact: `POST /api/v1/admin/campuses/{campusId}/members` must not silently return or overwrite an active membership. It should match the existing invite-code duplicate join policy while still reactivating `INACTIVE` memberships.

### 2026-06-19 - Issue #57 My Monthly Devotion Summary Contract

- Context: GitHub Issue #57 was split from Issue #31 for the Notion `10.5 내 월간 경건생활 통계 조회` API. The issue still said to verify the Notion source before choosing path, query parameters, and response shape.
- Decision: Issue #57 follows Notion API `10.5`: `GET /api/v1/campuses/{campusId}/devotions/me/monthly-summary?year={year}&month={month}`. The response keeps the common `ApiResponse` envelope and returns `campusId`, `campusName`, `region`, `userId`, `name`, `year`, `month`, a monthly `devotion` summary, and `weeklyRecords[]` with `weeklyRecordId`, `weekStartDate`, `weekEndDate`, `quietTimeCount`, `prayerCount`, `bibleReadingCount`, `saturdayLateMinutes`, and `submittedAt`.
- Impact: Issue #57 does not add a new table. Monthly totals are calculated by calendar date from `devotion_daily_checks.record_date` between the selected month's first and last day for the current authenticated user after ACTIVE campus membership validation. `weeklyRecords[]` groups the selected month's daily rows by week and may contain partial-week counts when a week crosses a month boundary. `SATURDAY_LATE` minutes are included in the month that contains that week's Saturday date. Controller must return DTOs, detailed contract must be covered with Spring REST Docs, and Swagger documentation annotations must not be added.

### 2026-06-19 - Issue #33 Weekly Devotion Submission Response Shape

- Context: Issue #33 creates a `PENALTY` charge as a side effect of the first weekly devotion final submission. The remaining API contract question was whether the existing weekly devotion response should add a new field such as `generatedCharges`.
- Decision: Keep the existing `WeeklyDevotionResponse` structure unchanged for Issue #33. Do not add `generatedCharges` or another generated-charge summary field to `PUT /api/v1/campuses/{campusId}/devotions/me/weeks/{weekStartDate}`. Clients should confirm generated charges through the existing charge query APIs when needed.
- Impact: Issue #33 preserves the current devotion response contract while adding billing side effects. REST Docs should continue to document the existing weekly devotion response fields and verify that generated-charge response fields are not part of the contract.

### 2026-06-19 - Issue #33 Weekly Devotion Duplicate Submission Error Contract

- Context: Issue #33 needed a stable API error contract for requests after a weekly devotion record has already been finally submitted.
- Decision: If `weekly_devotion_records.submitted_at` already exists for the same campus, user, and week, both duplicate `submit = true` requests and post-submission `submit = false` saves fail with `DEVOTION_WEEKLY_ALREADY_SUBMITTED`, HTTP `409 CONFLICT`, and the user-facing message `이미 제출된 주간 경건생활은 수정할 수 없습니다.`
- Impact: The devotion submission boundary blocks same-week resubmission before billing reruns. The generated `PENALTY` charge for the first submission is not recalculated or overwritten through the normal weekly devotion API.

### 2026-06-19 - Issue #33 Daily Devotion Check After Weekly Submission

- Context: The one-time weekly devotion submission policy also needs to prevent the daily check API from changing the same week's source rows after final submission. Otherwise the weekly summary and generated `PENALTY` charge can diverge.
- Decision: If `weekly_devotion_records.submitted_at` already exists for the campus, user, and week containing `recordDate`, `PUT /api/v1/campuses/{campusId}/devotions/me/days/{recordDate}` must fail with `DEVOTION_WEEKLY_ALREADY_SUBMITTED`, HTTP `409 CONFLICT`, and the existing user-facing message `이미 제출된 주간 경건생활은 수정할 수 없습니다.`
- Impact: After final weekly submission, daily check requests for the same week must not create or update `weekly_devotion_records`, `devotion_daily_checks`, or `charge_items`. This preserves the MVP rule that same-week record modification, recalculation, and delta charge flows are excluded.

### 2026-06-19 - Issue #33 One-Time Weekly Devotion Submission

- Context: Issue #33 connects weekly devotion submission to automatic `PENALTY` charge creation. A previous open question asked how to handle resubmitting a weekly devotion record after the generated charge became terminal.
- Decision: Weekly devotion submission is one-time. Once `weekly_devotion_records.submitted_at` exists for a user/campus/week, the same weekly record cannot be submitted again. A later `submit = true` request for the same week must fail instead of recalculating or overwriting the existing devotion submission or charge. `submit = false` weekly saves are allowed only before final submission and must not create or update `PENALTY` charges.
- Impact: Issue #33 does not need a terminal charge resubmission policy because same-week resubmission is blocked at the devotion submission boundary. Development must test that first `submit = true` creates one combined `PENALTY` charge, duplicate `submit = true` fails, missing active `PENALTY` account fails the whole submission without creating a charge, and pre-submit `submit = false` saves do not create charges.

### 2026-06-19 - Issue #32 Penalty Rule And Fine Calculation Scope

- Context: Issue #32 still had an older API draft for devotion fine calculation, while the latest Notion integrated plan and API pages define penalty rule management APIs separately from the weekly devotion submission and charge creation flow.
- Decision: Issue #32 follows the latest Notion penalty rule API paths: `GET /api/v1/campuses/{campusId}/penalty-rules`, `POST /api/v1/admin/campuses/{campusId}/penalty-rules`, and `PATCH /api/v1/admin/penalty-rules/{ruleId}`. The issue also implements a `DevotionFineCalculator` domain service and calculation result model for `QUIET_TIME`, `PRAYER`, `BIBLE_READING`, and `SATURDAY_LATE` using the approved penalty table. The old draft endpoint `GET /api/v1/campuses/{campusId}/devotions/fines?weekStartDate=` is not part of Issue #32 unless the user explicitly approves a separate preview API later.
- Impact: Issue #32 implementation must not create or update `charge_items`; the real weekly submission to `PENALTY` charge integration remains Issue #33. REST Docs are required for the penalty rule APIs, while the calculator is verified with focused domain/application tests. Swagger documentation annotations must not be added.

### 2026-06-19 - Issue #32 Active Penalty Rule Replacement And Type Pairing

- Context: Issue #32 still needed final user approval for duplicate active penalty rule behavior and whether `ruleType`/`calculationType` combinations are flexible or fixed.
- Decision: When an administrator creates a new ACTIVE penalty rule for the same campus and `ruleType`, the previous ACTIVE rule of that type is automatically deactivated and the new rule becomes the only ACTIVE rule. `ruleType` and `calculationType` combinations are fixed: `QUIET_TIME`, `PRAYER`, and `BIBLE_READING` only allow `MISSING_COUNT`, while `SATURDAY_LATE` only allows `LATE_MINUTE`.
- Impact: Issue #32 must validate invalid type/calculation combinations and prevent multiple ACTIVE rules for the same campus/rule type from remaining active. Tests must cover replacement behavior and invalid pairing rejection.

### 2026-06-19 - Issue #55 Billing Charge List Error Code

- Context: During Issue #55 implementation, the existing billing charge query authorization failures had user-facing messages for "my charge list" and "campus charge list" access, but the approved detailed code list did not yet include a stable code for charge-list authorization errors.
- Decision: Add `BILLING_CHARGE_LIST_FORBIDDEN` and use it for billing charge list authorization failures while preserving the existing user-facing messages.
- Impact: Billing charge query APIs can return a detailed domain-prefixed code instead of reusing a less precise billing account/status code or a broad `FORBIDDEN` code. This remains within the Issue #55 refactor scope and does not change API paths or DB schema.

### 2026-06-19 - Common Error Code And Request Validation Refactor Policy

- Context: After Issue #36 was merged, charge query code showed that request validation and user-facing error messages were spread across controllers, presentation helpers, application services, and broad shared error codes such as `INVALID_REQUEST`. The user decided the stable API error contract and validation structure before creating a separate refactor issue.
- Decision: Error responses use `HTTP status + detailed code` as the fixed API contract. The `message` field is managed as user-facing display text. `ErrorCode` remains one global enum, but each code should be split by domain prefix, such as `BILLING_INVALID_SORT_DIRECTION` or `CAMPUS_MEMBER_NOT_FOUND`. Invalid `page`, `size`, or `sort` values must return `400` instead of being silently corrected. Simple DTO validation uses Bean Validation. Pagination and sorting parsing move to a common request validation component. Business rules move to explicit policy classes such as `CampusRolePolicy`, `ChargeStatusPolicy`, and `BillingAccessPolicy`. This cleanup is handled as one separate refactor issue after Issue #36, not inside the already merged #36 feature PR.
- Impact: Future development must not keep adding broad `INVALID_REQUEST` usage with hardcoded messages when a stable domain error code is required. REST Docs must document error responses using the detailed `code`, and tests must cover invalid pagination/sorting values returning `400`. This refactor must not add new features, change API paths, change DB schema, or add Swagger documentation annotations.

### 2026-06-18 - Issue #36 Charge Query Date Filter And Monthly Summary Policy

- Context: Issue #36 originally listed `startDate` and `endDate` query parameters for charge list APIs, but the user reconsidered the product behavior during development. The user preferred not to expose manual date-range filtering in the API contract and wanted the app to show recent paid/charged history without deleting older records.
- Decision: Remove `startDate` and `endDate` from the four Issue #36 charge query APIs. The backend keeps charge history queryable through pagination, sorting, `paymentCategory`, `status`, `userId`, and `keyword` filters. The client may choose to emphasize recent paid items in the default screen, but backend records are not hidden or deleted by time. For `GET /api/v1/campuses/{campusId}/charges/me/summary`, `monthlyPaidAmount` is calculated by `paidAt` in the selected year/month, while `monthlyUnpaidAmount`, `monthlyTotalChargeAmount`, and `monthlyByCategory` use charge `createdAt` in the selected year/month as the "charged" period.
- Impact: Issue #36 implementation, REST Docs, and tests must not document or bind `startDate`/`endDate`. Query tests must cover full-history pagination and the split monthly summary basis (`paidAt` for paid totals, `createdAt` for charged/unpaid totals). If a future UX needs explicit date-range search, it must be approved as a new API contract change.

### 2026-06-18 - Issue #36 Charge List And Campus Summary Contract

- Context: Issue #36 needed final charge query behavior for member-facing charge lists, member payment summary, campus-level administrator aggregation, and administrator member detail.
- Decision: Use the latest Issue #36 API paths: `GET /api/v1/campuses/{campusId}/charges/me`, `GET /api/v1/campuses/{campusId}/charges/me/summary`, `GET /api/v1/admin/campuses/{campusId}/charges`, and `GET /api/v1/admin/campuses/{campusId}/members/{userId}/charges`. Do not implement `/api/v1/users/me/charges`, `/api/v1/campuses/{campusId}/charges`, or `/api/v1/campuses/{campusId}/charges/unpaid-users`. Administrator campus charge query returns `summary + members[]` aggregation only and does not include individual charge items. Administrator member detail includes target member `userId`, `name`, and `email`, and uses the same charge item `account`/`source` structure as the member-facing list.
- Impact: Issue #36 controller responses must use DTOs and the common `ApiResponse` envelope, not Entity returns. Request/Response DTOs and application Result records remain separated. Detailed API contracts are verified through Spring REST Docs tests, without adding Swagger documentation annotations.

### 2026-06-18 - Issue #35 Charge Status Transition Policy

- Context: Issue #35 needed final clarification for charge payment completion, waiver, cancellation, and administrator correction behavior before development. The earlier issue draft and Notion API page allowed an administrator to set `PAID`, but the user chose a stricter rule.
- Decision: User payment completion is the only path that changes an `UNPAID` charge to `PAID`. Administrators must not mark a charge as `PAID`. Administrators may change a charge to `WAIVED` or `CANCELED`, and may revert an incorrectly handled `PAID`, `WAIVED`, or `CANCELED` charge back to `UNPAID`. Issue #35 does not store an administrator status-change reason and does not add `statusChangedReason`, `waivedAt`, `canceledAt`, or a status history table.
- Impact: Issue #35 implementation, tests, REST Docs, GitHub issue body, and Notion API documentation must use admin target statuses `UNPAID`, `WAIVED`, and `CANCELED` only. `paidAt` is used for user payment completion; when an administrator reverts a `PAID` charge to `UNPAID`, `paidAt` should be cleared. Automatic source rerun behavior for terminal charges remains outside this decision and must still be confirmed when wiring Issue #33/#39.

### 2026-06-18 - Issue #35 Payment Completion Request Contract

- Context: Issue #35 specified that `paidAt` is optional for `PATCH /api/v1/campuses/{campusId}/charges/me/{chargeItemId}/paid`, but left the empty-body contract and timestamp format to be fixed before TDD implementation.
- Decision: The user payment completion API accepts both an omitted request body and an empty JSON body. If `paidAt` is omitted, the server time is used. When `paidAt` is provided, the request must use an offset-aware instant format such as `2026-06-12T12:30:00Z`.
- Impact: Issue #35 controller tests and REST Docs must document optional `paidAt`, omitted-body support, and offset-aware `Instant` parsing. The response should expose `paidAt` as an instant value.

### 2026-06-18 - Issue #34 Admin Account List And Penalty Charge Rerun Policy

- Context: PM review found that service-level `ADMIN` could not list campus payment accounts without campus membership, and `BillingService.createPenaltyCharge` raised a unique constraint error when the same penalty charge source was executed again.
- Decision: `GET /api/v1/campuses/{campusId}/payment-accounts` allows either service-level `ADMIN` or an ACTIVE campus member. `BillingService.createPenaltyCharge` behaves as create-or-update for an existing `UNPAID` `PENALTY` charge with the same `(campusId, userId, paymentCategory, sourceType, sourceId)`: it updates the latest active PENALTY account snapshot, title, reason, amount, and due date, then returns the same row.
- Implementation guard: The Issue #34 billing foundation keeps terminal charges guarded so `PAID`, `WAIVED`, or `CANCELED` charges are not overwritten by a source rerun. For Issue #33 specifically, the later 2026-06-19 decision blocks same-week devotion resubmission at the devotion boundary, so terminal devotion charge reruns should not occur through the normal weekly submission flow.
- Impact: Issue #34 service and controller tests must cover service-admin account list access and service-level penalty charge reruns for existing `UNPAID` charges. The DB unique key remains a safety net, but normal service reruns should not surface unique constraint exceptions for existing `UNPAID` charges.

### 2026-06-18 - Issue #34 Member Payment Account Response Contract

- Context: The Issue #34 payment account list API is available to every ACTIVE campus member, but older Notion endpoint detail examples included admin-oriented fields such as `ownerUserId` and `isActive`.
- Decision: `GET /api/v1/campuses/{campusId}/payment-accounts` returns active accounts only and exposes the member-facing fields required for payment: `id`, `accountType`, `nickname`, `bankName`, `accountNumber`, and `accountHolder`. It does not expose admin-only metadata such as `ownerUserId`, `isActive`, `createdAt`, or `deactivatedAt` in the member-facing response.
- Impact: Issue #34 REST Docs and controller tests must document the reduced member-facing response. Account numbers remain fully visible because they are required for bank transfer payment.

### 2026-06-18 - Issue 30 Same-Level Campus Role Assignment And Coffee Duty Permission

- Context: Issue #30 role hierarchy wording could be read as "only roles below the requester can be changed or assigned." The user clarified the final behavior during the development session.
- Decision: A campus manager can assign campus roles up to the manager's own campus role level, but cannot change or assign roles above that level. `MINISTER` can change another user to `MINISTER`, `ELDER`, `CAMPUS_LEADER`, or `MEMBER`. `ELDER` can change another user to `ELDER`, `CAMPUS_LEADER`, or `MEMBER`, but cannot change an existing `MINISTER` or assign `MINISTER`. `CAMPUS_LEADER` can change another user to `CAMPUS_LEADER` or `MEMBER`, but cannot change an existing `ELDER` or `MINISTER` or assign those roles. `MEMBER` cannot change roles. Service-level `ADMIN` can change all campus roles, and service-level `MANAGER` alone does not grant campus role change permission. Coffee duty management is allowed for service-level `ADMIN` and active campus members whose campus role is not `MEMBER`; service-level `MANAGER` alone does not grant coffee duty management permission.
- Impact: Issue #30 implementation, tests, and REST Docs must use same-level assignment semantics and non-`MEMBER` coffee duty management permission. Any earlier "below only" interpretation is superseded.

### 2026-06-18 - Issue 30 Campus Role Hierarchy And Coffee Duty Contract

- Context: Issue #30 needed final confirmation before development because the campus role update API path, coffee duty assignment cardinality, and campus role downgrade rules were ambiguous.
- Decision: Issue #30 must follow the latest Notion API contract. Campus role changes use `PATCH /api/v1/admin/campuses/{campusId}/members/{campusMemberId}/campus-role`, where `campusMemberId` means `campus_members.id`. Coffee duty assignment is limited to one active `DutyType.COFFEE` assignee per campus and uses `PUT /api/v1/admin/campuses/{campusId}/duty-assignments/coffee` to assign/replace the active assignee and `DELETE /api/v1/admin/campuses/{campusId}/duty-assignments/coffee/{assignmentId}` to revoke. The campus role hierarchy is `MINISTER > ELDER > CAMPUS_LEADER > MEMBER`. A campus manager may change roles only below their own role: `MINISTER` can change `ELDER`, `CAMPUS_LEADER`, and `MEMBER`; `ELDER` can change `CAMPUS_LEADER` and `MEMBER`, but not `MINISTER`; `CAMPUS_LEADER` can change `MEMBER`, but not `MINISTER` or `ELDER`; `MEMBER` cannot change roles. Service-level `ADMIN` can change any campus member role in any campus. The last campus management role holder may still be downgraded to `MEMBER`; do not block it with a last-manager guard in Issue #30.
- Impact: Issue #30, Notion planning, API documentation, REST Docs tests, and implementation must use these paths and authorization rules. Development must not use the older `members/{memberId}/role`, generic `POST duty-assignments`, or `PATCH revoke` API drafts for #30.
- Status: Role assignment hierarchy wording is superseded by the later 2026-06-18 decision `Issue 30 Same-Level Campus Role Assignment And Coffee Duty Permission`: same-level assignment is allowed. API paths, `campusMemberId`, coffee duty cardinality, and last-manager downgrade policy remain valid.

### 2026-06-18 - Campus Member Delete And Management Permission For Issue 29

- Context: PR #50 / Issue #29 needed an additional campus member delete feature. The user approved adding the feature and clarified that everyone except normal users can manage campus members.
- Decision: Add `DELETE /api/v1/campuses/{campusId}/members/{membershipId}`. The endpoint soft-deletes the campus membership by changing `campus_members.status` to `INACTIVE` and returns `204 No Content` on success. Campus member management is allowed for service-level `ADMIN` and active campus members whose `campus_role` is not `MEMBER` (`MINISTER`, `ELDER`, `CAMPUS_LEADER`). A normal campus `MEMBER` cannot manage or delete campus members. If an inactive/deleted user joins again by invite code, the existing membership is reactivated as `ACTIVE + MEMBER` to respect the `(campus_id, user_id)` uniqueness rule.
- Impact: Issue #29 and PR #50 now include campus member delete in addition to campus creation, invite-code join, my-campus list, and campus detail APIs. Tests and REST Docs must cover delete permission, soft delete status transition, service-admin delete without campus membership, and rejoin after inactive membership.

### 2026-06-18 - Campus API Response And Error Contract For Issue 29

- Context: Issue #29 needed final confirmation for ambiguous campus response fields, admin campus-detail behavior, and user-facing error messages before implementation.
- Decision: `GET /api/v1/campuses/me` returns only the current user's `ACTIVE` memberships, with each item containing `membershipId`, `campusId`, `campusName`, `region`, `campusRole`, and `status`; `joinedAt` is excluded. Campus detail returns `campusId`, `name`, `region`, `description`, `isActive`, `myCampusRole`, `membershipStatus`, and conditionally `inviteCode`. `ADMIN` can see all campus details and invite codes; when an admin is not a member of the campus, `myCampusRole` and `membershipStatus` are `null`. Error messages are `유효하지 않은 초대코드입니다.`, `이미 가입된 캠퍼스입니다.`, `캠퍼스 조회 권한이 없습니다.`, and `캠퍼스 생성 권한이 없습니다.`.
- Impact: Issue #29 implementation and REST Docs must follow these response shapes and messages. Older endpoint drafts with different field names or creator roles are superseded by this decision and the latest Issue #29 scope.

### 2026-06-18 - Campus Creation Does Not Create Payment Account Or Penalty Rules

- Context: Older local docs still said campus creation should create a `PENALTY` payment account and default `penalty_rules`, while the latest Issue #29, Notion integrated document, and current development delegation state that campus creation and account/rule setup are separate.
- Decision: Campus creation must not receive `penaltyAccount`, must not create `PaymentAccount`, and must not create default `penalty_rules`.
- Impact: Issue #29 tests must guard that campus creation only creates the campus and creator membership. Billing prerequisites are configured through separate admin setup flows.

### 2026-06-18 - Issue 29 Campus Role And Invite Code Visibility Clarification

- Context: Issue #29 needed a documentation-only clarification so the service-level role, campus-level role, campus creation permission, campus management permission, and invite-code visibility rules are consistently recorded without overwriting the existing #29 decisions.
- Decision: `users.role` is the service-level role set and uses `USER`, `MANAGER`, and `ADMIN`. `campus_members.campus_role` is the campus-level role set and uses `MINISTER`, `ELDER`, `CAMPUS_LEADER`, and `MEMBER`. `MANAGER` and `ADMIN` can create campuses. When a `MANAGER` creates a campus, that user is registered in the new campus as `ACTIVE + MINISTER`. `MANAGER` is not itself a campus-management role; campus management must be based on `campus_members.campus_role`. `ADMIN` can access all campus details. Invite codes are included in campus creation responses, and can be viewed by `ADMIN`, `MINISTER`, `ELDER`, and `CAMPUS_LEADER`, but must not be exposed in normal `MEMBER` campus detail responses. `GET /api/v1/campuses/me` returns only the current user's `ACTIVE` memberships.
- Impact: Issue #29 documentation and implementation must keep service roles and campus roles separate. Service-level admin user-role management APIs, last `ADMIN` protection, and last campus manager protection are separate pending/admin issues and are not implemented as part of #29.

### 2026-06-16 - User Owns All Project Decisions

- Context: The user stated that Codex must not develop or implement based on guesses.
- Decision: All ambiguous or unusual decisions must be asked of the user before implementation.
- Impact: Codex must stop and ask before choosing product behavior, architecture, schema, deployment, test strategy, monitoring scope, resume metrics, or implementation tradeoffs.

### 2026-06-16 - Track Resume Metrics During Development

- Context: The project will be deployed and operated, and the user wants resume-ready quantitative evidence.
- Decision: Codex should record measurable project progress, tests, troubleshooting, and improvements in Markdown and Obsidian.
- Impact: Metrics are tracked in `docs/resume-metrics.md` and mirrored to the Obsidian FaithLog note.

### 2026-06-16 - Backend API And Issue Policy Alignment

- Context: The user provided final backend policy decisions for auth, FCM, poll comments, devotion APIs, coffee charge automation, issue status management, and MVP exclusions.
- Decision: GitHub Issues must follow the final policies recorded in `docs/backend-implementation-policy.md`.
- Impact: Issues #21, #27, #28, #31, #38, #39, and #40 were updated with final policy details. Manual `칸반 상태:` lines were removed from #17~#41 where present so GitHub Project Board Status remains the source of truth.

### 2026-06-16 - Codex Hook Development Rules

- Context: The user requested a Codex Hook document that consolidates TDD, final FaithLog design rules, architecture rules, security rules, forbidden-term checks, test rules, Obsidian documentation, and GitHub Issue/Project workflow.
- Decision: Use `AGENTS.md` as the single Agent rule file, and put detailed development hook rules in `docs/codex/FAITHLOG_CODEX_HOOK.md`.
- Impact: Issue #43 was created and connected to the FaithLog Backend Kanban board. The board card was moved to `In Progress`. `PollComment` was not treated as a forbidden term because poll comments are MVP scope in #38.

### 2026-06-16 - Agent Rule File Consolidation

- Context: The user requested that Agent rules be merged into one file.
- Decision: `AGENTS.md` is the single source of Agent instructions for Codex in this repository.
- Impact: The former `AGENT.md` content was merged into `AGENTS.md`, and repository documentation now points to `AGENTS.md`.

### 2026-06-16 - Poll Template Default Policy

- Context: The user clarified which poll templates should exist by default.
- Decision: Only the coffee poll template should be provided as a default template. Wednesday worship, Saturday shepherd meeting, and custom poll templates should be created by an admin.
- Impact: Issue #37 and Codex Hook rules must treat coffee as the only default poll template. Other poll templates are admin-created and must not be silently seeded unless the user later approves a new decision.

### 2026-06-16 - Poll Template Weekly Auto Generation Setting

- Context: The user asked whether polls can be generated automatically every week, then decided that admins should be able to set this when creating custom/admin-created templates.
- Decision: Poll templates can include a weekly auto-generation setting chosen by the admin at template creation time. Templates without auto-generation enabled are used only for manual poll creation.
- Impact: Issue #37 must capture the template setting/API scope, and Issue #24 must execute enabled template schedules with duplicate prevention.

### 2026-06-16 - Coffee Duty Poll Time Settings

- Context: The user clarified that Notion ERD includes coffee poll timing design.
- Decision: The coffee duty assignee can set the weekly coffee poll auto-generation time and the coffee poll close time.
- Impact: Issue #37 must store these timing settings on the coffee poll template according to the Notion ERD column names. Issue #24 must use those settings when generating and closing weekly coffee polls.

### 2026-06-17 - Devotion Penalty Table

- Context: The user provided the current devotion check notice used by the ministry team.
- Decision: Devotion penalty rules use a 5-day weekly standard. Quiet time missing count is charged at 500 KRW per day, prayer missing count is charged at 500 KRW per day, Bible reading missing count is charged at 300 KRW per day, and Saturday lateness is charged as 1,000 KRW base plus 100 KRW per late minute. For the referenced week, lateness minutes are all 0.
- Impact: Issue #32 must seed or configure `penalty_rules` to support `QUIET_TIME`, `PRAYER`, `BIBLE_READING`, and `SATURDAY_LATE` with these amounts. Issue #33 must calculate one combined weekly `PENALTY` charge per `weekly_devotion_records.id`.

### 2026-06-17 - Final Implementation Overrides For Conflicting Old Specs

- Context: The user approved the implementation direction previously identified by Codex for conflicts between older Notion/API drafts and the latest local decisions.
- Decision: Refresh Token storage follows Redis allowlist and not the old `refresh_tokens` table design. Poll responses must use request field `optionIds` and `poll_response_options`, not request field `optionId` or `poll_responses.option_id`. Devotion implementation uses the weekly `PUT /api/v1/campuses/{campusId}/devotions/me/weeks/{weekStartDate}` flow to create or update 7 daily rows; the old single-day devotion API is not the MVP implementation path.
- Impact: Auth, poll, and devotion issues must treat these decisions as higher priority than older Notion API text. Any implementation or API documentation that still exposes the old paths/fields must be corrected before development is considered complete.
- Status: Partially superseded for devotion. The 2026-06-19 decision `Issue #31 Devotion Daily Check And Weekly Submission Sync` includes the daily check API in MVP while keeping weekly submission as the only submission/penalty trigger.

### 2026-06-19 - Issue #31 Devotion Daily Check And Weekly Submission Sync

- Context: The user asked to compare Issue #31 with the latest Notion planning/API/ERD and update local planning to match Notion before development.
- Decision: Issue #31 includes both daily check and weekly save/submit flows. The daily check API is `PUT /api/v1/campuses/{campusId}/devotions/me/days/{recordDate}` and creates or updates the matching `devotion_daily_checks` row while synchronizing the weekly row if missing. Daily checks never update `submitted_at` and never create or update `PENALTY` charges. The weekly API remains `PUT /api/v1/campuses/{campusId}/devotions/me/weeks/{weekStartDate}` and uses request field `dailyChecks`. Weekly submission creates or updates Monday-Sunday daily rows, fills missing submission dates with false defaults, updates `weekly_devotion_records.submitted_at` when `submit = true`, and uses `weekly_devotion_records.submitted_at` as the submission/missing-user source of truth.
- Impact: Issue #31 development must implement the daily API and weekly API together. Issue #33 remains responsible for the final penalty charge integration, but #31 tests must prove daily checks do not trigger submission or billing behavior.

### 2026-06-19 - Monthly Devotion Statistics Split From Issue #31

- Context: Notion includes a monthly devotion statistics page, but Issue #31 already covers daily check, weekly save/submit, weekly read, and admin missing-user lookup.
- Decision: Monthly devotion statistics are excluded from Issue #31 and must be tracked as a separate GitHub Issue.
- Impact: Issue #31 stays focused on the daily/weekly submission flow. Monthly statistics development must verify the final Notion 10.5 API and response contract before implementation instead of guessing the API path or aggregation rules.

### 2026-06-19 - Empty Weekly Devotion Lookup Returns Defaults

- Context: The user chose the mobile-friendly behavior for `GET /api/v1/campuses/{campusId}/devotions/me/weeks/{weekStartDate}` when the user has no record for the requested week.
- Decision: My weekly devotion lookup must not return 404 just because the weekly record does not exist yet. Instead, it returns a default weekly response with Monday-Sunday `dailyChecks`, all check fields false, summary counts 0, `saturdayLateMinutes = 0`, and `submittedAt = null`.
- Impact: Issue #31 development must update tests and implementation so the weekly screen can render immediately before the user creates any daily checks or weekly submission. Missing-user admin lookup still treats absent weekly records as missing submissions.

### 2026-06-17 - Pagination Sorting Redis TTL And Notification Failure Policies

- Context: The user approved the recommended implementation policies for list APIs, Redis token TTLs, and notification failure handling.
- Decision: List APIs use common pagination query parameters `page`, `size`, and `sort`; default page is 0, default size is 20, maximum size is 100, and default sorting is latest-first unless a domain has an explicit order such as poll option `sortOrder,asc`. Access token blacklist TTL uses the access token remaining lifetime plus 60 seconds. Refresh token allowlist TTL uses the refresh token expiration. Refresh token reuse-detection keys, when used, live until the refresh token expiration. Notification sends do not retry automatically in MVP. Success, failure, and skip results are all saved to `notification_logs`; invalid or unregistered FCM token errors deactivate the affected token.
- Impact: Repository query APIs, Redis auth infrastructure, and notification services must implement these defaults and cover them with tests where applicable.
- Status: Notification retry wording is superseded for Issue #40 by the 2026-06-20 decision `Issue #40 Notification Async Retry Policy`: transient notification send failures retry per token up to 3 times with `1s -> 5s -> 30s` intervals.

### 2026-06-17 - Lateness Penalty Calculation

- Context: The user confirmed the intended interpretation of the Saturday lateness penalty rule.
- Decision: If `saturdayLateMinutes = 0`, the lateness penalty is 0 KRW. If `saturdayLateMinutes > 0`, the lateness penalty is `1,000 + saturdayLateMinutes * 100` KRW.
- Impact: Issue #32 must implement this conditional formula in the devotion penalty calculator.

### 2026-06-17 - Campus Creation Includes Penalty Account And Penalty Rules

- Context: The user identified that creating the campus penalty account and penalty rules at campus creation time reduces later runtime exceptions in devotion submission.
- Decision: Campus creation must also create the campus penalty account and default penalty rules. The campus creation request/flow must collect or receive enough penalty account information to create the active `PENALTY` payment account, and must initialize the default devotion penalty rules from the approved penalty table.
- Impact: Issue #29 and Issue #34 must be aligned so campus onboarding creates the required billing prerequisites. Issue #33 can assume a properly onboarded campus has an active `PENALTY` account, while still returning a clear error if the account is missing due to legacy or corrupted data.
- Status: Superseded. This is a historical record only. The later 2026-06-18 decision `Campus Creation Does Not Create Payment Account Or Penalty Rules` and the latest Issue #29 scope take precedence: campus creation and account/rule setup are separate, and campus creation must not receive `penaltyAccount`, create `PaymentAccount`, or create default `penalty_rules`.

### 2026-06-18 - Issue #34 Payment Account And Charge Foundation Scope

- Context: Issue #34 was checked against the latest Notion integrated plan, final ERD, and API design before development.
- Decision: Issue #34 follows the Notion billing foundation model: implement `PaymentAccount`, `ChargeItem`, `PaymentCategory`, `ChargeSourceType`, `ChargeStatus`, payment account list/create/deactivate APIs, account snapshot support, missing-account failure behavior, and duplicate charge prevention. Campus creation does not create accounts or default penalty rules. Manual admin charge creation is not MVP scope.
- Impact: Detailed API contracts must be verified through Spring REST Docs tests, while Swagger/springdoc remains for simple API exploration. Later charge-producing flows must use the billing foundation instead of manipulating another domain's entity directly.

### 2026-06-18 - Issue #34 Payment Account Activation And Visibility Policy

- Context: The user finalized the remaining account-management behavior before Issue #34 development.
- Decision: Each campus can have only one active payment account per `account_type`. All active campus members can view campus payment accounts, and account numbers are fully visible because users need them for bank transfer payment. Creating a new active account automatically deactivates the previous active account for the same campus and account type. Accounts can be deactivated even if unpaid charge items are linked to them. When a new active account replaces the old one, existing `UNPAID` charge items for that campus and payment category are re-linked to the new active account and their account snapshots are updated. Terminal `PAID`, `WAIVED`, and `CANCELED` charge items keep their historical snapshots. Issue #34 implements only the billing foundation service; actual devotion and coffee auto-charge flow integration remains in Issue #33 and Issue #39.
- Impact: Issue #34 tests must cover one-active-account-per-type behavior, member account list access, full account-number exposure in payment account responses, deactivation with unpaid charges, unpaid charge re-linking on account replacement, and preservation of terminal charge snapshots.

### 2026-06-17 - Coffee Poll Requires Coffee Duty Assignment

- Context: The user decided that coffee poll behavior should fail clearly when no coffee duty assignee exists.
- Decision: If a coffee poll flow requires a coffee duty assignee and no active `CampusDutyAssignment` with `DutyType.COFFEE` exists for the campus, the API must fail with a clear user-facing message: `관리자에게 문의하세요`.
- Impact: Issue #30 must provide active coffee duty assignment management, and Issue #37/#39 must validate the assignment before coffee poll setup or coffee charge flow where required.

### 2026-06-19 - Issue #37 Coffee Brand And Menu Catalog

- Context: The user clarified that coffee ordering is initially limited to Compose Coffee, but the design should allow additional coffee brands later.
- Decision: Do not store Compose Coffee menu names and prices in frontend-only data or Java enums. Issue #37 must add backend-managed coffee brand/menu catalog data. MVP seeds one active brand, Compose Coffee, and seeds all current Compose Coffee menu items into the catalog. The default coffee poll template starts with these five options: iced americano, americano, iced tea, iced latte, and latte. Additional template options are added by selecting from the backend coffee menu catalog. `poll_template_options` and `poll_options` store copied menu name/code/price snapshots so later catalog price changes do not mutate already-created polls or charges.
- Impact: Issue #37 must include `coffee_brands`, `coffee_menu_catalog`, catalog lookup API, Compose Coffee full-menu seed, and default coffee template option seeding. Brand/menu admin CRUD and additional brand onboarding are excluded unless the user approves a separate issue. Development must verify the full Compose Coffee seed list and prices from an approved current source before implementation instead of guessing.

### 2026-06-19 - Issue #37 Coffee Catalog Source And API Path

- Context: The user approved the source of truth for Compose Coffee menu seed data and accepted the recommended catalog lookup API paths.
- Decision: Initial planning preferred the official Compose Coffee menu board/source available at implementation time, and the catalog lookup APIs are `GET /api/v1/coffee-brands` and `GET /api/v1/coffee-brands/{brandId}/menus`. This source rule is superseded by the later `Issue #37 Compose Coffee Seed Source Override` decision above: if official verification is blocked or impossible, a latest menu/price source explicitly approved by the user may be used.
- Impact: Issue #37 development must not guess menu prices. REST Docs tests must document both catalog lookup APIs, and the seed verification record must name whether the seed came from an official source or from a user-approved latest source.

### 2026-06-19 - Issue #38 Poll Result Visibility

- Context: The user clarified how poll result visibility should work for normal and anonymous polls.
- Decision: Normal poll results are visible to all active campus members, not only admins. If `polls.is_anonymous = false`, the result response may show who voted for each option. If `polls.is_anonymous = true`, nobody should be able to identify who voted for which option through result APIs; return aggregate counts only and hide respondent user identifiers/names. The backend still stores `poll_responses.user_id` for duplicate response prevention, response editing, and missing-member calculation, but does not expose voter identity for anonymous poll results.
- Impact: Issue #38 must use a member-facing result endpoint, such as `GET /api/v1/campuses/{campusId}/polls/{pollId}/results`, and tests must cover both non-anonymous identity exposure and anonymous identity hiding. Admin-only missing-member lookup can remain separate.

### 2026-06-19 - Issue #38 Poll Result And Past Poll Visibility Window

- Context: The user clarified how long poll results and past polls should remain visible after a poll ends.
- Decision: Visibility windows are based on `polls.ends_at`. User-facing poll history, poll detail, and poll result lookup are visible to active campus members only until 3 days after `ends_at`. Admin-facing poll history, poll detail, and poll result lookup are visible in the admin page only until 7 days after `ends_at`. After the visibility window expires, expired polls should be hidden from lists and direct lookup should not expose the poll/result data.
- Impact: Issue #38 must add tests for member visibility before and after `ends_at + 3 days`, and admin visibility before and after `ends_at + 7 days`. The anonymous poll identity-hiding rule still applies during the visible window.

### 2026-06-19 - Issue #38 Poll-Level Result Query

- Context: The user clarified that seeing who voted for what should be handled as a full result view for one poll, not as separate option-level result lookup APIs.
- Decision: Issue #38 must provide one poll-level result API, `GET /api/v1/campuses/{campusId}/polls/{pollId}/results`. Do not create option-level result endpoints such as `/options/{optionId}/results` for MVP. The poll-level result response contains poll metadata, all options, vote counts, and, when the poll is not anonymous, respondent lists grouped under each option. Anonymous polls return aggregate counts only and omit respondent identity fields.
- Impact: API docs, tests, and frontend planning should treat the poll detail/result screen as a single poll-level result resource. Tests must verify that all options are returned in one response and that anonymous polls do not expose respondent identity.

### 2026-06-17 - Prayer Requests Group Board

- Context: The user described a weekly Saturday prayer request workflow where each sharing group collects member prayer requests and currently posts them as one KakaoTalk message. The user decided the app should replace the message view instead of generating KakaoTalk share text.
- Decision: Add a prayer request feature where all campus members can view the weekly prayer requests for all groups on one page. Prayer groups are managed inside an active prayer season that can start without a fixed end date and be manually closed when groups change. Prayer request input should also work on one page so a user with permission can enter multiple group members' weekly prayer requests together. KakaoTalk sharing is not MVP scope for this feature.
- Impact: A new prayer domain should be planned with prayer seasons, prayer groups, group members, weekly prayer boards, and member submissions. The read API must support one-page grouped output for the whole campus.

### 2026-06-17 - Prayer Request Editing And Conflict Policy

- Context: The user was concerned about simultaneous edits when multiple people can edit prayer requests from one page.
- Decision: Store prayer requests per member submission rather than as one large page blob. A weekly prayer submission can have nullable content for cases where there is no meeting or no request to write. Each member submission must use version-based optimistic locking. The client sends the current version when saving; the server saves only if the version still matches and otherwise returns a conflict instead of silently overwriting another edit.
- Impact: Prayer request write APIs must support partial person-level saves and `409 Conflict` behavior. The UI can show all members on one page, but persistence and conflict detection are scoped to each member's submission row.

### 2026-06-17 - Prayer Request No-Meeting And Writing Status Policy

- Context: The user clarified that prayer requests are still written even when there is no meeting, and that prayer requests do not need a separate submission deadline.
- Decision: `NO_MEETING`, if used, is not a blocking prayer request submission state. It only describes meeting schedule/context. Prayer request writing remains available even if there is no meeting. Because there is no separate deadline, do not split "not written yet" and "missing submission" into separate states; model/display prayer request writing as not written vs written.
- Impact: Issue #45 must not treat `NO_MEETING` as a reason to disable prayer request entry or as a missing-submission state. Schema/API/UI planning should keep meeting status separate from person-level prayer request content/submission status. The exact storage scope for meeting status, such as whole prayer week vs group-week, still needs user confirmation before schema implementation.

### 2026-06-17 - Daily Resume Monitor Manual Automation

- Context: The user requested a daily resume monitoring automation that reviews previous-day FaithLog work and writes resume-oriented Markdown notes in both project docs and the Obsidian vault.
- Decision: Implement the monitor as a manual script first, store its versioned prompt at `docs/prompts/daily-resume-monitor.md`, read the prompt and Agent rules each run, write only evidence-backed notes/metrics, record unverified items as pending decisions, and do not schedule the automation until a scheduling policy is explicitly approved.
- Impact: `python3 scripts/daily_resume_monitor.py` is the approved manual entry point. The monitor may update `docs/resume-metrics.md`, `docs/decision-log.md`, `docs/wiki/`, and the approved Obsidian FaithLog path, but it must not invent metrics or infer product/architecture/schema/API/security/deployment/testing direction.

### 2026-06-17 - Defer Flyway Until Feature Development Is Complete

- Context: The user decided that Flyway should be introduced after the main feature development work is complete, rather than used as the active schema mechanism during early feature implementation.
- Decision: During feature development, do not treat Flyway migrations as the primary implementation deliverable. Final Flyway migration scripts should be organized near the end of development after the approved domain model has stabilized.
- Impact: GitHub Projects should include a later infra/build task for final Flyway migration consolidation. Feature issues may define schema requirements and tests, but should not be blocked on final Flyway script authoring unless the user later changes this policy.

### 2026-06-17 - Remove Flyway From Active Runtime

- Context: The user explicitly requested removing Flyway now before raising the PR again.
- Decision: Remove Flyway Gradle dependencies, remove `spring.flyway` runtime configuration, and remove the placeholder Flyway migration file from the active codebase. Flyway remains deferred until the main feature development work is complete.
- Impact: Early feature development will rely on approved schema requirements and persistence tests instead of active Flyway migration scripts. A later infra/build task should reintroduce consolidated migrations when the domain model stabilizes.

### 2026-06-17 - Local Docker Uses JPA DDL Auto Update During Development

- Context: #27 Docker verification reached the application boot step after the local Postgres credential mismatch was resolved, but the app could not start against an empty local Docker database while Flyway remains deferred and `ddl-auto=validate` was active.
- Decision: For local Docker development verification only, default `SPRING_JPA_HIBERNATE_DDL_AUTO` to `update` so Hibernate can create or update the local development schema. Keep the value environment-overridable.
- Impact: Local Docker can boot and serve `GET /api/v1/health` during early feature development before final Flyway migration consolidation. This does not change the deferred Flyway policy or define a production migration strategy.

### 2026-06-17 - API Documentation Uses Spring REST Docs For Detailed Contracts

- Context: The user clarified that Swagger/springdoc should remain available for simple API exploration, but the codebase should not be cluttered with Swagger documentation annotations on Controllers, DTOs, or Entities.
- Decision: Swagger/springdoc is kept. Swagger annotation-centered documentation is not used as the main documentation approach. Detailed request/response API contracts are verified and documented through Spring REST Docs tests and generated snippets/asciidoc.
- Impact: New APIs or changed APIs should add MockMvc/WebMvc/Spring REST Docs tests where practical. Controllers, DTOs, and Entities must not be polluted with documentation-only Swagger annotations such as `@Operation`, `@Schema`, or `@ApiResponse`.

### 2026-06-17 - FCM Token Lifecycle Policy

- Context: The user clarified that FCM tokens are issued by the frontend Firebase SDK, not by the backend, and asked whether token expiration/staleness handling is included in the plan.
- Decision: The frontend must fetch the current FCM token on app entry/login and send it to the backend. The backend stores FCM tokens in `user_fcm_tokens` as the source of truth and handles the registration API as an idempotent upsert. Redis is not the source of truth for FCM tokens. The backend stores a frontend-generated `clientInstanceId` to identify the app installation, updates `lastSeenAt` and `lastRefreshedAt`, deactivates previous tokens for the same user/client instance when a token changes, deactivates token ownership for another user when the same token is re-registered, deactivates the current device token on logout, and excludes or deactivates tokens stale for 90 days. `UNREGISTERED`/token-not-registered failures deactivate the token immediately, while `INVALID_ARGUMENT` deactivates the token only when the payload is known to be valid.
- Impact: Issue #40, Notion planning, ERD, and API design must include FCM token lifecycle fields and upsert/stale-token behavior. Issue #24 or a later scheduler task may clean up stale tokens in batch, but send-time filtering must not target inactive or stale tokens.

### 2026-07-04 - FCM Active Token Uniqueness And Ownership History

- Context: Frontend QA found that app update/reinstall can register a new FCM token while an old token remains active, and frontend-only cleanup cannot cover reinstall or cleared local storage cases.
- Decision: Backend FCM token registration is the source of truth for active-token integrity. Active `userId + clientInstanceId` is unique, active `token` is unique, and inactive rows may remain as history. Re-registering the same active `userId + clientInstanceId + token` returns the same row after refreshing metadata. Registering a new token for the same user/client deactivates the old active row before saving the new row. Registering a token that is active under another user/client deactivates the previous ownership row before saving the current ownership row. Logout continues to deactivate the current device token by `clientInstanceId` or `fcmToken` when provided.
- Impact: FCM token schema should use active-only uniqueness rather than global token uniqueness. REST Docs response includes the registered `token`, and tests must cover same-device token rotation, cross-user token ownership transfer, same-token idempotency, and logout deactivation.

### 2026-06-17 - Auth Refresh Logout Contract For Issue 28

- Context: Issue #28 needed clarification before development so Codex would not guess the refresh/logout request contracts, session rotation behavior, FCM dependency boundary, or REST Docs expectations.
- Decision: `POST /api/v1/auth/refresh` receives `refreshToken` in the JSON request body and returns the same token response shape as login. Refresh rotation keeps the same `sessionId` and replaces the refresh token identifier. `POST /api/v1/auth/logout` requires `Authorization: Bearer {accessToken}` and accepts optional body fields `refreshToken`, `clientInstanceId`, and `fcmToken`; logout must still succeed without FCM fields. Issue #28 should not implement Notification entities directly. It may define and call an application port for current-device FCM deactivation, while #40 owns the actual `user_fcm_tokens` persistence implementation. New/changed auth APIs should add Spring REST Docs tests.
- Impact: Issue #28, Notion auth API pages, backend policy, and the Codex hook must align on this contract before the development session starts. Tests must cover refresh rotation, old refresh token reuse, logout blacklist/allowlist deletion, optional FCM fields, no raw token storage, Redis TTLs, and REST Docs snippets.

## Issue #189 Approved Decisions

### 2026-07-13 - Issue #189 Meal Duty Poll Post-settlement Contract

- Context: Issue #189 introduced a MEAL workflow separate from COFFEE, and the user approved the recommended DTO and visibility details needed to implement the issue without inventing missing API fields.
- Decision: A campus may have unlimited ACTIVE MEAL duty assignments and assigning the same active member is idempotent. MEAL operational APIs require the requester to be an ACTIVE member and ACTIVE MEAL duty even when the requester is a service ADMIN or campus manager. A MEAL account create request contains only `nickname`, `bankName`, `accountNumber`, and `accountHolder`; the server fixes category to MEAL and owner to the requester. A MEAL poll create request contains `title`, `isAnonymous`, `allowUserOptionAdd`, future `endsAt`, and `options[{content,sortOrder}]`; account, amount, `startsAt`, category, generation, and price fields are rejected with 400. Other-campus and non-MEAL resources are hidden as 404 after the MEAL duty access boundary, while a same-campus requester without ACTIVE MEAL duty receives 403.
- Decision: The server uses one `Instant` for MEAL poll `startsAt` and `createdAt`, opens it immediately, and preserves the existing `optionIds`/`poll_response_options` response contract and #97 user-added option contract. Closing creates zero settlement/charge rows. One later poll-level request chooses exactly one requester-owned ACTIVE MEAL account and includes every responded option once. `PER_MEMBER` and `GROUP_TOTAL` use integer exact arithmetic; GROUP_TOTAL uses ceiling division and stores requested, actual, and rounding adjustment separately. Settlement/group/charge persistence is one transaction and a poll may be settled once.
- Decision: Another MEAL duty may see charged status, counts, and calculation snapshots but never another duty's account ID/details. `/meal/charges/my-accounts` includes only MEAL charges connected to accounts owned by the requester. Existing generic admin account/charge APIs exclude MEAL data so this non-exposure boundary cannot be bypassed.
- Impact: V8 adds MEAL enum/check support, active MEAL duty/account partial uniqueness, and normalized `meal_poll_settlements` / `meal_poll_charge_groups` tables without modifying V1-V7 or operational data. Existing COFFEE, PENALTY, and non-MEAL Poll semantics remain unchanged.

### 2026-07-13 - Issue #189 Feature Docker QA Deferred To Integration

- Context: The user changed the validation sequence after implementation began.
- Decision: Do not run Docker build/up/API QA in the #189 feature worktree. Complete focused/full Gradle tests, build, asciidoctor, REST Docs, `git diff --check`, repository docs, Obsidian, and PM review in the feature branch. After #188/#189/#190 all receive PM approval, merge them only into `integration/188-190-devotion-meal-billing` created from latest `origin/develop`, then run the single isolated PostgreSQL/Redis/backend Docker health and connected HTTP QA there.
- Impact: Docker QA absence is an explicit user-approved deferral, not a hidden omission or feature failure. No Docker command was started in the #189 session. PM approval remains required before push, PR, or merge.

## Pending Decisions

### 2026-06-17 - Prayer Request Meeting Status Storage Scope

- Context: Prayer request writing remains available even when there is no meeting, so meeting status must be separated from whether a prayer request can be written. The remaining unresolved decision is only where, if anywhere, meeting status should be stored.
- Pending question: Should `NO_MEETING` or an equivalent meeting status be stored at the whole prayer week level, at the group-week level, both, or omitted from MVP?
- Recommendation: If FaithLog only needs to display campus-wide off-weeks, keep `NO_MEETING` on `prayer_weeks`. If individual prayer groups can skip independently and the app must display that, add a group-week status model. If the app does not need to display meeting status, omit `NO_MEETING` from MVP and rely on nullable content plus written/not-written status.
- Current action: Prayer request writing availability and no-deadline status policy are approved; exact meeting status storage scope must be confirmed before schema implementation.

### 2026-06-17 - Daily Health And Response-Time Measurement Scope

- Context: The codebase exposes `GET /api/v1/health`, and the daily monitor can record health/latency metrics only if the measurement target and runtime are user-approved.
- Pending question: For daily monitoring, should Codex measure health/response time against a local booted app, a deployed environment URL, or leave health/latency as manual-only until the user defines the target?
- Recommendation: Approve one source of truth for daily health metrics first so the monitor can report comparable numbers instead of mixed local/deploy timings.
- Current action: Today's report records that the endpoint exists but does not publish latency or availability percentages.

### 2026-06-16 - Poll Comment Issue Split

- Context: Poll comments are now MVP scope. Issue #38 contains the PollComment implementation scope.
- Pending question: Should PollComment stay inside #38, or should a separate `[Feat] 투표 댓글 구현` issue be created?
- Current action: No new issue was created because the user said to create it only if needed, and #38 now contains the required implementation scope.

### 2026-06-16 - Project Board Domain Fields For Mixed Domains

- Context: Project Board items #23 and #24 have mixed domain text in their issue bodies, but the board Domain field is single-select.
- Pending question: Should #23 use `global` or another convention for `global/domain-admin`, and should #24 use `global` or `notification` for `global/notification`?
- Current action: Domain was left blank for #23 and #24 to avoid guessing.

### 2026-06-16 - Obsidian FaithLog Folder Path

- Context: The current Hook uses `/Users/josephuk77/obsidian/obsidian-writing-vault/Projects/FaithLog/`, while one attached requirement may imply `04_Projects/FaithLog`.
- Pending question: Should FaithLog Obsidian notes live under `Projects/FaithLog` or `04_Projects/FaithLog`?
- Current action: The path was not changed to avoid guessing the user's vault structure.

<!-- daily-resume-monitor:start:decision-log:2026-06-16 -->
### 2026-06-16 - Daily Resume Monitor Transcript Source

- Context: The daily resume monitor can only use Codex or assistant transcripts when their source/location is explicitly available and verifiable.
- Pending question: Where should the daily monitor read Codex conversation transcripts from, if transcript context should be included?
- Recommendation: Provide one stable local transcript source path or leave transcript analysis disabled.
- Current action: No transcript source was provided, so conversation transcripts were not inspected.
<!-- daily-resume-monitor:end:decision-log:2026-06-16 -->
