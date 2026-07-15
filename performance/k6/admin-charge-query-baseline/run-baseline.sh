#!/usr/bin/env bash
set -euo pipefail
umask 077

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
SCENARIO_DIR="$ROOT_DIR/performance/k6/admin-charge-query-baseline"

: "${DATASET_ID:?Set a fresh approved dataset id.}"
: "${FIXTURE_RUN_ID:?Set a new immutable FIXTURE_RUN_ID.}"
: "${PERF_EXECUTION_RUN_ID:?Set a fresh immutable EXEC193_ execution id.}"
: "${REQUESTER_USER_ID:?Set the runtime service ADMIN user id.}"
: "${DUTY_REQUESTER_USER_ID:?Set the runtime COFFEE duty user id.}"
: "${PERF_ADMIN_EMAIL:?Provide the runtime-only ADMIN email.}"
: "${PERF_ADMIN_PASSWORD:?Provide the runtime-only ADMIN password.}"
: "${PERF_DUTY_EMAIL:?Provide the runtime-only COFFEE duty email.}"
: "${PERF_DUTY_PASSWORD:?Provide the runtime-only COFFEE duty password.}"
: "${BASE_URL:?Set the exact inspected APP_CONTAINER published loopback URL.}"
: "${APP_CONTAINER:?Set the approved app container name or id.}"
: "${POSTGRES_CONTAINER:?Set the approved PostgreSQL container name or id.}"
: "${REDIS_CONTAINER:?Set the approved Redis container name or id.}"
: "${EXPECTED_COMPOSE_PROJECT:?Set the approved actual Compose project.}"
: "${EXPECTED_APP_COMPOSE_SERVICE:?Set the approved app Compose service.}"
: "${EXPECTED_POSTGRES_COMPOSE_SERVICE:?Set the approved PostgreSQL Compose service.}"
: "${EXPECTED_REDIS_COMPOSE_SERVICE:?Set the approved Redis Compose service.}"
: "${EXPECTED_APP_CONTAINER_ID:?Set the approved immutable app container id.}"
: "${EXPECTED_APP_IMAGE_ID:?Set the approved immutable app image id.}"
: "${EXPECTED_POSTGRES_CONTAINER_ID:?Set the approved immutable PostgreSQL container id.}"
: "${EXPECTED_POSTGRES_IMAGE_ID:?Set the approved immutable PostgreSQL image id.}"
: "${EXPECTED_REDIS_CONTAINER_ID:?Set the approved immutable Redis container id.}"
: "${EXPECTED_REDIS_IMAGE_ID:?Set the approved immutable Redis image id.}"
: "${EXPECTED_SOURCE_COMMIT:?Set the approved source commit provenance.}"
: "${POSTGRES_DB:?Set the approved PostgreSQL database.}"
: "${POSTGRES_USER:?Set the runtime-only PostgreSQL user.}"
: "${EXTERNAL_ACTIVITY:?Declare the approved shared-stack condition as none.}"
: "${WARMUP_ITERATIONS:?Choose the approved warmup iteration count.}"
: "${WARMUP_VUS:?Choose the approved warmup VUS.}"
: "${WARMUP_MAX_DURATION:?Choose the approved warmup max duration.}"
: "${MEASURED_VUS:?Choose the approved measured VUS.}"
: "${MEASURED_DURATION:?Choose the approved measured duration.}"
: "${TOKEN_EXPIRY_SAFETY_SECONDS:?Choose the approved positive token expiry safety seconds.}"
: "${DOCKER_STATS_SAMPLING_INTERVAL_SECONDS:?Choose the approved positive Docker stats maximum sample gap.}"

ADMIN_EMAIL="$PERF_ADMIN_EMAIL"
ADMIN_PASSWORD="$PERF_ADMIN_PASSWORD"
DUTY_EMAIL="$PERF_DUTY_EMAIL"
DUTY_PASSWORD="$PERF_DUTY_PASSWORD"
unset PERF_ADMIN_EMAIL PERF_ADMIN_PASSWORD PERF_DUTY_EMAIL PERF_DUTY_PASSWORD

