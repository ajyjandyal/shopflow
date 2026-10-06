# Phase 4 --- Kafka Event-Driven Order Processing

## Overview

Phase 4 changes ShopFlow checkout from a synchronous stock-reservation
workflow into an event-driven workflow using Apache Kafka and the
Transactional Outbox Pattern.

### Phase 4 goals

-   Decouple Order Service from synchronous stock reservation.
-   Use Kafka for asynchronous order and inventory events.
-   Use the Transactional Outbox Pattern to avoid the database-to-Kafka
    dual-write problem.
-   Keep stock reservation idempotent.
-   Make cancellation safe even when cancellation happens before stock
    reservation.
-   Keep services independently deployable.
-   Preserve PostgreSQL as the source of truth for orders and inventory.

------------------------------------------------------------------------

## Architecture

### Before Phase 4

``` text
Client
  |
  v
API Gateway
  |
  v
Order Service
  |
  | synchronous HTTP
  v
Product Service
  |
  v
PostgreSQL
```

The problem was:

1.  Order Service asks Product Service to reserve stock.
2.  Product Service reserves stock successfully.
3.  Order Service attempts to save the order.
4.  If the order transaction fails, Order Service must compensate by
    releasing stock.
5.  If compensation also fails, inventory can remain reserved without a
    corresponding order.

This is a distributed transaction problem.

### After Phase 4

``` text
                         +----------------------+
                         |      PostgreSQL      |
                         |     Order Service    |
                         +----------+-----------+
                                    |
                           DB transaction
                                    |
                              +-----v------+
                              |   Outbox   |
                              +-----+------+
                                    |
                              Outbox Relay
                                    |
                                    v
                         +----------------------+
                         |        Kafka         |
                         | order.created.v1     |
                         +----------+-----------+
                                    |
                              Kafka Consumer
                                    |
                                    v
                         +----------------------+
                         |   Product Service    |
                         | InventoryService     |
                         +----------+-----------+
                                    |
                           DB transaction
                                    |
                              +-----v------+
                              |   Outbox   |
                              +-----+------+
                                    |
                              Outbox Relay
                                    |
                                    v
                         +----------------------+
                         |        Kafka         |
                         | stock.reserved.v1    |
                         | stock.reservation.   |
                         | failed.v1            |
                         +----------+-----------+
                                    |
                              Kafka Consumer
                                    |
                                    v
                         +----------------------+
                         |    Order Service     |
                         | PENDING -> CONFIRMED |
                         | PENDING -> CANCELLED |
                         +----------------------+
```

Cancellation uses:

``` text
Order Service
     |
     | order.cancelled.v1
     v
   Kafka
     |
     v
Product Service
     |
     v
InventoryService.release()
```

------------------------------------------------------------------------

# Kafka Topics

Phase 4 uses four Kafka topics:

  -------------------------------------------------------------------------------------
  Topic                           Producer          Consumer          Purpose
  ------------------------------- ----------------- ----------------- -----------------
  `order.created.v1`              Order Service     Product Service   Request inventory
                                                                      reservation

  `stock.reserved.v1`             Product Service   Order Service     Confirm
                                                                      successful
                                                                      reservation

  `stock.reservation.failed.v1`   Product Service   Order Service     Cancel order
                                                                      after reservation
                                                                      failure

  `order.cancelled.v1`            Order Service     Product Service   Release inventory
  -------------------------------------------------------------------------------------

Kafka message keys use the order/reservation identity where appropriate
so related events can be partitioned consistently.

Kafka ordering is guaranteed within a partition, not globally across all
topics.

------------------------------------------------------------------------

# Event Contracts

Shared event records live in:

``` text
services/common/src/main/java/com/shopflow/common/event/
```

### OrderCreatedEvent

Represents a new order waiting for inventory reservation.

``` text
eventId
schemaVersion
occurredAt
orderId
userId
reservationId
items
```

Each item contains:

``` text
productId
quantity
```

