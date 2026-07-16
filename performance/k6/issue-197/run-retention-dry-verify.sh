#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
REPO_ROOT="$(CDPATH= cd -- "$SCRIPT_DIR/../../.." && pwd)"
LOCK_DIR=''
LOCK_OWNED='false'
EXECUTION_MODE='dry-verify-only'
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
	if [[ "$LOCK_OWNED" == 'true' && -n "$LOCK_DIR" ]]; then
		rmdir "$LOCK_DIR" 2>/dev/null || true
		LOCK_OWNED='false'
	fi
}

if [[ -z "${REJECTION_EVIDENCE_FILE-}" ]]; then
	printf '{"schemaVersion":1,"scenario":"retention","status":"rejected","automaticAdoption":false,"stage":"bootstrap","exitCode":1}\n' >&2
	exit 1
fi
finalize() {
	local status=$?
	trap - EXIT INT TERM
	if [[ "$status" -ne 0 ]]; then
		node "$SCRIPT_DIR/lib/rejection-contract.mjs" write-first \
			"$REJECTION_EVIDENCE_FILE" retention "$CURRENT_STAGE" "$status" >/dev/null 2>&1 || \
			printf '{"schemaVersion":1,"scenario":"retention","status":"rejected","automaticAdoption":false,"stage":"%s","exitCode":%s}\n' "$CURRENT_STAGE" "$status" >&2
	fi
	release_lock
	exit "$status"
}
trap finalize EXIT
trap 'exit 130' INT TERM
node "$SCRIPT_DIR/lib/rejection-contract.mjs" prepare "$REJECTION_EVIDENCE_FILE" >/dev/null

CURRENT_STAGE='runtime-input'
for name in RETENTION_MANIFEST APP_CONTAINER DB_CONTAINER REDIS_CONTAINER EXPECTED_COMPOSE_PROJECT \
	EXPECTED_APP_COMPOSE_SERVICE EXPECTED_DB_COMPOSE_SERVICE EXPECTED_REDIS_COMPOSE_SERVICE \
	EXPECTED_APP_REVISION EXPECTED_APP_IMAGE_ID EXPECTED_APP_JAR_SHA256 EXPECTED_API_CONTRACT_SHA256 \
	EXPECTED_DB_IMAGE_ID EXPECTED_REDIS_IMAGE_ID EXPECTED_FLYWAY_VERSION EXPECTED_FLYWAY_SCRIPT EXPECTED_FLYWAY_CHECKSUM \
	DB_HOST REDIS_HOST EXPECTED_DB_PORT EXPECTED_REDIS_PORT \
	DB_NAME DB_USER ALLOW_ISOLATED_RETENTION; do
	require_env "$name"
done

if [[ "$ALLOW_ISOLATED_RETENTION" != 'true' ]]; then
	printf 'Retention access is blocked unless ALLOW_ISOLATED_RETENTION=true.\n' >&2
	exit 1
fi
case "$EXPECTED_COMPOSE_PROJECT" in
	faithlog-perf-197-*) ;;
	*) printf 'Shared/default Compose is refused; expected faithlog-perf-197-* isolation.\n' >&2; exit 1 ;;
esac

CURRENT_STAGE='runtime-tools'
for command_name in node docker; do
	if ! command -v "$command_name" >/dev/null 2>&1; then
		printf 'Required command is missing: %s\n' "$command_name" >&2
		exit 1
	fi
done

CURRENT_STAGE='scenario-contract'
node "$SCRIPT_DIR/lib/runtime-contract.mjs" validate-host "$DB_HOST" DB_HOST >/dev/null
node "$SCRIPT_DIR/lib/runtime-contract.mjs" validate-host "$REDIS_HOST" REDIS_HOST >/dev/null
node "$SCRIPT_DIR/lib/fixture-contract.mjs" validate-retention "$RETENTION_MANIFEST" >/dev/null