REPORT_PARENT="$ROOT_DIR/build/reports/k6/issue-193/${DATASET_ID}/${FIXTURE_RUN_ID}"
REPORT_DIR="$ROOT_DIR/build/reports/k6/issue-193/${DATASET_ID}/${FIXTURE_RUN_ID}/${PERF_EXECUTION_RUN_ID}"

if [[ ! "$DATASET_ID" =~ ^[A-Za-z0-9_-]{1,32}$ ]]; then
	echo 'DATASET_ID must contain 1-32 letters, digits, underscore, or hyphen.' >&2
	exit 2
fi
if [[ ! "$FIXTURE_RUN_ID" =~ ^[A-Za-z0-9_-]{1,32}$ ]]; then
	echo 'FIXTURE_RUN_ID must contain 1-32 letters, digits, underscore, or hyphen.' >&2
	exit 2
fi
if [[ "$EXPECTED_SOURCE_COMMIT" != '355f79df5b2e47636b7d1a17dea029da6c93c62d' ]]; then
	echo 'EXPECTED_SOURCE_COMMIT does not match the approved immutable baseline provenance.' >&2
	exit 2
fi
if [[ "$EXTERNAL_ACTIVITY" != 'none' ]]; then
	echo 'EXTERNAL_ACTIVITY must be the explicit approved value none.' >&2
	exit 2
fi
if [[ ! "$TOKEN_EXPIRY_SAFETY_SECONDS" =~ ^[1-9][0-9]*$ ]]; then
	echo 'TOKEN_EXPIRY_SAFETY_SECONDS must be a positive integer with no default.' >&2
	exit 2
fi

command -v node >/dev/null

# Parse every approved load input before report creation, container inspection,
# credential-bearing HTTP, fixture SQL, or k6 can consume external state.
WORKLOAD_CONTRACT="$(
	node "$SCENARIO_DIR/auth-contract.mjs" workload \
		"$WARMUP_ITERATIONS" "$WARMUP_VUS" "$WARMUP_MAX_DURATION" \
		"$MEASURED_VUS" "$MEASURED_DURATION"
)"
WARMUP_MAX_SECONDS="$(node -e 'process.stdout.write(String(JSON.parse(process.argv[1]).warmupMaxSeconds))' "$WORKLOAD_CONTRACT")"
MEASURED_SECONDS="$(node -e 'process.stdout.write(String(JSON.parse(process.argv[1]).measuredSeconds))' "$WORKLOAD_CONTRACT")"
WARMUP_REQUIRED_TTL_SECONDS="$(
	node "$SCENARIO_DIR/auth-contract.mjs" coverage "$WARMUP_MAX_SECONDS" "$TOKEN_EXPIRY_SAFETY_SECONDS"
)"
MEASURED_REQUIRED_TTL_SECONDS="$(
	node "$SCENARIO_DIR/auth-contract.mjs" coverage "$MEASURED_SECONDS" "$TOKEN_EXPIRY_SAFETY_SECONDS"
)"
DOCKER_STATS_SAMPLING_INTERVAL_SECONDS="$(
	node "$SCENARIO_DIR/docker-resource-evidence.mjs" interval "$DOCKER_STATS_SAMPLING_INTERVAL_SECONDS"
)"
DOCKER_STATS_COLLECTION_SLEEP_SECONDS="$(
	node -e 'process.stdout.write(String(Number(process.argv[1]) / 2))' "$DOCKER_STATS_SAMPLING_INTERVAL_SECONDS"
)"

command -v docker >/dev/null
command -v k6 >/dev/null

CREATED_REPORT_DIR="$(node "$SCENARIO_DIR/execution-directory.mjs" "$REPORT_PARENT" "$PERF_EXECUTION_RUN_ID")"
if [[ "$CREATED_REPORT_DIR" != "$REPORT_DIR" ]]; then
	echo 'Execution report directory identity mismatch.' >&2
	exit 3
fi
mkdir -p "$REPORT_DIR/warmup" "$REPORT_DIR/measured" "$REPORT_DIR/evidence"

inspect_container_identity() {
	docker inspect --format '{"id":"{{.Id}}","image":"{{.Image}}","startedAt":"{{.State.StartedAt}}","project":"{{ index .Config.Labels "com.docker.compose.project" }}","service":"{{ index .Config.Labels "com.docker.compose.service" }}","running":{{.State.Running}}}' "$1"
}

