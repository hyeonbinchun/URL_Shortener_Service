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

### List Docker image files
```
docker image ls
```

## Cassandra
### status
kubectl exec -it cassandra-0 -n cassandra -- nodetool status
### remove the dead nodes properly
kubectl exec -it cassandra-0 -n cassandra -- nodetool removenode <host-id>
### delete vol 
kubectl delete pvc -n cassandra --all