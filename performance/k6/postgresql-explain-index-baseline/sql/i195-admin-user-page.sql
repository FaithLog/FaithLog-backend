WITH paged_users AS (
  SELECT u.id,
         u.name,
         u.email,
         u.role,
         u.is_active,
         u.created_at
  FROM users u
  WHERE (
    lower(u.name) LIKE lower(:'keyword_pattern')
    OR lower(u.email) LIKE lower(:'keyword_pattern')
  )
  ORDER BY u.created_at DESC, u.id DESC
  LIMIT :'page_size'::integer
  OFFSET :'page_offset'::integer
)
SELECT pu.id,
       pu.name,
       pu.email,
       pu.role,
       pu.is_active,
       cm.id AS membership_id,
       cm.campus_id,
       c.name AS campus_name,
       cm.campus_role,
       cm.status AS membership_status
FROM paged_users pu
LEFT JOIN campus_members cm ON cm.user_id = pu.id
LEFT JOIN campuses c ON c.id = cm.campus_id
ORDER BY pu.created_at DESC, pu.id DESC, cm.id ASC;
