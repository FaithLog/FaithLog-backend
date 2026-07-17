SELECT p.id,
       p.campus_id,
       p.ends_at,
       p.payment_account_id
FROM polls p
WHERE p.poll_type = 'COFFEE'
  AND p.status = 'OPEN'
  AND p.ends_at <= :'range_end'::timestamptz
ORDER BY p.id ASC;
