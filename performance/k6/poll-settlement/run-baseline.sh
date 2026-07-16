#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../../.." && pwd)"

for name in MODE BASE_URL TARGET_CONTRACT REPORT_ROOT PERF_DATASET_ID PERF_FIXTURE_RUN_ID PERF_EXECUTION_RUN_ID PERF_PASSWORD TOKEN_EXPIRY_SAFETY_SECONDS MAINTENANCE_POLL_INTERVAL_SECONDS MAINTENANCE_QUIET_SECONDS MAINTENANCE_TIMEOUT_SECONDS; do
	[[ -n "${!name:-}" ]] || { echo "${name} is required at runtime." >&2; exit 2; }
done
TARGET_CONTRACT="$(node -e 'console.log(require("path").resolve(process.argv[1]))' "${TARGET_CONTRACT}")"
MODE="$(MODE="${MODE}" node --input-type=module -e 'import { requireExactMode } from "./performance/k6/poll-settlement/single-mode-contract.mjs"; process.stdout.write(requireExactMode(process.env.MODE));')"
for command in node docker curl k6; do
	command -v "${command}" >/dev/null || { echo "Missing command: ${command}" >&2; exit 2; }
done
[[ "${PERF_DATASET_ID}" =~ ^PERFORMANCE_[A-Z0-9_-]{4,48}$ ]] || { echo 'Invalid PERF_DATASET_ID.' >&2; exit 2; }
[[ "${PERF_FIXTURE_RUN_ID}" =~ ^[A-Z0-9][A-Z0-9_-]{3,39}$ ]] || { echo 'Invalid PERF_FIXTURE_RUN_ID.' >&2; exit 2; }
[[ "${PERF_EXECUTION_RUN_ID}" =~ ^EXEC192_[A-Z0-9_-]{4,40}$ ]] || { echo 'Invalid PERF_EXECUTION_RUN_ID.' >&2; exit 2; }
[[ "${TOKEN_EXPIRY_SAFETY_SECONDS}" =~ ^[1-9][0-9]*$ ]] || { echo 'Invalid TOKEN_EXPIRY_SAFETY_SECONDS.' >&2; exit 2; }
for name in MAINTENANCE_POLL_INTERVAL_SECONDS MAINTENANCE_QUIET_SECONDS MAINTENANCE_TIMEOUT_SECONDS; do
	[[ "${!name}" =~ ^[1-9][0-9]*$ ]] || { echo "Invalid ${name}." >&2; exit 2; }
done

read_target() {
	node -e 'let value=require(process.argv[1]); for (const key of process.argv[2].split(".")) value=value[key]; if (value===undefined) process.exit(2); console.log(value)' "${TARGET_CONTRACT}" "$1"
}
EXPECTED_BASE_URL="$(read_target baseUrl)"
EXPECTED_FLYWAY="$(read_target flywayVersion)"
COMPOSE_PROJECT="$(read_target containers.app.composeProject)"
POSTGRES_CONTAINER_ID="$(read_target containers.postgres.id)"
POSTGRES_USER="$(read_target database.user)"
POSTGRES_DB="$(read_target database.name)"
SAMPLING_INTERVAL_MS="$(read_target resourceSampling.samplingIntervalMs)"
MAX_GAP_MS="$(read_target resourceSampling.maxGapMs)"
EXPECTED_MAINTENANCE_POLL_INTERVAL_SECONDS="$(read_target maintenanceReadiness.pollIntervalSeconds)"
EXPECTED_MAINTENANCE_QUIET_SECONDS="$(read_target maintenanceReadiness.quietSeconds)"
EXPECTED_MAINTENANCE_TIMEOUT_SECONDS="$(read_target maintenanceReadiness.timeoutSeconds)"
[[ "${BASE_URL}" == "${EXPECTED_BASE_URL}" ]] || { echo 'BASE_URL does not match target contract.' >&2; exit 2; }
[[ "${COMPOSE_PROJECT}" =~ ^[a-zA-Z0-9_-]+$ ]] || { echo 'Unsafe Compose project label.' >&2; exit 2; }
[[ "${MAX_GAP_MS}" =~ ^[1-9][0-9]*$ && "${MAX_GAP_MS}" -ge "${SAMPLING_INTERVAL_MS}" ]] || { echo 'Invalid resource sampling contract.' >&2; exit 2; }
[[ "${MAINTENANCE_POLL_INTERVAL_SECONDS}" == "${EXPECTED_MAINTENANCE_POLL_INTERVAL_SECONDS}" && "${MAINTENANCE_QUIET_SECONDS}" == "${EXPECTED_MAINTENANCE_QUIET_SECONDS}" && "${MAINTENANCE_TIMEOUT_SECONDS}" == "${EXPECTED_MAINTENANCE_TIMEOUT_SECONDS}" ]] || { echo 'Maintenance readiness does not match target contract.' >&2; exit 2; }

