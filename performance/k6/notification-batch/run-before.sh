#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPOSITORY_ROOT="$(cd "${SCRIPT_DIR}/../../.." && pwd)"
source "${SCRIPT_DIR}/guard-runtime.sh"

POSTGRES_USER="${POSTGRES_USER:-faithlog}"
POSTGRES_DB="${POSTGRES_DB:-faithlog}"
REPORT_ROOT="${REPORT_ROOT:-build/reports/k6/notification-batch}"
MANIFEST_PATH="${MANIFEST_PATH:-}"
RUN_ID="${RUN_ID:-$(date -u +%Y%m%dT%H%M%SZ)}"
RUN_DIR="${REPORT_ROOT}/runs/${RUN_ID}"
LOCK_DIR="build/reports/k6/active-measurement.lock"

guard_notification_batch_runtime

if [[ -z "${MANIFEST_PATH}" || ! -f "${MANIFEST_PATH}" ]]; then
	echo "MANIFEST_PATH must reference a prepared #198 fixture manifest." >&2
	exit 2
fi
MANIFEST_PATH="$(cd "$(dirname "${MANIFEST_PATH}")" && pwd)/$(basename "${MANIFEST_PATH}")"

eval "$(node -e '
	const fs = require("node:fs");
	const manifest = JSON.parse(fs.readFileSync(process.argv[1], "utf8"));
	const values = {
		PERF_DATASET_ID: manifest.datasetId,
		PERF_FIXTURE_RUN_ID: manifest.fixtureRunId,
		PERF_SAMPLE_KIND: manifest.sampleKind,
		PERF_CAMPUS_ID: manifest.campusId,
		PERF_MEMBER_COUNT: manifest.memberCount,
		PERF_SUCCESS_COUNT: manifest.successCount,
		PERF_TRANSIENT_COUNT: manifest.transientCount,
		PERF_PERMANENT_COUNT: manifest.permanentCount,
		PERF_INACTIVE_COUNT: manifest.inactiveCount,
		PERF_NO_TOKEN_COUNT: manifest.noTokenCount,
	};
	for (const [key, value] of Object.entries(values)) {
		if (value === undefined || value === null || !String(value).match(/^[A-Za-z0-9_-]+$/)) {
			throw new Error(`Unsafe or missing manifest field: ${key}`);
		}
		process.stdout.write(`export ${key}=${value}\n`);
	}
' "${MANIFEST_PATH}")"

: "${PERF_BUSINESS_DATE:?Set an explicit YYYY-MM-DD business date for the dedupe key.}"
if [[ ! "${PERF_BUSINESS_DATE}" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}$ ]]; then
	echo "PERF_BUSINESS_DATE must use YYYY-MM-DD." >&2
	exit 2
fi

if ! mkdir "${LOCK_DIR}" 2>/dev/null; then
	echo "Another performance measurement lock exists; do not run shared load tests in parallel." >&2
	exit 2
fi

SAMPLER_MARKER=""
SAMPLER_PID=""
cleanup() {
	if [[ -n "${SAMPLER_MARKER}" ]]; then
		rm -f "${SAMPLER_MARKER}"
	fi
	if [[ -n "${SAMPLER_PID}" ]]; then
		wait "${SAMPLER_PID}" 2>/dev/null || true
	fi
	rmdir "${LOCK_DIR}" 2>/dev/null || true
}
trap cleanup EXIT

mkdir -p "${RUN_DIR}"
cp "${MANIFEST_PATH}" "${RUN_DIR}/manifest.json"
printf '%s\n' "${RUN_DIR}" > "${REPORT_ROOT}/latest-run.txt"
printf '{"springProfile":"%s","fcmAdapter":"%s","postgresContainer":"%s","redisContainer":"%s","dockerProject":"%s","composeLabel":"com.docker.compose.project","sharedStack":false,"externalFcm":false}\n' \
	"${PERF_SPRING_PROFILE}" \
	"${PERF_FCM_ADAPTER}" \
	"${POSTGRES_CONTAINER}" \
	"${REDIS_CONTAINER}" \
	"${PERF_COMPOSE_PROJECT}" \
	> "${RUN_DIR}/environment.json"

