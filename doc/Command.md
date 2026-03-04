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

## Kubernetes
### create or update resources defined in a YAML file:
Pod:
```
kubectl apply -f nginx-pod.yaml 
```

Deployment:
```
kubectl apply -f spring-deployment.yaml 
```

Service:
```
kubectl apply -f spring-service.yaml
```

### lists the Pods running in your cluster:
Deployment:
```
kubectl get deployment
```

ReplicaSet:
```
kubectl get replicaSet
```

Pod:
```
kubectl get pods
```