CURRENT_STAGE='prelock-identity'
app_compose_project="$(docker inspect --format '{{ index .Config.Labels "com.docker.compose.project" }}' "$APP_CONTAINER")"
app_compose_service="$(docker inspect --format '{{ index .Config.Labels "com.docker.compose.service" }}' "$APP_CONTAINER")"
app_container_id="$(docker inspect --format '{{.Id}}' "$APP_CONTAINER")"
app_image_id="$(docker inspect --format '{{.Image}}' "$APP_CONTAINER")"
app_started_at="$(docker inspect --format '{{.State.StartedAt}}' "$APP_CONTAINER")"
app_revision="$(docker inspect --format '{{ index .Config.Labels "org.opencontainers.image.revision" }}' "$APP_CONTAINER")"
app_api_contract_sha256="$(docker inspect --format '{{ index .Config.Labels "org.opencontainers.image.api-contract-sha256" }}' "$APP_CONTAINER")"
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
for label_value in \
	"$app_compose_project" "$app_compose_service" "$app_container_id" "$app_image_id" "$app_started_at" "$app_published_port" \
	"$app_revision" "$app_api_contract_sha256" "$app_jar_sha256" \
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
node "$SCRIPT_DIR/lib/runtime-contract.mjs" validate-app-identity \
	"$EXPECTED_APP_REVISION" "$EXPECTED_APP_IMAGE_ID" "$EXPECTED_APP_JAR_SHA256" "$EXPECTED_API_CONTRACT_SHA256" \
	"$app_revision" "$app_image_id" "$app_jar_sha256" "$app_api_contract_sha256" >/dev/null
case "$app_compose_project" in
	faithlog-perf-197-*) ;;
	*) printf 'Actual Compose label is shared/default; retention is refused immediately.\n' >&2; exit 1 ;;
esac
if [[ ! "$app_compose_project" =~ ^[A-Za-z0-9._-]+$ ]]; then
	printf 'Unsafe Compose project label for performance runner lock: %s\n' "$app_compose_project" >&2
	exit 1
fi

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
	"SPRING_DATASOURCE_URL=jdbc:postgresql://$EXPECTED_DB_COMPOSE_SERVICE:$EXPECTED_DB_PORT/$DB_NAME" \
	"SPRING_DATASOURCE_USERNAME=$DB_USER" \
	"SPRING_DATA_REDIS_HOST=$EXPECTED_REDIS_COMPOSE_SERVICE" \
	"SPRING_DATA_REDIS_PORT=$EXPECTED_REDIS_PORT"; do
	if ! grep -Fqx "$expected_app_env" <<<"$app_environment"; then
		printf 'Retention app runtime target is not approved: missing %s\n' "$expected_app_env" >&2
		exit 1
	fi
done

fixture_run_id="$(node "$SCRIPT_DIR/lib/fixture-contract.mjs" field "$RETENTION_MANIFEST" fixtureRunId)"
dataset_id="$(node "$SCRIPT_DIR/lib/fixture-contract.mjs" field "$RETENTION_MANIFEST" datasetId)"
dataset_prefix="$(node "$SCRIPT_DIR/lib/fixture-contract.mjs" field "$RETENTION_MANIFEST" datasetPrefix)"
reference_instant="$(node "$SCRIPT_DIR/lib/fixture-contract.mjs" field "$RETENTION_MANIFEST" referenceInstant)"
report_root="build/reports/k6/issue-197/$fixture_run_id/retention"
mkdir -p "$report_root"

