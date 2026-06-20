# Issue #37 Poll Template And Poll Create

## Summary

Implemented the first poll foundation for FaithLog:

- Coffee brand/menu catalog backed by reusable CSV seed data.
- Campus default coffee poll template with five default options.
- Admin poll template create/update/deactivate/list/detail APIs.
- Admin poll create API for direct options and template-based option copying.
- Menu code/name/price snapshots on template options and poll options.

## Seed Data

The reusable source of truth is `src/main/resources/seed/compose-coffee-menu-2026.csv`.

Implementation notes:

- The application seed runner upserts `COMPOSE_COFFEE` and menu rows by `menu_code`.
- `AMERICANO_HOT` stores `아메리카노` at 1,500 KRW.
- `AMERICANO_ICE` stores `아이스 아메리카노` at 1,800 KRW.
- The source was user-approved after official Compose Coffee access was blocked during implementation.

## Verification

- TDD failure: `./gradlew test --tests 'com.faithlog.poll.application.PollServiceTest'` failed before implementation with 97 missing-type compile errors.
- Target service tests: success.
- Poll REST Docs test: success.
- Full test/build/docs: success.
- Docker build and app health: success. `docker compose down` completed after validation.

## Boundaries

- Did not implement #24 scheduler/batch auto poll generation.
- Did not implement #38 poll response/result/comment logic.
- Did not implement #39 coffee response charge automation.
- Did not add Swagger documentation annotations.
