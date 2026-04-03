# Testing Guide

---

## 1. Load Testing

Load tests are written in k6 and located in `loadTesting/`. All scripts target `BASE_URL` (configured inside each file — update to your service node's IP before running).

**Prerequisite**: seed 10,000 URLs so read tests have data to hit:
```bash
cd scripts && ./seed.sh
```

### Scenarios

| Script | Workload | VUs | Ramp-up | Steady | Ramp-down |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `read-test.js` | 100% reads (GET `/{short}`) | 1,000 | 2 min | 5 min | 1 min |
| `write-test.js` | 100% writes (PUT `/?short=…&long=…`) | 10,000 | 4 min | 7 min | 1 min |
| `mixed-test.js` | 90% reads / 10% writes | 1,000 | 2 min | 5 min | 1 min |

Run a scenario:
```bash
k6 run loadTesting/read-test.js
k6 run loadTesting/write-test.js
k6 run loadTesting/mixed-test.js
```

All requests use k6 tags (`type: read` / `type: write`) so results can be filtered per operation type in Grafana or the k6 summary output.

### VU Scaling Reference

These targets were used for progressive load experiments:

| VUs | Ramp-up | Steady | Ramp-down |
| :--- | :--- | :--- | :--- |
| 1,000 | 1 min | 3 min | 1 min |
| 2,000 | 2 min | 5 min | 1 min |
| 3,000 | 3 min | 7 min | 1 min |
| 5,000 | 4 min | 7 min | 1 min |
| 7,000 | 4 min | 7 min | 1 min |
| 10,000 | 5 min | 7 min | 1 min |
---

## 2. Fault Injection

### 2.1 Redis Sentinel Failover Test

This test verifies that the application continues to serve reads and writes after the Redis master pod is killed and Sentinel promotes a replica.

**1. Deploy (or redeploy) Redis with Sentinel:**
```bash
cd scripts && ./deploy-redis.sh
```

**2. Confirm all pods are healthy:**
```bash
kubectl get pods -n redis
kubectl get statefulset -n redis
kubectl get svc -n redis
```
Expected: 3 `redis-*` pods and 3 `redis-sentinel-*` pods all `Running`.

**3. Create a test short URL:**
```bash
curl -i -X PUT 'http://<NODE_IP>:30000/?short=failover&long=https://example.com/failover'
```

**4. Warm the cache and confirm reads work:**
```bash
curl -i 'http://<NODE_IP>:30000/failover'
```
Expected: `HTTP/1.1 301` with `Location: https://example.com/failover`.

**5. Note the current master pod IP:**
```bash
kubectl get pods -n redis -o wide
kubectl exec -n redis redis-sentinel-0 -- redis-cli -p 26379 SENTINEL get-master-addr-by-name mymaster
```

**6. Kill the master (redis-0) to trigger Sentinel failover:**
```bash
kubectl scale statefulset redis -n redis --replicas=0
sleep 12
```
> 12 seconds is above the Sentinel `down-after-milliseconds` threshold (default 5 s), giving Sentinel time to detect the outage and hold a vote.

**7. Verify a new master was elected:**
```bash
kubectl exec -n redis redis-sentinel-0 -- redis-cli -p 26379 SENTINEL get-master-addr-by-name mymaster
```
Expected: the IP returned is different from `redis-0`'s original IP — a replica was promoted.

**8. Restore the StatefulSet:**
```bash
kubectl scale statefulset redis -n redis --replicas=3
kubectl get pods -n redis -w
```
The old master rejoins as a replica; Sentinel reconfigures it automatically.

**9. Confirm reads still work after failover:**
```bash
curl -i 'http://<NODE_IP>:30000/failover'
```
Expected: `HTTP/1.1 301` — the app resolved the short URL through the new master.

**10. (If needed) Restart the Spring deployment:**

Only required if the Spring pods were started before Sentinel was deployed:
```bash
kubectl rollout restart deployment/spring-deployment
```

---

### 2.2 Kafka DLT (Dead Letter Topic) Test

This test verifies that messages which cannot be processed after all retries are persisted to the `url_shortener.failed_messages` table in Cassandra.

**1. Send a specially crafted payload that triggers the simulated failure:**
```bash
curl -i -X PUT 'http://<NODE_IP>:30000/?short=fail-retry&long=https://example.com'
```
The Writer Service recognises `shortUrl=fail-retry` and throws a `RuntimeException`, exhausting all 3 retry attempts.

**2. Verify the message landed in the DLT topic:**
```bash
# On the Kafka host — list messages in the DLT topic
kafka-console-consumer.sh \
  --bootstrap-server <KAFKA_IP>:9092 \
  --topic url.write.dlt \
  --from-beginning \
  --timeout-ms 5000
```

**3. Verify the failed event was persisted to Cassandra:**
```bash
kubectl exec -it cassandra-0 -n cassandra -- cqlsh -e \
  "SELECT id, topic, message_key, error_message, failed_at FROM url_shortener.failed_messages;"
```
Expected: one row with `message_key=fail-retry` and the error message from the simulated exception.