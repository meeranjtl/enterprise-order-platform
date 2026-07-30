# Inventory Service — Phase 6 Complete

The Inventory Service manages stock balances and safe order reservations for the Enterprise Order Platform. It owns the `inventory` PostgreSQL schema and runs on port **8084**. All write operations require an `Idempotency-Key` header, so a client retry does not apply the same stock mutation twice.

## Features

- Stock balance tracking: total, reserved, and available quantities
- Pessimistic database locking for concurrent reservations
- Reserve and release operations for order workflows
- Signed administrative stock adjustments
- Immutable inventory transaction audit trail
- Idempotent write operations, scoped by operation and request key
- Flyway database migrations, OpenAPI/Swagger UI, health and metrics endpoints
- Kafka order-event consumer skeleton (disabled until Phase 8)

## API URLs

When running locally, use the Inventory Service directly:

| Resource | URL |
|---|---|
| Inventory API | `http://localhost:8084/api/v1/inventory` |
| Swagger UI | `http://localhost:8084/swagger-ui.html` |
| OpenAPI document | `http://localhost:8084/api-docs` |
| Health | `http://localhost:8084/actuator/health` |
| Metrics | `http://localhost:8084/actuator/metrics` |

Once the gateway is running, use `http://localhost:8080/api/v1/inventory/**`. Its aggregated Swagger UI also includes Inventory Service at `http://localhost:8080/swagger-ui.html`.

## Prerequisites

- Java 21
- Maven 3.9+
- Docker Desktop (for PostgreSQL and Compose deployment)

## Run Locally

1. Start PostgreSQL from the repository root:

   ```powershell
   docker compose up -d postgres
   ```

2. Build the service and its shared-library dependency:

   ```powershell
   mvn clean install -pl services/inventory-service -am
   ```

3. Start Inventory Service:

   ```powershell
   mvn -pl services/inventory-service spring-boot:run
   ```

   Flyway creates the `inventory` schema and applies its migration at startup.

4. Verify it is running:

   ```powershell
   Invoke-WebRequest http://localhost:8084/actuator/health
   ```

## Run with Docker Compose

Build and start the database, Inventory Service, and gateway:

```powershell
docker compose up --build postgres inventory-service gateway
```

The Inventory Service listens on `8084`; the gateway exposes it on `8080`. Stop the stack with:

```powershell
docker compose down
```

## API Endpoints

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/v1/inventory/{productId}` | Retrieve current stock status |
| `POST` | `/api/v1/inventory/adjust` | Add or remove physical stock (admin workflow) |
| `POST` | `/api/v1/inventory/reserve` | Reserve available stock for an order |
| `POST` | `/api/v1/inventory/release` | Release a prior reservation |

`adjust`, `reserve`, and `release` all require a non-empty `Idempotency-Key` request header.

## Typical Workflow

A product has no inventory record until its first adjustment. Create or replenish stock before attempting a reservation.

### 1. Add stock

```powershell
curl.exe -X POST http://localhost:8084/api/v1/inventory/adjust `
  -H "Content-Type: application/json" `
  -H "Idempotency-Key: stock-1001-initial" `
  -d '{"productId":1001,"quantity":50,"reason":"Initial warehouse stock"}'
```

`quantity` is a signed delta: use a positive value to add stock and a negative value to remove stock. The resulting total may never fall below the currently reserved quantity.

### 2. Reserve stock for an order

```powershell
curl.exe -X POST http://localhost:8084/api/v1/inventory/reserve `
  -H "Content-Type: application/json" `
  -H "Idempotency-Key: order-5001-reserve-1001" `
  -d '{"productId":1001,"orderId":5001,"quantity":3}'
```

### 3. Release the reservation

```powershell
curl.exe -X POST http://localhost:8084/api/v1/inventory/release `
  -H "Content-Type: application/json" `
  -H "Idempotency-Key: order-5001-release-1001" `
  -d '{"productId":1001,"orderId":5001,"quantity":3}'
```

### 4. Read the current balance

```powershell
curl.exe http://localhost:8084/api/v1/inventory/1001
```

Successful responses use the shared response envelope:

```json
{
  "success": true,
  "message": "Inventory reserved successfully",
  "data": {
    "productId": 1001,
    "totalQuantity": 50,
    "reservedQuantity": 3,
    "availableQuantity": 47,
    "transactionId": 2,
    "transactionType": "RESERVE"
  }
}
```

## Testing

Run the Inventory Service unit test suite:

```powershell
mvn test -pl services/inventory-service -am
```

The tests cover successful reservation, insufficient stock rejection, and idempotent replay behavior.

## Configuration

| Setting | Default | Purpose |
|---|---|---|
| `server.port` | `8084` | HTTP port |
| `spring.datasource.url` | localhost PostgreSQL, `inventory` schema | Database connection |
| `inventory.kafka.enabled` | `false` | Enables the Phase 8 Kafka consumer skeleton |

## Project Structure

```
inventory-service/
├── src/main/java/.../inventory/
│   ├── controller/     # REST API
│   ├── dto/            # Request and response contracts
│   ├── entity/         # Inventory, transaction, idempotency entities
│   ├── messaging/      # Kafka consumer skeleton
│   ├── repository/     # JPA repositories and locking query
│   └── service/        # Reservation and stock business rules
├── src/main/resources/db/migration/
│   └── V1__create_inventory_tables.sql
├── Dockerfile
└── README.md
```

---

**Phase:** 6 — Inventory Service (Reservation & Stock)  
**Status:** Complete  
**Last Updated:** July 29, 2026
