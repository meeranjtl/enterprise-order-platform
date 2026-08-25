# AGENTS.md - AI Agent Guidance for Enterprise Order Platform

**Purpose:** Essential knowledge for AI coding agents to be immediately productive in this microservices architecture codebase.

---

## Quick Context

This is a **14-phase microservices order processing platform** (Spring Boot 3, Java 21, PostgreSQL, Kafka). Built as a portfolio/learning project demonstrating enterprise architecture, operational excellence, and engineering maturity. Currently in **Phase 11 complete (Observability)** — foundation, customer-service (:8081), product-service (:8082), gateway (:8080), order-service (:8083), inventory-service (:8084), payment-service (:8085), shipping-service (:8086), notification-service (:8087), analytics-service (:8088) are implemented with event-driven saga orchestration through fulfillment, a read-model analytics layer, and JSON logging/Prometheus/Grafana/Zipkin observability platform-wide; next up is Phase 12 (Security).

**Gateway note:** the gateway is reactive (Netty) and must never depend on the servlet-based shared-library. It generates/propagates `X-Correlation-Id`; shared-library's `CorrelationIdLoggingFilter` puts it in the MDC of servlet services. All client traffic should go through `:8080`.

**Key Stack:** Spring Boot 3, Spring Cloud Gateway, Apache Kafka, PostgreSQL, React 18, Docker Compose, Resilience4j  
**Project Type:** Maven multi-module with 10 services + shared library  
**Tech Leadership:** Event-driven architecture, CQRS/Saga patterns (planned), microservices with distributed tracing

---

## Architecture Overview (Read First!)

### Service Topology
```
React UI (Phase 13) → API Gateway (Spring Cloud Gateway) → 9 Microservices
                                        ↓
                    Kafka (Event Bus) - decouples services
                                        ↓
                    PostgreSQL (shared DB per phase design)
                                        ↓
        Prometheus/Grafana/Zipkin (Observability Stack)
```

**Current Services** (see `services/` directory):
- **shared-library**: Common exceptions, DTOs, validators, response wrappers, logging utilities, observability (JSON log base, Kafka health indicator)
- **gateway**: API Gateway (Phase 4) ✅ — reactive, own copy of Phase 11 observability deps
- **customer-service**: Customer CRUD (Phase 2) ✅
- **product-service**: Product catalog (Phase 3) ✅
- **order-service**: Order processing (Phase 5) ✅
- **inventory-service**: Stock management (Phase 6) ✅
- **payment-service**: Payment handling (Phase 7) ✅
- **shipping-service**: Fulfillment (Phase 9) ✅
- **notification-service**: Email/SMS (Phase 9) ✅
- **analytics-service**: Metrics/reporting (Phase 10) ✅

All 10 services above are instrumented for observability (Phase 11): JSON
logs, `/actuator/prometheus`, liveness/readiness probes, Zipkin tracing.

**Critical Design Decision:** All services depend on `shared-library` for common code. When adding cross-cutting concerns (exceptions, DTOs, validators), add to `shared-library` first, then services can consume.

---

## Build & Deployment Workflows

### Maven Commands (Parent POM at root)
```powershell
# Build everything (all services + shared-library)
mvn clean install

# Build specific service only
mvn clean install -pl services/customer-service

# Skip tests for faster iteration
mvn clean install -DskipTests

# Run single service with Spring Boot plugin
mvn -pl services/customer-service spring-boot:run

# Run tests only for a specific service
mvn test -pl services/customer-service

# Check test coverage
mvn clean install jacoco:report -pl services/customer-service
```

### Docker Compose (Infrastructure)
```powershell
# Start PostgreSQL only (for local development)
docker compose up postgres

# View logs
docker compose logs -f postgres

# Stop all
docker compose down
```

**Parent POM Location:** `C:\dev\projects\enterprise-order-platform\pom.xml`  
**Dependency Management:** Spring Boot BOM (3.3.0), Spring Cloud (2023.0.3), Lombok, MapStruct configured in parent  
**Java Version:** 21 (set in properties), enforced via maven-compiler-plugin

### Key Build Artifacts
- Each service builds as: `services/{service-name}/target/{service-name}-1.0.0.jar`
- JAR includes embedded Tomcat, PostgreSQL driver, Spring Boot runtime
- Docker image naming: `{service-name}:1.0.0` (build command in each Dockerfile)

---

## Code Organization & Naming Conventions

