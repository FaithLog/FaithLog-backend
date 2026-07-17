SELECT uft.id,
       uft.user_id,
       uft.device_type,
       uft.last_seen_at,
       uft.last_refreshed_at
FROM campus_members cm
JOIN user_fcm_tokens uft ON uft.user_id = cm.user_id
WHERE cm.campus_id = :'campus_id'::bigint
  AND cm.status = 'ACTIVE'
  AND uft.is_active = TRUE
  AND uft.last_seen_at >= :'stale_before'::timestamptz
  AND uft.last_refreshed_at >= :'stale_before'::timestamptz
ORDER BY uft.id ASC;
