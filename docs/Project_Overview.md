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
| Writer Service | Java + Spring Boot | Kafka consumer for DB writes and cache updates
| Cache | Redis | Low-latency reads
| Database | Apache Cassandra | Horizontal scalability
| Streaming | Kafka | Async writes
| Containeralization | Docker | Environment isolation
| Orchestration | Kubernetes | Scaling and self-healing
| Observability | Prometheus + Grafana | Metrics collection and visualization
| Load Testing | k6 | Performance benchmarking under high load
| Automation | Bash | Deployment scripts and infrastructure management 

### 2.3 System Architecture

```
# Full Architecture Plan

Load Generator (k6) → Prometheus → Grafana
    ↓
Client / Virtual Users
    ↓
API Service (Producer) 
    ↓                    
-------------------- READ PATH --------------------
    │
    ├─> Redis Replicas (read-heavy queries)
    │           ↑
    │           │
    │       Replication from Primary
    │
    └─> Cassandra DB (fallback on cache miss)
            │
            └─> Updates Redis Primary → propagates to Replicas


-------------------- WRITE PATH -------------------
    │
    └─> Kafka (Durable Log)
                ↓   
    Writer Service (Consumer)
                ↓
Cassandra DB + Cache Evict(DELETE) + Logging
```

**Read Path (Synchronous)**:
1. Reads go to Redis Replicas first.
2. On cache miss → read from Cassandra, then populate Redis Primary, which replicates to Replicas.

**Write Path (Asynchronous)**:
1. Queue write request to Kafka → return immediately (non-blocking)
2. API writes are pushed to Kafka → Writer Service handles DB writes.
3. Writer Service invalidates cache (deletes keys) after successful write.
4. Writer Service logs both write operations and cache eviction for observability.

**Separation of responsibilities**:
- Kafka: durable message transport only.
- Writer Service: writes to Cassandra, invalidates cache, logs events.
- Redis Primary: receives lazy population from read misses; no direct write during writes.



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
- Updates Cassandra and invalidates Redis cache
- Logs operations:
    - Kafka message consumption status (success/failure)
    - Cassandra write success/failure
    - Redis cache eviction/update
    - Errors and exceptions for observability and debugging
- Tradeoff: adds complexity, requires monitoring of consumer lag

### 3.5 Containerization and Orchestration
- Docker + Kubernetes → easy deployment, scaling, self-healing
- Tradeoff: introduces operational complexity

### 3.6 Observability & Load Testing
- k6 → Generate HTTP load. Can push it’s own metrics to Prometheus  
- Prometheus + Grafana → Monitor latency, throughput, errors
- Tradeoff: simplified metrics for this project 


### 3.7 Observability & Load Testing
- k6 → Generate HTTP load. Can push it’s own metrics to Prometheus  
- Prometheus + Grafana → Monitor latency, throughput, errors
- Tradeoff: simplified metrics for this project 

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
- Kubernetes StatefulSet + Headless Service
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
- **Failure detection**: Kubernetes liveness probes (or Redis Sentinel/operator) detect Primary pod failure.
- **Replica promotion**: One of the existing Replicas is promoted to Primary. The operator updates client connections to the new Primary.
- **Client behavior**: The API service or Redis client automatically redirects writes to the new Primary.
- **Persistence**:
    - If persistent storage -> reloads persisted data on startup
    - Else: relies on its(replica) in-memory copy replicated from the old Primary.
- **Data guarantees**: No data is lost if replication was up-to-date at the time of Primary failure.


### Redis Replica failure:
- Can be replaced dynamically by adding a new Replica pod in Kubernetes.
- New Replica syncs data from Primary.

## 6. Observability & Metrics
**Metrics collected:**
- Throughput (RPS)
- P99 latency
- P95 latency
- P50 latency
- Error Rate (http_request_failed)


**Observability stack:**
- **Prometheus**: Scrapes metrics generated by k6
- **Grafana**: Visualizes metrics in dashboards

**Future Improvement**:
1. Add system-level metrics: CPU, Memory
2. Extend monitoring to all components: API Service, Redis, Kafka, Writer Service, Cassandra


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


## 8. Infrastructure Setup (for measurements, not for demo)

To keep the setup simple and avoid unnecessary complexity, we demonstrate the architecture using a minimal AWS configuration.

### Single-Node Simulation 

