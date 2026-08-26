# Architecture

Canonical reference for service topology, ports, Kafka topics, and the saga flow. Read this before touching cross-service wiring, adding a service, or adding a Kafka topic. For phase-by-phase delivery narrative and validation results, see the individual `PHASE_N_COMPLETE.md` files at repo root.

## Service Topology

```
React UI (Phase 13) → API Gateway (Spring Cloud Gateway, :8080) → Services
                                        ↓
                    Kafka (Event Bus) - decouples services
                                        ↓
                    PostgreSQL (shared DB per phase design)
                                        ↓
        Prometheus/Grafana/Zipkin (Observability Stack)
```

| Service | Port | Schema | Purpose |
|---|---|---|---|
| gateway | 8080 | — | Reactive (Netty) API gateway; **must never depend on shared-library** (servlet-based). Generates/propagates `X-Correlation-Id`. All client traffic routes through here. |
| customer-service | 8081 | — | Customer CRUD |
| product-service | 8082 | — | Product catalog |
| order-service | 8083 | — | Order processing, saga orchestration |
| inventory-service | 8084 | — | Stock management |
| payment-service | 8085 | — | Payment handling |
| shipping-service | 8086 | `shipping` | Fulfillment: `PENDING → SHIPPED → DELIVERED` |
| notification-service | 8087 | `notification` | Simulated EMAIL/SMS with DB audit rows |
| analytics-service | 8088 | `analytics` | Read-model only — never joins the saga or publishes events |
| shared-library | — | — | Common exceptions, DTOs, validators, response wrappers, logging/observability utilities. Servlet services only (not gateway). |

All 9 non-gateway services + gateway are instrumented for observability (Phase 11): JSON logs, `/actuator/prometheus`, liveness/readiness probes, Zipkin tracing.

## Package Structure (per service)

```
com.enterprise.order.{service-name}/
├── controller/          # @RestController endpoints
├── service/             # @Service business logic
├── repository/          # Spring Data JpaRepository
├── entity/              # @Entity JPA models
├── dto/                 # Request/Response objects
├── mapper/              # MapStruct @Mapper interfaces
├── exception/           # Service-specific exceptions (if any)
├── config/              # Service-specific @Configuration
└── {ServiceName}Application.java
```

Shared cross-cutting code lives in `services/shared-library/src/main/java/com/enterprise/order/shared/`: `exception/`, `dto/` (`BaseResponse<T>`), `validation/`, `config/` (`GlobalExceptionHandler`), `util/`. Add new cross-cutting concerns there first, then consume from services — see [domain-rules.md](domain-rules.md).

Reference implementation: `services/customer-service` is the most complete example of the standard controller → service → repository → mapper layering.

## Kafka Topics

| Topic | Events | Introduced |
|---|---|---|
| `order-events` | OrderCreatedEvent | Phase 8 |
| `inventory-events` | InventoryReservedEvent, InventoryReleasedEvent | Phase 8 |
| `payment-events` | PaymentProcessedEvent, PaymentRefundedEvent | Phase 8 |
| `shipping-events` | ShipmentCreatedEvent, ShipmentDeliveredEvent (dispatched via `eventType` header) | Phase 9 |
| `notification-events` | NotificationSentEvent | Phase 9 |
| `inventory-shipping-request-events` / `-reply-events` | Async packing-list request/reply between shipping and inventory | Phase 9 |
| `*-dlq` | Dead-letter topic per topic above, auto-created, auto-consumed for logging | Phase 8+ |

Kafka message key = `orderId` everywhere. Topics are created at service startup via each service's `KafkaConfig` bean, not auto-provisioned by the broker (`KAFKA_AUTO_CREATE_TOPICS_ENABLE: "false"`). analytics-service (Phase 10) added no new topics — it only consumes the 4 primary ones.

## Saga Flow

```
1. Order created → OrderService.createOrder() stores OrderCreatedEvent in outbox
2. OutboxPoller publishes to order-events topic
3. InventoryService consumes OrderCreatedEvent, reserves stock, publishes InventoryReservedEvent
4. PaymentService consumes InventoryReservedEvent, processes payment, publishes PaymentProcessedEvent
5. OrderService saga consumer listens to payment-events, updates order status
6. ShippingService consumes COMPLETED payment → creates shipment (idempotent), exchanges packing-list with Inventory, → SHIPPED → DELIVERED
7. NotificationService consumes order/payment/shipping events → simulated EMAIL/SMS, audit row, publishes NotificationSentEvent
8. On failure: compensation flows trigger (inventory release, refund)
```

Order status terminal states and the notification event-mapping table live in [domain-rules.md](domain-rules.md). Full state machine, step-by-step flow, and compensating transactions: [saga.md](saga.md). CQRS (analytics-service) and the outbox pattern's relationship to event sourcing: [patterns.md](patterns.md).

## Component Map (where to find things)

| Concern | Location |
|---|---|
| Outbox pattern (entity, poller, publisher) | `shared-library/.../outbox/` |
| Event DTOs | `shared-library/.../events/` |
| Kafka topic/consumer config | `shared-library/.../config/KafkaConfig.java` |
| DLQ handler | `shared-library/.../messaging/DeadLetterQueueHandler.java` |
| Saga orchestrator | `order-service/.../saga/OrderSagaOrchestrator.java` |
| Event consumers | `*/messaging/*EventConsumer.java` (per service) |
| Shipping ↔ Inventory packing-list exchange | `shipping-service/.../messaging/PackingListReplyConsumer.java`, `inventory-service/.../messaging/PackingListRequestConsumer.java` |
| Analytics fact/rollup aggregation | `analytics-service/.../service/MetricsAggregationService.java`, `MetricsReconciliationJob.java` (scheduled sweep) |
| Analytics report API | `analytics-service/.../service/AnalyticsReportService.java` — `/api/v1/analytics/*` |
| JSON logging | `shared-library/src/main/resources/logback-json-base.xml` (`<include>`d by servlet services); gateway carries a standalone copy |
| Kafka health check | `shared-library/.../health/KafkaHealthIndicator.java` |
| Observability infra config | `observability/prometheus.yml`, `observability/grafana/provisioning/`, `observability/grafana/dashboards/*.json` |

## Database

PostgreSQL 15, shared across services (Phase 1 design; shipping/notification/analytics use dedicated schemas within it). Flyway migrations at `services/{service}/src/main/resources/db/migration/`, naming `V{N}__{description}.sql`, auto-run on startup. Always `hibernate.ddl-auto: validate` — see [domain-rules.md](domain-rules.md).

## Build Artifacts

- Each service builds as `services/{service-name}/target/{service-name}-1.0.0.jar`
- Docker image naming: `{service-name}:1.0.0`
