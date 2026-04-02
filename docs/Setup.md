# Setup Guide

End-to-end instructions for deploying the Distributed URL Shortener Service on AWS using two EC2 instances and a k3s Kubernetes cluster.

---

## Prerequisites

| Tool | Purpose |
| :--- | :--- |
| Docker | Build application images |
| kubectl | Interact with the Kubernetes cluster |
| k3s | Lightweight Kubernetes (installed on EC2) |
| k6 | Load testing (installed on load-tester EC2) |
| curl | Smoke-test API endpoints |

---

## Infrastructure

### EC2 Instances

| Instance | Type | Purpose |
| :--- | :--- | :--- |
| `load-tester` | `t3.medium` | Runs k6 load generator |
| `service-node` | `t4g.xlarge` | Runs k3s cluster (all services) |

Both instances must be in the same AWS VPC so the load tester can reach the service node's NodePort.

### Install k3s on `service-node`

```bash
curl -sfL https://get.k3s.io | sh -
# Export the kubeconfig
export KUBECONFIG=/etc/rancher/k3s/k3s.yaml
```

### Install AWS EBS CSI Driver (for network-backed PVCs)

Install the AWS EBS CSI driver in your cluster before deploying Cassandra/Redis:

```bash
helm repo add aws-ebs-csi-driver https://kubernetes-sigs.github.io/aws-ebs-csi-driver
helm repo update

kubectl create namespace kube-system --dry-run=client -o yaml | kubectl apply -f -
helm upgrade --install aws-ebs-csi-driver aws-ebs-csi-driver/aws-ebs-csi-driver \
  -n kube-system
```

Create the project StorageClass:

```bash
kubectl apply -f storage/ebs-gp3-storageclass.yaml
kubectl get storageclass
```

Expected class for this project: `ebs-csi-gp3` (provisioner: `ebs.csi.aws.com`).

### Non-Production Migration: local-path -> EBS CSI (k3s on EC2)

If this is a portfolio/non-production environment, the fastest migration is a destructive reset of StatefulSet PVCs.

```bash
chmod +x scripts/migrate-to-ebs-nonprod.sh
./scripts/migrate-to-ebs-nonprod.sh
```

What this script does:
1. Applies `storage/ebs-gp3-storageclass.yaml`.
2. Sets `ebs-csi-gp3` as default StorageClass and removes default flag from `local-path`.
3. Deletes Redis StatefulSets and PVCs, then recreates them.
4. Deletes Cassandra StatefulSet and PVCs, then recreates it.
5. Verifies new PVCs in `cassandra` and `redis` namespaces.

Verify resulting PVC storage classes:

```bash
kubectl get pvc -A -o custom-columns=NAMESPACE:.metadata.namespace,NAME:.metadata.name,SC:.spec.storageClassName,STATUS:.status.phase
```

### Install Kafka (external, on `service-node` or separate host)

Kafka runs outside the k3s cluster. After starting a Kafka broker, update the bootstrap-servers address in both `URLShortener/src/main/resources/application.yaml` and `url-write-consumer/src/main/resources/application.yaml`, then rebuild the images.

Create the required Kafka topic:
```bash
kafka-topics.sh --create \
  --bootstrap-server <KAFKA_IP>:9092 \
  --topic url.write \
  --partitions 3 \
  --replication-factor 1
```

---

## Deployment Order

Services must be deployed in order because Redis and Cassandra must be ready before the application pods start.

### 1. Deploy Cassandra

```bash
cd scripts
./deploy-cassandra.sh
```

Wait for all 3 Cassandra pods to be `Running`:
```bash
kubectl get pods -n cassandra -w
```

#### Initialize Cassandra Schema

Run this once after the first deployment:
```bash
# Create keyspace
kubectl exec -it cassandra-0 -n cassandra -- cqlsh -e "
  CREATE KEYSPACE IF NOT EXISTS url_shortener
  WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 2};
"

# Create URL table
kubectl exec -it cassandra-0 -n cassandra -- cqlsh -e "
  CREATE TABLE IF NOT EXISTS url_shortener.urls (
    short_url text PRIMARY KEY,
    long_url  text,
    created_at timestamp
  );
"

# Create Dead Letter Table (for failed Kafka messages)
kubectl exec -it cassandra-0 -n cassandra -- cqlsh -e "
  CREATE TABLE IF NOT EXISTS url_shortener.failed_messages (
    id           uuid PRIMARY KEY,
    topic        text,
    partition_id int,
    offset_value bigint,
    message_key  text,
    payload      text,
    error_message text,
    failed_at    timestamp
  );
"
```

