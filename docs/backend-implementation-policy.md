# FaithLog Backend Implementation Policy

This document records the current backend implementation source of truth.

## Dependency Security Baseline

- The backend Spring Boot plugin/BOM maintenance baseline is `3.5.15`.
- Spring Security config/core/crypto/web/test must resolve to `6.5.11` or later within the approved Spring Boot 3.5.x managed line.
- Do not override individual Spring Security transitive modules. Upgrade through the official Spring Boot BOM unless the user approves a separate BOM/property strategy.
- The dependency contract must fail if any Spring Security runtime/test module resolves to the vulnerable 6.5.0-6.5.10 range.
- A maintenance dependency upgrade must preserve the existing API, authentication/authorization meaning, database schema, and infrastructure policy. Unexpected compatibility fixes require user approval before production changes.
- Do not add an eager-header workaround or change servlet `ERROR` dispatch/`/error` authorization policy without an explicit user decision.

## Auth

- Refresh Token is stored in Redis, not in the database.
- The old Notion ERD `refresh_tokens` table is superseded by the Redis allowlist decision and must not be implemented as the MVP refresh-token source of truth.
- Refresh Token uses a Redis allowlist.
- Access Token remains stateless JWT, but logout uses Redis blacklist/denylist.
- Role changes immediately invalidate already issued Access Tokens through `users.token_version`.
- Access Token must include `tokenVersion`, and the authentication filter must compare the token `userId/tokenVersion` with the current persisted user `tokenVersion`.
- Service-level role changes and campus role changes both increment the target user's `tokenVersion`.
- Token version mismatch uses the existing authentication failure policy (`AUTH_UNAUTHORIZED`); Issue #76 does not add a separate ErrorCode.
- Refresh Token reissue must create a new Access Token using the latest persisted user role and token version.
- Refresh Token Rotation is required.
- One Redis Lua or equivalent CAS execution must own the complete rotate-or-revoke state transition. A matching expected JTI is replaced with the new JTI and rotation TTL; a mismatch deletes `auth:refresh:{userId}:{sessionId}` and stores the fixed marker key `auth:session:revoked:{userId}:{sessionId}` with the revocation TTL in that same atomic execution.
- Parallel requests using the same old Refresh Token allow exactly one rotation winner. A CAS loser or sequential reuse returns `401 AUTH_UNAUTHORIZED` after the atomic script has revoked that `userId + sessionId` session.
- If the revoked marker already exists, rotation is rejected without extending the marker TTL. The authentication filter rejects Access Tokens whose session marker exists.
- Session revocation is scoped to one `userId + sessionId`; another session belonging to the same user and sessions belonging to other users remain valid.
- The session revocation marker TTL is the configured Refresh Token lifetime plus 60 seconds. Redis failures during refresh CAS, session revoke, or authentication marker lookup fail closed.
- Normal logout keeps its existing current-access blacklist plus current-refresh deletion meaning and does not create a session revocation marker solely because of Issue #176.
- Redis must not store raw tokens. Store a hash or token identifier.
- Access Token must include `jti`, `userId`, `role`, `sessionId`, and `tokenVersion`.
- Refresh Token must include `userId`, `sessionId`, and `refreshJti`.
- Refresh rotation keeps the same `sessionId` and replaces the refresh token identifier.
- `POST /api/v1/auth/refresh` receives `refreshToken` in the JSON request body and returns the same token response shape as login.
- `POST /api/v1/auth/logout` requires `Authorization: Bearer {accessToken}` and accepts optional JSON body fields `refreshToken`, `clientInstanceId`, and `fcmToken`.
- Logout must succeed even when `clientInstanceId` and `fcmToken` are omitted.
- Auth must not directly implement or manipulate Notification entities. For issue #28, use an application port for current-device FCM deactivation; issue #40 owns the actual `user_fcm_tokens` persistence implementation.

Final auth APIs:

- `POST /api/v1/auth/refresh`
- `POST /api/v1/auth/logout`
- `DELETE /api/v1/users/me`

Do not use:

- `POST /api/v1/auth/reissue`

Account deletion policy:

- `DELETE /api/v1/users/me` provides the App Store account deletion flow.
- Account deletion is soft delete plus privacy anonymization, not physical row deletion.
- The request requires the current password and confirmation text `회원탈퇴`.
- Deletion sets `users.is_active = false` and `users.deleted_at = now()`.
- Deletion anonymizes `users.email` to `deleted_user_{id}@deleted.faithlog.local`, changes `users.name` to `탈퇴한 사용자`, and replaces `users.password_hash` with an unusable random hash.
- The original email must become available for signup again.
- Deletion increments `users.token_version`, blacklists the current access token, removes refresh sessions, deactivates all active user FCM tokens, and changes the user's campus memberships to `INACTIVE`.
- Existing devotion, prayer, poll, comment, charge, and notification rows remain for FK and service-history integrity.

Recommended Redis keys:

- `auth:refresh:{userId}:{sessionId}`
- `auth:access:blacklist:{jti}`
- `auth:session:revoked:{userId}:{sessionId}`
- Optional reuse detection: `auth:refresh:used:{refreshJti}` or current `refreshJti` comparison within the session.

Redis TTL policy:

- Access token lifetime: 30 minutes.
- Refresh token lifetime: 14 days.
- `auth:access:blacklist:{jti}` TTL is the access token remaining lifetime plus 60 seconds.
- `auth:refresh:{userId}:{sessionId}` TTL is the refresh token expiration.
- `auth:session:revoked:{userId}:{sessionId}` TTL is the configured refresh token lifetime plus 60 seconds.
- Reuse-detection keys, when used, live until the refresh token expiration.

## Pagination And Sorting

- List APIs use common query parameters: `page`, `size`, and `sort`.
- Default `page` is 0.
- Default `size` is 20.
- Maximum `size` is 100.
- Default sorting is latest-first: `createdAt,desc`.
- Domain-specific stable ordering can override the default where needed, such as poll options sorted by `sortOrder,asc`.
- Invalid `page`, `size`, or `sort` values must return `400` and must not be silently corrected.
- Pagination and sorting parsing/validation should live in a common request validation component, not in each controller or domain-specific presentation helper.
- Allowed sort fields are part of each API contract and must be tested and documented through Spring REST Docs.

## Error Codes And Request Validation

- Error responses use `HTTP status + detailed code` as the fixed API contract.
- The response `message` is user-facing display text and may be managed separately from the stable code.
- Keep one global `ErrorCode` enum, but split codes with domain prefixes instead of relying only on broad codes such as `INVALID_REQUEST`, `NOT_FOUND`, or `FORBIDDEN`.
- Example codes:
  - `BILLING_INVALID_SORT_DIRECTION`
  - `BILLING_CHARGE_ITEM_NOT_FOUND`
  - `CAMPUS_MEMBER_NOT_FOUND`
  - `AUTH_EMAIL_ALREADY_EXISTS`
- `BusinessException` and `GlobalExceptionHandler` must preserve the detailed error code in the API response.
- Simple request DTO validation uses Bean Validation.
- Pagination/sorting parsing uses the common request validation component.
- Business rule validation belongs in policy classes such as `CampusRolePolicy`, `ChargeStatusPolicy`, and `BillingAccessPolicy`.
- REST Docs tests must cover the detailed error response contract for new or changed APIs where practical.

## Schema Migration Timing

- Issue #46 reintroduced Flyway after the MVP domain model stabilized.
- `src/main/resources/db/migration/V1__initial_schema.sql` is the clean initial schema for a new Supabase PostgreSQL database.
- Production-like profiles must use Flyway migrations with Hibernate `ddl-auto=validate`; Hibernate must not create or update production schema.
- Local development may keep Flyway disabled and use Hibernate `ddl-auto=update` for speed, but PostgreSQL migration verification must cover the clean database path.
- Existing-data Supabase migration or Flyway baseline behavior requires a separate PM-approved plan before implementation.
- Runtime environments are split by profile: `local` uses local or Docker PostgreSQL/Redis, `docker` uses Docker Compose `postgres`/`redis`, `test` must not depend on Supabase or Upstash, and `prod`/Cloud Run uses Supabase PostgreSQL plus Upstash Redis.
- Production Redis uses Spring Boot Redis host/port/password/SSL properties for Upstash. Docker and local defaults must not point to Upstash.

## API Documentation

- Swagger/springdoc is kept for simple API exploration and quick checks.
- Do not use Swagger annotation-centered documentation as the main API documentation strategy.
- Do not pollute Controllers, DTOs, or Entities with documentation-only Swagger annotations such as `@Operation`, `@Schema`, or `@ApiResponse`.
- Detailed API request/response contracts are verified and documented with Spring REST Docs tests.
- New APIs or changed APIs should add MockMvc/WebMvc/Spring REST Docs coverage where practical so tests generate snippets from the real contract.
- REST Docs generated snippets live under `build/generated-snippets`, and rendered Asciidoc output lives under `build/docs/asciidoc`.

## QA Docker Compose Isolation

- Full QA and Docker QA must not use shared default named volumes as the default execution baseline.
- Use a QA-specific Docker Compose project name, such as `faithlog-qa-84` or `faithlog-qa-84-20260622`, so compose-managed named volumes are scoped to that project.
- The approved script entry point is `scripts/qa_docker_compose_isolated.sh`.
- Example: `QA_COMPOSE_PROJECT=faithlog-qa-84 ./scripts/qa_docker_compose_isolated.sh`.
- If `QA_COMPOSE_PROJECT` is omitted, the script generates a traceable `faithlog-qa-<suffix>` name.
- QA shutdown uses only the same project name: `docker compose -p <projectName> down`.
- Default QA procedures must not delete volumes. Volume deletion is allowed only as a separate cleanup procedure after explicit user approval.
- Do not automatically run destructive Docker volume cleanup commands such as compose volume deletion, direct volume removal, or system-wide volume pruning.
- The current `docker-compose.yml` has fixed `container_name` values. The QA script therefore stops before startup if those container names already exist, rather than stopping or replacing an existing development/PM stack without approval.

