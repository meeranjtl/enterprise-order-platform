# Analytics Service

Read-only business intelligence layer for the Enterprise Order Platform.
Consumes the existing Kafka event bus (`order-events`, `payment-events`,
`shipping-events`) and exposes aggregated metrics and reports — daily
order/revenue rollups, product performance, customer analytics, and
fulfillment timings — through a REST API behind the gateway.

Unlike every other service in the platform, analytics-service does **not**
participate in the order saga. It is a pure projection / read model (the
CQRS "query side" the platform's later phases build toward) — it only reads
from the event bus and never publishes.

- **Port:** `8088`
- **Schema:** `analytics` (Flyway-managed, `flyway_schema_history_analytics`)
- **Kafka consumer group:** `analytics-service-group`
- **Swagger UI:** `http://localhost:8088/swagger-ui.html`
- **Health:** `http://localhost:8088/actuator/health`

## Data model

Every event lands first in an append-only **fact table** keyed by a unique
constraint (`order_id`, or `order_id`+`product_id`), then the affected
**rollup tables** are fully **recomputed from facts** — never incremented.
This is deliberate: Kafka's at-least-once delivery redelivers messages, and
an incremental `counter = counter + 1` double-counts on redelivery. Recompute-
from-facts converges to the same correct answer no matter how many times or
in what order an event is replayed.

| Table | Key | Purpose |
|---|---|---|
| `order_facts` | unique(`order_id`) | One row per `OrderCreatedEvent` — anchors order counts and distinct-customer correctness |
| `order_item_facts` | unique(`order_id`, `product_id`) | One row per order line — source of truth for product rollups |
| `daily_metrics` | unique(`metric_date`) | Per-day totals: orders, revenue, avg order value, completed/failed orders, distinct customers |
| `product_metrics` | unique(`metric_date`, `product_id`) | Per-day per-product: units sold, revenue, times ordered. **ID-only** — no `productName` (see below) |
| `order_revenue` | unique(`order_id`) | One row per order's latest payment status — source of truth for revenue, prevents double-counting on COMPLETED/FAILED/REFUNDED redelivery |
| `fulfillment_metrics` | unique(`order_id`) | order→shipped and order→delivered durations, derived from shipping events |

**Why `product_metrics` is ID-only:** `OrderCreatedEvent.OrderItem` only
carries `productId`, not a product name — and analytics-service never makes
synchronous HTTP calls to other services (it's event-sourced only, by
design). Product names are deferred to the Phase 13 React UI, which enriches
IDs via product-service. This was a deliberate decision, not an oversight.

### Reconciliation sweep

Two Kafka consumers (e.g. the order-events and payment-events listeners) run
in separate transactions and can each miss the other's uncommitted writes —
if payment-events for an order is processed a few milliseconds before its
order-events row commits, the revenue rollup can be built against a
not-yet-visible order fact and silently skip it. `MetricsReconciliationJob`
runs on a fixed delay (`analytics.reconcile.interval-ms`, default 10s) and
recomputes the rollups for the last N days (`analytics.reconcile.lookback-days`,
default 3) from the fact tables — since recompute is idempotent, this heals
the race within one interval without any special-casing in the consumers
themselves.

## Kafka consumers

| Listener | Topic | Behavior |
|---|---|---|
| `OrderEventConsumer` | `order-events` | Upserts `order_facts`/`order_item_facts`, recomputes `daily_metrics` + `product_metrics` for the order's date |
| `PaymentEventConsumer` | `payment-events` | COMPLETED → `order_revenue` row + revenue rollup; FAILED → failed-order rollup; REFUNDED → revenue adjustment; PENDING is ignored |
| `ShippingEventConsumer` | `shipping-events` | Dispatches on the `eventType` header (topic carries two event types) — `ShipmentCreated` records the shipped timestamp, `ShipmentDelivered` records the delivered timestamp and duration |

## Report API

All endpoints are wrapped in `BaseResponse.success(...)`, reachable through
the gateway at `http://localhost:8080/api/v1/analytics/...`, and take
optional `from`/`to` ISO date params (default: trailing 30 days, max range
366 days) except `/summary`, which is a lifetime snapshot.

| Endpoint | Description |
|---|---|
| `GET /api/v1/analytics/daily-metrics?from=&to=` | Daily order/revenue series |
| `GET /api/v1/analytics/product-metrics?from=&to=&sortBy=revenue\|units&limit=` | Top-N product performance |
| `GET /api/v1/analytics/revenue?from=&to=` | Gross/refunded/net revenue summary |
| `GET /api/v1/analytics/customer-metrics?from=&to=&limit=` | Distinct ordering customers, top spenders |
| `GET /api/v1/analytics/fulfillment-metrics?from=&to=` | Avg order→ship / order→deliver durations |
| `GET /api/v1/analytics/summary` | Lifetime KPI snapshot + top 5 products |

## Running locally

```powershell
# Infra only
docker compose up -d postgres kafka zookeeper

# Run the service (uses application.yml defaults: localhost:5432, localhost:9092)
mvn -pl services/analytics-service spring-boot:run
```

Inside the full `docker compose up -d --build` stack, the service talks to
`postgres:5432` and `kafka:9092` (container-network hostnames) via the
environment overrides in `docker-compose.yml`.

## Testing

```powershell
mvn test -pl services/analytics-service
```

44 tests: unit tests for `MetricsAggregationService` and `AnalyticsReportService`
(Mockito), consumer tests for header dispatch and unknown-event handling,
`@WebMvcTest` controller tests, and `AnalyticsConsumerIT` — a full-path
integration test (`@Testcontainers` PostgreSQL + `@EmbeddedKafka`) covering
idempotent redelivery, COMPLETED→REFUNDED revenue correction, and the
cross-topic reconciliation race.

## Known issues

`GET /api-docs` (and therefore the rendered content of `/swagger-ui.html`)
500s — this is a **pre-existing, platform-wide** issue also present on
notification-service and any other service that pulls in
`kafka-avro-serializer`, not something introduced by analytics-service.
`shared-library`'s Confluent `kafka-avro-serializer` dependency transitively
pulls `swagger-annotations:2.1.10`, which conflicts with the
`swagger-annotations-jakarta:2.2.9` springdoc expects
(`NoSuchMethodError: Schema.requiredMode()`). `swagger-ui.html` itself still
302-redirects fine; only the generated OpenAPI JSON is broken. Fixing this
requires an exclusion or a pinned version in the parent POM's dependency
management and affects every service — tracked as a follow-up, not fixed
here to keep this phase's scope to analytics.
