Warning: truncated output (original token count: 79121)
Total output lines: 1379

# FaithLog Decision Log

This file records user-approved project decisions so Codex does not rely on guesses later.

## Rules

- Every product, architecture, data, deployment, test-strategy, or resume-metric decision must come from the user.
- If Codex is unsure, Codex must ask before implementation.
- Record the decision date, context, options if relevant, the user's decision, and the implementation impact.

## Decisions

### 2026-08-05 - Issue #244 Archived Announcement Restore

- Context: ARCHIVED 상태 공지를 영구 삭제하기 전에 관리자 API로 다시 일반 게시 상태에 노출해야 한다. #242 PDF 첨부와 #243 보관 공지 삭제 계약을 완화하지 않고, 복구 자체가 알림 재발송이나 첨부 lifecycle 변경을 만들면 안 된다.
- Decision: `POST /api/v1/admin/campuses/{campusId}/announcements/{announcementId}/restore`는 Access Token과 해당 캠퍼스 관리자 권한을 요구하고 request body를 받지 않는다. 성공은 `200 OK`와 현재 공지 상세와 같은 전체 `AnnouncementResponse`다. `ARCHIVED`만 `PUBLISHED`로 복구하며 `SCHEDULED`로 되돌리지 않는다.
- Time/status: 이미 게시됐다 보관된 공지는 원래 `publishedAt`을 그대로 보존한다. 게시 전에 예약 상태로 보관되어 `publishedAt`이 null인 공지는 injected server `Clock`의 restore instant를 `publishedAt`으로 기록한다. 같은 요청 반복은 첫 200 뒤 두 번째 요청이 non-ARCHIVED이므로 `409 ANNOUNCEMENT_STATUS_CONFLICT`다.
- Boundary: missing, deleted, other-campus ID는 `404 ANNOUNCEMENT_NOT_FOUND`; non-ARCHIVED는 `409 ANNOUNCEMENT_STATUS_CONFLICT`; 권한 부족은 `403 ANNOUNCEMENT_MANAGE_FORBIDDEN`이다. Restore는 FCM 알림, announcement outbox 생성/재발송, attachment ORPHANED 전환, R2/network 호출을 하지 않는다. 기존 본문, category, author, ordered image/PDF links, media READY 상태, uploader, campus는 보존한다.
- Concurrency: 캠퍼스/권한 검증 뒤 announcement row를 pessimistic write lock으로 잡아 restore/delete/archive/publish 경쟁을 직렬화한다. 단일 DB transaction 안에서 상태와 게시 시각을 변경하며, 이후 실패는 전체 rollback한다. Restore/delete race는 lock 순서에 따라 restore 성공 후 delete 409 또는 delete 성공 후 restore 404만 허용한다.
- Verification: Production 변경 전에 domain/service/controller/REST Docs/integration/rollback/concurrency RED를 커밋했고, 최소 GREEN에서 focused 38-test announcement suite를 통과했다. 최종 repository gate는 `./gradlew --no-daemon test build asciidoctor`로 확인한다.

### 2026-08-03 - Issue #236 Previous-Year Recap Snapshot

- Context: 로그인 복원 뒤 새해 첫 실행에서 직전 연도 활동을 한 번 자동 표시하고, 1월 1일부터 14일까지 홈에서 다시 열 수 있는 매년 재사용 가능한 회고가 필요하다. 기기 변경, 재설치, 다중 기기에서도 표시 완료 상태가 중복되면 안 되며 원본 retention이나 후속 수정이 과거 회고를 바꾸면 안 된다.
- Decision: 서버 `Clock`과 `Asia/Seoul`로 `currentYear - 1`을 정하고, 첫 GET에서 `(user_id, recap_year)` UNIQUE snapshot을 `REPEATABLE_READ` transaction 안에서 idempotent하게 만든다. POST presented가 snapshot을 처음 만들 때도 outer transaction은 같은 isolation을 사용한다. concurrent first request는 user row lock, unique constraint, snapshot unique/transient DB conflict만 대상으로 한 최대 3회 transaction retry로 하나만 확정한다. 실제 화면 렌더 뒤 POST presented를 호출하며 최초 시각만 보존한다. 자동 표시는 presented 전까지 한 번 가능하고, 홈 카드는 데이터가 있는 경우에만 1월 1일 00:00:00부터 1월 14일 23:59:59까지 노출한다. 1월 15일 이후라도 미표시 사용자의 자동 표시는 가능하다.
- Decision: 활동과 ACTIVE 캠퍼스 여정이 모두 없으면 zero payload, `hasRecapData=false`, `shouldAutoPresent=false`, `homeCardVisible=false`를 반환한다. 이 상태의 valid presented 요청은 200 no-op이며 표시 시각을 만들지 않는다. `mostActiveMonth` 동률은 가장 이른 월이고 전 달 0이면 null이다.
- Decision: 캠퍼스 여정은 현재 ACTIVE membership 중 `campus_members.joined_at`이 회고 연도 종료 exclusive 경계보다 이전인 row만 사용한다. Asia/Seoul 기준 12월 31일 가입은 포함하고 다음 해 1월 1일 가입은 제외하며, current rejoin 의미를 유지한다. 기도는 `submitted_at IS NOT NULL`인 submission을 `prayer_weeks.week_start_date` 연도로 귀속해 distinct week/season을 집계한다. 투표 참여와 PollType별 통계는 최종 제품 shape에서 제거하고, 본인의 삭제되지 않은 댓글 작성 수만 `commentActivity.writtenCount`로 집계한다.
- Privacy/Performance: 응답과 snapshot에는 기도문, 투표 참여·선택·메모·댓글 본문, 이메일, 계좌 정보, 개별 청구, JWT/session/device/FCM token을 저장하거나 반환하지 않는다. 경건 기록에 연결된 본인 `PENALTY` 중 `PAID`/`UNPAID` count와 원 단위 amount만 immutable summary로 보존한다. 집계 adapter는 활동 row 수와 무관한 고정 6개 query 경계를 유지한다.
- Aggregation clarification: 다중 ACTIVE 캠퍼스에 같은 날짜/달력 주차 기록이 있어도 경건 일별·경건 제출 주차·기도 제출 주차는 날짜 또는 `weekStartDate` 기준으로 한 번만 센다. 기도 참여 시즌 수는 `seasonId` distinct를 독립적으로 유지한다.
- Schema/Integration: #237 V14, corrected #236 V15, #238 V16, compact archive V17, cleanup retry/lease V18을 물리적으로 분리한다. V15 snapshot과 campus child, V17 archive/coverage는 explicit RLS와 fail-closed FK/CHECK를 사용한다. 최종 V15 SQL SHA-256은 `bd7b956e8aba48d9c21dd0cd113cb09170aed41023002318aa723489d12dfb34`이며, 전용 PostgreSQL 17에서 clean V1→V18과 보호된 V13/V15/V16/V17/V18 upgrade 9건을 재검증했다.

### 2026-08-02 - Issue #233 Authenticated Password Change

- Context: The app already supports email-code password reset for a user who forgot the password, but the signed-in profile screen needs a separate flow that proves knowledge of the current password without another email challenge.
- Decision: Add `PATCH /api/v1/users/me/password` with Access Token authentication and exact request fields `currentPassword` and `newPassword`. The frontend owns new-password confirmation and sends only those two fields. Passwords are not trimmed.
- Decision: Reject an incorrect current password with `400 AUTH_CURRENT_PASSWORD_MISMATCH` and reject reuse of the current password with `400 AUTH_PASSWORD_CHANGE_SAME_PASSWORD`. Success changes the BCrypt hash, increments `tokenVersion`, deletes every Refresh Token session, returns no tokens, and requires the client to clear authentication state and return to login.
- Boundary: The operation keeps email, name, role, active state, campus memberships, and FCM tokens unchanged. A Refresh Token store failure rolls back the database mutation. The existing email-verification password-reset flow remains the only forgot-password recovery path.
- Verification: Production implementation follows a test-first RED for the missing API/service/errors, followed by focused controller, service, transaction, and structure tests. REST Docs and the full repository gate are required before integration.

### 2026-07-18 - Issue #196 Targeted After Measurement Adoption Boundary

- Context: The conditional `h05` before run identified `prayer_groups` as the confirmed bottleneck at p95 `536.956 ms` and `16.927 req/s`, with about 586 captured SQL statements per request. Production bulk loading reduced the focused 25-member fixture from 32 prepared statements to at most 7 without changing the API contract.
- Decision: Compare the preserved `h05` before with three sequential after runs using the exact `5 VU / 2m` workload and the same Hibernate SQL DEBUG, `show_sql=false`, `format_sql=false`, bind/extract OFF instrumentation. Adopt the three-run after median for a resume case study only. Exclude the accidental 10-VU run and the non-instrumented run from the comparison.
- Result: Across 63,713 after requests, correctness was 100% and failure rate was 0. Median p50/p95/p99 were `20.977 / 32.520 / 46.306 ms` and throughput was `179.561 req/s`, corresponding to `91.6% / 93.9% / 95.0%` latency reductions and `10.61x` throughput versus the conditional before.
- Impact: These values may be used as an explicitly scoped resume performance case study. They are not a production SLO, capacity guarantee, or proof of fully isolated traffic because the before is one conditional shared-stack run and the after is a three-run median. The full 27-endpoint suite is not repeated; API, DTO, authorization, ErrorCode, Flyway, schema, index, dependency, and frontend remain unchanged.