inspect_target_binding() {
	docker inspect --format '{"service":"{{ index .Config.Labels "com.docker.compose.service" }}","ports":{{json .NetworkSettings.Ports}}}' "$APP_CONTAINER"
}

psql_exec() {
	docker exec -i "$POSTGRES_CONTAINER" psql -X -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" "$@"
}

collect_database_identity() {
	psql_exec -q -t -A -c "SELECT JSONB_BUILD_OBJECT(
		'name', CURRENT_DATABASE(),
		'serverAddress', COALESCE(INET_SERVER_ADDR()::text, 'unix-socket'),
		'serverPort', COALESCE(INET_SERVER_PORT(), CURRENT_SETTING('port')::integer),
		'postmasterStartTime', PG_POSTMASTER_START_TIME()
	)"
}

validate_runtime_bootstrap() {
	local app_identity_json postgres_identity_json redis_identity_json
	app_identity_json="$(inspect_container_identity "$APP_CONTAINER")"
	postgres_identity_json="$(inspect_container_identity "$POSTGRES_CONTAINER")"
	redis_identity_json="$(inspect_container_identity "$REDIS_CONTAINER")"
	APP_IDENTITY_JSON="$app_identity_json" \
	POSTGRES_IDENTITY_JSON="$postgres_identity_json" \
	REDIS_IDENTITY_JSON="$redis_identity_json" \
	EXPECTED_COMPOSE_PROJECT="$EXPECTED_COMPOSE_PROJECT" \
	EXPECTED_APP_COMPOSE_SERVICE="$EXPECTED_APP_COMPOSE_SERVICE" \
	EXPECTED_POSTGRES_COMPOSE_SERVICE="$EXPECTED_POSTGRES_COMPOSE_SERVICE" \
	EXPECTED_REDIS_COMPOSE_SERVICE="$EXPECTED_REDIS_COMPOSE_SERVICE" \
	EXPECTED_APP_CONTAINER_ID="$EXPECTED_APP_CONTAINER_ID" \
	EXPECTED_APP_IMAGE_ID="$EXPECTED_APP_IMAGE_ID" \
	EXPECTED_POSTGRES_CONTAINER_ID="$EXPECTED_POSTGRES_CONTAINER_ID" \
	EXPECTED_POSTGRES_IMAGE_ID="$EXPECTED_POSTGRES_IMAGE_ID" \
	EXPECTED_REDIS_CONTAINER_ID="$EXPECTED_REDIS_CONTAINER_ID" \
	EXPECTED_REDIS_IMAGE_ID="$EXPECTED_REDIS_IMAGE_ID" \
		node "$SCENARIO_DIR/runtime-identity.mjs" bootstrap
}

validate_runtime_bootstrap > "$REPORT_DIR/evidence/runtime-identity-before.json"
TARGET_BINDING_JSON="$(inspect_target_binding)"
BASE_URL="$BASE_URL" TARGET_BINDING_JSON="$TARGET_BINDING_JSON" \
EXPECTED_APP_COMPOSE_SERVICE="$EXPECTED_APP_COMPOSE_SERVICE" \
	node "$SCENARIO_DIR/target-binding.mjs" \
	> "$REPORT_DIR/evidence/target-binding-before-lock.json"
DATABASE_IDENTITY_JSON="$(collect_database_identity)"
DATABASE_IDENTITY_JSON="$DATABASE_IDENTITY_JSON" EXPECTED_DATABASE_NAME="$POSTGRES_DB" \
	node "$SCENARIO_DIR/runtime-identity.mjs" database \
	> "$REPORT_DIR/evidence/database-identity-before-lock.json"
APP_PROJECT="$(node -e 'process.stdout.write(JSON.parse(require("node:fs").readFileSync(process.argv[1], "utf8")).app.project)' "$REPORT_DIR/evidence/runtime-identity-before.json")"

