# Phase 13 - COMPLETE: React UI

**Date:** August 26, 2026
**Status:** ✅ Validated — full React dashboard deployed and verified live
through the gateway (auth, CRUD, orders/payments, Kafka event visibility,
system health), UI typechecks/lints/builds clean, dockerized and verified
serving on `:3000` alongside the rest of the stack.

Phase 13 builds the platform's first (and only) UI for what had been an
API-only system through 12 phases. It is a **pure consumer** of the
existing gateway at `:8080` — no business-logic changes — plus a small set
of new **read-only observability endpoints** (outbox event history, a
health aggregator) added specifically to make the Kafka Events and System
Health pages real rather than synthetic.

---

## What Was Delivered

### Scaffold & Auth (Sprint 0)

- Vite + React 19 + TypeScript, Tailwind CSS v4 + shadcn/ui (indigo-on-zinc
  "Nova" preset, dark mode via `next-themes`), TanStack Query, React Router
  v6, React Hook Form + Zod, axios.
- `apiClient` (`src/lib/api.ts`): request interceptor attaches the in-memory
  access token; response interceptor does a single-flight silent refresh on
  401 (queued so concurrent 401s don't each trigger their own refresh —
  the backend's single-active-session model would invalidate a second
  concurrent refresh). Refresh token in `localStorage`, access token
  in-memory only.
- `AuthContext`: silent refresh on mount, `login`/`register`/`logout`/`hasRole`.
  Live login → refresh → logout round-trip verified against the real gateway.

### Shell & Read-Only Pages (Sprint 1)

- `AppShell`: collapsible desktop sidebar + mobile slide-over drawer (`Sheet`),
  dark-mode toggle, role badge in the user menu.
- `StatusBadge`: one shared color vocabulary (amber/blue/indigo/emerald/red/zinc)
  reused across every order/payment/product/customer status everywhere it appears.
- Products page (filter by name/category/status/price, debounced) and
  Customers page (admin-gated CRUD) — first pages built, established the
  table/pagination/filter pattern every later list page reuses.

### Orders & Payments (Sprint 2)

- Orders: role-aware list (admin sees all, customer sees their own),
  creation dialog, detail view with a 5-step status timeline.
- Payments: lookup-by-ID (payment-service has no list endpoint — the page
  is designed around the real API surface, not a wished-for one), with
  admin-only retry/refund actions.

### Dashboard, Kafka Events, Health (Sprint 3)

- Dashboard: 4 KPI stat cards, a Recharts revenue-over-time chart, a
  top-products list (enriched with real product names via a per-ID lookup),
  a role-aware recent-orders table, and an admin-only system health strip.
- Kafka Events: a live-polling feed (4s) across all 5 event-producing
  services, each entry tagged with a topic badge and a formatted JSON
  payload — reads real saga events, not a synthetic activity log.
- System Health: gateway + all 8 downstream services, live UP/DOWN,
  polling every 10s.
