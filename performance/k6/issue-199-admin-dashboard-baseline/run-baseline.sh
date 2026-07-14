#!/usr/bin/env bash
set -euo pipefail

ISSUE_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
INPUT_MANIFEST="$(node -e 'const path = require("node:path"); console.log(path.resolve(process.argv[1]));' "${INPUT_MANIFEST:?Set INPUT_MANIFEST to an approved Issue #199 input manifest.}")"
BASE_URL="${BASE_URL:?Set the approved BASE_URL bound to APP_CONTAINER.}"
DATASET_MODES="${DATASET_MODES:?Set the user-approved dataset modes explicitly.}"
WARMUP_VUS="${WARMUP_VUS:?Set the user-approved warmup VUS.}"
WARMUP_DURATION="${WARMUP_DURATION:?Set the user-approved warmup duration.}"
MEASURED_VUS="${MEASURED_VUS:?Set the user-approved measured VUS.}"
MEASURED_DURATION="${MEASURED_DURATION:?Set the user-approved measured duration.}"
TOKEN_EXPIRY_SAFETY_SECONDS="${TOKEN_EXPIRY_SAFETY_SECONDS:?Set the approved measured-token expiry safety seconds.}"
EXTERNAL_ACTIVITY="${EXTERNAL_ACTIVITY:?Describe frontend QA, other load, deploy, maintenance, and manual DB activity during the run.}"
PERF_ADMIN_EMAIL="${PERF_ADMIN_EMAIL:?Set the runtime-only campus manager email.}"
PERF_ADMIN_PASSWORD="${PERF_ADMIN_PASSWORD:?Set the runtime-only campus manager password.}"
PERF_DB_USER="${PERF_DB_USER:?Set the runtime-only PostgreSQL user.}"
PERF_DB_PASSWORD="${PERF_DB_PASSWORD:?Set the runtime-only PostgreSQL password.}"
PERF_DB_NAME="${PERF_DB_NAME:?Set the runtime-only PostgreSQL database.}"
export -n PERF_ADMIN_EMAIL PERF_ADMIN_PASSWORD PERF_DB_USER PERF_DB_PASSWORD PERF_DB_NAME
APP_CONTAINER="${APP_CONTAINER:?Set the exact app container serving BASE_URL.}"
POSTGRES_CONTAINER="${POSTGRES_CONTAINER:-faithlog-latest-postgres}"
REDIS_CONTAINER="${REDIS_CONTAINER:-faithlog-latest-redis}"
CONTAINER_ALIAS="${CONTAINER_ALIAS:-faithlog-latest}"
LOCK_DIR=""
REPORT_ROOT="$ISSUE_ROOT/reports"
RUNTIME_ACCESS_TOKEN=""
APP_PROJECT=""
APP_SERVICE=""
POSTGRES_PROJECT=""
POSTGRES_SERVICE=""
REDIS_PROJECT=""
REDIS_SERVICE=""
COMPOSE_PROJECT=""
ENDPOINT_IDENTITY=""
INITIAL_RUNTIME_IDENTITY_JSON=""
HAS_CONDITIONAL_DB_WINDOW=0

for command_name in node k6 docker date; do
	command -v "$command_name" >/dev/null || {
		echo "Required command is missing: $command_name" >&2
		exit 1
	}
done

node "$ISSUE_ROOT/validate-run-input.mjs" "$INPUT_MANIFEST" "$DATASET_MODES" >/dev/null
if [[ "$EXTERNAL_ACTIVITY" != "none" ]]; then
	echo "EXTERNAL_ACTIVITY must be exactly 'none' for Issue #199 evidence collection." >&2
	exit 1
fi

cleanup() {
	RUNTIME_ACCESS_TOKEN=""
	unset RUNTIME_ACCESS_TOKEN PERF_ACCESS_TOKEN PERF_ADMIN_EMAIL PERF_ADMIN_PASSWORD
	if [[ -n "$LOCK_DIR" ]]; then
		rmdir "$LOCK_DIR"
	fi
}

case "$BASE_URL" in
	http://127.0.0.1:*|http://\[::1\]:*) ;;
	*)
		echo "Issue #199 runner is restricted to the local shared stack." >&2
		exit 1
		;;
