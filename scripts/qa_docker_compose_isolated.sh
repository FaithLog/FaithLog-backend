#!/usr/bin/env bash
set -Eeuo pipefail

readonly DEFAULT_PROJECT_PREFIX="faithlog-qa"
readonly ISSUE_NUMBER="84"
readonly COMPOSE_SERVICES="postgres redis app"
readonly HEALTH_URL="http://localhost:8080/api/v1/health"

usage() {
  cat <<'USAGE'
Usage:
  scripts/qa_docker_compose_isolated.sh [--project-name NAME] [--suffix SUFFIX]

Environment:
  QA_COMPOSE_PROJECT          Full compose project name. Example: faithlog-qa-84
  QA_COMPOSE_SUFFIX           Suffix used when QA_COMPOSE_PROJECT is not set.
  QA_HEALTH_RETRIES           Health check retry count. Default: 60
  QA_HEALTH_INTERVAL_SECONDS  Seconds between health checks. Default: 2

Examples:
  scripts/qa_docker_compose_isolated.sh
  QA_COMPOSE_PROJECT=faithlog-qa-84 scripts/qa_docker_compose_isolated.sh
  scripts/qa_docker_compose_isolated.sh --suffix 84-manual

Behavior:
  - Starts postgres, redis, and app with docker compose -p <project>.
  - Checks GET /api/v1/health from inside the app container.
  - Stops only the same compose project with docker compose -p <project> down.
  - Does not delete Docker volumes.
USAGE
}

log() {
  printf '[qa-docker] %s\n' "$*"
}

fail() {
  printf '[qa-docker] ERROR: %s\n' "$*" >&2
  exit 1
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "required command not found: $1"
}

normalize_project_name() {
  printf '%s' "$1" | tr '[:upper:]' '[:lower:]'
}

validate_project_name() {
  case "$1" in
    *[!a-z0-9_-]* | "" | [!a-z0-9]*)
      fail "compose project name must start with a lowercase letter or digit and contain only lowercase letters, digits, underscores, or dashes: $1"
      ;;
  esac
}

print_existing_container_owner() {
  local container_name="$1"
  docker inspect \
    --format '{{ index .Config.Labels "com.docker.compose.project" }}' \
    "$container_name" 2>/dev/null || true
}

guard_fixed_container_names() {
  local names existing owner
  names="$(docker ps -a --format '{{.Names}}')"

  for existing in faithlog-postgres faithlog-redis faithlog-backend; do
    if printf '%s\n' "$names" | grep -Fxq "$existing"; then
      owner="$(print_existing_container_owner "$existing")"
      if [ -n "$owner" ]; then
        fail "container name '$existing' already exists for compose project '$owner'. Stop that project intentionally before QA; this script will not touch existing containers."
      fi
      fail "container name '$existing' already exists. Stop or inspect it intentionally before QA; this script will not touch existing containers."
    fi
  done
}

project_name_from_args() {
  local project_name="${QA_COMPOSE_PROJECT:-}"
  local suffix="${QA_COMPOSE_SUFFIX:-}"

  while [ "$#" -gt 0 ]; do
    case "$1" in
      --project-name|-p)
        [ "$#" -ge 2 ] || fail "$1 requires a value"
        project_name="$2"
        shift 2
        ;;
      --suffix)
        [ "$#" -ge 2 ] || fail "$1 requires a value"
        suffix="$2"
        shift 2
        ;;
      --help|-h)
        usage
        exit 0
        ;;
      *)
        fail "unknown argument: $1"
        ;;
    esac
  done

  if [ -z "$project_name" ]; then
    if [ -z "$suffix" ]; then
      suffix="${ISSUE_NUMBER}-$(date +%Y%m%d%H%M%S)-${RANDOM}"
    fi
    project_name="${DEFAULT_PROJECT_PREFIX}-${suffix}"
  fi

  normalize_project_name "$project_name"
}

wait_for_app_health() {
  local retries="${QA_HEALTH_RETRIES:-60}"
  local interval="${QA_HEALTH_INTERVAL_SECONDS:-2}"
  local attempt response

  log "waiting for app health: ${HEALTH_URL}"
  for attempt in $(seq 1 "$retries"); do
    response="$(
      docker compose -p "$PROJECT_NAME" exec -T app sh -c \
        "wget -qO- '${HEALTH_URL}' 2>/dev/null || curl -fsS '${HEALTH_URL}' 2>/dev/null" \
        2>/dev/null || true
    )"

    if printf '%s' "$response" | grep -q '"status":"UP"'; then
      log "health check passed on attempt ${attempt}: ${response}"
      return 0
    fi

    sleep "$interval"
  done

  docker compose -p "$PROJECT_NAME" logs --tail=120 app || true
  fail "app health check did not pass after ${retries} attempts"
}

cleanup() {
  local exit_code=$?
  if [ "${CLEANUP_STARTED:-false}" = "true" ]; then
    log "stopping compose project without deleting volumes: ${PROJECT_NAME}"
    docker compose -p "$PROJECT_NAME" down
  fi
  exit "$exit_code"
}

main() {
  for arg in "$@"; do
    case "$arg" in
      --help|-h)
        usage
        exit 0
        ;;
    esac
  done

  PROJECT_NAME="$(project_name_from_args "$@")"
  readonly PROJECT_NAME
  validate_project_name "$PROJECT_NAME"
  require_command docker

  log "compose project: ${PROJECT_NAME}"
  log "services: ${COMPOSE_SERVICES}"

  guard_fixed_container_names

  trap cleanup EXIT
  CLEANUP_STARTED=true

  log "starting isolated QA stack"
  docker compose -p "$PROJECT_NAME" up -d --build postgres redis app
  docker compose -p "$PROJECT_NAME" ps

  wait_for_app_health
  log "QA Docker compose isolation check completed"
}

main "$@"
