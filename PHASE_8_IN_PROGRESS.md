# Phase 8 - Event-Driven Architecture & Order Orchestration - IN PROGRESS

**Estimated Duration:** 1.5 weeks  
**Goal:** Implement Kafka event-driven architecture with Order Orchestration Saga to coordinate payment, inventory, and order workflows.

---

## Sprint 1 Completion Summary ✅

### Deliverables Completed

#### 1. Kafka Infrastructure
- ✅ **docker-compose.yml updated** with:
  - Zookeeper service (event log broker dependency)
  - Kafka broker service (9092 internal, 9094 external)
  - Schema Registry service (8081 for Avro schema management)
  - Kafka UI service (8888 for monitoring and debugging)
  - All services health checks and network dependencies configured

#### 2. Avro Event Schemas Defined
- ✅ **OrderEvents.avsc** - OrderCreatedEvent with nested OrderItem structure
- ✅ **InventoryEvents.avsc** - InventoryReservedEvent with reservation status enum
- ✅ **PaymentEvents.avsc** - PaymentProcessedEvent with payment status enum
- All schemas include null-safe optional fields for failure reasons

#### 3. Maven Avro Plugin Configuration
- ✅ **Parent POM updated** with:
  - avro-maven-plugin (1.11.3) for code generation
  - Avro runtime dependency (1.11.3)
  - Confluent kafka-avro-serializer (7.5.0)
  - TestContainers Kafka (1.19.1) for integration testing
  - Confluent Maven repository configured

#### 4. Outbox Pattern Implementation (Exactly-Once Semantics)
- ✅ **OutboxEvent JPA Entity** - Stores events with published/timestamp tracking
- ✅ **OutboxEventRepository** - Queries unpublished events, finds latest by aggregate
- ✅ **OutboxPublisher Service** - Polls unpublished events, publishes to Kafka, marks as published
- ✅ **OutboxPoller Component** - Scheduled async task runs every 5 seconds
- ✅ **Flyway Migration V1** - Creates outbox_events table with indexes
- Ensures **no event loss** and **exactly-once delivery** (with idempotent consumers)

#### 5. Event DTO Classes
- ✅ **OrderCreatedEvent** - Contains orderId, orderNumber, customerId, items, totalAmount
- ✅ **InventoryReservedEvent** - Contains reservationId, orderId, quantity, status enum
- ✅ **PaymentProcessedEvent** - Contains paymentId, orderId, amount, status enum
- All DTOs have topic and event-type constants for routing

#### 6. Dependency Management
- ✅ **Shared Library enhanced** with:
  - Spring Data JPA (for Outbox entity persistence)
  - Spring Kafka (for KafkaTemplate)
  - Avro and Confluent serializer dependencies
  - TestContainers Kafka for integration tests

#### 7. Verification
- ✅ **Shared library builds successfully** with all new components
- ✅ **All Avro schemas compile** with code generation
- ✅ **Outbox pattern classes** integrate with Spring Data JPA
- ✅ **Confluent repository** correctly resolves kafka-avro-serializer

---

## Next Steps: Sprint 2 (Producer Implementation)

### Sprint 2 Focus: Event Producers + Outbox Pattern

**Tasks:**
1. **Kafka Configuration Classes** - Create common KafkaProducerConfig in all services
2. **Order Service Producer** - OrderEventPublisher, modify OrderService.createOrder() to emit OrderCreatedEvent
3. **Inventory Service Producer** - InventoryEventPublisher for reservation/release events
4. **Payment Service Producer** - PaymentEventPublisher for payment complete/failed events
5. **Application Configuration** - Add Kafka broker, schema registry URLs to application.yml for all services
6. **Docker Environment** - Create application-docker.yml variants with Docker hostnames

---

## Architecture: Event-Driven Order Orchestration

### Happy Path Flow
```
1. Client: POST /api/v1/orders
   ↓
2. Order Service:
   - Create Order entity (PENDING status)
   - Store OrderCreatedEvent in OutboxEvent table
   - Return orderId to client
   ↓
3. OutboxPoller (background, every 5s):
   - Finds unpublished OrderCreatedEvent
   - Publishes to Kafka topic "order-events"
   - Marks event as published (idempotent: won't re-publish)
   ↓
4. Inventory Service Consumer:
   - Receives OrderCreatedEvent from "order-events" topic
   - Calls inventoryService.reserve(productId, quantity)
   - On success: publishes InventoryReservedEvent to outbox
   - On failure: publishes InventoryReservationFailedEvent
   ↓
5. Payment Service Consumer:
   - Receives InventoryReservedEvent from "inventory-events" topic
   - Calls paymentService.process(orderId, amount, paymentMethod)
   - On success: publishes PaymentProcessedEvent to outbox
   - On failure: publishes PaymentFailedEvent with reason
   ↓
6. Order Service Saga Consumer:
   - Listens to both "inventory-events" and "payment-events" topics
   - Receives InventoryReservedEvent + PaymentProcessedEvent
   - Aggregates state: when both received, transitions order to CONFIRMED
   - Publishes OrderConfirmedEvent
```

