# Distributed URL Shortener Service

## 1. Project Objective

Design and implement a horizontally scalable, fault-tolerant URL shortener Service capable of:
- Handling high read/write throughput
- Low Latency
- Maintaining data durability
- Demonstrating distributed system tradeoffs
- Operating in containerized cloud-native environments

The system prioritizes:
- Scalability
- Availability and Resilience (Falut-tolerance, Healing)
- Observability (Monitoring System, Monitoring UI)
- Performance benchmarking (Load Testing, Failure Testing) 
- Tradeoff analysis

## 2. High-Level Architecture
### 2.1 Core Components
1. API Service (Spring Boot)
2. Cache Layer (Redis)
3. Persistence Layer (Cassandra)
4. Streaming Layer (Kafka)
5. Writer Service (Spring Boot) 
6. Load Generator (k6)
7. Observability Stack (Prometheus + Grafana)

### 2.2 Tech Stack
| Layer | Technology | Reasoning | 
| :--- | :--- | :--- |
| API Service | Java + Spring Boot | Fast development
| Cache | Redis | Low-latency reads
| Database | Apache Cassandra | Horizontal scalability
| Streaming | Kafka | Async writes
| Writer Service | Java + Spring Boot | Kafka consumer for DB writes
| Containeralization | Docker | Environment isolation
| Orchestration | Kubernetes | Scaling and self-healing
| Observability | Prometheus + Grafana | Metrics collection and visualization
| Load Testing | k6 | Performance benchmarking under high load
| Automation | Bash | Deployment scripts and infrastructure management 

### 2.3 System Architecture

```
# Full Architecture
┌─────────────────────────────────────────────────────────────────────────────┐
│                           OBSERVABILITY LAYER                               │
│                                                                             │
│   k6 (Load Generator) ──────► Prometheus ──────► Grafana                    │
│                                    ▲                                        │
│                                    │ scrape                                 │
└────────────────────────────────────┼────────────────────────────────────────┘
                                     │
                         ┌───────────┴───────────┐
                         │      API SERVICE      │
                         │     (Spring Boot)     │
                         └─────┬──────────┬──────┘
              READ PATH        │          │    WRITE PATH
          ┌────────────────────┘          └─────────────────────┐
          │                                                     │  
          ▼                                                     ▼
┌────────────────────────────────────────┐              ┌──────────────────────┐
│                REDIS                   │              │        KAFKA         │
│                                        │              │   (url.write topic)  │
│  ┌──────────────┐  replicates          │              └──────────┬───────────┘
│  │ Redis Primary│──────────────┐       │                         │
│  │ (writes)     │              ▼       │                         ▼
│  └──────┬───────┘  ┌─────────────────┐ │            ┌───────────────────────────┐
│         │          │ Redis Replica 1 │ │            │      WRITER SERVICE       │
│         │          │ (reads)         │ │            │   (Spring Boot Consumer)  │
│         │          └─────────────────┘ │            │   @RetryableTopic         │
│         │          ┌─────────────────┐ │            │   3 retries, exp. backoff │
│         └─────────►│ Redis Replica 2 │ │            └─────────────┬─────────────┘
│                    │ (reads)         │ │                          │
│                    └─────────────────┘ │                          │
│  ┌──────────────────────────────┐      │                          │ on success
│  │                              │      │                          │
│  │     SENTINEL PODS (×3)       │      │                          ▼
│  │  monitors: mymaster          │      │                ┌────────────────────┐
│  │  quorum: 2                   │      │                │     CASSANDRA      │
│  │  auto-promotes replica on    │      │                │  (url_shortener)   │
│  │  primary failure             │      │                └────────────────────┘
│  └──────────────────────────────┘      │
└──────────────────┬─────────────────────┘
                   │ cache miss → read from DB, populates Redis Primary 
                   │ cache hit  → return immediately
                   ▼
          ┌────────────────────┐
          │     CASSANDRA      │
          │  (url_shortener)   │
          └────────────────────┘


══════════════════════════ FAULT TOLERANCE PATH ══════════════════════════════

   WRITER SERVICE
        │
        │ retry 1 → retry 2 → retry 3 (exponential backoff: 1s, 2s, 4s)
        │
        │ all retries exhausted
        ▼
┌─────────────────────┐
│   KAFKA DLT TOPIC   │
│  (url.write.dlt)    │
└──────────┬──────────┘
           │
           ▼
┌────────────────────────┐
│     DLT CONSUMER       │
│  (UrlWriteDltService)  │
└──────────┬─────────────┘
           │ persists failed event
           ▼
┌──────────────────────────────────────────────────────┐
│              CASSANDRA · failed_messages              │
│  id · topic · partition · offset · message_key       │
│  payload · error_message · failed_at                 │
└──────────────────────────────────────────────────────┘
           │
           └──► No write is silently dropped.
                Failed events available for inspection or replay.
```

