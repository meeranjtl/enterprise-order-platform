# Phase 12 — Security: Getting Started

**Date:** August 25, 2026
**Status:** ✅ Complete — see [PHASE_12_COMPLETE.md](PHASE_12_COMPLETE.md) for validation results
**Predecessor:** Phase 11 complete (Observability) — see `PHASE_11_COMPLETE.md`

Phase 12 adds authentication and authorization to a platform that
currently has **none** — every one of the 9 services + gateway is wide
open. Scope: JWT login/refresh, role-based access control (`CUSTOMER`,
`ADMIN`), CORS (already largely done in Phase 4/9), and per-user rate
limiting (gateway's `RateLimiterConfig.java` already has a comment
predicting this phase). **Decision confirmed with the platform owner:
no new auth-service** — `customer-service` becomes the JWT issuer, since
its `Customer.email` is already the natural username. This keeps the
platform at 9 services + gateway as documented in `AGENTS.md`.

---

## 1. What Already Exists (don't rebuild this)

| Piece | Where | Status |
|---|---|---|
| Centralized CORS (`Authorization` header allowed, credentials on) | `gateway/application.yml` `globalcors` | ✅ Done since an earlier phase — verify it still matches the Phase 12 spec, don't re-add |
| Redis-backed IP rate limiting | `gateway/.../filter/RateLimitResponseFilter.java`, `config/RateLimiterConfig.java` (`ipKeyResolver`) | ✅ Done — comment in `RateLimiterConfig.java` already flags a per-user `KeyResolver` for Phase 12 |
| `UnauthorizedException` (401) / `ForbiddenException` (403) | `shared-library/.../shared/exception/` | ✅ Done — reuse, don't add new exception types for auth errors |
| `GlobalExceptionHandler` (`@RestControllerAdvice`) | `shared-library/.../shared/config/` | ✅ Done — new `SecurityConfig` must coexist with it, not replace it |
| `Customer` entity with unique `email` | `customer-service/.../entity/Customer.java` | ✅ Done — extend with password/role, don't create a parallel `User` entity |

**Gateway constraint (unchanged, critical):** gateway is reactive (Netty)
and must **never** depend on `shared-library` (servlet-based). The JWT
resource-server config added to shared-library for the 8 servlet services
needs a **duplicated, reactive-safe equivalent** directly in gateway's
own module — same pattern Phase 11 used for JSON logging.

No security dependency (Spring Security, `oauth2-resource-server`, JJWT)
exists in any of the 11 `pom.xml` files today — clean slate, zero
collision risk.

One deferred item from Phase 11: a full `mvn clean install` platform-wide
validation was explicitly punted to Phase 12 (see `docs/gotchas.md`).
Run it first in Sprint 0, so any failure it surfaces isn't confused with
new Phase 12 work.

---

## 2. Deliverables (from IMPLEMENTATION_PLAN.md §Phase 12)

- [ ] JWT token generation/validation
- [ ] Refresh token mechanism (7-day, cannot be used as an access token)
- [ ] Role-based authorization (`CUSTOMER`, `ADMIN`) via `@PreAuthorize`
- [ ] CORS configuration — verify existing config against the spec
- [ ] Rate limiting per user — swap gateway's IP `KeyResolver` for a JWT-subject one
- [ ] Secure password handling (BCrypt)
- [ ] ~~API key authentication~~ → marked optional in the plan; **deferred**, not needed for this platform's client model
- [ ] ~~OAuth2 integration~~ → marked optional in the plan; **deferred**, no external IdP in scope

---

## 3. Design

### 3.1 Token Scheme

HS256, one shared secret (`jwt.secret`). Following this repo's existing
secrets convention (no `${ENV_VAR:default}` pattern exists anywhere —
every service hardcodes a local dev default in `application.yml` and
`docker-compose.yml` overrides it per-service via a plain `environment:`
block, e.g. `SPRING_DATASOURCE_PASSWORD`): add `jwt.secret` with a dev
default in each `application.yml`, and `JWT_SECRET` to each of the 9
`docker-compose.yml` service blocks (gateway + 8 servlet services — no
shared `.env`/YAML anchors exist in this compose file, so this is 9
individual edits).