snapshot_postgres() {
	local output="$1"
	docker exec "${POSTGRES_CONTAINER}" psql \
		-U "${POSTGRES_USER}" -d "${POSTGRES_DB}" -X -q -A -t \
		-c "SELECT json_build_object(
			'capturedAt', now(),
			'database', (SELECT row_to_json(database_stats) FROM (
				SELECT xact_commit, xact_rollback, blks_read, blks_hit,
					tup_returned, tup_fetched, tup_inserted, tup_updated, tup_deleted
				FROM pg_stat_database WHERE datname = current_database()
			) database_stats),
			'tables', (SELECT json_object_agg(relname, row_to_json(table_stats)) FROM (
				SELECT relname, seq_scan, seq_tup_read, idx_scan, idx_tup_fetch,
					n_tup_ins, n_tup_upd, n_tup_del
				FROM pg_stat_user_tables
				WHERE relname IN ('campus_members','user_fcm_tokens','notification_logs')
				ORDER BY relname
			) table_stats)
		);" > "${output}"
}

snapshot_redis() {
	local output="$1"
	docker exec "${REDIS_CONTAINER}" redis-cli --raw INFO commandstats > "${output}"
}

sample_docker_stats() {
	local marker="$1"
	local output="$2"
	printf 'captured_at,container,cpu_percent,memory_usage,memory_percent\n' > "${output}"
	while [[ -f "${marker}" ]]; do
		local captured_at
		captured_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
		docker stats --no-stream --format '{{.Name}},{{.CPUPerc}},{{.MemUsage}},{{.MemPerc}}' \
			"${POSTGRES_CONTAINER}" "${REDIS_CONTAINER}" \
			| while IFS= read -r row; do printf '%s,%s\n' "${captured_at}" "${row}"; done >> "${output}"
		sleep 1
	done
}

snapshot_postgres "${RUN_DIR}/postgres-before.json"
snapshot_redis "${RUN_DIR}/redis-commandstats-before.txt"

SAMPLER_MARKER="${RUN_DIR}/.sampling"
touch "${SAMPLER_MARKER}"
sample_docker_stats "${SAMPLER_MARKER}" "${RUN_DIR}/docker-stats.csv" &
SAMPLER_PID=$!

set +e
(
	cd "${REPOSITORY_ROOT}"
	export ALLOW_NOTIFICATION_BATCH_BASELINE=true
	export PERF_SPRING_PROFILE PERF_FCM_ADAPTER PERF_COMPOSE_PROJECT
	export PERF_DATASET_ID PERF_FIXTURE_RUN_ID PERF_CAMPUS_ID PERF_MEMBER_COUNT
	export PERF_SAMPLE_KIND
	export PERF_SUCCESS_COUNT PERF_TRANSIENT_COUNT PERF_PERMANENT_COUNT
	export PERF_INACTIVE_COUNT PERF_NO_TOKEN_COUNT PERF_BUSINESS_DATE
	export PERF_REPORT_DIR="${REPOSITORY_ROOT}/${RUN_DIR}"
	./gradlew --no-daemon --rerun-tasks test \
		--tests com.faithlog.performance.notification.NotificationBatchBeforeScenarioTest
) > "${RUN_DIR}/gradle-scenario.log" 2>&1
GRADLE_STATUS=$?
set -e

rm -f "${SAMPLER_MARKER}"
wait "${SAMPLER_PID}"
SAMPLER_MARKER=""
SAMPLER_PID=""

snapshot_postgres "${RUN_DIR}/postgres-after.json"
snapshot_redis "${RUN_DIR}/redis-commandstats-after.txt"

if [[ ${GRADLE_STATUS} -ne 0 ]]; then
	echo "#198 scenario failed; inspect ${RUN_DIR}/gradle-scenario.log" >&2
	exit "${GRADLE_STATUS}"
fi

MANIFEST_PATH="${RUN_DIR}/manifest.json" RUN_DIR="${RUN_DIR}" \
	node "${SCRIPT_DIR}/verify-before.mjs"

echo "Before reports: ${RUN_DIR}"