### 2026-07-17 - Issue #192 Current-Develop Settlement Batch Optimization

- Context: The validated current-develop before bundle `EXEC192_BEFORE_CURRENT_BUNDLE_R` binds source `6796ed146244d8f3f5b5dd7048ebe16865084a97` and target SHA-256 `7e7dd3a430666112f5139cf3950f2b859dc49034c5306cee0b770cfe80b8cb62`. It passed all four mode correctness and failure gates while retaining `conditional-boundary-only`, `accepted=false`, and `automaticAdoption=false` to distinguish validated numeric evidence from machine-proven continuous exclusivity.
- Decision: Use the R bundle as the authoritative before evidence for the identical integration-after protocol. Replace per-response COFFEE/MEAL payment-account and existing-charge repository lookups with one account validation, one source-ID-set pessimistic lock lookup ordered by charge ID, and one collection-save boundary per settlement. Keep all existing scalar charge APIs compatible.
- Decision: Preserve the outer settlement transactions, COFFEE UNPAID update and terminal-charge preservation, MEAL duplicate `409` and final flush/rollback behavior, #200 creator/duty/account ownership and lock order, amount/source/snapshot correctness, and the existing unique constraint as the final concurrent-insert defense.
- Impact: Controller paths, request/response DTOs, HTTP/ErrorCode contracts, frontend, dependencies, Flyway, and indexes do not change. `saveAll` is a repository collection boundary only; the project does not claim JDBC insert batching or fewer required charge INSERT rows. Issue #194 retains the execution-plan candidate `charge_items(campus_id, payment_category, source_type, source_id)` for later independent validation. Actual after measurement remains a one-server/one-load PM integration step.

### 2026-07-16 - Issue #193 Measured Login Update Counter ACK

- Context: Actual-before L은 fresh measured login 뒤 users VACUUM(ANALYZE)을 완료했지만 users `nModSinceAnalyze`가 before 0, after 1로 바뀌어 rejected됐다. Login commit은 `06:00:28.808644Z`, VACUUM start는 약 166ms 뒤인 `06:00:28.974009Z`, ANALYZE는 `06:00:29.394557Z`여서 app backend의 login UPDATE 통계가 flush되기 전에 maintenance를 시작한 것이 원인이었다.
- Decision: Measured login 직전에 `pg_stat_user_tables.users.n_tup_upd`를 canonical decimal string으로 캡처한다. Login 뒤 immutable PostgreSQL container ID의 read-only polling으로 counter가 정확히 `before + 1`이 될 때만 ACK하고 users VACUUM(ANALYZE)을 실행한다. `+0`은 기존 pre-boundary 1초/최대 5회 상수 안에서만 pending이며 감소, `>+1`, timeout, malformed, PostgreSQL bigint 범위 초과는 fail-closed한다.
- Impact: ACK와 users VACUUM은 PostgreSQL before evidence·stable pair·counter·resource sampler·measured window보다 앞선다. Existing fixture 3-table VACUUM, users stable-pair, measured strict maintenance continuity는 완화하지 않는다. Measured 6,000 GET 자체의 users write 증거로 해석하지 않으며 `src/main`/Flyway는 변경하지 않는다. 다음 actual은 fresh M namespace만 사용한다.

### 2026-07-16 - Issue #193 Fresh Measured Login Users Statistics Boundary

- Context: Actual-before K는 fixture와 exact 3-table VACUUM(ANALYZE), warmup, measured 16 cases/6,000 HTTP를 failure 0으로 완료했다. 그러나 fresh measured login의 `users.last_login_at` UPDATE 통계가 1초 간격 stable pair의 72/72 뒤에 늦게 반영돼 after 73으로 나타났고 strict integrity가 false contamination으로 측정을 거부했다.
- Decision: Fresh measured login 직후, PostgreSQL before evidence·stable pair·counter·resource sampler·measured window보다 앞서 immutable PostgreSQL container ID의 psql stdin으로 exact `VACUUM (ANALYZE) users;`를 실행한다. `pg_stat_clear_snapshot()`은 관측 backend snapshot만 비우고 `pg_stat_force_next_flush()`는 호출 backend 자체 통계만 대상으로 하므로 app login backend의 지연 통계를 확실히 정리하는 대안으로 채택하지 않는다.
- Impact: Users-only completion evidence를 남기며 다른 table, `VACUUM FULL`, `FREEZE`, 데이터/schema/index/Flyway/config/reset/extension 변경은 금지한다. Fixture COMMIT 직후 exact 3-table VACUUM(ANALYZE), measured-login 뒤 stable-pair, measured before/after strict maintenance continuity는 모두 유지한다. 다음 actual은 fresh L namespace만 사용한다.

### 2026-07-16 - Performance Measurement Code Approval Boundary

- Context: 반복되는 before 측정에서 fixture, k6, validator, fake/static test와 evidence 문서의 measurement-boundary 결함을 PM 리뷰로 보정해야 했지만, 매 보정마다 별도 사용자 승인을 기다리면 유효 baseline 확보가 지연됐다. 반면 production backend와 Flyway 변경은 실제 제품·데이터 계약에 영향을 줄 수 있어 사용자 결정권을 유지해야 한다.
- Decision: 이 성능 개선 목표에서는 k6, fixture, validator, test, docs 같은 측정·테스트 코드 수정은 별도 사용자 승인 없이 TDD와 PM 리뷰로 진행한다. `src/main` production backend 또는 Flyway 변경은 실제 편집 직전에 사용자 승인을 받는다. 실제 Docker/DB/k6 실행 권한은 각 실행 지시를 따르며, 이 승인 경계 변경만으로 개발 세션이 load를 실행할 수는 없다.
- Impact: 측정 harness 보정은 RED→최소 GREEN→정적/fake 검증→PM finding 0 절차로 진행한다. Production 최적화와 migration은 유효 before baseline 중간보고 후에도 사용자 승인 전에는 편집하지 않는다.

### 2026-07-16 - Issue #193 Pre-measurement Fixture VACUUM ANALYZE Boundary

- Context: Actual-before J는 fixture COMMIT 뒤 exact 3-table ANALYZE로 `charge_items` autoanalyze 오염을 막았지만, measured 시작 약 45초 뒤 `campus_members` insert-triggered autovacuum이 실행돼 strict measurement-integrity에서 rejected됐다.
- Decision: 기존 ANALYZE-only command를 fixture COMMIT 직후, dataset binding·expectations·preflight·warmup보다 앞서 immutable PostgreSQL container ID의 psql stdin으로 실행하는 exact `VACUUM (ANALYZE) campus_members, payment_accounts, charge_items;`로 교체한다. `users`나 다른 table, `VACUUM FULL`, `FREEZE`, 데이터 삭제, schema/index/Flyway/config/reset/extension 변경은 허용하지 않는다.
- Impact: Completion evidence에는 exact 3-table 목록을 유지한다. 이 command는 `prepare-fixture.sql` transaction 밖에서만 실행하고 measured before/after의 vacuum/analyze/auto-maintenance continuity 검증은 완화하지 않는다. 다음 실제 실행은 재사용하지 않는 fresh K namespace만 사용할 수 있다.
### 2026-07-17 - Issue #195 Member List Bulk Optimization Authorized And Implemented

- Authorization: the user's latest decision supersedes the earlier proposal-only boundary and authorizes the two evidence-backed production backend changes without another approval wait. Actual Docker/DB/HTTP/k6 load remains prohibited while the other performance session is active; no after result or improvement claim is produced here.
- TDD evidence: a focused integration RED preserved admin authorization, page metadata/order, campus membership order, ACTIVE filtering, response values, and measured JDBC statement counts after clearing the persistence context. Current source failed with 8 statements for a four-user/two-campus admin page and 6 statements for a four-member campus list; both contracts require a constant 3 statements.
- Implementation: `AdminUserManagementService.searchUsers` now loads one left-joined membership/campus projection for the page user IDs, groups by user ID, and maps the original `Page` in its original order. The projection keeps membership ID ascending and explicitly raises `CAMPUS_NOT_FOUND` when a membership's campus cannot be resolved. `getUser` and role mutation retain the original single-user path and locking semantics.
- Implementation: `CampusMemberManagementService.getCampusMembers` retains authorization first and the existing ACTIVE membership/ID-ascending query, then calls the existing `CampusAccessPolicy.getUsers` bulk boundary once and maps users back in membership order. Missing-user behavior remains `CAMPUS_MEMBER_NOT_FOUND`; user active status is not newly filtered.
- Boundary: controller paths/queries, DTO fields, page metadata, authorization, ErrorCodes, read-only transaction boundaries, entities, frontend, dependencies, Flyway, and indexes are unchanged. `campus_members (user_id, id)` remains only a separately evidenced #194 query-plan candidate. Status is production-code-ready/after-not-measured; G remains conditional and no numerical target or performance achievement is adopted.

### 2026-07-17 - Issue #195 G Conditional Before Checkpoint And Optimization Boundary

