#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPOSITORY_ROOT="$(cd "${SCRIPT_DIR}/../../.." && pwd)"
source "${SCRIPT_DIR}/guard-runtime.sh"

POSTGRES_USER="${POSTGRES_USER:-faithlog}"
POSTGRES_DB="${POSTGRES_DB:-faithlog}"
export POSTGRES_USER POSTGRES_DB
REPORT_ROOT="${REPOSITORY_ROOT}/build/reports/k6/notification-batch"
MANIFEST_PATH="${MANIFEST_PATH:-}"
RUN_ID="${RUN_ID:-}"
RUN_DIR="${REPORT_ROOT}/runs/${RUN_ID}"

guard_notification_batch_runtime

if [[ ! "${POSTGRES_USER}" =~ ^[A-Za-z_][A-Za-z0-9_]*$ \
	|| ! "${POSTGRES_DB}" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]]; then
	echo "POSTGRES_USER and POSTGRES_DB must be safe PostgreSQL identifiers." >&2
	exit 2
fi
if [[ ! "${RUN_ID}" =~ ^[A-Za-z0-9][A-Za-z0-9_-]{0,79}$ ]]; then
	echo "RUN_ID must be an explicit fresh 1-80 character safe identifier." >&2
	exit 2
fi
if [[ -z "${MANIFEST_PATH}" || ! -f "${MANIFEST_PATH}" ]]; then
	echo "MANIFEST_PATH must reference a prepared #198 fixture manifest." >&2
	exit 2
fi
MANIFEST_PATH="$(cd "$(dirname "${MANIFEST_PATH}")" && pwd)/$(basename "${MANIFEST_PATH}")"
case "${MANIFEST_PATH}" in
	"${REPORT_ROOT}/fixtures/"*) ;;
	*)
		echo "MANIFEST_PATH must stay under the ignored Issue #198 fixture report directory." >&2
		exit 2
		;;
esac

eval "$(node -e '
	const fs = require("node:fs");
	const manifest = JSON.parse(fs.readFileSync(process.argv[1], "utf8"));
	const values = {
		PERF_DATASET_ID: manifest.datasetId,
		PERF_FIXTURE_RUN_ID: manifest.fixtureRunId,
		PERF_SAMPLE_KIND: manifest.sampleKind,
		PERF_MANIFEST_COMPOSE_PROJECT: manifest.composeProject,
		PERF_MANIFEST_POSTGRES_DATABASE: manifest.postgresDatabase,
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

if [[ ! "${PERF_DATASET_ID}" =~ ^PERFORMANCE_[A-Za-z0-9_-]+$ \
	|| "${PERF_MEMBER_COUNT}" != "1000" \
	|| ! "${PERF_CAMPUS_ID}" =~ ^[1-9][0-9]*$ \
	|| "${PERF_MANIFEST_COMPOSE_PROJECT}" != "${PERF_COMPOSE_PROJECT}" \
	|| "${PERF_MANIFEST_POSTGRES_DATABASE}" != "${POSTGRES_DB}" ]]; then
	echo "Manifest dataset, campus, Compose project, or database violates the Issue #198 contract." >&2
	exit 2
fi

: "${PERF_BUSINESS_DATE:?Set an explicit YYYY-MM-DD business date for the dedupe key.}"
: "${PERF_DOCKER_STATS_SAMPLE_INTERVAL_SECONDS:?Set the user-approved Docker sampling cadence.}"
if [[ ! "${PERF_BUSINESS_DATE}" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}$ ]]; then
	echo "PERF_BUSINESS_DATE must use YYYY-MM-DD." >&2
	exit 2
fi
if [[ ! "${PERF_DOCKER_STATS_SAMPLE_INTERVAL_SECONDS}" =~ ^[1-9][0-9]*$ ]] \
	|| (( PERF_DOCKER_STATS_SAMPLE_INTERVAL_SECONDS > 60 )); then
	echo "PERF_DOCKER_STATS_SAMPLE_INTERVAL_SECONDS must be an approved integer from 1 through 60." >&2
	exit 2
fi

if [[ -n "$(git -C "${REPOSITORY_ROOT}" status --porcelain --untracked-files=all)" ]]; then
	echo "The Issue #198 runner requires a clean index and worktree so gitCommit identifies executed code." >&2
	exit 2
fi

acquire_notification_batch_locks

