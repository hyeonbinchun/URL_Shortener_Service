# Setup Guide

End-to-end instructions for deploying the Distributed URL Shortener Service on AWS using EC2 instances and a k3s Kubernetes cluster.

---

## Infrastructure

### EC2 Instances

| Instance | Type | Purpose |
| :--- | :--- | :--- |
| `service-node1` | `t4g.large` | Control Plane|
| `service-node2` | `t4g.large` | Worker Node|
| `service-node3` | `t4g.large` | Worker Node|
| `kafka-server` | `t4g.large` | Runs kafka broker |
| `load-tester` | `t3.medium` | Runs k6 load generator |

Instances must be in the same AWS VPC so the load tester can reach the service node's NodePort.



### 1. Setup `service-node1` (Control Plane)

#### 1.1 Install k3s

```bash

# Install k3s
curl -sfL https://get.k3s.io | sh -

# Make kubeconfig readable
sudo chmod 644 /etc/rancher/k3s/k3s.yaml

# Set KUBECONFIG for current session + permanently
export KUBECONFIG=/etc/rancher/k3s/k3s.yaml
echo "export KUBECONFIG=/etc/rancher/k3s/k3s.yaml" >> ~/.bashrc
source ~/.bashrc
```

#### 1.2 Install AWS EBS CSI Driver (for network-backed PVCs)

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

#### 1.3 Configure security groups

Control plane and worker nodes use the same security group.

Configure the following inbound rules:

| Port | Protocol | Source | Purpose |
| :--- | :--- | :--- | :--- |
| 22 | TCP | 0.0.0.0/0 | SSH |
| 6443 | TCP | 0.0.0.0/0 | k3s control plane communication |
| 10250 | TCP | 0.0.0.0/0 | Kubelet |
| 8472 | UDP | 0.0.0.0/0 | Flannel (VXLAN) |
| 30000-30002 | TCP | 0.0.0.0/0 | Kubernetes NodePort (API) |
| 9090 | TCP | 172.31.44.6/32 | Prometheus internal port for k6 |
| All | All | same security group | Intra-cluster communication |

### 2. Setup `service-node2` and `service-node3` (Worker Nodes)
#### 2.1 Get node token on your first EC2 (Control Plane)
```bash
# Get node token
sudo cat /var/lib/rancher/k3s/server/node-token

# use private IP if both EC2 are in same VPC
SERVER_IP=172.31.x.x
```
#### 2.2 Join the Cluster
```bash
curl -sfL https://get.k3s.io | K3S_URL=https://<CONTROL_PLANE_PRIVATE_IP>:6443 \
  K3S_TOKEN=<TOKEN_FROM_STEP_1> sh -
  
```

#### 2.3 Verify node joined on control plane EC2
```bash
kubectl get nodes

# you should see
NAME        STATUS   ROLES                  
master      Ready    control-plane
worker-1    Ready    <none>
```


### 3. Setup `kafka-server`
#### 3.1 Install and initialize Kafka
```bash
sudo apt update

sudo apt install openjdk-17-jdk

wget https://downloads.apache.org/kafka/4.0.1/kafka_2.13-4.0.1.tgz

tar -xzf kafka_2.13-4.0.1.tgz 

export KAFKA_HEAP_OPTS="-Xmx400m -Xms400m"

sudo dd if=/dev/zero of=/swapfile bs=128M count=16

sudo chmod 600 /swapfile

sudo mkswap /swapfile

sudo swapon /swapfile

# Persist swap across reboot
sudo vi /etc/fstab
	Add: "/swapfile swap swap defaults 0 0"

-----
vi config/server.properties 
# Replace localhost with the EC2 public IP in listeners/advertised listeners

KAFKA_CLUSTER_ID="$(bin/kafka-storage.sh random-uuid)"

bin/kafka-storage.sh format --standalone -t $KAFKA_CLUSTER_ID -c config/server.properties
```
#### 3.2 Update application bootstrap servers and rebuild images
Kafka runs outside the k3s cluster. After starting a Kafka broker, update the bootstrap-servers address in both `URLShortener/src/main/resources/application.yaml` and `url-write-consumer/src/main/resources/application.yaml`, then rebuild the images.

#### 3.3 Create the required Kafka topic:
```bash
kafka-topics.sh --create \
  --bootstrap-server <KAFKA_IP>:9092 \
  --topic url.write \
  --partitions 3 \
  --replication-factor 1
```


### 4 Install k6 on `load-tester`

```bash
sudo mkdir -p /root/.gnupg
sudo chmod 700 /root/.gnupg

sudo gpg --no-default-keyring \
  --keyring /usr/share/keyrings/k6-archive-keyring.gpg \
  --keyserver hkp://keyserver.ubuntu.com:80 \
  --recv-keys C5AD17C747E3415A3642D57D77C6C491D6AC1D69 && \
echo "deb [signed-by=/usr/share/keyrings/k6-archive-keyring.gpg] https://dl.k6.io/deb stable main" | \
  sudo tee /etc/apt/sources.list.d/k6.list && \
sudo apt-get update && \
sudo apt-get install k6
```
---

## Deployment Order

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