WITH active_members AS (
  SELECT cm.user_id, cm.campus_role
  FROM campus_members cm
  WHERE cm.campus_id = :'campus_id'::bigint
    AND cm.status = 'ACTIVE'
), member_summary AS (
  SELECT count(*) AS active_count,
         count(*) FILTER (WHERE campus_role IN ('MINISTER', 'ELDER', 'CAMPUS_LEADER')) AS admin_count
  FROM active_members
), inactive_summary AS (
  SELECT count(*) AS inactive_count
  FROM campus_members cm
  WHERE cm.campus_id = :'campus_id'::bigint
    AND cm.status = 'INACTIVE'
), devotion_summary AS (
  SELECT count(DISTINCT wdr.user_id) AS submitted_count
  FROM weekly_devotion_records wdr
  JOIN active_members am ON am.user_id = wdr.user_id
  WHERE wdr.campus_id = :'campus_id'::bigint
    AND wdr.week_start_date = :'week_start_date'::date
    AND wdr.submitted_at IS NOT NULL
), charge_summary AS (
  SELECT count(DISTINCT ci.user_id) AS unpaid_member_count,
         coalesce(sum(ci.amount), 0) AS unpaid_amount,
         coalesce(sum(ci.amount) FILTER (WHERE ci.payment_category = 'PENALTY'), 0) AS penalty_amount,
         coalesce(sum(ci.amount) FILTER (WHERE ci.payment_category = 'COFFEE'), 0) AS coffee_amount
  FROM charge_items ci
  WHERE ci.campus_id = :'campus_id'::bigint
    AND ci.status = 'UNPAID'
    AND ci.payment_category IN ('PENALTY', 'COFFEE')
)
SELECT ms.active_count,
       ins.inactive_count,
       ms.admin_count,
       ds.submitted_count,
       ms.active_count - ds.submitted_count AS missing_count,
       cs.unpaid_member_count,
       cs.unpaid_amount,
       cs.penalty_amount,
       cs.coffee_amount
FROM member_summary ms
CROSS JOIN inactive_summary ins
CROSS JOIN devotion_summary ds
CROSS JOIN charge_summary cs;