capture_runtime_identity() {
	local output_file="$1"
	local checkpoint="$2"
	local db_server_file="${output_file%.json}-database-server.json"
	local redis_server_file="${output_file%.json}-redis-server.txt"
	local current_app_container_id current_app_image_id current_app_started_at
	local current_app_revision current_app_jar_sha256 current_app_api_contract_sha256
	local current_app_project current_app_service current_app_port
	local current_db_container_id current_db_image_id current_db_started_at
	local current_db_project current_db_service current_database
	local current_redis_container_id current_redis_image_id current_redis_started_at
	local current_redis_project current_redis_service
	current_app_container_id="$(docker inspect --format '{{.Id}}' "$APP_CONTAINER")"
	current_app_image_id="$(docker inspect --format '{{.Image}}' "$APP_CONTAINER")"
	current_app_started_at="$(docker inspect --format '{{.State.StartedAt}}' "$APP_CONTAINER")"
	current_app_revision="$(docker inspect --format '{{ index .Config.Labels "org.opencontainers.image.revision" }}' "$APP_CONTAINER")"
	current_app_api_contract_sha256="$(docker inspect --format '{{ index .Config.Labels "org.opencontainers.image.api-contract-sha256" }}' "$APP_CONTAINER")"
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
	node "$SCRIPT_DIR/lib/runtime-contract.mjs" validate-app-identity \
		"$EXPECTED_APP_REVISION" "$EXPECTED_APP_IMAGE_ID" "$EXPECTED_APP_JAR_SHA256" "$EXPECTED_API_CONTRACT_SHA256" \
		"$current_app_revision" "$current_app_image_id" "$current_app_jar_sha256" "$current_app_api_contract_sha256" >/dev/null
	if [[ "$checkpoint" == 'initial' && ( \
		"$current_app_container_id" != "$app_container_id" || "$current_app_image_id" != "$app_image_id" || \
		"$current_app_started_at" != "$app_started_at" || "$current_app_revision" != "$app_revision" || \
		"$current_app_jar_sha256" != "$app_jar_sha256" || "$current_app_api_contract_sha256" != "$app_api_contract_sha256" || \
		"$current_app_project" != "$app_compose_project" || \
		"$current_app_service" != "$app_compose_service" || "$current_app_port" != "$app_published_port" || \
		"$current_db_container_id" != "$db_container_id" || "$current_db_image_id" != "$db_image_id" || \
		"$current_db_started_at" != "$db_started_at" || "$current_db_project" != "$db_compose_project" || \
		"$current_db_service" != "$db_compose_service" || "$current_redis_container_id" != "$redis_container_id" || \
		"$current_redis_image_id" != "$redis_image_id" || "$current_redis_started_at" != "$redis_started_at" || \
		"$current_redis_project" != "$redis_compose_project" || "$current_redis_service" != "$redis_compose_service" ) ]]; then
		printf 'Retention runtime container identity changed between lock inspection and initial capture.\n' >&2
		exit 1
	fi
	docker exec -i "$DB_CONTAINER" psql -h "$DB_HOST" -X -qAt -v ON_ERROR_STOP=1 -U "$DB_USER" -d "$DB_NAME" \
		<"$SCRIPT_DIR/runtime-identity.sql" >"$db_server_file"
	docker exec "$REDIS_CONTAINER" redis-cli -h "$REDIS_HOST" -p "$EXPECTED_REDIS_PORT" --raw INFO server >"$redis_server_file"
	APP_CONTAINER_ID="$current_app_container_id" APP_IMAGE_ID="$current_app_image_id" APP_STARTED_AT="$current_app_started_at" \
	APP_COMPOSE_PROJECT="$current_app_project" APP_COMPOSE_SERVICE="$current_app_service" APP_PUBLISHED_PORT="$current_app_port" \
	APP_REVISION="$current_app_revision" APP_JAR_SHA256="$current_app_jar_sha256" APP_API_CONTRACT_SHA256="$current_app_api_contract_sha256" \
	DB_CONTAINER_ID="$current_db_container_id" DB_IMAGE_ID="$current_db_image_id" DB_STARTED_AT="$current_db_started_at" \
	DB_COMPOSE_PROJECT="$current_db_project" DB_COMPOSE_SERVICE="$current_db_service" \
	REDIS_CONTAINER_ID="$current_redis_container_id" REDIS_IMAGE_ID="$current_redis_image_id" REDIS_STARTED_AT="$current_redis_started_at" \
	REDIS_COMPOSE_PROJECT="$current_redis_project" REDIS_COMPOSE_SERVICE="$current_redis_service" \
	EXPECTED_FLYWAY_VERSION="$EXPECTED_FLYWAY_VERSION" EXPECTED_FLYWAY_SCRIPT="$EXPECTED_FLYWAY_SCRIPT" EXPECTED_FLYWAY_CHECKSUM="$EXPECTED_FLYWAY_CHECKSUM" \
	DB_HOST="$DB_HOST" EXPECTED_DB_PORT="$EXPECTED_DB_PORT" EXPECTED_REDIS_PORT="$EXPECTED_REDIS_PORT" \
		node "$SCRIPT_DIR/lib/validate-runtime-identity.mjs" capture "$db_server_file" "$redis_server_file" "$output_file"
	current_database="$(node "$SCRIPT_DIR/lib/validate-runtime-identity.mjs" field "$output_file" databaseServer currentDatabase)"
	if [[ "$current_database" != "$DB_NAME" ]]; then
		printf 'Retention PostgreSQL current database mismatch: expected=%s actual=%s\n' "$DB_NAME" "$current_database" >&2
		exit 1
	fi
}

