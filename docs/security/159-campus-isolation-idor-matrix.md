# Issue #159 campus isolation and IDOR authorization matrix

## 1. Audit baseline

- Audit date: 2026-07-12
- Baseline commit: `5cad9f76df0a0e1bd0a086ab4059f6ccb5e51af2` (`origin/develop`)
- Branch: `audit/159-campus-isolation-idor`
- Scope: read-only static tracing from Controller identifiers through command/query authorization guards and repository predicates
- Excluded: production traffic, credentials, Docker, code/test/config/database/Flyway changes
- Sensitive-data rule: actual secrets, tokens, personal data, account numbers, and prayer-request content were not printed or recorded

The 80 endpoint rows in `docs/security/157-api-authorization-matrix.md` were used as the starting
manifest. Every current Controller mapping was enumerated again at the baseline commit. No endpoint
was added, removed, or remapped after #157. This document adds the object-parent-tenant-owner and
repository-predicate trace required by #159.

## 2. Counted HTTP manifest

The following 21 Controller files contain exactly 80 endpoint mappings. The row total is the counted
manifest; `GlobalExceptionHandler` and framework-only routes are excluded.

| # | Controller file | Endpoint rows |
| ---: | --- | ---: |
| 1 | `src/main/java/com/faithlog/admin/controller/AdminDashboardController.java` | 1 |
| 2 | `src/main/java/com/faithlog/admin/controller/AdminManagementController.java` | 5 |
| 3 | `src/main/java/com/faithlog/billing/controller/AdminBillingController.java` | 9 |
| 4 | `src/main/java/com/faithlog/billing/controller/BillingController.java` | 4 |
| 5 | `src/main/java/com/faithlog/campus/controller/AdminCampusController.java` | 5 |
| 6 | `src/main/java/com/faithlog/campus/controller/CampusController.java` | 7 |
| 7 | `src/main/java/com/faithlog/devotion/controller/AdminDevotionController.java` | 1 |
| 8 | `src/main/java/com/faithlog/devotion/controller/AdminPenaltyRuleController.java` | 2 |
| 9 | `src/main/java/com/faithlog/devotion/controller/DevotionController.java` | 4 |
| 10 | `src/main/java/com/faithlog/devotion/controller/PenaltyRuleController.java` | 1 |
| 11 | `src/main/java/com/faithlog/global/controller/HealthController.java` | 1 |
| 12 | `src/main/java/com/faithlog/notification/controller/AdminNotificationController.java` | 2 |
| 13 | `src/main/java/com/faithlog/notification/controller/FcmTokenController.java` | 2 |
| 14 | `src/main/java/com/faithlog/poll/controller/AdminPollController.java` | 3 |
| 15 | `src/main/java/com/faithlog/poll/controller/AdminPollTemplateController.java` | 5 |
| 16 | `src/main/java/com/faithlog/poll/controller/CoffeeCatalogController.java` | 2 |
| 17 | `src/main/java/com/faithlog/poll/controller/PollController.java` | 9 |
| 18 | `src/main/java/com/faithlog/prayer/controller/AdminPrayerController.java` | 8 |
| 19 | `src/main/java/com/faithlog/prayer/controller/PrayerController.java` | 3 |
| 20 | `src/main/java/com/faithlog/user/controller/AuthController.java` | 4 |
| 21 | `src/main/java/com/faithlog/user/controller/UserMeController.java` | 2 |
| **Total** | **21 Controller files** | **80** |

Full method/path rows 1-80 are in `docs/security/157-api-authorization-matrix.md`, sections 5.1-5.8.
The current mapping count can be reproduced without reading request values:

```text
rg -n '@(Get|Post|Put|Patch|Delete)Mapping' src/main/java --glob '*Controller.java'
```

## 3. Object identifier to guard and predicate matrix

Status semantics describe the current code, not a new policy. `404 hide` means a mismatched parent or
owner is deliberately reported as not found. `403 deny` means the object may have been loaded before
the authorization failure. Inconsistent 403/404 behavior is recorded separately in the findings report.

