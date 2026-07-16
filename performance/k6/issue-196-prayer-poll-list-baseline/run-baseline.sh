#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${ROOT_DIR}/../../.." && pwd)"
SCENARIO_FILE="${ROOT_DIR}/scenario.js"
DB_STATS_SQL="${ROOT_DIR}/db-table-stats.sql"
DB_ACTIVITY_SQL="${ROOT_DIR}/db-activity.sql"
DB_QUIESCENCE_SQL="${ROOT_DIR}/db-quiescence.sql"
VALIDATE_DB_QUIESCENCE="${ROOT_DIR}/db-quiescence.mjs"
VALIDATE_TARGET="${ROOT_DIR}/validate-published-target.mjs"
DB_RUNTIME_IDENTITY_SQL="${ROOT_DIR}/db-runtime-identity.sql"
VALIDATE_RUNTIME_IDENTITY="${ROOT_DIR}/validate-runtime-identity.mjs"
PARSE_REDIS_IDENTITY="${ROOT_DIR}/redis-runtime-identity.mjs"
FILTER_SQL_LOG="${ROOT_DIR}/filter-sql-log.mjs"
TOOLING_PROVENANCE="${ROOT_DIR}/tooling-provenance.mjs"
RESOURCE_WINDOW_SAMPLER="${ROOT_DIR}/resource-window-sampler.mjs"
PERF_SCENARIO_WORKTREE="${PERF_SCENARIO_WORKTREE:?PERF_SCENARIO_WORKTREE is required at runtime}"
EXPECTED_SCENARIO_HEAD="${EXPECTED_SCENARIO_HEAD:?EXPECTED_SCENARIO_HEAD is required at runtime}"
FIXTURE_RUN_ID="${FIXTURE_RUN_ID:?FIXTURE_RUN_ID is required}"
EXECUTION_RUN_ID="${EXECUTION_RUN_ID:?EXECUTION_RUN_ID is required}"
FIXTURE_MANIFEST="${FIXTURE_MANIFEST:-${REPO_ROOT}/build/reports/k6/issue-196/${FIXTURE_RUN_ID}/fixture-manifest.json}"
PERF_REPORT_ROOT="${PERF_REPORT_ROOT:-${REPO_ROOT}/build/reports/k6/issue-196}"
REPORT_ROOT="${PERF_REPORT_ROOT}/${FIXTURE_RUN_ID}/${EXECUTION_RUN_ID}"
BASE_URL="${BASE_URL:?BASE_URL is required at runtime}"
WARMUP_VUS="${WARMUP_VUS:?WARMUP_VUS must be explicitly approved and supplied}"
WARMUP_DURATION="${WARMUP_DURATION:?WARMUP_DURATION must be explicitly approved and supplied}"
MEASURED_VUS="${MEASURED_VUS:?MEASURED_VUS must be explicitly approved and supplied}"
MEASURED_DURATION="${MEASURED_DURATION:?MEASURED_DURATION must be explicitly approved and supplied}"
SAMPLING_INTERVAL_SECONDS="${SAMPLING_INTERVAL_SECONDS:?SAMPLING_INTERVAL_SECONDS requires explicit user approval}"
SAMPLING_MAX_GAP_SECONDS="${SAMPLING_MAX_GAP_SECONDS:?SAMPLING_MAX_GAP_SECONDS requires explicit user approval}"
PERF_MAINTENANCE_QUIET_SECONDS="${PERF_MAINTENANCE_QUIET_SECONDS:?PERF_MAINTENANCE_QUIET_SECONDS is required at runtime}"
PERF_QUIESCENCE_TIMEOUT_SECONDS="${PERF_QUIESCENCE_TIMEOUT_SECONDS:?PERF_QUIESCENCE_TIMEOUT_SECONDS is required at runtime}"
PERF_ADMIN_EMAIL="${PERF_ADMIN_EMAIL:?PERF_ADMIN_EMAIL is required at runtime}"
PERF_ADMIN_PASSWORD="${PERF_ADMIN_PASSWORD:?PERF_ADMIN_PASSWORD is required at runtime}"
PERF_MEMBER_PASSWORD="${PERF_MEMBER_PASSWORD:?PERF_MEMBER_PASSWORD is required at runtime}"
PERF_DB_USER="${PERF_DB_USER:?PERF_DB_USER is required at runtime}"
PERF_DB_NAME="${PERF_DB_NAME:?PERF_DB_NAME is required at runtime}"
PERF_DB_PASSWORD="${PERF_DB_PASSWORD:?PERF_DB_PASSWORD is required at runtime}"
APP_CONTAINER="${APP_CONTAINER:?APP_CONTAINER is required at runtime}"
DB_CONTAINER="${DB_CONTAINER:?DB_CONTAINER is required at runtime}"
REDIS_CONTAINER="${REDIS_CONTAINER:?REDIS_CONTAINER is required at runtime}"
EXPECTED_APP_SERVICE="${EXPECTED_APP_SERVICE:?EXPECTED_APP_SERVICE is required at runtime}"
EXPECTED_DB_SERVICE="${EXPECTED_DB_SERVICE:?EXPECTED_DB_SERVICE is required at runtime}"
EXPECTED_REDIS_SERVICE="${EXPECTED_REDIS_SERVICE:?EXPECTED_REDIS_SERVICE is required at runtime}"
EXPECTED_APP_IMAGE="${EXPECTED_APP_IMAGE:?EXPECTED_APP_IMAGE is required at runtime}"
EXPECTED_APP_IMAGE_ID="${EXPECTED_APP_IMAGE_ID:?EXPECTED_APP_IMAGE_ID is required at runtime}"
EXPECTED_DB_IMAGE="${EXPECTED_DB_IMAGE:?EXPECTED_DB_IMAGE is required at runtime}"
EXPECTED_DB_IMAGE_ID="${EXPECTED_DB_IMAGE_ID:?EXPECTED_DB_IMAGE_ID is required at runtime}"
EXPECTED_REDIS_IMAGE="${EXPECTED_REDIS_IMAGE:?EXPECTED_REDIS_IMAGE is required at runtime}"
EXPECTED_REDIS_IMAGE_ID="${EXPECTED_REDIS_IMAGE_ID:?EXPECTED_REDIS_IMAGE_ID is required at runtime}"
EXPECTED_REDIS_PORT="${EXPECTED_REDIS_PORT:?EXPECTED_REDIS_PORT is required at runtime}"
EXPECTED_FLYWAY_VERSION="${EXPECTED_FLYWAY_VERSION:?EXPECTED_FLYWAY_VERSION is required at runtime}"
EXPECTED_SOURCE_REVISION="${EXPECTED_SOURCE_REVISION:?EXPECTED_SOURCE_REVISION is required at runtime}"
SQL_LOG_MARKER="org.hibernate.SQL"
REQUESTED_MODE="${1:?Mode is required: all, prayer, poll-member, poll-admin, or poll-duty}"
MODE_SEQUENCE=(prayer poll-member poll-admin poll-duty)
overall_non_adoptable=0

