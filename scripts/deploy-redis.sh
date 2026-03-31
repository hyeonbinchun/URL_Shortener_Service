#!/bin/bash
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REDIS="$SCRIPT_DIR/../redis"
STORAGE="$SCRIPT_DIR/../storage"

echo "Deploying Redis..."
kubectl create namespace redis --dry-run=client -o yaml | kubectl apply -f -

kubectl apply -f "$STORAGE/ebs-gp3-storageclass.yaml"
kubectl apply -f "$REDIS/redis-service.yaml"
kubectl apply -f "$REDIS/redis-statefulset.yaml"
kubectl apply -f "$REDIS/redis-sentinel-configmap.yaml"
kubectl apply -f "$REDIS/redis-sentinel-service.yaml"
kubectl apply -f "$REDIS/redis-sentinel-statefulset.yaml"

kubectl rollout status statefulset/redis -n redis --timeout=120s
kubectl rollout status statefulset/redis-sentinel -n redis --timeout=120s
echo "Redis is ready."
