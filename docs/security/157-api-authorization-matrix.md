# Issue #157 API 권한 행렬

## 1. 판정 기준과 기호

기준 커밋은 `d9d9f2506ddf50ce0a36d6ceada3b0768cfc8c81`이다. Spring Security는
4개 application endpoint를 `permitAll`로 두고 나머지 mapping에 인증을 요구한다. 인증 뒤의
세부 권한은 Controller 경로명이 아니라 각 service의 active user, campus membership, 역할,
duty, owner guard로 판정한다.

- `P`: public, 인증 불필요
- `AU`: active authenticated user
- `AM`: 해당 campus의 ACTIVE member
- `CM`: 해당 campus의 ACTIVE `MINISTER`, `ELDER`, `CAMPUS_LEADER`
- `SA`: active service `ADMIN`
- `CD`: 해당 campus의 active `DutyType.COFFEE` 담당자이면서 ACTIVE member
- `SELF`: 인증 principal의 user ID로만 처리
- `OWNER`: 자원의 user/owner ID가 인증 principal과 일치
- `AUTHOR`: 댓글 작성자
- `GROUP`: 현재 active prayer season에서 요청자와 같은 active prayer group

서비스 `MANAGER`는 campus 생성 권한 외에는 campus 관리자 권한이 아니다. 따라서 아래 표에서
`CM`은 `users.role=MANAGER`를 뜻하지 않는다. `SA`는 대부분 campus membership 없이 전역
override가 가능하다.

## 2. public/authenticated 경계

| 분류 | endpoint 수 | endpoint |
| --- | ---: | --- |
| public application API | 4 | `GET /api/v1/health`, `POST /api/v1/auth/signup`, `POST /api/v1/auth/login`, `POST /api/v1/auth/refresh` |
| authenticated application API | 76 | 아래 endpoint 행렬의 나머지 전체 |
| public framework surface | 4 pattern | `/actuator/health`, `/swagger-ui.html`, `/swagger-ui/**`, `/api-docs/**` |

prod example은 Swagger/API docs를 기본 비활성화한다. framework surface의 실제 운영 노출 여부는
Cloud Run 환경변수 확인이 필요하다.

## 3. service role 행렬

`USER`와 `MANAGER`가 아래에서 `조건부`인 경우 campus role/duty/owner 조건을 추가로 충족해야 한다.

| 기능 | USER | MANAGER | ADMIN | 실제 판정 |
| --- | --- | --- | --- | --- |
| 회원가입/로그인/refresh | P | P | P | credential/refresh token 검증 |
| 본인 조회/로그아웃/탈퇴/FCM | SELF | SELF | SELF | active JWT와 본인 식별자 |
| campus 생성 | 불가 | 허용 | 허용 | `canCreateCampus()` |
| campus 가입 | 허용 | 허용 | 허용 | active user + invite code |
| campus member 기능 | 조건부 | 조건부 | 전역 허용 | ACTIVE membership; MANAGER 자체는 추가 권한 없음 |
| service 사용자/전체 campus 관리 | 불가 | 불가 | 허용 | `AdminAccessPolicy.requireServiceAdmin` |
| campus admin 도메인 기능 | 조건부 | 조건부 | 전역 허용 | CM 또는 도메인별 CD 예외 |
| 본인 경건/청구/기도/투표 | 조건부 | 조건부 | 도메인별 전역/본인 | AM 또는 SELF/campus scope |

## 4. campus role과 COFFEE duty 행렬

