# Phase 8 — Kubernetes

## Overview

Phase 8 deploys ShopFlow as a multi-service Kubernetes application.

The Kubernetes deployment includes:

- API Gateway
- User Service
- Product Service
- Order Service
- PostgreSQL
- Redis
- Kafka
- Kafka topic initialization Job

This phase demonstrates container orchestration, service discovery, persistent storage, health checks, configuration management, secrets, resource management, and Kubernetes networking.

## Architecture

```text
                         API Gateway
                            |
              +-------------+-------------+
              |             |             |
              v             v             v
        User Service   Product Service  Order Service
              |             |             |
              +-------------+-------------+
                            |
                       PostgreSQL

                  Product Service
                       |
                     Redis

              Order/Product Services
                       |
                      Kafka
```

## Kubernetes Resources

### Namespace

All ShopFlow resources are isolated inside the `shopflow` namespace.

### ConfigMap

The `shopflow-config` ConfigMap stores non-sensitive configuration such as:

- Service URLs
- Redis host and port
- Kafka bootstrap server
- CORS configuration
- Server port

### Secret

The `shopflow-secrets` Kubernetes Secret stores sensitive values such as:

- Database username
- Database password
- JWT secret
- Admin credentials
- Internal API key

Production deployments should use a dedicated secret-management solution rather than committing real credentials to Git.

### Deployments

The following application components use Kubernetes Deployments:

- User Service
- Product Service
- Order Service
- API Gateway
- Redis

### StatefulSets

The following stateful infrastructure components use StatefulSets:

- PostgreSQL
- Kafka

StatefulSets provide stable identities and persistent storage for stateful workloads.

### Services

Kubernetes Services provide stable DNS names and networking between components:

- `user-service:8081`
- `product-service:8082`
- `order-service:8083`
- `postgres:5432`
- `redis:6379`
- `kafka:9092`
- `api-gateway:8080`

### Persistent Storage

PostgreSQL and Kafka use PersistentVolumeClaims so their data is not tied to the lifecycle of an individual pod.

### Kafka Initialization Job

A Kubernetes Job creates the ShopFlow Kafka topics after Kafka becomes available:

- `order.created.v1`
- `stock.reserved.v1`
- `stock.reservation.failed.v1`
- `order.cancelled.v1`

## Health Checks

Application services expose Spring Boot Actuator health endpoints.

Kubernetes uses:

- Readiness probes to determine whether a pod can receive traffic.
- Liveness probes to determine whether a container needs to be restarted.

Infrastructure components use appropriate command-based health checks.

## Resource Management

Application and infrastructure containers define CPU and memory requests and limits.

This demonstrates Kubernetes resource management and prevents individual workloads from consuming unlimited cluster resources.

## Local Kubernetes Deployment

The project is designed for local Kubernetes development using Docker Desktop Kubernetes or another local Kubernetes cluster.

First verify Kubernetes:

```bash
kubectl version --client
kubectl config current-context
kubectl get nodes
```

Build the ShopFlow images:

```bash
docker build -f services/user-service/Dockerfile -t shopflow/user-service:latest services
docker build -f services/product-service/Dockerfile -t shopflow/product-service:latest services
docker build -f services/order-service/Dockerfile -t shopflow/order-service:latest services
docker build -f services/api-gateway/Dockerfile -t shopflow/api-gateway:latest services
```

Apply the manifests:

```bash
kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/configmap.yaml
kubectl apply -f k8s/secret.yaml
kubectl apply -f k8s/postgres-init.yaml
kubectl apply -f k8s/postgres.yaml
kubectl apply -f k8s/redis.yaml
kubectl apply -f k8s/kafka.yaml
kubectl apply -f k8s/user-service.yaml
kubectl apply -f k8s/product-service.yaml
kubectl apply -f k8s/order-service.yaml
kubectl apply -f k8s/api-gateway.yaml
kubectl apply -f k8s/kafka-init.yaml
```

Check the cluster:

```bash
kubectl get pods -n shopflow
kubectl get services -n shopflow
kubectl get deployments -n shopflow
kubectl get statefulsets -n shopflow
kubectl get jobs -n shopflow
```

The API Gateway is exposed through NodePort `30080` in the local Kubernetes configuration.

## Useful Troubleshooting Commands

Check pod details:

```bash
kubectl describe pod <pod-name> -n shopflow
```

View application logs:

```bash
kubectl logs deployment/user-service -n shopflow
kubectl logs deployment/product-service -n shopflow
kubectl logs deployment/order-service -n shopflow
kubectl logs deployment/api-gateway -n shopflow
```

Check PostgreSQL:

```bash
kubectl logs statefulset/postgres -n shopflow
```

Check Kafka:

```bash
kubectl logs statefulset/kafka -n shopflow
```

Check the Kafka initialization Job:

```bash
kubectl logs job/kafka-init -n shopflow
```

Check events:

```bash
kubectl get events -n shopflow --sort-by=.lastTimestamp
```

## Phase 8 Learning Outcomes

After completing this phase, ShopFlow demonstrates practical knowledge of:

- Kubernetes Deployments
- Kubernetes StatefulSets
- Kubernetes Services
- Kubernetes Namespaces
- ConfigMaps
- Secrets
- PersistentVolumeClaims
- Kubernetes Jobs
- Readiness probes
- Liveness probes
- Resource requests and limits
- Kubernetes DNS/service discovery
- Container orchestration
- Stateful application deployment
- Kafka deployment
- Redis deployment
- PostgreSQL deployment

## Interview Questions

### 1. Why use Kubernetes Services?

Pods are ephemeral and their IP addresses can change. A Kubernetes Service provides a stable network endpoint and DNS name for accessing a group of pods.

### 2. Deployment vs StatefulSet?

Deployments are normally used for stateless workloads. StatefulSets are designed for workloads requiring stable identities and persistent storage.

### 3. ConfigMap vs Secret?

ConfigMaps store non-sensitive configuration. Secrets are intended for sensitive configuration such as credentials and API keys.

### 4. Readiness vs liveness probe?

A readiness probe determines whether a pod should receive traffic. A liveness probe determines whether the container is healthy enough to continue running.

### 5. Why does PostgreSQL need persistent storage?

Database data must survive pod restarts and rescheduling. PersistentVolumeClaims provide storage independent of the pod lifecycle.

### 6. Why does Kafka use a StatefulSet?

Kafka is stateful and benefits from stable identities and persistent storage.

### 7. What is Kubernetes service discovery?

Kubernetes automatically provides DNS names for Services. Applications can communicate using names such as `postgres`, `redis`, and `kafka` instead of hard-coded pod IP addresses.

### 8. Why define resource requests and limits?

Requests influence scheduling and guarantee a baseline allocation. Limits cap resource consumption and help prevent a workload from consuming excessive cluster resources.

## Phase 8 Status

- [x] Kubernetes manifests created
- [x] Namespace configuration
- [x] ConfigMap
- [x] Secret configuration
- [x] PostgreSQL StatefulSet
- [x] Redis Deployment
- [x] Kafka StatefulSet
- [x] Kafka initialization Job
- [x] User Service Deployment
- [x] Product Service Deployment
- [x] Order Service Deployment
- [x] API Gateway Deployment
- [x] Health probes
- [x] Resource requests and limits

Deployment verification is completed after the manifests are applied to the local Kubernetes cluster.
