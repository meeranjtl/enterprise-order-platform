# API Gateway - Phase 4 Complete

Spring Cloud Gateway as the single entry point for the Enterprise Order Platform: routing, correlation-ID propagation, request logging, Redis-backed rate limiting, circuit breakers, and aggregated Swagger UI.

## Overview

- **Routing** — declarative routes for customer-service (:8081) and product-service (:8082), with commented placeholders for future services
- **Correlation ID** — `X-Correlation-Id` generated or propagated on every request; echoed on the response; logged in gateway *and* downstream services
- **Rate limiting** — Redis-backed token bucket (default 10 req/s, burst 20 per client IP); 429 responses carry the standard JSON error envelope + `Retry-After`
- **Circuit breakers** — Resilience4j per route; downstream failures forward to a fallback returning 503 `SERVICE_UNAVAILABLE`
- **Consistent errors** — gateway-originated errors (404/429/503/500) use the same `BaseResponse` JSON shape as the services; downstream 4xx/5xx pass through untouched
- **Swagger aggregation** — `http://localhost:8080/swagger-ui.html` lists both services' OpenAPI docs
- **CORS** — centralized for browser clients (React UI in Phase 13)

**Status:** ✅ Phase 4 Complete

## Architecture

```
Client
  ↓
API Gateway (:8080, Netty/reactive)
  ├── CorrelationIdFilter        (order: first — generates/propagates X-Correlation-Id)
  ├── RequestLoggingFilter       (method, path, status, duration)
  ├── RateLimitResponseFilter    (consistent 429 JSON body)
  ├── RequestRateLimiter         (default filter — Redis token bucket, IP key)
  ├── CircuitBreaker             (per route, fallback on failure)
  ↓
customer-service :8081  |  product-service :8082
```

### Directory Structure

```
gateway/
├── src/main/java/com/enterprise/order/gateway/
│   ├── config/
│   │   └── RateLimiterConfig.java          # ipKeyResolver (X-Forwarded-For aware)
│   ├── controller/
│   │   └── FallbackController.java         # circuit-breaker fallbacks (503)
│   ├── dto/
│   │   └── GatewayErrorResponse.java       # BaseResponse-shaped error envelope
│   ├── exception/
│   │   └── GatewayExceptionHandler.java    # ErrorWebExceptionHandler (@Order(-2))
│   ├── filter/
│   │   ├── CorrelationIdFilter.java        # GlobalFilter
│   │   ├── RequestLoggingFilter.java       # GlobalFilter
│   │   └── RateLimitResponseFilter.java    # GlobalFilter (429 body)
│   └── GatewayApplication.java
├── src/main/resources/
│   ├── application.yml                     # routes, CORS, rate limit, resilience4j
│   ├── application-docker.yml              # container-hostname route URIs (docker profile)
│   └── logback-spring.xml                  # [corr=%X{correlationId}] log pattern
├── src/test/java/                          # unit tests + WireMock/Testcontainers IT
├── pom.xml
├── Dockerfile                              # multi-stage build
└── README.md
```

> **Reactive constraint:** this service is WebFlux/Netty. Never add `shared-library` or
> `spring-boot-starter-web` to its classpath — servlet dependencies break the gateway.
> `GatewayErrorResponse` mirrors shared-library's `BaseResponse` JSON contract by field naming.

## Routes

| Route ID | Predicate | Destination (local / docker) | Circuit breaker |
|---|---|---|---|
| customer-service | `/api/v1/customers/**` | `http://localhost:8081` / `http://customer-service:8081` | `customerService` → `/fallback/customer-service` |
| product-service | `/api/v1/products/**` | `http://localhost:8082` / `http://product-service:8082` | `productService` → `/fallback/product-service` |
| customer-service-api-docs | `/customer-service/api-docs` | rewritten to `/api-docs` | — |
| product-service-api-docs | `/product-service/api-docs` | rewritten to `/api-docs` | — |

The docker profile (`SPRING_PROFILES_ACTIVE=docker`, set by docker-compose) re-declares the **full** route list with container hostnames — Spring Boot replaces list properties on profile merge rather than merging them.