### 2. Deploy Redis + Redis Sentinel

```bash
cd scripts
./deploy-redis.sh
```

Verify all 6 pods are `Running` (3 Redis + 3 Sentinel):
```bash
kubectl get pods -n redis
```

Confirm Sentinel recognises the master:
```bash
kubectl exec -n redis redis-sentinel-0 -- redis-cli -p 26379 SENTINEL get-master-addr-by-name mymaster
```

### 3. Build and Deploy Application Services

```bash
cd scripts
./deploy-urlshortener.sh
./deploy-url-write-consumer.sh
```

These scripts:
1. Build the `spring-server` Docker image from `URLShortener/`
2. Build the `url-write-consumer` Docker image from `url-write-consumer/`
3. Apply the Kubernetes manifests for the API Service and Writer Service
4. Wait for each deployment to be healthy

Legacy combined command is still available:

```bash
./deploy-spring.sh
```

> **Tip**: If you rebuild images using the same tag (`spring-server`, `url-write-consumer`), restart the deployments to pick up the new image:
> ```bash
> kubectl rollout restart deployment/spring-deployment
> kubectl rollout restart deployment/consumer-deployment
> ```

### 4. (Optional) Deploy Observability Stack

```bash
kubectl apply -f observability/prometheus-config.yaml
kubectl apply -f observability/prometheus-deployment.yaml
kubectl apply -f observability/grafana-deployment.yaml
```

Prometheus scrapes the Spring Boot Actuator endpoint at `/actuator/prometheus` and k6 metrics pushed via Remote Write.

---

## Verify the Deployment

```bash
# All namespaces overview
kubectl get pods -A

# API Service NodePort
kubectl get svc spring-service
```

Smoke test:
```bash
NODE_IP=<service-node public IP>

# Write a short URL
curl -i -X PUT "http://$NODE_IP:30000/?short=test&long=https://example.com"

# Redirect
curl -i "http://$NODE_IP:30000/test"

# Debug cache
curl "http://$NODE_IP:30000/debug/cache/test"
```

### Kafka-Down API Scale Test Mode

If Kafka broker is intentionally down and you only want to compare 1 vs 2 API replicas, set API write mode to direct Cassandra writes:

```bash
kubectl set env deployment/spring-deployment APP_WRITE_MODE=direct
kubectl rollout restart deployment/spring-deployment
kubectl rollout status deployment/spring-deployment
```

Restore normal async write path after Kafka is back:

```bash
kubectl set env deployment/spring-deployment APP_WRITE_MODE=kafka
kubectl rollout restart deployment/spring-deployment
kubectl rollout status deployment/spring-deployment
```

---

## Seed Data (for Load Testing)

The `seed.sh` script inserts 10,000 URLs so the load tests can exercise cache hit/miss behaviour.

Update `BASE_URL` inside the script to point to your service node, then run:
```bash
cd scripts
./seed.sh
```

---

## Load Testing

Run load tests from the `load-tester` EC2 instance. Copy the scripts in `loadTesting/` to that machine.

Install k6:
```bash
# On the load-tester EC2
sudo gpg --no-default-keyring --keyring /usr/share/keyrings/k6-archive-keyring.gpg \
  --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys C5AD17C747E3415A3642D57D77C6C491D6AC1D69
echo "deb [signed-by=/usr/share/keyrings/k6-archive-keyring.gpg] https://dl.k6.io/deb stable main" \
  | sudo tee /etc/apt/sources.list.d/k6.list
sudo apt update && sudo apt install k6
```

Run a test scenario:
```bash
# Read-only workload
k6 run loadTesting/read-test.js

# Write-only workload
k6 run loadTesting/write-test.js

# Mixed (80% reads / 20% writes)
k6 run loadTesting/mixed-test.js
```

See [Test.md](Test.md) for detailed scenario descriptions and fault injection steps.

---

## Teardown

```bash
cd scripts
./teardown.sh
```

This removes all Kubernetes resources. Cassandra PVCs are intentionally kept so data survives a redeploy. To also delete Cassandra data:
```bash
kubectl delete pvc -n cassandra --all
```