| 기능 | MINISTER | ELDER | CAMPUS_LEADER | MEMBER | active COFFEE duty만 보유 | service ADMIN |
| --- | --- | --- | --- | --- | --- | --- |
| campus 상세/계좌/투표/기도 읽기 | 허용 | 허용 | 허용 | 허용 | AM이면 허용 | 전역 허용 |
| campus 수정/멤버 삭제 | 허용 | 허용 | 허용 | 불가 | 불가 | 전역 허용 |
| campus role 변경 | 동급 이하 | 동급 이하 | 동급 이하 | 불가 | 불가 | 전역 허용 |
| duty 지정/해제 | 허용 | 허용 | 허용 | 불가 | 불가 | 전역 허용 |
| PENALTY 계좌/청구 관리 | 허용 | 허용 | 허용 | 불가 | 불가 | 전역 허용 |
| 자기 소유 COFFEE 계좌 관리 | 허용 | 허용 | 허용 | 담당자면 허용 | 허용 | 전역 허용 |
| 일반 Poll/template 관리 | 허용 | 허용 | 허용 | 불가 | 불가 | 전역 허용 |
| COFFEE Poll/template 생성·수정 | 허용 | 허용 | 허용 | 담당자면 허용 | 허용 | 전역 허용 |
| COFFEE 청구 조회 | 허용 | 허용 | 허용 | 담당자+owner 범위 | owner 범위 | 전역 허용 |
| 경건 미제출/기도 관리/알림 관리 | 허용 | 허용 | 허용 | 불가 | 불가 | 전역 허용 |
| 투표 댓글 수정/삭제 | 작성자 또는 관리자 | 작성자 또는 관리자 | 작성자 또는 관리자 | 작성자만 | 작성자만 | 전역 관리자 |

## 5. endpoint별 권한 행렬

### 5.1 Global, Auth, User — 7

| # | Method/path | 허용식 | 객체 범위/guard |
| ---: | --- | --- | --- |
| 1 | `GET /api/v1/health` | P | 상태 문자열만 반환 |
| 2 | `POST /api/v1/auth/signup` | P | email unique, password BCrypt |
| 3 | `POST /api/v1/auth/login` | P | active user + password |
| 4 | `POST /api/v1/auth/refresh` | P | refresh signature/type + Redis current `userId/sessionId/refreshJti` |
| 5 | `POST /api/v1/auth/logout` | AU+SELF | access JTI blacklist, current session refresh 삭제, 선택 기기 FCM은 user 범위 |
| 6 | `GET /api/v1/users/me` | AU+SELF | principal user ID 고정 |
| 7 | `DELETE /api/v1/users/me` | AU+SELF | password+확인문구; 마지막 ADMIN guard 누락은 finding F-157-01 |

### 5.2 Campus — 12

| # | Method/path | 허용식 | 객체 범위/guard |
| ---: | --- | --- | --- |
| 8 | `POST /api/v1/campuses` | service MANAGER 또는 SA | active user + `canCreateCampus` |
| 9 | `POST /api/v1/campuses/join` | AU | invite code로 campus 도출, 기존 membership 재활성화 |
| 10 | `GET /api/v1/campuses/me` | AU+SELF | 본인의 ACTIVE membership만 |
| 11 | `GET /api/v1/campuses/{campusId}` | AM 또는 SA | MEMBER 응답은 inviteCode 숨김 |
| 12 | `GET /api/v1/campuses/{campusId}/duty-assignments/me` | AM+SELF | 같은 campus ACTIVE membership |
| 13 | `PATCH /api/v1/campuses/{campusId}` | CM 또는 SA | path campus를 직접 조회 후 권한 판정 |
| 14 | `DELETE /api/v1/campuses/{campusId}/members/{membershipId}` | CM 또는 SA | `campusId + membershipId` 결합 조회 |
| 15 | `GET /api/v1/admin/campuses/{campusId}/members` | CM 또는 SA | ACTIVE members만 |
| 16 | `PATCH /api/v1/admin/campuses/{campusId}/members/{campusMemberId}/campus-role` | CM 계층 또는 SA | 같은 campus active target, 동급 이하; target tokenVersion 증가 |
| 17 | `GET /api/v1/admin/campuses/{campusId}/duty-assignments` | CM 또는 SA | 같은 campus active assignments |
| 18 | `PUT /api/v1/admin/campuses/{campusId}/duty-assignments/coffee` | CM 또는 SA | 같은 campus ACTIVE target member |
| 19 | `DELETE /api/v1/admin/campuses/{campusId}/duty-assignments/coffee/{assignmentId}` | CM 또는 SA | `campusId + COFFEE + assignmentId + active` |

### 5.3 Service Admin/Dashboard — 6

