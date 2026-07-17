SELECT cm.id AS membership_id,
       cm.user_id,
       u.name,
       u.email,
       assigned.group_id AS assigned_group_id,
       assigned.group_name AS assigned_group_name,
       (assigned.group_id IS NULL) AS assignable
FROM campus_members cm
JOIN users u ON u.id = cm.user_id
LEFT JOIN LATERAL (
  SELECT pgm.user_id,
         pg.id AS group_id,
         pg.name AS group_name
  FROM prayer_group_members pgm
  JOIN prayer_groups pg ON pg.id = pgm.group_id
  WHERE pgm.is_active = TRUE
    AND pg.season_id = :'prayer_season_id'::bigint
    AND pg.is_active = TRUE
    AND pgm.user_id = cm.user_id
  ORDER BY pgm.id ASC
  LIMIT 1
) assigned ON TRUE
WHERE cm.campus_id = :'campus_id'::bigint
  AND cm.status = 'ACTIVE'
ORDER BY cm.id ASC;
