# Phase 3 - Product Service & Catalog - COMPLETED

## Overview

Phase 3 implements Product Service as a catalog microservice with categories, product CRUD, dynamic search/filtering, basic stock quantity management, validation, Flyway schema management, Docker support, and tests.

## Deliverables Completed

- Product Service Spring Boot module
- Product and Category entities
- Product and Category DTOs
- MapStruct mappers
- JPA repositories and dynamic product specifications
- Product CRUD endpoints
- Category CRUD/list endpoints
- Product search by SKU, name, category, price range, status, and in-stock flag
- Stock update endpoint
- Product soft deletion through `DISCONTINUED` status
- Flyway migration for catalog schema
- Swagger/OpenAPI configuration
- Dockerfile
- Product Service README
- Unit, MVC controller, and TestContainers integration tests
- Maven Surefire updated to run JUnit 5 tests and `*IT` classes

## Verification

Command:

```powershell
mvn test -pl services/product-service -am
```

Expected behavior:

- Unit and MVC tests run in all environments.
- `ProductServiceIT` runs when Docker is available.
- `ProductServiceIT` is skipped when Docker is unavailable.

## Main Endpoints

- `POST /api/v1/categories`
- `GET /api/v1/categories`
- `GET /api/v1/categories/{id}`
- `PUT /api/v1/categories/{id}`
- `DELETE /api/v1/categories/{id}`
- `POST /api/v1/products`
- `GET /api/v1/products`
- `GET /api/v1/products/{id}`
- `GET /api/v1/products/search`
- `PUT /api/v1/products/{id}`
- `PATCH /api/v1/products/{id}/stock`
- `DELETE /api/v1/products/{id}`

## Next Phase

Phase 4 should wire API Gateway routing for Customer Service and Product Service, then add correlation ID propagation and gateway-level protections.