| # | Method/path | 허용식 | 객체 범위/guard |
| ---: | --- | --- | --- |
| 20 | `GET /api/v1/admin/users` | SA | active service ADMIN requester |
| 21 | `GET /api/v1/admin/users/{userId}` | SA | target user 직접 조회 |
| 22 | `PATCH /api/v1/admin/users/{userId}/role` | SA | 마지막 active ADMIN 강등 차단, role 변경 시 tokenVersion 증가 |
| 23 | `GET /api/v1/admin/campuses` | SA | 전체 campus 검색 |
| 24 | `POST /api/v1/admin/campuses/{campusId}/members` | SA | 같은 campus, active target user, 중복 membership 처리 |
| 25 | `GET /api/v1/admin/campuses/{campusId}/dashboard/summary` | CM 또는 SA | 같은 campus aggregate |

### 5.4 Billing — 13

| # | Method/path | 허용식 | 객체 범위/guard |
| ---: | --- | --- | --- |
| 26 | `GET /api/v1/campuses/{campusId}/payment-accounts` | AM 또는 SA | 같은 campus active accounts, member DTO |
| 27 | `GET /api/v1/campuses/{campusId}/charges/me` | AM+SELF | campus+principal user 고정 |
| 28 | `GET /api/v1/campuses/{campusId}/charges/me/summary` | AM+SELF | campus+principal user+month |
| 29 | `PATCH /api/v1/campuses/{campusId}/charges/me/{chargeItemId}/paid` | AM+SELF+OWNER | charge user와 requester 일치, charge campus와 path 일치 |
| 30 | `POST /api/v1/admin/campuses/{campusId}/payment-accounts` | PENALTY: CM/SA, COFFEE: CM/CD/SA | COFFEE owner는 requester, campus/type scope |
| 31 | `PATCH /api/v1/admin/payment-accounts/{accountId}/deactivate` | PENALTY: CM/SA, COFFEE: CM/CD owner/SA | account에서 campus 도출, non-SA COFFEE owner 검사 |
| 32 | `GET /api/v1/admin/campuses/{campusId}/payment-accounts` | CM/SA 또는 CD owner COFFEE | duty는 active own COFFEE만 |
| 33 | `PATCH /api/v1/admin/campuses/{campusId}/payment-accounts/{paymentAccountId}/activate` | CM 또는 SA | same-campus non-deleted PENALTY만 |
| 34 | `DELETE /api/v1/admin/campuses/{campusId}/payment-accounts/{paymentAccountId}` | PENALTY: CM/SA, COFFEE: CM/CD owner/SA | same-campus, inactive, non-deleted |
| 35 | `PATCH /api/v1/admin/charges/{chargeItemId}/status` | CM 또는 SA | charge에서 campus 도출; duty-only 예외 없음 |
| 36 | `GET /api/v1/admin/campuses/{campusId}/charges` | CM/SA 또는 CD COFFEE owner 범위 | paymentAccount campus/type/owner 필터 |
| 37 | `GET /api/v1/admin/campuses/{campusId}/charges/my-accounts` | CM/SA 또는 CD COFFEE owner 범위 | manager PENALTY + requester-owned COFFEE |
| 38 | `GET /api/v1/admin/campuses/{campusId}/members/{userId}/charges` | CM/SA; CD는 명시 COFFEE일 때 | target ACTIVE member, campus+category 범위 |

### 5.5 Devotion — 8

| # | Method/path | 허용식 | 객체 범위/guard |
| ---: | --- | --- | --- |
| 39 | `PUT /api/v1/campuses/{campusId}/devotions/me/days/{recordDate}` | AM+SELF | principal user 고정, campus ACTIVE membership |
| 40 | `PUT /api/v1/campuses/{campusId}/devotions/me/weeks/{weekStartDate}` | AM+SELF | principal user, Monday/week 범위 |
| 41 | `GET /api/v1/campuses/{campusId}/devotions/me/weeks/{weekStartDate}` | AM+SELF | principal user 고정 |
| 42 | `GET /api/v1/campuses/{campusId}/devotions/me/monthly-summary` | AM+SELF | principal user+calendar month |
| 43 | `GET /api/v1/admin/campuses/{campusId}/devotions/missing` | CM 또는 SA | 같은 campus ACTIVE members |
| 44 | `GET /api/v1/campuses/{campusId}/penalty-rules` | AM 또는 SA | 같은 campus rules |
| 45 | `POST /api/v1/admin/campuses/{campusId}/penalty-rules` | CM 또는 SA | same campus lock/active replacement |
| 46 | `PATCH /api/v1/admin/penalty-rules/{ruleId}` | rule campus의 CM 또는 SA | rule에서 campus 도출 |

