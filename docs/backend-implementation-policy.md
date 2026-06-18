# FaithLog Backend Implementation Policy

This document records the current backend implementation source of truth.

## Auth

- Refresh Token is stored in Redis, not in the database.
- The old Notion ERD `refresh_tokens` table is superseded by the Redis allowlist decision and must not be implemented as the MVP refresh-token source of truth.
- Refresh Token uses a Redis allowlist.
- Access Token remains stateless JWT, but logout uses Redis blacklist/denylist.
- Refresh Token Rotation is required.
- Refresh success must issue a new Refresh Token and immediately revoke the previous one.
- Reuse of an old Refresh Token must fail and revoke at least the current session.
- Redis must not store raw tokens. Store a hash or token identifier.
- Access Token must include `jti`, `userId`, `role`, and `sessionId`.
- Refresh Token must include `userId`, `sessionId`, and `refreshJti`.
- Refresh rotation keeps the same `sessionId` and replaces the refresh token identifier.
- `POST /api/v1/auth/refresh` receives `refreshToken` in the JSON request body and returns the same token response shape as login.
- `POST /api/v1/auth/logout` requires `Authorization: Bearer {accessToken}` and accepts optional JSON body fields `refreshToken`, `clientInstanceId`, and `fcmToken`.
- Logout must succeed even when `clientInstanceId` and `fcmToken` are omitted.
- Auth must not directly implement or manipulate Notification entities. For issue #28, use an application port for current-device FCM deactivation; issue #40 owns the actual `user_fcm_tokens` persistence implementation.

Final auth APIs:

- `POST /api/v1/auth/refresh`
- `POST /api/v1/auth/logout`

Do not use:

- `POST /api/v1/auth/reissue`

Recommended Redis keys:

- `auth:refresh:{userId}:{sessionId}`
- `auth:access:blacklist:{jti}`
- Optional reuse detection: `auth:refresh:used:{refreshJti}` or current `refreshJti` comparison within the session.

Redis TTL policy:

- Access token lifetime: 30 minutes.
- Refresh token lifetime: 14 days.
- `auth:access:blacklist:{jti}` TTL is the access token remaining lifetime plus 60 seconds.
- `auth:refresh:{userId}:{sessionId}` TTL is the refresh token expiration.
- Reuse-detection keys, when used, live until the refresh token expiration.

## Pagination And Sorting

- List APIs use common query parameters: `page`, `size`, and `sort`.
- Default `page` is 0.
- Default `size` is 20.
- Maximum `size` is 100.
- Default sorting is latest-first: `createdAt,desc`.
- Domain-specific stable ordering can override the default where needed, such as poll options sorted by `sortOrder,asc`.

## Schema Migration Timing

- Flyway is deferred until the main feature development work is complete.
- Flyway runtime dependencies, configuration, and migration files are not active during early feature development.
- Feature issues may define approved schema requirements and write tests around persistence behavior.
- Final Flyway migration scripts should be consolidated after the domain model stabilizes near the end of development.
- Do not block early feature implementation on final Flyway migration authoring unless the user later approves a different migration policy.

## API Documentation

- Swagger/springdoc is kept for simple API exploration and quick checks.
- Do not use Swagger annotation-centered documentation as the main API documentation strategy.
- Do not pollute Controllers, DTOs, or Entities with documentation-only Swagger annotations such as `@Operation`, `@Schema`, or `@ApiResponse`.
- Detailed API request/response contracts are verified and documented with Spring REST Docs tests.
- New APIs or changed APIs should add MockMvc/WebMvc/Spring REST Docs coverage where practical so tests generate snippets from the real contract.
- REST Docs generated snippets live under `build/generated-snippets`, and rendered Asciidoc output lives under `build/docs/asciidoc`.

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
- `MINISTER` can change `ELDER`, `CAMPUS_LEADER`, and `MEMBER`; `ELDER` can change `CAMPUS_LEADER` and `MEMBER`; `CAMPUS_LEADER` can change `MEMBER`; `MEMBER` cannot change roles.
- Service-level `ADMIN` can change any campus member role in any campus.
- Issue #30 must not block downgrading the last campus management role holder to `MEMBER`.
- Service-level admin user-role management APIs are not part of issue #29 and must be handled in a separate admin role-management issue.
- Last `ADMIN` protection remains a pending policy question until the user approves its exact behavior.

## Campus Duty Assignment

