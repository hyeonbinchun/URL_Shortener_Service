# Testing Guide

## 1. Load Testing

Load tests are written in k6 and located in `loadTesting/`.

### 1.1 Scenarios

| Script | Workload | VUs |
| :--- | :--- | :--- |
| `read-test.js` | 100% reads (GET `/{short}`) | 1,000-10,000 |
| `write-test.js` | 100% writes (PUT `/?short=...&long=...`) | 1,000-10,000 |
| `mixed-test.js` | 90% reads / 10% writes | 1,000-10,000 |

Run a scenario:

```bash
K6_WEB_DASHBOARD=true \
K6_WEB_DASHBOARD_HOST=0.0.0.0 \
K6_WEB_DASHBOARD_PORT=5665 \
k6 run read-test.js
```

### 1.2 VU Scaling Reference

These targets were used for progressive load experiments:

| VUs | Ramp-up | Steady | Ramp-down |
| :--- | :--- | :--- | :--- |
| 1,000 | 1 min | 3 min | 1 min |
| 2,000 | 2 min | 5 min | 1 min |
| 3,000 | 3 min | 7 min | 1 min |
| 5,000 | 4 min | 7 min | 1 min |
| 7,000 | 4 min | 7 min | 1 min |
| 10,000 | 5 min | 7 min | 1 min |

### 1.3 Benchmark Results Summary (Completed)

Test environment:
- 3-node EC2 Kubernetes cluster
- Same-VPC load generator and API service (low network-path variance)
- Read tests use 10,000 pre-populated URLs

#### Architecture 0 - Baseline (1 API + 1 Cassandra on single node)

Write-only:

| VUs | P50 | P95 | P99 | Throughput | Error Rate |
| :--- | :--- | :--- | :--- | :--- | :--- |
| 1,000 | 2 ms | 30 ms | 102 ms | 996 req/s | 0.00% |
| 2,000 | 12 ms | 204 ms | 446 ms | 1.85k req/s | 0.00% |
| 3,000 | 207 ms | 758 ms | 1 s | 2.09k req/s | 0.00% |
| 5,000 | 781 ms | 1 s | 2 s | 2.08k req/s | 1.00% |

Read-only:

| VUs | P50 | P95 | P99 | Throughput | Error Rate |
| :--- | :--- | :--- | :--- | :--- | :--- |
| 1,000 | 2 ms | 17 ms | 57 ms | 997 req/s | 0.00% |
| 2,000 | 11 ms | 150 ms | 354 ms | 1.91k req/s | 0.00% |
| 3,000 | 231 ms | 668 ms | 903 ms | 2.22k req/s | 0.00% |
| 5,000 | 943 ms | 1 s | 1 s | 2.36k req/s | 0.00% |

#### Architecture 1 - Horizontal Scale (3 API + 1 Cassandra)

Write-only:

| VUs | P50 | P95 | P99 | Throughput | Error Rate |
| :--- | :--- | :--- | :--- | :--- | :--- |
| 1,000 | 2 ms | 8 ms | 27 ms | 999 req/s | 0.00% |
| 2,000 | 3 ms | 29 ms | 102 ms | 1.97k req/s | 0.00% |
| 3,000 | 7 ms | 197 ms | 366 ms | 2.86k req/s | 0.00% |
| 5,000 | 51 ms | 627 ms | 1 s | 4.05k req/s | 0.00% |

Read-only:

| VUs | P50 | P95 | P99 | Throughput | Error Rate |
| :--- | :--- | :--- | :--- | :--- | :--- |
| 1,000 | 3 ms | 9 ms | 25 ms | 998 req/s | 0.00% |
| 2,000 | 3 ms | 26 ms | 94 ms | 1.98k req/s | 0.00% |
| 3,000 | 8 ms | 233 ms | 404 ms | 2.82k req/s | 0.00% |
| 5,000 | 68 ms | 758 ms | 1 s | 3.94k req/s | 0.00% |

#### Architecture 2 - Horizontal Scale + Cassandra Scale (3 API + 3 Cassandra)

Write-only:

| VUs | P50 | P95 | P99 | Throughput | Error Rate |
| :--- | :--- | :--- | :--- | :--- | :--- |
| 1,000 | 2 ms | 7 ms | 22 ms | 998 req/s | 0.00% |
| 2,000 | 4 ms | 37 ms | 118 ms | 1.98k req/s | 0.00% |
| 3,000 | 19 ms | 311 ms | 520 ms | 2.81k req/s | 0.00% |
| 5,000 | 135 ms | 1 s | 2 s | 3.32k req/s | 0.00% |

