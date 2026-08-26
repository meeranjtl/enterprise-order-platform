# Phase 13 — React UI: Getting Started

**Date:** August 25, 2026
**Status:** ✅ Complete — see [PHASE_13_COMPLETE.md](PHASE_13_COMPLETE.md) for validation results
**Predecessor:** Phase 12 complete (Security — JWT auth, RBAC) — see `PHASE_12_COMPLETE.md`

Phase 13 builds the first (and only) UI for a platform that has been
API-only through 12 phases. It is a **pure consumer** of the existing
gateway — no backend changes. Scope: a React dashboard covering
customers, products, orders, payments, Kafka event visibility, system
health, and JWT-based auth, all routed through the gateway at `:8080`
per `docs/architecture.md`'s topology diagram (`React UI (Phase 13) →
API Gateway → Services`).

---

## 1. What Already Exists (the API surface the UI consumes)

| Piece | Where | Status |
|---|---|---|
| Gateway, single entry point | `:8080`, all routes documented in `docs/architecture.md` | ✅ Done — UI must never call a service port (8081-8088) directly |
| Auth endpoints | `POST /api/auth/{register,login,refresh,logout}` via customer-service, routed through gateway, pre-auth permitted | ✅ Done (Phase 12) |
| Access/refresh token scheme | Access 15 min (`type: access`), refresh 7 days (`type: refresh`), single active session per customer | ✅ Done — UI's token refresh logic must respect the 15-min window and handle a `401` from a stale access token by transparently refreshing once, not looping |
| Roles | `CUSTOMER`, `ADMIN` in the JWT `roles` claim | ✅ Done — UI role-gates admin-only actions (customer/product writes, etc.) but treats this as UX only; the server remains the actual enforcement point |
| CORS | gateway `globalcors`, `Authorization` header + credentials allowed | ✅ Done — verify it also allows the UI's dev origin (`localhost:5173` for Vite) and add if missing |
| Rate limiting | Redis-backed, keyed by JWT `sub`, `429` body carries `RATE_LIMIT_EXCEEDED` + `Retry-After` | ✅ Done — UI should surface `429` as a distinct "slow down" message, not a generic error |
| `BaseResponse<T>` envelope | every REST response, shared-library | ✅ Done — API client unwraps this consistently across all 9 services |
| No WebSocket/SSE anywhere in the platform | — | Real-time UI sections (Kafka events, order status, health) must use **polling**, not a socket — matches the plan's own "polling or WebSocket" acceptance criterion, and adding a socket layer now would be new backend scope out of bounds for Phase 13 |

**Hard constraint carried over from every prior phase:** the UI is a new
top-level thing at repo root (`ui/`), not a Maven module — it does not
touch `pom.xml`, does not go in `services/`, and has its own
`package.json`/build toolchain entirely separate from the Java build.

---

## 2. Deliverables (from IMPLEMENTATION_PLAN.md §Phase 13)

- [ ] React project setup (Vite)
- [ ] Dashboard page (stats, recent orders, revenue chart, top products, system health)
- [ ] Customers page (CRUD, admin-gated writes)
- [ ] Products page (browse, filter by category/price)
- [ ] Orders page (create, list, detail with status timeline)
- [ ] Payments page
- [ ] Kafka events monitoring (polling-based feed)
- [ ] System health/metrics display (per-service status)
- [ ] Authentication UI (login/register, token refresh, role-based rendering)
- [ ] Responsive design (desktop + mobile)
- [ ] Docker image, served on port 3000

---

## 3. Design

### 3.1 Tech Stack

| Concern | Choice | Why |
|---|---|---|
| Build tool | **Vite** (not CRA — deprecated) | Matches `IMPLEMENTATION_PLAN.md`'s "Vite/CRA" option; fast dev server, standard for new React projects |
| Language | **TypeScript** | The rest of this platform is statically typed (Java); typed DTOs matching the backend's `*DTO` classes catch integration bugs at compile time instead of in the browser |
| Routing | **React Router v6** | Standard, matches the `pages/` structure already sketched in the plan |
| Server state | **TanStack Query** | Gives caching, polling (`refetchInterval`) for the Kafka/health/order-status "real-time" requirement, and request de-duping for free — avoids hand-rolled `useEffect` fetch/loading/error boilerplate across 6+ pages |
| Client state | React Context only (`AuthContext`, as the plan specifies) | No Redux/Zustand — nothing in this app needs global client state beyond the authenticated user; adding a state library would be unjustified per this repo's own anti-abstraction convention |
| Forms | **React Hook Form + Zod** | Typed validation schemas can mirror the backend's Bean Validation constraints (e.g. customer email format, order quantity > 0) |
| Styling | **Tailwind CSS + shadcn/ui** (Radix primitives) | See §3.3 — this is the look-and-feel decision |
| Charts | **Recharts** | Revenue chart, analytics — lightweight, composable |
| Icons | **lucide-react** | Pairs with shadcn/ui by default |
| HTTP | **axios**, one `apiClient` instance per plan's `api.js` sketch | Interceptor for `Authorization` header + silent refresh-on-401 |

### 3.2 Project Structure

Follows the structure already sketched in `IMPLEMENTATION_PLAN.md`
§13.1 almost exactly, with `.tsx`/`.ts` extensions and a `types/` folder
added for the DTO mirrors:

```
ui/
├── src/
│   ├── components/       # shared building blocks (Table, StatusBadge, StatCard, Sidebar...)
│   ├── pages/             # Dashboard, Customers, Products, Orders, Payments, KafkaEvents, Health, Login
│   ├── hooks/              # useAuth, usePolling wrappers around TanStack Query
│   ├── services/            # api.ts, auth.ts — axios instance + typed endpoint calls
│   ├── context/               # AuthContext.tsx
│   ├── types/                   # TS interfaces mirroring backend DTOs (CustomerDTO, OrderDTO, ...)
│   └── App.tsx
├── package.json
├── vite.config.ts
├── tailwind.config.ts
└── Dockerfile
```

### 3.3 Look and Feel — Modern Web Design Direction

Goal: read as a **professional internal ops/admin console** (think
Linear, Vercel's dashboard, Stripe's dashboard) rather than a consumer
storefront — appropriate for a platform whose actual users are
customers/admins managing orders, not shoppers.

**Visual system**
- **Palette:** neutral base (Tailwind `zinc`/`slate` grays) for chrome,
  backgrounds, and text, with **one** accent color (indigo or violet)
  reserved for primary actions, links, and active nav state. Avoid
  multi-color decoration — the only place color multiplies is status
  badges (below).
- **Dark mode:** ship it from day one via a `class`-based Tailwind theme
  + a toggle in the top bar — expected by default in 2026 for anything
  that looks like a dev/ops tool, and it's nearly free with Tailwind +
  shadcn/ui's existing dark-mode tokens.
- **Typography:** Inter (UI text) + a monospace face (JetBrains Mono or
  ui-monospace) for anything identifier-shaped — order IDs, correlation
  IDs, Kafka event payloads — which ties visually back to the
  correlation-ID/tracing work from Phase 11.
- **Shape language:** `rounded-lg` (8-10px) corners, thin 1px borders
  (`border-zinc-200`/`border-zinc-800`) instead of heavy drop shadows —
  flatter, calmer than a card-heavy Bootstrap look.
- **Density:** compact, information-dense tables and stat rows over
  large marketing-style whitespace — this is a working tool, not a
  landing page.

**Layout**
- Fixed left sidebar (collapsible to icon-only rail) for primary nav —
  Dashboard / Customers / Products / Orders / Payments / Kafka Events /
  Health — plus a top bar with breadcrumb, the dark-mode toggle, and a
  user menu (shows role badge: `ADMIN`/`CUSTOMER`).
- Mobile: sidebar collapses into a slide-over drawer behind a hamburger;
  data tables reflow into stacked cards below `sm` breakpoint rather
  than horizontally scrolling.

**Key patterns**
- **Status badges:** small color-coded pill for every order/payment/
  shipment status — amber (pending), blue (processing/confirmed), indigo
  (shipped), green (delivered/completed), red (cancelled/failed) —
  reused everywhere a status appears so the color vocabulary stays
  consistent across Orders, Payments, and the Dashboard's recent-orders
  list.
- **Loading state:** skeleton placeholders (shadcn/ui `Skeleton`), not
  spinners — feels faster and avoids layout shift when data lands.
- **Feedback:** toast notifications (`sonner`, pairs with shadcn/ui) for
  mutation success/error instead of `alert()`/inline-only banners;
  distinct toast copy for the `429` rate-limit case per §1.
- **Dashboard stat cards:** 4-across KPI tiles (Total Orders, Revenue,
  Active Customers, Pending Shipments) each with a small trend
  indicator, above the revenue chart and recent-orders table — standard
  modern-dashboard "stat row → chart → table" composition.
- **Kafka Events page:** styled as a live-feeling log/timeline (polling
  every 3-5s via TanStack Query `refetchInterval`), each entry tagged
  with a topic badge (`order-events`, `payment-events`, etc.) and
  monospace payload preview — communicates "event bus" visually without
  needing an actual socket.
- **System Health page:** one row per service (9 + gateway) with a
  colored status dot (green/amber/red) sourced from each service's
  `/actuator/health` via the gateway, refreshed on a poll — mirrors the
  Phase 11 observability investment instead of duplicating Grafana.
- **Motion:** short, subtle transitions only (150-200ms ease-out) on
  hover/focus/route change — no decorative animation.
- **Accessibility:** visible focus rings (shadcn/ui default), sufficient
  contrast in both themes, admin-only controls hidden *and*
  `aria-disabled`/tooltip-explained rather than just vanishing, so
  role-based UI doesn't read as broken to a `CUSTOMER` user poking
  around.

### 3.4 API Integration & Auth

- `services/api.ts`: axios instance, `baseURL` from
  `VITE_API_URL` (default `http://localhost:8080/api/v1`), request
  interceptor attaches `Authorization: Bearer <access_token>` from
  memory (not `localStorage` for the access token — see anti-patterns).
