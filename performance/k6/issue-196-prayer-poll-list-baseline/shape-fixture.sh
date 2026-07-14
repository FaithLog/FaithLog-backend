#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${ROOT_DIR}/../../.." && pwd)"
FIXTURE_RUN_ID="${FIXTURE_RUN_ID:?FIXTURE_RUN_ID is required}"
FIXTURE_MANIFEST="${FIXTURE_MANIFEST:-${REPO_ROOT}/build/reports/k6/issue-196/${FIXTURE_RUN_ID}/fixture-manifest.json}"
DB_CONTAINER="${DB_CONTAINER:-faithlog-postgres}"
PERF_DB_USER="${PERF_DB_USER:?PERF_DB_USER is required at runtime}"
PERF_DB_NAME="${PERF_DB_NAME:?PERF_DB_NAME is required at runtime}"
PERF_DB_PASSWORD="${PERF_DB_PASSWORD:?PERF_DB_PASSWORD is required at runtime}"
PERF_GLOBAL_LOCK="${PERF_GLOBAL_LOCK:-/tmp/faithlog-performance-global.lock}"

if [[ ! "${FIXTURE_RUN_ID}" =~ ^[a-zA-Z0-9][a-zA-Z0-9_-]{7,31}$ ]]; then
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
fixture_run_id="${FIXTURE_RUN_ID}"
if [[ "${manifest_run_id}" != "${fixture_run_id}" ]]; then
	echo "Manifest fixtureRunId does not match FIXTURE_RUN_ID." >&2
	exit 1
fi

campus_id="$(json_value primaryCampus.campusId)"

shape_poll() {
	local key="$1"
	local starts_interval="$2"
	local ends_interval="$3"
	local poll_id
	local poll_title
	local changed
	poll_id="$(json_value "polls.byKey.${key}.id")"
	poll_title="$(json_value "polls.byKey.${key}.title")"

	# Update created fixture rows only: exact current-run id + campus + title ownership must all match.
	local sql="WITH updated AS (
		UPDATE polls
		SET starts_at = now() - interval '${starts_interval}',
			ends_at = now() - interval '${ends_interval}',
			status = 'CLOSED',
			updated_at = now()
		WHERE id = :'poll_id'::bigint
			AND campus_id = :'campus_id'::bigint
			AND title = :'poll_title'
		RETURNING id
	) SELECT count(*) FROM updated;"

	changed="$(docker exec -e PGPASSWORD="${PERF_DB_PASSWORD}" "${DB_CONTAINER}" \
		psql -X -v ON_ERROR_STOP=1 \
		-v poll_id="${poll_id}" \
		-v campus_id="${campus_id}" \
		-v poll_title="${poll_title}" \
		-U "${PERF_DB_USER}" -d "${PERF_DB_NAME}" -At -c "${sql}")"
	if [[ "${changed}" != "1" ]]; then
		echo "Refusing to shape ${key}: expected exactly one fixture-owned row, changed=${changed}." >&2
		exit 1
	fi
}

shape_poll closed_member_visible "3 days" "2 days"
shape_poll closed_admin_only "6 days" "5 days"
shape_poll closed_expired "9 days" "8 days"

node -e '
	const fs = require("node:fs");
	const path = process.argv[1];
	const manifest = JSON.parse(fs.readFileSync(path, "utf8"));
	manifest.shapedAt = new Date().toISOString();
	manifest.polls.byKey.closed_member_visible.status = "CLOSED";
	manifest.polls.byKey.closed_admin_only.status = "CLOSED";
	manifest.polls.byKey.closed_expired.status = "CLOSED";
	fs.writeFileSync(path, `${JSON.stringify(manifest, null, 2)}\n`, { mode: 0o600 });
' "${FIXTURE_MANIFEST}"

echo "Fixture visibility windows shaped for fixtureRunId=${fixture_run_id}. No row outside this fixture run was modified or deleted."
