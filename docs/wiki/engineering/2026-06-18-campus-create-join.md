# 2026-06-18 Campus Create And Invite Join

## Scope

- Issue: #29 `[Feat] 캠퍼스 생성과 초대코드 가입 구현`
- Branch: `feat/29-campus-create-join`
- APIs implemented:
  - `POST /api/v1/campuses`
  - `POST /api/v1/campuses/join`
  - `GET /api/v1/campuses/me`
  - `GET /api/v1/campuses/{campusId}`
  - `DELETE /api/v1/campuses/{campusId}/members/{membershipId}`

## Decisions Recorded

- Campus creation and account registration are separate.
- Campus creation does not receive `penaltyAccount`.
- Campus creation does not create `PaymentAccount`.
- Campus creation does not create default `penalty_rules`.
- `GET /api/v1/campuses/me` returns only ACTIVE memberships with `membershipId`, `campusId`, `campusName`, `region`, `campusRole`, and `status`.
- `ADMIN` can see all campus details and invite codes. If the admin is not a campus member, `myCampusRole` and `membershipStatus` are `null`.
- Campus member delete is a soft delete that changes `campus_members.status` to `INACTIVE`.
- Campus member management is allowed for `ADMIN`, `MINISTER`, `ELDER`, and `CAMPUS_LEADER`; normal `MEMBER` users cannot manage campus members.
- If an inactive/deleted member joins again with an invite code, the existing membership is reactivated as `ACTIVE + MEMBER`.

## Implementation Notes

- Added `Campus`, `CampusMember`, `CampusRole`, and `CampusMemberStatus`.
- `CampusMember` stores `campusId` and `userId` instead of directly referencing `User`.
- Added application ports so `CampusService` depends on campus/user lookup ports rather than JPA repository packages.
- Campus creators are registered as `MINISTER + ACTIVE`.
- Invite-code joins immediately create `MEMBER + ACTIVE` membership.
- Duplicate campus membership is blocked by both application check and a `(campus_id, user_id)` unique constraint.
- `CampusDetailResponse.inviteCode` is omitted when null so general `MEMBER` responses do not expose the invite code.
- `CampusMember.deactivate()` handles member removal without deleting historical membership rows.
- `CampusMember.reactivateAsMember()` keeps invite-code rejoin compatible with the unique campus-user membership constraint.

## Verification

- `./gradlew test`: success, 37 tests / 0 failures / 0 errors / 0 skipped.
- `./gradlew build`: success.
- `./gradlew asciidoctor`: success.
- Spring REST Docs snippet groups: 17.
- Forbidden-term scan: no source/test/API-doc violations for the configured forbidden terms or single `optionId` request field.
- Docker PostgreSQL validation:
  - `docker compose up -d postgres redis app` built the app image and started postgres/redis successfully.
  - postgres and redis healthchecks became healthy.
  - Initial app startup failed at DB authentication with `FATAL: password authentication failed for user "faithlog"` because the existing local Docker volume had a network-connection password mismatch for the `faithlog` role.
  - Fixed without deleting the Docker volume by resetting the local development role password to the compose default, then force-recreating only the app container.
  - Validation succeeded with `docker compose run --rm --no-deps postgres ... select 1`, `docker compose up -d --force-recreate app`, and `GET /api/v1/health` 200.

## Evidence

- API tests: `CampusControllerTest`
- Application tests: `CampusServiceTest`
- REST Docs tests: `CampusApiRestDocsTest`
- API docs index: `src/docs/asciidoc/index.adoc`