## Campus Onboarding

- Campus creation and account registration are separate flows.
- Campus creation is allowed only for service roles `MANAGER` and `ADMIN`.
- When a `MANAGER` creates a campus, the creator is registered in that campus as `ACTIVE + MINISTER`.
- Campus creation must not receive `penaltyAccount`.
- Campus creation must not create `PaymentAccount`.
- Campus creation must not create default `penalty_rules`.
- Campus management authority is based on `campus_members.campus_role`, not `users.role = MANAGER`.
- `ADMIN` can access all campus details.
- Campus creation responses include `inviteCode`.
- `ADMIN`, `MINISTER`, `ELDER`, and `CAMPUS_LEADER` can view invite codes.
- Normal `MEMBER` campus detail responses must not expose `inviteCode`.
- `GET /api/v1/campuses/me` returns only the current user's `ACTIVE` memberships.
- Campus member delete uses `DELETE /api/v1/campuses/{campusId}/members/{membershipId}`.
- Campus member delete soft-deletes membership by setting `campus_members.status = INACTIVE`.
- Campus member management is allowed for service-level `ADMIN` and active campus members whose campus role is `MINISTER`, `ELDER`, or `CAMPUS_LEADER`.
- Normal campus `MEMBER` users cannot manage or delete campus members.
- If an inactive/deleted member joins again by invite code, reactivate the existing membership as `ACTIVE + MEMBER`.
- Devotion penalty charge generation should return a clear error if the campus has no active `PENALTY` account.

## Role Management

- Service-level roles live on `users.role`:
  - `USER`
  - `MANAGER`
  - `ADMIN`
- Campus-level roles live on `campus_members.campus_role`:
  - `MINISTER`
  - `ELDER`
  - `CAMPUS_LEADER`
  - `MEMBER`
- `MANAGER` is a service-level role that can create campuses. It is not a campus-management role by itself.
- Campus management permission must be derived from the user's membership and `campus_members.campus_role`.
- Campus member management excludes only normal campus `MEMBER` users; `MINISTER`, `ELDER`, `CAMPUS_LEADER`, and service-level `ADMIN` can manage campus members.
- Issue #30 role changes use `PATCH /api/v1/admin/campuses/{campusId}/members/{campusMemberId}/campus-role`; `campusMemberId` is `campus_members.id`.
- Campus role hierarchy for role changes is `MINISTER > ELDER > CAMPUS_LEADER > MEMBER`.
- Issue #30 uses same-level assignment. A campus manager can assign roles up to the manager's own campus role level, but cannot change or assign roles above that level. Any earlier "below only" interpretation is superseded by this same-level assignment decision.
- `MINISTER` can change another user to `MINISTER`, `ELDER`, `CAMPUS_LEADER`, or `MEMBER`.
- `ELDER` can change another user to `ELDER`, `CAMPUS_LEADER`, or `MEMBER`, but cannot change an existing `MINISTER` or assign `MINISTER`.
- `CAMPUS_LEADER` can change another user to `CAMPUS_LEADER` or `MEMBER`, but cannot change an existing `MINISTER`/`ELDER` or assign `MINISTER`/`ELDER`.
- `MEMBER` cannot change roles.
- Service-level `ADMIN` can change any campus member role in any campus.
- Service-level `MANAGER` alone does not grant campus role change permission.
- Issue #30 must not block downgrading the last campus management role holder to `MEMBER`.
- Service-level admin user-role management APIs are not part of issue #29 and must be handled in a separate admin role-management issue.
- Last active service-level `ADMIN` protection is final. If a role change would demote the only active service-level `ADMIN` to `USER` or `MANAGER`, the API must fail with `409 ADMIN_LAST_ADMIN_DEMOTION_FORBIDDEN`.
- Last service-level `ADMIN` protection counts only users where `users.role = ADMIN` and `users.is_active = true`.

## Campus Duty Assignment

- Coffee duty is not a `CampusRole`.
- Coffee duty uses `CampusDutyAssignment` with `DutyType.COFFEE`.
- A campus has at most one active `DutyType.COFFEE` assignee.
- Issue #30 assigns or replaces the active coffee assignee with `PUT /api/v1/admin/campuses/{campusId}/duty-assignments/coffee`.
- Issue #30 revokes the active coffee assignee with `DELETE /api/v1/admin/campuses/{campusId}/duty-assignments/coffee/{assignmentId}`.
- Active campus members can check their own coffee duty status with `GET /api/v1/campuses/{campusId}/duty-assignments/me`.
- The my-duty response is `userId`, `campusId`, `dutyType=COFFEE`, and `isActive`.
- A non-duty ACTIVE member receives `200 OK` with `isActive=false`; non-members and inactive members are forbidden.

