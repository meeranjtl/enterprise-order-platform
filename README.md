# Enterprise Order Platform

A hands-on portfolio project simulating a real-world, enterprise-grade e-commerce order processing system — built to practice and demonstrate modern microservices architecture, operational excellence, and engineering maturity using **Java 21** and **Spring Boot 3**.

This isn't a tutorial clone. It's built the way a production platform would be: with a shared library, correlation-ID tracing across services, a reactive API gateway, and a phased delivery plan that mirrors how real engineering teams incrementally ship enterprise systems.

## Why this project exists

After 20+ years building enterprise Java systems in client environments, this project is my own sandbox for:
- Applying event-driven architecture and microservices patterns end-to-end, from a clean slate
- Practicing modern tooling (Java 21, Spring Boot 3, Spring Cloud Gateway, Resilience4j) outside a legacy codebase
- Evaluating **AI-assisted development workflows** — using GitHub Copilot, Claude Code, and Codex throughout design, scaffolding, and code review — against the standard of enterprise-grade engineering discipline

## Architecture at a glance

- **Maven multi-module** project: 10 planned services + 1 shared library
- **Event-driven core**: Apache Kafka for cross-service messaging; CQRS/Saga patterns planned for order workflow orchestration
- **Reactive API Gateway**: built on Spring Cloud Gateway (Netty/WebFlux) — intentionally kept dependency-free from the servlet-based shared library
- **Correlation-ID tracing**: the gateway generates and propagates an `X-Correlation-Id` header on every request; downstream servlet-based services pick it up via the shared library's `CorrelationIdLoggingFilter` and attach it to their logging MDC, giving end-to-end request tracing across the platform
- **All client traffic routes through the gateway** (`:8080`) — no service is called directly from outside the platform

```
Client → API Gateway (:8080, reactive)
              ├── Customer Service (:8081)
              ├── Product Service (:8082)
              └── Order Service (:8083, in progress)
```

## Tech stack

| Layer | Technology |
|---|---|
| Language / Runtime | Java 21 |
| Framework | Spring Boot 3, Spring Cloud Gateway |
| Messaging | Apache Kafka |
| Database | PostgreSQL |
| Frontend | React 18 |
| Resilience | Resilience4j (circuit breakers, retries) |
| Containerization | Docker Compose |
| Build | Maven (multi-module) |

## Project status

| Phase | Scope | Status |
|---|---|---|
| 1 | Foundation — multi-module scaffold, shared library, Docker setup | ✅ Complete |
| 2 | Customer Service (`:8081`) | ✅ Complete |
| 3 | Product Service (`:8082`) | ✅ Complete |
| 4 | API Gateway (`:8080`, reactive) | ✅ Complete |
| 5 | Order Service (`:8083`) | 🚧 In progress |
| 6–14 | Payment, inventory, notification services; CQRS/Saga order orchestration; distributed tracing; observability stack; and further operational-maturity phases | 🔜 Planned |

*(Update this table as phases complete — keeping it current is what makes this credible to anyone reviewing the repo.)*

## Getting started

See [`GETTING_STARTED.md`](./GETTING_STARTED.md) for local setup instructions, and [`IMPLEMENTATION_PLAN.md`](./IMPLEMENTATION_PLAN.md) for the full phase-by-phase build plan.

```bash
docker-compose up -d      # starts PostgreSQL, Kafka, and supporting infra
mvn clean install         # builds all modules
```

## Built with AI-assisted engineering

This project is also a deliberate exercise in AI-augmented development — using GitHub Copilot, Claude Code, and Codex across design discussions, service scaffolding, and code review, while holding the output to the same standards (clean architecture, tracing, resilience, test coverage) expected in enterprise delivery. See [`CLAUDE.md`](./CLAUDE.md) and [`AGENTS.md`](./AGENTS.md) for the working conventions used with these tools on this repo.

## License

Personal portfolio project — feel free to browse and reference; not licensed for reuse as-is.