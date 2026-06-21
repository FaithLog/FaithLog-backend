# FaithLog Decision Log

This file records user-approved project decisions so Codex does not rely on guesses later.

## Rules

- Every product, architecture, data, deployment, test-strategy, or resume-metric decision must come from the user.
- If Codex is unsure, Codex must ask before implementation.
- Record the decision date, context, options if relevant, the user's decision, and the implementation impact.

## Decisions

### 2026-06-21 - Issue #23 Campus Admin Dashboard Summary Contract

- Context: Issue #23 was verified against the latest Notion API design and GitHub issue text. The scope is a campus-internal admin dashboard summary API, not the service-level `ADMIN` user/campus management API implemented by Issue #61.
- Decision: Issue #23 uses `GET /api/v1/admin/campuses/{campusId}/dashboard/summary`. The primary users are `ACTIVE` campus members with `campus_members.campus_role` of `MINISTER`, `ELDER`, or `CAMPUS_LEADER`. Service-level `ADMIN` may access every campus dashboard. Plain `MEMBER`, managers of other campuses, and users with only the global `MANAGER` role may not access the dashboard.
- Decision: `weekStartDate` is an optional query parameter. If omitted, the server calculates the current week's Monday using `Asia/Seoul`. If provided, `weekStartDate` must be a Monday; invalid dates or non-Monday dates return 400. The member summary includes `activeCount`, `inactiveCount`, and `adminCount`. The devotion summary uses `weekly_devotion_records.submitted_at` for the selected week and includes submitted count, missing count, and submit rate. The billing summary includes total unpaid amount, unpaid member count, and category-level unpaid amounts for `PENALTY` and `COFFEE`. The poll summary includes open poll count, recently closed poll count for the last 7 days, and missing-response count only; detailed missing-member lists remain in the Issue #38 API.
- Impact: Issue #23 implementation must not reimplement #61 service-admin management, #31 devotion missing detail, #36 billing detail, #38 poll result/missing-member detail, or #40 notification log APIs. REST Docs tests must lock the summary response fields, permission matrix, optional `weekStartDate` default behavior, non-Monday validation, category-level billing summary, and 7-day recently closed poll rule.

### 2026-06-20 - Issue #41 Notification Redis Dedup And Lock Policy

- Context: Issue #41 adds Redis-based duplicate prevention and execution locks on top of the Issue #40 notification flow without adding new notification APIs or moving source-of-truth data into Redis.
- Decision: FCM token source of truth remains `user_fcm_tokens`, and notification history source of truth remains `notification_logs`. Redis is used only for notification deduplication and execution locking. Automatic notifications use the business dedup key `notificationType + campusId + scopeId + targetUserId + businessDate`, with 25-hour TTL for daily jobs and 8-day TTL for weekly jobs. Notification execution locks use `notification:lock:{jobName}:{campusId}:{scopeId}`, default to 10 minutes, and allow custom TTLs for longer batch jobs.
- Decision: Redis failures are fail-closed for automatic/scheduled notifications, so the automatic send/enqueue is skipped. Manual admin notification requests do not use the automatic business dedup key, but they do require Redis lock availability; if Redis is unavailable, the admin API fails clearly instead of sending without duplicate protection.
- Impact: Issue #41 implementation must expose reusable application ports/services for #24 Scheduler/Batch, keep Redis adapters under `notification/infrastructure/redis`, keep application services independent of `RedisTemplate`, preserve the #40 async `notification_logs` PENDING/SENT/FAILED/SKIPPED policy, and continue using the existing admin notification API paths.

### 2026-06-20 - Issue #40 Notification Async Retry Policy

- Context: Local backend policy still contained the older "no notification retry" MVP wording, while the latest current conversation, GitHub Issue #40, and Notion 2026-06-20 notification pages define asynchronous notification delivery with token-level retry.
- Decision: Issue #40 creates per-target `notification_logs` rows with one server-generated `request_id` and `PENDING` status before background FCM delivery. The admin send API returns `202 Accepted` with `notificationRequestId`, `queuedCount`, and `skippedCount`. Transient failures retry per token up to 3 times with `1s -> 5s -> 30s` intervals. Permanent failures such as `UNREGISTERED`, token-not-registered, and payload-valid invalid-token responses do not retry and deactivate the affected FCM token. Old `PENDING` log reprocessing after server restart remains out of #40 scope.
- Impact: `docs/backend-implementation-policy.md`, implementation, tests, and REST Docs must follow the retry policy. Older local "notification sends do not retry automatically in MVP" wording is superseded for #40.

### 2026-06-20 - Issue #39 Coffee Poll Charge Settlement Timing

- Context: Issue #39 originally had older local wording that could imply `COFFEE` charges are created during poll response writes, while the latest user decision and Notion/API pages moved charge generation to closed-poll settlement.
- Decision: `PUT /api/v1/campuses/{campusId}/polls/{pollId}/responses/me` only saves the current response and `poll_response_options`. `COFFEE` charge rows are generated or updated by a closed coffee poll settlement application service after the poll is `CLOSED`, using final responses, `poll_options.price_amount`/`content` snapshots, `sourceType = POLL_RESPONSE`, `sourceId = poll_responses.id`, `paymentCategory = COFFEE`, and `dueDate = null`.
- Decision: Settlement target polls are `poll_type = COFFEE`, `charge_generation_type = OPTION_PRICE`, `payment_category = COFFEE`, and `status = CLOSED`. Settlement is idempotent, keeps terminal `PAID`/`WAIVED`/`CANCELED` `COFFEE` charges unchanged, requires an active `DutyType.COFFEE` assignee and a valid active same-campus `COFFEE` account from `polls.payment_account_id`, and runs all-or-nothing in one transaction.
- Impact: Issue #39 implementation must use a Poll application port/adapter to Billing so Poll domain code does not directly manipulate Billing entities. Scheduler/Batch invocation remains Issue #24 scope, and no separate user-facing coffee charge creation API is added.

### 2026-06-20 - Issue #38 Poll PM Review Contract Clarifications

- Context: PM review found that empty `optionIds` could be intercepted by controller validation, SCHEDULED/future polls could be writable and visible, and response option replacement could hit the `(response_id, option_id)` unique constraint when the same options were saved again.
- Decision: Empty or missing `optionIds` must be validated in the poll service and return `POLL_RESPONSE_INVALID_SELECTION_COUNT`. Poll response/comment writes are allowed only when `polls.status = OPEN` and `starts_at <= now <= ends_at`; SCHEDULED/future polls are hidden from member list/detail and direct lookup returns `POLL_NOT_FOUND`. Not-open write attempts continue to use the existing `POLL_CLOSED` / 409 / `마감된 투표에는 응답하거나 댓글을 작성할 수 없습니다.` contract instead of introducing a new ErrorCode in this fix.
- Impact: Issue #38 service and REST Docs tests must cover empty selection errors, SCHEDULED/future visibility blocking, SCHEDULED write blocking, and same-option response resave without duplicate rows or unique constraint failures.

### 2026-06-20 - Issue #38 Poll ErrorCode Contract

