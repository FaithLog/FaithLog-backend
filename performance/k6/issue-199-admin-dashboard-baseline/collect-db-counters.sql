-- issue199:evidence=counters
\set ON_ERROR_STOP on
\pset format unaligned
\pset tuples_only on
\pset pager off

BEGIN TRANSACTION READ ONLY;

WITH database_counters AS (
    SELECT
        datname,
        stats_reset,
        xact_commit::text AS xact_commit,
        xact_rollback::text AS xact_rollback,
        blks_read::text AS blks_read,
        blks_hit::text AS blks_hit,
        tup_returned::text AS tup_returned,
        tup_fetched::text AS tup_fetched,
        temp_files::text AS temp_files,
        temp_bytes::text AS temp_bytes,
        deadlocks::text AS deadlocks
    FROM pg_stat_database
    WHERE datname = current_database()
),
table_counters AS (
    SELECT
        relname,
        seq_scan::text AS seq_scan,
        seq_tup_read::text AS seq_tup_read,
        idx_scan::text AS idx_scan,
        idx_tup_fetch::text AS idx_tup_fetch,
        n_live_tup::text AS n_live_tup,
        n_dead_tup::text AS n_dead_tup,
        n_mod_since_analyze::text AS n_mod_since_analyze,
        last_analyze,
        last_autoanalyze,
        analyze_count::text AS analyze_count,
        autoanalyze_count::text AS autoanalyze_count
    FROM pg_stat_user_tables
    WHERE relname IN (
        'users',
        'campuses',
        'campus_members',
        'weekly_devotion_records',
        'devotion_daily_checks',
        'payment_accounts',
        'charge_items',
        'polls',
        'poll_responses',
        'meal_poll_settlements',
        'prayer_submissions'
    )
),
planner_settings AS (
    SELECT name, setting, source
    FROM pg_settings
    WHERE name IN (
        'enable_bitmapscan',
        'enable_hashagg',
        'enable_hashjoin',
        'enable_indexonlyscan',
        'enable_indexscan',
        'enable_material',
        'enable_mergejoin',
        'enable_nestloop',
        'enable_seqscan',
        'jit',
        'plan_cache_mode',
        'random_page_cost',
        'work_mem'
    )
),
planner_context AS (
    SELECT jsonb_build_object(
        'currentUser', current_user,
        'sessionUser', session_user,
        'database', current_database(),
        'applicationName', current_setting('application_name'),
        'scope', 'observer-session'
    ) AS context
),
external_activity AS (
    SELECT
        COUNT(*) AS active_sessions,
        COALESCE(
            jsonb_agg(jsonb_build_object(
                'pid', pid,
                'applicationName', application_name,
                'backendStart', backend_start,
                'transactionStart', xact_start,
                'queryStart', query_start,
                'state', state
            ) ORDER BY pid),
            '[]'::jsonb
        ) AS active_session_details
    FROM pg_stat_activity
    WHERE datname = current_database()
      AND pid <> pg_backend_pid()
      AND backend_type = 'client backend'
      AND state IS DISTINCT FROM 'idle'
)
SELECT jsonb_build_object(
    'capturedAt', now(),
    'externalActivityCoverage', 'boundary-snapshot-only',
    'externalActiveSessions', (SELECT active_sessions FROM external_activity),
    'externalActiveSessionDetails', (SELECT active_session_details FROM external_activity),
    'observerOverhead', jsonb_build_object(
        'databaseWideCountersIncludeSnapshotTransaction', true,
        'databaseWideDeltaIsExactQueryCount', false,
        'appTableCountersReadApplicationTables', false
    ),
    'database', (SELECT to_jsonb(database_counters) FROM database_counters),
    'tables', COALESCE(
        (SELECT jsonb_agg(to_jsonb(table_counters) ORDER BY relname) FROM table_counters),
        '[]'::jsonb
    ),
    'plannerSettings', COALESCE(
        (SELECT jsonb_agg(to_jsonb(planner_settings) ORDER BY name) FROM planner_settings),
        '[]'::jsonb
    ),
    'plannerContext', (SELECT context FROM planner_context)
)
FROM database_counters;

COMMIT;
