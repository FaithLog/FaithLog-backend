# 2026-06-20 #39 커피 투표 정산 구현

## 요약

- 브랜치: `feat/39-coffee-poll-charge-automation`
- #39 기준에 맞춰 커피 청구 생성 시점을 투표 응답 API가 아니라 CLOSED 커피 투표 정산 application service로 분리했다.
- Poll application 계층은 `CoffeePollChargePort`만 호출하고, Billing adapter가 `BillingService.createOrUpdateCoffeeCharge`로 연결한다.

## 구현

- `CoffeePollSettlementService`
  - `poll_type = COFFEE`
  - `charge_generation_type = OPTION_PRICE`
  - `payment_category = COFFEE`
  - `status = CLOSED`
- 최종 응답은 `poll_responses`와 `poll_response_options` 기준으로 읽는다.
- 금액과 제목은 현재 카탈로그가 아니라 `poll_options.price_amount`, `poll_options.content` snapshot을 사용한다.
- `UNPAID` COFFEE 청구는 같은 unique source row를 갱신하거나 유지하고, `PAID`, `WAIVED`, `CANCELED`는 덮어쓰지 않는다.
- 담당자 또는 계좌 전제조건 실패, 응답 데이터 불일치 실패는 한 poll 정산 전체 rollback으로 처리한다.

## 검증

- TDD 실패 evidence: 구현 전 `./gradlew test --tests com.faithlog.poll.application.PollServiceTest`가 `CoffeePollSettlementService`, `POLL_SETTLEMENT_NOT_CLOSED` 부재로 `compileTestJava` 실패.
- `./gradlew test --tests com.faithlog.poll.application.PollServiceTest` 성공.
- `./gradlew test --tests com.faithlog.poll.presentation.PollApiRestDocsTest` 성공.
- `./gradlew test --tests com.faithlog.billing.application.BillingServiceTest` 성공.
- `./gradlew test` 성공: 161 tests / 0 failures.
- `./gradlew build` 성공.
- `./gradlew asciidoctor` 성공.
- Docker compose build/up 성공, health `UP`.
- 실제 API QA: 커피 담당자와 COFFEE 계좌가 있는 캠퍼스에서 커피 투표 OPEN 응답 저장 후 `poll_response_options` 1건 저장, `COFFEE charge_items` 0건 확인.

## 제외

- 별도 사용자용 커피 청구 생성 API를 추가하지 않았다.
- #24 Scheduler/Batch 연결은 하지 않았다.
- 점심/LUNCH, 커피 주문 취합/발주, 결제 API 연동은 구현하지 않았다.
