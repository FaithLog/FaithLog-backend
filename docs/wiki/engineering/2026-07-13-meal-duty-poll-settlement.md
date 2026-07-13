# 2026-07-13 Issue #189 밥 담당·투표 그룹별 후청구

## 승인 계약

- 한 캠퍼스에 ACTIVE MEAL duty를 여러 명 둘 수 있고 같은 사용자 재지정은 idempotent다.
- service ADMIN/캠퍼스 관리자도 ACTIVE MEAL duty가 없으면 MEAL 운영 API는 403이다.
- MEAL 계좌는 요청자 본인 소유만 생성·조회·비활성화한다.
- MEAL poll은 서버 시각 하나를 `startsAt/createdAt`에 사용하고 `SINGLE/OPEN`으로 즉시 생성한다.
- 생성 요청은 미래 `endsAt`만 받으며 account/amount/startsAt/price/category/generation 필드는 400이다.
- 기존 `optionIds`/`poll_response_options`와 사용자 옵션 추가 API를 재사용한다.
- close는 CLOSED 전환만 하며 settlement/charge를 만들지 않는다.
- CLOSED poll 후청구는 공통 본인 ACTIVE MEAL 계좌 1개와 응답 option별 `PER_MEMBER` 또는 `GROUP_TOTAL`을 한 요청으로 받는다.
- 서버 최종 응답을 다시 집계하고, 정수 exact arithmetic과 ceiling division으로 requested/actual/rounding을 분리한다.
- settlement/group/charge는 한 transaction이며 poll row lock과 DB unique로 재청구·경합 중복을 막는다.
- 다른 MEAL duty에게는 계산 결과만 보이고 settlement 계좌 ID/상세는 노출하지 않는다.

## 구현

- Flyway V8: MEAL enum/check 확장, COFFEE 단일 duty index 분리, ACTIVE MEAL duty 사용자 unique, 본인 ACTIVE MEAL account unique, 정규화된 `meal_poll_settlements`와 `meal_poll_charge_groups`.
- Duty: 다수 배정, 사용자별 idempotency, 본인 duty 상태 API, MEAL 운영 공통 access service.
- Account: 본인 소유 MEAL 계좌 전용 API, 담당자별 active 교체, generic admin API의 MEAL 비노출.
- Poll: 전용 create/manage/detail/close API, 즉시 OPEN, 과거 CLOSED pagination, settlement status, 사용자 option 409 중복.
- Settlement: 응답 option 완전집합 검증, zero-response 제외, exact calculation, 공통 account snapshot charge, all-or-nothing rollback, retry 409.
- Query: 요청자 소유 MEAL account에 연결된 MEAL charge만 집계.

## TDD 기록

- RED: V8 부재와 `DutyType.MEAL` 부재 2건 실패.
- RED: MEAL account API 부재로 controller 테스트 실패.
- RED: MEAL poll `createdAt`/즉시 시작 계약 부재로 compile test 실패.
- RED: poll-level `/charges` API 부재로 404 실패.
- RED: exact arithmetic 계산기 부재로 compile test 실패.
- 리뷰 RED: generic MEAL 우회·dashboard 노출·공용 poll lock·null list 요소 회귀 묶음 82개 중 6개 실패, 정산된 poll 보존 삭제 1개 FK 실패.
- 재리뷰 RED: 수동 close가 미래 `endsAt`을 변경하는 계약 위반 1개 실패.
- GREEN: enum/Flyway, duty/account, poll create, GROUP_TOTAL 10,000원÷3명=3,334원·실제 10,002원·차액 2원, retry 409, calculator focused 테스트 통과.
- 리뷰 GREEN: response/user option/generic·전용 close/settlement가 같은 pessimistic poll lookup을 사용한다. 일반 관리자 MEAL close·missing-members·status 변경은 404, dashboard와 generic create/template은 MEAL을 제외하며, V8 option/poll cascade가 30일 cleanup을 보장한다. null 요소는 400이고 회귀 묶음 90개가 통과했다.
- 재리뷰 GREEN: 수동 close는 `endsAt`을 유지하고 상태만 CLOSED로 바꾼다. settlement/group/첫 charge write 이후 unique 충돌도 전부 rollback하며, 전용 close cross-campus/non-MEAL 404, role 불변, PER_MEMBER/source/account snapshot, 오래된 CLOSED 목록을 명시 검증했다.

## 검증 범위

- focused: Duty/Poll/Billing/Flyway/REST Docs 187 tests / 0 failures / 0 errors / 2 skipped. 사용자 option 후청구, 0응답 제외, account 격리, 실제 write 후 rollback, Clock 경계, 경합 중복 차단까지 보강했다.
- full: `./gradlew test` 427 tests / 0 failures / 0 errors / 3 skipped, `./gradlew build`, `./gradlew asciidoctor`, `git diff --check` 성공.
- REST Docs: 전체 147개 snippet group 중 MEAL 관련 23개를 생성하고 `build/docs/asciidoc/index.html` 렌더를 확인했다.
- Docker QA: 사용자 최신 결정에 따라 이 feature에서는 실행하지 않고 #188/#189/#190 승인 후 integration branch에서 PostgreSQL/Redis/backend 연결 QA로 이관한다.

## 리스크와 후속

- GitHub token에 `read:project` scope가 없어 Issue #189 Project 카드 존재/상태를 확인하거나 In Progress로 이동하지 못했다.
- 실제 PostgreSQL Flyway clean/upgrade와 실제 HTTP Docker QA는 integration branch의 단일 격리 QA에서 수행한다.
- PM finding 0과 비Docker 완료 게이트 통과 전 push/PR/merge하지 않는다.
