SELECT ci.id,
       ci.user_id,
       u.name,
       u.email,
       ci.payment_category,
       ci.status,
       ci.amount,
       ci.created_at
FROM charge_items ci
JOIN users u ON u.id = ci.user_id
WHERE ci.campus_id = :'campus_id'::bigint
  AND ci.status = 'UNPAID'
  AND ci.payment_account_id = :'payment_account_id'::bigint
  AND (
    lower(u.name) LIKE lower(:'keyword_pattern')
    OR lower(u.email) LIKE lower(:'keyword_pattern')
  )
ORDER BY ci.created_at DESC, ci.id DESC
LIMIT :'page_size'::integer
OFFSET :'page_offset'::integer;