- Context: Issue #38 needed detailed poll error codes for response validation, campus scope mismatch, visibility window expiration, poll comment authorization, and admin missing-member authorization. The implementation policy requires domain-prefixed detailed codes instead of broad `INVALID_REQUEST`, `NOT_FOUND`, or `FORBIDDEN` codes.
- Decision: Use `POLL_NOT_FOUND` / 404 / `투표를 찾을 수 없습니다.` for campus mismatch, visibility window expiration, and direct lookup data non-exposure. Use `POLL_OPTION_NOT_FOUND` / 404 / `투표 선택지를 찾을 수 없습니다.` for nonexistent options or options belonging to another poll. Use `POLL_RESPONSE_INVALID_SELECTION_COUNT` / 400 / `투표 선택 개수가 올바르지 않습니다.` for empty `optionIds`, `SINGLE` requests not containing exactly one option, and `MULTIPLE` requests containing fewer than one option. Use `POLL_RESPONSE_DUPLICATE_OPTION` / 400 / `중복된 투표 선택지가 포함되어 있습니다.` for duplicate option IDs. Use `POLL_CLOSED` / 409 / `마감된 투표에는 응답하거나 댓글을 작성할 수 없습니다.` for closed poll response/comment write attempts. Use `POLL_ACCESS_FORBIDDEN` / 403 / `투표 접근 권한이 없습니다.` for non-ACTIVE campus member access. Use `POLL_ADMIN_FORBIDDEN` / 403 / `투표 관리 권한이 없습니다.` for missing-member admin authorization failures. Use `POLL_COMMENT_NOT_FOUND` / 404 / `투표 댓글을 찾을 수 없습니다.` and `POLL_COMMENT_FORBIDDEN` / 403 / `투표 댓글 수정/삭제 권한이 없습니다.` for comment lookup and edit/delete authorization.
- Impact: Issue #38 implementation and REST Docs must use these detailed codes/status/messages for poll exceptions. New poll exceptions must not be collapsed into broad global error codes.

### 2026-06-19 - Issue #37 Compose Coffee Seed Source Override

- Context: Issue #37 originally required seeding Compose Coffee menu prices only from official Compose Coffee menu boards, official app data, or official menu images. During development, official web access was blocked and the user provided a 2026 Compose Coffee menu/price text list from a blog source after being told the official-source requirement was blocking implementation.
- Decision: For Issue #37, use the user-provided 2026 Compose Coffee menu/price list as the approved seed source. Record that this is a user-approved override of the earlier official-source-only seed constraint for this implementation session.
- Impact: `coffee_menu_catalog` seed data for Issue #37 should reflect the user-provided menu names and prices. The implementation record must mention that the seed source was user-provided and not independently verified from an official Compose Coffee endpoint.

### 2026-06-19 - Issue #61 Service Admin User And Campus Management API Contract

- Context: Issue #61 implements service-level `ADMIN` user and campus management APIs. The issue had approved behavior but left several REST Docs contract details open, including response field names, allowed sort fields, and the last-admin protection query basis.
- Decision: `GET /api/v1/admin/users` uses query parameters `name`, `email`, `userId`, `role`, `page`, `size`, and `sort`. User list items return `userId`, `name`, `email`, `role`, `campusCount`, and `campuses[]`; user detail returns `userId`, `name`, `email`, `role`, `isActive`, and `campuses[]`. User campus items use `membershipId`, `campusId`, `campusName`, `region`, `campusRole`, and `status`. `GET /api/v1/admin/campuses` uses query parameters `name`, `region`, `status`, `page`, `size`, and `sort`, and returns `campusId`, `name`, `region`, `isActive`, `status`, `memberCount`, and `adminCount`. Allowed user sort fields are `id`, `name`, `email`, `role`, and `createdAt`. Allowed campus sort fields are `id`, `name`, `region`, and `createdAt`. The last service-level `ADMIN` protection counts only users where `users.role = ADMIN` and `users.is_active = true`.
- Impact: Issue #61 REST Docs and tests must lock these names and sort contracts. Aggregate fields such as `memberCount` and `adminCount` are not sortable in this MVP. Last-admin demotion must fail when the target is the only active service-level `ADMIN`.

### 2026-06-19 - Issue #61 Service Admin Direct Member Add Duplicate Policy

- Context: Issue #61 allows service-level `ADMIN` to add users to campuses directly without invite codes and reactivates an existing `INACTIVE` membership as `ACTIVE + MEMBER`. The remaining open behavior was how to handle a direct add request when the same user already has an `ACTIVE` membership in that campus.
- Decision: If service-level `ADMIN` directly adds a user who already has an `ACTIVE` membership in the target campus, the API fails with `CAMPUS_ALREADY_JOINED`, HTTP `400 Bad Request`, and the existing user-facing message `이미 가입된 캠퍼스입니다.`
- Impact: `POST /api/v1/admin/campuses/{campusId}/members` must not silently return or overwrite an active membership. It should match the existing invite-code duplicate join policy while still reactivating `INACTIVE` memberships.

### 2026-06-19 - Issue #57 My Monthly Devotion Summary Contract

- Context: GitHub Issue #57 was split from Issue #31 for the Notion `10.5 내 월간 경건생활 통계 조회` API. The issue still said to verify the Notion source before choosing path, query parameters, and response shape.
- Decision: Issue #57 follows Notion API `10.5`: `GET /api/v1/campuses/{campusId}/devotions/me/monthly-summary?year={year}&month={month}`. The response keeps the common `ApiResponse` envelope and returns `campusId`, `campusName`, `region`, `userId`, `name`, `year`, `month`, a monthly `devotion` summary, and `weeklyRecords[]` with `weeklyRecordId`, `weekStartDate`, `weekEndDate`, `quietTimeCount`, `prayerCount`, `bibleReadingCount`, `saturdayLateMinutes`, and `submittedAt`.
- Impact: Issue #57 does not add a new table. Monthly totals are calculated by calendar date from `devotion_daily_checks.record_date` between the selected month's first and last day for the current authenticated user after ACTIVE campus membership validation. `weeklyRecords[]` groups the selected month's daily rows by week and may contain partial-week counts when a week crosses a month boundary. `SATURDAY_LATE` minutes are included in the month that contains that week's Saturday date. Controller must return DTOs, detailed contract must be covered with Spring REST Docs, and Swagger documentation annotations must not be added.

### 2026-06-19 - Issue #33 Weekly Devotion Submission Response Shape

- Context: Issue #33 creates a `PENALTY` charge as a side effect of the first weekly devotion final submission. The remaining API contract question was whether the existing weekly devotion response should add a new field such as `generatedCharges`.
- Decision: Keep the existing `WeeklyDevotionResponse` structure unchanged for Issue #33. Do not add `generatedCharges` or another generated-charge summary field to `PUT /api/v1/campuses/{campusId}/devotions/me/weeks/{weekStartDate}`. Clients should confirm generated charges through the existing charge query APIs when needed.
- Impact: Issue #33 preserves the current devotion response contract while adding billing side effects. REST Docs should continue to document the existing weekly devotion response fields and verify that generated-charge response fields are not part of the contract.

### 2026-06-19 - Issue #33 Weekly Devotion Duplicate Submission Error Contract

- Context: Issue #33 needed a stable API error contract for requests after a weekly devotion record has already been finally submitted.
- Decision: If `weekly_devotion_records.submitted_at` already exists for the same campus, user, and week, both duplicate `submit = true` requests and post-submission `submit = false` saves fail with `DEVOTION_WEEKLY_ALREADY_SUBMITTED`, HTTP `409 CONFLICT`, and the user-facing message `이미 제출된 주간 경건생활은 수정할 수 없습니다.`
- Impact: The devotion submission boundary blocks same-week resubmission before billing reruns. The generated `PENALTY` charge for the first submission is not recalculated or overwritten through the normal weekly devotion API.

### 2026-06-19 - Issue #33 Daily Devotion Check After Weekly Submission

- Context: The one-time weekly devotion submission policy also needs to prevent the daily check API from changing the same week's source rows after final submission. Otherwise the weekly summary and generated `PENALTY` charge can diverge.
- Decision: If `weekly_devotion_records.submitted_at` already exists for the campus, user, and week containing `recordDate`, `PUT /api/v1/campuses/{campusId}/devotions/me/days/{recordDate}` must fail with `DEVOTION_WEEKLY_ALREADY_SUBMITTED`, HTTP `409 CONFLICT`, and the existing user-facing message `이미 제출된 주간 경건생활은 수정할 수 없습니다.`
- Impact: After final weekly submission, daily check requests for the same week must not create or update `weekly_devotion_records`, `devotion_daily_checks`, or `charge_items`. This preserves the MVP rule that same-week record modification, recalculation, and delta charge flows are excluded.

