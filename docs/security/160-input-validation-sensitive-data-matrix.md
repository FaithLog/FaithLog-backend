# Issue #160 input validation and sensitive-data exposure matrix

## 1. Audit baseline

- Audit date: 2026-07-13
- Baseline commit: `52e0b4ae26995e2a730832d4164d1cf8212b95be` (`origin/develop`)
- Branch: `audit/160-input-validation-sensitive-data`
- Scope: read-only static analysis and safe existing focused tests
- Excluded: production/test/config/database/Flyway/runtime-infrastructure changes, live scanning,
  load testing, credential use, new tests, fixes, push, and pull requests
- Sensitive-data handling: actual secrets, tokens, personal data, account numbers, prayer content,
  and notification body values are not printed or recorded

Issue #160 and the project policy documents are the source of the audit scope. Findings and fixes
from #157, #158, #159, #176, and #179 are predecessor work and are not counted again.

## 2. Counted manifest summary

The following counts were established before finding analysis. A file belongs to one row only in this
summary. Service result records are supporting traces and are not included in the response DTO count.

| Surface | Count | Reproduction rule |
| --- | ---: | --- |
| Controller files | 21 | `rg --files src/main/java \| rg '/controller/.*Controller\\.java$'` |
| HTTP endpoint mappings | 80 | `rg -n '@(Get\|Post\|Put\|Patch\|Delete)Mapping' src/main/java --glob '*Controller.java'` |
| Request DTO files | 36 | `rg --files src/main/java \| rg '/controller/dto/request/.*\\.java$'` |
| Response DTO files | 57 | `rg --files src/main/java \| rg '/controller/dto/response/.*\\.java$'` |
| Request path/query binding occurrences | 123 | `rg -n '@RequestParam\|@PathVariable' src/main/java --glob '*Controller.java'` |
| Common/domain page-sort parser files | 4 | exact manifest in section 2.4 |
| Global exception files | 3 | exact manifest in section 2.5 |
| Production logger-bearing files | 2 | exact manifest in section 2.5 |
| Application configuration files | 8 | 3 Java config + 5 main-resource YAML files |
| Persistence Entity files | 25 | `rg --files src/main/java \| rg '/domain/entity/.*\\.java$'` |
| Concrete JPA repository files | 25 | `rg --files src/main/java \| rg '/infrastructure/repository/.*Repository\\.java$'` |
| Flyway migration files | 6 | `rg --files src/main/resources/db/migration` |

At baseline, 26 of 36 request DTO files import Jakarta Validation and 10 do not. This is a census,
not a finding count: an unannotated DTO can still be safely validated by a service rule, and an
annotated DTO can still omit a security-relevant bound.

## 2.1 Controller manifest (21 files / 80 mappings)

| Domain | Count | Unique classes |
| --- | ---: | --- |
| admin | 2 | `AdminDashboardController`, `AdminManagementController` |
| billing | 2 | `AdminBillingController`, `BillingController` |
| campus | 2 | `AdminCampusController`, `CampusController` |
| devotion | 4 | `AdminDevotionController`, `AdminPenaltyRuleController`, `DevotionController`, `PenaltyRuleController` |
| global | 1 | `HealthController` |
| notification | 2 | `AdminNotificationController`, `FcmTokenController` |
| poll | 4 | `AdminPollController`, `AdminPollTemplateController`, `CoffeeCatalogController`, `PollController` |
| prayer | 2 | `AdminPrayerController`, `PrayerController` |
| user | 2 | `AuthController`, `UserMeController` |
| **Total** | **21** | **80 endpoint mappings** |

Controller paths are under `src/main/java/com/faithlog/<domain>/controller/`.

## 2.2 Request DTO manifest (36 files)