This project runs on a single EC2 instance to keep infrastructure costs minimal.
While components like Cassandra and the API service are deployed as multiple pods,
they share the same physical host — so this setup simulates distributed behavior
rather than providing true distributed fault isolation.

What this setup validly demonstrates:
- API horizontal scaling: k3s load balances real traffic across multiple pods
- Cache effectiveness: Redis hit/miss ratio and latency improvement are genuine
- Async write decoupling: Kafka offloads writes from the API response path

What requires a multi-node setup to demonstrate properly:
- Cassandra fault tolerance (node failure with data still available)
- True Cassandra read/write throughput scaling across nodes
- Network partition and split-brain scenarios


### EC2 #1: Load Tester 
The load testing tool (k6) must run independently from the system under test (backend services, databases, etc.). This separation is important because the load tester itself generates traffic and consumes computing resources such as CPU and memory. Running it on a separate instance ensures the test results are not skewed by resource contention.

### EC2 #2: Service Node
Runs the core application stack:
- API
- Writer
- Redis
- Cassandra
- Kafka
- Observability tools

### Orchestration: k3s
- Installs in ~5 minutes
- Lightweight compared to full Kubernetes distributions
- Still provides a real Kubernetes environment


### AWS Architecture

```
AWS VPC (default)

    EC2 #1 load-tester
        ↓
    EC2 #2 service-node
    (k3s cluster)
        │
        ├── API Service Pods (3 pods)
        ├── Writer Service Pod (1 pod)
        │
        ├── Redis (single Primary + single Replica)
        │
        ├── Cassandra (3 nodes(pods) with rf=2)
        │
        ├── Kafka (1 pod)
        │
        ├── Prometheus
        └── Grafana
```
Im skipping Prometheus and Grafana on EC2 for now and use k6 dashboard instaed (Temporary)

## EC2 Instance Recommendation

The EC2 instance type used in this project is chosen purely to ensure all pods
can run stably without OOM failures — not to maximize throughput or minimize latency.

The benchmark goal is to measure the **relative improvement** between two configurations:
- **Baseline**: 1 API pod, 1 Cassandra node, no cache, no async writes
- **Full architecture**: 3 API pods, 3 Cassandra nodes, Redis, Kafka

Since both configurations run on the **same EC2 instance**, the hardware is a constant.
The delta in throughput, latency, and error rate between the two runs reflects
architectural differences only — not vertical scaling.

In other words, a more powerful instance would shift both results upward equally,
but would not change the conclusion about what the distributed architecture gains you.

| EC # | Type | Reasoning | 
| :--- | :--- | :--- |
| Load tester | `t3.medium` | CPU matters for k6
| Service node | `t4g.xlarge` | Cassandra + Redis + Kafka need RAM

## Networking
```
                    User Request   
                         │
                         │
                         │
┌────────────────────────┼─────────────────────────┐
│  Kubernetes Cluster    │                         │
│                        │                         │
│  Namespace: default    │                         │
│  ┌─────────────────────▼────────────────────┐    │
│  │ spring-service (NodePort :30080)         │    │
│  └───────────────┬──────────────────────────┘    │
│                  │ routes to                     │
│  [Deployment] ──manages───────────────────────┐  │                   
│  ┌───────────────▼──────────────────────────┐ │  │
│  │ Spring Pod 1                             │ │  │
│  │ Spring Pod 2  (spring-deployment)        │ │  │
│  │ Spring Pod 3                             │ │  │
│  │                                          │ │  │
│  │  Controller → Service → Repository       │ │  │
│  │                    │                     │ │  │
│  └────────────────────┼─────────────────────┘ │  │
│  └────────────────────┼───────────────────────┘  │ 
│                       │ DNS across namespaces    │
│  Namespace: cassandra │                          │
│  [StatefulSet] ──manages──────────────────────┐  │ 
│  ┌────────────────────▼─────────────────────┐ │  │
│  │ Headless Service (cassandra-service)     │ │  │
│  │ cassandra.cassandra.svc.cluster.local    │ │  │
│  └───────┬───────────┬───────────┬──────────┘ │  │
│  │       │           │           │            │  │
│  │    [cass-0]    [cass-1]    [cass-2]        │  │
│  └────────────────────────────────────────────┘  │ 
└──────────────────────────────────────────────────┘
```


## Future:
- Multi-Node Environment
- Cloud services: EKS, EBS, External LB, ETC 