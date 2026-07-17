\set ON_ERROR_STOP on

BEGIN;

WITH inserted_campus AS (
	INSERT INTO campuses (name, region, description, invite_code, is_active, created_at, updated_at)
	VALUES (
		:'dataset_id', 'PERFORMANCE_ONLY', 'Issue #198 deterministic synthetic dataset',
		'PERFORMANCE_198_SYNTHETIC_ONLY', TRUE,
		TIMESTAMPTZ '2026-01-01 00:00:00+00', TIMESTAMPTZ '2026-01-01 00:00:00+00'
	)
	RETURNING id
), inserted_users AS (
	INSERT INTO users (name, email, password_hash, role, is_active, token_version, created_at, updated_at)
	SELECT
		'PERFORMANCE #198 USER ' || lpad(series::text, 4, '0'),
		'performance_198_' || lpad(series::text, 4, '0') || '@invalid.local',
		'PERFORMANCE_198_DISABLED_LOGIN', 'USER', TRUE, 0,
		TIMESTAMPTZ '2026-01-01 00:00:00+00', TIMESTAMPTZ '2026-01-01 00:00:00+00'
	FROM generate_series(1, 1000) AS series
	RETURNING id
)
INSERT INTO campus_members (campus_id, user_id, campus_role, status, joined_at, created_at, updated_at)
SELECT
	campus.id, target.id, 'MEMBER', 'ACTIVE',
	TIMESTAMPTZ '2026-01-01 00:00:00+00', TIMESTAMPTZ '2026-01-01 00:00:00+00',
	TIMESTAMPTZ '2026-01-01 00:00:00+00'
FROM inserted_campus campus
CROSS JOIN inserted_users target;

SELECT json_build_object(
	'composeProject', :'compose_project',
	'postgresDatabase', :'postgres_database',
	'datasetId', :'dataset_id',
	'campusId', campus.id,
	'activeUserCount', (SELECT count(*) FROM users WHERE is_active = TRUE),
	'activeMemberCount', (
		SELECT count(*) FROM campus_members member
		WHERE member.campus_id = campus.id AND member.status = 'ACTIVE'
	),
	'userStateMd5', (
		SELECT md5(string_agg(id::text || ':' || email || ':' || is_active::text, ',' ORDER BY id)) FROM users
	),
	'memberStateMd5', (
		SELECT md5(string_agg(id::text || ':' || campus_id::text || ':' || user_id::text || ':' || status, ',' ORDER BY id))
		FROM campus_members
	),
	'fcmTokenCount', (SELECT count(*) FROM user_fcm_tokens),
	'notificationLogCount', (SELECT count(*) FROM notification_logs)
)
FROM campuses campus
WHERE campus.name = :'dataset_id';

COMMIT;
