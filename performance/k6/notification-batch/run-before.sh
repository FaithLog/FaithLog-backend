#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPOSITORY_ROOT="$(cd "${SCRIPT_DIR}/../../.." && pwd)"
source "${SCRIPT_DIR}/guard-runtime.sh"
source "${SCRIPT_DIR}/runner-lifecycle.sh"

CURRENT_DEVELOP_CONTRACT_PATH="${SCRIPT_DIR}/current-develop-contract.json"
export CURRENT_DEVELOP_CONTRACT_PATH
REPORT_ROOT="${PERF_REPORT_ROOT:-${REPOSITORY_ROOT}/build/reports/k6/notification-batch}"
MANIFEST_PATH="${MANIFEST_PATH:-}"
RUN_ID="${RUN_ID:-}"
RUN_DIR="${REPORT_ROOT}/runs/${RUN_ID}"

if [[ ! "${RUN_ID}" =~ ^[A-Za-z0-9][A-Za-z0-9_-]{0,79}$ ]]; then
	echo "RUN_ID must be an explicit fresh 1-80 character safe identifier." >&2
	exit 2
fi
REJECTION_PATH="${REPORT_ROOT}/rejections/${RUN_ID}.json"
REJECTION_STAGE="preflight"
REJECTION_REASON="runner-command-failed"
mkdir -p "${REPORT_ROOT}/rejections"
if [[ -e "${REJECTION_PATH}" ]]; then
	echo "RUN_ID was previously rejected; use a fresh run ID." >&2
	exit 2
fi
SAMPLER_MARKER=""
SAMPLER_PID=""
PRELOCK_HARNESS_SOURCE_PATH=""
PERF_GLOBAL_LOCK_DIR=""
PERF_PROJECT_LOCK_DIR=""
PERF_GLOBAL_LOCK_HELD=false
PERF_PROJECT_LOCK_HELD=false
install_notification_batch_runner_traps

if ! notification_batch_require_runtime_inputs \
	POSTGRES_USER POSTGRES_PASSWORD POSTGRES_DB PERF_EXPECTED_POSTGRES_ROLE \
	PERF_EXPECTED_HARNESS_HEAD PERF_EXPECTED_HARNESS_CONTRACT_DIGEST; then
	exit 2
fi
: "${POSTGRES_USER:?Set the runtime-approved direct owner JDBC role.}"
: "${POSTGRES_PASSWORD:?Set the runtime-only PostgreSQL credential.}"
: "${POSTGRES_DB:?Set the runtime-approved dedicated PostgreSQL database.}"
: "${PERF_EXPECTED_POSTGRES_ROLE:?Set the runtime-approved direct owner JDBC role for #202 continuity.}"
export POSTGRES_USER POSTGRES_PASSWORD POSTGRES_DB PERF_EXPECTED_POSTGRES_ROLE
if [[ ! "${PERF_EXPECTED_HARNESS_HEAD}" =~ ^[a-f0-9]{40}$ \
	|| ! "${PERF_EXPECTED_HARNESS_CONTRACT_DIGEST}" =~ ^[a-f0-9]{64}$ ]]; then
	echo "Approved harness HEAD and contract digest must be exact lowercase hex." >&2
	exit 2
fi
export PERF_EXPECTED_HARNESS_HEAD PERF_EXPECTED_HARNESS_CONTRACT_DIGEST

REJECTION_REASON="current-develop-source-identity-failed"
node "${SCRIPT_DIR}/verify-current-develop-contract.mjs" >/dev/null
REJECTION_REASON="runtime-target-validation-failed"
guard_notification_batch_runtime
REJECTION_REASON="manifest-or-run-input-invalid"