Read-only:

| VUs | P50 | P95 | P99 | Throughput | Error Rate |
| :--- | :--- | :--- | :--- | :--- | :--- |
| 1,000 | 3 ms | 7 ms | 18 ms | 995 req/s | 0.00% |
| 2,000 | 4 ms | 30 ms | 112 ms | 1.96k req/s | 0.00% |
| 3,000 | 12 ms | 285 ms | 432 ms | 2.81k req/s | 0.00% |
| 5,000 | 88 ms | 1 s | 1 s | 3.52k req/s | 0.00% |

#### Architecture 3 - Redis Caching

Read-only:

| VUs | P50 | P95 | P99 | Throughput | Error Rate |
| :--- | :--- | :--- | :--- | :--- | :--- |
| 1,000 | 2 ms | 9 ms | 47 ms | 999 req/s | 0.00% |
| 2,000 | 2 ms | 36 ms | 170 ms | 1.96k req/s | 0.00% |
| 3,000 | 3 ms | 137 ms | 306 ms | 2.82k req/s | 0.00% |
| 5,000 | 45 ms | 343 ms | 654 ms | 4.34k req/s | 0.00% |
| 7,000 | 84 ms | 410 ms | 839 ms | 5.71k req/s | 0.00% |
| 10,000 | 156 ms | 711 ms | 1 s | 6.51k req/s | 0.40% |

#### Architecture 4 - Kafka Asynchronous Write

Write-only:

| VUs | P50 | P95 | P99 | Throughput | Error Rate |
| :--- | :--- | :--- | :--- | :--- | :--- |
| 1,000 | 1 ms | 13 ms | 56 ms | 998.6 req/s | 0.00% |
| 2,000 | 2 ms | 30 ms | 112 ms | 1.98k req/s | 0.00% |
| 3,000 | 6 ms | 213 ms | 392 ms | 2.89k req/s | 0.00% |
| 5,000 | 35 ms | 422 ms | 762 ms | 4.48k req/s | 0.00% |
| 7,000 | 91 ms | 776 ms | 1 s | 6.1k req/s | 0.00% |
| 10,000 | 181 ms | 1 s | 2 s | 6.13k req/s | 0.60% |

#### Key Findings

- API horizontal scaling (Architecture 1) improved write throughput from 2.09k req/s to 4.05k req/s at 5,000 VUs with 0.00% errors.
- Cassandra scaling (Architecture 2) improved durability-oriented write capacity over baseline but did not outperform API-only scale at peak write throughput.
- Redis cache (Architecture 3) produced the strongest read-path gain, reaching 6.51k req/s at 10,000 VUs.
- Kafka async writes (Architecture 4) delivered the highest write throughput at 6.13k req/s, with minor error rate increase only at extreme load (10,000 VUs).
- Overall, bottleneck shifted from synchronous storage path to queue/consumer and cache behavior as the architecture evolved.

---

## 2. Fault Injection

### 2.1 Node-Level Failure

This test verifies recovery from an EC2 node outage in a 3-node cluster.

**1. Check nodes and current service placement:**

```bash
kubectl get nodes -o wide

kubectl get pods -A -o wide
```


**2. Drain one node (simulate node failure):**

```bash
kubectl drain <NODE_NAME> --ignore-daemonsets --delete-emptydir-data --grace-period=0 --force
```

**3. Monitor pod rescheduling and failover:**

```bash
kubectl get pods -A -w -o wide
```

**4. Validate service is still reachable from another node:**

```bash
curl -i 'http://<OTHER_NODE_IP>:30000/<EXISTING_SHORT_CODE>'
```

**5. Re-enable the drained node:**

```bash
kubectl uncordon <NODE_NAME>
```

**6. Verify cluster returns to healthy state:**

```bash
kubectl get nodes
kubectl get pods -A
```

Expected:
- Read/write traffic continues with temporary latency increase.
- Pods are rescheduled on surviving nodes.
- System returns to steady state after `uncordon`.

---

### 2.2 Redis Sentinel Failover Test

This test verifies that reads continue after Redis master failover.

**1. Deploy (or redeploy) Redis with Sentinel:**

```bash
cd scripts && ./deploy-redis.sh
```

**2. Confirm Redis and Sentinel are healthy:**

