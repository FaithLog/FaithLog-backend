# Issue #257 캠퍼스 납부 계좌 정보 수정

## 계약

- API: `PATCH /api/v1/campuses/{campusId}/payment-accounts/{accountId}`
- 수정 필드: `nickname`, `bankName`, `accountNumber`, `accountHolder`
- PENALTY: 캠퍼스 관리자 또는 전역 ADMIN
- COFFEE/MEAL: 현재 담당이면서 target account owner인 본인
- active/inactive 수정 허용, deleted/other-campus 404
- account type, owner, active state 불변

## 데이터 경계

immutable scope로 tenant·삭제 상태를 먼저 확인한다. COFFEE/MEAL은 현재 담당 row, 계좌 row, 같은 account ID에 연결된 UNPAID 청구를 ID 오름차순으로 잠가 기존 비활성화 흐름과 잠금 순서를 맞춘다. PENALTY는 계좌 row와 연결 UNPAID 청구를 잠근다. PAID/WAIVED/CANCELED 청구는 과거 기록으로 유지한다.

## 검증

- test-only RED: command와 domain update method 부재 compile failure
- focused GREEN: domain/service/controller/REST Docs
- self-review RED: 수정과 비활성화의 반대 account/duty 잠금 순서
- GREEN: COFFEE/MEAL 잠금을 duty -> account -> linked UNPAID charge ID 순으로 통일
- 첫 전체 gate에서 기존 JWT tokenVersion 테스트의 고정 발급 시각 만료 1건을 확인하고 테스트 Clock만 현재 UTC로 보정
- 최종 `./gradlew --no-daemon test build asciidoctor`: 969 tests / failures 0 / errors 0 / skipped 20, BUILD SUCCESSFUL in 15m 51s
- bootJar/plain jar, JaCoCo, REST Docs HTML 생성 확인
- DB/Flyway/dependency 변경 없음