- Actual checkpoint: PM-controlled `EXEC195_BEFORE_20260716_G` reused `PERF_1000_20260716_195_A` / `ISSUE195_BEFORE_20260716_A` and completed measured adoption for all 11 cases with every `failureRate=0`. There was no intermediate case rejection. The only first rejection was the designed `final-classification` exit `2`; final status is `conditional-not-adoptable`, `automaticAdoption=false`, because boundary evidence and the cooperative lock cannot prove absence of transient external shared-stack load. PM confirmed one temporary account was restored to service role `USER` and lock/process count was zero.
- Decision: G may be used only as a conditional shared-stack before baseline for an explicitly reviewed after comparison. It is not automatically adoptable, is not a performance achievement, and must not be presented as resume improvement. Its complete execution/report is non-reusable. No new actual load, production source, Flyway, index, API, frontend, Docker, or DB change is authorized by this checkpoint.
- Evidence: the two directly supported investigation candidates are `admin_users/large_page` (5,068 requests, 42.156241 req/s, p50 231.0955 ms, p95 339.36645 ms, p99 414.71317 ms, max 629.44 ms) and `campus_members/full_list` (5,160 requests, 42.909684 req/s, p50 216.426 ms, p95 338.1192 ms, p99 435.98053 ms, max 904.414 ms). Current source independently shows page-sized per-user/per-campus enrichment in `AdminUserManagementService.searchUsers` and per-member user lookup in `CampusMemberManagementService.getCampusMembers`. This selects proposal scope but does not prove a latency attribution percentage or approve a target.
- Proposal, not approved: for admin user search, bulk-load membership/campus projection rows for the page's user IDs, explicitly preserve missing-campus failure, original page order, and per-user membership ID order. For campus members, keep authorization and the ACTIVE/ID-ascending membership query, then use existing `CampusAccessPolicy.getUsers(...)` / `UserRepository.findCampusUsersByIds(...)` once and map back in membership order. Do not change controllers, DTO contracts, ErrorCodes, transaction boundaries, locks, entities, frontend, admin-campus search, or already-bulk duty assignment search.
- Schema boundary: the first N+1 removal needs no Flyway or index change. The existing `(campus_id, user_id)` unique key supports campus-scoped membership reads and PKs support bulk user/campus reads. Because it does not lead with `user_id`, a later `campus_members (user_id, id)` index may be considered only after a separately approved post-change query plan proves the bulk admin membership scan remains dominant; it is not part of the current proposal.
- TDD/after boundary: before any production implementation, RED must cover size-20/size-100 admin query growth, 1,000-member user query growth, one-bulk-query GREEN caps, mapping/cardinality/order/status/error/authorization preservation, no data query before rejected authorization, and unchanged controller/REST Docs contracts. Any approved after run uses the same workload/cardinality/runtime inputs with a fresh PM-assigned ID, compares all 11 zero-failure cases, and remains conditional unless the user separately approves exclusive/continuous provenance. No numerical performance acceptance threshold is approved.

### 2026-07-16 - Issue #195 Current-Develop Member List Scenario Boundary

