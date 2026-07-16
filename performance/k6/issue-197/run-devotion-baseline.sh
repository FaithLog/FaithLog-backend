#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
REPO_ROOT="$(CDPATH= cd -- "$SCRIPT_DIR/../../.." && pwd)"
LOCK_DIR=''
LOCK_OWNED='false'
STATS_PID=''
STATS_STOP_FILE=''
PROVENANCE_TMP=''
CURRENT_STAGE='bootstrap'
cd "$REPO_ROOT"

require_env() {
	local name="$1"
	if [[ -z "${!name:-}" ]]; then
		printf 'Required environment variable is missing: %s\n' "$name" >&2
		exit 1
	fi
}

release_lock() {
	if [[ -n "$STATS_PID" ]]; then
		if [[ -n "$STATS_STOP_FILE" ]]; then
			: >"$STATS_STOP_FILE"
		fi
		wait "$STATS_PID" 2>/dev/null || true
		STATS_PID=''
	fi
	if [[ "$LOCK_OWNED" == 'true' && -n "$LOCK_DIR" ]]; then
		rmdir "$LOCK_DIR" 2>/dev/null || true
		LOCK_OWNED='false'
	fi
	if [[ -n "$PROVENANCE_TMP" ]]; then
		rm -f "$PROVENANCE_TMP"
		PROVENANCE_TMP=''
	fi
}

if [[ -z "${REJECTION_EVIDENCE_FILE-}" ]]; then
	printf '{"schemaVersion":1,"scenario":"devotion","status":"rejected","automaticAdoption":false,"stage":"bootstrap","exitCode":1}\n' >&2
	exit 1
fi
finalize() {
	local status=$?
	trap - EXIT INT TERM
	if [[ "$status" -ne 0 ]]; then
		node "$SCRIPT_DIR/lib/rejection-contract.mjs" write-first \
			"$REJECTION_EVIDENCE_FILE" devotion "$CURRENT_STAGE" "$status" >/dev/null 2>&1 || \
			printf '{"schemaVersion":1,"scenario":"devotion","status":"rejected","automaticAdoption":false,"stage":"%s","exitCode":%s}\n' "$CURRENT_STAGE" "$status" >&2
	fi
	release_lock
	exit "$status"
}
trap finalize EXIT
trap 'exit 130' INT TERM
node "$SCRIPT_DIR/lib/rejection-contract.mjs" prepare "$REJECTION_EVIDENCE_FILE" >/dev/null

CURRENT_STAGE='runtime-input'
for name in \
	FIXTURE_MANIFEST CREDENTIALS_FILE APP_CONTAINER DB_CONTAINER REDIS_CONTAINER APP_SOURCE_WORKTREE \
	EXPECTED_COMPOSE_PROJECT EXPECTED_APP_COMPOSE_SERVICE EXPECTED_DB_COMPOSE_SERVICE EXPECTED_REDIS_COMPOSE_SERVICE DB_NAME DB_USER BASE_URL \
	EXPECTED_APP_REVISION EXPECTED_APP_IMAGE_ID EXPECTED_APP_JAR_SHA256 EXPECTED_API_CONTRACT_SHA256 \
	EXPECTED_DB_IMAGE_ID EXPECTED_REDIS_IMAGE_ID EXPECTED_FLYWAY_VERSION EXPECTED_FLYWAY_SCRIPT EXPECTED_FLYWAY_CHECKSUM \
	DB_HOST REDIS_HOST EXPECTED_DB_PORT EXPECTED_REDIS_PORT \
	WARMUP_VUS MEASURED_VUS ROLLBACK_VUS \
	WARMUP_MAX_DURATION MEASURED_MAX_DURATION ROLLBACK_MAX_DURATION \
	TOKEN_TTL_SAFETY_SECONDS RESOURCE_SAMPLE_INTERVAL_SECONDS RESOURCE_SAMPLE_MAX_GAP_SECONDS \
	EXTERNAL_ACTIVITY; do
	require_env "$name"
done
credentials_file="$CREDENTIALS_FILE"
unset CREDENTIALS_FILE

if [[ "$EXTERNAL_ACTIVITY" != 'none' ]]; then
	printf 'EXTERNAL_ACTIVITY must be exactly none before any Issue #197 write.\n' >&2
	exit 1
fi

CURRENT_STAGE='runtime-tools'
for command_name in node k6 docker; do
	if ! command -v "$command_name" >/dev/null 2>&1; then
		printf 'Required command is missing: %s\n' "$command_name" >&2
		exit 1
	fi
