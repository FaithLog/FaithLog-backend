#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${ROOT_DIR}/../../.." && pwd)"
VALIDATE_TARGET="${ROOT_DIR}/validate-published-target.mjs"
DB_RUNTIME_IDENTITY_SQL="${ROOT_DIR}/db-runtime-identity.sql"
VALIDATE_RUNTIME_IDENTITY="${ROOT_DIR}/validate-runtime-identity.mjs"
PARSE_REDIS_IDENTITY="${ROOT_DIR}/redis-runtime-identity.mjs"
TOOLING_PROVENANCE="${ROOT_DIR}/tooling-provenance.mjs"
PERF_SCENARIO_WORKTREE="${PERF_SCENARIO_WORKTREE:?PERF_SCENARIO_WORKTREE is required at runtime}"
EXPECTED_SCENARIO_HEAD="${EXPECTED_SCENARIO_HEAD:?EXPECTED_SCENARIO_HEAD is required at runtime}"
FIXTURE_RUN_ID="${FIXTURE_RUN_ID:?FIXTURE_RUN_ID is required}"
FIXTURE_MANIFEST="${FIXTURE_MANIFEST:-${REPO_ROOT}/build/reports/k6/issue-196/${FIXTURE_RUN_ID}/fixture-manifest.json}"
BASE_URL="${BASE_URL:?BASE_URL is required at runtime}"
DB_CONTAINER="${DB_CONTAINER:?DB_CONTAINER is required at runtime}"
APP_CONTAINER="${APP_CONTAINER:?APP_CONTAINER is required at runtime}"
REDIS_CONTAINER="${REDIS_CONTAINER:?REDIS_CONTAINER is required at runtime}"
EXPECTED_APP_SERVICE="${EXPECTED_APP_SERVICE:?EXPECTED_APP_SERVICE is required at runtime}"
EXPECTED_DB_SERVICE="${EXPECTED_DB_SERVICE:?EXPECTED_DB_SERVICE is required at runtime}"
EXPECTED_REDIS_SERVICE="${EXPECTED_REDIS_SERVICE:?EXPECTED_REDIS_SERVICE is required at runtime}"
EXPECTED_APP_IMAGE="${EXPECTED_APP_IMAGE:?EXPECTED_APP_IMAGE is required at runtime}"
EXPECTED_APP_IMAGE_ID="${EXPECTED_APP_IMAGE_ID:?EXPECTED_APP_IMAGE_ID is required at runtime}"
EXPECTED_DB_IMAGE="${EXPECTED_DB_IMAGE:?EXPECTED_DB_IMAGE is required at runtime}"
EXPECTED_DB_IMAGE_ID="${EXPECTED_DB_IMAGE_ID:?EXPECTED_DB_IMAGE_ID is required at runtime}"
EXPECTED_REDIS_IMAGE="${EXPECTED_REDIS_IMAGE:?EXPECTED_REDIS_IMAGE is required at runtime}"
EXPECTED_REDIS_IMAGE_ID="${EXPECTED_REDIS_IMAGE_ID:?EXPECTED_REDIS_IMAGE_ID is required at runtime}"
EXPECTED_REDIS_PORT="${EXPECTED_REDIS_PORT:?EXPECTED_REDIS_PORT is required at runtime}"
EXPECTED_FLYWAY_VERSION="${EXPECTED_FLYWAY_VERSION:?EXPECTED_FLYWAY_VERSION is required at runtime}"
EXPECTED_SOURCE_REVISION="${EXPECTED_SOURCE_REVISION:?EXPECTED_SOURCE_REVISION is required at runtime}"
PERF_DB_USER="${PERF_DB_USER:?PERF_DB_USER is required at runtime}"
PERF_DB_NAME="${PERF_DB_NAME:?PERF_DB_NAME is required at runtime}"
PERF_DB_PASSWORD="${PERF_DB_PASSWORD:?PERF_DB_PASSWORD is required at runtime}"

export -n PERF_DB_USER PERF_DB_NAME PERF_DB_PASSWORD
unset PERF_ACCESS_TOKEN PERF_ADMIN_ACCESS_TOKEN PERF_MEMBER_ACCESS_TOKEN \
	PERF_COFFEE_CREATOR_ACCESS_TOKEN PERF_OTHER_COFFEE_DUTY_ACCESS_TOKEN PERF_MEAL_DUTY_ACCESS_TOKEN