### 2026-06-19 - Issue #33 One-Time Weekly Devotion Submission

- Context: Issue #33 connects weekly devotion submission to automatic `PENALTY` charge creation. A previous open question asked how to handle resubmitting a weekly devotion record after the generated charge became terminal.
- Decision: Weekly devotion submission is one-time. Once `weekly_devotion_records.submitted_at` exists for a user/campus/week, the same weekly record cannot be submitted again. A later `submit = true` request for the same week must fail instead of recalculating or overwriting the existing devotion submission or charge. `submit = false` weekly saves are allowed only before final submission and must not create or update `PENALTY` charges.
- Impact: Issue #33 does not need a terminal charge resubmission policy because same-week resubmission is blocked at the devotion submission boundary. Development must test that first `submit = true` creates one combined `PENALTY` charge, duplicate `submit = true` fails, missing active `PENALTY` account fails the whole submission without creating a charge, and pre-submit `submit = false` saves do not create charges.

### 2026-06-19 - Issue #32 Penalty Rule And Fine Calculation Scope

- Context: Issue #32 still had an older API draft for devotion fine calculation, while the latest Notion integrated plan and API pages define penalty rule management APIs separately from the weekly devotion submission and charge creation flow.
- Decision: Issue #32 follows the latest Notion penalty rule API paths: `GET /api/v1/campuses/{campusId}/penalty-rules`, `POST /api/v1/admin/campuses/{campusId}/penalty-rules`, and `PATCH /api/v1/admin/penalty-rules/{ruleId}`. The issue also implements a `DevotionFineCalculator` domain service and calculation result model for `QUIET_TIME`, `PRAYER`, `BIBLE_READING`, and `SATURDAY_LATE` using the approved penalty table. The old draft endpoint `GET /api/v1/campuses/{campusId}/devotions/fines?weekStartDate=` is not part of Issue #32 unless the user explicitly approves a separate preview API later.
- Impact: Issue #32 implementation must not create or update `charge_items`; the real weekly submission to `PENALTY` charge integration remains Issue #33. REST Docs are required for the penalty rule APIs, while the calculator is verified with focused domain/application tests. Swagger documentation annotations must not be added.

### 2026-06-19 - Issue #32 Active Penalty Rule Replacement And Type Pairing

- Context: Issue #32 still needed final user approval for duplicate active penalty rule behavior and whether `ruleType`/`calculationType` combinations are flexible or fixed.
- Decision: When an administrator creates a new ACTIVE penalty rule for the same campus and `ruleType`, the previous ACTIVE rule of that type is automatically deactivated and the new rule becomes the only ACTIVE rule. `ruleType` and `calculationType` combinations are fixed: `QUIET_TIME`, `PRAYER`, and `BIBLE_READING` only allow `MISSING_COUNT`, while `SATURDAY_LATE` only allows `LATE_MINUTE`.
- Impact: Issue #32 must validate invalid type/calculation combinations and prevent multiple ACTIVE rules for the same campus/rule type from remaining active. Tests must cover replacement behavior and invalid pairing rejection.

### 2026-06-19 - Issue #55 Billing Charge List Error Code

- Context: During Issue #55 implementation, the existing billing charge query authorization failures had user-facing messages for "my charge list" and "campus charge list" access, but the approved detailed code list did not yet include a stable code for charge-list authorization errors.
- Decision: Add `BILLING_CHARGE_LIST_FORBIDDEN` and use it for billing charge list authorization failures while preserving the existing user-facing messages.
- Impact: Billing charge query APIs can return a detailed domain-prefixed code instead of reusing a less precise billing account/status code or a broad `FORBIDDEN` code. This remains within the Issue #55 refactor scope and does not change API paths or DB schema.

### 2026-06-19 - Common Error Code And Request Validation Refactor Policy

- Context: After Issue #36 was merged, charge query code showed that request validation and user-facing error messages were spread across controllers, presentation helpers, application services, and broad shared error codes such as `INVALID_REQUEST`. The user decided the stable API error contract and validation structure before creating a separate refactor issue.
- Decision: Error responses use `HTTP status + detailed code` as the fixed API contract. The `message` field is managed as user-facing display text. `ErrorCode` remains one global enum, but each code should be split by domain prefix, such as `BILLING_INVALID_SORT_DIRECTION` or `CAMPUS_MEMBER_NOT_FOUND`. Invalid `page`, `size`, or `sort` values must return `400` instead of being silently corrected. Simple DTO validation uses Bean Validation. Pagination and sorting parsing move to a common request validation component. Business rules move to explicit policy classes such as `CampusRolePolicy`, `ChargeStatusPolicy`, and `BillingAccessPolicy`. This cleanup is handled as one separate refactor issue after Issue #36, not inside the already merged #36 feature PR.
- Impact: Future development must not keep adding broad `INVALID_REQUEST` usage with hardcoded messages when a stable domain error code is required. REST Docs must document error responses using the detailed `code`, and tests must cover invalid pagination/sorting values returning `400`. This refactor must not add new features, change API paths, change DB schema, or add Swagger documentation annotations.

### 2026-06-18 - Issue #36 Charge Query Date Filter And Monthly Summary Policy

- Context: Issue #36 originally listed `startDate` and `endDate` query parameters for charge list APIs, but the user reconsidered the product behavior during development. The user preferred not to expose manual date-range filtering in the API contract and wanted the app to show recent paid/charged history without deleting older records.
- Decision: Remove `startDate` and `endDate` from the four Issue #36 charge query APIs. The backend keeps charge history queryable through pagination, sorting, `paymentCategory`, `status`, `userId`, and `keyword` filters. The client may choose to emphasize recent paid items in the default screen, but backend records are not hidden or deleted by time. For `GET /api/v1/campuses/{campusId}/charges/me/summary`, `monthlyPaidAmount` is calculated by `paidAt` in the selected year/month, while `monthlyUnpaidAmount`, `monthlyTotalChargeAmount`, and `monthlyByCategory` use charge `createdAt` in the selected year/month as the "charged" period.
- Impact: Issue #36 implementation, REST Docs, and tests must not document or bind `startDate`/`endDate`. Query tests must cover full-history pagination and the split monthly summary basis (`paidAt` for paid totals, `createdAt` for charged/unpaid totals). If a future UX needs explicit date-range search, it must be approved as a new API contract change.

### 2026-06-18 - Issue #36 Charge List And Campus Summary Contract

- Context: Issue #36 needed final charge query behavior for member-facing charge lists, member payment summary, campus-level administrator aggregation, and administrator member detail.
- Decision: Use the latest Issue #36 API paths: `GET /api/v1/campuses/{campusId}/charges/me`, `GET /api/v1/campuses/{campusId}/charges/me/summary`, `GET /api/v1/admin/campuses/{campusId}/charges`, and `GET /api/v1/admin/campuses/{campusId}/members/{userId}/charges`. Do not implement `/api/v1/users/me/charges`, `/api/v1/campuses/{campusId}/charges`, or `/api/v1/campuses/{campusId}/charges/unpaid-users`. Administrator campus charge query returns `summary + members[]` aggregation only and does not include individual charge items. Administrator member detail includes target member `userId`, `name`, and `email`, and uses the same charge item `account`/`source` structure as the member-facing list.
- Impact: Issue #36 controller responses must use DTOs and the common `ApiResponse` envelope, not Entity returns. Request/Response DTOs and application Result records remain separated. Detailed API contracts are verified through Spring REST Docs tests, without adding Swagger documentation annotations.

### 2026-06-18 - Issue #35 Charge Status Transition Policy