export -n PERF_ADMIN_EMAIL PERF_ADMIN_PASSWORD PERF_MEMBER_PASSWORD PERF_DB_USER PERF_DB_NAME PERF_DB_PASSWORD
unset PERF_ACCESS_TOKEN PERF_ADMIN_ACCESS_TOKEN PERF_MEMBER_ACCESS_TOKEN \
	PERF_COFFEE_CREATOR_ACCESS_TOKEN PERF_OTHER_COFFEE_DUTY_ACCESS_TOKEN PERF_MEAL_DUTY_ACCESS_TOKEN

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
SAMPLING_INTERVAL_VALUE="${SAMPLING_INTERVAL_SECONDS}" SAMPLING_MAX_GAP_VALUE="${SAMPLING_MAX_GAP_SECONDS}" \
	MAINTENANCE_QUIET_VALUE="${PERF_MAINTENANCE_QUIET_SECONDS}" \
	QUIESCENCE_TIMEOUT_VALUE="${PERF_QUIESCENCE_TIMEOUT_SECONDS}" node -e '
	const interval = Number(process.env.SAMPLING_INTERVAL_VALUE);
	const maxGap = Number(process.env.SAMPLING_MAX_GAP_VALUE);
	const quiet = Number(process.env.MAINTENANCE_QUIET_VALUE);
	const timeout = Number(process.env.QUIESCENCE_TIMEOUT_VALUE);
	if (!Number.isFinite(interval) || interval <= 0 || !Number.isFinite(maxGap) || maxGap < interval
		|| quiet !== 30 || timeout !== 180) process.exit(1);