### StockReservedEvent

Represents successful inventory reservation.

``` text
eventId
schemaVersion
occurredAt
orderId
reservationId
```

### StockReservationFailedEvent

Represents a business-level inventory reservation failure.

``` text
eventId
schemaVersion
occurredAt
orderId
reservationId
reason
```

### OrderCancelledEvent

Represents an order cancellation that Product Service must react to.

``` text
eventId
schemaVersion
occurredAt
orderId
userId
reservationId
reason
```

The `schemaVersion` field gives the event contract an explicit version
that can be evolved later.

------------------------------------------------------------------------

# Transactional Outbox Pattern

## Why an outbox is needed

This code is unsafe:

``` text
BEGIN DATABASE TRANSACTION
    save order
COMMIT

send Kafka message
```

If the application crashes between the database commit and Kafka send,
the order exists but Kafka never receives the event.

The opposite order is also unsafe:

``` text
send Kafka message
BEGIN DATABASE TRANSACTION
    save order
ROLLBACK
```

Kafka contains an event for an order that does not exist.

## Solution

Write the business data and the Kafka event into the same database
transaction:

``` text
BEGIN TRANSACTION

    save order

    insert outbox event

COMMIT
```

A separate publisher later reads the outbox and sends the event to
Kafka.

``` text
PostgreSQL
    |
    | unpublished rows
    v
OutboxPublisher
    |
    v
Kafka
```

This closes the database-to-message-broker dual-write gap.

------------------------------------------------------------------------

# Outbox Database Schema

Both Order Service and Product Service have their own `outbox_events`
table.

``` sql
CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    event_key VARCHAR(200) NOT NULL,
    topic VARCHAR(200) NOT NULL,
    aggregate_id VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at TIMESTAMPTZ NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    last_error VARCHAR(2000) NULL,
    CONSTRAINT uk_outbox_event_key UNIQUE (event_key)
);

CREATE INDEX idx_outbox_unpublished
    ON outbox_events (published_at, created_at);
```

Migrations:

``` text
services/order-service/src/main/resources/db/migration/V2__order_outbox.sql
services/product-service/src/main/resources/db/migration/V2__product_outbox.sql
```

Each service uses version `V2` because each service has its own
PostgreSQL database and Flyway history.

------------------------------------------------------------------------

# Outbox Event Keys

The unique event key provides database-level idempotency.

Examples:

``` text
order-created:<orderId>
order-cancelled:<orderId>
stock-reserved:<orderId>
stock-reservation-failed:<orderId>
```

If application code accidentally attempts to create the same logical
event again, the unique constraint prevents a duplicate outbox row.

------------------------------------------------------------------------

# Order Service Flow

## Creating an order

The new checkout flow is:

``` text
1. Load cart
2. Validate cart is not empty
3. Build product quantities
4. Read product snapshots
5. Validate products
6. Calculate order total
7. Generate reservation ID
8. BEGIN DB transaction
9. Create PENDING order
10. Save order items
11. Clear cart
12. Insert OrderCreatedEvent into outbox
13. COMMIT
14. Return PENDING order
```

The important difference is that Order Service does not synchronously
reserve stock.

The API can therefore return a `PENDING` order while inventory
processing happens asynchronously.

------------------------------------------------------------------------

# Order State Machine

``` text
             stock.reserved.v1
PENDING --------------------------> CONFIRMED
   |
   | stock.reservation.failed.v1
   v
CANCELLED
```

A user can also explicitly cancel a pending or confirmed order:

``` text
PENDING ---------> CANCELLED
CONFIRMED --------> CANCELLED
```

Other existing states remain:

``` text
CONFIRMED -> SHIPPED -> DELIVERED
```

Terminal states:

``` text
DELIVERED
CANCELLED
```

A duplicate `stock.reserved.v1` event must never resurrect a cancelled
order.

------------------------------------------------------------------------

# Order Cancellation

Cancellation is event-driven.