SAMPLER_MARKER=""
SAMPLER_PID=""
cleanup() {
	if [[ -n "${SAMPLER_MARKER}" ]]; then
		rm -f "${SAMPLER_MARKER}"
	fi
	if [[ -n "${SAMPLER_PID}" ]]; then
		wait "${SAMPLER_PID}" 2>/dev/null || true
	fi
	release_notification_batch_locks
}
trap cleanup EXIT

mkdir -p "${REPORT_ROOT}/runs"
if ! mkdir "${RUN_DIR}" 2>/dev/null; then
	echo "RUN_ID already exists; use a fresh run ID instead of merging evidence." >&2
	exit 2
fi
cp "${MANIFEST_PATH}" "${RUN_DIR}/manifest.json"
printf '%s\n' "${RUN_DIR}" > "${REPORT_ROOT}/latest-run.txt"
GIT_COMMIT="$(git -C "${REPOSITORY_ROOT}" rev-parse HEAD)"

bash "${SCRIPT_DIR}/capture-runtime-identity.sh" "${RUN_DIR}/runtime-identity-initial.json"
PERF_POSTGRES_CONTAINER_ID="$(node -p \
	'require(process.argv[1]).postgres.container.id' "${RUN_DIR}/runtime-identity-initial.json")"
PERF_REDIS_CONTAINER_ID="$(node -p \
	'require(process.argv[1]).redis.container.id' "${RUN_DIR}/runtime-identity-initial.json")"
printf '{"springProfile":"%s","fcmAdapter":"%s","postgresContainer":"%s","postgresContainerId":"%s","redisContainer":"%s","redisContainerId":"%s","dockerProject":"%s","composeLabel":"com.docker.compose.project","postgresHost":"127.0.0.1","postgresHostPort":%s,"postgresDatabase":"%s","redisHost":"127.0.0.1","redisHostPort":%s,"postgresImageId":"%s","redisImageId":"%s","gitCommit":"%s","businessDate":"%s","executionModel":"cold-jvm-per-sample","warmupScope":"external-postgres-redis-cache-only","externalEvidenceWindow":"gradle-spring-harness-lifecycle","dockerStatsSampleIntervalSeconds":%s,"sharedStack":false,"externalFcm":false}\n' \
	"${PERF_SPRING_PROFILE}" \
	"${PERF_FCM_ADAPTER}" \
	"${POSTGRES_CONTAINER}" \
	"${PERF_POSTGRES_CONTAINER_ID}" \
	"${REDIS_CONTAINER}" \
	"${PERF_REDIS_CONTAINER_ID}" \
	"${PERF_COMPOSE_PROJECT}" \
	"${PERF_POSTGRES_HOST_PORT}" \
	"${POSTGRES_DB}" \
	"${PERF_REDIS_HOST_PORT}" \
	"${PERF_POSTGRES_IMAGE_ID}" \
	"${PERF_REDIS_IMAGE_ID}" \
	"${GIT_COMMIT}" \
	"${PERF_BUSINESS_DATE}" \
	"${PERF_DOCKER_STATS_SAMPLE_INTERVAL_SECONDS}" \
	> "${RUN_DIR}/environment.json"

snapshot_postgres() {
	local output="$1"
	docker exec "${POSTGRES_CONTAINER}" psql \
		-h 127.0.0.1 -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" -X -q -A -t \
		-c "SELECT json_build_object(
			'capturedAt', clock_timestamp(),
			'currentDatabase', current_database(),
			'statsReset', (SELECT stats_reset
				FROM pg_stat_database WHERE datname = current_database()),
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
			) table_stats),
			'cardinality', json_build_object(
				'userFcmTokensTotal', (SELECT count(*) FROM user_fcm_tokens),
				'activeTokensTotal', (SELECT count(*) FROM user_fcm_tokens WHERE is_active = TRUE),
				'issue198DummyTokensTotal', (
					SELECT count(*) FROM user_fcm_tokens WHERE token LIKE 'PERFORMANCE_198_DUMMY:%'
				),
				'issue198ActiveDummyTokens', (
					SELECT count(*) FROM user_fcm_tokens
					WHERE is_active = TRUE AND token LIKE 'PERFORMANCE_198_DUMMY:%'
				),
				'notificationLogsTotal', (SELECT count(*) FROM notification_logs),
				'issue198MarkerLogsTotal', (
					SELECT count(*) FROM notification_logs WHERE title LIKE 'PERFORMANCE #198 %'
				)
			),
			'relationBytes', json_build_object(
				'userFcmTokens', pg_total_relation_size('user_fcm_tokens'),
				'notificationLogs', pg_total_relation_size('notification_logs')
			)
		);" > "${output}"
}