capture_runtime_identity "$report_root/runtime-identity-initial.json" initial

APP_CONTAINER="$APP_CONTAINER" APP_PROJECT="$app_compose_project" APP_SERVICE="$app_compose_service" \
DB_CONTAINER="$DB_CONTAINER" DB_PROJECT="$db_compose_project" DB_SERVICE="$db_compose_service" \
REDIS_CONTAINER="$REDIS_CONTAINER" REDIS_PROJECT="$redis_compose_project" REDIS_SERVICE="$redis_compose_service" \
EXPECTED_PROJECT="$EXPECTED_COMPOSE_PROJECT" EXPECTED_APP_SERVICE="$EXPECTED_APP_COMPOSE_SERVICE" EXPECTED_DB_SERVICE="$EXPECTED_DB_COMPOSE_SERVICE" \
EXPECTED_REDIS_SERVICE="$EXPECTED_REDIS_COMPOSE_SERVICE" \
RUNNER_LOCK="$LOCK_DIR" DATASET_ID="$dataset_id" FIXTURE_RUN_ID="$fixture_run_id" \
RUNTIME_IDENTITY_FILE="$report_root/runtime-identity-initial.json" \
node -e '
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
		runnerLock: process.env.RUNNER_LOCK, executionMode: "dry-verify-only",
		runtimeIdentity: JSON.parse(require("node:fs").readFileSync(process.env.RUNTIME_IDENTITY_FILE, "utf8")),
	};
	process.stdout.write(`${JSON.stringify(evidence, null, 2)}\n`);
' >"$report_root/environment.json"

CURRENT_STAGE='candidate-dry-verify'
docker exec -i "$DB_CONTAINER" psql -h "$DB_HOST" -X -q -tA -v ON_ERROR_STOP=1 -U "$DB_USER" -d "$DB_NAME" \
	-v dataset_id="$dataset_id" -v fixture_run_id="$fixture_run_id" \
	-v dataset_prefix="$dataset_prefix" -v reference_instant="$reference_instant" \
	<"$SCRIPT_DIR/retention-dry-verify.sql" >"$report_root/actual-candidate-counts.json"

CURRENT_STAGE='final-identity'
capture_runtime_identity "$report_root/runtime-identity-retention-after.json" retentionAfter
node "$SCRIPT_DIR/lib/validate-runtime-identity.mjs" validate-pair \
	"$report_root/runtime-identity-initial.json" "$report_root/runtime-identity-retention-after.json" retentionAfter \
	"$report_root/runtime-identity-retention-after-gate.json"

CURRENT_STAGE='evidence'
node "$SCRIPT_DIR/retention-dry-verify.mjs" \
	"$RETENTION_MANIFEST" \
	"$report_root/actual-candidate-counts.json" \
	"$report_root/scenario-evidence.json" \
	"$app_compose_project"

printf 'Issue #197 retention %s evidence: %s\n' "$EXECUTION_MODE" "$report_root/scenario-evidence.json"
