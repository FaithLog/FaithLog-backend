#!/usr/bin/env bash

guard_notification_batch_runtime() {
	: "${ALLOW_NOTIFICATION_BATCH_BASELINE:?Set ALLOW_NOTIFICATION_BATCH_BASELINE=true explicitly.}"
	: "${PERF_SPRING_PROFILE:?Set PERF_SPRING_PROFILE=local.}"
	: "${PERF_FCM_ADAPTER:?Set PERF_FCM_ADAPTER=fake explicitly.}"
	: "${PERF_EXPECTED_COMPOSE_PROJECT:?Set the user-approved dedicated Compose project.}"
	: "${POSTGRES_CONTAINER:?Set the dedicated PostgreSQL container name.}"
	: "${REDIS_CONTAINER:?Set the dedicated Redis container name.}"
	: "${PERF_EXPECTED_POSTGRES_CONTAINER_ID:?Set the approved full PostgreSQL container ID.}"
	: "${PERF_EXPECTED_REDIS_CONTAINER_ID:?Set the approved full Redis container ID.}"
	: "${PERF_EXPECTED_POSTGRES_SERVICE:?Set the approved PostgreSQL Compose service.}"
	: "${PERF_EXPECTED_REDIS_SERVICE:?Set the approved Redis Compose service.}"
	: "${PERF_EXPECTED_POSTGRES_IMAGE_ID:?Set the approved PostgreSQL image ID.}"
	: "${PERF_EXPECTED_REDIS_IMAGE_ID:?Set the approved Redis image ID.}"
	: "${POSTGRES_USER:?Set the runtime PostgreSQL user.}"
	: "${POSTGRES_PASSWORD:?Set the runtime-only PostgreSQL password.}"
	: "${POSTGRES_DB:?Set the runtime PostgreSQL database.}"
	: "${PERF_EXPECTED_POSTGRES_ROLE:?Set the approved direct owner JDBC role.}"
	: "${PERF_EXPECTED_POSTGRES_SERVER_ADDRESS:?Set the approved plain PostgreSQL loopback server address.}"
	: "${PERF_REDIS_AUTH_MODE:?Set PERF_REDIS_AUTH_MODE=none or password.}"

	local guard_script_dir
	guard_script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
	node "${guard_script_dir}/runtime-inet-contract.mjs" validate-approved \
		"${PERF_EXPECTED_POSTGRES_SERVER_ADDRESS}" >/dev/null

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
	case "${PERF_REDIS_AUTH_MODE}" in
		none)
			if [[ -n "${REDIS_PASSWORD:-}" ]]; then
				echo "REDIS_PASSWORD must be absent when PERF_REDIS_AUTH_MODE=none." >&2
				return 2
			fi
			;;
		password)
			: "${REDIS_PASSWORD:?Set the runtime-only Redis password.}"
			;;
		*)
			echo "PERF_REDIS_AUTH_MODE must be none or password." >&2
			return 2
			;;
	esac
	if [[ ! "${PERF_EXPECTED_COMPOSE_PROJECT}" =~ ^[a-zA-Z0-9][a-zA-Z0-9_-]*$ ]]; then
		echo "PERF_EXPECTED_COMPOSE_PROJECT must be an explicit safe project name." >&2
		return 2
	fi
	if [[ ! "${POSTGRES_CONTAINER}" =~ ^[a-zA-Z0-9][a-zA-Z0-9_.-]*$ \
		|| ! "${REDIS_CONTAINER}" =~ ^[a-zA-Z0-9][a-zA-Z0-9_.-]*$ ]]; then
		echo "Container names must contain only safe Docker name characters." >&2
		return 2
	fi
	if [[ "${POSTGRES_CONTAINER}" == "${REDIS_CONTAINER}" \
		|| ! "${PERF_EXPECTED_POSTGRES_CONTAINER_ID}" =~ ^[a-f0-9]{64}$ \
		|| ! "${PERF_EXPECTED_REDIS_CONTAINER_ID}" =~ ^[a-f0-9]{64}$ \
		|| "${PERF_EXPECTED_POSTGRES_CONTAINER_ID}" == "${PERF_EXPECTED_REDIS_CONTAINER_ID}" \
		|| ! "${PERF_EXPECTED_POSTGRES_IMAGE_ID}" =~ ^sha256:[a-f0-9]{64}$ \
		|| ! "${PERF_EXPECTED_REDIS_IMAGE_ID}" =~ ^sha256:[a-f0-9]{64}$ \
		|| ! "${PERF_EXPECTED_POSTGRES_SERVICE}" =~ ^[A-Za-z0-9][A-Za-z0-9_.-]*$ \
		|| ! "${PERF_EXPECTED_REDIS_SERVICE}" =~ ^[A-Za-z0-9][A-Za-z0-9_.-]*$ ]]; then
		echo "Runtime targets require distinct full container IDs, immutable image IDs, and safe services." >&2
		return 2
	fi
	export POSTGRES_CONTAINER REDIS_CONTAINER POSTGRES_USER POSTGRES_PASSWORD POSTGRES_DB
	export PERF_EXPECTED_POSTGRES_ROLE PERF_EXPECTED_POSTGRES_SERVER_ADDRESS PERF_REDIS_AUTH_MODE REDIS_PASSWORD
	export PERF_EXPECTED_POSTGRES_CONTAINER_ID PERF_EXPECTED_REDIS_CONTAINER_ID
	export PERF_EXPECTED_POSTGRES_SERVICE PERF_EXPECTED_REDIS_SERVICE
	export PERF_EXPECTED_POSTGRES_IMAGE_ID PERF_EXPECTED_REDIS_IMAGE_ID

	local postgres_project redis_project postgres_id redis_id postgres_service redis_service postgres_image redis_image
	local postgres_started redis_started postgres_config redis_config postgres_host_port redis_host_port
	postgres_project="$(docker inspect --format '{{ index .Config.Labels "com.docker.compose.project" }}' "${POSTGRES_CONTAINER}")"
	redis_project="$(docker inspect --format '{{ index .Config.Labels "com.docker.compose.project" }}' "${REDIS_CONTAINER}")"
	postgres_id="$(docker inspect --format '{{.Id}}' "${POSTGRES_CONTAINER}")"
	redis_id="$(docker inspect --format '{{.Id}}' "${REDIS_CONTAINER}")"
	postgres_service="$(docker inspect --format '{{ index .Config.Labels "com.docker.compose.service" }}' "${POSTGRES_CONTAINER}")"
	redis_service="$(docker inspect --format '{{ index .Config.Labels "com.docker.compose.service" }}' "${REDIS_CONTAINER}")"
	postgres_image="$(docker inspect --format '{{.Image}}' "${POSTGRES_CONTAINER}")"
	redis_image="$(docker inspect --format '{{.Image}}' "${REDIS_CONTAINER}")"
	postgres_started="$(docker inspect --format '{{.State.StartedAt}}' "${POSTGRES_CONTAINER}")"
	redis_started="$(docker inspect --format '{{.State.StartedAt}}' "${REDIS_CONTAINER}")"
	postgres_config="$(docker inspect --format '{{ index .Config.Labels "com.docker.compose.config-hash" }}' "${POSTGRES_CONTAINER}")"
	redis_config="$(docker inspect --format '{{ index .Config.Labels "com.docker.compose.config-hash" }}' "${REDIS_CONTAINER}")"
	postgres_host_port="$(docker inspect --format '{{ (index (index .NetworkSettings.Ports "5432/tcp") 0).HostPort }}' "${POSTGRES_CONTAINER}")"
	redis_host_port="$(docker inspect --format '{{ (index (index .NetworkSettings.Ports "6379/tcp") 0).HostPort }}' "${REDIS_CONTAINER}")"
	if [[ -z "${postgres_project}" || "${postgres_project}" == "<no value>" \
		|| "${postgres_project}" == "null" || "${postgres_project}" != "${redis_project}" ]]; then
		echo "PostgreSQL and Redis must expose one matching Compose project label." >&2
		return 2
	fi
	if [[ "${postgres_project}" != "${PERF_EXPECTED_COMPOSE_PROJECT}" ]]; then
		echo "Actual Compose label does not match PERF_EXPECTED_COMPOSE_PROJECT." >&2
		return 2
	fi
	if [[ "${postgres_project}" == *"faithlog-latest"* \
		|| ! "${postgres_project}" =~ ^faithlog-perf-198($|-[A-Za-z0-9_-]+$) \
		|| "${postgres_project}" =~ (shared|latest|frontend|qa) ]]; then
		echo "Only a dedicated #198 isolated Compose project is allowed; shared stacks are forbidden." >&2
		return 2
	fi
	if [[ "${postgres_id}" != "${PERF_EXPECTED_POSTGRES_CONTAINER_ID}" \
		|| "${redis_id}" != "${PERF_EXPECTED_REDIS_CONTAINER_ID}" \
		|| "${postgres_service}" != "${PERF_EXPECTED_POSTGRES_SERVICE}" \
		|| "${redis_service}" != "${PERF_EXPECTED_REDIS_SERVICE}" \
		|| "${postgres_image}" != "${PERF_EXPECTED_POSTGRES_IMAGE_ID}" \
		|| "${redis_image}" != "${PERF_EXPECTED_REDIS_IMAGE_ID}" ]]; then
		echo "Runtime container full ID, service, or image does not match the approved target." >&2
		return 2
	fi
	if [[ -z "${postgres_started}" || -z "${redis_started}" \
		|| -z "${postgres_config}" || -z "${redis_config}" \
		|| ! "${postgres_host_port}" =~ ^[1-9][0-9]*$ \
		|| ! "${redis_host_port}" =~ ^[1-9][0-9]*$ ]]; then
		echo "Pre-lock immutable runtime identity is incomplete." >&2
		return 2
	fi

	export PERF_COMPOSE_PROJECT="${postgres_project}"
	export PERF_PRELOCK_POSTGRES_STARTED_AT="${postgres_started}"
	export PERF_PRELOCK_REDIS_STARTED_AT="${redis_started}"
	export PERF_PRELOCK_POSTGRES_CONFIG_HASH="${postgres_config}"
	export PERF_PRELOCK_REDIS_CONFIG_HASH="${redis_config}"
	export PERF_PRELOCK_POSTGRES_HOST_PORT="${postgres_host_port}"
	export PERF_PRELOCK_REDIS_HOST_PORT="${redis_host_port}"
}

