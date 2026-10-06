# Phase 5 — Docker and Docker Compose

## 1. Goal

Phase 5 containerizes the complete ShopFlow platform so the application can be started consistently with Docker Compose.

After this phase, ShopFlow will have:

- User Service container
- Product Service container
- Order Service container
- API Gateway container
- PostgreSQL container
- Redis container
- Kafka container
- A Docker Compose network connecting all services
- Health checks and service dependencies
- Environment-based configuration
- One-command local startup

The goal is reproducible local infrastructure without requiring PostgreSQL, Redis, or Kafka to be installed directly on the host.

---

## 2. Why Docker?

Before Phase 5, infrastructure was installed directly on macOS:

```text
macOS
 ├── PostgreSQL
 ├── Redis
 └── Kafka

Java processes
 ├── user-service
 ├── product-service
 ├── order-service
 └── api-gateway
```

This works locally but is difficult to reproduce on another machine.

Docker changes this to:

```text
Docker Compose
│
├── PostgreSQL
├── Redis
├── Kafka
│
├── user-service
├── product-service
├── order-service
└── api-gateway
```

Every component gets a predictable environment.

---

## 3. Architecture

```text
                         ┌─────────────────┐
                         │     Client      │
                         └────────┬────────┘
                                  │
                                  ▼
                         ┌─────────────────┐
                         │   API Gateway   │
                         │     :8080       │
                         └───────┬─────────┘
                                 │
                 ┌───────────────┼───────────────┐
                 ▼               ▼               ▼
          ┌────────────┐ ┌────────────┐ ┌────────────┐
          │   User     │ │  Product   │ │   Order    │
          │  :8081     │ │   :8082    │ │   :8083    │
          └─────┬──────┘ └─────┬──────┘ └─────┬──────┘
                │              │              │
                ▼              ▼              ▼
          ┌────────────┐ ┌────────────┐ ┌────────────┐
          │ PostgreSQL │ │ PostgreSQL │ │ PostgreSQL │
          │   users    │ │  products  │ │   orders   │
          └────────────┘ └────────────┘ └────────────┘

                         ┌────────────┐
                         │   Redis    │
                         └────────────┘

                         ┌────────────┐
                         │   Kafka    │
                         └────────────┘
```

---

## 4. Docker Concepts Learned

### Image

A Docker image is the packaged application template.

### Container

A running instance of an image.

### Dockerfile

Instructions used to build an image.

### Docker Compose

Defines and runs multiple containers together.

### Network

Allows containers to communicate using service names.

For example:

```text
order-service → product-service:8082
```

instead of:

```text
localhost:8082
```

Inside Docker, `localhost` means the current container.

---

## 5. Phase 5 File Structure

The target repository structure is:

```text
shopflow/
├── services/
│   ├── common/
│   ├── user-service/
│   ├── product-service/
│   ├── order-service/
│   └── api-gateway/
│
├── docker/
│   ├── user-service/
│   ├── product-service/
│   ├── order-service/
│   └── api-gateway/
│
├── docker-compose.yml
├── .dockerignore
├── .env
├── .gitignore
└── README.md
```

The exact Dockerfile location may be simplified if desired:

```text
services/user-service/Dockerfile
services/product-service/Dockerfile
services/order-service/Dockerfile
services/api-gateway/Dockerfile
```

---

## 6. Multi-Stage Docker Builds

ShopFlow should use multi-stage Docker builds.

Typical pattern:

```dockerfile
FROM eclipse-temurin:21-jdk AS build

WORKDIR /app

COPY pom.xml .
COPY services ./services

RUN ./mvnw clean package -DskipTests

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /app/services/.../target/*.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]
```

The important idea is:

```text
JDK image
   ↓
compile application
   ↓
JAR
   ↓
JRE image
   ↓
production container
```

The final image does not need the full JDK or Maven build environment.

---

## 7. Docker Compose Services

The Compose file will define:

```yaml
services:
  postgres-users:
  postgres-products:
  postgres-orders:
  redis:
  kafka:
  user-service:
  product-service:
  order-service:
  api-gateway:
```

Each service should have:

- image/build configuration
- environment variables
- ports where required
- dependencies
- health checks where useful
- persistent volumes for stateful infrastructure

---

## 8. PostgreSQL Containers

Each microservice owns its own database.

```text
user-service    → shopflow_users
product-service → shopflow_products
order-service   → shopflow_orders
```

