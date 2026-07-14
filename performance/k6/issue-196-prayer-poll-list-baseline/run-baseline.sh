#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${ROOT_DIR}/../../.." && pwd)"
SCENARIO_FILE="${ROOT_DIR}/scenario.js"
DB_STATS_SQL="${ROOT_DIR}/db-table-stats.sql"
DB_ACTIVITY_SQL="${ROOT_DIR}/db-activity.sql"
FIXTURE_RUN_ID="${FIXTURE_RUN_ID:?FIXTURE_RUN_ID is required}"
EXECUTION_RUN_ID="${EXECUTION_RUN_ID:?EXECUTION_RUN_ID is required}"
FIXTURE_MANIFEST="${FIXTURE_MANIFEST:-${REPO_ROOT}/build/reports/k6/issue-196/${FIXTURE_RUN_ID}/fixture-manifest.json}"
REPORT_ROOT="${REPO_ROOT}/build/reports/k6/issue-196/${FIXTURE_RUN_ID}/${EXECUTION_RUN_ID}"
BASE_URL="${BASE_URL:?BASE_URL is required at runtime}"
WARMUP_VUS="${WARMUP_VUS:?WARMUP_VUS must be explicitly approved and supplied}"
WARMUP_DURATION="${WARMUP_DURATION:?WARMUP_DURATION must be explicitly approved and supplied}"
MEASURED_VUS="${MEASURED_VUS:?MEASURED_VUS must be explicitly approved and supplied}"
MEASURED_DURATION="${MEASURED_DURATION:?MEASURED_DURATION must be explicitly approved and supplied}"
SAMPLING_INTERVAL_SECONDS="${SAMPLING_INTERVAL_SECONDS:?SAMPLING_INTERVAL_SECONDS requires explicit user approval}"
SAMPLING_MAX_GAP_SECONDS="${SAMPLING_MAX_GAP_SECONDS:?SAMPLING_MAX_GAP_SECONDS requires explicit user approval}"
PERF_ADMIN_EMAIL="${PERF_ADMIN_EMAIL:?PERF_ADMIN_EMAIL is required at runtime}"
PERF_ADMIN_PASSWORD="${PERF_ADMIN_PASSWORD:?PERF_ADMIN_PASSWORD is required at runtime}"
PERF_MEMBER_PASSWORD="${PERF_MEMBER_PASSWORD:?PERF_MEMBER_PASSWORD is required at runtime}"
PERF_DB_USER="${PERF_DB_USER:?PERF_DB_USER is required at runtime}"
PERF_DB_NAME="${PERF_DB_NAME:?PERF_DB_NAME is required at runtime}"
PERF_DB_PASSWORD="${PERF_DB_PASSWORD:?PERF_DB_PASSWORD is required at runtime}"
APP_CONTAINER="${APP_CONTAINER:?APP_CONTAINER is required at runtime}"
DB_CONTAINER="${DB_CONTAINER:?DB_CONTAINER is required at runtime}"
EXPECTED_APP_SERVICE="${EXPECTED_APP_SERVICE:?EXPECTED_APP_SERVICE is required at runtime}"
EXPECTED_DB_SERVICE="${EXPECTED_DB_SERVICE:?EXPECTED_DB_SERVICE is required at runtime}"
EXPECTED_APP_IMAGE="${EXPECTED_APP_IMAGE:?EXPECTED_APP_IMAGE is required at runtime}"
SQL_LOG_MARKER="org.hibernate.SQL"
REQUESTED_MODE="${1:?Mode is required: all, prayer, poll-member, or poll-admin}"
MODE_SEQUENCE=(prayer poll-member poll-admin)

export -n PERF_ADMIN_EMAIL PERF_ADMIN_PASSWORD PERF_MEMBER_PASSWORD PERF_DB_USER PERF_DB_NAME PERF_DB_PASSWORD
unset PERF_ACCESS_TOKEN PERF_ADMIN_ACCESS_TOKEN PERF_MEMBER_ACCESS_TOKEN

if [[ ! "${FIXTURE_RUN_ID}" =~ ^[a-z0-9][a-z0-9_-]{7,31}$ ]]; then
	echo "FIXTURE_RUN_ID must be 8-32 lowercase characters." >&2
	exit 1
fi

if [[ ! "${EXECUTION_RUN_ID}" =~ ^[a-z0-9][a-z0-9_-]{7,31}$ ]]; then
	echo "EXECUTION_RUN_ID must be 8-32 lowercase characters." >&2
	exit 1
