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
FIXTURE_EXPIRY_SAFETY_SECONDS="${FIXTURE_EXPIRY_SAFETY_SECONDS:?Set the approved fixture-namespace expiry safety seconds.}"
EXTERNAL_ACTIVITY="${EXTERNAL_ACTIVITY:?Describe frontend QA, other load, deploy, maintenance, and manual DB activity during the run.}"
PERF_ADMIN_EMAIL="${PERF_ADMIN_EMAIL:?Set the runtime-only campus manager email.}"
PERF_ADMIN_PASSWORD="${PERF_ADMIN_PASSWORD:?Set the runtime-only campus manager password.}"
PERF_DB_USER="${PERF_DB_USER:?Set the runtime-only PostgreSQL user.}"
PERF_DB_PASSWORD="${PERF_DB_PASSWORD:?Set the runtime-only PostgreSQL password.}"
PERF_DB_NAME="${PERF_DB_NAME:?Set the runtime-only PostgreSQL database.}"
export -n PERF_ADMIN_EMAIL PERF_ADMIN_PASSWORD PERF_DB_USER PERF_DB_PASSWORD PERF_DB_NAME
APP_CONTAINER="${APP_CONTAINER:?Set the exact app container serving BASE_URL.}"
POSTGRES_CONTAINER="${POSTGRES_CONTAINER:?Set the exact approved PostgreSQL container.}"
REDIS_CONTAINER="${REDIS_CONTAINER:?Set the exact approved Redis container.}"
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
INITIAL_SOURCE_IMAGE_PROVENANCE_JSON=""
PROVENANCE_TMP_DIR=""
PRE_POST_LOCK_GATE_JSON=""
HAS_CONDITIONAL_DB_WINDOW=0
HAS_CONDITIONAL_RESOURCE=0

for command_name in node k6 docker date; do
	command -v "$command_name" >/dev/null || {
		echo "Required command is missing: $command_name" >&2
		exit 1
	}
done

node "$ISSUE_ROOT/verify-current-develop-contract.mjs" "$ISSUE_ROOT/current-develop-contract.json" >/dev/null
node "$ISSUE_ROOT/validate-run-input.mjs" "$INPUT_MANIFEST" "$DATASET_MODES" "$(date +%s)" \
	"$WARMUP_VUS" "$WARMUP_DURATION" "$MEASURED_VUS" "$MEASURED_DURATION" "$FIXTURE_EXPIRY_SAFETY_SECONDS" >/dev/null
if [[ "$EXTERNAL_ACTIVITY" != "none" ]]; then
	echo "EXTERNAL_ACTIVITY must be exactly 'none' for Issue #199 evidence collection." >&2
	exit 1
fi

