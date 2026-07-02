#!/usr/bin/env bash
set -euo pipefail

COMPOSE_FILE="${COMPOSE_FILE:-compose.yaml}"
PROJECT_NAME="${COMPOSE_PROJECT_NAME:-booktown-backend}"
APP_HEALTH_URL="${APP_HEALTH_URL:-http://localhost:8080/api/v1/health/readiness}"
GRAFANA_URL="${GRAFANA_URL:-http://localhost:3001/api/health}"
PROMETHEUS_URL="${PROMETHEUS_URL:-http://localhost:9090/-/ready}"

usage() {
  cat <<'USAGE'
Usage: scripts/local-infra.sh <command>

Commands:
  up          Start local dependency containers.
  up-app      Start dependencies and backend container.
  monitor     Start dependencies, backend, Prometheus, Grafana, Loki, and exporters.
  down        Stop containers without deleting volumes.
  clean       Stop containers and delete local volumes.
  ps          Show compose service status.
  logs        Follow backend logs.
  health      Check app, Prometheus, and Grafana readiness endpoints.

Environment:
  COMPOSE_FILE              Compose file path. Default: compose.yaml
  COMPOSE_PROJECT_NAME      Compose project name. Default: booktown-backend
  APP_HEALTH_URL            App readiness URL. Default: http://localhost:8080/api/v1/health/readiness
USAGE
}

compose() {
  docker compose -p "${PROJECT_NAME}" -f "${COMPOSE_FILE}" "$@"
}

wait_for_url() {
  local name="$1"
  local url="$2"
  local retries="${3:-30}"
  local delay="${4:-3}"

  for attempt in $(seq 1 "${retries}"); do
    if curl -fsS "${url}" >/dev/null 2>&1; then
      echo "OK ${name}: ${url}"
      return 0
    fi

    echo "WAIT ${name}: attempt ${attempt}/${retries}"
    sleep "${delay}"
  done

  echo "FAIL ${name}: ${url}" >&2
  return 1
}

command="${1:-}"

case "${command}" in
  up)
    compose up -d mysql redis mongodb chroma
    compose ps
    ;;
  up-app)
    compose --profile app up -d
    wait_for_url "backend" "${APP_HEALTH_URL}" 40 3
    ;;
  monitor)
    compose --profile app --profile monitoring up -d
    wait_for_url "backend" "${APP_HEALTH_URL}" 40 3
    wait_for_url "prometheus" "${PROMETHEUS_URL}" 20 3
    wait_for_url "grafana" "${GRAFANA_URL}" 20 3
    ;;
  down)
    compose --profile app --profile monitoring down
    ;;
  clean)
    compose --profile app --profile monitoring down -v
    ;;
  ps)
    compose --profile app --profile monitoring ps
    ;;
  logs)
    compose logs -f backend
    ;;
  health)
    wait_for_url "backend" "${APP_HEALTH_URL}" 1 1
    wait_for_url "prometheus" "${PROMETHEUS_URL}" 1 1
    wait_for_url "grafana" "${GRAFANA_URL}" 1 1
    ;;
  -h|--help|help)
    usage
    ;;
  *)
    usage
    exit 1
    ;;
esac
