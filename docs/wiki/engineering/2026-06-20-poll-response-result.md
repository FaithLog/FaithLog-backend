# Issue #38 Poll Response And Result

## Summary

Implemented poll response, poll-level result lookup, missing-member lookup, and poll comments for campus ACTIVE members.

## Implemented

- `poll_responses` stores one response bundle per poll/user.
- `poll_response_options` stores selected `optionIds`.
- `poll_comments` supports author identity and soft delete.
- Member APIs:
  - `GET /api/v1/campuses/{campusId}/polls`
  - `GET /api/v1/campuses/{campusId}/polls/{pollId}`
  - `PUT /api/v1/campuses/{campusId}/polls/{pollId}/responses/me`
  - `GET /api/v1/campuses/{campusId}/polls/{pollId}/results`
  - poll comment CRUD
- Admin API:
  - `GET /api/v1/admin/campuses/{campusId}/polls/{pollId}/missing-members`

## TDD Record

- Red: `./gradlew test --tests com.faithlog.poll.application.PollServiceTest` failed at `compileTestJava` because #38 entities, repositories, commands/results, service methods, and detailed poll error codes did not exist.
- Green: Added focused service tests for response validation, response option replacement, anonymous/non-anonymous result privacy, visibility windows, missing members, comments, and soft delete.
- Docs: Added REST Docs coverage for response upsert, duplicate-option error, poll list/detail, results, missing members, and comments.

## Verification

- `./gradlew test --tests com.faithlog.poll.application.PollServiceTest --tests com.faithlog.poll.presentation.PollApiRestDocsTest`: success.
- `./gradlew test`: success, 152 tests / 0 failures / 0 errors / 0 skipped.
- `./gradlew build`: success.
- `./gradlew asciidoctor`: success after sandbox escalation for Gradle wrapper lock access.
- Docker:
  - `docker compose build app`: success.
  - `docker compose up -d`: success.
  - Postgres and Redis healthy.
  - Backend container internal `GET /actuator/health`: `{"status":"UP"}`.
  - Host `curl localhost:8080` remained unavailable from this Codex environment, matching prior host-port limitations in this project.

## Boundaries

- Did not implement #39 coffee charge automation.
- Did not connect `charge_items.source_type = POLL_RESPONSE`.
- Did not implement #24 scheduler/batch.
- Did not add option-level result APIs.
- Did not implement anonymous comments.
- Did not implement lunch poll/order/charge scope.
- Did not add Swagger documentation annotations.
