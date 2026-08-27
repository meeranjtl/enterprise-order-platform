# Phase 14 — Docker Orchestration & Advanced Patterns: Getting Started

**Date:** August 26, 2026
**Status:** 📋 Planning
**Predecessor:** Phase 13 complete (React UI) — see `PHASE_13_COMPLETE.md`

Phase 14 is the platform's final phase. Per `IMPLEMENTATION_PLAN.md`'s
original Phase 14 spec, the nominal scope is broad (complete
`docker-compose.yml`, circuit breakers, saga/CQRS/event-sourcing
patterns, an API collection, final docs). Most of that generic template
was already delivered incrementally in earlier phases — this plan scopes
Phase 14 down to what's **actually missing**, verified against the real
codebase rather than assumed from the template.

---

## 1. What's Already Done (verified, not re-implemented)

| Template deliverable | Actual status |
|---|---|
| `docker-compose.yml` with all services, healthchecks, network | ✅ Done — 9 services + gateway + UI + Postgres/Kafka/ZooKeeper/Redis/schema-registry/Zipkin/Prometheus/Grafana/kafka-ui, all on `enterprise-order-network`, healthchecks throughout (Phases 1–13) |
| Circuit breakers | ✅ Done **at the gateway** — resilience4j per downstream route (Phase 6/7). ❌ **Missing** on order-service's two internal synchronous calls (`CustomerClient`, `ProductClient`) — confirmed via repo-wide grep, zero resilience4j usage outside `services/gateway`. These are the exact calls that broke silently once already this session (Phase 13's auth-forwarding bug) — real gap, real value in closing it. |
| Saga orchestration | ✅ Implemented (order state machine + Kafka events across order/inventory/payment/shipping/notification, Phases 5–9) but never written up as its own document — only scattered across `docs/architecture.md` prose and the code itself |
| CQRS | ✅ analytics-service (Phase 10) already **is** a CQRS read-model (separate read store, built from events, never queried transactionally against order-service) — undocumented as such |
| Event sourcing | Partial/adjacent: the transactional outbox (`outbox_event` table, Phase 8) is an event-sourcing-flavored mechanism, but full event replay/state reconstruction was never built. `IMPLEMENTATION_PLAN.md`'s own "Future Enhancements" list defers "Event sourcing complete implementation" — treating it as out of scope here is consistent with the plan's own intent, not a shortcut |
| Postman/API collection | ❌ Doesn't exist |
| CI/CD (`.github/workflows/`) | ❌ Doesn't exist |
| Root `README.md` | Stale — still shows Phase 5 "in progress", Phases 6–14 "planned", React 18 (actual: React 19) |
| Volume management | Postgres has a named volume. **Kafka/ZooKeeper do not** — already flagged in `docs/gotchas.md` (Phase 11/13) as the cause of a real data-loss incident this session (Phase 13's stuck `PAYMENT_PENDING` order). User deferred the decision on fixing this to "end of next phase" — **revisit at the end of this phase**, not before. |

---

## 2. Actual Phase 14 Scope

1. **Circuit breaker + retry on order-service's `CustomerClient`/`ProductClient`** — `@CircuitBreaker` + `@Retry` with a fallback method, resilience4j config in `application.yml`, matching the gateway's existing pattern. This is the one deliverable with real behavioral value, not just documentation.
2. **`docs/saga.md`** — document the existing orchestration-based saga: state machine, per-service Kafka event flow, compensating transactions on failure (inventory release, payment refund, etc.). Cross-link from `docs/architecture.md`.
3. **`docs/patterns.md`** — document analytics-service as the platform's CQRS example, and the outbox pattern as the event-sourcing-adjacent mechanism, explicit about what's implemented vs. deliberately deferred.
4. **Postman collection** (`postman/enterprise-order-platform.postman_collection.json` + environment) — auth, customers, products, orders, payments, analytics, all through the gateway.
5. **`.github/workflows/ci.yml`** — build + test (Maven multi-module + UI). Scoped to build/test only; no registry push or deploy target exists for a local portfolio project, so that's not fabricated.
6. **`docker-compose.yml` completeness pass** — re-verify healthchecks/volumes/network against the template checklist; revisit the Kafka/ZooKeeper persistent-volume decision with the user at the end.
7. **`README.md` refresh** — bring phase-status table and tech stack current through Phase 14.
8. **`PHASE_14_COMPLETE.md`**, final `AGENTS.md` update (project marked complete).

**Explicitly out of scope** (per `IMPLEMENTATION_PLAN.md`'s own "Future Enhancements"): full event sourcing/replay, full CQRS beyond the existing analytics-service example, SonarQube integration, k6/JMeter perf testing, Pact contract testing, Kubernetes, service mesh, GraphQL, gRPC.

## 3. Validation Approach

Per explicit instruction: validate with `mvn test` (unit + integration) after the circuit-breaker code change, and only rebuild/restart the Docker stack **once, at the end**, for a single end-to-end verification pass — not per-change.
