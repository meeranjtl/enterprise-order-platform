# Phase 10 — Analytics & Reporting: Getting Started

**Date:** August 14, 2026
**Status:** 📋 Planned — ready to implement
**Predecessor:** Phase 9 complete (Shipping & Notification) — see `PHASE_9_COMPLETE.md`

Phase 10 adds the **analytics-service** (:8088, schema `analytics`): a
read-only consumer of the Kafka event bus that aggregates business metrics
(daily order/revenue metrics, product performance, customer analytics,
fulfillment timings) and exposes them through report APIs via the gateway.

This is the platform's **business intelligence layer** — the first service
that consumes events *without* participating in the saga (pure projection /
read model, i.e. the CQRS "query side" foreshadowed in Phase 14).

---

## 1. Inputs — What the Event Bus Gives Us

Phase 10 consumes existing topics; **no new event schemas required**.

| Topic | Event(s) | Fields usable for metrics |
|---|---|---|
| `order-events` | `OrderCreatedEvent` | `orderId`, `orderNumber`, `customerId`, `totalAmount`, `orderItems[]` (`productId`, `quantity`, `unitPrice`), `createdAt` |
| `payment-events` | `PaymentProcessedEvent` | `orderId`, `customerId`, `amount`, `status` (COMPLETED / FAILED / REFUNDED), `failureReason`, `createdAt` |
| `shipping-events` | `ShipmentCreatedEvent`, `ShipmentDeliveredEvent` | dispatch on `eventType` header (same pattern as `OrderSagaOrchestrator`) |
| `notification-events` | `NotificationSentEvent` | optional — notification delivery stats |

Notes:
- There is **no customer-created event** (customer-service predates Kafka).
  Customer metrics are derived from `OrderCreatedEvent.customerId`
  (distinct ordering customers) — not total registered customers.
- `shipping-events` carries **two** event types — consumers must dispatch on
  the `eventType` header written by `OutboxPublisher` (Phase 9 pattern).
- Kafka message key = `orderId` everywhere → partition ordering per order is
  guaranteed within a topic.

Reference implementations to copy patterns from:
- Consumer style: `notification-service/.../messaging/NotificationEventListener.java`
- Header dispatch: `order-service/.../saga/OrderSagaOrchestrator.java`
- Idempotency: DB-level unique constraints + `@Transactional` consumers
  (see `OrderEventConsumer`, `PaymentEventConsumer`)

---

## 2. Deliverables (from IMPLEMENTATION_PLAN.md §10)

- [ ] Analytics data model (Flyway-managed, schema `analytics`)
- [ ] Kafka consumers for order/payment (and shipping) events
- [ ] Aggregated metrics (daily rollups, product & customer aggregates)
- [ ] Report generation APIs (date-range queryable)
- [ ] Time-series data storage (`daily_metrics` date-keyed rows)
- [ ] ~~Analytics dashboards (UI)~~ → **deferred to Phase 13** (React UI);
      Phase 10 delivers the APIs the dashboard will call

---

## 3. Design

### 3.1 Data Model (schema `analytics`)

| Table | Key | Purpose |
|---|---|---|
| `daily_metrics` | unique(`metric_date`) | Per-day totals: `total_orders`, `total_revenue`, `avg_order_value`, `completed_orders`, `failed_orders`, `distinct_customers` |
| `product_metrics` | unique(`metric_date`, `product_id`) | Per-day per-product: `units_sold`, `revenue`, `times_in_order` (**ID-only** — decided Aug 14: names enriched by Phase 13 UI via product-service; no event schema change) |
| `order_revenue` | unique(`order_id`) | One row per paid order (`amount`, `status`, timestamps) — source of truth for revenue, prevents double-counting |
| `fulfillment_metrics` | unique(`order_id`) | Order→shipped and order→delivered durations (from shipping events) |

Plus **fact anchor tables** added in Sprint 2 (`V2__create_fact_tables.sql`):

| Table | Key | Purpose |
|---|---|---|
| `order_facts` | unique(`order_id`) | One row per OrderCreatedEvent — anchors order counts and distinct-customer correctness |
| `order_item_facts` | unique(`order_id`,`product_id`) | One row per order line — source of truth for product rollups |

All monetary values as `NUMERIC(12,2)`; counts default 0; `created_at`/
`updated_at` timestamps on every table. Indexes on `metric_date` columns.