acquire_notification_batch_locks() {
	: "${PERF_COMPOSE_PROJECT:?guard_notification_batch_runtime must run before lock acquisition.}"
	if [[ -n "${PERF_ORCHESTRATION_LOCK_RECEIPT:-}" ]]; then
		local inherited_lock_exports
		if ! inherited_lock_exports="$(node -e '
			const fs = require("node:fs");
			const receipt = JSON.parse(fs.readFileSync(process.argv[1], "utf8"));
			const expectedPid = Number(process.argv[2]);
			const expectedProject = process.argv[3];
			if (receipt.schemaVersion !== 1 || receipt.ownerPid !== expectedPid
				|| receipt.composeProject !== expectedProject || receipt.automaticAdoption !== false) process.exit(2);
			const globalLock = "/tmp/faithlog-performance-global.lock";
			const projectLock = `/tmp/faithlog-performance-${expectedProject}.lock`;
			if (receipt.globalLockDir !== globalLock || receipt.projectLockDir !== projectLock
				|| !fs.statSync(globalLock).isDirectory() || !fs.statSync(projectLock).isDirectory()) process.exit(2);
			process.stdout.write(`PERF_GLOBAL_LOCK_DIR=${globalLock}\nPERF_PROJECT_LOCK_DIR=${projectLock}\n`);
		' "${PERF_ORCHESTRATION_LOCK_RECEIPT}" "${PPID}" "${PERF_COMPOSE_PROJECT}")"; then
			echo "Orchestration lock receipt does not match the parent process and Compose project." >&2
			return 2
		fi
		eval "${inherited_lock_exports}"
		PERF_GLOBAL_LOCK_HELD=true
		PERF_PROJECT_LOCK_HELD=true
		PERF_LOCK_RELEASE_OWNER=false
		export PERF_GLOBAL_LOCK_DIR PERF_PROJECT_LOCK_DIR PERF_GLOBAL_LOCK_HELD PERF_PROJECT_LOCK_HELD PERF_LOCK_RELEASE_OWNER
		return 0
	fi
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
	PERF_LOCK_RELEASE_OWNER=true
	export PERF_GLOBAL_LOCK_DIR PERF_PROJECT_LOCK_DIR PERF_GLOBAL_LOCK_HELD PERF_PROJECT_LOCK_HELD PERF_LOCK_RELEASE_OWNER
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
	if [[ "${PERF_LOCK_RELEASE_OWNER:-true}" != "true" ]]; then
		PERF_PROJECT_LOCK_HELD=false
		PERF_GLOBAL_LOCK_HELD=false
		return 0
	fi
	if [[ "${PERF_PROJECT_LOCK_HELD:-false}" == "true" && -n "${PERF_PROJECT_LOCK_DIR:-}" ]]; then
		rmdir "${PERF_PROJECT_LOCK_DIR}" 2>/dev/null || true
		PERF_PROJECT_LOCK_HELD=false
	fi
	if [[ "${PERF_GLOBAL_LOCK_HELD:-false}" == "true" && -n "${PERF_GLOBAL_LOCK_DIR:-}" ]]; then
		rmdir "${PERF_GLOBAL_LOCK_DIR}" 2>/dev/null || true
		PERF_GLOBAL_LOCK_HELD=false
	fi
}
