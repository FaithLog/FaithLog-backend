SELECT pg.id AS group_id,
       pg.name,
       pg.sort_order,
       pgm.id AS group_member_id,
       pgm.user_id,
       u.name AS member_name,
       u.email AS member_email
FROM prayer_groups pg
LEFT JOIN prayer_group_members pgm ON pgm.group_id = pg.id AND pgm.is_active = TRUE
LEFT JOIN users u ON u.id = pgm.user_id
WHERE pg.season_id = :'prayer_season_id'::bigint
  AND pg.is_active = TRUE
ORDER BY pg.sort_order ASC, pg.id ASC, pgm.id ASC;
