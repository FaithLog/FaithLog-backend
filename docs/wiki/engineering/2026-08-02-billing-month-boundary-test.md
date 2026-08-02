# Billing 월 경계 테스트 실패

## 증상

2026-08-02에 다음 테스트가 단독 실행과 전체 실행에서 모두 실패했다.

- `BillingControllerTest.charge_query_api_maps_my_summary_admin_campus_and_admin_member_responses`
- `BillingApiRestDocsTest.documents_charge_query_contracts`

월별 납부 금액과 카테고리 집계가 `0` 또는 빈 배열로 반환됐다.

## 원인

테스트 요청은 `year=2026&month=7`을 사용했지만, fixture의 `ChargeItem.createdAt`은 JPA `@PrePersist`에서 `Instant.now()`로 생성됐다. `paid.markPaid()`도 현재 시각을 사용했다. 따라서 8월부터 July 집계 대상에서 fixture가 제외됐다.

## 수정

테스트 전용 고정 시각 `2026-07-16T00:00:00Z`를 사용한다.

- 납부 완료 시각은 production에 이미 존재하는 `markPaid(Instant)`로 설정한다.
- `created_at`은 Entity에서 `updatable=false`이므로 test-only `JdbcTemplate` fixture update로 두 청구에 동일하게 설정한다.
- 조회 parameter와 REST Docs 예시는 기존 `2026-07`을 유지한다.

## 예방

고정 year/month를 검증하는 통합 테스트는 fixture timestamp도 같은 명시적 기간에 결속한다. `YearMonth.now()`, sleep, 시간 tolerance는 월말 경계 경쟁 조건을 남기므로 사용하지 않는다. Production clock이나 API 의미를 테스트 편의를 위해 변경하지 않는다.

## 검증

- RED: 2 tests / 2 failures
- GREEN focused: 2 tests / 0 failures
- Production/Flyway/dependency diff: 0

Obsidian 동기화 대상: `Projects/FaithLog/05_Troubleshooting/2026-08-02_billing-month-boundary-test.md`.
