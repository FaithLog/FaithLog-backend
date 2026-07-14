#!/usr/bin/env bash

notification_batch_runner_cleanup() {
	if [[ -n "${SAMPLER_MARKER:-}" ]]; then
		rm -f "${SAMPLER_MARKER}"
	fi
	if [[ -n "${SAMPLER_PID:-}" ]]; then
		kill "${SAMPLER_PID}" 2>/dev/null || true
		wait "${SAMPLER_PID}" 2>/dev/null || true
	fi
	release_notification_batch_locks
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
