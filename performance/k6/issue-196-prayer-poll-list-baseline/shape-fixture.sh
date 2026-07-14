#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${ROOT_DIR}/../../.." && pwd)"
FIXTURE_RUN_ID="${FIXTURE_RUN_ID:?FIXTURE_RUN_ID is required}"
FIXTURE_MANIFEST="${FIXTURE_MANIFEST:-${REPO_ROOT}/build/reports/k6/issue-196/${FIXTURE_RUN_ID}/fixture-manifest.json}"
DB_CONTAINER="${DB_CONTAINER:-faithlog-postgres}"
APP_CONTAINER="${APP_CONTAINER:-faithlog-backend}"
EXPECTED_APP_IMAGE="faithlog-latest"
PERF_DB_USER="${PERF_DB_USER:?PERF_DB_USER is required at runtime}"
PERF_DB_NAME="${PERF_DB_NAME:?PERF_DB_NAME is required at runtime}"
PERF_DB_PASSWORD="${PERF_DB_PASSWORD:?PERF_DB_PASSWORD is required at runtime}"
PERF_GLOBAL_LOCK="/tmp/faithlog-performance-global.lock"

if [[ ! "${FIXTURE_RUN_ID}" =~ ^[a-z0-9][a-z0-9_-]{7,31}$ ]]; then
	echo "Invalid FIXTURE_RUN_ID." >&2
	exit 1
fi

if [[ ! -f "${FIXTURE_MANIFEST}" ]]; then
	echo "Fixture manifest not found: ${FIXTURE_MANIFEST}" >&2
	exit 1
fi

if ! mkdir "${PERF_GLOBAL_LOCK}" 2>/dev/null; then
	echo "Another performance seed or load run owns ${PERF_GLOBAL_LOCK}." >&2
	exit 1
fi
trap 'rmdir "${PERF_GLOBAL_LOCK}" 2>/dev/null || true' EXIT

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
if [[ "${dataset_id}" != "issue-196-prayer-poll-list-v1" || "${shaped_at}" != "null" ]]; then
	echo "Manifest must use the Issue #196 dataset and must not have been shaped already." >&2
	exit 1
fi

campus_id="$(json_value primaryCampus.campusId)"
seed_project="$(json_value composeRuntime.composeProject)"
seed_app_hash="$(json_value composeRuntime.appConfigHash)"
seed_db_hash="$(json_value composeRuntime.dbConfigHash)"
seed_app_image_id="$(json_value composeRuntime.appImageId)"
open_id="$(json_value polls.byKey.open.id)"
member_id="$(json_value polls.byKey.closed_member_visible.id)"
admin_id="$(json_value polls.byKey.closed_admin_only.id)"
expired_id="$(json_value polls.byKey.closed_expired.id)"
future_id="$(json_value polls.byKey.scheduled_future.id)"

label() {
	docker inspect --format "{{ index .Config.Labels \"$2\" }}" "$1"
}

compose_project="$(label "${APP_CONTAINER}" com.docker.compose.project)"
db_project="$(label "${DB_CONTAINER}" com.docker.compose.project)"
app_service="$(label "${APP_CONTAINER}" com.docker.compose.service)"
db_service="$(label "${DB_CONTAINER}" com.docker.compose.service)"
app_hash="$(label "${APP_CONTAINER}" com.docker.compose.config-hash)"
db_hash="$(label "${DB_CONTAINER}" com.docker.compose.config-hash)"
app_image="$(docker inspect --format '{{.Config.Image}}' "${APP_CONTAINER}")"
app_image_id="$(docker inspect --format '{{.Image}}' "${APP_CONTAINER}")"
if [[ -z "${compose_project}" || "${compose_project}" != "${db_project}" \
	|| "${app_service}" != "app" || "${db_service}" != "postgres" \
	|| -z "${app_hash}" || -z "${db_hash}" \
	|| ( "${app_image}" != "${EXPECTED_APP_IMAGE}" && "${app_image}" != "${EXPECTED_APP_IMAGE}:"* ) ]]; then
	echo "Refusing to shape: Compose project/service/config-hash or app image attestation failed." >&2
	exit 1