LOCK_KEY="$(printf '%s' "$EXPECTED_COMPOSE_PROJECT" | tr -c 'A-Za-z0-9_.-' '_')"
LOCK_DIR="/tmp/faithlog-performance-${LOCK_KEY}.lock"
if ! mkdir "$LOCK_DIR"; then
	echo "Another performance runner owns $LOCK_DIR. Parallel shared-stack use is blocked." >&2
	if [[ -f "$LOCK_DIR/owner.txt" ]]; then
		awk '{print "lock-owner: " $0}' "$LOCK_DIR/owner.txt" >&2
	fi
	exit 4
fi
printf 'issue=193 pid=%s startedAt=%s\n' "$$" "$(date -u +%Y-%m-%dT%H:%M:%SZ)" > "$LOCK_DIR/owner.txt"

STATS_STOP_FILE="$LOCK_DIR/docker-stats.stop"
STATS_READY_FILE="$LOCK_DIR/docker-stats.ready"
STATS_PID=''
ADMIN_ACCESS_TOKEN=''
DUTY_ACCESS_TOKEN=''
cleanup() {
	ADMIN_ACCESS_TOKEN=''
	DUTY_ACCESS_TOKEN=''
	ADMIN_EMAIL=''
	ADMIN_PASSWORD=''
	DUTY_EMAIL=''
	DUTY_PASSWORD=''
	unset PERF_ADMIN_EMAIL PERF_ADMIN_PASSWORD PERF_DUTY_EMAIL PERF_DUTY_PASSWORD PERF_ACCESS_TOKEN
	if [[ -d "$LOCK_DIR" ]]; then
		: > "$STATS_STOP_FILE"
		if [[ -n "$STATS_PID" ]]; then
			wait "$STATS_PID" 2>/dev/null || true
		fi
		if [[ -e "$STATS_STOP_FILE" ]]; then
			unlink "$STATS_STOP_FILE"
		fi
		if [[ -e "$STATS_READY_FILE" ]]; then
			unlink "$STATS_READY_FILE"
		fi
		if [[ -e "$LOCK_DIR/owner.txt" ]]; then
			unlink "$LOCK_DIR/owner.txt"
		fi
		rmdir "$LOCK_DIR" 2>/dev/null || true
	fi
}
trap cleanup EXIT
trap 'exit 130' INT TERM

# The canonical lock serializes cooperating runners, but names can still be
# replaced externally. Rebind the complete approved target before any login,
# fixture mutation, measurement evidence, or k6 process is allowed.
validate_runtime_bootstrap > "$REPORT_DIR/evidence/runtime-identity-post-lock.json"
TARGET_BINDING_JSON="$(inspect_target_binding)"
BASE_URL="$BASE_URL" TARGET_BINDING_JSON="$TARGET_BINDING_JSON" \
EXPECTED_APP_COMPOSE_SERVICE="$EXPECTED_APP_COMPOSE_SERVICE" \
	node "$SCENARIO_DIR/target-binding.mjs" \
	> "$REPORT_DIR/evidence/target-binding.json"
DATABASE_IDENTITY_JSON="$(collect_database_identity)"
DATABASE_IDENTITY_JSON="$DATABASE_IDENTITY_JSON" EXPECTED_DATABASE_NAME="$POSTGRES_DB" \
	node "$SCENARIO_DIR/runtime-identity.mjs" database \
	> "$REPORT_DIR/evidence/database-identity-post-lock.json"
EXPECTED_DATABASE_NAME="$POSTGRES_DB" node "$SCENARIO_DIR/runtime-identity.mjs" post-lock \
	"$REPORT_DIR/evidence/runtime-identity-before.json" \
	"$REPORT_DIR/evidence/runtime-identity-post-lock.json" \
	"$REPORT_DIR/evidence/database-identity-before-lock.json" \
	"$REPORT_DIR/evidence/database-identity-post-lock.json" \
	"$REPORT_DIR/evidence/target-binding-before-lock.json" \
	"$REPORT_DIR/evidence/target-binding.json"

