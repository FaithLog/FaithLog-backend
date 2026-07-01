# 2026-07-01 - #116 Penalty Account Activation And Delete Policy

## Background

- Frontend needs active `PENALTY` account data at the top and inactive `PENALTY` accounts in a lower management list.
- Inactive accounts need activate/delete actions, while active accounts must not be deleted.
- Existing charge snapshots must remain stable when an inactive account is soft deleted.

## Decisions

- `PENALTY` accounts keep one active account per campus.
- Activating an inactive `PENALTY` account deactivates the previous active `PENALTY` account.
- Activating an already active `PENALTY` account is idempotent success.
- The activate API is `PENALTY`-only; `COFFEE` activation fails with `BILLING_PAYMENT_ACCOUNT_ACTIVATE_UNSUPPORTED`.
- Delete is soft delete and inactive-only; active delete fails with `BILLING_PAYMENT_ACCOUNT_ACTIVE_DELETE_FORBIDDEN`.
- Soft deleted accounts are hidden from default and `includeInactive=true` admin lists.

## Implementation

- Added `payment_accounts.deleted_at` through Flyway `V4__add_payment_account_soft_delete.sql`.
- Added admin list filters: `accountType` and `includeInactive`.
- Added:
  - `PATCH /api/v1/admin/campuses/{campusId}/payment-accounts/{paymentAccountId}/activate`
  - `DELETE /api/v1/admin/campuses/{campusId}/payment-accounts/{paymentAccountId}`
- Updated REST Docs snippets and `src/docs/asciidoc/index.adoc`.

## Verification

- TDD red: focused billing tests initially failed at compile time because `deletePaymentAccount`, `activatePenaltyPaymentAccount`, and `deletedAt` did not exist.
- Focused billing service/controller/REST Docs tests: passed in PM session.
- `./gradlew test`: passed.
- `./gradlew build`: passed.
- `./gradlew asciidoctor`: passed after rerun with Gradle wrapper lock access.
- Docker QA: health `UP`, actual API flow verified account register/list/activate/delete behavior and terminal charge snapshot preservation.

## Troubleshooting

- Default Docker compose project reused an old Postgres volume with a mismatched password. QA was rerun with a fresh project name, `faithlogqa116`, without deleting existing volumes.
- `./gradlew cleanTest test` hit a Gradle XML result writer issue after test execution. The normal `./gradlew test` and `./gradlew build` were successful.
