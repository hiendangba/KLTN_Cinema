#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 2 ]]; then
  echo "Usage: $0 <image_repo> <tag>"
  echo "Example: $0 mydockerhub dev-2026-04-12"
  exit 1
fi

IMAGE_REPO="$1"
IMAGE_TAG="$2"

build_and_push() {
  local service_name="$1"
  local dockerfile_path="$2"
  local image="${IMAGE_REPO}/${service_name}:${IMAGE_TAG}"

  echo ">>> Building ${image}"
  docker build -t "${image}" -f "${dockerfile_path}" .
  echo ">>> Pushing ${image}"
  docker push "${image}"
}

build_and_push "identity-service" "./identity-service/Dockerfile"
build_and_push "user-service" "./user-service/Dockerfile"
build_and_push "film-service" "./film-service/Dockerfile"
build_and_push "showtime-service" "./showtime-service/Dockerfile"
build_and_push "hall-services" "./hall-service/Dockerfile"
build_and_push "email-service" "./email-service/Dockerfile"

echo "Done. Export these vars on server before deploy:"
echo "  export IMAGE_REPO=${IMAGE_REPO}"
echo "  export IMAGE_TAG=${IMAGE_TAG}"
