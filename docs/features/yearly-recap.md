# Previous-Year Recap

Issue #236 provides an immutable, privacy-minimized recap for the previous calendar year.

## API

- `GET /api/v1/users/me/yearly-recaps/previous`
- `POST /api/v1/users/me/yearly-recaps/{recapYear}/presented`

Both APIs require an Access Token. The client calls `presented` only after the recap UI has rendered successfully. A recap-fetch failure must not block login or home navigation.

## Time And Presentation

- The server calculates `currentYear - 1` with an injected `Clock` and `Asia/Seoul`.
- An unpresented recap with data may auto-present exactly once across devices.
- The home entry is visible from January 1 00:00:00 through January 14 23:59:59 in `Asia/Seoul`.
- A first open after January 14 may still auto-present once if no device has marked the recap presented.
- A no-data recap returns zero counts and disables both automatic presentation and the home entry.

## Snapshot

The first GET creates one `(user_id, recap_year)` snapshot in a transaction. A user-row lock serializes creation and the database unique constraint is the final race guard. Subsequent GETs read the snapshot, so retention or later source edits do not rewrite history.

The snapshot stores only aggregate counts and ACTIVE campus journey metadata. `presented` preserves the first timestamp and is idempotent. For a no-data snapshot it is a successful no-op.

## Aggregation

- Campus: current ACTIVE memberships, campus name, `joined_at` as an `Asia/Seoul` date, and whether that date belongs to the recap year.
- Devotion: quiet time, Bible reading, prayer, all-completed days, submitted weeks, longest all-completed streak, and earliest positive most-active month.
- Prayer activity: distinct submitted weeks and seasons, where `submitted_at` is non-null and year attribution comes from `prayer_weeks.week_start_date`.
- Poll activity: participation for `WED_SERVICE`, `SATURDAY_LEADER`, `COFFEE`, `MEAL`, and `CUSTOM`, plus the user's non-deleted comment count. Year attribution uses `polls.starts_at` in `Asia/Seoul`.

The aggregate adapter has a fixed six-statement boundary and does not issue one query per member or activity row.

## Privacy

The response and snapshot exclude prayer text, poll selections, poll memo/comment text, email, payment/account/charge data, JWT/session/device identifiers, and FCM tokens.

## Migration

The branch currently uses provisional Flyway V14 for `yearly_recap_snapshots` and `yearly_recap_campuses`. Since Issues #237 and #238 are developed in parallel, the migration version must be re-audited and renumbered before integration.

The full repository gate completed with 701 tests, no failures or errors, 9 skipped tests, 184 REST Docs snippet groups, and rendered HTML. The environment's Docker daemon returned `EOF`, so a dedicated PostgreSQL V1-to-V14 migration remains an integration prerequisite rather than a claimed verification result.