esac

manifest_root_value() {
	local component="$1"
	local field="$2"
	node -e '
		const fs = require("node:fs");
		const manifest = JSON.parse(fs.readFileSync(process.argv[1], "utf8"));
		const value = manifest.runtimeTarget?.[process.argv[2]]?.[process.argv[3]];
		if (value === undefined || value === null || value === "") throw new Error(`Missing runtime target: ${process.argv[2]}.${process.argv[3]}`);
		process.stdout.write(String(value));
	' "$INPUT_MANIFEST" "$component" "$field"
}

EXPECTED_APP_SERVICE="$(manifest_root_value app service)"
EXPECTED_APP_CONTAINER_PORT="$(manifest_root_value app containerPort)"
EXPECTED_POSTGRES_SERVICE="$(manifest_root_value postgres service)"
EXPECTED_REDIS_SERVICE="$(manifest_root_value redis service)"

APP_PROJECT="$(docker inspect --format '{{ index .Config.Labels "com.docker.compose.project" }}' "$APP_CONTAINER")"
APP_SERVICE="$(docker inspect --format '{{ index .Config.Labels "com.docker.compose.service" }}' "$APP_CONTAINER")"
POSTGRES_PROJECT="$(docker inspect --format '{{ index .Config.Labels "com.docker.compose.project" }}' "$POSTGRES_CONTAINER")"
POSTGRES_SERVICE="$(docker inspect --format '{{ index .Config.Labels "com.docker.compose.service" }}' "$POSTGRES_CONTAINER")"
REDIS_PROJECT="$(docker inspect --format '{{ index .Config.Labels "com.docker.compose.project" }}' "$REDIS_CONTAINER")"
REDIS_SERVICE="$(docker inspect --format '{{ index .Config.Labels "com.docker.compose.service" }}' "$REDIS_CONTAINER")"
APP_PUBLISHED_PORTS="$(docker inspect --format '{{json .NetworkSettings.Ports}}' "$APP_CONTAINER")"

for label_value in \
	"$APP_PROJECT" "$APP_SERVICE" \
	"$POSTGRES_PROJECT" "$POSTGRES_SERVICE" \
	"$REDIS_PROJECT" "$REDIS_SERVICE"; do
	if [[ -z "$label_value" ]]; then
		echo "Required Compose project/service label is empty." >&2
		exit 1
	fi
done

if [[ "$APP_PROJECT" != "$POSTGRES_PROJECT" || "$APP_PROJECT" != "$REDIS_PROJECT" ]]; then
	echo "Compose project labels do not match: app=$APP_PROJECT postgres=$POSTGRES_PROJECT redis=$REDIS_PROJECT" >&2
	exit 1
fi

if [[ "$APP_SERVICE" != "$EXPECTED_APP_SERVICE" \
	|| "$POSTGRES_SERVICE" != "$EXPECTED_POSTGRES_SERVICE" \
	|| "$REDIS_SERVICE" != "$EXPECTED_REDIS_SERVICE" ]]; then
	echo "Compose service labels do not match approved manifest: app=$APP_SERVICE postgres=$POSTGRES_SERVICE redis=$REDIS_SERVICE" >&2
	exit 1
fi

ENDPOINT_IDENTITY="$(
	node "$ISSUE_ROOT/validate-runtime-target.mjs" \
		"$BASE_URL" "$EXPECTED_APP_CONTAINER_PORT" "$APP_PUBLISHED_PORTS"
)"

COMPOSE_PROJECT="$APP_PROJECT"
if [[ ! "$COMPOSE_PROJECT" =~ ^[A-Za-z0-9._-]+$ ]]; then
	echo "Unsafe Compose project label for shared runner lock: $COMPOSE_PROJECT" >&2
	exit 1