snapshot_redis() {
	local output="$1"
	local dbsize server_info commandstats captured_at
	dbsize="$(docker exec "${REDIS_CONTAINER}" redis-cli --raw DBSIZE)"
	server_info="$(docker exec "${REDIS_CONTAINER}" redis-cli --raw INFO server)"
	commandstats="$(docker exec "${REDIS_CONTAINER}" redis-cli --raw INFO commandstats)"
	captured_at="$(node -p 'new Date().toISOString()')"
	REDIS_DBSIZE="${dbsize}" REDIS_SERVER_INFO="${server_info}" \
	REDIS_COMMANDSTATS="${commandstats}" REDIS_CAPTURED_AT="${captured_at}" \
	node -e '
		const assert = require("node:assert/strict");
		const value = (text, name) => {
			const match = text.match(new RegExp(`^${name}:(.+)\\r?$`, "m"));
			assert.ok(match, `Redis evidence missing ${name}`);
			return match[1].trim();
		};
		const integer = (raw, name, minimum = 0) => {
			assert.match(raw, /^[0-9]+$/, `${name} must be numeric`);
			const parsed = Number(raw);
			assert.ok(Number.isSafeInteger(parsed) && parsed >= minimum, `${name} is invalid`);
			return parsed;
		};
		const setCalls = process.env.REDIS_COMMANDSTATS.match(/^cmdstat_set:calls=(\d+),/m)?.[1] ?? "0";
		const snapshot = {
			capturedAt: process.env.REDIS_CAPTURED_AT,
			runId: value(process.env.REDIS_SERVER_INFO, "run_id"),
			uptimeSeconds: integer(value(process.env.REDIS_SERVER_INFO, "uptime_in_seconds"), "uptime", 1),
			tcpPort: integer(value(process.env.REDIS_SERVER_INFO, "tcp_port"), "tcp_port", 1),
			dbSize: integer(process.env.REDIS_DBSIZE, "DBSIZE"),
			commands: { set: integer(setCalls, "SET calls") },
		};
		process.stdout.write(`${JSON.stringify(snapshot)}\n`);
	' > "${output}"
}

timestamp_now() {
	node -p 'new Date().toISOString()'
}

append_docker_stats() {
	local output="$1"
	local captured_at postgres_stats redis_stats
	captured_at="$(timestamp_now)"
	postgres_stats="$(docker stats --no-stream --format '{{.CPUPerc}},{{.MemUsage}},{{.MemPerc}}' \
		"${POSTGRES_CONTAINER}")"
	redis_stats="$(docker stats --no-stream --format '{{.CPUPerc}},{{.MemUsage}},{{.MemPerc}}' \
		"${REDIS_CONTAINER}")"
	printf '%s,%s,%s,%s\n' "${captured_at}" "${POSTGRES_CONTAINER}" \
		"${PERF_POSTGRES_CONTAINER_ID}" "${postgres_stats}" >> "${output}"
	printf '%s,%s,%s,%s\n' "${captured_at}" "${REDIS_CONTAINER}" \
		"${PERF_REDIS_CONTAINER_ID}" "${redis_stats}" >> "${output}"
}

sample_docker_stats() {
	local marker="$1"
	local output="$2"
	while [[ -f "${marker}" ]]; do
		sleep "${PERF_DOCKER_STATS_SAMPLE_INTERVAL_SECONDS}"
		[[ -f "${marker}" ]] || break
		append_docker_stats "${output}"
	done
}

snapshot_postgres "${RUN_DIR}/postgres-before.json"
snapshot_redis "${RUN_DIR}/redis-before.json"
bash "${SCRIPT_DIR}/capture-runtime-identity.sh" "${RUN_DIR}/runtime-identity-before.json"

SAMPLER_MARKER="${RUN_DIR}/.sampling"
touch "${SAMPLER_MARKER}"
printf 'captured_at,container_name,container_id,cpu_percent,memory_usage,memory_percent\n' \
	> "${RUN_DIR}/docker-stats.csv"
append_docker_stats "${RUN_DIR}/docker-stats.csv"
WORKLOAD_STARTED_AT="$(timestamp_now)"
sample_docker_stats "${SAMPLER_MARKER}" "${RUN_DIR}/docker-stats.csv" &
SAMPLER_PID=$!

