#!/bin/bash
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REDIS="$SCRIPT_DIR/../redis"

echo "Deploying Redis..."
kubectl create namespace redis --dry-run=client -o yaml | kubectl apply -f -

kubectl apply -f "$REDIS/redis-service.yaml"
kubectl apply -f "$REDIS/redis-primary-deployment.yaml"
kubectl apply -f "$REDIS/redis-replica-deployment.yaml"

kubectl rollout status deployment/redis-primary-deployment -n redis --timeout=120s
kubectl rollout status deployment/redis-replica-deployment -n redis --timeout=120s
echo "Redis is ready."
