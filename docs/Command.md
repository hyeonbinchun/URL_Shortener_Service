# Useful Command Line

## Spring Boot
### Clean and build (creates a JAR file)
```
./mvnw clean package
```

### Run directly with Maven
```
./mvnw spring-boot:run
```

## Docker
### Build Docker Image for Spring Boot
```
docker build -t spring-server .
```

### Build Docker Image for URL Write Consumer
```
docker build -t url-write-consumer .
```

### List Docker image files
```
docker image ls
```

## Kubernetes Deploy Scripts
### Deploy URLShortener API
```
./scripts/deploy-urlshortener.sh
```

### Deploy URL Write Consumer
```
./scripts/deploy-url-write-consumer.sh
```

## Cassandra
### status
kubectl exec -it cassandra-0 -n cassandra -- nodetool status
### remove the dead nodes properly
kubectl exec -it cassandra-0 -n cassandra -- nodetool removenode <host-id>
### delete vol 
kubectl delete pvc -n cassandra --all

### create failed messages table (one-time)
kubectl exec -it cassandra-0 -n cassandra -- cqlsh -e "CREATE TABLE IF NOT EXISTS url_shortener.failed_messages (id uuid PRIMARY KEY, topic text, partition_id int, offset_value bigint, message_key text, payload text, error_message text, failed_at timestamp);"