SELECT pa.id AS payment_account_id,
       count(ci.id) AS charge_count,
       count(ci.id) FILTER (WHERE ci.status = 'UNPAID') AS unpaid_count,
       coalesce(sum(ci.amount) FILTER (WHERE ci.status = 'UNPAID'), 0) AS unpaid_amount,
       coalesce(sum(ci.amount) FILTER (WHERE ci.status = 'PAID'), 0) AS paid_amount
FROM payment_accounts pa
LEFT JOIN charge_items ci
  ON ci.payment_account_id = pa.id
 AND (
   ci.status = 'UNPAID'
   OR (ci.status = 'PAID' AND ci.paid_at >= :'archive_cutoff'::timestamptz)
   OR (ci.status IN ('WAIVED', 'CANCELED') AND ci.updated_at >= :'archive_cutoff'::timestamptz)
 )
WHERE pa.campus_id = :'campus_id'::bigint
  AND pa.owner_user_id = :'member_user_id'::bigint
  AND pa.account_type = 'COFFEE'
  AND pa.is_active = TRUE
  AND pa.deleted_at IS NULL
GROUP BY pa.id
ORDER BY pa.id ASC;
