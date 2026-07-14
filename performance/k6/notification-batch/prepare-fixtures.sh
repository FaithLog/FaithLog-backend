#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPOSITORY_ROOT="$(cd "${SCRIPT_DIR}/../../.." && pwd)"
source "${SCRIPT_DIR}/guard-runtime.sh"

PERF_MEMBER_COUNT="${PERF_MEMBER_COUNT:-1000}"
POSTGRES_USER="${POSTGRES_USER:-faithlog}"
POSTGRES_DB="${POSTGRES_DB:-faithlog}"

: "${PERF_DATASET_ID:?Set the existing PERFORMANCE datasetId.}"
: "${PERF_FIXTURE_RUN_ID:?Set a fresh fixtureRunId.}"
: "${PERF_SAMPLE_KIND:?Set PERF_SAMPLE_KIND to warmup or measured.}"
: "${PERF_CAMPUS_ID:?Set the existing 1,000-member PERFORMANCE campus ID.}"
: "${PERF_SUCCESS_COUNT:?Set the active-token immediate-success count.}"
: "${PERF_TRANSIENT_COUNT:?Set the active-token transient-then-success count.}"
: "${PERF_PERMANENT_COUNT:?Set the active-token permanent-failure count.}"
: "${PERF_INACTIVE_COUNT:?Set the inactive-token-only count.}"
: "${PERF_NO_TOKEN_COUNT:?Set the no-token count.}"

if [[ ! "${PERF_DATASET_ID}" =~ ^PERFORMANCE_[A-Za-z0-9_-]+$ ]]; then
	echo "PERF_DATASET_ID must be a PERFORMANCE_ identifier." >&2
	exit 2
fi
if [[ "${PERF_MEMBER_COUNT}" != "1000" ]]; then
	echo "PERF_MEMBER_COUNT must be exactly 1000 before fixture mutation." >&2
	exit 2
fi
if [[ ! "${PERF_CAMPUS_ID}" =~ ^[1-9][0-9]*$ ]]; then
	echo "PERF_CAMPUS_ID must be a positive integer." >&2
	exit 2
fi
if [[ ! "${PERF_FIXTURE_RUN_ID}" =~ ^[A-Za-z0-9_-]{1,40}$ ]]; then
	echo "PERF_FIXTURE_RUN_ID must be 1-40 safe identifier characters." >&2
	exit 2
fi
if [[ "${PERF_SAMPLE_KIND}" != "warmup" && "${PERF_SAMPLE_KIND}" != "measured" ]]; then
	echo "PERF_SAMPLE_KIND must be warmup or measured." >&2
	exit 2
fi
if [[ ! "${POSTGRES_USER}" =~ ^[A-Za-z_][A-Za-z0-9_]*$ \
	|| ! "${POSTGRES_DB}" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]]; then
	echo "POSTGRES_USER and POSTGRES_DB must be safe PostgreSQL identifiers." >&2
	exit 2
fi
for count in \
	"${PERF_SUCCESS_COUNT}" \
	"${PERF_TRANSIENT_COUNT}" \
	"${PERF_PERMANENT_COUNT}" \
	"${PERF_INACTIVE_COUNT}" \
	"${PERF_NO_TOKEN_COUNT}"; do
	if [[ ! "${count}" =~ ^[1-9][0-9]*$ ]]; then
		echo "Every outcome count must be a positive integer." >&2
		exit 2
	fi
done
if (( PERF_SUCCESS_COUNT + PERF_TRANSIENT_COUNT + PERF_PERMANENT_COUNT \
	+ PERF_INACTIVE_COUNT + PERF_NO_TOKEN_COUNT != PERF_MEMBER_COUNT )); then
	echo "Outcome counts must total PERF_MEMBER_COUNT=${PERF_MEMBER_COUNT}." >&2
	exit 2
fi

guard_notification_batch_runtime

LOCK_DIR="/tmp/faithlog-performance-global.lock"
if ! mkdir "${LOCK_DIR}" 2>/dev/null; then
	echo "Another FaithLog fixture, QA, or performance measurement holds the host-global lock." >&2
	exit 2
fi

FIXTURE_ROOT="${REPOSITORY_ROOT}/build/reports/k6/notification-batch/fixtures"
REPORT_DIR="${FIXTURE_ROOT}/${PERF_FIXTURE_RUN_ID}"
MANIFEST_PATH="${REPORT_DIR}/manifest.json"
TEMP_MANIFEST_PATH="${REPORT_DIR}/.manifest.json.tmp.$$"
cleanup() {
	rm -f "${TEMP_MANIFEST_PATH}"
	if [[ ! -f "${MANIFEST_PATH}" ]]; then
		rmdir "${REPORT_DIR}" 2>/dev/null || true
	fi
	rmdir "${LOCK_DIR}" 2>/dev/null || true
}
trap cleanup EXIT
mkdir -p "${FIXTURE_ROOT}"
if ! mkdir "${REPORT_DIR}" 2>/dev/null; then
	echo "PERF_FIXTURE_RUN_ID report directory already exists; use a fresh fixtureRunId." >&2
	exit 2
fi

docker exec -i "${POSTGRES_CONTAINER}" psql \
	-U "${POSTGRES_USER}" \
	-d "${POSTGRES_DB}" \
	-X -q -A -t \
	-v dataset_id="${PERF_DATASET_ID}" \
	-v fixture_run_id="${PERF_FIXTURE_RUN_ID}" \
	-v sample_kind="${PERF_SAMPLE_KIND}" \
	-v compose_project="${PERF_COMPOSE_PROJECT}" \
	-v postgres_database="${POSTGRES_DB}" \
	-v campus_id="${PERF_CAMPUS_ID}" \
	-v member_count="${PERF_MEMBER_COUNT}" \
	-v success_count="${PERF_SUCCESS_COUNT}" \
	-v transient_count="${PERF_TRANSIENT_COUNT}" \
	-v permanent_count="${PERF_PERMANENT_COUNT}" \
	-v inactive_count="${PERF_INACTIVE_COUNT}" \
	-v no_token_count="${PERF_NO_TOKEN_COUNT}" \
	< "${SCRIPT_DIR}/prepare-fixtures.sql" \
	| tail -n 1 > "${TEMP_MANIFEST_PATH}"

node -e '
	const fs = require("node:fs");
	const manifest = JSON.parse(fs.readFileSync(process.argv[1], "utf8"));
	if (manifest.memberCount !== 1000
		|| manifest.mixedTokenUserCount !== 1
		|| manifest.insertedDummyTokenCount !== manifest.memberCount - manifest.noTokenCount + 1
		|| manifest.composeProject !== process.argv[2]
		|| manifest.postgresDatabase !== process.argv[3]
		|| manifest.credentialRecorded !== false) {
		throw new Error("Fixture manifest violates the #198 contract.");
	}
	' "${TEMP_MANIFEST_PATH}" "${PERF_COMPOSE_PROJECT}" "${POSTGRES_DB}"

mv "${TEMP_MANIFEST_PATH}" "${MANIFEST_PATH}"

echo "Fixture manifest: ${MANIFEST_PATH}"
