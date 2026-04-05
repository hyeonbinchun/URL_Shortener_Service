# Test Results

## 1. Test Environment

- **Cluster**: 3-node Kubernetes on EC2 instances.
- **Load Geneator**: k6 running on a separate EC2 instance to generate read and write workloads.
- **Note**: Tests were executed in a controlled same-VPC environment so the results emphasize service behavior rather than internet-path latency.

## 2. Architecture Progression

| Architecture | Change Introduced|
| :--- | :--- |
| Baseline (Single-node) | 1 API Pod + 1 Cassandra Pod |
| Horizontal API Scaling | 3 API Pod + 1 Cassandra Pod |
| Horizontal DB Scaling | 3 API Pod + 3 Cassandra Pod |
| Redis Caching | Added Redis (cache-aside read path) |
| Kafka Async Writes | Added Kafka + Writer Service (async wirte path) |

## 3. Results Summary

<p align="center">
  <img src="/assets/dashboard.png" alt="Graph" width="800" style="max-width: 100%; height: auto;" />
</p>

| Architecture | Peak Read | Peak Write | Notable Outcome |
| :--- | :---: | :---: | :--- |
| Baseline | 2.36k req/s | 2.09k req/s,  | Saturates quickly; P95 >1.5s at 5k VUs |
| Horizontal API scale | 3.94k req/s | 4.05k req/s | Near 2× gain |
| Horizontal DB scale | 3.52k read req/s | 3.32k req/s | Improved durability capacity, but replication overhead reduces throughput |
| Redis cache | 6.51k req/s | - | Strongest read gain; P95 343 ms at 5k VUs |
| Kafka async writes | - | 6.13k write req/s | Highest write gain; P95  422 ms at 5k VUs|


## 4. Benchmark Results

---

### Architecture 0 - Baseline: 1 API + 1 Cassandra

Write-only:

| VUs | P50 | P95 | P99 | Throughput | Error Rate |
| :--- | :--- | :--- | :--- | :--- | :--- |
| 1,000 | 2 ms | 30 ms | 102 ms | 996 req/s | 0.00% |
| 2,000 | 12 ms | 204 ms | 446 ms | 1.85k req/s | 0.00% |
| 3,000 | 207 ms | 758 ms | 1.2 s | 2.09k req/s | 0.00% |
| 5,000 | 781 ms | 1.56 s | 2.3 s | 2.08k req/s | 1.00% |

Read-only:

| VUs | P50 | P95 | P99 | Throughput | Error Rate |
| :--- | :--- | :--- | :--- | :--- | :--- |
| 1,000 | 2 ms | 17 ms | 57 ms | 997 req/s | 0.00% |
| 2,000 | 11 ms | 150 ms | 354 ms | 1.91k req/s | 0.00% |
| 3,000 | 231 ms | 668 ms | 903 ms | 2.22k req/s | 0.00% |
| 5,000 | 943 ms | 1.4 s | 1.8 s | 2.36k req/s | 0.00% |

---

### Architecture 1 - Horizontal API Scale: 3 API + 1 Cassandra

Write-only:

| VUs | P50 | P95 | P99 | Throughput | Error Rate |
| :--- | :--- | :--- | :--- | :--- | :--- |
| 1,000 | 2 ms | 8 ms | 27 ms | 999 req/s | 0.00% |
| 2,000 | 3 ms | 29 ms | 102 ms | 1.97k req/s | 0.00% |
| 3,000 | 7 ms | 197 ms | 366 ms | 2.86k req/s | 0.00% |
| 5,000 | 51 ms | 627 ms | 1.3 s | 4.05k req/s | 0.00% |

Read-only:

| VUs | P50 | P95 | P99 | Throughput | Error Rate |
| :--- | :--- | :--- | :--- | :--- | :--- |
| 1,000 | 3 ms | 9 ms | 25 ms | 998 req/s | 0.00% |
| 2,000 | 3 ms | 26 ms | 94 ms | 1.98k req/s | 0.00% |
| 3,000 | 8 ms | 233 ms | 404 ms | 2.82k req/s | 0.00% |
| 5,000 | 68 ms | 758 ms | 1.2 s | 3.94k req/s | 0.00% |

---

### Architecture 2 - Horizontal DB Scale: 3 API + 3 Cassandra

Write-only:

| VUs | P50 | P95 | P99 | Throughput | Error Rate |
| :--- | :--- | :--- | :--- | :--- | :--- |
| 1,000 | 2 ms | 7 ms | 22 ms | 998 req/s | 0.00% |
| 2,000 | 4 ms | 37 ms | 118 ms | 1.98k req/s | 0.00% |
| 3,000 | 19 ms | 311 ms | 520 ms | 2.81k req/s | 0.00% |
| 5,000 | 135 ms | 1.5 s | 2 s | 3.32k req/s | 0.00% |

