#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
BASE_URL=${BASE_URL:?BASE_URL is required and must match APP_CONTAINER_ID published endpoint}
PERF_DATASET_ID=${PERF_DATASET_ID:?PERF_DATASET_ID is required}
PERF_FIXTURE_RUN_ID=${PERF_FIXTURE_RUN_ID:?PERF_FIXTURE_RUN_ID is required}
PERF_EXECUTION_RUN_ID=${PERF_EXECUTION_RUN_ID:?PERF_EXECUTION_RUN_ID is required and must be unique}
PERF_ADMIN_EMAIL=${PERF_ADMIN_EMAIL:?PERF_ADMIN_EMAIL is required}
PERF_ADMIN_PASSWORD=${PERF_ADMIN_PASSWORD:?PERF_ADMIN_PASSWORD is required}
CAMPUS_ID=${CAMPUS_ID:?CAMPUS_ID is required}
ISOLATION_CAMPUS_ID=${ISOLATION_CAMPUS_ID:?ISOLATION_CAMPUS_ID is required}
ISOLATION_USER_ID=${ISOLATION_USER_ID:?ISOLATION_USER_ID is required}
APP_CONTAINER_ID=${APP_CONTAINER_ID:?APP_CONTAINER_ID is required}
EXPECTED_APP_COMPOSE_SERVICE=${EXPECTED_APP_COMPOSE_SERVICE:?EXPECTED_APP_COMPOSE_SERVICE requires approval}
POSTGRES_CONTAINER_ID=${POSTGRES_CONTAINER_ID:?POSTGRES_CONTAINER_ID is required}
EXPECTED_POSTGRES_COMPOSE_SERVICE=${EXPECTED_POSTGRES_COMPOSE_SERVICE:?EXPECTED_POSTGRES_COMPOSE_SERVICE requires approval}
POSTGRES_USER=${POSTGRES_USER:?POSTGRES_USER is required}
POSTGRES_DB=${POSTGRES_DB:?POSTGRES_DB is required}
POSTGRES_PASSWORD=${POSTGRES_PASSWORD:?POSTGRES_PASSWORD is required}
WARMUP_VUS=${WARMUP_VUS:?WARMUP_VUS requires user approval}
WARMUP_DURATION=${WARMUP_DURATION:?WARMUP_DURATION requires user approval}
MEASURED_VUS=${MEASURED_VUS:?MEASURED_VUS requires user approval}
MEASURED_DURATION=${MEASURED_DURATION:?MEASURED_DURATION requires user approval}
MAX_FAILURE_RATE=${MAX_FAILURE_RATE:?MAX_FAILURE_RATE requires user approval and must be 0}
TOKEN_SAFETY_MARGIN_SECONDS=${TOKEN_SAFETY_MARGIN_SECONDS:?TOKEN_SAFETY_MARGIN_SECONDS requires user approval}
K6_BIN=${K6_BIN:-k6}
REPORT_ROOT=${PERF_REPORT_ROOT:-$SCRIPT_DIR/reports}
REPORT_DIR="$REPORT_ROOT/$PERF_DATASET_ID/$PERF_FIXTURE_RUN_ID/$PERF_EXECUTION_RUN_ID"

if [[ ! "$PERF_EXECUTION_RUN_ID" =~ ^EXEC195_[A-Za-z0-9_]+$ ]]; then
	printf '%s\n' 'PERF_EXECUTION_RUN_ID must be a fresh EXEC195_ identifier.' >&2
	exit 1
fi

# Keep API and database credentials as shell-only values. Pass each credential only
# to the one child process that requires it instead of exporting it to k6 or reports.
export -n PERF_ADMIN_EMAIL PERF_ADMIN_PASSWORD POSTGRES_USER POSTGRES_DB POSTGRES_PASSWORD
unset PERF_ACCESS_TOKEN