if [[ ! "${FIXTURE_RUN_ID}" =~ ^[a-z0-9][a-z0-9_-]{7,31}$ ]]; then
	echo "Invalid FIXTURE_RUN_ID." >&2
	exit 1
fi

if [[ ! -f "${FIXTURE_MANIFEST}" ]]; then
	echo "Fixture manifest not found: ${FIXTURE_MANIFEST}" >&2
	exit 1
fi
node "${TOOLING_PROVENANCE}" --assert-manifest "${PERF_SCENARIO_WORKTREE}" "${EXPECTED_SCENARIO_HEAD}" "${FIXTURE_MANIFEST}"

json_value() {
	node -e '
		const fs = require("node:fs");
		const value = process.argv[2].split(".").reduce((current, key) => current[key], JSON.parse(fs.readFileSync(process.argv[1], "utf8")));
		process.stdout.write(String(value));
	' "${FIXTURE_MANIFEST}" "$1"
}

manifest_run_id="$(json_value fixtureRunId)"
dataset_id="$(json_value datasetId)"
shaped_at="$(json_value shapedAt)"
fixture_run_id="${FIXTURE_RUN_ID}"
if [[ "${manifest_run_id}" != "${fixture_run_id}" ]]; then
	echo "Manifest fixtureRunId does not match FIXTURE_RUN_ID." >&2
	exit 1
fi
if [[ "${dataset_id}" != "issue-196-prayer-poll-list-v2" || "${shaped_at}" != "null" ]]; then
	echo "Manifest must use the Issue #196 dataset and must not have been shaped already." >&2
	exit 1
fi

campus_id="$(json_value primaryCampus.campusId)"
seed_project="$(json_value composeRuntime.composeProject)"
seed_app_hash="$(json_value composeRuntime.appConfigHash)"
seed_db_hash="$(json_value composeRuntime.dbConfigHash)"
seed_redis_hash="$(json_value composeRuntime.redisConfigHash)"
seed_app_image_id="$(json_value composeRuntime.appImageId)"
seed_db_image_id="$(json_value composeRuntime.dbImageId)"
seed_redis_image_id="$(json_value composeRuntime.redisImageId)"
seed_app_container_id="$(json_value composeRuntime.appContainerId)"
seed_app_started_at="$(json_value composeRuntime.appContainerStartedAt)"
seed_db_container_id="$(json_value composeRuntime.dbContainerId)"
seed_db_started_at="$(json_value composeRuntime.dbContainerStartedAt)"
seed_redis_container_id="$(json_value composeRuntime.redisContainerId)"
seed_redis_started_at="$(json_value composeRuntime.redisContainerStartedAt)"
seed_source_revision="$(json_value composeRuntime.sourceRevision)"
seed_target_port="$(json_value composeRuntime.targetPort)"
open_id="$(json_value polls.byKey.open.id)"
member_id="$(json_value polls.byKey.closed_member_visible.id)"
admin_id="$(json_value polls.byKey.closed_admin_only.id)"
expired_id="$(json_value polls.byKey.closed_expired.id)"
future_id="$(json_value polls.byKey.scheduled_future.id)"
meal_archived_id="$(json_value polls.duty.mealArchived.id)"
coffee_id="$(json_value polls.duty.coffee.id)"
meal_open_id="$(json_value polls.duty.mealOpen.id)"

label() {
	docker inspect --format "{{ index .Config.Labels \"$2\" }}" "$1"
}

capture_database_identity() {
	local raw
	raw="$(PGPASSWORD="${PERF_DB_PASSWORD}" docker exec -i -e PGPASSWORD -e PGAPPNAME=faithlog_issue196_observer "${DB_CONTAINER}" \
		psql -X -v ON_ERROR_STOP=1 -h 127.0.0.1 -U "${PERF_DB_USER}" -d "${PERF_DB_NAME}" -At \
		-f - < "${DB_RUNTIME_IDENTITY_SQL}")"
	DB_RUNTIME_IDENTITY_JSON="${raw}" EXPECTED_FLYWAY_VERSION="${EXPECTED_FLYWAY_VERSION}" node "${VALIDATE_RUNTIME_IDENTITY}"
}