if [[ ! "${POSTGRES_USER}" =~ ^[A-Za-z_][A-Za-z0-9_]*$ \
	|| ! "${POSTGRES_DB}" =~ ^[A-Za-z_][A-Za-z0-9_]*$ \
	|| ! "${PERF_EXPECTED_POSTGRES_ROLE}" =~ ^[A-Za-z_][A-Za-z0-9_]*$ \
	|| "${POSTGRES_USER}" != "${PERF_EXPECTED_POSTGRES_ROLE}" ]]; then
	echo "POSTGRES_USER and POSTGRES_DB must be safe PostgreSQL identifiers." >&2
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
	const assert = require("node:assert/strict");
	const fs = require("node:fs");
	const manifest = JSON.parse(fs.readFileSync(process.argv[1], "utf8"));
	const expectedKeys = [
		"datasetId", "fixtureRunId", "sampleKind", "composeProject", "postgresDatabase", "campusId",
		"memberCount", "successCount", "transientCount", "permanentCount", "inactiveCount", "noTokenCount",
		"mixedTokenUserCount", "insertedDummyTokenCount", "fixturePolicy", "credentialRecorded",
	];
	assert.deepEqual(Object.keys(manifest).sort(), expectedKeys.sort(), "Fixture manifest schema mismatch");
	assert.equal(manifest.fixturePolicy, "dummy-token-and-generated-log-only");
	assert.equal(manifest.credentialRecorded, false);
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
		PERF_MANIFEST_MIXED_TOKEN_USER_COUNT: manifest.mixedTokenUserCount,
		PERF_MANIFEST_INSERTED_DUMMY_TOKEN_COUNT: manifest.insertedDummyTokenCount,
	};
	for (const [key, value] of Object.entries(values)) {
		if (value === undefined || value === null || !String(value).match(/^[A-Za-z0-9_-]+$/)) {
			throw new Error(`Unsafe or missing manifest field: ${key}`);
		}
		process.stdout.write(`export ${key}=${value}\n`);
	}
' "${MANIFEST_PATH}")"

for count in \
	"${PERF_SUCCESS_COUNT}" "${PERF_TRANSIENT_COUNT}" "${PERF_PERMANENT_COUNT}" \
	"${PERF_INACTIVE_COUNT}" "${PERF_NO_TOKEN_COUNT}"; do
	if [[ ! "${count}" =~ ^[1-9][0-9]*$ ]]; then
		echo "Manifest outcome counts must be positive integers." >&2
		exit 2
	fi
done
if (( PERF_SUCCESS_COUNT + PERF_TRANSIENT_COUNT + PERF_PERMANENT_COUNT \
	+ PERF_INACTIVE_COUNT + PERF_NO_TOKEN_COUNT != PERF_MEMBER_COUNT )); then
	echo "Manifest outcome counts must total PERF_MEMBER_COUNT." >&2
	exit 2
fi

if [[ ! "${PERF_DATASET_ID}" =~ ^PERFORMANCE_[A-Za-z0-9_-]+$ \
	|| "${PERF_MEMBER_COUNT}" != "1000" \
	|| "${PERF_MANIFEST_MIXED_TOKEN_USER_COUNT}" != "1" \
	|| "${PERF_MANIFEST_INSERTED_DUMMY_TOKEN_COUNT}" != "$((PERF_MEMBER_COUNT - PERF_NO_TOKEN_COUNT + 1))" \
	|| ! "${PERF_CAMPUS_ID}" =~ ^[1-9][0-9]*$ \
	|| ( "${PERF_SAMPLE_KIND}" != "warmup" && "${PERF_SAMPLE_KIND}" != "measured" ) \
	|| "${PERF_FIXTURE_RUN_ID}" != "$(basename "$(dirname "${MANIFEST_PATH}")")" \
	|| "${PERF_MANIFEST_COMPOSE_PROJECT}" != "${PERF_COMPOSE_PROJECT}" \
	|| "${PERF_MANIFEST_POSTGRES_DATABASE}" != "${POSTGRES_DB}" ]]; then
	echo "Manifest dataset, campus, Compose project, or database violates the Issue #198 contract." >&2
	exit 2
fi

: "${PERF_BUSINESS_DATE:?Set an explicit YYYY-MM-DD business date for the dedupe key.}"
: "${PERF_DOCKER_STATS_SAMPLE_INTERVAL_SECONDS:?Set the user-approved Docker sampling cadence.}"
: "${PERF_DOCKER_STATS_MAX_GAP_MILLISECONDS:?Set the user-approved maximum Docker sampling gap.}"
if [[ ! "${PERF_BUSINESS_DATE}" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}$ ]]; then
	echo "PERF_BUSINESS_DATE must use YYYY-MM-DD." >&2
	exit 2