done

CURRENT_STAGE='scenario-contract'
node "$SCRIPT_DIR/lib/validate-resource-window.mjs" validate-settings \
	"$RESOURCE_SAMPLE_INTERVAL_SECONDS" "$RESOURCE_SAMPLE_MAX_GAP_SECONDS"
node "$SCRIPT_DIR/lib/runtime-contract.mjs" validate-host "$DB_HOST" DB_HOST >/dev/null
node "$SCRIPT_DIR/lib/runtime-contract.mjs" validate-host "$REDIS_HOST" REDIS_HOST >/dev/null

node "$SCRIPT_DIR/lib/fixture-contract.mjs" validate-devotion "$FIXTURE_MANIFEST" "$credentials_file" >/dev/null
node "$SCRIPT_DIR/lib/runtime-contract.mjs" validate-run "$FIXTURE_MANIFEST" "$credentials_file" >/dev/null
CURRENT_STAGE='prelock-identity'
app_compose_project="$(docker inspect --format '{{ index .Config.Labels "com.docker.compose.project" }}' "$APP_CONTAINER")"
app_compose_service="$(docker inspect --format '{{ index .Config.Labels "com.docker.compose.service" }}' "$APP_CONTAINER")"
app_container_id="$(docker inspect --format '{{.Id}}' "$APP_CONTAINER")"
app_image_id="$(docker inspect --format '{{.Image}}' "$APP_CONTAINER")"
app_started_at="$(docker inspect --format '{{.State.StartedAt}}' "$APP_CONTAINER")"
app_compose_working_dir="$(docker inspect --format '{{ index .Config.Labels "com.docker.compose.project.working_dir" }}' "$APP_CONTAINER")"
app_image_created_at="$(docker image inspect --format '{{.Created}}' "$app_image_id")"
app_jar_sha256="$(docker exec "$APP_CONTAINER" sha256sum /app/app.jar | awk '{print $1}')"
db_compose_project="$(docker inspect --format '{{ index .Config.Labels "com.docker.compose.project" }}' "$DB_CONTAINER")"
db_compose_service="$(docker inspect --format '{{ index .Config.Labels "com.docker.compose.service" }}' "$DB_CONTAINER")"
db_container_id="$(docker inspect --format '{{.Id}}' "$DB_CONTAINER")"
db_image_id="$(docker inspect --format '{{.Image}}' "$DB_CONTAINER")"
db_started_at="$(docker inspect --format '{{.State.StartedAt}}' "$DB_CONTAINER")"
redis_compose_project="$(docker inspect --format '{{ index .Config.Labels "com.docker.compose.project" }}' "$REDIS_CONTAINER")"
redis_compose_service="$(docker inspect --format '{{ index .Config.Labels "com.docker.compose.service" }}' "$REDIS_CONTAINER")"
redis_container_id="$(docker inspect --format '{{.Id}}' "$REDIS_CONTAINER")"
redis_image_id="$(docker inspect --format '{{.Image}}' "$REDIS_CONTAINER")"
redis_started_at="$(docker inspect --format '{{.State.StartedAt}}' "$REDIS_CONTAINER")"
app_published_port="$(docker inspect --format '{{with (index .NetworkSettings.Ports "8080/tcp")}}{{(index . 0).HostPort}}{{end}}' "$APP_CONTAINER")"

for label_value in \
	"$app_compose_project" "$app_compose_service" "$app_container_id" "$app_image_id" "$app_started_at" \
	"$app_compose_working_dir" "$app_image_created_at" "$app_jar_sha256" \
	"$db_compose_project" "$db_compose_service" "$db_container_id" "$db_image_id" "$db_started_at" \
	"$redis_compose_project" "$redis_compose_service" "$redis_container_id" "$redis_image_id" "$redis_started_at"; do
	if [[ -z "$label_value" ]]; then
		printf 'Required Compose project/service label is empty.\n' >&2
		exit 1
	fi
done
if [[ "$app_compose_project" != "$EXPECTED_COMPOSE_PROJECT" || "$db_compose_project" != "$EXPECTED_COMPOSE_PROJECT" || "$redis_compose_project" != "$EXPECTED_COMPOSE_PROJECT" ]]; then
	printf 'Actual Compose label mismatch: expected=%s app=%s db=%s redis=%s\n' "$EXPECTED_COMPOSE_PROJECT" "$app_compose_project" "$db_compose_project" "$redis_compose_project" >&2
	exit 1
