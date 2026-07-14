#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${ROOT_DIR}/../../.." && pwd)"
SCENARIO_FILE="${ROOT_DIR}/scenario.js"
DB_STATS_SQL="${ROOT_DIR}/db-table-stats.sql"
FIXTURE_RUN_ID="${FIXTURE_RUN_ID:?FIXTURE_RUN_ID is required}"
FIXTURE_MANIFEST="${FIXTURE_MANIFEST:-${REPO_ROOT}/build/reports/k6/issue-196/${FIXTURE_RUN_ID}/fixture-manifest.json}"
REPORT_ROOT="${REPORT_ROOT:-${REPO_ROOT}/build/reports/k6/issue-196/${FIXTURE_RUN_ID}}"
BASE_URL="${BASE_URL:-http://localhost:8080}"
VUS="${VUS:?VUS must be explicitly approved and supplied}"
DURATION="${DURATION:?DURATION must be explicitly approved and supplied}"
MAX_FAILURE_RATE="${MAX_FAILURE_RATE:-0.01}"
PERF_ADMIN_EMAIL="${PERF_ADMIN_EMAIL:?PERF_ADMIN_EMAIL is required at runtime}"
PERF_ADMIN_PASSWORD="${PERF_ADMIN_PASSWORD:?PERF_ADMIN_PASSWORD is required at runtime}"
PERF_MEMBER_PASSWORD="${PERF_MEMBER_PASSWORD:?PERF_MEMBER_PASSWORD is required at runtime}"
PERF_DB_USER="${PERF_DB_USER:?PERF_DB_USER is required at runtime}"
PERF_DB_NAME="${PERF_DB_NAME:?PERF_DB_NAME is required at runtime}"
PERF_DB_PASSWORD="${PERF_DB_PASSWORD:?PERF_DB_PASSWORD is required at runtime}"
APP_CONTAINER="${APP_CONTAINER:-faithlog-backend}"
DB_CONTAINER="${DB_CONTAINER:-faithlog-postgres}"
EXPECTED_APP_IMAGE="${EXPECTED_APP_IMAGE:-faithlog-latest}"
PERF_GLOBAL_LOCK="${PERF_GLOBAL_LOCK:-/tmp/faithlog-performance-global.lock}"
SQL_LOG_MARKER="org.hibernate.SQL"
REQUESTED_MODE="${1:-all}"
MODE_SEQUENCE=(prayer poll-member poll-admin)

for command in node k6 docker; do
	command -v "${command}" >/dev/null || { echo "Missing command: ${command}" >&2; exit 1; }
