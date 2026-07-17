SELECT wdr.id AS weekly_record_id,
       wdr.submitted_at,
       ddc.id AS daily_check_id,
       ddc.record_date,
       ddc.quiet_time_checked,
       ddc.prayer_checked,
       ddc.bible_reading_checked
FROM weekly_devotion_records wdr
LEFT JOIN devotion_daily_checks ddc ON ddc.weekly_record_id = wdr.id
WHERE wdr.campus_id = :'campus_id'::bigint
  AND wdr.user_id = :'member_user_id'::bigint
  AND wdr.week_start_date = :'week_start_date'::date
ORDER BY ddc.record_date ASC;