fi
if [[ "$app_compose_service" != "$EXPECTED_APP_COMPOSE_SERVICE" || "$db_compose_service" != "$EXPECTED_DB_COMPOSE_SERVICE" || "$redis_compose_service" != "$EXPECTED_REDIS_COMPOSE_SERVICE" ]]; then
	printf 'Actual Compose service role mismatch: expected_app=%s actual_app=%s expected_db=%s actual_db=%s expected_redis=%s actual_redis=%s\n' \
		"$EXPECTED_APP_COMPOSE_SERVICE" "$app_compose_service" "$EXPECTED_DB_COMPOSE_SERVICE" "$db_compose_service" \
		"$EXPECTED_REDIS_COMPOSE_SERVICE" "$redis_compose_service" >&2
	exit 1
fi
if [[ "$db_image_id" != "$EXPECTED_DB_IMAGE_ID" || "$redis_image_id" != "$EXPECTED_REDIS_IMAGE_ID" ]]; then
	printf 'Actual dependency image mismatch: expected_db=%s actual_db=%s expected_redis=%s actual_redis=%s\n' \
		"$EXPECTED_DB_IMAGE_ID" "$db_image_id" "$EXPECTED_REDIS_IMAGE_ID" "$redis_image_id" >&2
	exit 1
fi
if [[ ! "$app_compose_project" =~ ^[A-Za-z0-9._-]+$ ]]; then
	printf 'Unsafe Compose project label for performance runner lock: %s\n' "$app_compose_project" >&2
	exit 1
fi
PROVENANCE_TMP="$(mktemp /tmp/faithlog-197-source-provenance.XXXXXX)"
node "$SCRIPT_DIR/lib/source-image-provenance.mjs" capture \
	"$APP_SOURCE_WORKTREE" "$app_compose_working_dir" "$EXPECTED_APP_REVISION" "$app_image_id" \
	"$app_image_created_at" "$EXPECTED_API_CONTRACT_SHA256" "$PROVENANCE_TMP"
node "$SCRIPT_DIR/lib/runtime-contract.mjs" validate-app-identity \
	"$EXPECTED_APP_REVISION" "$EXPECTED_APP_IMAGE_ID" "$EXPECTED_APP_JAR_SHA256" "$EXPECTED_API_CONTRACT_SHA256" \
	"$EXPECTED_APP_REVISION" "$app_image_id" "$app_jar_sha256" "$EXPECTED_API_CONTRACT_SHA256" >/dev/null
node "$SCRIPT_DIR/lib/runtime-contract.mjs" validate-target "$BASE_URL" "$app_published_port" >/dev/null

CURRENT_STAGE='lock'
LOCK_DIR="/tmp/faithlog-performance-${app_compose_project}.lock"
if ! mkdir "$LOCK_DIR" 2>/dev/null; then
	printf 'Another performance, frontend QA, or load run may be active; parallel work is refused: %s\n' "$LOCK_DIR" >&2
	exit 1
fi
LOCK_OWNED='true'

CURRENT_STAGE='postlock-runtime'
app_environment="$(docker inspect --format '{{range .Config.Env}}{{println .}}{{end}}' "$APP_CONTAINER")"
for expected_app_env in \
	'FAITHLOG_SCHEDULER_ENABLED=false' \
	'SPRING_PROFILES_ACTIVE=docker' \
	"SPRING_DATASOURCE_URL=jdbc:postgresql://$EXPECTED_DB_COMPOSE_SERVICE:$EXPECTED_DB_PORT/$DB_NAME" \
	"SPRING_DATASOURCE_USERNAME=$DB_USER" \
	"SPRING_DATA_REDIS_HOST=$EXPECTED_REDIS_COMPOSE_SERVICE" \
	"SPRING_DATA_REDIS_PORT=$EXPECTED_REDIS_PORT"; do
	if ! grep -Fqx "$expected_app_env" <<<"$app_environment"; then
		printf 'App container environment is not approved for local writes: missing %s\n' "$expected_app_env" >&2
		exit 1
	fi
done

