# Distributed URL Shortener Service

A portfolio-grade URL shortener that demonstrates practical distributed system tradeoffs: cache-aside reads, asynchronous writes, Redis Sentinel failover, Cassandra persistence, and Kafka dead-letter handling on Kubernetes.

## Why This Project

- Built to show how a simple web API evolves under scale and failure.
- Validated on a multi-node Kubernetes cluster with load testing and fault injection.
- Designed to highlight architecture decisions, not just application features.

## What It Does

- Shortens URLs and redirects using a public HTTP API.
- Serves reads through Redis for low-latency lookups.
- Buffers writes through Kafka so the API can return quickly.
- Persists data in Cassandra for durable storage.
- Uses Redis Sentinel and Kafka retry/DLT handling for failure recovery.

## Key Features

- Cache-aside read path with Redis replicas.
- Asynchronous write path with Kafka and a separate writer service.
- Redis Sentinel-based master failover.
- Kafka retry handling with a dead-letter topic for failed writes.
- Kubernetes manifests and scripts for repeatable deployment.
- Load testing and fault-injection scenarios for benchmarking reliability and performance.

## Architecture at a Glance

| Layer | Technology |
| :--- | :--- |
| API Service | Java 21 + Spring Boot 3 |
| Writer Service | Java 21 + Spring Boot 3 |
| Cache | Redis 7 + Redis Sentinel |
| Database | Apache Cassandra 4.1 |
| Streaming | Apache Kafka |
| Orchestration | Kubernetes / k3s |
| Observability | Spring Boot Actuator, Prometheus, Grafana |
| Load Testing | k6 |

## Quick Start

1. Review the setup guide in [docs/SETUP.md](docs/SETUP.md).
2. Deploy the stack from the repository root:

```bash
./scripts/deploy-all.sh
```

3. Tear the environment down when finished:

```bash
./scripts/teardown.sh
```

## API Examples

Shorten a URL:

```bash
curl -i -X PUT 'http://<NODE_IP>:30000/?short=abc&long=https://example.com'
```

Redirect using a short code:

```bash
curl -i 'http://<NODE_IP>:30000/abc'
```

Inspect cache state:

```bash
curl 'http://<NODE_IP>:30000/debug/cache/abc'
```

## Documentation

| Document | Purpose |
| :--- | :--- |
| [docs/Project_Overview.md](docs/Project_Overview.md) | Architecture, data flow, scalability, and design decisions |
| [docs/TEST_RESULTS.md](docs/TEST_RESULTS.md) | Benchmark methodology, results, and conclusions |
| [docs/SETUP.md](docs/SETUP.md) | Installation and environment setup |
| [docs/COMMANDS.md](docs/COMMANDS.md) | Operational command reference |
| [docs/TESTING.md](docs/TESTING.md) | Test plan and fault-injection workflow |

## Repository Layout

- `URLShortener/`: API service and deployment manifests.
- `url-write-consumer/`: Kafka consumer that writes events to Cassandra.
- `redis/`: Redis and Sentinel manifests.
- `cassandra/`: Cassandra manifests.
- `observability/`: Prometheus and Grafana configuration.
- `loadTesting/`: k6 scenarios for reads, writes, and mixed traffic.

## Highlights

- Horizontal API scaling increased peak write throughput from 2.09k req/s to 4.05k req/s.
- Redis caching raised peak read throughput to 6.51k req/s at 10,000 virtual users.
- Kafka asynchronous writes reached 6.13k req/s under peak write load.
- Redis Sentinel failover preserved availability during master replacement.
- Dead-letter handling ensured failed writes were retained for inspection instead of being dropped.