#!/usr/bin/env bash

guard_notification_batch_runtime() {
	: "${ALLOW_NOTIFICATION_BATCH_BASELINE:?Set ALLOW_NOTIFICATION_BATCH_BASELINE=true explicitly.}"
	: "${PERF_SPRING_PROFILE:?Set PERF_SPRING_PROFILE=local.}"
	: "${PERF_FCM_ADAPTER:?Set PERF_FCM_ADAPTER=fake explicitly.}"
	: "${PERF_EXPECTED_COMPOSE_PROJECT:?Set the user-approved dedicated Compose project.}"
	: "${POSTGRES_CONTAINER:?Set the dedicated PostgreSQL container name.}"
	: "${REDIS_CONTAINER:?Set the dedicated Redis container name.}"

	if [[ "${ALLOW_NOTIFICATION_BATCH_BASELINE}" != "true" ]]; then
		echo "ALLOW_NOTIFICATION_BATCH_BASELINE must be exactly true." >&2
		return 2
	fi
	case "${PERF_SPRING_PROFILE}" in
		local) ;;
		docker|prod)
			echo "Shared Docker and production profiles are forbidden for #198." >&2
			return 2
			;;
		*)
			echo "Only the local profile with a test fake sender is allowed for #198." >&2
			return 2
			;;
	esac
	if [[ "${PERF_FCM_ADAPTER}" != "fake" ]]; then
		echo "PERF_FCM_ADAPTER must be fake; external FCM is forbidden." >&2
		return 2
	fi
	if [[ -n "${FIREBASE_CONFIG_JSON:-}" || -n "${FIREBASE_CONFIG_PATH:-}" ]]; then
		echo "FIREBASE_CONFIG_JSON and FIREBASE_CONFIG_PATH must be empty." >&2
		return 2
	fi
	if [[ ! "${PERF_EXPECTED_COMPOSE_PROJECT}" =~ ^[a-zA-Z0-9][a-zA-Z0-9_-]*$ ]]; then
		echo "PERF_EXPECTED_COMPOSE_PROJECT must be an explicit safe project name." >&2
		return 2
	fi
	if [[ ! "${POSTGRES_CONTAINER}" =~ ^[a-zA-Z0-9][a-zA-Z0-9_.-]*$ \
		|| ! "${REDIS_CONTAINER}" =~ ^[a-zA-Z0-9][a-zA-Z0-9_.-]*$ ]]; then
		echo "Container names must contain only safe Docker name characters." >&2
		return 2
	fi
	export POSTGRES_CONTAINER REDIS_CONTAINER

	local postgres_project
	local redis_project
	postgres_project="$(docker inspect --format '{{ index .Config.Labels "com.docker.compose.project" }}' "${POSTGRES_CONTAINER}")"
	redis_project="$(docker inspect --format '{{ index .Config.Labels "com.docker.compose.project" }}' "${REDIS_CONTAINER}")"
	if [[ -z "${postgres_project}" || "${postgres_project}" == "<no value>" \
		|| "${postgres_project}" == "null" || "${postgres_project}" != "${redis_project}" ]]; then
		echo "PostgreSQL and Redis must expose one matching Compose project label." >&2
		return 2
	fi
	if [[ "${postgres_project}" != "${PERF_EXPECTED_COMPOSE_PROJECT}" ]]; then
		echo "Actual Compose label does not match PERF_EXPECTED_COMPOSE_PROJECT." >&2
		return 2
	fi
	if [[ "${postgres_project}" == *"faithlog-latest"* ]]; then
		echo "The shared faithlog-latest stack is forbidden for #198." >&2
		return 2
	fi

	export PERF_COMPOSE_PROJECT="${postgres_project}"
}

