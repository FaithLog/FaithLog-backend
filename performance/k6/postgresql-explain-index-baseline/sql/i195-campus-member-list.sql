SELECT cm.id AS membership_id,
       cm.user_id,
       cm.campus_role,
       cm.status,
       u.name,
       u.email
FROM campus_members cm
JOIN users u ON u.id = cm.user_id
WHERE cm.campus_id = :'campus_id'::bigint
  AND cm.status = 'ACTIVE'
ORDER BY cm.id ASC;
