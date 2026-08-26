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

## Authentication & Authorization (Phase 12)

- customer-service is the platform's **only** JWT issuer. Every other service — including the gateway — validates tokens independently as a resource server (shared-library's `JwtDecoderConfig`/`SecurityConfig`, or the gateway's own duplicated reactive equivalent); authorization never requires a synchronous call back to customer-service.
- New JWTs **must** be signed with an explicitly-pinned algorithm (`Jwts.SIG.HS256`) — `signWith(key)` alone silently picks a different HMAC variant for a long secret and breaks every decoder in the platform. See [gotchas.md](gotchas.md#phase-12--security).
- `/api/auth/register` must never accept a client-supplied `role` — always force `CUSTOMER`. Only the seeded admin (or a manual DB change) gets `ADMIN`.
- Role checks are `@PreAuthorize`, not filter-chain `authorizeHttpRequests()`/`authorizeExchange()` rules — the filter chain only enforces "authenticated or not" plus the public-path allowlist. When adding a new controller endpoint, decide its `@PreAuthorize` the same way the existing ones are decided: reads that don't expose another customer's data need no annotation (authenticated is enough); anything that mutates state, or reads across customers, needs `hasRole('ADMIN')` or an ownership check (`#id.toString() == authentication.name` for a path variable, or a security-bean method like `OrderSecurity.isOwner(...)` when the owning id isn't in the path).
- `GlobalExceptionHandler` (shared-library) must have a dedicated `@ExceptionHandler(AccessDeniedException.class)` — without it, every `@PreAuthorize` denial is caught by the generic `Exception.class` handler and returns 500 instead of 403. See [gotchas.md](gotchas.md#phase-12--security).
- Adding a public (pre-auth) endpoint means updating the `PUBLIC_PATHS`/permitted-matcher list in **both** shared-library's `SecurityConfig` (servlet services) **and** the gateway's own `SecurityConfig` (reactive) — they don't share config, same as every other reactive/servlet-boundary concern.
- Don't verify RBAC with a `@WebMvcTest` slice test expecting a 403 — it doesn't reliably exercise `@EnableMethodSecurity`'s AOP proxying in this codebase. Verify authorization behavior via a full-context test or live E2E check instead. See [gotchas.md](gotchas.md#phase-12--security).
- `OPTIONS` (CORS preflight) must always be permitted pre-auth in gateway's `SecurityConfig` (`.pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()`) — a browser's preflight never carries `Authorization`, so `.anyExchange().authenticated()` 401s it before `globalcors` ever answers with `Access-Control-Allow-*` headers, breaking every protected route for real browser clients. `curl`/Postman never trigger a preflight, so this doesn't show up outside an actual browser. See [gotchas.md](gotchas.md#phase-13--react-ui).
- order-service's two internal synchronous calls (`CustomerClient`, `ProductClient` — the one deliberate pre-Phase-8 exception to the Kafka-only rule above, kept for immediate-consistency validation at order-creation time) must forward the inbound request's `Authorization` header. They don't get one for free just because the original request was authenticated — every outbound `RestClient`/`WebClient` call to another JWT-protected service needs it added explicitly. See [gotchas.md](gotchas.md#phase-13--react-ui).
- Gateway's CORS policy lives in exactly one place: the `CorsWebFilter` bean in `CorsConfig.java`, covering `/**` for the whole app (both gateway-routed paths and any local `@RestController`, e.g. `SystemHealthController`). Never re-add `spring.cloud.gateway.globalcors` alongside it — Spring Cloud Gateway's own CORS mechanism only covers routed paths, and running it next to a `CorsWebFilter` bean double-applies `Access-Control-Allow-*` headers (browsers reject the response). See [gotchas.md](gotchas.md#phase-13--react-ui).

## Gateway route wiring (adding/changing a route)

Touches **5 places**, and missing one fails silently (route just doesn't exist, no build error) rather than erroring:
1. `application.yml` route list
2. `application-docker.yml` route list — the docker Spring profile *replaces* list properties, it does not merge, so the full route list must be redeclared here too
3. Resilience4j `circuitbreaker` instance map
4. Resilience4j `timelimiter` instance map
5. springdoc `swagger-ui.urls` list, and `FallbackController`
