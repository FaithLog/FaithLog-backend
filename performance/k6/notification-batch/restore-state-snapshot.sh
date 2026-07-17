#!/usr/bin/env bash
set -euo pipefail
umask 077

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/guard-runtime.sh"
source "${SCRIPT_DIR}/runner-lifecycle.sh"

: "${PERF_SNAPSHOT_ROOT:?Set the existing absolute snapshot root.}"
: "${PERF_SNAPSHOT_RECEIPT_PATH:?Set the existing snapshot receipt.}"
: "${PERF_RESTORE_RECEIPT_PATH:?Set a fresh restore-receipt path.}"
: "${PERF_RESTORE_ORDINAL:?Set the 1-based restore ordinal.}"
: "${PERF_SAMPLE_KIND:?Set warmup or measured.}"
: "${PERF_SAMPLE_INDEX:?Set the 1-based sample index.}"
: "${PERF_REDIS_DATABASE:?Set the dedicated workload Redis database.}"
: "${PERF_REDIS_SNAPSHOT_DATABASE:?Set the dedicated snapshot Redis database.}"
if [[ "${PERF_SNAPSHOT_ROOT}" != /* \
	|| "${PERF_SNAPSHOT_RECEIPT_PATH}" != "${PERF_SNAPSHOT_ROOT}/snapshot-receipt.json" \
	|| "${PERF_RESTORE_RECEIPT_PATH}" != "${PERF_SNAPSHOT_ROOT}/restores/"* \
	|| ! "${PERF_RESTORE_ORDINAL}" =~ ^[1-9][0-9]*$ \
	|| ! "${PERF_SAMPLE_INDEX}" =~ ^[1-9][0-9]*$ \
	|| ( "${PERF_SAMPLE_KIND}" != "warmup" && "${PERF_SAMPLE_KIND}" != "measured" ) \
	|| ! "${PERF_REDIS_DATABASE}" =~ ^[0-9]+$ \
	|| ! "${PERF_REDIS_SNAPSHOT_DATABASE}" =~ ^[0-9]+$ \
	|| "${PERF_REDIS_DATABASE}" == "${PERF_REDIS_SNAPSHOT_DATABASE}" \
	|| ! -f "${PERF_SNAPSHOT_RECEIPT_PATH}" ]]; then
	echo "Restore inputs violate the #198 snapshot contract." >&2
	exit 2
fi

POSTGRES_DUMP_PATH="${PERF_SNAPSHOT_ROOT}/payload/postgres.dump"
POSTGRES_FINGERPRINT_PATH="${PERF_SNAPSHOT_ROOT}/restores/.postgres-${PERF_RESTORE_ORDINAL}.json"
REDIS_FINGERPRINT_PATH="${PERF_SNAPSHOT_ROOT}/restores/.redis-${PERF_RESTORE_ORDINAL}.json"
REDIS_RESTORE_METADATA_PATH="${PERF_SNAPSHOT_ROOT}/restores/.redis-restore-${PERF_RESTORE_ORDINAL}.json"
PERF_GLOBAL_LOCK_DIR=""
PERF_PROJECT_LOCK_DIR=""
PERF_GLOBAL_LOCK_HELD=false
PERF_PROJECT_LOCK_HELD=false
restore_cleanup() {
	local exit_code=$?
	trap - EXIT
	release_notification_batch_locks
	exit "${exit_code}"
}
install_notification_batch_signal_traps restore_cleanup

node "${SCRIPT_DIR}/verify-current-develop-contract.mjs" >/dev/null
guard_notification_batch_runtime
if [[ ! "${PERF_COMPOSE_PROJECT}" =~ ^faithlog-perf-198($|-[A-Za-z0-9_-]+$) \
	|| "${PERF_COMPOSE_PROJECT}" =~ (shared|latest|frontend|qa) ]]; then
	echo "Snapshot restore requires a dedicated #198 isolated Compose project." >&2
	exit 2
fi
acquire_notification_batch_locks
verify_notification_batch_runtime_after_lock \
	"${PERF_SNAPSHOT_ROOT}/restores/runtime-identity-${PERF_RESTORE_ORDINAL}.json"
if [[ -e "${PERF_RESTORE_RECEIPT_PATH}" ]]; then
	echo "Restore receipt already exists; restore ordinals are never reused." >&2
	exit 2
fi

PGPASSWORD="${POSTGRES_PASSWORD}" docker exec -e PGPASSWORD -i "${PERF_POSTGRES_CONTAINER_ID}" pg_restore \
	-h 127.0.0.1 -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" \
	--clean --if-exists --exit-on-error --single-transaction --no-owner --no-privileges \
	< "${POSTGRES_DUMP_PATH}"

if [[ "${PERF_REDIS_AUTH_MODE}" == "password" ]]; then
	REDISCLI_AUTH="${REDIS_PASSWORD}" docker exec -i -e REDISCLI_AUTH \
		-e TARGET_DB="${PERF_REDIS_DATABASE}" -e SNAPSHOT_DB="${PERF_REDIS_SNAPSHOT_DATABASE}" \
		"${PERF_REDIS_CONTAINER_ID}" sh -eu -c \
		'script=$(cat)
		test "$(redis-cli --no-auth-warning --raw -n "$TARGET_DB" EXISTS \
			__faithlog_198_snapshot_meta __faithlog_198_snapshot_values __faithlog_198_snapshot_ttls)" = 0
		for key in __faithlog_198_snapshot_meta __faithlog_198_snapshot_values __faithlog_198_snapshot_ttls; do
			test "$(redis-cli --no-auth-warning --raw -n "$SNAPSHOT_DB" COPY "$key" "$key" DB "$TARGET_DB")" = 1
		done
		redis-cli --no-auth-warning --raw -n "$TARGET_DB" EVAL "$script" 0' \
		< "${SCRIPT_DIR}/redis-restore-snapshot.lua" > "${REDIS_RESTORE_METADATA_PATH}"
else
	docker exec -i -e TARGET_DB="${PERF_REDIS_DATABASE}" -e SNAPSHOT_DB="${PERF_REDIS_SNAPSHOT_DATABASE}" \
		"${PERF_REDIS_CONTAINER_ID}" sh -eu -c \
		'script=$(cat)
		test "$(redis-cli --raw -n "$TARGET_DB" EXISTS \
			__faithlog_198_snapshot_meta __faithlog_198_snapshot_values __faithlog_198_snapshot_ttls)" = 0
		for key in __faithlog_198_snapshot_meta __faithlog_198_snapshot_values __faithlog_198_snapshot_ttls; do
			test "$(redis-cli --raw -n "$SNAPSHOT_DB" COPY "$key" "$key" DB "$TARGET_DB")" = 1
		done
		redis-cli --raw -n "$TARGET_DB" EVAL "$script" 0' \
		< "${SCRIPT_DIR}/redis-restore-snapshot.lua" > "${REDIS_RESTORE_METADATA_PATH}"
fi

PGPASSWORD="${POSTGRES_PASSWORD}" docker exec -e PGPASSWORD -i "${PERF_POSTGRES_CONTAINER_ID}" psql \
	-h 127.0.0.1 -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" -X -q -A -t \
	< "${SCRIPT_DIR}/postgres-state-fingerprint.sql" > "${POSTGRES_FINGERPRINT_PATH}"
if [[ "${PERF_REDIS_AUTH_MODE}" == "password" ]]; then
	REDISCLI_AUTH="${REDIS_PASSWORD}" docker exec -i -e REDISCLI_AUTH -e TARGET_DB="${PERF_REDIS_DATABASE}" \
		"${PERF_REDIS_CONTAINER_ID}" sh -eu -c \
		'redis-cli --no-auth-warning --raw -n "$TARGET_DB" EVAL "$(cat)" 0' \
		< "${SCRIPT_DIR}/redis-state-fingerprint.lua" > "${REDIS_FINGERPRINT_PATH}"
else
	docker exec -i -e TARGET_DB="${PERF_REDIS_DATABASE}" "${PERF_REDIS_CONTAINER_ID}" sh -eu -c \
		'redis-cli --raw -n "$TARGET_DB" EVAL "$(cat)" 0' \
		< "${SCRIPT_DIR}/redis-state-fingerprint.lua" > "${REDIS_FINGERPRINT_PATH}"
fi

PERF_POSTGRES_FINGERPRINT_PATH="${POSTGRES_FINGERPRINT_PATH}" \
	PERF_REDIS_FINGERPRINT_PATH="${REDIS_FINGERPRINT_PATH}" \
	PERF_REDIS_RESTORE_METADATA_PATH="${REDIS_RESTORE_METADATA_PATH}" \
	PERF_SNAPSHOT_RECEIPT_PATH="${PERF_SNAPSHOT_RECEIPT_PATH}" \
	PERF_RESTORE_RECEIPT_PATH="${PERF_RESTORE_RECEIPT_PATH}" \
	PERF_RESTORE_ORDINAL="${PERF_RESTORE_ORDINAL}" \
	PERF_SAMPLE_KIND="${PERF_SAMPLE_KIND}" \
	PERF_SAMPLE_INDEX="${PERF_SAMPLE_INDEX}" \
	POSTGRES_DB="${POSTGRES_DB}" \
	PERF_REDIS_DATABASE="${PERF_REDIS_DATABASE}" \
	PERF_REDIS_SNAPSHOT_DATABASE="${PERF_REDIS_SNAPSHOT_DATABASE}" \
	node "${SCRIPT_DIR}/state-snapshot-receipt.mjs" restore

echo "Restore receipt: ${PERF_RESTORE_RECEIPT_PATH}"
