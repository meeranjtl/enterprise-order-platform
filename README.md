# Enterprise Order Platform

A hands-on portfolio project simulating a real-world, enterprise-grade e-commerce order processing system — built to practice and demonstrate modern microservices architecture, operational excellence, and engineering maturity using **Java 21** and **Spring Boot 3**.

This isn't a tutorial clone. It's built the way a production platform would be: with a shared library, correlation-ID tracing across services, a reactive API gateway, and a phased delivery plan that mirrors how real engineering teams incrementally ship enterprise systems.

## Why this project exists

After 20+ years building enterprise Java systems in client environments, this project is my own sandbox for:
- Applying event-driven architecture and microservices patterns end-to-end, from a clean slate
- Practicing modern tooling (Java 21, Spring Boot 3, Spring Cloud Gateway, Resilience4j) outside a legacy codebase
- Evaluating **AI-assisted development workflows** — using GitHub Copilot, Claude Code, and Codex throughout design, scaffolding, and code review — against the standard of enterprise-grade engineering discipline

## Architecture at a glance

- **Maven multi-module** project: 9 services + 1 shared library, plus a standalone React UI (own `package.json`, not a Maven module)
- **Event-driven core**: Apache Kafka for cross-service messaging, via a transactional outbox on every producer (Phase 8) — see [`docs/saga.md`](docs/saga.md) for the order-fulfillment saga's full state machine and compensating transactions, and [`docs/patterns.md`](docs/patterns.md) for the CQRS (analytics-service) and event-sourcing-adjacent (outbox) patterns in use
- **Reactive API Gateway**: built on Spring Cloud Gateway (Netty/WebFlux) — intentionally kept dependency-free from the servlet-based shared library; per-route circuit breakers (Resilience4j)
- **JWT auth + RBAC** (Phase 12): customer-service is the platform's sole issuer; every service validates independently as a resource server; `CUSTOMER`/`ADMIN` roles enforced via `@PreAuthorize`
- **Correlation-ID tracing**: the gateway generates and propagates an `X-Correlation-Id` header on every request; downstream servlet-based services pick it up via the shared library's `CorrelationIdLoggingFilter` and attach it to their logging MDC, giving end-to-end request tracing across the platform
- **Observability** (Phase 11): structured JSON logging, Prometheus metrics, Zipkin distributed tracing, Grafana dashboards
- **All client traffic routes through the gateway** (`:8080`) — no service is called directly from outside the platform

```
React UI (:3000) → API Gateway (:8080, reactive)
                          ├── Customer Service (:8081)     ├── Payment Service (:8085)
                          ├── Product Service (:8082)      ├── Shipping Service (:8086)
                          ├── Order Service (:8083)        ├── Notification Service (:8087)
                          └── Inventory Service (:8084)    └── Analytics Service (:8088, CQRS read model)
                                        ↓
                    Kafka (event bus) — decouples every service above
                                        ↓
                    PostgreSQL (schema-per-service), Redis (rate limiting)
                                        ↓
        Prometheus + Grafana + Zipkin (observability stack)
```

## Tech stack

| Layer | Technology |
|---|---|
| Language / Runtime | Java 21 |
| Backend framework | Spring Boot 3, Spring Cloud Gateway |
| Frontend | React 19 + Vite + TypeScript, TanStack Query, React Hook Form + Zod, Tailwind CSS + shadcn/ui |
| Messaging | Apache Kafka (transactional outbox on every producer) |
| Database | PostgreSQL (schema-per-service), Redis (gateway rate limiting) |
| Auth | JWT (access + refresh), role-based access control |
| Resilience | Resilience4j (circuit breakers, retries — gateway routes and order-service's internal calls) |
| Observability | Structured JSON logs, Micrometer/Prometheus, Zipkin, Grafana |
| Containerization | Docker Compose (13 infra/observability containers + 9 services + gateway + UI) |
| Build | Maven (multi-module) for the backend, npm/Vite for the UI |

## Project status

**All 14 phases complete.**

| Phase | Scope | Status |
|---|---|---|
| 1 | Foundation — multi-module scaffold, shared library, Docker setup | ✅ Complete |
| 2 | Customer Service (`:8081`) | ✅ Complete |
| 3 | Product Service (`:8082`) | ✅ Complete |
| 4 | API Gateway (`:8080`, reactive) | ✅ Complete |
| 5 | Order Service (`:8083`) | ✅ Complete |
| 6 | Inventory Service (`:8084`) | ✅ Complete |
| 7 | Payment Service (`:8085`), resilience patterns | ✅ Complete |
| 8 | Event-driven architecture — Kafka, transactional outbox | ✅ Complete |
| 9 | Shipping & Notification Services (`:8086`, `:8087`) | ✅ Complete |
| 10 | Analytics Service (`:8088`) — CQRS read model | ✅ Complete |
| 11 | Observability — structured logging, Prometheus, Zipkin, Grafana | ✅ Complete |
| 12 | Security — JWT auth, RBAC | ✅ Complete |
| 13 | React UI (`:3000`) | ✅ Complete |
| 14 | Docker orchestration, circuit breakers on internal calls, saga/CQRS/event-sourcing documentation, Postman collection, CI | ✅ Complete |

See [`AGENTS.md`](./AGENTS.md) and the individual `PHASE_N_COMPLETE.md` files for the full delivery narrative and validation evidence per phase.

## Getting started

See [`PHASE_1_GETTING_STARTED.md`](./PHASE_1_GETTING_STARTED.md) for original local setup instructions, [`IMPLEMENTATION_PLAN.md`](./IMPLEMENTATION_PLAN.md) for the full phase-by-phase build plan, and [`AGENTS.md`](./AGENTS.md) for day-to-day build/run/test commands (backend and UI).

```bash
mvn clean install                     # build all backend modules
docker compose up -d --build          # full stack — rebuild services SEQUENTIALLY on a
                                       # resource-constrained machine, see docs/gotchas.md
cd ui && npm install && npm run dev   # UI dev server against the gateway at :8080
```

A ready-to-import API collection covering every endpoint is in [`postman/`](./postman/).

## Built with AI-assisted engineering

This project is also a deliberate exercise in AI-augmented development — using GitHub Copilot, Claude Code, and Codex across design discussions, service scaffolding, and code review, while holding the output to the same standards (clean architecture, tracing, resilience, test coverage) expected in enterprise delivery. See [`CLAUDE.md`](./CLAUDE.md) and [`AGENTS.md`](./AGENTS.md) for the working conventions used with these tools on this repo.

## License

Personal portfolio project — feel free to browse and reference; not licensed for reuse as-is.