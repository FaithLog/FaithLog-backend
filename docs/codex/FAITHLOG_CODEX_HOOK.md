# FaithLog Codex Hook

이 문서는 FaithLog 백엔드 프로젝트에서 Codex가 반드시 따라야 하는 개발 규칙이다.

Codex는 이 문서를 프로젝트의 개발 Hook으로 간주하고, 모든 이슈 작업 시작 전/중/후에 아래 규칙을 따른다.

## 0. 최우선 의사결정 규칙

1. 사용자가 이 프로젝트의 유일한 의사결정권자다.
2. Codex는 애매한 요구사항, 이상한 설계, 위험한 변경, 명시적으로 승인되지 않은 구현을 추측으로 진행하지 않는다.
3. 결정이 필요한 경우 무엇이 애매한지, 왜 중요한지, 가능한 선택지, 추천안을 정리하고 사용자 확인을 받는다.
4. 중요한 결정은 `docs/decision-log.md`에 기록한다.
5. 충돌이 있으면 `AGENTS.md`의 문서 우선순위를 따른다. 현재 대화에서 사용자가 명시한 최신 결정과 `docs/decision-log.md`의 사용자 승인 결정은 Notion 문서보다 우선한다.

## 1. 작업 시작 전 규칙

1. 현재 작업 이슈 번호와 이슈 본문을 먼저 확인한다.
2. 가능하면 GitHub Projects 칸반보드에서 해당 Issue 카드가 있는지 확인한다.
3. 카드가 있으면 현재 상태를 확인하고, 작업 시작 시 상태를 `In Progress` 또는 보드에서 사용하는 진행 상태로 이동한다.
4. `AGENTS.md`의 문서 우선순위에 따라 현재 사용자 결정, `docs/decision-log.md`, Notion 최종 기획서/ERD/API 기준, 이 문서, `docs/backend-implementation-policy.md` 순으로 확인한다.
5. Notion 문서에 접근할 수 없으면 이 문서와 `docs/backend-implementation-policy.md`에 명시된 FaithLog 최종 설계 기준을 따른다.
6. Notion 접근 불가 또는 설계 확인 불가 사항은 최종 보고에 남긴다.
7. 작업 범위는 해당 이슈에 포함된 내용으로 제한한다.
8. `main`, `master`, `develop` 브랜치에서 직접 작업하지 않는다.
9. 브랜치명은 아래 형식을 따른다.

```text
feat/{issueNumber}-{summary}
fix/{issueNumber}-{summary}
chore/{summary}
docs/{issueNumber}-{summary}
```

10. 오류가 없는 코드만 만든다.
11. 작업 전 현재 테스트 실행 가능 여부를 확인한다.
12. 기능 코드 변경 전에는 관련 테스트 위치를 먼저 확인한다.

## 2. GitHub Issue 및 칸반보드 작업 규칙

1. 모든 작업은 가능한 경우 GitHub Issue 기준으로 진행한다.
2. Issue 없이 임의로 큰 작업을 진행하지 않는다.
3. 기존 Issue가 있으면 중복 생성하지 않는다.
4. GitHub Projects 보드에 해당 Issue 카드가 있으면 작업 상태를 확인한다.
5. 작업 시작 시 카드 상태를 `In Progress` 또는 보드의 진행 중 상태로 변경한다.
6. 작업 완료 후 카드 상태를 `Done`, `Review`, `Ready for Review`, `Code Review` 중 보드에서 사용하는 적절한 상태로 변경한다.
7. 카드 상태 변경 권한이 없으면 최종 보고에 남긴다.
8. GitHub Projects 보드를 찾을 수 없으면 임의로 새 보드를 만들지 않는다.
9. GitHub Projects 접근이 불가능한 경우에도 Issue 또는 `docs/issues/` 대체 문서를 기준으로 작업을 진행한다.
10. Issue 본문에 수동 상태 줄을 쓰지 않는다. Project Board Status를 상태의 진실 원천으로 사용한다.
11. 최종 보고에는 Issue 번호, 카드 생성/연결 여부, 카드 상태 변경 여부를 반드시 포함한다.

## 2.1 커밋 메시지 Hook 규칙

1. 저장소의 `core.hooksPath`는 `.githooks`를 사용한다.
2. `.githooks/commit-msg`는 버전 관리 대상이며 실행 권한을 유지한다.
3. 커밋 제목은 `<분류>: #<이슈번호> <한국어 작업 요약>` 형식을 따른다.
4. 허용 분류는 `feat`, `fix`, `test`, `refactor`, `docs`, `chore`, `build`, `style`, `release`이다.
5. 커밋 제목에는 `#숫자` 이슈 번호와 한글 작업 요약이 반드시 포함되어야 한다.

## 3. FaithLog 최종 설계 기준

### 3.1 운영 단위

- 운영 단위는 `campus_id`이다.
- `app_groups` 구조는 사용하지 않는다.
- 주요 데이터는 모두 `campus_id` 기준으로 연결한다.

### 3.2 인증 기준