- Context: 사용자는 성능 이슈 #192-#199의 test/scenario code는 병렬 보정하되 실제 measurement slot은 PM만 순차 사용하도록 결정했다. 기존 #195 before 시나리오는 최신 `origin/develop`의 #200 권한/담당자 조회, #201 page/archive, #202 RLS, #206 ordering 계약을 다시 대조해야 한다.
- Decision: #195는 `scenario-ready/not-measured` 상태에서 관리자 사용자·캠퍼스 목록과 캠퍼스 멤버·담당자 목록 시나리오만 유지한다. 관리자 목록은 기본 `size=20`, 최대 `size=100`을 분리하고, 멤버 목록은 ACTIVE membership과 ID 오름차순, 담당자 목록은 명시적 `staleOnly=false`와 ACTIVE assignment/ACTIVE membership 및 ID 오름차순을 검증한다. 네 endpoint 모두 지원하지 않는 `includeArchived`를 보내지 않는다.
- Decision: #200 반영 후 담당자 목록의 사용자 조회는 이미 bulk이고 캠퍼스 멤버 목록은 per-member인 current-develop 상태를 그대로 기록한다. #202는 `FORCE ROW LEVEL SECURITY`를 사용하지 않는 owner-JDBC 경계만 정적으로 기록하고 실제 연결 연속성은 승인된 측정까지 미검증으로 둔다. #206 billing tie-break는 #195 endpoint에 적용하지 않으며, #195는 각 API의 명시적 ID 오름차순 계약만 검증한다.
- Impact: production Java/API/권한/응답/오류/트랜잭션/Entity/DB/Flyway/dependency를 변경하지 않는다. test code는 병렬 보정할 수 있지만 fixture/HTTP/DB/Docker/k6 actual load는 PM exclusive window에서만 순차 수행한다. 수치와 개선 성과는 실제 측정 전까지 기록하지 않는다.
- Decision: 실제 fixture/measurement는 manifest의 exact `origin/develop` commit과 runtime-required app/PostgreSQL/Redis Compose service·full image ID·container identity, credential, VUS/duration/cardinality, resource boundary maximum gap, k6 binary를 default 없이 받아야 한다. Fixture는 app/PostgreSQL/Redis를 pre-lock/post-lock/final에, measurement는 세 container와 PostgreSQL server/postmaster를 post-lock, 각 case 전후, final에 exact 연속성 검증한다. Redis local Compose에 없는 password를 발명하지 않는다.
- Decision: k6 direct/`metric.values` summary는 Counter count가 positive safe integer이고 rate가 positive finite여야 한다. k6 v2 custom failure Rate의 실제 shape에서 `value=0`, `passes=0`, `fails=Counter count`를 exact 검증하며 missing/non-finite/string/positive value, positive passes, count mismatch를 모두 거부한다. Resource evidence는 app/PostgreSQL/Redis exact set과 full immutable identity, CPU percent/memory bytes, runtime-approved boundary cadence를 검증한다. Report를 source/target preflight 전에 fresh execution path로 생성하고 이후 최초 실패는 secret-free `first-rejection.json`을 exclusive create해 후속 실패가 덮어쓰지 않는다. 최종 classification은 계속 `automaticAdoption=false`다.
- Decision: 준비 조건이 충족되면 성능 부하는 별도 사용자 재승인 없이 PM이 한 서버 한 load로 순차 실행한다. Workload 값은 script default로 만들지 않고 PM handoff가 runtime input으로 명시한다. 현재 추천값은 warmup `1 VU/30s`, measured `10 VU/2m`, failure `0`, token safety `120s`, resource boundary maximum gap `10s`이며 측정값이나 자동 채택 성과로 기록하지 않는다.
- Decision: #192의 999 ACTIVE USER + 1 ADMIN dataset은 정확히 1,000 ACTIVE USER/admin-excluded인 #195 계약에 재사용하지 않는다. Fresh `PERF_1000_20260716_195_A` namespace는 공개 signup만으로 1,000 ACTIVE USER를 additive 생성하고, collision/partial/ADMIN/inactive/duplicate는 cleanup 없이 ID 전체를 폐기한다. Provisioning은 signup 직후 ADMIN을 다시 로그인하고, JWT `exp`가 runtime safety margin 이상인지 검증하며 verification page/detail마다 재검사해 부족할 때만 refresh한다. Credential/token/expiry 원문은 report/log/argv/commit에 남기지 않고 refresh 횟수만 manifest에 허용한다.
- Decision: #195 provisioning, relationship fixture, measurement runner는 모두 같은 absolute `PERF_REPORT_ROOT`를 default 없이 요구한다. 누락/상대경로는 Docker/identity/lock/API/report mutation 전에 거부한다. Fixture는 `TOKEN_SAFETY_MARGIN_SECONDS`를 추가로 요구하고 모든 authenticated request 직전에 JWT `exp` margin을 검사한다. Report directory는 Docker/API보다 먼저 exclusive reserve하며, 이후 최초 실패는 partial mutation count와 refresh count만 담은 secret-free receipt로 보존한다. Runtime target은 numeric loopback과 app published port를 exact bind하고, app/PostgreSQL/Redis 모두 full 64-hex container/image identity, name, `StartedAt`, Compose project/service, ports schema를 fail-closed 검증한다. Docker inspect child에는 명시적인 non-secret environment allowlist만 전달한다.
- Actual checkpoint: `PERF_1000_20260716_195_A` provisioning과 `ISSUE195_BEFORE_20260716_A` fixture manifest는 성공 상태로 보존한다. `EXEC195_BEFORE_20260716_A`는 첫 warmup 초기화에서 k6가 JSON을 JavaScript module로 파싱해 exit `107`이 되었고 warmup/measured HTTP는 모두 0이므로 execution/report 전체를 재사용하지 않는다. JSON contract의 내용·hash·권한·시나리오 의미는 유지하고 k6 init context에서 `open()` 후 `JSON.parse`한다. 이 보정 뒤 fresh B를 시도했으며 그 rejected 결과와 다음 C handoff는 아래 checkpoint를 따른다.
- Actual checkpoint: `EXEC195_BEFORE_20260716_B`는 첫 warmup 142 HTTP/checks 1,278/1,278/failure 0 뒤 summary shape 불일치로 거부됐고 measured HTTP는 0이다. Warmup latency는 rejected diagnostic일 뿐 baseline/성과로 채택하지 않는다. B report는 read-only/non-reusable로 보존하고 actual k6 v2 Trend/Counter/Rate를 direct/values와 warmup/measured, normalized DB integrity까지 검증한다. PM은 B 중단 뒤 service role 0 ADMIN/1 USER를 DB에서 확인했다. 다음 actual load는 preserved dataset/fixture와 fresh `EXEC195_BEFORE_20260716_C`만 사용한다.
- Actual checkpoint: `EXEC195_BEFORE_20260716_C`는 첫 `admin_users/first_page` warmup 530 HTTP/checks 4,770/4,770/failure 0 뒤 measured-before DB evidence까지 진행했지만, 첫 resource snapshot에서 Docker의 rounded decimal binary display를 exact integer byte로 오인해 거부됐고 measured HTTP는 0이다. Warmup latency는 rejected diagnostic일 뿐 baseline/성과로 채택하지 않는다. C report는 read-only/non-reusable로 보존하고 다음 actual load는 preserved dataset/fixture와 fresh `EXEC195_BEFORE_20260716_D`만 사용한다. PM은 C 중단 뒤 broad `perf-195` 임시 계정 service role 0 ADMIN/3 USER를 확인했다.
- Decision: Docker CLI의 rounded memory display는 지원 단위 `B/kB/KB/KiB/MB/MiB/GB/GiB/TB/TiB`를 적용한 뒤 deterministic nearest whole byte로 정규화하고, 결과가 non-negative safe integer인지 fail-closed 검증한다. NaN/Infinity/negative/unsupported/overflow는 계속 거부하며 resource identity, percent, cadence, threshold 계약은 완화하지 않는다.
- Decision: k6 setup은 runtime access token의 존재를 검증하되 setup data로 반환하지 않는다. VU는 process-scoped runtime token을 직접 사용하고 installed-k6 no-HTTP micro serialization에서 sentinel이 summary에 남지 않음을 검증한다. Preserved C summary의 historical token-bearing evidence는 수정·복사·채택하지 않는다.
- Actual checkpoint: `EXEC195_BEFORE_20260716_D`는 첫 `admin_users/first_page` warmup 542건과 measured 7,197건/checks 64,773/64,773/failure 0을 완료했지만 after table artifact의 psql `Output format is csv.` status contamination으로 `measured-evidence-after`에서 거부됐다. 모든 D 수치는 rejected diagnostic일 뿐 baseline/성과로 채택하지 않는다. D report는 read-only/non-reusable로 보존하고 다음 actual load는 preserved dataset/fixture와 fresh `EXEC195_BEFORE_20260716_E`만 사용한다. PM은 D 중단 뒤 broad `perf-195` 임시 계정 service role 0 ADMIN/4 USER와 lock/process cleanup을 확인했다.
- Decision: table-counter/runtime-integrity의 `\pset` 경로와 available/unavailable query의 `-At` 경로를 포함한 collector의 모든 psql invocation은 quiet machine-output mode를 사용한다. Status banner만 제거하며 SQL, metric, DB window, observer 순서, pgss availability/query 의미는 변경하지 않는다. Preserved B/C/D evidence는 수정·정리·출력하지 않는다.
- Actual checkpoint: `EXEC195_BEFORE_20260716_E`는 첫 `admin_users/first_page` warmup 334건과 measured 4,200건/checks 37,800/37,800/failure 0을 완료했지만 DB-wide `xact_commit` delta 8,427이 request-derived 8,400 + before observer 1보다 26 커 exact-equality gate에서 거부됐다. E latency/throughput은 rejected diagnostic일 뿐 baseline/성과로 채택하지 않는다. E report는 read-only/non-reusable로 보존하고 다음 actual은 fresh `EXEC195_BEFORE_20260716_F`만 사용한다.
- Decision: shared runtime의 `pg_stat_database` commit은 source-unattributed DB-wide supporting evidence로 취급한다. Measured와 동일 duration의 immediately-before idle control을 추가하고 database/observer/capture timestamps/external sessions/planner/4-table maintenance/xact/observer overhead를 strict schema로 전후 검증한다. Control rollback, external activity, planner/maintenance drift, malformed/missing evidence는 fail closed다. Control commit의 before observer 1을 strict BigInt로 분리하되 background delta를 measured에서 빼거나 비례 추정하지 않는다. Control adoption은 case, `MEASURED_DURATION`, `controlCommitDelta = backgroundCommitDelta + 1`, control-after 완료 시각과 measured-before snapshot 인접성을 runner input에 exact bind하고 항상 `supporting-only`, `automaticAdoption=false`로 유지한다. Measured가 request-derived 최소 commit보다 작으면 거부하고 초과분은 source-unattributed `conditional-not-adoptable`로 기록한다. Deployed `FAITHLOG_SCHEDULER_ENABLED=false`이므로 background를 FaithLog scheduler에 귀속하지 않는다.
- Decision: case 최초 JWT lifetime gate는 warmup + idle control measured duration + measured load duration + runtime safety margin을 모두 포함한다. 권고값에서는 `30s + 2m + 2m + 120s = 390s`이며 control snapshot/observer write 전에 fail closed한다. Measured 직전 gate는 기존 measured + safety의 strict remaining 계약을 유지한다. Control JSON conversion Node child는 credential을 상속하지 않는 최소 환경만 사용한다.
- Verification checkpoint: PM 독립 issue-local 실행은 preserved B/C/D/E absolute evidence와 installed k6를 연결해 `80/80` GREEN이었다. Node/Bash/JSON/diff-check와 production/Flyway/Gradle diff 0도 GREEN이었다. 별도 2초 read-only control smoke는 `supporting-only`, `automaticAdoption=false`, raw commit delta 2, background/unattributed 1, rollback 0, subtraction false였으며 baseline 수치가 아니다. Fresh F는 Issue #208 공통 audit 완료 전 실행하지 않는다.
- Actual checkpoint: `EXEC195_BEFORE_20260716_F`는 `admin_users/first_page`를 완료하고 `admin_users/middle_page`의 `measured-evidence-after`에서 첫 거부됐다. 해당 warmup은 HTTP 837/failure 0, measured는 12,342 requests/checks 전부 통과/failure 0이었고 DB-wide commit은 `167243 -> 191857`(delta 24,614), rollback 0이었다. 기존 `requestCount × 2 + observer = 24,685` gate보다 71 작아 거부됐으며, immediately-before same-duration idle control은 raw commit 40/background-unattributed 39/rollback 0이었다. F의 latency/throughput과 모든 수치는 rejected diagnostic-only이고 baseline·개선 성과·자동 채택에 사용하지 않는다. F report는 전체 read-only/non-reusable로 보존하며 임시 계정 service role `USER` 원복과 lock/process cleanup을 PM이 확인했다.
- Decision: current-develop source는 `UserAccessTokenVersionChecker.findById`의 인증 repository transaction 1회와 `AdminUserManagementService.searchUsers @Transactional(readOnly=true)`의 endpoint transaction 1회로 요청당 2회 경계를 유지한다. 그러나 runner의 after DB snapshot은 F measured-end보다 6.304초 뒤이고 measured 최대 HTTP duration 4.117026초보다도 늦었는데도 71이 부족했다. PostgreSQL cumulative statistics의 per-backend publication을 observer가 다른 backend에 강제할 수 없고 `pg_stat_database` DB-wide counter는 source attribution도 제공하지 않으므로, 임의 settle delay/default/tolerance로 `2/request`를 증명했다고 간주하지 않는다.
- Decision: measured DB integrity의 증명 가능한 보수적 최소 lower bound는 strict BigInt `requestCount + before observer 1`로 한다. Source-derived `2 × requestCount + observer`는 diagnostic field로 유지하되 acceptance gate로 쓰지 않는다. 최소를 넘는 모든 commit은 source-unattributed `conditional-not-adoptable`이며 idle-control background subtraction, 비례 추정, tolerance, scheduler/request attribution, 성과 채택을 금지한다. Malformed/missing evidence, rollback, boundary external activity, planner/maintenance drift, control case/duration/timestamp/arithmetic mismatch는 계속 fail closed이고 `automaticAdoption=false`다. 다음 actual은 preserved dataset/fixture와 fresh `EXEC195_BEFORE_20260716_G`만 사용한다.
### 2026-07-17 - Issue #198 Isolated Snapshot-Restore Measurement Policy

- Context: additive #198 dummy token/log/dedupe state made repeated samples non-equivalent. The user approved test/scenario decisions without another approval and selected `EXPECTED_WARMUP_SAMPLES=1`, `EXPECTED_MEASURED_SAMPLES=10`, and `CUMULATIVE_STATE_STRATEGY=snapshot-restore`. PM review required the snapshot to represent the fully prepared fixture, not an empty pre-fixture database, and required Redis TTL intent to be proven without comparing elapsed live PTTL.
- Decision: use one fresh canonical fixture with `sampleKind=canonical`, capture one PostgreSQL/Redis snapshot after fixture preparation, then restore and fingerprint that snapshot before each of 1 warmup and 10 measured samples. Per-sample manifest views only change local `sampleKind` metadata and never create business rows. PostgreSQL receipt binds dump SHA-256, relevant table cardinality, and ordered-row fingerprint. Redis receipt separately binds key/value SHA-1 and TTL-intent SHA-1 over key, serialized value, and captured PTTL; restore Lua returns the exact stored metadata…61121 tokens truncated… do not force a full workload rerun when the core k6 summary and correctness evidence are intact.
- Representative integration after measurement is limited to the two #195 N+1 paths. The other optimizations retain focused SQL/query-count tests and #194 EXPLAIN evidence instead of repeating all issue-local runners.
### 2026-07-16 - Issue #192 Latest-develop Scenario Compatibility

