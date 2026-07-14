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

WITH active_members AS (
    SELECT user_id, campus_role
    FROM campus_members
    WHERE campus_id = :'campus_id'::bigint
      AND status = 'ACTIVE'
),
member_summary AS (
    SELECT
        (SELECT COUNT(*) FROM active_members) AS active_count,
        COUNT(*) FILTER (WHERE status <> 'ACTIVE') AS inactive_count,
        (SELECT COUNT(*) FROM active_members WHERE campus_role IN ('MINISTER', 'ELDER', 'CAMPUS_LEADER')) AS admin_count
    FROM campus_members
    WHERE campus_id = :'campus_id'::bigint
),
devotion_summary AS (
    SELECT COUNT(DISTINCT wdr.user_id) FILTER (WHERE wdr.submitted_at IS NOT NULL) AS submitted_count
    FROM weekly_devotion_records wdr
    JOIN active_members am ON am.user_id = wdr.user_id
    WHERE wdr.campus_id = :'campus_id'::bigint
      AND wdr.week_start_date = :'week_start_date'::date
),
unpaid_charges AS (
    SELECT user_id, payment_category, amount
    FROM charge_items
    WHERE campus_id = :'campus_id'::bigint
      AND status = 'UNPAID'
      AND payment_category IN ('PENALTY', 'COFFEE')
),
charge_summary AS (
    SELECT COALESCE(SUM(amount), 0) AS unpaid_amount, COUNT(DISTINCT user_id) AS unpaid_member_count
    FROM unpaid_charges
),
charge_categories AS (
    SELECT jsonb_agg(
        jsonb_build_object('paymentCategory', category.payment_category, 'unpaidAmount', COALESCE(amounts.unpaid_amount, 0))
        ORDER BY category.sort_order
    ) AS categories
    FROM (VALUES ('PENALTY', 1), ('COFFEE', 2)) AS category(payment_category, sort_order)
    LEFT JOIN (
        SELECT payment_category, SUM(amount) AS unpaid_amount
        FROM unpaid_charges
        GROUP BY payment_category
    ) amounts ON amounts.payment_category = category.payment_category
),
open_polls AS (
    SELECT id
    FROM polls
    WHERE campus_id = :'campus_id'::bigint
      AND status = 'OPEN'
      AND poll_type <> 'MEAL'
),
poll_response_counts AS (
    SELECT
        op.id AS poll_id,
        COUNT(DISTINCT pr.user_id) FILTER (WHERE am.user_id IS NOT NULL) AS poll_response_count
    FROM open_polls op
    LEFT JOIN poll_responses pr ON pr.poll_id = op.id
    LEFT JOIN active_members am ON am.user_id = pr.user_id
    GROUP BY op.id
),
poll_summary AS (
    SELECT
        COUNT(prc.poll_id) AS open_count,
        COALESCE(SUM(GREATEST(0, ms.active_count - prc.poll_response_count)), 0) AS missing_response_count,
        COALESCE(
            jsonb_agg(
                jsonb_build_object('pollId', prc.poll_id, 'responseCount', prc.poll_response_count)
                ORDER BY prc.poll_id
            ) FILTER (WHERE prc.poll_id IS NOT NULL),
            '[]'::jsonb
        ) AS poll_response_counts
    FROM member_summary ms
    LEFT JOIN poll_response_counts prc ON TRUE
    GROUP BY ms.active_count
),
recently_closed AS (
    SELECT COUNT(*) AS recently_closed_count
    FROM polls
    WHERE campus_id = :'campus_id'::bigint
      AND status = 'CLOSED'
      AND poll_type <> 'MEAL'
      AND ends_at BETWEEN now() - interval '7 days' AND now()
)
SELECT jsonb_pretty(jsonb_build_object(
    'members', jsonb_build_object(
        'activeCount', ms.active_count,
        'inactiveCount', ms.inactive_count,
        'adminCount', ms.admin_count
    ),
    'devotion', jsonb_build_object(
        'weekStartDate', :'week_start_date',
        'submittedCount', ds.submitted_count,
        'missingCount', ms.active_count - ds.submitted_count,
        'submitRate', CASE
            WHEN ms.active_count = 0 THEN 0
            ELSE ROUND(ds.submitted_count * 100.0 / ms.active_count, 1)
        END
    ),
    'charges', jsonb_build_object(
        'statusBasis', jsonb_build_array('UNPAID'),
        'unpaidAmount', cs.unpaid_amount,
        'unpaidMemberCount', cs.unpaid_member_count,
        'byCategory', cc.categories
    ),
    'polls', jsonb_build_object(
        'openCount', ps.open_count,
        'recentlyClosedCount', rc.recently_closed_count,
        'recentlyClosedDays', 7,
        'pollResponseCounts', ps.poll_response_counts,
        'missingResponseCount', ps.missing_response_count
    )
)) AS correctness_evidence
FROM member_summary ms
CROSS JOIN devotion_summary ds
CROSS JOIN charge_summary cs
CROSS JOIN charge_categories cc
CROSS JOIN poll_summary ps
CROSS JOIN recently_closed rc;

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
