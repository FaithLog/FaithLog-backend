SELECT cm.id AS membership_id,
       cm.user_id,
       u.name,
       u.email
FROM campus_members cm
JOIN users u ON u.id = cm.user_id
WHERE cm.campus_id = :'campus_id'::bigint
  AND cm.status = 'ACTIVE'
  AND NOT EXISTS (
    SELECT 1
    FROM poll_responses pr
    WHERE pr.poll_id = :'poll_id'::bigint
      AND pr.user_id = cm.user_id
  )
ORDER BY cm.id ASC;