' || { echo "Sampling values or the approved 30/180 maintenance quiet contract are invalid." >&2; exit 1; }
[[ -f "${FIXTURE_MANIFEST}" ]] || { echo "Fixture manifest not found: ${FIXTURE_MANIFEST}" >&2; exit 1; }
node "${TOOLING_PROVENANCE}" --assert-manifest "${PERF_SCENARIO_WORKTREE}" "${EXPECTED_SCENARIO_HEAD}" "${FIXTURE_MANIFEST}"
[[ "${BASE_URL}" =~ ^http://(127\.0\.0\.1|\[::1\])(:[0-9]+)?$ ]] \
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
coffee_creator_email="$(manifest_value primaryCampus.coffeeCreator.email)"
other_coffee_duty_email="$(manifest_value primaryCampus.otherCoffeeDuty.email)"
meal_duty_email="$(manifest_value primaryCampus.mealDuty.email)"
shaped_at="$(manifest_value shapedAt)"
[[ "${manifest_run_id}" == "${FIXTURE_RUN_ID}" ]] || { echo "fixtureRunId mismatch." >&2; exit 1; }
[[ "${dataset_id}" == "issue-196-prayer-poll-list-v2" ]] || { echo "Unexpected datasetId=${dataset_id}." >&2; exit 1; }
[[ "${shaped_at}" != "null" ]] || { echo "Fixture has not been shaped." >&2; exit 1; }

assert_fixture_windows() {
	node -e '
	const fs = require("node:fs");
	const manifest = JSON.parse(fs.readFileSync(process.argv[1], "utf8"));
	const now = Date.now();
	const poll = manifest.polls.byKey;
	const duty = manifest.polls.duty;
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
	if (!(instant(duty.coffee.startsAt) <= now && now < instant(duty.coffee.endsAt))) throw new Error("COFFEE duty fixture window is stale.");
	if (!(instant(duty.mealOpen.startsAt) <= now && now < instant(duty.mealOpen.endsAt))) throw new Error("MEAL duty fixture window is stale.");
	if (!(now > instant(duty.mealArchived.endsAt) + 90 * 86400000)) throw new Error("MEAL archive fixture is not beyond 90 days.");
' "${FIXTURE_MANIFEST}"
}

assert_fixture_windows

label() {
	docker inspect --format "{{ index .Config.Labels \"$2\" }}" "$1"
}

capture_database_identity() {
	local raw
	raw="$(PGPASSWORD="${PERF_DB_PASSWORD}" docker exec -i -e PGPASSWORD -e PGAPPNAME=faithlog_issue196_observer "${DB_CONTAINER}" \
		psql -X -v ON_ERROR_STOP=1 -h 127.0.0.1 -U "${PERF_DB_USER}" -d "${PERF_DB_NAME}" -At \
		-f - < "${DB_RUNTIME_IDENTITY_SQL}")"
	DB_RUNTIME_IDENTITY_JSON="${raw}" EXPECTED_FLYWAY_VERSION="${EXPECTED_FLYWAY_VERSION}" node "${VALIDATE_RUNTIME_IDENTITY}"
}

capture_redis_identity() {
	local info
	info="$(docker exec "${REDIS_CONTAINER}" redis-cli --raw INFO server)"
	REDIS_INFO_TEXT="${info}" EXPECTED_REDIS_PORT="${EXPECTED_REDIS_PORT}" node "${PARSE_REDIS_IDENTITY}"
}

app_container_id="$(docker inspect --format '{{.Id}}' "${APP_CONTAINER}")"
app_container_started_at="$(docker inspect --format '{{.State.StartedAt}}' "${APP_CONTAINER}")"
db_container_id="$(docker inspect --format '{{.Id}}' "${DB_CONTAINER}")"
db_image_id="$(docker inspect --format '{{.Image}}' "${DB_CONTAINER}")"
db_container_started_at="$(docker inspect --format '{{.State.StartedAt}}' "${DB_CONTAINER}")"
redis_container_id="$(docker inspect --format '{{.Id}}' "${REDIS_CONTAINER}")"
redis_image_id="$(docker inspect --format '{{.Image}}' "${REDIS_CONTAINER}")"
redis_container_started_at="$(docker inspect --format '{{.State.StartedAt}}' "${REDIS_CONTAINER}")"
compose_project="$(label "${APP_CONTAINER}" com.docker.compose.project)"
app_service="$(label "${APP_CONTAINER}" com.docker.compose.service)"
db_project="$(label "${DB_CONTAINER}" com.docker.compose.project)"
db_service="$(label "${DB_CONTAINER}" com.docker.compose.service)"
redis_project="$(label "${REDIS_CONTAINER}" com.docker.compose.project)"
redis_service="$(label "${REDIS_CONTAINER}" com.docker.compose.service)"
app_config_hash="$(label "${APP_CONTAINER}" com.docker.compose.config-hash)"
db_config_hash="$(label "${DB_CONTAINER}" com.docker.compose.config-hash)"
redis_config_hash="$(label "${REDIS_CONTAINER}" com.docker.compose.config-hash)"
app_image="$(docker inspect --format '{{.Config.Image}}' "${APP_CONTAINER}")"
app_image_id="$(docker inspect --format '{{.Image}}' "${APP_CONTAINER}")"
db_image="$(docker inspect --format '{{.Config.Image}}' "${DB_CONTAINER}")"
redis_image="$(docker inspect --format '{{.Config.Image}}' "${REDIS_CONTAINER}")"
app_client_addrs="$(docker inspect --format '{{range .NetworkSettings.Networks}}{{println .IPAddress}}{{end}}' "${APP_CONTAINER}")"
app_client_addrs="${app_client_addrs//$'\n'/,}"
app_client_addrs="${app_client_addrs%,}"
database_identity="$(capture_database_identity)"
redis_identity="$(capture_redis_identity)"
seed_project="$(manifest_value composeRuntime.composeProject)"
seed_app_hash="$(manifest_value composeRuntime.appConfigHash)"
seed_db_hash="$(manifest_value composeRuntime.dbConfigHash)"
seed_redis_hash="$(manifest_value composeRuntime.redisConfigHash)"
seed_app_image_id="$(manifest_value composeRuntime.appImageId)"
seed_db_image_id="$(manifest_value composeRuntime.dbImageId)"
seed_redis_image_id="$(manifest_value composeRuntime.redisImageId)"
seed_app_container_id="$(manifest_value composeRuntime.appContainerId)"
seed_app_started_at="$(manifest_value composeRuntime.appContainerStartedAt)"
seed_db_container_id="$(manifest_value composeRuntime.dbContainerId)"
seed_db_started_at="$(manifest_value composeRuntime.dbContainerStartedAt)"
seed_redis_container_id="$(manifest_value composeRuntime.redisContainerId)"
seed_redis_started_at="$(manifest_value composeRuntime.redisContainerStartedAt)"
seed_source_revision="$(manifest_value composeRuntime.sourceRevision)"
seed_target_port="$(manifest_value composeRuntime.targetPort)"
published_ports="$(docker port "${APP_CONTAINER}" 8080/tcp)"
base_target_port="$(BASE_URL_VALUE="${BASE_URL}" PUBLISHED_BINDINGS_VALUE="${published_ports}" \
	node "${VALIDATE_TARGET}" --host-port)" \
	|| { echo "BASE_URL and the app container published binding do not identify one approved numeric loopback target." >&2; exit 1; }

[[ -n "${compose_project}" && "${compose_project}" == "${db_project}" && "${compose_project}" == "${redis_project}" ]] \
	|| { echo "App/Postgres/Redis Compose project labels are missing or different." >&2; exit 1; }
[[ "${compose_project}" =~ ^[a-z0-9][a-z0-9_-]*$ ]] \
	|| { echo "Compose project label cannot be represented by the canonical lock path." >&2; exit 1; }
[[ "${app_service}" == "${EXPECTED_APP_SERVICE}" && "${db_service}" == "${EXPECTED_DB_SERVICE}" \
	&& "${redis_service}" == "${EXPECTED_REDIS_SERVICE}" ]] \
	|| { echo "Unexpected Compose service labels: app=${app_service}, db=${db_service}, redis=${redis_service}." >&2; exit 1; }
[[ -n "${app_config_hash}" && -n "${db_config_hash}" && -n "${redis_config_hash}" ]] \
	|| { echo "Compose config-hash labels must be present on app, PostgreSQL, and Redis." >&2; exit 1; }
[[ "${app_image}" == "${EXPECTED_APP_IMAGE}" && "${app_image_id}" == "${EXPECTED_APP_IMAGE_ID}" \
	&& "${db_image}" == "${EXPECTED_DB_IMAGE}" && "${db_image_id}" == "${EXPECTED_DB_IMAGE_ID}" \
	&& "${redis_image}" == "${EXPECTED_REDIS_IMAGE}" && "${redis_image_id}" == "${EXPECTED_REDIS_IMAGE_ID}" ]] \
	|| { echo "immutable-app-image-mismatch: approved app/PostgreSQL/Redis image identity differs." >&2; exit 1; }
[[ "${compose_project}" == "${seed_project}" && "${app_config_hash}" == "${seed_app_hash}" \
	&& "${db_config_hash}" == "${seed_db_hash}" && "${redis_config_hash}" == "${seed_redis_hash}" ]] \
	|| { echo "Current Compose identity differs from the seed manifest." >&2; exit 1; }
[[ "${app_image_id}" == "${seed_app_image_id}" && "${db_image_id}" == "${seed_db_image_id}" \
	&& "${redis_image_id}" == "${seed_redis_image_id}" && "${EXPECTED_SOURCE_REVISION}" == "${seed_source_revision}" ]] \
	|| { echo "Current immutable runtime/source identity differs from the seed manifest." >&2; exit 1; }
[[ "${app_container_id}" == "${seed_app_container_id}" && "${app_container_started_at}" == "${seed_app_started_at}" \
	&& "${db_container_id}" == "${seed_db_container_id}" && "${db_container_started_at}" == "${seed_db_started_at}" \
	&& "${redis_container_id}" == "${seed_redis_container_id}" && "${redis_container_started_at}" == "${seed_redis_started_at}" ]] \
	|| { echo "Current full container identity differs from the seed manifest." >&2; exit 1; }
[[ "${base_target_port}" == "${seed_target_port}" ]] \
	|| { echo "BASE_URL port ${base_target_port} differs from the seed target port ${seed_target_port}." >&2; exit 1; }
[[ "${app_client_addrs}" =~ ^[0-9a-fA-F:.]+(,[0-9a-fA-F:.]+)*$ ]] \
	|| { echo "Attested app container network addresses are missing or invalid." >&2; exit 1; }

PERF_PROJECT_LOCK="/tmp/faithlog-performance-${compose_project}.lock"
if ! mkdir "${PERF_PROJECT_LOCK}" 2>/dev/null; then
	echo "Another seed or load run owns ${PERF_PROJECT_LOCK}. Parallel execution is forbidden." >&2
	exit 1
fi
credential_runtime_dir=''

cleanup_runtime_credentials() {
	if [[ -n "${credential_runtime_dir}" ]]; then
		rm -f -- "${credential_runtime_dir}"/*.json
		rmdir "${credential_runtime_dir}" 2>/dev/null || true
	fi
	rmdir "${PERF_PROJECT_LOCK}" 2>/dev/null || true
}

trap cleanup_runtime_credentials EXIT
trap 'exit 130' INT TERM

assert_runtime_continuity() {
	local current_database_identity
	local current_redis_identity
	local current_published_ports
	local current_target_port
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
	[[ "$(docker inspect --format '{{.Id}}' "${REDIS_CONTAINER}")" == "${redis_container_id}" ]] \
		|| { echo "Redis container ID changed during execution." >&2; return 1; }
	[[ "$(docker inspect --format '{{.Image}}' "${REDIS_CONTAINER}")" == "${redis_image_id}" ]] \
		|| { echo "Redis immutable image ID changed during execution." >&2; return 1; }
	[[ "$(docker inspect --format '{{.State.StartedAt}}' "${REDIS_CONTAINER}")" == "${redis_container_started_at}" ]] \
		|| { echo "Redis container start time changed during execution." >&2; return 1; }
	[[ "$(docker inspect --format '{{.Config.Image}}' "${APP_CONTAINER}")" == "${app_image}" ]] \
		|| { echo "App configured image changed during execution." >&2; return 1; }
	[[ "$(docker inspect --format '{{.Config.Image}}' "${DB_CONTAINER}")" == "${db_image}"
		&& "$(docker inspect --format '{{.Config.Image}}' "${REDIS_CONTAINER}")" == "${redis_image}" ]] \
		|| { echo "PostgreSQL/Redis configured image changed during execution." >&2; return 1; }
	[[ "$(label "${APP_CONTAINER}" com.docker.compose.project)" == "${compose_project}"
		&& "$(label "${DB_CONTAINER}" com.docker.compose.project)" == "${compose_project}"
		&& "$(label "${REDIS_CONTAINER}" com.docker.compose.project)" == "${compose_project}"
		&& "$(label "${APP_CONTAINER}" com.docker.compose.service)" == "${app_service}"
		&& "$(label "${DB_CONTAINER}" com.docker.compose.service)" == "${db_service}"
		&& "$(label "${REDIS_CONTAINER}" com.docker.compose.service)" == "${redis_service}"
		&& "$(label "${APP_CONTAINER}" com.docker.compose.config-hash)" == "${app_config_hash}"
		&& "$(label "${DB_CONTAINER}" com.docker.compose.config-hash)" == "${db_config_hash}"
		&& "$(label "${REDIS_CONTAINER}" com.docker.compose.config-hash)" == "${redis_config_hash}" ]] \
		|| { echo "Compose runtime labels changed during execution." >&2; return 1; }
	current_published_ports="$(docker port "${APP_CONTAINER}" 8080/tcp)"
	current_target_port="$(BASE_URL_VALUE="${BASE_URL}" PUBLISHED_BINDINGS_VALUE="${current_published_ports}" \
		node "${VALIDATE_TARGET}" --host-port)" \
		|| { echo "App published target became invalid during execution." >&2; return 1; }
	[[ "${current_target_port}" == "${base_target_port}" && "${current_published_ports}" == "${published_ports}" ]] \
		|| { echo "App published target changed during execution." >&2; return 1; }
	current_database_identity="$(capture_database_identity)"
	EXPECTED_DB_IDENTITY="${database_identity}" CURRENT_DB_IDENTITY="${current_database_identity}" node -e '
		const expected = JSON.parse(process.env.EXPECTED_DB_IDENTITY);
		const current = JSON.parse(process.env.CURRENT_DB_IDENTITY);
		if (JSON.stringify(expected) !== JSON.stringify(current)) process.exit(1);
	' || { echo "PostgreSQL runtime identity changed during execution." >&2; return 1; }
	current_redis_identity="$(capture_redis_identity)"
	EXPECTED_REDIS_IDENTITY="${redis_identity}" CURRENT_REDIS_IDENTITY="${current_redis_identity}" node -e '
		const expected = JSON.parse(process.env.EXPECTED_REDIS_IDENTITY);
		const current = JSON.parse(process.env.CURRENT_REDIS_IDENTITY);
		if (JSON.stringify(expected) !== JSON.stringify(current)) process.exit(1);
	' || { echo "Redis runtime identity changed during execution." >&2; return 1; }
}

if ! assert_runtime_continuity; then
	echo "Runtime identity changed after project lock." >&2
	exit 1
fi
node "${TOOLING_PROVENANCE}" --assert-manifest "${PERF_SCENARIO_WORKTREE}" "${EXPECTED_SCENARIO_HEAD}" "${FIXTURE_MANIFEST}"

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
credential_runtime_dir="$(mktemp -d /tmp/faithlog-196-credentials.XXXXXX)"
chmod 700 "${credential_runtime_dir}"

app_environment="$(docker inspect --format '{{range .Config.Env}}{{println .}}{{end}}' "${APP_CONTAINER}")"
spring_application_assignment="$(grep '^SPRING_APPLICATION_JSON=' <<<"${app_environment}")"
[[ "$(grep -c '^SPRING_APPLICATION_JSON=' <<<"${app_environment}")" == 1 ]] \
	|| { echo "Instrumented app must contain one exact Spring application JSON assignment." >&2; exit 1; }
SPRING_APPLICATION_JSON_VALUE="${spring_application_assignment#SPRING_APPLICATION_JSON=}" node -e '
	const parsed = JSON.parse(process.env.SPRING_APPLICATION_JSON_VALUE || "null");
	const expected = {
		logging: { level: {
			"org.hibernate.SQL": "DEBUG",
			"org.hibernate.orm.jdbc.bind": "OFF",
			"org.hibernate.orm.jdbc.extract": "OFF",
		} },
		spring: { jpa: { "show-sql": false, properties: { hibernate: { format_sql: false } } } },
	};
	if (JSON.stringify(parsed) !== JSON.stringify(expected)) process.exit(1);
' || { echo "Instrumented app exact-case statement-only SQL logger configuration is missing." >&2; exit 1; }
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

write_credentials_file() {
	local output="$1"
	local phase="$2"
	TOKEN_ADMIN="$3" TOKEN_MEMBER="$4" TOKEN_COFFEE_CREATOR="$5" \
		TOKEN_OTHER_COFFEE_DUTY="$6" TOKEN_MEAL_DUTY="$7" \
		CREDENTIALS_OUTPUT="${output}" CREDENTIALS_PHASE="${phase}" CREDENTIALS_FIXTURE_RUN_ID="${FIXTURE_RUN_ID}" \
		node -e '
			const fs = require("node:fs");
			const tokens = {
				admin: process.env.TOKEN_ADMIN,
				member: process.env.TOKEN_MEMBER,
				coffeeCreator: process.env.TOKEN_COFFEE_CREATOR,
				otherCoffeeDuty: process.env.TOKEN_OTHER_COFFEE_DUTY,
				mealDuty: process.env.TOKEN_MEAL_DUTY,
			};
			if (!Object.values(tokens).every((value) => typeof value === "string" && value.length > 0)) process.exit(1);
			fs.writeFileSync(process.env.CREDENTIALS_OUTPUT, `${JSON.stringify({
				schemaVersion: 1,
				fixtureRunId: process.env.CREDENTIALS_FIXTURE_RUN_ID,
				phase: process.env.CREDENTIALS_PHASE,
				tokens,
			})}\n`, { encoding: "utf8", flag: "wx", mode: 0o600 });
		'
	chmod 600 "${output}"
}

remove_credentials_file() {
	rm -f -- "$1"
}

snapshot_db_tables() {
	local output="$1"
	PGPASSWORD="${PERF_DB_PASSWORD}" docker exec -i -e PGPASSWORD "${DB_CONTAINER}" \
		psql -X -v ON_ERROR_STOP=1 -h 127.0.0.1 -U "${PERF_DB_USER}" -d "${PERF_DB_NAME}" -At -f - \
		< "${DB_STATS_SQL}" > "${output}"
}

capture_database_quiescence() {
	local output="$1"
	PGPASSWORD="${PERF_DB_PASSWORD}" docker exec -i -e PGPASSWORD -e PGAPPNAME=faithlog_issue196_quiescence "${DB_CONTAINER}" \
		psql -X -v ON_ERROR_STOP=1 -h 127.0.0.1 -U "${PERF_DB_USER}" -d "${PERF_DB_NAME}" -At -f - \
		< "${DB_QUIESCENCE_SQL}" >> "${output}"
}

wait_for_database_quiescence() {
	local output="$1"
	local status_file="$2"
	local status
	: > "${output}"
	while true; do
		capture_database_quiescence "${output}"
		set +e
		node "${VALIDATE_DB_QUIESCENCE}" "${output}" \
			"${SAMPLING_INTERVAL_SECONDS}" "${PERF_MAINTENANCE_QUIET_SECONDS}" "${PERF_QUIESCENCE_TIMEOUT_SECONDS}" \
			> "${status_file}"
		status=$?
		set -e
		if (( status == 0 )); then
			return 0
		fi
		if (( status != 2 )); then
			echo "Database write/maintenance state did not reach the approved quiet window." >&2
			return "${status}"
		fi
		sleep "${SAMPLING_INTERVAL_SECONDS}"
	done
}

capture_logger_probe() {
	local since="$1"
	local output="$2"
	local until
	local -a pipeline_status
	until="$(rfc3339_now)"
	set +e
	docker logs --since "${since}" --until "${until}" "${APP_CONTAINER}" 2>&1 \
		| node "${FILTER_SQL_LOG}" > "${output}"
	pipeline_status=("${PIPESTATUS[@]}")
	set -e
	if (( pipeline_status[0] != 0 || pipeline_status[1] != 0 )) || [[ ! -s "${output}" ]]; then
		echo "Exact-case Hibernate statement logger probe failed before the measured window." >&2
		return 1
	fi
}

validate_token_lifetime() {
	PERF_ACCESS_TOKEN="$1" PHASE_DURATION="$2" node "${ROOT_DIR}/token-lifetime.mjs"
}

sample_resources_snapshot() {
	local output="$1"
	local raw captured_at
	raw="$(docker stats --no-stream --no-trunc --format '{{.ID}}|{{.CPUPerc}}|{{.MemUsage}}|{{.MemPerc}}' \
		"${app_container_id}" "${db_container_id}" "${redis_container_id}")"
	captured_at="$(rfc3339_now)"
	printf '%s\n' "${raw}" | node "${RESOURCE_WINDOW_SAMPLER}" append-snapshot \
		"${output}" "${captured_at}" \
		"${APP_CONTAINER}" "${app_container_id}" \
		"${DB_CONTAINER}" "${db_container_id}" \
		"${REDIS_CONTAINER}" "${redis_container_id}"
}

sample_resources() {
	local output="$1"
	local stop_file="$2"
	set +o pipefail
	docker stats --no-trunc --format '{{.ID}}|{{.CPUPerc}}|{{.MemUsage}}|{{.MemPerc}}' \
		"${app_container_id}" "${db_container_id}" "${redis_container_id}" | \
		node "${RESOURCE_WINDOW_SAMPLER}" stream-samples \
			"${output}" "${stop_file}" "${SAMPLING_MAX_GAP_SECONDS}" \
			"${APP_CONTAINER}" "${app_container_id}" \
			"${DB_CONTAINER}" "${db_container_id}" \
			"${REDIS_CONTAINER}" "${redis_container_id}"
}

sample_runtime_integrity() {
	local k6_pid="$1"
	local output="$2"
	local next_tick_ms
	next_tick_ms="$(node -e 'process.stdout.write(String(Date.now()))')"
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
		db_activity="$(PGPASSWORD="${PERF_DB_PASSWORD}" docker exec -i -e PGPASSWORD -e PGAPPNAME=faithlog_issue196_observer "${DB_CONTAINER}" \
			psql -X -v ON_ERROR_STOP=1 -U "${PERF_DB_USER}" -d "${PERF_DB_NAME}" -At \
			-v app_client_addrs="${app_client_addrs}" -f - < "${DB_ACTIVITY_SQL}")"
		LSOF_TEXT="${lsof_text}" DB_ACTIVITY_JSON="${db_activity}" K6_PID="${k6_pid}" \
			node "${ROOT_DIR}/activity-sample.mjs" >> "${output}"
		next_tick_ms="$(CURRENT_TICK_MS="${next_tick_ms}" INTERVAL_SECONDS="${SAMPLING_INTERVAL_SECONDS}" node -e '
			process.stdout.write(String(Number(process.env.CURRENT_TICK_MS) + Number(process.env.INTERVAL_SECONDS) * 1000));
		')"
		sleep_until_tick "${next_tick_ms}"
	done
}

sleep_until_tick() {
	TARGET_TICK_MS="$1" node -e '
		const remaining = Math.max(0, Number(process.env.TARGET_TICK_MS) - Date.now());
		setTimeout(() => {}, remaining);
	'
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
		poll-duty)
			echo "poll_coffee_creator_list poll_other_coffee_duty_list poll_meal_duty_list poll_coffee_creator_detail poll_meal_duty_detail poll_meal_management_default poll_meal_management_archive poll_meal_management_forbidden"
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
	node "${TOOLING_PROVENANCE}" --assert-manifest "${PERF_SCENARIO_WORKTREE}" "${EXPECTED_SCENARIO_HEAD}" "${FIXTURE_MANIFEST}"
	local endpoint_dir="${REPORT_ROOT}/${mode}/${endpoint}"
	local warmup_summary_file="${endpoint_dir}/warmup-k6-summary.json"
	local summary_file="${endpoint_dir}/k6-summary.json"
	local before_file="${endpoint_dir}/db-before.json"
	local after_file="${endpoint_dir}/db-after.json"
	local quiescence_file="${endpoint_dir}/pre-measured-quiescence.jsonl"
	local quiescence_status_file="${endpoint_dir}/pre-measured-quiescence-status.json"
	local logger_probe_file="${endpoint_dir}/logger-probe.sql"
	local sql_log_file="${endpoint_dir}/hibernate-sql.log"
	local resource_file="${endpoint_dir}/resource-samples.tsv"
	local resource_stop_file="${endpoint_dir}/resource-sampler.stop"
	local integrity_file="${endpoint_dir}/runtime-integrity.jsonl"
	local metadata_file="${endpoint_dir}/runtime-metadata.json"
	local report_file="${endpoint_dir}/report.json"
	local warmup_credentials_file="${credential_runtime_dir}/${mode}-${endpoint}-warmup.json"
	local measured_credentials_file="${credential_runtime_dir}/${mode}-${endpoint}-measured.json"
	local admin_token
	local member_token
	local coffee_creator_token
	local other_coffee_duty_token
	local meal_duty_token
	local warmup_status
	local log_since
	local log_until
	local logger_probe_since
	local k6_pid
	local sampler_pid
	local integrity_pid
	local k6_status
	local sampler_status
	local integrity_status
	local fixture_window_status
	local log_capture_status
	local -a log_pipeline_status
	local after_snapshot_status
	local runtime_continuity_status
	local final_continuity_status
	local summarize_status
	local measurement_status

	mkdir -p "$(dirname "${endpoint_dir}")"
	if ! mkdir "${endpoint_dir}"; then
		echo "Endpoint report directory exists. Refusing to overwrite: ${endpoint_dir}" >&2
		return 1
	fi
	assert_fixture_windows
	assert_runtime_continuity

	admin_token="$(login_token "${PERF_ADMIN_EMAIL}" "${PERF_ADMIN_PASSWORD}")"
	member_token="$(login_token "${member_email}" "${PERF_MEMBER_PASSWORD}")"
	coffee_creator_token="$(login_token "${coffee_creator_email}" "${PERF_MEMBER_PASSWORD}")"
	other_coffee_duty_token="$(login_token "${other_coffee_duty_email}" "${PERF_MEMBER_PASSWORD}")"
	meal_duty_token="$(login_token "${meal_duty_email}" "${PERF_MEMBER_PASSWORD}")"
	validate_token_lifetime "${admin_token}" "${WARMUP_DURATION}"
	validate_token_lifetime "${member_token}" "${WARMUP_DURATION}"
	validate_token_lifetime "${coffee_creator_token}" "${WARMUP_DURATION}"
	validate_token_lifetime "${other_coffee_duty_token}" "${WARMUP_DURATION}"
	validate_token_lifetime "${meal_duty_token}" "${WARMUP_DURATION}"
	write_credentials_file "${warmup_credentials_file}" warmup \
		"${admin_token}" "${member_token}" "${coffee_creator_token}" "${other_coffee_duty_token}" "${meal_duty_token}"
	admin_token=''
	member_token=''
	coffee_creator_token=''
	other_coffee_duty_token=''
	meal_duty_token=''
	set +e
	env -u PERF_ADMIN_EMAIL -u PERF_ADMIN_PASSWORD -u PERF_MEMBER_PASSWORD \
	-u PERF_DB_USER -u PERF_DB_NAME -u PERF_DB_PASSWORD \
	BASE_URL="${BASE_URL}" MODE="${mode}" ENDPOINT="${endpoint}" \
	VUS="${WARMUP_VUS}" DURATION="${WARMUP_DURATION}" FIXTURE_MANIFEST="${FIXTURE_MANIFEST}" \
	EXPECTED_SOURCE_REVISION="${EXPECTED_SOURCE_REVISION}" EXPECTED_APP_SERVICE="${EXPECTED_APP_SERVICE}" \
	EXPECTED_DB_SERVICE="${EXPECTED_DB_SERVICE}" EXPECTED_REDIS_SERVICE="${EXPECTED_REDIS_SERVICE}" \
	EXPECTED_APP_IMAGE="${EXPECTED_APP_IMAGE}" EXPECTED_APP_IMAGE_ID="${EXPECTED_APP_IMAGE_ID}" \
	EXPECTED_DB_IMAGE="${EXPECTED_DB_IMAGE}" EXPECTED_DB_IMAGE_ID="${EXPECTED_DB_IMAGE_ID}" \
	EXPECTED_REDIS_IMAGE="${EXPECTED_REDIS_IMAGE}" EXPECTED_REDIS_IMAGE_ID="${EXPECTED_REDIS_IMAGE_ID}" \
		k6 run \
			-e "BASE_URL=${BASE_URL}" \
			-e "FIXTURE_MANIFEST=${FIXTURE_MANIFEST}" \
			-e "CREDENTIALS_FILE=${warmup_credentials_file}" \
			-e "PHASE=warmup" \
			-e "MODE=${mode}" \
			-e "ENDPOINT=${endpoint}" \
			-e "VUS=${WARMUP_VUS}" \
			-e "DURATION=${WARMUP_DURATION}" \
			--summary-export "${warmup_summary_file}" "${SCENARIO_FILE}"
	warmup_status=$?
	set -e
	remove_credentials_file "${warmup_credentials_file}"
	if (( warmup_status != 0 )); then
		echo "Warmup failed for ${mode}/${endpoint}; measured phase was not started." >&2
		return "${warmup_status}"
	fi
	assert_fixture_windows
	assert_runtime_continuity

	logger_probe_since="$(rfc3339_now)"
	admin_token="$(login_token "${PERF_ADMIN_EMAIL}" "${PERF_ADMIN_PASSWORD}")"
	member_token="$(login_token "${member_email}" "${PERF_MEMBER_PASSWORD}")"
	coffee_creator_token="$(login_token "${coffee_creator_email}" "${PERF_MEMBER_PASSWORD}")"
	other_coffee_duty_token="$(login_token "${other_coffee_duty_email}" "${PERF_MEMBER_PASSWORD}")"
	meal_duty_token="$(login_token "${meal_duty_email}" "${PERF_MEMBER_PASSWORD}")"
	validate_token_lifetime "${admin_token}" "${MEASURED_DURATION}"
	validate_token_lifetime "${member_token}" "${MEASURED_DURATION}"
	validate_token_lifetime "${coffee_creator_token}" "${MEASURED_DURATION}"
	validate_token_lifetime "${other_coffee_duty_token}" "${MEASURED_DURATION}"
	validate_token_lifetime "${meal_duty_token}" "${MEASURED_DURATION}"
	capture_logger_probe "${logger_probe_since}" "${logger_probe_file}"
	wait_for_database_quiescence "${quiescence_file}" "${quiescence_status_file}"
	write_credentials_file "${measured_credentials_file}" measured \
		"${admin_token}" "${member_token}" "${coffee_creator_token}" "${other_coffee_duty_token}" "${meal_duty_token}"
	admin_token=''
	member_token=''
	coffee_creator_token=''
	other_coffee_duty_token=''
	meal_duty_token=''
	snapshot_db_tables "${before_file}"
	: > "${resource_file}"
	: > "${integrity_file}"
	[[ ! -e "${resource_stop_file}" ]] || { echo "Resource sampler stop marker already exists." >&2; return 1; }
	sample_resources_snapshot "${resource_file}"
	log_since="$(rfc3339_now)"
	sample_resources "${resource_file}" "${resource_stop_file}" &
	sampler_pid=$!

	# measured phase: only this k6 process is inside the DB/log/resource evidence window.
	env -u PERF_ADMIN_EMAIL -u PERF_ADMIN_PASSWORD -u PERF_MEMBER_PASSWORD \
	-u PERF_DB_USER -u PERF_DB_NAME -u PERF_DB_PASSWORD \
	BASE_URL="${BASE_URL}" \
	MODE="${mode}" \
	ENDPOINT="${endpoint}" \
	VUS="${MEASURED_VUS}" \
	DURATION="${MEASURED_DURATION}" \
	FIXTURE_MANIFEST="${FIXTURE_MANIFEST}" \
	EXPECTED_SOURCE_REVISION="${EXPECTED_SOURCE_REVISION}" EXPECTED_APP_SERVICE="${EXPECTED_APP_SERVICE}" \
	EXPECTED_DB_SERVICE="${EXPECTED_DB_SERVICE}" EXPECTED_REDIS_SERVICE="${EXPECTED_REDIS_SERVICE}" \
	EXPECTED_APP_IMAGE="${EXPECTED_APP_IMAGE}" EXPECTED_APP_IMAGE_ID="${EXPECTED_APP_IMAGE_ID}" \
	EXPECTED_DB_IMAGE="${EXPECTED_DB_IMAGE}" EXPECTED_DB_IMAGE_ID="${EXPECTED_DB_IMAGE_ID}" \
	EXPECTED_REDIS_IMAGE="${EXPECTED_REDIS_IMAGE}" EXPECTED_REDIS_IMAGE_ID="${EXPECTED_REDIS_IMAGE_ID}" \
		k6 run \
			-e "BASE_URL=${BASE_URL}" \
			-e "FIXTURE_MANIFEST=${FIXTURE_MANIFEST}" \
			-e "CREDENTIALS_FILE=${measured_credentials_file}" \
			-e "PHASE=measured" \
			-e "MODE=${mode}" \
			-e "ENDPOINT=${endpoint}" \
			-e "VUS=${MEASURED_VUS}" \
			-e "DURATION=${MEASURED_DURATION}" \
			--summary-export "${summary_file}" "${SCENARIO_FILE}" &
	k6_pid=$!
	sample_runtime_integrity "${k6_pid}" "${integrity_file}" &
	integrity_pid=$!
	set +e
	wait "${k6_pid}"
	k6_status=$?
	set -e
	remove_credentials_file "${measured_credentials_file}"
	log_until="$(rfc3339_now)"
	if ! kill -0 "${sampler_pid}" 2>/dev/null; then
		set +e
		wait "${sampler_pid}"
		sampler_status=$?
		set -e
		if (( sampler_status == 0 )); then
			sampler_status=1
		fi
	else
		: > "${resource_stop_file}"
		set +e
		wait "${sampler_pid}"
		sampler_status=$?
		set -e
	fi
	set +e
	wait "${integrity_pid}"
	integrity_status=$?
	set -e
	set +e
	assert_runtime_continuity
	runtime_continuity_status=$?
	assert_fixture_windows
	fixture_window_status=$?
	docker logs --since "${log_since}" --until "${log_until}" "${APP_CONTAINER}" 2>&1 \
		| node "${FILTER_SQL_LOG}" > "${sql_log_file}"
	log_pipeline_status=("${PIPESTATUS[@]}")
	if (( log_pipeline_status[0] != 0 )); then
		log_capture_status="${log_pipeline_status[0]}"
	else
		log_capture_status="${log_pipeline_status[1]}"
	fi
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
	REDIS_SERVICE_VALUE="${redis_service}" SOURCE_REVISION_VALUE="${EXPECTED_SOURCE_REVISION}" \
	EXPECTED_FLYWAY_VERSION_VALUE="${EXPECTED_FLYWAY_VERSION}" \
	EXPECTED_APP_SERVICE_VALUE="${EXPECTED_APP_SERVICE}" EXPECTED_DB_SERVICE_VALUE="${EXPECTED_DB_SERVICE}" \
	APP_HASH_VALUE="${app_config_hash}" DB_HASH_VALUE="${db_config_hash}" \
	REDIS_HASH_VALUE="${redis_config_hash}" \
	APP_IMAGE_VALUE="${app_image}" EXPECTED_IMAGE_VALUE="${EXPECTED_APP_IMAGE}" \
	EXPECTED_APP_IMAGE_ID_VALUE="${EXPECTED_APP_IMAGE_ID}" EXPECTED_DB_IMAGE_VALUE="${EXPECTED_DB_IMAGE}" \
	EXPECTED_DB_IMAGE_ID_VALUE="${EXPECTED_DB_IMAGE_ID}" EXPECTED_REDIS_IMAGE_VALUE="${EXPECTED_REDIS_IMAGE}" \
	EXPECTED_REDIS_IMAGE_ID_VALUE="${EXPECTED_REDIS_IMAGE_ID}" \
	APP_IMAGE_ID_VALUE="${app_image_id}" TARGET_PORT_VALUE="${seed_target_port}" \
	DB_IMAGE_VALUE="${db_image}" REDIS_IMAGE_VALUE="${redis_image}" \
	APP_CONTAINER_VALUE="${APP_CONTAINER}" DB_CONTAINER_VALUE="${DB_CONTAINER}" REDIS_CONTAINER_VALUE="${REDIS_CONTAINER}" \
	APP_CONTAINER_ID_VALUE="${app_container_id}" APP_STARTED_AT_VALUE="${app_container_started_at}" \
	DB_CONTAINER_ID_VALUE="${db_container_id}" DB_IMAGE_ID_VALUE="${db_image_id}" \
	DB_STARTED_AT_VALUE="${db_container_started_at}" DB_IDENTITY_VALUE="${database_identity}" \
	REDIS_CONTAINER_ID_VALUE="${redis_container_id}" REDIS_IMAGE_ID_VALUE="${redis_image_id}" \
	REDIS_STARTED_AT_VALUE="${redis_container_started_at}" REDIS_IDENTITY_VALUE="${redis_identity}" \
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
				sourceRevision: process.env.SOURCE_REVISION_VALUE,
				expectedFlywayVersion: process.env.EXPECTED_FLYWAY_VERSION_VALUE,
				composeProject: process.env.PROJECT_VALUE,
				appServiceLabel: process.env.APP_SERVICE_VALUE,
				dbServiceLabel: process.env.DB_SERVICE_VALUE,
				redisServiceLabel: process.env.REDIS_SERVICE_VALUE,
				appConfigHash: process.env.APP_HASH_VALUE,
				dbConfigHash: process.env.DB_HASH_VALUE,
				redisConfigHash: process.env.REDIS_HASH_VALUE,
				appImage: process.env.APP_IMAGE_VALUE,
				appImageId: process.env.APP_IMAGE_ID_VALUE,
				dbImage: process.env.DB_IMAGE_VALUE,
				redisImage: process.env.REDIS_IMAGE_VALUE,
				expectedAppImage: process.env.EXPECTED_IMAGE_VALUE,
				expectedAppImageId: process.env.EXPECTED_APP_IMAGE_ID_VALUE,
				expectedDbImage: process.env.EXPECTED_DB_IMAGE_VALUE,
				expectedDbImageId: process.env.EXPECTED_DB_IMAGE_ID_VALUE,
				expectedRedisImage: process.env.EXPECTED_REDIS_IMAGE_VALUE,
				expectedRedisImageId: process.env.EXPECTED_REDIS_IMAGE_ID_VALUE,
				expectedAppService: process.env.EXPECTED_APP_SERVICE_VALUE,
				expectedDbService: process.env.EXPECTED_DB_SERVICE_VALUE,
				targetPort: process.env.TARGET_PORT_VALUE,
				appContainer: process.env.APP_CONTAINER_VALUE,
				dbContainer: process.env.DB_CONTAINER_VALUE,
				redisContainer: process.env.REDIS_CONTAINER_VALUE,
				appContainerId: process.env.APP_CONTAINER_ID_VALUE,
				appContainerStartedAt: process.env.APP_STARTED_AT_VALUE,
				dbContainerId: process.env.DB_CONTAINER_ID_VALUE,
				dbImageId: process.env.DB_IMAGE_ID_VALUE,
				dbContainerStartedAt: process.env.DB_STARTED_AT_VALUE,
				redisContainerId: process.env.REDIS_CONTAINER_ID_VALUE,
				redisImageId: process.env.REDIS_IMAGE_ID_VALUE,
				redisContainerStartedAt: process.env.REDIS_STARTED_AT_VALUE,
				redisIdentity: JSON.parse(process.env.REDIS_IDENTITY_VALUE),
				resourceContainerIds: {
					[process.env.APP_CONTAINER_VALUE]: process.env.APP_CONTAINER_ID_VALUE,
					[process.env.DB_CONTAINER_VALUE]: process.env.DB_CONTAINER_ID_VALUE,
					[process.env.REDIS_CONTAINER_VALUE]: process.env.REDIS_CONTAINER_ID_VALUE,
				},
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
	if ! measurement_status="$(node -e '
		const fs = require("node:fs");
		const report = JSON.parse(fs.readFileSync(process.argv[1], "utf8"));
		if (report.accepted !== false || report.automaticAdoption !== false
			|| typeof report.measurementStatus !== "string") process.exit(1);
		process.stdout.write(report.measurementStatus);
	' "${report_file}")"; then
		echo "Evidence report status is missing or malformed for ${mode}/${endpoint}." >&2
		return 1
	fi
	if [[ "${measurement_status}" != "conditional-not-adoptable" || "${summarize_status}" -ne 2 ]]; then
		if (( summarize_status != 0 )); then
			echo "Evidence report was rejected for ${mode}/${endpoint}; see ${report_file}." >&2
			return "${summarize_status}"
		fi
		echo "Unexpected evidence status=${measurement_status} for ${mode}/${endpoint}." >&2
		return 1
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
	overall_non_adoptable=1
	node "${TOOLING_PROVENANCE}" --assert-manifest "${PERF_SCENARIO_WORKTREE}" "${EXPECTED_SCENARIO_HEAD}" "${FIXTURE_MANIFEST}"
	echo "Conditional evidence preserved for ${mode}/${endpoint}; continuing the approved sequential scope." >&2
}

if [[ "${REQUESTED_MODE}" == "all" ]]; then
	modes=("${MODE_SEQUENCE[@]}")
else
	case " ${MODE_SEQUENCE[*]} " in
		*" ${REQUESTED_MODE} "*) modes=("${REQUESTED_MODE}") ;;
		*) echo "Mode must be all, prayer, poll-member, poll-admin, or poll-duty." >&2; exit 1 ;;
	esac
fi

for mode in "${modes[@]}"; do
	for endpoint in $(endpoints_for_mode "${mode}"); do
		ENDPOINT="${endpoint}" run_endpoint "${mode}" "${endpoint}"
	done
done

node "${TOOLING_PROVENANCE}" --assert-manifest "${PERF_SCENARIO_WORKTREE}" "${EXPECTED_SCENARIO_HEAD}" "${FIXTURE_MANIFEST}"

if (( overall_non_adoptable != 1 )); then
	echo "Issue #196 produced no conditional evidence; refusing a successful completion." >&2
	exit 1
fi
echo "Issue #196 baseline evidence collection finished requested mode=${REQUESTED_MODE}; automatic adoption remains disabled. Reports: ${REPORT_ROOT}" >&2
exit 2