fixture_run_id="$(node "$SCRIPT_DIR/lib/fixture-contract.mjs" field "$FIXTURE_MANIFEST" fixtureRunId)"
dataset_id="$(node "$SCRIPT_DIR/lib/fixture-contract.mjs" field "$FIXTURE_MANIFEST" datasetId)"
campus_id="$(node "$SCRIPT_DIR/lib/fixture-contract.mjs" field "$FIXTURE_MANIFEST" campusId)"
rollback_campus_id="$(node "$SCRIPT_DIR/lib/fixture-contract.mjs" field "$FIXTURE_MANIFEST" rollbackCampusId)"
warmup_week="$(node "$SCRIPT_DIR/lib/fixture-contract.mjs" field "$FIXTURE_MANIFEST" warmupWeekStartDate)"
measured_week="$(node "$SCRIPT_DIR/lib/fixture-contract.mjs" field "$FIXTURE_MANIFEST" measuredWeekStartDate)"
rollback_week="$(node "$SCRIPT_DIR/lib/fixture-contract.mjs" field "$FIXTURE_MANIFEST" rollbackWeekStartDate)"
expected_user_count="$(node "$SCRIPT_DIR/lib/fixture-contract.mjs" field "$FIXTURE_MANIFEST" expectedMeasuredUserCount)"
expected_penalty_amount="$(node "$SCRIPT_DIR/lib/fixture-contract.mjs" field "$FIXTURE_MANIFEST" expectedPenaltyAmount)"
warmup_user_ids="$(node "$SCRIPT_DIR/lib/fixture-contract.mjs" cohort-ids "$FIXTURE_MANIFEST" warmup)"
measured_user_ids="$(node "$SCRIPT_DIR/lib/fixture-contract.mjs" cohort-ids "$FIXTURE_MANIFEST" measured)"
rollback_user_ids="$(node "$SCRIPT_DIR/lib/fixture-contract.mjs" cohort-ids "$FIXTURE_MANIFEST" rollback)"
warmup_user_count="$(node -e 'const fs=require("node:fs"); const m=JSON.parse(fs.readFileSync(process.argv[1], "utf8")); process.stdout.write(String(m.warmupUserIds.length));' "$FIXTURE_MANIFEST")"
rollback_user_count="$(node -e 'const fs=require("node:fs"); const m=JSON.parse(fs.readFileSync(process.argv[1], "utf8")); process.stdout.write(String(m.rollbackUserIds.length));' "$FIXTURE_MANIFEST")"
report_base="${PERF_REPORT_ROOT:-build/reports/k6/issue-197}"
fixture_report_root="$report_base/$fixture_run_id"
mkdir -p "$report_base"
if ! mkdir -m 700 "$fixture_report_root" 2>/dev/null; then
	printf 'Fixture report namespace already exists; fresh fixtureRunId is required: %s\n' "$fixture_report_root" >&2
	exit 1
