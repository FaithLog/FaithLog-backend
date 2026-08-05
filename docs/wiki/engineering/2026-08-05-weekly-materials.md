# Issue #245 캠퍼스 주간자료

## 제품 계약

- `SHEPHERD_GUIDE`, `SHARING_SHEET`를 campus/week/type별 독립 슬롯으로 관리한다.
- `weekStartDate`는 Asia/Seoul 기준 월요일이다.
- 기존 private PDF upload reservation/complete/access/cleanup과 30MiB 상한을 재사용한다.
- 캠퍼스 관리자만 등록·교체·삭제하고 ACTIVE 멤버만 조회한다.
- 최초 `SHARING_SHEET` 등록만 알림을 생성하며 업로더는 제외한다.
- 승인 copy: `새 주일설교 나눔지가 등록되었어요` / `{weekStartDate} 주차 주일설교 나눔지를 확인해 주세요` / `WEEKLY_SHARING_SHEET_PUBLISHED`.

## TDD 기록

1. domain/time, V20, media association, query/API, outbox, REST Docs 단위마다 test-only RED를 먼저 커밋했다.
2. same-campus requester-owned READY PDF 검증과 stable media lock을 구현했다.
3. replacement/delete의 이전 asset은 ORPHANED로 전환하고 cleanup이 삭제할 수 있도록 DELETED tombstone의 media FK를 비운다.
4. 최초 PUT race는 campus row lock으로 직렬화하고 같은 transaction에서 unique outbox를 기록한다.
5. outbox processor는 ACTIVE 멤버를 조회해 업로더를 제외하고, 실패 시 pending을 유지한다.
6. `weekStartDate.plusMonths(3)`의 Asia/Seoul 00:00부터 scheduler가 row lock 아래 ACTIVE PDF를 ORPHANED로 전환하고 weekly-material 행을 물리 삭제한다. tombstone도 같은 경계에서 삭제하며 outbox/log는 보존한다.
7. 추가 승인에 따라 공지도 `publishedAt`의 서울 달력 날짜에서 `plusMonths(3)`인 00:00에 PUBLISHED/ARCHIVED 행, attachment link, announcement outbox를 물리 삭제한다. SCHEDULED는 제외하고 첨부 media는 ORPHANED로 넘긴다. notification log는 기존 14일 retention, R2 객체와 media 행은 기존 24시간 cleanup retry/lease가 물리 삭제한다.

## 검증

- #245 및 #242 PDF/media, #237 announcement outbox, #238 poll outbox, V18 cleanup focused regression: 82 tests, failures/errors/skipped 0.
- 격리 PostgreSQL 17의 전용 DB/port에서 `PostgresFlywayMigrationTest`: 11 tests, failures/errors/skipped 0. V1→V20 clean과 V19→V20 upgrade를 검증했다.
- 해당 11-test 실행 뒤 retention SQL을 추가했다. 최종 V20 재검증용 격리 컨테이너는 Docker overlay/containerd `input/output error` 때문에 생성되지 않아, 최종 migration의 실제 PostgreSQL 검증은 미완료로 남긴다.
- `build asciidoctor -x test -x jacocoTestReport`: 성공, bootJar와 REST Docs HTML 생성.
- 전체 `test build asciidoctor`는 두 번 모두 `:test` 장시간 무출력/JVM instrumentation 정지로 완료되지 않아 전체 성공으로 주장하지 않는다.
- 실제 R2/FCM, 기존 shared PostgreSQL/Redis/app container mutation, push/PR/merge/deploy는 0이다. 검증 전용 PostgreSQL container만 생성 후 제거했다.
- 최종 retention 및 Asia/Seoul 자정 cron 변경 뒤 weeklymaterial/announcement/media/Flyway contract 회귀: 175 tests, failures/errors/skipped 0. `build asciidoctor -x test -x jacocoTestReport`는 성공했다. 전체 `test build asciidoctor` 재시도는 관련 없는 `PasswordResetTransactionIntegrationTest`의 Spring context 생성 중 `OutOfMemoryError`로 완료되지 않았다.

## 프론트엔드 handoff

- 목자지침과 주일설교 나눔지는 별도 control로 업로드·교체·삭제한다.
- 기존 upload reservation → private R2 PUT → complete → weekly-material PUT 순서를 재사용한다.
- week response의 `shepherdGuide`, `sharingSheet`는 각각 nullable이다.
- 다운로드는 `assetId`를 기존 private media access API에 전달한다. object key/public URL을 기대하지 않는다.
- 기기 cache key는 `assetId + sha256`, TTL은 7일이다. SHA가 바뀌면 새 PDF를 받고 이전 cache는 LRU/정리 정책으로 제거한다.

## Obsidian 전달

현재 작업 환경에서 Vault가 쓰기 허용 경로 밖이므로 아래 경로에 이 문서 본문을 복사해야 한다.

`/Users/josephuk77/obsidian/obsidian-writing-vault/Projects/FaithLog/04_DevLog/2026-08-05_issue-245-weekly-materials.md`

그리고 `/Users/josephuk77/obsidian/obsidian-writing-vault/Projects/FaithLog/00_Index.md`의 DevLog에 다음을 추가한다.

`- [[04_DevLog/2026-08-05_issue-245-weekly-materials]]`