## FCM And Notifications

User-owned FCM token APIs:

- `POST /api/v1/users/me/fcm-tokens`
- `DELETE /api/v1/users/me/fcm-tokens/{tokenId}`

Admin notification APIs:

- `POST /api/v1/admin/campuses/{campusId}/notifications`
- `GET /api/v1/admin/campuses/{campusId}/notification-logs`

Do not use:

- `/api/v1/notifications/fcm-tokens`
- `/notifications/logs`

`notification-logs` is the final spelling.

Notification failure policy:

- Notification requests create per-target `notification_logs` rows with the same server-generated `request_id` and `PENDING` status before background delivery starts.
- Admin notification send APIs return `202 Accepted` without waiting for Firebase delivery to complete.
- Success, failure, and skip results are all saved to `notification_logs`.
- Transient notification send failures retry immediately per token up to 3 times with `1s -> 5s -> 30s` intervals.
- Transient failures include network errors, temporary Firebase failures, rate limits, and timeouts.
- Permanent failures do not retry. `UNREGISTERED`, token-not-registered, and payload-valid invalid-token responses deactivate the affected `user_fcm_tokens` row.
- `INVALID_ARGUMENT` deactivates a token only when the payload is known to be valid.
- Other send failures are logged but do not deactivate the token unless the provider error clearly marks the token unusable.
- Reprocessing old `PENDING` logs after server restart is not part of issue #40 and belongs to issue #24 Scheduler/Batch or a follow-up issue.

FCM token lifecycle policy:

- FCM tokens are issued or returned by the frontend Firebase SDK.
- The backend does not issue FCM tokens.
- The frontend sends the current token to the backend on app entry/login and whenever Firebase reports a token change.
- `POST /api/v1/users/me/fcm-tokens` must be idempotent and behave as an upsert.
- `user_fcm_tokens` is the source of truth for FCM tokens. Redis is not the source of truth.
- The request must include `clientInstanceId`, a frontend-generated app-installation identifier, plus `token`, `deviceType`, and optionally `appVersion`.
- If the same active `userId + clientInstanceId + token` already exists, return the same token row and update `lastSeenAt`, `lastRefreshedAt`, device metadata, and `appVersion`.
- Active `userId + clientInstanceId` must be unique. If the same user/client instance registers a different token, deactivate the previous active row before saving the new active row.
- Active `token` must be unique. If the same token is registered under another user or another client instance, deactivate the previous active ownership row before saving the current user's active row so notifications do not leak across accounts on shared devices.
- Inactive FCM token rows may remain as history. Do not enforce global uniqueness on inactive token history.
- Logout should deactivate the current device token when the client provides the token or `clientInstanceId`.
- Notification sends must target only `isActive = true` tokens that are not stale.
- A token is stale when `lastSeenAt` or `lastRefreshedAt` is older than 90 days. Stale tokens must be excluded from sending and may be deactivated by a cleanup job.
- `UNREGISTERED` or token-not-registered provider errors deactivate the token immediately.
- `INVALID_ARGUMENT` deactivates the token only when the payload is known to be valid.

Redis notification deduplication and lock policy:

- FCM token source of truth remains `user_fcm_tokens`; Redis must not become an FCM token store.
- Notification history source of truth remains `notification_logs`; Redis must not replace `notification_logs`.
- Redis notification deduplication and locks are infrastructure for duplicate execution prevention and concurrent execution prevention only.
- Automatic notifications use the business dedup key `notification:dedup:{notificationType}:{campusId}:{scopeId}:{targetUserId}:{businessDate}`.
- Daily automatic notification dedup TTL is 25 hours.
- Weekly automatic notification dedup TTL is 8 days.
- Notification execution locks use `notification:lock:{jobName}:{campusId}:{scopeId}`.
- Short notification job lock TTL defaults to 10 minutes.
- Long batch notification jobs may pass a custom TTL based on expected runtime plus buffer.
- Automatic/scheduled notifications fail closed when Redis is unavailable and must not send without dedup protection.
- Manual admin notifications are intentional sends and must not be blocked by the automatic business dedup key.
- Manual admin notification requests may use execution locks, and Redis unavailability must fail the API clearly rather than silently sending.
- Application services use notification deduplication/lock ports. Redis integration stays under `notification/infrastructure/redis`.

## Poll Comments

Poll comments are included in MVP.

- `poll_comments` table is required.
- Comments are allowed only for ACTIVE members of the campus.
- Comment author is stored as `user_id`.
- Anonymous polls do not make comments anonymous.
- Anonymous comments are Post-MVP.
- Comment update/delete is allowed for the author or campus admins: `MINISTER`, `ELDER`, `CAMPUS_LEADER`, `ADMIN`.
- Delete is soft delete.
- Deleted comments must hide content or respond with `삭제된 댓글입니다.`
- Comment creation is allowed only for `OPEN` polls.
- `CLOSED` polls allow comment read only.

