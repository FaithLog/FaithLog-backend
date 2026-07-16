#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
REPO_ROOT="$(CDPATH= cd -- "$SCRIPT_DIR/../../.." && pwd)"
LOCK_DIR=''
LOCK_OWNED='false'
RUNTIME_TEMP_DIR=''
CURRENT_STAGE='bootstrap'
cd "$REPO_ROOT"

require_env() {
	local name="$1"
	if [[ -z "${!name:-}" ]]; then
		printf 'Required environment variable is missing: %s\n' "$name" >&2
		exit 1
	fi
}

release_owned_runtime() {
	if [[ "$LOCK_OWNED" == 'true' && -n "$LOCK_DIR" ]]; then
		rmdir "$LOCK_DIR" 2>/dev/null || true
		LOCK_OWNED='false'
	fi
	if [[ -n "$RUNTIME_TEMP_DIR" ]]; then
		rm -rf "$RUNTIME_TEMP_DIR"
		RUNTIME_TEMP_DIR=''
	fi
}

if [[ -z "${REJECTION_EVIDENCE_FILE-}" ]]; then
	printf '{"schemaVersion":1,"scenario":"devotion-prepare","status":"rejected","automaticAdoption":false,"stage":"bootstrap","exitCode":1}\n' >&2
	exit 1
fi

finalize() {
	local status=$?
	trap - EXIT INT TERM
	if [[ "$status" -ne 0 ]]; then
		node "$SCRIPT_DIR/lib/rejection-contract.mjs" write-first \
			"$REJECTION_EVIDENCE_FILE" devotion "$CURRENT_STAGE" "$status" >/dev/null 2>&1 || \
			printf '{"schemaVersion":1,"scenario":"devotion-prepare","status":"rejected","automaticAdoption":false,"stage":"%s","exitCode":%s}\n' "$CURRENT_STAGE" "$status" >&2
	fi
	release_owned_runtime
	exit "$status"
}
trap finalize EXIT
trap 'exit 130' INT TERM
node "$SCRIPT_DIR/lib/rejection-contract.mjs" prepare "$REJECTION_EVIDENCE_FILE" >/dev/null

CURRENT_STAGE='runtime-input'
for name in \
	DATASET_ID FIXTURE_RUN_ID PREPARE_REPORT_ROOT RUNTIME_SECRET_ROOT PREPARE_INPUT_FILE \
	APP_CONTAINER DB_CONTAINER REDIS_CONTAINER APP_SOURCE_WORKTREE \
	EXPECTED_COMPOSE_PROJECT EXPECTED_APP_COMPOSE_SERVICE EXPECTED_DB_COMPOSE_SERVICE EXPECTED_REDIS_COMPOSE_SERVICE \
	EXPECTED_APP_REVISION EXPECTED_APP_IMAGE_ID EXPECTED_APP_JAR_SHA256 EXPECTED_API_CONTRACT_SHA256 \
	EXPECTED_DB_IMAGE_ID EXPECTED_REDIS_IMAGE_ID EXPECTED_FLYWAY_VERSION EXPECTED_FLYWAY_SCRIPT EXPECTED_FLYWAY_CHECKSUM \
	DB_HOST REDIS_HOST EXPECTED_DB_PORT EXPECTED_REDIS_PORT DB_NAME DB_USER BASE_URL \
	WARMUP_VUS MEASURED_VUS ROLLBACK_VUS WARMUP_MAX_DURATION MEASURED_MAX_DURATION ROLLBACK_MAX_DURATION \
	PREPARE_MAX_DURATION_SECONDS TOKEN_TTL_SAFETY_SECONDS RESOURCE_SAMPLE_INTERVAL_SECONDS RESOURCE_SAMPLE_MAX_GAP_SECONDS EXTERNAL_ACTIVITY; do
	require_env "$name"
done
if [[ "$EXTERNAL_ACTIVITY" != 'none' ]]; then
	printf 'EXTERNAL_ACTIVITY must be exactly none before Issue #197 fixture preparation.\n' >&2
	exit 1
