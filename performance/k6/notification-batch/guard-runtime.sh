#!/usr/bin/env bash

guard_notification_batch_runtime() {
	: "${ALLOW_NOTIFICATION_BATCH_BASELINE:?Set ALLOW_NOTIFICATION_BATCH_BASELINE=true explicitly.}"
	: "${PERF_SPRING_PROFILE:?Set PERF_SPRING_PROFILE to local or test.}"
	: "${PERF_FCM_ADAPTER:?Set PERF_FCM_ADAPTER=fake explicitly.}"
	: "${POSTGRES_CONTAINER:?Set the dedicated PostgreSQL container name.}"
	: "${REDIS_CONTAINER:?Set the dedicated Redis container name.}"

	if [[ "${ALLOW_NOTIFICATION_BATCH_BASELINE}" != "true" ]]; then
		echo "ALLOW_NOTIFICATION_BATCH_BASELINE must be exactly true." >&2
		return 2
	fi
	case "${PERF_SPRING_PROFILE}" in
		local|test) ;;
		docker|prod)
			echo "Shared Docker and production profiles are forbidden for #198." >&2
			return 2
			;;
		*)
			echo "Only local|test profiles with a fake sender are allowed for #198." >&2
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

	local postgres_project
	local redis_project
	postgres_project="$(docker inspect --format '{{ index .Config.Labels "com.docker.compose.project" }}' "${POSTGRES_CONTAINER}")"
	redis_project="$(docker inspect --format '{{ index .Config.Labels "com.docker.compose.project" }}' "${REDIS_CONTAINER}")"
	if [[ -z "${postgres_project}" || "${postgres_project}" != "${redis_project}" ]]; then
		echo "PostgreSQL and Redis must expose one matching Compose project label." >&2
		return 2
	fi
	if [[ "${postgres_project}" == *"faithlog-latest"* ]]; then
		echo "The shared faithlog-latest stack is forbidden for #198." >&2
		return 2
	fi

	export PERF_COMPOSE_PROJECT="${postgres_project}"
}
