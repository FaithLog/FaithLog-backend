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

	local postgres_host_port
	local redis_host_port
	postgres_host_port="$(docker inspect --format '{{ (index (index .NetworkSettings.Ports "5432/tcp") 0).HostPort }}' "${POSTGRES_CONTAINER}")"
	redis_host_port="$(docker inspect --format '{{ (index (index .NetworkSettings.Ports "6379/tcp") 0).HostPort }}' "${REDIS_CONTAINER}")"
	if [[ ! "${postgres_host_port}" =~ ^[1-9][0-9]*$ || ! "${redis_host_port}" =~ ^[1-9][0-9]*$ ]]; then
		echo "Dedicated PostgreSQL and Redis must publish numeric host ports." >&2
		return 2
	fi

	export PERF_COMPOSE_PROJECT="${postgres_project}"
	export PERF_POSTGRES_HOST_PORT="${postgres_host_port}"
	export PERF_REDIS_HOST_PORT="${redis_host_port}"
	export PERF_POSTGRES_IMAGE_ID
	export PERF_REDIS_IMAGE_ID
	PERF_POSTGRES_IMAGE_ID="$(docker inspect --format '{{.Image}}' "${POSTGRES_CONTAINER}")"
	PERF_REDIS_IMAGE_ID="$(docker inspect --format '{{.Image}}' "${REDIS_CONTAINER}")"
}