COMPOSE_PROJECT=$(docker inspect --format '{{ index .Config.Labels "com.docker.compose.project" }}' "$APP_CONTAINER_ID")
COMPOSE_SERVICE=$(docker inspect --format '{{ index .Config.Labels "com.docker.compose.service" }}' "$APP_CONTAINER_ID")
POSTGRES_COMPOSE_PROJECT=$(docker inspect --format '{{ index .Config.Labels "com.docker.compose.project" }}' "$POSTGRES_CONTAINER_ID")
POSTGRES_COMPOSE_SERVICE=$(docker inspect --format '{{ index .Config.Labels "com.docker.compose.service" }}' "$POSTGRES_CONTAINER_ID")
APP_PUBLISHED_PORTS_JSON=$(docker inspect --format '{{json .NetworkSettings.Ports}}' "$APP_CONTAINER_ID")
if [[ -z "$COMPOSE_PROJECT" || -z "$COMPOSE_SERVICE" || -z "$POSTGRES_COMPOSE_PROJECT" || -z "$POSTGRES_COMPOSE_SERVICE" ]]; then
	printf '%s\n' 'The app and PostgreSQL containers must expose actual Docker Compose project/service labels.' >&2
	exit 1
fi
if [[ "$COMPOSE_PROJECT" != "$POSTGRES_COMPOSE_PROJECT" ]]; then
	printf 'Compose project mismatch: app=%s postgres=%s\n' "$COMPOSE_PROJECT" "$POSTGRES_COMPOSE_PROJECT" >&2
	exit 1
fi
PRE_LOCK_TARGET_IDENTITY_JSON=$( \
	BASE_URL="$BASE_URL" \
	APP_CONTAINER_ID="$APP_CONTAINER_ID" \
	APP_COMPOSE_SERVICE="$COMPOSE_SERVICE" \
	EXPECTED_APP_COMPOSE_SERVICE="$EXPECTED_APP_COMPOSE_SERVICE" \
	APP_PUBLISHED_PORTS_JSON="$APP_PUBLISHED_PORTS_JSON" \
	POSTGRES_COMPOSE_SERVICE="$POSTGRES_COMPOSE_SERVICE" \
	EXPECTED_POSTGRES_COMPOSE_SERVICE="$EXPECTED_POSTGRES_COMPOSE_SERVICE" \
		node "$SCRIPT_DIR/validate-target-identity.mjs"
)
if [[ ! "$COMPOSE_PROJECT" =~ ^[A-Za-z0-9_.-]+$ ]]; then
	printf 'Unsafe Compose project label for shared lock: %s\n' "$COMPOSE_PROJECT" >&2
	exit 1
fi

LOCK_DIR="/tmp/faithlog-performance-${COMPOSE_PROJECT}.lock"
if ! mkdir "$LOCK_DIR"; then
	printf 'Shared performance runner lock is held: %s\n' "$LOCK_DIR" >&2
	exit 1
fi
INITIAL_RUNTIME_IDENTITY_TMP=
cleanup() {
	if [[ -n "$INITIAL_RUNTIME_IDENTITY_TMP" && -f "$INITIAL_RUNTIME_IDENTITY_TMP" ]]; then
		rm -f "$INITIAL_RUNTIME_IDENTITY_TMP"
	fi
	rmdir "$LOCK_DIR"
}
trap cleanup EXIT

mkdir -p "$REPORT_ROOT/$PERF_DATASET_ID/$PERF_FIXTURE_RUN_ID"
if ! mkdir "$REPORT_DIR"; then
	printf 'Measurement report directory already exists; use a fresh PERF_EXECUTION_RUN_ID: %s\n' "$REPORT_DIR" >&2
	exit 1
fi

