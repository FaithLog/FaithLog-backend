# Issue #245 전역 주간자료

## 제품 계약

- `SHEPHERD_GUIDE`, `SUNDAY_SHARING_SHEET`, `SATURDAY_LEADER_SHARING_SHEET`를 global week/type별 독립 슬롯으로 관리한다. 기존 `SHARING_SHEET`와 outbox history는 V21에서 주일 나눔지로 이관한다.
- `weekStartDate`는 Asia/Seoul 기준 월요일이다.
- 기존 private PDF upload reservation/complete/access/cleanup과 30MiB 상한을 재사용한다.
- 관리자는 자신이 관리하는 campus path로 전역 슬롯을 등록·교체·삭제하고, 요청 campus ACTIVE 멤버는 모든 캠퍼스가 공유하는 같은 자료를 조회한다.
- 최초 global `SUNDAY_SHARING_SHEET` 등록만 전체 캠퍼스 distinct ACTIVE 사용자에게 알림을 생성하며 업로더는 제외한다. 다중 membership 사용자는 하나의 유효 campus context로 한 번만 받는다.
- 승인 copy: `새 주일설교 나눔지가 등록되었어요` / `{weekStartDate} 주차 주일설교 나눔지를 확인해 주세요` / `WEEKLY_SHARING_SHEET_PUBLISHED`.

## TDD 기록

1. domain/time, V20, media association, query/API, outbox, REST Docs 단위마다 test-only RED를 먼저 커밋했다.
2. 신규 asset은 request-campus requester-owned READY PDF로 제한하고, 기존 전역 asset은 저장된 `mediaCampusId`를 검증해 stable media lock 아래 교체·삭제한다.
3. replacement/delete의 이전 asset은 ORPHANED로 전환하고 cleanup이 삭제할 수 있도록 DELETED tombstone의 media FK를 비운다.
4. 서로 다른 campus의 최초 PUT/교체 race는 V21 singleton DB row lock으로 직렬화하고 global week/type unique outbox를 같은 transaction에 기록한다.
5. outbox processor는 전체 campus ACTIVE membership을 distinct user와 deterministic campus context로 축약해 업로더를 제외하고, 실패 시 pending을 유지한다.
6. `weekStartDate.plusMonths(3)`의 Asia/Seoul 00:00부터 scheduler가 row lock 아래 ACTIVE PDF를 ORPHANED로 전환하고 weekly-material 행을 물리 삭제한다. tombstone도 같은 경계에서 삭제하며 outbox/log는 보존한다.
7. 추가 승인에 따라 공지도 `publishedAt`의 서울 달력 날짜에서 `plusMonths(3)`인 00:00에 PUBLISHED/ARCHIVED 행, attachment link, announcement outbox를 물리 삭제한다. SCHEDULED는 제외하고 첨부 media는 ORPHANED로 넘긴다. notification log는 기존 14일 retention, R2 객체와 media 행은 기존 24시간 cleanup retry/lease가 물리 삭제한다.
8. PM 리뷰에서 빈 주차 200, ACTIVE weekly PDF media access, weekly/announcement/poll 양방향 exclusivity, pending outbox 삭제 억제, 물리 삭제 후 재등록 dedupe, global ADMIN PUT transaction을 각각 RED→GREEN으로 보강했다.
9. processor-vs-processor 테스트는 managed Entity snapshot에서 실제 중복 성공 `[true,true]`를 재현했다. 초기 조회를 immutable scalar projection으로 바꾸고 material→outbox row lock에서 최신 processed 상태를 읽어 exact-one으로 수정했다.
10. PM 후속 RED는 enum/응답/V21/global repository/outbox/recipient 계약 3개 실패를 먼저 고정했다. GREEN은 V20 무수정 V21, global lock, cross-tenant weekly-only private access, 다른 campus manager 교체·삭제, global recipient routing을 구현했다.

## 검증

