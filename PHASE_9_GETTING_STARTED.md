# Phase 9 - Getting Started Guide (Shipping & Notification Services)

Phase 9 completes the order fulfillment flow: after payment is approved, the
**Shipping Service** creates a shipment (via an async request/reply packing-list
exchange with Inventory) and the **Notification Service** emails/SMSes the
customer at every milestone. The Order saga is extended to
`PAYMENT_APPROVED → SHIPPED → COMPLETED`.

---

## Prerequisites

- Phases 1-8 complete (see `PHASE_8_COMPLETE.md`)
- Java 21, Maven 3.8+, Docker & Docker Compose
- Infrastructure stack runnable: `docker compose up -d postgres zookeeper kafka schema-registry`

---

## What You Will Build

### 1. Shipping Service (:8086, schema `shipping`)
- `Shipment` entity: orderId (unique), trackingNumber, embedded shipping address,
  status (`PENDING → SHIPPED → DELIVERED`), packing list, shippedAt/deliveredAt
- Consumes `payment-events` (payment COMPLETED → creates shipment, idempotent per order)
- **Async request/reply**: publishes `PackingListRequestedEvent` to
  `inventory-shipping-request-events`; Inventory replies with `PackingListProvidedEvent`
  on `inventory-shipping-reply-events`; on reply the shipment gets its tracking
  number and becomes `SHIPPED` (`ShipmentCreatedEvent` on `shipping-events`)
- Delivery simulation endpoint publishes `ShipmentDeliveredEvent`
- REST: `POST /api/v1/shipments`, `GET /api/v1/shipments/{id}`,
  `GET /api/v1/shipments?orderId=`, `POST /api/v1/shipments/{id}/deliver`

### 2. Notification Service (:8087, schema `notification`)
- Consumes `order-events`, `payment-events`, `shipping-events` (group `notification-service-group`)
- Event-to-notification mapping:
  | Event | Type | Channels |
  |---|---|---|
  | OrderCreated | ORDER_CONFIRMED | EMAIL |
  | PaymentProcessed (COMPLETED) | PAYMENT_RECEIVED | EMAIL |
  | ShipmentCreated | SHIPPED | EMAIL + SMS |
  | ShipmentDelivered | DELIVERED | EMAIL + SMS |
- Email/SMS simulation (log + DB audit row, unique per order+type+channel → idempotent)
- Publishes `NotificationSentEvent` to `notification-events` via outbox
- REST: `GET /api/v1/notifications/{id}`, `GET /api/v1/notifications?orderId=`

### 3. Saga & Infrastructure Extensions
- `OrderSagaOrchestrator`: new `shipping-events` listener →
  `PAYMENT_APPROVED → SHIPPED` and `SHIPPED → COMPLETED`
- Inventory: packing-list request consumer + reply publisher (outbox, idempotent)
- shared-library: 5 new event DTOs, 8 new topics (4 primary + 4 DLQ),
  DLQ listeners, and a real `DeadLetterPublishingRecoverer` so failed events
  actually land in `<topic>-dlq` after retries
- Gateway routes for `/api/v1/shipments/**` and `/api/v1/notifications/**`
- Docker Compose entries for both services

### Design Decisions
- **No new inter-service HTTP calls** (AGENTS.md anti-pattern): contact details
  and event-driven shipping addresses are simulated
  (`customer-{id}@example.com`, placeholder address), keeping services purely
  event-driven. Manual `POST /api/v1/shipments` accepts an explicit address.
- `shipping-events` carries two event types; consumers dispatch on the new
  `eventType` Kafka header added by `OutboxPublisher`.
- Kafka message key for all shipping/notification flow events is the orderId,
  preserving per-order partition ordering.

---

## Steps

1. Extend `shared-library` (events, KafkaConfig topics + DLQ recoverer,
   OutboxPublisher eventType header, DeadLetterQueueHandler)
2. Implement `shipping-service` (pom, app class, JpaConfig, Flyway V1,
   entity/repository/service/controller, consumers, application.yml, Dockerfile)
3. Implement `notification-service` (same shape)
4. Extend `order-service` saga orchestrator + `inventory-service` packing-list reply
5. Gateway routes (`application.yml` + `application-docker.yml`) + FallbackController
6. `docker-compose.yml` entries for both services
7. Tests: event serialization (shared), shipping/notification unit tests
8. `mvn clean install` → full build green
9. Runtime validation: `docker compose up -d --build`, create order end-to-end,
   verify order reaches `SHIPPED` (then `COMPLETED` after deliver), shipments +
   notifications populated, events visible in Kafka

---

## Success Checklist

- [ ] All modules build: `mvn clean install`
- [ ] Shipping service healthy: `http://localhost:8086/actuator/health`
- [ ] Notification service healthy: `http://localhost:8087/actuator/health`
- [ ] New order flows automatically to `SHIPPED` (shipment + tracking number created)
- [ ] `POST /api/v1/shipments/{id}/deliver` completes the order (`COMPLETED`)
- [ ] Notifications recorded for ORDER_CONFIRMED, PAYMENT_RECEIVED, SHIPPED, DELIVERED
- [ ] Gateway routes `/api/v1/shipments/**` and `/api/v1/notifications/**`
- [ ] Phase 9 tests pass

**Once complete, you're ready for Phase 10 - Analytics & Reporting!**

For detailed phase information, see IMPLEMENTATION_PLAN.md
For quick reference, see PHASE_QUICK_REFERENCE.md