### 5.6 Poll/Coffee Catalog — 19

| # | Method/path | 허용식 | 객체 범위/guard |
| ---: | --- | --- | --- |
| 47 | `GET /api/v1/campuses/{campusId}/polls` | AM 또는 SA | campus list + 3일/7일 visibility |
| 48 | `GET /api/v1/campuses/{campusId}/polls/{pollId}` | AM 또는 SA | `poll.campusId == path`, visibility |
| 49 | `PUT /api/v1/campuses/{campusId}/polls/{pollId}/responses/me` | AM+SELF | same poll/campus, OPEN, option belongs to poll |
| 50 | `POST /api/v1/campuses/{campusId}/polls/{pollId}/options` | AM+SELF | same poll/campus, OPEN, option-add enabled |
| 51 | `GET /api/v1/campuses/{campusId}/polls/{pollId}/results` | AM 또는 SA | same poll/campus/visibility; anonymous respondent 숨김 |
| 52 | `GET /api/v1/campuses/{campusId}/polls/{pollId}/comments` | AM 또는 SA | same poll/campus/visibility |
| 53 | `POST /api/v1/campuses/{campusId}/polls/{pollId}/comments` | AM | same poll/campus, OPEN |
| 54 | `PATCH /api/v1/campuses/{campusId}/polls/{pollId}/comments/{commentId}` | AUTHOR 또는 CM/SA | comment belongs to poll, OPEN |
| 55 | `DELETE /api/v1/campuses/{campusId}/polls/{pollId}/comments/{commentId}` | AUTHOR 또는 CM/SA | comment belongs to poll, OPEN |
| 56 | `POST /api/v1/admin/campuses/{campusId}/polls` | 일반: CM/SA, COFFEE: CM/CD/SA | template/poll campus, COFFEE account active+same campus+owner |
| 57 | `GET /api/v1/admin/campuses/{campusId}/polls/{pollId}/missing-members` | 일반: CM/SA, COFFEE: CM/CD/SA | same poll/campus |
| 58 | `PATCH /api/v1/admin/campuses/{campusId}/polls/{pollId}/close` | 일반: CM/SA, COFFEE: CM/CD/SA | same poll/campus, OPEN |
| 59 | `GET /api/v1/admin/campuses/{campusId}/poll-templates` | CM 또는 SA | active same-campus templates; duty-only 조회 예외 없음 |
| 60 | `GET /api/v1/admin/campuses/{campusId}/poll-templates/{templateId}` | CM 또는 SA | same-campus template |
| 61 | `POST /api/v1/admin/campuses/{campusId}/poll-templates` | 일반: CM/SA, COFFEE: CM/CD/SA | COFFEE account active+same campus+owner |
| 62 | `PATCH /api/v1/admin/campuses/{campusId}/poll-templates/{templateId}` | 일반: CM/SA, COFFEE: CM/CD/SA | same-campus template/account |
| 63 | `DELETE /api/v1/admin/campuses/{campusId}/poll-templates/{templateId}` | CM 또는 SA | same-campus template; 현재 구현은 duty-only 예외 없음 |
| 64 | `GET /api/v1/coffee-brands` | AU | active catalog만, campus 데이터 없음 |
| 65 | `GET /api/v1/coffee-brands/{brandId}/menus` | AU | active brand와 active menus |

### 5.7 Prayer — 11

