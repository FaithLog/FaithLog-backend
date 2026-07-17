\set ON_ERROR_STOP on
\pset tuples_only on
\pset format unaligned

SELECT EXISTS (
	SELECT 1
	FROM pg_extension
	WHERE extname = 'pg_stat_statements'
		AND POSITION('pg_stat_statements' IN CURRENT_SETTING('shared_preload_libraries', TRUE)) > 0
) AS has_pg_stat_statements \gset

\if :has_pg_stat_statements
SELECT JSONB_BUILD_OBJECT(
	'kind', 'pg_stat_statements',
	'stage', :'stage',
	'capturedAt', CLOCK_TIMESTAMP(),
	'calls', calls,
	'totalExecTimeMs', total_exec_time,
	'rows', rows,
	'query', query
)
FROM pg_stat_statements
WHERE query ILIKE '%charge_items%'
	OR query ILIKE '%campus_members%'
	OR query ILIKE '%users%'
ORDER BY total_exec_time DESC
LIMIT 30;
\else
SELECT JSONB_BUILD_OBJECT(
	'kind', 'pg_stat_statements',
	'stage', :'stage',
	'capturedAt', CLOCK_TIMESTAMP(),
	'available', FALSE,
	'reason', 'extension/preload is not active; shared PostgreSQL configuration was not changed'
);
\endif

\if :run_explain
SELECT JSONB_BUILD_OBJECT(
	'kind', 'synthetic_explain_scope',
	'productionPlanEvidence', FALSE,
	'optimizationClaimAllowed', FALSE,
	'interpretation', 'supporting table-access samples only; these statements do not reproduce AdminChargeQueryService orchestration, JVM aggregation, or its full Criteria query shape; Issue #194 owns production-equivalent plan evidence',
	'stage', :'stage',
	'fixtureRunId', :'fixture_run_id'
);

SELECT JSONB_BUILD_OBJECT(
	'kind', 'synthetic_explain_active_campus_members',
	'stage', :'stage',
	'fixtureRunId', :'fixture_run_id'
);
EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON)
SELECT cm.*
FROM campus_members cm
WHERE cm.campus_id = :'campus_id'::bigint
	AND cm.status = 'ACTIVE'
ORDER BY cm.id;

SELECT JSONB_BUILD_OBJECT(
	'kind', 'synthetic_explain_single_user_lookup',
	'stage', :'stage',
	'fixtureRunId', :'fixture_run_id'
);
EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON)
SELECT u.*
FROM users u
WHERE u.id = :'target_user_id'::bigint;

SELECT JSONB_BUILD_OBJECT(
	'kind', 'synthetic_explain_charge_filter',
	'stage', :'stage',
	'fixtureRunId', :'fixture_run_id'
);
EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON)
SELECT c.*
FROM charge_items c
WHERE c.campus_id = :'campus_id'::bigint
	AND c.payment_category = 'COFFEE'
	AND c.status = 'UNPAID'
	AND c.payment_account_id = :'fixture_account_id'::bigint;
\endif
