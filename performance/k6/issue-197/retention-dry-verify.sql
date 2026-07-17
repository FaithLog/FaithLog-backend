BEGIN TRANSACTION READ ONLY;

WITH fixture_campuses AS (
    SELECT id
    FROM campuses
    WHERE left(name, length(:'dataset_prefix')) = :'dataset_prefix'
),
non_fixture_campuses AS (
    SELECT id
    FROM campuses
    WHERE left(name, length(:'dataset_prefix')) <> :'dataset_prefix'
),
expired_polls AS (
    SELECT poll.id
    FROM polls poll
    JOIN fixture_campuses campus ON campus.id = poll.campus_id
    WHERE poll.ends_at < :'reference_instant'::TIMESTAMPTZ - INTERVAL '30 days'
),
expired_poll_responses AS (
    SELECT response.id
    FROM poll_responses response
    JOIN expired_polls poll ON poll.id = response.poll_id
),
remaining_soft_comments AS (
    SELECT comment.id
    FROM poll_comments comment
    JOIN polls poll ON poll.id = comment.poll_id
    JOIN fixture_campuses campus ON campus.id = poll.campus_id
    WHERE comment.is_deleted = TRUE
      AND comment.deleted_at < :'reference_instant'::TIMESTAMPTZ - INTERVAL '30 days'
      AND NOT EXISTS (SELECT 1 FROM expired_polls expired WHERE expired.id = comment.poll_id)
),
previous_year AS (
    SELECT
        make_date(extract(year FROM :'reference_instant'::TIMESTAMPTZ AT TIME ZONE 'Asia/Seoul')::INTEGER - 1, 1, 1) AS start_date,
        make_date(extract(year FROM :'reference_instant'::TIMESTAMPTZ AT TIME ZONE 'Asia/Seoul')::INTEGER, 1, 1) AS end_exclusive_date
),
fixture_weekly AS (
    SELECT weekly.id
    FROM weekly_devotion_records weekly
    JOIN fixture_campuses campus ON campus.id = weekly.campus_id
    CROSS JOIN previous_year annual
    WHERE weekly.week_start_date >= annual.start_date
      AND weekly.week_start_date < annual.end_exclusive_date
),
annual_fk_blockers AS (
    SELECT daily.id
    FROM devotion_daily_checks daily
    JOIN fixture_weekly weekly ON weekly.id = daily.weekly_record_id
    CROSS JOIN previous_year annual
    WHERE daily.record_date < annual.start_date
       OR daily.record_date >= annual.end_exclusive_date
)
SELECT json_build_object(
    'datasetId', :'dataset_id',
    'fixtureRunId', :'fixture_run_id',
    'datasetPrefix', :'dataset_prefix',
    'referenceInstant', :'reference_instant',
    'annualForeignKeyBlockers', (SELECT count(*) FROM annual_fk_blockers),
    'outsideFixtureCandidateRoots', (
        SELECT
            (SELECT count(*) FROM notification_logs log JOIN non_fixture_campuses campus ON campus.id = log.campus_id
             WHERE log.created_at < :'reference_instant'::TIMESTAMPTZ - INTERVAL '14 days')
          + (SELECT count(*) FROM polls poll JOIN non_fixture_campuses campus ON campus.id = poll.campus_id
             WHERE poll.ends_at < :'reference_instant'::TIMESTAMPTZ - INTERVAL '30 days')
          + (SELECT count(*) FROM poll_comments comment JOIN polls poll ON poll.id = comment.poll_id
             JOIN non_fixture_campuses campus ON campus.id = poll.campus_id
             WHERE comment.is_deleted = TRUE
               AND comment.deleted_at < :'reference_instant'::TIMESTAMPTZ - INTERVAL '30 days')
          + (SELECT count(*) FROM prayer_submissions submission
             JOIN prayer_weeks prayer_week ON prayer_week.id = submission.prayer_week_id
             JOIN non_fixture_campuses campus ON campus.id = prayer_week.campus_id
             WHERE submission.created_at < :'reference_instant'::TIMESTAMPTZ - INTERVAL '1 year')
          + (SELECT count(*) FROM weekly_devotion_records weekly
             JOIN non_fixture_campuses campus ON campus.id = weekly.campus_id CROSS JOIN previous_year annual
             WHERE weekly.week_start_date >= annual.start_date AND weekly.week_start_date < annual.end_exclusive_date)
          + (SELECT count(*) FROM devotion_daily_checks daily
             JOIN weekly_devotion_records weekly ON weekly.id = daily.weekly_record_id
             JOIN non_fixture_campuses campus ON campus.id = weekly.campus_id CROSS JOIN previous_year annual
             WHERE daily.record_date >= annual.start_date AND daily.record_date < annual.end_exclusive_date)
          + (SELECT count(*) FROM charge_items charge
             JOIN non_fixture_campuses campus ON campus.id = charge.campus_id CROSS JOIN previous_year annual
             WHERE charge.status IN ('PAID', 'WAIVED', 'CANCELED')
               AND charge.created_at >= annual.start_date AT TIME ZONE 'Asia/Seoul'
               AND charge.created_at < annual.end_exclusive_date AT TIME ZONE 'Asia/Seoul')
    ),
    'actualCandidateCounts', json_build_object(
        'notificationLogs', (
            SELECT count(*)
            FROM notification_logs log
            JOIN fixture_campuses campus ON campus.id = log.campus_id
            WHERE log.created_at < :'reference_instant'::TIMESTAMPTZ - INTERVAL '14 days'
        ),
        'pollResponseOptions', (
            SELECT count(*)
            FROM poll_response_options selected
            JOIN expired_poll_responses response ON response.id = selected.response_id
        ),
        'pollResponses', (SELECT count(*) FROM expired_poll_responses),
        'pollComments', (
            SELECT count(*) FROM poll_comments comment JOIN expired_polls poll ON poll.id = comment.poll_id
        ),
        'pollOptions', (
            SELECT count(*) FROM poll_options option_row JOIN expired_polls poll ON poll.id = option_row.poll_id
        ),
        'polls', (SELECT count(*) FROM expired_polls),
        'softDeletedPollComments', (SELECT count(*) FROM remaining_soft_comments),
        'prayerSubmissions', (
            SELECT count(*)
            FROM prayer_submissions submission
            JOIN prayer_weeks prayer_week ON prayer_week.id = submission.prayer_week_id
            JOIN fixture_campuses campus ON campus.id = prayer_week.campus_id
            WHERE submission.created_at < :'reference_instant'::TIMESTAMPTZ - INTERVAL '1 year'
        ),
        'devotionDailyChecks', (
            SELECT count(*)
            FROM devotion_daily_checks daily
            JOIN weekly_devotion_records weekly ON weekly.id = daily.weekly_record_id
            JOIN fixture_campuses campus ON campus.id = weekly.campus_id
            CROSS JOIN previous_year annual
            WHERE daily.record_date >= annual.start_date
              AND daily.record_date < annual.end_exclusive_date
        ),
        'weeklyDevotionRecords', (SELECT count(*) FROM fixture_weekly),
        'chargeItems', (
            SELECT count(*)
            FROM charge_items charge
            JOIN fixture_campuses campus ON campus.id = charge.campus_id
            CROSS JOIN previous_year annual
            WHERE charge.status IN ('PAID', 'WAIVED', 'CANCELED')
              AND charge.created_at >= annual.start_date AT TIME ZONE 'Asia/Seoul'
              AND charge.created_at < annual.end_exclusive_date AT TIME ZONE 'Asia/Seoul'
        )
    )
);

COMMIT;