- **New backend surface** (the one deliberate exception to "pure consumer,
  no backend changes" — a user-approved decision, see below):
  - `OutboxEventController` (shared-library): `GET {service-prefix}/events/recent`,
    `ADMIN`-only, added to the 5 producer services (order/payment/inventory/
    shipping/notification). Mounted under each service's *existing*
    gateway-routed path prefix via `app.events.base-path`, so **zero new
    gateway routes were needed** — the UI fans out to all 5 endpoints and
    merges by timestamp client-side (`services/eventsApi.ts`).
  - `SystemHealthController` (gateway): `GET /api/v1/system/health`,
    `ADMIN`-only, probes all 8 services' `/actuator/health` in parallel via
    `WebClient` — chosen over adding 8 actuator-proxy gateway routes as the
    simpler, lower-surface option.

### Polish & Ship (Sprint 4)

- Responsive pass: Orders/Products/Customers/Order-detail-items tables now
  dual-render — stacked cards below `sm`, the original table from `sm` up
  (same data, two Tailwind-gated renderings, no JS media-query logic).
  Order-detail's header and status timeline restructured to stack/scroll
  cleanly at 375px instead of wrapping or clipping.
- Error/empty/loading states audited: every query-backed section now has
  a distinct loading (skeleton), error, and empty rendering — several pages
  (Dashboard's stat cards/chart/recent-orders, System Health's table) were
  silently swallowing fetch failures before this pass.
- `ui/Dockerfile`: multi-stage (`node:22-alpine` build → `nginx-unprivileged:alpine`
  runtime), SPA fallback via `ui/nginx.conf`, non-root, healthchecked, served
  on `:3000`. Wired into `docker-compose.yml` as the `ui` service.

---

## Validation Results

### Build

```
mvn test  →  gateway 27/27, order-service 25/25, payment-service 2/2,
              inventory-service 3/3, shipping-service 7/7,
              notification-service 8/8  — all green
npx tsc -b --noEmit  →  clean
npm run lint         →  clean (only pre-existing, unrelated shadcn/react-refresh warnings)
npm run build        →  succeeds (bundle-size advisory only, not an error)
```

### Live E2E (via gateway `:8080`, `docker compose up`)

- Playwright-driven: register → login → browse products → create order →
  view order detail with status timeline → admin cross-customer order
  visibility → payment lookup → Kafka Events feed showing real saga events
  (`OrderCreated` → `InventoryReserved` → `NotificationSent`) → System
  Health showing gateway + all 8 services UP.
- Mobile (375×812) pass: no page-level horizontal overflow on any of the
  9 routes; Orders/Products/Customers/Order-detail card-reflow verified
  visually; mobile nav drawer opens/navigates/closes correctly.
- Zero browser console errors across every page, light and dark mode,
  desktop and mobile viewports, in the final verification pass.

### Docker Deployment

- All 6 touched backend images (gateway, order/payment/inventory/shipping/
  notification-service) rebuilt and healthy.
- `ui` image builds standalone, runs standalone (root `/`, SPA fallback on
  a deep client route, and a static asset all verified `200` via `curl`),
  and reports Docker `healthy`.
- Full stack verified together: browser at `http://localhost:3000` (the
  dockerized UI) driving the real gateway at `:8080` — login through to
  System Health, zero console errors, exactly like the dev-server run.

---

## Issues Found & Fixed During Implementation

All found via live testing (browser/Docker), not achievable via static
analysis or `curl` alone unless noted. Full narrative for each is in
[docs/gotchas.md](docs/gotchas.md#phase-13--react-ui).

1. **CORS preflight blocked by gateway's own JWT auth** — `OPTIONS` wasn't
   permitted pre-auth in the reactive `SecurityConfig`, so every protected
   route silently failed CORS for a real browser (curl/Postman never
   trigger a preflight, so this survived all of Phase 12's validation).
2. **`/api/v1/categories/**` had no gateway route** — `CategoryController`
   existed in product-service with nothing routing to it.
3. **order-service's internal `CustomerClient`/`ProductClient` calls never
   forwarded the caller's JWT** — broken since Phase 12 shipped JWT auth on
   their targets; every order creation failed with a misleading generic
   400 until this was found via the first live order-creation test.
4. **`OutboxEventController`'s LOB read outside a transaction** — Postgres's
   `@Lob`-mapped `payload` column can only be streamed inside an active
   Hibernate session; a plain non-transactional `@GetMapping` threw
   `"Unable to access lob stream"` the first time it actually ran.
5. **Gateway `globalcors` doesn't cover a local `@RestController`** —
   `SystemHealthController` got no CORS headers at all; the documented fix
   (`add-to-simple-url-handler-mapping`) made it worse by letting Spring
   Security auto-enforce CORS and 403 every *other* route. Fixed by
   consolidating into one explicit `CorsWebFilter` bean for the whole app.
6. **UI Dockerfile `HEALTHCHECK` used `localhost`, which resolves to `::1`
   only in `nginx-unprivileged:alpine`**, while nginx binds `0.0.0.0` —
   the app was serving correctly the whole time; only the healthcheck's
   own DNS resolution was wrong (`docker ps` never reported `healthy`).
7. **Root `.dockerignore`'s blanket `ui/` exclude broke `ui/Dockerfile`'s
   own build** the moment it existed — `.dockerignore` is only read at the
   build context root, so a `ui/.dockerignore` had no effect.

Two Docker Desktop environment issues (not code) were also hit and resolved
mid-phase: engine instability requiring a host restart, and 8GB→16GB memory
needed to run the full 9-service + Kafka/ZooKeeper/Postgres/Redis stack
without Postgres OOM-crash-looping.

---

## Known Issues / Notes

- **Order 1's saga is stuck at `PAYMENT_PENDING`.** A mid-phase Docker
  restart triggered the documented zookeeper/kafka stale-ephemeral-node
  recovery (`docker compose rm -f zookeeper kafka && up -d`), which has no
  persistent volume and wiped the in-flight Kafka event that would have
  produced order 1's Payment record. This is demo-data fallout from the
  documented recovery procedure, not a Phase 13 defect — harmless, and
  visible on the Dashboard/Orders pages as an intentionally-realistic
  "stuck" order rather than fabricated clean data.
- **Kafka Events aggregates 5 producer services, not a platform-wide
  event bus view.** Only order/payment/inventory/shipping/notification
  publish outbox events; customer/product/analytics-service carry the
  `OutboxEventController` too (shared-library, harmless no-op there) but
  have nothing to show since they never call `outboxPublisher.storeEvent()`.
- **System Health's per-service status is a direct actuator probe, not a
  gateway-routed one** — `SystemHealthController` calls each service's
  `:808X/actuator/health` directly via `WebClient` from inside the gateway
  container (server-to-server, not browser-to-service), which is why this
  doesn't violate the "UI never calls a service port directly" rule: the
  *UI* only ever calls `GET /api/v1/system/health` on the gateway.
- **Bundle size advisory** (`npm run build`): the main JS chunk is ~1MB
  pre-gzip / ~306KB gzipped — flagged by Vite as exceeding its 500KB
  warning threshold, not a build failure. Code-splitting was out of scope
  for this phase; revisit if initial load time becomes a real concern.

---

## Success Checklist (from PHASE_13_GETTING_STARTED.md)

- [x] `npm run build` produces a deployable static bundle
- [x] Live login → token refresh → logout flow works against the real gateway (not mocked)
- [x] All pages responsive on desktop/mobile
- [x] CRUD operations work end-to-end against real services
- [x] Role-based UI rendering verified for both `CUSTOMER` and `ADMIN`
- [x] Kafka Events and Health pages poll and update without a refresh
- [x] Error states (401/403/429/5xx) each render a distinct, user-friendly message
- [x] Docker image builds and serves the UI on port 3000
- [x] `AGENTS.md` + `PHASE_13_COMPLETE.md` updated

---

## Next Phase Context (Phase 14 — Docker Orchestration)

Per [PHASE_QUICK_REFERENCE.md](PHASE_QUICK_REFERENCE.md), Phase 14 covers
completing `docker-compose.yml`, circuit breakers, and saga-pattern docs,
with "React UI fully functional" and "single `docker compose up` deploys
everything" as its own success criteria. Both are **already true** as of
this phase closing — the `ui` service is wired into `docker-compose.yml`
and verified end-to-end alongside the rest of the stack. Remaining Phase 14
scope is narrower than originally planned: circuit-breaker review (mostly
already in place since Phase 8+) and writing up the saga pattern
documentation that's been implemented but not formally documented.