### Failure Handling (Compensating Transactions)
```
If Payment Fails:
  ├─ Payment Service publishes PaymentFailedEvent
  ├─ Order Service saga listener receives failure
  ├─ Calls Inventory Service release(orderId)
  ├─ Publishes InventoryReleasedEvent
  └─ Order transitions to PAYMENT_REJECTED

If Inventory Fails:
  ├─ Inventory Service publishes InventoryReservationFailedEvent
  ├─ Order Service saga listener receives failure
  ├─ Doesn't proceed to payment
  └─ Order transitions to INVENTORY_INSUFFICIENT
```

---

## Kafka Topics & Consumer Groups

### Topics
- **order-events** (partition 1, rf 1):
  - OrderCreatedEvent
  - OrderConfirmedEvent

- **inventory-events** (partition 1, rf 1):
  - InventoryReservedEvent
  - InventoryReleasedEvent

- **payment-events** (partition 1, rf 1):
  - PaymentProcessedEvent
  - PaymentFailedEvent

- **order-events-dlq**: Dead Letter Queue for failed order processing
- **inventory-events-dlq**: Dead Letter Queue for failed inventory operations
- **payment-events-dlq**: Dead Letter Queue for failed payment processing

### Consumer Groups
- **order-service-group** - Order Service listens on inventory-events, payment-events
- **inventory-service-group** - Inventory Service listens on order-events
- **payment-service-group** - Payment Service listens on inventory-events

---

## Idempotency & Exactly-Once Semantics

### Outbox Pattern Guarantees
1. **Database Transaction**: Entity creation + OutboxEvent insertion in same transaction
2. **Scheduled Publishing**: OutboxPoller queries and publishes asynchronously
3. **Idempotent Marking**: If Kafka publish succeeds, event marked `published=true`
4. **Retry on Failure**: If publish fails, event remains unpublished and retried next poll
5. **Consumer Idempotency**: Each consumer caches `eventId` to ignore replays

### Idempotency Key Pattern (for REST clients)
- Order Service `/orders` endpoint accepts `Idempotency-Key` header
- Caches order creation result keyed by `Idempotency-Key`
- Repeat requests with same key return cached order (no duplicate order)
- Same pattern for inventory reserve, payment process endpoints

---

## Configuration Example (application.yml)

```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9092
    producer:
      value-serializer: io.confluent.kafka.serializers.KafkaAvroSerializer
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
    consumer:
      value-deserializer: io.confluent.kafka.serializers.KafkaAvroDeserializer
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      group-id: order-service-group
      auto-offset-reset: earliest
    properties:
      schema.registry.url: http://localhost:8081

outbox:
  poll:
    interval: 5000  # milliseconds
```

---

## Verification Commands

```powershell
# Start Kafka infrastructure
docker compose up -d zookeeper kafka schema-registry kafka-ui

# Create Kafka topics (manual or auto-create if enabled)
docker exec enterprise-kafka kafka-topics --create --bootstrap-server localhost:9092 --topic order-events --partitions 1 --replication-factor 1
docker exec enterprise-kafka kafka-topics --create --bootstrap-server localhost:9092 --topic inventory-events --partitions 1 --replication-factor 1
docker exec enterprise-kafka kafka-topics --create --bootstrap-server localhost:9092 --topic payment-events --partitions 1 --replication-factor 1

# View topics in Kafka UI
http://localhost:8888

# Test event flow (after producers implemented)
mvn test -pl services/order-service -am -Dtest=*Event*
mvn test -pl services/inventory-service -am -Dtest=*Event*
mvn test -pl services/payment-service -am -Dtest=*Event*
```

---

## Timeline

| Sprint | Duration | Completed | Focus |
|--------|----------|-----------|-------|
| 1 | 2-3 days | ✅ **DONE** | Kafka infrastructure + Avro schemas + Outbox pattern |
| 2 | 2-3 days | ⏳ **NEXT** | Event producers in Order/Inventory/Payment services |
| 3 | 3-4 days | ⏹️ **TODO** | Event consumers + Saga orchestrator |
| 4 | 2-3 days | ⏹️ **TODO** | Tests, DLQ, documentation |

---

## Success Metrics (Sprint 1)

- ✅ All services compile with Kafka dependencies
- ✅ Outbox events persisted in database
- ✅ Avro schemas registered with Schema Registry
- ✅ OutboxPoller publishes events correctly
- ✅ Events queryable in Kafka UI (localhost:8888)
- ✅ No duplicate events published (idempotent marking works)
- ✅ Failed publishes retried on next poll cycle

