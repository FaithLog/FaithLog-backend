SELECT ci.id,
       ci.payment_category,
       ci.status,
       ci.amount,
       ci.created_at,
       ci.paid_at,
       ci.updated_at
FROM charge_items ci
WHERE ci.campus_id = :'campus_id'::bigint
  AND ci.user_id = :'member_user_id'::bigint
  AND ci.payment_category <> 'MEAL'
  AND (
    ci.status = 'UNPAID'
    OR (ci.status = 'PAID' AND ci.paid_at >= :'archive_cutoff'::timestamptz)
    OR (ci.status IN ('WAIVED', 'CANCELED') AND ci.updated_at >= :'archive_cutoff'::timestamptz)
  )
ORDER BY ci.created_at DESC, ci.id DESC
LIMIT :'page_size'::integer
OFFSET :'page_offset'::integer;