fi
report_root="$fixture_report_root/devotion"
mkdir -m 700 "$report_root"
capture_runtime_identity() {
	local output_file="$1"
	local checkpoint="$2"
	local db_server_file="${output_file%.json}-database-server.json"
	local redis_server_file="${output_file%.json}-redis-server.txt"
	local current_app_container_id current_app_image_id current_app_started_at
	local current_app_jar_sha256 current_app_project current_app_service current_app_port
	local current_app_compose_working_dir current_app_image_created_at source_provenance_file
	local current_db_container_id current_db_image_id current_db_started_at
	local current_db_project current_db_service
	local current_redis_container_id current_redis_image_id current_redis_started_at
	local current_redis_project current_redis_service
	current_app_container_id="$(docker inspect --format '{{.Id}}' "$APP_CONTAINER")"
	current_app_image_id="$(docker inspect --format '{{.Image}}' "$APP_CONTAINER")"
	current_app_started_at="$(docker inspect --format '{{.State.StartedAt}}' "$APP_CONTAINER")"
	current_app_compose_working_dir="$(docker inspect --format '{{ index .Config.Labels "com.docker.compose.project.working_dir" }}' "$APP_CONTAINER")"
	current_app_image_created_at="$(docker image inspect --format '{{.Created}}' "$current_app_image_id")"
	current_app_jar_sha256="$(docker exec "$APP_CONTAINER" sha256sum /app/app.jar | awk '{print $1}')"
	current_app_project="$(docker inspect --format '{{ index .Config.Labels "com.docker.compose.project" }}' "$APP_CONTAINER")"
	current_app_service="$(docker inspect --format '{{ index .Config.Labels "com.docker.compose.service" }}' "$APP_CONTAINER")"
	current_app_port="$(docker inspect --format '{{with (index .NetworkSettings.Ports "8080/tcp")}}{{(index . 0).HostPort}}{{end}}' "$APP_CONTAINER")"
	current_db_container_id="$(docker inspect --format '{{.Id}}' "$DB_CONTAINER")"
	current_db_image_id="$(docker inspect --format '{{.Image}}' "$DB_CONTAINER")"
	current_db_started_at="$(docker inspect --format '{{.State.StartedAt}}' "$DB_CONTAINER")"
	current_db_project="$(docker inspect --format '{{ index .Config.Labels "com.docker.compose.project" }}' "$DB_CONTAINER")"
	current_db_service="$(docker inspect --format '{{ index .Config.Labels "com.docker.compose.service" }}' "$DB_CONTAINER")"
	current_redis_container_id="$(docker inspect --format '{{.Id}}' "$REDIS_CONTAINER")"
	current_redis_image_id="$(docker inspect --format '{{.Image}}' "$REDIS_CONTAINER")"
	current_redis_started_at="$(docker inspect --format '{{.State.StartedAt}}' "$REDIS_CONTAINER")"
	current_redis_project="$(docker inspect --format '{{ index .Config.Labels "com.docker.compose.project" }}' "$REDIS_CONTAINER")"
	current_redis_service="$(docker inspect --format '{{ index .Config.Labels "com.docker.compose.service" }}' "$REDIS_CONTAINER")"
	source_provenance_file="${output_file%.json}-source-image-provenance.json"
	node "$SCRIPT_DIR/lib/source-image-provenance.mjs" capture \
		"$APP_SOURCE_WORKTREE" "$current_app_compose_working_dir" "$EXPECTED_APP_REVISION" "$current_app_image_id" \
		"$current_app_image_created_at" "$EXPECTED_API_CONTRACT_SHA256" "$source_provenance_file"
	node "$SCRIPT_DIR/lib/runtime-contract.mjs" validate-app-identity \
		"$EXPECTED_APP_REVISION" "$EXPECTED_APP_IMAGE_ID" "$EXPECTED_APP_JAR_SHA256" "$EXPECTED_API_CONTRACT_SHA256" \
		"$EXPECTED_APP_REVISION" "$current_app_image_id" "$current_app_jar_sha256" "$EXPECTED_API_CONTRACT_SHA256" >/dev/null
	if [[ "$checkpoint" == 'initial' && ( \
		"$current_app_container_id" != "$app_container_id" || "$current_app_image_id" != "$app_image_id" || \
		"$current_app_started_at" != "$app_started_at" || "$current_app_compose_working_dir" != "$app_compose_working_dir" || \
		"$current_app_image_created_at" != "$app_image_created_at" || "$current_app_jar_sha256" != "$app_jar_sha256" || \
		"$current_app_project" != "$app_compose_project" || "$current_app_service" != "$app_compose_service" || \
		"$current_app_port" != "$app_published_port" || "$current_db_container_id" != "$db_container_id" || \
		"$current_db_image_id" != "$db_image_id" || "$current_db_started_at" != "$db_started_at" || \
		"$current_db_project" != "$db_compose_project" || "$current_db_service" != "$db_compose_service" || \
		"$current_redis_container_id" != "$redis_container_id" || "$current_redis_image_id" != "$redis_image_id" || \
		"$current_redis_started_at" != "$redis_started_at" || "$current_redis_project" != "$redis_compose_project" || \
		"$current_redis_service" != "$redis_compose_service" ) ]]; then
		printf 'Runtime container identity changed between lock inspection and initial capture.\n' >&2
		exit 1
	fi
	docker exec -i "$DB_CONTAINER" psql -h "$DB_HOST" -X -qAt -v ON_ERROR_STOP=1 -U "$DB_USER" -d "$DB_NAME" \
		<"$SCRIPT_DIR/runtime-identity.sql" >"$db_server_file"
	docker exec "$REDIS_CONTAINER" redis-cli -h "$REDIS_HOST" -p "$EXPECTED_REDIS_PORT" --raw INFO server >"$redis_server_file"
	APP_CONTAINER_ID="$current_app_container_id" APP_IMAGE_ID="$current_app_image_id" APP_STARTED_AT="$current_app_started_at" \
	APP_COMPOSE_PROJECT="$current_app_project" APP_COMPOSE_SERVICE="$current_app_service" APP_PUBLISHED_PORT="$current_app_port" \
	APP_REVISION="$EXPECTED_APP_REVISION" APP_JAR_SHA256="$current_app_jar_sha256" APP_API_CONTRACT_SHA256="$EXPECTED_API_CONTRACT_SHA256" \
	DB_CONTAINER_ID="$current_db_container_id" DB_IMAGE_ID="$current_db_image_id" DB_STARTED_AT="$current_db_started_at" \
	DB_COMPOSE_PROJECT="$current_db_project" DB_COMPOSE_SERVICE="$current_db_service" \
	REDIS_CONTAINER_ID="$current_redis_container_id" REDIS_IMAGE_ID="$current_redis_image_id" REDIS_STARTED_AT="$current_redis_started_at" \
	REDIS_COMPOSE_PROJECT="$current_redis_project" REDIS_COMPOSE_SERVICE="$current_redis_service" \
	EXPECTED_FLYWAY_VERSION="$EXPECTED_FLYWAY_VERSION" EXPECTED_FLYWAY_SCRIPT="$EXPECTED_FLYWAY_SCRIPT" EXPECTED_FLYWAY_CHECKSUM="$EXPECTED_FLYWAY_CHECKSUM" \
	DB_HOST="$DB_HOST" EXPECTED_DB_PORT="$EXPECTED_DB_PORT" EXPECTED_REDIS_PORT="$EXPECTED_REDIS_PORT" \
		node "$SCRIPT_DIR/lib/validate-runtime-identity.mjs" capture "$db_server_file" "$redis_server_file" "$output_file"
}

