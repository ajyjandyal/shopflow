# ShopFlow

A cloud-native e-commerce and order processing platform, built progressively from a
well-structured monolith into event-driven microservices.

> **Current status: Phase 3 — Redis caching and rate limiting.** An API gateway in front of
> three Spring Boot services (users, products, orders), each with its own PostgreSQL database.
> Checkout uses idempotent stock reservations with retries and compensation instead of a
> single transaction. Product reads are cached in Redis with after-commit invalidation, and
> the gateway rate-limits every route with Redis token buckets (stricter on login/register).
> The Phase 1 monolith remains in `backend/` for reference.

## Tech stack

| Area | Choice |
|---|---|
| Language / runtime | Java 21 (LTS) |
| Services | Spring Boot 3.5 (Web, Data JPA, Security, Validation, Actuator) |
| Gateway | Spring Cloud Gateway 2025.0 (reactive), Redis-backed `RequestRateLimiter` |
| Caching | Redis 7+, Spring Cache (`@Cacheable`), JSON values, after-commit eviction |
| Service-to-service | Spring `RestClient` with timeouts, retries, idempotency keys |
| Database | PostgreSQL 16, one database per service, Flyway migrations |
| Auth | JWT (jjwt 0.12), BCrypt, RBAC (USER / SELLER / ADMIN) |
| API docs | springdoc-openapi (Swagger UI per service) |
| Tests | JUnit 5, Mockito, AssertJ |

## Architecture

```mermaid
flowchart LR
    C[Client] -->|:8080| GW[api-gateway]
    GW --> US[user-service :8081]
    GW --> PS[product-service :8082]
    GW --> OS[order-service :8083]
    GW <-->|rate-limit buckets| R[(Redis)]
    PS <-->|product cache| R
    OS -->|reserve / release stock<br/>internal API| PS
    US --> UDB[(shopflow_users)]
    PS --> PDB[(shopflow_products)]
    OS --> ODB[(shopflow_orders)]
```

### Checkout flow
```mermaid
sequenceDiagram
    participant C as Client
    participant GW as Gateway
    participant OS as order-service
    participant PS as product-service
    C->>GW: POST /api/v1/orders (JWT)
    GW->>OS: forward
    OS->>OS: load cart, generate reservationId
    OS->>PS: POST /internal/products/reservations (idempotent)
    PS->>PS: lock rows, check + decrement stock
    PS-->>OS: reserved lines (name, price snapshot)
    OS->>OS: save CONFIRMED order, clear cart (local tx)
    alt local save fails
        OS->>PS: release reservation (compensation)
    end
    OS-->>C: 201 Created
```

## Getting started

### Prerequisites
Java 21 JDK, Maven 3.9+, PostgreSQL 16 (Homebrew or Docker), Redis 7+ (Homebrew or Docker), Git, `openssl`.

### Setup (once)
```bash
cp .env.example .env
# Fill in DB_PASSWORD, ADMIN_PASSWORD, and:
openssl rand -base64 32      # -> JWT_SECRET
openssl rand -hex 32         # -> INTERNAL_API_KEY
./scripts/create-databases.sh        # Homebrew PostgreSQL
brew install redis && brew services start redis   # Homebrew Redis
# or: docker compose up -d           # Docker PostgreSQL + Redis (creates the databases)
```

### Run
```bash
./scripts/run-local.sh       # build + start all services
./scripts/smoke-test.sh      # end-to-end test through the gateway
./scripts/stop-local.sh
```
Swagger UIs: http://localhost:8081/swagger-ui.html (users), :8082 (products), :8083 (orders).

### Tests
```bash
cd services && mvn test
```

## API overview

All public endpoints are served through the gateway at `http://localhost:8080`.

| Method | Path | Access |
|---|---|---|
| POST | `/api/v1/auth/register` | Public |
| POST | `/api/v1/auth/login` | Public |
| GET | `/api/v1/users/me` | Authenticated |
| GET | `/api/v1/products` (search, filter, paginate, sort) | Public |
| GET | `/api/v1/products/{id}` | Public |
| POST | `/api/v1/products` | SELLER, ADMIN |
| PUT / DELETE | `/api/v1/products/{id}` | Owning SELLER, ADMIN |
| GET / DELETE | `/api/v1/cart` | Authenticated |
| POST | `/api/v1/cart/items` | Authenticated |
| PATCH / DELETE | `/api/v1/cart/items/{productId}` | Authenticated |
| POST | `/api/v1/orders` (checkout from cart) | Authenticated |
| GET | `/api/v1/orders`, `/api/v1/orders/{id}` | Owner (ADMIN sees all) |
| POST | `/api/v1/orders/{id}/cancel` | Owner, ADMIN |
| GET | `/api/v1/admin/orders` | ADMIN |
| PATCH | `/api/v1/admin/orders/{id}/status` | ADMIN |

Every route is rate limited at the gateway. Responses carry `X-RateLimit-Remaining`,
`X-RateLimit-Burst-Capacity`, `X-RateLimit-Replenish-Rate` and `X-RateLimit-Requested-Tokens`.
Exceeding the limit returns `429 Too Many Requests` in the standard error format.

All errors use one JSON shape:
```json
{ "timestamp": "...", "status": 409, "error": "Conflict",
  "message": "Insufficient stock for 'Keyboard': requested 3, available 1",
  "path": "/api/v1/orders" }
```

## Documentation
- [Phase 1 guide: concepts, walkthrough, testing, troubleshooting, interview questions](docs/PHASE-1-GUIDE.md)
- [Phase 2 guide: microservices, idempotency, sagas, experiments, interview questions](docs/PHASE-2-GUIDE.md)
- [ADR 0001: Start with a modular monolith](docs/adr/0001-modular-monolith-first.md)
- [Phase 3 guide: caching, invalidation, rate limiting, experiments, interview questions](docs/PHASE-3-GUIDE.md)
- [ADR 0002: Sync REST with idempotent reservations](docs/adr/0002-sync-rest-with-idempotent-reservations.md)
- [ADR 0003: Redis cache-aside and gateway rate limiting](docs/adr/0003-redis-cache-aside-and-gateway-rate-limiting.md)
- [Phase 4 guide: Kafka event-driven orders, transactional outbox, idempotency, cancellation, testing, interview questions](docs/PHASE-4-GUIDE.md)

## Roadmap
- [x] Phase 1 — Modular monolith REST API
- [x] Phase 2 — Microservices + API Gateway
- [x] Phase 3 — Redis caching and rate limiting
- [x] Phase 4 — Kafka event-driven order processing
- [x] Phase 5 — Docker and Docker Compose for all services
- [ ] Phase 6 — Integration testing with Testcontainers
- [ ] Phase 7 — GitHub Actions CI/CD
- [ ] Phase 8 — Kubernetes
- [ ] Phase 9 — Terraform
- [ ] Phase 10 — Prometheus, Grafana, load testing
- [ ] Phase 11 — Optional AWS deployment (free-tier only)