- Refresh Token은 DB가 아니라 Redis allowlist 방식으로 관리한다.
- Access Token은 JWT stateless 구조를 유지하되, 로그아웃 즉시 무효화를 위해 Redis blacklist/denylist를 사용한다.
- MVP에서는 role 변경 후 이미 발급된 Access Token을 즉시 무효화하지 않는다.
- 이미 발급된 Access Token은 기존 30분 TTL까지 유효할 수 있다.
- Refresh Token 재발급 시점에는 DB에 저장된 최신 사용자 role 기준으로 새 Access Token을 발급한다.
- role 변경 즉시 기존 Access Token을 무효화하는 정책은 tokenVersion, 세션 무효화, Redis blacklist/session 확장 등 영향 범위가 커서 Issue #76 보안 강화 작업으로 분리한다.
- Access Token이 refresh endpoint를 통해 재발급될 때마다 Refresh Token도 반드시 새로 발급한다.
- Refresh Token Rotation을 적용한다.
- Redis에는 원본 token을 저장하지 않고 hash 또는 token identifier 기준으로 저장한다.
- Access Token에는 `jti`, `userId`, `role`, `sessionId`를 포함한다.
- Refresh Token에는 `userId`, `sessionId`, `refreshJti`를 포함한다.
- Refresh Token Rotation 시 `sessionId`는 유지하고 refresh token identifier를 교체한다.
- `POST /api/v1/auth/refresh`는 JSON request body의 `refreshToken`을 받는다.
- `POST /api/v1/auth/logout`은 `Authorization: Bearer {accessToken}`을 필수로 사용하고, JSON request body의 `refreshToken`, `clientInstanceId`, `fcmToken`은 선택 입력으로 받는다.
- 로그아웃은 `clientInstanceId`와 `fcmToken`이 없어도 인증 토큰 무효화는 성공해야 한다.
- #28은 Notification Entity를 직접 구현하거나 조작하지 않는다. 현재 기기 FCM 토큰 비활성화는 Application port로 분리하고, 실제 `user_fcm_tokens` 저장소 구현은 #40 범위로 둔다.

기준 API:

```text
POST /api/v1/auth/refresh
POST /api/v1/auth/logout
```

사용하지 않는 API:

```text
POST /api/v1/auth/reissue
```

### 3.3 경건생활 기준

- 경건생활 원본은 `devotion_daily_checks`이다.
- `weekly_devotion_records`는 주간 요약 및 벌금 계산용이다.
- 하루 경건생활 체크 API는 해당 날짜의 daily row를 생성 또는 수정하고, 해당 주차의 weekly row가 없으면 함께 생성한다.
- 하루 체크만으로는 `submitted_at`을 변경하지 않고 벌금 계산이나 `PENALTY` 청구 생성/갱신을 수행하지 않는다.
- 단, 해당 주차의 `weekly_devotion_records.submitted_at`이 이미 있으면 하루 체크 API도 `DEVOTION_WEEKLY_ALREADY_SUBMITTED`로 실패하며 weekly/daily/charge row를 생성하거나 수정하지 않는다.
- 주간 경건생활 제출 API는 월요일부터 일요일까지 7일치 daily row를 생성 또는 수정한다.
- 주간 저장/제출 요청 필드명은 `dailyChecks`를 사용한다.
- 주간 제출 시 요청에 없는 날짜는 false 기본값으로 채운다.
- 주간 경건생활 최종 제출은 1회만 가능하다. 같은 캠퍼스/사용자/주차의 `weekly_devotion_records.submitted_at`이 이미 있으면 다시 `submit = true`로 제출할 수 없다.
- `submit = false` 주간 저장은 최종 제출 전까지만 가능하고 벌금 계산이나 `PENALTY` 청구 생성/갱신을 수행하지 않는다.
- 경건생활 제출 여부와 관리자 미제출자 조회 기준은 daily row 존재 여부가 아니라 `weekly_devotion_records.submitted_at`이다.
- `weekStartDate`는 월요일이어야 한다.
- 내 월간 경건생활 통계 조회는 Notion `10.5` 기준 API `GET /api/v1/campuses/{campusId}/devotions/me/monthly-summary?year={year}&month={month}`를 사용한다.
- 월간 통계 응답은 현재 로그인한 사용자 본인 기준이며, ACTIVE 캠퍼스 멤버십을 검증한 뒤 선택 월의 첫날부터 마지막 날까지 `devotion_daily_checks.record_date` 기준으로 월간 `devotion` 합계를 계산한다.
- `weeklyRecords[]`는 선택 월에 포함되는 일별 체크를 주차별로 묶어 반환하며, 월 경계에 걸친 주차는 선택 월에 해당하는 날짜만 부분 집계될 수 있다.
- `SATURDAY_LATE` 지각 시간은 해당 주차의 토요일 날짜가 선택 월에 포함될 때 그 월 통계에 포함한다.

기준 API:

```text
PUT /api/v1/campuses/{campusId}/devotions/me/days/{recordDate}
PUT /api/v1/campuses/{campusId}/devotions/me/weeks/{weekStartDate}
GET /api/v1/campuses/{campusId}/devotions/me/weeks/{weekStartDate}
GET /api/v1/campuses/{campusId}/devotions/me/monthly-summary?year={year}&month={month}
GET /api/v1/admin/campuses/{campusId}/devotions/missing?weekStartDate={weekStartDate}
```

### 3.4 벌금 청구 기준

주간 경건생활 첫 최종 제출 시 서버가 자동으로 벌금을 계산하고 청구를 생성한다.

관리자가 별도로 벌금 청구 생성을 요청하는 API는 MVP에서 제공하지 않는다.

벌금 청구 기준:

```text
paymentCategory = PENALTY
sourceType = DEVOTION_RECORD
sourceId = weekly_devotion_records.id
status = UNPAID
```

중복 방지 기준:

```text
(campus_id, user_id, payment_category, source_type, source_id) unique
```

### 3.5 계좌 기준

관리자는 캠퍼스별 납부 계좌를 미리 등록한다.

Issue #34 계좌 관리 API 기준:

```text
GET   /api/v1/campuses/{campusId}/payment-accounts
POST  /api/v1/admin/campuses/{campusId}/payment-accounts
PATCH /api/v1/admin/payment-accounts/{accountId}/deactivate
```

계좌 조회는 해당 캠퍼스의 모든 ACTIVE 멤버가 사용할 수 있다.

계좌 등록과 비활성화는 캠퍼스 관리자 권한이 필요하다.

캠퍼스별 활성 계좌는 `account_type`별로 1개만 허용한다.

새 계좌를 활성으로 등록하면 같은 캠퍼스와 같은 `account_type`의 기존 활성 계좌는 자동 비활성화하고, 새 계좌만 활성 상태로 둔다.

계좌 조회 응답은 납부에 필요하므로 계좌번호를 전체 노출한다. 단, 일반 멤버 조회 응답에는 관리용 정보가 필요 이상으로 노출되지 않게 한다.

계좌는 기존 미납 청구가 있어도 비활성화할 수 있다. 새 활성 계좌가 등록되면 기존 `UNPAID` 청구는 새 활성 계좌로 재연결하고 계좌 snapshot도 새 계좌 정보로 갱신한다. 이미 종료된 `PAID`, `WAIVED`, `CANCELED` 청구의 snapshot은 과거 기록으로 유지한다.

벌금 청구 생성 시 활성 PENALTY 계좌를 자동 연결한다.

```text
payment_accounts.account_type = PENALTY
payment_accounts.campus_id = 현재 campusId
payment_accounts.is_active = true
```

커피 청구는 CLOSED 커피 투표 정산 시 투표 또는 투표 템플릿에 연결된 계좌를 사용한다.

```text
poll_templates.payment_account_id
polls.payment_account_id
```

청구 생성 시 계좌 정보를 snapshot으로 저장한다.

```text
payment_account_id
bank_name_snapshot
account_number_snapshot
account_holder_snapshot
```

활성 계좌가 없는 경우에는 조용히 청구를 생성하지 말고, 명확한 예외 또는 실패 결과를 반환한다.

Issue #34는 청구 기반 서비스까지만 구현하고, 실제 경건생활 제출과 CLOSED 커피 투표 정산 흐름 연결은 각각 Issue #33과 Issue #39에서 처리한다.

### 3.6 청구 타입 기준

사용 가능한 `paymentCategory`:

```text
PENALTY
COFFEE
```

사용 가능한 `chargeSourceType`:

```text
DEVOTION_RECORD
POLL_RESPONSE
```

사용 가능한 `chargeStatus`:

```text
UNPAID
PAID
WAIVED
CANCELED
```

사용하지 않는 옛 용어:

```text
BillingType
DEVOTION_FINE
MANUAL
sourceType=COFFEE
sourceType=DEVOTION_FINE
PAYMENT_REQUESTED
```

### 3.7 납부 기준

사용자가 계좌이체 후 앱에서 `납부했어요`를 누르면 즉시 `PAID` 처리한다.

`UNPAID -> PAID`는 사용자 납부 API에서만 가능하다.

관리자는 청구를 `PAID`로 변경할 수 없다.

관리자는 청구를 `WAIVED`, `CANCELED`로 변경할 수 있다.

관리자는 잘못 처리된 `PAID`, `WAIVED`, `CANCELED` 청구를 `UNPAID`로 되돌릴 수 있다.

관리자가 `PAID -> UNPAID`로 되돌릴 때는 `paidAt`을 비운다.

관리자 상태 변경 사유는 Issue #35에서 저장하지 않는다.

관리자 승인/반려 흐름은 MVP에서 제공하지 않는다.

사용자 납부 API:

```text
PATCH /api/v1/campuses/{campusId}/charges/me/{chargeItemId}/paid
```

관리자 상태 변경 API:

```text
PATCH /api/v1/admin/charges/{chargeItemId}/status
```

청구 조회 API:

```text
GET /api/v1/campuses/{campusId}/charges/me
GET /api/v1/campuses/{campusId}/charges/me/summary
GET /api/v1/admin/campuses/{campusId}/charges
GET /api/v1/admin/campuses/{campusId}/members/{userId}/charges
```

금지:

```text
payment-request API
ChargeItem.requestPayment()
관리자 납부 승인
관리자 납부 반려
입금 인증 사진
```

### 3.8 투표 기준

투표는 단일 선택과 복수 선택을 모두 지원한다.

투표 템플릿 생성 기준:

- 커피 투표 템플릿은 기본 템플릿으로 제공한다.
- 수요예배 투표 템플릿은 기본 제공하지 않고 관리자가 생성한다.
- 토요목자모임 투표 템플릿은 기본 제공하지 않고 관리자가 생성한다.
- 커스텀 투표 템플릿은 관리자가 생성한다.
- 투표 생성 요청에서 `templateId`는 선택 입력이며, 템플릿 없이 직접 투표를 생성할 수 있다.
- 관리자가 템플릿을 생성할 때 매주 자동 투표 생성 여부를 설정할 수 있다.
- 자동 생성이 꺼진 템플릿은 관리자가 직접 투표를 생성할 때만 사용한다.
- 자동 생성이 켜진 템플릿은 Scheduler/Batch가 설정된 주기 기준으로 Poll을 생성한다.
- 같은 캠퍼스, 같은 템플릿, 같은 주차에 중복 Poll을 생성하지 않는다.

```text
SelectionType:
- SINGLE
- MULTIPLE
```

투표 응답 API는 `optionIds` 배열을 사용한다.

