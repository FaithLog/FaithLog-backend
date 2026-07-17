#!/usr/bin/env bash
set -euo pipefail
umask 077

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/guard-runtime.sh"
source "${SCRIPT_DIR}/runner-lifecycle.sh"

: "${PERF_SNAPSHOT_ROOT:?Set the fresh absolute snapshot root.}"
: "${PERF_SNAPSHOT_ID:?Set a fresh snapshot ID.}"
: "${PERF_SNAPSHOT_RECEIPT_PATH:?Set the snapshot-receipt.json output path.}"
: "${PERF_REDIS_DATABASE:?Set the dedicated workload Redis database.}"
: "${PERF_REDIS_SNAPSHOT_DATABASE:?Set the fresh dedicated snapshot Redis database.}"
if [[ "${PERF_SNAPSHOT_ROOT}" != /* \
	|| "${PERF_SNAPSHOT_RECEIPT_PATH}" != "${PERF_SNAPSHOT_ROOT}/snapshot-receipt.json" \
	|| ! "${PERF_SNAPSHOT_ID}" =~ ^[A-Za-z0-9][A-Za-z0-9_-]{0,79}$ \
	|| ! "${PERF_REDIS_DATABASE}" =~ ^[0-9]+$ \
	|| ! "${PERF_REDIS_SNAPSHOT_DATABASE}" =~ ^[0-9]+$ \
	|| "${PERF_REDIS_DATABASE}" == "${PERF_REDIS_SNAPSHOT_DATABASE}" ]]; then
	echo "Snapshot paths, ID, and distinct Redis databases violate the #198 contract." >&2
	exit 2
fi

PAYLOAD_DIR="${PERF_SNAPSHOT_ROOT}/payload"
POSTGRES_DUMP_PATH="${PAYLOAD_DIR}/postgres.dump"
POSTGRES_FINGERPRINT_PATH="${PAYLOAD_DIR}/postgres-fingerprint.json"
REDIS_FINGERPRINT_PATH="${PAYLOAD_DIR}/redis-fingerprint.json"
PERF_GLOBAL_LOCK_DIR=""
PERF_PROJECT_LOCK_DIR=""
PERF_GLOBAL_LOCK_HELD=false
PERF_PROJECT_LOCK_HELD=false
snapshot_cleanup() {
	local exit_code=$?
	trap - EXIT
	release_notification_batch_locks
	exit "${exit_code}"
}
install_notification_batch_signal_traps snapshot_cleanup

node "${SCRIPT_DIR}/verify-current-develop-contract.mjs" >/dev/null
guard_notification_batch_runtime
if [[ ! "${PERF_COMPOSE_PROJECT}" =~ ^faithlog-perf-198($|-[A-Za-z0-9_-]+$) \
	|| "${PERF_COMPOSE_PROJECT}" =~ (shared|latest|frontend|qa) ]]; then
	echo "Snapshot capture requires a dedicated #198 isolated Compose project." >&2
	exit 2
fi
acquire_notification_batch_locks
if ! mkdir "${PAYLOAD_DIR}" 2>/dev/null; then
	echo "Snapshot payload namespace already exists; use a fresh batch ID." >&2
	exit 2
fi
verify_notification_batch_runtime_after_lock "${PAYLOAD_DIR}/runtime-identity-capture.json"

PGPASSWORD="${POSTGRES_PASSWORD}" docker exec -e PGPASSWORD "${PERF_POSTGRES_CONTAINER_ID}" pg_dump \
	-h 127.0.0.1 -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" \
	--format=custom --no-owner --no-privileges > "${POSTGRES_DUMP_PATH}"
PGPASSWORD="${POSTGRES_PASSWORD}" docker exec -e PGPASSWORD -i "${PERF_POSTGRES_CONTAINER_ID}" psql \
	-h 127.0.0.1 -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" -X -q -A -t \
	< "${SCRIPT_DIR}/postgres-state-fingerprint.sql" > "${POSTGRES_FINGERPRINT_PATH}"

if [[ "${PERF_REDIS_AUTH_MODE}" == "password" ]]; then
	REDISCLI_AUTH="${REDIS_PASSWORD}" docker exec -i -e REDISCLI_AUTH \
		-e TARGET_DB="${PERF_REDIS_DATABASE}" -e SNAPSHOT_DB="${PERF_REDIS_SNAPSHOT_DATABASE}" \
		"${PERF_REDIS_CONTAINER_ID}" sh -eu -c \
		'script=$(cat)
		test "$(redis-cli --no-auth-warning --raw -n "$SNAPSHOT_DB" DBSIZE)" = 0
		redis-cli --no-auth-warning --raw -n "$TARGET_DB" EVAL "$script" 0
		for key in __faithlog_198_snapshot_meta __faithlog_198_snapshot_values __faithlog_198_snapshot_ttls; do
			test "$(redis-cli --no-auth-warning --raw -n "$TARGET_DB" COPY "$key" "$key" DB "$SNAPSHOT_DB")" = 1
		done
		redis-cli --no-auth-warning --raw -n "$TARGET_DB" DEL \
			__faithlog_198_snapshot_meta __faithlog_198_snapshot_values __faithlog_198_snapshot_ttls >/dev/null' \
		< "${SCRIPT_DIR}/redis-capture-snapshot.lua" > "${REDIS_FINGERPRINT_PATH}"
else
	docker exec -i -e TARGET_DB="${PERF_REDIS_DATABASE}" -e SNAPSHOT_DB="${PERF_REDIS_SNAPSHOT_DATABASE}" \
		"${PERF_REDIS_CONTAINER_ID}" sh -eu -c \
		'script=$(cat)
		test "$(redis-cli --raw -n "$SNAPSHOT_DB" DBSIZE)" = 0
		redis-cli --raw -n "$TARGET_DB" EVAL "$script" 0
		for key in __faithlog_198_snapshot_meta __faithlog_198_snapshot_values __faithlog_198_snapshot_ttls; do
			test "$(redis-cli --raw -n "$TARGET_DB" COPY "$key" "$key" DB "$SNAPSHOT_DB")" = 1
		done
		redis-cli --raw -n "$TARGET_DB" DEL \
			__faithlog_198_snapshot_meta __faithlog_198_snapshot_values __faithlog_198_snapshot_ttls >/dev/null' \
		< "${SCRIPT_DIR}/redis-capture-snapshot.lua" > "${REDIS_FINGERPRINT_PATH}"
fi

PERF_POSTGRES_DUMP_PATH="${POSTGRES_DUMP_PATH}" \
	PERF_POSTGRES_FINGERPRINT_PATH="${POSTGRES_FINGERPRINT_PATH}" \
	PERF_REDIS_FINGERPRINT_PATH="${REDIS_FINGERPRINT_PATH}" \
	PERF_SNAPSHOT_RECEIPT_PATH="${PERF_SNAPSHOT_RECEIPT_PATH}" \
	PERF_SNAPSHOT_ID="${PERF_SNAPSHOT_ID}" \
	PERF_COMPOSE_PROJECT="${PERF_COMPOSE_PROJECT}" \
	POSTGRES_DB="${POSTGRES_DB}" \
	PERF_REDIS_DATABASE="${PERF_REDIS_DATABASE}" \
	PERF_REDIS_SNAPSHOT_DATABASE="${PERF_REDIS_SNAPSHOT_DATABASE}" \
	node "${SCRIPT_DIR}/state-snapshot-receipt.mjs" capture

echo "Snapshot receipt: ${PERF_SNAPSHOT_RECEIPT_PATH}"
