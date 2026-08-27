# CQRS & Event Sourcing — What's Actually Implemented

`IMPLEMENTATION_PLAN.md`'s Phase 14 template calls for CQRS and event
sourcing "pattern examples." Both already exist in this platform as a
natural consequence of earlier phases — this document names them
explicitly and, just as importantly, states what was **not** built, so
neither pattern is overclaimed.

## CQRS — analytics-service (Phase 10)

analytics-service is the platform's command/query segregation example:

- **Write side**: order-service, inventory-service, payment-service,
  shipping-service each own their transactional write model and publish
  domain events (`order-events`, `inventory-events`, `payment-events`,
  `shipping-events`) via the outbox pattern (below) — they never write to
  or query analytics-service's schema.
- **Read side**: analytics-service consumes those same events and builds
  its own denormalized read model (`analytics` schema — fact tables plus
  daily/product/customer rollups) purely for reporting. It **never joins
  the saga or publishes events itself** (see
  [architecture.md](architecture.md#service-topology)) — a query-only
  service by construction, not just convention.
- **Why it matters**: order-service's `Order` table is optimized for
  transactional correctness (one row per order, `SELECT ... FOR UPDATE`
  friendly); analytics-service's tables are optimized for aggregation
  queries (`GET /api/v1/analytics/summary`, `/daily-metrics`,
  `/product-metrics`, etc.) — the two would fight each other under one
  shared model. CQRS lets each side use the model that fits its own
  access pattern.
- **Code**: `analytics-service/.../service/MetricsAggregationService.java`
  (event consumption → fact tables → rollups),
  `MetricsReconciliationJob.java` (scheduled sweep that re-derives rollups
  from facts, catching any drift from at-least-once redelivery),
  `AnalyticsReportService.java` (the query API).
- **The one hard rule** (see
  [domain-rules.md](domain-rules.md#idempotency-required-on-every-consumer)):
  rollups are always *recomputed from the fact table*, never incremented
  directly from an event — Kafka's at-least-once delivery would silently
  double-count an incrementing counter on redelivery.

## Event Sourcing — the outbox pattern, and where it stops

The **transactional outbox** (`shared-library/.../outbox/OutboxEvent.java`,
`OutboxPoller.java`, `OutboxPublisher.java`, Phase 8) is event-sourcing-*adjacent*,
not a full event-sourcing implementation:

- What it does: every state change that needs to notify other services
  writes an `OutboxEvent` row (`aggregateId`, `eventType`, JSON `payload`,
  target `kafkaTopic`/`kafkaKey`) inside the **same transaction** as the
  business write, guaranteeing the event is never lost or published
  without the state change actually having committed. A separate
  `@Scheduled` poller publishes unpublished rows to Kafka afterward. This
  is the standard "reliable event publication" half of event sourcing.
- What it deliberately does **not** do: an entity's current state is
  still stored directly (`Order`, `Payment`, `InventoryTransaction`
  rows), not reconstructed by replaying its event history. There's no
  event store as the system of record, no snapshotting, and no replay
  capability — restoring a service from nothing would mean restoring its
  Postgres schema from a backup, not replaying `outbox_events`.
- **Why this is the right call here, not a shortcut**: full event
  sourcing (event store as source of truth, state rebuilt by replay) is
  explicitly listed under `IMPLEMENTATION_PLAN.md`'s own "Future
  Enhancements," not a Phase 14 deliverable — the template itself defers
  it. Building it now would mean redesigning every service's persistence
  layer, well outside a "polish and document" final phase.

## Summary

| Pattern | Status | Where |
|---|---|---|
| CQRS | ✅ Real, working example | analytics-service (Phase 10) |
| Saga (orchestration) | ✅ Real, working example | order-service `OrderSagaOrchestrator` — see [saga.md](saga.md) |
| Event sourcing | Partial (reliable publication via outbox) — full replay/event-store deliberately out of scope | `shared-library/.../outbox/` |