```json
{
  "optionIds": [1, 2],
  "memo": "복수 선택합니다"
}
```

검증 기준:

```text
selectionType = SINGLE   -> optionIds 길이 1
selectionType = MULTIPLE -> optionIds 길이 1개 이상
```

ERD 기준:

```text
poll_responses = 사용자 1명의 응답 묶음
poll_response_options = 실제 선택한 선택지 목록
```

`poll_responses.option_id`는 사용하지 않는다.

투표 응답 API:

```text
PUT /api/v1/campuses/{campusId}/polls/{pollId}/responses/me
```

OPEN 투표에서는 기존 응답을 수정할 수 있다. CLOSED 투표에서는 응답과 재투표를 막는다.

관리자 직접 투표 생성 시 생성 시점 현재 시간이 `startsAt <= now < endsAt` 범위이면 생성 직후 `OPEN` 상태로 둔다.

아직 시작 전 투표는 `SCHEDULED` 상태를 유지한다.

Scheduler/Batch는 기존처럼 자동 생성, 마감, 보정 전환 역할을 유지한다.

투표 결과 조회는 일반 캠퍼스 ACTIVE 멤버도 사용할 수 있다.

투표 결과 조회는 항목별 조회 API가 아니라 투표별 전체 결과 조회 API 하나로 구현한다.

```text
GET /api/v1/campuses/{campusId}/polls/{pollId}/results
```

MVP에서는 `/options/{optionId}/results` 같은 선택지 단위 결과 조회 API를 만들지 않는다.

비익명 투표(`isAnonymous = false`)는 결과 조회에서 누가 어느 선택지에 투표했는지 노출할 수 있다.

익명 투표(`isAnonymous = true`)는 아무도 누가 어느 선택지에 투표했는지 식별할 수 없어야 한다. 결과 조회 응답은 선택지별 집계만 노출하고 응답자 `userId`, 이름, 이메일, 선택지별 응답자 목록을 숨긴다.

익명 투표에서도 `poll_responses.user_id`는 중복 응답 방지, 내 응답 수정, 미참여자 계산을 위해 저장한다. 단, 익명 결과 API에서 이를 노출하지 않는다.

투표 결과 조회와 지난 투표 조회 가능 기간은 `polls.ends_at` 기준으로 제한한다.

- 일반 사용자/캠퍼스 ACTIVE 멤버 화면과 API: 종료 후 3일까지만 지난 투표, 투표 상세, 투표 결과를 볼 수 있다.
- 관리자 화면과 API: 종료 후 7일까지만 지난 투표, 투표 상세, 투표 결과를 볼 수 있다.
- 공개 기간이 지난 투표는 목록에서 제외하고, 직접 조회도 투표/결과 데이터를 노출하지 않는다.

커피 브랜드/메뉴 카탈로그 조회 API는 아래 경로를 사용한다.

```text
GET /api/v1/coffee-brands
GET /api/v1/coffee-brands/{brandId}/menus
```

컴포즈커피 메뉴 seed는 컴포즈커피 공식 자료를 우선한다. 공식 검증이 불가능하면 사용자가 명시 승인한 최신 메뉴/가격 자료를 seed 기준으로 사용할 수 있으며, 실제 Issue #37 구현은 사용자가 승인한 2026년 컴포즈커피 메뉴/가격 자료 기준이다. 가격은 청구 금액에 직접 영향을 주므로 Codex가 임의로 추측해서 채우지 않는다.

### 3.9 투표 댓글 기준

투표 댓글은 MVP에 포함한다.

- `PollComment`와 `poll_comments` 테이블을 사용한다.
- 댓글은 캠퍼스 소속 ACTIVE 멤버만 작성 가능하다.
- 댓글 작성자는 `user_id`로 저장한다.
- 익명 투표여도 댓글은 익명 처리하지 않는다.
- 익명 댓글은 Post-MVP로 둔다.
- 댓글 수정/삭제는 작성자 또는 캠퍼스 관리자 권한(`MINISTER`, `ELDER`, `CAMPUS_LEADER`, `ADMIN`)만 가능하다.
- 삭제는 soft delete를 기본으로 한다.
- 댓글 목록 조회 시 삭제된 댓글은 content를 노출하지 않거나 `삭제된 댓글입니다.`로 응답한다.
- 댓글 작성은 OPEN 상태 투표에서만 허용한다.
- CLOSED 투표는 댓글 조회만 허용한다.

기준 API:

```text
GET /api/v1/campuses/{campusId}/polls/{pollId}/comments
POST /api/v1/campuses/{campusId}/polls/{pollId}/comments
PATCH /api/v1/campuses/{campusId}/polls/{pollId}/comments/{commentId}
DELETE /api/v1/campuses/{campusId}/polls/{pollId}/comments/{commentId}
```

### 3.10 커피 주문 기준

커피 주문은 투표 기능을 사용한다.

커피 투표 템플릿은 기본으로 제공한다.

커피 주문 브랜드는 MVP에서 컴포즈커피만 사용한다.

커피 메뉴명과 가격은 청구 금액으로 이어지므로 프론트 전용 데이터나 Java enum 상수로 관리하지 않는다.

Issue #37은 백엔드 기준 데이터로 아래 구조를 추가한다.

```text
coffee_brands
coffee_menu_catalog
```

MVP seed 기준:

- `coffee_brands`: 컴포즈커피 1개
- `coffee_menu_catalog`: 현재 컴포즈커피 전체 메뉴
- 기본 커피 투표 템플릿 옵션: 아이스 아메리카노, 아메리카노, 아이스티, 아이스 라떼, 라떼