acquire_notification_batch_locks() {
	: "${PERF_COMPOSE_PROJECT:?guard_notification_batch_runtime must run before lock acquisition.}"
	PERF_GLOBAL_LOCK_DIR="/tmp/faithlog-performance-global.lock"
	PERF_PROJECT_LOCK_DIR="/tmp/faithlog-performance-${PERF_COMPOSE_PROJECT}.lock"
	PERF_GLOBAL_LOCK_HELD=false
	PERF_PROJECT_LOCK_HELD=false
	if ! mkdir "${PERF_GLOBAL_LOCK_DIR}" 2>/dev/null; then
		echo "Another Issue #198 fixture or measurement holds the host-global lock." >&2
		return 2
	fi
	PERF_GLOBAL_LOCK_HELD=true
	if ! mkdir "${PERF_PROJECT_LOCK_DIR}" 2>/dev/null; then
		rmdir "${PERF_GLOBAL_LOCK_DIR}" 2>/dev/null || true
		PERF_GLOBAL_LOCK_HELD=false
		echo "Another FaithLog QA or performance runner holds the canonical Compose-project lock." >&2
		return 2
	fi
	PERF_PROJECT_LOCK_HELD=true
	export PERF_GLOBAL_LOCK_DIR PERF_PROJECT_LOCK_DIR PERF_GLOBAL_LOCK_HELD PERF_PROJECT_LOCK_HELD
}

verify_notification_batch_runtime_after_lock() {
	: "${PERF_PROJECT_LOCK_DIR:?Canonical project lock must be acquired before runtime verification.}"
	: "${PERF_GLOBAL_LOCK_DIR:?Host-global lock must be acquired before runtime verification.}"
	local output_path="${1:?Locked runtime identity output path is required.}"
	if [[ "${PERF_PROJECT_LOCK_HELD:-false}" != "true" \
		|| "${PERF_GLOBAL_LOCK_HELD:-false}" != "true" \
		|| ! -d "${PERF_PROJECT_LOCK_DIR}" || ! -d "${PERF_GLOBAL_LOCK_DIR}" ]]; then
		echo "Both performance locks must still be held during post-lock runtime verification." >&2
		return 2
	fi
	local guard_script_dir
	guard_script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
	if ! bash "${guard_script_dir}/capture-runtime-identity.sh" "${output_path}"; then
		return 2
	fi
	local locked_exports
	if ! locked_exports="$(node -e '
		const fs = require("node:fs");
		const identity = JSON.parse(fs.readFileSync(process.argv[1], "utf8"));
		const values = {
			PERF_POSTGRES_CONTAINER_ID: identity.postgres.container.id,
			PERF_REDIS_CONTAINER_ID: identity.redis.container.id,
			PERF_POSTGRES_IMAGE_ID: identity.postgres.container.imageId,
			PERF_REDIS_IMAGE_ID: identity.redis.container.imageId,
			PERF_POSTGRES_HOST_PORT: identity.postgres.container.hostPort,
			PERF_REDIS_HOST_PORT: identity.redis.container.hostPort,
		};
		for (const [key, value] of Object.entries(values)) {
			if (!String(value).match(/^[A-Za-z0-9:_.-]+$/)) throw new Error(`Unsafe locked runtime value: ${key}`);
			process.stdout.write(`export ${key}=${value}\n`);
		}
	' "${output_path}")"; then
		return 2
	fi
	eval "${locked_exports}"
}

release_notification_batch_locks() {
	if [[ "${PERF_PROJECT_LOCK_HELD:-false}" == "true" && -n "${PERF_PROJECT_LOCK_DIR:-}" ]]; then
		rmdir "${PERF_PROJECT_LOCK_DIR}" 2>/dev/null || true
		PERF_PROJECT_LOCK_HELD=false
	fi
	if [[ "${PERF_GLOBAL_LOCK_HELD:-false}" == "true" && -n "${PERF_GLOBAL_LOCK_DIR:-}" ]]; then
		rmdir "${PERF_GLOBAL_LOCK_DIR}" 2>/dev/null || true
		PERF_GLOBAL_LOCK_HELD=false
	fi
}