capture_redis_identity() {
	local info
	info="$(docker exec "${REDIS_CONTAINER}" redis-cli --raw INFO server)"
	REDIS_INFO_TEXT="${info}" EXPECTED_REDIS_PORT="${EXPECTED_REDIS_PORT}" node "${PARSE_REDIS_IDENTITY}"
}

app_container_id="$(docker inspect --format '{{.Id}}' "${APP_CONTAINER}")"
app_container_started_at="$(docker inspect --format '{{.State.StartedAt}}' "${APP_CONTAINER}")"
db_container_id="$(docker inspect --format '{{.Id}}' "${DB_CONTAINER}")"
db_image_id="$(docker inspect --format '{{.Image}}' "${DB_CONTAINER}")"
db_container_started_at="$(docker inspect --format '{{.State.StartedAt}}' "${DB_CONTAINER}")"
redis_container_id="$(docker inspect --format '{{.Id}}' "${REDIS_CONTAINER}")"
redis_image_id="$(docker inspect --format '{{.Image}}' "${REDIS_CONTAINER}")"
redis_container_started_at="$(docker inspect --format '{{.State.StartedAt}}' "${REDIS_CONTAINER}")"
compose_project="$(label "${APP_CONTAINER}" com.docker.compose.project)"
db_project="$(label "${DB_CONTAINER}" com.docker.compose.project)"
redis_project="$(label "${REDIS_CONTAINER}" com.docker.compose.project)"
app_service="$(label "${APP_CONTAINER}" com.docker.compose.service)"
db_service="$(label "${DB_CONTAINER}" com.docker.compose.service)"
redis_service="$(label "${REDIS_CONTAINER}" com.docker.compose.service)"
app_hash="$(label "${APP_CONTAINER}" com.docker.compose.config-hash)"
db_hash="$(label "${DB_CONTAINER}" com.docker.compose.config-hash)"
redis_hash="$(label "${REDIS_CONTAINER}" com.docker.compose.config-hash)"
app_image="$(docker inspect --format '{{.Config.Image}}' "${APP_CONTAINER}")"
app_image_id="$(docker inspect --format '{{.Image}}' "${APP_CONTAINER}")"
db_image="$(docker inspect --format '{{.Config.Image}}' "${DB_CONTAINER}")"
redis_image="$(docker inspect --format '{{.Config.Image}}' "${REDIS_CONTAINER}")"
database_identity="$(capture_database_identity)"
redis_identity="$(capture_redis_identity)"
published_ports="$(docker port "${APP_CONTAINER}" 8080/tcp)"
base_target_port="$(BASE_URL_VALUE="${BASE_URL}" PUBLISHED_BINDINGS_VALUE="${published_ports}" \
	node "${VALIDATE_TARGET}" --host-port)" \
	|| { echo "BASE_URL and the app container published binding do not identify one approved numeric loopback target." >&2; exit 1; }
if [[ -z "${compose_project}" || "${compose_project}" != "${db_project}" || "${compose_project}" != "${redis_project}" \
	|| "${app_service}" != "${EXPECTED_APP_SERVICE}" || "${db_service}" != "${EXPECTED_DB_SERVICE}" \
	|| "${redis_service}" != "${EXPECTED_REDIS_SERVICE}" \
	|| -z "${app_hash}" || -z "${db_hash}" || -z "${redis_hash}" \
	|| "${app_image}" != "${EXPECTED_APP_IMAGE}" || "${app_image_id}" != "${EXPECTED_APP_IMAGE_ID}" \
	|| "${db_image}" != "${EXPECTED_DB_IMAGE}" || "${db_image_id}" != "${EXPECTED_DB_IMAGE_ID}" \
	|| "${redis_image}" != "${EXPECTED_REDIS_IMAGE}" || "${redis_image_id}" != "${EXPECTED_REDIS_IMAGE_ID}" ]]; then
	echo "Refusing to shape: immutable app/PostgreSQL/Redis Compose attestation failed." >&2
	exit 1
fi
if [[ ! "${compose_project}" =~ ^[a-z0-9][a-z0-9_-]*$ ]]; then
	echo "Compose project label cannot be represented by the canonical lock path." >&2
	exit 1