| # | Identifier category | Parent / tenant / owner invariant | Service guard | Repository predicate or post-load comparison | Current mismatch result |
| ---: | --- | --- | --- | --- | --- |
| 1 | `campusId` | resource tenant and ACTIVE membership, campus manager, service ADMIN, or domain-specific duty | `CampusAccessPolicy`, domain access helpers | `findByCampusId...`; service ADMIN bypass is explicit | domain 403; absent campus 404 |
| 2 | `userId` | principal for self APIs; service ADMIN or same-campus target for admin APIs | Controller builds commands from `AuthenticatedUser`; admin/campus target guards | campus target sets originate from ACTIVE `campus_members` rows | self fixed; target mismatch 403/empty |
| 3 | `membershipId` | membership belongs to path `campusId` | `CampusMemberManagementService.deleteCampusMember` | `findByCampusIdAndId(campusId, membershipId)` | 404 hide |
| 4 | `campusMemberId` | ACTIVE membership belongs to path `campusId`; requester hierarchy reaches old and new role | `CampusRolePolicy.requireRoleChangeAllowed` | `findByCampusIdAndId(...).filter(isActive)` | 404 target / 403 hierarchy |
| 5 | `assignmentId` | active COFFEE assignment belongs to path `campusId` | campus manager or service ADMIN | `findByCampusIdAndDutyTypeAndId(campusId, COFFEE, id).filter(isActive)` | 404 hide |
| 6 | `paymentAccountId` / `accountId` | same campus, non-deleted; type, active state, and COFFEE owner as required | `PaymentAccountCommandService`, `PaymentAccountQueryService`, `AdminChargeQueryService` | campus comparison after `findById`, or campus+type+owner repository query | 404 for campus mismatch; 403 for owner/type access |
| 7 | `chargeItemId` | self endpoint requires principal owner and path campus; admin endpoint derives campus from charge | `ChargeStatusCommandService` | `findChargeItemById`, then `userId` and `campusId` comparison | 403 owner, 409 campus mismatch, 404 absent |
| 8 | `pollId` | poll belongs to path campus and is inside the applicable visibility window | `PollLookupSupport`, `PollAccessService` | `findById` plus `poll.campusId == path campusId`; list query includes campus | 404 hide |
| 9 | `templateId` | template belongs to path campus; action allowed for persisted template function | `PollTemplateCommandService`, `PollTemplateQueryService` | `findById` plus `template.campusId == path campusId` | 404 hide; update BFLA is F-159-01 |
| 10 | `optionId` / `optionIds` | every selected option belongs to request `pollId` | `PollResponseCommandService` | allowed map built only from `findByPollIdOrderBySortOrderAsc(pollId)` | 404 hide |
| 11 | `responseId` | response belongs to already campus-scoped poll; no external direct response mutation API | Poll result and settlement assemblers | response IDs loaded only by `pollId`; response options loaded by those IDs | no direct external status |
| 12 | `commentId` | comment belongs to request `pollId`; author or campus manager/service ADMIN edits | `PollCommentCommandService.requireCommentEditor` | `findById` then `comment.pollId == path pollId` | 404 parent; 403 editor |
| 13 | `seasonId` | season determines campus, then campus manager/service ADMIN required | `PrayerSeason*Service`, `PrayerGroup*Service`, `PrayerAccessSupport` | `findById(seasonId)` then `season.campusId` authorization | 404 absent; 403 other campus |
| 14 | `groupId` | group resolves season, season resolves campus; targets are ACTIVE members of that campus | `PrayerGroupCommandService`, `PrayerAccessSupport` | `findById(groupId)` → `findById(seasonId)` → campus membership map | 404 absent; 403 other campus |
| 15 | `weekStartDate` and prayer/devotion target user | Monday; campus + principal for devotion, current season/group scope for prayer | devotion self guards; `PrayerGroupSubmissionCommandService`; `MyPrayerSubmissionCommandService` | campus+user+week queries; prayer week uses campus+season+week | 400 invalid date; 403 scope |
| 16 | FCM `tokenId` | token owner is authenticated principal | `FcmTokenCommandService.deactivateToken` | `findByIdAndUserId(tokenId, principalUserId)` | 404 hide |
| 17 | notification `requestId` / `targetId` | log predicate always includes path campus; poll target must be same campus | `NotificationRequestCommandService`, `NotificationLogQueryService` | log specification starts with `campusId`; poll uses `findByIdAndCampusId`; explicit users intersect ACTIVE campus users | empty result / 404 target / 403 viewer |
| **Total** | **17 identifier categories** |  |  |  |  |

