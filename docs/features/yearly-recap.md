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
- Devotion: quiet time, Bible reading, prayer, all-completed days, submitted weeks, longest all-completed streak, and earliest positive most-active month. The same calendar date and submitted `week_start_date` are counted once across multiple campuses.
- Prayer activity: distinct submitted calendar weeks and seasons, where `submitted_at` is non-null and year attribution comes from `prayer_weeks.week_start_date`. The same `week_start_date` across campuses counts once while distinct seasons remain independent.
- Poll activity: participation for `WED_SERVICE`, `SATURDAY_LEADER`, `COFFEE`, `MEAL`, and `CUSTOM`, plus the user's non-deleted comment count. Year attribution uses `polls.starts_at` in `Asia/Seoul`.

The aggregate adapter has a fixed six-statement boundary and does not issue one query per member or activity row.

## Privacy

The response and snapshot exclude prayer text, poll selections, poll memo/comment text, email, payment/account/charge data, JWT/session/device identifiers, and FCM tokens.

## Migration

Issue #237 owns Flyway V14. Issue #236 uses the separate `V15__add_yearly_recap_snapshots.sql` for `yearly_recap_snapshots` and `yearly_recap_campuses`; the two migrations must not be combined. The V15 SQL SHA-256 is `da23f130727718b22f7f880c6f0e9d6a9c0903f1d74e7ca6f915b68930be2716`. Final integration must rebase on develop after #237 and verify the exact V14-to-V15 order again.

The final repository gate after V15 renumbering and calendar-week deduplication completed with 702 tests, no failures or errors, 10 skipped tests, 184 REST Docs snippet groups, and rendered HTML. A dedicated PostgreSQL 17 verification also passed both clean V1-to-V15 and V14-to-V15 upgrade paths using #237's exact V14 SQL: V14 history checksum `1004514371` remained unchanged, V15 checksum was `365369066`, and both recap tables existed. Re-run the migration gate once more after #237 is actually integrated into develop.