- Context: Issue #35 needed final clarification for charge payment completion, waiver, cancellation, and administrator correction behavior before development. The earlier issue draft and Notion API page allowed an administrator to set `PAID`, but the user chose a stricter rule.
- Decision: User payment completion is the only path that changes an `UNPAID` charge to `PAID`. Administrators must not mark a charge as `PAID`. Administrators may change a charge to `WAIVED` or `CANCELED`, and may revert an incorrectly handled `PAID`, `WAIVED`, or `CANCELED` charge back to `UNPAID`. Issue #35 does not store an administrator status-change reason and does not add `statusChangedReason`, `waivedAt`, `canceledAt`, or a status history table.
- Impact: Issue #35 implementation, tests, REST Docs, GitHub issue body, and Notion API documentation must use admin target statuses `UNPAID`, `WAIVED`, and `CANCELED` only. `paidAt` is used for user payment completion; when an administrator reverts a `PAID` charge to `UNPAID`, `paidAt` should be cleared. Automatic source rerun behavior for terminal charges remains outside this decision and must still be confirmed when wiring Issue #33/#39.

### 2026-06-18 - Issue #35 Payment Completion Request Contract

- Context: Issue #35 specified that `paidAt` is optional for `PATCH /api/v1/campuses/{campusId}/charges/me/{chargeItemId}/paid`, but left the empty-body contract and timestamp format to be fixed before TDD implementation.
- Decision: The user payment completion API accepts both an omitted request body and an empty JSON body. If `paidAt` is omitted, the server time is used. When `paidAt` is provided, the request must use an offset-aware instant format such as `2026-06-12T12:30:00Z`.
- Impact: Issue #35 controller tests and REST Docs must document optional `paidAt`, omitted-body support, and offset-aware `Instant` parsing. The response should expose `paidAt` as an instant value.

### 2026-06-18 - Issue #34 Admin Account List And Penalty Charge Rerun Policy

- Context: PM review found that service-level `ADMIN` could not list campus payment accounts without campus membership, and `BillingService.createPenaltyCharge` raised a unique constraint error when the same penalty charge source was executed again.
- Decision: `GET /api/v1/campuses/{campusId}/payment-accounts` allows either service-level `ADMIN` or an ACTIVE campus member. `BillingService.createPenaltyCharge` behaves as create-or-update for an existing `UNPAID` `PENALTY` charge with the same `(campusId, userId, paymentCategory, sourceType, sourceId)`: it updates the latest active PENALTY account snapshot, title, reason, amount, and due date, then returns the same row.
- Implementation guard: The Issue #34 billing foundation keeps terminal charges guarded so `PAID`, `WAIVED`, or `CANCELED` charges are not overwritten by a source rerun. For Issue #33 specifically, the later 2026-06-19 decision blocks same-week devotion resubmission at the devotion boundary, so terminal devotion charge reruns should not occur through the normal weekly submission flow.
- Impact: Issue #34 service and controller tests must cover service-admin account list access and service-level penalty charge reruns for existing `UNPAID` charges. The DB unique key remains a safety net, but normal service reruns should not surface unique constraint exceptions for existing `UNPAID` charges.

### 2026-06-18 - Issue #34 Member Payment Account Response Contract

- Context: The Issue #34 payment account list API is available to every ACTIVE campus member, but older Notion endpoint detail examples included admin-oriented fields such as `ownerUserId` and `isActive`.
- Decision: `GET /api/v1/campuses/{campusId}/payment-accounts` returns active accounts only and exposes the member-facing fields required for payment: `id`, `accountType`, `nickname`, `bankName`, `accountNumber`, and `accountHolder`. It does not expose admin-only metadata such as `ownerUserId`, `isActive`, `createdAt`, or `deactivatedAt` in the member-facing response.
- Impact: Issue #34 REST Docs and controller tests must document the reduced member-facing response. Account numbers remain fully visible because they are required for bank transfer payment.

### 2026-06-18 - Issue 30 Same-Level Campus Role Assignment And Coffee Duty Permission

- Context: Issue #30 role hierarchy wording could be read as "only roles below the requester can be changed or assigned." The user clarified the final behavior during the development session.
- Decision: A campus manager can assign campus roles up to the manager's own campus role level, but cannot change or assign roles above that level. `MINISTER` can change another user to `MINISTER`, `ELDER`, `CAMPUS_LEADER`, or `MEMBER`. `ELDER` can change another user to `ELDER`, `CAMPUS_LEADER`, or `MEMBER`, but cannot change an existing `MINISTER` or assign `MINISTER`. `CAMPUS_LEADER` can change another user to `CAMPUS_LEADER` or `MEMBER`, but cannot change an existing `ELDER` or `MINISTER` or assign those roles. `MEMBER` cannot change roles. Service-level `ADMIN` can change all campus roles, and service-level `MANAGER` alone does not grant campus role change permission. Coffee duty management is allowed for service-level `ADMIN` and active campus members whose campus role is not `MEMBER`; service-level `MANAGER` alone does not grant coffee duty management permission.
- Impact: Issue #30 implementation, tests, and REST Docs must use same-level assignment semantics and non-`MEMBER` coffee duty management permission. Any earlier "below only" interpretation is superseded.

### 2026-06-18 - Issue 30 Campus Role Hierarchy And Coffee Duty Contract

- Context: Issue #30 needed final confirmation before development because the campus role update API path, coffee duty assignment cardinality, and campus role downgrade rules were ambiguous.
- Decision: Issue #30 must follow the latest Notion API contract. Campus role changes use `PATCH /api/v1/admin/campuses/{campusId}/members/{campusMemberId}/campus-role`, where `campusMemberId` means `campus_members.id`. Coffee duty assignment is limited to one active `DutyType.COFFEE` assignee per campus and uses `PUT /api/v1/admin/campuses/{campusId}/duty-assignments/coffee` to assign/replace the active assignee and `DELETE /api/v1/admin/campuses/{campusId}/duty-assignments/coffee/{assignmentId}` to revoke. The campus role hierarchy is `MINISTER > ELDER > CAMPUS_LEADER > MEMBER`. A campus manager may change roles only below their own role: `MINISTER` can change `ELDER`, `CAMPUS_LEADER`, and `MEMBER`; `ELDER` can change `CAMPUS_LEADER` and `MEMBER`, but not `MINISTER`; `CAMPUS_LEADER` can change `MEMBER`, but not `MINISTER` or `ELDER`; `MEMBER` cannot change roles. Service-level `ADMIN` can change any campus member role in any campus. The last campus management role holder may still be downgraded to `MEMBER`; do not block it with a last-manager guard in Issue #30.
- Impact: Issue #30, Notion planning, API documentation, REST Docs tests, and implementation must use these paths and authorization rules. Development must not use the older `members/{memberId}/role`, generic `POST duty-assignments`, or `PATCH revoke` API drafts for #30.
- Status: Role assignment hierarchy wording is superseded by the later 2026-06-18 decision `Issue 30 Same-Level Campus Role Assignment And Coffee Duty Permission`: same-level assignment is allowed. API paths, `campusMemberId`, coffee duty cardinality, and last-manager downgrade policy remain valid.

### 2026-06-18 - Campus Member Delete And Management Permission For Issue 29

- Context: PR #50 / Issue #29 needed an additional campus member delete feature. The user approved adding the feature and clarified that everyone except normal users can manage campus members.
- Decision: Add `DELETE /api/v1/campuses/{campusId}/members/{membershipId}`. The endpoint soft-deletes the campus membership by changing `campus_members.status` to `INACTIVE` and returns `204 No Content` on success. Campus member management is allowed for service-level `ADMIN` and active campus members whose `campus_role` is not `MEMBER` (`MINISTER`, `ELDER`, `CAMPUS_LEADER`). A normal campus `MEMBER` cannot manage or delete campus members. If an inactive/deleted user joins again by invite code, the existing membership is reactivated as `ACTIVE + MEMBER` to respect the `(campus_id, user_id)` uniqueness rule.
- Impact: Issue #29 and PR #50 now include campus member delete in addition to campus creation, invite-code join, my-campus list, and campus detail APIs. Tests and REST Docs must cover delete permission, soft delete status transition, service-admin delete without campus membership, and rejoin after inactive membership.

