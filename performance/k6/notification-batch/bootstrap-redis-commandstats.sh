#!/usr/bin/env bash
set -euo pipefail
umask 077

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/guard-runtime.sh"
source "${SCRIPT_DIR}/runner-lifecycle.sh"

: "${PERF_BATCH_ID:?Set the fresh batch ID.}"
: "${PERF_REDIS_DATABASE:?Set the dedicated workload Redis database.}"
: "${PERF_REDIS_BOOTSTRAP_RECEIPT_PATH:?Set the fresh Redis bootstrap receipt path.}"
[[ "${PERF_BATCH_ID}" =~ ^[A-Za-z0-9][A-Za-z0-9_-]{0,30}$ ]]
[[ "${PERF_REDIS_DATABASE}" =~ ^[0-9]+$ ]]

PERF_GLOBAL_LOCK_DIR=""
PERF_PROJECT_LOCK_DIR=""
PERF_GLOBAL_LOCK_HELD=false
PERF_PROJECT_LOCK_HELD=false
bootstrap_cleanup() {
	local status=$?
	trap - EXIT
	release_notification_batch_locks
	exit "${status}"
}
install_notification_batch_signal_traps bootstrap_cleanup

guard_notification_batch_runtime
[[ "${PERF_COMPOSE_PROJECT}" =~ ^faithlog-perf-198($|-[A-Za-z0-9_-]+$) ]]
[[ ! "${PERF_COMPOSE_PROJECT}" =~ (shared|latest|frontend|qa) ]]
acquire_notification_batch_locks
verify_notification_batch_runtime_after_lock "${PERF_REDIS_BOOTSTRAP_RECEIPT_PATH}.runtime.json"

bootstrap_key="__faithlog_198_commandstats_bootstrap:${PERF_BATCH_ID}"
redis_command() {
	if [[ "${PERF_REDIS_AUTH_MODE}" == "password" ]]; then
		REDISCLI_AUTH="${REDIS_PASSWORD}" docker exec -e REDISCLI_AUTH "${PERF_REDIS_CONTAINER_ID}" \
			redis-cli --no-auth-warning --raw -n "${PERF_REDIS_DATABASE}" "$@"
	else
		docker exec "${PERF_REDIS_CONTAINER_ID}" redis-cli --raw -n "${PERF_REDIS_DATABASE}" "$@"
	fi
}
[[ "$(redis_command DBSIZE)" == "0" ]]
[[ "$(redis_command SET "${bootstrap_key}" 1)" == "OK" ]]
[[ "$(redis_command DEL "${bootstrap_key}")" == "1" ]]
dbsize_after="$(redis_command DBSIZE)"
[[ "${dbsize_after}" == "0" ]]
commandstats="$(redis_command INFO commandstats)"
set_calls="$(sed -nE 's/^cmdstat_set:calls=([0-9]+),.*/\1/p' <<<"${commandstats}" | tr -d '\r')"
del_calls="$(sed -nE 's/^cmdstat_del:calls=([0-9]+),.*/\1/p' <<<"${commandstats}" | tr -d '\r')"
[[ "${set_calls}" =~ ^[1-9][0-9]*$ && "${del_calls}" =~ ^[1-9][0-9]*$ ]]

PERF_REDIS_BOOTSTRAP_KEY="${bootstrap_key}" \
PERF_REDIS_BOOTSTRAP_DBSIZE_AFTER="${dbsize_after}" \
PERF_REDIS_BOOTSTRAP_SET_CALLS="${set_calls}" \
PERF_REDIS_BOOTSTRAP_DEL_CALLS="${del_calls}" \
node "${SCRIPT_DIR}/redis-commandstats-bootstrap-contract.mjs" capture \
	"${PERF_REDIS_BOOTSTRAP_RECEIPT_PATH}"
