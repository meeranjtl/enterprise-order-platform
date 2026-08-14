# Phase 9 - COMPLETE: Shipping & Notification Services

**Date:** August 14, 2026
**Status:** ✅ Validated — full build green, all containers healthy, end-to-end saga
verified through `COMPLETED`

Phase 9 closes the order fulfillment loop. After payment approval the
**Shipping Service** creates a shipment via an async request/reply packing-list
exchange with Inventory, and the **Notification Service** emails/SMSes the
customer at every milestone. The Order saga now runs
`PAYMENT_APPROVED → SHIPPED → COMPLETED`.

---

## What Was Delivered

### Shipping Service (:8086, schema `shipping`)
- `Shipment` entity: orderId (unique), trackingNumber, embedded address,
  status `PENDING → SHIPPED → DELIVERED`, packing list, shippedAt/deliveredAt
- Consumes `payment-events` (COMPLETED → idempotent shipment creation)
- Async request/reply: `PackingListRequestedEvent` on
  `inventory-shipping-request-events` → Inventory replies with
  `PackingListProvidedEvent` on `inventory-shipping-reply-events` → shipment
  gets its tracking number, becomes `SHIPPED`, publishes `ShipmentCreatedEvent`
- `POST /api/v1/shipments/{id}/deliver` publishes `ShipmentDeliveredEvent`
- REST: create, get by id, get by orderId, deliver

### Notification Service (:8087, schema `notification`)
- Consumes `order-events`, `payment-events`, `shipping-events`
  (group `notification-service-group`)
- Mapping: OrderCreated → ORDER_CONFIRMED (EMAIL); PaymentProcessed COMPLETED →
  PAYMENT_RECEIVED (EMAIL); ShipmentCreated → SHIPPED (EMAIL+SMS);
  ShipmentDelivered → DELIVERED (EMAIL+SMS)
- Simulated delivery (log + DB audit row, unique per order+type+channel →
  idempotent), publishes `NotificationSentEvent` via outbox
- REST: get by id, get by orderId

### Saga & Infrastructure Extensions
- `OrderSagaOrchestrator`: new `shipping-events` listener dispatching on the
  `eventType` header (`SHIPPED`, then `COMPLETED`)
- Inventory: `PackingListRequestConsumer` + reply publisher (outbox, idempotent)
- shared-library: 5 new event DTOs, 8 new topics (4 primary + 4 DLQ), DLQ
  listeners, real `DeadLetterPublishingRecoverer`, `eventType` header in
  `OutboxPublisher`
- Gateway routes `/api/v1/shipments/**` and `/api/v1/notifications/**`
  (both profiles) + fallbacks + api-docs aggregation

---

## Validation Results

### Build & Tests
- `mvn clean install` — **BUILD SUCCESS**, all 11 modules
- 137 tests passing, 0 failures (shared 18, gateway 25, customer 19,
  product 30, order 25, inventory 3, payment 2, shipping 7, notification 8,
  +2 new regression tests for the SHIPPED→COMPLETED transition)

### Docker Deployment
- `docker compose up -d --build` — all images build (multi-stage, shared
  BuildKit m2 cache)
- All containers report **healthy**: gateway, customer, product, order,
  inventory, payment, shipping, notification, postgres, redis, kafka,
  zookeeper, schema-registry, kafka-ui

### End-to-End Saga (via gateway :8080)
1. Seed: customer id=2, product id=2 (`PH9-WIDGET-001`), category Electronics,
   stock adjusted to 25
2. `POST /api/v1/orders` → order flows automatically:
   `PENDING → PAYMENT_APPROVED → SHIPPED` (~20s; shipment row with tracking
   number `TRK-UQIFXF489P5R` and packing list from Inventory's reply)
3. `POST /api/v1/shipments/2/deliver` → shipment `DELIVERED`, order
   `COMPLETED` (~8s)
4. Notifications recorded for the order: ORDER_CONFIRMED (EMAIL),
   PAYMENT_RECEIVED (EMAIL), SHIPPED (EMAIL+SMS), DELIVERED (EMAIL+SMS) — all
   `SENT`
5. Failure path also observed: an order created before stock existed stayed
   `PENDING` with the reserve event exhausting retries to the DLQ (expected
   Phase 8 compensation behaviour)

---

## Issues Found & Fixed During Validation

1. **MapStruct `componentModel` constant broke bean registration.**
   `@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)` in
   CustomerMapper (and Product/CategoryMapper) produced a mapper impl that
   Spring failed to register as a bean ("No qualifying bean of type ...Mapper"),
   preventing customer-service from starting at all. Fixed by using the string
   literal `componentModel = "spring"` (the form used by every working
   service). The customer-service integration test was also renamed
   `CustomerServiceIT` to match its file name (repo convention, Surefire
   `**/*IT.java` include).

2. **Order state machine treated SHIPPED as terminal** — the Phase 9
   `SHIPPED → COMPLETED` transition was added to `ALLOWED_TRANSITIONS` but
   `SHIPPED` was left in `TERMINAL_STATUSES`, so delivery events were rejected
   (`Order status SHIPPED cannot be changed`). Removed SHIPPED from
   `TERMINAL_STATUSES` and added regression tests
   (`updateStatus_shippedToCompleted`, `updateStatus_shippedOnlyAllowsCompleted`).

3. **Docker healthcheck windows too tight for this stack.** Kafka's JVM CLI
   health probe needs >5s under rebalance load, schema-registry can take ~3min
   to boot when all services connect at once, and the Spring Boot services
   take up to ~120s to start. Added `start_period`/wider timeouts to the
   kafka + schema-registry healthchecks in `docker-compose.yml` and
   `--start-period=180s` to all service Dockerfile HEALTHCHECKs. Also removed
   a stray empty `{controller,service,...}` directory (unexpanded brace) from
   customer-service sources.

## Known Issues / Notes
- Order 4 of the validation run is stuck at `SHIPPED`: its delivery event was
  consumed (and logged as an error) before fix #2 deployed. Order 5 proves the
  corrected flow.
- The gateway has no `/api/v1/categories/**` route (pre-existing, Phase 3/4
  gap — categories reachable only on product-service :8082). Not Phase 9 scope.

---

## Success Checklist (from PHASE_9_GETTING_STARTED.md)

- [x] All modules build: `mvn clean install`
- [x] Shipping service healthy: `http://localhost:8086/actuator/health`
- [x] Notification service healthy: `http://localhost:8087/actuator/health`
- [x] New order flows automatically to `SHIPPED` (shipment + tracking number created)
- [x] `POST /api/v1/shipments/{id}/deliver` completes the order (`COMPLETED`)
- [x] Notifications recorded for ORDER_CONFIRMED, PAYMENT_RECEIVED, SHIPPED, DELIVERED
- [x] Gateway routes `/api/v1/shipments/**` and `/api/v1/notifications/**`
- [x] Phase 9 tests pass

**Ready for Phase 10 - Analytics & Reporting ✅**
