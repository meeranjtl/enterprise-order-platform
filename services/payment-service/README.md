# Payment Service — Phase 7 Complete

Payment Service owns mock payment authorization and refund processing for the platform. It runs on port **8085**, persists data in the `payment` schema, and exposes its API through the gateway at `/api/v1/payments`.

## Features

- Mock gateway with an 80% approval rate
- Payment lifecycle tracking: pending, processing, completed, failed, refunded
- Automatic exponential retry scheduling (1, 2, and 4 seconds; maximum three retries)
- Manual retry and refund endpoints
- Configurable Kafka event publishing (`payment.kafka.enabled`, off until Phase 8)
- Flyway migration, Swagger UI, health endpoint, and shared RFC 7807 errors

## Endpoints

| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/v1/payments` | Initiate and process a payment |
| GET | `/api/v1/payments/{id}` | Retrieve payment status |
| POST | `/api/v1/payments/{id}/retry` | Retry a failed payment |
| POST | `/api/v1/payments/{id}/refund` | Refund a completed payment |

Start locally with `mvn -pl services/payment-service -am spring-boot:run`. Swagger is at `http://localhost:8085/swagger-ui.html`.
