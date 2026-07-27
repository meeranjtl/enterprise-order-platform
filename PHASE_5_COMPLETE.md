# Phase 5 Complete - Order Service

## Summary

Phase 5 implements the order-service as the core order creation and lifecycle service. It runs on port 8083 and is routed through the API Gateway on port 8080.

## Delivered

- Order service Spring Boot application
- Order and order item persistence model
- Flyway migration for `orders.orders` and `orders.order_items`
- Order creation endpoint with transaction management
- Customer validation through customer-service
- Product validation through product-service
- Product price/name/SKU snapshotting on order items
- Stock availability validation without reservation or decrement
- Subtotal, tax, shipping, and total calculations
- Controlled order status transitions
- Order lookup, listing, customer history, status update, and cancellation APIs
- Gateway routes, API docs aggregation, and fallback endpoint
- Dockerfile and docker-compose integration
- Shared validation exception handling for 400 responses
- Unit, MVC, and TestContainers integration tests

## API Surface

- `POST /api/v1/orders`
- `GET /api/v1/orders/{id}`
- `GET /api/v1/orders/number/{orderNumber}`
- `GET /api/v1/orders?status=PENDING`
- `GET /api/v1/orders/customer/{customerId}`
- `PATCH /api/v1/orders/{id}/status`
- `DELETE /api/v1/orders/{id}`

## Business Rules

- Orders start in `PENDING` status.
- Duplicate products in a single order are rejected.
- Products must be `ACTIVE`, priced, and sufficiently stocked.
- Tax defaults to 10%.
- Shipping is 10.00 unless subtotal is at least 100.00.
- Phase 5 validates stock only; Phase 6 owns reservation and stock mutation.

## Verification

- `mvn test -pl services/order-service -am` passed.
- `mvn test` passed for the full reactor.
- TestContainers integration tests are configured with `disabledWithoutDocker = true`; they are skipped in this environment because Docker is unavailable.
