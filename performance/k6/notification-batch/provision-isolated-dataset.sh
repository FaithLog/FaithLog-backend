#!/usr/bin/env bash
set -euo pipefail
umask 077

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPOSITORY_ROOT="$(cd "${SCRIPT_DIR}/../../.." && pwd)"
MIGRATION_ROOT="${REPOSITORY_ROOT}/src/main/resources/db/migration"
source "${SCRIPT_DIR}/guard-runtime.sh"
source "${SCRIPT_DIR}/runner-lifecycle.sh"

: "${PERF_SEED_RECEIPT_PATH:?Set a fresh absolute seed receipt path.}"
: "${PERF_DATASET_ID:?Set the deterministic PERFORMANCE_ dataset ID.}"
if [[ "${PERF_SEED_RECEIPT_PATH}" != /* \
	|| ! "${PERF_DATASET_ID}" =~ ^PERFORMANCE_[A-Za-z0-9_-]+$ \
	|| -e "${PERF_SEED_RECEIPT_PATH}" ]]; then
	echo "Seed receipt and dataset namespace must be fresh, absolute, and safe." >&2
	exit 2
fi

PERF_GLOBAL_LOCK_DIR=""
PERF_PROJECT_LOCK_DIR=""
PERF_GLOBAL_LOCK_HELD=false
PERF_PROJECT_LOCK_HELD=false
SEED_ROOT="$(dirname "${PERF_SEED_RECEIPT_PATH}")"
seed_cleanup() {
	local status=$?
	trap - EXIT
	rm -f "${PERF_SEED_RECEIPT_PATH}.raw.$$"
	release_notification_batch_locks
	exit "${status}"
}
install_notification_batch_signal_traps seed_cleanup

node "${SCRIPT_DIR}/verify-current-develop-contract.mjs" >/dev/null
guard_notification_batch_runtime
if [[ ! "${PERF_COMPOSE_PROJECT}" =~ ^faithlog-perf-198($|-[A-Za-z0-9_-]+$) \
	|| "${PERF_COMPOSE_PROJECT}" =~ (shared|latest|frontend|qa) ]]; then
	echo "Synthetic seed provision requires a dedicated #198 isolated Compose project." >&2
	exit 2
fi
acquire_notification_batch_locks
verify_notification_batch_runtime_after_lock "${SEED_ROOT}/runtime-identity-seed-locked.json"
bash "${SCRIPT_DIR}/capture-runtime-identity.sh" "${SEED_ROOT}/runtime-identity-seed-before.json"
RUNTIME_CONTINUITY_REPORT_PATH="${SEED_ROOT}/runtime-continuity-seed-before.json" \
	RUNTIME_IDENTITY_PHASES=seed-locked,seed-before RUN_DIR="${SEED_ROOT}" \
	node "${SCRIPT_DIR}/assert-runtime-continuity.mjs"

table_count="$(PGPASSWORD="${POSTGRES_PASSWORD}" docker exec -e PGPASSWORD "${PERF_POSTGRES_CONTAINER_ID}" psql \
	-h 127.0.0.1 -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" -X -q -A -t \
	-c "SELECT count(*) FROM pg_tables WHERE schemaname = 'public';")"
if [[ "${table_count}" != "0" ]]; then
	echo "Synthetic seed provision requires a schema-empty isolated database." >&2
	exit 2
fi

for migration in \
	V1__initial_schema.sql V2__add_poll_user_option_fields.sql \
	V3__split_active_coffee_payment_account_owner_scope.sql \
	V4__add_payment_account_soft_delete.sql V5__fix_fcm_token_active_uniqueness.sql \
	V6__add_user_deleted_at.sql V7__enforce_positive_charge_amount.sql \
	V8__add_meal_poll_settlement.sql V9__allow_multiple_active_coffee_duties.sql \
	V10__neutralize_coffee_template_accounts.sql V11__secure_supabase_data_api.sql; do
	PGPASSWORD="${POSTGRES_PASSWORD}" docker exec -e PGPASSWORD -i "${PERF_POSTGRES_CONTAINER_ID}" psql \
		-h 127.0.0.1 -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" -X -q -v ON_ERROR_STOP=1 \
		< "${MIGRATION_ROOT}/${migration}"
done

PGPASSWORD="${POSTGRES_PASSWORD}" docker exec -e PGPASSWORD -i "${PERF_POSTGRES_CONTAINER_ID}" psql \
	-h 127.0.0.1 -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" -X -q -A -t \
	-v dataset_id="${PERF_DATASET_ID}" -v compose_project="${PERF_COMPOSE_PROJECT}" \
	-v postgres_database="${POSTGRES_DB}" \
	< "${SCRIPT_DIR}/provision-isolated-dataset.sql" \
	| tail -n 1 > "${PERF_SEED_RECEIPT_PATH}.raw.$$"

node "${SCRIPT_DIR}/seed-contract.mjs" capture \
	"${PERF_SEED_RECEIPT_PATH}.raw.$$" "${SCRIPT_DIR}/current-develop-contract.json" \
	"${PERF_SEED_RECEIPT_PATH}"
rm -f "${PERF_SEED_RECEIPT_PATH}.raw.$$"
bash "${SCRIPT_DIR}/capture-runtime-identity.sh" "${SEED_ROOT}/runtime-identity-seed-after.json"
RUNTIME_CONTINUITY_REPORT_PATH="${SEED_ROOT}/runtime-continuity-seed-report.json" \
	RUNTIME_IDENTITY_PHASES=seed-locked,seed-before,seed-after RUN_DIR="${SEED_ROOT}" \
	node "${SCRIPT_DIR}/assert-runtime-continuity.mjs"
echo "Synthetic seed receipt: ${PERF_SEED_RECEIPT_PATH}"