This preserves the database-per-service architecture from earlier phases.

Example environment variables:

```text
DB_USERNAME=shopflow
DB_PASSWORD=...
DB_HOST=postgres-users
DB_PORT=5432
DB_NAME=shopflow_users
```

Do not use:

```text
DB_HOST=localhost
```

from inside a container.

---

## 9. Redis

Redis becomes another Compose service.

Application containers should connect using:

```text
redis:6379
```

not:

```text
localhost:6379
```

Redis is used for:

- product cache
- API gateway rate limiting

---

## 10. Kafka

Kafka becomes a Compose service.

Applications connect to Kafka using the Docker network hostname.

Inside Compose:

```text
kafka:9092
```

The host machine may use another advertised listener such as:

```text
localhost:9092
```

Kafka listener configuration must distinguish between:

- container-to-container traffic
- host-to-container traffic

This is one of the most common Kafka + Docker configuration problems.

---

## 11. Environment Variables

Secrets must never be hardcoded into Dockerfiles.

Use:

```text
.env
```

Example:

```text
DB_USERNAME=shopflow
DB_PASSWORD=change-me
JWT_SECRET=...
JWT_EXPIRATION=3600
CORS_ALLOWED_ORIGINS=http://localhost:8080
ADMIN_EMAIL=admin@example.com
ADMIN_PASSWORD=change-me
INTERNAL_API_KEY=change-me
KAFKA_BOOTSTRAP_SERVERS=kafka:9092
```

`.env` must remain ignored by Git.

Verify:

```bash
git status
```

and ensure `.env` is not staged.

---

## 12. Docker Compose Networking

Compose creates a private network.

For example:

```text
order-service
     |
     | HTTP
     ▼
product-service:8082
```

The hostname is the Compose service name.

Similarly:

```text
order-service → kafka:9092
product-service → kafka:9092
gateway → redis:6379
```

Do not use `localhost` for another container.

---

## 13. Health Checks

Health checks help Compose understand whether infrastructure is ready.

Examples:

PostgreSQL:

```text
pg_isready
```

Redis:

```text
redis-cli ping
```

Kafka:

Kafka readiness should be checked using a broker/topic command appropriate to the selected Kafka image.

Application services already expose Spring Boot Actuator health endpoints.

For example:

```text
http://localhost:8080/actuator/health
```

---

## 14. Startup Order

A basic dependency chain is:

```text
PostgreSQL ─────┐
Redis ──────────┼──→ Application services
Kafka ──────────┘
```

However:

> `depends_on` controls startup ordering, not application readiness unless health conditions are configured.

Applications must still tolerate infrastructure becoming available slightly later.

---

## 15. Building Images

From the repository root:

```bash
cd ~/projects/shopflow
docker compose build
```

Check images:

```bash
docker images
```

---

## 16. Starting ShopFlow

Start everything:

```bash
docker compose up -d
```

Check containers:

```bash
docker compose ps
```

View logs:

```bash
docker compose logs -f
```

View one service:

```bash
docker compose logs -f order-service
```

---

## 17. Stopping ShopFlow

Stop containers:

```bash
docker compose down
```

Stop and remove volumes:

```bash
docker compose down -v
```

Warning:

```text
docker compose down -v
```

deletes the Compose database volumes and therefore local database data.

Do not use it casually if you want to preserve test data.

---

## 18. Rebuilding After Code Changes

When application code changes:

```bash
docker compose build
docker compose up -d
```

Or:

```bash
docker compose up -d --build
```

---

## 19. Checking Application Health

After startup:

```bash
curl http://localhost:8080/actuator/health
curl http://localhost:8081/actuator/health
curl http://localhost:8082/actuator/health
curl http://localhost:8083/actuator/health
```

Expected:

```json
{"status":"UP"}
```

---

## 20. Kafka Verification

Check Kafka topics from inside the Kafka container.

The exact command depends on the Kafka image and version.

The expected topics remain:

```text
order.created.v1
order.cancelled.v1
stock.reserved.v1
stock.reservation.failed.v1
```

---

## 21. Database Persistence

Stateful infrastructure should use Docker volumes.

Example:

```text
postgres-users-data
postgres-products-data
postgres-orders-data
redis-data
kafka-data
```

This means restarting containers does not automatically delete data.

---

## 22. Docker Security

For production-quality containers:

