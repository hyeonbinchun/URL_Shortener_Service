# Distributed URL Shortener

## 1. Project Overview
### 1.1 Objective
Design and implement a horizontally scalable, fault-tolerant URL shortener capable of:

- Handling high read/write throughput
- Maintaining data durability
- Supporting asynchronous write optimization
- Demonstrating distributed system tradeoffs
- Operating in containerized cloud-native environments

The system prioritizes:
- Scalability
- Observability
- Resilience
- Performance benchmarking
- Tradeoff analysis

## 2. High-Level Architecture
### 2.1 Core Components
1. API Service (Spring Boot)
2. Redis Cache Layer
3. Cassandra Persistence Layer
4. Async Write Pipeline
5. Logger Service
6. Load Generator
7. Monitoring Stack

### 2.2 System Architecture

```
Client => API Gateway => URL API Service
    => Read: Redis Cache => Cassandra DB
    => Write: Redis Stream (Queue) => Writer Service => Cassandra DB => Cache Update + Logger
```

**Read Flow**:
1. Check Redis cache first
2. If cache miss → query Cassandra
3. Cache the result in Redis
4. Return to user

**Write Flow**:
1. Write request comes in → Only queue it to Redis Streams (queue_write())
2. Return immediately to user (non-blocking!)
3. Separate writer service consumes from Redis Streams asynchronously
4. Writer service then:
5. Writes to Cassandra
6. Deletes the key from Redis cache


## 3. Technology Stack
| Layer | Technology | Reasoning | 
| :--- | :--- | :--- |
| Backend | Java + Spring Boot | Production-ready framework
| Cache | Redis | Low-latency read optimization
| Database | Apache Cassandra | Horizontal scalability
| Containeralization | Docker | Environment isolation
| Orchestration | Kubernetes | Scaling & self-healing
| Monitoring | Prometheus + Grafana | Observability
| Streaming (Async) | Redis Streams | Durable async pipeline

## 4. System Design Decisions
### 4.1 Cassandra Configuration

Table: urls

| Column | Type | Description | 
| :--- | :--- | :--- |
| short_url | text (PK) | Partition key
| long_url | text | Original URL (Target URL)
| Dcreated_at | timestamp | Metadata



### 4.2 Caching Strategy
Read Flow:
1. Check Redis
2. If miss → query Cassandra
3. Populate cache

Write Flow (Synchronous Version):
1. Write to Redis Stream
2. Immediate 200 OK
3. Writer service processes queue
4. Write to Cassandra
5. Update cache
6. Log operation

### 4.1 Redis Configuration
- Primary with persistence (AOF)
- Replicas without persistence
- Eviction policy: allkeys-lru
- TTL for cache entries

## 5. Scalability Strategy
### 5.1 Horizontal Scaling
API layer:
- Kubernetes Deployment
- Horizontal Pod Autoscaler (CPU-based scaling)

Cassandra:
- StatefulSet
- Add nodes dynamically
- Automatic rebalancing

Redis:
- Single primary
- Read replicas
- Future option: Redis Cluster

### 5.2 Data Scalability
Handled by:
- Cassandra partitioning
- Replication
- Eventual consistency model

Demonstration:
- Add Cassandra node
- Observe reduced write pressure
- Observe data rebalancing

## 6. Fault Tolerance
### 6.1 
6.1 Node Failure
Cassandra:
- Replication ensures availability
- QUORUM writes prevent data loss

Redis:
- Replica promotion on primary failure

Kubernetes:
- Pod restart on crash
- Liveness and readiness probes

## 7. Observability & Metrics

Metrics collected:
- RPS
- P95 latency
- Cassandra write latency
- Queue depth (async mode)
Monitoring stack:
- Prometheus scraping
- Grafana dashboards
- Health endpoints via Spring Actuator


## 8. Testing Strategy
### 8.1 Load Testing

Custom load generator:
- Configurable RPS
- Thread control
- Duration-based execution

Scenarios:
- Read-heavy workload
- Write-heavy workload
- Mixed workload
- Node failure under load

### 8.2 Fault Injection
- Kill Cassandra pod
- Kill Redis primary
- Increase latency artificially
- Observe recovery time

## 9. Tradeoff Analysis

| Aspect | Sync Version | Async Version | 
| :--- | :--- | :--- |
| Latency | Higher | Lower
| Consistency | Stronger | Eventual
| Throughput | Limited by DB | Limited by queue
| Complexity | Lower | Higher
