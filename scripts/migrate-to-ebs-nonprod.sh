#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

echo "[1/6] Applying EBS StorageClass"
kubectl apply -f "$SCRIPT_DIR/../storage/ebs-gp3-storageclass.yaml"

echo "[2/6] Making ebs-csi-gp3 the default StorageClass (k3s local-path remains installed)"
kubectl patch storageclass local-path -p '{"metadata":{"annotations":{"storageclass.kubernetes.io/is-default-class":"false"}}}' || true
kubectl patch storageclass ebs-csi-gp3 -p '{"metadata":{"annotations":{"storageclass.kubernetes.io/is-default-class":"true"}}}'

echo "[3/6] Recreating Redis stateful data on EBS"
kubectl delete statefulset redis -n redis --ignore-not-found --wait=true
kubectl delete pvc -n redis --all --ignore-not-found

# Sentinel config is ephemeral (emptyDir), but restart for clean cluster bootstrap.
kubectl delete statefulset redis-sentinel -n redis --ignore-not-found --wait=true

echo "[4/6] Recreating Cassandra stateful data on EBS (non-production destructive reset)"
kubectl delete statefulset cassandra -n cassandra --ignore-not-found --wait=true
kubectl delete pvc -n cassandra --all --ignore-not-found

echo "[5/6] Re-deploying Cassandra and Redis"
"$SCRIPT_DIR/deploy-cassandra.sh"
"$SCRIPT_DIR/deploy-redis.sh"

echo "[6/6] Verifying PVCs are bound to EBS"
kubectl get pvc -n cassandra
kubectl get pvc -n redis

echo "Done. Validate storage classes with: kubectl get pvc -A -o custom-columns=NAMESPACE:.metadata.namespace,NAME:.metadata.name,SC:.spec.storageClassName,STATUS:.status.phase"