추가 선택지는 프론트가 백엔드 메뉴 카탈로그를 조회한 뒤 선택해서 `poll_template_options`에 복사 저장한다.

`poll_template_options`와 `poll_options`는 생성 당시의 메뉴 코드, 메뉴명, 가격을 snapshot으로 저장한다. 이후 카탈로그 가격이 바뀌어도 이미 생성된 템플릿, 투표, 청구 금액은 조용히 변경하지 않는다.

컴포즈커피 전체 메뉴 seed 목록과 가격은 개발 전에 공식 메뉴판 또는 사용자가 승인한 최신 자료로 검증해야 하며, Codex가 임의로 추측해서 채우지 않는다.

커피 담당자는 기본 커피 투표 템플릿의 아래 시간을 설정할 수 있다.

- 매주 커피 투표가 자동 생성되는 시간
- 생성된 커피 투표가 마감되는 시간

구현 시 실제 DB 칼럼명은 Notion ERD의 `poll_templates`/`polls` 설계를 따른다. Codex는 칼럼명을 추측해서 새로 정하지 않는다.

커피 투표 응답 API는 응답 저장만 수행하고 COFFEE 청구를 즉시 생성하지 않는다.

커피 청구는 CLOSED 커피 투표 정산 서비스가 최종 `poll_responses`와 `poll_response_options` 기준으로 생성 또는 갱신한다.

별도 커피 청구 생성 API는 MVP에서 제공하지 않는다.

커피 청구 기준:

```text
paymentCategory = COFFEE
sourceType = POLL_RESPONSE
sourceId = poll_responses.id
```

MVP에서 커피 주문 투표는 단일 선택 기본이다.

커피 정산 대상 poll 기준:

```text
poll_type = COFFEE
charge_generation_type = OPTION_PRICE
payment_category = COFFEE
status = CLOSED
```

정산 금액은 현재 카탈로그 가격이 아니라 `poll_options.price_amount` snapshot을 사용한다.

정산은 멱등이어야 하며, 기존 `UNPAID` COFFEE 청구는 갱신 또는 유지하고 `PAID`, `WAIVED`, `CANCELED` 청구는 덮어쓰지 않는다.

한 poll 정산은 전체 성공 또는 전체 실패 트랜잭션으로 처리한다.

### 3.11 캠퍼스 역할 기준

서비스 전체 역할은 `users.role`로 관리한다.

```text
USER
MANAGER
ADMIN
```

`MANAGER`와 `ADMIN`은 캠퍼스를 생성할 수 있다.

`MANAGER`가 캠퍼스를 생성하면 해당 사용자는 생성한 캠퍼스에 `MINISTER + ACTIVE` 멤버십으로 등록된다.

`MANAGER`는 캠퍼스 생성 가능 전역 역할이며, 캠퍼스 내부 관리 권한 자체가 아니다.

캠퍼스 내부 관리 권한은 `users.role = MANAGER`가 아니라 `campus_members.campus_role` 기준으로 판단한다.

`ADMIN`은 전체 캠퍼스 상세에 접근할 수 있다.

`CampusRole`은 아래 값만 사용한다.

```text
MINISTER
ELDER
CAMPUS_LEADER
MEMBER
```

초대코드 노출 기준:

- 캠퍼스 생성 응답에는 `inviteCode`를 포함한다.
- `ADMIN`, `MINISTER`, `ELDER`, `CAMPUS_LEADER`는 초대코드를 조회할 수 있다.
- 일반 `MEMBER`의 캠퍼스 상세 조회 응답에는 `inviteCode`를 노출하지 않는다.

`GET /api/v1/campuses/me`는 MVP에서 현재 사용자의 `ACTIVE` 캠퍼스 멤버십만 반환한다.

캠퍼스 멤버 삭제 API:

```text
DELETE /api/v1/campuses/{campusId}/members/{membershipId}
```

캠퍼스 멤버 삭제는 `campus_members.status = INACTIVE`로 처리한다.

캠퍼스 멤버 관리는 `ADMIN`, `MINISTER`, `ELDER`, `CAMPUS_LEADER`가 할 수 있다.

일반 캠퍼스 `MEMBER`는 캠퍼스 멤버를 관리하거나 삭제할 수 없다.

비활성화된 멤버가 초대코드로 다시 가입하면 기존 멤버십을 `ACTIVE + MEMBER`로 재활성화한다.

커피 담당자는 `CampusRole`로 처리하지 않는다.

커피 담당자는 아래 구조로 분리한다.

```text
CampusDutyAssignment
DutyType.COFFEE
```

캠퍼스당 활성 `DutyType.COFFEE` 담당자는 1명만 둔다.

Issue #30 커피 담당자 API 기준:

```text
GET    /api/v1/admin/campuses/{campusId}/duty-assignments
PUT    /api/v1/admin/campuses/{campusId}/duty-assignments/coffee
DELETE /api/v1/admin/campuses/{campusId}/duty-assignments/coffee/{assignmentId}
```

Issue #30 캠퍼스 역할 변경 API 기준:

```text
GET   /api/v1/admin/campuses/{campusId}/members
PATCH /api/v1/admin/campuses/{campusId}/members/{campusMemberId}/campus-role
```

`campusMemberId`는 `campus_members.id`를 의미한다.

캠퍼스 역할 변경 위계는 아래 순서를 따른다.

```text
MINISTER > ELDER > CAMPUS_LEADER > MEMBER
```