| Domain | Count | Unique classes |
| --- | ---: | --- |
| admin | 2 | `AddCampusMemberRequest`, `ChangeUserRoleRequest` |
| billing | 3 | `ChangeChargeStatusRequest`, `CompleteChargePaymentRequest`, `CreatePaymentAccountRequest` |
| campus | 5 | `AssignCoffeeDutyRequest`, `ChangeCampusRoleRequest`, `CreateCampusRequest`, `JoinCampusRequest`, `UpdateCampusRequest` |
| devotion | 4 | `CreatePenaltyRuleRequest`, `UpdateDailyDevotionRequest`, `UpdatePenaltyRuleRequest`, `UpdateWeeklyDevotionRequest` |
| notification | 2 | `RegisterFcmTokenRequest`, `SendNotificationRequest` |
| poll | 8 | `AddPollOptionRequest`, `CreatePollRequest`, `CreatePollTemplateRequest`, `PollCommentRequest`, `PollOptionRequest`, `PollTemplateOptionRequest`, `RespondToPollRequest`, `UpdatePollTemplateRequest` |
| prayer | 7 | `ClosePrayerSeasonRequest`, `CreatePrayerGroupRequest`, `CreatePrayerSeasonRequest`, `ReplacePrayerGroupMembersRequest`, `SaveMyPrayerSubmissionRequest`, `SavePrayerSubmissionsRequest`, `UpdatePrayerGroupRequest` |
| user | 5 | `DeleteMyAccountRequest`, `LoginRequest`, `LogoutRequest`, `RefreshRequest`, `SignupRequest` |
| **Total** | **36** |  |

Request DTO paths are under `src/main/java/com/faithlog/<domain>/controller/dto/request/`.

## 2.3 Response DTO manifest (57 files)

| Domain | Count | Unique classes |
| --- | ---: | --- |
| admin | 5 | `AdminCampusResponse`, `AdminDashboardSummaryResponse`, `AdminUserCampusResponse`, `AdminUserDetailResponse`, `AdminUserSummaryResponse` |
| billing | 9 | `AdminCampusChargesResponse`, `AdminMemberChargesResponse`, `ChargeAmountSummaryResponse`, `ChargeItemResponse`, `ChargeListItemResponse`, `MyChargeSummaryResponse`, `MyChargesResponse`, `PaymentAccountAdminResponse`, `PaymentAccountMemberResponse` |
| campus | 6 | `CampusCreateResponse`, `CampusDetailResponse`, `CampusMemberAdminResponse`, `CampusMembershipResponse`, `DutyAssignmentResponse`, `MyDutyAssignmentResponse` |
| devotion | 6 | `DailyDevotionCheckResponse`, `DailyDevotionResponse`, `MissingDevotionMemberResponse`, `MyMonthlyDevotionSummaryResponse`, `PenaltyRuleResponse`, `WeeklyDevotionResponse` |
| notification | 4 | `FcmTokenResponse`, `NotificationLogListResponse`, `NotificationLogResponse`, `SendNotificationResponse` |
| poll | 14 | `CoffeeBrandResponse`, `CoffeeMenuResponse`, `PollCommentResponse`, `PollDetailResponse`, `PollListResponse`, `PollMissingMemberResponse`, `PollMyResponseResponse`, `PollOptionResponse`, `PollOptionResultResponse`, `PollRespondentResponse`, `PollResponse`, `PollResultsResponse`, `PollTemplateOptionResponse`, `PollTemplateResponse` |
| prayer | 7 | `PrayerAssignableMemberResponse`, `PrayerGroupBoardResponse`, `PrayerGroupMemberResponse`, `PrayerGroupResponse`, `PrayerMemberSubmissionResponse`, `PrayerSeasonResponse`, `PrayerWeekBoardResponse` |
| user | 6 | `CampusMembershipResponse`, `DeleteMyAccountResponse`, `LoginResponse`, `SignupResponse`, `TokenResponse`, `UserMeResponse` |
| **Total** | **57** |  |

Response DTO paths are under `src/main/java/com/faithlog/<domain>/controller/dto/response/`.

## 2.4 Query/path parser manifest (4 files / 123 binding occurrences)

1. `src/main/java/com/faithlog/global/controller/PageSortRequestValidator.java`
2. `src/main/java/com/faithlog/admin/controller/AdminPageRequests.java`
3. `src/main/java/com/faithlog/billing/controller/BillingPageRequests.java`
4. `src/main/java/com/faithlog/notification/controller/NotificationPageRequests.java`

