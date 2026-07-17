SELECT 'prayer_submissions' AS source_table,
       count(*) AS candidate_count
FROM prayer_submissions
WHERE created_at < :'range_start'::timestamptz
UNION ALL
SELECT 'charge_items' AS source_table,
       count(*) AS candidate_count
FROM charge_items
WHERE status IN ('PAID', 'WAIVED', 'CANCELED')
  AND created_at >= :'range_start'::timestamptz
  AND created_at < :'range_end'::timestamptz;
