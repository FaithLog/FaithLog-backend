#!/usr/bin/env bash
set -euo pipefail

: "${POSTGRES_CONTAINER:?POSTGRES_CONTAINER is required.}"
: "${REDIS_CONTAINER:?REDIS_CONTAINER is required.}"
: "${PERF_COMPOSE_PROJECT:?PERF_COMPOSE_PROJECT is required.}"
: "${POSTGRES_USER:?POSTGRES_USER is required.}"
: "${POSTGRES_DB:?POSTGRES_DB is required.}"

OUTPUT_PATH="${1:?Output path is required.}"
TEMP_PATH="${OUTPUT_PATH}.tmp.$$"
PG_SERVER_PATH="${OUTPUT_PATH}.postgres-server.tmp.$$"
REDIS_SERVER_PATH="${OUTPUT_PATH}.redis-server.tmp.$$"
cleanup() {
	rm -f "${TEMP_PATH}" "${PG_SERVER_PATH}" "${REDIS_SERVER_PATH}"
}
trap cleanup EXIT

inspect_value() {
	local template="$1"
	local container="$2"
	local value
	value="$(docker inspect --format "${template}" "${container}")"
	if [[ -z "${value}" || "${value}" == "<no value>" || "${value}" == "null" ]]; then
		echo "Missing immutable Docker identity for ${container}: ${template}" >&2
		exit 2
	fi
	printf '%s' "${value}"
}

export PG_CONTAINER_ID PG_CONTAINER_IMAGE_ID PG_CONTAINER_STARTED_AT
export PG_COMPOSE_PROJECT PG_COMPOSE_SERVICE PG_COMPOSE_CONFIG_HASH
export PG_CONTAINER_HOST_PORT
export REDIS_CONTAINER_ID REDIS_CONTAINER_IMAGE_ID REDIS_CONTAINER_STARTED_AT
export REDIS_COMPOSE_PROJECT REDIS_COMPOSE_SERVICE REDIS_COMPOSE_CONFIG_HASH
export REDIS_CONTAINER_HOST_PORT
PG_CONTAINER_ID="$(inspect_value '{{.Id}}' "${POSTGRES_CONTAINER}")"
PG_CONTAINER_IMAGE_ID="$(inspect_value '{{.Image}}' "${POSTGRES_CONTAINER}")"
PG_CONTAINER_STARTED_AT="$(inspect_value '{{.State.StartedAt}}' "${POSTGRES_CONTAINER}")"
PG_COMPOSE_PROJECT="$(inspect_value '{{ index .Config.Labels "com.docker.compose.project" }}' "${POSTGRES_CONTAINER}")"
PG_COMPOSE_SERVICE="$(inspect_value '{{ index .Config.Labels "com.docker.compose.service" }}' "${POSTGRES_CONTAINER}")"
PG_COMPOSE_CONFIG_HASH="$(inspect_value '{{ index .Config.Labels "com.docker.compose.config-hash" }}' "${POSTGRES_CONTAINER}")"
PG_CONTAINER_HOST_PORT="$(inspect_value '{{ (index (index .NetworkSettings.Ports "5432/tcp") 0).HostPort }}' "${POSTGRES_CONTAINER}")"
REDIS_CONTAINER_ID="$(inspect_value '{{.Id}}' "${REDIS_CONTAINER}")"
REDIS_CONTAINER_IMAGE_ID="$(inspect_value '{{.Image}}' "${REDIS_CONTAINER}")"
REDIS_CONTAINER_STARTED_AT="$(inspect_value '{{.State.StartedAt}}' "${REDIS_CONTAINER}")"
REDIS_COMPOSE_PROJECT="$(inspect_value '{{ index .Config.Labels "com.docker.compose.project" }}' "${REDIS_CONTAINER}")"
REDIS_COMPOSE_SERVICE="$(inspect_value '{{ index .Config.Labels "com.docker.compose.service" }}' "${REDIS_CONTAINER}")"
REDIS_COMPOSE_CONFIG_HASH="$(inspect_value '{{ index .Config.Labels "com.docker.compose.config-hash" }}' "${REDIS_CONTAINER}")"
REDIS_CONTAINER_HOST_PORT="$(inspect_value '{{ (index (index .NetworkSettings.Ports "6379/tcp") 0).HostPort }}' "${REDIS_CONTAINER}")"