```bash
kubectl get pods -n redis
kubectl get statefulset -n redis
kubectl get svc -n redis
```

Expected: 3 `redis-*` pods and 3 `redis-sentinel-*` pods are `Running`.

**3. Create test data:**

```bash
curl -i -X PUT 'http://<NODE_IP>:30000/?short=failover&long=https://example.com/failover'
```

**4. Warm cache and verify baseline read:**

```bash
curl -i 'http://<NODE_IP>:30000/failover'
```

Expected: `HTTP/1.1 301` with `Location: https://example.com/failover`.

**5. Record current master:**

```bash
kubectl get pods -n redis -o wide
kubectl exec -n redis redis-sentinel-0 -- redis-cli -p 26379 SENTINEL get-master-addr-by-name mymaster
```

**6. Trigger Sentinel failover:**

```bash
kubectl exec -it redis-sentinel-0 -n redis -- redis-cli -p 26379 SENTINEL failover mymaster
```

**7. Verify a new master was elected:**

```bash
kubectl exec -n redis redis-sentinel-0 -- redis-cli -p 26379 SENTINEL get-master-addr-by-name mymaster
```

Expected: master IP changes to a previous replica.

**8. Confirm application read path still works:**

```bash
curl -i 'http://<NODE_IP>:30000/failover'
```

Expected: still `HTTP/1.1 301`.

---

### 2.3 Kafka Consumer Failure Test

This test verifies buffering behavior when the writer consumer is unavailable.

**1. Scale down writer service:**

```bash
kubectl scale deployment consumer-deployment -n default --replicas=0
```

**2. Send write traffic:**

```bash
for i in {1..100}; do
  curl -s -o /dev/null -w "%{http_code}\n" \
    "http://<NODE_IP>:30000/?short=msg${i}&long=https://example.com/msg${i}" \
    -X PUT
done
```

**3. Verify consumer lag increases:**

```bash
kafka-consumer-groups.sh --bootstrap-server <KAFKA_IP>:9092 --group url-write-group --describe
```

Expected: Total LAG = 100.

**4. Restore writer service:**

```bash
kubectl scale deployment consumer-deployment -n default --replicas=3
```

**5. Verify queue drains and data is persisted:**

```bash
kafka-consumer-groups.sh --bootstrap-server <KAFKA_IP>:9092 --group url-write-group --describe
```
```bash
kubectl exec -it cassandra-0 -n cassandra -- cqlsh -e \
  "SELECT COUNT(*) FROM url_shortener.urls;"
```

Expected:
- lag returns to 0.
- messages written during outage appear in Cassandra after recovery.

---

### 2.4 Cassandra Node Failure Test

This test verifies read/write continuity when one Cassandra pod is unavailable.

**1. Delete one Cassandra pod:**

```bash
kubectl delete pod cassandra-1 -n cassandra
```

**2. Write a new URL:**

```bash
curl -i -X PUT 'http://<NODE_IP>:30000/?short=test-cs&long=https://example.com/cs'
```

**3. Read back the URL:**

```bash
curl -i 'http://<NODE_IP>:30000/test-cs'
```

**4. Validate Cassandra ring health:**

```bash
kubectl exec -it cassandra-0 -n cassandra -- nodetool status
kubectl get pods -n cassandra -w
```

Expected:
- requests still succeed (RF=2).
- remaining Cassandra nodes stay `UN` in `nodetool status`.
- the deleted pod is recreated automatically by the StatefulSet and returns to `Running`.


---

### 2.5 Kafka DLT (Dead Letter Topic) Test

This test verifies that messages which fail all retries are persisted in `url_shortener.failed_messages`.

**1. Send payload that triggers simulated failure:**

```bash
curl -i -X PUT 'http://<NODE_IP>:30000/?short=fail-retry&long=https://example.com'
```

The writer recognizes `short=fail-retry` and throws a simulated exception until retries are exhausted.

**2. Verify message reached DLT:**

```bash
kafka-console-consumer.sh \
  --bootstrap-server <KAFKA_IP>:9092 \
  --topic url.write.dlt \
  --from-beginning \
  --timeout-ms 5000
```

**3. Verify failed message persisted to Cassandra:**

```bash
kubectl exec -it cassandra-0 -n cassandra -- cqlsh -e \
  "SELECT id, topic, message_key, error_message, failed_at FROM url_shortener.failed_messages;"
```

Expected: a row exists with `message_key=fail-retry` and failure details.