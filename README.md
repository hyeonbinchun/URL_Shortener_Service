# Distributed URL Shortener Service

A horizontally scalable, fault-tolerant URL shortener built to demonstrate distributed system tradeoffs — caching, asynchronous writes, sentinel-based failover, and dead-letter handling — running on Kubernetes (k3s).

## Documentation

| Document | Description |
| :--- | :--- |
| [docs/Project_Overview.md](docs/Project_Overview.md) | Architecture, design decisions, scalability strategy, fault tolerance |
| [docs/Setup.md](docs/Setup.md) | Step-by-step deployment and local/cloud setup guide |
| [docs/Command.md](docs/Command.md) | Useful kubectl, Docker, and Cassandra commands |
| [docs/Test.md](docs/Test.md) | Load testing scenarios and fault injection steps |

---

## Architecture Overview
### Architecture Diagram

<p align="center">
  <img src="docs/images/architecture.png" alt="Architecture Diagram" width="1000" style="max-width: 100%; height: auto;" />
</p>

### Tech Stack

| Layer | Technology |
| :--- | :--- |
| API Service | Java 21 + Spring Boot 3 |
| Writer Service | Java 21 + Spring Boot 3 (Kafka consumer) |
| Cache | Redis 7 (1 master + 2 replicas) + Redis Sentinel (3 pods) |
| Database | Apache Cassandra 4.1 (3-node StatefulSet, rf=2) |
| Streaming | Apache Kafka (external) |
| Orchestration | Kubernetes / k3s |
| Observability | Spring Boot Actuator + Prometheus + Grafana |
| Load Testing | k6 |
| Automation | Bash deploy scripts |

---

## Quick Start

See [docs/Setup.md](docs/Setup.md) for the full setup guide.

### API Usage

**Shorten a URL:**
```bash
curl -i -X PUT 'http://<NODE_IP>:30000/?short=abc&long=https://example.com'
```

**Redirect via short code:**
```bash
curl -i 'http://<NODE_IP>:30000/abc'
```

**Debug cache status:**
```bash
curl 'http://<NODE_IP>:30000/debug/cache/abc'
```

---

## Key Design Highlights

- **Cache-Aside Read Path**: reads check Redis replicas first; on cache miss the API falls back to Cassandra and lazily populates the Redis primary.
- **Write-Around Async Write Path**: PUT requests publish a Kafka event and return immediately; the Writer Service persists to Cassandra asynchronously.
- **Redis Sentinel Failover**: 3 Sentinel pods monitor the Redis master; on failure a replica is promoted automatically and the Spring client reconnects transparently.
- **Dead Letter Topic (DLT)**: failed Kafka messages are retried 3 times (exponential backoff), then routed to `url.write.dlt` and persisted to `url_shortener.failed_messages` in Cassandra — no write is silently dropped.
- **Single-Node Kubernetes (k3s)**: all components run on one EC2 instance to minimise cost while still exercising real distributed behaviour (load-balanced pods, Sentinel failover, async write decoupling).