---
project: FaithLog
type: engineering-wiki
created: 2026-07-09
tags:
  - FaithLog
  - backend
  - spring-boot
  - poll
  - tdd
---

# Issue #142 Poll Status Time Synchronization

## Context

- Production data had polls whose DB `status` stayed `SCHEDULED` even though `starts_at <= now < ends_at`.
- User poll list, detail, and response validation required `OPEN`, so current polls were hidden or rejected.
- `timestamptz` UTC storage is correct; the fix must compare with `Instant` and must not rewrite timestamps to KST.

## Implementation

- `PollService` now synchronizes only `SCHEDULED` polls in the current active period to `OPEN` with `poll.open()`.
- Synchronization happens after campus-scoped lookup:
  - user poll list
  - poll detail/result/comment-list visible lookup
  - response/comment/user-option open validation
- The read synchronization intentionally does not close ended polls and does not call coffee settlement, notification, or other close side effects.

## Regression Coverage

- Added `PollServiceTest.current_scheduled_poll_opens_on_member_list_detail_and_response_with_campus_scope`.
- The test verifies:
  - current `SCHEDULED` poll appears in the campus member list as `OPEN`
  - list/detail/response calls persist the `OPEN` transition
  - future scheduled polls stay hidden
  - recently ended polls stay visible in the member closed window
  - another campus poll is not mixed or synchronized by the requested campus path
  - ended polls still reject responses

## Validation

- TDD failure observed before implementation:
  - `./gradlew test --tests com.faithlog.poll.application.PollServiceTest`
  - failed at the new list assertion because the current `SCHEDULED` poll was missing.
- Passing checks after implementation:
  - `./gradlew test --tests com.faithlog.poll.application.PollServiceTest`
  - `./gradlew test --tests com.faithlog.poll.presentation.PollApiRestDocsTest`
  - `./gradlew test` succeeded with 313 tests, 0 failures, 0 errors, 1 skipped.
  - `./gradlew build`
  - `git diff --check`
  - `scripts/qa_docker_compose_isolated.sh --suffix 142-poll-status-sync` completed app image build, PostgreSQL/Redis health, backend health `UP`, and compose down.

## Notes

- No API request/response contract changed.
- No Swagger documentation annotations were added.
- The Docker QA script intentionally does not delete volumes.
