SELECT nl.id,
       nl.request_id,
       nl.user_id,
       nl.campus_id,
       nl.created_at
FROM notification_logs nl
WHERE nl.send_status = 'PENDING'
  AND nl.created_at <= :'stale_before'::timestamptz
ORDER BY nl.id ASC;