capture_runtime_identity() {
	local output_path=$1
	local app_actual_id app_name app_image_id app_started_at app_project app_service app_ports
	local postgres_actual_id postgres_name postgres_image_id postgres_started_at postgres_project postgres_service postgres_ports
	local database_identity
	app_actual_id=$(docker inspect --format '{{.Id}}' "$APP_CONTAINER_ID")
	app_name=$(docker inspect --format '{{.Name}}' "$APP_CONTAINER_ID")
	app_image_id=$(docker inspect --format '{{.Image}}' "$APP_CONTAINER_ID")
	app_started_at=$(docker inspect --format '{{.State.StartedAt}}' "$APP_CONTAINER_ID")
	app_project=$(docker inspect --format '{{ index .Config.Labels "com.docker.compose.project" }}' "$APP_CONTAINER_ID")
	app_service=$(docker inspect --format '{{ index .Config.Labels "com.docker.compose.service" }}' "$APP_CONTAINER_ID")
	app_ports=$(docker inspect --format '{{json .NetworkSettings.Ports}}' "$APP_CONTAINER_ID")
	postgres_actual_id=$(docker inspect --format '{{.Id}}' "$POSTGRES_CONTAINER_ID")
	postgres_name=$(docker inspect --format '{{.Name}}' "$POSTGRES_CONTAINER_ID")
	postgres_image_id=$(docker inspect --format '{{.Image}}' "$POSTGRES_CONTAINER_ID")
	postgres_started_at=$(docker inspect --format '{{.State.StartedAt}}' "$POSTGRES_CONTAINER_ID")
	postgres_project=$(docker inspect --format '{{ index .Config.Labels "com.docker.compose.project" }}' "$POSTGRES_CONTAINER_ID")
	postgres_service=$(docker inspect --format '{{ index .Config.Labels "com.docker.compose.service" }}' "$POSTGRES_CONTAINER_ID")
	postgres_ports=$(docker inspect --format '{{json .NetworkSettings.Ports}}' "$POSTGRES_CONTAINER_ID")
	database_identity=$(PGPASSWORD="$POSTGRES_PASSWORD" docker exec \
		-e PGPASSWORD \
		-e PGAPPNAME=faithlog-issue195-runtime-identity \
		"$POSTGRES_CONTAINER_ID" \
		psql -h 127.0.0.1 -X -At -v ON_ERROR_STOP=1 \
			-U "$POSTGRES_USER" \
			-d "$POSTGRES_DB" \
			-c "select json_build_object('name', current_database(), 'serverAddress', inet_server_addr()::text, 'serverPort', inet_server_port(), 'postmasterStartedAt', pg_postmaster_start_time()::text)::text;")
	OUTPUT_PATH="$output_path" \
	APP_ACTUAL_ID="$app_actual_id" \
	APP_NAME="$app_name" \
	APP_IMAGE_ID="$app_image_id" \
	APP_STARTED_AT="$app_started_at" \
	APP_PROJECT="$app_project" \
	APP_SERVICE="$app_service" \
	APP_PORTS="$app_ports" \
	POSTGRES_ACTUAL_ID="$postgres_actual_id" \
	POSTGRES_NAME="$postgres_name" \
	POSTGRES_IMAGE_ID="$postgres_image_id" \
	POSTGRES_STARTED_AT="$postgres_started_at" \
	POSTGRES_PROJECT="$postgres_project" \
	POSTGRES_SERVICE="$postgres_service" \
	POSTGRES_PORTS="$postgres_ports" \
	DATABASE_IDENTITY="$database_identity" \
		node -e 'const fs=require("node:fs"); const e=process.env; fs.writeFileSync(e.OUTPUT_PATH, JSON.stringify({app:{containerId:e.APP_ACTUAL_ID,name:e.APP_NAME,imageId:e.APP_IMAGE_ID,startedAt:e.APP_STARTED_AT,composeProject:e.APP_PROJECT,composeService:e.APP_SERVICE,publishedPorts:JSON.parse(e.APP_PORTS)},postgres:{containerId:e.POSTGRES_ACTUAL_ID,name:e.POSTGRES_NAME,imageId:e.POSTGRES_IMAGE_ID,startedAt:e.POSTGRES_STARTED_AT,composeProject:e.POSTGRES_PROJECT,composeService:e.POSTGRES_SERVICE,publishedPorts:JSON.parse(e.POSTGRES_PORTS)},database:JSON.parse(e.DATABASE_IDENTITY)},null,2)+"\n");'
}

INITIAL_RUNTIME_IDENTITY_TMP=$(mktemp "/tmp/faithlog-issue195-${PERF_EXECUTION_RUN_ID}-identity.XXXXXX")
capture_runtime_identity "$INITIAL_RUNTIME_IDENTITY_TMP"
TARGET_IDENTITY_JSON=$(node "$SCRIPT_DIR/validate-approved-runtime-target.mjs" \
	"$INITIAL_RUNTIME_IDENTITY_TMP" \
	"$COMPOSE_PROJECT" \
	"$BASE_URL" \
	"$EXPECTED_APP_COMPOSE_SERVICE" \
	"$EXPECTED_POSTGRES_COMPOSE_SERVICE" \
	"$POSTGRES_DB")