cleanup() {
	RUNTIME_ACCESS_TOKEN=""
	unset RUNTIME_ACCESS_TOKEN PERF_ACCESS_TOKEN PERF_ADMIN_EMAIL PERF_ADMIN_PASSWORD
	if [[ -n "$LOCK_DIR" ]]; then
		rm -f "$LOCK_DIR/pre-lock-target.json" "$LOCK_DIR/post-lock-target.json" "$LOCK_DIR/pre-post-lock-gate.json" \
			"$LOCK_DIR/post-lock-source-image-provenance.json"
		rmdir "$LOCK_DIR"
	fi
	if [[ -n "$PROVENANCE_TMP_DIR" ]]; then
		rm -f "$PROVENANCE_TMP_DIR/pre-lock-source-image-provenance.json"
		rmdir "$PROVENANCE_TMP_DIR"
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
EXPECTED_APP_IMAGE_ID="$(manifest_root_value app imageId)"
EXPECTED_APP_IMAGE_REF="$(manifest_root_value app imageRef)"
EXPECTED_POSTGRES_SERVICE="$(manifest_root_value postgres service)"
EXPECTED_POSTGRES_CONTAINER_PORT="$(manifest_root_value postgres containerPort)"
EXPECTED_POSTGRES_IMAGE_ID="$(manifest_root_value postgres imageId)"
EXPECTED_POSTGRES_IMAGE_REF="$(manifest_root_value postgres imageRef)"
EXPECTED_REDIS_SERVICE="$(manifest_root_value redis service)"
EXPECTED_REDIS_CONTAINER_PORT="$(manifest_root_value redis containerPort)"
EXPECTED_REDIS_IMAGE_ID="$(manifest_root_value redis imageId)"
EXPECTED_REDIS_IMAGE_REF="$(manifest_root_value redis imageRef)"

inspect_container_identity() {
	docker inspect --format '{"evidence":"issue199-runtime-identity","id":{{json .Id}},"imageId":{{json .Image}},"imageRef":{{json .Config.Image}},"startedAt":{{json .State.StartedAt}},"composeProject":{{json (index .Config.Labels "com.docker.compose.project")}},"composeService":{{json (index .Config.Labels "com.docker.compose.service")}},"composeConfigHash":{{json (index .Config.Labels "com.docker.compose.config-hash")}},"name":{{json .Name}},"publishedPorts":{{json .NetworkSettings.Ports}}}' "$1"
}

capture_container_identity_json() {
	local app_identity postgres_identity redis_identity
	app_identity="$(inspect_container_identity "$APP_CONTAINER")"
	postgres_identity="$(inspect_container_identity "$POSTGRES_CONTAINER")"
	redis_identity="$(inspect_container_identity "$REDIS_CONTAINER")"
	APP_IDENTITY="$app_identity" POSTGRES_IDENTITY="$postgres_identity" REDIS_IDENTITY="$redis_identity" node -e '
		const containers = {app: JSON.parse(process.env.APP_IDENTITY), postgres: JSON.parse(process.env.POSTGRES_IDENTITY), redis: JSON.parse(process.env.REDIS_IDENTITY)};
		for (const value of Object.values(containers)) { delete value.evidence; value.name = value.name.replace(/^\//, ""); }
		process.stdout.write(JSON.stringify({capturedAt: new Date().toISOString(), containers}));
	'
}

capture_source_image_provenance() {
	local output_file="$1"
	local compose_working_dir image_created_at
	compose_working_dir="$(docker inspect --format '{{ index .Config.Labels "com.docker.compose.project.working_dir" }}' "$APP_CONTAINER")"
	image_created_at="$(docker image inspect --format '{{.Created}}' "$EXPECTED_APP_IMAGE_ID")"
	node "$ISSUE_ROOT/validate-source-image-provenance.mjs" capture \
		"$INPUT_MANIFEST" "$compose_working_dir" "$EXPECTED_APP_IMAGE_ID" "$image_created_at" "$output_file"
}

capture_and_validate_source_image_provenance() {
	local output_file="$1"
	capture_source_image_provenance "$output_file"
	CURRENT_SOURCE_IMAGE_PROVENANCE_JSON="$(<"$output_file")" \
	INITIAL_SOURCE_IMAGE_PROVENANCE_JSON="$INITIAL_SOURCE_IMAGE_PROVENANCE_JSON" \
	node -e '
		const assert = require("node:assert/strict");
		assert.deepEqual(JSON.parse(process.env.CURRENT_SOURCE_IMAGE_PROVENANCE_JSON),
			JSON.parse(process.env.INITIAL_SOURCE_IMAGE_PROVENANCE_JSON),
			"source/image provenance changed after initial validation");
	'
}

PRE_LOCK_CONTAINER_IDENTITY_JSON="$(capture_container_identity_json)"

APP_PROJECT="$(docker inspect --format '{{ index .Config.Labels "com.docker.compose.project" }}' "$APP_CONTAINER")"
APP_SERVICE="$(docker inspect --format '{{ index .Config.Labels "com.docker.compose.service" }}' "$APP_CONTAINER")"
POSTGRES_PROJECT="$(docker inspect --format '{{ index .Config.Labels "com.docker.compose.project" }}' "$POSTGRES_CONTAINER")"
POSTGRES_SERVICE="$(docker inspect --format '{{ index .Config.Labels "com.docker.compose.service" }}' "$POSTGRES_CONTAINER")"
REDIS_PROJECT="$(docker inspect --format '{{ index .Config.Labels "com.docker.compose.project" }}' "$REDIS_CONTAINER")"
REDIS_SERVICE="$(docker inspect --format '{{ index .Config.Labels "com.docker.compose.service" }}' "$REDIS_CONTAINER")"
APP_PUBLISHED_PORTS="$(docker inspect --format '{{json .NetworkSettings.Ports}}' "$APP_CONTAINER")"
APP_RUNTIME_ENV="$(docker inspect --format '{{json .Config.Env}}' "$APP_CONTAINER")"

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

PRE_LOCK_CONTAINER_IDENTITY_JSON="$PRE_LOCK_CONTAINER_IDENTITY_JSON" \
EXPECTED_APP_IMAGE_ID="$EXPECTED_APP_IMAGE_ID" EXPECTED_APP_IMAGE_REF="$EXPECTED_APP_IMAGE_REF" \
EXPECTED_POSTGRES_IMAGE_ID="$EXPECTED_POSTGRES_IMAGE_ID" EXPECTED_POSTGRES_IMAGE_REF="$EXPECTED_POSTGRES_IMAGE_REF" \
EXPECTED_REDIS_IMAGE_ID="$EXPECTED_REDIS_IMAGE_ID" EXPECTED_REDIS_IMAGE_REF="$EXPECTED_REDIS_IMAGE_REF" \
node -e '
	const identity = JSON.parse(process.env.PRE_LOCK_CONTAINER_IDENTITY_JSON);
	for (const component of ["app", "postgres", "redis"]) {
		const prefix = component.toUpperCase();
		if (identity.containers[component].imageId !== process.env[`EXPECTED_${prefix}_IMAGE_ID`]
			|| identity.containers[component].imageRef !== process.env[`EXPECTED_${prefix}_IMAGE_REF`]) {
			throw new Error(`${component} image identity differs from the approved manifest.`);
		}
	}
'

PROVENANCE_TMP_DIR="$(mktemp -d /tmp/faithlog-199-source-provenance.XXXXXX)"
capture_source_image_provenance "$PROVENANCE_TMP_DIR/pre-lock-source-image-provenance.json"
INITIAL_SOURCE_IMAGE_PROVENANCE_JSON="$(<"$PROVENANCE_TMP_DIR/pre-lock-source-image-provenance.json")"

APP_CONNECTION_IDENTITY="$(node "$ISSUE_ROOT/validate-app-runtime-connections.mjs" "$APP_RUNTIME_ENV" \
	"$EXPECTED_POSTGRES_SERVICE" "$EXPECTED_POSTGRES_CONTAINER_PORT" "$PERF_DB_NAME" "$PERF_DB_USER" \
	"$EXPECTED_REDIS_SERVICE" "$EXPECTED_REDIS_CONTAINER_PORT")"
APP_RUNTIME_ENV=""

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
printf '%s\n' "$PRE_LOCK_CONTAINER_IDENTITY_JSON" > "$LOCK_DIR/pre-lock-target.json"

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
	APP_CONNECTION_IDENTITY="$APP_CONNECTION_IDENTITY" \
	PRE_POST_LOCK_GATE_JSON="$PRE_POST_LOCK_GATE_JSON" \
	SOURCE_IMAGE_PROVENANCE_JSON="$INITIAL_SOURCE_IMAGE_PROVENANCE_JSON" \
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
			appRuntimeConnections: JSON.parse(process.env.APP_CONNECTION_IDENTITY),
			prePostLockTargetGate: JSON.parse(process.env.PRE_POST_LOCK_GATE_JSON),
			sourceImageProvenance: JSON.parse(process.env.SOURCE_IMAGE_PROVENANCE_JSON),
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
	local evidence_boundary="${5:-}"
	PGPASSWORD="$PERF_DB_PASSWORD" docker exec -i \
		-e PGPASSWORD \
		-e PGAPPNAME=faithlog-issue199-observer \
		"$POSTGRES_CONTAINER" \
		psql -X -qAt -v ON_ERROR_STOP=1 \
		-U "$PERF_DB_USER" \
		-d "$PERF_DB_NAME" \
		-v campus_id="$campus_id" \
		-v week_start_date="$week_start_date" \
		-v evidence_boundary="${evidence_boundary:-not-applicable}" \
		-f - < "$sql_file" > "$output_file"
}

collect_db_context() {
	collect_db_evidence "$1" "$2" "$ISSUE_ROOT/collect-db-evidence.sql" "$3"
}

collect_db_correctness() {
	collect_db_machine_evidence "$1" "$2" "$ISSUE_ROOT/collect-correctness-evidence.sql" "$4" "$3"
}

collect_db_counters() {
	collect_db_machine_evidence "$1" "$2" "$ISSUE_ROOT/collect-db-counters.sql" "$3"
}

collect_postgres_runtime_identity() {
	PGPASSWORD="$PERF_DB_PASSWORD" docker exec -i \
		-e PGPASSWORD \
		-e PGAPPNAME=faithlog-issue199-observer \
		"$POSTGRES_CONTAINER" \
		psql -X -qAt -v ON_ERROR_STOP=1 \
		-U "$PERF_DB_USER" \
		-d "$PERF_DB_NAME" \
		-v expected_db_user="$PERF_DB_USER" \
		-f - < "$ISSUE_ROOT/collect-runtime-identity.sql"
}

collect_redis_runtime_identity() {
	docker exec "$REDIS_CONTAINER" redis-cli --raw INFO server | node "$ISSUE_ROOT/parse-redis-server-identity.mjs"
}

capture_runtime_identity_json() {
	local app_identity
	local postgres_container_identity
	local redis_identity
	local postgres_server_identity
	local redis_server_identity
	app_identity="$(inspect_container_identity "$APP_CONTAINER")"
	postgres_container_identity="$(inspect_container_identity "$POSTGRES_CONTAINER")"
	redis_identity="$(inspect_container_identity "$REDIS_CONTAINER")"
	postgres_server_identity="$(collect_postgres_runtime_identity)"
	redis_server_identity="$(collect_redis_runtime_identity)"
	APP_IDENTITY="$app_identity" \
	POSTGRES_CONTAINER_IDENTITY="$postgres_container_identity" \
	REDIS_IDENTITY="$redis_identity" \
	POSTGRES_SERVER_IDENTITY="$postgres_server_identity" \
	REDIS_SERVER_IDENTITY="$redis_server_identity" \
	node -e '
		const report = {
			capturedAt: new Date().toISOString(),
			containers: {
				app: JSON.parse(process.env.APP_IDENTITY),
				postgres: JSON.parse(process.env.POSTGRES_CONTAINER_IDENTITY),
				redis: JSON.parse(process.env.REDIS_IDENTITY),
			},
			postgres: JSON.parse(process.env.POSTGRES_SERVER_IDENTITY),
			redis: JSON.parse(process.env.REDIS_SERVER_IDENTITY),
		};
		for (const container of Object.values(report.containers)) {
			delete container.evidence;
			container.name = container.name.replace(/^\//, "");
		}
		process.stdout.write(`${JSON.stringify(report, null, 2)}\n`);
	'
}

validate_runtime_continuity() {
	node "$ISSUE_ROOT/validate-runtime-continuity.mjs" "$1" "$2" "$3" "$4"
}

collect_docker_resources() {
	local mode="$1"
	local boundary="$2"
	local output_file="$3"
	local stats_format
	local sampled_at
	sampled_at="$(node -e 'process.stdout.write(new Date().toISOString())')"
	stats_format='{"datasetMode":"'"$mode"'","boundary":"'"$boundary"'","stats":{{json .}}}'
	RESOURCE_DATASET_MODE="$mode" RESOURCE_BOUNDARY="$boundary" \
		docker stats --no-stream --no-trunc --format "$stats_format" \
		"$APP_CONTAINER" "$POSTGRES_CONTAINER" "$REDIS_CONTAINER" | \
		SAMPLED_AT="$sampled_at" node -e '
			const fs = require("node:fs");
			const lines = fs.readFileSync(0, "utf8").split(/\r?\n/).filter(Boolean);
			for (const line of lines) {
				const row = JSON.parse(line);
				process.stdout.write(`${JSON.stringify({...row, sampledAt: process.env.SAMPLED_AT,
					sampleSequence: 1, samplingCadence: "one-no-stream-snapshot-per-boundary"})}\n`);
			}
		' > "$output_file"
}

validate_docker_resources() {
	node "$ISSUE_ROOT/validate-docker-resources.mjs" "$1" "$2" "$3" "$4" "$5"
}

validate_docker_resources_or_defer_adoption() {
	if validate_docker_resources "$@"; then
		return
	fi
	local output_file="${5}"
	if node -e '
		const fs = require("node:fs");
		const gate = JSON.parse(fs.readFileSync(process.argv[1], "utf8"));
		if (gate.status !== "conditional-not-adoptable" || gate.automaticAdoption !== false) process.exit(1);
	' "$output_file"; then
		HAS_CONDITIONAL_RESOURCE=1
		return
	fi
	return 1
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
	local boundary="$2"
	local evidence_file="$3"
	node "$ISSUE_ROOT/validate-db-correctness.mjs" "$INPUT_MANIFEST" "$mode" "$boundary" "$evidence_file" >/dev/null
}

verify_api_correctness() {
	local mode="$1"
	local boundary="$2"
	local output_file="$3"
	INPUT_MANIFEST="$INPUT_MANIFEST" \
	BASE_URL="$BASE_URL" \
	PERF_ACCESS_TOKEN="$RUNTIME_ACCESS_TOKEN" \
	DATASET_MODE="$mode" \
	EVIDENCE_BOUNDARY="$boundary" \
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
printf '%s\n' "$INITIAL_RUNTIME_IDENTITY_JSON" > "$LOCK_DIR/post-lock-target.json"
capture_and_validate_source_image_provenance "$LOCK_DIR/post-lock-source-image-provenance.json"
node "$ISSUE_ROOT/validate-pre-post-lock-target.mjs" \
	"$LOCK_DIR/pre-lock-target.json" "$LOCK_DIR/post-lock-target.json" "$LOCK_DIR/pre-post-lock-gate.json"
PRE_POST_LOCK_GATE_JSON="$(<"$LOCK_DIR/pre-post-lock-gate.json")"

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
	printf '%s\n' "$INITIAL_SOURCE_IMAGE_PROVENANCE_JSON" > "$mode_report_dir/source-image-provenance-initial.json"

	record_environment "$mode_report_dir" "$dataset_id" "$fixture_run_id" "$mode"
	prepare_runtime_token "$mode" warmup
	PERF_ACCESS_TOKEN="$RUNTIME_ACCESS_TOKEN" \
		node "$ISSUE_ROOT/validate-token-lifetime.mjs" \
		"$WARMUP_DURATION" "$TOKEN_EXPIRY_SAFETY_SECONDS" "$(date +%s)" warmup >/dev/null
	collect_db_correctness "$campus_id" "$week_start_date" before "$mode_report_dir/db-correctness-before.json"
	validate_db_correctness "$mode" before "$mode_report_dir/db-correctness-before.json"
	verify_api_correctness "$mode" before "$mode_report_dir/api-correctness-before.json"
	collect_db_context "$campus_id" "$week_start_date" "$mode_report_dir/db-context-before.txt"

	PHASE=warmup run_k6_phase warmup "$mode" "$WARMUP_VUS" "$WARMUP_DURATION" "$mode_report_dir/warmup"
	test -f "$mode_report_dir/warmup/summary.json"
	node "$ISSUE_ROOT/validate-k6-summary.mjs" "$mode_report_dir/warmup/summary.json" \
		> "$mode_report_dir/warmup/adoption-gate.json"
	collect_db_correctness "$campus_id" "$week_start_date" pre-measured "$mode_report_dir/db-correctness-pre-measured.json"
	validate_db_correctness "$mode" pre-measured "$mode_report_dir/db-correctness-pre-measured.json"
	verify_api_correctness "$mode" pre-measured "$mode_report_dir/api-correctness-pre-measured.json"
	collect_db_context "$campus_id" "$week_start_date" "$mode_report_dir/db-context-pre-measured.txt"
	clear_runtime_token
	prepare_runtime_token "$mode" measured
	PERF_ACCESS_TOKEN="$RUNTIME_ACCESS_TOKEN" \
		node "$ISSUE_ROOT/validate-token-lifetime.mjs" \
		"$MEASURED_DURATION" "$TOKEN_EXPIRY_SAFETY_SECONDS" "$(date +%s)" measured >/dev/null
	printf '%s\n' "$INITIAL_RUNTIME_IDENTITY_JSON" > "$mode_report_dir/runtime-identity-initial.json"
	capture_and_validate_source_image_provenance "$mode_report_dir/measured/source-image-provenance-before.json"
	capture_runtime_identity_json > "$mode_report_dir/measured/runtime-identity-before.json"
	validate_runtime_continuity \
		"$mode_report_dir/runtime-identity-initial.json" \
		"$mode_report_dir/measured/runtime-identity-before.json" \
		"$mode_report_dir/measured/runtime-identity-before.json" \
		"$mode_report_dir/measured/runtime-continuity-pre-gate.json"
	collect_docker_resources "$mode" before "$mode_report_dir/measured/docker-stats-before.jsonl"
	validate_docker_resources_or_defer_adoption \
		"$mode_report_dir/measured/docker-stats-before.jsonl" \
		"$mode_report_dir/measured/runtime-identity-before.json" \
		"$mode" before "$mode_report_dir/measured/docker-resource-before-gate.json"
	collect_db_counters "$campus_id" "$week_start_date" "$mode_report_dir/measured/db-counters-before.json"

	PHASE=measured run_k6_phase measured "$mode" "$MEASURED_VUS" "$MEASURED_DURATION" "$mode_report_dir/measured"
	test -f "$mode_report_dir/measured/summary.json"
	node "$ISSUE_ROOT/validate-k6-summary.mjs" "$mode_report_dir/measured/summary.json" \
		> "$mode_report_dir/measured/adoption-gate.json"
	collect_db_counters "$campus_id" "$week_start_date" "$mode_report_dir/measured/db-counters-after.json"
	collect_docker_resources "$mode" after "$mode_report_dir/measured/docker-stats-after.jsonl"
	capture_and_validate_source_image_provenance "$mode_report_dir/measured/source-image-provenance-after.json"
	capture_runtime_identity_json > "$mode_report_dir/measured/runtime-identity-after.json"
	validate_docker_resources_or_defer_adoption \
		"$mode_report_dir/measured/docker-stats-after.jsonl" \
		"$mode_report_dir/measured/runtime-identity-after.json" \
		"$mode" after "$mode_report_dir/measured/docker-resource-after-gate.json"
	collect_db_context "$campus_id" "$week_start_date" "$mode_report_dir/db-context-after.txt"
	collect_db_correctness "$campus_id" "$week_start_date" after "$mode_report_dir/db-correctness-after.json"
	validate_db_correctness "$mode" after "$mode_report_dir/db-correctness-after.json"
	verify_api_correctness "$mode" after "$mode_report_dir/api-correctness-after.json"
	capture_and_validate_source_image_provenance "$mode_report_dir/source-image-provenance-final.json"
	capture_runtime_identity_json > "$mode_report_dir/runtime-identity-final.json"
	clear_runtime_token
	validate_runtime_continuity \
		"$mode_report_dir/runtime-identity-initial.json" \
		"$mode_report_dir/measured/runtime-identity-before.json" \
		"$mode_report_dir/runtime-identity-final.json" \
		"$mode_report_dir/measured/runtime-continuity-gate.json"
	validate_db_window_or_defer_adoption \
		"$mode_report_dir/measured/db-counters-before.json" \
		"$mode_report_dir/measured/db-counters-after.json" \
		"$mode_report_dir/measured/db-window-adoption-gate.json"
	MODE="$mode" node -e '
		const fs = require("node:fs");
		fs.writeFileSync(process.argv[1], `${JSON.stringify({status: "conditional-not-adoptable",
			automaticAdoption: false, datasetMode: process.env.MODE,
			reasons: ["external-activity-boundary-only", "docker-resource-boundary-only"]}, null, 2)}\n`,
			{flag: "wx", mode: 0o600});
	' "$mode_report_dir/final-adoption-gate.json"
done

if (( HAS_CONDITIONAL_DB_WINDOW || HAS_CONDITIONAL_RESOURCE )); then
	echo "Baseline is conditional-not-adoptable: external activity and Docker resources are boundary-only, or raw RAM percentage lacks an approved tolerance." >&2
	exit 1
fi
