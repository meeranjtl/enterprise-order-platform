# Phase 11 — Observability: Getting Started

**Date:** August 24, 2026
**Status:** 📋 Planned — ready to implement
**Predecessor:** Phase 10 complete (Analytics & Reporting) — see `PHASE_10_COMPLETE.md`

Phase 11 adds cross-cutting **observability** to all 10 running services
(gateway + 9 microservices): structured JSON logs, Prometheus metrics,
Grafana dashboards, Zipkin distributed tracing, and liveness/readiness
health probes. Unlike every prior phase, this phase touches **every module**
rather than adding one new service — no new business logic, no new Kafka
topics, no new DB tables.

---

## 1. What Already Exists (don't rebuild this)

| Piece | Where | Status |
|---|---|---|
| Correlation ID generation + propagation | `gateway` generates `X-Correlation-Id`; `CorrelationIdLoggingFilter` (shared-library) puts it in servlet-service MDC | ✅ Done since Phase 4/8 |
| Plain-text log pattern with `corr=` | every service's `application.yml` (`logging.pattern.level`) / gateway's `logback-spring.xml` | ✅ Done, but **not JSON** — Phase 11 upgrades this |
| `spring-boot-starter-actuator` | already a dependency in every service pom (confirmed: order-service, gateway) | ✅ Done |
| `management.endpoints.web.exposure.include: health,metrics,info(,gateway)` | every `application.yml` | ✅ Done — Phase 11 adds `prometheus` |
| `management.endpoint.health.show-details: always` | every `application.yml` | ✅ Done |

**Gateway constraint (unchanged, critical):** gateway is reactive (Netty) and
must **never** depend on `shared-library` (servlet-based). Any shared-library
addition for Phase 11 (JSON log config, Kafka metrics binder, custom health
indicators) needs a **duplicated, reactive-safe equivalent** in gateway's own
module — same pattern as `CorrelationIdLoggingFilter` today.

Nothing observability-related exists yet in `docker-compose.yml` — no
Prometheus, Grafana, or Zipkin containers.

---

## 2. Deliverables (from IMPLEMENTATION_PLAN.md §Phase 11)

- [ ] Structured JSON logging (SLF4J + Logback + `logstash-logback-encoder`)
- [ ] Correlation ID present in every JSON log line (reuse existing MDC key)
- [ ] Prometheus metrics (`/actuator/prometheus` on every service)
- [ ] Grafana dashboards (JVM/HTTP, Kafka consumer lag, business KPIs)
- [ ] Health endpoints: liveness + readiness probes (K8s-style, `management.endpoint.health.probes.enabled`)
- [ ] Distributed tracing via Zipkin (Micrometer Tracing + Brave, sampling 1.0 for a portfolio project)
- [ ] Custom business metrics (order creation timer, payment success rate, Kafka consumer lag)
- [ ] ~~ELK log aggregation~~ → marked optional in the plan; **deferred**, JSON stdout logs are sufficient for this project's scale (revisit only if a real log shipper gets added later)

---

## 3. Design

### 3.1 JSON Structured Logging

- Add `logstash-logback-encoder` (pin version, e.g. `7.4`) to `shared-library`'s `pom.xml` (`<dependency>`, no scope restriction — it's a runtime logging concern every servlet service needs) **and** separately to `gateway`'s `pom.xml` (gateway can't pull it in transitively via shared-library).
- Replace every service's implicit default Logback config with an explicit `logback-spring.xml` using `LogstashEncoder`, including custom fields: `service` (`${spring.application.name}`), `correlationId` (from MDC — already populated), `traceId`/`spanId` (from MDC once Micrometer Tracing's `MDCScopeDecorator` is wired in §3.4).
- Gateway's existing `logback-spring.xml` gets converted from its current plain `%pattern` encoder to the same `LogstashEncoder` shape, kept in its own module (reactive-safe, no shared-library import).
- **To avoid copy-pasting the same XML into 10 files:** put a shared `logback-json-base.xml` fragment in `shared-library/src/main/resources/`, and each servlet service's `logback-spring.xml` does `<include resource="logback-json-base.xml"/>`. Gateway keeps a standalone copy (small, ~15 lines) since it has no shared-library dependency to include from.

### 3.2 Prometheus Metrics

