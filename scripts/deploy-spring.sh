#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

echo "deploy-spring.sh is deprecated. Use deploy-urlshortener.sh and deploy-url-write-consumer.sh."

"$SCRIPT_DIR/deploy-urlshortener.sh"
"$SCRIPT_DIR/deploy-url-write-consumer.sh"

echo "Application stack is ready."