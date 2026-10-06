# Phase 6 — Integration Testing with Testcontainers

## Goal

Phase 6 adds real infrastructure integration tests to ShopFlow.

The tests verify:

- PostgreSQL persistence for `user-service`
- PostgreSQL persistence for `product-service`
- Redis connectivity and read/write behavior
- Kafka publish/consume behavior
- Maven Failsafe execution of `*IT.java` integration tests

The containers are created dynamically by Testcontainers, so the tests do not require the
developer's local PostgreSQL, Redis, or Kafka processes to be running.

## Why this matters

Unit tests prove application logic in isolation. Integration tests prove that application
code works against real infrastructure.

ShopFlow uses PostgreSQL, Redis, and Kafka in production. Testcontainers gives the test
suite disposable Docker containers containing those same infrastructure technologies.

This catches problems such as:

- incorrect SQL/schema assumptions
- PostgreSQL-specific behavior
- Redis connection/configuration problems
- Kafka producer/consumer configuration errors
- integration failures hidden by mocks

## Test layout

```text
services/
├── user-service/
│   └── src/test/java/com/shopflow/user/
│       └── UserRepositoryIT.java
├── product-service/
│   └── src/test/java/
│       ├── com/shopflow/product/
│       │   └── ProductRepositoryIT.java
│       └── com/shopflow/integration/
│           └── RedisIT.java
└── order-service/
    └── src/test/java/com/shopflow/integration/
        └── KafkaIT.java
```

## Testcontainers

The parent Maven POM manages Testcontainers version `2.0.5` through its BOM.

The service modules use:

- `testcontainers`
- `testcontainers-junit-jupiter`
- `testcontainers-postgresql`
- `testcontainers-kafka`

Redis uses Testcontainers' `GenericContainer` because the test only needs a disposable
Redis container.

## PostgreSQL tests

`UserRepositoryIT` and `ProductRepositoryIT` use `@DataJpaTest` and a real PostgreSQL
16 container.

`@DynamicPropertySource` injects the container's JDBC URL, username, and password into
Spring Boot. The mapped container port is therefore never hard-coded.

## Redis test

`RedisIT` starts `redis:7-alpine`, obtains its dynamically mapped port, and uses the
Lettuce client already present in the product service to write and read a value.

## Kafka test

`KafkaIT` starts `apache/kafka:4.3.1`, creates a unique topic name, publishes a JSON event,
and polls a real Kafka consumer until the event arrives or the timeout expires.

## Maven Failsafe

Unit tests use Maven Surefire during `test`.

Integration tests use Maven Failsafe and are named `*IT.java`.

Run:

```bash
cd services
mvn verify
```

`verify` runs the normal build, unit tests, integration tests, and Failsafe verification.

## Prerequisites

- Java 21
- Maven 3.9+
- Docker Desktop running

No local PostgreSQL, Redis, or Kafka process is required for the Phase 6 tests.

## Troubleshooting

### Docker is not running

Start Docker Desktop and run:

```bash
docker info
```

Then:

```bash
cd services
mvn verify
```

### Testcontainers downloads images

The first run may take longer because Docker must download the PostgreSQL, Redis, and Kafka
test images. Later runs can reuse locally cached images.

### Integration tests are not running

Confirm:

```bash
find services -name "*IT.java" -print
```

Then:

```bash
cd services
mvn verify
```

## Interview questions

### Why Testcontainers instead of mocks?

Mocks verify interactions with fake dependencies. Testcontainers verifies behavior against
real infrastructure such as PostgreSQL, Redis, and Kafka.

### Why use dynamic ports?

Containers expose dynamically mapped host ports, avoiding hard-coded host-port collisions.

### Why separate Surefire and Failsafe?

Surefire is normally used for fast unit tests during the `test` phase. Failsafe is designed
for integration tests and runs them during `integration-test`, followed by verification in
the `verify` phase.

### What problems can integration testing catch here?

A repository can pass unit tests while failing against PostgreSQL because of schema, SQL,
migration, transaction, or dialect differences. Kafka and Redis have similar
integration-specific failure modes.

### Why not use the local Docker Compose Kafka?

Integration tests should be self-contained and isolated. Each test suite controls its own
container lifecycle, making tests repeatable on developer machines and CI.