fi
if [[ "${compose_project}" != "${seed_project}" || "${app_hash}" != "${seed_app_hash}" \
	|| "${db_hash}" != "${seed_db_hash}" || "${redis_hash}" != "${seed_redis_hash}" \
	|| "${app_image_id}" != "${seed_app_image_id}" || "${db_image_id}" != "${seed_db_image_id}" \
	|| "${redis_image_id}" != "${seed_redis_image_id}" || "${EXPECTED_SOURCE_REVISION}" != "${seed_source_revision}" \
	|| "${app_container_id}" != "${seed_app_container_id}" || "${app_container_started_at}" != "${seed_app_started_at}" \
	|| "${db_container_id}" != "${seed_db_container_id}" || "${db_container_started_at}" != "${seed_db_started_at}" \
	|| "${redis_container_id}" != "${seed_redis_container_id}" || "${redis_container_started_at}" != "${seed_redis_started_at}" \
	|| "${base_target_port}" != "${seed_target_port}" ]]; then
	echo "Refusing to shape: current Compose identity differs from the seed manifest." >&2
	exit 1
fi

PERF_PROJECT_LOCK="/tmp/faithlog-performance-${compose_project}.lock"
if ! mkdir "${PERF_PROJECT_LOCK}" 2>/dev/null; then
	echo "Another seed or load run owns ${PERF_PROJECT_LOCK}." >&2
	exit 1
fi
trap 'rmdir "${PERF_PROJECT_LOCK}" 2>/dev/null || true' EXIT

assert_post_lock_runtime_identity() {
	local current_published_ports
	local current_target_port
	local current_database_identity
	local current_redis_identity
	current_published_ports="$(docker port "${APP_CONTAINER}" 8080/tcp)"
	current_target_port="$(BASE_URL_VALUE="${BASE_URL}" PUBLISHED_BINDINGS_VALUE="${current_published_ports}" \
		node "${VALIDATE_TARGET}" --host-port)" || return 1
	current_database_identity="$(capture_database_identity)" || return 1
	current_redis_identity="$(capture_redis_identity)" || return 1
	[[ "$(docker inspect --format '{{.Id}}' "${APP_CONTAINER}")" == "${app_container_id}"
		&& "$(docker inspect --format '{{.Image}}' "${APP_CONTAINER}")" == "${app_image_id}"
		&& "$(docker inspect --format '{{.State.StartedAt}}' "${APP_CONTAINER}")" == "${app_container_started_at}"
		&& "$(docker inspect --format '{{.Id}}' "${DB_CONTAINER}")" == "${db_container_id}"
		&& "$(docker inspect --format '{{.Image}}' "${DB_CONTAINER}")" == "${db_image_id}"
		&& "$(docker inspect --format '{{.State.StartedAt}}' "${DB_CONTAINER}")" == "${db_container_started_at}"
		&& "$(docker inspect --format '{{.Id}}' "${REDIS_CONTAINER}")" == "${redis_container_id}"
		&& "$(docker inspect --format '{{.Image}}' "${REDIS_CONTAINER}")" == "${redis_image_id}"
		&& "$(docker inspect --format '{{.State.StartedAt}}' "${REDIS_CONTAINER}")" == "${redis_container_started_at}"
		&& "$(label "${APP_CONTAINER}" com.docker.compose.project)" == "${compose_project}"
		&& "$(label "${DB_CONTAINER}" com.docker.compose.project)" == "${db_project}"
		&& "$(label "${REDIS_CONTAINER}" com.docker.compose.project)" == "${redis_project}"
		&& "$(label "${APP_CONTAINER}" com.docker.compose.service)" == "${app_service}"
		&& "$(label "${DB_CONTAINER}" com.docker.compose.service)" == "${db_service}"
		&& "$(label "${REDIS_CONTAINER}" com.docker.compose.service)" == "${redis_service}"
		&& "$(label "${APP_CONTAINER}" com.docker.compose.config-hash)" == "${app_hash}"
		&& "$(label "${DB_CONTAINER}" com.docker.compose.config-hash)" == "${db_hash}"
		&& "$(label "${REDIS_CONTAINER}" com.docker.compose.config-hash)" == "${redis_hash}"
		&& "$(docker inspect --format '{{.Config.Image}}' "${APP_CONTAINER}")" == "${app_image}"
		&& "$(docker inspect --format '{{.Config.Image}}' "${DB_CONTAINER}")" == "${db_image}"
		&& "$(docker inspect --format '{{.Config.Image}}' "${REDIS_CONTAINER}")" == "${redis_image}"
		&& "${current_target_port}" == "${base_target_port}"
		&& "${current_published_ports}" == "${published_ports}"
		&& "${current_database_identity}" == "${database_identity}"
		&& "${current_redis_identity}" == "${redis_identity}" ]]
}

