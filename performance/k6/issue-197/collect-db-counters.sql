\set ON_ERROR_STOP on
\pset format unaligned
\pset tuples_only on
\pset pager off

BEGIN TRANSACTION READ ONLY;

WITH database_counters AS (
    SELECT datname, stats_reset, xact_commit::text AS xact_commit, xact_rollback::text AS xact_rollback,
           blks_read::text AS blks_read, blks_hit::text AS blks_hit,
           tup_returned::text AS tup_returned, tup_fetched::text AS tup_fetched,
           tup_inserted::text AS tup_inserted, tup_updated::text AS tup_updated, tup_deleted::text AS tup_deleted
    FROM pg_stat_database
    WHERE datname = current_database()
), all_database_counters AS (
    SELECT datname, stats_reset, xact_commit::text AS xact_commit, xact_rollback::text AS xact_rollback,
           blks_read::text AS blks_read, blks_hit::text AS blks_hit,
           tup_returned::text AS tup_returned, tup_fetched::text AS tup_fetched,
           tup_inserted::text AS tup_inserted, tup_updated::text AS tup_updated, tup_deleted::text AS tup_deleted
    FROM pg_stat_database
), table_counters AS (
    SELECT relname, seq_scan::text AS seq_scan, seq_tup_read::text AS seq_tup_read,
           idx_scan::text AS idx_scan, idx_tup_fetch::text AS idx_tup_fetch,
           n_tup_ins::text AS n_tup_ins, n_tup_upd::text AS n_tup_upd,
           n_tup_del::text AS n_tup_del, n_mod_since_analyze::text AS n_mod_since_analyze,
           last_analyze, last_autoanalyze, analyze_count, autoanalyze_count,
           last_vacuum, last_autovacuum, vacuum_count, autovacuum_count
    FROM pg_stat_user_tables
    WHERE relname IN (
        'users', 'campuses', 'campus_members', 'penalty_rules', 'payment_accounts',
        'weekly_devotion_records', 'devotion_daily_checks', 'charge_items'
    )
), external_activity AS (
    SELECT count(*) FILTER (WHERE datname = current_database()) AS current_database_active_sessions,
           count(*) AS all_database_active_sessions
    FROM pg_stat_activity
    WHERE pid <> pg_backend_pid()
      AND backend_type = 'client backend'
      AND state IS DISTINCT FROM 'idle'
), planner_settings AS (
    SELECT name, setting, unit, source
    FROM pg_settings
    WHERE name IN (
        'enable_bitmapscan', 'enable_hashagg', 'enable_hashjoin', 'enable_indexonlyscan',
        'enable_indexscan', 'enable_material', 'enable_mergejoin', 'enable_nestloop',
        'enable_seqscan', 'jit', 'plan_cache_mode', 'random_page_cost', 'work_mem'
    )
)
-- issue197:db-counter-observer
SELECT json_build_object(
    'snapshot', json_build_object(
        'capturedAt', now(),
        'observerOverhead', json_build_object(
            'databaseWideCountersIncludeSnapshotTransaction', true,
            'databaseWideDeltaIsExactQueryCount', false,
            'appTableCountersReadApplicationTables', false
        ),
        'externalActiveSessions', (SELECT current_database_active_sessions FROM external_activity),
        'externalActiveSessionsAllDatabases', (SELECT all_database_active_sessions FROM external_activity),
        'database', (SELECT row_to_json(database_counters) FROM database_counters),
        'allDatabases', COALESCE((SELECT json_agg(row_to_json(all_database_counters) ORDER BY datname NULLS FIRST) FROM all_database_counters), '[]'::json),
        'tables', COALESCE((SELECT json_agg(row_to_json(table_counters) ORDER BY relname) FROM table_counters), '[]'::json),
        'plannerSettings', COALESCE((SELECT json_agg(row_to_json(planner_settings) ORDER BY name) FROM planner_settings), '[]'::json)
    )
);

SELECT (
    to_regclass('pg_stat_statements') IS NOT NULL
    AND position('pg_stat_statements' IN current_setting('shared_preload_libraries', true)) > 0
)::integer AS pgss_available \gset

\if :pgss_available
-- issue197:db-counter-observer
SELECT json_build_object(
    'pgStatStatements', json_build_object(
        'available', true,
        'statements', COALESCE(json_agg(json_build_object(
            'query', query,
            'calls', calls,
            'rows', rows,
            'totalExecTime', total_exec_time,
            'sharedBlksHit', shared_blks_hit,
            'sharedBlksRead', shared_blks_read
        ) ORDER BY query), '[]'::json)
    )
)
FROM (
    SELECT query,
           sum(calls)::text AS calls,
           sum(rows)::text AS rows,
           sum(total_exec_time) AS total_exec_time,
           sum(shared_blks_hit)::text AS shared_blks_hit,
           sum(shared_blks_read)::text AS shared_blks_read
    FROM pg_stat_statements
    WHERE dbid = (SELECT oid FROM pg_database WHERE datname = current_database())
      AND query ILIKE ANY (ARRAY[
          '%weekly_devotion_records%', '%devotion_daily_checks%', '%penalty_rules%',
          '%payment_accounts%', '%charge_items%'
      ])
      AND query NOT LIKE '%issue197:db-counter-observer%'
      AND query NOT ILIKE '%pg_stat_%'
    GROUP BY query
) AS statement_counters;
\else
SELECT json_build_object(
    'pgStatStatements', json_build_object(
        'available', false,
        'reason', 'extension or shared_preload_libraries unavailable',
        'statements', '[]'::json
    )
);
\endif

COMMIT;