fi
if [[ ! "${PERF_DOCKER_STATS_SAMPLE_INTERVAL_SECONDS}" =~ ^[1-9][0-9]*$ ]] \
	|| (( PERF_DOCKER_STATS_SAMPLE_INTERVAL_SECONDS > 60 )); then
	echo "PERF_DOCKER_STATS_SAMPLE_INTERVAL_SECONDS must be an approved integer from 1 through 60." >&2
	exit 2
fi
if [[ ! "${PERF_DOCKER_STATS_MAX_GAP_MILLISECONDS}" =~ ^[1-9][0-9]*$ ]] \
	|| (( PERF_DOCKER_STATS_MAX_GAP_MILLISECONDS < PERF_DOCKER_STATS_SAMPLE_INTERVAL_SECONDS * 1000 )) \
	|| (( PERF_DOCKER_STATS_MAX_GAP_MILLISECONDS > 300000 )); then
	echo "PERF_DOCKER_STATS_MAX_GAP_MILLISECONDS must be an approved integer from cadence through 300000." >&2
	exit 2
fi

if [[ -n "$(git -C "${REPOSITORY_ROOT}" status --porcelain --untracked-files=all)" ]]; then
	echo "The Issue #198 runner requires a clean index and worktree so gitCommit identifies executed code." >&2
	exit 2
fi
PRELOCK_HARNESS_SOURCE_PATH="$(mktemp /tmp/faithlog-198-harness-source.XXXXXX)"
rm -f "${PRELOCK_HARNESS_SOURCE_PATH}"
node "${SCRIPT_DIR}/harness-provenance.mjs" capture \
	"${REPOSITORY_ROOT}" "${PRELOCK_HARNESS_SOURCE_PATH}"

REJECTION_STAGE="lock"
REJECTION_REASON="performance-lock-unavailable"
acquire_notification_batch_locks

mkdir -p "${REPORT_ROOT}/runs"
if ! mkdir "${RUN_DIR}" 2>/dev/null; then
	echo "RUN_ID already exists; use a fresh run ID instead of merging evidence." >&2
	exit 2
fi
mv "${PRELOCK_HARNESS_SOURCE_PATH}" "${RUN_DIR}/harness-source-prelock.json"
PRELOCK_HARNESS_SOURCE_PATH=""
REJECTION_STAGE="post-lock-runtime"
REJECTION_REASON="post-lock-runtime-identity-mismatch"
verify_notification_batch_runtime_after_lock "${RUN_DIR}/runtime-identity-locked.json"
node "${SCRIPT_DIR}/harness-provenance.mjs" capture \
	"${REPOSITORY_ROOT}" "${RUN_DIR}/harness-source-locked.json"
HARNESS_PROVENANCE_REPORT_PATH="${RUN_DIR}/harness-provenance-post-lock.json" \
	HARNESS_SOURCE_PHASES=prelock,locked HARNESS_ARTIFACT_PHASES= RUN_DIR="${RUN_DIR}" \
	HARNESS_PROVENANCE_MODE=source-only \
	node "${SCRIPT_DIR}/assert-harness-provenance-continuity.mjs"
cp "${MANIFEST_PATH}" "${RUN_DIR}/manifest.json"
printf '%s\n' "${RUN_DIR}" > "${REPORT_ROOT}/latest-run.txt"
GIT_COMMIT="$(git -C "${REPOSITORY_ROOT}" rev-parse HEAD)"

REJECTION_STAGE="pre-workload-continuity"
REJECTION_REASON="immutable-runtime-continuity-failed"
bash "${SCRIPT_DIR}/capture-runtime-identity.sh" "${RUN_DIR}/runtime-identity-initial.json"
RUNTIME_CONTINUITY_REPORT_PATH="${RUN_DIR}/runtime-continuity-post-lock.json" \
	RUNTIME_IDENTITY_PHASES=locked,initial RUN_DIR="${RUN_DIR}" \
	node "${SCRIPT_DIR}/assert-runtime-continuity.mjs"
PERF_POSTGRES_CONTAINER_ID="$(node -p \
	'require(process.argv[1]).postgres.container.id' "${RUN_DIR}/runtime-identity-initial.json")"
PERF_REDIS_CONTAINER_ID="$(node -p \
	'require(process.argv[1]).redis.container.id' "${RUN_DIR}/runtime-identity-initial.json")"