if ! assert_post_lock_runtime_identity; then
	echo "Runtime identity changed after project lock." >&2
	exit 1
fi
node "${TOOLING_PROVENANCE}" --assert-manifest "${PERF_SCENARIO_WORKTREE}" "${EXPECTED_SCENARIO_HEAD}" "${FIXTURE_MANIFEST}"

shape_attempt="${FIXTURE_MANIFEST}.shape-attempted"
SHAPE_ATTEMPT="${shape_attempt}" node -e '
	const fs = require("node:fs");
	fs.writeFileSync(process.env.SHAPE_ATTEMPT, `${new Date().toISOString()}\n`, { flag: "wx", mode: 0o600 });
' || { echo "This fixture already has a shaping attempt; use a new fixtureRunId." >&2; exit 1; }

# Atomically update all eight rows created by this fixture run. Titles are derived, never trusted from the manifest.
sql="WITH
open_updated AS (
	UPDATE polls SET starts_at = clock_timestamp() - interval '1 hour', ends_at = clock_timestamp() + interval '24 hours', status = 'OPEN', updated_at = clock_timestamp()
	WHERE id = :'open_id'::bigint AND campus_id = :'campus_id'::bigint AND title = :'open_title'
	RETURNING starts_at, ends_at
), member_updated AS (
	UPDATE polls SET starts_at = clock_timestamp() - interval '3 days', ends_at = clock_timestamp() - interval '2 days', status = 'CLOSED', updated_at = clock_timestamp()
	WHERE id = :'member_id'::bigint AND campus_id = :'campus_id'::bigint AND title = :'member_title'
	RETURNING starts_at, ends_at
), admin_updated AS (
	UPDATE polls SET starts_at = clock_timestamp() - interval '6 days', ends_at = clock_timestamp() - interval '5 days', status = 'CLOSED', updated_at = clock_timestamp()
	WHERE id = :'admin_id'::bigint AND campus_id = :'campus_id'::bigint AND title = :'admin_title'
	RETURNING starts_at, ends_at
), expired_updated AS (
	UPDATE polls SET starts_at = clock_timestamp() - interval '9 days', ends_at = clock_timestamp() - interval '8 days', status = 'CLOSED', updated_at = clock_timestamp()
	WHERE id = :'expired_id'::bigint AND campus_id = :'campus_id'::bigint AND title = :'expired_title'
	RETURNING starts_at, ends_at
), future_updated AS (
	UPDATE polls SET starts_at = clock_timestamp() + interval '2 days', ends_at = clock_timestamp() + interval '3 days', status = 'SCHEDULED', updated_at = clock_timestamp()
	WHERE id = :'future_id'::bigint AND campus_id = :'campus_id'::bigint AND title = :'future_title'
	RETURNING starts_at, ends_at
), meal_archived_updated AS (
	UPDATE polls SET starts_at = clock_timestamp() - interval '92 days', ends_at = clock_timestamp() - interval '91 days', status = 'CLOSED', updated_at = clock_timestamp()
	WHERE id = :'meal_archived_id'::bigint AND campus_id = :'campus_id'::bigint AND title = :'meal_archived_title' AND poll_type = 'MEAL'
	RETURNING starts_at, ends_at
), coffee_updated AS (
	UPDATE polls SET starts_at = clock_timestamp() - interval '1 hour', ends_at = clock_timestamp() + interval '24 hours', status = 'OPEN', updated_at = clock_timestamp()
	WHERE id = :'coffee_id'::bigint AND campus_id = :'campus_id'::bigint AND title = :'coffee_title' AND poll_type = 'COFFEE'
	RETURNING starts_at, ends_at
), meal_open_updated AS (
	UPDATE polls SET starts_at = clock_timestamp() - interval '1 hour', ends_at = clock_timestamp() + interval '24 hours', status = 'OPEN', updated_at = clock_timestamp()
	WHERE id = :'meal_open_id'::bigint AND campus_id = :'campus_id'::bigint AND title = :'meal_open_title' AND poll_type = 'MEAL'
	RETURNING starts_at, ends_at
)
SELECT json_build_object(
	'open', (SELECT row_to_json(value) FROM open_updated value),
	'closed_member_visible', (SELECT row_to_json(value) FROM member_updated value),
	'closed_admin_only', (SELECT row_to_json(value) FROM admin_updated value),
	'closed_expired', (SELECT row_to_json(value) FROM expired_updated value),
	'scheduled_future', (SELECT row_to_json(value) FROM future_updated value),
	'meal_archived', (SELECT row_to_json(value) FROM meal_archived_updated value),
	'coffee', (SELECT row_to_json(value) FROM coffee_updated value),
	'meal_open', (SELECT row_to_json(value) FROM meal_open_updated value),
	'guard', 1 / CASE WHEN
		(SELECT count(*) FROM open_updated) = 1 AND (SELECT count(*) FROM member_updated) = 1
		AND (SELECT count(*) FROM admin_updated) = 1 AND (SELECT count(*) FROM expired_updated) = 1
		AND (SELECT count(*) FROM future_updated) = 1 AND (SELECT count(*) FROM meal_archived_updated) = 1
		AND (SELECT count(*) FROM coffee_updated) = 1 AND (SELECT count(*) FROM meal_open_updated) = 1 THEN 1 ELSE 0 END
);"

