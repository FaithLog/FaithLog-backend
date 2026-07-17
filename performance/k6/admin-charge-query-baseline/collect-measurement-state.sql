\set ON_ERROR_STOP on
\pset tuples_only on
\pset format unaligned

SELECT EXISTS (
	SELECT 1
	FROM pg_extension
	WHERE extname = 'pg_stat_statements'
		AND POSITION('pg_stat_statements' IN CURRENT_SETTING('shared_preload_libraries', TRUE)) > 0
) AS pgss_available \gset

\if :pgss_available
SELECT
	COALESCE(stats_reset::text, '') AS pgss_stats_reset,
	COALESCE(dealloc, 0) AS pgss_dealloc
FROM pg_stat_statements_info
LIMIT 1 \gset
\else
\set pgss_stats_reset ''
\set pgss_dealloc 0
\endif

WITH table_state AS (
	SELECT JSONB_OBJECT_AGG(
		relname,
		JSONB_BUILD_OBJECT(
			'nModSinceAnalyze', n_mod_since_analyze,
			'lastAnalyze', last_analyze,
			'lastAutoanalyze', last_autoanalyze,
			'analyzeCount', analyze_count,
			'autoanalyzeCount', autoanalyze_count,
			'lastVacuum', last_vacuum,
			'lastAutovacuum', last_autovacuum,
			'vacuumCount', vacuum_count,
			'autovacuumCount', autovacuum_count
		)
	) AS value
	FROM pg_stat_user_tables
	WHERE relname IN ('users', 'campus_members', 'payment_accounts', 'charge_items')
), planner_state AS (
	SELECT JSONB_OBJECT_AGG(name, setting ORDER BY name) AS value
	FROM pg_settings
	WHERE name IN (
		'enable_bitmapscan', 'enable_hashjoin', 'enable_indexonlyscan', 'enable_indexscan',
		'enable_mergejoin', 'enable_nestloop', 'enable_seqscan', 'effective_cache_size',
		'random_page_cost', 'seq_page_cost', 'work_mem'
	)
)
SELECT JSONB_BUILD_OBJECT(
	'capturedAt', CLOCK_TIMESTAMP(),
	'externalActiveCount', (
		SELECT COUNT(*)
		FROM pg_stat_activity
		WHERE datname = CURRENT_DATABASE()
			AND pid <> PG_BACKEND_PID()
			AND state <> 'idle'
	),
	'database', (
		SELECT JSONB_BUILD_OBJECT(
			'name', CURRENT_DATABASE(),
			'serverAddress', COALESCE(INET_SERVER_ADDR()::text, 'unix-socket'),
			'serverPort', COALESCE(INET_SERVER_PORT(), CURRENT_SETTING('port')::integer),
			'postmasterStartTime', PG_POSTMASTER_START_TIME(),
			'statsReset', stats_reset
		)
		FROM pg_stat_database
		WHERE datname = CURRENT_DATABASE()
	),
	'plannerSettings', planner_state.value,
	'pgStatStatements', JSONB_BUILD_OBJECT(
		'available', :'pgss_available'::boolean,
		'statsReset', NULLIF(:'pgss_stats_reset', '')::timestamptz,
		'dealloc', :'pgss_dealloc'::bigint
	),
	'tables', table_state.value
)
FROM table_state, planner_state;