All 21 Controllers are also traced for their 123 `@RequestParam`/`@PathVariable` occurrences,
including Spring conversion of `Long`, `int`, `boolean`, `UUID`, `LocalDate`, and enum values.

## 2.5 Exception, logging, and configuration manifest

Global exception files (3):

1. `src/main/java/com/faithlog/global/exception/BusinessException.java`
2. `src/main/java/com/faithlog/global/exception/ErrorCode.java`
3. `src/main/java/com/faithlog/global/exception/GlobalExceptionHandler.java`

Production logger-bearing files (2):

1. `src/main/java/com/faithlog/batch/infrastructure/scheduler/FaithLogScheduledJobs.java`
2. `src/main/java/com/faithlog/batch/service/PendingNotificationRecoveryService.java`

Application configuration files (8):

1. `src/main/java/com/faithlog/global/config/ApplicationTimeZoneConfig.java`
2. `src/main/java/com/faithlog/global/config/OpenApiConfig.java`
3. `src/main/java/com/faithlog/global/config/RedisConfig.java`
4. `src/main/resources/application.yml`
5. `src/main/resources/application-dev.yml`
6. `src/main/resources/application-docker.yml`
7. `src/main/resources/application-local.yml`
8. `src/main/resources/application-prod.example.yml`

Security-specific configuration and error writers, such as `SecurityConfig`,
`RestAuthenticationEntryPoint`, and `JwtAuthenticationFilter`, are supporting traces. They will be
listed separately in the sensitive-field matrix and are not added to the eight-file application
configuration count.

## 2.6 Persistence-constraint manifest (56 files)

The persistence constraint surface is 25 Entity files, 25 concrete JPA repository files, and 6
Flyway migrations. Entity and repository classes are uniquely reproduced with:

```text
rg --files src/main/java | rg '/domain/entity/.*\.java$|/infrastructure/repository/.*Repository\.java$'
```

Flyway files:

1. `src/main/resources/db/migration/V1__initial_schema.sql`
2. `src/main/resources/db/migration/V2__add_poll_user_option_fields.sql`
3. `src/main/resources/db/migration/V3__split_active_coffee_payment_account_owner_scope.sql`
4. `src/main/resources/db/migration/V4__add_payment_account_soft_delete.sql`
5. `src/main/resources/db/migration/V5__fix_fcm_token_active_uniqueness.sql`
6. `src/main/resources/db/migration/V6__add_user_deleted_at.sql`

The audit will compare request validation and normalization against Java column annotations,
repository predicates, database nullability, length/check/unique constraints, and foreign keys.

## 3. Input surface to persistence matrix

The rows below cover all 36 request DTOs and the Controller query/path surfaces. A row can contain
multiple DTOs only when they share one validation and persistence boundary.