RUN_STARTED_AT=$(date -u '+%Y-%m-%dT%H:%M:%SZ')
INITIAL_RUNTIME_IDENTITY="$REPORT_DIR/initial-runtime-identity.json"
mv "$INITIAL_RUNTIME_IDENTITY_TMP" "$INITIAL_RUNTIME_IDENTITY"
INITIAL_RUNTIME_IDENTITY_TMP=
docker inspect --format '{{json .Config.Labels}}' "$APP_CONTAINER_ID" > "$REPORT_DIR/compose-labels.json"
docker inspect --format '{{json .Config.Labels}}' "$POSTGRES_CONTAINER_ID" > "$REPORT_DIR/postgres-compose-labels.json"
printf '%s\n' "$TARGET_IDENTITY_JSON" > "$REPORT_DIR/target-identity.json"

METADATA_PATH="$REPORT_DIR/run-metadata.json" \
BASE_URL="$BASE_URL" \
PERF_DATASET_ID="$PERF_DATASET_ID" \
PERF_FIXTURE_RUN_ID="$PERF_FIXTURE_RUN_ID" \
PERF_EXECUTION_RUN_ID="$PERF_EXECUTION_RUN_ID" \
CAMPUS_ID="$CAMPUS_ID" \
ISOLATION_CAMPUS_ID="$ISOLATION_CAMPUS_ID" \
ISOLATION_USER_ID="$ISOLATION_USER_ID" \
COMPOSE_PROJECT="$COMPOSE_PROJECT" \
COMPOSE_SERVICE="$COMPOSE_SERVICE" \
POSTGRES_COMPOSE_SERVICE="$POSTGRES_COMPOSE_SERVICE" \
WARMUP_VUS="$WARMUP_VUS" \
WARMUP_DURATION="$WARMUP_DURATION" \
MEASURED_VUS="$MEASURED_VUS" \
MEASURED_DURATION="$MEASURED_DURATION" \
MAX_FAILURE_RATE="$MAX_FAILURE_RATE" \
TOKEN_SAFETY_MARGIN_SECONDS="$TOKEN_SAFETY_MARGIN_SECONDS" \
node -e 'const fs=require("node:fs"); const e=process.env; fs.writeFileSync(e.METADATA_PATH, JSON.stringify({issue:195,status:"measurement-pending",baseUrl:e.BASE_URL,datasetId:e.PERF_DATASET_ID,fixtureRunId:e.PERF_FIXTURE_RUN_ID,executionRunId:e.PERF_EXECUTION_RUN_ID,campusId:Number(e.CAMPUS_ID),isolationCampusId:Number(e.ISOLATION_CAMPUS_ID),isolationUserId:Number(e.ISOLATION_USER_ID),composeProject:e.COMPOSE_PROJECT,composeService:e.COMPOSE_SERVICE,postgresComposeService:e.POSTGRES_COMPOSE_SERVICE,warmup:{vus:Number(e.WARMUP_VUS),duration:e.WARMUP_DURATION},measured:{vus:Number(e.MEASURED_VUS),duration:e.MEASURED_DURATION},maxFailureRate:Number(e.MAX_FAILURE_RATE),tokenSafetyMarginSeconds:Number(e.TOKEN_SAFETY_MARGIN_SECONDS),startedAt:new Date().toISOString()},null,2)+"\n");'

export BASE_URL PERF_DATASET_ID PERF_FIXTURE_RUN_ID PERF_EXECUTION_RUN_ID CAMPUS_ID ISOLATION_CAMPUS_ID ISOLATION_USER_ID
export PERF_REPORT_DIR="$REPORT_DIR"

REDACT_ADMIN_EMAIL=$PERF_ADMIN_EMAIL
REDACT_ADMIN_PASSWORD=$PERF_ADMIN_PASSWORD

node "$SCRIPT_DIR/validate-runtime-continuity.mjs" \
	"$INITIAL_RUNTIME_IDENTITY" \
	"$INITIAL_RUNTIME_IDENTITY" \
	"$REPORT_DIR/initial-runtime-continuity-adoption.json"

