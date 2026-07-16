#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
EVIDENCE_OVERRIDE="${ROOT_DIR}/runtime-evidence.override.yml"
VALIDATE_TARGET="${ROOT_DIR}/validate-published-target.mjs"
PREP_CONTRACT="${ROOT_DIR}/runtime-prep-contract.mjs"
TOOLING_PROVENANCE="${ROOT_DIR}/tooling-provenance.mjs"
ENV_ATTESTATION="${ROOT_DIR}/runtime-env-attestation.mjs"

PERF_SCENARIO_WORKTREE="${PERF_SCENARIO_WORKTREE:?PERF_SCENARIO_WORKTREE is required at runtime}"
EXPECTED_SCENARIO_HEAD="${EXPECTED_SCENARIO_HEAD:?EXPECTED_SCENARIO_HEAD is required at runtime}"
PERF_RUNTIME_PREP_ATTEMPT_ID="${PERF_RUNTIME_PREP_ATTEMPT_ID:?PERF_RUNTIME_PREP_ATTEMPT_ID is required at runtime}"
PERF_RUNTIME_PREP_REPORT_ROOT="${PERF_RUNTIME_PREP_REPORT_ROOT:?PERF_RUNTIME_PREP_REPORT_ROOT is required at runtime}"
PERF_DEPLOY_DIR="${PERF_DEPLOY_DIR:?PERF_DEPLOY_DIR is required at runtime}"
PERF_BASE_COMPOSE_FILE="${PERF_BASE_COMPOSE_FILE:?PERF_BASE_COMPOSE_FILE is required at runtime}"
PERF_BASE_OVERRIDE_FILE="${PERF_BASE_OVERRIDE_FILE:?PERF_BASE_OVERRIDE_FILE is required at runtime}"
PERF_COMPOSE_ENV_FILE="${PERF_COMPOSE_ENV_FILE:?PERF_COMPOSE_ENV_FILE is required at runtime}"
PERF_RUNTIME_PREP_MANIFEST="${PERF_RUNTIME_PREP_MANIFEST:?PERF_RUNTIME_PREP_MANIFEST is required at runtime}"
PERF_APP_READY_TIMEOUT_SECONDS="${PERF_APP_READY_TIMEOUT_SECONDS:?PERF_APP_READY_TIMEOUT_SECONDS is required at runtime}"
BASE_URL="${BASE_URL:?BASE_URL is required at runtime}"
APP_CONTAINER="${APP_CONTAINER:?APP_CONTAINER is required at runtime}"
DB_CONTAINER="${DB_CONTAINER:?DB_CONTAINER is required at runtime}"
REDIS_CONTAINER="${REDIS_CONTAINER:?REDIS_CONTAINER is required at runtime}"
EXPECTED_COMPOSE_PROJECT="${EXPECTED_COMPOSE_PROJECT:?EXPECTED_COMPOSE_PROJECT is required at runtime}"
EXPECTED_APP_SERVICE="${EXPECTED_APP_SERVICE:?EXPECTED_APP_SERVICE is required at runtime}"
EXPECTED_DB_SERVICE="${EXPECTED_DB_SERVICE:?EXPECTED_DB_SERVICE is required at runtime}"
EXPECTED_REDIS_SERVICE="${EXPECTED_REDIS_SERVICE:?EXPECTED_REDIS_SERVICE is required at runtime}"
EXPECTED_SOURCE_REVISION="${EXPECTED_SOURCE_REVISION:?EXPECTED_SOURCE_REVISION is required at runtime}"
EXPECTED_CURRENT_APP_CONTAINER_ID="${EXPECTED_CURRENT_APP_CONTAINER_ID:?EXPECTED_CURRENT_APP_CONTAINER_ID is required at runtime}"
EXPECTED_CURRENT_APP_IMAGE_ID="${EXPECTED_CURRENT_APP_IMAGE_ID:?EXPECTED_CURRENT_APP_IMAGE_ID is required at runtime}"
EXPECTED_CURRENT_APP_STARTED_AT="${EXPECTED_CURRENT_APP_STARTED_AT:?EXPECTED_CURRENT_APP_STARTED_AT is required at runtime}"
EXPECTED_CURRENT_APP_CONFIG_HASH="${EXPECTED_CURRENT_APP_CONFIG_HASH:?EXPECTED_CURRENT_APP_CONFIG_HASH is required at runtime}"
EXPECTED_DB_CONTAINER_ID="${EXPECTED_DB_CONTAINER_ID:?EXPECTED_DB_CONTAINER_ID is required at runtime}"
EXPECTED_DB_IMAGE_ID="${EXPECTED_DB_IMAGE_ID:?EXPECTED_DB_IMAGE_ID is required at runtime}"
EXPECTED_DB_STARTED_AT="${EXPECTED_DB_STARTED_AT:?EXPECTED_DB_STARTED_AT is required at runtime}"
EXPECTED_DB_CONFIG_HASH="${EXPECTED_DB_CONFIG_HASH:?EXPECTED_DB_CONFIG_HASH is required at runtime}"
EXPECTED_REDIS_CONTAINER_ID="${EXPECTED_REDIS_CONTAINER_ID:?EXPECTED_REDIS_CONTAINER_ID is required at runtime}"
EXPECTED_REDIS_IMAGE_ID="${EXPECTED_REDIS_IMAGE_ID:?EXPECTED_REDIS_IMAGE_ID is required at runtime}"
EXPECTED_REDIS_STARTED_AT="${EXPECTED_REDIS_STARTED_AT:?EXPECTED_REDIS_STARTED_AT is required at runtime}"
EXPECTED_REDIS_CONFIG_HASH="${EXPECTED_REDIS_CONFIG_HASH:?EXPECTED_REDIS_CONFIG_HASH is required at runtime}"