Final comment APIs:

- `GET /api/v1/campuses/{campusId}/polls/{pollId}/comments`
- `POST /api/v1/campuses/{campusId}/polls/{pollId}/comments`
- `PATCH /api/v1/campuses/{campusId}/polls/{pollId}/comments/{commentId}`
- `DELETE /api/v1/campuses/{campusId}/polls/{pollId}/comments/{commentId}`

## Devotion

Final devotion APIs:

- `PUT /api/v1/campuses/{campusId}/devotions/me/days/{recordDate}`
- `PUT /api/v1/campuses/{campusId}/devotions/me/weeks/{weekStartDate}`
- `GET /api/v1/campuses/{campusId}/devotions/me/weeks/{weekStartDate}`
- `GET /api/v1/campuses/{campusId}/devotions/me/monthly-summary?year={year}&month={month}`
- `GET /api/v1/admin/campuses/{campusId}/devotions/missing?weekStartDate={weekStartDate}`

Rules:

- `weekStartDate` must be Monday.
- Monthly devotion summary follows Notion `10.5 내 월간 경건생활 통계 조회`.
- Monthly devotion summary validates the authenticated user is an ACTIVE campus member and returns only the authenticated user's data.
- Monthly devotion summary response includes campus/user identity, `year`, `month`, monthly `devotion` totals, and `weeklyRecords[]`.
- Monthly devotion summary calculates monthly totals from `devotion_daily_checks.record_date` between the selected month's first and last day and must not add a new persistence table.
- Monthly devotion summary groups the selected month's daily rows by week for `weeklyRecords[]`; a week crossing a month boundary can be returned with partial counts for the selected month's dates only.
- `SATURDAY_LATE` minutes are included in the month containing the Saturday date for that weekly record.
- A daily check creates or updates the `devotion_daily_checks` row for `recordDate` and creates the matching weekly row when missing.
- A daily check must not update `submitted_at`, calculate penalties, or create/update `PENALTY` charges.
- Weekly save/submit creates or updates Monday-Sunday `devotion_daily_checks`.
- Weekly save/submit request uses the `dailyChecks` field.
- Missing dates in a weekly submission are filled with false defaults.
- `weekly_devotion_records` is used for weekly summary and calculations.
- Devotion submission and admin missing-user checks are based on `weekly_devotion_records.submitted_at`, not on daily row existence.
- `submit = false` weekly saves are allowed only before final submission and must not create or update `PENALTY` charges.
- Weekly devotion submission is one-time. If `weekly_devotion_records.submitted_at` already exists for the same campus/user/week, another `submit = true` request must fail.
- The first `submit = true` weekly submission calculates penalties and creates one combined `PENALTY` charge through issue #33 when the calculated total is greater than 0 KRW.
- If the calculated penalty total is 0 KRW, do not create a `charge_items` row and do not require an active `PENALTY` account.
- If the calculated penalty total is greater than 0 KRW and there is no active `PENALTY` account, issue #33 must fail the whole `submit = true` request with the user-facing message `관리자에게 문의하세요` and must not create a `charge_items` row.

Penalty table:

- Weekly standard: 5 days.
- Quiet time: 500 KRW per missing day.
- Prayer: 500 KRW per missing day.
- Bible reading: 300 KRW per missing day.
- Saturday lateness: 1,000 KRW base plus 100 KRW per late minute.
- Saturday lateness is 0 KRW when `saturdayLateMinutes = 0`; when `saturdayLateMinutes > 0`, calculate `1,000 + saturdayLateMinutes * 100`.
- `saturdayLateMinutes` accepts only 0 through 1,440 inclusive. Out-of-range input uses `DEVOTION_INVALID_SATURDAY_LATE_MINUTES` with HTTP 400.
- Fine multiplication and item-total addition use `long` exact arithmetic. Arithmetic overflow or a total outside the persisted PostgreSQL `INTEGER` range uses `DEVOTION_FINE_AMOUNT_OUT_OF_RANGE` with HTTP 400.
- A weekly devotion submission creates one combined `PENALTY` charge for the weekly record, not separate charges per penalty category.

Penalty rule APIs for issue #32:

- `GET /api/v1/campuses/{campusId}/penalty-rules`
- `POST /api/v1/admin/campuses/{campusId}/penalty-rules`
- `PATCH /api/v1/admin/penalty-rules/{ruleId}`

Issue #32 implements penalty rule management and fine calculation only. It must not create or update `charge_items`; weekly devotion submission to `PENALTY` charge integration belongs to issue #33.

Penalty calculation integration note:

- `DevotionFineCalculator` is a calculation-only Domain Service and assumes validated weekly summary input.
- Issue #31 rejects negative `saturdayLateMinutes` values at the weekly save/submit request boundary.
- Issue #33 must keep or add tests proving negative `saturdayLateMinutes` cannot reach the calculator when weekly devotion submission is wired to `PENALTY` charge generation.
- If needed during issue #33, add an application-layer guard immediately before calculator invocation without changing the issue #32 calculator contract.
- Because weekly devotion submission is one-time, issue #33 must not implement same-week charge recalculation by resubmitting the same weekly record.
- Existing `PENALTY` charges for the same weekly record must not be overwritten through the normal weekly devotion submission flow.

Penalty rule replacement and validation:

- Creating a new ACTIVE penalty rule for the same campus and `rule_type` automatically deactivates the previous ACTIVE rule and leaves only the new rule active.
- `QUIET_TIME`, `PRAYER`, and `BIBLE_READING` must use `MISSING_COUNT`.
- `SATURDAY_LATE` must use `LATE_MINUTE`.
- Invalid `rule_type` and `calculation_type` pairings must return `400`.

Do not use:

- `POST /api/v1/campuses/{campusId}/devotions/weeks`
- `PATCH /api/v1/devotions/weeks/{recordId}/days/{date}`
- `GET /api/v1/campuses/{campusId}/devotions/fines?weekStartDate=` unless the user explicitly approves a separate preview API.

## Coffee Charge Automation

Issue #39 is P0.

- Coffee poll response saves only the current response and selected `poll_response_options`; it must not create `COFFEE` `charge_items` at response time.
- Closed coffee poll settlement creates or updates `COFFEE` `charge_items` from the final saved responses.
- Issue #39 implements the closed coffee poll settlement application service logic. Automatic scheduler/batch invocation remains Issue #24 scope.
- Settlement target polls must match `poll_type = COFFEE`, `charge_generation_type = OPTION_PRICE`, `payment_category = COFFEE`, and `status = CLOSED`.
- `sourceType = POLL_RESPONSE`
- `sourceId = poll_responses.id`
- `paymentCategory = COFFEE`
- `amount` uses the selected `poll_options.price_amount` snapshot.
- `title` uses the selected `poll_options.content` snapshot.
- `dueDate` is `null`.
- `paymentAccountId` and account snapshot must be saved.
- Settlement must be idempotent. Existing `UNPAID` `COFFEE` charges for the same source are updated or kept, and terminal `PAID`, `WAIVED`, or `CANCELED` charges are not overwritten.
- One poll settlement must be all-or-nothing in a single transaction.
- Duplicate charge prevention must be covered by a unique index test.
- Poll must not directly reference Billing Entity. Keep the flow in the application layer.
- COFFEE poll setup no longer requires the requester to be the active `DutyType.COFFEE` assignee when the requester is a campus manager. It does require a selected active same-campus `COFFEE` account owned by the requester.
- The account used for settlement is `polls.payment_account_id`; it must belong to the same campus, have `account_type = COFFEE`, be active at poll/template creation time, and be owned by the requester who creates the paid COFFEE poll/template.
- Issue #37 provides the coffee brand/menu catalog used by coffee poll templates.
- MVP coffee ordering is limited to Compose Coffee.
- Coffee menu names and prices must not be frontend-only data or Java enum constants because they affect billing.
- Issue #37 must seed one active Compose Coffee brand and all current Compose Coffee menu items into backend catalog data.
- Compose Coffee seed source policy is official-first. Prefer official Compose Coffee menu boards, official app data, official menu images, or another official Compose Coffee source.
- If official verification is blocked or impossible, a latest menu/price source explicitly approved by the user may be used as the seed baseline.
- The actual Issue #37 implementation uses the user-approved 2026 Compose Coffee menu/price source recorded in `docs/decision-log.md`.
- New campus creation must not automatically create a default COFFEE poll template or recurring coffee poll. Existing auto-created COFFEE templates are not deleted or deactivated by Issue #112.
- Additional coffee template options are selected from the backend menu catalog and copied into `poll_template_options`.
- `poll_template_options` and `poll_options` keep copied `composeMenuCode`, display name, and `priceAmount` snapshots so later catalog price changes do not mutate existing templates, polls, or charges.
- Every option in direct `pollType=COFFEE` Poll creation and persisted `pollType=COFFEE` PollTemplate creation/update requires an active backend `coffee_menu_catalog` `menuId`.
- COFFEE option snapshots always use `content = catalog.name`, `composeMenuCode = catalog.menuCode`, and `priceAmount = catalog.priceAmount`. Client `content` and `priceAmount` fields remain accepted for request compatibility but are never authoritative for COFFEE snapshots.
- Missing COFFEE option `menuId` fails with `400 POLL_COFFEE_OPTION_MENU_REQUIRED`; missing and inactive menu rows reuse `POLL_MENU_NOT_FOUND` and `POLL_MENU_INACTIVE`.
- Non-COFFEE Poll/template custom content and zero-price behavior remains separate from COFFEE catalog validation.
- Brand/menu admin CRUD and additional brand onboarding are outside Issue #37 unless the user approves a separate issue.

