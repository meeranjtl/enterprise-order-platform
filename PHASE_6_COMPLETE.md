# Phase 6 - Inventory Service (Reservation & Stock) - COMPLETED

## Overview

Phase 6 implements Inventory Service as the owner of stock balances and order reservations. It provides safe concurrent stock mutations, idempotent write operations, an immutable transaction audit trail, and gateway routing on port `8080`.

## Deliverables Completed

- Inventory Service Spring Boot module running on port `8084`
- Inventory, inventory transaction, and idempotency-record persistence model
- Flyway migration for the `inventory` schema and its tables
- Inventory status lookup endpoint
- Stock-adjustment endpoint for signed physical-stock changes
- Stock-reservation endpoint for order workflows
- Reservation-release endpoint
- Total, reserved, and available quantity tracking
- Pessimistic database locking for concurrent reservation safety
- Immutable inventory transaction audit trail
- Idempotency support for adjust, reserve, and release operations through `Idempotency-Key`
- Validation and shared `BaseResponse` API response envelope
- Swagger/OpenAPI, health, and metrics endpoints
- Kafka order-event consumer skeleton, disabled until the event-driven phase
- API Gateway route and aggregated OpenAPI documentation entry
- Dockerfile and Docker Compose integration
- Unit tests for successful reservation, insufficient-stock rejection, and idempotent replay behavior

## Verification

Command:

```powershell
mvn test -pl services/inventory-service -am
```

Expected behavior:

- Inventory Service and its shared-library dependency build successfully.
- Reservation reduces available quantity and increases reserved quantity.
- Insufficient available stock is rejected.
- Reusing an idempotency key replays the prior result without applying the stock mutation again.

## Main Endpoints

- `GET /api/v1/inventory/{productId}`
- `POST /api/v1/inventory/adjust`
- `POST /api/v1/inventory/reserve`
- `POST /api/v1/inventory/release`

`adjust`, `reserve`, and `release` require a non-empty `Idempotency-Key` request header.

## Next Phase

Phase 7 should implement Payment Service and integrate it with the order lifecycle while preserving the inventory reservation workflow.