REPORT_ROOT="$(node -e 'console.log(require("path").resolve(process.argv[1]))' "${REPORT_ROOT}")"
MANIFEST_PATH="${REPORT_ROOT}/fixtures/${PERF_FIXTURE_RUN_ID}/manifest.json"
RUN_DIR="${REPORT_ROOT}/runs/${PERF_EXECUTION_RUN_ID}"
[[ ! -e "${RUN_DIR}" ]] || { echo 'Execution report directory already exists.' >&2; exit 2; }
[[ ! -e "${MANIFEST_PATH}" ]] || { echo 'Fixture manifest already exists; a fresh fixtureRunId is required.' >&2; exit 2; }

node --test \
	"${PROJECT_ROOT}/performance/k6/poll-settlement-contract.test.mjs" \
	"${SCRIPT_DIR}/capture-db-evidence.test.mjs" \
	"${SCRIPT_DIR}/capture-resource-sample.test.mjs" \
	"${SCRIPT_DIR}/maintenance-quiet.test.mjs" \
	"${SCRIPT_DIR}/maintenance-readiness.test.mjs" \
	"${SCRIPT_DIR}/single-mode-protocol.test.mjs" \
	"${SCRIPT_DIR}/bundle-results.test.mjs" \
	"${SCRIPT_DIR}/compare-bundles.test.mjs" \
	"${SCRIPT_DIR}/run-baseline-fail-fast.test.mjs" \
	"${SCRIPT_DIR}/run-baseline-sampler.test.mjs" \
	"${SCRIPT_DIR}/evidence-contract.test.mjs" \
	"${SCRIPT_DIR}/resource-contract.test.mjs" \
	"${SCRIPT_DIR}/summarize-results.test.mjs"
for script in seed-fixtures.mjs verify-baseline.mjs summarize-results.mjs bundle-results.mjs compare-bundles.mjs single-mode-contract.mjs capture-runtime.mjs capture-db-evidence.mjs capture-resource-sample.mjs evidence-contract.mjs resource-contract.mjs maintenance-quiet-contract.mjs wait-maintenance-quiet.mjs wait-maintenance-readiness.mjs validate-evidence.mjs prepare-measured-token.mjs; do
	node --check "${SCRIPT_DIR}/${script}"
done
k6 inspect -e BASE_URL="${BASE_URL}" -e MODE="${MODE}" -e PHASE=measured "${SCRIPT_DIR}/settlement-baseline.js" >/dev/null

case_env() {
	PERF_DATASET_ID="${PERF_DATASET_ID}" PERF_FIXTURE_RUN_ID="${PERF_FIXTURE_RUN_ID}" PERF_EXECUTION_RUN_ID="${PERF_EXECUTION_RUN_ID}" "$@"
}
capture_runtime() {
	local output="$1" phase="$2"
	TARGET_CONTRACT="${TARGET_CONTRACT}" EVIDENCE_PHASE="${phase}" case_env node "${SCRIPT_DIR}/capture-runtime.mjs" > "${output}"
}
validate_runtime_initial() {
	case_env node "${SCRIPT_DIR}/validate-evidence.mjs" runtime-initial "${TARGET_CONTRACT}" "$1"
}
validate_runtime_boundary() {
	case_env node "${SCRIPT_DIR}/validate-evidence.mjs" runtime "${TARGET_CONTRACT}" "${RUN_DIR}/runtime-initial.json" "$1" "$2"
}
capture_db() {
	local output="$1" scope="$2" mode="${3:-}"
	if [[ "${scope}" == global ]]; then
		TARGET_CONTRACT="${TARGET_CONTRACT}" EVIDENCE_SCOPE=global case_env env -u MODE node "${SCRIPT_DIR}/capture-db-evidence.mjs" > "${output}"
		case_env env -u MODE node "${SCRIPT_DIR}/validate-evidence.mjs" db-snapshot "${output}" global
	else
		TARGET_CONTRACT="${TARGET_CONTRACT}" EVIDENCE_SCOPE=mode MODE="${mode}" case_env node "${SCRIPT_DIR}/capture-db-evidence.mjs" > "${output}"
		case_env node "${SCRIPT_DIR}/validate-evidence.mjs" db-snapshot "${output}" "${mode}"
	fi
}
capture_resource() {
	local output="$1" mode="$2" stage="$3"
	TARGET_CONTRACT="${TARGET_CONTRACT}" RESOURCE_OUTPUT="${output}" MODE="${mode}" RESOURCE_REJECTION_PATH="${RUN_DIR}/baseline-adoption.json" RESOURCE_STAGE="${stage}" \
		case_env node "${SCRIPT_DIR}/capture-resource-sample.mjs"
}

