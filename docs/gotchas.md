# Gotchas Ledger

Hard-won, non-obvious failure modes discovered during implementation — each cost real debugging time and won't be re-derived from reading the code. Compressed one-entry-per-issue; full narrative and validation evidence for each is in the linked `PHASE_N_COMPLETE.md`. Add new entries here when closing a phase — don't re-narrate them in `AGENTS.md`.

## Phase 9 — Shipping & Notification

- **MapStruct: never use `MappingConstants.ComponentModel.SPRING`** in `@Mapper` — with this repo's Lombok/MapStruct processor setup it silently fails Spring bean registration at runtime (`No qualifying bean of type ...Mapper`, service won't start). Always use the literal `@Mapper(componentModel = "spring")`.
- **Docker healthcheck timings matter under cold-start load**: kafka CLI probe needs `timeout: 20s`; schema-registry needs `start_period: 150s+`; service Dockerfile `HEALTHCHECK`s need `--start-period=180s` (JVM startup ~120s). Keep these windows when adding a service.
- **Surefire test discovery**: includes are `**/*Test.java`, `**/*Tests.java`, `**/*IT.java`. Repo convention is `XxxIT.java` / `class XxxIT` for integration tests (see `OrderServiceIT`, `CustomerServiceIT`) — a differently-named integration test silently never runs.

→ full detail: [PHASE_9_COMPLETE.md](../PHASE_9_COMPLETE.md)

## Phase 10 — Analytics & Reporting

- **Spring Data JPA aggregate JPQL `SELECT`s always return `List<Object[]>`**, even for a guaranteed single row. Declaring the repository method to return a bare `Object[]` produces a nested-array `ClassCastException` at runtime — invisible to unit tests that mock the repository, only surfaces under a real query.
- **SQL `SUM(CASE...)` over zero matching rows is `NULL`, not `0`.** Wrap every conditional `SUM` in `COALESCE(..., 0)` unless the field should legitimately render as JSON `null` (e.g. an `AVG` with no contributing rows).
- **`localhost:9092` vs `localhost:9094`**: a JVM running directly on the host (not in a container) must use `9094` (`KAFKA_ADVERTISED_LISTENERS` → `PLAINTEXT_HOST`); `9092` only works from inside the compose network. Silent failure — the consumer just never receives anything, no error thrown.
- **Creating a product via product-service does not provision an inventory-service record.** An order against a freshly-created product fails reservation (`Inventory not found with identifier: N`) and sticks at `PENDING`. Pre-existing platform gap, not phase-specific — use a product with a seeded inventory row (id 1 or 2), or call `/api/v1/inventory/adjust` first, for E2E testing.
- **Gateway route wiring for a new service touches 5 places** — see [domain-rules.md](domain-rules.md#gateway-route-wiring-addingchanging-a-route).

→ full detail: [PHASE_10_COMPLETE.md](../PHASE_10_COMPLETE.md)

## Phase 11 — Observability

- **A Spring profile's scalar property fully overrides the base value, not merges** — same silent-drop risk as the list-override gotcha above, but for scalars. Gateway's `application-docker.yml` must redeclare `management.endpoints.web.exposure.include` or `/actuator/prometheus` silently vanishes only in Docker (works fine locally).
- **Kafka-hop trace propagation is architecturally absent, not a config gap.** The transactional outbox pattern writes the event inside the original request's transaction, then a separate `@Scheduled` `OutboxPoller` publishes it later on an unrelated thread — there is no trace context to propagate from at publish time. Confirmed in Zipkin: every outbox-poller span is a root span with no parent. A real fix means storing `traceId`/`spanId` on the outbox row and restoring that span context in `OutboxPublisher.publishEvent()` — deferred, not yet done. `X-Correlation-Id` (propagated via Kafka headers already) remains the way to correlate a saga's logs across services in the meantime.
- **Never rebuild many Spring Boot service images concurrently on a resource-constrained Docker Desktop.** `docker compose up -d --build` across 8 services at once crashed the Docker Desktop engine (`docker version` itself returned a `500` from the daemon — confirmed broken, not just slow). Rebuild sequentially: `docker compose build <service>` one at a time.
- **Neither `kafka` nor `zookeeper` has a data volume.** If either gets into a bad state (e.g. a stale ZooKeeper `NodeExistsException` after an unclean shutdown), `docker compose rm -f zookeeper kafka` + `up -d` recreates them cleanly — no data loss to worry about.

→ full detail: [PHASE_11_COMPLETE.md](../PHASE_11_COMPLETE.md)
