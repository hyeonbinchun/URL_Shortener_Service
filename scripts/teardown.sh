#!/bin/bash
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
CASSANDRA="$SCRIPT_DIR/../cassandra"
REDIS="$SCRIPT_DIR/../redis"
SPRING="$SCRIPT_DIR/../URLShortener"

echo "Tearing down Spring Boot..."
kubectl delete -f "$SPRING/spring-deployment.yaml" --ignore-not-found
kubectl delete -f "$SPRING/spring-service.yaml" --ignore-not-found

echo "Tearing down Redis..."
kubectl delete -f "$REDIS/redis-replica-deployment.yaml" --ignore-not-found
kubectl delete -f "$REDIS/redis-primary-deployment.yaml" --ignore-not-found
kubectl delete -f "$REDIS/redis-service.yaml" --ignore-not-found

echo "Tearing down Cassandra..."
kubectl delete -f "$CASSANDRA/cassandra-statefulset.yaml" --ignore-not-found
kubectl delete -f "$CASSANDRA/cassandra-service.yaml" --ignore-not-found

# PVCs are intentionally not deleted by kubectl delete -f
# Uncomment below if you want to wipe Cassandra data too
# kubectl delete pvc -n cassandra --all

# Optional: Check if all resources are deleted
# kubectl get all -A