INITIAL_TEMP="$(mktemp /tmp/faithlog-192-runtime-initial.XXXXXX)"
capture_runtime "${INITIAL_TEMP}" initial
validate_runtime_initial "${INITIAL_TEMP}"

LOCK_DIR="/tmp/faithlog-performance-${COMPOSE_PROJECT}.lock"
if ! mkdir "${LOCK_DIR}" 2>/dev/null; then
	echo "Another performance runner owns ${LOCK_DIR}." >&2
	exit 2
fi
LOCK_INODE="$(stat -f '%d:%i' "${LOCK_DIR}")"
SAMPLER_PID=''
SAMPLE_MARKER=''
TOKEN_DIR=''
TOKEN_PATH=''
cleanup() {
	local status=$?
	if [[ -n "${SAMPLE_MARKER}" ]]; then rm -f "${SAMPLE_MARKER}"; fi
	if [[ -n "${SAMPLER_PID}" ]]; then kill "${SAMPLER_PID}" 2>/dev/null || true; wait "${SAMPLER_PID}" 2>/dev/null || true; fi
	if [[ -n "${TOKEN_PATH}" && -f "${TOKEN_PATH}" ]]; then rm -f "${TOKEN_PATH}"; fi
	if [[ -n "${TOKEN_DIR}" && -d "${TOKEN_DIR}" ]]; then rmdir "${TOKEN_DIR}" 2>/dev/null || true; fi
	if [[ -f "${INITIAL_TEMP}" ]]; then rm -f "${INITIAL_TEMP}"; fi
	if [[ -d "${LOCK_DIR}" && "$(stat -f '%d:%i' "${LOCK_DIR}")" == "${LOCK_INODE}" ]]; then rmdir "${LOCK_DIR}"; fi
	if [[ ${status} -ne 0 && -d "${RUN_DIR}" && ! -e "${RUN_DIR}/baseline-adoption.json" ]]; then
		printf '{"case":{"datasetId":"%s","fixtureRunId":"%s","executionRunId":"%s","mode":"%s"},"accepted":false,"automaticAdoption":false,"evidenceIntegrity":"rejected","measurementStatus":"rejected","reasons":["runner-exit-%s"]}\n' "${PERF_DATASET_ID}" "${PERF_FIXTURE_RUN_ID}" "${PERF_EXECUTION_RUN_ID}" "${MODE}" "${status}" > "${RUN_DIR}/baseline-adoption.json"
	fi
	return "${status}"
}
trap cleanup EXIT

if pgrep -f '[k]6 run' >/dev/null; then
	echo 'Another k6 process is running.' >&2
	exit 2
fi
mkdir -p "${RUN_DIR}"
TOKEN_DIR="$(mktemp -d /tmp/faithlog-192-token.XXXXXX)"
mv "${INITIAL_TEMP}" "${RUN_DIR}/runtime-initial.json"
capture_runtime "${RUN_DIR}/runtime-post-lock.json" post-lock
validate_runtime_boundary "${RUN_DIR}/runtime-post-lock.json" post-lock
cp "${TARGET_CONTRACT}" "${RUN_DIR}/target-contract.json"

curl --silent --show-error --fail "${BASE_URL}/api/v1/health" > "${RUN_DIR}/health.json"
curl --silent --show-error --fail "${BASE_URL}/api-docs" > "${RUN_DIR}/api-docs.json"
node -e '
	const fs=require("fs"); const d=JSON.parse(fs.readFileSync(process.argv[1]));
	for (const {method,path} of [
		{method:"patch",path:"/api/v1/admin/campuses/{campusId}/polls/{pollId}/close"},
		{method:"post",path:"/api/v1/campuses/{campusId}/meal/polls/{pollId}/charges"}
	]) if (!d.paths?.[path]?.[method]) throw new Error(`Missing API contract: ${method.toUpperCase()} ${path}`);
