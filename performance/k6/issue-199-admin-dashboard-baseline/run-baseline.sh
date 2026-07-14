#!/usr/bin/env bash
set -euo pipefail

ISSUE_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
INPUT_MANIFEST="$(node -e 'const path = require("node:path"); console.log(path.resolve(process.argv[1]));' "${INPUT_MANIFEST:?Set INPUT_MANIFEST to an approved Issue #199 input manifest.}")"
BASE_URL="${BASE_URL:-http://127.0.0.1:28080}"
DATASET_MODES="${DATASET_MODES:-empty,small,thousand}"
WARMUP_VUS="${WARMUP_VUS:?Set the user-approved warmup VUS.}"
WARMUP_DURATION="${WARMUP_DURATION:?Set the user-approved warmup duration.}"
MEASURED_VUS="${MEASURED_VUS:?Set the user-approved measured VUS.}"
MEASURED_DURATION="${MEASURED_DURATION:?Set the user-approved measured duration.}"
EXTERNAL_ACTIVITY="${EXTERNAL_ACTIVITY:?Describe frontend QA, other load, deploy, maintenance, and manual DB activity during the run.}"
PERF_ADMIN_EMAIL="${PERF_ADMIN_EMAIL:?Set the runtime-only campus manager email.}"
PERF_ADMIN_PASSWORD="${PERF_ADMIN_PASSWORD:?Set the runtime-only campus manager password.}"
PERF_DB_USER="${PERF_DB_USER:?Set the runtime-only PostgreSQL user.}"
PERF_DB_PASSWORD="${PERF_DB_PASSWORD:?Set the runtime-only PostgreSQL password.}"
PERF_DB_NAME="${PERF_DB_NAME:?Set the runtime-only PostgreSQL database.}"
APP_CONTAINER="${APP_CONTAINER:-faithlog-latest-app}"
POSTGRES_CONTAINER="${POSTGRES_CONTAINER:-faithlog-latest-postgres}"
REDIS_CONTAINER="${REDIS_CONTAINER:-faithlog-latest-redis}"
CONTAINER_ALIAS="${CONTAINER_ALIAS:-faithlog-latest}"
LOCK_DIR="/tmp/faithlog-performance-runner.lock"
REPORT_ROOT="$ISSUE_ROOT/reports"

for command_name in node k6 docker; do
	command -v "$command_name" >/dev/null || {
		echo "Required command is missing: $command_name" >&2
		exit 1
	}
done

if ! mkdir "$LOCK_DIR"; then
	echo "Another performance, frontend QA, or load run may be active: $LOCK_DIR" >&2
	exit 1
fi
trap 'rmdir "$LOCK_DIR"' EXIT

case "$BASE_URL" in
	http://127.0.0.1:*|http://localhost:*|http://host.docker.internal:*) ;;
	*)
		echo "Issue #199 runner is restricted to the local shared stack." >&2
		exit 1
		;;
esac

for container in "$APP_CONTAINER" "$POSTGRES_CONTAINER" "$REDIS_CONTAINER"; do
	docker inspect "$container" >/dev/null
done

manifest_value() {
	local mode="$1"
	local field="$2"
	node -e '
		const fs = require("node:fs");
		const manifest = JSON.parse(fs.readFileSync(process.argv[1], "utf8"));
		const mode = manifest.modes?.[process.argv[2]];
		if (!mode) throw new Error(`Missing dataset mode: ${process.argv[2]}`);
		const value = process.argv[3] === "datasetId" ? manifest.datasetId : mode[process.argv[3]];
		if (value === undefined || value === null || value === "") throw new Error(`Missing manifest value: ${process.argv[3]}`);
		process.stdout.write(String(value));
	' "$INPUT_MANIFEST" "$mode" "$field"
}

safe_segment() {
	local value="$1"
	if [[ ! "$value" =~ ^[A-Za-z0-9._-]+$ ]]; then
		echo "Unsafe report path segment: $value" >&2
		exit 1
	fi
}

record_environment() {
	local output_dir="$1"
	local dataset_id="$2"
	local fixture_run_id="$3"
	local mode="$4"
	local app_project app_service postgres_project postgres_service redis_project redis_service
	app_project="$(docker inspect --format '{{ index .Config.Labels "com.docker.compose.project" }}' "$APP_CONTAINER")"
	app_service="$(docker inspect --format '{{ index .Config.Labels "com.docker.compose.service" }}' "$APP_CONTAINER")"
	postgres_project="$(docker inspect --format '{{ index .Config.Labels "com.docker.compose.project" }}' "$POSTGRES_CONTAINER")"
	postgres_service="$(docker inspect --format '{{ index .Config.Labels "com.docker.compose.service" }}' "$POSTGRES_CONTAINER")"
	redis_project="$(docker inspect --format '{{ index .Config.Labels "com.docker.compose.project" }}' "$REDIS_CONTAINER")"
	redis_service="$(docker inspect --format '{{ index .Config.Labels "com.docker.compose.service" }}' "$REDIS_CONTAINER")"

	DATASET_ID="$dataset_id" \
	FIXTURE_RUN_ID="$fixture_run_id" \
	DATASET_MODE="$mode" \
	CONTAINER_ALIAS="$CONTAINER_ALIAS" \
	APP_CONTAINER="$APP_CONTAINER" \
	APP_PROJECT="$app_project" \
	APP_SERVICE="$app_service" \
	POSTGRES_CONTAINER="$POSTGRES_CONTAINER" \
	POSTGRES_PROJECT="$postgres_project" \
	POSTGRES_SERVICE="$postgres_service" \
	REDIS_CONTAINER="$REDIS_CONTAINER" \
	REDIS_PROJECT="$redis_project" \
	REDIS_SERVICE="$redis_service" \
	EXTERNAL_ACTIVITY="$EXTERNAL_ACTIVITY" \
	node -e '
		const report = {
			capturedAt: new Date().toISOString(),
			datasetId: process.env.DATASET_ID,
			fixtureRunId: process.env.FIXTURE_RUN_ID,
			datasetMode: process.env.DATASET_MODE,
			containerAlias: process.env.CONTAINER_ALIAS,
			actualComposeLabels: {
				app: {container: process.env.APP_CONTAINER, project: process.env.APP_PROJECT, service: process.env.APP_SERVICE},
				postgres: {container: process.env.POSTGRES_CONTAINER, project: process.env.POSTGRES_PROJECT, service: process.env.POSTGRES_SERVICE},
				redis: {container: process.env.REDIS_CONTAINER, project: process.env.REDIS_PROJECT, service: process.env.REDIS_SERVICE},
			},
			externalActivity: process.env.EXTERNAL_ACTIVITY,
			cacheResetPerformed: false,
			coldInterpretation: "First observation only; shared Docker and database caches are not reset.",
		};
		process.stdout.write(`${JSON.stringify(report, null, 2)}\n`);
	' > "$output_dir/environment.json"
}

