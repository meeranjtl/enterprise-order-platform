# Domain Rules & Non-Negotiable Invariants

Read this before modifying cross-cutting behavior, exception handling, event publishing, or service-to-service communication. These rules are enforced by convention, not by the compiler — breaking them tends to fail silently. See [architecture.md](architecture.md) for topology and [gotchas.md](gotchas.md) for the specific incidents that produced several of these rules.

## Shared library first

All services depend on `shared-library` for common code. When adding a cross-cutting concern (exception, DTO, validator, config), add it to `shared-library` first, rebuild (`mvn clean install -pl services/shared-library`), then consume from services. **Exception: gateway** — it is reactive (Netty) and must never depend on the servlet-based `shared-library`; it carries standalone copies of anything it needs (e.g. JSON logging config).

## Exceptions

- Never throw generic `Exception`. Throw/extend `com.enterprise.order.shared.exception.ApplicationException`.
- `GlobalExceptionHandler` (`@RestControllerAdvice` in shared-library) converts all application exceptions to RFC 7807 Problem Details automatically. Don't hand-roll error responses in controllers.
- Never swallow exceptions — log or throw.

## Service communication

- **Before Phase 8:** synchronous HTTP (`RestTemplate`/`WebClient`) was acceptable.
- **Phase 8 onward: use Kafka events, not direct HTTP calls, between order/inventory/payment/shipping/notification/analytics.** This is enforced by convention only — a new synchronous call between these services is a regression, not a valid shortcut.
- Event producers: call `outboxPublisher.storeEvent()` inside the same `@Transactional` business logic that makes the state change — never publish to Kafka directly, use the outbox (transactional guarantee).
- Event consumers: use `@KafkaListener` with an explicit `groupId`; **must be idempotent** (see below). Mark consumer methods `@Transactional` for atomic status updates. Failures route to DLQ automatically via the configured `ErrorHandler`.

## Idempotency (required on every consumer)

- Order consumer: `orderId:productId` composite key.
- Payment/inventory: DB existence checks (double-payment prevention, duplicate reserve detection).
- Analytics (Phase 10 pattern — applies to any future read-model aggregation): **never increment a counter directly from an event.** Anchor every event in a unique-constrained fact table first, then recompute the affected rollup *from facts*, never by incrementing. At-least-once Kafka redelivery can otherwise double-count silently.

## Order state machine

`TERMINAL_STATUSES = {CANCELLED, FAILED, COMPLETED}`. **`SHIPPED` is not terminal** — the only valid transition out of it is `SHIPPED → COMPLETED`. Regression tests guard this; don't add a transition that treats `SHIPPED` as final.

## Notification mapping (event → notification)

| Event | Type | Channels |
|---|---|---|
| OrderCreated | ORDER_CONFIRMED | EMAIL |
| PaymentProcessed (COMPLETED) | PAYMENT_RECEIVED | EMAIL |
| ShipmentCreated | SHIPPED | EMAIL + SMS |
| ShipmentDelivered | DELIVERED | EMAIL + SMS |

## Database

- `hibernate.ddl-auto: validate` always — schema changes go through Flyway migrations (`V{N}__{description}.sql`), never `create`/`update`.
- New field: add to `@Entity` + migration script + `{Resource}DTO` + MapStruct mapper if mapped.

## API / DTO conventions

- Controllers wrap responses in `BaseResponse<T>` (`BaseResponse.success(...)`).
- DTOs never expose `@Entity` types directly — always map via MapStruct (`@Mapper(componentModel = "spring")` — see [gotchas.md](gotchas.md) for why the `MappingConstants` form breaks).
- Validation: JSR-303 annotations (`@NotBlank`, `@Email`) on DTOs; custom cross-field rules as `ConstraintValidator` in shared-library.
- `@Transactional` at the service method level, not controller.

## Anti-patterns (reject in review)

- Direct database queries in a controller (belongs in service layer).
- Catching and swallowing exceptions.
- Hardcoded config values instead of `application.yml`.
- Manual null checks instead of `Optional<T>`.
- Synchronous service-to-service HTTP calls for anything Phase 8+ should route through Kafka.
- Missing `@Transactional` on a service method that changes state.
- DTOs with direct `@Entity` references.

## Gateway route wiring (adding/changing a route)

Touches **5 places**, and missing one fails silently (route just doesn't exist, no build error) rather than erroring:
1. `application.yml` route list
2. `application-docker.yml` route list — the docker Spring profile *replaces* list properties, it does not merge, so the full route list must be redeclared here too
3. Resilience4j `circuitbreaker` instance map
4. Resilience4j `timelimiter` instance map
5. springdoc `swagger-ui.urls` list, and `FallbackController`
