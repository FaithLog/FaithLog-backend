\set ON_ERROR_STOP on
\pset tuples_only on
\pset format unaligned

WITH snapshot_cleared AS (
	SELECT PG_STAT_CLEAR_SNAPSHOT()
)
SELECT JSONB_BUILD_OBJECT(
	'xactCommit', d.xact_commit::text,
	'xactRollback', d.xact_rollback::text,
	'blksRead', d.blks_read::text,
	'blksHit', d.blks_hit::text,
	'tupReturned', d.tup_returned::text,
	'tupFetched', d.tup_fetched::text
)
FROM pg_stat_database d
CROSS JOIN snapshot_cleared
WHERE d.datname = CURRENT_DATABASE();
