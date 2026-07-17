#!/usr/bin/env bash

notification_batch_require_runtime_inputs() {
	local input_name
	for input_name in "$@"; do
		if ! declare -p "${input_name}" >/dev/null 2>&1; then
			echo "${input_name} is required." >&2
			return 2
		fi
		if [[ -z "${!input_name}" ]]; then
			echo "${input_name} is required." >&2
			return 2
		fi
	done
}

notification_batch_runner_cleanup() {
	local status=$?
	trap - EXIT
	if (( status != 0 )) && [[ -n "${REJECTION_PATH:-}" ]]; then
		REJECTION_PATH="${REJECTION_PATH}" \
			REJECTION_STAGE="${REJECTION_STAGE:-preflight}" \
			REJECTION_REASON="${REJECTION_REASON:-runner-command-failed}" \
			REJECTION_EXIT_CODE="${status}" \
			node "${SCRIPT_DIR}/rejection-contract.mjs" >/dev/null 2>&1 || true
	fi
	if [[ -n "${SAMPLER_MARKER:-}" ]]; then
		rm -f "${SAMPLER_MARKER}"
	fi
	if [[ -n "${PRELOCK_HARNESS_SOURCE_PATH:-}" ]]; then
		rm -f "${PRELOCK_HARNESS_SOURCE_PATH}"
	fi
	if [[ -n "${SAMPLER_PID:-}" ]]; then
		kill "${SAMPLER_PID}" 2>/dev/null || true
		wait "${SAMPLER_PID}" 2>/dev/null || true
	fi
	release_notification_batch_locks
	exit "${status}"
}

notification_batch_runner_signal_exit() {
	local status="$1"
	trap - HUP INT TERM
	exit "${status}"
}

install_notification_batch_runner_traps() {
	install_notification_batch_signal_traps notification_batch_runner_cleanup
}

install_notification_batch_fixture_traps() {
	install_notification_batch_signal_traps notification_batch_fixture_cleanup
}

install_notification_batch_signal_traps() {
	local cleanup_function="${1:?Cleanup function is required.}"
	trap "${cleanup_function}" EXIT
	trap 'notification_batch_runner_signal_exit 129' HUP
	trap 'notification_batch_runner_signal_exit 130' INT
	trap 'notification_batch_runner_signal_exit 143' TERM
}