printf '{"springProfile":"%s","fcmAdapter":"%s","postgresContainer":"%s","postgresContainerId":"%s","redisContainer":"%s","redisContainerId":"%s","dockerProject":"%s","composeLabel":"com.docker.compose.project","postgresHost":"127.0.0.1","postgresHostPort":%s,"postgresDatabase":"%s","expectedPostgresRole":"%s","redisHost":"127.0.0.1","redisHostPort":%s,"postgresImageId":"%s","redisImageId":"%s","gitCommit":"%s","businessDate":"%s","executionModel":"cold-jvm-per-sample","warmupScope":"external-postgres-redis-cache-only","externalEvidenceWindow":"gradle-spring-harness-lifecycle","dockerStatsSampleIntervalSeconds":%s,"dockerStatsMaxGapMilliseconds":%s,"sharedStack":false,"externalFcm":false}\n' \
	"${PERF_SPRING_PROFILE}" \
	"${PERF_FCM_ADAPTER}" \
	"${POSTGRES_CONTAINER}" \
	"${PERF_POSTGRES_CONTAINER_ID}" \
	"${REDIS_CONTAINER}" \
	"${PERF_REDIS_CONTAINER_ID}" \
	"${PERF_COMPOSE_PROJECT}" \
	"${PERF_POSTGRES_HOST_PORT}" \
	"${POSTGRES_DB}" \
	"${PERF_EXPECTED_POSTGRES_ROLE}" \
	"${PERF_REDIS_HOST_PORT}" \
	"${PERF_POSTGRES_IMAGE_ID}" \
	"${PERF_REDIS_IMAGE_ID}" \
	"${GIT_COMMIT}" \
	"${PERF_BUSINESS_DATE}" \
	"${PERF_DOCKER_STATS_SAMPLE_INTERVAL_SECONDS}" \
	"${PERF_DOCKER_STATS_MAX_GAP_MILLISECONDS}" \
	> "${RUN_DIR}/environment.json"

snapshot_postgres() {
	local output="$1"
	PGPASSWORD="${POSTGRES_PASSWORD}" docker exec -e PGPASSWORD "${PERF_POSTGRES_CONTAINER_ID}" psql \
		-h 127.0.0.1 -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" -X -q -A -t \
		-c "SELECT json_build_object(
			'capturedAt', clock_timestamp(),
			'currentDatabase', current_database(),
			'currentUser', current_user,
			'statsReset', (SELECT stats_reset
				FROM pg_stat_database WHERE datname = current_database()),
			'database', (SELECT row_to_json(database_stats) FROM (
				SELECT xact_commit::text, xact_rollback::text, blks_read::text, blks_hit::text,
					tup_returned::text, tup_fetched::text, tup_inserted::text, tup_updated::text,
					tup_deleted::text
				FROM pg_stat_database WHERE datname = current_database()
			) database_stats),
			'tables', (SELECT jsonb_object_agg(relname, to_jsonb(table_stats) - 'relname') FROM (
				SELECT relname, seq_scan::text, seq_tup_read::text, idx_scan::text, idx_tup_fetch::text,
					n_tup_ins::text, n_tup_upd::text, n_tup_del::text
				FROM pg_stat_user_tables
				WHERE relname IN ('campus_members','user_fcm_tokens','notification_logs')
				ORDER BY relname
			) table_stats),
			'cardinality', json_build_object(
				'userFcmTokensTotal', (SELECT count(*)::text FROM user_fcm_tokens),
				'activeTokensTotal', (SELECT count(*)::text FROM user_fcm_tokens WHERE is_active = TRUE),
				'issue198DummyTokensTotal', (
					SELECT count(*)::text FROM user_fcm_tokens
					WHERE starts_with(token, 'PERFORMANCE_198_DUMMY:')
				),
				'issue198ActiveDummyTokens', (
					SELECT count(*)::text FROM user_fcm_tokens
					WHERE is_active = TRUE AND starts_with(token, 'PERFORMANCE_198_DUMMY:')
				),
				'notificationLogsTotal', (SELECT count(*)::text FROM notification_logs),
				'issue198MarkerLogsTotal', (
					SELECT count(*)::text FROM notification_logs WHERE title LIKE 'PERFORMANCE #198 %'
				)
			),
			'relationBytes', json_build_object(
				'userFcmTokens', pg_total_relation_size('user_fcm_tokens')::text,
				'notificationLogs', pg_total_relation_size('notification_logs')::text
			)
		);" > "${output}"
}