**Aggregation strategy (refined in Sprint 2):** events first land in the
per-order FACT tables (`ON CONFLICT DO NOTHING` / `DO UPDATE`), then the
affected daily/product rollups are **recomputed from facts** — not
incremented. Incremental counters would double-count on Kafka redelivery;
recompute-from-facts converges to the truth for any replay/ordering. A
periodic **reconciliation sweep** (`MetricsReconciliationJob`, 10s default,
3-day lookback, both configurable) heals the rare race where two concurrent
consumer transactions miss each other's uncommitted writes.

### 3.2 Consumers (group `analytics-service-group`)

| Listener | Topic | Behavior |
|---|---|---|
| `OrderEventConsumer` | `order-events` | Upsert `daily_metrics` (orders++, customers, AOV inputs) + `product_metrics` per item |
| `PaymentEventConsumer` | `payment-events` | COMPLETED → `order_revenue` row + revenue rollup; FAILED → `failed_orders++`; REFUNDED → revenue adjustment |
| `ShippingEventConsumer` | `shipping-events` | Header dispatch: `ShipmentCreated` → shipped timestamp; `ShipmentDelivered` → duration metrics |

Idempotency rules (Phase 8 pattern):
- Unique constraints make reprocessing safe; consumers are `@Transactional`.
- Redelivery after crash must not double-count revenue → `order_revenue.order_id`
  unique key is the guard; rollups recompute from it or use conditional upserts.

### 3.3 Report APIs (`/api/v1/analytics/...`)

| Endpoint | Description |
|---|---|
| `GET /api/v1/analytics/daily-metrics?from=&to=` | Daily metric series for a date range |
| `GET /api/v1/analytics/product-metrics?from=&to=&sortBy=revenue|units` | Product performance, top-N support |
| `GET /api/v1/analytics/revenue?from=&to=` | Revenue summary (gross, refunded, net) |
| `GET /api/v1/analytics/customer-metrics?from=&to=` | Distinct ordering customers, top customers by spend |
| `GET /api/v1/analytics/fulfillment-metrics?from=&to=` | Avg order→ship / order→deliver times |
| `GET /api/v1/analytics/summary` | KPI snapshot (today + lifetime totals) |

All wrapped in `BaseResponse.success(...)`; date params validated
(`from <= to`, sensible max range); Swagger `@Operation` docs.

### 3.4 Infrastructure

- **Port:** `:8088` (next free after notification :8087)
- **Module:** `services/analytics-service` added to parent POM `<modules>`
- **DB:** Flyway `V1__analytics_schema.sql` creates schema + tables;
  `hibernate.ddl-auto: validate`
- **Gateway:** route `/api/v1/analytics/**` (both profiles) + fallback +
  api-docs aggregation entry
- **docker-compose.yml:** `analytics-service` container (multi-stage
  Dockerfile, shared BuildKit m2 cache, HEALTHCHECK with
  `--start-period=180s` per Phase 9 gotcha)

---

## 4. Sprint Plan

### Sprint 1 — Service Skeleton ✅ (Aug 22, 2026)
Delivered: module skeleton, `AnalyticsApplication` (named per repo
convention, not `AnalyticsServiceApplication`), `application.yml`,
`V1__create_analytics_tables.sql`. Build green, Flyway applied, actuator UP,
Swagger reachable.
1. `services/analytics-service/pom.xml` (copy notification-service structure;
   deps: web, JPA, postgres, flyway, kafka, shared-library, springdoc, lombok, mapstruct)
2. `AnalyticsServiceApplication`, `application.yml` (port 8088, schema
   `analytics`, kafka group `analytics-service-group`)
3. Flyway `V1__analytics_schema.sql` (tables per §3.1)
4. Register module in parent POM; build green
5. Actuator health up locally (kafka/postgres via `docker compose up postgres kafka`)

### Sprint 2 — Kafka Consumers ✅ (Aug 22, 2026)
6. Entities + repositories: the 4 rollup/fulfillment entities above **plus**
   `OrderFact`/`OrderItemFact` anchors (V2 migration); native
   `INSERT ... ON CONFLICT` upserts + rollup recompute queries
7. `OrderEventConsumer`, `PaymentEventConsumer`, `ShippingEventConsumer`
   (group `analytics-service-group`, header dispatch for shipping,
   exceptions propagate → shared error handler → DLQ)
8. `MetricsAggregationService` orchestrates facts → recompute;
   `MetricsReconciliationJob` scheduled sweep (re-enabled `@EnableScheduling`)