최신 Issue #30 결정 기준은 same-level assignment이다. 즉 캠퍼스 관리자는 자기 역할과 같은 단계까지 대상 유저에게 부여할 수 있고, 자기보다 높은 역할은 변경하거나 부여할 수 없다. 이전의 "자기보다 아래 역할만 변경 가능" 해석은 이 최신 결정으로 대체한다.

- `MINISTER`는 다른 유저를 `MINISTER`, `ELDER`, `CAMPUS_LEADER`, `MEMBER`로 변경할 수 있다.
- `ELDER`는 다른 유저를 `ELDER`, `CAMPUS_LEADER`, `MEMBER`로 변경할 수 있지만, 기존 `MINISTER`를 변경하거나 `MINISTER`를 부여할 수 없다.
- `CAMPUS_LEADER`는 다른 유저를 `CAMPUS_LEADER`, `MEMBER`로 변경할 수 있지만, 기존 `MINISTER`/`ELDER`를 변경하거나 `MINISTER`/`ELDER`를 부여할 수 없다.
- `MEMBER`는 역할을 변경할 수 없다.
- 서비스 전체 `ADMIN`은 모든 캠퍼스 멤버 역할을 변경할 수 있다.
- 서비스 전체 `MANAGER`만으로는 캠퍼스 역할 변경 권한이 생기지 않는다.
- 마지막 캠퍼스 관리 역할 보유자를 `MEMBER`로 내리는 것은 막지 않는다.

### 3.12 FCM 알림 기준

사용자 본인 FCM 토큰 API:

```text
POST /api/v1/users/me/fcm-tokens
DELETE /api/v1/users/me/fcm-tokens/{tokenId}
```

관리자 알림 API:

```text
POST /api/v1/admin/campuses/{campusId}/notifications
GET /api/v1/admin/campuses/{campusId}/notification-logs
```

사용하지 않는 경로:

```text
/api/v1/notifications/fcm-tokens
/notifications/logs
```

`notification-logs` 표기를 사용한다.

## 4. TDD 개발 규칙

1. 기능 구현 전 실패하는 테스트를 먼저 작성한다.
2. 테스트가 실패하는 상태를 확인한다.
3. 그 다음 최소 구현으로 테스트를 통과시킨다.
4. 테스트 통과 후 리팩토링한다.
5. Service, Domain, Application 로직은 테스트 없이 구현하지 않는다.
6. Controller는 핵심 request/response mapping 테스트를 작성한다.
7. Repository의 단순 CRUD는 테스트 생략 가능하다.
8. 복잡한 조회 조건, 집계, unique 중복 방지, 상태 전이는 테스트 필수다.
9. 테스트 없이 기능 코드만 추가하지 않는다.
10. 문서-only 작업은 테스트 추가를 생략할 수 있지만, 가능한 경우 기존 테스트 실행 여부를 확인한다.

### 4.1 API 문서화 규칙

1. FaithLog의 상세 API 계약 문서화는 Spring REST Docs를 주된 방식으로 사용한다.
2. Swagger/springdoc은 간단한 API 탐색과 확인 용도로 유지한다.
3. Swagger 문서화 어노테이션 중심 문서화는 사용하지 않는다.
4. Controller, DTO, Entity는 `@Operation`, `@Schema`, `@ApiResponse` 같은 Swagger 문서화 어노테이션으로 오염시키지 않는다.
5. 새 API 또는 변경 API의 상세 request/response 계약은 가능한 경우 MockMvc/WebMvc/Spring REST Docs 테스트로 검증하고 snippets를 생성한다.
6. API 문서와 테스트가 어긋나지 않도록 문서 생성 테스트도 TDD 및 검증 범위에 포함한다.
7. REST Docs 산출물은 `build/generated-snippets`와 `build/docs/asciidoc` 기준으로 확인한다.

### 4.2 에러 코드와 요청 검증 규칙

1. 에러 응답은 `HTTP status + 세부 code`를 고정 API 계약으로 사용한다.
2. `message`는 사용자 표시용 문구로 관리한다.
3. `ErrorCode`는 글로벌 enum 하나를 유지하되, 도메인 prefix 기반 세부 코드로 나눈다.
4. 넓은 `INVALID_REQUEST`, `NOT_FOUND`, `FORBIDDEN`만으로 새 도메인 예외를 표현하지 않는다.
5. `page`, `size`, `sort`가 잘못된 경우 자동 보정하지 않고 `400`을 반환한다.
6. 단순 DTO 검증은 Bean Validation을 사용한다.
7. 페이지/정렬 파싱과 검증은 공통 요청 검증 컴포넌트로 분리한다.
8. 비즈니스 규칙 검증은 `CampusRolePolicy`, `ChargeStatusPolicy`, `BillingAccessPolicy` 같은 정책 클래스로 분리한다.
9. 새 API 또는 변경 API의 에러 응답 계약은 가능한 경우 Spring REST Docs 테스트로 문서화한다.

## 5. 테스트 필수 영역

다음 기능은 반드시 테스트를 작성한다.

- 주간 경건생활 제출 시 daily row 7개 생성 또는 수정
- `weekly_devotion_records` 요약값 계산
- 벌금 계산
- PENALTY 청구 자동 생성
- 이미 제출된 주차의 중복 `submit = true` 제출 방지
- 중복 청구 방지
- 활성 PENALTY 계좌 조회 및 계좌 snapshot 저장
- `납부했어요` 즉시 PAID 처리
- SINGLE/MULTIPLE 투표 응답 검증
- `poll_response_options` 저장
- 투표 댓글 작성/수정/삭제 권한 검증
- CLOSED 투표 댓글 작성 방지
- CLOSED 커피 투표 정산 시 COFFEE 청구 자동 생성 또는 갱신
- 커피 투표 계좌 snapshot 저장
- CampusRole 권한 변경
- CampusMember 삭제 권한 검증 및 INACTIVE 상태 전이
- CampusDutyAssignment 커피 담당자 지정/해제

