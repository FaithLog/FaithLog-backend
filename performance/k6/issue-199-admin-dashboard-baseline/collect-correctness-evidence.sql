-- issue199:evidence=correctness
\set ON_ERROR_STOP on
\pset format unaligned
\pset tuples_only on
\pset pager off

BEGIN TRANSACTION READ ONLY;

WITH campus_summary AS (
    SELECT id AS campus_id, name AS campus_name, region
    FROM campuses
    WHERE id = :'campus_id'::bigint
),
active_members AS (
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
SELECT jsonb_build_object(
	'evidenceBoundary', :'evidence_boundary',
    'campus', jsonb_build_object(
        'campusId', campus.campus_id,
        'campusName', campus.campus_name,
        'region', campus.region
    ),
    'members', jsonb_build_object(
        'activeCount', members.active_count,
        'inactiveCount', members.inactive_count,
        'adminCount', members.admin_count
    ),
    'devotion', jsonb_build_object(
        'weekStartDate', :'week_start_date',
        'submittedCount', devotion.submitted_count,
        'missingCount', members.active_count - devotion.submitted_count,
        'submitRate', CASE
            WHEN members.active_count = 0 THEN 0
            ELSE ROUND(devotion.submitted_count * 100.0 / members.active_count, 1)
        END
    ),
    'charges', jsonb_build_object(
        'statusBasis', jsonb_build_array('UNPAID'),
        'unpaidAmount', charges.unpaid_amount,
        'unpaidMemberCount', charges.unpaid_member_count,
        'byCategory', categories.categories
    ),
    'polls', jsonb_build_object(
        'openCount', polls.open_count,
        'recentlyClosedCount', closed.recently_closed_count,
        'missingResponseCount', polls.missing_response_count,
        'recentlyClosedDays', 7
    ),
    'pollResponseCounts', polls.poll_response_counts
)
FROM campus_summary campus
CROSS JOIN member_summary members
CROSS JOIN devotion_summary devotion
CROSS JOIN charge_summary charges
CROSS JOIN charge_categories categories
CROSS JOIN poll_summary polls
CROSS JOIN recently_closed closed;

COMMIT;