validate_runtime_checkpoint() {
	local checkpoint="$1"
	local identity_file="$2"
	node "$SCRIPT_DIR/lib/validate-runtime-identity.mjs" validate-pair \
		"$report_root/runtime-identity-initial.json" "$identity_file" "$checkpoint" \
		"$report_root/runtime-identity-${checkpoint}-gate.json"
}

capture_runtime_identity "$report_root/runtime-identity-initial.json" initial

APP_CONTAINER="$APP_CONTAINER" APP_PROJECT="$app_compose_project" APP_SERVICE="$app_compose_service" \
DB_CONTAINER="$DB_CONTAINER" DB_PROJECT="$db_compose_project" DB_SERVICE="$db_compose_service" \
REDIS_CONTAINER="$REDIS_CONTAINER" REDIS_PROJECT="$redis_compose_project" REDIS_SERVICE="$redis_compose_service" \
EXPECTED_PROJECT="$EXPECTED_COMPOSE_PROJECT" EXPECTED_APP_SERVICE="$EXPECTED_APP_COMPOSE_SERVICE" EXPECTED_DB_SERVICE="$EXPECTED_DB_COMPOSE_SERVICE" \
EXPECTED_REDIS_SERVICE="$EXPECTED_REDIS_COMPOSE_SERVICE" \
APP_PUBLISHED_PORT="$app_published_port" BASE_URL="$BASE_URL" RUNNER_LOCK="$LOCK_DIR" \
DATASET_ID="$dataset_id" FIXTURE_RUN_ID="$fixture_run_id" EXTERNAL_ACTIVITY="$EXTERNAL_ACTIVITY" \
RUNTIME_IDENTITY_FILE="$report_root/runtime-identity-initial.json" \
node -e '
	const fs = require("node:fs");
	const evidence = {
		capturedAt: new Date().toISOString(), datasetId: process.env.DATASET_ID, fixtureRunId: process.env.FIXTURE_RUN_ID,
		actualComposeLabels: {
			app: { container: process.env.APP_CONTAINER, project: process.env.APP_PROJECT, service: process.env.APP_SERVICE },
			database: { container: process.env.DB_CONTAINER, project: process.env.DB_PROJECT, service: process.env.DB_SERVICE },
			redis: { container: process.env.REDIS_CONTAINER, project: process.env.REDIS_PROJECT, service: process.env.REDIS_SERVICE },
		},
		expectedComposeLabels: {
			project: process.env.EXPECTED_PROJECT,
			appService: process.env.EXPECTED_APP_SERVICE,
			databaseService: process.env.EXPECTED_DB_SERVICE,
			redisService: process.env.EXPECTED_REDIS_SERVICE,
		},
		baseUrlTarget: { baseUrl: process.env.BASE_URL, publishedPort: Number(process.env.APP_PUBLISHED_PORT) },
		runnerLock: process.env.RUNNER_LOCK, externalActivity: process.env.EXTERNAL_ACTIVITY,
		dbEvidenceClassification: "runtime-observed-supporting-only",
		automaticAdoption: false,
		runtimeIdentity: JSON.parse(fs.readFileSync(process.env.RUNTIME_IDENTITY_FILE, "utf8")),
	};
	process.stdout.write(`${JSON.stringify(evidence, null, 2)}\n`);
