#!/usr/bin/env bash
set -euo pipefail

COMPOSE_FILE="${1:-compose.yaml}"

export COMPOSE_BAKE=false
export DOCKER_BUILDKIT=1

services=(
  identity-service
  user-service
  film-service
  showtime-service
  hall-services
  email-service
)

for svc in "${services[@]}"; do
  echo ">>> Building ${svc} using ${COMPOSE_FILE}"
  docker compose -f "${COMPOSE_FILE}" build "${svc}"
done

echo "All services built sequentially."