- User decision: Performance issues may correct k6/fixture/validator/test/docs code in parallel, while all actual fixture/DB/HTTP/k6 loads remain PM-owned and execute sequentially on one server.
- Issue #192 scope: Preserve the valid before bundle and completed production optimization; update only latest-develop scenario and comparison contracts. Do not rerun before/after in the issue branch.
- Current-develop drift: `origin/develop` `6796ed1` adds #206 stable charge paging. The settlement verifier must use the same `created_at DESC, id DESC` ordering. #202 RLS does not change the direct JDBC observer; DB identity remains runtime target-bound and no Supabase Data API credential is used.
- Target/comparison decision: `TARGET_CONTRACT` is runtime-required with no runner default. Before/after comparison requires exact workload and target-role continuity, distinct source/immutable target identities, and preserves four mode metrics separately.
- Status: scenario-ready / not measured. Actual Docker/DB/HTTP/fixture/k6 and production/Flyway/dependency changes were zero in this compatibility pass.
- Integrity audit: The runner now validates the exact target top-level schema, source commit, Flyway version, immutable service/image/runtime identity, resource metadata, and approved workload before runtime capture or fixture work. No implicit target fallback is allowed.
- k6 evidence decision: Metadata-bearing v2 wrappers must match their Counter/Rate/Trend type and contains identity. Wrapped Counter rate must be finite, and wrapped Rate passes/fails totals and ratio must match the exact measured request/check counts. Approved metadata-free direct/values fixtures remain supported.
## 2026-07-16 - Issue #193 관리자 청구 집계 production 최적화 승인 및 snapshot 경계

- 사용자는 O actual-before 수동 채택 뒤 `listAdminCampusCharges`와 `listAdminCampusChargesForMyAccounts`의 production 최적화를 승인했다.
- 두 endpoint는 전용 `AdminChargeAggregationQueryPort`를 사용하며 DB summary 1회, member aggregate/page 1회, distinct member count 1회의 고정 query 구조를 사용한다.
- `charge_items`를 ACTIVE `campus_members`와 active `users`에 결합하고 status별 합계, 최신 생성 시각, user/category/status/account/archive/keyword predicate, 기존 정렬과 pagination을 DB에서 처리한다.
- 멤버별 User lookup과 전체 `ChargeItem` entity materialization은 두 endpoint에서 제거한다. API path/query/response/page metadata, 권한, account ownership, archive cutoff 의미는 변경하지 않는다.
- 세 statement가 동시 write 사이에서 서로 다른 `READ_COMMITTED` snapshot을 볼 수 있는 반례가 재현됐으므로, 승인된 두 public endpoint에만 read-only `REPEATABLE_READ`를 적용한다. 다른 query service와 member-detail/MEAL 경로의 isolation은 변경하지 않는다.
- 금액 projection은 long으로 보존한 뒤 기존 API int 경계에서 exact 변환하고 overflow는 fail-fast한다. Nullable latest-created projection과 기존 primary 방향 + userId asc tie 계약을 유지한다.
- Issue #193에서는 index/Flyway/dependency/schema/API 변경을 금지한다. index 후보와 PostgreSQL EXPLAIN 실행계획은 #194 통합 단계에만 전달한다.
- After 성능 측정은 개별 branch에서 실행하지 않고 PM integration branch에서 before와 동일 조건으로 수행한다. 그 전에는 production 구현 완료를 성능 개선 수치로 해석하지 않는다.
- PM 독립 검증은 전체 build `BUILD SUCCESSFUL in 3m16s`, asciidoctor `BUILD SUCCESSFUL in 17s`, Node scenario/runner 36/36, issue-local node/diff check 통과를 확인했다. 현재 test XML은 tests 561, failures 0, errors 0, skipped 3이다.

### 2026-07-17 - Resume Performance Measurement Uses A Lean Targeted Protocol

- Context: The issue-local performance harnesses had grown into production-forensics systems. Repeated full-scope runs were spending more time on collector portability and evidence-boundary failures than on finding and removing backend bottlenecks.
- Decision: Preserve the existing strict before evidence, but do not repeat full endpoint inventories for resume claims. Select only one to three source-confirmed bottleneck endpoints, keep one fixed fixture and identical runtime workload, require correctness and zero failures, and compare three before runs with three after runs. Record p50/p95/p99, throughput, and the relevant SQL/repository-call count. A collector-only failure does not require a full rerun when HTTP correctness, failure rate, timing, and SQL evidence for the target endpoint are intact; server or correctness failures still invalidate the run.
- Impact: The current #196 `h05` run is the last 27-endpoint full-scope attempt. Future #196/#199 and integration-after work uses targeted runs. Resume wording remains blocked until the optimized code is deployed and the same target endpoints complete the three-run after comparison.

### 2026-07-17 - Issue #196 Prayer Group List Bulk Lookup

- Context: The preserved #196 `h05` `prayer_groups` endpoint completed 2,035 requests with zero failures, p95 536.956 ms, throughput 16.927 requests/s, and about 586 SQL statements per request. The dominant query was a scalar `users.id` lookup repeated for group members.
- Decision: Keep the API, authorization, ordering, transaction, error, and DTO contracts unchanged. Load active members for all returned groups once and resolve all member users once, then assemble results in existing group and member order. No Flyway, index, dependency, or frontend change is required.
- Impact: The regression test reproduced 32 prepared statements for 25 users across two groups against a maximum of 7. The bulk implementation passes that contract and the Prayer service/structure/REST Docs regression suite. After latency and throughput are not yet measured.

## 2026-07-20 - Expose poll user-option-add setting in query responses

- Decision: `GET /api/v1/campuses/{campusId}/polls`의 각 항목과 `GET /api/v1/campuses/{campusId}/polls/{pollId}` 상세 응답에 저장된 `allowUserOptionAdd` boolean을 필수 필드로 반환한다.
- Decision: 프론트는 누락값을 추론하지 않고 이 필드가 `true`일 때만 사용자 선택지 추가 UI를 노출한다. 실제 추가 요청의 ACTIVE 멤버, OPEN 상태, 활성 시간 구간 검증은 서버가 계속 수행한다.
- Decision: API 경로, 요청 DTO, 권한, 오류 코드, Entity, DB/Flyway 계약은 변경하지 않는다.

### 2026-08-02 - Issue #227 authenticated self-service name update contract

- Context: A signed-in user needs to edit the display name shown in FaithLog without coupling the operation to email verification or the password-reset workflow.
- Decision: Add `PATCH /api/v1/users/me` with Access Token authentication. The JSON request is `{ "name": "새 이름" }`; the request DTO trims the value and validates 1 to 100 characters after trimming. Null, blank, whitespace-only, and more than 100 characters reuse the existing `400 GLOBAL_VALIDATION_FAILED` contract. Missing authentication, Refresh Token Bearer, and inactive users reuse `401 AUTH_UNAUTHORIZED`. Repeating the same name succeeds idempotently. Success returns the full updated `UserMeResponse`, including ACTIVE campus memberships, through `ApiResponse.success`.
- Boundary: Only `users.name` changes. Email, password hash, role, active state, token version, campus memberships, FCM tokens, and sessions remain unchanged. Password changes continue to use the existing email-verification password-reset flow. Names remain non-unique, so there is no DB, Flyway, dependency, token, FCM, session, Docker, or deployment change.
- Implementation: Keep `UserMeQueryService` read-only and introduce a dedicated transactional `UserNameCommandService` that locks and rechecks the authenticated user, changes only the name, and reuses the existing complete `UserMeResult` assembly.

## 2026-08-02 - Issue #228 Billing month-boundary test fixture

- Context: Two Billing controller/REST Docs tests queried July 2026 while their `ChargeItem.createdAt` and `paidAt` values came from the host clock. Both tests started failing in August even though Billing production code had not changed.
- Decision: Keep the July API example and bind both fixture charge creation timestamps and the paid charge completion timestamp to `2026-07-16T00:00:00Z`. Use the existing domain `markPaid(Instant)` boundary for `paidAt` and a test-only JDBC fixture update for the JPA-managed, non-updatable `created_at` column.
- Boundary: Do not use the current month, sleep, tolerance, production clock changes, or weaker assertions. Production/API/DB/Flyway/dependency behavior remains unchanged; the fix is test-only and deterministic across month boundaries.
### 2026-08-03 - Issue #237 Campus Announcements, Durable Publication, And Private R2 Media