fi

for command in node k6 docker lsof; do
	command -v "${command}" >/dev/null || { echo "Missing command: ${command}" >&2; exit 1; }
done
SAMPLING_INTERVAL_VALUE="${SAMPLING_INTERVAL_SECONDS}" SAMPLING_MAX_GAP_VALUE="${SAMPLING_MAX_GAP_SECONDS}" node -e '
	const interval = Number(process.env.SAMPLING_INTERVAL_VALUE);
	const maxGap = Number(process.env.SAMPLING_MAX_GAP_VALUE);
	if (!Number.isFinite(interval) || interval <= 0 || !Number.isFinite(maxGap) || maxGap < interval) process.exit(1);
' || { echo "Sampling values must be positive and max gap must be at least the interval." >&2; exit 1; }
[[ -f "${FIXTURE_MANIFEST}" ]] || { echo "Fixture manifest not found: ${FIXTURE_MANIFEST}" >&2; exit 1; }
[[ "${BASE_URL}" =~ ^http://(localhost|127\.0\.0\.1|\[::1\])(:[0-9]+)?$ ]] \
	|| { echo "Issue #196 baseline is local-Docker-only." >&2; exit 1; }

manifest_value() {
	node -e '
		const fs = require("node:fs");
		const value = process.argv[2].split(".").reduce((current, key) => current[key], JSON.parse(fs.readFileSync(process.argv[1], "utf8")));
		process.stdout.write(String(value));
	' "${FIXTURE_MANIFEST}" "$1"
}

manifest_run_id="$(manifest_value fixtureRunId)"
dataset_id="$(manifest_value datasetId)"
member_email="$(manifest_value primaryCampus.memberActor.email)"
shaped_at="$(manifest_value shapedAt)"
[[ "${manifest_run_id}" == "${FIXTURE_RUN_ID}" ]] || { echo "fixtureRunId mismatch." >&2; exit 1; }
[[ "${dataset_id}" == "issue-196-prayer-poll-list-v1" ]] || { echo "Unexpected datasetId=${dataset_id}." >&2; exit 1; }
[[ "${shaped_at}" != "null" ]] || { echo "Fixture has not been shaped." >&2; exit 1; }

assert_fixture_windows() {
	node -e '
	const fs = require("node:fs");
	const manifest = JSON.parse(fs.readFileSync(process.argv[1], "utf8"));
	const now = Date.now();
	const poll = manifest.polls.byKey;
	const instant = (value) => {
		const parsed = Date.parse(value);
		if (!Number.isFinite(parsed)) throw new Error(`Invalid manifest instant: ${value}`);
		return parsed;
	};
	if (!(instant(poll.open.startsAt) <= now && now < instant(poll.open.endsAt))) throw new Error("OPEN fixture window is stale.");
	if (!(instant(poll.closed_member_visible.endsAt) <= now && now <= instant(poll.closed_member_visible.endsAt) + 3 * 86400000)) throw new Error("Member visibility fixture window is stale.");
	if (!(instant(poll.closed_admin_only.endsAt) <= now && now <= instant(poll.closed_admin_only.endsAt) + 7 * 86400000)) throw new Error("Admin visibility fixture window is stale.");
	if (!(now > instant(poll.closed_expired.endsAt) + 7 * 86400000)) throw new Error("Expired fixture is not beyond the admin window.");
	if (!(now < instant(poll.scheduled_future.startsAt))) throw new Error("Scheduled fixture is no longer in the future.");
' "${FIXTURE_MANIFEST}"
}

assert_fixture_windows

label() {
	docker inspect --format "{{ index .Config.Labels \"$2\" }}" "$1"
}

compose_project="$(label "${APP_CONTAINER}" com.docker.compose.project)"
app_service="$(label "${APP_CONTAINER}" com.docker.compose.service)"
db_project="$(label "${DB_CONTAINER}" com.docker.compose.project)"
db_service="$(label "${DB_CONTAINER}" com.docker.compose.service)"
app_config_hash="$(label "${APP_CONTAINER}" com.docker.compose.config-hash)"
db_config_hash="$(label "${DB_CONTAINER}" com.docker.compose.config-hash)"
app_image="$(docker inspect --format '{{.Config.Image}}' "${APP_CONTAINER}")"
app_image_id="$(docker inspect --format '{{.Image}}' "${APP_CONTAINER}")"
seed_project="$(manifest_value composeRuntime.composeProject)"
seed_app_hash="$(manifest_value composeRuntime.appConfigHash)"
seed_db_hash="$(manifest_value composeRuntime.dbConfigHash)"
seed_app_image_id="$(manifest_value composeRuntime.appImageId)"
seed_target_port="$(manifest_value composeRuntime.targetPort)"
base_target_port="$(BASE_URL_VALUE="${BASE_URL}" node -e '
	const url = new URL(process.env.BASE_URL_VALUE);
	if (url.protocol !== "http:" || !["localhost", "127.0.0.1", "[::1]"].includes(url.hostname)) process.exit(2);
	process.stdout.write(url.port || "80");
')"
published_ports="$(docker port "${APP_CONTAINER}" 8080/tcp)"
app_client_addrs="$(docker inspect --format '{{range .NetworkSettings.Networks}}{{println .IPAddress}}{{end}}' "${APP_CONTAINER}")"
app_client_addrs="${app_client_addrs//$'\n'/,}"
app_client_addrs="${app_client_addrs%,}"

[[ -n "${compose_project}" && "${compose_project}" == "${db_project}" ]] \
	|| { echo "App/Postgres Compose project labels are missing or different." >&2; exit 1; }
[[ "${compose_project}" =~ ^[a-z0-9][a-z0-9_-]*$ ]] \
	|| { echo "Compose project label cannot be represented by the canonical lock path." >&2; exit 1; }
[[ "${app_service}" == "${EXPECTED_APP_SERVICE}" && "${db_service}" == "${EXPECTED_DB_SERVICE}" ]] \
	|| { echo "Unexpected Compose service labels: app=${app_service}, db=${db_service}." >&2; exit 1; }
[[ -n "${app_config_hash}" && -n "${db_config_hash}" ]] \
	|| { echo "Compose config-hash labels must be present on app and PostgreSQL." >&2; exit 1; }
[[ "${app_image}" == "${EXPECTED_APP_IMAGE}" ]] \
	|| { echo "Expected app image ${EXPECTED_APP_IMAGE}, actual ${app_image}." >&2; exit 1; }
[[ "${compose_project}" == "${seed_project}" && "${app_config_hash}" == "${seed_app_hash}" && "${db_config_hash}" == "${seed_db_hash}" ]] \
	|| { echo "Current Compose identity differs from the seed manifest." >&2; exit 1; }
[[ "${app_image_id}" == "${seed_app_image_id}" ]] \
	|| { echo "Current immutable app image differs from the seed manifest." >&2; exit 1; }
[[ "${base_target_port}" == "${seed_target_port}" ]] \
	|| { echo "BASE_URL port ${base_target_port} differs from the seed target port ${seed_target_port}." >&2; exit 1; }
grep -Eq "(^|:|\\])${seed_target_port}$" <<<"${published_ports}" \
	|| { echo "The attested app container no longer publishes target port ${seed_target_port}." >&2; exit 1; }
[[ "${app_client_addrs}" =~ ^[0-9a-fA-F:.]+(,[0-9a-fA-F:.]+)*$ ]] \
	|| { echo "Attested app container network addresses are missing or invalid." >&2; exit 1; }

PERF_PROJECT_LOCK="/tmp/faithlog-performance-${compose_project}.lock"
if ! mkdir "${PERF_PROJECT_LOCK}" 2>/dev/null; then
	echo "Another seed or load run owns ${PERF_PROJECT_LOCK}. Parallel execution is forbidden." >&2
	exit 1
fi
trap 'rmdir "${PERF_PROJECT_LOCK}" 2>/dev/null || true' EXIT

app_container_id="$(docker inspect --format '{{.Id}}' "${APP_CONTAINER}")"
app_container_started_at="$(docker inspect --format '{{.State.StartedAt}}' "${APP_CONTAINER}")"
db_container_id="$(docker inspect --format '{{.Id}}' "${DB_CONTAINER}")"
db_image_id="$(docker inspect --format '{{.Image}}' "${DB_CONTAINER}")"
db_container_started_at="$(docker inspect --format '{{.State.StartedAt}}' "${DB_CONTAINER}")"

capture_database_identity() {
	PGPASSWORD="${PERF_DB_PASSWORD}" docker exec -e PGPASSWORD -e PGAPPNAME=faithlog_issue196_observer "${DB_CONTAINER}" \
		psql -X -v ON_ERROR_STOP=1 -h 127.0.0.1 -U "${PERF_DB_USER}" -d "${PERF_DB_NAME}" -At \
		-c "select json_build_object('currentDatabase', current_database(), 'serverAddress', inet_server_addr(), 'serverPort', inet_server_port(), 'postmasterStartedAt', pg_postmaster_start_time())"
}

database_identity="$(capture_database_identity)"

assert_runtime_continuity() {
	local current_database_identity
	[[ "$(docker inspect --format '{{.Id}}' "${APP_CONTAINER}")" == "${app_container_id}" ]] \
		|| { echo "App container ID changed during execution." >&2; return 1; }
	[[ "$(docker inspect --format '{{.Image}}' "${APP_CONTAINER}")" == "${app_image_id}" ]] \
		|| { echo "App immutable image ID changed during execution." >&2; return 1; }
	[[ "$(docker inspect --format '{{.State.StartedAt}}' "${APP_CONTAINER}")" == "${app_container_started_at}" ]] \
		|| { echo "App container start time changed during execution." >&2; return 1; }
	[[ "$(docker inspect --format '{{.Id}}' "${DB_CONTAINER}")" == "${db_container_id}" ]] \
		|| { echo "PostgreSQL container ID changed during execution." >&2; return 1; }
	[[ "$(docker inspect --format '{{.Image}}' "${DB_CONTAINER}")" == "${db_image_id}" ]] \
		|| { echo "PostgreSQL immutable image ID changed during execution." >&2; return 1; }
	[[ "$(docker inspect --format '{{.State.StartedAt}}' "${DB_CONTAINER}")" == "${db_container_started_at}" ]] \
		|| { echo "PostgreSQL container start time changed during execution." >&2; return 1; }
	[[ "$(label "${APP_CONTAINER}" com.docker.compose.project)" == "${compose_project}"
		&& "$(label "${DB_CONTAINER}" com.docker.compose.project)" == "${compose_project}"
		&& "$(label "${APP_CONTAINER}" com.docker.compose.service)" == "${app_service}"
		&& "$(label "${DB_CONTAINER}" com.docker.compose.service)" == "${db_service}"
		&& "$(label "${APP_CONTAINER}" com.docker.compose.config-hash)" == "${app_config_hash}"
		&& "$(label "${DB_CONTAINER}" com.docker.compose.config-hash)" == "${db_config_hash}" ]] \
		|| { echo "Compose runtime labels changed during execution." >&2; return 1; }
	grep -Eq "(^|:|\\])${seed_target_port}$" <<<"$(docker port "${APP_CONTAINER}" 8080/tcp)" \
		|| { echo "App published port changed during execution." >&2; return 1; }
	current_database_identity="$(capture_database_identity)"
	EXPECTED_DB_IDENTITY="${database_identity}" CURRENT_DB_IDENTITY="${current_database_identity}" node -e '
		const expected = JSON.parse(process.env.EXPECTED_DB_IDENTITY);
		const current = JSON.parse(process.env.CURRENT_DB_IDENTITY);
		if (JSON.stringify(expected) !== JSON.stringify(current)) process.exit(1);
	' || { echo "PostgreSQL runtime identity changed during execution." >&2; return 1; }
}

if pgrep -f '[k]6 run' >/dev/null 2>&1; then
	echo "Another k6 process is running outside the canonical project lock." >&2
	exit 1
fi

mkdir -p "$(dirname "${REPORT_ROOT}")"
if [[ -e "${REPORT_ROOT}" ]]; then
	echo "Report directory already exists. Refusing to overwrite: ${REPORT_ROOT}" >&2
	exit 1
fi
mkdir "${REPORT_ROOT}"

app_environment="$(docker inspect --format '{{range .Config.Env}}{{println .}}{{end}}' "${APP_CONTAINER}")"
grep -q '^LOGGING_LEVEL_ORG_HIBERNATE_SQL=DEBUG$' <<<"${app_environment}" \
	|| { echo "Start the existing app container externally with LOGGING_LEVEL_ORG_HIBERNATE_SQL=DEBUG." >&2; exit 1; }
grep -q '^SPRING_JPA_PROPERTIES_HIBERNATE_FORMAT_SQL=false$' <<<"${app_environment}" \
	|| { echo "Start the existing app container externally with SPRING_JPA_PROPERTIES_HIBERNATE_FORMAT_SQL=false." >&2; exit 1; }
grep -q '^FAITHLOG_SCHEDULER_ENABLED=false$' <<<"${app_environment}" \
	|| { echo "Start the existing app container externally with FAITHLOG_SCHEDULER_ENABLED=false for isolated query evidence." >&2; exit 1; }

login_token() {
	LOGIN_EMAIL="$1" LOGIN_PASSWORD="$2" LOGIN_BASE_URL="${BASE_URL}" node -e '
		const response = await fetch(`${process.env.LOGIN_BASE_URL}/api/v1/auth/login`, {
			method: "POST",
			headers: { "Content-Type": "application/json" },
			body: JSON.stringify({ email: process.env.LOGIN_EMAIL, password: process.env.LOGIN_PASSWORD }),
		});
		const payload = await response.json();
		if (response.status !== 200 || !payload.data?.accessToken) throw new Error(`login failed: ${response.status}`);
		process.stdout.write(payload.data.accessToken);
	'
}

snapshot_db_tables() {
	local output="$1"
	PGPASSWORD="${PERF_DB_PASSWORD}" docker exec -e PGPASSWORD "${DB_CONTAINER}" \
		psql -X -v ON_ERROR_STOP=1 -h 127.0.0.1 -U "${PERF_DB_USER}" -d "${PERF_DB_NAME}" -At -f - \
		< "${DB_STATS_SQL}" > "${output}"
}

validate_token_lifetime() {
	PERF_ACCESS_TOKEN="$1" PHASE_DURATION="$2" node "${ROOT_DIR}/token-lifetime.mjs"
}

sample_resources() {
	local k6_pid="$1"
	local output="$2"
	while kill -0 "${k6_pid}" 2>/dev/null; do
		local captured_at
		captured_at="$(rfc3339_now)"
		docker stats --no-stream --format '{{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}\t{{.MemPerc}}' \
			"${APP_CONTAINER}" "${DB_CONTAINER}" \
			| while IFS= read -r sample; do printf '%s\t%s\n' "${captured_at}" "${sample}"; done \
			>> "${output}"
		sleep "${SAMPLING_INTERVAL_SECONDS}"
	done
}

sample_runtime_integrity() {
	local k6_pid="$1"
	local output="$2"
	while kill -0 "${k6_pid}" 2>/dev/null; do
		local lsof_text
		local lsof_status
		local db_activity
		set +e
		lsof_text="$(lsof -nP -iTCP:"${seed_target_port}" -sTCP:ESTABLISHED -Fpc)"
		lsof_status=$?
		set -e
		if (( lsof_status > 1 )); then
			return "${lsof_status}"
		fi
		db_activity="$(PGPASSWORD="${PERF_DB_PASSWORD}" docker exec -e PGPASSWORD -e PGAPPNAME=faithlog_issue196_observer "${DB_CONTAINER}" \
			psql -X -v ON_ERROR_STOP=1 -U "${PERF_DB_USER}" -d "${PERF_DB_NAME}" -At \
			-v app_client_addrs="${app_client_addrs}" -f - < "${DB_ACTIVITY_SQL}")"
		LSOF_TEXT="${lsof_text}" DB_ACTIVITY_JSON="${db_activity}" K6_PID="${k6_pid}" \
			node "${ROOT_DIR}/activity-sample.mjs" >> "${output}"
		sleep "${SAMPLING_INTERVAL_SECONDS}"
	done
}

rfc3339_now() {
	node -e 'process.stdout.write(new Date().toISOString())'
}

endpoints_for_mode() {
	case "$1" in
		prayer)
			echo "prayer_current_season prayer_groups prayer_assignable prayer_weekly_board_admin prayer_weekly_board_member"
			;;
		poll-member)
			echo "poll_member_list poll_member_detail poll_member_results poll_member_comments poll_member_cross_campus_detail poll_member_isolation_campus_detail"
			;;
		poll-admin)
			echo "poll_admin_list poll_admin_detail poll_admin_results poll_admin_comments poll_admin_missing_members poll_admin_template_list poll_admin_template_detail poll_admin_cross_campus_detail"
			;;
		*)
			echo "Unknown mode: $1" >&2
			return 1
			;;
	esac
}