capture_resource_snapshot() {
	local scenario=$1
	local case_name=$2
	local phase=$3
	local output_path="$REPORT_DIR/${scenario}-${case_name}-docker-stats.ndjson"
	local app_actual_id app_actual_name postgres_actual_id postgres_actual_name app_stats postgres_stats
	app_actual_id=$(docker inspect --format '{{.Id}}' "$APP_CONTAINER_ID")
	app_actual_name=$(docker inspect --format '{{.Name}}' "$APP_CONTAINER_ID")
	postgres_actual_id=$(docker inspect --format '{{.Id}}' "$POSTGRES_CONTAINER_ID")
	postgres_actual_name=$(docker inspect --format '{{.Name}}' "$POSTGRES_CONTAINER_ID")
	app_stats=$(docker stats --no-stream --format '{{json .}}' "$app_actual_id")
	postgres_stats=$(docker stats --no-stream --format '{{json .}}' "$postgres_actual_id")
	APP_ACTUAL_ID="$app_actual_id" \
	APP_ACTUAL_NAME="$app_actual_name" \
	APP_STATS_JSON="$app_stats" \
	POSTGRES_ACTUAL_ID="$postgres_actual_id" \
	POSTGRES_ACTUAL_NAME="$postgres_actual_name" \
	POSTGRES_STATS_JSON="$postgres_stats" \
		node "$SCRIPT_DIR/capture-resource-snapshot.mjs" "$scenario" "$case_name" "$phase" "$output_path"
}

record_case_window() {
	local scenario=$1
	local case_name=$2
	local event=$3
	local status=$4
	local at
	at=$(node -e 'process.stdout.write(new Date().toISOString())')
	printf '{"scenario":"%s","case":"%s","event":"%s","status":"%s","at":"%s"}\n' \
		"$scenario" "$case_name" "$event" "$status" "$at" \
		>> "$REPORT_DIR/case-windows.ndjson"
}

CASES=(
	admin_users:first_page
	admin_users:middle_page
	admin_users:large_page
	admin_users:role_filter
	admin_users:search_filter
	admin_campuses:first_page
	admin_campuses:middle_page
	admin_campuses:large_page
	admin_campuses:active_search
	campus_members:full_list
	duty_assignments:full_list
)