When an order is cancelled:

``` text
Order Service
    |
    | local DB transaction
    |  - change status to CANCELLED
    |  - insert OrderCancelledEvent
    v
PostgreSQL
    |
    v
OutboxPublisher
    |
    v
Kafka: order.cancelled.v1
    |
    v
Product Service
    |
    v
InventoryService.release(reservationId)
```

There is no synchronous Product Service release call from the Order
Service cancellation path.

------------------------------------------------------------------------

# Important Cancellation Race

Consider this sequence:

``` text
T1: Order created
T2: Order cancellation requested
T3: order.cancelled.v1 reaches Product Service
T4: order.created.v1 reaches Product Service
```

If cancellation arrives first, Product Service must not later reserve
stock for the cancelled order.

The existing reservation model solves this using a `RELEASED` tombstone.

``` text
release(reservationId)
       |
       v
reservation does not exist
       |
       v
create RELEASED reservation
```

Later:

``` text
reserve(reservationId)
       |
       v
reservation exists as RELEASED
       |
       v
reservation rejected
```

This makes cancellation safe even when Kafka events arrive in an
unexpected cross-topic order.

------------------------------------------------------------------------

# Product Service Flow

Product Service consumes:

``` text
order.created.v1
```

The consumer converts the event into the existing inventory reservation
request.

``` text
OrderCreatedEvent
       |
       v
InventoryEventProcessor
       |
       v
InventoryService.reserve()
```

InventoryService remains responsible for:

-   locking products,
-   checking active products,
-   checking available stock,
-   decrementing stock,
-   creating reservation lines,
-   marking the reservation `RESERVED`,
-   publishing product cache invalidation events.

If reservation succeeds:

``` text
stock.reserved.v1
```

is written to the Product Service outbox.

If a business rule prevents reservation:

``` text
stock.reservation.failed.v1
```

is written instead.

------------------------------------------------------------------------

# Business Failure vs Technical Failure

This distinction is important.

## Business failure

Examples:

``` text
Insufficient stock
Inactive product
Reservation already released
```

These are expected business outcomes.

The consumer can create:

``` text
stock.reservation.failed.v1
```

The order then becomes:

``` text
CANCELLED
```

## Technical failure

Examples:

``` text
Database unavailable
Unexpected exception
Serialization failure
Temporary infrastructure failure
```

These should normally be allowed to propagate so Kafka/Spring Kafka can
retry the message.

Do not convert every exception into a business failure.

------------------------------------------------------------------------

# Idempotency

Kafka provides at-least-once delivery in the normal consumer model.

Therefore a message may be delivered more than once.

The system must tolerate duplicates.

## Inventory idempotency

`reservationId` is generated by Order Service and reused for the same
order.

InventoryService already handles:

``` text
PENDING
RESERVED
RELEASED
```

reservation states.

A repeated reservation request for an already `RESERVED` reservation
returns the existing result rather than decrementing stock again.

## Outbox idempotency

The database has:

``` text
UNIQUE(event_key)
```

which prevents duplicate logical outbox events.

## Order event idempotency

When an order receives:

``` text
stock.reserved.v1
```

the processor checks the current order state.

For example:

``` text
PENDING -> CONFIRMED
```

is valid.

If the order is already:

``` text
CONFIRMED
```

the duplicate event is ignored.

If the order is:

``` text
CANCELLED
```

the reservation event must not resurrect it.

------------------------------------------------------------------------

# Outbox Publisher

The publisher periodically polls unpublished rows.

Conceptually:

``` text
every 1 second:

    find unpublished events

    for each event:
        publish to Kafka
        if successful:
            mark published
        if failed:
            increment attempts
            save last_error
```

The publisher uses the aggregate ID as the Kafka message key.

This helps keep related messages consistently partitioned.

------------------------------------------------------------------------

# Crash Scenario

The outbox publisher can crash here:

``` text
1. Send Kafka message
2. Application crashes
3. published_at was not updated
```