fi
if [[ "${compose_project}" != "${seed_project}" || "${app_hash}" != "${seed_app_hash}" \
	|| "${db_hash}" != "${seed_db_hash}" || "${app_image_id}" != "${seed_app_image_id}" ]]; then
	echo "Refusing to shape: current Compose identity differs from the seed manifest." >&2
	exit 1
fi

shape_attempt="${FIXTURE_MANIFEST}.shape-attempted"
SHAPE_ATTEMPT="${shape_attempt}" node -e '
	const fs = require("node:fs");
	fs.writeFileSync(process.env.SHAPE_ATTEMPT, `${new Date().toISOString()}\n`, { flag: "wx", mode: 0o600 });
' || { echo "This fixture already has a shaping attempt; use a new fixtureRunId." >&2; exit 1; }

# Atomically update all five rows created by this fixture run. Titles are derived, never trusted from the manifest.
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
)
SELECT json_build_object(
	'open', (SELECT row_to_json(value) FROM open_updated value),
	'closed_member_visible', (SELECT row_to_json(value) FROM member_updated value),
	'closed_admin_only', (SELECT row_to_json(value) FROM admin_updated value),
	'closed_expired', (SELECT row_to_json(value) FROM expired_updated value),
	'scheduled_future', (SELECT row_to_json(value) FROM future_updated value),
	'guard', 1 / CASE WHEN
		(SELECT count(*) FROM open_updated) = 1 AND (SELECT count(*) FROM member_updated) = 1
		AND (SELECT count(*) FROM admin_updated) = 1 AND (SELECT count(*) FROM expired_updated) = 1
		AND (SELECT count(*) FROM future_updated) = 1 THEN 1 ELSE 0 END
);"

shape_result="$(PGPASSWORD="${PERF_DB_PASSWORD}" docker exec -e PGPASSWORD "${DB_CONTAINER}" \
	psql -X -v ON_ERROR_STOP=1 -U "${PERF_DB_USER}" -d "${PERF_DB_NAME}" -At \
	-v campus_id="${campus_id}" \
	-v open_id="${open_id}" -v open_title="PERF_196_${FIXTURE_RUN_ID}_POLL_OPEN" \
	-v member_id="${member_id}" -v member_title="PERF_196_${FIXTURE_RUN_ID}_POLL_CLOSED_MEMBER_VISIBLE" \
	-v admin_id="${admin_id}" -v admin_title="PERF_196_${FIXTURE_RUN_ID}_POLL_CLOSED_ADMIN_ONLY" \
	-v expired_id="${expired_id}" -v expired_title="PERF_196_${FIXTURE_RUN_ID}_POLL_CLOSED_EXPIRED" \
	-v future_id="${future_id}" -v future_title="PERF_196_${FIXTURE_RUN_ID}_POLL_SCHEDULED_FUTURE" \
	-c "${sql}")"

SHAPE_RESULT="${shape_result}" node -e '
	const fs = require("node:fs");
	const path = process.argv[1];
	const manifest = JSON.parse(fs.readFileSync(path, "utf8"));
	const windows = JSON.parse(process.env.SHAPE_RESULT);
	manifest.shapedAt = new Date().toISOString();
	for (const [key, window] of Object.entries(windows)) {
		if (key === "guard") continue;
		manifest.polls.byKey[key].startsAt = window.starts_at;
		manifest.polls.byKey[key].endsAt = window.ends_at;
	}
	manifest.polls.byKey.open.status = "OPEN";
	manifest.polls.byKey.closed_member_visible.status = "CLOSED";
	manifest.polls.byKey.closed_admin_only.status = "CLOSED";
	manifest.polls.byKey.closed_expired.status = "CLOSED";
	manifest.polls.byKey.scheduled_future.status = "SCHEDULED";
	fs.writeFileSync(path, `${JSON.stringify(manifest, null, 2)}\n`, { mode: 0o600 });
' "${FIXTURE_MANIFEST}"

echo "Five fixture-owned visibility windows shaped atomically for fixtureRunId=${fixture_run_id}. No other row was modified or deleted."