**Read Path (Synchronous) - Cache Aside**:
1. Read request hits Redis Replicas first.
2. Cache hit → return immediately.
3. Cache miss → read from Cassandra → populate Redis Primary → replicates to Replicas.
4. Redis Sentinel (×3) monitors the Primary; on failure, promotes a Replica to Primary (quorum = 2).


**Write Path (Asynchronous) - Write Around**:
1. API enqueues write to Kafka → returns immediately (non-blocking).
2. Writer Service consumes from Kafka → writes to Cassandra.
3. On failure: retried up to 3 times with exponential backoff (1s, 2s, 4s).
4. All retries exhausted → message routed to Dead Letter Topic (url.write.dlt).
5. DLT Consumer (UrlWriteDltService) persists failed event to Cassandra (failed_messages table) for inspection or replay.


**Separation of responsibilities**:
- Kafka: durable message transport only.
- Writer Service: consumes from Kafka, writes to Cassandra, logs outcomes, handles retries.
- DLT Consumer: consumes from url.write.dlt, persists failed events — ensures no write is silently dropped.
- Redis Primary: populated lazily on cache miss only; never written to directly during the write path.
- Redis Sentinels: monitor Primary health, coordinate automatic failover — transparent to the API Service.


## 3. System Design Decisions
### 3.1 Cassandra for Persistence
- Horizontally scalable and highly available
- Supports partitioning and replication → durable storage
- Tradeoff: weaker consistency guarantees (eventual consistency)

### 3.2  Redis Cache for Fast Reads
- Reduces load on Cassandra for popular URLs
- Provides TTL-based eviction for short-lived entries
- Tradeoff: cache misses require fallback to DB

### 3.3 Asynchronous Writes with Kafka
- Decouples API from database writes → reduces API latency
- Buffers spikes in traffic → prevents Cassandra overload
- Enables replayable events for resilience or future analytics
- Tradeoff: introduces eventual consistency

### 3.4 Writer Service (Separate Consumer)
- Handles writes asynchronously from Kafka
- Updates Cassandra
- Logs operations:
    - Kafka message consumption status (success/failure)
    - Cassandra write success/failure
    - Errors and exceptions for observability and debugging
- Tradeoff: adds complexity, requires monitoring of consumer lag

### 3.5 Dead Letter Topic (DLT) for Fault Tolerance
- The Writer Service uses `@RetryableTopic` (3 attempts, 1s initial delay, 2× exponential backoff)
- Messages that fail all retries are routed to the `url.write.dlt` topic
- A dedicated `UrlWriteDltService` consumes from the DLT and persists failed events to the Cassandra table `url_shortener.failed_messages`
- This ensures no write is silently dropped: every failed event is durably stored for inspection or replay
- Schema: `id`, `topic`, `partition`, `offset`, `message_key`, `payload`, `error_message`, `failed_at`

### 3.6 Containerization and Orchestration
- Docker + Kubernetes → easy deployment, scaling, self-healing
- Tradeoff: introduces operational complexity

### 3.7 Observability & Load Testing
- The API Service exposes Prometheus metrics via Spring Boot Actuator at `/actuator/prometheus`
- k6 → Generates HTTP load; can also push its own metrics to Prometheus via the Remote Write protocol
- Prometheus → Scrapes both k6 metrics and Spring Boot application metrics
- Grafana → Visualizes all metrics in dashboards
- Tradeoff: system-level metrics (CPU, memory per pod) and Kafka consumer lag are not yet collected

## 4. Scalability Strategy
### 4.1 Horizontal Scaling
**API layer (Service):**
- Kubernetes Deployment + Service
- Pods can be scaled horizontally (add/remove) based on load
- Internal traffic between pods is automatically routed by the Kubernetes Service (basic L4 load balancing)

