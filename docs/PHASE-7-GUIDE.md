# Phase 7 — GitHub Actions CI/CD

## Overview

ShopFlow now uses GitHub Actions for continuous integration.

Every push or pull request targeting `main` or `develop` runs the automated pipeline.

## CI Architecture

```text
Developer
   |
   v
Git Push / Pull Request
   |
   v
GitHub Actions
   |
   +------------------------+
   |                        |
   v                        v
Build & Test          Docker Validation
   |                        |
   +-- Java 21              +-- Compose validation
   +-- Maven                +-- User Service image
   +-- Unit tests           +-- Product Service image
   +-- Testcontainers       +-- Order Service image
   +-- PostgreSQL           +-- API Gateway image
   +-- Redis
   +-- Kafka
   |
   v
Test Reports
```

## Pipeline

The workflow is stored at:

```text
.github/workflows/ci.yml
```

### Build and Test

The CI pipeline executes:

```bash
cd services
mvn clean verify
```

This runs:

- Maven compilation
- Unit tests
- Maven Failsafe integration tests
- PostgreSQL Testcontainers
- Redis Testcontainers
- Kafka Testcontainers

### Docker Validation

The second job validates:

```bash
docker compose config
```

and builds Docker images for:

- User Service
- Product Service
- Order Service
- API Gateway

The images are validated on the GitHub Actions runner and are not pushed to a registry in this phase.

### Test Reports

CI uploads:

```text
target/surefire-reports/
target/failsafe-reports/
```

as GitHub Actions artifacts so failed builds can be investigated.

## GitHub Actions Features Used

- `actions/checkout@v4`
- `actions/setup-java@v4`
- Temurin Java 21
- Maven dependency caching
- `actions/upload-artifact@v4`
- Automated unit testing
- Automated integration testing
- Docker Compose validation
- Docker image builds

## Why This Matters

Phase 7 demonstrates:

- Continuous Integration
- GitHub Actions
- Automated testing
- Testcontainers
- Maven Surefire
- Maven Failsafe
- Docker validation
- Docker image builds
- Pull-request checks
- Maven dependency caching

## Local Equivalent

Run the same test pipeline locally:

```bash
cd ~/projects/shopflow/services
mvn clean verify
```

## Expected Result

A successful CI run should show:

```text
Build and Test       PASSED
Docker Validation    PASSED
```

The integration tests should include:

```text
UserRepositoryIT
ProductRepositoryIT
RedisIT
KafkaIT
```

## Phase 7 Success Criteria

- [x] GitHub Actions workflow
- [x] Java 21 CI environment
- [x] Maven dependency caching
- [x] Unit tests
- [x] Testcontainers integration tests
- [x] Test report artifacts
- [x] Docker Compose validation
- [x] User Service Docker build
- [x] Product Service Docker build
- [x] Order Service Docker build
- [x] API Gateway Docker build

## Important

Phase 7 should only be marked complete in the main README after the GitHub Actions workflow has successfully passed on GitHub.