fi
if [[ ! "$PREPARE_REPORT_ROOT" = /* || ! "$RUNTIME_SECRET_ROOT" = /* ]]; then
	printf 'PREPARE_REPORT_ROOT and RUNTIME_SECRET_ROOT must be absolute paths.\n' >&2
	exit 1
fi

CURRENT_STAGE='runtime-tools'
for command_name in node k6 docker; do
	if ! command -v "$command_name" >/dev/null 2>&1; then
		printf 'Required command is missing: %s\n' "$command_name" >&2
		exit 1
	fi
done
node "$SCRIPT_DIR/lib/runtime-contract.mjs" validate-host "$DB_HOST" DB_HOST >/dev/null
node "$SCRIPT_DIR/lib/runtime-contract.mjs" validate-host "$REDIS_HOST" REDIS_HOST >/dev/null
node "$SCRIPT_DIR/lib/validate-resource-window.mjs" validate-settings \
	"$RESOURCE_SAMPLE_INTERVAL_SECONDS" "$RESOURCE_SAMPLE_MAX_GAP_SECONDS" >/dev/null
PREPARE_MAX_DURATION_SECONDS="$PREPARE_MAX_DURATION_SECONDS" \
	node "$SCRIPT_DIR/lib/devotion-prepare.mjs" validate-window >/dev/null

CURRENT_STAGE='prelock-identity'
app_compose_project="$(docker inspect --format '{{ index .Config.Labels "com.docker.compose.project" }}' "$APP_CONTAINER")"
app_compose_service="$(docker inspect --format '{{ index .Config.Labels "com.docker.compose.service" }}' "$APP_CONTAINER")"
app_container_id="$(docker inspect --format '{{.Id}}' "$APP_CONTAINER")"
app_image_id="$(docker inspect --format '{{.Image}}' "$APP_CONTAINER")"
app_started_at="$(docker inspect --format '{{.State.StartedAt}}' "$APP_CONTAINER")"
app_compose_working_dir="$(docker inspect --format '{{ index .Config.Labels "com.docker.compose.project.working_dir" }}' "$APP_CONTAINER")"
app_image_created_at="$(docker image inspect --format '{{.Created}}' "$app_image_id")"
app_jar_sha256="$(docker exec "$APP_CONTAINER" sha256sum /app/app.jar | awk '{print $1}')"
app_published_port="$(docker inspect --format '{{with (index .NetworkSettings.Ports "8080/tcp")}}{{(index . 0).HostPort}}{{end}}' "$APP_CONTAINER")"
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

for immutable_value in \
	"$app_compose_project" "$app_compose_service" "$app_container_id" "$app_image_id" "$app_started_at" \
	"$app_compose_working_dir" "$app_image_created_at" "$app_jar_sha256" "$app_published_port" \
	"$db_compose_project" "$db_compose_service" "$db_container_id" "$db_image_id" "$db_started_at" \
	"$redis_compose_project" "$redis_compose_service" "$redis_container_id" "$redis_image_id" "$redis_started_at"; do
	if [[ -z "$immutable_value" ]]; then
		printf 'Required runtime identity value is empty.\n' >&2
		exit 1
	fi
done
if [[ "$app_compose_project" != "$EXPECTED_COMPOSE_PROJECT" || "$db_compose_project" != "$EXPECTED_COMPOSE_PROJECT" || "$redis_compose_project" != "$EXPECTED_COMPOSE_PROJECT" ]]; then
	printf 'Actual Compose project does not match the runtime-approved project.\n' >&2
	exit 1
fi
if [[ "$app_compose_service" != "$EXPECTED_APP_COMPOSE_SERVICE" || "$db_compose_service" != "$EXPECTED_DB_COMPOSE_SERVICE" || "$redis_compose_service" != "$EXPECTED_REDIS_COMPOSE_SERVICE" ]]; then
	printf 'Actual Compose service roles do not match the runtime-approved roles.\n' >&2
	exit 1
fi
if [[ "$app_image_id" != "$EXPECTED_APP_IMAGE_ID" || "$db_image_id" != "$EXPECTED_DB_IMAGE_ID" || "$redis_image_id" != "$EXPECTED_REDIS_IMAGE_ID" ]]; then
	printf 'Actual container images do not match the runtime-approved images.\n' >&2
	exit 1
fi
if [[ ! "$app_compose_project" =~ ^[A-Za-z0-9._-]+$ ]]; then
	printf 'Unsafe Compose project label for performance runner lock.\n' >&2
	exit 1
fi
node "$SCRIPT_DIR/lib/runtime-contract.mjs" validate-app-identity \
	"$EXPECTED_APP_REVISION" "$EXPECTED_APP_IMAGE_ID" "$EXPECTED_APP_JAR_SHA256" "$EXPECTED_API_CONTRACT_SHA256" \
	"$EXPECTED_APP_REVISION" "$app_image_id" "$app_jar_sha256" "$EXPECTED_API_CONTRACT_SHA256" >/dev/null
node "$SCRIPT_DIR/lib/runtime-contract.mjs" validate-target "$BASE_URL" "$app_published_port" >/dev/null
RUNTIME_TEMP_DIR="$(mktemp -d /tmp/faithlog-197-prepare.XXXXXX)"
node "$SCRIPT_DIR/lib/source-image-provenance.mjs" capture \
	"$APP_SOURCE_WORKTREE" "$app_compose_working_dir" "$EXPECTED_APP_REVISION" "$app_image_id" \
	"$app_image_created_at" "$EXPECTED_API_CONTRACT_SHA256" "$RUNTIME_TEMP_DIR/prelock-source-image-provenance.json"

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
		printf 'App container environment is not approved for fixture writes: missing %s\n' "$expected_app_env" >&2
		exit 1
	fi
done

capture_runtime_identity() {
	local output_file="$1"
	local db_server_file="${output_file%.json}-database-server.json"
	local redis_server_file="${output_file%.json}-redis-server.txt"
	local current_app_container_id current_app_image_id current_app_started_at current_app_project current_app_service current_app_port
	local current_app_working_dir current_app_image_created_at current_app_jar_sha256 source_file
	local current_db_container_id current_db_image_id current_db_started_at current_db_project current_db_service
	local current_redis_container_id current_redis_image_id current_redis_started_at current_redis_project current_redis_service
	current_app_container_id="$(docker inspect --format '{{.Id}}' "$APP_CONTAINER")"
	current_app_image_id="$(docker inspect --format '{{.Image}}' "$APP_CONTAINER")"
	current_app_started_at="$(docker inspect --format '{{.State.StartedAt}}' "$APP_CONTAINER")"
	current_app_project="$(docker inspect --format '{{ index .Config.Labels "com.docker.compose.project" }}' "$APP_CONTAINER")"
	current_app_service="$(docker inspect --format '{{ index .Config.Labels "com.docker.compose.service" }}' "$APP_CONTAINER")"
	current_app_port="$(docker inspect --format '{{with (index .NetworkSettings.Ports "8080/tcp")}}{{(index . 0).HostPort}}{{end}}' "$APP_CONTAINER")"
	current_app_working_dir="$(docker inspect --format '{{ index .Config.Labels "com.docker.compose.project.working_dir" }}' "$APP_CONTAINER")"
	current_app_image_created_at="$(docker image inspect --format '{{.Created}}' "$current_app_image_id")"
	current_app_jar_sha256="$(docker exec "$APP_CONTAINER" sha256sum /app/app.jar | awk '{print $1}')"
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
	if [[ \
		"$current_app_container_id" != "$app_container_id" || "$current_app_image_id" != "$app_image_id" || "$current_app_started_at" != "$app_started_at" || \
		"$current_app_project" != "$app_compose_project" || "$current_app_service" != "$app_compose_service" || "$current_app_port" != "$app_published_port" || \
		"$current_app_working_dir" != "$app_compose_working_dir" || "$current_app_jar_sha256" != "$app_jar_sha256" || \
		"$current_db_container_id" != "$db_container_id" || "$current_db_image_id" != "$db_image_id" || "$current_db_started_at" != "$db_started_at" || \
		"$current_db_project" != "$db_compose_project" || "$current_db_service" != "$db_compose_service" || \
		"$current_redis_container_id" != "$redis_container_id" || "$current_redis_image_id" != "$redis_image_id" || "$current_redis_started_at" != "$redis_started_at" || \
		"$current_redis_project" != "$redis_compose_project" || "$current_redis_service" != "$redis_compose_service" ]]; then
		printf 'Runtime container identity changed after the pre-lock inspection.\n' >&2
		exit 1
	fi
	source_file="${output_file%.json}-source-image-provenance.json"
	node "$SCRIPT_DIR/lib/source-image-provenance.mjs" capture \
		"$APP_SOURCE_WORKTREE" "$current_app_working_dir" "$EXPECTED_APP_REVISION" "$current_app_image_id" \
		"$current_app_image_created_at" "$EXPECTED_API_CONTRACT_SHA256" "$source_file"
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

capture_runtime_identity "$RUNTIME_TEMP_DIR/runtime-identity-initial.json"

CURRENT_STAGE='namespace-blueprint'
reference_date="$(TZ=Asia/Seoul date +%F)"
REFERENCE_DATE="$reference_date" PREPARE_INPUT_FILE="$PREPARE_INPUT_FILE" DATASET_ID="$DATASET_ID" FIXTURE_RUN_ID="$FIXTURE_RUN_ID" \
	node "$SCRIPT_DIR/lib/devotion-prepare.mjs" blueprint >"$RUNTIME_TEMP_DIR/blueprint.json"
success_campus_name="$(node -e 'const b=require(process.argv[1]); process.stdout.write(b.successCampusName)' "$RUNTIME_TEMP_DIR/blueprint.json")"
rollback_campus_name="$(node -e 'const b=require(process.argv[1]); process.stdout.write(b.rollbackCampusName)' "$RUNTIME_TEMP_DIR/blueprint.json")"
email_prefix="$(node -e 'const b=require(process.argv[1]); process.stdout.write(b.emailPrefix)' "$RUNTIME_TEMP_DIR/blueprint.json")"

CURRENT_STAGE='namespace-read-only-check'
docker exec -i "$DB_CONTAINER" psql -h "$DB_HOST" -X -qAt -v ON_ERROR_STOP=1 -U "$DB_USER" -d "$DB_NAME" \
	-v success_campus_name="$success_campus_name" -v rollback_campus_name="$rollback_campus_name" -v email_prefix="$email_prefix" \
	<"$SCRIPT_DIR/preflight-devotion-namespace.sql" >"$RUNTIME_TEMP_DIR/namespace-evidence.json"

CURRENT_STAGE='reserve-fresh-namespace'
reservation="$(NAMESPACE_EVIDENCE_FILE="$RUNTIME_TEMP_DIR/namespace-evidence.json" PREPARE_REPORT_ROOT="$PREPARE_REPORT_ROOT" \
	RUNTIME_SECRET_ROOT="$RUNTIME_SECRET_ROOT" FIXTURE_RUN_ID="$FIXTURE_RUN_ID" node "$SCRIPT_DIR/lib/devotion-prepare.mjs" reserve)"
report_directory="$(node -e 'const v=JSON.parse(process.argv[1]); process.stdout.write(v.reportDirectory)' "$reservation")"
secret_directory="$(node -e 'const v=JSON.parse(process.argv[1]); process.stdout.write(v.secretDirectory)' "$reservation")"
cp "$RUNTIME_TEMP_DIR/namespace-evidence.json" "$report_directory/namespace-evidence.json"
cp "$RUNTIME_TEMP_DIR/runtime-identity-initial.json" "$report_directory/runtime-identity-initial.json"
cp "$RUNTIME_TEMP_DIR/runtime-identity-initial-source-image-provenance.json" "$report_directory/runtime-identity-initial-source-image-provenance.json"
chmod 600 "$report_directory"/*.json

CURRENT_STAGE='create-only-api-seed'
prepare_result="$(PREPARE_INPUT_FILE="$PREPARE_INPUT_FILE" DATASET_ID="$DATASET_ID" FIXTURE_RUN_ID="$FIXTURE_RUN_ID" \
	REFERENCE_DATE="$reference_date" BASE_URL="$BASE_URL" PREPARE_REPORT_DIRECTORY="$report_directory" \
	PREPARE_MAX_DURATION_SECONDS="$PREPARE_MAX_DURATION_SECONDS" TOKEN_TTL_SAFETY_SECONDS="$TOKEN_TTL_SAFETY_SECONDS" \
	RUNTIME_SECRET_DIRECTORY="$secret_directory" node "$SCRIPT_DIR/lib/devotion-prepare.mjs" prepare)"
fixture_manifest="$(node -e 'const v=JSON.parse(process.argv[1]); process.stdout.write(v.manifestPath)' "$prepare_result")"
credentials_file="$(node -e 'const v=JSON.parse(process.argv[1]); process.stdout.write(v.credentialsPath)' "$prepare_result")"

CURRENT_STAGE='fixture-preflight'
campus_id="$(node "$SCRIPT_DIR/lib/fixture-contract.mjs" field "$fixture_manifest" campusId)"
rollback_campus_id="$(node "$SCRIPT_DIR/lib/fixture-contract.mjs" field "$fixture_manifest" rollbackCampusId)"
warmup_week="$(node "$SCRIPT_DIR/lib/fixture-contract.mjs" field "$fixture_manifest" warmupWeekStartDate)"
measured_week="$(node "$SCRIPT_DIR/lib/fixture-contract.mjs" field "$fixture_manifest" measuredWeekStartDate)"
rollback_week="$(node "$SCRIPT_DIR/lib/fixture-contract.mjs" field "$fixture_manifest" rollbackWeekStartDate)"
warmup_user_ids="$(node "$SCRIPT_DIR/lib/fixture-contract.mjs" cohort-ids "$fixture_manifest" warmup)"
measured_user_ids="$(node "$SCRIPT_DIR/lib/fixture-contract.mjs" cohort-ids "$fixture_manifest" measured)"
rollback_user_ids="$(node "$SCRIPT_DIR/lib/fixture-contract.mjs" cohort-ids "$fixture_manifest" rollback)"
docker exec -i "$DB_CONTAINER" psql -h "$DB_HOST" -X -qAt -v ON_ERROR_STOP=1 -U "$DB_USER" -d "$DB_NAME" \
	-v campus_id="$campus_id" -v rollback_campus_id="$rollback_campus_id" \
	-v warmup_week_start_date="$warmup_week" -v measured_week_start_date="$measured_week" \
	-v rollback_week_start_date="$rollback_week" -v warmup_user_ids="$warmup_user_ids" \
	-v measured_user_ids="$measured_user_ids" -v rollback_user_ids="$rollback_user_ids" \
	<"$SCRIPT_DIR/preflight-devotion.sql" >"$report_directory/preflight.json"
node "$SCRIPT_DIR/lib/validate-devotion-preflight.mjs" "$fixture_manifest" "$report_directory/preflight.json" >/dev/null

CURRENT_STAGE='installed-k6-inspect'
BASE_URL="$BASE_URL" FIXTURE_MANIFEST="$fixture_manifest" CREDENTIALS_FILE="$credentials_file" \
	PHASE=warmup VUS="$WARMUP_VUS" MAX_DURATION="$WARMUP_MAX_DURATION" \
	k6 inspect "$SCRIPT_DIR/devotion-write.js" >"$report_directory/k6-inspect.json"

CURRENT_STAGE='final-runtime'
capture_runtime_identity "$report_directory/runtime-identity-final.json"
node "$SCRIPT_DIR/lib/validate-runtime-identity.mjs" validate-pair \
	"$report_directory/runtime-identity-initial.json" "$report_directory/runtime-identity-final.json" final \
	"$report_directory/runtime-identity-final-gate.json" >/dev/null

CURRENT_STAGE='preparation-inspect'
FIXTURE_MANIFEST="$fixture_manifest" CREDENTIALS_FILE="$credentials_file" \
PREFLIGHT_EVIDENCE_FILE="$report_directory/preflight.json" PREPARATION_RECEIPT_FILE="$report_directory/preparation-receipt.json" \
WARMUP_VUS="$WARMUP_VUS" MEASURED_VUS="$MEASURED_VUS" ROLLBACK_VUS="$ROLLBACK_VUS" \
WARMUP_MAX_DURATION="$WARMUP_MAX_DURATION" MEASURED_MAX_DURATION="$MEASURED_MAX_DURATION" ROLLBACK_MAX_DURATION="$ROLLBACK_MAX_DURATION" \
TOKEN_TTL_SAFETY_SECONDS="$TOKEN_TTL_SAFETY_SECONDS" \
	node "$SCRIPT_DIR/lib/devotion-prepare.mjs" inspect >"$report_directory/preparation-inspection.json"

CURRENT_STAGE='complete'
printf 'Issue #197 devotion preparation evidence: %s\n' "$report_directory/preparation-inspection.json"
printf 'Issue #197 runtime-only credentials: %s\n' "$credentials_file"