for entry in "${CASES[@]}"; do
	SCENARIO=${entry%%:*}
	CASE=${entry##*:}
	EVIDENCE_CASE="${SCENARIO}-${CASE}"
	export SCENARIO CASE

	PERF_ACCESS_TOKEN=$(
		PERF_ADMIN_EMAIL="$PERF_ADMIN_EMAIL" \
		PERF_ADMIN_PASSWORD="$PERF_ADMIN_PASSWORD" \
			node "$SCRIPT_DIR/runtime-login.mjs"
	)
	if ! printf '%s' "$PERF_ACCESS_TOKEN" \
		| WARMUP_DURATION="$WARMUP_DURATION" \
			MEASURED_DURATION="$MEASURED_DURATION" \
			TOKEN_SAFETY_MARGIN_SECONDS="$TOKEN_SAFETY_MARGIN_SECONDS" \
			TOKEN_LIFETIME_PHASE=case \
				node "$SCRIPT_DIR/validate-token-lifetime.mjs"; then
		unset PERF_ACCESS_TOKEN
		printf 'Access token lifetime is insufficient for case: %s/%s\n' "$SCENARIO" "$CASE" >&2
		exit 1
	fi
	WARMUP_SUMMARY="$REPORT_DIR/warmup-${SCENARIO}-${CASE}.json"
	WARMUP_ADOPTION="$REPORT_DIR/warmup-${SCENARIO}-${CASE}-adoption.json"
	if PERF_ACCESS_TOKEN="$PERF_ACCESS_TOKEN" \
		MAX_FAILURE_RATE="$MAX_FAILURE_RATE" \
		VUS="$WARMUP_VUS" \
		DURATION="$WARMUP_DURATION" \
		"$K6_BIN" run \
			--summary-export "$WARMUP_SUMMARY" \
			"$SCRIPT_DIR/member-list-baseline.js"; then
		:
	else
		WARMUP_EXIT=$?
		unset PERF_ACCESS_TOKEN
		exit "$WARMUP_EXIT"
	fi
	if ! node "$SCRIPT_DIR/validate-k6-summary.mjs" \
		"$WARMUP_SUMMARY" "$SCENARIO" "$CASE" warmup "$WARMUP_ADOPTION"; then
		unset PERF_ACCESS_TOKEN
		printf 'Warmup summary is not adoptable: %s/%s\n' "$SCENARIO" "$CASE" >&2
		exit 1
	fi
	RUNTIME_BEFORE="$REPORT_DIR/${EVIDENCE_CASE}-runtime-identity-before.json"
	capture_runtime_identity "$RUNTIME_BEFORE"
	if ! node "$SCRIPT_DIR/validate-runtime-continuity.mjs" \
		"$INITIAL_RUNTIME_IDENTITY" "$RUNTIME_BEFORE" \
		"$REPORT_DIR/${EVIDENCE_CASE}-runtime-continuity-before-adoption.json"; then
		unset PERF_ACCESS_TOKEN
		printf 'Runtime identity changed before measured case: %s/%s\n' "$SCENARIO" "$CASE" >&2
		exit 1
	fi

	POSTGRES_CONTAINER_ID="$POSTGRES_CONTAINER_ID" \
	POSTGRES_USER="$POSTGRES_USER" \
	POSTGRES_DB="$POSTGRES_DB" \
	POSTGRES_PASSWORD="$POSTGRES_PASSWORD" \
		"$SCRIPT_DIR/collect-db-evidence.sh" "$EVIDENCE_CASE" before
	capture_resource_snapshot "$SCENARIO" "$CASE" before
	record_case_window "$SCENARIO" "$CASE" measured-start pending
	MEASURED_SUMMARY="$REPORT_DIR/measured-${SCENARIO}-${CASE}.json"
	MEASURED_ADOPTION="$REPORT_DIR/measured-${SCENARIO}-${CASE}-adoption.json"
	if ! printf '%s' "$PERF_ACCESS_TOKEN" \
		| WARMUP_DURATION="$WARMUP_DURATION" \
			MEASURED_DURATION="$MEASURED_DURATION" \
			TOKEN_SAFETY_MARGIN_SECONDS="$TOKEN_SAFETY_MARGIN_SECONDS" \
			TOKEN_LIFETIME_PHASE=measured \
				node "$SCRIPT_DIR/validate-token-lifetime.mjs"; then
		unset PERF_ACCESS_TOKEN
		printf 'Access token remaining lifetime is insufficient for measured case: %s/%s\n' "$SCENARIO" "$CASE" >&2
		exit 1
	fi
	if PERF_ACCESS_TOKEN="$PERF_ACCESS_TOKEN" \
		MAX_FAILURE_RATE="$MAX_FAILURE_RATE" \
		VUS="$MEASURED_VUS" \
		DURATION="$MEASURED_DURATION" \
		"$K6_BIN" run \
		--summary-export "$MEASURED_SUMMARY" \
		"$SCRIPT_DIR/member-list-baseline.js"; then
		MEASURED_STATUS=passed
		MEASURED_EXIT=0
	else
		MEASURED_EXIT=$?
		MEASURED_STATUS=failed
	fi
	if [[ "$MEASURED_EXIT" -eq 0 ]] \
		&& ! node "$SCRIPT_DIR/validate-k6-summary.mjs" \
			"$MEASURED_SUMMARY" "$SCENARIO" "$CASE" measured "$MEASURED_ADOPTION"; then
		MEASURED_EXIT=1
		MEASURED_STATUS=failed
	fi
	record_case_window "$SCENARIO" "$CASE" measured-end "$MEASURED_STATUS"
	POSTGRES_CONTAINER_ID="$POSTGRES_CONTAINER_ID" \
	POSTGRES_USER="$POSTGRES_USER" \
	POSTGRES_DB="$POSTGRES_DB" \
	POSTGRES_PASSWORD="$POSTGRES_PASSWORD" \
		"$SCRIPT_DIR/collect-db-evidence.sh" "$EVIDENCE_CASE" after
	RUNTIME_AFTER="$REPORT_DIR/${EVIDENCE_CASE}-runtime-identity-after.json"
	capture_runtime_identity "$RUNTIME_AFTER"
	if ! node "$SCRIPT_DIR/validate-runtime-continuity.mjs" \
		"$INITIAL_RUNTIME_IDENTITY" "$RUNTIME_AFTER" \
		"$REPORT_DIR/${EVIDENCE_CASE}-runtime-continuity-after-adoption.json"; then
		unset PERF_ACCESS_TOKEN
		printf 'Runtime identity changed during measured case: %s/%s\n' "$SCENARIO" "$CASE" >&2
		exit 1
	fi
	if [[ "$MEASURED_EXIT" -ne 0 ]]; then
		unset PERF_ACCESS_TOKEN
		printf 'Measured case failed and is not adoptable: %s/%s\n' "$SCENARIO" "$CASE" >&2
		exit "$MEASURED_EXIT"
	fi
	if node "$SCRIPT_DIR/derive-query-delta.mjs" \
		"$REPORT_DIR/db-evidence/$EVIDENCE_CASE/before-query-evidence.ndjson" \
		"$REPORT_DIR/db-evidence/$EVIDENCE_CASE/after-query-evidence.ndjson" \
		"$REPORT_DIR/db-evidence/$EVIDENCE_CASE/before-query-availability.json" \
		"$REPORT_DIR/db-evidence/$EVIDENCE_CASE/after-query-availability.json" \
		"$REPORT_DIR/db-evidence/$EVIDENCE_CASE/query-delta.json"; then
		:
	else
		QUERY_DELTA_EXIT=$?
		unset PERF_ACCESS_TOKEN
		printf 'Query evidence lost integrity and is not adoptable: %s/%s\n' "$SCENARIO" "$CASE" >&2
		exit "$QUERY_DELTA_EXIT"
	fi
	if ! node "$SCRIPT_DIR/validate-table-counters.mjs" \
		"$REPORT_DIR/db-evidence/$EVIDENCE_CASE/before-table-counters.csv" \
		"$REPORT_DIR/db-evidence/$EVIDENCE_CASE/after-table-counters.csv" \
		"$REPORT_DIR/db-evidence/$EVIDENCE_CASE/table-counter-adoption.json"; then
		unset PERF_ACCESS_TOKEN
		printf 'Table counters are not adoptable: %s/%s\n' "$SCENARIO" "$CASE" >&2
		exit 1
	fi
	if ! node "$SCRIPT_DIR/validate-db-integrity.mjs" \
		"$REPORT_DIR/db-evidence/$EVIDENCE_CASE/before-runtime-integrity.json" \
		"$REPORT_DIR/db-evidence/$EVIDENCE_CASE/after-runtime-integrity.json" \
		"$MEASURED_ADOPTION" \
		"$REPORT_DIR/db-evidence/$EVIDENCE_CASE/db-integrity-adoption.json" \
		"$EVIDENCE_CASE"; then
		unset PERF_ACCESS_TOKEN
		printf 'DB activity/analyze/planner evidence is not adoptable: %s/%s\n' "$SCENARIO" "$CASE" >&2
		exit 1
	fi
	capture_resource_snapshot "$SCENARIO" "$CASE" after
	if ! node "$SCRIPT_DIR/validate-resource-snapshots.mjs" \
		"$INITIAL_RUNTIME_IDENTITY" \
		"$REPORT_DIR/${SCENARIO}-${CASE}-docker-stats.ndjson" \
		"$REPORT_DIR/case-windows.ndjson" \
		"$SCENARIO" \
		"$CASE" \
		"$REPORT_DIR/${EVIDENCE_CASE}-resource-adoption.json"; then
		unset PERF_ACCESS_TOKEN
		printf 'CPU/RAM boundary evidence is not adoptable: %s/%s\n' "$SCENARIO" "$CASE" >&2
		exit 1
	fi
	unset PERF_ACCESS_TOKEN
done

FINAL_RUNTIME_IDENTITY="$REPORT_DIR/final-runtime-identity.json"
capture_runtime_identity "$FINAL_RUNTIME_IDENTITY"
node "$SCRIPT_DIR/validate-runtime-continuity.mjs" \
	"$INITIAL_RUNTIME_IDENTITY" \
	"$FINAL_RUNTIME_IDENTITY" \
	"$REPORT_DIR/final-runtime-continuity-adoption.json"

docker logs --since "$RUN_STARTED_AT" "$APP_CONTAINER_ID" 2>&1 \
	| REDACT_ADMIN_EMAIL="$REDACT_ADMIN_EMAIL" REDACT_ADMIN_PASSWORD="$REDACT_ADMIN_PASSWORD" \
		node -e 'let text=""; process.stdin.setEncoding("utf8"); process.stdin.on("data", chunk => text += chunk); process.stdin.on("end", () => process.stdout.write(text.replaceAll(process.env.REDACT_ADMIN_EMAIL, "[REDACTED]").replaceAll(process.env.REDACT_ADMIN_PASSWORD, "[REDACTED]")));' \
	> "$REPORT_DIR/application-logs.txt"
printf 'Issue #195 reports (conditional-not-adoptable): %s\n' "$REPORT_DIR"
printf '%s\n' 'Measurement classification is conditional-not-adoptable until user-approved exclusive provenance exists.' >&2
node "$SCRIPT_DIR/classify-measurement.mjs" "$REPORT_DIR/measurement-classification.json"
