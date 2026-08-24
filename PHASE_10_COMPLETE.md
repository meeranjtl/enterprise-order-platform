# Phase 10 - COMPLETE: Analytics & Reporting

**Date:** August 24, 2026
**Status:** ✅ Validated — full build green, all containers healthy, metrics verified
end-to-end through the gateway for a real order → payment → shipment → delivery saga

Phase 10 adds the **Analytics Service**, the platform's first pure read-model
consumer: it never joins the order saga or publishes events, only aggregates
the existing `order-events`/`payment-events`/`shipping-events` bus into
queryable business metrics — daily order/revenue rollups, product
performance, customer analytics, and fulfillment timings.

---

## What Was Delivered

### Analytics Service (:8088, schema `analytics`)

**Data model** — fact tables anchor idempotency, rollups are always
recomputed from facts (never incremented) so Kafka's at-least-once
redelivery can never double-count:
- `order_facts` (unique `order_id`), `order_item_facts` (unique `order_id`+`product_id`)
- `daily_metrics` (unique `metric_date`), `product_metrics` (unique `metric_date`+`product_id`, **ID-only** — no `productName`, deferred to the Phase 13 UI via product-service)
- `order_revenue` (unique `order_id`, latest payment status wins), `fulfillment_metrics` (unique `order_id`)

**Consumers** (group `analytics-service-group`):
- `OrderEventConsumer` (`order-events`) — upserts facts, recomputes daily + product rollups
- `PaymentEventConsumer` (`payment-events`) — COMPLETED → revenue row + rollup; FAILED → failed-order rollup; REFUNDED → revenue adjustment; PENDING ignored
- `ShippingEventConsumer` (`shipping-events`) — dispatches on the `eventType` header; `ShipmentCreated`/`ShipmentDelivered` update fulfillment timings

**Reconciliation:** `MetricsReconciliationJob` (`@Scheduled`, 10s default
interval, 3-day lookback) periodically recomputes recent rollups from facts,
healing the race where two parallel consumer transactions each miss the
other's uncommitted writes.

**Report API** (`/api/v1/analytics/...`, all wrapped in `BaseResponse`):
`daily-metrics`, `product-metrics` (sortBy revenue|units, top-N), `revenue`
(gross/refunded/net), `customer-metrics` (distinct + top spenders),
`fulfillment-metrics` (avg order→ship / order→deliver), `summary` (lifetime
KPIs + top 5 products).

### Gateway & Infrastructure
- Route `/api/v1/analytics/**` + `/analytics-service/api-docs` (both
  `application.yml` and `application-docker.yml`), circuit breaker +
  fallback + resilience4j instances, Swagger URL aggregation entry
- `services/analytics-service/Dockerfile` (multi-stage, shared BuildKit m2
  cache, `--start-period=180s` HEALTHCHECK per the Phase 9 gotcha)
- `docker-compose.yml`: `analytics-service` container (port 8088, schema
  `analytics`), added to gateway's `depends_on`

---

## Validation Results

### Build & Tests
- `mvn clean install` — **BUILD SUCCESS**, all 11 modules
- 181 tests passing, 0 failures platform-wide (44 new for analytics-service:
  unit tests for `MetricsAggregationService`/`AnalyticsReportService`,
  consumer header-dispatch tests, `@WebMvcTest` controller tests, and
  `AnalyticsConsumerIT` — full-path Testcontainers PostgreSQL + EmbeddedKafka
  covering idempotent redelivery, revenue correction, and the reconciliation race)
- Gateway module: 25/25 tests green after adding the analytics route (incl.
  `GatewayRoutingIT`, Testcontainers Redis)

### Docker Deployment
- `docker compose up -d --build` — all 14 containers healthy: gateway,
  customer, product, order, inventory, payment, shipping, notification,
  **analytics**, postgres, redis, kafka, zookeeper, schema-registry, kafka-ui

### End-to-End Saga (via gateway :8080)
1. Seeded category (direct on product-service :8082 — `/api/v1/categories`
   has no gateway route, see Known Issues), customer id=3, and an order
   against an existing seeded product (id=1, `SKU-SAGA-001`) with real
   inventory stock
2. `POST /api/v1/orders` (order 7, $92.50) → saga ran automatically to
   `SHIPPED` (~13s); `POST /api/v1/shipments/{id}/deliver` → `DELIVERED`