| # | Input surface / request DTO | Bean Validation / conversion | Normalization and business validation | Persistence constraint / sink | Current error status |
| ---: | --- | --- | --- | --- | --- |
| 1 | `SignupRequest` | name/email/password nonblank, email syntax; no maximum lengths | duplicate email and password hashing; no trim or length bound | `users.name` 100, email/password hash 255 | DTO 400; overlong persisted field can reach unhandled DB 5xx |
| 2 | `LoginRequest`, `RefreshRequest`, `DeleteMyAccountRequest` | nonblank; login email syntax | credential/JWT/confirm-text checks; principal fixes deletion owner | user/Redis lookup only; no raw token storage | fixed 400/401 business errors |
| 3 | optional `LogoutRequest` | optional body and fields; no length bounds | principal fixes user/session/JTI; optional refresh/FCM cleanup is owner-scoped | Redis identifiers and FCM lookup | 200 without body; invalid supplied token does not echo input |
| 4 | `CreateCampusRequest`, `JoinCampusRequest`, `UpdateCampusRequest` | create name 100, region 100; join code 50; update has no annotations | principal fixes requester; update applies nullable fields; no common trim | campus name/region 100, invite code 50, description text | validation/business 400; overlong update can reach DB 5xx |
| 5 | `AssignCoffeeDutyRequest`, `ChangeCampusRoleRequest`, `AddCampusMemberRequest`, `ChangeUserRoleRequest` | identifier/enum nonnull | requester comes from principal; role hierarchy and target membership checks | numeric IDs and enum columns | 400 conversion/validation, domain 403/404/409 |
| 6 | `CreatePaymentAccountRequest` | type nonnull; four strings nonblank and max 100 | COFFEE owner is requester; account scope/type/owner rules | account and snapshots max 100 | aligned 400 before DB |
| 7 | optional `CompleteChargePaymentRequest`, `ChangeChargeStatusRequest` | optional `paidAt`; status nonnull | owner/principal and status transition policy | charge timestamp/status | domain 400/403/404/409 |
| 8 | `UpdateDailyDevotionRequest` | primitive booleans | path date derives Monday; principal fixes user; submitted-week guard | unique weekly record/date constraints | conversion 400; domain 403/409 |
| 9 | `UpdateWeeklyDevotionRequest` | daily date nonnull; nested validation; no list maximum; no upper bound on lateness | Monday, in-week dates, negative lateness, duplicates through map semantics; missing days default false | integer summary columns and charge amount lack positive/max checks | negative input 400; excessive positive input reaches F-160-01 |
| 10 | `CreatePenaltyRuleRequest`, `UpdatePenaltyRuleRequest` | nonnull values; no `@Min`/`@Max` | entity rejects negative and invalid type pairing; no upper bound | integer rule columns have no range checks | negative/type errors 400; excessive values contribute to F-160-01 |
| 11 | `CreatePollRequest` | title nonblank, times nonnull, nested options valid marker; no title/list maximum | requires `startsAt < endsAt`; template fields ignored when template-backed; direct options resolved | title 200, option content 200, price/sort integer | period/business 400; overlong title/content can reach DB 5xx |
| 12 | `CreatePollTemplateRequest`, `UpdatePollTemplateRequest` | title/nonnull enums/times, day 1-7, nonempty nested options; no title/list maximum | persisted target type drives update authorization after #179; account checks; option snapshot resolution | title/content 200, price/sort integer | validation/domain 400/403/404; client price reaches F-160-02 |
| 13 | `PollOptionRequest`, `PollTemplateOptionRequest` | no field annotations despite nested `@Valid` | blank content rejected only when menu absent; user-added non-COFFEE content trims/max 200, admin create paths do not; menu path uses catalog price | option content 200; integer price has no range/catalog constraint | invalid option 400; direct COFFEE price reaches F-160-02 |
| 14 | `AddPollOptionRequest` | no annotations | COFFEE requires catalog menu and forbids content; non-COFFEE trims content and maxes at 200; duplicate names rejected | option content 200 and catalog snapshot | domain 400/403/404; aligned for user-added option path |
| 15 | `RespondToPollRequest` | no annotations | nonempty, SINGLE exactly one, duplicate IDs rejected, every option must belong to poll | response-option unique constraint; memo max 500 | domain 400/404; overlong memo can reach DB 5xx, U-160-02 |
| 16 | `PollCommentRequest` | nonblank only | author/manager and OPEN-window checks; no trim/max | comment content max 1000 | blank 400; overlong content can reach DB 5xx |
| 17 | `CreatePrayerSeasonRequest`, `ClosePrayerSeasonRequest` | name nonblank/start/end nonnull; no name max | manager guard; close does not compare end against start | season name 100 and dates | validation/domain 400/403/404; overlong name can reach DB 5xx |
| 18 | `CreatePrayerGroupRequest`, `UpdatePrayerGroupRequest` | create name nonblank/sort nonnull; update has no annotations | manager guard; nullable patch fields; no trim/range/max | group name 100, sort integer | validation/domain 400/403/404; overlong update can reach DB 5xx |
| 19 | `ReplacePrayerGroupMembersRequest` | list nonnull; no empty/max/element constraint | null/duplicate rejected, targets intersect ACTIVE campus, other-group assignment rejected | unique group/user rows | domain 400/404/409; collection size hardening U-160-01 |
| 20 | `SavePrayerSubmissionsRequest`, `SaveMyPrayerSubmissionRequest` | group list nonnull/nested valid; IDs/version nonnull; content and list unbounded; self request unannotated | Monday, scope, duplicate users, version and optimistic update checks | one text submission per week/user | domain 400/403/409; text/list hardening U-160-01 |
| 21 | `RegisterFcmTokenRequest` | token/client nonblank, client max 100, device nonnull, app max 50; token unbounded | principal fixes owner; active ownership upsert/deactivation | token text; client/app limits aligned | validation 400; token-size hardening U-160-01 |
| 22 | `SendNotificationRequest` | type nonnull, title nonblank/max 200, body nonblank; target list/body unbounded | manager guard; target IDs distinct and intersect ACTIVE campus | title 200, body text, one log per effective target | validation/domain 400/403; body/list hardening U-160-01 |
| 23 | admin/billing/notification `page`, `size`, `sort` | Spring int/string conversion | common validator enforces page >= 0, size 1-100, format, field allowlist, asc/desc | parameterized Pageable/Criteria | domain-specific 400, no silent correction |
| 24 | query filters `keyword`, name/email/region, enum, UUID, dates, IDs | Spring conversion; nullable | Criteria API values are bound parameters; blank filters ignored; no keyword length/wildcard escaping | parameterized JPA Criteria/JPQL | malformed typed values 400; no SQL/JPQL injection found |
| 25 | path IDs, enums, `LocalDate`, year/month | Spring conversion | parent/principal rules from #159; Monday/year-month and poll period checks | scoped repository predicates | malformed conversion 400; domain 400/403/404/409 |
| 26 | JSON unknown fields / entity binding | Jackson binds explicit record components; default unknown-field rejection is not configured | Controllers accept request DTOs only and convert to commands | no Controller accepts an Entity | unknown fields can be ignored, but no mass assignment to Entity |