## Payment Account And Charge Foundation

Issue #34 is P0.

- Campus creation must not create `PaymentAccount` or default `penalty_rules`.
- Admins manage campus payment accounts through:
  - `GET /api/v1/campuses/{campusId}/payment-accounts`
  - `POST /api/v1/admin/campuses/{campusId}/payment-accounts`
  - `PATCH /api/v1/admin/payment-accounts/{accountId}/deactivate`
  - `GET /api/v1/admin/campuses/{campusId}/payment-accounts`
  - `GET /api/v1/admin/campuses/{campusId}/charges`
  - `GET /api/v1/admin/campuses/{campusId}/charges/my-accounts`
- All active campus members can list payment accounts for their campus.
- Campus admin roles and service-level `ADMIN` can create or deactivate `PENALTY` payment accounts.
- Campus admin roles and active COFFEE duty assignees can create their own `COFFEE` payment accounts. Normal members without active COFFEE duty cannot create `COFFEE` accounts.
- PENALTY account `ownerUserId` is registration/management metadata. If `ownerUserId` is null when creating a PENALTY account, store the requester user ID; if present, store the supplied value.
- COFFEE account creation is requester-owned. If `ownerUserId` is null, the owner is the requester. If `ownerUserId` is present and different from the requester, reject the request with `403 BILLING_PAYMENT_ACCOUNT_OWNER_FORBIDDEN`.
- Non-service-admin users can deactivate only their own `COFFEE` payment account. Active COFFEE duty alone must not create or deactivate `PENALTY` accounts.
- `PENALTY` payment account creation/deactivation keeps the existing campus admin or service admin permission.
- `GET /api/v1/admin/campuses/{campusId}/payment-accounts` returns manager-facing metadata including `ownerUserId`, `isActive`, `createdAt`, and `deactivatedAt`. Campus managers and service-level `ADMIN` can see all campus accounts. Active COFFEE duty users can see only active COFFEE accounts they own.
- `GET /api/v1/admin/campuses/{campusId}/charges` supports optional `paymentAccountId`; when present, `summary + members[]` must include only charge items linked to that payment account and must compose with existing filters. Campus managers and COFFEE duty users can filter COFFEE accounts only when the account is their own; service-level `ADMIN` can access all.
- `GET /api/v1/admin/campuses/{campusId}/charges/my-accounts` includes active PENALTY accounts for campus managers and service-level `ADMIN` regardless of `ownerUserId`, including legacy active PENALTY accounts whose owner is null. COFFEE remains limited to active COFFEE accounts owned by the current user. Active COFFEE duty users are limited to owned active COFFEE accounts and cannot see PENALTY data.
- PENALTY account and charge views require service-level `ADMIN` or a campus manager role (`MINISTER`, `ELDER`, `CAMPUS_LEADER`). Active COFFEE duty alone must not expose PENALTY account or charge data.
- Account numbers are fully visible in account list responses because members need them for bank transfer payment. Do not expose unnecessary admin-only metadata in member-facing responses.
- A campus can have only one active `PENALTY` payment account per campus and account type.
- A campus can have one active `COFFEE` payment account per `ownerUserId`; active COFFEE uniqueness is `campusId + accountType + ownerUserId`.
- Creating a new active `PENALTY` account automatically deactivates the previous active account for the same campus and `account_type`.
- Creating a new active `COFFEE` account automatically deactivates only the requester's previous active COFFEE account and must not deactivate another user's COFFEE account.
- Payment accounts can be deactivated even if unpaid charge items are linked to them.
- When a new active `PENALTY` account replaces the previous active account, existing `UNPAID` PENALTY charge items for that campus must be re-linked to the new active account and their account snapshots updated. Already terminal `PAID`, `WAIVED`, and `CANCELED` charge items keep their historical snapshots.
- Creating a new active `COFFEE` account must not re-link existing `UNPAID` COFFEE charge items. COFFEE charges remain linked to the `polls.payment_account_id` selected when the poll was created.
- `PaymentCategory` values are `PENALTY` and `COFFEE`.
- `ChargeSourceType` values are `DEVOTION_RECORD` and `POLL_RESPONSE`.
- `ChargeStatus` values are `UNPAID`, `PAID`, `WAIVED`, and `CANCELED`.
- User payment completion is the only path from `UNPAID` to `PAID`.
- Administrators must not mark a charge as `PAID`.
- Administrators may change a charge to `WAIVED` or `CANCELED`.
- Administrators may revert an incorrectly handled `PAID`, `WAIVED`, or `CANCELED` charge back to `UNPAID`.
- When an administrator reverts `PAID` to `UNPAID`, clear `paidAt`.
- Do not store administrator status-change reasons in Issue #35.
- Charge creation must save `payment_account_id`, `bank_name_snapshot`, `account_number_snapshot`, and `account_holder_snapshot`.
- Billing domain creation and unpaid-charge updates require `amount > 0`. Zero and negative charge rows are invalid for both `PENALTY` and `COFFEE`.
- Flyway V7 adds `ck_charge_items_amount_positive` and validates it during migration. If any legacy `amount <= 0` row exists, the migration must fail closed and roll back; migration must not edit or delete historical rows automatically.
- Do not create incomplete `charge_items` rows when a required account is missing.
- If the active `PENALTY` account is missing during positive-amount penalty charge creation, fail with the user-facing message `관리자에게 문의하세요`.
- Manual admin charge creation is not part of the MVP.
- Issue #34 implements the billing foundation service only. Devotion and poll flows connect to it in Issue #33 and Issue #39.
- Detailed API contracts must be documented with Spring REST Docs tests. Swagger/springdoc remains only for simple API exploration.