- Decision: Implement campus-scoped general announcements with manager-defined categories. The default category is `일반`, `#3B82F6`, display order 0; category names are trim-normalized and case-insensitively unique per campus.
- Decision: Use `SCHEDULED`, `PUBLISHED`, and `ARCHIVED`. Scheduled announcements can be edited, rescheduled, or archived before publication. Published announcements can edit content/category/pinning without changing `publishedAt` or resending notification.
- Decision: Non-null `publishAt` is future-only in both DTO and command-service boundaries. Expired or missing scheduled times return `GLOBAL_VALIDATION_FAILED`; they must not escape as an unhandled exception.
- Decision: Deactivation prevents selecting a category for a new announcement or changing an existing announcement to it. An announcement already using that category may still edit content and publish manually or by scheduler.
- Decision: Publication and one unique outbox row share one DB transaction. Manual and scheduled publication reuse the same path. The push title is `새 공지가 등록되었어요`, body is `[{categoryName}] {announcementTitle}`, author is excluded, and data payload contains only event/campus/announcement/category IDs.
- Decision: Use private Cloudflare R2 with AWS SDK S3 `2.46.8` and Thumbnailator `0.4.21`. The app uploads through short-lived Presigned PUT and the backend synchronously finalizes outside DB transactions. Accept JPEG/PNG up to 5MiB and 4096x4096, generate EXIF-free 480px thumbnail and 1600px detail variants, then delete the temporary original only after READY commits.
- Decision: Use explicit PENDING/PROCESSING/READY/FAILED/ORPHANED media states. READY assets are single-announcement use; removed assets become ORPHANED and cleanup waits 24 hours. Expired PENDING/FAILED and aged ORPHANED cleanup rechecks locked state before deleting DB rows and never deletes attached READY objects.
- Decision: Upload reservation has atomic Redis user+campus limits of 30 per fixed 10-minute window and fails closed when Redis is unavailable. Existing announcement and media reads remain available.
- Decision: Announcement APIs exchange ordered `imageAssetIds`; media reads use an ordered batch of at most 100 IDs with 10-minute Presigned GET URLs. The 100-ID request limit is a technical chunk boundary, not a product image-count limit.
- Decision: Public announcement reads require an actual ACTIVE membership even for a service ADMIN. Management continues to reuse the existing campus-manager policy, where service ADMIN remains allowed.
- Decision: Announcement image validation sorts and locks IDs in batches of 100 and checks cross-announcement attachment conflicts in the same batched shape. The batch size protects SQL shape without imposing a product attachment limit.
- Decision: Any authorized manager of the campus may edit an announcement regardless of its author. During full image-list replacement, READY assets already attached to that announcement may be retained or reordered even when another manager uploaded them; only newly introduced assets require requester ownership. Unattached assets owned by another manager, other-campus assets, non-READY assets, and assets attached elsewhere remain rejected. Removed assets become ORPHANED, and editing does not create another publication outbox event.
- Decision: Announcement outbox delivery uses required Redis deduplication reservations and releases them on transaction rollback. Redis/deduplication failure leaves the outbox pending instead of silently completing with zero notification logs.
- Decision: Category create/update uses `saveAndFlush` inside the typed duplicate boundary so a concurrent `lower(name)` unique-index conflict is returned as `ANNOUNCEMENT_CATEGORY_DUPLICATE`.
- Decision: Variant object keys are recorded before R2 writes. Failed compensation remains recoverable by cleanup, and an expired temporary original left on a READY asset is deleted without deleting the READY row or its long-lived variants.
- Decision: READY is the durable finalize boundary. A temporary-original deletion failure after that boundary keeps the key for scheduled cleanup and still returns the READY result, including repeated complete calls.
- Decision: Expired PENDING upload reservations fail closed before R2 access. Batched access URL responses preserve request order and include immutable `sha256`; provider presign failures return typed `MEDIA_STORAGE_UNAVAILABLE` without exposing provider details or object keys.
- Decision: V14 updates `ck_notification_logs_type` for `ANNOUNCEMENT_PUBLISHED` and binds `announcement_images` to announcement and media asset through same-campus composite foreign keys.
- Impact: Flyway V14 adds announcement category, announcement, outbox, media asset, and announcement image tables and backfills existing campuses. No poll image/domain behavior from Issue #238 is included. External R2, Docker, Cloud Run, and real FCM operations were not performed in development verification.

## 2026-08-04 - Issue #239 커피 투표 자동 마감 개별 실패 격리

- 만료된 커피 투표 한 건의 계좌·권한·상태 선행조건 오류가 전체 자동 마감 배치를 중단하지 않도록 `BusinessException`을 투표 단위로 격리한다.
- 실패한 투표의 기존 transaction은 rollback하여 `OPEN` 상태를 유지하고, 이후 ID의 정상 투표는 계속 마감·정산한다. DB·Redis·락 같은 예상 밖 runtime 예외는 계속 전체 작업 실패로 전파한다.
- 실패 로그에는 `pollId`와 `ErrorCode`만 남기며 계좌번호, 사용자 정보, 예외 stack trace는 반복 출력하지 않는다.

## 2026-08-04 - Issue #242 공지·투표 PDF 첨부

- 사용자는 기존 private Cloudflare R2 공통 미디어 API를 공지와 투표가 함께 재사용하고, 이미지와 PDF 관계만 `imageAssetIds`와 `documentAssetIds`로 분리하는 호환 확장을 승인했다.
- PDF 입력 상한은 사용자가 최종 승인한 30MiB다. 기존 JPEG/PNG 상한 5MiB와 이미지 variant 계약은 변경하지 않는다.
- PDF는 `application/pdf`, 예약 크기와 SHA-256, `%PDF-` signature, PDFBox decode를 모두 검증한다. 암호화, embedded file, JavaScript, open/additional action, launch, rich media는 fail closed한다. 외부 악성코드 scanner는 MVP에 포함하지 않는다.
- PDF는 thumbnail/detail로 변환하지 않고 검증된 단일 private final object로 저장한다. 다운로드는 10분 Presigned GET과 `Content-Disposition: attachment`를 사용하며 안전한 원본 파일명, MIME, 크기, SHA-256만 응답한다.
- 공지·투표 생성과 수정은 ordered `documentAssetIds`를 additive로 지원한다. 공동 관리자는 현재 글에 이미 첨부된 타인 소유 PDF만 유지·재정렬할 수 있고, 새 unattached 타인 소유 PDF 추가는 계속 거부한다. 제거는 ORPHANED와 기존 retry/lease cleanup을 재사용한다.
- 첨부 개수의 제품 상한은 두지 않는다. 기존 100-ID 경계는 SQL lock 및 access URL 요청의 기술적 batch 크기일 뿐이다. poll 공개 알림 payload와 수정 시 재알림 정책은 변경하지 않는다.

## 2026-08-05 - Issue #243 보관된 공지 영구 삭제 API

- 사용자는 `DELETE /api/v1/admin/campuses/{campusId}/announcements/{announcementId}`를 승인했다. request body는 없고, 캠퍼스 관리자 권한이 필요하며, 성공은 `204 No Content`다.
- 이전 #237 문서의 “physical deletion 미노출” 의미는 #243 승인으로 대체한다. 삭제는 `ARCHIVED` 상태에서만 가능하고, `SCHEDULED`/`PUBLISHED`는 기존 `ANNOUNCEMENT_STATUS_CONFLICT` 409를 재사용한다. 없는 공지와 이미 삭제된 공지는 기존 `ANNOUNCEMENT_NOT_FOUND` 404를 재사용한다.
- 삭제 transaction은 캠퍼스 범위로 announcement row를 lock하고, 연결된 image/PDF attachment media row를 stable batch로 lock한 뒤 same-campus READY 상태를 검증한다. 연결 media는 `ORPHANED`로 전환하고 `announcement_images`/`announcement_documents` link row를 삭제한다.
- 삭제 대상 공지의 `announcement_notification_outbox` row는 announcement row 삭제 전에 제거한다. 이미 생성된 `notification_logs` 이력은 유지한다.
- API transaction에서는 R2 또는 외부 network/storage 호출을 하지 않는다. 실제 object 삭제는 기존 24h media cleanup/retry/lease 경로가 담당한다.
- 어떤 단계에서든 실패하면 전체 DB transaction을 rollback하며, 다른 공지, 다른 캠퍼스, poll media는 변경하지 않는다. 새 ErrorCode, Flyway, dependency, push/PR/deploy 변경은 없다.

## 2026-08-05 - Issue #245 캠퍼스 주간자료와 주일설교 나눔지 알림

- 주간자료는 `SHEPHERD_GUIDE`, `SHARING_SHEET` exact enum이며 campus/Asia-Seoul 월요일/type별 독립 슬롯이다. write/delete는 캠퍼스 관리자, read는 ACTIVE 멤버에게만 허용한다.
- 기존 private PDF media lifecycle과 30MiB 상한을 재사용한다. 교체·삭제된 PDF는 같은 transaction에서 ORPHANED로 전환하고 API transaction의 R2/network 호출은 0이다.
- 동일 campus/week/type의 최초 나눔지 등록만 durable outbox를 생성한다. 교체·삭제·tombstone 재등록과 목자지침은 outbox를 만들지 않는다.
- 사용자가 승인한 exact notification copy는 title `새 주일설교 나눔지가 등록되었어요`, body `{weekStartDate} 주차 주일설교 나눔지를 확인해 주세요`, eventType `WEEKLY_SHARING_SHEET_PUBLISHED`다.
- 응답은 nullable 두 슬롯과 asset metadata만 노출하며 object key/public URL은 노출하지 않는다.
- 사용자가 추가 승인한 retention은 `weekStartDate.plusMonths(3)`의 Asia/Seoul 00:00이다. due row를 lock하고 ACTIVE PDF를 같은 transaction에서 ORPHANED로 전환한 뒤 `weekly_materials` 행을 물리 삭제하며, DELETED tombstone도 같은 경계에서 물리 삭제한다. 최초 등록 outbox와 notification log는 보존하고 실제 R2 object 삭제는 기존 cleanup retry/lease가 담당한다.
- 공지 retention은 사용자가 승인한 `publishedAt`의 Asia/Seoul 달력 날짜 `plusMonths(3)` 00:00이다. PUBLISHED/ARCHIVED 공지는 due 시점에 row lock 아래 첨부 image/PDF를 ORPHANED로 전환하고 link, announcement outbox, announcement 행을 물리 삭제한다. 아직 게시되지 않은 SCHEDULED는 제외한다. notification log는 기존 14일 retention이 먼저 삭제하고, ORPHANED media DB 행과 R2 객체는 기존 cleanup retry/lease가 24시간 안전 유예 뒤 물리 삭제한다.
- 2026-08-05 최신 `origin/develop` fetch 결과 V18까지였고 현재 branch ancestry V19 다음 V20의 버전 충돌은 없었다.
- PM 독립 리뷰에 따라 유효한 월요일의 빈 주차는 nullable 두 슬롯을 가진 200 empty state로 확정했다. 목록은 ACTIVE 자료가 있는 주차만 반환한다.
- ACTIVE 주간자료 PDF는 기존 private media access 정책에 consumer-owned port로 결속하고, attachment exclusivity도 weekly/announcement/poll 양방향 consumer-owned port로 검증한다.
- 삭제 시 아직 pending인 최초 나눔지 outbox는 material→outbox 잠금 순서로 processed 처리해 늦은 발송을 억제하되 unique dedupe 이력은 보존한다. 물리 삭제 뒤 동일 과거 슬롯 재등록은 성공하지만 알림은 0이다.
- processor 초기 조회는 managed Entity가 아닌 immutable scalar snapshot을 사용한다. 이후 material row와 outbox row를 차례로 잠가 동시 processor exact-one과 delete 경쟁의 단일 결과를 보장한다.
- global ADMIN의 PUT command와 manager 응답 조립은 하나의 transaction이며, public GET의 ACTIVE membership 조건은 완화하지 않는다.