- Add `micrometer-registry-prometheus` to `shared-library`'s pom (+ gateway's pom separately).
- `application.yml` (all services): `management.endpoints.web.exposure.include` gains `prometheus`; add `management.metrics.tags.application: ${spring.application.name}` so every metric is labeled by service in a shared Prometheus instance.
- Custom metrics (per IMPLEMENTATION_PLAN.md §11.3), added where the domain logic already lives:
  - `order.creation.duration` — `Timer` around `OrderService.createOrder()` (order-service)
  - `payment.success.rate` — derived from existing `PaymentProcessedEvent` status counter (payment-service)
  - Kafka consumer lag — bind `KafkaClientMetrics` (Micrometer's Kafka binder) to each service's `ConsumerFactory`/`ProducerFactory` beans in `shared-library`'s `KafkaConfig` (gateway has no Kafka consumers, skip)
  - DB connection pool (HikariCP) — auto-instrumented once `micrometer-registry-prometheus` is on the classpath, no code needed

### 3.3 Health Probes

- `management.endpoint.health.probes.enabled: true` + `management.health.livenessState.enabled` / `readinessState.enabled` (Spring Boot 3 built-ins) in every `application.yml`.
- Custom `AbstractHealthIndicator` per IMPLEMENTATION_PLAN.md §11.4 is **likely redundant** — Spring Boot 3 autoconfigures DB (`DataSourceHealthIndicator`) and Kafka health checks already once actuator + the respective starter are present; verify during Sprint 1 before writing custom indicators, only add one if a gap is found (e.g. no built-in Kafka health indicator ships by default in Boot 3 — confirm and add a small custom one only if missing).

### 3.4 Distributed Tracing (Zipkin)

- Add `micrometer-tracing-bridge-brave` + `zipkin-reporter-brave` to `shared-library` pom (+ gateway pom separately; Spring Cloud Gateway has native reactive tracing support via the same starters).
- **Correction vs. IMPLEMENTATION_PLAN.md's example:** that snippet uses Spring Boot 2-era `spring.zipkin.base-url` — Spring Boot 3 property is `management.zipkin.tracing.endpoint: http://zipkin:9411/api/v2/spans`. Use the Boot 3 property.
- `management.tracing.sampling.probability: 1.0` (trace everything — fine at this traffic volume; would be lowered in a real prod system).
- Wire `MDCScopeDecorator` so `traceId`/`spanId` land in MDC → flow into the JSON logs from §3.1 automatically (this is what makes "click a slow request in Grafana, jump to its trace in Zipkin, jump to its logs by traceId" actually work end-to-end).
- Existing `X-Correlation-Id` stays as-is (business-level correlation across the saga); `traceId`/`spanId` are a separate, lower-level per-hop concern from Micrometer Tracing. Don't conflate the two or try to force them to the same value.

### 3.5 Infrastructure (docker-compose.yml)

Three new containers:

| Service | Image | Port | Notes |
|---|---|---|---|
| `zipkin` | `openzipkin/zipkin:latest` | `9411:9411` | no persistence needed for a portfolio project (in-memory storage) |
| `prometheus` | `prom/prometheus:latest` | `9090:9090` | needs `observability/prometheus.yml` scrape config (10 targets: gateway :8080 + 9 services :8081–:8088 minus port collisions — actually 8081–8088 covers customer through analytics, gateway is 8080) mounted read-only |
| `grafana` | `grafana/grafana:latest` | `3000:3000` | provisioned datasource pointing at `prometheus:9090`; dashboards auto-provisioned from `observability/grafana/dashboards/` |

New top-level folder: `observability/` (prometheus.yml, grafana/provisioning/{datasources,dashboards}/, grafana/dashboards/*.json).

Each of the 10 service containers needs `SPRING_ZIPKIN...` env override for the docker network hostname (`http://zipkin:9411/...`), same pattern as `SPRING_KAFKA_BOOTSTRAP_SERVERS`.

**Known Docker healthcheck gotcha (Phase 9 lesson, applies again):** give `grafana` and `prometheus` a `start_period` before marking `gateway`'s `depends_on` conditional on them (or don't gate the app services on the observability stack at all — logs/metrics being briefly unavailable at cold-start shouldn't block the order-processing services from starting).

### 3.6 Grafana Dashboards

- **Platform dashboard** — JVM (heap, GC, threads), HTTP request rate/latency/error-rate per service, imported from a known-good community dashboard ID (e.g. Micrometer/Spring Boot dashboard) rather than hand-built, then trimmed.
- **Kafka dashboard** — consumer lag per group (`analytics-service-group`, `notification-service-group`, saga consumers), from the `KafkaClientMetrics` binder in §3.2.
- **Business dashboard** — orders/revenue over time, sourced from the custom metrics in §3.2 (cheap alternative to hitting analytics-service's own report API from Grafana; that JSON API integration is a stretch goal, not required for Phase 11).

---

## 4. Sprint Plan

### Sprint 1 — Shared Foundations ✅ (Aug 24, 2026)
1. `shared-library` pom: `logstash-logback-encoder`, `micrometer-registry-prometheus`, `micrometer-tracing-bridge-brave`, `zipkin-reporter-brave`
2. `shared-library/src/main/resources/logback-json-base.xml` (JSON encoder, MDC fields incl. correlationId/traceId/spanId)
3. Verified against the actual `spring-boot-actuator-autoconfigure-3.3.0.jar`: `DataSourceHealthContributorAutoConfiguration` exists (DB health is automatic), but **no Kafka health autoconfiguration exists** (only `KafkaMetricsAutoConfiguration`, metrics-only) — added `shared-library/.../health/KafkaHealthIndicator.java`, reusing the existing `KafkaAdmin` bean
4. Decompiled `KafkaMetricsAutoConfiguration`: it registers `DefaultKafkaConsumerFactoryCustomizer`/`ProducerFactoryCustomizer` beans that Spring Boot's autoconfigured Kafka factories pick up automatically — since `KafkaConfig` defines no custom factory beans, Kafka consumer/producer metrics (incl. lag) are automatic once `micrometer-registry-prometheus` is on the classpath. **No manual binding code needed** — skipped to avoid an unnecessary abstraction.

### Sprint 2 — Wire Every Servlet Service (8 services: customer, product, order, inventory, payment, shipping, notification, analytics) ✅ (Aug 24, 2026)
5. Each `application.yml`: added `prometheus` to exposure, `management.metrics.tags.application`, `management.zipkin.tracing.endpoint`, `management.tracing.sampling.probability: 1.0`, health probes enabled
6. Each `logback-spring.xml`: `<include resource="logback-json-base.xml"/>` — verified JSON log lines (with `service` field) actually appear in test output
7. Custom metrics: `order.creation.duration` Timer wrapping `OrderService.createOrder()`; `payment.result` Counter tagged `outcome=COMPLETED|FAILED` in `PaymentService.process()` (rate computed in Grafana via PromQL, not in-app — standard Micrometer practice) — both services needed a `MeterRegistry` constructor param, and both `*ServiceTest` classes needed a `@Spy private MeterRegistry meterRegistry = new SimpleMeterRegistry();` (a Mockito mock of `MeterRegistry` NPEs inside `Timer.start()`/`.counter()`; a real `SimpleMeterRegistry` wrapped as `@Spy` works)
8. Per-module builds green (`order-service`, `payment-service`, and the other 6 services all individually test green)

**Sprint 2 gotcha — flaky `CustomerServiceIT`:** during a full `mvn clean install`, `CustomerServiceIT` (the one test that boots the entire Spring context against real TestContainers Postgres, ~55s startup) intermittently failed with `No qualifying bean of type 'CustomerMapper' available`, despite `CustomerMapperImpl` compiling correctly and being `@Component`-annotated. Bisection initially pointed at the new `KafkaHealthIndicator`, but re-running the identical code 3x produced fail/pass/pass — confirming it's **intermittent**, not caused by any specific Phase 11 change. Likely a timing/resource-contention artifact of a heavier classpath (5 new shared-library deps) combined with a loaded build machine, not a logic bug. Not root-caused further per project decision; flag if it recurs during Sprint 5 final validation.

### Sprint 3 — Gateway (reactive, standalone) ✅ (Aug 24, 2026)
9. Gateway pom: same 4 dependencies as shared-library (duplicated, not inherited)
10. Gateway `logback-spring.xml`: converted to JSON (standalone copy, not the `<include>`)
11. Gateway `application.yml` + `application-docker.yml`: prometheus + zipkin + tracing + health-probe config. **Gotcha (same class as the Phase 10 gateway-route one):** `application-docker.yml`'s `management.endpoints.web.exposure.include` is a scalar property that fully overrides the base file's value under the docker profile — had to add `prometheus` there too, or it would silently vanish only in Docker. Tracing/probe/metrics-tag keys didn't need duplicating since the docker file doesn't redeclare those specific keys.
12. Verified via `mvn -pl services/gateway test`: 25/25 green, and the JSON log output confirms `correlationId` (existing `CorrelationIdFilter`) flows correctly through Reactor Context → MDC → JSON fields on the reactive side, same as the servlet services. Zipkin trace verification deferred to Sprint 5 E2E (needs the `zipkin` container from Sprint 4).

### Sprint 4 — Observability Stack + Docker Compose ✅ (Aug 24, 2026)
13. `observability/prometheus.yml` — 9 scrape targets (gateway + 8 servlet services), `/actuator/prometheus`, 15s interval
14. `observability/grafana/provisioning/` — datasource (Prometheus, `isDefault: true`) + file-based dashboard provisioner pointing at `/var/lib/grafana/dashboards`
15. `observability/grafana/dashboards/*.json` — **hand-built rather than imported** (decided against fetching community dashboard IDs: no verified network access in this environment, and hand-authored panels can target our exact metric names/labels instead of needing to be trimmed down from a generic import). Three dashboards: `platform.json` (HTTP rate/p95/5xx + JVM heap + HikariCP), `kafka.json` (consumer lag + fetch rate via `kafka_consumer_fetch_manager_records_lag`), `business.json` (order creation rate/p95 + payment success rate, reading the two Sprint 2 custom metrics — note `order.creation.duration` uses `publishPercentiles` so p95 is a `{quantile="0.95"}` gauge series, not a `_bucket` histogram, so its panel query is a direct gauge read, not `histogram_quantile(...)`)
16. `docker-compose.yml`: added `zipkin` (9411), `prometheus` (9090, mounts `observability/prometheus.yml`), `grafana` (3000, mounts provisioning + dashboards, anonymous Viewer access enabled for convenience); added `MANAGEMENT_ZIPKIN_TRACING_ENDPOINT: http://zipkin:9411/api/v2/spans` to all 10 app service environments. Per the anti-pattern list: **no `depends_on` from any app service onto zipkin/prometheus/grafana** — the Zipkin reporter is async and non-blocking, so app services start fine even if the observability stack is cold.
17. Validated: `docker compose config -q` passes (valid compose schema); all 3 dashboard JSON files parse correctly (`node -e "JSON.parse(...)"`). Docker daemon wasn't running in this environment so `docker compose up -d --build` itself is deferred to Sprint 5, where it needs to run against your local Docker anyway for the E2E trace/dashboard validation.

### Sprint 5 — Validation & Docs
18. E2E: create an order through the gateway → confirm one trace in Zipkin spans gateway→order-service→(Kafka)→inventory/payment/shipping/notification/analytics; confirm the trace's `traceId` appears in every service's JSON logs for that request
19. Confirm Grafana dashboards populate (generate some load first — a handful of orders via existing E2E scripts)
20. `PHASE_11_COMPLETE.md`, update `AGENTS.md` (phase status, decisions, gotchas, next-phase context for Phase 12 Security)

---

## 5. Anti-Patterns to Avoid

❌ Giving gateway a `shared-library` dependency to reuse the JSON logging/metrics config — breaks the reactive/servlet boundary (documented constraint since Phase 4). Duplicate the small config instead.
❌ Using `spring.zipkin.base-url` (Spring Boot 2 property, present in IMPLEMENTATION_PLAN.md's example) — Spring Boot 3 uses `management.zipkin.tracing.endpoint`.
❌ Writing a custom DB/Kafka `AbstractHealthIndicator` before checking whether Spring Boot 3 autoconfiguration already provides it — don't duplicate what's already there.
❌ Conflating `X-Correlation-Id` (business/saga-level, existing) with Micrometer's `traceId`/`spanId` (per-hop tracing, new) — keep them as two distinct MDC fields, both logged.
❌ Hard-gating app service startup on Grafana/Prometheus/Zipkin health in `docker-compose.yml` `depends_on` — the order-processing services should start fine even if the observability stack is briefly cold.
❌ Sampling probability `< 1.0` isn't wrong, but for a low-traffic portfolio project it just hides traces you want to demo — keep it at `1.0` here.

---

## 6. Success Checklist

- [ ] All modules build: `mvn clean install` green (existing 181 tests, no regressions expected — Phase 11 is config + cross-cutting wiring, minimal new business-logic code)
- [ ] Every service (incl. gateway) emits JSON logs with `service`, `correlationId`, `traceId`, `spanId` fields
- [ ] `/actuator/prometheus` returns metrics on all 10 services; Prometheus UI shows all targets `UP`
- [ ] `/actuator/health/liveness` and `/actuator/health/readiness` return 200 on all services
- [ ] A request through the gateway produces a single Zipkin trace spanning every service it touches
- [ ] Grafana shows all 3 dashboards (platform, Kafka lag, business) with real data after generating some order traffic
- [ ] `docker compose up -d --build` — all containers healthy, including `zipkin`, `prometheus`, `grafana`
- [ ] `AGENTS.md` + `PHASE_11_COMPLETE.md` updated

**Open decisions to confirm before/during Sprint 1:**
- Whether to hand-build Grafana dashboards or start from imported community dashboard IDs (recommend: import + trim, faster and battle-tested)
- Whether alert rules (mentioned in IMPLEMENTATION_PLAN.md's acceptance criteria) are in scope — no alerting channel (Slack/email/PagerDuty) exists in this project, so recommend defining Prometheus alert *rules* only (visible in Prometheus UI) without wiring Alertmanager notification routing, unless you want that too
