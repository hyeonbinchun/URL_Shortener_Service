#!/bin/bash
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SPRING="$SCRIPT_DIR/../URLShortener"

echo "Deploying Spring Boot..."
kubectl apply -f "$SPRING/spring-service.yaml"
kubectl apply -f "$SPRING/spring-deployment.yaml"

kubectl rollout status deployment/spring-deployment --timeout=120s
echo "Spring Boot is ready."