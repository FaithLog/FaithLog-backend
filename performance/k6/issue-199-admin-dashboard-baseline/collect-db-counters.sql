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
        xact_commit,
        xact_rollback,
        blks_read,
        blks_hit,
        tup_returned,
        tup_fetched,
        temp_files,
        temp_bytes,
        deadlocks
    FROM pg_stat_database
    WHERE datname = current_database()
),
table_counters AS (
    SELECT
        relname,
        seq_scan,
        seq_tup_read,
        idx_scan,
        idx_tup_fetch,
        n_live_tup,
        n_dead_tup,
        n_mod_since_analyze,
        last_analyze,
        last_autoanalyze,
        analyze_count,
        autoanalyze_count
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
external_activity AS (
    SELECT COUNT(*) AS active_sessions
    FROM pg_stat_activity
    WHERE datname = current_database()
      AND pid <> pg_backend_pid()
      AND backend_type = 'client backend'
      AND state IS DISTINCT FROM 'idle'
      AND application_name IS DISTINCT FROM 'faithlog-issue199-observer'
)
SELECT jsonb_build_object(
    'capturedAt', now(),
    'externalActiveSessions', (SELECT active_sessions FROM external_activity),
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
    )
)
FROM database_counters;

COMMIT;