| # | Method/path | 허용식 | 객체 범위/guard |
| ---: | --- | --- | --- |
| 66 | `GET /api/v1/campuses/{campusId}/prayers/weeks/{weekStartDate}` | AM 또는 SA | current season in path campus |
| 67 | `PUT /api/v1/campuses/{campusId}/prayers/weeks/{weekStartDate}/submissions` | CM/SA 또는 GROUP | target들이 current season active group에 속함; normal은 자기 group 전체만 |
| 68 | `PUT /api/v1/campuses/{campusId}/prayers/weeks/{weekStartDate}/me` | AM+SELF+GROUP | principal의 current active group assignment |
| 69 | `POST /api/v1/admin/campuses/{campusId}/prayer-seasons` | CM 또는 SA | path campus manager |
| 70 | `GET /api/v1/admin/campuses/{campusId}/prayer-seasons/current` | CM 또는 SA | path campus manager |
| 71 | `PATCH /api/v1/admin/prayer-seasons/{seasonId}/close` | season campus의 CM 또는 SA | season에서 campus 도출 |
| 72 | `POST /api/v1/admin/prayer-seasons/{seasonId}/groups` | season campus의 CM 또는 SA | season에서 campus 도출 |
| 73 | `GET /api/v1/admin/prayer-seasons/{seasonId}/groups` | season campus의 CM 또는 SA | season에서 campus 도출 |
| 74 | `GET /api/v1/admin/prayer-seasons/{seasonId}/members/assignable` | season campus의 CM 또는 SA | 같은 campus ACTIVE members |
| 75 | `PATCH /api/v1/admin/prayer-groups/{groupId}` | group→season campus의 CM 또는 SA | 연쇄 조회로 campus 도출 |
| 76 | `PUT /api/v1/admin/prayer-groups/{groupId}/members` | group→season campus의 CM 또는 SA | target 같은 campus ACTIVE member, same-season 중복 차단 |

### 5.8 FCM/Notification — 4

| # | Method/path | 허용식 | 객체 범위/guard |
| ---: | --- | --- | --- |
| 77 | `POST /api/v1/users/me/fcm-tokens` | AU+SELF | command user ID는 principal, active ownership 이전/upsert |
| 78 | `DELETE /api/v1/users/me/fcm-tokens/{tokenId}` | AU+SELF+OWNER | repository `tokenId + userId` |
| 79 | `POST /api/v1/admin/campuses/{campusId}/notifications` | CM 또는 SA | target은 같은 campus ACTIVE members로 제한, manual Redis lock |
| 80 | `GET /api/v1/admin/campuses/{campusId}/notification-logs` | CM 또는 SA | criteria에 path campus predicate 강제 |

## 6. `permitAll` 대조

| SecurityConfig matcher | Controller mapping | 결과 |
| --- | --- | --- |
| `GET /api/v1/health` | `HealthController.health` | 일치 |
| `POST /api/v1/auth/signup` | `AuthController.signup` | 일치 |
| `POST /api/v1/auth/login` | `AuthController.login` | 일치 |
| `POST /api/v1/auth/refresh` | `AuthController.refresh` | 일치 |

`logout`, `/users/me`, coffee catalog을 포함한 나머지 mapping은 `anyRequest().authenticated()`에
포함된다. Controller에 `@PreAuthorize`는 없으며, 모든 세부 권한은 service에 있다. 따라서 새
Controller/use-case 추가 시 service guard 누락을 탐지하는 endpoint-to-policy 회귀 검사가 중요하다.

## 7. 핵심 BOLA/BFLA 대조 결과

- `membershipId`, `campusMemberId`, `assignmentId`: path campus와 결합 조회함.
- `pollId`, `templateId`, `commentId`, `optionIds`: parent campus/poll 소속을 다시 검사함.
- `paymentAccountId`, `chargeItemId`: campus, authenticated owner, account type/status를 다시 검사함.
- `seasonId`, `groupId`: season/group에서 campus를 도출한 뒤 manager guard를 적용함.
- `tokenId`: authenticated user ID와 복합 조회함.
- 본인 경건/청구/FCM API: request body/path의 다른 `userId`를 신뢰하지 않고 principal을 사용함.
- service ADMIN 기능: `AdminAccessPolicy` 또는 동등한 active ADMIN guard를 사용함.
- 예외: 본인 탈퇴는 합법적 SELF 동작이지만 마지막 active service ADMIN 불변조건을 검사하지 않아
  `docs/security/157-audit-findings.md`의 F-157-01로 확정했다.