snapshot_redis() {
	local output="$1"
	local dbsize server_info commandstats captured_at
	if [[ "${PERF_REDIS_AUTH_MODE}" == "password" ]]; then
		dbsize="$(REDISCLI_AUTH="${REDIS_PASSWORD}" docker exec -e REDISCLI_AUTH "${PERF_REDIS_CONTAINER_ID}" \
			redis-cli --no-auth-warning --raw DBSIZE)"
		server_info="$(REDISCLI_AUTH="${REDIS_PASSWORD}" docker exec -e REDISCLI_AUTH "${PERF_REDIS_CONTAINER_ID}" \
			redis-cli --no-auth-warning --raw INFO server)"
		commandstats="$(REDISCLI_AUTH="${REDIS_PASSWORD}" docker exec -e REDISCLI_AUTH "${PERF_REDIS_CONTAINER_ID}" \
			redis-cli --no-auth-warning --raw INFO commandstats)"
	else
		dbsize="$(docker exec "${PERF_REDIS_CONTAINER_ID}" redis-cli --raw DBSIZE)"
		server_info="$(docker exec "${PERF_REDIS_CONTAINER_ID}" redis-cli --raw INFO server)"
		commandstats="$(docker exec "${PERF_REDIS_CONTAINER_ID}" redis-cli --raw INFO commandstats)"
	fi
	captured_at="$(node -p 'new Date().toISOString()')"
	REDIS_DBSIZE="${dbsize}" REDIS_SERVER_INFO="${server_info}" \
	REDIS_COMMANDSTATS="${commandstats}" REDIS_CAPTURED_AT="${captured_at}" \
	REDIS_EVIDENCE_OUTPUT_PATH="${output}" node "${SCRIPT_DIR}/parse-redis-evidence.mjs"
}

timestamp_now() {
	node -p 'new Date().toISOString()'
}

