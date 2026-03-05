#!/bin/bash
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
CASSANDRA="$SCRIPT_DIR/../cassandra"


echo "Deploying Cassandra..."
kubectl apply -f "$CASSANDRA/cassandra-service.yaml"
kubectl apply -f "$CASSANDRA/cassandra-statefulset.yaml"

echo "Waiting for Cassandra pods to be ready (this takes a few minutes)..."
kubectl rollout status statefulset/cassandra -n cassandra --timeout=360s

echo "Cassandra is ready."
echo "Run ./deploy-spring.sh to start the Spring Boot app."