fi
LOCK_DIR="/tmp/faithlog-performance-${COMPOSE_PROJECT}.lock"
if ! mkdir "$LOCK_DIR"; then
	echo "Another performance, frontend QA, or load run may be active: $LOCK_DIR" >&2
	exit 1
fi
trap cleanup EXIT

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

	DATASET_ID="$dataset_id" \
	FIXTURE_RUN_ID="$fixture_run_id" \
	DATASET_MODE="$mode" \
	CONTAINER_ALIAS="$CONTAINER_ALIAS" \
	APP_CONTAINER="$APP_CONTAINER" \
	APP_PROJECT="$APP_PROJECT" \
	APP_SERVICE="$APP_SERVICE" \
	POSTGRES_CONTAINER="$POSTGRES_CONTAINER" \
	POSTGRES_PROJECT="$POSTGRES_PROJECT" \
	POSTGRES_SERVICE="$POSTGRES_SERVICE" \
	REDIS_CONTAINER="$REDIS_CONTAINER" \
	REDIS_PROJECT="$REDIS_PROJECT" \
	REDIS_SERVICE="$REDIS_SERVICE" \
	ENDPOINT_IDENTITY="$ENDPOINT_IDENTITY" \
	RUNNER_LOCK="$LOCK_DIR" \
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
			endpointIdentity: JSON.parse(process.env.ENDPOINT_IDENTITY),
			runnerLock: process.env.RUNNER_LOCK,
			externalActivity: process.env.EXTERNAL_ACTIVITY,
			externalActivityCoverage: "boundary-snapshot-only",
			cacheResetPerformed: false,
			coldInterpretation: "First observation only; shared Docker and database caches are not reset.",
		};
		process.stdout.write(`${JSON.stringify(report, null, 2)}\n`);
	' > "$output_dir/environment.json"
}

collect_db_evidence() {
	local campus_id="$1"
	local week_start_date="$2"
	local sql_file="$3"
	local output_file="$4"
	PGPASSWORD="$PERF_DB_PASSWORD" docker exec -i \
		-e PGPASSWORD \
		-e PGAPPNAME=faithlog-issue199-observer \
		"$POSTGRES_CONTAINER" \
		psql -X -v ON_ERROR_STOP=1 \
		-U "$PERF_DB_USER" \
		-d "$PERF_DB_NAME" \
		-v campus_id="$campus_id" \
		-v week_start_date="$week_start_date" \
		-f - < "$sql_file" > "$output_file"
}

collect_db_machine_evidence() {
	local campus_id="$1"
	local week_start_date="$2"
	local sql_file="$3"
	local output_file="$4"
	PGPASSWORD="$PERF_DB_PASSWORD" docker exec -i \
		-e PGPASSWORD \
		-e PGAPPNAME=faithlog-issue199-observer \
		"$POSTGRES_CONTAINER" \
		psql -X -qAt -v ON_ERROR_STOP=1 \
		-U "$PERF_DB_USER" \
		-d "$PERF_DB_NAME" \
		-v campus_id="$campus_id" \
		-v week_start_date="$week_start_date" \
		-f - < "$sql_file" > "$output_file"
}

collect_db_context() {
	collect_db_evidence "$1" "$2" "$ISSUE_ROOT/collect-db-evidence.sql" "$3"
}

collect_db_correctness() {
	collect_db_machine_evidence "$1" "$2" "$ISSUE_ROOT/collect-correctness-evidence.sql" "$3"
}

collect_db_counters() {
	collect_db_machine_evidence "$1" "$2" "$ISSUE_ROOT/collect-db-counters.sql" "$3"
}

inspect_container_identity() {
	docker inspect --format '{"evidence":"issue199-runtime-identity","id":{{json .Id}},"imageId":{{json .Image}},"imageRef":{{json .Config.Image}},"startedAt":{{json .State.StartedAt}},"composeProject":{{json (index .Config.Labels "com.docker.compose.project")}},"composeService":{{json (index .Config.Labels "com.docker.compose.service")}},"composeConfigHash":{{json (index .Config.Labels "com.docker.compose.config-hash")}},"publishedPorts":{{json .NetworkSettings.Ports}}}' "$1"
}