fail() {
	echo "Issue #196 runtime prep failed: $*" >&2
	exit 1
}

for command in docker git node mkdir; do
	command -v "${command}" >/dev/null || fail "Missing command: ${command}"
done

[[ "${BASE_URL}" =~ ^http://(127\.0\.0\.1|\[::1\])(:[0-9]+)?$ ]] || fail "BASE_URL must be an explicit numeric loopback target."
[[ "${PERF_SCENARIO_WORKTREE}" == /* && -d "${PERF_SCENARIO_WORKTREE}" ]] || fail "PERF_SCENARIO_WORKTREE must be an existing absolute directory."
[[ "${EXPECTED_SCENARIO_HEAD}" =~ ^[a-f0-9]{40}$ ]] || fail "EXPECTED_SCENARIO_HEAD must be a full Git commit."
[[ "${PERF_RUNTIME_PREP_ATTEMPT_ID}" =~ ^[a-z0-9][a-z0-9_-]{7,31}$ ]] || fail "PERF_RUNTIME_PREP_ATTEMPT_ID is invalid."
[[ "${PERF_RUNTIME_PREP_REPORT_ROOT}" == /* ]] || fail "PERF_RUNTIME_PREP_REPORT_ROOT must be absolute."
[[ "${PERF_DEPLOY_DIR}" == /* && -d "${PERF_DEPLOY_DIR}" ]] || fail "PERF_DEPLOY_DIR must be an existing absolute directory."
[[ "${PERF_BASE_COMPOSE_FILE}" == /* && -f "${PERF_BASE_COMPOSE_FILE}" ]] || fail "PERF_BASE_COMPOSE_FILE must be an existing absolute file."
[[ "${PERF_BASE_OVERRIDE_FILE}" == /* && -f "${PERF_BASE_OVERRIDE_FILE}" ]] || fail "PERF_BASE_OVERRIDE_FILE must be an existing absolute file."
[[ "${PERF_COMPOSE_ENV_FILE}" == /* && -f "${PERF_COMPOSE_ENV_FILE}" ]] || fail "PERF_COMPOSE_ENV_FILE must be an existing absolute file."
[[ "${PERF_BASE_COMPOSE_FILE}" == "${PERF_DEPLOY_DIR}/docker-compose.yml" ]] || fail "Base Compose file must belong to the approved deploy checkout."
COMPOSE_ENV_FILE_VALUE="${PERF_COMPOSE_ENV_FILE}" node -e '
	const fs = require("node:fs");
	const path = process.env.COMPOSE_ENV_FILE_VALUE;
	const stat = fs.lstatSync(path);
	if (!stat.isFile() || stat.isSymbolicLink() || (stat.mode & 0o077) !== 0) process.exit(1);
' || fail "PERF_COMPOSE_ENV_FILE must be a non-symlink regular file with no group/other permissions."
[[ "${PERF_RUNTIME_PREP_MANIFEST}" == /* ]] || fail "PERF_RUNTIME_PREP_MANIFEST must be an absolute path."
attempt_dir="${PERF_RUNTIME_PREP_REPORT_ROOT}/${PERF_RUNTIME_PREP_ATTEMPT_ID}"
attempt_receipt_path="${attempt_dir}/runtime-prep-attempt.json"
rejected_receipt_path="${attempt_dir}/runtime-prep-rejected.json"
[[ "${PERF_RUNTIME_PREP_MANIFEST}" == "${attempt_dir}/runtime-prep-manifest.json" ]] || fail "Runtime prep manifest must belong to its immutable attempt namespace."
mkdir -p "${PERF_RUNTIME_PREP_REPORT_ROOT}"
mkdir -m 700 "${attempt_dir}" 2>/dev/null || fail "Runtime prep attempt namespace already exists; attempt IDs are never reusable."
[[ ! -e "${PERF_RUNTIME_PREP_MANIFEST}" && ! -e "${rejected_receipt_path}" ]] || fail "Runtime prep success or rejection receipt already exists."
READY_TIMEOUT_VALUE="${PERF_APP_READY_TIMEOUT_SECONDS}" node -e '
	const value = Number(process.env.READY_TIMEOUT_VALUE);
	if (!Number.isInteger(value) || value < 1 || value > 600) process.exit(1);
' || fail "PERF_APP_READY_TIMEOUT_SECONDS must be an integer from 1 through 600."

capture_tooling_provenance() {
	node "${TOOLING_PROVENANCE}" --capture "${PERF_SCENARIO_WORKTREE}" "${EXPECTED_SCENARIO_HEAD}"
}

tooling_before="$(capture_tooling_provenance)" || fail "Scenario tooling provenance is not clean and approved."
lifecycle_started=false
prep_succeeded=false
prep_stage="preflight"
PERF_PROJECT_LOCK=""

write_partial_rejection() {
	[[ "${lifecycle_started}" == true && "${prep_succeeded}" == false && ! -e "${PERF_RUNTIME_PREP_MANIFEST}" && ! -e "${rejected_receipt_path}" ]] || return 0
	local current_app_json current_db_json current_redis_json tooling_json rejection_json
	set +e
	current_app_json="$(container_snapshot "${APP_CONTAINER}" "${EXPECTED_APP_SERVICE}" 2>/dev/null)"
	current_db_json="$(container_snapshot "${DB_CONTAINER}" "${EXPECTED_DB_SERVICE}" 2>/dev/null)"
	current_redis_json="$(container_snapshot "${REDIS_CONTAINER}" "${EXPECTED_REDIS_SERVICE}" 2>/dev/null)"
	tooling_json="$(capture_tooling_provenance 2>/dev/null)"
	set -e
	[[ -n "${current_app_json}" ]] || current_app_json="${previous_app}"
	[[ -n "${current_db_json}" ]] || current_db_json="${previous_db}"
	[[ -n "${current_redis_json}" ]] || current_redis_json="${previous_redis}"
	[[ -n "${tooling_json}" ]] || tooling_json="${tooling_before}"
	rejection_json="$(ATTEMPT_ID_VALUE="${PERF_RUNTIME_PREP_ATTEMPT_ID}" STAGE_VALUE="${prep_stage}" \
		PREVIOUS_APP_JSON="${previous_app}" CURRENT_APP_JSON="${current_app_json}" \
		CURRENT_DB_JSON="${current_db_json}" CURRENT_REDIS_JSON="${current_redis_json}" TOOLING_JSON="${tooling_json}" node -e '
		const receipt = {
			contractVersion: 1, issue: 196, attemptId: process.env.ATTEMPT_ID_VALUE, status: "rejected",
			failedAt: new Date().toISOString(), stage: process.env.STAGE_VALUE, lifecycleStarted: true,
			reusable: false, automaticCleanup: false,
			previousApp: JSON.parse(process.env.PREVIOUS_APP_JSON), currentApp: JSON.parse(process.env.CURRENT_APP_JSON),
			preservedDatabase: JSON.parse(process.env.CURRENT_DB_JSON), preservedRedis: JSON.parse(process.env.CURRENT_REDIS_JSON),
			tooling: JSON.parse(process.env.TOOLING_JSON),
			restoreHandoff: "recreate-app-from-approved-base-compose-without-runtime-evidence-override",
			primaryRejectionReason: `runtime-preparation-failed-at-${process.env.STAGE_VALUE}`,
		};
		process.stdout.write(JSON.stringify(receipt));
	')" || return 0
	RUNTIME_PREP_MANIFEST_JSON="${rejection_json}" node "${PREP_CONTRACT}" --write-rejection-from-env "${rejected_receipt_path}" 2>/dev/null || true
}

cleanup() {
	local status=$?
	if (( status != 0 )); then write_partial_rejection; fi
	if [[ -n "${PERF_PROJECT_LOCK}" ]]; then rmdir "${PERF_PROJECT_LOCK}" 2>/dev/null || true; fi
	exit "${status}"
}
trap cleanup EXIT

source_revision="$(git -C "${PERF_DEPLOY_DIR}" rev-parse HEAD)"
[[ "${source_revision}" == "${EXPECTED_SOURCE_REVISION}" ]] || fail "Deploy checkout revision differs from the approved source."
[[ -z "$(git -C "${PERF_DEPLOY_DIR}" status --porcelain --untracked-files=all)" ]] || fail "Deploy checkout must be clean."
if git -C "${PERF_DEPLOY_DIR}" symbolic-ref -q HEAD >/dev/null 2>&1; then
	fail "Deploy checkout must remain detached."
fi
source_committed_at="$(git -C "${PERF_DEPLOY_DIR}" show -s --format=%cI HEAD)"

label() {
	docker inspect --format "{{ index .Config.Labels \"$2\" }}" "$1"
}

inspect() {
	docker inspect --format "$2" "$1"
}

container_snapshot() {
	local container="$1"
	local service="$2"
	CONTAINER_NAME="${container}" SERVICE_VALUE="${service}" \
	CONFIGURED_IMAGE="$(inspect "${container}" '{{.Config.Image}}')" \
	IMAGE_ID="$(inspect "${container}" '{{.Image}}')" \
	CONTAINER_ID="$(inspect "${container}" '{{.Id}}')" \
	STARTED_AT="$(inspect "${container}" '{{.State.StartedAt}}')" \
	CONFIG_HASH="$(label "${container}" com.docker.compose.config-hash)" node -e '
		const value = {
			containerName: process.env.CONTAINER_NAME,
			service: process.env.SERVICE_VALUE,
			configuredImage: process.env.CONFIGURED_IMAGE,
			imageId: process.env.IMAGE_ID,
			containerId: process.env.CONTAINER_ID,
			startedAt: process.env.STARTED_AT,
			configHash: process.env.CONFIG_HASH,
		};
		if (Object.values(value).some((item) => !item)) process.exit(1);
		process.stdout.write(JSON.stringify(value));
	'
}

assert_expected_current_identity() {
	CURRENT_APP_JSON="$1" CURRENT_DB_JSON="$2" CURRENT_REDIS_JSON="$3" \
	EXPECTED_APP_ID="${EXPECTED_CURRENT_APP_CONTAINER_ID}" EXPECTED_APP_IMAGE="${EXPECTED_CURRENT_APP_IMAGE_ID}" \
	EXPECTED_APP_STARTED="${EXPECTED_CURRENT_APP_STARTED_AT}" EXPECTED_APP_HASH="${EXPECTED_CURRENT_APP_CONFIG_HASH}" \
	EXPECTED_DATABASE_ID="${EXPECTED_DB_CONTAINER_ID}" EXPECTED_DATABASE_IMAGE="${EXPECTED_DB_IMAGE_ID}" \
	EXPECTED_DATABASE_STARTED="${EXPECTED_DB_STARTED_AT}" EXPECTED_DATABASE_HASH="${EXPECTED_DB_CONFIG_HASH}" \
	EXPECTED_CACHE_ID="${EXPECTED_REDIS_CONTAINER_ID}" EXPECTED_CACHE_IMAGE="${EXPECTED_REDIS_IMAGE_ID}" \
	EXPECTED_CACHE_STARTED="${EXPECTED_REDIS_STARTED_AT}" EXPECTED_CACHE_HASH="${EXPECTED_REDIS_CONFIG_HASH}" node -e '
		const app = JSON.parse(process.env.CURRENT_APP_JSON);
		const db = JSON.parse(process.env.CURRENT_DB_JSON);
		const redis = JSON.parse(process.env.CURRENT_REDIS_JSON);
		const exact = (actual, expected) => Object.entries(expected).every(([key, value]) => actual[key] === value);
		if (!exact(app, { containerId: process.env.EXPECTED_APP_ID, imageId: process.env.EXPECTED_APP_IMAGE, startedAt: process.env.EXPECTED_APP_STARTED, configHash: process.env.EXPECTED_APP_HASH })) process.exit(1);
		if (!exact(db, { containerId: process.env.EXPECTED_DATABASE_ID, imageId: process.env.EXPECTED_DATABASE_IMAGE, startedAt: process.env.EXPECTED_DATABASE_STARTED, configHash: process.env.EXPECTED_DATABASE_HASH })) process.exit(1);
		if (!exact(redis, { containerId: process.env.EXPECTED_CACHE_ID, imageId: process.env.EXPECTED_CACHE_IMAGE, startedAt: process.env.EXPECTED_CACHE_STARTED, configHash: process.env.EXPECTED_CACHE_HASH })) process.exit(1);
	' || fail "Current app/PostgreSQL/Redis identity differs from the approved pre-prep snapshot."
}

assert_compose_labels() {
	local expected_config_files="$1"
	local app_project
	app_project="$(label "${APP_CONTAINER}" com.docker.compose.project)"
	[[ "${app_project}" == "${EXPECTED_COMPOSE_PROJECT}" \
		&& "$(label "${DB_CONTAINER}" com.docker.compose.project)" == "${EXPECTED_COMPOSE_PROJECT}" \
		&& "$(label "${REDIS_CONTAINER}" com.docker.compose.project)" == "${EXPECTED_COMPOSE_PROJECT}" ]] \
		|| fail "App/PostgreSQL/Redis Compose project changed."
	[[ "$(label "${APP_CONTAINER}" com.docker.compose.service)" == "${EXPECTED_APP_SERVICE}" \
		&& "$(label "${DB_CONTAINER}" com.docker.compose.service)" == "${EXPECTED_DB_SERVICE}" \
		&& "$(label "${REDIS_CONTAINER}" com.docker.compose.service)" == "${EXPECTED_REDIS_SERVICE}" ]] \
		|| fail "Compose service label changed."
	[[ "$(label "${APP_CONTAINER}" com.docker.compose.project.working_dir)" == "${PERF_DEPLOY_DIR}" ]] \
		|| fail "Compose working directory differs from the approved deploy checkout."
	[[ "$(label "${APP_CONTAINER}" com.docker.compose.project.config_files)" == "${expected_config_files}" ]] \
		|| fail "Compose config file provenance differs from the approved list."
}

published_ports="$(docker port "${APP_CONTAINER}" 8080/tcp)"
target_port="$(BASE_URL_VALUE="${BASE_URL}" PUBLISHED_BINDINGS_VALUE="${published_ports}" node "${VALIDATE_TARGET}" --host-port)" \
	|| fail "BASE_URL and published app binding do not identify one approved target."
pre_config_files="${PERF_BASE_COMPOSE_FILE},${PERF_BASE_OVERRIDE_FILE}"
assert_compose_labels "${pre_config_files}"
previous_app="$(container_snapshot "${APP_CONTAINER}" "${EXPECTED_APP_SERVICE}")"
previous_db="$(container_snapshot "${DB_CONTAINER}" "${EXPECTED_DB_SERVICE}")"
previous_redis="$(container_snapshot "${REDIS_CONTAINER}" "${EXPECTED_REDIS_SERVICE}")"
previous_app_environment_json="$(docker inspect --format '{{json .Config.Env}}' "${APP_CONTAINER}")"
assert_expected_current_identity "${previous_app}" "${previous_db}" "${previous_redis}"

ATTEMPT_ID_VALUE="${PERF_RUNTIME_PREP_ATTEMPT_ID}" ATTEMPT_DIR_VALUE="${attempt_dir}" \
	PREVIOUS_APP_JSON="${previous_app}" PREVIOUS_DB_JSON="${previous_db}" PREVIOUS_REDIS_JSON="${previous_redis}" \
	TOOLING_JSON="${tooling_before}" node -e '
	const fs = require("node:fs");
	const tooling = JSON.parse(process.env.TOOLING_JSON);
	const receipt = {
		contractVersion: 1, issue: 196, attemptId: process.env.ATTEMPT_ID_VALUE, status: "reserved",
		reservedAt: new Date().toISOString(), reportDirectory: process.env.ATTEMPT_DIR_VALUE, reusable: false,
		previousApp: JSON.parse(process.env.PREVIOUS_APP_JSON),
		preservedDatabase: JSON.parse(process.env.PREVIOUS_DB_JSON), preservedRedis: JSON.parse(process.env.PREVIOUS_REDIS_JSON),
		toolingAggregateSha256: tooling.aggregateSha256,
	};
	fs.writeFileSync(process.argv[1], `${JSON.stringify(receipt, null, 2)}\n`, { flag: "wx", mode: 0o600 });
' "${attempt_receipt_path}"

compose_project="$(label "${APP_CONTAINER}" com.docker.compose.project)"
[[ "${compose_project}" =~ ^[a-z0-9][a-z0-9_-]*$ ]] || fail "Compose project cannot form the canonical performance lock."
PERF_PROJECT_LOCK="/tmp/faithlog-performance-${compose_project}.lock"
if ! mkdir "${PERF_PROJECT_LOCK}" 2>/dev/null; then
	fail "Another performance preparation or load owns ${PERF_PROJECT_LOCK}."
fi

[[ "$(container_snapshot "${APP_CONTAINER}" "${EXPECTED_APP_SERVICE}")" == "${previous_app}" \
	&& "$(container_snapshot "${DB_CONTAINER}" "${EXPECTED_DB_SERVICE}")" == "${previous_db}" \
	&& "$(container_snapshot "${REDIS_CONTAINER}" "${EXPECTED_REDIS_SERVICE}")" == "${previous_redis}" ]] \
	|| fail "Runtime identity changed after the canonical project lock."

[[ "$(capture_tooling_provenance)" == "${tooling_before}" ]] || fail "Scenario tooling changed while waiting for the canonical lock."

prep_stage="app-recreate"
lifecycle_started=true
env -i PATH="${PATH}" HOME="${HOME:-}" DOCKER_CONFIG="${DOCKER_CONFIG:-}" TMPDIR="${TMPDIR:-/tmp}" \
	docker compose --env-file "${PERF_COMPOSE_ENV_FILE}" --project-name "${EXPECTED_COMPOSE_PROJECT}" \
	--project-directory "${PERF_DEPLOY_DIR}" -f "${PERF_BASE_COMPOSE_FILE}" -f "${PERF_BASE_OVERRIDE_FILE}" \
	-f "${EVIDENCE_OVERRIDE}" up -d --build --no-deps --force-recreate app

prep_stage="post-recreate-provenance"
tooling_after_recreate="$(capture_tooling_provenance)" || fail "Scenario tooling became invalid after app recreation."
[[ "${tooling_after_recreate}" == "${tooling_before}" ]] || fail "Scenario tooling changed during app recreation."

prep_stage="health-check"
ready_deadline=$((SECONDS + PERF_APP_READY_TIMEOUT_SECONDS))
until BASE_URL_VALUE="${BASE_URL}" node -e '
	const response = await fetch(`${process.env.BASE_URL_VALUE}/api/v1/health`).catch(() => null);
	if (!response?.ok) process.exit(1);
	const payload = await response.json().catch(() => null);
	if (payload?.data?.status !== "UP" && payload?.status !== "UP") process.exit(1);
'; do
	(( SECONDS < ready_deadline )) || fail "Instrumented app did not become healthy before the approved timeout."
	sleep 1
done

prep_stage="runtime-continuity"
instrumented_app="$(container_snapshot "${APP_CONTAINER}" "${EXPECTED_APP_SERVICE}")"
preserved_db="$(container_snapshot "${DB_CONTAINER}" "${EXPECTED_DB_SERVICE}")"
preserved_redis="$(container_snapshot "${REDIS_CONTAINER}" "${EXPECTED_REDIS_SERVICE}")"
[[ "${preserved_db}" == "${previous_db}" ]] || fail "PostgreSQL identity changed during app-only runtime preparation."
[[ "${preserved_redis}" == "${previous_redis}" ]] || fail "Redis identity changed during app-only runtime preparation."
PREVIOUS_APP_JSON="${previous_app}" INSTRUMENTED_APP_JSON="${instrumented_app}" node -e '
	const before = JSON.parse(process.env.PREVIOUS_APP_JSON);
	const after = JSON.parse(process.env.INSTRUMENTED_APP_JSON);
	if (before.containerId === after.containerId || before.startedAt === after.startedAt || before.configHash === after.configHash) process.exit(1);
	for (const key of ["containerName", "service", "configuredImage"]) if (before[key] !== after[key]) process.exit(1);
' || fail "App was not recreated with one new instrumented identity."

post_config_files="${PERF_BASE_COMPOSE_FILE},${PERF_BASE_OVERRIDE_FILE},${EVIDENCE_OVERRIDE}"
assert_compose_labels "${post_config_files}"
post_published_ports="$(docker port "${APP_CONTAINER}" 8080/tcp)"
post_target_port="$(BASE_URL_VALUE="${BASE_URL}" PUBLISHED_BINDINGS_VALUE="${post_published_ports}" node "${VALIDATE_TARGET}" --host-port)" \
	|| fail "Instrumented app published binding is invalid."
[[ "${post_target_port}" == "${target_port}" && "${post_published_ports}" == "${published_ports}" ]] \
	|| fail "Instrumented app changed the approved published target."

prep_stage="environment-attestation"
app_environment_json="$(docker inspect --format '{{json .Config.Env}}' "${APP_CONTAINER}")"
environment_attestation="$(PREVIOUS_APP_ENV_JSON="${previous_app_environment_json}" CURRENT_APP_ENV_JSON="${app_environment_json}" \
	node "${ENV_ATTESTATION}")" || fail "Instrumented app has unrelated environment drift."
APP_ENVIRONMENT_JSON="${app_environment_json}" node -e '
	const actual = new Set(JSON.parse(process.env.APP_ENVIRONMENT_JSON));
	const springApplicationJson = "{\"logging\":{\"level\":{\"org.hibernate.SQL\":\"DEBUG\",\"org.hibernate.orm.jdbc.bind\":\"OFF\",\"org.hibernate.orm.jdbc.extract\":\"OFF\"}},\"spring\":{\"jpa\":{\"show-sql\":false,\"properties\":{\"hibernate\":{\"format_sql\":false}}}}}";
	const required = [
		`SPRING_APPLICATION_JSON=${springApplicationJson}`,
		"FAITHLOG_SCHEDULER_ENABLED=false",
	];
	if (!required.every((value) => actual.has(value))) process.exit(1);
' || fail "Instrumented app evidence logging environment is incomplete."

prep_stage="final-provenance"
tooling_final="$(capture_tooling_provenance)" || fail "Scenario tooling is invalid before final manifest."
[[ "${tooling_final}" == "${tooling_before}" ]] || fail "Scenario tooling changed before final manifest."
[[ "$(container_snapshot "${APP_CONTAINER}" "${EXPECTED_APP_SERVICE}")" == "${instrumented_app}" \
	&& "$(container_snapshot "${DB_CONTAINER}" "${EXPECTED_DB_SERVICE}")" == "${preserved_db}" \
	&& "$(container_snapshot "${REDIS_CONTAINER}" "${EXPECTED_REDIS_SERVICE}")" == "${preserved_redis}" ]] \
	|| fail "Runtime identity changed before final manifest."

instrumented_image_id="$(INSTRUMENTED_APP_JSON="${instrumented_app}" node -e 'process.stdout.write(JSON.parse(process.env.INSTRUMENTED_APP_JSON).imageId)')"
image_created_at="$(docker image inspect --format '{{.Created}}' "${instrumented_image_id}")"

attempt_receipt_json="$(node -e 'const fs=require("node:fs");process.stdout.write(JSON.stringify(JSON.parse(fs.readFileSync(process.argv[1],"utf8"))))' "${attempt_receipt_path}")"
manifest_json="$(ATTEMPT_ID_VALUE="${PERF_RUNTIME_PREP_ATTEMPT_ID}" ATTEMPT_RECEIPT_JSON="${attempt_receipt_json}" TOOLING_JSON="${tooling_final}" \
	ENVIRONMENT_ATTESTATION_JSON="${environment_attestation}" \
	SOURCE_REVISION_VALUE="${source_revision}" DEPLOY_DIR_VALUE="${PERF_DEPLOY_DIR}" \
	SOURCE_COMMITTED_AT_VALUE="${source_committed_at}" IMAGE_CREATED_AT_VALUE="${image_created_at}" \
	PROJECT_VALUE="${compose_project}" CONFIG_FILES_VALUE="${post_config_files}" TARGET_PORT_VALUE="${target_port}" \
	PREVIOUS_APP_JSON="${previous_app}" INSTRUMENTED_APP_JSON="${instrumented_app}" \
	PRESERVED_DB_JSON="${preserved_db}" PRESERVED_REDIS_JSON="${preserved_redis}" node -e '
	const manifest = {
		contractVersion: 1,
		issue: 196,
		attemptId: process.env.ATTEMPT_ID_VALUE,
		createdAt: new Date().toISOString(),
		attemptReceipt: JSON.parse(process.env.ATTEMPT_RECEIPT_JSON),
		tooling: JSON.parse(process.env.TOOLING_JSON),
		source: {
			revision: process.env.SOURCE_REVISION_VALUE,
			deployDirectory: process.env.DEPLOY_DIR_VALUE,
			clean: true,
			detached: true,
			committedAt: process.env.SOURCE_COMMITTED_AT_VALUE,
			imageCreatedAt: process.env.IMAGE_CREATED_AT_VALUE,
			operationalProvenance: "clean-detached-checkout-image-created-after-source",
			imageAloneCryptographicProof: false,
		},
		compose: {
			project: process.env.PROJECT_VALUE,
			workingDirectory: process.env.DEPLOY_DIR_VALUE,
			configFiles: process.env.CONFIG_FILES_VALUE.split(","),
			targetPort: process.env.TARGET_PORT_VALUE,
		},
		previousApp: JSON.parse(process.env.PREVIOUS_APP_JSON),
		instrumentedApp: JSON.parse(process.env.INSTRUMENTED_APP_JSON),
		preservedDatabase: JSON.parse(process.env.PRESERVED_DB_JSON),
		preservedRedis: JSON.parse(process.env.PRESERVED_REDIS_JSON),
		environmentAttestation: JSON.parse(process.env.ENVIRONMENT_ATTESTATION_JSON),
		evidenceLogging: {
			sqlLogger: "DEBUG",
			formatSql: false,
			showSql: false,
			bindLogger: "OFF",
			extractLogger: "OFF",
			statementOnlyArtifact: true,
		},
	};
	process.stdout.write(JSON.stringify(manifest));
')"
prep_stage="manifest-write"
RUNTIME_PREP_MANIFEST_JSON="${manifest_json}" node "${PREP_CONTRACT}" --write-from-env "${PERF_RUNTIME_PREP_MANIFEST}"
prep_succeeded=true
echo "Issue #196 instrumented app runtime prepared; immutable manifest=${PERF_RUNTIME_PREP_MANIFEST}" >&2