append_docker_stats() {
	local output="$1"
	bash "${SCRIPT_DIR}/capture-docker-stats.sh" "${output}"
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

REJECTION_STAGE="gradle-harness-build"
REJECTION_REASON="harness-build-failed"
set +e
(
	cd "${REPOSITORY_ROOT}"
	./gradlew --no-daemon cleanTest testClasses
) > "${RUN_DIR}/gradle-harness-build.log" 2>&1
HARNESS_BUILD_STATUS=$?
set -e
if [[ ${HARNESS_BUILD_STATUS} -ne 0 ]]; then
	printf '{"status":"failed","accepted":false,"automaticAdoption":false,"phase":"gradle-harness-build","exitCode":%s}\n' \
		"${HARNESS_BUILD_STATUS}" > "${RUN_DIR}/run-status.json"
	exit "${HARNESS_BUILD_STATUS}"
fi
node "${SCRIPT_DIR}/harness-provenance.mjs" capture \
	"${REPOSITORY_ROOT}" "${RUN_DIR}/harness-source-preworkload.json"
node "${SCRIPT_DIR}/harness-artifact-provenance.mjs" capture \
	"${REPOSITORY_ROOT}" "${RUN_DIR}/harness-artifact-preworkload.json"
HARNESS_PROVENANCE_REPORT_PATH="${RUN_DIR}/harness-provenance-pre-workload.json" \
	HARNESS_SOURCE_PHASES=prelock,locked,preworkload HARNESS_ARTIFACT_PHASES= RUN_DIR="${RUN_DIR}" \
	HARNESS_PROVENANCE_MODE=source-only \
	node "${SCRIPT_DIR}/assert-harness-provenance-continuity.mjs"

snapshot_postgres "${RUN_DIR}/postgres-before.json"
PGSS_PHASE=before bash "${SCRIPT_DIR}/capture-pgss.sh" "${RUN_DIR}/pgss-before.json"
snapshot_redis "${RUN_DIR}/redis-before.json"
bash "${SCRIPT_DIR}/capture-runtime-identity.sh" "${RUN_DIR}/runtime-identity-before.json"
RUNTIME_CONTINUITY_REPORT_PATH="${RUN_DIR}/runtime-continuity-pre-workload.json" \
	RUNTIME_IDENTITY_PHASES=locked,initial,before RUN_DIR="${RUN_DIR}" \
	node "${SCRIPT_DIR}/assert-runtime-continuity.mjs"

SAMPLER_MARKER="${RUN_DIR}/.sampling"
touch "${SAMPLER_MARKER}"
	printf 'captured_at,component,container_name,container_id,cpu_percent,memory_used_bytes,memory_limit_bytes,memory_percent\n' \
	> "${RUN_DIR}/docker-stats.csv"
append_docker_stats "${RUN_DIR}/docker-stats.csv"
WORKLOAD_STARTED_AT="$(timestamp_now)"
sample_docker_stats "${SAMPLER_MARKER}" "${RUN_DIR}/docker-stats.csv" &
SAMPLER_PID=$!

set +e
REJECTION_STAGE="gradle-scenario"
REJECTION_REASON="scenario-contract-failed"
(
	cd "${REPOSITORY_ROOT}"
	export ALLOW_NOTIFICATION_BATCH_BASELINE=true
	export PERF_SPRING_PROFILE PERF_FCM_ADAPTER PERF_COMPOSE_PROJECT PERF_EXPECTED_COMPOSE_PROJECT
	export PERF_POSTGRES_HOST_PORT PERF_REDIS_HOST_PORT POSTGRES_USER POSTGRES_PASSWORD POSTGRES_DB
	export PERF_EXPECTED_POSTGRES_ROLE PERF_REDIS_AUTH_MODE
	if [[ "${PERF_REDIS_AUTH_MODE}" == "password" ]]; then
		export REDIS_PASSWORD
		export SPRING_DATA_REDIS_PASSWORD="${REDIS_PASSWORD}"
	fi
	export PERF_DATASET_ID PERF_FIXTURE_RUN_ID PERF_CAMPUS_ID PERF_MEMBER_COUNT
	export PERF_SAMPLE_KIND
	export PERF_SUCCESS_COUNT PERF_TRANSIENT_COUNT PERF_PERMANENT_COUNT
	export PERF_INACTIVE_COUNT PERF_NO_TOKEN_COUNT PERF_BUSINESS_DATE
	export PERF_REPORT_DIR="${RUN_DIR}"
	export SPRING_DATASOURCE_URL="jdbc:postgresql://127.0.0.1:${PERF_POSTGRES_HOST_PORT}/${POSTGRES_DB}"
	export SPRING_DATASOURCE_USERNAME="${POSTGRES_USER}"
	export SPRING_DATASOURCE_PASSWORD="${POSTGRES_PASSWORD}"
	export SPRING_DATA_REDIS_HOST="127.0.0.1"
	export SPRING_DATA_REDIS_PORT="${PERF_REDIS_HOST_PORT}"
	export SPRING_JPA_HIBERNATE_DDL_AUTO="validate"
	export SPRING_FLYWAY_ENABLED="false"
	./gradlew --no-daemon test \
		--tests com.faithlog.performance.notification.NotificationBatchBeforeScenarioTest
) > "${RUN_DIR}/gradle-scenario.log" 2>&1
GRADLE_STATUS=$?
set -e
WORKLOAD_FINISHED_AT="$(timestamp_now)"
REJECTION_STAGE="post-workload-evidence"
REJECTION_REASON="post-workload-evidence-capture-failed"
bash "${SCRIPT_DIR}/capture-runtime-identity.sh" "${RUN_DIR}/runtime-identity-after.json"
node "${SCRIPT_DIR}/harness-provenance.mjs" capture \
	"${REPOSITORY_ROOT}" "${RUN_DIR}/harness-source-postworkload.json"
node "${SCRIPT_DIR}/harness-artifact-provenance.mjs" capture \
	"${REPOSITORY_ROOT}" "${RUN_DIR}/harness-artifact-postworkload.json"

rm -f "${SAMPLER_MARKER}"
wait "${SAMPLER_PID}"
SAMPLER_MARKER=""
SAMPLER_PID=""
append_docker_stats "${RUN_DIR}/docker-stats.csv"
printf '{"workloadStartedAt":"%s","workloadFinishedAt":"%s","dockerStatsSampleIntervalSeconds":%s,"dockerStatsMaxGapMilliseconds":%s}\n' \
	"${WORKLOAD_STARTED_AT}" "${WORKLOAD_FINISHED_AT}" \
	"${PERF_DOCKER_STATS_SAMPLE_INTERVAL_SECONDS}" \
	"${PERF_DOCKER_STATS_MAX_GAP_MILLISECONDS}" > "${RUN_DIR}/evidence-window.json"

snapshot_postgres "${RUN_DIR}/postgres-after.json"
PGSS_PHASE=after bash "${SCRIPT_DIR}/capture-pgss.sh" "${RUN_DIR}/pgss-after.json"
snapshot_redis "${RUN_DIR}/redis-after.json"
bash "${SCRIPT_DIR}/capture-runtime-identity.sh" "${RUN_DIR}/runtime-identity-final.json"
node "${SCRIPT_DIR}/harness-provenance.mjs" capture \
	"${REPOSITORY_ROOT}" "${RUN_DIR}/harness-source-final.json"
node "${SCRIPT_DIR}/harness-artifact-provenance.mjs" capture \
	"${REPOSITORY_ROOT}" "${RUN_DIR}/harness-artifact-final.json"

set +e
RUNTIME_CONTINUITY_REPORT_PATH="${RUN_DIR}/runtime-continuity-report.json" \
	RUNTIME_IDENTITY_PHASES=locked,initial,before,after,final RUN_DIR="${RUN_DIR}" \
	node "${SCRIPT_DIR}/assert-runtime-continuity.mjs"
CONTINUITY_STATUS=$?
set -e
if [[ ${CONTINUITY_STATUS} -ne 0 ]]; then
	REJECTION_STAGE="runtime-continuity"
	REJECTION_REASON="immutable-runtime-continuity-failed"
	printf '{"status":"failed","accepted":false,"automaticAdoption":false,"phase":"runtime-continuity","exitCode":%s}\n' \
		"${CONTINUITY_STATUS}" > "${RUN_DIR}/run-status.json"
	exit "${CONTINUITY_STATUS}"
fi
set +e
HARNESS_PROVENANCE_REPORT_PATH="${RUN_DIR}/harness-provenance-report.json" \
	HARNESS_SOURCE_PHASES=prelock,locked,preworkload,postworkload,final \
	HARNESS_ARTIFACT_PHASES=preworkload,postworkload,final RUN_DIR="${RUN_DIR}" \
	HARNESS_PROVENANCE_MODE=full \
	node "${SCRIPT_DIR}/assert-harness-provenance-continuity.mjs"
HARNESS_PROVENANCE_STATUS=$?
set -e
if [[ ${HARNESS_PROVENANCE_STATUS} -ne 0 ]]; then
	REJECTION_STAGE="harness-provenance-continuity"
	REJECTION_REASON="executed-harness-provenance-changed"
	printf '{"status":"failed","accepted":false,"automaticAdoption":false,"phase":"harness-provenance-continuity","exitCode":%s}\n' \
		"${HARNESS_PROVENANCE_STATUS}" > "${RUN_DIR}/run-status.json"
	exit "${HARNESS_PROVENANCE_STATUS}"
fi

if [[ ${GRADLE_STATUS} -ne 0 ]]; then
	REJECTION_STAGE="gradle-scenario"
	REJECTION_REASON="scenario-contract-failed"
	printf '{"status":"failed","accepted":false,"automaticAdoption":false,"phase":"gradle-scenario","exitCode":%s}\n' \
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
	REJECTION_STAGE="verification"
	REJECTION_REASON="evidence-verification-failed"
	printf '{"status":"failed","accepted":false,"automaticAdoption":false,"phase":"verification","exitCode":%s}\n' \
		"${VERIFY_STATUS}" > "${RUN_DIR}/run-status.json"
	exit "${VERIFY_STATUS}"
fi
printf '{"status":"verified","accepted":false,"automaticAdoption":false,"phase":"complete","exitCode":0}\n' \
	> "${RUN_DIR}/run-status.json"

echo "Before reports: ${RUN_DIR}"