- #245 및 #242 PDF/media, #237 announcement outbox, #238 poll outbox, V18 cleanup focused regression: 82 tests, failures/errors/skipped 0.
- 초기 격리 PostgreSQL 17 회귀에서 `PostgresFlywayMigrationTest` 11 tests, failures/errors/skipped 0을 확인했고, PM 보강 뒤 최종 exact V20 두 migration 경로를 다시 실행했다.
- 최종 exact V20은 전용 `faithlog-245-pg-v20:55445` PostgreSQL 17에서 Flyway V1→V20 clean과 V19→V20 upgrade 2 tests, failures/errors/skipped 0으로 재검증했다. CHECK/composite FK/outbox unique/RLS/notification type/retention expression index/native due SQL도 실제 catalog에서 확인했다.
- `build asciidoctor -x test -x jacocoTestReport`: 성공, bootJar와 REST Docs HTML 생성.
- 초기 전체 gate는 호스트 메모리 조건에서 완료되지 않았으며, PM 보강 뒤 저장소 설정을 바꾸지 않는 one-off Gradle init script로 다시 실행한다.
- 실제 R2/FCM, 기존 shared PostgreSQL/Redis/app container mutation, push/PR/merge/deploy는 0이다. 검증 전용 PostgreSQL container만 생성 후 제거했다.
- retention 및 Asia/Seoul 자정 cron 변경 직후 weeklymaterial/announcement/media/Flyway contract 회귀 175 tests, failures/errors/skipped 0과 `build asciidoctor -x test -x jacocoTestReport` 성공을 확인했다. PM 보강 뒤 전체 gate 결과는 아래 최종 기록으로 대체한다.
- PM finding 보강 integration은 pending suppression/rollback/history 재등록, processor-delete 및 processor-processor 경쟁, weekly-announcement/poll 동시 결속, weekly media access persistence, global ADMIN PUT/rollback을 실제 transaction으로 통과했다. 전체 gate는 저장소 Gradle 정책을 바꾸지 않고 `/private/tmp` one-off init script 조건으로 별도 기록한다.
- 최종 focused gate는 75 tests / failures 0 / errors 0 / skipped 0이다. 전용 PostgreSQL 17 `integration245` DB에서 위 persistence/concurrency 10 tests / failures 0 / errors 0 / skipped 0을 다시 통과했다.
- 최종 전체 gate는 저장소 Gradle 설정을 변경하지 않고 `/private/tmp/faithlog-245-test-memory.init.gradle`과 one-off daemon 옵션을 사용했다: daemon 256MiB, test worker 512MiB, maxParallelForks 1, forkEvery 25. `test build asciidoctor` 결과는 193 suites / 942 tests / failures 0 / errors 0 / skipped 17이고, 별도 `build asciidoctor -x test -x jacocoTestReport`는 `BUILD SUCCESSFUL in 23s`다.
- 후속 전역 계약의 test-only RED는 3 tests / failures 3으로 시작했다. GREEN focused gate는 weekly/media/Flyway 78 tests와 투표 미디어 회귀 3 tests가 모두 통과했다.
- 전용 `faithlog-245-v21-pg:55446` PostgreSQL 17에서 V1→V21 clean, V20→V21 legacy migration, material duplicate 및 outbox-only duplicate SQLSTATE 23505 fail-closed, global CHECK/FK/RLS/index/outbox unique를 3 tests / failures 0 / errors 0 / skipped 0으로 실제 실행했다.
- 후속 full `test build asciidoctor`는 저장소 정책을 바꾸지 않는 one-off 조건(daemon 256MiB, test worker 512MiB, maxParallelForks 1, forkEvery 25)에서 953 tests / failures 0 / errors 0 / skipped 19, `BUILD SUCCESSFUL in 8m 8s`로 끝났다. 첫 시도에서 기존 투표 테스트 2건의 campus-scoped mock이 전역 조회 포트와 맞지 않아 실패했고, fixture를 새 포트로 보정한 뒤 재실행했다.

## 프론트엔드 handoff

- 목자지침, 주일 나눔지, 토목모 나눔지는 세 개의 별도 control로 업로드·교체·삭제한다.
- 기존 upload reservation → private R2 PUT → complete → weekly-material PUT 순서를 재사용한다.
- week response의 `shepherdGuide`, `sundaySharingSheet`, `saturdayLeaderSharingSheet`는 각각 nullable이다. 기존 `sharingSheet` 필드는 제거된다.
- API path와 upload flow는 유지되며 enum path 값만 새 exact enum을 사용한다. 알림 copy/eventType과 7-day `assetId + sha256` cache는 변경하지 않는다.
- 다운로드는 `assetId`를 기존 private media access API에 전달한다. object key/public URL을 기대하지 않는다.
- 기기 cache key는 `assetId + sha256`, TTL은 7일이다. SHA가 바뀌면 새 PDF를 받고 이전 cache는 LRU/정리 정책으로 제거한다.

## Obsidian 전달

현재 작업 환경에서 Vault가 쓰기 허용 경로 밖이므로 아래 경로에 이 문서 본문을 복사해야 한다.

`/Users/josephuk77/obsidian/obsidian-writing-vault/Projects/FaithLog/04_DevLog/2026-08-05_issue-245-weekly-materials.md`

그리고 `/Users/josephuk77/obsidian/obsidian-writing-vault/Projects/FaithLog/00_Index.md`의 DevLog에 다음을 추가한다.

`- [[04_DevLog/2026-08-05_issue-245-weekly-materials]]`