- **Access token:** 15 min expiry. Claims: `sub` = `Customer.id` (lines
  up directly with the `customerId` fields already used across
  order-service etc.), `roles` = `["CUSTOMER"]` or `["ADMIN"]`,
  `type: "access"`.
- **Refresh token:** 7 day expiry, `type: "refresh"`. A custom claim
  check in the resource-server config rejects any `refresh`-typed token
  at a protected endpoint — this is what satisfies the acceptance
  criterion "refresh token cannot be used as access token."
- **Refresh state:** `refreshTokenHash` + `refreshTokenExpiresAt` columns
  directly on `Customer` — single active session (logging in again, or
  `/api/auth/logout`, invalidates the previous refresh token). Keeps
  access-token validation fully stateless (no DB hit on the hot path);
  only `/api/auth/refresh` touches the DB. **Known simplification:** no
  multi-device sessions — acceptable for this project's scope, revisit
  only if that becomes a concrete requirement.

### 3.2 Roles

Single `role` enum column (`CUSTOMER`, `ADMIN`) on `Customer` — not a
many-to-many roles table (YAGNI for a 2-role model).
`/api/auth/register` always creates `CUSTOMER` and never accepts a
client-supplied role, to avoid privilege escalation. One `ADMIN` row is
seeded via the Flyway migration for local testing/Swagger use.

### 3.3 Where the Code Lives

- **`shared-library/.../shared/config/`** — new `JwtDecoderConfig`
  (`NimbusJwtDecoder.withSecretKey(...)` from `jwt.secret`) and
  `SecurityConfig` (`@EnableWebSecurity` + `@EnableMethodSecurity`,
  permits `/actuator/health/**`, `/api-docs/**`, `/swagger-ui/**`,
  authenticates everything else, `JwtAuthenticationConverter` maps the
  `roles` claim to `ROLE_*` authorities). All 8 servlet services inherit
  this for free via the existing "add cross-cutting concern to
  shared-library first" rule. `customer-service` additionally permits
  `/api/auth/**` (pre-auth endpoints).
- **`customer-service`** — `V2__add_auth_fields.sql` (Flyway) adding
  `password`, `role`, `refresh_token_hash`, `refresh_token_expires_at` to
  `customers` + one seeded admin row; matching `Customer` entity fields;
  `AuthController` (sibling of `CustomerController`, same
  `@RequiredArgsConstructor`/Slf4j/Swagger style) exposing
  `POST /api/auth/{register,login,refresh,logout}`; `JwtTokenProvider`
  for issuing tokens + a `PasswordEncoder` (BCrypt) bean; reuse
  `UnauthorizedException` for bad credentials / expired-or-reused
  refresh tokens.
- **`gateway`** — standalone reactive `SecurityWebFilterChain`
  (`ReactiveJwtDecoder`/`NimbusReactiveJwtDecoder`, same secret/claims
  logic as shared-library's, duplicated per the reactive/servlet
  boundary rule). New gateway route for `/api/auth/**` → customer-service
  (doesn't exist today — customer-service is currently only routed under
  `/api/v1/customers/**`), permitted pre-auth. New `jwtKeyResolver`
  `KeyResolver` bean next to the existing `ipKeyResolver` (resolves to
  the JWT `sub` claim when `Authorization` is present, else falls back to
  IP); swap `application.yml`'s
  `default-filters.RequestRateLimiter.args.key-resolver` to it.
- **Per-endpoint `@PreAuthorize`** — applied per the spec's examples
  (e.g. `OrderController`: reads allow `CUSTOMER` or `ADMIN`, create
  requires `CUSTOMER`). `Order.customerId` is a plain `Long` with no
  relation, so ownership on `GET /orders/{id}` (no `customerId` in the
  path) needs a small `@Component("orderSecurity")` bean —
  `isOwner(Long orderId, Authentication auth)`, loads the order and
  compares `customerId` to the token's `sub` — referenced as
  `@PreAuthorize("hasRole('ADMIN') or @orderSecurity.isOwner(#id, authentication)")`.
  `GET /orders/customer/{customerId}` already takes the id as a path var,
  so it can compare directly in the SpEL expression without a bean.

