# Test Results

## 1. Benchmark Results Summary

### Throughput vs Load

<p align="center">
  <img src="assets/dashboard.png" alt="Architecture Diagram" width="500" style="max-width: 100%; height: auto;" />
</p>


### 

<p align="center">
  <img src="assets/throughput-load2.png" alt="Architecture Diagram" width="500" style="max-width: 100%; height: auto;" />
</p>


### 1.6 Key Findings

- API horizontal scaling (Architecture 1) improved write throughput from 2.09k req/s to 4.05k req/s at 5,000 VUs with 0.00% errors.
- Cassandra scaling (Architecture 2) improved durability-oriented write capacity over baseline but did not outperform API-only scale at peak write throughput.
- Redis cache (Architecture 3) produced the strongest read-path gain, reaching 6.51k req/s at 10,000 VUs.
- Kafka async writes (Architecture 4) delivered the highest write throughput at 6.13k req/s, with minor error rate increase only at extreme load (10,000 VUs).
- Overall, bottleneck shifted from synchronous storage path to queue/consumer and cache behavior as the architecture evolved.


## 2. Benchmark Measurments
### 2.1 Architecture 0 - Baseline (1 API + 1 Cassandra on single node)

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

### 2.2 Architecture 1 - Horizontal Scale (3 API + 1 Cassandra)

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

### 2.3 Architecture 2 - Horizontal Scale (3 API + 3 Cassandra)

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

### 2.4 Architecture 3 - Redis Caching

Read-only:

| VUs | P50 | P95 | P99 | Throughput | Error Rate |
| :--- | :--- | :--- | :--- | :--- | :--- |
| 1,000 | 2 ms | 9 ms | 47 ms | 999 req/s | 0.00% |
| 2,000 | 2 ms | 36 ms | 170 ms | 1.96k req/s | 0.00% |
| 3,000 | 3 ms | 137 ms | 306 ms | 2.82k req/s | 0.00% |
| 5,000 | 45 ms | 343 ms | 654 ms | 4.34k req/s | 0.00% |
| 7,000 | 84 ms | 410 ms | 839 ms | 5.71k req/s | 0.00% |
| 10,000 | 156 ms | 711 ms | 1.2 s | 6.51k req/s | 0.40% |

### 2.5 Architecture 4 - Kafka Asynchronous Write

Write-only:

| VUs | P50 | P95 | P99 | Throughput | Error Rate |
| :--- | :--- | :--- | :--- | :--- | :--- |
| 1,000 | 1 ms | 13 ms | 56 ms | 998.6 req/s | 0.00% |
| 2,000 | 2 ms | 30 ms | 112 ms | 1.98k req/s | 0.00% |
| 3,000 | 6 ms | 213 ms | 392 ms | 2.89k req/s | 0.00% |
| 5,000 | 35 ms | 422 ms | 762 ms | 4.48k req/s | 0.00% |
| 7,000 | 91 ms | 776 ms | 1.3 s | 6.1k req/s | 0.00% |
| 10,000 | 181 ms | 1.5 s | 2.2 s | 6.13k req/s | 0.60% |