docker inspect --format '{"name":"{{.Name}}","id":"{{.Id}}","image":"{{.Config.Image}}","project":"{{ index .Config.Labels "com.docker.compose.project" }}","service":"{{ index .Config.Labels "com.docker.compose.service" }}","configFiles":"{{ index .Config.Labels "com.docker.compose.project.config_files" }}","workingDir":"{{ index .Config.Labels "com.docker.compose.project.working_dir" }}"}' \
	"$APP_CONTAINER" "$POSTGRES_CONTAINER" "$REDIS_CONTAINER" > "$REPORT_DIR/evidence/compose-labels.jsonl"

printf '%s\n' \
	"status=before-baseline-measurement" \
	"datasetId=$DATASET_ID" \
	"fixtureRunId=$FIXTURE_RUN_ID" \
	"executionRunId=$PERF_EXECUTION_RUN_ID" \
	"requesterUserId=$REQUESTER_USER_ID" \
	"dutyRequesterUserId=$DUTY_REQUESTER_USER_ID" \
	"baseUrl=$BASE_URL" \
	"composeProject=$APP_PROJECT" \
	"sourceCommitProvenance=$EXPECTED_SOURCE_COMMIT" \
	"appComposeService=$EXPECTED_APP_COMPOSE_SERVICE" \
	"postgresComposeService=$EXPECTED_POSTGRES_COMPOSE_SERVICE" \
	"redisComposeService=$EXPECTED_REDIS_COMPOSE_SERVICE" \
	"postgresDatabase=$POSTGRES_DB" \
	"externalActivity=$EXTERNAL_ACTIVITY" \
	"warmupIterations=$WARMUP_ITERATIONS" \
	"warmupVus=$WARMUP_VUS" \
	"warmupMaxDuration=$WARMUP_MAX_DURATION" \
	"measuredVus=$MEASURED_VUS" \
	"measuredDuration=$MEASURED_DURATION" \
	"tokenExpirySafetySeconds=$TOKEN_EXPIRY_SAFETY_SECONDS" \
	"dockerStatsSamplingIntervalSeconds=$DOCKER_STATS_SAMPLING_INTERVAL_SECONDS" \
	> "$REPORT_DIR/run-conditions.txt"

# shared-stack-check: two quiet snapshots catch concurrent non-idle database use.
for snapshot in 1 2; do
	ACTIVE_QUERIES="$(psql_exec -Atc "SELECT COUNT(*) FROM pg_stat_activity WHERE datname = current_database() AND pid <> pg_backend_pid() AND state <> 'idle'")"
	printf 'snapshot=%s activeQueries=%s\n' "$snapshot" "$ACTIVE_QUERIES" >> "$REPORT_DIR/evidence/shared-stack-check.txt"
	if [[ "$ACTIVE_QUERIES" != '0' ]]; then
		echo 'Concurrent database activity detected. The shared-stack baseline is blocked.' >&2
		exit 5
	fi
	sleep 1
done

# Read-only logins prove both approved identities before the immutable fixture
# namespace is consumed. Tokens stay in shell memory and never enter reports.
ADMIN_ACCESS_TOKEN="$(
	BASE_URL="$BASE_URL" \
	PERF_LOGIN_EMAIL="$ADMIN_EMAIL" \
	PERF_LOGIN_PASSWORD="$ADMIN_PASSWORD" \
	EXPECTED_USER_ID="$REQUESTER_USER_ID" \
	IDENTITY_LABEL=ADMIN \
	MIN_TOKEN_TTL_SECONDS="$WARMUP_REQUIRED_TTL_SECONDS" \
	node "$SCENARIO_DIR/authenticate.mjs"
)"
DUTY_ACCESS_TOKEN="$(
	BASE_URL="$BASE_URL" \
	PERF_LOGIN_EMAIL="$DUTY_EMAIL" \
	PERF_LOGIN_PASSWORD="$DUTY_PASSWORD" \
	EXPECTED_USER_ID="$DUTY_REQUESTER_USER_ID" \
	IDENTITY_LABEL=COFFEE_DUTY \
	MIN_TOKEN_TTL_SECONDS="$TOKEN_EXPIRY_SAFETY_SECONDS" \
	node "$SCENARIO_DIR/authenticate.mjs"
)"