shape_result="$(PGPASSWORD="${PERF_DB_PASSWORD}" docker exec -e PGPASSWORD "${DB_CONTAINER}" \
	psql -X -v ON_ERROR_STOP=1 -U "${PERF_DB_USER}" -d "${PERF_DB_NAME}" -At \
	-v campus_id="${campus_id}" \
	-v open_id="${open_id}" -v open_title="PERF_196_${FIXTURE_RUN_ID}_POLL_OPEN" \
	-v member_id="${member_id}" -v member_title="PERF_196_${FIXTURE_RUN_ID}_POLL_CLOSED_MEMBER_VISIBLE" \
	-v admin_id="${admin_id}" -v admin_title="PERF_196_${FIXTURE_RUN_ID}_POLL_CLOSED_ADMIN_ONLY" \
	-v expired_id="${expired_id}" -v expired_title="PERF_196_${FIXTURE_RUN_ID}_POLL_CLOSED_EXPIRED" \
	-v future_id="${future_id}" -v future_title="PERF_196_${FIXTURE_RUN_ID}_POLL_SCHEDULED_FUTURE" \
	-v meal_archived_id="${meal_archived_id}" -v meal_archived_title="PERF_196_${FIXTURE_RUN_ID}_POLL_MEAL_ARCHIVED" \
	-v coffee_id="${coffee_id}" -v coffee_title="PERF_196_${FIXTURE_RUN_ID}_POLL_COFFEE" \
	-v meal_open_id="${meal_open_id}" -v meal_open_title="PERF_196_${FIXTURE_RUN_ID}_POLL_MEAL_OPEN" \
	-c "${sql}")"

SHAPE_RESULT="${shape_result}" node -e '
	const fs = require("node:fs");
	const path = process.argv[1];
	const manifest = JSON.parse(fs.readFileSync(path, "utf8"));
	const windows = JSON.parse(process.env.SHAPE_RESULT);
	manifest.shapedAt = new Date().toISOString();
	for (const [key, window] of Object.entries(windows)) {
		if (key === "guard") continue;
		const target = key === "meal_archived" ? manifest.polls.duty.mealArchived
			: key === "coffee" ? manifest.polls.duty.coffee
			: key === "meal_open" ? manifest.polls.duty.mealOpen
			: manifest.polls.byKey[key];
		target.startsAt = window.starts_at;
		target.endsAt = window.ends_at;
	}
	manifest.polls.byKey.open.status = "OPEN";
	manifest.polls.byKey.closed_member_visible.status = "CLOSED";
	manifest.polls.byKey.closed_admin_only.status = "CLOSED";
	manifest.polls.byKey.closed_expired.status = "CLOSED";
	manifest.polls.byKey.scheduled_future.status = "SCHEDULED";
	manifest.polls.duty.mealArchived.status = "CLOSED";
	manifest.polls.duty.coffee.status = "OPEN";
	manifest.polls.duty.mealOpen.status = "OPEN";
	fs.writeFileSync(path, `${JSON.stringify(manifest, null, 2)}\n`, { mode: 0o600 });
' "${FIXTURE_MANIFEST}"

echo "Eight fixture-owned visibility/archive windows shaped atomically for fixtureRunId=${fixture_run_id}. No other row was modified or deleted."