## Error Handling

Errors generated **by the gateway** (not downstream services) return the standard envelope:

| Scenario | Status | error.code |
|---|---|---|
| No matching route | 404 | `ROUTE_NOT_FOUND` |
| Rate limit exceeded | 429 | `RATE_LIMIT_EXCEEDED` (+ `Retry-After`) |
| Downstream failure/timeout (fallback) | 503 | `SERVICE_UNAVAILABLE` |
| Circuit breaker open | 503 | `CIRCUIT_BREAKER_OPEN` |
| Unexpected gateway error | 500 | `GATEWAY_ERROR` |

```json
{
  "success": false,
  "error": { "code": "RATE_LIMIT_EXCEEDED", "message": "Too many requests", "details": "..." },
  "timestamp": "2026-07-25T20:55:14.623"
}
```

## Observability

| Endpoint | Description |
|---|---|
| `GET /actuator/health` | Health (includes circuit breaker indicators) |
| `GET /actuator/gateway/routes` | Live route definitions and filters |
| `GET /actuator/metrics` | Micrometer metrics |
| `GET /swagger-ui.html` | Aggregated Swagger UI (both services) |

Every gateway log line carries `[corr=<correlation-id>]`. Downstream services pick the ID up via shared-library's `CorrelationIdLoggingFilter` (servlet MDC), so the same ID appears across gateway → service logs.

## Building & Running

### Prerequisites

- Java 21+, Maven 3.8+
- **Redis** (rate limiting) — `docker run -p 6379:6379 redis:7-alpine` or `docker compose up -d redis`
- customer-service (:8081) and product-service (:8082) for routing

### Local Development

```bash
mvn clean install -pl services/gateway
mvn spring-boot:run -pl services/gateway
```

### Docker Compose (full stack)

```bash
docker compose up --build
# Gateway at http://localhost:8080 (docker profile, container hostnames)
```

## Quick Verification

```bash
# Routing
curl http://localhost:8080/api/v1/customers

# Correlation ID: echoed when sent, generated when absent
curl -i -H "X-Correlation-Id: test-123" http://localhost:8080/api/v1/customers

# 404 envelope
curl http://localhost:8080/api/v1/unknown

# Rate limiting (fast burst — a slow loop lets the bucket replenish)
urls=$(for i in $(seq 1 40); do printf 'http://localhost:8080/api/v1/customers '; done)
curl -s -o /dev/null -w '%{http_code}\n' $urls | sort | uniq -c

# Fallback (stop customer-service first)
curl http://localhost:8080/api/v1/customers/1    # -> 503 SERVICE_UNAVAILABLE

# Live routes
curl http://localhost:8080/actuator/gateway/routes
```

## Testing

| Suite | Command | Notes |
|---|---|---|
| Unit (18 tests) | `mvn test -pl services/gateway` | filters, exception handler, key resolver |
| Integration (7 tests) | `mvn verify -pl services/gateway` | WireMock stubs + Testcontainers Redis; skipped without Docker |

## Configuration Tunables

| Setting | Where | Default |
|---|---|---|
| Rate limit | `default-filters.RequestRateLimiter` args | 10/s, burst 20 |
| Per-endpoint limits | add `RequestRateLimiter` filter args to an individual route | overrides default |
| Circuit breaker | `resilience4j.circuitbreaker.configs.default` | 50% failure, window 10, open 30s |
| Downstream timeout | `resilience4j.timelimiter.configs.default` | 10s (framework default is 1s — overridden deliberately) |
| CORS origins | `spring.cloud.gateway.globalcors` | `http://localhost:*` |

## Deferred (future phases)

- Per-user rate limiting (JWT KeyResolver — Phase 12)
- Response-body logging (needs stream buffering)
- Redis fail-open hardening (Phase 10+)
- Service discovery (direct URL mapping chosen instead)

---

**Phase:** 4 - API Gateway & Routing
**Status:** ✅ COMPLETE
**Last Updated:** July 25, 2026
**Tests:** 25 (18 unit + 7 integration)
