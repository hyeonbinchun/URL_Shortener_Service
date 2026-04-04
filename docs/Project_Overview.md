# Distributed URL Shortener

## Purpose

This project demonstrates how a URL shortener can evolve into a distributed system with clear scalability, fault tolerance, and observability tradeoffs. The application is intentionally small in scope so the architectural decisions are easy to understand and evaluate.

## System Overview

The system is composed of the following services:

| Component | Responsibility |
| :--- | :--- |
| API Service | Handles create and redirect requests |
| Redis | Serves low-latency reads and cache-aside lookups |
| Cassandra | Stores canonical URL mappings and failure records |
| Kafka | Buffers write events for asynchronous processing |
| Writer Service | Consumes Kafka events and persists them to Cassandra |
| Redis Sentinel | Monitors Redis health and promotes replicas on failure |
| Prometheus and Grafana | Collect metrics and visualize performance |
| k6 | Generates load for benchmarking and testing |

## Architecture

The application uses two primary request paths:

- Read path: the API checks Redis first, falls back to Cassandra on cache miss, then repopulates the cache.
- Write path: the API publishes a Kafka event and returns immediately; the Writer Service later persists the event to Cassandra.

This separation keeps read latency low while preventing writes from blocking on database performance.

## Runtime Topology

| Layer | Kubernetes Pattern | Notes |
| :--- | :--- | :--- |
| API Service | Deployment | Horizontally scalable stateless service |
| Writer Service | Deployment | Multiple consumers can share a Kafka consumer group |
| Redis | StatefulSet + Sentinel | Primary/replica topology with failover |
| Cassandra | StatefulSet | Durable storage with partitioned data distribution |
| Observability | Deployment | Prometheus scrapes metrics and Grafana visualizes them |

## Data Flow

### Read Path

1. Client requests a short URL redirect.
2. API checks Redis replicas for the mapping.
3. If the cache misses, the API reads from Cassandra.
4. The mapping is written back to Redis for future requests.

### Write Path

1. Client submits a new short URL mapping.
2. API publishes the write event to Kafka.
3. Writer Service consumes the event and writes it to Cassandra.
4. Failed writes are retried and eventually routed to the dead-letter topic.
5. DLT processing persists failed events for inspection or replay.

## Design Decisions

### Redis for Read Latency

Redis is used as a cache layer because URL redirects are read-heavy and benefit from memory-backed lookups. The tradeoff is that cache state must be kept coherent enough for the workload, which is handled through cache-aside behavior.

### Cassandra for Durable Storage

Cassandra provides horizontal scalability and durable persistence for URL mappings and failure records. It is a good fit for write distribution and availability, but it introduces distributed storage tradeoffs such as replication overhead and weaker consistency than a single-node database.

### Kafka for Asynchronous Writes

Kafka decouples request handling from persistence. The API stays responsive during spikes, and the Writer Service absorbs write traffic independently. The tradeoff is eventual consistency between request acceptance and durable storage.

### Writer Service as a Separate Consumer

Separating the consumer from the API keeps responsibilities clear and makes write throughput easier to scale independently. It also creates a clean boundary for retries, error handling, and observability.

### Dead Letter Handling

Failed Kafka messages are retried with exponential backoff and then sent to a dead-letter topic. A dedicated DLT consumer stores the failed event details in Cassandra so no write is silently dropped.

### Kubernetes for Deployment

Kubernetes provides the restart, scheduling, and service-discovery behavior needed for a resilient distributed application. Stateful workloads use StatefulSets, while stateless services use Deployments for straightforward scaling.

## Observability

The system exposes metrics through Spring Boot Actuator and collects them with Prometheus. Grafana is used for dashboards during load testing and fault injection. The observability layer focuses on request throughput, latency percentiles, and error rates.

## Scalability Notes

- API capacity is increased by adding more stateless pods.
- Redis scales read traffic by serving requests from replicas.
- Cassandra scales data storage and persistence across multiple nodes.
- Kafka allows write throughput to scale independently from the API.

## Related Documentation

- [README.md](../README.md) for the project summary and quick start.
- [TEST_RESULTS.md](TEST_RESULTS.md) for benchmark methodology and outcomes.
- [SETUP.md](SETUP.md) for deployment and environment setup.
- [COMMANDS.md](COMMANDS.md) for operational commands.
