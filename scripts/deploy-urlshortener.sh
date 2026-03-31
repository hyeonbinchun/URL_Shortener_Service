#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SPRING="$SCRIPT_DIR/../URLShortener"

build_image() {
	local image_name="$1"
	local context_dir="$2"

	echo "Building ${image_name} image..."
	docker build -t "$image_name" "$context_dir"
}

if [[ "${SKIP_IMAGE_BUILD:-0}" != "1" ]]; then
	build_image spring-server "$SPRING"
fi

echo "Deploying Spring Boot API..."
kubectl apply -f "$SPRING/spring-service.yaml"
kubectl apply -f "$SPRING/spring-deployment.yaml"

kubectl rollout status deployment/spring-deployment --timeout=120s
echo "Spring Boot API is ready."