- Coffee duty is not a `CampusRole`.
- Coffee duty uses `CampusDutyAssignment` with `DutyType.COFFEE`.
- A campus has at most one active `DutyType.COFFEE` assignee.
- Issue #30 assigns or replaces the active coffee assignee with `PUT /api/v1/admin/campuses/{campusId}/duty-assignments/coffee`.
- Issue #30 revokes the active coffee assignee with `DELETE /api/v1/admin/campuses/{campusId}/duty-assignments/coffee/{assignmentId}`.

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

- MVP does not automatically retry notification sends.
- Success, failure, and skip results are all saved to `notification_logs`.
- Invalid or unregistered FCM token errors deactivate the affected `user_fcm_tokens` row.
- Other send failures are logged but do not deactivate the token unless the provider error clearly marks the token unusable.

FCM token lifecycle policy:

- FCM tokens are issued or returned by the frontend Firebase SDK.
- The backend does not issue FCM tokens.
- The frontend sends the current token to the backend on app entry/login and whenever Firebase reports a token change.
- `POST /api/v1/users/me/fcm-tokens` must be idempotent and behave as an upsert.
- `user_fcm_tokens` is the source of truth for FCM tokens. Redis is not the source of truth.
- The request must include `clientInstanceId`, a frontend-generated app-installation identifier, plus `token`, `deviceType`, and optionally `appVersion`.
- If the same `userId + token` already exists, reactivate it if needed and update `lastSeenAt`, `lastRefreshedAt`, and `appVersion`.
- If the same `userId + clientInstanceId` has a different active token, deactivate the previous token and save the new token.
- If the same token is registered under another user, deactivate the previous user's token ownership before associating it with the current user so notifications do not leak across accounts on shared devices.
- Logout should deactivate the current device token when the client provides the token or `clientInstanceId`.
- Notification sends must target only `isActive = true` tokens that are not stale.
- A token is stale when `lastSeenAt` or `lastRefreshedAt` is older than 90 days. Stale tokens must be excluded from sending and may be deactivated by a cleanup job.
- `UNREGISTERED` or token-not-registered provider errors deactivate the token immediately.
- `INVALID_ARGUMENT` deactivates the token only when the payload is known to be valid.

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

- `PUT /api/v1/campuses/{campusId}/devotions/me/weeks/{weekStartDate}`
- `GET /api/v1/campuses/{campusId}/devotions/me/weeks/{weekStartDate}`
- `GET /api/v1/admin/campuses/{campusId}/devotions/missing?weekStartDate={weekStartDate}`

Rules:

- `weekStartDate` must be Monday.
- Weekly save/submit creates or updates Monday-Sunday `devotion_daily_checks`.
- `weekly_devotion_records` is used for weekly summary and calculations.
- The old single-day devotion check API is not the MVP implementation path.
- Penalty calculation and `PENALTY` charge creation follows issue #33.

Penalty table:

- Weekly standard: 5 days.
- Quiet time: 500 KRW per missing day.
- Prayer: 500 KRW per missing day.
- Bible reading: 300 KRW per missing day.
- Saturday lateness: 1,000 KRW base plus 100 KRW per late minute.
- Saturday lateness is 0 KRW when `saturdayLateMinutes = 0`; when `saturdayLateMinutes > 0`, calculate `1,000 + saturdayLateMinutes * 100`.
- A weekly devotion submission creates one combined `PENALTY` charge for the weekly record, not separate charges per penalty category.

Do not use:

- `POST /api/v1/campuses/{campusId}/devotions/weeks`
- `PATCH /api/v1/devotions/weeks/{recordId}/days/{date}`

## Coffee Charge Automation

Issue #39 is P0.

- Coffee poll response must automatically create or update `COFFEE` `charge_items`.
- `sourceType = POLL_RESPONSE`
- `sourceId = poll_responses.id`
- `paymentCategory = COFFEE`
- `paymentAccountId` and account snapshot must be saved.
- Duplicate charge prevention must be covered by a unique index test.
- Poll must not directly reference Billing Entity. Keep the flow in the application layer.
- If coffee poll setup or charge generation requires a coffee duty assignee and no active `CampusDutyAssignment` with `DutyType.COFFEE` exists for the campus, fail clearly with the user-facing message `관리자에게 문의하세요`.

## Poll Response

- Poll response requests must use `optionIds`.
- Selected options must be stored in `poll_response_options`.
- Do not implement request field `optionId` or `poll_responses.option_id` from older API drafts.

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