## 4. Role and function authorization matrix

`MANAGER` is a service role for campus creation, not an implicit campus manager. `ADMIN` is the
approved global override. COFFEE duty requires an ACTIVE campus membership and is intentionally
limited to COFFEE functions.

| Function | USER | MANAGER | ADMIN | MINISTER / ELDER / CAMPUS_LEADER | MEMBER | Active COFFEE duty |
| --- | --- | --- | --- | --- | --- | --- |
| Create campus | no | yes | yes | no additional service permission | no | no |
| View ACTIVE campus member data | membership required | membership required | global | yes | yes | membership required |
| Update campus, manage members/roles/duty | no | no unless campus role | global | yes, role hierarchy applies | no | no |
| PENALTY account/charge management | no | no unless campus role | global | yes | no | no |
| Own COFFEE account create/deactivate/query | no unless duty | no unless role/duty | global | own account | no unless duty | own active COFFEE scope |
| Non-COFFEE Poll/template management | no | no unless campus role | global | yes | no | no; F-159-01 bypasses template update |
| COFFEE Poll/template create/update | no unless duty | no unless role/duty | global, but account owner rule still applies | own account required | no unless duty | own account and same campus |
| Devotion missing, prayer admin, notification admin | no | no unless campus role | global | yes | no | no |
| Self devotion/charge/FCM/prayer | principal only | principal only | principal or documented ADMIN read override | principal for self writes | principal | principal |

## 5. Counted repository manifest

These 25 concrete repository files were inspected. Admin queries reuse the user/campus repositories and
ports rather than owning an `admin/infrastructure/repository` package.

| Domain | Count | Files |
| --- | ---: | --- |
| billing | 2 | `ChargeItemRepository`, `PaymentAccountRepository` |
| campus/admin | 3 | `CampusRepository`, `CampusMemberRepository`, `CampusDutyAssignmentRepository` |
| devotion | 3 | `DevotionDailyCheckRepository`, `WeeklyDevotionRecordRepository`, `PenaltyRuleRepository` |
| notification | 2 | `NotificationLogRepository`, `UserFcmTokenRepository` |
| poll | 9 | `CoffeeBrandRepository`, `CoffeeMenuCatalogRepository`, `PollCommentRepository`, `PollOptionRepository`, `PollRepository`, `PollResponseOptionRepository`, `PollResponseRepository`, `PollTemplateOptionRepository`, `PollTemplateRepository` |
| prayer | 5 | `PrayerGroupMemberRepository`, `PrayerGroupRepository`, `PrayerSeasonRepository`, `PrayerSubmissionRepository`, `PrayerWeekRepository` |
| user | 1 | `UserRepository` |
| **Total** | **25** | **25 concrete repository files** |

The tenant-sensitive pageable/filter paths preserve their campus predicate:

- `ChargeItemRepository.searchPredicates` unconditionally adds `campusId` before user, category,
  status, and account filters.
- `NotificationLogQueryService` unconditionally adds `campusId` before request, target, date,
  type, status, sort, and pageable processing.
- service-admin user/campus search is intentionally global and requires active service ADMIN first.
- Poll, devotion, prayer, campus, and account list repositories use campus/parent IDs supplied only
  after their service guard, except global catalog and service-admin surfaces.

## 6. Authorization implementation manifest

Exactly 56 service/policy/support files were traced. This count excludes compatibility facades that
only delegate after Issues #147-#156.

