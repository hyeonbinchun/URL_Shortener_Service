#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
CONSUMER="$SCRIPT_DIR/../url-write-consumer"

echo "Deploying URL write consumer..."
kubectl apply -f "$CONSUMER/consumer-deployment.yaml"

kubectl rollout status deployment/consumer-deployment --timeout=120s
echo "URL write consumer is ready."