if [[ "${PG_COMPOSE_PROJECT}" != "${PERF_COMPOSE_PROJECT}" \
	|| "${REDIS_COMPOSE_PROJECT}" != "${PERF_COMPOSE_PROJECT}" ]]; then
	echo "Runtime identity Compose project changed after lock acquisition." >&2
	exit 2
fi

docker exec "${POSTGRES_CONTAINER}" psql \
	-h 127.0.0.1 -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" -X -q -A -t \
	-c "SELECT json_build_object(
		'database', current_database(),
		'address', inet_server_addr()::text,
		'port', inet_server_port(),
		'postmasterStartTime', pg_postmaster_start_time()
	);" > "${PG_SERVER_PATH}"
docker exec "${REDIS_CONTAINER}" redis-cli --raw INFO server > "${REDIS_SERVER_PATH}"

POSTGRES_CONTAINER="${POSTGRES_CONTAINER}" REDIS_CONTAINER="${REDIS_CONTAINER}" \
PG_SERVER_PATH="${PG_SERVER_PATH}" REDIS_SERVER_PATH="${REDIS_SERVER_PATH}" \
node -e '
	const fs = require("node:fs");
	const assert = require("node:assert/strict");
	const postgresServer = JSON.parse(fs.readFileSync(process.env.PG_SERVER_PATH, "utf8"));
	const redisInfo = fs.readFileSync(process.env.REDIS_SERVER_PATH, "utf8");
	const redisValue = (name) => {
		const match = redisInfo.match(new RegExp(`^${name}:(.+)\\r?$`, "m"));
		assert.ok(match, `Redis INFO server missing ${name}`);
		return match[1].trim();
	};
	const positiveInteger = (value, name) => {
		assert.match(value, /^[1-9][0-9]*$/, `${name} must be a positive integer`);
		return Number(value);
	};
	assert.equal(postgresServer.database, process.env.POSTGRES_DB);
	assert.match(postgresServer.address, /^\d{1,3}(\.\d{1,3}){3}$|^[0-9a-f:]+$/i);
	assert.ok(Number.isInteger(postgresServer.port) && postgresServer.port > 0);
	assert.match(process.env.PG_CONTAINER_HOST_PORT, /^[1-9][0-9]*$/);
	assert.match(process.env.REDIS_CONTAINER_HOST_PORT, /^[1-9][0-9]*$/);
	assert.ok(Number.isFinite(Date.parse(postgresServer.postmasterStartTime)));
	const identity = {
		capturedAt: new Date().toISOString(),
		postgres: {
			container: {
				name: process.env.POSTGRES_CONTAINER,
				id: process.env.PG_CONTAINER_ID,
				imageId: process.env.PG_CONTAINER_IMAGE_ID,
				startedAt: process.env.PG_CONTAINER_STARTED_AT,
				composeProject: process.env.PG_COMPOSE_PROJECT,
				composeService: process.env.PG_COMPOSE_SERVICE,
				composeConfigHash: process.env.PG_COMPOSE_CONFIG_HASH,
				hostPort: Number(process.env.PG_CONTAINER_HOST_PORT),
			},
			server: postgresServer,
		},
		redis: {
			container: {
				name: process.env.REDIS_CONTAINER,
				id: process.env.REDIS_CONTAINER_ID,
				imageId: process.env.REDIS_CONTAINER_IMAGE_ID,
				startedAt: process.env.REDIS_CONTAINER_STARTED_AT,
				composeProject: process.env.REDIS_COMPOSE_PROJECT,
				composeService: process.env.REDIS_COMPOSE_SERVICE,
				composeConfigHash: process.env.REDIS_COMPOSE_CONFIG_HASH,
				hostPort: Number(process.env.REDIS_CONTAINER_HOST_PORT),
			},
			server: {
				runId: redisValue("run_id"),
				uptimeSeconds: positiveInteger(redisValue("uptime_in_seconds"), "Redis uptime"),
				port: positiveInteger(redisValue("tcp_port"), "Redis tcp_port"),
			},
		},
	};
	fs.writeFileSync(process.argv[1], `${JSON.stringify(identity)}\n`, { flag: "wx" });
' "${TEMP_PATH}"
mv "${TEMP_PATH}" "${OUTPUT_PATH}"
