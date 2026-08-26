# AGENTS.md - AI Agent Guidance for Enterprise Order Platform

**Purpose:** Essential knowledge for AI coding agents to be immediately productive in this microservices codebase. Detail that doesn't need to be loaded for every task lives in `docs/` — read those on demand, not by default.

---

## Quick Context

**14-phase microservices order processing platform** (Spring Boot 3, Java 21, PostgreSQL, Kafka) — portfolio/learning project demonstrating enterprise architecture and operational maturity. **Currently: Phase 13 complete (React UI).** Next: Phase 14 (Docker Orchestration).

**Key Stack:** Spring Boot 3, Spring Cloud Gateway, Apache Kafka, PostgreSQL, React 19 + Vite + TypeScript (Phase 13), Docker Compose, Resilience4j
**Project Type:** Maven multi-module (9 services + gateway + shared-library) + a separate `ui/` npm project — not a Maven module, own `package.json`/build toolchain; see [PHASE_13_COMPLETE.md](PHASE_13_COMPLETE.md)

**Critical:** the gateway is reactive (Netty) and must never depend on the servlet-based shared-library. All client traffic routes through `:8080`. Full topology, ports, Kafka topics, and saga flow: **[docs/architecture.md](docs/architecture.md)**. Non-negotiable rules (exception handling, idempotency, event-vs-HTTP, gateway route wiring, anti-patterns): **[docs/domain-rules.md](docs/domain-rules.md)**. Hard-won implementation gotchas by phase: **[docs/gotchas.md](docs/gotchas.md)**.

---

## Build & Deployment

```powershell
# Build everything
mvn clean install

# Build/run/test a single service
mvn clean install -pl services/customer-service
mvn -pl services/customer-service spring-boot:run
mvn test -pl services/customer-service

# Skip tests for faster iteration
mvn clean install -DskipTests

# Docker infra
docker compose up postgres        # Postgres only, for local dev
docker compose up -d --build      # full stack — rebuild services SEQUENTIALLY, see docs/gotchas.md
docker compose logs -f postgres
docker compose down
```

**Parent POM:** `pom.xml` at repo root — Spring Boot BOM 3.3.0, Spring Cloud 2023.0.3, Lombok, MapStruct. **Java 21**, enforced via maven-compiler-plugin. Never redeclare dependency versions in child POMs.

Build artifact: `services/{service-name}/target/{service-name}-1.0.0.jar`. Docker image: `{service-name}:1.0.0`.

**UI (`ui/`, Phase 13):**
```powershell
cd ui
npm install
npm run dev              # Vite dev server, http://localhost:5173, talks to gateway at :8080
npx tsc -b --noEmit       # typecheck
npm run lint
npm run build             # production bundle -> ui/dist
docker compose build ui && docker compose up -d ui   # nginx image, served on :3000
```
Vite bakes `VITE_API_URL` into the static bundle at build time (default `http://localhost:8080` — correct for every local/docker-compose setup since the *browser*, not the container, calls the gateway). Never call a service port (`:8081`-`:8088`) directly from the UI — always through the gateway at `:8080`.

---

## Code Conventions

- **Naming:** `{Resource}Controller` / `{Resource}Service` / `{Resource}Repository extends JpaRepository` / `{Resource}` entity / `{Resource}DTO` / `{Resource}Mapper`.
- **Package layout, shared-library organization, and the "add to shared-library first" rule:** [docs/architecture.md](docs/architecture.md).
- **Reference implementation:** `services/customer-service` is the most complete example of the standard layering — follow its patterns for new endpoints, exceptions, validation, and tests rather than reinventing them.
- **Formatting:** standard language style guides (PEP 8, Prettier/Airbnb, etc.); no minified/compressed output; preserve vertical spacing.
- **Logging:** SLF4J (`LoggerFactory.getLogger(...)`), JSON-structured (Phase 11) — see [docs/architecture.md](docs/architecture.md) for the logback setup.
- **Commit messages:** `{phase}-{component}: {description}` (e.g. `phase-5-order-service: add order validation logic`).

## Database Migrations

Flyway, `services/{service}/src/main/resources/db/migration/`, naming `V{N}__{description}.sql`, auto-runs on startup. `hibernate.ddl-auto: validate` always — see [docs/domain-rules.md](docs/domain-rules.md).

## Testing

- Unit tests mock dependencies; integration tests (`XxxIT.java`, `class XxxIT`) use TestContainers PostgreSQL — see [docs/gotchas.md](docs/gotchas.md) for the Surefire naming gotcha.
- Target 80%+ line coverage on business logic (not Lombok-generated getters/setters).
- Mock external calls (payment gateway, email/SMS).

---

## Phase Workflow

Phases build sequentially; each is independently testable and deployable. Process for starting/finishing a phase is defined in root `CLAUDE.md`. Phase dependencies and a one-page overview: [PHASE_QUICK_REFERENCE.md](PHASE_QUICK_REFERENCE.md). Full technical spec per phase: [IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md). Big-picture strategy: [PROJECT_OVERVIEW.md](PROJECT_OVERVIEW.md).

**When closing a phase:** update this file's Quick Context (phase number, next phase), append new gotchas to [docs/gotchas.md](docs/gotchas.md), append new invariants to [docs/domain-rules.md](docs/domain-rules.md) or topology changes to [docs/architecture.md](docs/architecture.md) if applicable — do **not** re-narrate the phase's full architecture/validation here; that belongs in `PHASE_N_COMPLETE.md` only.

---

## Anti-Patterns to Reject

See the full list with rationale in [docs/domain-rules.md](docs/domain-rules.md#anti-patterns-reject-in-review). Summary: no controller-layer DB queries, no swallowed exceptions, no hardcoded config, no HTTP calls between services for anything Phase 8+ should route through Kafka, no missing `@Transactional`, no DTOs referencing `@Entity` directly.

---

**Last Updated:** August 26, 2026
**Current Phase:** Phase 13 complete (React UI — full dashboard, CRUD, orders/payments, Kafka event visibility, system health, auth; see [PHASE_13_COMPLETE.md](PHASE_13_COMPLETE.md))
**Next Phase:** Phase 14 (Docker Orchestration — the `ui` service is already wired into `docker-compose.yml` and verified; remaining scope is circuit-breaker/saga-pattern documentation and final end-to-end polish per [PHASE_QUICK_REFERENCE.md](PHASE_QUICK_REFERENCE.md))