' "${RUN_DIR}/api-docs.json"
FLYWAY="$(docker exec "${POSTGRES_CONTAINER_ID}" psql -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" -X -q -v ON_ERROR_STOP=1 -A -t -c 'SELECT max(installed_rank) FROM flyway_schema_history WHERE success;')"
[[ "${FLYWAY}" == "${EXPECTED_FLYWAY}" ]] || { echo 'Flyway version drift.' >&2; exit 2; }
capture_db "${RUN_DIR}/db-initial.json" global

TARGET_CONTRACT="${TARGET_CONTRACT}" BASE_URL="${BASE_URL}" MODE="${MODE}" PERF_DATASET_ID="${PERF_DATASET_ID}" PERF_FIXTURE_RUN_ID="${PERF_FIXTURE_RUN_ID}" PERF_PASSWORD="${PERF_PASSWORD}" REPORT_ROOT="${REPORT_ROOT}" node "${SCRIPT_DIR}/seed-fixtures.mjs"
cp "$(dirname "${MANIFEST_PATH}")/analyze-report.json" "${RUN_DIR}/fixture-analyze-report.json"
cp "${MANIFEST_PATH}" "${RUN_DIR}/fixture-manifest.json"

resource_loop() {
	local marker="$1" output="$2" mode="$3"
	while [[ -f "${marker}" ]]; do
		capture_resource "${output}" "${mode}" "${mode}-resource-sampler" || return $?
	done
	return 0
}
run_mode() {
	local mode="$1"
	local runtime_before="${RUN_DIR}/${mode}-runtime-before.json" runtime_after="${RUN_DIR}/${mode}-runtime-after.json"
	local db_before="${RUN_DIR}/${mode}-db-before.json" db_after="${RUN_DIR}/${mode}-db-after.json"
	local resources="${RUN_DIR}/${mode}-resources.ndjson" metrics="${RUN_DIR}/${mode}-k6-evidence.json"
	local warmup_evidence="${RUN_DIR}/${mode}-warmup-evidence.json"
	local readiness_evidence="${RUN_DIR}/${mode}-maintenance-readiness.json"
	capture_runtime "${runtime_before}" "${mode}-before" || return $?
	validate_runtime_boundary "${runtime_before}" "${mode}-before" || return $?
	TARGET_CONTRACT="${TARGET_CONTRACT}" MAINTENANCE_OUTPUT="${readiness_evidence}" MODE="${mode}" case_env node "${SCRIPT_DIR}/wait-maintenance-readiness.mjs" || return $?
	case_env node "${SCRIPT_DIR}/validate-evidence.mjs" maintenance-readiness "${TARGET_CONTRACT}" "${readiness_evidence}" "${mode}" || return $?
	set +e
	env -u TOKEN_PATH BASE_URL="${BASE_URL}" MODE="${mode}" PHASE=warmup MANIFEST_PATH="${MANIFEST_PATH}" PERF_PASSWORD="${PERF_PASSWORD}" \
		PERF_DATASET_ID="${PERF_DATASET_ID}" PERF_FIXTURE_RUN_ID="${PERF_FIXTURE_RUN_ID}" PERF_EXECUTION_RUN_ID="${PERF_EXECUTION_RUN_ID}" EVIDENCE_PATH="${warmup_evidence}" \
		k6 run "${SCRIPT_DIR}/settlement-baseline.js" > "${RUN_DIR}/${mode}-warmup-k6.log" 2>&1
	local warmup_status=$?
	set -e
	[[ ${warmup_status} -eq 0 ]] || return "${warmup_status}"
	case_env node "${SCRIPT_DIR}/validate-evidence.mjs" warmup "${warmup_evidence}" "${mode}" || return $?
	TOKEN_PATH="${TOKEN_DIR}/${mode}.jwt"
	TARGET_CONTRACT="${TARGET_CONTRACT}" MANIFEST_PATH="${MANIFEST_PATH}" BASE_URL="${BASE_URL}" MODE="${mode}" TOKEN_OUTPUT="${TOKEN_PATH}" PERF_PASSWORD="${PERF_PASSWORD}" \
		TOKEN_EXPIRY_SAFETY_SECONDS="${TOKEN_EXPIRY_SAFETY_SECONDS}" PERF_DATASET_ID="${PERF_DATASET_ID}" PERF_FIXTURE_RUN_ID="${PERF_FIXTURE_RUN_ID}" \
		node "${SCRIPT_DIR}/prepare-measured-token.mjs" || return $?
	capture_db "${db_before}" mode "${mode}" || return $?
	[[ ! -e "${resources}" ]] || { echo "Resource evidence already exists: ${mode}" >&2; return 2; }
	touch "${resources}" || return $?
	SAMPLE_MARKER="${RUN_DIR}/.${mode}.sampling"
	touch "${SAMPLE_MARKER}" || return $?
	capture_resource "${resources}" "${mode}" "${mode}-resource-initial" || return $?
	resource_loop "${SAMPLE_MARKER}" "${resources}" "${mode}" &
	SAMPLER_PID=$!
	set +e
	env -u PERF_PASSWORD BASE_URL="${BASE_URL}" MODE="${mode}" PHASE=measured MANIFEST_PATH="${MANIFEST_PATH}" TOKEN_PATH="${TOKEN_PATH}" \
		PERF_DATASET_ID="${PERF_DATASET_ID}" PERF_FIXTURE_RUN_ID="${PERF_FIXTURE_RUN_ID}" PERF_EXECUTION_RUN_ID="${PERF_EXECUTION_RUN_ID}" \
		EVIDENCE_PATH="${metrics}" \
		k6 run "${SCRIPT_DIR}/settlement-baseline.js" > "${RUN_DIR}/${mode}-k6.log" 2>&1
	local status=$?
	set -e
	rm -f "${TOKEN_PATH}" || return $?
	TOKEN_PATH=''
	rm -f "${SAMPLE_MARKER}" || return $?
	SAMPLE_MARKER=''
	wait "${SAMPLER_PID}" || return $?
	SAMPLER_PID=''
	capture_resource "${resources}" "${mode}" "${mode}-resource-final" || return $?
	capture_db "${db_after}" mode "${mode}" || return $?
	VALIDATION_REJECTION_PATH="${RUN_DIR}/baseline-adoption.json" VALIDATION_STAGE="${mode}-db-pair-validator" \
		case_env node "${SCRIPT_DIR}/validate-evidence.mjs" db-pair "${db_before}" "${db_after}" "${RUN_DIR}/db-initial.json" "${mode}" || return $?
	capture_runtime "${runtime_after}" "${mode}-after" || return $?
	validate_runtime_boundary "${runtime_after}" "${mode}-after" || return $?
	printf '{"case":{"datasetId":"%s","fixtureRunId":"%s","executionRunId":"%s","mode":"%s"},"exitStatus":%s}\n' "${PERF_DATASET_ID}" "${PERF_FIXTURE_RUN_ID}" "${PERF_EXECUTION_RUN_ID}" "${mode}" "${status}" > "${RUN_DIR}/${mode}-status.json" || return $?
	[[ ${status} -eq 0 ]] || return "${status}"
	VALIDATION_REJECTION_PATH="${RUN_DIR}/baseline-adoption.json" VALIDATION_STAGE="${mode}-mode-validator" \
		case_env node "${SCRIPT_DIR}/validate-evidence.mjs" mode "${TARGET_CONTRACT}" "${metrics}" "${resources}" "${mode}" || return $?
}