- Do not put passwords in Dockerfiles.
- Do not commit `.env`.
- Prefer non-root application users.
- Keep images small.
- Do not expose databases publicly.
- Only expose ports required by the developer.
- Use environment variables for configuration.
- Keep internal services on the Compose network.

---

## 23. Common Docker Errors

### Error: Cannot connect to Docker daemon

Check Docker Desktop:

```bash
docker info
```

If Docker is not running, start Docker Desktop.

---

### Error: Port already allocated

Find the process:

```bash
lsof -i :8080
```

or change the host port in Compose.

---

### Error: Connection refused to localhost

Inside a container, replace:

```text
localhost
```

with the Compose service name.

Examples:

```text
postgres-users:5432
redis:6379
kafka:9092
product-service:8082
```

---

### Error: Kafka keeps restarting

Inspect:

```bash
docker compose logs kafka
```

Kafka listener configuration is usually the first thing to check.

---

### Error: Application starts before PostgreSQL

Check:

```bash
docker compose ps
docker compose logs postgres-users
docker compose logs user-service
```

Use health checks and resilient application startup.

---

## 24. Testing the Complete Containerized System

After everything is running:

```bash
docker compose ps
```

Then run the existing smoke test:

```bash
./scripts/smoke-test.sh
```

The Phase 3 smoke test should continue to work after containerization.

Phase 4 Kafka behavior should additionally be verified by:

1. Creating a product.
2. Adding stock.
3. Adding the product to a cart.
4. Creating an order.
5. Confirming the order initially enters `PENDING`.
6. Waiting for Kafka processing.
7. Confirming it becomes `CONFIRMED`.
8. Testing insufficient stock.
9. Confirming the order becomes `CANCELLED`.
10. Testing cancellation and stock release.
11. Verifying duplicate Kafka delivery does not corrupt stock.

---

## 25. Important Distributed-System Behavior

Docker does not change the architecture.

The Phase 4 event flow remains:

```text
Order Service
     |
     | order.created.v1
     ▼
   Kafka
     |
     ▼
Product Service
     |
     | stock.reserved.v1
     ▼
   Kafka
     |
     ▼
Order Service
     |
     ▼
CONFIRMED
```

Failure:

```text
Order Service
     |
     | order.created.v1
     ▼
Product Service
     |
     | insufficient stock
     ▼
stock.reservation.failed.v1
     |
     ▼
Order Service
     |
     ▼
CANCELLED
```

Cancellation:

```text
Order Service
     |
     | order.cancelled.v1
     ▼
   Kafka
     |
     ▼
Product Service
     |
     ▼
Release reservation
```

---

## 26. Interview Concepts

You should be able to explain:

### Why Docker?

To make application and infrastructure environments reproducible and isolated.

### Why Docker Compose?

To define and run a multi-container local environment as one application.

### Why multi-stage builds?

To separate compilation from runtime and reduce the final image size.

### Why not use localhost between containers?

Because `localhost` refers to the current container.

### What is a Docker volume?

Persistent storage managed outside the writable container layer.

### What is a Docker network?

A virtual network allowing containers to communicate using service names.

### Does depends_on guarantee readiness?

No. It primarily controls startup order. Health checks and application-level retry/readiness are still important.

### How does Docker fit Kubernetes?

Docker/Compose is useful for local development. Kubernetes provides orchestration, scaling, rolling deployments, service discovery, and self-healing for larger environments.

---

## 27. Phase 5 Completion Checklist

- [ ] Docker Desktop installed and running
- [ ] `docker info` works
- [ ] `.dockerignore` added
- [ ] Dockerfiles added
- [ ] `docker-compose.yml` added
- [ ] PostgreSQL containers running
- [ ] Redis container running
- [ ] Kafka container running
- [ ] User Service container running
- [ ] Product Service container running
- [ ] Order Service container running
- [ ] API Gateway container running
- [ ] Health checks pass
- [ ] Kafka topics available
- [ ] Smoke test passes
- [ ] Phase 4 event flow works
- [ ] `.env` is not committed
- [ ] Git status is clean
- [ ] Phase 5 documentation added to README
- [ ] Phase 5 changes committed and pushed

---

## 28. Expected Final Result

At the end of Phase 5, one command should be enough to start the complete local ShopFlow environment:

```bash
docker compose up -d --build
```

And one command should show the running system:

```bash
docker compose ps
```

The project is then ready for Phase 6 integration testing with Testcontainers.
