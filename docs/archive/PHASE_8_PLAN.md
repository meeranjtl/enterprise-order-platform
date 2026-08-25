# Phase 8 - Event-Driven Architecture & Order Orchestration (Saga) - PLAN

**Estimated Duration:** 1.5 weeks  
**Goal:** Implement Kafka event-driven architecture with Order Orchestration Saga to coordinate payment, inventory, and order workflows.

---

## High-Level Overview

Phase 8 transforms the platform from synchronous microservice calls to **asynchronous event-driven architecture**. This phase:
1. Replaces Order → Inventory → Payment HTTP calls with Kafka events
2. Implements the **Saga Pattern** for distributed order orchestration
3. Ensures idempotent message processing and exactly-once semantics
4. Adds resilience via Outbox Pattern and Dead Letter Queues (DLQ)
5. Introduces Avro schema versioning for event contracts

### Architecture Flow

```
Order Service                Inventory Service              Payment Service
     │                            │                               │
     ├─> Publish: OrderCreated ──┐                               │
     │                            ├──> Consume: OrderCreated      │
     │                            ├─> Publish: InventoryReserved ┐
     │                            │                               ├──> Consume: InventoryReserved
     │                            │                               ├─> Process Payment
     │                            │                               ├─> Publish: PaymentProcessed
     │                            │                               │
     │  <─────────────────────────────────────────────────────────┤
     ├─────── Consume: PaymentProcessed ──────────────────────────┘
     │
     └─> Publish: OrderConfirmed
     │
     └─> [Proceed to Shipping/Fulfillment (Phase 9)]
```

---

## Phase 8 Deliverables

### 1. Kafka Infrastructure
- [ ] Kafka broker configuration in docker-compose.yml
- [ ] Kafka UI for topic monitoring
- [ ] Topic auto-creation and retention policies
- [ ] Schema Registry integration

### 2. Event Schema Definitions (Avro)
- [ ] OrderEvents.avsc (OrderCreated, OrderValidated, OrderConfirmed)
- [ ] InventoryEvents.avsc (InventoryReserved, InventoryReleased)
- [ ] PaymentEvents.avsc (PaymentProcessed, PaymentFailed)
- [ ] Schema Registry schema registration

### 3. Event Producer Implementation
- [ ] Order Service producer (OrderCreated, OrderValidated)
- [ ] Inventory Service producer (InventoryReserved, InventoryReleased)
- [ ] Payment Service producer (PaymentProcessed, PaymentFailed)
- [ ] Outbox Pattern implementation for exactly-once semantics

### 4. Event Consumer Implementation (Saga Orchestration)
- [ ] Inventory Service consumer (OrderCreated → trigger reservation)
- [ ] Payment Service consumer (InventoryReserved → trigger payment)
- [ ] Order Service saga orchestrator (aggregates events, manages workflow)

### 5. Resilience & Reliability
- [ ] Dead Letter Queue (DLQ) topics for each service
- [ ] DLQ consumer for alerting/logging
- [ ] Retry policy configuration
- [ ] Idempotency key handling for duplicate message deduplication

### 6. Testing
- [ ] Unit tests for event serialization/deserialization
- [ ] Integration tests with EmbeddedKafka
- [ ] Saga workflow tests (happy path + failure scenarios)
- [ ] DLQ handling tests

### 7. Documentation & Configuration
- [ ] Phase 8 Complete document
- [ ] Kafka topic and consumer group documentation
- [ ] AGENTS.md update for Phase 8 context
- [ ] Troubleshooting guide

---

## Implementation Breakdown

### Sprint 1: Infrastructure & Schema Definition

**Tasks:**
1. **Update docker-compose.yml**
   - Add Kafka broker service
   - Add Zookeeper service (Kafka dependency)
   - Add Schema Registry service
   - Add Kafka UI service for monitoring

2. **Define Avro Schemas**
   - Create `services/shared-library/src/main/avro/` directory
   - Define `OrderEvents.avsc`
   - Define `InventoryEvents.avsc`
   - Define `PaymentEvents.avsc`

3. **Maven Avro Plugin Configuration**
   - Add avro-maven-plugin to parent POM
   - Configure code generation from .avsc files
   - Add avro runtime dependency

### Sprint 2: Event Producer & Outbox Pattern

**Tasks:**
1. **Outbox Pattern in Shared Library**
   - Create `OutboxEvent` entity
   - Create `OutboxEventRepository`
   - Create `OutboxPublisher` scheduled task
   - Ensure atomicity: save entity + publish to outbox in same transaction

2. **Order Service Producer**
   - Add Kafka template configuration
   - Implement `OrderEventPublisher`
   - Modify `OrderService.createOrder()` to publish `OrderCreatedEvent`
   - Add event publishing to outbox