| Domain | Count | Counted files |
| --- | ---: | --- |
| campus | 8 | `CampusQueryService`, `CampusUpdateService`, `CampusMemberManagementService`, `CampusDutyAssignmentService`, `CampusCreationService`, `CampusJoinService`, `CampusAccessPolicy`, `CampusRolePolicy` |
| admin | 4 | `AdminUserManagementService`, `AdminCampusManagementService`, `AdminDashboardQueryService`, `AdminAccessPolicy` |
| billing | 8 | `PaymentAccountCommandService`, `PaymentAccountQueryService`, `ChargeStatusCommandService`, `AdminChargeQueryService`, `MyChargeQueryService`, `ChargeCreationService`, `BillingAccessPolicy`, `ChargeStatusPolicy` |
| devotion | 7 | `DailyDevotionCommandService`, `WeeklyDevotionCommandService`, `MyWeeklyDevotionQueryService`, `DevotionMonthlySummaryQueryService`, `MissingDevotionMemberQueryService`, `PenaltyRuleCommandService`, `PenaltyRuleQueryService` |
| poll | 14 | `PollAccessService`, `PollLookupSupport`, `PollQueryService`, `PollResultQueryService`, `PollResponseCommandService`, `PollCommentCommandService`, `PollCommentQueryService`, `PollUserOptionCommandService`, `PollCreationCommandService`, `PollStatusCommandService`, `PollTemplateCommandService`, `PollTemplateQueryService`, `CoffeePollSettlementCommandService`, `CoffeePollSettlementSupport` |
| prayer | 9 | `PrayerAccessSupport`, `PrayerTargetMemberSupport`, `PrayerSeasonCommandService`, `PrayerSeasonQueryService`, `PrayerGroupCommandService`, `PrayerGroupQueryService`, `PrayerWeekBoardQueryService`, `PrayerGroupSubmissionCommandService`, `MyPrayerSubmissionCommandService` |
| notification | 3 | `FcmTokenCommandService`, `NotificationRequestCommandService`, `NotificationLogQueryService` |
| user/self | 3 | `UserMeQueryService`, `AccountWithdrawalCommandService`, `LogoutCommandService` |
| **Total** | **56** | **56 authorization implementation files** |

## 7. Focused test manifest and result

No test was added. The following 13 existing test classes were rerun as one focused command.

| # | Test class path |
| ---: | --- |
| 1 | `src/test/java/com/faithlog/admin/service/AdminManagementServiceTest.java` |
| 2 | `src/test/java/com/faithlog/billing/controller/BillingControllerTest.java` |
| 3 | `src/test/java/com/faithlog/billing/service/BillingQueryServiceTest.java` |
| 4 | `src/test/java/com/faithlog/billing/service/BillingServiceTest.java` |
| 5 | `src/test/java/com/faithlog/campus/controller/CampusControllerTest.java` |
| 6 | `src/test/java/com/faithlog/campus/service/CampusServiceTest.java` |
| 7 | `src/test/java/com/faithlog/devotion/service/DevotionServiceTest.java` |
| 8 | `src/test/java/com/faithlog/notification/controller/FcmTokenControllerTest.java` |
| 9 | `src/test/java/com/faithlog/notification/controller/NotificationControllerTest.java` |
| 10 | `src/test/java/com/faithlog/notification/service/FcmTokenServiceTest.java` |
| 11 | `src/test/java/com/faithlog/poll/service/PollServiceTest.java` |
| 12 | `src/test/java/com/faithlog/prayer/service/PrayerServiceTest.java` |
| 13 | `src/test/java/com/faithlog/prayer/service/PrayerSubmissionConcurrencyTest.java` |
| **Total** | **13 test classes** |

Result: **172 tests / 0 failures / 0 errors / 0 skipped**, `BUILD SUCCESSFUL`.

The existing suite covers campus parent mismatch, role hierarchy, billing owner and campus scope,
Poll/template/comment scope, anonymous results, prayer group/season scope, FCM ownership, and
notification campus filtering. It does not cover the request-body authorization pivot in F-159-01.