## Poll Response

- Poll response requests must use `optionIds`.
- Selected options must be stored in `poll_response_options`.
- Do not implement request field `optionId` or `poll_responses.option_id` from older API drafts.
- Administrator direct poll creation must set a poll to `OPEN` immediately when the creation-time current instant satisfies `startsAt <= now < endsAt`.
- Polls that have not started yet remain `SCHEDULED`, and Scheduler/Batch keeps its existing automatic creation, close, and correction role.
- Coffee brand lookup uses `GET /api/v1/coffee-brands`.
- Coffee menu catalog lookup uses `GET /api/v1/coffee-brands/{brandId}/menus`.
- Compose Coffee menu catalog seed data should come from official Compose Coffee sources first. If official verification is not possible, a latest menu/price source explicitly approved by the user may be used; Issue #37 used the user-approved 2026 Compose Coffee menu/price source.
- Poll results are visible to all active campus members.
- Poll result lookup is a single poll-level API: `GET /api/v1/campuses/{campusId}/polls/{pollId}/results`.
- Active COFFEE duty assignees can create and manage only `pollType=COFFEE` polls in their own campus.
- Coffee-external poll types such as `CUSTOM`, `WED_SERVICE`, and `SATURDAY_LEADER` keep the existing campus admin or service admin permission.
- COFFEE poll and COFFEE poll template creation/update allow campus managers or active `DutyType.COFFEE` assignees.
- Selected COFFEE `paymentAccountId` values are required and must point to an active same-campus COFFEE account owned by the requester. Null, inactive, other-campus, PENALTY, and another user's COFFEE account must fail with a clear billing account error.
- New campus creation must not automatically provision default COFFEE poll templates or recurring coffee polls. Existing auto-created templates are retained unless a separate cleanup issue is approved.
- When a direct `pollType=COFFEE` poll omits `allowUserOptionAdd`, the backend defaults it to true regardless of whether the requester is the active COFFEE duty assignee. Explicit `allowUserOptionAdd=false` is preserved. Other direct poll creation defaults omitted `allowUserOptionAdd` to false.
- The user-option-add API keeps `{ "content": "새 항목" }` for non-COFFEE polls and requires `menuId` without client `content` for COFFEE polls. COFFEE user-added options snapshot the active catalog name, menu code, and price.
- Do not create option-level poll result endpoints for MVP.
- For non-anonymous polls, result responses may expose who voted for each option.
- For anonymous polls, result responses must expose aggregate counts only and must not expose voter user IDs, names, emails, or option-level respondent identity to any user.
- `poll_responses.user_id` is still stored for duplicate response prevention, response editing, missing-member calculation, and internal auditing, but anonymous result APIs must not reveal it.
- User-facing past poll, poll detail, and poll result visibility is limited to 3 days after `polls.ends_at`.
- Admin-facing past poll, poll detail, and poll result visibility is limited to 7 days after `polls.ends_at`.
- After the visibility window expires, expired polls must be excluded from lists and direct lookup must not expose poll/result data.

## Prayer Requests

- Prayer requests are organized by campus, active prayer season, prayer group, and weekly prayer board.
- A prayer season may start without a fixed end date and is manually closed when group composition changes.
- All campus members can view the weekly prayer requests for all groups on one grouped page.
- The prayer request input experience may show all group members on one page.
- Persistence must be per member submission, not one large page blob.
- Member submission content is nullable so a no-meeting or intentionally empty entry can be saved.
- Each member submission must use version-based optimistic locking.
- Save requests must include the version read by the client.
- If the submitted version does not match the current stored version, return a conflict instead of overwriting the newer content.
- KakaoTalk sharing or automatic KakaoTalk posting is not MVP scope.

## Issue Status

- GitHub Project Board Status is the source of truth.
- Do not keep manual status lines such as `칸반 상태: To Do` in issue bodies.

## MVP Exclusions

Keep these out of MVP scope:

- Lunch polls
- Lunch group orders
- Lunch amount splitting
- Lunch manager
- Lunch account
- Admin payment approval/rejection
- Deposit proof photo
- Payment API integration
- KakaoTalk automatic integration
- QR check-in