collect_postgres_runtime_identity() {
	PGPASSWORD="$PERF_DB_PASSWORD" docker exec -i \
		-e PGPASSWORD \
		-e PGAPPNAME=faithlog-issue199-observer \
		"$POSTGRES_CONTAINER" \
		psql -X -qAt -v ON_ERROR_STOP=1 \
		-U "$PERF_DB_USER" \
		-d "$PERF_DB_NAME" \
		-f - < "$ISSUE_ROOT/collect-runtime-identity.sql"
}

capture_runtime_identity_json() {
	local app_identity
	local postgres_container_identity
	local redis_identity
	local postgres_server_identity
	app_identity="$(inspect_container_identity "$APP_CONTAINER")"
	postgres_container_identity="$(inspect_container_identity "$POSTGRES_CONTAINER")"
	redis_identity="$(inspect_container_identity "$REDIS_CONTAINER")"
	postgres_server_identity="$(collect_postgres_runtime_identity)"
	APP_IDENTITY="$app_identity" \
	POSTGRES_CONTAINER_IDENTITY="$postgres_container_identity" \
	REDIS_IDENTITY="$redis_identity" \
	POSTGRES_SERVER_IDENTITY="$postgres_server_identity" \
	node -e '
		const report = {
			capturedAt: new Date().toISOString(),
			containers: {
				app: JSON.parse(process.env.APP_IDENTITY),
				postgres: JSON.parse(process.env.POSTGRES_CONTAINER_IDENTITY),
				redis: JSON.parse(process.env.REDIS_IDENTITY),
			},
			postgres: JSON.parse(process.env.POSTGRES_SERVER_IDENTITY),
		};
		for (const container of Object.values(report.containers)) delete container.evidence;
		process.stdout.write(`${JSON.stringify(report, null, 2)}\n`);
	'
}

validate_runtime_continuity() {
	node "$ISSUE_ROOT/validate-runtime-continuity.mjs" "$1" "$2" "$3" "$4"
}

validate_runtime_identity_target_json() {
	local identity_json="$1"
	local published_ports
	published_ports="$(
		RUNTIME_IDENTITY_JSON="$identity_json" \
		EXPECTED_PROJECT="$COMPOSE_PROJECT" \
		EXPECTED_APP_SERVICE="$EXPECTED_APP_SERVICE" \
		EXPECTED_POSTGRES_SERVICE="$EXPECTED_POSTGRES_SERVICE" \
		EXPECTED_REDIS_SERVICE="$EXPECTED_REDIS_SERVICE" \
		node -e '
			const assert = require("node:assert/strict");
			const identity = JSON.parse(process.env.RUNTIME_IDENTITY_JSON);
			for (const [component, expectedService] of [
				["app", process.env.EXPECTED_APP_SERVICE],
				["postgres", process.env.EXPECTED_POSTGRES_SERVICE],
				["redis", process.env.EXPECTED_REDIS_SERVICE],
			]) {
				assert.equal(identity.containers?.[component]?.composeProject, process.env.EXPECTED_PROJECT,
					`${component} runtime identity Compose project changed after lock acquisition.`);
				assert.equal(identity.containers?.[component]?.composeService, expectedService,
					`${component} runtime identity Compose service changed after lock acquisition.`);
			}
			process.stdout.write(JSON.stringify(identity.containers.app.publishedPorts));
		'
	)"
	node "$ISSUE_ROOT/validate-runtime-target.mjs" \
		"$BASE_URL" "$EXPECTED_APP_CONTAINER_PORT" "$published_ports"
}

validate_db_window() {
	local before_file="$1"
	local after_file="$2"
	local output_file="$3"
	node "$ISSUE_ROOT/validate-db-window.mjs" \
		"$before_file" "$after_file" "$EXTERNAL_ACTIVITY" "$output_file"
}

