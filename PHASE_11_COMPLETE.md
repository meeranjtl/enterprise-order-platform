# Phase 11 - COMPLETE: Observability

**Date:** August 24, 2026
**Status:** ✅ Validated — full observability stack deployed and healthy, JSON
logs/metrics/health probes verified on all 10 services, distributed tracing
verified for the synchronous HTTP hop, Grafana dashboards confirmed wired to
live Prometheus data

Phase 11 adds cross-cutting observability to every service (gateway + 9
microservices) — structured JSON logs, Prometheus metrics, Grafana
dashboards, Zipkin distributed tracing, and liveness/readiness health
probes. No new business logic, no new Kafka topics, no new DB tables.

---

## What Was Delivered

### Structured JSON Logging
- `logstash-logback-encoder` on every service; `shared-library`'s
  `logback-json-base.xml` is `<include>`d by the 8 servlet services'
  `logback-spring.xml`; gateway carries a standalone equivalent (it must
  never depend on shared-library — reactive vs. servlet).
- Every JSON log line carries `service`, and (where present) `correlationId`
  and `traceId`/`spanId` via SLF4J MDC — no extra code needed, since
  `LogstashEncoder` includes all MDC entries by default and Micrometer
  Tracing's Brave integration auto-populates `traceId`/`spanId` into MDC.

### Prometheus Metrics
- `micrometer-registry-prometheus` on every service; `/actuator/prometheus`
  exposed platform-wide; `management.metrics.tags.application` labels every
  metric by service name for a shared Prometheus instance.
- Kafka consumer/producer metrics (including lag) are automatic — Spring
  Boot's `KafkaMetricsAutoConfiguration` binds Micrometer's `KafkaClientMetrics`
  to the autoconfigured `ConsumerFactory`/`ProducerFactory` beans with zero
  extra code, confirmed via `kafka_consumer_fetch_manager_records_lag` (42
  series) in Prometheus.
- Two custom business metrics: `order.creation.duration` (Timer around
  `OrderService.createOrder()`, order-service) and `payment.result` (Counter
  tagged `outcome=COMPLETED|FAILED`, payment-service) — success rate is
  computed in Grafana via PromQL, not in-app, per standard Micrometer practice.

### Health Probes
- `management.endpoint.health.probes.enabled: true` on every service —
  `/actuator/health/liveness` and `/actuator/health/readiness`.
- DB health is auto-configured by Spring Boot (`DataSourceHealthContributorAutoConfiguration`).
  Kafka health is **not** — verified directly against
  `spring-boot-actuator-autoconfigure-3.3.0.jar` (only `KafkaMetricsAutoConfiguration`
  exists, no health contributor) — added `shared-library`'s
  `KafkaHealthIndicator`, reusing the existing `KafkaAdmin` bean.

### Distributed Tracing (Zipkin)
- `micrometer-tracing-bridge-brave` + `zipkin-reporter-brave` on every
  service; `management.tracing.sampling.probability: 1.0` (trace everything —
  fine at this traffic volume); `management.zipkin.tracing.endpoint`
  (Spring Boot 3 property — IMPLEMENTATION_PLAN.md's example used the
  Boot-2-era `spring.zipkin.base-url`, corrected during planning).
- All 9 services register in Zipkin (`/api/v2/services`).
- **Verified end-to-end for the synchronous hop:** a real
  `POST /api/v1/orders` through the gateway produces one 3-span Zipkin trace
  — `api-gateway` SERVER → `api-gateway` CLIENT → `order-service` SERVER —
  with correct parent/child relationships.
- **Known gap, by design of the existing architecture:** the async
  saga hops (order → inventory → payment → shipping → notification →
  analytics, all via the Phase 8 transactional outbox pattern) are **not**
  part of that trace. See Known Issues below.

### Infrastructure (`docker-compose.yml` + `observability/`)
- Three new containers: `zipkin` (in-memory, no persistence needed at this
  scale), `prometheus` (scrapes all 9 app services' `/actuator/prometheus`
  every 15s via `observability/prometheus.yml`), `grafana` (auto-provisioned
  Prometheus datasource + 3 dashboards from `observability/grafana/`).
- Every app service gets `MANAGEMENT_ZIPKIN_TRACING_ENDPOINT` pointing at the
  `zipkin` container over the compose network.
- Deliberately **not** gated on `depends_on` — the order-processing services
  start fine even if the observability stack is briefly cold, per the
  anti-pattern noted in the getting-started plan.
- 3 Grafana dashboards (hand-authored against actual metric names rather than
  imported, to avoid a network dependency during development): **Platform
  Overview** (HTTP rate/latency/errors, JVM heap, HikariCP), **Kafka
  Consumers** (lag + consume rate per group/topic), **Business KPIs** (order
  creation rate/p95, payment success rate/outcome breakdown).

---

## Validation Results

### Docker Deployment
- `docker compose up -d --build` — all **18 containers healthy**: gateway,
  customer, product, order, inventory, payment, shipping, notification,
  analytics, postgres, redis, kafka, zookeeper, schema-registry, kafka-ui,
  **zipkin, prometheus, grafana**

### Prometheus
- All 9 app-service scrape targets report `up` at `/api/v1/targets`
- Custom metrics confirmed live: `order_creation_duration_seconds_count`,
  `payment_result_total{outcome="COMPLETED"|"FAILED"}` (matching real test
  orders exactly), `kafka_consumer_fetch_manager_records_lag` (42 series)

### Grafana
- Provisioned `Prometheus` datasource confirmed via API
  (`http://prometheus:9090`)
- All 3 dashboards confirmed loaded via API: Platform Overview, Kafka
  Consumers, Business KPIs
- Confirmed the Grafana container can reach Prometheus directly over the
  compose network and get real data back

