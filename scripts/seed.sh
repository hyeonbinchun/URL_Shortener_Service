#!/bin/bash
# inserts 10,000 URLs via API

BASE_URL="http://172.31.35.239:30000"
TOTAL=10000

echo "Seeding $TOTAL URLs..."

for i in $(seq 1 $TOTAL); do
  curl -s -o /dev/null -X PUT "$BASE_URL/?short=short$i&long=https://long-url.com/destination/$i"

  # Progress every 1000
  if (( i % 1000 == 0 )); then
    echo "  $i / $TOTAL inserted"
  fi
done

echo "Done."