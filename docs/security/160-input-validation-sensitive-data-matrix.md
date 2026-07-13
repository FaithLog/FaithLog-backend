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

Pending static trace. Each final row will record input surface, Bean Validation, service
normalization/business validation, persistence constraint, error status, and existing test evidence.

## 4. Sensitive-field exposure matrix

Pending static trace. Each final row will record the sensitive field class, storage location,
response DTO boundary, logging/documentation boundary, and approved roles.

## 5. Test manifest and XML result

Pending focused-test discovery and execution. No test is added by this audit.