3. **Inventory Service Producer**
   - Implement `InventoryEventPublisher`
   - Publish events on reservation, release, adjustment

4. **Payment Service Producer**
   - Implement `PaymentEventPublisher`
   - Publish events on payment success/failure

### Sprint 3: Event Consumer & Saga Orchestration

**Tasks:**
1. **Saga Orchestrator Pattern**
   - Create `OrderSagaOrchestrator` in Order Service
   - Maintain saga state machine (PENDING → INVENTORY_RESERVED → PAYMENT_PROCESSED → CONFIRMED)
   - Handle compensating transactions (rollback on failure)

2. **Inventory Service Consumer**
   - Add `@KafkaListener` for `order-events` topic
   - Process `OrderCreatedEvent`
   - Call `InventoryService.reserve()` and publish `InventoryReservedEvent`
   - Handle failure: publish `InventoryReservationFailed` event

3. **Payment Service Consumer**
   - Add `@KafkaListener` for `inventory-events` topic
   - Process `InventoryReservedEvent`
   - Call payment processing and publish `PaymentProcessedEvent` or `PaymentFailedEvent`

4. **Order Service Saga Consumer**
   - Add listeners for `inventory-events` and `payment-events`
   - Aggregate state: when all events received, transition order to CONFIRMED
   - On failure: trigger compensating transactions (release inventory, refund payment)

### Sprint 4: Resilience, Testing & Documentation

**Tasks:**
1. **Dead Letter Queue Setup**
   - Create DLQ topics for each service
   - Implement DLQ consumer for logging/alerting
   - Configure retry policies and backoff

2. **Integration Tests**
   - Add `@EmbeddedKafka` for test Kafka cluster
   - Test happy path: Order → Inventory → Payment → Confirm
   - Test failure scenarios: payment failure, inventory insufficient
   - Test idempotent processing

3. **Documentation**
   - Create `PHASE_8_COMPLETE.md`
   - Update `AGENTS.md`
   - Document event contracts (topics, schemas, consumers)
   - Add troubleshooting guide

4. **Configuration Sync**
   - Update `application.yml` for all services (Kafka broker, schema registry)
   - Update `application-docker.yml` for Docker environment
   - Add Spring Kafka configuration

---

## Key Technical Decisions

### 1. Saga Pattern: Choreography vs Orchestration
- **Decision**: Orchestration via Order Service as central coordinator
- **Reason**: Clearer workflow visibility, easier to debug, explicit compensation logic

### 2. Event Schema Versioning
- **Decision**: Use Avro with Schema Registry
- **Reason**: Strong typing, backward compatibility, language agnostic

### 3. Exactly-Once Semantics
- **Decision**: Outbox Pattern + Idempotency Keys
- **Reason**: Prevents duplicate charges/reservations, complies with financial regulations

### 4. Error Handling
- **Decision**: DLQ with scheduled alerting, manual recovery process
- **Reason**: Prevents infinite retry loops, allows investigation and selective replay

---

## Definition of Done (DoD)

- ✅ All event schemas defined and registered in Schema Registry
- ✅ All services publish events via Outbox Pattern
- ✅ Saga orchestration flow working end-to-end (Order → Inventory → Payment → Confirm)
- ✅ DLQ topics created and consumers implemented
- ✅ Integration tests pass with embedded Kafka
- ✅ Idempotent message processing verified
- ✅ Docker Compose includes Kafka, Zookeeper, Schema Registry, Kafka UI
- ✅ PHASE_8_COMPLETE.md created with verification commands
- ✅ AGENTS.md updated with Phase 8 context

---

## Verification Commands

```powershell
# Build all services
mvn clean install

# Start Kafka infrastructure
docker compose up kafka zookeeper schema-registry kafka-ui postgres

# Run Phase 8 specific tests
mvn test -pl services/order-service -am -Dtest=*Saga*
mvn test -pl services/inventory-service -am -Dtest=*Event*
mvn test -pl services/payment-service -am -Dtest=*Event*

# Verify event topics in Kafka UI
# Navigate to http://localhost:8888 (Kafka UI)
```

---

## Success Criteria

1. **Architecture**: Order creation triggers Inventory → Payment asynchronously
2. **Resilience**: Failed events go to DLQ, can be replayed
3. **Testing**: 80%+ coverage on saga orchestrator
4. **Documentation**: Clear event contract documentation
5. **Operations**: Kafka UI shows all topics and message flow

---

## Timeline

| Sprint | Duration | Focus |
|--------|----------|-------|
| 1 | 2-3 days | Kafka infrastructure + schemas |
| 2 | 2-3 days | Event producers + Outbox pattern |
| 3 | 3-4 days | Event consumers + Saga orchestrator |
| 4 | 2-3 days | Tests, DLQ, documentation |
| **Total** | **~10 days** | **Fully event-driven order orchestration** |