psql_exec \
	-v dataset_id="$DATASET_ID" \
	-v fixture_run_id="$FIXTURE_RUN_ID" \
	-v requester_user_id="$REQUESTER_USER_ID" \
	-v duty_requester_user_id="$DUTY_REQUESTER_USER_ID" \
	< "$SCENARIO_DIR/prepare-fixture.sql" \
	> "$REPORT_DIR/evidence/fixture-prepare.txt"

DATASET_BINDING_JSON="$(psql_exec -q -t -A -c "SELECT JSONB_BUILD_OBJECT(
	'campusId', (SELECT id FROM campuses WHERE name = 'PERF_ISSUE_193:' || :'dataset_id'),
	'crossCampusId', (SELECT id FROM campuses WHERE name = 'PERF_ISSUE_193:' || :'dataset_id' || ':CROSS')
)" -v dataset_id="$DATASET_ID")"
CAMPUS_ID="$(node -e 'const value=JSON.parse(process.argv[1]); if(!Number.isSafeInteger(value.campusId)||value.campusId<=0)throw new Error("invalid campusId"); process.stdout.write(String(value.campusId))' "$DATASET_BINDING_JSON")"
CROSS_CAMPUS_ID="$(node -e 'const value=JSON.parse(process.argv[1]); if(!Number.isSafeInteger(value.crossCampusId)||value.crossCampusId<=0||value.crossCampusId===value.campusId)throw new Error("invalid crossCampusId"); process.stdout.write(String(value.crossCampusId))' "$DATASET_BINDING_JSON")"

EXPECTATIONS_PATH="$REPORT_DIR/evidence/fixture-expectations.json"
psql_exec -q -t -A \
	-v dataset_id="$DATASET_ID" \
	-v fixture_run_id="$FIXTURE_RUN_ID" \
	-v campus_id="$CAMPUS_ID" \
	-v cross_campus_id="$CROSS_CAMPUS_ID" \
	-v requester_user_id="$REQUESTER_USER_ID" \
	-v duty_requester_user_id="$DUTY_REQUESTER_USER_ID" \
	< "$SCENARIO_DIR/fixture-expectations.sql" \
	> "$EXPECTATIONS_PATH"
node -e 'JSON.parse(require("node:fs").readFileSync(process.argv[1], "utf8"))' "$EXPECTATIONS_PATH"

TARGET_USER_ID="$(node -e 'process.stdout.write(String(JSON.parse(require("node:fs").readFileSync(process.argv[1], "utf8")).targetUserId))' "$EXPECTATIONS_PATH")"
FIXTURE_ACCOUNT_ID="$(node -e 'process.stdout.write(String(JSON.parse(require("node:fs").readFileSync(process.argv[1], "utf8")).fixtureAccountId))' "$EXPECTATIONS_PATH")"

BASE_URL="$BASE_URL" \
CAMPUS_ID="$CAMPUS_ID" \
PERF_ADMIN_ACCESS_TOKEN="$ADMIN_ACCESS_TOKEN" \
PERF_DUTY_ACCESS_TOKEN="$DUTY_ACCESS_TOKEN" \
EXPECTATIONS_PATH="$EXPECTATIONS_PATH" \
node "$SCENARIO_DIR/preflight.mjs"

# The first token must still cover the complete approved warmup window after
# fixture creation and correctness preflight have finished.
PERF_ACCESS_TOKEN="$ADMIN_ACCESS_TOKEN" MIN_TOKEN_TTL_SECONDS="$WARMUP_REQUIRED_TTL_SECONDS" \
	node "$SCENARIO_DIR/validate-token-ttl.mjs"

PHASE=warmup \
BASE_URL="$BASE_URL" \
CAMPUS_ID="$CAMPUS_ID" \
PERF_ACCESS_TOKEN="$ADMIN_ACCESS_TOKEN" \
EXPECTATIONS_PATH="$EXPECTATIONS_PATH" \
WARMUP_ITERATIONS="$WARMUP_ITERATIONS" \
WARMUP_VUS="$WARMUP_VUS" \
WARMUP_MAX_DURATION="$WARMUP_MAX_DURATION" \
MEASURED_VUS="$MEASURED_VUS" \
MEASURED_DURATION="$MEASURED_DURATION" \
k6 run --summary-export "$REPORT_DIR/warmup/summary.json" \
	"$SCENARIO_DIR/admin-charge-query-baseline.js" \
	> "$REPORT_DIR/warmup/k6-console.txt"