### 2026-06-18 - Campus API Response And Error Contract For Issue 29

- Context: Issue #29 needed final confirmation for ambiguous campus response fields, admin campus-detail behavior, and user-facing error messages before implementation.
- Decision: `GET /api/v1/campuses/me` returns only the current user's `ACTIVE` memberships, with each item containing `membershipId`, `campusId`, `campusName`, `region`, `campusRole`, and `status`; `joinedAt` is excluded. Campus detail returns `campusId`, `name`, `region`, `description`, `isActive`, `myCampusRole`, `membershipStatus`, and conditionally `inviteCode`. `ADMIN` can see all campus details and invite codes; when an admin is not a member of the campus, `myCampusRole` and `membershipStatus` are `null`. Error messages are `유효하지 않은 초대코드입니다.`, `이미 가입된 캠퍼스입니다.`, `캠퍼스 조회 권한이 없습니다.`, and `캠퍼스 생성 권한이 없습니다.`.
- Impact: Issue #29 implementation and REST Docs must follow these response shapes and messages. Older endpoint drafts with different field names or creator roles are superseded by this decision and the latest Issue #29 scope.

### 2026-06-18 - Campus Creation Does Not Create Payment Account Or Penalty Rules

- Context: Older local docs still said campus creation should create a `PENALTY` payment account and default `penalty_rules`, while the latest Issue #29, Notion integrated document, and current development delegation state that campus creation and account/rule setup are separate.
- Decision: Campus creation must not receive `penaltyAccount`, must not create `PaymentAccount`, and must not create default `penalty_rules`.
- Impact: Issue #29 tests must guard that campus creation only creates the campus and creator membership. Billing prerequisites are configured through separate admin setup flows.

### 2026-06-18 - Issue 29 Campus Role And Invite Code Visibility Clarification

- Context: Issue #29 needed a documentation-only clarification so the service-level role, campus-level role, campus creation permission, campus management permission, and invite-code visibility rules are consistently recorded without overwriting the existing #29 decisions.
- Decision: `users.role` is the service-level role set and uses `USER`, `MANAGER`, and `ADMIN`. `campus_members.campus_role` is the campus-level role set and uses `MINISTER`, `ELDER`, `CAMPUS_LEADER`, and `MEMBER`. `MANAGER` and `ADMIN` can create campuses. When a `MANAGER` creates a campus, that user is registered in the new campus as `ACTIVE + MINISTER`. `MANAGER` is not itself a campus-management role; campus management must be based on `campus_members.campus_role`. `ADMIN` can access all campus details. Invite codes are included in campus creation responses, and can be viewed by `ADMIN`, `MINISTER`, `ELDER`, and `CAMPUS_LEADER`, but must not be exposed in normal `MEMBER` campus detail responses. `GET /api/v1/campuses/me` returns only the current user's `ACTIVE` memberships.
- Impact: Issue #29 documentation and implementation must keep service roles and campus roles separate. Service-level admin user-role management APIs, last `ADMIN` protection, and last campus manager protection are separate pending/admin issues and are not implemented as part of #29.

### 2026-06-16 - User Owns All Project Decisions

- Context: The user stated that Codex must not develop or implement based on guesses.
- Decision: All ambiguous or unusual decisions must be asked of the user before implementation.
- Impact: Codex must stop and ask before choosing product behavior, architecture, schema, deployment, test strategy, monitoring scope, resume metrics, or implementation tradeoffs.

### 2026-06-16 - Track Resume Metrics During Development

- Context: The project will be deployed and operated, and the user wants resume-ready quantitative evidence.
- Decision: Codex should record measurable project progress, tests, troubleshooting, and improvements in Markdown and Obsidian.
- Impact: Metrics are tracked in `docs/resume-metrics.md` and mirrored to the Obsidian FaithLog note.

### 2026-06-16 - Backend API And Issue Policy Alignment

- Context: The user provided final backend policy decisions for auth, FCM, poll comments, devotion APIs, coffee charge automation, issue status management, and MVP exclusions.
- Decision: GitHub Issues must follow the final policies recorded in `docs/backend-implementation-policy.md`.
- Impact: Issues #21, #27, #28, #31, #38, #39, and #40 were updated with final policy details. Manual `칸반 상태:` lines were removed from #17~#41 where present so GitHub Project Board Status remains the source of truth.

### 2026-06-16 - Codex Hook Development Rules

- Context: The user requested a Codex Hook document that consolidates TDD, final FaithLog design rules, architecture rules, security rules, forbidden-term checks, test rules, Obsidian documentation, and GitHub Issue/Project workflow.
- Decision: Use `AGENTS.md` as the single Agent rule file, and put detailed development hook rules in `docs/codex/FAITHLOG_CODEX_HOOK.md`.
- Impact: Issue #43 was created and connected to the FaithLog Backend Kanban board. The board card was moved to `In Progress`. `PollComment` was not treated as a forbidden term because poll comments are MVP scope in #38.

### 2026-06-16 - Agent Rule File Consolidation

- Context: The user requested that Agent rules be merged into one file.
- Decision: `AGENTS.md` is the single source of Agent instructions for Codex in this repository.
- Impact: The former `AGENT.md` content was merged into `AGENTS.md`, and repository documentation now points to `AGENTS.md`.

### 2026-06-16 - Poll Template Default Policy

- Context: The user clarified which poll templates should exist by default.
- Decision: Only the coffee poll template should be provided as a default template. Wednesday worship, Saturday shepherd meeting, and custom poll templates should be created by an admin.
- Impact: Issue #37 and Codex Hook rules must treat coffee as the only default poll template. Other poll templates are admin-created and must not be silently seeded unless the user later approves a new decision.

### 2026-06-16 - Poll Template Weekly Auto Generation Setting

- Context: The user asked whether polls can be generated automatically every week, then decided that admins should be able to set this when creating custom/admin-created templates.
- Decision: Poll templates can include a weekly auto-generation setting chosen by the admin at template creation time. Templates without auto-generation enabled are used only for manual poll creation.
- Impact: Issue #37 must capture the template setting/API scope, and Issue #24 must execute enabled template schedules with duplicate prevention.

### 2026-06-16 - Coffee Duty Poll Time Settings

- Context: The user clarified that Notion ERD includes coffee poll timing design.
- Decision: The coffee duty assignee can set the weekly coffee poll auto-generation time and the coffee poll close time.
- Impact: Issue #37 must store these timing settings on the coffee poll template according to the Notion ERD column names. Issue #24 must use those settings when generating and closing weekly coffee polls.

### 2026-06-17 - Devotion Penalty Table

- Context: The user provided the current devotion check notice used by the ministry team.
- Decision: Devotion penalty rules use a 5-day weekly standard. Quiet time missing count is charged at 500 KRW per day, prayer missing count is charged at 500 KRW per day, Bible reading missing count is charged at 300 KRW per day, and Saturday lateness is charged as 1,000 KRW base plus 100 KRW per late minute. For the referenced week, lateness minutes are all 0.
- Impact: Issue #32 must seed or configure `penalty_rules` to support `QUIET_TIME`, `PRAYER`, `BIBLE_READING`, and `SATURDAY_LATE` with these amounts. Issue #33 must calculate one combined weekly `PENALTY` charge per `weekly_devotion_records.id`.

### 2026-06-17 - Final Implementation Overrides For Conflicting Old Specs

