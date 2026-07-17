WITH namespace_counts AS (
    SELECT
        (
            SELECT COUNT(*)::integer
            FROM campuses campus
            WHERE campus.name IN (:'success_campus_name', :'rollback_campus_name')
        ) AS existing_campus_count,
        (
            SELECT COUNT(*)::integer
            FROM users fixture_user
            WHERE LEFT(fixture_user.email, LENGTH(:'email_prefix')) = :'email_prefix'
        ) AS existing_user_count
)
SELECT json_build_object(
    'existingCampusCount', existing_campus_count,
    'existingUserCount', existing_user_count
)::text
FROM namespace_counts;