## 6. 아키텍처 규칙

1. Controller는 Request를 Command로 변환하고 Application Service를 호출한다.
2. Controller에서 Entity를 직접 반환하지 않는다.
3. Entity를 Request/Response DTO로 사용하지 않는다.
4. Domain 로직은 Entity 또는 Domain Service에 둔다.
5. Application Service는 유스케이스 흐름을 조합한다.
6. Infrastructure는 외부 연동과 DB 구현체를 담당한다.
7. 다른 도메인 Entity를 직접 참조하지 말고 ID 또는 Command/Result로 연결한다.
8. Devotion 도메인은 Billing Entity를 직접 조작하지 않는다.
9. Poll 도메인은 Billing Entity를 직접 조작하지 않는다.
10. 청구 생성은 Application 계층에서 Billing Command를 호출하는 방식으로 연결한다.

## 7. 보안 규칙

1. `.env` 파일을 생성하거나 수정하지 않는다.
2. `application-prod.yml`, `application-secret.yml`을 커밋하지 않는다.
3. JWT Secret, DB Password, Firebase Key, private key를 코드에 직접 작성하지 않는다.
4. 테스트용 값도 실제 키처럼 보이는 값을 사용하지 않는다.
5. Firebase Admin SDK 키 파일은 저장소에 추가하지 않는다.
6. 민감 정보는 환경변수 또는 로컬 전용 설정으로만 참조한다.
7. 예시 값이 필요하면 `dummy`, `example`, `test-only`, `changeme`처럼 실제 키로 오해되지 않는 값을 사용한다.

## 8. 금지어 검사 규칙

작업 완료 전 아래 옛 용어가 실제 소스 코드, DTO, Entity, Enum, API 문서, 테스트 코드에 남아 있는지 검색한다.

```text
DEVOTION_FINE
sourceType=COFFEE
BillingType
MANUAL
PAYMENT_REQUESTED
payment-request
requestPayment
poll_responses.option_id
```

API 요청 필드로 사용되는 단수 `optionId`는 금지한다.

단, `optionIds` 배열을 순회하는 내부 변수명으로 `optionId`를 사용하는 것은 허용한다.

`LEADER`, `MEMBER`만 있는 CampusRole 정의는 금지한다.

### 단수 `optionId` 검사 기준

단수 `optionId` 금지는 API 요청/응답 DTO, API 문서, 테스트 요청 JSON의 필드명에 적용한다.

아래처럼 단어 경계 또는 JSON/property 필드 패턴으로 검사하고, `optionIds` 배열명과 반복문 내부 변수명은 위반으로 보지 않는다.

```bash
rg -n '"optionId"|\boptionId\s*[:=]|\boptionId\s*\(' src docs README.md
```

검사 결과가 `optionIds`, `for (... optionId ...)`, `optionIds.stream().map(optionId -> ...)`처럼 허용된 내부 변수명인지 확인한다.

### 금지어 검사 예외

아래 위치에서 금지어가 "금지어 예시"로 등장하는 것은 허용한다.

```text
AGENTS.md
docs/codex/FAITHLOG_CODEX_HOOK.md
docs/issues/CHORE-CODEX-HOOK-001.md
docs/backend-implementation-policy.md
docs/decision-log.md
docs/resume-metrics.md
```

즉, 금지어 목록을 설명하기 위한 문서 내부 예시는 허용하지만, 실제 구현 코드와 API 스펙에는 사용하지 않는다.

## 9. Obsidian 문서화 Hook

FaithLog 기능 개발 작업을 완료하기 전, 반드시 Obsidian Vault에 개발 기록을 남긴다.

현재 Codex 작업환경에서 확인된 Obsidian Vault 기준 경로:

```text
/Users/josephuk77/obsidian/obsidian-writing-vault/Projects/FaithLog/
```

문서에서 상대 경로가 필요하면 아래 구조를 사용한다.

```text
Projects/FaithLog/
  00_Index.md
  01_Planning.md
  02_ERD.md
  03_API.md
  04_DevLog/
  05_Troubleshooting/
  06_Retrospective/
  07_Velog-Drafts/
```

### 9.1 개발 로그 작성

기능 이슈를 완료하면 아래 경로에 개발 로그를 작성한다.

```text
Projects/FaithLog/04_DevLog/YYYY-MM-DD_issue-{issueNumber}-{summary}.md
```

개발 로그 템플릿:

```markdown
---
project: FaithLog
type: devlog
issue: #{issueNumber}
status: done
created: YYYY-MM-DD
tags:
  - FaithLog
  - backend
  - spring-boot
  - tdd
---

# #{issueNumber} {issueTitle}

## 1. 작업 배경

## 2. 최종 설계 기준

## 3. 구현 내용

- Entity:
- Command:
- Service:
- Repository:
- Controller:
- Test:

## 4. TDD 기록

1. 실패 테스트 작성:
2. 실패 확인:
3. 최소 구현:
4. 테스트 통과:
5. 리팩토링:

## 5. 테스트 결과

명령:

`./gradlew test`

결과:

`BUILD SUCCESSFUL`

## 6. 고민한 부분

## 7. 트러블슈팅

- 문제:
- 원인:
- 해결:
- 재발 방지:

## 8. 다음 작업

- [ ]

## 9. Velog 글감

-
```

