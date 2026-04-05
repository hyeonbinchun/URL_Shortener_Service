# Distributed URL Shortener

A horizontally scalable, fault-tolerant URL shortener built to demonstrate real-world distributed systems tradeoffs — including caching strategies, asynchronous writes, sentinel-based failover, and dead-letter handling — deployed on Kubernetes (k3s).

## Performance Highlights

<p>
  <img src="/assets/dashboard.png" alt="Graph" width="700" style="max-width: 100%; height: auto;" />
</p>

| Architecture | Peak Read | Peak Write | VUs |
| :--- | :---: | :---: | :---: |
| Baseline (1 API + 1 Cassandra) | 2.36k req/s | 2.09k req/s | 5,000 |
| Horizontal API Scaling (3 API + 1 Cassandra) | 3.94k req/s | 4.05k req/s | 5,000 |
| Cassandra Scaling (3 API + 3 Cassandra) | 3.52k req/s | 3.32k req/s | 5,000 |
| Redis Caching | 6.51k req/s | — | 10,000 |
| Kafka Async Writes | — | 6.13k req/s | 10,000 |

Full benchmark tables: [docs/TEST_RESULTS.md](docs/TEST_RESULTS.md)

## Architecture Overview

<p align="center">
  <img src="/assets/architecture.png" alt="Architecture Diagram" width="500" style="max-width: 100%; height: auto;" />
</p>

Full architecture and design decisions: [docs/SYSTEM_DESIGN.md](docs/SYSTEM_DESIGN.md)

## Tech Stack

| Layer | Technology |
| :--- | :--- |
| API Service | Java 21 + Spring Boot 3 |
| Writer Service | Java 21 + Spring Boot 3 | 
| Cache | Redis 7 |
| Database Apache | Cassandra 4.1 | 
| Message Queue | Apache Kafka |
| Orchestration | Kubernetes / k3s |
|Observability | Prometheus + Grafana |
| Load Testing | k6 |
| Automation | Bash deploy scripts | 

## Key Design Highlights

- **Cache-Aside Read Path** — Redis replicas serve reads; misses fall back to Cassandra and lazily populate the cache.
- **Write-Around Async Write Path** — Kafka decouples the API from Cassandra writes, enabling non-blocking responses and traffic spike buffering.
- **Redis Sentinel Failover** — Three Sentinel pods monitor the Redis primary; on failure, a replica is promoted and the Spring client reconnects transparently.
- **Dead Letter Topic (DLT)** — Failed Kafka messages are retried 3× then routed to url.write.dlt and persisted to url_shortener.failed_messages in Cassandra.
- **Multi-Node Kubernetes (k3s)** — 3-EC2-node cluster validates horizontal scaling, node failover, and distributed recovery under fault injection.


## Quick Start

See [docs/SETUP.md](docs/SETUP.md) for the full setup guide.

**Deploy**

```bash
# Deploy all services
./scripts/deploy-all.sh

# Teardown
./scripts/teardown.sh
```

**API Usage**
```bash
# Shorten a URL
curl -i -X PUT 'http://<NODE_IP>:30000/?short=abc&long=https://example.com'

# Resolve a short code
curl -i 'http://<NODE_IP>:30000/abc'
```

## Documentation
| Document | Purpose |
| :--- | :--- |
| [docs/SYSTEM_DESIGN.md](docs/SYSTEM_DESIGN.md) | Architecture, design decisions, scalability, and fault tolerance |
| [docs/SETUP.md](docs/SETUP.md) | Deployment and cloud setup guide |
| [docs/TESTING.md](docs/TESTING.md) | Test plan and fault-injection workflow |
| [docs/TEST_RESULTS.md](docs/TEST_RESULTS.md) | Benchmarking methodology, results, and analysis |
| [docs/COMMANDS.md](docs/COMMANDS.md) | Operational command reference |