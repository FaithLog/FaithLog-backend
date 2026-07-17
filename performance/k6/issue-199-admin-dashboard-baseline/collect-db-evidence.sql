-- issue199:evidence=context
\set ON_ERROR_STOP on

BEGIN TRANSACTION READ ONLY;

SELECT
    current_database() AS database_name,
    now() AS captured_at,
    datname,
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
WHERE datname = current_database();

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
ORDER BY relname;

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
ORDER BY name;

SELECT 'users' AS table_name, COUNT(*) AS row_count FROM users
UNION ALL
SELECT 'campuses', COUNT(*) FROM campuses
UNION ALL
SELECT 'campus_members', COUNT(*) FROM campus_members WHERE campus_id = :'campus_id'::bigint
UNION ALL
SELECT 'weekly_devotion_records', COUNT(*) FROM weekly_devotion_records WHERE campus_id = :'campus_id'::bigint
UNION ALL
SELECT 'devotion_daily_checks', COUNT(*)
FROM devotion_daily_checks ddc
JOIN weekly_devotion_records wdr ON wdr.id = ddc.weekly_record_id
WHERE wdr.campus_id = :'campus_id'::bigint
UNION ALL
SELECT 'payment_accounts', COUNT(*) FROM payment_accounts WHERE campus_id = :'campus_id'::bigint
UNION ALL
SELECT 'charge_items', COUNT(*) FROM charge_items WHERE campus_id = :'campus_id'::bigint
UNION ALL
SELECT 'polls', COUNT(*) FROM polls WHERE campus_id = :'campus_id'::bigint
UNION ALL
SELECT 'poll_responses', COUNT(*)
FROM poll_responses pr
JOIN polls p ON p.id = pr.poll_id
WHERE p.campus_id = :'campus_id'::bigint
UNION ALL
SELECT 'meal_poll_settlements', COUNT(*) FROM meal_poll_settlements WHERE campus_id = :'campus_id'::bigint
UNION ALL
SELECT 'prayer_submissions', COUNT(*)
FROM prayer_submissions ps
JOIN prayer_weeks pw ON pw.id = ps.prayer_week_id
WHERE pw.campus_id = :'campus_id'::bigint
ORDER BY table_name;

SELECT (to_regclass('pg_stat_statements') IS NOT NULL)::integer AS pgss_available \gset
\if :pgss_available
SELECT
    queryid,
    calls,
    rows,
    total_exec_time,
    mean_exec_time,
    shared_blks_hit,
    shared_blks_read,
    temp_blks_read,
    temp_blks_written,
    query
FROM pg_stat_statements
WHERE query ILIKE '%campus_members%'
   OR query ILIKE '%weekly_devotion_records%'
   OR query ILIKE '%charge_items%'
   OR query ILIKE '%poll_responses%'
ORDER BY total_exec_time DESC
LIMIT 50;
\else
SELECT 'query-evidence-unavailable: pg_stat_statements is not installed' AS pg_stat_statements_status;
\endif

COMMIT;
