# Issue #155 Batch와 Scheduler 책임 분리

## 배경

Issue #152와 #154 이후에도 `PollAutomationService`와 `AutomaticNotificationService`가 서로 다른 scheduled use case를 함께 소유했다. Scheduler trigger와 application orchestration을 분리하되 공개 API, DB, 스케줄, 시간대, 락, 트랜잭션, 정산, 알림, retention 정책은 변경하지 않는 것이 목표였다.

## TDD

- 기존 Batch focused 기준선 GREEN을 확인했다.
- production 수정 전에 전용 Poll/알림 use case, scheduler 직접 연결, cleanup/recovery transaction, thin facade, SDK 누출과 순환 의존 금지를 요구하는 구조 테스트 5건을 추가했다.
- 최초 실행은 `5 tests / 5 failures`로 RED였다.
- 책임 이동 후 신규 구조 테스트와 #152/#154 구조 회귀를 GREEN으로 전환했다.
- scheduler disabled Context와 coffee due close 재호출 시 CLOSED/charge가 정확히 1회 유지되는 characterization을 보강했다.

## 최종 책임

- `ScheduledPollCreationService`: due template, Asia/Seoul window, lock, transaction, factory.
- `DueCoffeePollClosureService`: due OPEN COFFEE, lock, CLOSED, command settlement.
- `DevotionMissingNotificationService`: 지난주 미제출 대상과 daily dedup scope.
- `PollMissingNotificationService`: 5/3/2/1시간, WED/SATURDAY/COFFEE/CUSTOM 대상과 offset scope.
- `PaymentUnpaidNotificationService`: ACTIVE member 중 UNPAID 대상과 daily scope.
- `FcmTokenCleanupService`: 90일 stale cutoff와 write transaction.
- `DataRetentionCleanupService`, `PendingNotificationRecoveryService`: 기존 전용 TransactionTemplate 경계 유지.
- `FaithLogScheduledJobs`: 기존 trigger와 application service 호출/결과 로깅만 유지.

## 정책 보존

8개 scheduler property와 cron/fixedDelay, `Asia/Seoul`, enable flag, template/week 중복 방지와 snapshot, OPEN 생성, CLOSED 후 coffee settlement, Redis lock/dedup/fail-closed, CUSTOM reminder, 90일 stale token, 10분 PENDING recovery, retention 기간·2월 1일·삭제 순서를 변경하지 않았다. API/DTO/HTTP/ErrorCode/auth, Entity/DB/Flyway/repository query, TTL/retry/dependency 변경은 없다.

## 검증

- Batch focused: 성공.
- Batch/Notification/Poll/Billing/Devotion/User: 283 tests, failures/errors/skipped 0.
- 전체 `./gradlew test`: 368 tests, failures/errors 0, skipped 1.
- `./gradlew build`: 성공.
- `./gradlew asciidoctor`: 성공.
- `git diff --check`: 성공.
- RedisTemplate/Firebase SDK 직접 의존, Swagger annotation 추가, Controller Entity 반환, 서비스 순환 의존: 0건.

## 환경 제약

GitHub token에 `read:project` scope가 없어 Project 카드 조회/이동을 수행하지 못했다. 현재 환경에 `pm-dev/SKILL.md`, `.harness`, `harness.yaml`이 없어 임의 생성하지 않고 저장소 TDD gate를 적용했다. push와 PR은 PM 코드리뷰 전 금지 지시에 따라 수행하지 않았다.

호스트 Data 볼륨은 100% 사용 중이고 가용 공간은 1.8GiB였으며 sandbox에서 Docker socket 접근도 거부됐다. 삭제나 prune으로 우회하지 않고 격리 Docker QA를 생략했으므로 원격 Docker CI가 필요하다.