node "$SCENARIO_DIR/validate-measured-summary.mjs" "$REPORT_DIR/warmup/summary.json" "$WARMUP_ITERATIONS"

# Refresh authentication after warmup. No measured-window HTTP setup request is
# needed, and the fresh token must cover the full approved measured duration.
ADMIN_ACCESS_TOKEN="$(
	BASE_URL="$BASE_URL" \
	PERF_LOGIN_EMAIL="$ADMIN_EMAIL" \
	PERF_LOGIN_PASSWORD="$ADMIN_PASSWORD" \
	EXPECTED_USER_ID="$REQUESTER_USER_ID" \
	IDENTITY_LABEL=ADMIN_MEASURED \
	MIN_TOKEN_TTL_SECONDS="$MEASURED_REQUIRED_TTL_SECONDS" \
	node "$SCENARIO_DIR/authenticate.mjs"
)"
ADMIN_EMAIL=''
ADMIN_PASSWORD=''
DUTY_EMAIL=''
DUTY_PASSWORD=''
DUTY_ACCESS_TOKEN=''

psql_exec -q -t -A \
	-v stage=before \
	-v run_explain=false \
	-v fixture_run_id="$FIXTURE_RUN_ID" \
	-v campus_id="$CAMPUS_ID" \
	-v target_user_id="$TARGET_USER_ID" \
	-v fixture_account_id="$FIXTURE_ACCOUNT_ID" \
	< "$SCENARIO_DIR/collect-postgres-evidence.sql" \
	> "$REPORT_DIR/evidence/postgres-before.jsonl"

psql_exec -q -t -A < "$SCENARIO_DIR/collect-measurement-state.sql" \
	> "$REPORT_DIR/evidence/measurement-state-before.json"
psql_exec -q -t -A < "$SCENARIO_DIR/collect-counter-boundary.sql" \
	> "$REPORT_DIR/evidence/counter-calibration.json"
psql_exec -q -t -A < "$SCENARIO_DIR/collect-counter-boundary.sql" \
	> "$REPORT_DIR/evidence/counter-before.json"

# Offline validation cannot add database work to the measured counter window.
PERF_ACCESS_TOKEN="$ADMIN_ACCESS_TOKEN" MIN_TOKEN_TTL_SECONDS="$MEASURED_REQUIRED_TTL_SECONDS" \
	node "$SCENARIO_DIR/validate-token-ttl.mjs"

collect_docker_stats_sample() {
	local captured_at raw_stats
	captured_at="$(node -e 'process.stdout.write(new Date().toISOString())')"
	raw_stats="$(docker stats --no-stream --no-trunc --format '{{json .}}' "$APP_CONTAINER" "$POSTGRES_CONTAINER" "$REDIS_CONTAINER")"
	CAPTURED_AT="$captured_at" RAW_DOCKER_STATS_JSONL="$raw_stats" \
		node "$SCENARIO_DIR/docker-resource-evidence.mjs" normalize \
		"$REPORT_DIR/evidence/runtime-identity-before.json" \
		>> "$REPORT_DIR/measured/docker-stats.jsonl"
}

collect_docker_stats() {
	while [[ ! -f "$STATS_STOP_FILE" ]]; do
		collect_docker_stats_sample
		if [[ ! -f "$STATS_READY_FILE" ]]; then
			: > "$STATS_READY_FILE"
		fi
		# Poll at half the approved maximum gap so docker-stats command overhead
		# can remain inside the fail-closed coverage interval.
		sleep "$DOCKER_STATS_COLLECTION_SLEEP_SECONDS"
	done
}
collect_docker_stats &
STATS_PID="$!"
while [[ ! -f "$STATS_READY_FILE" ]]; do
	if ! kill -0 "$STATS_PID" 2>/dev/null; then
		wait "$STATS_PID"
		exit 6
	fi
	sleep 0.05
done

MEASURED_START="$(node -e 'process.stdout.write(new Date().toISOString())')"

