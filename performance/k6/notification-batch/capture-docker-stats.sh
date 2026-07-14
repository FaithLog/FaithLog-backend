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
printf '%s,%s,%s,%s\n' "${CAPTURED_AT}" "${POSTGRES_CONTAINER}" \
	"${POSTGRES_OBSERVED_ID}" "${POSTGRES_STATS}" >> "${OUTPUT_PATH}"
printf '%s,%s,%s,%s\n' "${CAPTURED_AT}" "${REDIS_CONTAINER}" \
	"${REDIS_OBSERVED_ID}" "${REDIS_STATS}" >> "${OUTPUT_PATH}"