---

## 4. Sprint Plan

### Sprint 0 — Baseline
1. Run the full `mvn clean install` deferred from Phase 11 — confirm a
   clean starting point before layering security on top.

### Sprint 1 — Shared Foundation
2. `shared-library` pom: `spring-boot-starter-oauth2-resource-server`,
   `spring-boot-starter-security` (for `@PreAuthorize`/method security)
3. `JwtDecoderConfig` + `SecurityConfig` in shared-library
4. `jwt.secret` property (dev default) in every servlet service's
   `application.yml`; `JWT_SECRET` env var in `docker-compose.yml` for
   gateway + all 8 servlet services

### Sprint 2 — customer-service (Issuer)
5. `V2__add_auth_fields.sql` migration + `Customer` entity fields
6. `JwtTokenProvider` (generate/validate access + refresh, BCrypt
   `PasswordEncoder` bean)
7. `AuthController` (register/login/refresh/logout)
8. Unit tests for `JwtTokenProvider`; `AuthControllerIT` (TestContainers,
   matching existing `XxxIT` convention) for the four endpoints'
   happy/error paths

### Sprint 3 — Wire the Other 7 Servlet Services
9. Each picks up shared-library's `SecurityConfig` automatically once the
   pom dependency lands (no per-service security config needed)
10. Add `@PreAuthorize` per controller per the spec; order-service gets
    the `orderSecurity` ownership bean

### Sprint 4 — Gateway
11. Reactive `SecurityWebFilterChain` (standalone, mirrors shared-library
    logic)
12. New `/api/auth/**` route → customer-service, permitted pre-auth
13. `jwtKeyResolver` bean + swap the default rate-limiter key resolver

### Sprint 5 — Validation & Docs
14. Acceptance criteria pass (below) + `docker compose up -d --build`
    (sequential rebuild per `docs/gotchas.md`) — real login → protected
    call → refresh → logout flow through the gateway
15. `PHASE_12_COMPLETE.md`, update `AGENTS.md` (phase status, decisions,
    next-phase context for Phase 13), append any new gotchas to
    `docs/gotchas.md` / invariants to `docs/domain-rules.md`, update
    `docs/architecture.md` if the new gateway route changes the routing
    table there

---

## 5. Anti-Patterns to Avoid

❌ Adding a new auth-service — decided against; would break the
documented "9 services + gateway" count for no real benefit here.
❌ Giving gateway a `shared-library` dependency to reuse the JWT config —
breaks the reactive/servlet boundary. Duplicate the small config instead.
❌ Letting `/api/auth/register` accept a client-supplied `role` —
privilege escalation. Always force `CUSTOMER`.
❌ Validating refresh tokens against the DB on every access-token-guarded
request — only `/api/auth/refresh` should touch the DB; access-token
validation must stay stateless.
❌ A `@PreAuthorize` SpEL expression pretending `Order` has a path-derived
`customerId` when the endpoint is `GET /orders/{id}` — it doesn't; use
the `orderSecurity` bean instead of a broken/overly-permissive SpEL guess.
❌ Adding new `ApplicationException` subclasses for auth errors —
`UnauthorizedException`/`ForbiddenException` already exist in
shared-library; reuse them.

---

## 6. Success Checklist

- [ ] `mvn clean install` green platform-wide (baseline in Sprint 0, then
      again after all changes)
- [ ] JWT tokens generated and validated correctly; unauthorized → 401,
      forbidden → 403
- [ ] Refresh tokens work; a refresh token rejected at a protected
      endpoint (cannot be used as an access token)
- [ ] CORS headers present (verify existing gateway config, no regression)
- [ ] Rate limiting still triggers 429, now keyed per-user via JWT `sub`
- [ ] `docker compose up -d --build` — all containers healthy with
      `JWT_SECRET` wired; full login → protected call → refresh → logout
      flow verified through the gateway
- [ ] `AGENTS.md` + `PHASE_12_COMPLETE.md` updated