PHASE=measured \
BASE_URL="$BASE_URL" \
CAMPUS_ID="$CAMPUS_ID" \
PERF_ACCESS_TOKEN="$ADMIN_ACCESS_TOKEN" \
EXPECTATIONS_PATH="$EXPECTATIONS_PATH" \
WARMUP_ITERATIONS="$WARMUP_ITERATIONS" \
WARMUP_VUS="$WARMUP_VUS" \
WARMUP_MAX_DURATION="$WARMUP_MAX_DURATION" \
MEASURED_VUS="$MEASURED_VUS" \
MEASURED_DURATION="$MEASURED_DURATION" \
k6 run --summary-export "$REPORT_DIR/measured/summary.json" \
	"$SCENARIO_DIR/admin-charge-query-baseline.js" \
	> "$REPORT_DIR/measured/k6-console.txt"
MEASURED_END="$(node -e 'process.stdout.write(new Date().toISOString())')"

# Fail before reading a replacement database or accepting evidence from a
# recreated app/PostgreSQL/Redis runtime.
validate_runtime_bootstrap > "$REPORT_DIR/evidence/runtime-identity-after.json"
node "$SCENARIO_DIR/runtime-identity.mjs" compare \
	"$REPORT_DIR/evidence/runtime-identity-before.json" \
	"$REPORT_DIR/evidence/runtime-identity-after.json"

# This is intentionally the first database query after measured HTTP traffic.
psql_exec -q -t -A < "$SCENARIO_DIR/collect-counter-boundary.sql" \
	> "$REPORT_DIR/evidence/counter-after.json"

: > "$STATS_STOP_FILE"
wait "$STATS_PID"
STATS_PID=''
collect_docker_stats_sample
printf '{"measuredStart":"%s","measuredEnd":"%s"}\n' \
	"$MEASURED_START" "$MEASURED_END" \
	> "$REPORT_DIR/measured/measured-window.json"

psql_exec -q -t -A < "$SCENARIO_DIR/collect-measurement-state.sql" \
	> "$REPORT_DIR/evidence/measurement-state-after.json"
psql_exec -q -t -A \
	-v stage=after \
	-v run_explain=true \
	-v fixture_run_id="$FIXTURE_RUN_ID" \
	-v campus_id="$CAMPUS_ID" \
	-v target_user_id="$TARGET_USER_ID" \
	-v fixture_account_id="$FIXTURE_ACCOUNT_ID" \
	< "$SCENARIO_DIR/collect-postgres-evidence.sql" \
	> "$REPORT_DIR/evidence/postgres-after.jsonl"

# A user-approved latency target remains intentionally absent. Static gates
# validate metrics and supporting PostgreSQL evidence, but shared-stack
# provenance remains conditional until PM verifies the exclusive-use window.
node "$SCENARIO_DIR/validate-measured-summary.mjs" "$REPORT_DIR/measured/summary.json"
node "$SCENARIO_DIR/docker-resource-evidence.mjs" validate \
	"$REPORT_DIR/measured/docker-stats.jsonl" \
	"$REPORT_DIR/evidence/runtime-identity-before.json" \
	"$REPORT_DIR/measured/measured-window.json" \
	"$DOCKER_STATS_SAMPLING_INTERVAL_SECONDS" \
	> "$REPORT_DIR/evidence/docker-resource-validation.json"
EXPECTED_DATABASE_NAME="$POSTGRES_DB" node "$SCENARIO_DIR/measurement-integrity.mjs" \
	"$REPORT_DIR/evidence/measurement-state-before.json" \
	"$REPORT_DIR/evidence/measurement-state-after.json" \
	"$REPORT_DIR/evidence/counter-calibration.json" \
	"$REPORT_DIR/evidence/counter-before.json" \
	"$REPORT_DIR/evidence/counter-after.json" \
	"$REPORT_DIR/evidence/evidence-integrity.json"

EXTERNAL_ACTIVITY="$EXTERNAL_ACTIVITY" node "$SCENARIO_DIR/measurement-classification.mjs" \
	> "$REPORT_DIR/measurement-classification.json"
echo "Issue #193 evidence is conditional shared-stack evidence under $REPORT_DIR; PM adoption is separate."