### 3.1 Injection, file, and external-send result

- All dynamic repository filters use Spring Data method parameters, JPQL named parameters, or
  Criteria API predicates. No request string is concatenated into SQL/JPQL/native SQL.
- Sort fields and directions are allowlisted before reaching Spring Data.
- No production external command execution, SpEL/template evaluation, unsafe Java deserialization,
  request-controlled URL fetch, upload, or regular-expression surface was found.
- The coffee seed parser reads one fixed classpath CSV path. The Firebase JSON/path inputs are
  environment configuration, not HTTP request data. No request-controlled traversal path was found.
- Notification title/body are sent as Firebase notification data, not rendered as backend HTML or
  executed as a URL/template. Control-character and client rendering policy remains a hardening item,
  not a confirmed backend injection path.

## 4. Sensitive-field exposure matrix

| # | Sensitive field | Storage | Response DTO boundary | Log / documentation boundary | Allowed role / result |
| ---: | --- | --- | --- | --- | --- |
| 1 | access/refresh token | access stateless; refresh JTI/session state in Redis, raw token not stored | login/refresh return token pair and expiry only; no JTI/sessionId/tokenVersion fields | application logger does not write token; generated test docs can contain test JWT | public credential exchange to the authenticating client; intended policy |
| 2 | JTI, sessionId, tokenVersion | JWT claims, Redis/DB version state | not exposed in application response DTOs | fixed auth errors do not echo parser claims | internal auth boundary only |
| 3 | FCM token, clientInstanceId, appVersion | `user_fcm_tokens` | `FcmTokenResponse` returns the registered row fields | REST Docs documents test fixtures; application logger has no FCM field | authenticated SELF registration response; approved policy |
| 4 | account number, holder, bank name | account rows and immutable charge snapshots | member DTO has payment fields only; admin DTO adds campus/owner/status/timestamps | no application logger path; test docs use fixture data | ACTIVE member full number is approved for transfer; admin metadata is separated |
| 5 | user ID, name, email | user and membership data | service ADMIN lists/details; campus managers see member/duty/missing/notification targets; member self DTO | no direct application logger path; test docs use fixtures | role-scoped operational identity; no anonymous Poll reuse |
| 6 | non-anonymous Poll respondents / missing members | response and membership rows | non-anonymous result and admin missing-member DTOs include identity; anonymous result identity remains empty | REST Docs test fixtures only | intended policy; anonymous boundary was verified in #159 and not recounted |
| 7 | prayer content | `prayer_submissions.content` text | campus weekly board includes all group content | no application logger/error interpolation; REST Docs test fixtures only | campus-wide ACTIVE member read is approved policy; write scope remains group/self/admin |
| 8 | notification title/body | one copy per target in `notification_logs` | manager-only notification log response includes title/body | Firebase payload receives values; no logger writes them | campus manager/service ADMIN operational history; intended boundary |
| 9 | notification provider failure reason | notification log and token failure columns | manager log response includes raw provider-derived message | recovery logger can include exception stack | possible provider detail/token echo is U-160-03; not proven |
| 10 | validation/JSON/business errors | not persisted by handler | validation field + constraint text; malformed JSON and auth are fixed messages; BusinessException uses controlled code/message | scheduled-job failures log stack traces internally | no client stacktrace, SQL, class/package, or raw rejected value confirmed |
| 11 | Firebase JSON, JWT/DB/Redis password | environment variables / credential stream | no response DTO | config names only; values are not logged | internal runtime configuration |
| 12 | actuator and SpringDoc | runtime framework metadata | health is public; info requires auth under current security chain; API docs routes are public only when enabled | prod example defaults SpringDoc off | deployed profile state remains predecessor operational verification, not a new #160 finding |

