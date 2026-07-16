WITH warmup_users AS (
    SELECT unnest(string_to_array(:'warmup_user_ids', ',')::BIGINT[]) AS user_id
), measured_users AS (
    SELECT unnest(string_to_array(:'measured_user_ids', ',')::BIGINT[]) AS user_id
), rollback_users AS (
    SELECT unnest(string_to_array(:'rollback_user_ids', ',')::BIGINT[]) AS user_id
), success_users AS (
    SELECT user_id FROM warmup_users
    UNION ALL
    SELECT user_id FROM measured_users
), fixture_scope AS (
    SELECT user_id, :'campus_id'::BIGINT AS campus_id, :'warmup_week_start_date'::DATE AS week_start_date
    FROM warmup_users
    UNION ALL
    SELECT user_id, :'campus_id'::BIGINT, :'measured_week_start_date'::DATE
    FROM measured_users
    UNION ALL
    SELECT user_id, :'rollback_campus_id'::BIGINT, :'rollback_week_start_date'::DATE
    FROM rollback_users
), existing_weekly AS (
    SELECT weekly.id
    FROM weekly_devotion_records weekly
    JOIN fixture_scope fixture
      ON fixture.user_id = weekly.user_id
     AND fixture.campus_id = weekly.campus_id
     AND fixture.week_start_date = weekly.week_start_date
), active_rules AS (
    SELECT *
    FROM penalty_rules
    WHERE campus_id = :'campus_id'::BIGINT
      AND is_active = TRUE
), calculated_penalty AS (
    SELECT COALESCE(sum(
        CASE calculation_type
            WHEN 'MISSING_COUNT' THEN greatest(required_count - 4, 0)::BIGINT * amount_per_unit::BIGINT
            WHEN 'LATE_MINUTE' THEN base_amount::BIGINT + 5::BIGINT * amount_per_unit::BIGINT
            ELSE 0
        END
    ), 0) AS amount
    FROM active_rules
)
SELECT json_build_object(
    'distinctFixtureUsers', (SELECT count(DISTINCT user_id) FROM fixture_scope),
    'activeFixtureUsers', (
        SELECT count(*) FROM users app_user
        JOIN (SELECT DISTINCT user_id FROM fixture_scope) fixture ON fixture.user_id = app_user.id
        WHERE app_user.is_active = TRUE
    ),
    'activeCampuses', (
        SELECT count(*) FROM campuses
        WHERE id IN (:'campus_id'::BIGINT, :'rollback_campus_id'::BIGINT)
          AND is_active = TRUE
    ),
    'successActiveMembers', (
        SELECT count(*) FROM campus_members member
        JOIN success_users fixture ON fixture.user_id = member.user_id
        WHERE member.campus_id = :'campus_id'::BIGINT AND member.status = 'ACTIVE'
    ),
    'rollbackActiveMembers', (
        SELECT count(*) FROM campus_members member
        JOIN rollback_users fixture ON fixture.user_id = member.user_id
        WHERE member.campus_id = :'rollback_campus_id'::BIGINT AND member.status = 'ACTIVE'
    ),
    'successUsersInRollbackCampus', (
        SELECT count(*) FROM campus_members member
        JOIN success_users fixture ON fixture.user_id = member.user_id
        WHERE member.campus_id = :'rollback_campus_id'::BIGINT AND member.status = 'ACTIVE'
    ),
    'rollbackUsersInSuccessCampus', (
        SELECT count(*) FROM campus_members member
        JOIN rollback_users fixture ON fixture.user_id = member.user_id
        WHERE member.campus_id = :'campus_id'::BIGINT AND member.status = 'ACTIVE'
    ),
    'successActivePenaltyAccounts', (
        SELECT count(*) FROM payment_accounts
        WHERE campus_id = :'campus_id'::BIGINT AND account_type = 'PENALTY'
          AND is_active = TRUE AND deleted_at IS NULL
    ),
    'rollbackActivePenaltyAccounts', (
        SELECT count(*) FROM payment_accounts
        WHERE campus_id = :'rollback_campus_id'::BIGINT AND account_type = 'PENALTY'
          AND is_active = TRUE AND deleted_at IS NULL
    ),
    'existingWeeklyCount', (SELECT count(*) FROM existing_weekly),
    'existingDailyCount', (
        SELECT count(*) FROM devotion_daily_checks daily
        JOIN existing_weekly weekly ON weekly.id = daily.weekly_record_id
    ),
    'existingDevotionCharges', (
        SELECT count(*) FROM charge_items charge
        JOIN fixture_scope fixture ON fixture.user_id = charge.user_id AND fixture.campus_id = charge.campus_id
        WHERE charge.payment_category = 'PENALTY' AND charge.source_type = 'DEVOTION_RECORD'
    ),
    'activePenaltyRuleCount', (SELECT count(*) FROM active_rules),
    'invalidActivePenaltyRulePairs', (
        SELECT count(*) FROM active_rules
        WHERE (rule_type IN ('QUIET_TIME', 'PRAYER', 'BIBLE_READING') AND calculation_type <> 'MISSING_COUNT')
           OR (rule_type = 'SATURDAY_LATE' AND calculation_type <> 'LATE_MINUTE')
    ),
    'calculatedPenaltyAmount', (SELECT amount FROM calculated_penalty)
);
