# FaithLog Backend Implementation Policy

This document records the current backend implementation source of truth.

## Auth

- Refresh Token is stored in Redis, not in the database.
- Refresh Token uses a Redis allowlist.
- Access Token remains stateless JWT, but logout uses Redis blacklist/denylist.
- Refresh Token Rotation is required.
- Refresh success must issue a new Refresh Token and immediately revoke the previous one.
- Reuse of an old Refresh Token must fail and revoke at least the current session.
- Redis must not store raw tokens. Store a hash or token identifier.
- Access Token must include `jti`, `userId`, `role`, and `sessionId`.
- Refresh Token must include `userId`, `sessionId`, and `refreshJti`.

Final auth APIs:

- `POST /api/v1/auth/refresh`
- `POST /api/v1/auth/logout`

Do not use:

- `POST /api/v1/auth/reissue`

Recommended Redis keys:

- `auth:refresh:{userId}:{sessionId}`
- `auth:access:blacklist:{jti}`
- Optional reuse detection: `auth:refresh:used:{refreshJti}` or current `refreshJti` comparison within the session.

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
- Penalty calculation and `PENALTY` charge creation follows issue #33.

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
