# Phase 4 - API Gateway & Routing - COMPLETED

## Overview

Phase 4 implements the reactive API Gateway as the platform's single client entry point. It routes requests to downstream services, propagates correlation IDs for end-to-end tracing, applies gateway-level resilience and rate limiting, and aggregates service OpenAPI documentation.

## Deliverables Completed

- Reactive Spring Cloud Gateway Spring Boot module running on port `8080`
- Declarative routing for Customer Service and Product Service
- Correlation ID generation and propagation through the `X-Correlation-Id` header
- Gateway request logging with correlation IDs, request paths, status, and duration
- Redis-backed, IP-based token-bucket rate limiting
- Consistent gateway-generated `429` response body and `Retry-After` header
- Resilience4j circuit breakers with downstream-service fallback responses
- Consistent gateway error envelopes for route-not-found, rate-limit, service-unavailable, circuit-breaker, and unexpected-error cases
- Centralized CORS configuration for future browser clients
- Aggregated Swagger/OpenAPI UI for downstream services
- Actuator health, metrics, and live gateway-route endpoints
- Dockerfile and Docker Compose routing support
- Unit tests for filters, error handling, and the rate-limit key resolver
- WireMock and Testcontainers integration tests for gateway routing

## Verification

Command:

```powershell
mvn verify -pl services/gateway
```

Expected behavior:

- Gateway unit tests run in all environments.
- Gateway routing integration tests run when Docker is available.
- Redis-backed Testcontainers integration tests are skipped when Docker is unavailable.
- Requests to downstream services receive or preserve an `X-Correlation-Id` response header.

## Main Routes

- `GET|POST|PUT|PATCH|DELETE /api/v1/customers/**` → Customer Service (`:8081`)
- `GET|POST|PUT|PATCH|DELETE /api/v1/products/**` → Product Service (`:8082`)
- `GET /customer-service/api-docs` → Customer Service OpenAPI document
- `GET /product-service/api-docs` → Product Service OpenAPI document
- `GET /swagger-ui.html` → Aggregated Swagger UI
- `GET /actuator/gateway/routes` → Live gateway route definitions

## Next Phase

Phase 5 should implement Order Service, route it through the gateway, and validate customer and product data during order creation.
