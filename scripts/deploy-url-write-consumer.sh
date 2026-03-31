#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
CONSUMER="$SCRIPT_DIR/../url-write-consumer"

build_image() {
	local image_name="$1"
	local context_dir="$2"

	echo "Building ${image_name} image..."
	docker build -t "$image_name" "$context_dir"
}

if [[ "${SKIP_IMAGE_BUILD:-0}" != "1" ]]; then
	build_image url-write-consumer "$CONSUMER"
fi

echo "Deploying URL write consumer..."
kubectl apply -f "$CONSUMER/consumer-deployment.yaml"

kubectl rollout status deployment/consumer-deployment --timeout=120s
echo "URL write consumer is ready."