## 2026-08-05 - Issue #245 전역 3종 주간자료 후속 승인

- 이 결정은 위 #245의 campus별 `SHARING_SHEET` 계약을 대체한다. exact enum은 `SHEPHERD_GUIDE`, `SUNDAY_SHARING_SHEET`, `SATURDAY_LEADER_SHARING_SHEET`이고 기존 값은 V21에서 주일 나눔지로 이관한다.
- 세 슬롯은 모든 캠퍼스가 공유하는 global `weekStartDate + materialType`이다. 관리자는 자신이 관리할 수 있는 campus path로 다른 캠퍼스 관리자가 올린 자료도 교체·삭제할 수 있고, ACTIVE 멤버는 자신의 campus path에서 같은 전역 자료를 읽는다.
- content scope와 media tenant를 분리한다. 신규 asset은 요청 campus/requester-owned READY PDF여야 하며, 교체·삭제 시 저장된 기존 media tenant를 검증하고 ORPHANED로 전환한다.
- V20은 QA 적용 이력 때문에 변경하지 않고 V21을 추가한다. V21은 legacy 동일 week/type 다중 campus material/outbox가 있으면 SQLSTATE 23505로 fail closed하고, global unique와 singleton DB lock을 적용한다.
- 응답 nullable field는 `shepherdGuide`, `sundaySharingSheet`, `saturdayLeaderSharingSheet`다. 기존 API path, private R2/PDF 30MiB, 7-day cache, 3-month retention, cleanup-owned object deletion은 유지한다.
- 최초 global `SUNDAY_SHARING_SHEET`만 전체 캠퍼스 distinct ACTIVE 사용자에게 업로더 제외 알림을 보낸다. 다중 membership 사용자는 하나의 유효 ACTIVE campus context로 한 번만 받고, 나머지 두 자료와 교체·삭제·물리삭제 후 재등록은 알림 0이다. 승인된 copy/eventType은 변경하지 않는다.

# 2026-08-05 #245 CI 메모리 경계

- 953-test 전체 suite는 CI에서 `maxParallelForks=1`, `maxHeapSize=512m`, `forkEvery=25`로 실행한다.
- V19에서 V20만 검증하는 migration test는 최신 migration을 암묵적으로 따라가지 않고 Flyway target 20을 명시한다.
- 이 결정은 테스트 실행 안정화만을 위한 것이며 production runtime과 Flyway V21 동작을 변경하지 않는다.

## 2026-08-06 - Issue #249 목자지침 캠퍼스별 공동 관리

- 사용자는 목자지침 PDF만 캠퍼스 범위로 되돌리고, 주일 나눔지와 토목모 나눔지는 기존 전역 공유를 유지하기로 결정했다.
- 같은 캠퍼스의 ACTIVE `MINISTER`, `ELDER`, `CAMPUS_LEADER`와 전역 `ADMIN`은 목자지침을 등록·교체·삭제할 수 있다. 같은 캠퍼스의 모든 ACTIVE 구성원은 조회할 수 있고 다른 캠퍼스 접근은 거부한다.
- 기존 API path와 응답의 nullable 세 필드는 유지한다. 요청 `campusId`는 목자지침의 실제 scope이고 두 나눔지에는 인가 context다.
- 배포된 V21은 수정하지 않는다. V22는 기존 목자지침을 `media_campus_id` 캠퍼스로 귀속하고, 목자지침 campus partial unique와 두 나눔지 global partial unique를 분리한다.
- 목자지침과 토목모 나눔지는 알림 0이고, 최초 전역 주일 나눔지 알림 계약만 유지한다.

## 2026-08-08 - Issue #253 사용자 마감 투표 조회 기간 7일 연장

- 사용자는 ACTIVE 캠퍼스 구성원의 마감 투표 조회 기간을 기존 `endsAt + 3일`에서 `endsAt + 7일`로 연장하기로 결정했다.
- 사용자 목록, 직접 상세, 결과, 댓글 읽기, 이미지/PDF 접근은 모두 같은 7일 경계를 사용한다. 정확히 `endsAt + 7일`인 시각은 포함하고 그 이후에는 목록에서 숨기며 직접 조회와 첨부 접근도 거부한다.
- 관리자 7일 정책, ACTIVE membership과 campus tenant 검증, 익명 투표 신원 비노출, 마감 후 응답·댓글 쓰기 금지 정책은 유지한다.
- 투표 원본의 30일 retention과 cleanup scheduler, API path/DTO/ErrorCode, DB/Flyway/dependency는 변경하지 않는다.
## 2026-08-12 - Issue #257 캠퍼스 납부 계좌 정보 수정

- 사용자는 `PATCH /api/v1/campuses/{campusId}/payment-accounts/{accountId}`와 `nickname`, `bankName`, `accountNumber`, `accountHolder` 네 필드의 전체 교체 요청을 승인했다. 모든 값은 trim 후 필수이며 최대 100자다.
- `PENALTY`는 ACTIVE 캠퍼스 관리자(`MINISTER`, `ELDER`, `CAMPUS_LEADER`) 또는 전역 `ADMIN`이 수정한다. `COFFEE`와 `MEAL`은 각각 현재 담당이면서 target account owner인 사용자만 자기 계좌를 수정한다.
- active/inactive 계좌는 수정할 수 있고 soft deleted 또는 다른 campus 계좌는 `404`다. 같은 값 요청은 멱등 성공한다. account type, owner, active 상태와 생성 시각은 변경하지 않는다.
- 수정 계좌에 연결된 `UNPAID` 청구의 은행명·계좌번호·예금주 snapshot만 계좌 수정 transaction에서 잠그고 갱신한다. `PAID`, `WAIVED`, `CANCELED`의 과거 snapshot과 다른 계좌의 청구는 유지한다.
- `COFFEE`와 `MEAL` 수정은 기존 비활성화 경로와 동일하게 현재 담당 row를 먼저 잠그고 계좌 row를 잠근다. 계좌 수정과 비활성화가 반대 순서로 잠금을 획득하는 교착 가능성을 허용하지 않는다.
- 일반 ACTIVE 멤버의 납부용 활성 PENALTY/COFFEE 조회와 담당자별 본인 COFFEE/MEAL 관리 조회 계약은 변경하지 않는다. DB/Flyway/dependency 변경은 없다.

## 2026-08-13 - Issue #258 운영 장애 전용 관측

- 사용자는 기존 uptime, 5xx, p95, CPU, memory, instance, container, ERROR 급증 및 Cloud Tasks queue depth 정책에 DB 풀, scheduler, Upstash, Brevo, FCM, R2, 인증 실패 전용 관측을 추가하기로 승인했다.
- Hikari는 timeout 증가 1건을 즉시 기록하고, 1분 sampler에서 pending이 0보다 크거나 active/maximum이 90% 이상일 때 bounded event를 기록한다. pending은 2분, 90% 사용은 5분 지속을 알림 기준으로 사용한다.
- scheduler는 job별 성공 heartbeat와 실패 event를 기록한다. 고정 1분 작업은 11분 success absence, 일일 cron은 예정 시각 + 10분 absence를 미실행 기준으로 사용한다.
- Upstash Redis 3건/5분, Brevo 3건/10분, FCM transient 5건/10분, Cloudflare R2 3건/10분을 승인했다. FCM 영구 token 오류는 provider 장애에서 제외한다.
- 로그인과 Refresh Token 실패는 각각 20건/5분, 이메일 인증·password reset 인증 실패는 20건/10분을 승인했다.
- event label은 enum과 job 상수만 사용한다. 이메일, 사용자 ID, token, code, IP, object key, provider 응답 및 exception 원문은 관측 로그와 metric label에 저장하지 않는다. 기존 API, 예외, transaction, retry/outbox 동작은 변경하지 않는다.
- Google Cloud `faithlog-95890`에는 event별 로그 기반 metric 19개와 alert policy 19개를 생성하고 기존 `FaithLog Admin` 이메일 채널을 연결했다. 고정 지연 job은 11분 metric absence, 일일 cron은 일반 absence의 23시간 30분 제한을 피하면서 승인된 24시간 10분을 유지하는 PromQL `absent_over_time`을 사용한다. 일일 정책은 최근 25시간 내 최초 성공 sample이 존재할 때만 평가해 배포 전 오탐을 막는다.
- Upstash probe는 예외뿐 아니라 `PING` 결과가 exact `PONG`이 아닌 경우도 동일한 bounded provider failure로 기록한다.