set +e
(
	cd "${REPOSITORY_ROOT}"
	export ALLOW_NOTIFICATION_BATCH_BASELINE=true
	export PERF_SPRING_PROFILE PERF_FCM_ADAPTER PERF_COMPOSE_PROJECT PERF_EXPECTED_COMPOSE_PROJECT
	export PERF_POSTGRES_HOST_PORT PERF_REDIS_HOST_PORT POSTGRES_DB
	export PERF_DATASET_ID PERF_FIXTURE_RUN_ID PERF_CAMPUS_ID PERF_MEMBER_COUNT
	export PERF_SAMPLE_KIND
	export PERF_SUCCESS_COUNT PERF_TRANSIENT_COUNT PERF_PERMANENT_COUNT
	export PERF_INACTIVE_COUNT PERF_NO_TOKEN_COUNT PERF_BUSINESS_DATE
	export PERF_REPORT_DIR="${RUN_DIR}"
	export SPRING_DATASOURCE_URL="jdbc:postgresql://127.0.0.1:${PERF_POSTGRES_HOST_PORT}/${POSTGRES_DB}"
	export SPRING_DATA_REDIS_HOST="127.0.0.1"
	export SPRING_DATA_REDIS_PORT="${PERF_REDIS_HOST_PORT}"
	export SPRING_JPA_HIBERNATE_DDL_AUTO="validate"
	export SPRING_FLYWAY_ENABLED="false"
	./gradlew --no-daemon --rerun-tasks test \
		--tests com.faithlog.performance.notification.NotificationBatchBeforeScenarioTest
) > "${RUN_DIR}/gradle-scenario.log" 2>&1
GRADLE_STATUS=$?
set -e
WORKLOAD_FINISHED_AT="$(timestamp_now)"
bash "${SCRIPT_DIR}/capture-runtime-identity.sh" "${RUN_DIR}/runtime-identity-after.json"

rm -f "${SAMPLER_MARKER}"
wait "${SAMPLER_PID}"
SAMPLER_MARKER=""
SAMPLER_PID=""
append_docker_stats "${RUN_DIR}/docker-stats.csv"
printf '{"workloadStartedAt":"%s","workloadFinishedAt":"%s","dockerStatsSampleIntervalSeconds":%s}\n' \
	"${WORKLOAD_STARTED_AT}" "${WORKLOAD_FINISHED_AT}" \
	"${PERF_DOCKER_STATS_SAMPLE_INTERVAL_SECONDS}" > "${RUN_DIR}/evidence-window.json"

snapshot_postgres "${RUN_DIR}/postgres-after.json"
snapshot_redis "${RUN_DIR}/redis-after.json"
bash "${SCRIPT_DIR}/capture-runtime-identity.sh" "${RUN_DIR}/runtime-identity-final.json"

set +e
RUN_DIR="${RUN_DIR}" node "${SCRIPT_DIR}/assert-runtime-continuity.mjs"
CONTINUITY_STATUS=$?
set -e
if [[ ${CONTINUITY_STATUS} -ne 0 ]]; then
	printf '{"status":"failed","phase":"runtime-continuity","exitCode":%s}\n' \
		"${CONTINUITY_STATUS}" > "${RUN_DIR}/run-status.json"
	exit "${CONTINUITY_STATUS}"
fi

if [[ ${GRADLE_STATUS} -ne 0 ]]; then
	printf '{"status":"failed","phase":"gradle-scenario","exitCode":%s}\n' \
		"${GRADLE_STATUS}" > "${RUN_DIR}/run-status.json"
	echo "#198 scenario failed; inspect ${RUN_DIR}/gradle-scenario.log" >&2
	exit "${GRADLE_STATUS}"
fi

set +e
MANIFEST_PATH="${RUN_DIR}/manifest.json" RUN_DIR="${RUN_DIR}" \
	node "${SCRIPT_DIR}/verify-before.mjs"
VERIFY_STATUS=$?
set -e
if [[ ${VERIFY_STATUS} -ne 0 ]]; then
	printf '{"status":"failed","phase":"verification","exitCode":%s}\n' \
		"${VERIFY_STATUS}" > "${RUN_DIR}/run-status.json"
	exit "${VERIFY_STATUS}"
fi
printf '{"status":"verified","phase":"complete","exitCode":0}\n' > "${RUN_DIR}/run-status.json"

echo "Before reports: ${RUN_DIR}"