- Context: The user approved the implementation direction previously identified by Codex for conflicts between older Notion/API drafts and the latest local decisions.
- Decision: Refresh Token storage follows Redis allowlist and not the old `refresh_tokens` table design. Poll responses must use request field `optionIds` and `poll_response_options`, not request field `optionId` or `poll_responses.option_id`. Devotion implementation uses the weekly `PUT /api/v1/campuses/{campusId}/devotions/me/weeks/{weekStartDate}` flow to create or update 7 daily rows; the old single-day devotion API is not the MVP implementation path.
- Impact: Auth, poll, and devotion issues must treat these decisions as higher priority than older Notion API text. Any implementation or API documentation that still exposes the old paths/fields must be corrected before development is considered complete.
- Status: Partially superseded for devotion. The 2026-06-19 decision `Issue #31 Devotion Daily Check And Weekly Submission Sync` includes the daily check API in MVP while keeping weekly submission as the only submission/penalty trigger.

### 2026-06-19 - Issue #31 Devotion Daily Check And Weekly Submission Sync

- Context: The user asked to compare Issue #31 with the latest Notion planning/API/ERD and update local planning to match Notion before development.
- Decision: Issue #31 includes both daily check and weekly save/submit flows. The daily check API is `PUT /api/v1/campuses/{campusId}/devotions/me/days/{recordDate}` and creates or updates the matching `devotion_daily_checks` row while synchronizing the weekly row if missing. Daily checks never update `submitted_at` and never create or update `PENALTY` charges. The weekly API remains `PUT /api/v1/campuses/{campusId}/devotions/me/weeks/{weekStartDate}` and uses request field `dailyChecks`. Weekly submission creates or updates Monday-Sunday daily rows, fills missing submission dates with false defaults, updates `weekly_devotion_records.submitted_at` when `submit = true`, and uses `weekly_devotion_records.submitted_at` as the submission/missing-user source of truth.
- Impact: Issue #31 development must implement the daily API and weekly API together. Issue #33 remains responsible for the final penalty charge integration, but #31 tests must prove daily checks do not trigger submission or billing behavior.

### 2026-06-19 - Monthly Devotion Statistics Split From Issue #31

- Context: Notion includes a monthly devotion statistics page, but Issue #31 already covers daily check, weekly save/submit, weekly read, and admin missing-user lookup.
- Decision: Monthly devotion statistics are excluded from Issue #31 and must be tracked as a separate GitHub Issue.
- Impact: Issue #31 stays focused on the daily/weekly submission flow. Monthly statistics development must verify the final Notion 10.5 API and response contract before implementation instead of guessing the API path or aggregation rules.

### 2026-06-19 - Empty Weekly Devotion Lookup Returns Defaults

- Context: The user chose the mobile-friendly behavior for `GET /api/v1/campuses/{campusId}/devotions/me/weeks/{weekStartDate}` when the user has no record for the requested week.
- Decision: My weekly devotion lookup must not return 404 just because the weekly record does not exist yet. Instead, it returns a default weekly response with Monday-Sunday `dailyChecks`, all check fields false, summary counts 0, `saturdayLateMinutes = 0`, and `submittedAt = null`.
- Impact: Issue #31 development must update tests and implementation so the weekly screen can render immediately before the user creates any daily checks or weekly submission. Missing-user admin lookup still treats absent weekly records as missing submissions.

### 2026-06-17 - Pagination Sorting Redis TTL And Notification Failure Policies

- Context: The user approved the recommended implementation policies for list APIs, Redis token TTLs, and notification failure handling.
- Decision: List APIs use common pagination query parameters `page`, `size`, and `sort`; default page is 0, default size is 20, maximum size is 100, and default sorting is latest-first unless a domain has an explicit order such as poll option `sortOrder,asc`. Access token blacklist TTL uses the access token remaining lifetime plus 60 seconds. Refresh token allowlist TTL uses the refresh token expiration. Refresh token reuse-detection keys, when used, live until the refresh token expiration. Notification sends do not retry automatically in MVP. Success, failure, and skip results are all saved to `notification_logs`; invalid or unregistered FCM token errors deactivate the affected token.
- Impact: Repository query APIs, Redis auth infrastructure, and notification services must implement these defaults and cover them with tests where applicable.
- Status: Notification retry wording is superseded for Issue #40 by the 2026-06-20 decision `Issue #40 Notification Async Retry Policy`: transient notification send failures retry per token up to 3 times with `1s -> 5s -> 30s` intervals.

### 2026-06-17 - Lateness Penalty Calculation

- Context: The user confirmed the intended interpretation of the Saturday lateness penalty rule.
- Decision: If `saturdayLateMinutes = 0`, the lateness penalty is 0 KRW. If `saturdayLateMinutes > 0`, the lateness penalty is `1,000 + saturdayLateMinutes * 100` KRW.
- Impact: Issue #32 must implement this conditional formula in the devotion penalty calculator.

### 2026-06-17 - Campus Creation Includes Penalty Account And Penalty Rules

- Context: The user identified that creating the campus penalty account and penalty rules at campus creation time reduces later runtime exceptions in devotion submission.
- Decision: Campus creation must also create the campus penalty account and default penalty rules. The campus creation request/flow must collect or receive enough penalty account information to create the active `PENALTY` payment account, and must initialize the default devotion penalty rules from the approved penalty table.
- Impact: Issue #29 and Issue #34 must be aligned so campus onboarding creates the required billing prerequisites. Issue #33 can assume a properly onboarded campus has an active `PENALTY` account, while still returning a clear error if the account is missing due to legacy or corrupted data.
- Status: Superseded. This is a historical record only. The later 2026-06-18 decision `Campus Creation Does Not Create Payment Account Or Penalty Rules` and the latest Issue #29 scope take precedence: campus creation and account/rule setup are separate, and campus creation must not receive `penaltyAccount`, create `PaymentAccount`, or create default `penalty_rules`.

### 2026-06-18 - Issue #34 Payment Account And Charge Foundation Scope

- Context: Issue #34 was checked against the latest Notion integrated plan, final ERD, and API design before development.
- Decision: Issue #34 follows the Notion billing foundation model: implement `PaymentAccount`, `ChargeItem`, `PaymentCategory`, `ChargeSourceType`, `ChargeStatus`, payment account list/create/deactivate APIs, account snapshot support, missing-account failure behavior, and duplicate charge prevention. Campus creation does not create accounts or default penalty rules. Manual admin charge creation is not MVP scope.
- Impact: Detailed API contracts must be verified through Spring REST Docs tests, while Swagger/springdoc remains for simple API exploration. Later charge-producing flows must use the billing foundation instead of manipulating another domain's entity directly.

### 2026-06-18 - Issue #34 Payment Account Activation And Visibility Policy

- Context: The user finalized the remaining account-management behavior before Issue #34 development.
- Decision: Each campus can have only one active payment account per `account_type`. All active campus members can view campus payment accounts, and account numbers are fully visible because users need them for bank transfer payment. Creating a new active account automatically deactivates the previous active account for the same campus and account type. Accounts can be deactivated even if unpaid charge items are linked to them. When a new active account replaces the old one, existing `UNPAID` charge items for that campus and payment category are re-linked to the new active account and their account snapshots are updated. Terminal `PAID`, `WAIVED`, and `CANCELED` charge items keep their historical snapshots. Issue #34 implements only the billing foundation service; actual devotion and coffee auto-charge flow integration remains in Issue #33 and Issue #39.
- Impact: Issue #34 tests must cover one-active-account-per-type behavior, member account list access, full account-number exposure in payment account responses, deactivation with unpaid charges, unpaid charge re-linking on account replacement, and preservation of terminal charge snapshots.

### 2026-06-17 - Coffee Poll Requires Coffee Duty Assignment

