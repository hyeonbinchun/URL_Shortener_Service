#!/bin/bash
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
CASSANDRA="$SCRIPT_DIR/../cassandra"
STORAGE="$SCRIPT_DIR/../storage"


echo "Deploying Cassandra..."
kubectl create namespace cassandra --dry-run=client -o yaml | kubectl apply -f -
kubectl apply -f "$STORAGE/ebs-gp3-storageclass.yaml"
kubectl apply -f "$CASSANDRA/cassandra-service.yaml"
kubectl apply -f "$CASSANDRA/cassandra-statefulset.yaml"

echo "Waiting for Cassandra pods to be ready (this takes a few minutes)..."
kubectl rollout status statefulset/cassandra -n cassandra --timeout=360s

echo "Cassandra is ready."