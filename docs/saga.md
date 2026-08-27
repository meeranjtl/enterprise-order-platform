# Saga Pattern — Order Fulfillment

This platform's order fulfillment flow is an **orchestration-based saga**:
`order-service` owns the order's state machine and is the only service
that writes `Order.status`, but no single service calls the others
synchronously — each step is triggered by consuming the previous step's
Kafka event, and `order-service`'s `OrderSagaOrchestrator` listens to
every downstream topic purely to advance status. This is a hybrid of
orchestration (one authoritative state owner) and choreography (steps
react to events rather than being commanded) — pragmatic for a
single-writer state machine without a dedicated orchestrator service.

See [architecture.md](architecture.md#saga-flow) for the topic list and
one-line flow summary; this document covers the state machine, the
compensating transactions, and where each piece lives in code.

## State Machine

```
PENDING → VALIDATED → PAYMENT_PENDING → PAYMENT_APPROVED → SHIPPED → COMPLETED
              │              │                  │
              ▼              ▼                  ▼
           FAILED      PAYMENT_REJECTED      CANCELLED
                              │
                              ▼
                           FAILED
```

`TERMINAL_STATUSES = {CANCELLED, FAILED, COMPLETED}` — `SHIPPED` is
**not** terminal, the only valid transition out of it is
`SHIPPED → COMPLETED`. See [domain-rules.md](domain-rules.md#order-state-machine).

## Step-by-Step Flow

| # | Trigger | Actor | Action | Resulting order status |
|---|---|---|---|---|
| 1 | `POST /api/v1/orders` | order-service | Validates customer/product (`CustomerClient`/`ProductClient` — see below), persists order, stores `OrderCreatedEvent` in its outbox | `PENDING` |
| 2 | Outbox poll | order-service | `OutboxPoller` publishes the stored event to `order-events` | — |
| 3 | Consumes `order-events` | inventory-service | Reserves stock per line item, publishes `InventoryReservedEvent` (`CONFIRMED` or `FAILED`) to `inventory-events` | — |
| 4 | Consumes `inventory-events` | order-service (`OrderSagaOrchestrator`) | `CONFIRMED` → advance; `FAILED` → fail the order | `PAYMENT_PENDING` or `FAILED` |
| 5 | Consumes `inventory-events` | payment-service | On `CONFIRMED`, creates and processes a `Payment`, publishes `PaymentProcessedEvent` (`COMPLETED` or `FAILED`) to `payment-events` | — |
| 6 | Consumes `payment-events` | order-service (`OrderSagaOrchestrator`) | `COMPLETED` → advance; `FAILED` → reject | `PAYMENT_APPROVED` or `PAYMENT_REJECTED` |
| 7 | Consumes `payment-events` | inventory-service | On `FAILED`, releases the reservations it made in step 3 (compensating transaction — see below) | — |
| 8 | Consumes `payment-events` (`COMPLETED`) | shipping-service | Creates a shipment (idempotent on order id), exchanges a packing list with inventory-service, publishes `ShipmentCreatedEvent` then later `ShipmentDeliveredEvent` to `shipping-events` | — |
| 9 | Consumes `shipping-events` | order-service (`OrderSagaOrchestrator`) | `ShipmentCreatedEvent` → advance; `ShipmentDeliveredEvent` → close the saga | `SHIPPED` then `COMPLETED` |
| — | Consumes order/payment/shipping events | notification-service | Simulated EMAIL/SMS per the mapping table in [domain-rules.md](domain-rules.md#notification-mapping-event--notification), publishes `NotificationSentEvent` | — |

`CANCELLED` is a separate, customer/admin-initiated transition
(`cancelOrder`), not part of the automatic saga progression above —
allowed only while the order is still in a cancellable pre-fulfillment
state.

## Compensating Transactions

A saga has no distributed transaction to roll back — failure at any
step is handled by a **compensating action** triggered by the failure
event itself, not by the orchestrator reaching back into a prior step:

- **Payment fails** (`PaymentProcessedEvent.FAILED`) → inventory-service's
  `PaymentEventConsumer` finds every `RESERVE` transaction for that
  order and calls `InventoryService.release(...)` for each, with an
  idempotency key derived from `paymentId` + transaction id (double-release-safe).
  `services/inventory-service/.../messaging/PaymentEventConsumer.java`.
- **Inventory reservation fails** (`InventoryReservedEvent.FAILED`) →
  nothing to compensate yet (no payment was attempted) — the order simply
  moves to `FAILED` directly (step 4 above).
- **Order cancelled pre-fulfillment** → handled synchronously in
  `OrderService.cancelOrder()`, not via the event-driven compensation
  path described above (cancellation is customer-initiated, not a saga
  failure reaction).

## Idempotency

Every consumer in this flow is idempotent — required because Kafka is
at-least-once, not exactly-once. See
[domain-rules.md](domain-rules.md#idempotency-required-on-every-consumer)
for the per-consumer key strategy (composite `orderId:productId` for the
order consumer, DB existence checks for payment/inventory, unique-constrained
fact tables for analytics rollups).

## Known Issue: an internal call outside the saga

`payment-service`'s `InventoryEventConsumer` (step 5 above) does **not**
get order/customer/amount details from a Kafka event — it makes a
direct, synchronous `RestTemplate` call to
`http://order-service:8083/api/v1/orders/{id}` to fetch them. This is
doubly wrong:

1. It's the exact anti-pattern [domain-rules.md](domain-rules.md#service-communication)
   forbids for Phase 8+ ("use Kafka events, not direct HTTP calls,
   between order/inventory/payment/shipping/notification"). Unlike
   order-service's `CustomerClient`/`ProductClient` (a deliberately
   sanctioned pre-Phase-8 exception, kept for immediate-consistency
   validation at order-creation time), this call was never a sanctioned
   exception — it appears to be an unnoticed regression from whenever
   payment-service was built.
2. It carries no `Authorization` header, and `GET /api/v1/orders/{id}`
   requires `hasRole('ADMIN') or @orderSecurity.isOwner(...)` (Phase 12).
   The call 401s on every single invocation — **payment-service can
   never successfully create a `Payment` for any order**, independent of
   the Kafka-volume data-loss issue already documented in
   [gotchas.md](gotchas.md#phase-13--react-ui). That entry's explanation
   for the platform's stuck `PAYMENT_PENDING` test order was real but
   incomplete — this is the actual, universally-reproducing root cause.

Not fixed as part of this documentation pass — `OrderCreatedEvent`
(already consumed elsewhere, e.g. inventory-service) carries `orderId`,
`customerId`, and `totalAmount`, exactly what this call fetches, so the
correct fix is for payment-service to consume `order-events` into a
small local lookup instead of calling order-service at all — a
real (if contained) piece of implementation work, not a one-line patch.
Tracked as a decision point for the end of Phase 14 alongside the
Kafka/ZooKeeper persistent-volume question.
