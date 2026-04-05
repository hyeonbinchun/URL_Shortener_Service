# System Design

## 1. Objective

This project demonstrates how a URL shortener can evolve into a distributed system. The goal is to design and implement a horizontally scalable, fault-tolerant service deployed in a cloud-native environment, capable of:

- **Scalability**: Support seamless horizontal scaling to meet increasing traffic demands.
- **Availability & Resilience**: Ensure fault tolerance with self-healing mechanisms and minimal downtime.
- **High Throughput & Low Latency**: Optimize the system to handle sustained loads with high performance and minimal response time.
- **Observability**: Implement comprehensive metrics collection and visualization through dashboards for real-time monitoring.
- **Performance Trade-offs**: Analyze and quantify trade-offs introduced by different architectural decisions and optimizations.

Note: The application is designed with a small scope to ensure that architectural decisions are easy to comprehend and evaluate.

## 2. System Architecture

### 2.1 Component Overview

| Component | Technology | Role |
| :--- | :--- | :--- |
| API Service | Java + Spring Boot | Handles HTTP requests |
| Message Queue | Apache Kafka | Buffers & decouples writes |
| Writer Service | Java + Spring Boot | Consumes Kafka events |
| Database | Apache Cassandra (3 node, RF=2) | Scalable persistent storage |
| Cache | Redis (Primary + 2 Replicas) | Low-latency reads |
| Sentinel | Redis Sentinel | Monitors Redis, auto-failover |
| Orchestration | Kubernetes / k3s | Scheduling, scaling, self-healing |
| Observability | Prometheus + Grafana | Metrics & dashboards
| Load Testing | k6 | Performance & load testing |

### 2.2 Architecture Diagram

<p align="center">
  <img src="/assets/architecture.png" alt="Architecture Diagram" width="500" style="max-width: 100%; height: auto;" />
</p>

### 2.3 Data Flow

#### Read Path - Cache-Aside (Synchronous)

1. Client requests a short URL redirect.
2. Request hits Redis replicas first.
3. Cache hit → return immediately.
4. Cache miss → read from Cassandra → populate Redis primary → replicates to replicas.
5. Redis Sentinel (×3) monitors the primary; on failure, promotes a replica (quorum = 2). The Spring client reconnects transparently.

#### 2.4 Write Path - Write-Around (Asynchronous)

1. Client submits a new short URL mapping.
2. API publishes to Kafka → return immediately (non-blocking).
3. Writer Service consumes from Kafka → writes to Cassandra.
4. On failure: retried up to 3× with exponential backoff.
5. All retries exhausted → message routed to Dead Letter Topic (`url.write.dlt`).
6. `UrlWriteDltService` consumes from DLT → attempts to persist failed event to `url_shortener.failed_messages` for inspection or replay.

## 3. Design Decisions

### 3.1 Cassandra for Persistence

Cassandra provides native horizontal scalability and replication, making it well-suited for high write throughput. The tradeoff is replication overhead and eventual consistency — reads may briefly return stale data after a write.

### 3.2 Redis for Read Latency

An in-memory cache reduces Cassandra read pressure for hot URLs and dramatically lowers P95 latency. The tradeoff is potential stale reads on cache miss and additional operational overhead for eviction and sentinel management.

### 3.3 Kafka for Async Writes

Decoupling the API from Cassandra writes eliminates synchronous write latency from the critical path. Kafka also buffers traffic spikes, preventing Cassandra overload. The tradeoff is that reads may temporarily miss recent writes before the consumer catches up.


### 3.4 Writer Service (Dedicated Kafka Consumer)

Separating the consumer from the API keeps responsibilities clear and makes write throughput easier to scale independently. The tradeoff is added operational complexity: consumer lag must be monitored to ensure write durability.

### 3.5 Dead Letter Handling

Failed Kafka messages are retried with exponential backoff and then sent to a dead-letter topic. A dedicated DLT consumer stores failed event details in Cassandra for inspection or replay. If Cassandra is temporarily unavailable during DLT persistence, failures are logged for operational follow-up.

### 3.6 Kubernetes for Deployment

Kubernetes provides the automated pod restarts, rolling deployments, scheduling, horizontal scaling, and health-probe-based traffic routing needed for a resilient distributed system. The tradeoff is increased operational complexity compared to simple VM deployments.

### 3.7 AWS EBS for Stateful Persistence

Redis and Cassandra stateful pods use PVCs backed by the `ebs-csi-gp3` StorageClass (AWS EBS via CSI). This keeps data durable beyond pod restarts and supports automatic volume reattachment when pods are rescheduled. The tradeoff is cloud and zone coupling: EBS volumes are AZ-scoped and add storage cost/IOPS tuning considerations.

### 3.8 Observability

The API service exposes metrics through Spring Boot Actuator and Prometheus currently scrapes the API metrics endpoint. Grafana is used for dashboards during load testing and fault injection. Current gaps: per-pod CPU/memory, writer-service metrics, Kafka consumer lag, and Redis hit ratio are not yet collected.

## 4. Scalability Strategy

### 4.1 Horizontal Scaling by Layer

**API Service** — Kubernetes `Deployment`; pods scale horizontally. Kubernetes `Service` provides L4 load balancing across pods.

**Cassandra** — Kubernetes `StatefulSet` with headless `Service`; nodes can be added dynamically with automatic data rebalancing via token ring partitioning.

**Redis** — Kubernetes `StatefulSet`; single primary handles writes, replicas handle reads. Replicas can be added manually; new replicas sync from the primary automatically.

**Writer Service** — Kubernetes `Deployment` with multiple replicas. All replicas share a Kafka consumer group; Kafka distributes topic partitions across instances for parallel write processing.

**External Traffic** — Kubernetes NodePort service exposes the API. An external load balancer (e.g., AWS ELB, Nginx) can be placed in front for production ingress.

### 4.2 Data Scalability
Cassandra handles data scalability through consistent-hash-based partition distribution and configurable replication factors. Adding nodes triggers automatic token rebalancing with no downtime.

## 5. Fault Tolerance

| Failure Scenario | Recovery Mechanism | 
| :--- | :--- | 
| API pod crash | Kubernetes restarts the pod; traffic routes to healthy pods |
| Cassandra node failure | Replication ensures data availability; StatefulSet reschedules the pod|
| Redis primary failure | Sentinel quorum promotes a replica; Spring client reconnects transparently |
|  Redis replica failure | Pod replaced by Kubernetes; new replica syncs from primary |
| Writer Service failure | Unconsumed events remain in `url.write`; Kubernetes restarts the consumer pod |
| Kubernetes node failure | Pods on the failed node are rescheduled to healthy nodes; Services continue routing to ready endpoints |
| Stateful pod restart on another node | Kubernetes reattaches the existing EBS volume to a replacement node in the same AZ, preserving Redis/Cassandra data |