### Package Structure (per service)
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
└── {ServiceName}Application.java  # @SpringBootApplication entry point
```

### Naming Conventions
- **Controllers:** `{Resource}Controller` (e.g., `CustomerController`)
- **Services:** `{Resource}Service` (e.g., `CustomerService`)
- **Repositories:** `{Resource}Repository extends JpaRepository` (e.g., `CustomerRepository`)
- **Entities:** `{Resource}` (e.g., `Customer`) with `@Entity`
- **DTOs:** `{Resource}DTO` (e.g., `CustomerDTO`) for external APIs
- **Mappers:** `{Resource}Mapper extends MapStructMapper` (e.g., `CustomerMapper`)
- **Exception classes:** Extend `com.enterprise.order.shared.exception.ApplicationException`

### Critical Libraries (Understand These)
- **Lombok:** `@Data`, `@Builder`, `@RequiredArgsConstructor` reduce boilerplate; processor in maven-compiler-plugin
- **MapStruct:** DTO ↔ Entity mapping; uses `@Mapper(componentModel = "spring")` to generate Spring beans
- **Jakarta Validation:** `@NotBlank`, `@Email` on DTOs; custom validators extend `ConstraintValidator`
- **Springdoc OpenAPI:** Generates Swagger UI at `/swagger-ui.html`; configure in `application.yml`

### Shared Library Organization
All cross-cutting code lives in `services/shared-library/src/main/java/com/enterprise/order/shared/`:
- **exception/**: `ApplicationException`, `ResourceNotFoundException`, `BadRequestException`, etc. — all services throw these
- **dto/**: `BaseResponse<T>` wrapper for all API responses (success/error patterns)
- **validation/**: Custom validators (e.g., `@ValidPhone`, `@ValidAddress` with regex patterns)
- **config/**: `GlobalExceptionHandler` (@RestControllerAdvice) handles all app exceptions → RFC 7807 Problem Details format
- **util/**: Common utilities (logging, correlation IDs, etc.)

### Code Formatting Requirements
* **Preserve Structure:** Maintain proper vertical spacing and line breaks between logical blocks.
* **Enforce Readability:** Wrap long lines of code appropriately to prevent horizontal scrolling.
* **Standard Conventions:** Adhere strictly to language-specific style guides (e.g., PEP 8 for Python, Prettier/Airbnb for JavaScript).
* **No Compression:** Never output minified, single-line, or densely packed code unless explicitly requested.

**When adding new cross-cutting code:** Add to shared-library, rebuild (`mvn clean install -pl services/shared-library`), then services can consume.

---

## Key Development Patterns

### Exception Handling Pattern
```java
// Throw application exceptions (never generic Exception)
throw new ResourceNotFoundException("Customer", customerId);

// GlobalExceptionHandler automatically converts to RFC 7807 response:
// { "status": 404, "title": "Not Found", "detail": "Customer 123 not found", ... }
```

### Service Method Pattern
```java
@Service
@RequiredArgsConstructor  // Lombok constructor injection
@Transactional           // Automatic transaction management
public class CustomerService {
    private final CustomerRepository repo;
    private final CustomerMapper mapper;  // MapStruct
    
    public CustomerDTO getById(Long id) {
        return mapper.toDTO(repo.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Customer", id)));
    }
}
```

### REST Endpoint Pattern
```java
@RestController
@RequestMapping("/api/v1/customers")
@RequiredArgsConstructor
public class CustomerController {
    private final CustomerService service;
    
    @GetMapping("/{id}")
    @Operation(summary = "Get customer by ID")  // Swagger
    public BaseResponse<CustomerDTO> getCustomer(@PathVariable Long id) {
        return BaseResponse.success(service.getById(id));
    }
}
```

### Validation Pattern
Use JSR-303 annotations on DTOs + custom validators for complex logic:
```java
@Data
public class CustomerDTO {
    @NotBlank
    private String name;
    
