SELECT ci.id,
       ci.payment_category,
       ci.status,
       ci.amount,
       ci.created_at,
       ci.paid_at
FROM charge_items ci
WHERE ci.campus_id = :'campus_id'::bigint
  AND ci.user_id = :'member_user_id'::bigint
ORDER BY ci.created_at DESC, ci.id DESC
LIMIT :'page_size'::integer
OFFSET :'page_offset'::integer;
