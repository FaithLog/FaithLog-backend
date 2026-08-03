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

The first GET creates one `(user_id, recap_year)` snapshot in a `REPEATABLE_READ` transaction, so all six aggregate statements observe one PostgreSQL MVCC snapshot. The presented POST uses the same isolation when it must create a missing snapshot. A user-row lock and database unique constraint guard creation; a bounded transaction-level retry handles only the snapshot unique race or transient database conflict. Subsequent GETs read the snapshot, so retention or later source edits do not rewrite history.

The snapshot stores only aggregate counts and ACTIVE campus journey metadata. `presented` preserves the first timestamp and is idempotent. For a no-data snapshot it is a successful no-op.

V17 preserves only compact per-user/year facts immediately before retention deletes a source row. Archive upsert and source deletion share one database transaction. The first GET merges archived and still-live facts by stable source ID before freezing V15. A coverage watermark hides years that may already have lost data before V17 was deployed; incomplete years create no snapshot and are not presented as accurate recaps.

## Aggregation

- Campus: current ACTIVE memberships whose `joined_at` is before the recap-year end boundary, campus name, `joined_at` as an `Asia/Seoul` date, and whether that date belongs to the recap year. December 31 is included and January 1 of the next year is excluded.
- Devotion: quiet time, Bible reading, prayer, all-completed days, submitted weeks, longest all-completed streak, and earliest positive most-active month. The same calendar date and submitted `week_start_date` are counted once across multiple campuses.
- Prayer activity: distinct submitted calendar weeks and seasons, where `submitted_at` is non-null and year attribution comes from `prayer_weeks.week_start_date`. The same `week_start_date` across campuses counts once while distinct seasons remain independent.
- Comment activity: count only non-deleted comments written by the authenticated user in the recap year. Year attribution uses the comment's own `created_at` in `Asia/Seoul`, not the poll start. Poll participation, PollType counts, selections, options, and comment bodies are excluded.
- Penalty summary: count and sum only the authenticated user's `PENALTY` charges whose source is a `DEVOTION_RECORD` with `weekStartDate` in the recap year. Include only `PAID` and `UNPAID`; exclude `WAIVED`, `CANCELED`, coffee, meal, and group charges. Total count and amount equal paid plus unpaid exactly.

The aggregate adapter has a fixed six-statement boundary and does not issue one query per member or activity row. V17 archive existence checks and writes use batches of at most 500, preventing an annual 1,000-member archive from creating one unbounded SQL `IN` predicate.

## Privacy

The response, snapshot, and compact archive exclude prayer text, poll participation and selections, poll memo/comment text, email, account data, individual charge rows, JWT/session/device identifiers, and FCM tokens. Only the aggregate penalty summary is retained.

## Migration

Issue #237 owns V14. The corrected V15 snapshot stores the final comment and penalty shape, V16 adds poll notice/image/outbox, V17 adds compact recap facts and coverage, and V18 adds durable media-cleanup retry/lease metadata. Physical order is always V14 -> V15 -> V16 -> V17 -> V18. Every recap table explicitly enables RLS; snapshot and archive foreign-key boundaries remain fail closed. The corrected V15 SQL SHA-256 is `bd7b956e8aba48d9c21dd0cd113cb09170aed41023002318aa723489d12dfb34`.

The final exact-HEAD `clean test build asciidoctor` gate passed 836 tests with no failures or errors and 14 skipped tests. A disposable PostgreSQL 17 gate passed all 8 clean and protected upgrade cases through V18, including recap RLS/FK/CHECK constraints and migration order, without touching the shared QA database.