' >"$report_root/environment.json"

docker exec -i "$DB_CONTAINER" psql -h "$DB_HOST" -X -q -tA -v ON_ERROR_STOP=1 -U "$DB_USER" -d "$DB_NAME" \
	-v campus_id="$campus_id" -v rollback_campus_id="$rollback_campus_id" \
	-v warmup_week_start_date="$warmup_week" -v measured_week_start_date="$measured_week" \
	-v rollback_week_start_date="$rollback_week" -v warmup_user_ids="$warmup_user_ids" \
	-v measured_user_ids="$measured_user_ids" -v rollback_user_ids="$rollback_user_ids" \
	<"$SCRIPT_DIR/preflight-devotion.sql" >"$report_root/preflight.json"
node "$SCRIPT_DIR/lib/validate-devotion-preflight.mjs" "$FIXTURE_MANIFEST" "$report_root/preflight.json" >/dev/null

stats_file="$report_root/measured-docker-stats.jsonl"
: >"$stats_file"
STATS_STOP_FILE="$report_root/resource-sampler.stop"
sample_stats_snapshot() {
	local raw observed_at
	raw="$(docker stats --no-stream --no-trunc --format '{{.ID}}|{{.CPUPerc}}|{{.MemUsage}}' \
		"$app_container_id" "$db_container_id" "$redis_container_id")"
	observed_at="$(node -e 'process.stdout.write(new Date().toISOString())')"
	printf '%s\n' "$raw" | node "$SCRIPT_DIR/lib/validate-resource-window.mjs" append-snapshot \
		"$stats_file" "$observed_at" "$app_container_id" "$db_container_id" "$redis_container_id"
}

sample_stats() {
	set +o pipefail
	docker stats --no-trunc --format '{{.ID}}|{{.CPUPerc}}|{{.MemUsage}}' \
		"$app_container_id" "$db_container_id" "$redis_container_id" | \
		node "$SCRIPT_DIR/lib/validate-resource-window.mjs" stream-samples \
			"$stats_file" "$STATS_STOP_FILE" "$RESOURCE_SAMPLE_MAX_GAP_SECONDS" \
			"$app_container_id" "$db_container_id" "$redis_container_id"
}

run_phase() {
	local phase="$1"
	local vus="$2"
	local max_duration="$3"
	local summary_file="$4"
	k6 run \
		-e "BASE_URL=$BASE_URL" \
		-e "FIXTURE_MANIFEST=$FIXTURE_MANIFEST" \
		-e "CREDENTIALS_FILE=$credentials_file" \
		-e "PHASE=$phase" \
		-e "VUS=$vus" \
		-e "MAX_DURATION=$max_duration" \
		--summary-export "$summary_file" \
		"$SCRIPT_DIR/devotion-write.js"
}

collect_db_counters() {
	local output_file="$1"
	docker exec -i "$DB_CONTAINER" psql -h "$DB_HOST" -X -qAt -v ON_ERROR_STOP=1 -U "$DB_USER" -d "$DB_NAME" \
		<"$SCRIPT_DIR/collect-db-counters.sql" >"$output_file"
}

CURRENT_STAGE='warmup'
capture_runtime_identity "$report_root/runtime-identity-warmup-before.json" warmupBefore
validate_runtime_checkpoint warmupBefore "$report_root/runtime-identity-warmup-before.json"
PHASE=warmup run_phase warmup "$WARMUP_VUS" "$WARMUP_MAX_DURATION" "$report_root/warmup-summary.json"
node "$SCRIPT_DIR/lib/validate-k6-summary.mjs" "$report_root/warmup-summary.json" warmup "$warmup_user_count" \
	>"$report_root/warmup-adoption-gate.json"

CURRENT_STAGE='measured'
capture_runtime_identity "$report_root/runtime-identity-measured-before.json" measuredBefore
validate_runtime_checkpoint measuredBefore "$report_root/runtime-identity-measured-before.json"
collect_db_counters "$report_root/db-counters-before.jsonl"
sample_stats_snapshot
measured_start="$(node -e 'process.stdout.write(new Date().toISOString())')"
sample_stats &
STATS_PID="$!"
PHASE=measured run_phase measured "$MEASURED_VUS" "$MEASURED_MAX_DURATION" "$report_root/measured-summary.json"
measured_end="$(node -e 'process.stdout.write(new Date().toISOString())')"
if ! kill -0 "$STATS_PID" 2>/dev/null; then
	wait "$STATS_PID" 2>/dev/null || true
	STATS_PID=''
	printf 'Measured resource sampler terminated before the measured window ended.\n' >&2
	exit 1
