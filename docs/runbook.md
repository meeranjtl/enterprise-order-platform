# Runbook

Operational guide for running and using the platform after it's up — starting it, logging in, managing catalog/users, and validating an order end to end. Build/test commands for contributors live in [`AGENTS.md`](../AGENTS.md); architecture/ports/Kafka topics live in [`docs/architecture.md`](architecture.md).

---

## Starting the app

### Option A: Full stack in Docker (recommended)

```bash
docker compose up -d --build
```

Rebuilds and starts all 19 containers (9 services + gateway + UI + Postgres/Kafka/Redis/observability). First boot takes a few minutes — Kafka and schema-registry are the slowest to report healthy. Check status with:

```bash
docker compose ps
```

Everything should show `healthy` (a few infra containers like zookeeper/grafana/prometheus/kafka-ui have no health check defined and just show `Up`, which is normal).

> Rebuild service images **sequentially**, not in parallel — see [`docs/gotchas.md`](gotchas.md) if a rebuild misbehaves.

### Option B: Local dev inner loop

Run infra in Docker, run the Java services yourself (IDE or `mvn spring-boot:run`) for faster iteration/debugging:

```bash
docker compose up -d postgres kafka zookeeper schema-registry redis   # infra only

mvn -pl services/customer-service spring-boot:run   # repeat per service you're working on
```

Each service still needs the others it depends on (e.g. order-service calls customer-service and product-service directly) — either run the full set locally or leave the rest running in Docker and only pull the one you're changing out. The UI can run against either:

```bash
cd ui && npm install && npm run dev   # http://localhost:5173, talks to gateway at :8080
```

---

## Logging in

All client traffic goes through the gateway at `http://localhost:8080` (or the UI at `http://localhost:3000` in Docker / `:5173` in local dev).

### Seeded admin account

customer-service seeds one `ADMIN` account on startup if none exists yet (idempotent — safe across restarts). See [`AdminSeeder.java`](../services/customer-service/src/main/java/com/enterprise/order/customer/config/AdminSeeder.java).

| Field | Value |
|---|---|
| Email | `admin@enterprise-order.local` |
| Password | `Admin123!` |
| Role | `ADMIN` |

Dev-only credentials — override via the `admin.seed.email` / `admin.seed.password` properties for anything beyond local use.

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@enterprise-order.local","password":"Admin123!"}'
```

Save the returned `accessToken` — send it as `Authorization: Bearer <token>` on every subsequent request.

### Regular customers

No customer accounts are seeded. Register one:

```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"buyer@test.com","password":"Password123!","firstName":"Jane","lastName":"Doe"}'
```

This returns a token pair directly (register doubles as first login) plus the new `customerId`.

Roles are `CUSTOMER` (default on register) and `ADMIN`. There's no self-service promotion endpoint — an account becomes `ADMIN` only via the seeder or a direct DB update.

---

## Managing the product catalog (admin only)

Products belong to a category, so create the category first.

```bash
# Category
curl -X POST http://localhost:8080/api/v1/categories \
  -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
  -d '{"name":"Electronics","active":true}'
# -> note the returned id as $CATEGORY_ID

# Product
curl -X POST http://localhost:8080/api/v1/products \
  -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
  -d '{"sku":"SKU-001","name":"Test Widget","price":19.99,"stockQuantity":100,"categoryId":'$CATEGORY_ID'}'
```

Required fields: category `name`; product `sku`, `name`, `price` (> 0), `stockQuantity` (>= 0), `categoryId`.

Update/delete follow the same REST conventions (`PUT`/`DELETE /api/v1/products/{id}`, `/api/v1/categories/{id}`), all `ADMIN`-gated.

---

## Managing users (admin only)

```bash
# List all customers
curl -H "Authorization: Bearer $ADMIN_TOKEN" http://localhost:8080/api/v1/customers

# Get / update / delete a specific customer (a customer can also GET/PUT their own record)
curl -H "Authorization: Bearer $ADMIN_TOKEN" http://localhost:8080/api/v1/customers/{id}
curl -X PUT ... /api/v1/customers/{id}
curl -X DELETE -H "Authorization: Bearer $ADMIN_TOKEN" http://localhost:8080/api/v1/customers/{id}
```

---

## Validating an order end to end

1. **Register a customer and place an order:**

   ```bash
   curl -X POST http://localhost:8080/api/v1/orders \
     -H "Authorization: Bearer $CUSTOMER_TOKEN" -H "Content-Type: application/json" \
     -d '{"customerId":'$CUSTOMER_ID',"items":[{"productId":'$PRODUCT_ID',"quantity":2}]}'
   ```

2. **Poll order status** — the fulfillment saga is fully event-driven (Kafka), so status moves asynchronously:

   ```bash
   curl -H "Authorization: Bearer $CUSTOMER_TOKEN" http://localhost:8080/api/v1/orders/{id}
   ```

   Expected progression: `PENDING → VALIDATED → PAYMENT_PENDING → PAYMENT_APPROVED → SHIPPED → COMPLETED`. See [`docs/saga.md`](saga.md) for the full state machine, including the compensating-transaction path on `PAYMENT_REJECTED`.

3. **Cross-check the rest of the platform:**
   - Product stock: `GET /api/v1/products/{PRODUCT_ID}` — `stockQuantity` should have decremented
   - Kafka UI (`http://localhost:8888`) — inspect the order/payment/shipping/notification topics for the event trail
   - Zipkin (`http://localhost:9411`) — trace the request across services by `X-Correlation-Id`
   - Analytics service (`GET /api/v1/analytics/...`, admin-only) — CQRS read model should reflect the completed order
   - Grafana (`http://localhost:3001`, `admin`/`admin`) — request/error-rate dashboards

---

## Service map (Docker ports)

| Service | Port | Notes |
|---|---|---|
| UI | 3000 | React app (nginx in Docker; `:5173` under `npm run dev`) |
| Gateway | 8080 | All client traffic routes here — never call a service port directly from the UI |
| Customer Service | 8081 | Auth issuer (`/api/auth/**`) + customer CRUD |
| Product Service | 8082 | Products + categories |
| Order Service | 8083 | Orders, saga orchestration |
| Inventory Service | 8084 | Stock reservation |
| Payment Service | 8085 | Payment processing |
| Shipping Service | 8086 | Shipment tracking |
| Notification Service | 8087 | Notifications |
| Analytics Service | 8088 | CQRS read model |
| Kafka UI | 8888 | Topic/event inspection |
| Schema Registry | 8090 | Avro schemas (internal `:8081` in-network) |
| Zipkin | 9411 | Distributed tracing |
| Prometheus | 9090 | Metrics |
| Grafana | 3001 | Dashboards (`admin`/`admin`) |
| Postgres | 5432 | `postgres`/`postgres`, schema-per-service in one `enterprise_order` DB |
| Redis | 6379 | Gateway rate limiting |
| Kafka | 9094 (host) / 9092 (in-network) | |

Each backend service also exposes its own Swagger UI directly, bypassing the gateway (e.g. `http://localhost:8081/swagger-ui.html` for customer-service), or aggregated for every service through the gateway at `http://localhost:8080/swagger-ui.html`.

---

## Troubleshooting

- Hard-won gotchas by phase (Kafka/Zookeeper restart recovery, Docker rebuild ordering, port collisions, etc.): [`docs/gotchas.md`](gotchas.md)
- Non-negotiable invariants (exception handling, idempotency, event-vs-HTTP, gateway wiring): [`docs/domain-rules.md`](domain-rules.md)
- `docker compose logs -f <service>` for a live tail of one service