- Context: The user decided that coffee poll behavior should fail clearly when no coffee duty assignee exists.
- Decision: If a coffee poll flow requires a coffee duty assignee and no active `CampusDutyAssignment` with `DutyType.COFFEE` exists for the campus, the API must fail with a clear user-facing message: `관리자에게 문의하세요`.
- Impact: Issue #30 must provide active coffee duty assignment management, and Issue #37/#39 must validate the assignment before coffee poll setup or coffee charge flow where required.

### 2026-06-19 - Issue #37 Coffee Brand And Menu Catalog

- Context: The user clarified that coffee ordering is initially limited to Compose Coffee, but the design should allow additional coffee brands later.
- Decision: Do not store Compose Coffee menu names and prices in frontend-only data or Java enums. Issue #37 must add backend-managed coffee brand/menu catalog data. MVP seeds one active brand, Compose Coffee, and seeds all current Compose Coffee menu items into the catalog. The default coffee poll template starts with these five options: iced americano, americano, iced tea, iced latte, and latte. Additional template options are added by selecting from the backend coffee menu catalog. `poll_template_options` and `poll_options` store copied menu name/code/price snapshots so later catalog price changes do not mutate already-created polls or charges.
- Impact: Issue #37 must include `coffee_brands`, `coffee_menu_catalog`, catalog lookup API, Compose Coffee full-menu seed, and default coffee template option seeding. Brand/menu admin CRUD and additional brand onboarding are excluded unless the user approves a separate issue. Development must verify the full Compose Coffee seed list and prices from an approved current source before implementation instead of guessing.

### 2026-06-19 - Issue #37 Coffee Catalog Source And API Path

- Context: The user approved the source of truth for Compose Coffee menu seed data and accepted the recommended catalog lookup API paths.
- Decision: Issue #37 must seed Compose Coffee menu names and prices from the official Compose Coffee menu board/source available at implementation time. If the official website blocks automated access, the development session must use another official source provided by Compose Coffee, such as the official app/menu image/menu board, and record the source used. The catalog lookup APIs are `GET /api/v1/coffee-brands` and `GET /api/v1/coffee-brands/{brandId}/menus`.
- Impact: Issue #37 development must not guess menu prices from blogs or unofficial lists. REST Docs tests must document both catalog lookup APIs, and the seed verification record must name the official source and capture date.

### 2026-06-19 - Issue #38 Poll Result Visibility

- Context: The user clarified how poll result visibility should work for normal and anonymous polls.
- Decision: Normal poll results are visible to all active campus members, not only admins. If `polls.is_anonymous = false`, the result response may show who voted for each option. If `polls.is_anonymous = true`, nobody should be able to identify who voted for which option through result APIs; return aggregate counts only and hide respondent user identifiers/names. The backend still stores `poll_responses.user_id` for duplicate response prevention, response editing, and missing-member calculation, but does not expose voter identity for anonymous poll results.
- Impact: Issue #38 must use a member-facing result endpoint, such as `GET /api/v1/campuses/{campusId}/polls/{pollId}/results`, and tests must cover both non-anonymous identity exposure and anonymous identity hiding. Admin-only missing-member lookup can remain separate.

### 2026-06-19 - Issue #38 Poll Result And Past Poll Visibility Window

- Context: The user clarified how long poll results and past polls should remain visible after a poll ends.
- Decision: Visibility windows are based on `polls.ends_at`. User-facing poll history, poll detail, and poll result lookup are visible to active campus members only until 3 days after `ends_at`. Admin-facing poll history, poll detail, and poll result lookup are visible in the admin page only until 7 days after `ends_at`. After the visibility window expires, expired polls should be hidden from lists and direct lookup should not expose the poll/result data.
- Impact: Issue #38 must add tests for member visibility before and after `ends_at + 3 days`, and admin visibility before and after `ends_at + 7 days`. The anonymous poll identity-hiding rule still applies during the visible window.

### 2026-06-19 - Issue #38 Poll-Level Result Query

- Context: The user clarified that seeing who voted for what should be handled as a full result view for one poll, not as separate option-level result lookup APIs.
- Decision: Issue #38 must provide one poll-level result API, `GET /api/v1/campuses/{campusId}/polls/{pollId}/results`. Do not create option-level result endpoints such as `/options/{optionId}/results` for MVP. The poll-level result response contains poll metadata, all options, vote counts, and, when the poll is not anonymous, respondent lists grouped under each option. Anonymous polls return aggregate counts only and omit respondent identity fields.
- Impact: API docs, tests, and frontend planning should treat the poll detail/result screen as a single poll-level result resource. Tests must verify that all options are returned in one response and that anonymous polls do not expose respondent identity.

### 2026-06-17 - Prayer Requests Group Board

- Context: The user described a weekly Saturday prayer request workflow where each sharing group collects member prayer requests and currently posts them as one KakaoTalk message. The user decided the app should replace the message view instead of generating KakaoTalk share text.
- Decision: Add a prayer request feature where all campus members can view the weekly prayer requests for all groups on one page. Prayer groups are managed inside an active prayer season that can start without a fixed end date and be manually closed when groups change. Prayer request input should also work on one page so a user with permission can enter multiple group members' weekly prayer requests together. KakaoTalk sharing is not MVP scope for this feature.
- Impact: A new prayer domain should be planned with prayer seasons, prayer groups, group members, weekly prayer boards, and member submissions. The read API must support one-page grouped output for the whole campus.

### 2026-06-17 - Prayer Request Editing And Conflict Policy

- Context: The user was concerned about simultaneous edits when multiple people can edit prayer requests from one page.
- Decision: Store prayer requests per member submission rather than as one large page blob. A weekly prayer submission can have nullable content for cases where there is no meeting or no request to write. Each member submission must use version-based optimistic locking. The client sends the current version when saving; the server saves only if the version still matches and otherwise returns a conflict instead of silently overwriting another edit.
- Impact: Prayer request write APIs must support partial person-level saves and `409 Conflict` behavior. The UI can show all members on one page, but persistence and conflict detection are scoped to each member's submission row.

### 2026-06-17 - Prayer Request No-Meeting And Writing Status Policy

- Context: The user clarified that prayer requests are still written even when there is no meeting, and that prayer requests do not need a separate submission deadline.
- Decision: `NO_MEETING`, if used, is not a blocking prayer request submission state. It only describes meeting schedule/context. Prayer request writing remains available even if there is no meeting. Because there is no separate deadline, do not split "not written yet" and "missing submission" into separate states; model/display prayer request writing as not written vs written.
- Impact: Issue #45 must not treat `NO_MEETING` as a reason to disable prayer request entry or as a missing-submission state. Schema/API/UI planning should keep meeting status separate from person-level prayer request content/submission status. The exact storage scope for meeting status, such as whole prayer week vs group-week, still needs user confirmation before schema implementation.

### 2026-06-17 - Daily Resume Monitor Manual Automation

- Context: The user requested a daily resume monitoring automation that reviews previous-day FaithLog work and writes resume-oriented Markdown notes in both project docs and the Obsidian vault.
- Decision: Implement the monitor as a manual script first, store its versioned prompt at `docs/prompts/daily-resume-monitor.md`, read the prompt and Agent rules each run, write only evidence-backed notes/metrics, record unverified items as pending decisions, and do not schedule the automation until a scheduling policy is explicitly approved.
- Impact: `python3 scripts/daily_resume_monitor.py` is the approved manual entry point. The monitor may update `docs/resume-metrics.md`, `docs/decision-log.md`, `docs/wiki/`, and the approved Obsidian FaithLog path, but it must not invent metrics or infer product/architecture/schema/API/security/deployment/testing direction.

### 2026-06-17 - Defer Flyway Until Feature Development Is Complete

