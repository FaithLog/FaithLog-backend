SELECT cda.id,
       cda.user_id,
       cda.duty_type,
       cda.assigned_at,
       u.name,
       u.email
FROM campus_duty_assignments cda
JOIN users u ON u.id = cda.user_id
WHERE cda.campus_id = :'campus_id'::bigint
  AND cda.is_active = TRUE
ORDER BY cda.id ASC;