After restart the same outbox event can be published again.

This is intentional.

The system relies on idempotent consumers.

This gives an important interview point:

> The outbox guarantees reliable handoff from the database to the
> message broker, but it does not magically provide exactly-once
> end-to-end business processing.

------------------------------------------------------------------------

# Kafka Producer Configuration

The services use Spring Boot Kafka configuration with:

``` yaml
spring:
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}

    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer
      acks: all
      properties:
        enable.idempotence: true

    consumer:
      auto-offset-reset: earliest
      enable-auto-commit: false
      isolation-level: read_committed
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.apache.kafka.common.serialization.StringDeserializer
```

Consumer groups are independent:

``` text
order-service
inventory-service
```

The services acknowledge Kafka messages only after successful
processing.

------------------------------------------------------------------------

# Scheduling

Both services enable Spring scheduling:

``` java
@EnableScheduling
```

The outbox publisher uses a scheduled poll.

Example configuration:

``` yaml
app:
  outbox:
    poll-interval-ms: 1000
```

The value can be changed through configuration without modifying code.

------------------------------------------------------------------------

# Local Infrastructure

Phase 4 local development uses:

``` text
PostgreSQL
Redis
Kafka
```

Homebrew services:

``` bash
brew services list | grep -E 'postgresql|redis|kafka'
```

Kafka should be available at:

``` text
localhost:9092
```

Verify topics:

``` bash
/opt/homebrew/opt/kafka/bin/kafka-topics \
  --bootstrap-server localhost:9092 \
  --list
```

Expected topics:

``` text
order.cancelled.v1
order.created.v1
stock.reservation.failed.v1
stock.reserved.v1
```

------------------------------------------------------------------------

# Running ShopFlow

From the repository root:

``` bash
cd ~/projects/shopflow
```

Start local infrastructure if required:

``` bash
brew services start postgresql@16
brew services start redis
brew services start kafka
```

Then start ShopFlow:

``` bash
./scripts/run-local.sh
```

If port `8080` is already in use:

``` bash
lsof -ti tcp:8080 | xargs kill
```

Then run:

``` bash
./scripts/run-local.sh
```

------------------------------------------------------------------------

# Health Checks

Check:

``` bash
curl -s http://localhost:8081/actuator/health
curl -s http://localhost:8082/actuator/health
curl -s http://localhost:8083/actuator/health
curl -s http://localhost:8080/actuator/health
```

Expected response:

``` json
{"status":"UP"}
```

------------------------------------------------------------------------

# Build and Test

From the services directory:

``` bash
cd ~/projects/shopflow/services
mvn clean test
```

A successful Phase 4 build should finish with:

``` text
BUILD SUCCESS
```

Run the application-level smoke test from the repository root:

``` bash
cd ~/projects/shopflow
./scripts/smoke-test.sh
```

The important Phase 4 checkout behavior is that an order is initially:

``` text
PENDING
```

and later becomes:

``` text
CONFIRMED
```

after the Kafka inventory workflow completes.

------------------------------------------------------------------------

# Useful Kafka Debugging Commands

List topics:

``` bash
/opt/homebrew/opt/kafka/bin/kafka-topics \
  --bootstrap-server localhost:9092 \
  --list
```

Describe a topic:

``` bash
/opt/homebrew/opt/kafka/bin/kafka-topics \
  --bootstrap-server localhost:9092 \
  --describe \
  --topic order.created.v1
```

Consume order-created events:

``` bash
/opt/homebrew/opt/kafka/bin/kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic order.created.v1 \
  --from-beginning
```

Consume reservation events:

``` bash
/opt/homebrew/opt/kafka/bin/kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic stock.reserved.v1 \
  --from-beginning
```

Consume failed reservation events:

``` bash
/opt/homebrew/opt/kafka/bin/kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic stock.reservation.failed.v1 \
  --from-beginning
```

Consume cancellation events:

``` bash
/opt/homebrew/opt/kafka/bin/kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic order.cancelled.v1 \
  --from-beginning
```

------------------------------------------------------------------------

# Key Phase 4 Source Files

### Shared events

``` text
services/common/src/main/java/com/shopflow/common/event/
├── OrderCancelledEvent.java
├── OrderCreatedEvent.java
├── OrderCreatedItem.java
├── StockReservationFailedEvent.java
└── StockReservedEvent.java
```

### Order Service

``` text
services/order-service/src/main/java/com/shopflow/
├── config/
│   └── SchedulingConfig.java
├── order/
│   ├── OrderService.java
│   ├── OrderEventProcessor.java
│   └── OrderKafkaListener.java
└── outbox/
    ├── OutboxEvent.java
    ├── OutboxPublisher.java
    └── OutboxRepository.java
```

### Product Service

``` text
services/product-service/src/main/java/com/shopflow/
├── config/
│   └── SchedulingConfig.java
├── inventory/
│   ├── InventoryEventProcessor.java
│   ├── InventoryKafkaListener.java
│   └── InventoryService.java
└── outbox/
    ├── OutboxEvent.java
    ├── OutboxPublisher.java
    └── OutboxRepository.java
```

### Database migrations

``` text
services/order-service/src/main/resources/db/migration/
└── V2__order_outbox.sql

services/product-service/src/main/resources/db/migration/
└── V2__product_outbox.sql
```

------------------------------------------------------------------------

# Why Kafka Transactions Are Not Used Here

The architecture deliberately does not make Kafka transactions the
primary consistency mechanism.

The critical transaction is:

``` text
business database changes
+
outbox event
```

These are committed atomically in PostgreSQL.

Kafka publishing happens afterward.

This is simpler and directly solves the actual dual-write problem
between PostgreSQL and Kafka.

Kafka producer idempotence is enabled, but a Kafka transaction is not
required for the outbox pattern.

------------------------------------------------------------------------

# Failure Scenarios

## Order database transaction fails

``` text
Order
+
Outbox event
```

both roll back.

No Kafka event is published.

Safe.

## Kafka is temporarily unavailable

The order and outbox event remain in PostgreSQL.

The publisher retries later.

Safe.

## Product Service crashes after reservation

The reservation is committed in Product Service.

The `stock.reserved.v1` event is in the Product Service outbox.

After restart the publisher sends it.

Safe.

## Order Service crashes after Kafka receives stock.reserved

The order event can be delivered again.

The order processor checks the current state.

Safe.

## Cancellation occurs before reservation

Product Service creates a `RELEASED` reservation tombstone.

Later reservation attempts are rejected.

Safe.

## Outbox publisher crashes after Kafka send

The event may be sent again.

Consumers are idempotent.

Safe.

------------------------------------------------------------------------

# Phase 4 Design Decisions

### Decision 1 --- Transactional Outbox

Chosen because PostgreSQL and Kafka cannot participate in one simple
local transaction.

### Decision 2 --- At-least-once delivery

Chosen because reliability is more important than pretending the system
provides exactly-once end-to-end semantics.

### Decision 3 --- Idempotent consumers

Required because duplicate Kafka messages are possible.

### Decision 4 --- PENDING orders

Orders are created before inventory confirmation.

This makes the distributed workflow explicit.

### Decision 5 --- Cancellation events

Cancellation must be represented as an event because Product Service
owns inventory.

### Decision 6 --- RELEASED tombstones

A cancellation can arrive before an order-created event. A tombstone
prevents a later reservation from consuming stock for a cancelled order.

------------------------------------------------------------------------

# Interview Questions

## 1. Why did you introduce Kafka?

To decouple order creation from inventory reservation and make the
workflow asynchronous and resilient to temporary service failures.

## 2. What problem does the Transactional Outbox solve?

It prevents the database/Kafka dual-write problem where the database
transaction succeeds but publishing the corresponding Kafka event fails,
or vice versa.