- Context: The user decided that Flyway should be introduced after the main feature development work is complete, rather than used as the active schema mechanism during early feature implementation.
- Decision: During feature development, do not treat Flyway migrations as the primary implementation deliverable. Final Flyway migration scripts should be organized near the end of development after the approved domain model has stabilized.
- Impact: GitHub Projects should include a later infra/build task for final Flyway migration consolidation. Feature issues may define schema requirements and tests, but should not be blocked on final Flyway script authoring unless the user later changes this policy.

### 2026-06-17 - Remove Flyway From Active Runtime

- Context: The user explicitly requested removing Flyway now before raising the PR again.
- Decision: Remove Flyway Gradle dependencies, remove `spring.flyway` runtime configuration, and remove the placeholder Flyway migration file from the active codebase. Flyway remains deferred until the main feature development work is complete.
- Impact: Early feature development will rely on approved schema requirements and persistence tests instead of active Flyway migration scripts. A later infra/build task should reintroduce consolidated migrations when the domain model stabilizes.

### 2026-06-17 - Local Docker Uses JPA DDL Auto Update During Development

- Context: #27 Docker verification reached the application boot step after the local Postgres credential mismatch was resolved, but the app could not start against an empty local Docker database while Flyway remains deferred and `ddl-auto=validate` was active.
- Decision: For local Docker development verification only, default `SPRING_JPA_HIBERNATE_DDL_AUTO` to `update` so Hibernate can create or update the local development schema. Keep the value environment-overridable.
- Impact: Local Docker can boot and serve `GET /api/v1/health` during early feature development before final Flyway migration consolidation. This does not change the deferred Flyway policy or define a production migration strategy.

### 2026-06-17 - API Documentation Uses Spring REST Docs For Detailed Contracts

- Context: The user clarified that Swagger/springdoc should remain available for simple API exploration, but the codebase should not be cluttered with Swagger documentation annotations on Controllers, DTOs, or Entities.
- Decision: Swagger/springdoc is kept. Swagger annotation-centered documentation is not used as the main documentation approach. Detailed request/response API contracts are verified and documented through Spring REST Docs tests and generated snippets/asciidoc.
- Impact: New APIs or changed APIs should add MockMvc/WebMvc/Spring REST Docs tests where practical. Controllers, DTOs, and Entities must not be polluted with documentation-only Swagger annotations such as `@Operation`, `@Schema`, or `@ApiResponse`.

### 2026-06-17 - FCM Token Lifecycle Policy

- Context: The user clarified that FCM tokens are issued by the frontend Firebase SDK, not by the backend, and asked whether token expiration/staleness handling is included in the plan.
- Decision: The frontend must fetch the current FCM token on app entry/login and send it to the backend. The backend stores FCM tokens in `user_fcm_tokens` as the source of truth and handles the registration API as an idempotent upsert. Redis is not the source of truth for FCM tokens. The backend stores a frontend-generated `clientInstanceId` to identify the app installation, updates `lastSeenAt` and `lastRefreshedAt`, deactivates previous tokens for the same user/client instance when a token changes, deactivates token ownership for another user when the same token is re-registered, deactivates the current device token on logout, and excludes or deactivates tokens stale for 90 days. `UNREGISTERED`/token-not-registered failures deactivate the token immediately, while `INVALID_ARGUMENT` deactivates the token only when the payload is known to be valid.
- Impact: Issue #40, Notion planning, ERD, and API design must include FCM token lifecycle fields and upsert/stale-token behavior. Issue #24 or a later scheduler task may clean up stale tokens in batch, but send-time filtering must not target inactive or stale tokens.

### 2026-06-17 - Auth Refresh Logout Contract For Issue 28

- Context: Issue #28 needed clarification before development so Codex would not guess the refresh/logout request contracts, session rotation behavior, FCM dependency boundary, or REST Docs expectations.
- Decision: `POST /api/v1/auth/refresh` receives `refreshToken` in the JSON request body and returns the same token response shape as login. Refresh rotation keeps the same `sessionId` and replaces the refresh token identifier. `POST /api/v1/auth/logout` requires `Authorization: Bearer {accessToken}` and accepts optional body fields `refreshToken`, `clientInstanceId`, and `fcmToken`; logout must still succeed without FCM fields. Issue #28 should not implement Notification entities directly. It may define and call an application port for current-device FCM deactivation, while #40 owns the actual `user_fcm_tokens` persistence implementation. New/changed auth APIs should add Spring REST Docs tests.
- Impact: Issue #28, Notion auth API pages, backend policy, and the Codex hook must align on this contract before the development session starts. Tests must cover refresh rotation, old refresh token reuse, logout blacklist/allowlist deletion, optional FCM fields, no raw token storage, Redis TTLs, and REST Docs snippets.

## Pending Decisions

### 2026-06-17 - Prayer Request Meeting Status Storage Scope

- Context: Prayer request writing remains available even when there is no meeting, so meeting status must be separated from whether a prayer request can be written. The remaining unresolved decision is only where, if anywhere, meeting status should be stored.
- Pending question: Should `NO_MEETING` or an equivalent meeting status be stored at the whole prayer week level, at the group-week level, both, or omitted from MVP?
- Recommendation: If FaithLog only needs to display campus-wide off-weeks, keep `NO_MEETING` on `prayer_weeks`. If individual prayer groups can skip independently and the app must display that, add a group-week status model. If the app does not need to display meeting status, omit `NO_MEETING` from MVP and rely on nullable content plus written/not-written status.
- Current action: Prayer request writing availability and no-deadline status policy are approved; exact meeting status storage scope must be confirmed before schema implementation.

### 2026-06-17 - Daily Health And Response-Time Measurement Scope

- Context: The codebase exposes `GET /api/v1/health`, and the daily monitor can record health/latency metrics only if the measurement target and runtime are user-approved.
- Pending question: For daily monitoring, should Codex measure health/response time against a local booted app, a deployed environment URL, or leave health/latency as manual-only until the user defines the target?
- Recommendation: Approve one source of truth for daily health metrics first so the monitor can report comparable numbers instead of mixed local/deploy timings.
- Current action: Today's report records that the endpoint exists but does not publish latency or availability percentages.

### 2026-06-16 - Poll Comment Issue Split

- Context: Poll comments are now MVP scope. Issue #38 contains the PollComment implementation scope.
- Pending question: Should PollComment stay inside #38, or should a separate `[Feat] 투표 댓글 구현` issue be created?
- Current action: No new issue was created because the user said to create it only if needed, and #38 now contains the required implementation scope.

### 2026-06-16 - Project Board Domain Fields For Mixed Domains

- Context: Project Board items #23 and #24 have mixed domain text in their issue bodies, but the board Domain field is single-select.
- Pending question: Should #23 use `global` or another convention for `global/domain-admin`, and should #24 use `global` or `notification` for `global/notification`?
- Current action: Domain was left blank for #23 and #24 to avoid guessing.

### 2026-06-16 - Obsidian FaithLog Folder Path

- Context: The current Hook uses `/Users/josephuk77/obsidian/obsidian-writing-vault/Projects/FaithLog/`, while one attached requirement may imply `04_Projects/FaithLog`.
- Pending question: Should FaithLog Obsidian notes live under `Projects/FaithLog` or `04_Projects/FaithLog`?
- Current action: The path was not changed to avoid guessing the user's vault structure.

<!-- daily-resume-monitor:start:decision-log:2026-06-16 -->
### 2026-06-16 - Daily Resume Monitor Transcript Source

- Context: The daily resume monitor can only use Codex or assistant transcripts when their source/location is explicitly available and verifiable.
- Pending question: Where should the daily monitor read Codex conversation transcripts from, if transcript context should be included?
- Recommendation: Provide one stable local transcript source path or leave transcript analysis disabled.
- Current action: No transcript source was provided, so conversation transcripts were not inspected.
<!-- daily-resume-monitor:end:decision-log:2026-06-16 -->
