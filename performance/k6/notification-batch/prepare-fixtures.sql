\set ON_ERROR_STOP on

BEGIN;

CREATE TEMP TABLE perf_198_config AS
SELECT
	:'dataset_id'::text AS dataset_id,
	:'fixture_run_id'::text AS fixture_run_id,
	:'sample_kind'::text AS sample_kind,
	:'compose_project'::text AS compose_project,
	:'postgres_database'::text AS postgres_database,
	:'campus_id'::bigint AS campus_id,
	:'member_count'::integer AS member_count,
	:'success_count'::integer AS success_count,
	:'transient_count'::integer AS transient_count,
	:'permanent_count'::integer AS permanent_count,
	:'inactive_count'::integer AS inactive_count,
	:'no_token_count'::integer AS no_token_count;

SELECT 1 / CASE WHEN (
	SELECT member_count = 1000
		AND success_count > 0
		AND transient_count > 0
		AND permanent_count > 0
		AND inactive_count > 0
		AND no_token_count > 0
		AND success_count + transient_count + permanent_count + inactive_count + no_token_count = 1000
	FROM perf_198_config
) THEN 1 ELSE 0 END AS exact_workload_contract_guard;

CREATE TEMP TABLE perf_198_members AS
SELECT
	member.user_id,
	row_number() OVER (ORDER BY member.id)::integer AS member_order
FROM campus_members member
JOIN users target_user ON target_user.id = member.user_id
JOIN campuses campus ON campus.id = member.campus_id
JOIN perf_198_config config ON config.campus_id = member.campus_id
WHERE member.status = 'ACTIVE'
	AND target_user.is_active = TRUE
	AND campus.name IN (config.dataset_id, config.dataset_id || ' Campus');

SELECT 1 / CASE WHEN (SELECT count(*) FROM perf_198_members) = (SELECT member_count FROM perf_198_config)
	THEN 1 ELSE 0 END AS exact_member_count_guard;
SELECT 1 / CASE WHEN NOT EXISTS (
	SELECT 1
	FROM user_fcm_tokens token
	JOIN perf_198_members member ON member.user_id = token.user_id
	WHERE token.is_active = TRUE
		AND token.token NOT LIKE 'PERFORMANCE_198_DUMMY:%'
) THEN 1 ELSE 0 END AS existing_active_token_guard;
SELECT 1 / CASE WHEN NOT EXISTS (
	SELECT 1
	FROM user_fcm_tokens token
	JOIN perf_198_config config
		ON token.client_instance_id LIKE 'PERFORMANCE_198_DUMMY:' || config.fixture_run_id || ':%'
) THEN 1 ELSE 0 END AS fresh_fixture_run_guard;

UPDATE user_fcm_tokens token
SET
	is_active = FALSE,
	deactivated_at = COALESCE(token.deactivated_at, CURRENT_TIMESTAMP),
	updated_at = CURRENT_TIMESTAMP
FROM perf_198_members member
WHERE token.user_id = member.user_id
	AND token.is_active = TRUE
	AND token.token LIKE 'PERFORMANCE_198_DUMMY:%';

WITH categorized AS (
	SELECT
		member.user_id,
		member.member_order,
		config.fixture_run_id,
		config.success_count,
		config.transient_count,
		config.permanent_count,
		config.inactive_count,
		CASE
			WHEN member.member_order <= config.permanent_count THEN 'permanent'
			WHEN member.member_order <= config.permanent_count + config.transient_count THEN 'transient'
			WHEN member.member_order <= config.permanent_count + config.transient_count + config.success_count
				THEN 'success'
			WHEN member.member_order <= config.permanent_count + config.transient_count
				+ config.success_count + config.inactive_count THEN 'inactive'
			ELSE 'no-token'
		END AS outcome
	FROM perf_198_members member
	CROSS JOIN perf_198_config config
)
INSERT INTO user_fcm_tokens (
	user_id,
	token,
	client_instance_id,
	device_type,
	app_version,
	is_active,
	last_seen_at,
	last_refreshed_at,
	last_failure_reason,
	deactivated_at,
	created_at,
	updated_at
)
SELECT
	user_id,
	'PERFORMANCE_198_DUMMY:' || fixture_run_id || ':' || outcome || ':' || user_id,
	'PERFORMANCE_198_DUMMY:' || fixture_run_id || ':' || member_order,
	'IOS',
	'issue-198-test-only',
	(outcome <> 'inactive'),
	CURRENT_TIMESTAMP,
	CURRENT_TIMESTAMP,
	NULL,
	CASE WHEN outcome = 'inactive' THEN CURRENT_TIMESTAMP ELSE NULL END,
	CURRENT_TIMESTAMP,
	CURRENT_TIMESTAMP
FROM categorized
WHERE outcome <> 'no-token';

SELECT json_build_object(
	'datasetId', config.dataset_id,
	'fixtureRunId', config.fixture_run_id,
	'sampleKind', config.sample_kind,
	'composeProject', config.compose_project,
	'postgresDatabase', config.postgres_database,
	'campusId', config.campus_id,
	'memberCount', config.member_count,
	'successCount', config.success_count,
	'transientCount', config.transient_count,
	'permanentCount', config.permanent_count,
	'inactiveCount', config.inactive_count,
	'noTokenCount', config.no_token_count,
	'insertedDummyTokenCount', (
		SELECT count(*)
		FROM user_fcm_tokens token
		WHERE token.client_instance_id LIKE 'PERFORMANCE_198_DUMMY:' || config.fixture_run_id || ':%'
	),
	'fixturePolicy', 'dummy-token-and-generated-log-only',
	'credentialRecorded', false
)
FROM perf_198_config config;

COMMIT;