### End-to-End Saga (via gateway :8080)
- Created two real orders against a seeded product (id=1); one flowed to
  `PAYMENT_APPROVED`, one to `PAYMENT_REJECTED` (simulated gateway
  randomness in payment-service, not a bug) — both correctly incremented
  `payment_result_total` under the right `outcome` label
- Zipkin: confirmed the gateway→order-service HTTP hop for the order
  creation request is one connected 3-span trace

---

## Issues Found & Fixed During Implementation

1. **Gateway's docker profile silently drops `prometheus` from the exposure
   list.** `application-docker.yml`'s `management.endpoints.web.exposure.include`
   is a scalar property, so it fully *overrides* (not merges with) the base
   `application.yml` value when the docker profile is active — same class of
   silent gotcha AGENTS.md already documents for gateway route wiring. Fixed
   by re-declaring the full list (with `prometheus` added) in the docker
   profile; tracing/probe/metrics-tag keys didn't need duplicating since the
   docker file doesn't redeclare those specific keys.
2. **Kafka + Zookeeper crashed with a stale `NodeExistsException` after a
   Docker Desktop restart mid-build.** Not a Phase 11 code issue — neither
   container has a data volume, so removing and recreating both containers
   cleanly resolved it.
3. **Docker Desktop's engine crashed** (`rpc error: code = Unavailable desc =
   error reading from server: EOF`) when rebuilding all 8 changed servlet
   services **concurrently** — 8 simultaneous Maven+Lombok+MapStruct
   compilations exhausted the Docker Desktop VM. Confirmed via `docker
   version` itself returning a `500` from the daemon (not a timeout — the
   engine had genuinely broken, not just gotten slow). Fixed by restarting
   Docker Desktop and rebuilding the 8 services **sequentially**
   (`docker compose build <service>` one at a time) instead of via a single
   concurrent `--build`.

---

## Known Issues / Notes

- **Kafka-hop trace propagation is architecturally absent, not a config
  gap.** This platform publishes events via the Phase 8 **transactional
  outbox pattern**: the event is written to an outbox table inside the
  original request's transaction, then a separate `@Scheduled`
  `OutboxPoller` picks it up seconds later on its own thread and calls
  `kafkaTemplate.send()`. That poller thread has no relationship to the
  original HTTP request, so there is no trace context to propagate at the
  point the Kafka message is actually sent — confirmed directly in Zipkin
  (every outbox-poller trace is a single root span with no parent, no
  matter what tracing config is applied). `spring.kafka.template.observation-enabled`
  and `spring.kafka.listener.observation-enabled` are enabled on all 8
  servlet services (correct, harmless, and would matter for any future
  direct `kafkaTemplate.send()` call outside the outbox pattern), but they
  cannot connect a trace across the outbox boundary as-is. Properly fixing
  this would mean storing `traceId`/`spanId` as columns on the outbox row
  at `storeEvent()` time and manually restoring that span context in
  `OutboxPublisher.publishEvent()` — a real code change across
  `shared-library`'s outbox pattern (used by every service), deliberately
  deferred rather than done as part of this phase. The platform's existing
  `X-Correlation-Id` (business-level, propagated through Kafka headers
  already — see `eventType`/`kafka_messageKey` pattern) remains the way to
  correlate a saga's log lines across services; it's just not (yet) the
  same thing as one Zipkin trace.
- **`CustomerServiceIT` is intermittently flaky** (documented in
  `PHASE_11_GETTING_STARTED.md` §Sprint 2). It's the one test that boots the
  full Spring context against real TestContainers Postgres (~55s startup);
  re-running identical code produces fail/pass/pass across attempts. Not
  caused by any Phase 11 change (bisection briefly pointed at
  `KafkaHealthIndicator` before repeated runs disproved that) — most likely
  a timing/resource-contention artifact of a heavier classpath combined with
  a loaded build machine. Full-suite validation deferred to Phase 12 per
  the platform owner's call; revisit if it recurs.
- **`GET /api-docs` 500s platform-wide** (pre-existing since Phase 10, not
  Phase 11 scope — see `PHASE_10_COMPLETE.md`).
- **Grafana dashboards hand-authored, not imported** from a community
  dashboard ID — deliberate call made during Sprint 4 to avoid a network
  dependency and to match our exact custom metric names
  (`order_creation_duration_seconds`, `payment_result_total`) that no
  off-the-shelf dashboard would know about.
- **No Prometheus alert rules configured.** Flagged as an open decision in
  the getting-started plan (no Alertmanager notification channel exists in
  this project); not added — revisit if alerting becomes a concrete need.

---

## Success Checklist (from PHASE_11_GETTING_STARTED.md)

- [x] Every service (incl. gateway) emits JSON logs with `service`,
      `correlationId`, `traceId`, `spanId` fields
- [x] `/actuator/prometheus` returns metrics on all 10 services; Prometheus
      UI shows all targets `UP`
- [x] `/actuator/health/liveness` and `/actuator/health/readiness` return
      200 on all services (via `management.endpoint.health.probes.enabled`)
- [x] A request through the gateway produces a single Zipkin trace spanning
      every *synchronous* service it touches (gateway → order-service) —
      the Kafka-hop limitation is documented above, not silently missed
- [x] Grafana shows all 3 dashboards with a working Prometheus datasource
      and confirmed live data
- [x] `docker compose up -d --build` — all containers healthy, including
      `zipkin`, `prometheus`, `grafana`
- [x] `AGENTS.md` + phase docs updated
- [ ] Full `mvn clean install` platform-wide validation — deferred to
      Phase 12 per the platform owner; per-module test runs during Sprints
      1–3 were all green (order-service, payment-service, gateway, and the
      other 6 servlet services individually)

**Ready for Phase 12 (Security) ✅**