validate_db_window_or_defer_adoption() {
	local before_file="$1"
	local after_file="$2"
	local output_file="$3"
	if validate_db_window "$before_file" "$after_file" "$output_file"; then
		return
	fi
	if node -e '
		const fs = require("node:fs");
		const gate = JSON.parse(fs.readFileSync(process.argv[1], "utf8"));
		if (gate.status !== "conditional-not-adoptable" || gate.adoptable !== false) process.exit(1);
	' "$output_file"; then
		HAS_CONDITIONAL_DB_WINDOW=1
		return
	fi
	return 1
}

prepare_runtime_token() {
	local mode="$1"
	local purpose="$2"
	RUNTIME_ACCESS_TOKEN="$(
		INPUT_MANIFEST="$INPUT_MANIFEST" \
		DATASET_MODES="$mode" \
		TOKEN_PURPOSE="$purpose" \
		BASE_URL="$BASE_URL" \
		PERF_ADMIN_EMAIL="$PERF_ADMIN_EMAIL" \
		PERF_ADMIN_PASSWORD="$PERF_ADMIN_PASSWORD" \
		node "$ISSUE_ROOT/prepare-runtime-token.mjs"
	)"
	if [[ -z "$RUNTIME_ACCESS_TOKEN" ]]; then
		echo "Runtime access token preparation returned an empty value for mode/purpose: $mode/$purpose" >&2
		exit 1
	fi
}

clear_runtime_token() {
	RUNTIME_ACCESS_TOKEN=""
	unset PERF_ACCESS_TOKEN
}

validate_db_correctness() {
	local mode="$1"
	local evidence_file="$2"
	node "$ISSUE_ROOT/validate-db-correctness.mjs" "$INPUT_MANIFEST" "$mode" "$evidence_file" >/dev/null
}

verify_api_correctness() {
	local mode="$1"
	local output_file="$2"
	INPUT_MANIFEST="$INPUT_MANIFEST" \
	BASE_URL="$BASE_URL" \
	PERF_ACCESS_TOKEN="$RUNTIME_ACCESS_TOKEN" \
	DATASET_MODE="$mode" \
	node "$ISSUE_ROOT/verify-summary.mjs" > "$output_file"
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
	PERF_ACCESS_TOKEN="$RUNTIME_ACCESS_TOKEN" \
	VUS="$vus" \
	DURATION="$duration" \
	k6 run \
		--summary-export "$output_dir/summary.json" \
		"$ISSUE_ROOT/admin-dashboard-baseline.js" > "$output_dir/k6.log" 2>&1
}

