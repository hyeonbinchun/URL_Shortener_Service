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