## 2026-08-13 - Issue #260 목장 담당자 및 주간 목홀타 집계

- 사용자는 캠퍼스별 목장과 담당자, 매주 일요일 `목장모임`/`홀리웨이브`/`타예배` 독립 count 집계를 승인했다. 일반 ACTIVE 멤버 생성은 본인 자동 담당이며, 캠퍼스 관리자는 같은 캠퍼스 ACTIVE 사용자 중 담당자를 1명 이상 지정하거나 교체할 수 있다. 마지막 담당자 제거는 금지한다.
- 같은 캠퍼스의 목장 이름은 trim, 공백 정규화, case-insensitive normalized name으로 unique다. 중복 생성은 `409 SHEPHERD_GROUP_DUPLICATE`이고 일반 사용자가 기존 목장의 담당 권한을 임의 획득하지 않는다.
- 일반 사용자는 자신이 담당자인 목장만 목록/조회/작성/수정할 수 있고, 다른 목장은 존재를 숨기는 `SHEPHERD_GROUP_NOT_FOUND`로 처리한다. 캠퍼스 관리자와 서비스 `ADMIN`은 캠퍼스 전체 목장, 담당자, 주간 값을 관리한다.
- `serviceDate`는 Sunday만 허용하고, 세 count는 각각 0 이상이다. 상태는 `DRAFT`/`SUBMITTED`이며 제출 전후 수정 가능하다. 응답은 마지막 수정자/시각과 integer `version`을 포함하고, 저장 요청은 신규 `0`, 기존 현재 version을 보내야 한다. 불일치는 `409 SHEPHERD_ATTENDANCE_CONFLICT`다.
- 일반 사용자 홈 카드는 `GET /api/v1/campuses/{campusId}/shepherd-attendance/me/home` 전용 API로 제공한다. 서버 `Clock`과 `Asia/Seoul` 기준 현재 시각이 Sunday 00:00:00 이상 Monday 00:00:00 미만이고, 현재 ACTIVE campus에서 담당 ACTIVE 목장이 1개 이상인 사용자에게만 노출한다.
- 캠퍼스 관리자와 서비스 `ADMIN`도 담당 목장이 없으면 일반 사용자 홈 카드는 미노출이며 전체 입력은 관리자 페이지가 담당한다. 노출 문구는 exact `이번 주 목홀타를 입력해 주세요`이고, 담당 목장 수와 현재 Sunday `SUBMITTED` 완료 수를 반환한다. 비일요일 또는 미노출 응답은 `visible=false`, `groups=[]`다.
- 관리자 주차 화면은 `normalizedName, groupId` stable ordering, `size <= 100`, 목장+report page projection 1회, 담당자 bulk projection 1회, 합계 aggregate 1회로 조립한다. 완료 수는 `SUBMITTED` report만 세고 DRAFT는 완료 전으로 본다. 인증/권한 확인까지 포함한 실행형 테스트에서 1/100/1000 목장 모두 prepared statement 4개를 유지한다.
- 일반 홈 카드는 담당 assignment+group+현재 Sunday report를 bulk projection 1회로 조립하고, 인증/권한 확인까지 포함한 실행형 테스트에서 1/100 담당 목장 모두 prepared statement 2개를 유지한다.
- V23은 `shepherd_groups`, `shepherd_group_assignees`, `weekly_shepherd_attendance_reports`를 추가하고 unique, tenant FK, Sunday/count/status/version CHECK, RLS, index를 적용한다. 기존 migration은 수정하지 않는다.
- PM 독립 리뷰 보정으로 동시 목장명 rename의 exact `uk_shepherd_groups_campus_normalized_name` 충돌은 flush 경계에서 `409 SHEPHERD_GROUP_DUPLICATE`로 변환하고, 다른 integrity 오류는 그대로 전파한다.
- 목장명 request DTO는 compact constructor에서 trim/공백 정규화 후 Bean Validation `@Size(max=100)`을 적용한다. HTTP create/update의 normalized 101자 이름은 기존 `GLOBAL_VALIDATION_FAILED` 400으로 처리하며 담당자 오류로 노출하지 않는다. Service command 직접 호출의 이름 방어 검증도 같은 공통 validation 오류를 사용한다.
- `shepherd_group_assignees` 복합 ID 신규 저장은 `Persistable` 경계로 신규 row를 명시해 신규 담당자 수만큼 merge existence SELECT가 늘어나지 않게 한다. 담당자 응답 조립에 필요한 bulk SELECT와 필수 INSERT 자체는 유지한다.
- 일반 report GET은 report+last modifier user projection으로 재조회 직후에도 `lastModifiedByName`을 반환한다. 관리자 board의 기존 modifier projection과 권한/tenant 경계는 유지한다.


## 2026-10-16 - 커피 투표 항목 직접 입력

- 사용자 요청: 커피 투표에 직접 입력 항목을 추가할 수 있도록 백엔드부터 수정.
- 실제 투표 생성과 OPEN 투표 항목 추가에서 메뉴명·금액 직접 입력을 지원한다.
- 기존 카탈로그 선택 시 서버 메뉴 snapshot을 계속 사용한다. 직접 입력 항목은 카탈로그에 등록하지 않는다.
- 기존 청구 검증에 맞춰 직접 입력 금액은 양의 정수, 이름은 trim 후 1~200자로 검증한다.
- DB 변경 없이 poll_options 이름·금액과 nullable 메뉴 코드를 사용한다. CLOSED 정산 흐름은 유지한다.
- 반복 템플릿 편집과 프론트 변경은 이번 구현 범위에 포함하지 않는다.


## 2026-10-16 - #263 병합 및 청구 테스트 수정 순서

- 사용자가 develop과 main에 병합하도록 승인했다.
- 이어 청구 관련 실패를 먼저 수정하도록 요청했다. 조사 결과 생성 오류가 아닌 과거 납부 fixture와 기본 한 달 보관 정책의 테스트 불일치로 확인했다.
- 과거 내역을 검사하는 두 관리자 조회에 includeArchived=true를 명시하고 청구 관련 테스트를 통과시킨 뒤 두 브랜치 병합을 진행한다. 운영 청구 정책은 변경하지 않는다.

## 2026-10-29 - #266 입력칸별 검증 오류 안내

사용자 요청에 따라 입력 오류가 난 항목과 필요한 수정을 앱에서 식별할 수 있도록 한다. 기존 HTTP 400, GLOBAL_VALIDATION_FAILED와 message는 유지하고 Bean Validation 오류에서만 data.fieldErrors 배열에 field와 code를 제공한다. 여러 필드 오류를 빠짐없이 제공하며 중복을 제거하고 필드·코드 순서로 정렬한다. rejectedValue와 검증 인자는 포함하지 않는다. 프론트는 알려진 필드·코드만 표시하며 구형 서버의 단일 필드 메시지도 안전하게 처리한다. DB·검증 기준·권한과 잘못된 JSON 처리 정책은 변경하지 않는다.

### 2026-11-11 - Issue #270 캘린더 저장 기반 일간 경건 보상

- 사용자 승인: 큐티·말씀·기도는 캘린더에서 오늘 날짜를 체크하고 저장에 성공해야 각각 하루 한 번 30P 수령 가능. 주간 저장·제출은 일간 보상의 저장 근거가 아니며 주간 제출 보상은 별도 유지.
- 구현: 일별 저장 트랜잭션 안에 calendar_daily_rewards 저장 근거를 기록. 미션 조회·수령은 인증된 본인의 ACTIVE campus membership을 검증하고 서버 Clock/Asia-Seoul 날짜 및 원본 현재 체크를 함께 확인. 회원 범위 잠금과 날짜·항목 고유 키로 최초 저장·중복 수령을 직렬화한다. 실패한 저장은 근거도 rollback한다.
- 범위: 이 테이블의 누적 적립액은 일간 보상 원장 합계다. 뽑기·판매 지갑 잔액이 아니며 동물 경제의 정식 공개나 운영 DB 마이그레이션 실행은 포함하지 않는다. 앱의 정식 기능 공개 가드는 유지한다.

## 2026-11-11 - #272 계정 공통 동물 경제 설계 승인
사용자가 DB 설계 초안의 추천 방향으로 개발 진행을 승인했다. 지갑·동물·정원은 계정 공통이며 캠퍼스는 미션 근거로 보존한다. 단계1 지갑·원장부터 구현하고 단계2 일간 보상 연결은 별도 검증 후 진행한다. 운영 DB 적용·배포는 포함하지 않는다. 과거 복수 캠퍼스 중복 수령분 이관 처리 및 주간·월간 정책은 구현 전에 별도 확정한다.