- Response interceptor: on a `401`, attempt exactly **one** silent
  `POST /api/auth/refresh` using the refresh token, retry the original
  request once, then hard-redirect to `/login` if that also fails — a
  queue/mutex around the refresh call prevents concurrent requests each
  firing their own refresh (the backend's single-active-session model
  means a second concurrent refresh would invalidate the first).
- Refresh token: `localStorage` is acceptable (it's opaque and
  server-validated), access token stays in memory only (React Context),
  matching common JWT-in-SPA guidance and reducing XSS blast radius.
- Role-based rendering: `AuthContext` exposes `role`; components use a
  small `<RequireRole role="ADMIN">` wrapper rather than scattering
  `if (role === 'ADMIN')` checks through every page.

---

## 4. Sprint Plan

### Sprint 0 — Scaffold
1. `npm create vite@latest ui -- --template react-ts`; Tailwind +
   shadcn/ui init; ESLint/Prettier matching the repo's existing
   formatting conventions
2. `api.ts` client + `AuthContext` + login/register pages; verify a
   live login round-trip against the running gateway (`docker compose up`
   from Phase 12's stack) before building anything else

### Sprint 1 — Shell & Read-Only Pages
3. `App.tsx` routing, `Sidebar`/`TopBar` layout shell, dark-mode toggle
4. Products page (browse/filter) — read-only, good first page to prove
   the table/pagination/filter patterns other pages reuse
5. Customers page (list + detail), admin-gated create/edit forms

### Sprint 2 — Orders & Payments
6. Orders: creation form, list, detail view with status timeline
7. Payments page
8. Status badge component + color vocabulary applied consistently

### Sprint 3 — Dashboard, Kafka, Health
9. Dashboard: stat cards, revenue chart (Recharts), recent orders,
   top products, health summary strip
10. Kafka Events page (polling feed)
11. System Health page (per-service status via `/actuator/health`)

### Sprint 4 — Polish & Ship
12. Responsive pass (mobile drawer nav, card-reflow tables)
13. Error/empty/loading states audited across all pages; toast wiring
14. `Dockerfile` (multi-stage: `npm run build` → nginx serving on
    `:3000`), verify it builds and serves standalone
15. `PHASE_13_COMPLETE.md`, update `AGENTS.md` (phase status, next-phase
    context for Phase 14), append any new gotchas/invariants

---

## 5. Anti-Patterns to Avoid

❌ Calling a service port (`:8081`-`:8088`) directly from the UI —
everything routes through the gateway at `:8080`, per
`docs/architecture.md`.
❌ Storing the **access** token in `localStorage` — keep it in-memory
(React Context) to limit XSS exposure; the refresh token in
`localStorage` is the accepted tradeoff.
❌ Letting multiple concurrent `401`s each trigger their own
`/api/auth/refresh` call — the backend's single-active-session model
means the second refresh invalidates the first's new token pair,
causing a spurious logout. Queue/dedupe refresh calls.
❌ Building a WebSocket/SSE layer to make Kafka Events or order status
feel "real-time" — no such backend capability exists yet; that's new
service-layer scope outside Phase 13. Poll instead.
❌ Trusting client-side role checks as security — they're UX only
(hiding/disabling controls); the server's `@PreAuthorize` from Phase 12
remains the actual enforcement.
❌ Pulling in a global state library (Redux/Zustand) or a second data-
fetching library — TanStack Query + Context covers everything this app
needs; matches the repo's stated anti-abstraction convention.
❌ Reinventing the status-badge/color mapping per page — one shared
`StatusBadge` component keyed off a single color vocabulary, reused on
Dashboard, Orders, and Payments alike.

---

## 6. Success Checklist

- [ ] `npm run build` produces a deployable static bundle
- [ ] Live login → token refresh → logout flow works against the real
      gateway (not mocked)
- [ ] All pages responsive on desktop/mobile
- [ ] CRUD operations work end-to-end against real services
- [ ] Role-based UI rendering verified for both `CUSTOMER` and `ADMIN`
- [ ] Kafka Events and Health pages poll and update without a refresh
- [ ] Error states (401/403/429/5xx) each render a distinct,
      user-friendly message
- [ ] Docker image builds and serves the UI on port 3000
- [ ] `AGENTS.md` + `PHASE_13_COMPLETE.md` updated