### 4.1 Secret-pattern scan without value output

| Area | Result |
| --- | --- |
| tracked env files | 4, all named `.env*.example`; no tracked non-example env file |
| current production/docs high-signal prefix candidates | 0 real-key prefix files; 2 local/docker config files matched only the generic assignment heuristic |
| current test high-signal prefix candidates | 0 files |
| narrow git-history high-signal prefix candidate commits | 0 commits |
| generated REST Docs files | 771 files |
| generated files with high-signal secret prefixes | 0 files |
| generated files with JWT-shaped test values | 244 files, ignored `build/` test artifacts |
| repository scanner policy file | no `.gitleaks.toml` or `.secretlintrc`; hardening candidate only |

Generated-field-name counts are not treated as leaked-value counts: auth/password fields appeared in
66 files, FCM/client fields in 13, account fields in 40, prayer/content fields in 192, and
notification/title/body fields in 98. No actual value is recorded in this report.

## 5. Test manifest and XML result

No test was added. The following 16 existing test classes were selected.

1. `AdminManagementControllerTest`
2. `BillingControllerTest`
3. `BillingApiRestDocsTest`
4. `DevotionControllerTest`
5. `PenaltyRuleServiceTest`
6. `FcmTokenControllerTest`
7. `NotificationControllerTest`
8. `NotificationApiRestDocsTest`
9. `FirebaseFcmSendAdapterTest`
10. `PollServiceTest`
11. `PollApiRestDocsTest`
12. `PrayerServiceTest`
13. `PrayerApiRestDocsTest`
14. `AuthControllerTest`
15. `AuthRefreshControllerTest`
16. `AuthApiRestDocsTest`

Final PM independent JUnit XML recount: **16 suites / 138 tests / 0 failures / 0 errors / 0 skipped**.

Final verification status for Issue #160 is a passing focused-test rerun. PM reran all 16 documented
focused classes in one Gradle invocation with isolated
`GRADLE_USER_HOME=/private/tmp/faithlog-gradle-160-review`, `--no-parallel`, and `--rerun-tasks`,
and confirmed `BUILD SUCCESSFUL`.

Audit-session history is preserved separately: the initial Codex-only verification first failed to read
default Gradle cache metadata, and later isolated executions overlapped while writing XML results. PM
confirmed that those failures were execution-environment concerns and not code/test regressions.