    @ValidPhone  // Custom validator
    private String phone;
}
```

### Testing Pattern
```java
@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {
    @Mock private CustomerRepository repo;
    @InjectMocks private CustomerService service;
    
    @BeforeEach
    void setup() { /* initialization */ }
    
    @Test
    void testGetById_notFound() {
        when(repo.findById(999L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, 
            () -> service.getById(999L));
    }
}
```

### Spring Boot Configuration Pattern
Each service has `application.yml` configuring:
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/enterprise_order
  jpa:
    hibernate:
      ddl-auto: validate  # Use Flyway for migrations
  flyway:
    enabled: true
    locations: classpath:db/migration
  
  # Logging as JSON
  application:
    name: customer-service
  
  # Springdoc/Swagger
springdoc:
  swagger-ui:
    path: /swagger-ui.html
```

---

## Database Migrations & Schema

**Migration Tool:** Flyway (automated SQL versioning)  
**Location:** `services/{service}/src/main/resources/db/migration/`  
**Naming Convention:** `V1__initial_schema.sql`, `V2__add_column.sql`, etc.

**Pattern:** Flyway runs on service startup; scripts auto-discovered in `db/migration/` folder.

**To add a new table:**
1. Create `V{next_number}__{description}.sql` in migration folder
2. Service startup auto-runs it
3. Use `hibernate.ddl-auto: validate` (never `create` in production)

**Database:** PostgreSQL 15 shared across services (Phase 1 design; later phases may introduce database-per-service)

---

## Testing Strategy

### Scope of Tests
- **Unit Tests:** Mock all dependencies; test service logic in isolation (fastest)
- **Integration Tests:** Use TestContainers for PostgreSQL; test service + DB interactions
- **E2E Tests:** (Future phases) Test complete workflows across services

### TestContainers Usage (Integration Tests)
```java
@Testcontainers  // Annotation
@SpringBootTest
class CustomerServiceIntegrationTest {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15");
    
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
    }
}
```

### Test Coverage Target
- Aim for 80%+ line coverage per service
- Focus on business logic, not getters/setters (Lombok handles those)
- Mock external service calls (payment gateway, email service, etc.)

---

## CI/CD & Deployment

**CI/CD Framework:** GitHub Actions (not yet fully implemented in Phase 1, but planned)  
**Deployment:** Docker Compose (Phase 14); Kubernetes later  
**Artifact Registry:** (To be configured) Docker Hub or private registry

**Phase 1 Status:** Docker Compose skeleton ready; PostgreSQL container configured; other services' Dockerfiles ready.

---

## Common Tasks & Workflows

### "I need to add a new REST endpoint"
1. Add method to `{Resource}Service` class
2. Add DTO for request/response in `dto/` folder  
3. Add endpoint in `{Resource}Controller` with `@GetMapping/@PostMapping`
4. Use `BaseResponse.success()` wrapper for response
5. Add `@Operation` annotation for Swagger documentation
6. Test with `@WebMvcTest` or MockMvc

### "I need to handle a new exception scenario"
1. Check if exception exists in `shared-library/exception/`
2. If not, create new exception class extending `ApplicationException`
3. GlobalExceptionHandler automatically converts to RFC 7807 response
4. Test with `assertThrows` in unit tests

### "I need to add a new database field"
1. Add field to `@Entity` class with `@Column` annotation
2. Create migration script: `V{N}__add_{field}_to_{table}.sql`
3. Update `{Resource}DTO` to expose field
4. Update MapStruct `{Resource}Mapper` if mapping needed
5. Run `mvn flyway:migrate` to execute migration

### "I need to validate an input parameter"
1. Add JSR-303 annotation to DTO (`@NotBlank`, `@Email`, etc.)
2. For complex validation, create custom `@Constraint` annotation in shared-library
3. Implement `ConstraintValidator` interface
4. Spring Boot auto-validates DTO before controller method executes
5. Validation errors return 400 with field-level error messages

### "I need to call another service"
1. Use `RestTemplate` or `WebClient` bean (configure in shared-library)
2. Handle `RestClientException` gracefully (will add Resilience4j retry logic in later phases)
3. Implement timeout and circuit breaker patterns (Phase 7+)
4. Eventually (Phase 8) replace with Kafka event-based communication

### "I need to add metrics/monitoring"
1. Use Micrometer annotations (`@Timed`, `@Counted`)
2. Expose metrics endpoint: `GET /actuator/metrics` (enabled in application.yml)
3. Phase 11 adds Prometheus scraping, Grafana dashboards, Zipkin tracing

---

## Project Conventions & Patterns to Follow

### Code Quality Standards
- **Format:** Spring Boot conventions (IntelliJ IDEA defaults OK)
- **Logging:** Use SLF4J (`private static final Logger log = LoggerFactory.getLogger(...)`)
- **Comments:** Document *why*, not *what*; code should be self-documenting
- **Error Handling:** Never swallow exceptions; always log or throw
- **Null Safety:** Use `Optional<T>` for JPA queries, avoid null checks
- **Transactions:** Apply `@Transactional` at service level, let Spring manage TX boundaries

### Commit Message Convention
```
{phase}-{component}: {description}
Examples:
- phase-2-customer-service: implement get customer by ID endpoint
- phase-5-order-service: add order validation logic
- shared: add custom email validator
```

### Documentation Requirements
- Each service has README.md explaining purpose, setup, API examples
- Architecture decisions documented in service README
- Database schema changes documented in migration comments
- Complex business logic documented with inline comments

### Phase Progression
- Phases build sequentially (must complete Phase 1 before Phase 2)
- Each phase is independently testable and deployable
- Phase transitions require: all tests passing, documentation complete, Docker image builds
- See `PHASE_QUICK_REFERENCE.md` for phase dependencies

---

## Key Files for Understanding the Codebase

### Architecture & Planning
- `PROJECT_OVERVIEW.md` - 14-phase strategy, big picture
- `PHASE_QUICK_REFERENCE.md` - One-page phase overview, dependencies
- `IMPLEMENTATION_PLAN.md` - Detailed technical requirements per phase
- `architecture/HLD.md` - High-level design (being populated as phases progress)

### Build & Configuration
- `pom.xml` (root) - Parent POM with dependency management
- `services/shared-library/pom.xml` - Common library
- Each service has its own pom.xml with inherited parent

### Foundation Code to Study
- `services/shared-library/src/main/java/com/enterprise/order/shared/exception/` - Exception hierarchy
- `services/shared-library/src/main/java/com/enterprise/order/shared/dto/BaseResponse.java` - Response wrapper
- `services/customer-service/src/main/java/com/enterprise/order/customer/` - Example service implementation
- `services/customer-service/src/main/resources/application.yml` - Configuration example

### Development Getting Started
- `PHASE_1_GETTING_STARTED.md` - Step-by-step Phase 1 setup
- `README.md` (root) - Quick start commands

---

## Debugging Tips

### Service Won't Start?
1. Check `application.yml` datasource URL points to correct PostgreSQL
2. Verify PostgreSQL running: `docker compose ps`
3. Run migrations: `mvn flyway:migrate -pl services/{service}`
4. Check Java 21 installed: `java -version`

### Test Failures?
1. Ensure TestContainers PostgreSQL image available: `docker pull postgres:15`
2. Check mock setup in @BeforeEach method
3. Verify shared-library builds: `mvn clean install -pl services/shared-library`

### Build Failures?
1. Clear cache: `mvn clean` before retrying
2. Update IDE annotation processors if using Lombok/MapStruct
3. Verify parent pom.xml dependencies defined (don't redeclare versions in child POMs)

### Swagger/OpenAPI Not Showing?
1. Service must include `springdoc-openapi-starter-webmvc-ui` dependency
2. Hit `http://localhost:{port}/swagger-ui.html`
3. Check application.yml enables springdoc

---

## When in Doubt, Reference These

1. **Existing Customer Service** - Most complete example of Phase 2
2. **Shared Library** - Source of truth for common patterns
3. **PROJECT_OVERVIEW.md** - High-level architecture
4. **PHASE_QUICK_REFERENCE.md** - Quick navigation and commands
5. **Spring Boot & Spring Cloud docs** - Official source for framework questions

---

## Red Flags / Anti-Patterns (Avoid These)

❌ **Direct database queries in controller** - Should be in service layer  
❌ **Catching and swallowing exceptions** - Log and rethrow or throw ApplicationException  
❌ **Hardcoded values** - Use application.yml configuration  
❌ **Null pointer checks everywhere** - Use Optional<T> or Objects.requireNonNull()  
❌ **Service-to-service HTTP calls during Phase 8+** - Should use Kafka events  
❌ **Missing @Transactional on service methods** - Ensures TX boundaries  
❌ **DTOs with direct @Entity references** - Always map, maintains separation of concerns  

---

## Phase 8: Event-Driven Saga Orchestration (NEW!)

### What's New in Phase 8

**Architecture:**
- **Kafka Event Bus** – Order, Inventory, Payment services communicate via Kafka topics
- **Saga Orchestrator** – Order Service coordinates multi-service workflows (Order → Inventory → Payment)
- **Outbox Pattern** – Guarantees exactly-once event delivery (DB + Kafka atomicity)
- **DLQ Handling** – Dead-letter topics for failed events with automatic retry + logging

**Key Components:**

| Component | Location | Purpose |
|-----------|----------|---------|
| **Outbox Pattern** | `shared-library/src/main/java/com/enterprise/order/shared/outbox/` | Transactional outbox entity, poller, publisher |
| **Event DTOs** | `shared-library/src/main/java/com/enterprise/order/shared/events/` | OrderCreatedEvent, InventoryReservedEvent, PaymentProcessedEvent |
| **Kafka Config** | `shared-library/src/main/java/com/enterprise/order/shared/config/KafkaConfig.java` | Topic beans (6 topics), consumer error handler, retry policy |
| **DLQ Handler** | `shared-library/src/main/java/com/enterprise/order/shared/messaging/DeadLetterQueueHandler.java` | Logs failed events from DLQ topics |
| **Saga Orchestrator** | `order-service/src/main/java/com/enterprise/order/order/saga/OrderSagaOrchestrator.java` | Listens to inventory-events & payment-events, updates order status |
| **Consumers** | `*/messaging/*EventConsumer.java` | OrderEventConsumer, PaymentEventConsumer, InventoryEventConsumer |

**Kafka Topics:**
- `order-events` – OrderCreatedEvent
- `inventory-events` – InventoryReservedEvent, InventoryReleasedEvent
- `payment-events` – PaymentProcessedEvent, PaymentRefundedEvent
- `*-dlq` – Dead-letter topics for each (auto-created, auto-consumed for logging)

**Saga Flow:**
```
1. Order created → OrderService.createOrder() stores OrderCreatedEvent in outbox
2. OutboxPoller publishes to order-events topic
3. InventoryService consumes OrderCreatedEvent, reserves stock, publishes InventoryReservedEvent
4. PaymentService consumes InventoryReservedEvent, processes payment, publishes PaymentProcessedEvent
5. OrderService saga consumer listens to payment-events, updates order status to CONFIRMED
6. On failure: compensation flows trigger (inventory release, refund)
```

**Idempotency:**
- Order consumer uses `orderId:productId` composite key
- Payment & inventory use database checks (double-payment prevention, duplicate reserve detection)
- Consumers marked with `@Transactional` to ensure atomic status updates

**Testing:**
- 13 passing unit + integration tests (EventSerializationTest, KafkaEventIntegrationTest)
- Test happy path (Order → Inventory → Payment → Confirm)
- Test failure path (payment failure → inventory compensation)
- Verify event formats for Kafka transmission

### When to Use Phase 8 Patterns

✅ **Event Producers** – Call `outboxPublisher.storeEvent()` inside `@Transactional` business logic  
✅ **Event Consumers** – Use `@KafkaListener` with groupId; implement idempotency checks  
✅ **Saga Coordination** – Update aggregate root status based on consumed events  
✅ **Error Handling** – Catch exceptions in listeners, log & route to DLQ (automatic via ErrorHandler)  
❌ **NOT** – Direct HTTP calls between services (use Kafka events instead, Phase 8+)

### Infrastructure Setup

```yaml
# docker-compose.yml includes:
services:
  kafka:
    image: confluentinc/cp-kafka:latest
    ports:
      - "9092:9092"
    environment:
      KAFKA_AUTO_CREATE_TOPICS_ENABLE: "false"
  
  schema-registry:
    image: confluentinc/cp-schema-registry:latest
    ports:
      - "8090:8081"   # host 8081 belongs to customer-service; registry stays 8081 inside the network
  
  kafka-ui:
    image: provectuslabs/kafka-ui:latest
    ports:
      - "8888:8080"
```

Topics auto-created at service startup via `KafkaConfig` bean (not auto-provisioned by Kafka).

### Troubleshooting Phase 8

**Issue:** Events not flowing through Kafka  
→ Check Kafka is running: `docker compose ps`  
→ Check topics exist: `docker exec kafka kafka-topics --bootstrap-server localhost:9092 --list`  
→ Check consumer logs for deserialization errors  

**Issue:** Duplicate processing  
→ Verify consumer is idempotent (check `OrderEventConsumer.handleOrderCreated()`)  
→ Check idempotency key format (orderId:productId)

**Issue:** DLQ pile-up  
→ Check service logs for the actual exception  
→ Manually replay from DLQ (ops task, manual via Kafka console)

---

## Phase 9: Shipping & Notification (NEW!)

### What's New in Phase 9

**Architecture:**
- **Shipping Service (:8086, schema `shipping`)** – consumes `payment-events`
  (COMPLETED → idempotent shipment), async request/reply packing-list exchange
  with Inventory, `PENDING → SHIPPED → DELIVERED`
- **Notification Service (:8087, schema `notification`)** – consumes
  order/payment/shipping events (group `notification-service-group`),
  simulated EMAIL/SMS with DB audit rows (unique per order+type+channel),
  publishes `NotificationSentEvent` via outbox
- **Saga completion** – `PAYMENT_APPROVED → SHIPPED → COMPLETED`

**Key Components:**

| Component | Location | Purpose |
|-----------|----------|---------|
| **PaymentEventConsumer** | `shipping-service/.../messaging/PaymentEventConsumer.java` | COMPLETED payment → create shipment |
| **PackingListReplyConsumer** | `shipping-service/.../messaging/PackingListReplyConsumer.java` | Inventory reply → tracking number, SHIPPED |
| **PackingListRequestConsumer** | `inventory-service/.../messaging/PackingListRequestConsumer.java` | Replies with packing list (outbox, idempotent) |
| **NotificationEventListener** | `notification-service/.../messaging/NotificationEventListener.java` | Event→notification mapping, header dispatch |
| **Saga shipping listener** | `order-service/.../saga/OrderSagaOrchestrator.java` | `eventType` header → SHIPPED / COMPLETED |

**New Kafka Topics (8):** `shipping-events` (+dlq), `notification-events` (+dlq),
`inventory-shipping-request-events` (+dlq), `inventory-shipping-reply-events` (+dlq).
`shipping-events` carries two event types — consumers dispatch on the `eventType`
header now written by `OutboxPublisher`. Kafka message key = orderId everywhere.

**Event-to-Notification Mapping:**

| Event | Type | Channels |
|---|---|---|
| OrderCreated | ORDER_CONFIRMED | EMAIL |
| PaymentProcessed (COMPLETED) | PAYMENT_RECEIVED | EMAIL |
| ShipmentCreated | SHIPPED | EMAIL + SMS |
| ShipmentDelivered | DELIVERED | EMAIL + SMS |

### Phase 9 Gotchas (Learned During Validation)

❌ **Do not use `MappingConstants.ComponentModel.SPRING`** in `@Mapper` —
with this repo's Lombok/MapStruct processor setup it produced mapper impls
whose bean registration silently failed at runtime ("No qualifying bean of
type ...Mapper", service won't start). Always use the literal:
`@Mapper(componentModel = "spring")`.

❌ **SHIPPED is not terminal** — the order state machine allows
`SHIPPED → COMPLETED` only; `TERMINAL_STATUSES` is
`{CANCELLED, FAILED, COMPLETED}` (regression tests guard this).

✅ **Docker healthchecks** – kafka CLI probe needs `timeout: 20s`,
schema-registry needs `start_period: 150s`+ under cold-start load, service
Dockerfile HEALTHCHECKs carry `--start-period=180s` (JVMs take ~120s).
Keep these windows when adding services.

✅ **Test class file names must match class names** – Surefire includes are
`**/*Test.java`, `**/*Tests.java`, `**/*IT.java`; repo convention is
`XxxIT.java` containing `class XxxIT` (see OrderServiceIT, CustomerServiceIT).

### Validation (Aug 14, 2026)

- `mvn clean install` green — 137 tests, 0 failures (11 modules)
- `docker compose up -d --build` — all containers healthy
- E2E via gateway: order auto-flows to SHIPPED (~20s), deliver endpoint →
  COMPLETED; all 4 notification types recorded with correct channels

---

## Phase 10: Analytics & Reporting (NEW!)

### What's New in Phase 10

**Architecture:**
- **Analytics Service (:8088, schema `analytics`)** – the platform's first
  pure read-model consumer: never joins the order saga or publishes events,
  only aggregates `order-events`/`payment-events`/`shipping-events` (group
  `analytics-service-group`) into queryable business metrics
- **Fact-table + full-recompute pattern** – every event first upserts a
  unique-constrained fact row (`order_facts`, `order_item_facts`,
  `order_revenue`), then the affected `daily_metrics`/`product_metrics`/
  `fulfillment_metrics` rollups are **recomputed from facts**, never
  incremented — at-least-once Kafka redelivery can never double-count
- **Reconciliation sweep** – `MetricsReconciliationJob` (`@Scheduled`, 10s
  default interval, 3-day lookback, both configurable) periodically
  recomputes recent rollups, healing the race where two parallel consumer
  transactions each miss the other's uncommitted writes
- **Report API** – `/api/v1/analytics/{daily-metrics,product-metrics,revenue,
  customer-metrics,fulfillment-metrics,summary}`, all `BaseResponse`-wrapped,
  date-range validated (`from <= to`, max 366 days)

**Key Components:**

| Component | Location | Purpose |
|-----------|----------|---------|
| **OrderEventConsumer** | `analytics-service/.../messaging/OrderEventConsumer.java` | Upserts order/item facts, recomputes daily+product rollups |
| **PaymentEventConsumer** | `analytics-service/.../messaging/PaymentEventConsumer.java` | COMPLETED→revenue row+rollup; FAILED→failed-order rollup; REFUNDED→revenue adjustment |
| **ShippingEventConsumer** | `analytics-service/.../messaging/ShippingEventConsumer.java` | `eventType` header dispatch → fulfillment timings |
| **MetricsAggregationService** | `analytics-service/.../service/MetricsAggregationService.java` | Orchestrates facts→recompute; `reconcileRecentMetrics(...)` |
| **MetricsReconciliationJob** | `analytics-service/.../service/MetricsReconciliationJob.java` | Scheduled sweep calling the above |
| **AnalyticsReportService** | `analytics-service/.../service/AnalyticsReportService.java` | Range queries, sorting, top-N, lifetime summary |

**No new Kafka topics** — Phase 10 only consumes the existing 4 primary
topics from Phases 8–9; no event schema changes.

**Product metrics are ID-only** (no `productName`) — `OrderCreatedEvent.OrderItem`
doesn't carry a name and analytics-service makes no synchronous HTTP calls to
other services by design (event-sourced only). Deferred to the Phase 13 UI to
enrich via product-service.

### Phase 10 Gotchas (Learned During Implementation & Validation)

❌ **Never increment aggregate counters from events** — anchor every event in
a unique-constrained fact table, then recompute rollups from facts. Same
lesson as Phase 9's outbox idempotency, applied to read-side aggregation.

❌ **Spring Data JPA aggregate JPQL `SELECT`s always return `List<Object[]>`**,
even for a guaranteed single row — declaring the repository method to return
a bare `Object[]` produces a nested-array `ClassCastException` at runtime.
Invisible to unit tests that mock the repository; only surfaces under a real
query. Always declare `List<Object[]>` for aggregate queries.

❌ **SQL `SUM(CASE...)` over zero matching rows is `NULL`, not `0`** — wrap
every conditional `SUM` in `COALESCE(..., 0)` unless the column should
legitimately render as JSON `null` (e.g. an `AVG` with no contributing rows).

❌ **`localhost:9092` vs `localhost:9094`** — a JVM running directly on the
host (not in a container) must use `localhost:9094`
(`KAFKA_ADVERTISED_LISTENERS` → `PLAINTEXT_HOST`) to reach the dev Kafka
broker; `localhost:9092` only works from inside the compose network. Silent
failure mode: the consumer just never receives anything, no error.

❌ **Creating a product via product-service does not provision an
inventory-service record** — an order against a freshly-created product
fails reservation (`Inventory not found with identifier: N`) and sticks at
`PENDING`. Pre-existing platform gap (inventory rows are seeded
independently), not Phase 10 scope — E2E scripts must use a product with a
seeded inventory row (id 1 or 2) or call `/api/v1/inventory/adjust` first.

✅ **Gateway route wiring for a new service touches 5 places**: both
`application.yml`/`application-docker.yml` route lists (Spring profile merge
*replaces* list properties, so the docker profile re-declares the full
route list), the resilience4j `circuitbreaker`/`timelimiter` instance maps
(both), the springdoc `swagger-ui.urls` list, and `FallbackController`.
Miss one and the failure mode is silent (route just doesn't exist) rather
than a build error.

### Validation (Aug 24, 2026)

- `mvn clean install` green — 181 tests, 0 failures (11 modules, 44 new for
  analytics-service)
- `docker compose up -d --build` — all 14 containers healthy, including analytics-service
- E2E via gateway: a real order (product with seeded inventory) flowed
  `PENDING → SHIPPED → DELIVERED`; at every stage the corresponding analytics
  endpoint reflected the change (daily-metrics on order creation, revenue on
  payment COMPLETED, fulfillment-metrics on shipped and again on delivered)

---

## Phase 11: Observability (NEW!)

### What's New in Phase 11

**Architecture:** cross-cutting instrumentation added to all 10 running
services (gateway + 9 microservices) — no new business logic, no new Kafka
topics, no new DB tables. Three new infra containers: `zipkin` (:9411),
`prometheus` (:9090), `grafana` (:3000, default login `admin`/`admin`).

**Key Components:**

| Component | Location | Purpose |
|-----------|----------|---------|
| **`logback-json-base.xml`** | `shared-library/src/main/resources/` | Shared JSON log appender (`LogstashEncoder`), `<include>`d by every servlet service's own `logback-spring.xml`; gateway carries a standalone copy (reactive, can't depend on shared-library) |
| **`KafkaHealthIndicator`** | `shared-library/.../health/` | Spring Boot has no built-in Kafka health contributor (verified against the actual autoconfigure jar) — reuses the existing `KafkaAdmin` bean |
| **`order.creation.duration`** | `OrderService.createOrder()` | Micrometer `Timer`, `publishPercentiles(0.5, 0.95, 0.99)` |
| **`payment.result`** | `PaymentService.process()` | Micrometer `Counter` tagged `outcome=COMPLETED\|FAILED`; success rate computed in Grafana via PromQL, not in-app |
| **`observability/`** | repo root | `prometheus.yml` (9 scrape targets), `grafana/provisioning/` (datasource + dashboard auto-provisioning), `grafana/dashboards/*.json` (Platform Overview, Kafka Consumers, Business KPIs — hand-authored against real metric names, not imported) |

**Per-service config (`application.yml`, all 10 services):**
`management.endpoints.web.exposure.include` gains `prometheus`;
`management.metrics.tags.application`; `management.tracing.sampling.probability: 1.0`;
`management.zipkin.tracing.endpoint` (Spring Boot 3 property —
IMPLEMENTATION_PLAN.md's example used the Boot-2-era `spring.zipkin.base-url`);
`management.endpoint.health.probes.enabled: true`;
`spring.kafka.template.observation-enabled` / `listener.observation-enabled`
(the 8 servlet services — correct and harmless, but see the outbox gap below).

**Kafka metrics (including consumer lag) required zero code** — Spring
Boot's `KafkaMetricsAutoConfiguration` binds Micrometer's `KafkaClientMetrics`
to the autoconfigured `ConsumerFactory`/`ProducerFactory` beans automatically
once `micrometer-registry-prometheus` is on the classpath, since
`KafkaConfig` doesn't define custom factory beans.

### Phase 11 Gotchas (Learned During Implementation)

❌ **A Spring profile's scalar property fully overrides the base value, not
merges with it.** Gateway's `application-docker.yml` redeclares
`management.endpoints.web.exposure.include` (route list is `docker` profile
only for lists — but this is a scalar string property, same silent-drop risk
as the list case). Missing `prometheus` there would mean
`/actuator/prometheus` works locally but silently vanishes only in Docker.

❌ **Kafka-hop trace propagation is architecturally absent, not fixable by
config.** The Phase 8 transactional outbox pattern writes the event to a
table inside the original request's transaction, then a separate
`@Scheduled` `OutboxPoller` publishes it seconds later on an unrelated
thread — there is no trace context to propagate from at publish time no
matter what tracing properties are set. Confirmed directly in Zipkin: every
outbox-poller span is a root span with no parent. A real fix means storing
`traceId`/`spanId` on the outbox row and manually restoring that span
context in `OutboxPublisher.publishEvent()` — deferred, not done in Phase 11.
The existing `X-Correlation-Id` (business-level, already flows through Kafka
headers) remains the way to correlate a saga's logs across services.

❌ **Never rebuild many Spring Boot service images concurrently on a
resource-constrained Docker Desktop.** `docker compose up -d --build` across
8 services at once crashed the Docker Desktop engine (`docker version`
itself returned a `500` from the daemon — confirmed broken, not just slow).
Rebuilding sequentially (`docker compose build <service>` one at a time)
avoided it.

✅ **Neither `kafka` nor `zookeeper` has a data volume** — if either gets
into a bad state (e.g. a stale ZooKeeper `NodeExistsException` broker
registration after an unclean shutdown), `docker compose rm -f zookeeper
kafka` + `up -d` recreates them cleanly with no data loss to worry about.

### Validation (Aug 24, 2026)

- `docker compose up -d --build` — all 18 containers healthy (the 9
  services + gateway, postgres/redis/kafka/zookeeper/schema-registry/kafka-ui,
  and the 3 new observability containers)
- Prometheus: all 9 app-service scrape targets `up`; custom metrics
  (`order_creation_duration_seconds_count`, `payment_result_total`,
  `kafka_consumer_fetch_manager_records_lag`) confirmed live with real data
- Zipkin: all 9 services registered; a real order creation through the
  gateway produced one correctly-nested 3-span trace for the gateway→
  order-service HTTP hop (Kafka-hop limitation documented above)
- Grafana: provisioned datasource + all 3 dashboards confirmed via API,
  confirmed reachable to Prometheus with live data
- Full platform-wide `mvn clean install` deferred to Phase 12 (see
  `PHASE_11_COMPLETE.md` for the known-flaky `CustomerServiceIT` note);
  per-module test runs during implementation were all green

---

**Last Updated:** August 24, 2026
**For Phase:** Phase 11 complete (Observability — JSON logging, Prometheus metrics, Grafana dashboards, Zipkin tracing, health probes, all 10 services)
**Next Phase:** Security (Phase 12) — JWT authentication, role-based access control