done
[[ -f "${FIXTURE_MANIFEST}" ]] || { echo "Fixture manifest not found: ${FIXTURE_MANIFEST}" >&2; exit 1; }
[[ "${BASE_URL}" =~ ^https?://(localhost|127\.0\.0\.1|\[::1\]|host\.docker\.internal|faithlog-backend|app)(:[0-9]+)?$ ]] \
	|| { echo "Issue #196 baseline is local-Docker-only." >&2; exit 1; }

if ! mkdir "${PERF_GLOBAL_LOCK}" 2>/dev/null; then
	echo "Another performance seed or load run owns ${PERF_GLOBAL_LOCK}. Parallel load is forbidden." >&2
	exit 1
fi
trap 'rmdir "${PERF_GLOBAL_LOCK}" 2>/dev/null || true' EXIT

if pgrep -f '[k]6 run' >/dev/null 2>&1; then
	echo "Another k6 process is running outside the global lock. Refusing parallel load." >&2
	exit 1
fi

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

[[ -n "${compose_project}" && "${compose_project}" == "${db_project}" ]] \
	|| { echo "App/Postgres Compose project labels are missing or different." >&2; exit 1; }
[[ "${app_service}" == "app" && "${db_service}" == "postgres" ]] \
	|| { echo "Unexpected Compose service labels: app=${app_service}, db=${db_service}." >&2; exit 1; }
[[ -n "${app_config_hash}" && -n "${db_config_hash}" ]] \
	|| { echo "Compose config-hash labels must be present on app and PostgreSQL." >&2; exit 1; }
[[ "${app_image}" == "${EXPECTED_APP_IMAGE}" || "${app_image}" == "${EXPECTED_APP_IMAGE}:"* ]] \
	|| { echo "Expected app image ${EXPECTED_APP_IMAGE}, actual ${app_image}." >&2; exit 1; }

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
	docker exec -e PGPASSWORD="${PERF_DB_PASSWORD}" "${DB_CONTAINER}" \
		psql -X -v ON_ERROR_STOP=1 -U "${PERF_DB_USER}" -d "${PERF_DB_NAME}" -At -f - \
		< "${DB_STATS_SQL}" > "${output}"
}

sample_resources() {
	local k6_pid="$1"
	local output="$2"
	while kill -0 "${k6_pid}" 2>/dev/null; do
		local captured_at
		captured_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
		docker stats --no-stream --format '{{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}\t{{.MemPerc}}' \
			"${APP_CONTAINER}" "${DB_CONTAINER}" \
			| while IFS= read -r sample; do printf '%s\t%s\n' "${captured_at}" "${sample}"; done \
			>> "${output}"
		sleep 1
	done
}

endpoints_for_mode() {
	case "$1" in
		prayer)
			echo "prayer_current_season prayer_groups prayer_assignable prayer_weekly_board_admin prayer_weekly_board_member"
			;;
		poll-member)
			echo "poll_member_list poll_member_detail poll_member_results poll_member_comments poll_member_cross_campus_detail"
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
	local summary_file="${endpoint_dir}/k6-summary.json"
	local before_file="${endpoint_dir}/db-before.json"
	local after_file="${endpoint_dir}/db-after.json"
	local sql_log_file="${endpoint_dir}/hibernate-sql.log"
	local resource_file="${endpoint_dir}/resource-samples.tsv"
	local metadata_file="${endpoint_dir}/runtime-metadata.json"
	local report_file="${endpoint_dir}/report.json"
	local admin_token
	local member_token
	local log_since
	local log_until
	local k6_pid
	local sampler_pid

	mkdir -p "${endpoint_dir}"
	admin_token="$(login_token "${PERF_ADMIN_EMAIL}" "${PERF_ADMIN_PASSWORD}")"
	member_token="$(login_token "${member_email}" "${PERF_MEMBER_PASSWORD}")"
	snapshot_db_tables "${before_file}"
	log_since="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
	: > "${resource_file}"

	BASE_URL="${BASE_URL}" \
	MODE="${mode}" \
	ENDPOINT="${endpoint}" \
	VUS="${VUS}" \
	DURATION="${DURATION}" \
	MAX_FAILURE_RATE="${MAX_FAILURE_RATE}" \
	FIXTURE_MANIFEST="${FIXTURE_MANIFEST}" \
	PERF_ADMIN_EMAIL="${PERF_ADMIN_EMAIL}" \
	PERF_ADMIN_PASSWORD="${PERF_ADMIN_PASSWORD}" \
	PERF_MEMBER_PASSWORD="${PERF_MEMBER_PASSWORD}" \
	PERF_ADMIN_ACCESS_TOKEN="${admin_token}" \
	PERF_MEMBER_ACCESS_TOKEN="${member_token}" \
		k6 run --summary-export "${summary_file}" "${SCENARIO_FILE}" &
	k6_pid=$!
	sample_resources "${k6_pid}" "${resource_file}" &
	sampler_pid=$!
	if ! wait "${k6_pid}"; then
		wait "${sampler_pid}" || true
		echo "k6 failed for ${mode}/${endpoint}." >&2
		exit 1
	fi
	wait "${sampler_pid}" || true
	log_until="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
	docker logs --since "${log_since}" --until "${log_until}" "${APP_CONTAINER}" > "${sql_log_file}" 2>&1
	grep -q "${SQL_LOG_MARKER}" "${sql_log_file}" \
		|| { echo "No org.hibernate.SQL evidence captured for ${endpoint}." >&2; exit 1; }
	snapshot_db_tables "${after_file}"

	MODE_VALUE="${mode}" ENDPOINT_VALUE="${endpoint}" DATASET_VALUE="${dataset_id}" \
	FIXTURE_VALUE="${FIXTURE_RUN_ID}" PROJECT_VALUE="${compose_project}" \
	APP_SERVICE_VALUE="${app_service}" DB_SERVICE_VALUE="${db_service}" \
	APP_HASH_VALUE="${app_config_hash}" DB_HASH_VALUE="${db_config_hash}" \
	APP_IMAGE_VALUE="${app_image}" EXPECTED_IMAGE_VALUE="${EXPECTED_APP_IMAGE}" \
	APP_CONTAINER_VALUE="${APP_CONTAINER}" DB_CONTAINER_VALUE="${DB_CONTAINER}" \
	VUS_VALUE="${VUS}" DURATION_VALUE="${DURATION}" \
	node -e '
		const fs = require("node:fs");
		const metadata = {
			capturedAt: new Date().toISOString(),
			mode: process.env.MODE_VALUE,
			endpoint: process.env.ENDPOINT_VALUE,
			datasetId: process.env.DATASET_VALUE,
			fixtureRunId: process.env.FIXTURE_VALUE,
			runtime: {
				composeProject: process.env.PROJECT_VALUE,
				appServiceLabel: process.env.APP_SERVICE_VALUE,
				dbServiceLabel: process.env.DB_SERVICE_VALUE,
				appConfigHash: process.env.APP_HASH_VALUE,
				dbConfigHash: process.env.DB_HASH_VALUE,
				appImage: process.env.APP_IMAGE_VALUE,
				expectedAppImage: process.env.EXPECTED_IMAGE_VALUE,
				appContainer: process.env.APP_CONTAINER_VALUE,
				dbContainer: process.env.DB_CONTAINER_VALUE,
				vus: Number(process.env.VUS_VALUE),
				duration: process.env.DURATION_VALUE,
			},
		};
		fs.writeFileSync(process.argv[1], `${JSON.stringify(metadata, null, 2)}\n`);
	' "${metadata_file}"

	node "${ROOT_DIR}/summarize-run.mjs" \
		"${endpoint}" "${summary_file}" "${before_file}" "${after_file}" \
		"${sql_log_file}" "${resource_file}" "${metadata_file}" "${report_file}"
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

echo "Issue #196 baseline finished sequentially: prayer -> poll-member -> poll-admin. Reports: ${REPORT_ROOT}"
