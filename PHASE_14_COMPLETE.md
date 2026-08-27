# Phase 14 - COMPLETE: Docker Orchestration & Advanced Patterns

**Date:** August 26, 2026
**Status:** ✅ Validated — all 14 phases now complete. Full stack (17 containers)
rebuilt, restarted, and live-verified together; a real order was tracked
end-to-end from creation through payment to shipment for the first time
in this project's history.

Phase 14 was scoped down from `IMPLEMENTATION_PLAN.md`'s generic
template to what was actually missing (see `PHASE_14_GETTING_STARTED.md`)
— most of the template (circuit breakers at the gateway, observability
stack, saga orchestration itself) was already delivered in earlier
phases. What started as a documentation pass surfaced a critical,
universally-reproducing bug that had been silently breaking every order
since Phase 12 shipped.

---

## What Was Delivered

### Resilience — circuit breaker + retry on order-service's internal calls
- `CustomerClient`/`ProductClient` (the pre-Phase-8 sanctioned synchronous-call
  exception) now wrapped in `@CircuitBreaker` + `@Retry` (`resilience4j-spring-boot3`
  + `spring-boot-starter-aop`, config in `order-service/application.yml`).
- **Real bug found and fixed while building this**: `ignoreExceptions` only
  excludes an exception from the circuit breaker's failure-rate bookkeeping —
  it does **not** stop the fallback method from firing for it. A legitimate
  404/400 (e.g. looking up a customer you don't own) was being retried 3x
  and masked behind a generic 500. Fixed by having the fallback methods
  themselves rethrow `ResourceNotFoundException`/`BadRequestException`
  unchanged. Live-verified both directions: a not-owner lookup now returns
  a fast 400 (no retry delay), and a real customer-service outage (container
  stopped mid-test) returns a clean 500 in ~3.7s instead of hanging.
  See `docs/gotchas.md#phase-14`.

### Documentation
- `docs/saga.md` — full state machine, step-by-step Kafka flow, and
  compensating transactions for the order-fulfillment saga.
- `docs/patterns.md` — CQRS (analytics-service) and the outbox pattern's
  relationship to event sourcing, explicit about what's real vs. deliberately
  out of scope.
- `postman/enterprise-order-platform.postman_collection.json` +
  `.postman_environment.json` — every endpoint across all 8 services, an
  auth folder with test scripts that auto-capture tokens into collection
  variables.
- `.github/workflows/ci.yml` — Maven build+test (all modules) and UI
  typecheck/lint/build, on push/PR. Build+test only, no registry push —
  no real deploy target exists for this project.
- `README.md` refreshed (was stuck describing Phase 5 as "in progress").

### The real find: payment-service could never create a Payment
While writing up the saga flow, found that `payment-service`'s
`InventoryEventConsumer` made a direct, **unauthenticated** synchronous
`RestTemplate` call to `order-service` to fetch `customerId`/`totalAmount`
— itself the exact Kafka-only anti-pattern Phase 8 forbids (never a
sanctioned exception, unlike order-service's client calls), and broken on
every single invocation once Phase 12 required auth on that endpoint. Net
effect: **no order had ever successfully reached a completed payment since
Phase 12 shipped** — the Phase 13 "stuck `PAYMENT_PENDING` order" incident
was a symptom of this, not (only) the Kafka data-loss issue it was
originally attributed to.

Fixed properly, not patched: `payment-service` now has its own
`OrderEventConsumer` that consumes `order-events` into a local
`order_snapshots` table (`OrderSnapshot` entity, `V4` migration), and
`InventoryEventConsumer` reads `customerId`/`totalAmount` from that
instead of calling order-service at all — removes the anti-pattern and
the auth bug together, no new backend-to-backend HTTP dependency.

### Infrastructure fixes
- **Kafka/ZooKeeper now have persistent volumes** (`zookeeper_data`,
  `zookeeper_log`, `kafka_data`) — previously, any container recreation
  (including the documented stale-ephemeral-node recovery) silently
  dropped every in-flight Kafka message. This was the other half of the
  Phase 13 stuck-order incident.
- **Fixed a port collision**: `ui` and `grafana` both claimed host port
  `3000:3000` — undetected until this phase's full-stack restart brought
  both up fresh at the same time. Grafana remapped to `3001:3000`.

---

## Bugs Found and Fixed This Phase

1. Circuit breaker fallback masking legitimate 4xx errors as 500s (resilience4j `ignoreExceptions` semantics) — `CustomerClient.java`, `ProductClient.java`.
2. payment-service → order-service unauthenticated direct HTTP call, the true root cause of every order getting stuck — `InventoryEventConsumer.java`, new `OrderEventConsumer.java`/`OrderSnapshot`/`OrderSnapshotRepository`/`V4` migration.
3. Kafka/ZooKeeper data loss on container recreation — `docker-compose.yml` volumes.
4. `ui`/`grafana` host port collision — `docker-compose.yml`.

All four are documented in `docs/gotchas.md` under "Phase 14 — Docker
Orchestration & Advanced Patterns"; the now-inaccurate Phase 11 gotcha
about Kafka/ZooKeeper volumes ("no data loss to worry about") is
corrected in place.

---

## Validation

- `mvn -pl services/order-service -am test`: 25/25 pass.
- `mvn -pl services/gateway -am test`: 27/27 pass (regression check, gateway untouched).
- `mvn -pl services/payment-service -am test`: 2/2 pass.
- Full stack rebuilt (`order-service`, `payment-service` images) and
  restarted together (`docker compose up -d`, all 17 containers healthy,
  including Kafka/ZooKeeper recreated with their new volumes).
- **Live end-to-end saga verification** (first time ever for this
  project): registered a fresh customer, created an order, polled status —
  `PENDING → PAYMENT_PENDING → PAYMENT_APPROVED → SHIPPED` in ~10 seconds.
  Confirmed a real `Payment` record (`id=1`, `status=COMPLETED`, real mock
  `transactionId`) exists for the order.
- Circuit breaker fallback fix verified both directions: fast 400 for a
  not-owner lookup (no retry delay), clean 500 in ~3.7s for a genuine
  customer-service outage (container stopped mid-test, then restarted).

---

## Known Issues (carried forward, not blocking)

- Order 1 (created in Phase 13, before this phase's fixes) remains
  permanently stuck at `PAYMENT_PENDING` — its `InventoryReservedEvent`
  was lost to the pre-volume Kafka wipe and can't be replayed. Harmless
  demo-data artifact; every order created from this phase onward
  completes correctly.
- The Phase 12 gotcha about `@WebMvcTest` not reliably enforcing
  `@PreAuthorize` AOP still applies — no change this phase.
- `OrderController.createOrder` still doesn't verify the authenticated
  customer's ID matches the request's `customerId` — noted in Phase 13,
  not fixed (a customer can technically place an order "as" another
  customer ID and it will correctly fail ownership checks downstream, but
  the failure mode is a confusing generic error rather than an explicit
  403 at the point of order creation). Not chased this phase — flagged
  for awareness only.
- `.github/workflows/ci.yml` was authored and structurally validated but
  never actually triggered (no push to a remote this session) — worth a
  first real run before relying on it.

---

## Project Status

**All 14 phases complete.** See `README.md` for the full phase table and
`AGENTS.md` for day-to-day build/run/test commands. This is the last
`PHASE_N_COMPLETE.md` — there is no Phase 15 in the current plan.
