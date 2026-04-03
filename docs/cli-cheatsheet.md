# Useful Command Line

## Kubernetes Deploy Scripts
### Deploy URLShortener API
```
./scripts/deploy-urlshortener.sh
```

### Deploy URL Write Consumer
```
./scripts/deploy-url-write-consumer.sh
```
---
## Kubernetes
### 
```Bash
# List all pods across namespaces with node info
kubectl get pods -o wide --all-namespaces

# Apply or update Spring Boot deployment
kubectl apply -f spring-deployment.yaml

# Restart deployment to pick up changes
kubectl rollout restart deployment spring-deployment

# Watch rollout status until complete
kubectl rollout status deployment spring-deployment

# Filter pods by specific namespaces (default, redis, cassandra)
kubectl get pods -o wide --all-namespaces | grep -E '^(default|redis|cassandra)\s'
```

---
## Docker
### Build & Push Images (Spring Boot)
```Bash
# List local images
docker images

# Authenticate with Docker Hub
docker login

# Build images
docker build -t owenchun/spring-server:latest .
docker build -t owenchun/url-write-consumer:latest .

# Push images to registry
docker push owenchun/spring-server:latest
docker push owenchun/url-write-consumer:latest
```

---
## Cassandra
### General Commands
```Bash
# Open a shell in Cassandra pod
kubectl exec -it cassandra-0 -n cassandra -- bash

# Check cluster status
kubectl exec -it cassandra-0 -n cassandra -- nodetool status

# Open CQL shell
kubectl exec -it cassandra-0 -n cassandra -- cqlsh

# Delete all persistent volumes (destructive)
kubectl delete pvc -n cassandra --all
```

### CQL (Inside cqlsh)
```Bash
-- Create keyspace (Replication Factor = 2)
CREATE KEYSPACE IF NOT EXISTS url_shortener
WITH replication = {
  'class': 'SimpleStrategy',
  'replication_factor': 2
};

-- Use keyspace
USE url_shortener;

-- Main table
CREATE TABLE IF NOT EXISTS urls (
  short_url  text PRIMARY KEY,
  long_url   text,
  created_at timestamp
);

-- Failed messages table
CREATE TABLE IF NOT EXISTS failed_messages (
  id            uuid PRIMARY KEY,
  topic         text,
  partition_id  int,
  offset_value  bigint,
  message_key   text,
  payload       text,
  error_message text,
  failed_at     timestamp
);

-- Verify schema
DESCRIBE KEYSPACE url_shortener;

-- Common queries
SELECT * FROM url_shortener.urls;

SELECT COUNT(*) FROM url_shortener.urls;

TRUNCATE url_shortener.urls;
```

---
## Kafka
### 
```Bash
# Start Kafka broker as a daemon
bin/kafka-server-start.sh -daemon config/server.properties

# Stop Kafka broker
bin/kafka-server-stop.sh

# List all topics
bin/kafka-topics.sh --bootstrap-server localhost:9092 --list

# Describe a topic
bin/kafka-topics.sh --bootstrap-server localhost:9092 --describe --topic url.write

# Delete a topic
bin/kafka-topics.sh --bootstrap-server localhost:9092 --delete --topic url.write

# List all consumer groups
bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 --list

# Delete a consumer group
bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 --delete --group console-consumer-52174

# Create a topic with 3 partitions and RF=1
bin/kafka-topics.sh --create \
  --bootstrap-server localhost:9092 \
  --topic url.write \
  --partitions 3 \
  --replication-factor 1

# Read messages from beginning
bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic url.write --from-beginning
```

---
## Redis
### 
```Bash
# Open Redis CLI on primary pod
kubectl exec -it redis-primary-deployment-5bf74489c4-td2xp -n redis -- redis-cli

# List Redis pods with node info
kubectl get pods -n redis -o wide

# Check Sentinel masters
kubectl exec -it redis-sentinel-0 -n redis -- redis-cli -p 26379 sentinel masters

# Check Sentinel nodes for a master
kubectl exec -it redis-sentinel-0 -n redis -- redis-cli -p 26379 sentinel sentinels mymaster

# Inspect replication info of a Redis pod
kubectl exec -it redis-1 -n redis -- redis-cli info replication
```