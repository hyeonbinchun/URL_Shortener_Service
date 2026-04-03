#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

run_step() {
  local step_name="$1"
  local step_script="$2"

  echo "==> ${step_name}"
  "${SCRIPT_DIR}/${step_script}"
  echo "==> ${step_name} completed"
}

echo "Starting full deployment..."

run_step "Deploy Cassandra" "deploy-cassandra.sh"
run_step "Deploy Redis" "deploy-redis.sh"
run_step "Deploy URL Shortener API" "deploy-url-shortener.sh"
run_step "Deploy URL Write Consumer" "deploy-url-write-consumer.sh"

echo "Full deployment completed successfully."
