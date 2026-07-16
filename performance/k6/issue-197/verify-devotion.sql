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
measured_daily_by_user AS (
    SELECT weekly.user_id, count(daily.id) AS daily_count
    FROM measured_weekly weekly
    LEFT JOIN devotion_daily_checks daily ON daily.weekly_record_id = weekly.id
    GROUP BY weekly.user_id
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
    'warmup', json_build_object(
        'weeklyCount', (SELECT count(*) FROM warmup_weekly),
        'submittedCount', (SELECT count(*) FROM warmup_weekly WHERE submitted_at IS NOT NULL)
    ),
    'measured', json_build_object(
        'expectedUserCount', :expected_measured_user_count,
        'weeklyCount', (SELECT count(*) FROM measured_weekly),
        'submittedCount', (SELECT count(*) FROM measured_weekly WHERE submitted_at IS NOT NULL),
        'dailyCount', (SELECT coalesce(sum(daily_count), 0) FROM measured_daily_by_user),
        'usersWithSevenDaily', (SELECT count(*) FROM measured_daily_by_user WHERE daily_count = 7),
        'chargeCount', (SELECT count(*) FROM measured_charges),
        'correctChargeAmountCount', (
            SELECT count(*) FROM measured_charges WHERE amount = :expected_penalty_amount
        ),
        'distinctChargeSourceCount', (
            SELECT count(DISTINCT (user_id, source_id)) FROM measured_charges
        ),
        'duplicateChargeSourceGroups', (SELECT count(*) FROM measured_duplicate_sources)
    ),
    'rollback', json_build_object(
        'weeklyCount', (SELECT count(*) FROM rollback_weekly),
        'dailyCount', (SELECT count(*) FROM rollback_daily),
        'chargeCount', (SELECT count(*) FROM rollback_charges)
    )
);
