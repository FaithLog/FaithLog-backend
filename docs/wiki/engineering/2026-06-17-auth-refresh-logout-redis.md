---
project: FaithLog
type: engineering-wiki
date: 2026-06-17
generated_by: daily-resume-monitor
---

# 2026-06-17 Auth Refresh Logout Redis

## Feature

- Issue scope observed from code and commits: `#28 auth refresh logout redis`

## Problem Being Solved

- Login alone was already implemented, but refresh rotation, logout invalidation, and their contract verification needed a concrete backend path.

## Implemented Approach

- `AuthService.refresh(...)` parses the refresh token, reads `userId`, `sessionId`, and `refreshJti`, and rejects the request if the stored allowlist value does not match.
- When a reused or mismatched refresh token is detected, the current session entry is deleted before returning `UNAUTHORIZED`.
- `AuthService.logout(...)` blacklists the current access token `jti` for the remaining token lifetime plus 60 seconds, deletes the refresh allowlist entry for the session, and optionally calls the current-device FCM deactivation port.
- Runtime Redis adapters were added for both token stores:
  - `RedisRefreshTokenStore`
  - `RedisAccessTokenBlacklistStore`
- The #28 no-op FCM adapter is registered only when no real `CurrentDeviceFcmTokenDeactivationPort` bean exists, so #40 can provide the real adapter without colliding with the placeholder.
- Controller contracts now expose:
  - `POST /api/v1/auth/refresh`
  - `POST /api/v1/auth/logout`

## Evidence-Backed Rationale

- The repo decision log already records that refresh/logout contracts and Redis token lifecycle are approved user decisions.
- The implementation keeps FCM deactivation behind `CurrentDeviceFcmTokenDeactivationPort`, which matches the documented rule that issue #28 must not directly implement notification persistence.
- Tests explicitly verify:
  - refresh rotation keeps `sessionId`
  - rotated refresh token changes `refreshJti`
  - reused old refresh token invalidates the current session
  - logout blacklists the access token and removes the refresh allowlist
  - logout still succeeds when FCM fields are omitted

## Measurable Outcome

- `./gradlew test --rerun-tasks`: 21 tests, 0 failures, 0 errors, 0 skipped
- `./gradlew build`: success
- `./gradlew asciidoctor --rerun-tasks`: success
- REST Docs snippet groups present: 10
- Docker smoke validation: health 200, Redis refresh key created on login, Redis refresh key removed on logout, old access token rejected after logout
- Changed top-level modules: 2 (`global`, `user`)

## Constraints And Tradeoffs

- Redis runtime behavior was smoke-tested in Docker after the monitor draft: login created an allowlist key, logout removed it, and post-logout auth was rejected.
- Daily health/latency metrics remain unreported because the measurement target is still a pending user decision.
- Gradle deprecated feature warnings remain visible and should be traced before a Gradle 9 upgrade story is claimed.

## Related Evidence

- Commits:
  - `0b7cc7a` docs: #28 refresh logout 계약 기록
  - `f14ffb7` test: #28 refresh logout 실패 테스트 추가
  - `ea5bd3d` feat: #28 refresh rotation logout Redis 구현
  - `59d89b0` test: #28 refresh logout REST Docs 추가
  - `3885808` fix: #28 Redis auth store bean 구성 수정
  - `9a910ba` fix: #28 FCM no-op port 조건부 등록
  - `0cb9166` docs: #28 검증 지표 정합화
- Code:
  - `src/main/java/com/faithlog/user/application/AuthService.java`
  - `src/main/java/com/faithlog/user/presentation/AuthController.java`
  - `src/main/java/com/faithlog/user/infrastructure/redis/RedisRefreshTokenStore.java`
  - `src/main/java/com/faithlog/user/infrastructure/redis/RedisAccessTokenBlacklistStore.java`
  - `src/main/java/com/faithlog/user/infrastructure/fcm/FcmTokenDeactivationAdapterConfig.java`
- Tests:
  - `src/test/java/com/faithlog/user/presentation/AuthRefreshControllerTest.java`
  - `src/test/java/com/faithlog/user/presentation/AuthLogoutControllerTest.java`
  - `src/test/java/com/faithlog/user/presentation/AuthApiRestDocsTest.java`

## Open Questions

- Which runtime should become the single source of truth for daily health and latency metrics?
- Should the monitor start tracking Gradle deprecation warning counts as a maintenance metric after `--warning-mode all` is approved?
