# Product Service

Product Service owns the product catalog for the Enterprise Order Platform. It manages categories, product details, search/filtering, pricing validation, and basic stock quantity state used by later order and inventory phases.

## Technology

- Java 21
- Spring Boot 3.3.0
- Spring Web, Validation, Data JPA, Actuator
- PostgreSQL with Flyway migrations
- MapStruct and Lombok
- Springdoc OpenAPI
- JUnit 5, Mockito, TestContainers

## Local Run

Start PostgreSQL:

```powershell
docker compose up postgres
```

Run Product Service:

```powershell
mvn -pl services/product-service spring-boot:run
```

Service URLs:

- Health: `http://localhost:8082/actuator/health`
- Swagger: `http://localhost:8082/swagger-ui.html`
- API docs: `http://localhost:8082/api-docs`

## API Endpoints

### Categories

| Method | Endpoint | Purpose |
| --- | --- | --- |
| POST | `/api/v1/categories` | Create category |
| GET | `/api/v1/categories/{id}` | Get category by ID |
| GET | `/api/v1/categories` | List categories with pagination |
| PUT | `/api/v1/categories/{id}` | Update category |
| DELETE | `/api/v1/categories/{id}` | Delete category when no active products reference it |

### Products

| Method | Endpoint | Purpose |
| --- | --- | --- |
| POST | `/api/v1/products` | Create product |
| GET | `/api/v1/products/{id}` | Get product by ID |
| GET | `/api/v1/products` | List products with pagination |
| GET | `/api/v1/products/search` | Search by SKU, name, category, price, status, and stock |
| PUT | `/api/v1/products/{id}` | Update product |
| PATCH | `/api/v1/products/{id}/stock` | Set stock quantity |
| DELETE | `/api/v1/products/{id}` | Soft-delete by marking product `DISCONTINUED` |

## Search Filters

`GET /api/v1/products/search` supports:

- `sku`
- `name`
- `categoryId`
- `minPrice`
- `maxPrice`
- `status`: `ACTIVE`, `INACTIVE`, `OUT_OF_STOCK`, `DISCONTINUED`
- `inStockOnly=true`
- Spring pagination and sorting, for example `page=0&size=20&sort=createdAt,desc`

Example:

```http
GET /api/v1/products/search?name=keyboard&categoryId=1&minPrice=10&maxPrice=100&status=ACTIVE&inStockOnly=true
```

## Validation Rules

- Category name is required and unique, case-insensitive.
- Product SKU is required and unique.
- Product name is required.
- Product price must be greater than zero.
- Stock quantity cannot be negative.
- Products must reference an existing active category.
- Active products with zero stock are normalized to `OUT_OF_STOCK`.
- Product deletion is soft deletion via `DISCONTINUED` status.

## Database

Flyway migration: `src/main/resources/db/migration/V1__create_product_catalog.sql`

Tables:

- `categories`
- `products`

Important indexes exist for SKU, product name, category, status, price, stock quantity, and category name.

## Tests

Run Product Service tests:

```powershell
mvn test -pl services/product-service -am
```

The TestContainers integration test is included as `ProductServiceIT`. It runs when Docker is available and is skipped automatically when Docker is unavailable.

## Docker

Build the service image from the repository root:

```powershell
docker build -t product-service:1.0.0 -f services/product-service/Dockerfile .
```

When running in Docker Compose, use:

```yaml
SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/enterprise_order
```