RUN_STATUS=0
if ! run_mode "${MODE}"; then RUN_STATUS=1; fi

if [[ ${RUN_STATUS} -eq 0 ]]; then
	TARGET_CONTRACT="${TARGET_CONTRACT}" MANIFEST_PATH="${MANIFEST_PATH}" RUN_DIR="${RUN_DIR}" BASE_URL="${BASE_URL}" MODE="${MODE}" PERF_PASSWORD="${PERF_PASSWORD}" POSTGRES_CONTAINER_ID="${POSTGRES_CONTAINER_ID}" PERF_EXECUTION_RUN_ID="${PERF_EXECUTION_RUN_ID}" node "${SCRIPT_DIR}/verify-baseline.mjs"
fi
capture_runtime "${RUN_DIR}/runtime-final.json" final
validate_runtime_boundary "${RUN_DIR}/runtime-final.json" final
[[ ${RUN_STATUS} -eq 0 ]] || exit 1
set +e
MANIFEST_PATH="${MANIFEST_PATH}" RUN_DIR="${RUN_DIR}" TARGET_CONTRACT="${TARGET_CONTRACT}" PERF_EXECUTION_RUN_ID="${PERF_EXECUTION_RUN_ID}" MODE="${MODE}" node "${SCRIPT_DIR}/summarize-results.mjs"
SUMMARY_STATUS=$?
set -e
[[ ${RUN_STATUS} -eq 0 && ${SUMMARY_STATUS} -eq 0 ]] || exit 1
echo "Baseline reports: ${RUN_DIR}"
