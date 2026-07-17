SELECT ci.id,
       ci.user_id,
       ci.source_id,
       ci.status,
       ci.amount,
       ci.payment_account_id
FROM charge_items ci
JOIN poll_responses pr ON pr.id = ci.source_id
WHERE ci.campus_id = :'campus_id'::bigint
  AND ci.payment_category = 'COFFEE'
  AND ci.source_type = 'POLL_RESPONSE'
  AND pr.poll_id = :'poll_id'::bigint
ORDER BY ci.id ASC;