run_endpoint() {
	local mode="$1"
	local endpoint="$2"
	local endpoint_dir="${REPORT_ROOT}/${mode}/${endpoint}"
	local warmup_summary_file="${endpoint_dir}/warmup-k6-summary.json"
	local summary_file="${endpoint_dir}/k6-summary.json"
	local before_file="${endpoint_dir}/db-before.json"
	local after_file="${endpoint_dir}/db-after.json"
	local sql_log_file="${endpoint_dir}/hibernate-sql.log"
	local resource_file="${endpoint_dir}/resource-samples.tsv"
	local integrity_file="${endpoint_dir}/runtime-integrity.jsonl"
	local metadata_file="${endpoint_dir}/runtime-metadata.json"
	local report_file="${endpoint_dir}/report.json"
	local admin_token
	local member_token
	local warmup_status
	local log_since
	local log_until
	local k6_pid
	local sampler_pid
	local integrity_pid
	local k6_status
	local sampler_status
	local integrity_status
	local fixture_window_status
	local log_capture_status
	local after_snapshot_status
	local runtime_continuity_status
	local final_continuity_status
	local summarize_status

	mkdir -p "$(dirname "${endpoint_dir}")"
	if ! mkdir "${endpoint_dir}"; then
		echo "Endpoint report directory exists. Refusing to overwrite: ${endpoint_dir}" >&2
		return 1
	fi
	assert_fixture_windows
	assert_runtime_continuity

	admin_token="$(login_token "${PERF_ADMIN_EMAIL}" "${PERF_ADMIN_PASSWORD}")"
	member_token="$(login_token "${member_email}" "${PERF_MEMBER_PASSWORD}")"
	validate_token_lifetime "${admin_token}" "${WARMUP_DURATION}"
	validate_token_lifetime "${member_token}" "${WARMUP_DURATION}"
	set +e
	env -u PERF_ADMIN_EMAIL -u PERF_ADMIN_PASSWORD -u PERF_MEMBER_PASSWORD \
	-u PERF_DB_USER -u PERF_DB_NAME -u PERF_DB_PASSWORD \
	BASE_URL="${BASE_URL}" MODE="${mode}" ENDPOINT="${endpoint}" \
	VUS="${WARMUP_VUS}" DURATION="${WARMUP_DURATION}" FIXTURE_MANIFEST="${FIXTURE_MANIFEST}" \
	PERF_ADMIN_ACCESS_TOKEN="${admin_token}" PERF_MEMBER_ACCESS_TOKEN="${member_token}" \
		k6 run --summary-export "${warmup_summary_file}" "${SCENARIO_FILE}"
	warmup_status=$?
	set -e
	admin_token=''
	member_token=''
	if (( warmup_status != 0 )); then
		echo "Warmup failed for ${mode}/${endpoint}; measured phase was not started." >&2
		return "${warmup_status}"
	fi
	assert_fixture_windows
	assert_runtime_continuity

	admin_token="$(login_token "${PERF_ADMIN_EMAIL}" "${PERF_ADMIN_PASSWORD}")"
	member_token="$(login_token "${member_email}" "${PERF_MEMBER_PASSWORD}")"
	validate_token_lifetime "${admin_token}" "${MEASURED_DURATION}"
	validate_token_lifetime "${member_token}" "${MEASURED_DURATION}"
	snapshot_db_tables "${before_file}"
	log_since="$(rfc3339_now)"
	: > "${resource_file}"
	: > "${integrity_file}"

	# measured phase: only this k6 process is inside the DB/log/resource evidence window.
	env -u PERF_ADMIN_EMAIL -u PERF_ADMIN_PASSWORD -u PERF_MEMBER_PASSWORD \
	-u PERF_DB_USER -u PERF_DB_NAME -u PERF_DB_PASSWORD \
	BASE_URL="${BASE_URL}" \
	MODE="${mode}" \
	ENDPOINT="${endpoint}" \
	VUS="${MEASURED_VUS}" \
	DURATION="${MEASURED_DURATION}" \
	FIXTURE_MANIFEST="${FIXTURE_MANIFEST}" \
	PERF_ADMIN_ACCESS_TOKEN="${admin_token}" \
	PERF_MEMBER_ACCESS_TOKEN="${member_token}" \
		k6 run --summary-export "${summary_file}" "${SCENARIO_FILE}" &
	k6_pid=$!
	sample_resources "${k6_pid}" "${resource_file}" &
	sampler_pid=$!
	sample_runtime_integrity "${k6_pid}" "${integrity_file}" &
	integrity_pid=$!
	set +e
	wait "${k6_pid}"
	k6_status=$?
	set -e
	set +e
	wait "${sampler_pid}"
	sampler_status=$?
	set -e
	set +e
	wait "${integrity_pid}"
	integrity_status=$?
	set -e
	log_until="$(rfc3339_now)"
	set +e
	assert_runtime_continuity
	runtime_continuity_status=$?
	assert_fixture_windows
	fixture_window_status=$?
	docker logs --since "${log_since}" --until "${log_until}" "${APP_CONTAINER}" > "${sql_log_file}" 2>&1
	log_capture_status=$?
	snapshot_db_tables "${after_file}"
	after_snapshot_status=$?
	assert_runtime_continuity
	final_continuity_status=$?
	set -e
	if (( final_continuity_status != 0 )); then
		runtime_continuity_status="${final_continuity_status}"
	fi

	MODE_VALUE="${mode}" ENDPOINT_VALUE="${endpoint}" DATASET_VALUE="${dataset_id}" \
	FIXTURE_VALUE="${FIXTURE_RUN_ID}" EXECUTION_VALUE="${EXECUTION_RUN_ID}" PROJECT_VALUE="${compose_project}" \
	APP_SERVICE_VALUE="${app_service}" DB_SERVICE_VALUE="${db_service}" \
	EXPECTED_APP_SERVICE_VALUE="${EXPECTED_APP_SERVICE}" EXPECTED_DB_SERVICE_VALUE="${EXPECTED_DB_SERVICE}" \
	APP_HASH_VALUE="${app_config_hash}" DB_HASH_VALUE="${db_config_hash}" \
	APP_IMAGE_VALUE="${app_image}" EXPECTED_IMAGE_VALUE="${EXPECTED_APP_IMAGE}" \
	APP_IMAGE_ID_VALUE="${app_image_id}" TARGET_PORT_VALUE="${seed_target_port}" \
	APP_CONTAINER_VALUE="${APP_CONTAINER}" DB_CONTAINER_VALUE="${DB_CONTAINER}" \
	APP_CONTAINER_ID_VALUE="${app_container_id}" APP_STARTED_AT_VALUE="${app_container_started_at}" \
	DB_CONTAINER_ID_VALUE="${db_container_id}" DB_IMAGE_ID_VALUE="${db_image_id}" \
	DB_STARTED_AT_VALUE="${db_container_started_at}" DB_IDENTITY_VALUE="${database_identity}" \
	K6_STATUS_VALUE="${k6_status}" SAMPLER_STATUS_VALUE="${sampler_status}" \
	INTEGRITY_STATUS_VALUE="${integrity_status}" WARMUP_STATUS_VALUE="${warmup_status}" \
	CONTINUITY_STATUS_VALUE="${runtime_continuity_status}" \
	WINDOW_STATUS_VALUE="${fixture_window_status}" \
	LOG_STATUS_VALUE="${log_capture_status}" AFTER_DB_STATUS_VALUE="${after_snapshot_status}" \
	STARTED_AT_VALUE="${log_since}" ENDED_AT_VALUE="${log_until}" \
	WARMUP_VUS_VALUE="${WARMUP_VUS}" WARMUP_DURATION_VALUE="${WARMUP_DURATION}" \
	VUS_VALUE="${MEASURED_VUS}" DURATION_VALUE="${MEASURED_DURATION}" \
	SAMPLING_INTERVAL_VALUE="${SAMPLING_INTERVAL_SECONDS}" SAMPLING_MAX_GAP_VALUE="${SAMPLING_MAX_GAP_SECONDS}" \
	node -e '
		const fs = require("node:fs");
		const metadata = {
			capturedAt: new Date().toISOString(),
			mode: process.env.MODE_VALUE,
			endpoint: process.env.ENDPOINT_VALUE,
			datasetId: process.env.DATASET_VALUE,
			fixtureRunId: process.env.FIXTURE_VALUE,
			executionRunId: process.env.EXECUTION_VALUE,
			runtime: {
				composeProject: process.env.PROJECT_VALUE,
				appServiceLabel: process.env.APP_SERVICE_VALUE,
				dbServiceLabel: process.env.DB_SERVICE_VALUE,
				appConfigHash: process.env.APP_HASH_VALUE,
				dbConfigHash: process.env.DB_HASH_VALUE,
				appImage: process.env.APP_IMAGE_VALUE,
				appImageId: process.env.APP_IMAGE_ID_VALUE,
				expectedAppImage: process.env.EXPECTED_IMAGE_VALUE,
				expectedAppService: process.env.EXPECTED_APP_SERVICE_VALUE,
				expectedDbService: process.env.EXPECTED_DB_SERVICE_VALUE,
				targetPort: process.env.TARGET_PORT_VALUE,
				appContainer: process.env.APP_CONTAINER_VALUE,
				dbContainer: process.env.DB_CONTAINER_VALUE,
				appContainerId: process.env.APP_CONTAINER_ID_VALUE,
				appContainerStartedAt: process.env.APP_STARTED_AT_VALUE,
				dbContainerId: process.env.DB_CONTAINER_ID_VALUE,
				dbImageId: process.env.DB_IMAGE_ID_VALUE,
				dbContainerStartedAt: process.env.DB_STARTED_AT_VALUE,
				databaseIdentity: JSON.parse(process.env.DB_IDENTITY_VALUE),
				k6ExitStatus: Number(process.env.K6_STATUS_VALUE),
				resourceSamplerExitStatus: Number(process.env.SAMPLER_STATUS_VALUE),
				integritySamplerExitStatus: Number(process.env.INTEGRITY_STATUS_VALUE),
				warmupExitStatus: Number(process.env.WARMUP_STATUS_VALUE),
				runtimeContinuityExitStatus: Number(process.env.CONTINUITY_STATUS_VALUE),
				automaticAdoption: false,
				adoptionPolicyStatus: "pending-user-approval",
				fixtureWindowExitStatus: Number(process.env.WINDOW_STATUS_VALUE),
				logCaptureExitStatus: Number(process.env.LOG_STATUS_VALUE),
				afterDbSnapshotExitStatus: Number(process.env.AFTER_DB_STATUS_VALUE),
				measurementStartedAt: process.env.STARTED_AT_VALUE,
				measurementEndedAt: process.env.ENDED_AT_VALUE,
				samplingIntervalSeconds: Number(process.env.SAMPLING_INTERVAL_VALUE),
				samplingMaxGapSeconds: Number(process.env.SAMPLING_MAX_GAP_VALUE),
				warmupVus: Number(process.env.WARMUP_VUS_VALUE),
				warmupDuration: process.env.WARMUP_DURATION_VALUE,
				vus: Number(process.env.VUS_VALUE),
				duration: process.env.DURATION_VALUE,
			},
		};
		fs.writeFileSync(process.argv[1], `${JSON.stringify(metadata, null, 2)}\n`);
	' "${metadata_file}"

	set +e
	node "${ROOT_DIR}/summarize-run.mjs" \
		"${endpoint}" "${summary_file}" "${before_file}" "${after_file}" \
		"${sql_log_file}" "${resource_file}" "${integrity_file}" "${metadata_file}" "${report_file}"
	summarize_status=$?
	set -e
	if (( summarize_status != 0 )); then
		echo "Evidence report is non-adoptable for ${mode}/${endpoint}; see ${report_file}." >&2
		return "${summarize_status}"
	fi
	if (( k6_status != 0 )); then
		echo "k6 failed for ${mode}/${endpoint}; failure evidence was preserved in ${endpoint_dir}." >&2
		return "${k6_status}"
	fi
	if (( sampler_status != 0 )); then
		echo "Resource sampler failed for ${mode}/${endpoint}; report was rejected." >&2
		return "${sampler_status}"
	fi
	if (( integrity_status != 0 )); then
		echo "Runtime integrity sampler failed for ${mode}/${endpoint}; report was rejected." >&2
		return "${integrity_status}"
	fi
	if (( runtime_continuity_status != 0 )); then
		echo "Runtime identity changed during ${mode}/${endpoint}; report was rejected." >&2
		return "${runtime_continuity_status}"
	fi
	if (( fixture_window_status != 0 )); then
		echo "Fixture visibility windows crossed a boundary during ${mode}/${endpoint}; report was rejected." >&2
		return "${fixture_window_status}"
	fi
	if (( log_capture_status != 0 )); then
		echo "Docker log capture failed for ${mode}/${endpoint}; report was rejected." >&2
		return "${log_capture_status}"
	fi
	if (( after_snapshot_status != 0 )); then
		echo "After-run DB snapshot failed for ${mode}/${endpoint}; report was rejected." >&2
		return "${after_snapshot_status}"
	fi
}

if [[ "${REQUESTED_MODE}" == "all" ]]; then
	modes=("${MODE_SEQUENCE[@]}")
else
	case " ${MODE_SEQUENCE[*]} " in
		*" ${REQUESTED_MODE} "*) modes=("${REQUESTED_MODE}") ;;
		*) echo "Mode must be all, prayer, poll-member, or poll-admin." >&2; exit 1 ;;
	esac
fi

for mode in "${modes[@]}"; do
	for endpoint in $(endpoints_for_mode "${mode}"); do
		ENDPOINT="${endpoint}" run_endpoint "${mode}" "${endpoint}"
	done
done

echo "Issue #196 baseline finished requested mode=${REQUESTED_MODE}. Reports: ${REPORT_ROOT}"
