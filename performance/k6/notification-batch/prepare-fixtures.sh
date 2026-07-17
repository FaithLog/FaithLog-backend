#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPOSITORY_ROOT="$(cd "${SCRIPT_DIR}/../../.." && pwd)"
source "${SCRIPT_DIR}/guard-runtime.sh"
source "${SCRIPT_DIR}/runner-lifecycle.sh"

CURRENT_DEVELOP_CONTRACT_PATH="${SCRIPT_DIR}/current-develop-contract.json"
export CURRENT_DEVELOP_CONTRACT_PATH

: "${PERF_FIXTURE_RUN_ID:?Set a fresh fixtureRunId.}"
if [[ ! "${PERF_FIXTURE_RUN_ID}" =~ ^[A-Za-z0-9_-]{1,40}$ ]]; then
	echo "PERF_FIXTURE_RUN_ID must be 1-40 safe identifier characters." >&2
	exit 2
fi
REPORT_ROOT="${PERF_REPORT_ROOT:-${REPOSITORY_ROOT}/build/reports/k6/notification-batch}"
FIXTURE_ROOT="${REPORT_ROOT}/fixtures"
REPORT_DIR="${FIXTURE_ROOT}/${PERF_FIXTURE_RUN_ID}"
MANIFEST_PATH="${REPORT_DIR}/manifest.json"
TEMP_MANIFEST_PATH="${REPORT_DIR}/.manifest.json.tmp.$$"
REJECTION_PATH="${FIXTURE_ROOT}/rejections/${PERF_FIXTURE_RUN_ID}.json"
REJECTION_STAGE="fixture-preflight"
REJECTION_REASON="fixture-command-failed"
mkdir -p "${FIXTURE_ROOT}/rejections"
if [[ -e "${REJECTION_PATH}" ]]; then
	echo "PERF_FIXTURE_RUN_ID was previously rejected; use a fresh fixtureRunId." >&2
	exit 2
fi
notification_batch_fixture_cleanup() {
	local status=$?
	trap - EXIT
	if (( status != 0 )); then
		REJECTION_PATH="${REJECTION_PATH}" \
			REJECTION_STAGE="${REJECTION_STAGE}" \
			REJECTION_REASON="${REJECTION_REASON}" \
			REJECTION_EXIT_CODE="${status}" \
			node "${SCRIPT_DIR}/rejection-contract.mjs" >/dev/null 2>&1 || true
	fi
	rm -f "${TEMP_MANIFEST_PATH}"
	if [[ ! -f "${MANIFEST_PATH}" ]]; then
		rm -f "${REPORT_DIR}/runtime-identity-locked.json" \
			"${REPORT_DIR}/runtime-identity-before-fixture.json" \
			"${REPORT_DIR}/runtime-identity-after-fixture.json" \
			"${REPORT_DIR}/fixture-status.json" \
			"${REPORT_DIR}/runtime-continuity-pre-fixture.json" \
			"${REPORT_DIR}/runtime-continuity-report.json"
		rmdir "${REPORT_DIR}" 2>/dev/null || true
	fi
	release_notification_batch_locks
	exit "${status}"
}
PERF_GLOBAL_LOCK_DIR=""
PERF_PROJECT_LOCK_DIR=""
PERF_GLOBAL_LOCK_HELD=false
PERF_PROJECT_LOCK_HELD=false
install_notification_batch_fixture_traps

if ! notification_batch_require_runtime_inputs \
	PERF_MEMBER_COUNT POSTGRES_USER POSTGRES_PASSWORD POSTGRES_DB PERF_EXPECTED_POSTGRES_ROLE \
	PERF_DATASET_ID PERF_SAMPLE_KIND PERF_CAMPUS_ID PERF_SUCCESS_COUNT PERF_TRANSIENT_COUNT \
	PERF_PERMANENT_COUNT PERF_INACTIVE_COUNT PERF_NO_TOKEN_COUNT; then
	exit 2
