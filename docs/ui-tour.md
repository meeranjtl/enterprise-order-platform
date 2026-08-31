# UI Tour

A screen-by-screen walkthrough of the React UI (`ui/`, `:3000`), organized by
role. For step-by-step API/CLI instructions see [`runbook.md`](runbook.md);
this doc is the visual companion — what each screen actually looks like and
who gets to see it. Role gating shown here is UX-only (`RequireRole` /
`RequireRoleRoute` in `ui/src/components/auth/`) — the real enforcement is
server-side `@PreAuthorize`, documented per-endpoint in the individual
service controllers.

Screenshots are from a live local stack (`docker compose up -d --build`),
captured at 1440×900.

---

## Signing in

Shared by both roles — `/login` and `/register`. A new registration always
gets the `CUSTOMER` role; `ADMIN` is seeded once on `customer-service`
startup (see [`runbook.md`](runbook.md#logging-in) for the default
credentials) or promoted by a direct DB update — there's no self-service
promotion endpoint.

| Sign in | Register |
|---|---|
| ![Sign in](screenshots/00-login.png) | ![Register](screenshots/01-register.png) |

A session that expires mid-use (refresh token dead) lands back here with a
toast explaining why, instead of leaving a stale page showing a raw
"Failed to load" error.

---

## Admin

Everything below is visible only to `ADMIN` — the sidebar itself grows three
extra items (`Customers`, `Kafka Events`, `Health`) compared to the customer
view.

### Dashboard

Revenue, order volume, top products, and live per-service health at a
glance — the admin landing page.

![Dashboard](screenshots/02-dashboard.png)

### Products — browse and manage the catalog

The catalog table plus the two controls that were missing before this
round of work: **Add product** and **Categories**. Row actions (pencil /
trash) edit or deactivate a product without touching its order history.

![Products, admin view](screenshots/03-products.png)

**Add product** — creating one also seeds its starting stock in
inventory-service behind the scenes, so it's immediately orderable (see
[`gotchas.md`](gotchas.md) if you're digging into why that matters).

![Add product dialog](screenshots/04-products-add-dialog.png)

**Categories** — create, rename, or remove categories from one dialog;
products keep their category name even if it's later renamed.

![Manage categories dialog](screenshots/05-products-categories-dialog.png)

### Orders — every order on the platform

Admin sees every customer's orders, with a Customer column the customer
view doesn't have.

![Orders, admin view](screenshots/06-orders-admin.png)

### Order detail — the full fulfillment picture

Payment and Shipment cards render automatically once the saga has
progressed far enough to have data for them — no separate lookup needed.
This example has run all the way to `COMPLETED`; on a `SHIPPED` order,
admin sees a **Mark as delivered** button here instead of the delivery
timestamp.

![Order detail, completed order](screenshots/07-order-detail-completed.png)

### Payments — direct lookup by ID

The fallback path for when you already have a payment ID (a log line, a
ticket) and don't want to go through the order. Retry/refund only appear
for `FAILED`/`COMPLETED` payments respectively.

| Empty | Looked up |
|---|---|
| ![Payments, empty](screenshots/08-payments-empty.png) | ![Payments, result](screenshots/09-payments-result.png) |

### Customers

Manage customer accounts platform-wide — search, edit, deactivate.

![Customers](screenshots/10-customers.png)

### Kafka Events

A live tail of every service's transactional outbox — useful for watching
the saga's event trail (`OrderCreated` → `InventoryReserved` →
`PaymentProcessed` → `ShipmentCreated`/`ShipmentDelivered` →
`NotificationSent`) without opening Kafka UI separately.

![Kafka Events](screenshots/11-kafka-events.png)

### System Health

Gateway + every downstream service's `/actuator/health`, polled
periodically — the fastest way to confirm the stack is actually up before
chasing a "why isn't this working" elsewhere.

![System Health](screenshots/12-system-health.png)

---

## Customer

A narrower, self-service view: no Customers/Kafka Events/Health in the
sidebar, no catalog-management or payment-recovery controls — just placing
and tracking your own orders.

### Orders — your own order history

Scoped to the signed-in customer only (`GET /api/v1/orders/customer/{id}`,
not the admin-only `GET /api/v1/orders`), with a **New order** action the
admin view doesn't have (an admin manages orders, but this platform's RBAC
model has only customers place them).

![Orders, customer view](screenshots/13-orders-customer.png)

**New order** — pick from active, in-stock products and quantities; submit
to place the order.

![New order dialog](screenshots/14-orders-new-order-dialog.png)

### Products — browse only

Same catalog table as the admin view, minus every admin control — no
**Add product**, no **Categories**, no row actions.

![Products, customer view](screenshots/15-products-customer.png)

### Order detail — same page, fewer controls

Same Payment/Shipment cards as the admin view (a customer can see their own
order's payment and shipment status), but no retry/refund/mark-as-delivered
buttons — those stay admin-only.

![Order detail, customer view](screenshots/16-order-detail-customer.png)

### Payments — lookup only

Same lookup box, but no retry/refund actions — a customer can check a
payment's status but not act on it.

![Payments, customer view](screenshots/17-payments-customer.png)