### 9.2 트러블슈팅 문서 작성

개발 중 에러, 테스트 실패, 설계 충돌, Docker 문제, JPA 문제, Security 문제, Redis 문제, FCM 문제가 발생하면 아래 경로에 별도 문서를 작성한다.

```text
Projects/FaithLog/05_Troubleshooting/YYYY-MM-DD_{topic}.md
```

트러블슈팅 템플릿:

```markdown
---
project: FaithLog
type: troubleshooting
created: YYYY-MM-DD
tags:
  - FaithLog
  - troubleshooting
---

# {문제 제목}

## 문제 상황

## 에러 메시지

에러 메시지를 붙여넣는다.

## 원인 분석

## 해결 방법

## 재발 방지

## 관련 이슈

- #{issueNumber}
```

### 9.3 설계 변경 기록

Notion 최종 설계와 다르게 구현해야 하는 상황이 생기면 임의로 코드만 바꾸지 않는다.

아래 파일에 설계 변경 기록을 남긴다.

```text
Projects/FaithLog/06_Retrospective/Design-Decisions.md
```

기록 형식:

```markdown
## YYYY-MM-DD - {결정 제목}

### 배경

### 선택지

1.
2.
3.

### 결정

### 이유

### 영향 범위

### 관련 이슈

- #{issueNumber}
```

Notion 최종 설계와 충돌하는 변경은 사용자 확인 없이 확정하지 않는다.

### 9.4 프로젝트 인덱스 갱신

새 개발 로그나 트러블슈팅 문서를 만들면 아래 파일을 갱신한다.

```text
Projects/FaithLog/00_Index.md
```

인덱스에 다음을 추가한다.

```markdown
## DevLog

- [[04_DevLog/YYYY-MM-DD_issue-{issueNumber}-{summary}]]

## Troubleshooting

- [[05_Troubleshooting/YYYY-MM-DD_{topic}]]
```

### 9.5 Velog 초안 후보 작성

작업이 블로그 글감으로 적합하면 아래 경로에 초안 후보를 작성한다.

```text
Projects/FaithLog/07_Velog-Drafts/YYYY-MM-DD_{topic}.md
```

Velog 초안 후보 템플릿:

```markdown
---
project: FaithLog
type: velog-draft
created: YYYY-MM-DD
tags:
  - FaithLog
  - backend
  - spring-boot
---

# {글 제목 후보}

## 글로 풀어볼 문제

## 내가 고민한 지점

## 최종 선택

## 코드 또는 설계 예시

## 배운 점

## 글 전개 순서

1.
2.
3.
```

### 9.6 문서화 생략 가능 조건

아래 작업은 개발 로그를 생략할 수 있다.

- 오타 수정
- README 한 줄 수정
- 주석 수정
- 의존성 버전만 변경
- 테스트 없이 설명 문서만 수정하는 작업

단, 기능 구현, 버그 수정, 설계 변경, 테스트 추가 작업은 반드시 문서화한다.

이번 Codex Hook 세팅 작업은 기능 구현이 아니므로 실제 개발 로그 작성은 생략할 수 있다. 다만 Obsidian 문서화 규칙과 템플릿은 반드시 Hook 문서에 포함한다.

## 10. 작업 완료 전 확인

1. `./gradlew test`를 실행한다.
2. 테스트 실패가 있으면 수정한다.
3. Spring Boot 초기 설정, Gradle 설정, DB 의존성 문제 등으로 테스트 실행이 불가능하면 이유를 최종 보고에 남긴다.
4. 새 기능 작업인 경우 관련 테스트가 추가되었는지 확인한다.
5. Entity를 Controller에서 직접 반환하지 않는지 확인한다.
6. Notion 최종 설계와 충돌하는 enum/API/필드명이 없는지 확인한다.
7. 금지어 검색을 수행한다.
8. 문서화 대상 작업이면 Obsidian 개발 로그를 작성한다.
9. GitHub Projects 카드가 있으면 작업 완료 후 상태를 `Done`, `Review`, `Ready for Review`, `Code Review` 중 보드에서 사용하는 적절한 상태로 변경한다.
10. 카드 상태 변경 권한이 없으면 최종 보고에 남긴다.
11. 변경 파일 목록을 정리한다.
12. 실행한 테스트와 결과를 보고한다.

## 11. 보고 형식

작업이 끝나면 아래 형식으로 보고한다.

```text
작업 이슈:
- #번호 제목
- GitHub Issue 생성 여부:
- 대체 docs/issues 파일 생성 여부:

GitHub Projects 칸반보드:
- 보드 확인 여부:
- 사용한 Projects 보드:
- 카드 생성 또는 연결 여부:
- 카드 상태:
- 상태 변경 여부:
- 생성/연결/상태 변경 실패 시 사유:

변경 요약:
- ...

생성/수정 파일:
- ...

테스트:
- 추가한 테스트:
- 실행한 명령:
- 결과:
- 테스트 실행 불가 시 사유:

설계 준수 확인:
- 경건생활/청구/투표/납부/계좌 기준 충돌 없음
- 금지어 검색 결과 이상 없음
- 금지어 문서 예시 예외 처리 확인

Obsidian 문서화:
- 개발 로그: 작성함 / 작성하지 않음 / 생략 가능 작업
- 경로:
- 트러블슈팅: 작성함 / 해당 없음
- 경로:
- 인덱스 갱신: 완료 / 해당 없음
- Velog 초안 후보: 작성함 / 해당 없음

주의사항:
- ...
```