fi
: "${PERF_MEMBER_COUNT:?Set the approved target count; Issue #198 requires exactly 1000.}"
: "${POSTGRES_USER:?Set the runtime-approved direct owner JDBC role.}"
: "${POSTGRES_PASSWORD:?Set the runtime-only PostgreSQL credential.}"
: "${POSTGRES_DB:?Set the runtime-approved dedicated PostgreSQL database.}"
: "${PERF_EXPECTED_POSTGRES_ROLE:?Set the runtime-approved direct owner JDBC role for #202 continuity.}"
: "${PERF_DATASET_ID:?Set the existing PERFORMANCE datasetId.}"
: "${PERF_SAMPLE_KIND:?Set PERF_SAMPLE_KIND=canonical for the one prepared snapshot source.}"
: "${PERF_CAMPUS_ID:?Set the existing 1,000-member PERFORMANCE campus ID.}"
: "${PERF_SUCCESS_COUNT:?Set the active-token immediate-success count.}"
: "${PERF_TRANSIENT_COUNT:?Set the active-token transient-then-success count.}"
: "${PERF_PERMANENT_COUNT:?Set the active-token permanent-failure count.}"
: "${PERF_INACTIVE_COUNT:?Set the inactive-token-only count.}"
: "${PERF_NO_TOKEN_COUNT:?Set the no-token count.}"
export POSTGRES_USER POSTGRES_PASSWORD POSTGRES_DB PERF_EXPECTED_POSTGRES_ROLE

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
if [[ "${PERF_SAMPLE_KIND}" != "canonical" ]]; then
	echo "PERF_SAMPLE_KIND must be canonical; per-sample kinds are metadata views after restore." >&2
	exit 2
fi
if [[ ! "${POSTGRES_USER}" =~ ^[A-Za-z_][A-Za-z0-9_]*$ \
	|| ! "${POSTGRES_DB}" =~ ^[A-Za-z_][A-Za-z0-9_]*$ \
	|| ! "${PERF_EXPECTED_POSTGRES_ROLE}" =~ ^[A-Za-z_][A-Za-z0-9_]*$ \
	|| "${POSTGRES_USER}" != "${PERF_EXPECTED_POSTGRES_ROLE}" ]]; then
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

REJECTION_REASON="current-develop-source-identity-failed"
node "${SCRIPT_DIR}/verify-current-develop-contract.mjs" >/dev/null
REJECTION_REASON="runtime-target-validation-failed"
guard_notification_batch_runtime
REJECTION_STAGE="fixture-lock"
REJECTION_REASON="performance-lock-unavailable"
acquire_notification_batch_locks
mkdir -p "${FIXTURE_ROOT}"
if ! mkdir "${REPORT_DIR}" 2>/dev/null; then
	echo "PERF_FIXTURE_RUN_ID report directory already exists; use a fresh fixtureRunId." >&2
	exit 2
fi
REJECTION_STAGE="fixture-post-lock-runtime"
REJECTION_REASON="post-lock-runtime-identity-mismatch"
verify_notification_batch_runtime_after_lock "${REPORT_DIR}/runtime-identity-locked.json"
bash "${SCRIPT_DIR}/capture-runtime-identity.sh" "${REPORT_DIR}/runtime-identity-before-fixture.json"
RUNTIME_CONTINUITY_REPORT_PATH="${REPORT_DIR}/runtime-continuity-pre-fixture.json" \
	RUNTIME_IDENTITY_PHASES=locked,before-fixture RUN_DIR="${REPORT_DIR}" \
	node "${SCRIPT_DIR}/assert-runtime-continuity.mjs"

REJECTION_STAGE="fixture-mutation"
REJECTION_REASON="fixture-contract-failed"
PGPASSWORD="${POSTGRES_PASSWORD}" docker exec -e PGPASSWORD -i "${PERF_POSTGRES_CONTAINER_ID}" psql \
	-h 127.0.0.1 \
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

REJECTION_STAGE="fixture-final-continuity"
REJECTION_REASON="immutable-runtime-continuity-failed"
bash "${SCRIPT_DIR}/capture-runtime-identity.sh" "${REPORT_DIR}/runtime-identity-after-fixture.json"
RUNTIME_CONTINUITY_REPORT_PATH="${REPORT_DIR}/runtime-continuity-report.json" \
	RUNTIME_IDENTITY_PHASES=locked,before-fixture,after-fixture RUN_DIR="${REPORT_DIR}" \
	node "${SCRIPT_DIR}/assert-runtime-continuity.mjs"

mv "${TEMP_MANIFEST_PATH}" "${MANIFEST_PATH}"

# A prepared fixture is scenario-ready only; it is never accepted or automatically adopted as a baseline.
printf '{"status":"prepared","accepted":false,"automaticAdoption":false}\n' \
	> "${REPORT_DIR}/fixture-status.json"

echo "Fixture manifest: ${MANIFEST_PATH}"