**Database Layer (Cassandra):**
- Kubernetes StatefulSet + Headless Service
- Dynamic node addition/removal with automatic data rebalancing 

**Caching Layer (Redis):**
- Kubernetes StatefulSet + Service + Headless Service
- Single Primary handles writes
- Replicas handle reads for horizontal scaling
- Manual horizontal scaling: add/remove pods as needed

**Writer Service (Kafka Consumer Layer)**
- Kubernetes Deployment with multiple replicas.
- Each Writer Service instance belongs to the same consumer group in Apache Kafka.
- Kafka distributes topic partitions across consumer instances, allowing parallel processing of write events.

**Load Balancing / Traffic Routing**
- Internal: Kubernetes Service distributes requests across API pods
- External: Optional cloud or reverse proxy load balancer (e.g., AWS ELB, GCP LB, Nginx, Traefik) handles ingress traffic and L7 routing

### 4.2 Data Scalability
Handled by:
- Cassandra Partitioning
- Cassandra Replication
- Eventual consistency model



## 5. Fault Tolerance
### Kubernetes:
- Pods are automatically restarted on crash.
- Liveness and readiness probes detect unhealthy pods and remove them from service until healthy.

### API Service failure:
- Kubernetes restarts failed API pods automatically.
- Service traffic routing ensures that requests are sent only to healthy pods.
- Horizontal scaling allows adding more pods to maintain throughput during partial failures.

### Cassandra failure:
- Replication ensures data is available even if one or more nodes fail.
- Kubernetes StatefulSet can reschedule failed pods; data is automatically rebalanced.

### Redis Primary failure
- **Failure detection**: 3 Sentinel pods monitor master `mymaster`; a quorum of 2 votes to trigger failover.
- **Replica promotion**: One of the 2 replicas is promoted to the new Primary.
- **Client behavior**: The API service uses the Sentinel client which queries Sentinels for the current master address and reconnects automatically.
- **Persistence**: Relies on replica's AOF-persisted copy replicated from the old Primary.
- **Data guarantees**: No data is lost if replication was up-to-date at the time of Primary failure.

### Redis Replica failure:
- Can be replaced dynamically by adding a new Replica pod in Kubernetes.
- New Replica syncs data from Primary.

### Writer Service failure (Kafka consumer):
- `@RetryableTopic` retries failed messages up to 3 times with exponential backoff.
- After all retries, the message is moved to the DLT (`url.write.dlt`).
- The DLT consumer (`UrlWriteDltService`) persists failed events to Cassandra for later inspection or replay.
- No write is silently dropped.

## 6. Observability & Metrics
**Metrics collected:**
- Throughput (RPS)
- P99 latency
- P95 latency
- P50 latency
- Error Rate (http_request_failed)
- Spring Boot application metrics (JVM, HTTP request counts/latencies via Actuator)

**Observability stack:**
- **Prometheus**: Scrapes k6 metrics and Spring Boot `/actuator/prometheus` endpoint
- **Grafana**: Visualizes metrics in dashboards

**Future Improvement**:
1. Add system-level metrics: CPU, Memory per pod
2. Extend monitoring to Redis, Kafka consumer lag, Cassandra
3. Add alerting rules in Prometheus


## 7. Testing/Benchmarking Strategy
### 7.1 Load Testing
**Scenarios**:
1. Read-only workload
2. Write-only workload
3. Mixed workload (80% reads / 20% writes)
4. Node failure

**Each scenario includes the following phases**:
1. Ramp-up
2. Steady state
3. Ramp-down

**Note**:
- Each load testing scenario uses a preloaded dataset of 10,000 shortened URLs. This allows the system to demonstrate cache effectiveness, database fallback behavior, and asynchronous write handling under realistic conditions.
- Each load test runs for several minutes to allow the system to reach a steady state and to collect sufficient performance metrics.

### 7.2 Fault Injection
- Kill Cassandra pod
- Kill Redis primary

### 7.3 Measurement Comparison
- Simplest baseline setup (1 api server 1 cassandra node)
- Full architecture setup

**Note**: Because the system runs on a single EC2 node, hardware resources become the global bottleneck. Therefore, horizontal scaling benefits are limited. However, architectural optimizations such as caching and asynchronous processing still provide significant performance improvements.

## Future:
- Multi-Node Environment
- Cloud services: EKS, EBS, External LB, ETC 