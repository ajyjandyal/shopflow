# ADR 0001: Start with a modular monolith

**Status:** Accepted
**Date:** Phase 1

## Context
The target architecture is microservices with event-driven communication. However, the
domain (users, products, carts, orders) and its rules are not yet implemented or
validated. Building distributed services first would mean debugging network calls,
service discovery and data consistency before the core business logic even works.

## Decision
Build Phase 1 as a single Spring Boot application, organised **by feature** (`user`,
`product`, `cart`, `order`) instead of by layer (`controllers`, `services`, ...).
Modules reference each other's data by **id only**, never through JPA relationships
across module boundaries (e.g. `Product.sellerId`, `CartItem.productId`).

## Consequences
- Positive: one process and one database to run and debug; ACID transactions make
  checkout simple and correct; module boundaries are already visible for Phase 2.
- Negative: modules share one database, so it is still possible to write cross-module
  queries. We avoid them by convention, not by enforcement.
- Phase 2 impact: checkout currently relies on a single local transaction across cart,
  product stock and orders. Once these live in separate services that transaction
  disappears, which is the core problem Phase 4 (Kafka, eventual consistency) solves.
