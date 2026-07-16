WITH measured_users AS (
    SELECT unnest(string_to_array(:'measured_user_ids', ',')::BIGINT[]) AS user_id
),
warmup_users AS (
    SELECT unnest(string_to_array(:'warmup_user_ids', ',')::BIGINT[]) AS user_id
),
rollback_users AS (
    SELECT unnest(string_to_array(:'rollback_user_ids', ',')::BIGINT[]) AS user_id
),
measured_weekly AS (
    SELECT weekly.*
    FROM weekly_devotion_records weekly
    JOIN measured_users fixture_user ON fixture_user.user_id = weekly.user_id
    WHERE weekly.campus_id = :campus_id
      AND weekly.week_start_date = :'measured_week_start_date'::DATE
),
warmup_weekly AS (
    SELECT weekly.*
    FROM weekly_devotion_records weekly
    JOIN warmup_users fixture_user ON fixture_user.user_id = weekly.user_id
    WHERE weekly.campus_id = :campus_id
      AND weekly.week_start_date = :'warmup_week_start_date'::DATE
),
warmup_charges AS (
    SELECT charge.*
    FROM charge_items charge
    JOIN warmup_weekly weekly ON weekly.id = charge.source_id AND weekly.user_id = charge.user_id
    WHERE charge.campus_id = :campus_id
      AND charge.payment_category = 'PENALTY'
      AND charge.source_type = 'DEVOTION_RECORD'
),
measured_daily AS (
    SELECT daily.*, weekly.user_id AS fixture_user_id
    FROM measured_weekly weekly
    JOIN devotion_daily_checks daily ON daily.weekly_record_id = weekly.id
),
measured_daily_by_user AS (
    SELECT fixture_user_id AS user_id, count(*) AS daily_count
    FROM measured_daily
    GROUP BY fixture_user_id
),
measured_charges AS (
    SELECT charge.*
    FROM charge_items charge
    JOIN measured_weekly weekly ON weekly.id = charge.source_id AND weekly.user_id = charge.user_id
    WHERE charge.campus_id = :campus_id
      AND charge.payment_category = 'PENALTY'
      AND charge.source_type = 'DEVOTION_RECORD'
),
measured_duplicate_sources AS (
    SELECT charge.user_id, charge.source_id
    FROM measured_charges charge
    GROUP BY charge.user_id, charge.source_id
    HAVING count(*) > 1
),
success_campus_devotion_charges AS (
    SELECT charge.*
    FROM charge_items charge
    WHERE charge.campus_id = :campus_id
      AND charge.payment_category = 'PENALTY'
      AND charge.source_type = 'DEVOTION_RECORD'
),
rollback_weekly AS (
    SELECT weekly.*
    FROM weekly_devotion_records weekly
    JOIN rollback_users fixture_user ON fixture_user.user_id = weekly.user_id
    WHERE weekly.campus_id = :rollback_campus_id
      AND weekly.week_start_date = :'rollback_week_start_date'::DATE
),
rollback_daily AS (
    SELECT daily.*
    FROM devotion_daily_checks daily
    JOIN rollback_weekly weekly ON weekly.id = daily.weekly_record_id
),
rollback_charges AS (
    SELECT charge.*
    FROM charge_items charge
    JOIN rollback_users fixture_user ON fixture_user.user_id = charge.user_id
    WHERE charge.campus_id = :rollback_campus_id
      AND charge.payment_category = 'PENALTY'
      AND charge.source_type = 'DEVOTION_RECORD'
)
SELECT json_build_object(
    'datasetId', :'dataset_id',
    'fixtureRunId', :'fixture_run_id',
    'successCampusDevotionChargeCount', (SELECT count(*) FROM success_campus_devotion_charges),
    'warmup', json_build_object(
        'weeklyCount', (SELECT count(*) FROM warmup_weekly),
        'submittedCount', (SELECT count(*) FROM warmup_weekly WHERE submitted_at IS NOT NULL),
        'chargeCount', (SELECT count(*) FROM warmup_charges)
    ),
    'measured', json_build_object(
        'expectedUserCount', :expected_measured_user_count,
        'weeklyCount', (SELECT count(*) FROM measured_weekly),
        'distinctWeeklyUsers', (SELECT count(DISTINCT user_id) FROM measured_weekly),
        'submittedCount', (SELECT count(*) FROM measured_weekly WHERE submitted_at IS NOT NULL),
        'dailyCount', (SELECT coalesce(sum(daily_count), 0) FROM measured_daily_by_user),
        'distinctDailyUsers', (SELECT count(DISTINCT fixture_user_id) FROM measured_daily),
        'usersWithSevenDaily', (SELECT count(*) FROM measured_daily_by_user WHERE daily_count = 7),
        'correctDailyDateCount', (
            SELECT count(*)
            FROM measured_daily
            WHERE record_date BETWEEN :'measured_week_start_date'::DATE
                                  AND :'measured_week_start_date'::DATE + 6
        ),
        'chargeCount', (SELECT count(*) FROM measured_charges),
        'distinctChargeUsers', (SELECT count(DISTINCT user_id) FROM measured_charges),
        'correctChargeAmountCount', (
            SELECT count(*) FROM measured_charges WHERE amount = :expected_penalty_amount
        ),
        'distinctChargeSourceCount', (
            SELECT count(DISTINCT (user_id, source_id)) FROM measured_charges
        ),
        'correctChargeBindingCount', (SELECT count(*) FROM measured_charges),
        'chargeAmountSum', (SELECT coalesce(sum(amount), 0) FROM measured_charges),
        'duplicateChargeSourceGroups', (SELECT count(*) FROM measured_duplicate_sources)
    ),
    'rollback', json_build_object(
        'weeklyCount', (SELECT count(*) FROM rollback_weekly),
        'dailyCount', (SELECT count(*) FROM rollback_daily),
        'chargeCount', (SELECT count(*) FROM rollback_charges)
    )
);
