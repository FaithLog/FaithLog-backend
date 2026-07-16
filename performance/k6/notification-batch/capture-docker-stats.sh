#!/usr/bin/env bash
set -euo pipefail

: "${POSTGRES_CONTAINER:?POSTGRES_CONTAINER is required.}"
: "${REDIS_CONTAINER:?REDIS_CONTAINER is required.}"
: "${PERF_POSTGRES_CONTAINER_ID:?PERF_POSTGRES_CONTAINER_ID is required.}"
: "${PERF_REDIS_CONTAINER_ID:?PERF_REDIS_CONTAINER_ID is required.}"

OUTPUT_PATH="${1:?Docker stats output path is required.}"
CAPTURED_AT="$(node -p 'new Date().toISOString()')"
POSTGRES_OBSERVED_ID="$(docker inspect --format '{{.Id}}' "${PERF_POSTGRES_CONTAINER_ID}")"
REDIS_OBSERVED_ID="$(docker inspect --format '{{.Id}}' "${PERF_REDIS_CONTAINER_ID}")"
if [[ "${POSTGRES_OBSERVED_ID}" != "${PERF_POSTGRES_CONTAINER_ID}" \
	|| "${REDIS_OBSERVED_ID}" != "${PERF_REDIS_CONTAINER_ID}" ]]; then
	echo "Docker stats target identity changed during sampling." >&2
	exit 2
fi
POSTGRES_STATS="$(docker stats --no-stream --format '{{.CPUPerc}},{{.MemUsage}},{{.MemPerc}}' \
	"${POSTGRES_OBSERVED_ID}")"
REDIS_STATS="$(docker stats --no-stream --format '{{.CPUPerc}},{{.MemUsage}},{{.MemPerc}}' \
	"${REDIS_OBSERVED_ID}")"
RESOURCE_OUTPUT_PATH="${OUTPUT_PATH}" CAPTURED_AT="${CAPTURED_AT}" RESOURCE_COMPONENT=postgres \
	RESOURCE_CONTAINER_NAME="${POSTGRES_CONTAINER}" RESOURCE_CONTAINER_ID="${POSTGRES_OBSERVED_ID}" \
	RESOURCE_DOCKER_STATS="${POSTGRES_STATS}" node "$(dirname "${BASH_SOURCE[0]}")/append-docker-stats.mjs"
RESOURCE_OUTPUT_PATH="${OUTPUT_PATH}" CAPTURED_AT="${CAPTURED_AT}" RESOURCE_COMPONENT=redis \
	RESOURCE_CONTAINER_NAME="${REDIS_CONTAINER}" RESOURCE_CONTAINER_ID="${REDIS_OBSERVED_ID}" \
	RESOURCE_DOCKER_STATS="${REDIS_STATS}" node "$(dirname "${BASH_SOURCE[0]}")/append-docker-stats.mjs"
# append-docker-stats.mjs emits memoryUsedBytes, memoryLimitBytes, and memoryPercent evidence.
