SELECT pg.id AS group_id,
       pg.sort_order,
       pgm.id AS group_member_id,
       pgm.user_id,
       u.name,
       ps.id AS submission_id,
       ps.version,
       ps.submitted_at
FROM prayer_groups pg
JOIN prayer_group_members pgm ON pgm.group_id = pg.id AND pgm.is_active = TRUE
JOIN users u ON u.id = pgm.user_id
LEFT JOIN prayer_submissions ps
  ON ps.prayer_week_id = :'prayer_week_id'::bigint
 AND ps.group_id = pg.id
 AND ps.user_id = pgm.user_id
WHERE pg.season_id = :'prayer_season_id'::bigint
  AND pg.is_active = TRUE
ORDER BY pg.sort_order ASC, pg.id ASC, pgm.id ASC;