3. Analytics confirmed correct at every stage, queried through the gateway:
   - **Order created** → `daily-metrics.totalOrders` and `customer-metrics`
     updated immediately (before payment even completed)
   - **Payment COMPLETED** → `revenue.grossRevenue`/`netRevenue` and
     `daily-metrics.completedOrders` incremented by exactly the order total
   - **Shipped** → `fulfillment-metrics.shippedOrders` incremented,
     `avgOrderToShipSeconds` recomputed across all tracked orders
   - **Delivered** → `fulfillment-metrics.deliveredOrders` incremented,
     `avgOrderToDeliverSeconds` populated (295s for this run)
   - `product-metrics` and `summary` reflected the new product/revenue
     entries alongside prior smoke-test data

---

## Issues Found & Fixed During Validation

1. **`sumLifetimeTotals()` `Object[]` vs `List<Object[]>` return type.**
   Spring Data JPA always wraps aggregate JPQL `SELECT` results in a `List`,
   even for a single-row result; declaring the repository method to return a
   bare `Object[]` produced a nested-array cast failure
   (`Object[] cannot be cast to Number`) at runtime on `GET /summary`, invisible
   to unit tests because they mock the repository. Fixed the declared return
   type to `List<Object[]>` (matching every other aggregate query in the file)
   and updated the service call site and its test mock.

2. **`aggregateDurations()` missing `COALESCE` on `SUM(CASE...)`.** SQL `SUM`
   over zero matching rows returns `NULL`, not `0`. `GET /fulfillment-metrics`
   NPE'd on an empty/fresh schema because the shipped/delivered counts weren't
   wrapped in `COALESCE(..., 0)` like the equivalent daily-metrics query
   already was. Fixed; deliberately left the two `AVG(...)` expressions
   nullable (no orders → no average, correctly serializes as JSON `null`).

3. **Kafka host-vs-container networking during manual smoke testing.** The
   JVM run directly on the host needs `localhost:9094`
   (`KAFKA_ADVERTISED_LISTENERS` `PLAINTEXT_HOST`), not the default
   `localhost:9092` container-network address — a misconfigured first batch
   of test events was silently never consumed. Not a code bug; a
   `SPRING_KAFKA_BOOTSTRAP_SERVERS` environment override fixed the run. No
   code change required, since `docker-compose.yml` already sets `kafka:9092`
   correctly for in-network services.

---

## Known Issues / Notes

- **`GET /api-docs` 500s platform-wide** (pre-existing, not introduced by
  Phase 10). `shared-library`'s `kafka-avro-serializer` transitively pulls
  `swagger-annotations:2.1.10`, conflicting with springdoc's
  `swagger-annotations-jakarta:2.2.9` (`NoSuchMethodError: Schema.requiredMode()`).
  Confirmed identical on notification-service via `dependency:tree`.
  `swagger-ui.html` itself still 302-redirects correctly; only the generated
  OpenAPI JSON is broken. Fixing this needs a parent-POM-level exclusion or
  pinned version and affects every service — left as a follow-up, out of
  scope for this phase.
- **No gateway route for `/api/v1/categories/**`** (pre-existing gap, also
  noted in `PHASE_9_COMPLETE.md`; confirmed still present during Phase 10
  E2E testing). Categories are reachable only on product-service directly (`:8082`).
- **Creating a product via product-service does not provision an
  inventory-service record.** Discovered during E2E testing: an order placed
  against a freshly-created product fails inventory reservation
  (`Inventory not found with identifier: N`) and the order sticks at
  `PENDING` — this is expected given the current design (inventory rows are
  seeded independently, not auto-created from product events) and not a
  Phase 10 defect, but it means E2E scripts/demos must use a product that
  already has a seeded inventory row (e.g. product id 1 or 2) or call
  inventory-service's `/adjust` endpoint first.

---

## Success Checklist (from PHASE_10_GETTING_STARTED.md)

- [x] All modules build: `mvn clean install` green (181 tests)
- [x] Analytics service healthy: `http://localhost:8088/actuator/health`
- [x] Creating an order via gateway updates `daily_metrics` and `product_metrics`
- [x] Payment COMPLETED/FAILED/REFUNDED correctly update revenue metrics
      (and never double-count on redelivery — proven in `AnalyticsConsumerIT`)
- [x] Shipping events update fulfillment timings (header dispatch works)
- [x] All report endpoints return correct, date-range-filtered data via gateway
- [x] Idempotency proven: replaying the same event does not corrupt metrics
- [x] Swagger UI reachable at `:8088/swagger-ui.html` (redirect works; `/api-docs`
      JSON generation has the platform-wide known issue above)
- [x] Docker image builds; container healthy in compose stack
- [x] `AGENTS.md` + phase docs updated

**Ready for Phase 11 ✅**