## 3. Why not simply save the order and immediately call Kafka?

Because the application could crash between those two operations.

## 4. Does the outbox provide exactly-once delivery?

No. A publisher can send an event and crash before marking it published.
The event can therefore be sent again. Consumers must be idempotent.

## 5. How do you make inventory reservation idempotent?

The reservation uses a caller-generated UUID. The same reservation ID
represents the same logical reservation, and InventoryService recognizes
existing reservation states.

## 6. What happens if stock is insufficient?

Product Service treats it as a business failure and emits
`stock.reservation.failed.v1`. Order Service changes the order from
`PENDING` to `CANCELLED`.

## 7. Why doesn't Order Service synchronously release stock during cancellation?

Product Service owns inventory. Order Service publishes
`order.cancelled.v1`, allowing the inventory owner to perform the
release.

## 8. What happens if cancellation arrives before order creation reaches Product Service?

Product Service creates a `RELEASED` tombstone for the reservation ID. A
later reservation attempt sees the released state and refuses to reserve
stock.

## 9. Why is the order initially PENDING?

Because inventory confirmation is asynchronous.

## 10. What happens if Kafka is down?

Orders can still be committed with their outbox events. The outbox
publisher retries publishing when Kafka becomes available.

## 11. Why use separate Kafka consumer groups?

Each service needs to receive the events independently. Order Service
and Product Service have different responsibilities.

## 12. Why use a Kafka message key?

It allows related events to be routed consistently to the same
partition, helping preserve ordering for events sharing that key.

## 13. Does Kafka guarantee global ordering?

No. Kafka guarantees ordering within a partition.

## 14. Why keep ProductClient?

ProductClient is still useful for reading product snapshots needed to
create the immutable order-item snapshot. It is no longer used for
synchronous stock reservation or cancellation release.

## 15. What is the Saga pattern here?

The order/inventory workflow is a distributed business transaction
coordinated through events. Each service performs its own local
transaction and communicates the next step through events.

------------------------------------------------------------------------

# Phase 4 Completion Checklist

-   [x] Kafka installed locally
-   [x] Kafka broker running on `localhost:9092`
-   [x] `order.created.v1`
-   [x] `stock.reserved.v1`
-   [x] `stock.reservation.failed.v1`
-   [x] `order.cancelled.v1`
-   [x] Shared Kafka event contracts
-   [x] Order Service outbox
-   [x] Product Service outbox
-   [x] Outbox publishers
-   [x] Order Kafka listener
-   [x] Inventory Kafka listener
-   [x] Event-driven reservation
-   [x] Event-driven cancellation
-   [x] Idempotent inventory reservation
-   [x] RELEASED cancellation tombstone
-   [x] PENDING -\> CONFIRMED workflow
-   [x] PENDING -\> CANCELLED failure workflow
-   [x] Scheduling configuration
-   [x] Flyway outbox migrations
-   [x] Kafka configuration
-   [x] Automated tests passing
-   [x] Local smoke test passing

------------------------------------------------------------------------

# Git Commit

After verifying the build and smoke test:

``` bash
cd ~/projects/shopflow

git status

git add services docs

git commit -m "Phase 4: Kafka event-driven order processing"

git push origin main
```

Verify:

``` bash
git log -1 --oneline
git status
```

The working tree should be clean after the push.

------------------------------------------------------------------------

# What Phase 4 Adds to the Portfolio

Phase 4 demonstrates practical knowledge of:

-   Apache Kafka
-   Event-driven architecture
-   Distributed systems
-   Transactional Outbox Pattern
-   Saga-style workflows
-   Eventual consistency
-   Idempotent consumers
-   At-least-once delivery
-   Kafka consumer groups
-   Kafka partitions and keys
-   PostgreSQL transactions
-   Flyway migrations
-   Spring Kafka
-   Failure recovery
-   Distributed cancellation handling

This is a significant architectural step beyond a basic CRUD
microservice project.