9. Tests: 17 unit + 6 integration (TestContainers PostgreSQL + EmbeddedKafka,
   incl. redelivery-doesn't-double-count, COMPLETED→REFUNDED revenue removal,
   shipping duration math, cross-topic race convergence) — 25/25 green;
   full platform build green (162 tests)

### Sprint 3 — Report APIs
10. DTOs + MapStruct mappers (**literal** `componentModel = "spring"` — Phase 9 gotcha)
11. `AnalyticsService` (range queries, sorting, top-N, summary rollups)
12. `AnalyticsController` per §3.3 + Swagger annotations
13. Unit tests (MockMvc / service mocks) + date-range validation tests

### Sprint 4 — Gateway, Docker, Validation
14. Gateway routes `/api/v1/analytics/**` + api-docs aggregation
15. Dockerfile + docker-compose entry; `docker compose up -d --build` healthy
16. E2E: seed orders through gateway → verify metrics update end-to-end
    (order created → daily metrics; payment COMPLETED → revenue; deliver →
    fulfillment timings)
17. Docs: `services/analytics-service/README.md`, `PHASE_10_COMPLETE.md`,
    update `AGENTS.md`

---

## 5. Anti-Patterns to Avoid (learned in Phases 8–9)

❌ `MappingConstants.ComponentModel.SPRING` in `@Mapper` — use the literal
   `"spring"` (Phase 9 gotcha: silent bean-registration failure)
❌ Test class name ≠ file name — Surefire includes are `**/*Test.java`,
   `**/*Tests.java`, `**/*IT.java`; convention is `XxxIT.java` containing
   `class XxxIT`
❌ Consuming `shipping-events` without `eventType` header dispatch — the
   topic carries two event types
❌ Counting revenue from in-flight events without an idempotency guard —
   always anchor on the unique `order_id` row
❌ HTTP calls to other services for enrichment — analytics is event-sourced
   only; denormalize names from events (`productName` from `OrderItem` is
   *not* available → store `productId`, join/enrich later or accept ID-only
   until a product snapshot event exists)
❌ Tight Docker healthcheck windows — keep `--start-period=180s` on the
   Dockerfile HEALTHCHECK

### Sprint 2 Gotchas (Learned During Implementation)

❌ **Never increment aggregate counters from events** — at-least-once Kafka
   delivery redelivers; `counter = counter + 1` double-counts. Anchor every
   event in a unique-constrained fact table, then recompute rollups from facts.

❌ **Cross-topic consumer race** — order-events and payment-events are consumed
   by parallel containers; each handler's transaction can miss the other's
   uncommitted writes, leaving the last-written rollup stale *forever*. The
   reconciliation sweep heals this within one interval (10s default).

❌ **Version-less entries in the parent `<dependencyManagement>` shadow the
   Spring Boot BOM** — the BOM-managed `spring-kafka-test` became unresolvable
   ("version is missing") because the local entry wins over imports. Either
   give it a version or drop the entry (dropped, Aug 22).

❌ **Mockito argument matching uses `equals()`** — `BigDecimal.ZERO` does not
   equal `BigDecimal.valueOf(0.0)` (different scales). Assert with the same
   construction the code uses, or `compareTo` in value assertions.

✅ **`avg_order_value` must be computed on BOTH paths of an upsert** — a CASE
   only in `DO UPDATE` leaves the first INSERT of a day with a zero AOV even
   when payments already landed. Compute it inside the SELECT subquery.

---

## 6. Success Checklist

- [ ] All modules build: `mvn clean install` green (existing 137 tests + new)
- [ ] Analytics service healthy: `http://localhost:8088/actuator/health`
- [ ] Creating an order via gateway updates `daily_metrics` and `product_metrics`
- [ ] Payment COMPLETED/FAILED/REFUNDED correctly update revenue metrics
      (and never double-count on redelivery)
- [ ] Shipping events update fulfillment timings (header dispatch works)
- [ ] All report endpoints return correct, date-range-filtered data via gateway
- [ ] Idempotency proven: replaying the same event does not corrupt metrics
- [ ] Swagger at `:8088/swagger-ui.html`
- [ ] Docker image builds; container healthy in compose stack
- [ ] `AGENTS.md` + phase docs updated

**Decisions (Aug 14, 2026):**
- `product_metrics` is **ID-only** — no `productName` column, no event schema
  change; Phase 13 UI enriches names via product-service. Zero risk to the saga.