fi
: >"$STATS_STOP_FILE"
if ! wait "$STATS_PID"; then
	STATS_PID=''
	printf 'Measured resource sampler failed before a complete final boundary snapshot.\n' >&2
	exit 1
fi
STATS_PID=''
collect_db_counters "$report_root/db-counters-after.jsonl"
capture_runtime_identity "$report_root/runtime-identity-measured-after.json" measuredAfter
validate_runtime_checkpoint measuredAfter "$report_root/runtime-identity-measured-after.json"
node "$SCRIPT_DIR/lib/validate-resource-window.mjs" write-config \
	"$report_root/resource-window-config.json" \
	"$RESOURCE_SAMPLE_INTERVAL_SECONDS" "$RESOURCE_SAMPLE_MAX_GAP_SECONDS" \
	"$measured_start" "$measured_end" "$app_container_id" "$db_container_id" "$redis_container_id"
node "$SCRIPT_DIR/lib/validate-resource-window.mjs" \
	"$stats_file" "$report_root/resource-window-config.json" "$report_root/resource-window-evidence.json" >/dev/null
node "$SCRIPT_DIR/lib/validate-k6-summary.mjs" "$report_root/measured-summary.json" measured "$expected_user_count" \
	>"$report_root/measured-adoption-gate.json"
node "$SCRIPT_DIR/lib/validate-db-window.mjs" \
	"$report_root/db-counters-before.jsonl" "$report_root/db-counters-after.jsonl" "$EXTERNAL_ACTIVITY" \
	"$report_root/db-window-evidence.json" >/dev/null
CURRENT_STAGE='rollback'
PHASE=rollback run_phase rollback "$ROLLBACK_VUS" "$ROLLBACK_MAX_DURATION" "$report_root/rollback-summary.json"
node "$SCRIPT_DIR/lib/validate-k6-summary.mjs" "$report_root/rollback-summary.json" rollback "$rollback_user_count" \
	>"$report_root/rollback-adoption-gate.json"

CURRENT_STAGE='correctness'
docker exec -i "$DB_CONTAINER" psql -h "$DB_HOST" -X -q -tA -v ON_ERROR_STOP=1 -U "$DB_USER" -d "$DB_NAME" \
	-v dataset_id="$dataset_id" -v fixture_run_id="$fixture_run_id" \
	-v campus_id="$campus_id" -v rollback_campus_id="$rollback_campus_id" \
	-v warmup_week_start_date="$warmup_week" -v measured_week_start_date="$measured_week" \
	-v rollback_week_start_date="$rollback_week" -v expected_measured_user_count="$expected_user_count" \
	-v expected_penalty_amount="$expected_penalty_amount" -v warmup_user_ids="$warmup_user_ids" \
	-v measured_user_ids="$measured_user_ids" -v rollback_user_ids="$rollback_user_ids" \
	<"$SCRIPT_DIR/verify-devotion.sql" >"$report_root/db-counters.json"

CURRENT_STAGE='final-identity'
capture_runtime_identity "$report_root/runtime-identity-final.json" final
validate_runtime_checkpoint final "$report_root/runtime-identity-final.json"
node "$SCRIPT_DIR/lib/validate-runtime-identity.mjs" validate-series \
	"$report_root/runtime-identity-initial.json" \
	"$report_root/runtime-identity-warmup-before.json" \
	"$report_root/runtime-identity-measured-before.json" \
	"$report_root/runtime-identity-measured-after.json" \
	"$report_root/runtime-identity-final.json" \
	"$report_root/runtime-identity-evidence.json" >/dev/null

CURRENT_STAGE='evidence'
node "$SCRIPT_DIR/lib/scenario-contract.mjs" \
	"$FIXTURE_MANIFEST" \
	"$report_root/warmup-summary.json" \
	"$report_root/measured-summary.json" \
	"$report_root/rollback-summary.json" \
	"$report_root/db-counters.json" \
	"$report_root/resource-window-evidence.json" \
	"$report_root/db-window-evidence.json" \
	"$report_root/runtime-identity-evidence.json" \
	"$report_root/scenario-evidence.json" \
	"$app_compose_project"

printf 'Issue #197 devotion baseline evidence: %s\n' "$report_root/scenario-evidence.json"