INITIAL_RUNTIME_IDENTITY_JSON="$(capture_runtime_identity_json)"
ENDPOINT_IDENTITY="$(validate_runtime_identity_target_json "$INITIAL_RUNTIME_IDENTITY_JSON")"

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
	mkdir -p "$REPORT_ROOT/$dataset_id/$fixture_run_id"
	if ! mkdir "$mode_report_dir"; then
		echo "Report directory already exists; stale evidence mixing is blocked: $mode_report_dir" >&2
		exit 1
	fi
	mkdir "$mode_report_dir/warmup" "$mode_report_dir/measured"

	record_environment "$mode_report_dir" "$dataset_id" "$fixture_run_id" "$mode"
	prepare_runtime_token "$mode" warmup
	collect_db_correctness "$campus_id" "$week_start_date" "$mode_report_dir/db-correctness-before.json"
	validate_db_correctness "$mode" "$mode_report_dir/db-correctness-before.json"
	verify_api_correctness "$mode" "$mode_report_dir/api-correctness-before.json"
	collect_db_context "$campus_id" "$week_start_date" "$mode_report_dir/db-context-before.txt"

	PHASE=warmup run_k6_phase warmup "$mode" "$WARMUP_VUS" "$WARMUP_DURATION" "$mode_report_dir/warmup"
	test -f "$mode_report_dir/warmup/summary.json"
	node "$ISSUE_ROOT/validate-k6-summary.mjs" "$mode_report_dir/warmup/summary.json" \
		> "$mode_report_dir/warmup/adoption-gate.json"
	collect_db_correctness "$campus_id" "$week_start_date" "$mode_report_dir/db-correctness-pre-measured.json"
	validate_db_correctness "$mode" "$mode_report_dir/db-correctness-pre-measured.json"
	verify_api_correctness "$mode" "$mode_report_dir/api-correctness-pre-measured.json"
	collect_db_context "$campus_id" "$week_start_date" "$mode_report_dir/db-context-pre-measured.txt"
	clear_runtime_token
	prepare_runtime_token "$mode" measured
	PERF_ACCESS_TOKEN="$RUNTIME_ACCESS_TOKEN" \
		node "$ISSUE_ROOT/validate-token-lifetime.mjs" \
		"$MEASURED_DURATION" "$TOKEN_EXPIRY_SAFETY_SECONDS" "$(date +%s)" >/dev/null
	printf '%s\n' "$INITIAL_RUNTIME_IDENTITY_JSON" > "$mode_report_dir/runtime-identity-initial.json"
	capture_runtime_identity_json > "$mode_report_dir/measured/runtime-identity-before.json"
	validate_runtime_continuity \
		"$mode_report_dir/runtime-identity-initial.json" \
		"$mode_report_dir/measured/runtime-identity-before.json" \
		"$mode_report_dir/measured/runtime-identity-before.json" \
		"$mode_report_dir/measured/runtime-continuity-pre-gate.json"
	collect_db_counters "$campus_id" "$week_start_date" "$mode_report_dir/measured/db-counters-before.json"
	docker stats --no-stream --format '{{json .}}' \
		"$APP_CONTAINER" "$POSTGRES_CONTAINER" "$REDIS_CONTAINER" > "$mode_report_dir/measured/docker-stats-before.jsonl"

	PHASE=measured run_k6_phase measured "$mode" "$MEASURED_VUS" "$MEASURED_DURATION" "$mode_report_dir/measured"
	test -f "$mode_report_dir/measured/summary.json"
	node "$ISSUE_ROOT/validate-k6-summary.mjs" "$mode_report_dir/measured/summary.json" \
		> "$mode_report_dir/measured/adoption-gate.json"
	docker stats --no-stream --format '{{json .}}' \
		"$APP_CONTAINER" "$POSTGRES_CONTAINER" "$REDIS_CONTAINER" > "$mode_report_dir/measured/docker-stats-after.jsonl"
	collect_db_counters "$campus_id" "$week_start_date" "$mode_report_dir/measured/db-counters-after.json"
	capture_runtime_identity_json > "$mode_report_dir/measured/runtime-identity-after.json"
	collect_db_context "$campus_id" "$week_start_date" "$mode_report_dir/db-context-after.txt"
	collect_db_correctness "$campus_id" "$week_start_date" "$mode_report_dir/db-correctness-after.json"
	validate_db_correctness "$mode" "$mode_report_dir/db-correctness-after.json"
	verify_api_correctness "$mode" "$mode_report_dir/api-correctness-after.json"
	clear_runtime_token
	validate_runtime_continuity \
		"$mode_report_dir/runtime-identity-initial.json" \
		"$mode_report_dir/measured/runtime-identity-before.json" \
		"$mode_report_dir/measured/runtime-identity-after.json" \
		"$mode_report_dir/measured/runtime-continuity-gate.json"
	validate_db_window_or_defer_adoption \
		"$mode_report_dir/measured/db-counters-before.json" \
		"$mode_report_dir/measured/db-counters-after.json" \
		"$mode_report_dir/measured/db-window-adoption-gate.json"
done

if (( HAS_CONDITIONAL_DB_WINDOW )); then
	echo "Baseline is conditional-not-adoptable: external activity coverage is boundary-snapshot-only." >&2
	exit 1
fi
