WITH paged_campuses AS (
  SELECT c.id,
         c.name,
         c.region,
         c.is_active,
         c.created_at
  FROM campuses c
  ORDER BY c.created_at DESC, c.id DESC
  LIMIT :'page_size'::integer
  OFFSET :'page_offset'::integer
)
SELECT pc.id,
       pc.name,
       pc.region,
       pc.is_active,
       pc.created_at,
       count(cm.id) FILTER (WHERE cm.status = 'ACTIVE') AS active_member_count,
       count(cm.id) FILTER (
         WHERE cm.status = 'ACTIVE'
           AND cm.campus_role IN ('MINISTER', 'ELDER', 'CAMPUS_LEADER')
       ) AS active_admin_count
FROM paged_campuses pc
LEFT JOIN campus_members cm ON cm.campus_id = pc.id
GROUP BY pc.id, pc.name, pc.region, pc.is_active, pc.created_at
ORDER BY pc.created_at DESC, pc.id DESC;