Read-only:

| VUs | P50 | P95 | P99 | Throughput | Error Rate |
| :--- | :--- | :--- | :--- | :--- | :--- |
| 1,000 | 3 ms | 7 ms | 18 ms | 995 req/s | 0.00% |
| 2,000 | 4 ms | 30 ms | 112 ms | 1.96k req/s | 0.00% |
| 3,000 | 12 ms | 285 ms | 432 ms | 2.81k req/s | 0.00% |
| 5,000 | 88 ms | 1.2 s | 1.6 s | 3.52k req/s | 0.00% |

---

### Architecture 3 - Redis Caching

Read-only:

| VUs | P50 | P95 | P99 | Throughput | Error Rate |
| :--- | :--- | :--- | :--- | :--- | :--- |
| 1,000 | 2 ms | 9 ms | 47 ms | 999 req/s | 0.00% |
| 2,000 | 2 ms | 36 ms | 170 ms | 1.96k req/s | 0.00% |
| 3,000 | 3 ms | 137 ms | 306 ms | 2.82k req/s | 0.00% |
| 5,000 | 45 ms | 343 ms | 654 ms | 4.34k req/s | 0.00% |
| 7,000 | 84 ms | 410 ms | 839 ms | 5.71k req/s | 0.00% |
| 10,000 | 156 ms | 711 ms | 1.2 s | 6.51k req/s | 0.40% |

--- 

### Architecture 4 - Kafka Asynchronous Writes

Write-only:

| VUs | P50 | P95 | P99 | Throughput | Error Rate |
| :--- | :--- | :--- | :--- | :--- | :--- |
| 1,000 | 1 ms | 13 ms | 56 ms | 998.6 req/s | 0.00% |
| 2,000 | 2 ms | 30 ms | 112 ms | 1.98k req/s | 0.00% |
| 3,000 | 6 ms | 213 ms | 392 ms | 2.89k req/s | 0.00% |
| 5,000 | 35 ms | 422 ms | 762 ms | 4.48k req/s | 0.00% |
| 7,000 | 91 ms | 776 ms | 1.3 s | 6.1k req/s | 0.00% |
| 10,000 | 181 ms | 1.5 s | 2.2 s | 6.13k req/s | 0.60% |

---

## 5. Analysis & Key Findings

### Architecture 0 → 1: Horizontal API Scaling

Scaling the API layer from 1 to 3 pods removed the CPU/thread bottleneck. Peak write throughput nearly doubled from 2.09k → 4.05k req/s at 5,000 VUs. P95 latency at 5,000 VUs dropped from 1560 ms to 627 ms. The remaining bottleneck shifted to Cassandra.

### Architecture 1 → 2: Horizontl DB Scaling

Scaling Cassandra from 1 to 3 nodes **did not improve throughput** — write performance regressed from 4.05k to 3.32k req/s at 5,000 VUs. This is expected behavior at this load level for three reasons:
- **Replication overhead**: With RF=2, every write must be acknowledged by 2 nodes instead of 1, doubling disk writes and adding cross-node coordination latency.
- **Network in the critical path**: Single-node writes are local (memory + disk); distributed writes add coordinator-to-replica network round-trips.
- **Scale mismatch**: At 2–4k req/s, a single Cassandra node is still efficient. The overhead of distribution outweighs the benefit of parallelism at this load. Distributed systems typically benefit from multi-node setups at 10k–100k+ req/s.

**Takeaway**: Cassandra was not the bottleneck at this scale. Adding nodes introduced coordination cost without enough workload to justify it.

### Architecture 2 → 3: Redis Caching

Adding Redis with a cache-aside read strategy produced the largest read-path gain. Peak read throughput reached 6.51k req/s at 10,000 VUs. P95 latency at 5,000 VUs dropped from 1.2 s to 343 ms. The improvement is driven by serving reads entirely from memory, bypassing Cassandra disk I/O. The small 0.40% error rate at 10,000 VUs is the first sign of memory pressure approaching system limits.

### Architecture 3 → 4: Kafka Async Writes

Introducing Kafka decoupled the API from synchronous Cassandra writes. The API now publishes a message and returns immediately, making P50 latency extremely low (1–6 ms) across all VU levels. Peak write throughput reached 6.13k req/s at 10,000 VUs — a 3× improvement over baseline. 