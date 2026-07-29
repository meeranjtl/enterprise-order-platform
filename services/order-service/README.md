# Order Service

Phase 5 order processing service for the Enterprise Order Platform.

## Responsibilities

- Create orders with line items
- Validate customers through customer-service
- Validate product activity, price, and stock through product-service
- Snapshot product name, SKU, and unit price on each order item
- Calculate subtotal, tax, shipping, and total amount
- Manage controlled order status transitions

## Local Run

```powershell
mvn -pl services/order-service -am spring-boot:run
```

The service starts on `http://localhost:8083`.

## API

- `POST /api/v1/orders`
- `GET /api/v1/orders/{id}`
- `GET /api/v1/orders/number/{orderNumber}`
- `GET /api/v1/orders?status=PENDING`
- `GET /api/v1/orders/customer/{customerId}`
- `PATCH /api/v1/orders/{id}/status`
- `DELETE /api/v1/orders/{id}`

Swagger UI is available at `http://localhost:8083/swagger-ui.html` and through the gateway at `http://localhost:8080/swagger-ui.html`.

## Configuration

```yaml
order:
  clients:
    customer-service-url: http://localhost:8081
    product-service-url: http://localhost:8082
  pricing:
    tax-rate: 0.10
    flat-shipping-cost: 10.00
    free-shipping-threshold: 100.00
```

## Notes

Phase 5 validates product stock but does not reserve or decrement it. Reservation belongs to Phase 6 inventory-service.