collect_db_evidence() {
	local campus_id="$1"
	local week_start_date="$2"
	local output_file="$3"
	docker exec -i \
		-e PGPASSWORD="$PERF_DB_PASSWORD" \
		"$POSTGRES_CONTAINER" \
		psql -X -v ON_ERROR_STOP=1 \
		-U "$PERF_DB_USER" \
		-d "$PERF_DB_NAME" \
		-v campus_id="$campus_id" \
		-v week_start_date="$week_start_date" \
		-f - < "$ISSUE_ROOT/collect-db-evidence.sql" > "$output_file"
}

run_k6_phase() {
	local phase="$1"
	local mode="$2"
	local vus="$3"
	local duration="$4"
	local output_dir="$5"
	mkdir -p "$output_dir"
	PHASE="$phase" \
	DATASET_MODE="$mode" \
	INPUT_MANIFEST="$INPUT_MANIFEST" \
	BASE_URL="$BASE_URL" \
	PERF_ADMIN_EMAIL="$PERF_ADMIN_EMAIL" \
	PERF_ADMIN_PASSWORD="$PERF_ADMIN_PASSWORD" \
	VUS="$vus" \
	DURATION="$duration" \
	k6 run \
		--summary-export "$output_dir/summary.json" \
		"$ISSUE_ROOT/admin-dashboard-baseline.js" > "$output_dir/k6.log" 2>&1
}

IFS=',' read -r -a modes <<< "$DATASET_MODES"
for raw_mode in "${modes[@]}"; do
	mode="${raw_mode//[[:space:]]/}"
	case "$mode" in
		empty|small|thousand) ;;
		*)
			echo "Unsupported dataset mode: $mode" >&2
			exit 1
			;;
	esac

	dataset_id="$(manifest_value "$mode" datasetId)"
	fixture_run_id="$(manifest_value "$mode" fixtureRunId)"
	campus_id="$(manifest_value "$mode" campusId)"
	week_start_date="$(manifest_value "$mode" weekStartDate)"
	safe_segment "$dataset_id"
	safe_segment "$fixture_run_id"
	mode_report_dir="$REPORT_ROOT/$dataset_id/$fixture_run_id/$mode"
	mkdir -p "$mode_report_dir/warmup" "$mode_report_dir/measured"

	record_environment "$mode_report_dir" "$dataset_id" "$fixture_run_id" "$mode"
	INPUT_MANIFEST="$INPUT_MANIFEST" \
	BASE_URL="$BASE_URL" \
	PERF_ADMIN_EMAIL="$PERF_ADMIN_EMAIL" \
	PERF_ADMIN_PASSWORD="$PERF_ADMIN_PASSWORD" \
	DATASET_MODE="$mode" \
	node "$ISSUE_ROOT/verify-summary.mjs" > "$mode_report_dir/correctness-before.json"
	collect_db_evidence "$campus_id" "$week_start_date" "$mode_report_dir/pre-warmup-db-evidence.txt"

	PHASE=warmup run_k6_phase warmup "$mode" "$WARMUP_VUS" "$WARMUP_DURATION" "$mode_report_dir/warmup"
	test -f "$mode_report_dir/warmup/summary.json"
	collect_db_evidence "$campus_id" "$week_start_date" "$mode_report_dir/pre-measured-db-evidence.txt"
	docker stats --no-stream --format '{{json .}}' \
		"$APP_CONTAINER" "$POSTGRES_CONTAINER" "$REDIS_CONTAINER" > "$mode_report_dir/measured/docker-stats-before.jsonl"

	PHASE=measured run_k6_phase measured "$mode" "$MEASURED_VUS" "$MEASURED_DURATION" "$mode_report_dir/measured"
	test -f "$mode_report_dir/measured/summary.json"
	docker stats --no-stream --format '{{json .}}' \
		"$APP_CONTAINER" "$POSTGRES_CONTAINER" "$REDIS_CONTAINER" > "$mode_report_dir/measured/docker-stats-after.jsonl"
	collect_db_evidence "$campus_id" "$week_start_date" "$mode_report_dir/post-measured-db-evidence.txt"
	INPUT_MANIFEST="$INPUT_MANIFEST" \
	BASE_URL="$BASE_URL" \
	PERF_ADMIN_EMAIL="$PERF_ADMIN_EMAIL" \
	PERF_ADMIN_PASSWORD="$PERF_ADMIN_PASSWORD" \
	DATASET_MODE="$mode" \
	node "$ISSUE_ROOT/verify-summary.mjs" > "$mode_report_dir/correctness-after.json"
done
