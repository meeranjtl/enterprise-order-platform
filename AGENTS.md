# AGENTS.md - AI Agent Guidance for Enterprise Order Platform

**Purpose:** Essential knowledge for AI coding agents to be immediately productive in this microservices architecture codebase.

---

## Quick Context

This is a **14-phase microservices order processing platform** (Spring Boot 3, Java 21, PostgreSQL, Kafka). Built as a portfolio/learning project demonstrating enterprise architecture, operational excellence, and engineering maturity. Currently in **Phase 6 complete (Inventory Service)** — foundation, customer-service (:8081), product-service (:8082), gateway (:8080), order-service (:8083), and inventory-service (:8084) are implemented; next up is Phase 7 (Payment Service).

**Gateway note:** the gateway is reactive (Netty) and must never depend on the servlet-based shared-library. It generates/propagates `X-Correlation-Id`; shared-library's `CorrelationIdLoggingFilter` puts it in the MDC of servlet services. All client traffic should go through `:8080`.

**Key Stack:** Spring Boot 3, Spring Cloud Gateway, Apache Kafka, PostgreSQL, React 18, Docker Compose, Resilience4j  
**Project Type:** Maven multi-module with 10 services + shared library  
**Tech Leadership:** Event-driven architecture, CQRS/Saga patterns (planned), microservices with distributed tracing

---

## Architecture Overview (Read First!)

### Service Topology
```
React UI (Phase 13) → API Gateway (Spring Cloud Gateway) → 9 Microservices
                                        ↓
                    Kafka (Event Bus) - decouples services
                                        ↓
                    PostgreSQL (shared DB per phase design)
                                        ↓
        Prometheus/Grafana/Zipkin (Observability Stack)
```

**Current Services** (see `services/` directory):
- **shared-library**: Common exceptions, DTOs, validators, response wrappers, logging utilities
- **gateway**: API Gateway (Phase 4)
- **customer-service**: Customer CRUD (Phase 2)
- **product-service**: Product catalog (Phase 3)
- **order-service**: Order processing (Phase 5)
- **inventory-service**: Stock management (Phase 6)
- **payment-service**: Payment handling (Phase 7)
- **shipping-service**: Fulfillment (Phase 9)
- **notification-service**: Email/SMS (Phase 9)
- **analytics-service**: Metrics/reporting (Phase 10)

**Critical Design Decision:** All services depend on `shared-library` for common code. When adding cross-cutting concerns (exceptions, DTOs, validators), add to `shared-library` first, then services can consume.

---

## Build & Deployment Workflows

### Maven Commands (Parent POM at root)
```powershell
# Build everything (all services + shared-library)
mvn clean install

# Build specific service only
mvn clean install -pl services/customer-service

# Skip tests for faster iteration
mvn clean install -DskipTests

# Run single service with Spring Boot plugin
mvn -pl services/customer-service spring-boot:run

# Run tests only for a specific service
mvn test -pl services/customer-service

# Check test coverage
mvn clean install jacoco:report -pl services/customer-service
```

### Docker Compose (Infrastructure)
```powershell
# Start PostgreSQL only (for local development)
docker compose up postgres

# View logs
docker compose logs -f postgres

# Stop all
docker compose down
```

**Parent POM Location:** `C:\dev\projects\enterprise-order-platform\pom.xml`  
**Dependency Management:** Spring Boot BOM (3.3.0), Spring Cloud (2023.0.3), Lombok, MapStruct configured in parent  
**Java Version:** 21 (set in properties), enforced via maven-compiler-plugin

### Key Build Artifacts
- Each service builds as: `services/{service-name}/target/{service-name}-1.0.0.jar`
- JAR includes embedded Tomcat, PostgreSQL driver, Spring Boot runtime
- Docker image naming: `{service-name}:1.0.0` (build command in each Dockerfile)

---

## Code Organization & Naming Conventions

### Package Structure (per service)
```
com.enterprise.order.{service-name}/
├── controller/          # @RestController endpoints
├── service/             # @Service business logic  
├── repository/          # Spring Data JpaRepository
├── entity/              # @Entity JPA models
├── dto/                 # Request/Response objects
├── mapper/              # MapStruct @Mapper interfaces
├── exception/           # Service-specific exceptions (if any)
├── config/              # Service-specific @Configuration
└── {ServiceName}Application.java  # @SpringBootApplication entry point
```

### Naming Conventions
- **Controllers:** `{Resource}Controller` (e.g., `CustomerController`)
- **Services:** `{Resource}Service` (e.g., `CustomerService`)
- **Repositories:** `{Resource}Repository extends JpaRepository` (e.g., `CustomerRepository`)
- **Entities:** `{Resource}` (e.g., `Customer`) with `@Entity`
- **DTOs:** `{Resource}DTO` (e.g., `CustomerDTO`) for external APIs
- **Mappers:** `{Resource}Mapper extends MapStructMapper` (e.g., `CustomerMapper`)
- **Exception classes:** Extend `com.enterprise.order.shared.exception.ApplicationException`

### Critical Libraries (Understand These)
- **Lombok:** `@Data`, `@Builder`, `@RequiredArgsConstructor` reduce boilerplate; processor in maven-compiler-plugin
- **MapStruct:** DTO ↔ Entity mapping; uses `@Mapper(componentModel = "spring")` to generate Spring beans
- **Jakarta Validation:** `@NotBlank`, `@Email` on DTOs; custom validators extend `ConstraintValidator`
- **Springdoc OpenAPI:** Generates Swagger UI at `/swagger-ui.html`; configure in `application.yml`

### Shared Library Organization
All cross-cutting code lives in `services/shared-library/src/main/java/com/enterprise/order/shared/`:
- **exception/**: `ApplicationException`, `ResourceNotFoundException`, `BadRequestException`, etc. — all services throw these
- **dto/**: `BaseResponse<T>` wrapper for all API responses (success/error patterns)
- **validation/**: Custom validators (e.g., `@ValidPhone`, `@ValidAddress` with regex patterns)
- **config/**: `GlobalExceptionHandler` (@RestControllerAdvice) handles all app exceptions → RFC 7807 Problem Details format
- **util/**: Common utilities (logging, correlation IDs, etc.)

### Code Formatting Requirements
* **Preserve Structure:** Maintain proper vertical spacing and line breaks between logical blocks.
* **Enforce Readability:** Wrap long lines of code appropriately to prevent horizontal scrolling.
* **Standard Conventions:** Adhere strictly to language-specific style guides (e.g., PEP 8 for Python, Prettier/Airbnb for JavaScript).
* **No Compression:** Never output minified, single-line, or densely packed code unless explicitly requested.

**When adding new cross-cutting code:** Add to shared-library, rebuild (`mvn clean install -pl services/shared-library`), then services can consume.

---

## Key Development Patterns

### Exception Handling Pattern
```java
// Throw application exceptions (never generic Exception)
throw new ResourceNotFoundException("Customer", customerId);

// GlobalExceptionHandler automatically converts to RFC 7807 response:
// { "status": 404, "title": "Not Found", "detail": "Customer 123 not found", ... }
```

### Service Method Pattern
```java
@Service
@RequiredArgsConstructor  // Lombok constructor injection
@Transactional           // Automatic transaction management
public class CustomerService {
    private final CustomerRepository repo;
    private final CustomerMapper mapper;  // MapStruct
    
    public CustomerDTO getById(Long id) {
        return mapper.toDTO(repo.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Customer", id)));
    }
}
```

### REST Endpoint Pattern
```java
@RestController
@RequestMapping("/api/v1/customers")
@RequiredArgsConstructor
public class CustomerController {
    private final CustomerService service;
    
    @GetMapping("/{id}")
    @Operation(summary = "Get customer by ID")  // Swagger
    public BaseResponse<CustomerDTO> getCustomer(@PathVariable Long id) {
        return BaseResponse.success(service.getById(id));
    }
}
```

### Validation Pattern
Use JSR-303 annotations on DTOs + custom validators for complex logic:
```java
@Data
public class CustomerDTO {
    @NotBlank
    private String name;
    
    @ValidPhone  // Custom validator
    private String phone;
}
```

### Testing Pattern
```java
@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {
    @Mock private CustomerRepository repo;
    @InjectMocks private CustomerService service;
    
    @BeforeEach
    void setup() { /* initialization */ }
    
    @Test
    void testGetById_notFound() {
        when(repo.findById(999L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, 
            () -> service.getById(999L));
    }
}
```

### Spring Boot Configuration Pattern
Each service has `application.yml` configuring:
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/enterprise_order
  jpa:
    hibernate:
      ddl-auto: validate  # Use Flyway for migrations
  flyway:
    enabled: true
    locations: classpath:db/migration
  
  # Logging as JSON
  application:
    name: customer-service
  
  # Springdoc/Swagger
springdoc:
  swagger-ui:
    path: /swagger-ui.html
```

---

## Database Migrations & Schema

**Migration Tool:** Flyway (automated SQL versioning)  
**Location:** `services/{service}/src/main/resources/db/migration/`  
**Naming Convention:** `V1__initial_schema.sql`, `V2__add_column.sql`, etc.

**Pattern:** Flyway runs on service startup; scripts auto-discovered in `db/migration/` folder.

**To add a new table:**
1. Create `V{next_number}__{description}.sql` in migration folder
2. Service startup auto-runs it
3. Use `hibernate.ddl-auto: validate` (never `create` in production)

**Database:** PostgreSQL 15 shared across services (Phase 1 design; later phases may introduce database-per-service)

---

## Testing Strategy

### Scope of Tests
- **Unit Tests:** Mock all dependencies; test service logic in isolation (fastest)
- **Integration Tests:** Use TestContainers for PostgreSQL; test service + DB interactions
- **E2E Tests:** (Future phases) Test complete workflows across services

### TestContainers Usage (Integration Tests)
```java
@Testcontainers  // Annotation
@SpringBootTest
class CustomerServiceIntegrationTest {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15");
    
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
    }
}
```

### Test Coverage Target
- Aim for 80%+ line coverage per service
- Focus on business logic, not getters/setters (Lombok handles those)
- Mock external service calls (payment gateway, email service, etc.)

---

## CI/CD & Deployment

**CI/CD Framework:** GitHub Actions (not yet fully implemented in Phase 1, but planned)  
**Deployment:** Docker Compose (Phase 14); Kubernetes later  
**Artifact Registry:** (To be configured) Docker Hub or private registry

**Phase 1 Status:** Docker Compose skeleton ready; PostgreSQL container configured; other services' Dockerfiles ready.

---

## Common Tasks & Workflows

### "I need to add a new REST endpoint"
1. Add method to `{Resource}Service` class
2. Add DTO for request/response in `dto/` folder  
3. Add endpoint in `{Resource}Controller` with `@GetMapping/@PostMapping`
4. Use `BaseResponse.success()` wrapper for response
5. Add `@Operation` annotation for Swagger documentation
6. Test with `@WebMvcTest` or MockMvc

### "I need to handle a new exception scenario"
1. Check if exception exists in `shared-library/exception/`
2. If not, create new exception class extending `ApplicationException`
3. GlobalExceptionHandler automatically converts to RFC 7807 response
4. Test with `assertThrows` in unit tests

### "I need to add a new database field"
1. Add field to `@Entity` class with `@Column` annotation
2. Create migration script: `V{N}__add_{field}_to_{table}.sql`
3. Update `{Resource}DTO` to expose field
4. Update MapStruct `{Resource}Mapper` if mapping needed
5. Run `mvn flyway:migrate` to execute migration

### "I need to validate an input parameter"
1. Add JSR-303 annotation to DTO (`@NotBlank`, `@Email`, etc.)
2. For complex validation, create custom `@Constraint` annotation in shared-library
3. Implement `ConstraintValidator` interface
4. Spring Boot auto-validates DTO before controller method executes
5. Validation errors return 400 with field-level error messages

### "I need to call another service"
1. Use `RestTemplate` or `WebClient` bean (configure in shared-library)
2. Handle `RestClientException` gracefully (will add Resilience4j retry logic in later phases)
3. Implement timeout and circuit breaker patterns (Phase 7+)
4. Eventually (Phase 8) replace with Kafka event-based communication

### "I need to add metrics/monitoring"
1. Use Micrometer annotations (`@Timed`, `@Counted`)
2. Expose metrics endpoint: `GET /actuator/metrics` (enabled in application.yml)
3. Phase 11 adds Prometheus scraping, Grafana dashboards, Zipkin tracing

---

## Project Conventions & Patterns to Follow

### Code Quality Standards
- **Format:** Spring Boot conventions (IntelliJ IDEA defaults OK)
- **Logging:** Use SLF4J (`private static final Logger log = LoggerFactory.getLogger(...)`)
- **Comments:** Document *why*, not *what*; code should be self-documenting
- **Error Handling:** Never swallow exceptions; always log or throw
- **Null Safety:** Use `Optional<T>` for JPA queries, avoid null checks
- **Transactions:** Apply `@Transactional` at service level, let Spring manage TX boundaries

### Commit Message Convention
```
{phase}-{component}: {description}
Examples:
- phase-2-customer-service: implement get customer by ID endpoint
- phase-5-order-service: add order validation logic
- shared: add custom email validator
```

### Documentation Requirements
- Each service has README.md explaining purpose, setup, API examples
- Architecture decisions documented in service README
- Database schema changes documented in migration comments
- Complex business logic documented with inline comments

### Phase Progression
- Phases build sequentially (must complete Phase 1 before Phase 2)
- Each phase is independently testable and deployable
- Phase transitions require: all tests passing, documentation complete, Docker image builds
- See `PHASE_QUICK_REFERENCE.md` for phase dependencies

---

## Key Files for Understanding the Codebase

### Architecture & Planning
- `PROJECT_OVERVIEW.md` - 14-phase strategy, big picture
- `PHASE_QUICK_REFERENCE.md` - One-page phase overview, dependencies
- `IMPLEMENTATION_PLAN.md` - Detailed technical requirements per phase
- `architecture/HLD.md` - High-level design (being populated as phases progress)

### Build & Configuration
- `pom.xml` (root) - Parent POM with dependency management
- `services/shared-library/pom.xml` - Common library
- Each service has its own pom.xml with inherited parent

### Foundation Code to Study
- `services/shared-library/src/main/java/com/enterprise/order/shared/exception/` - Exception hierarchy
- `services/shared-library/src/main/java/com/enterprise/order/shared/dto/BaseResponse.java` - Response wrapper
- `services/customer-service/src/main/java/com/enterprise/order/customer/` - Example service implementation
- `services/customer-service/src/main/resources/application.yml` - Configuration example

### Development Getting Started
- `PHASE_1_GETTING_STARTED.md` - Step-by-step Phase 1 setup
- `README.md` (root) - Quick start commands

---

## Debugging Tips

### Service Won't Start?
1. Check `application.yml` datasource URL points to correct PostgreSQL
2. Verify PostgreSQL running: `docker compose ps`
3. Run migrations: `mvn flyway:migrate -pl services/{service}`
4. Check Java 21 installed: `java -version`

### Test Failures?
1. Ensure TestContainers PostgreSQL image available: `docker pull postgres:15`
2. Check mock setup in @BeforeEach method
3. Verify shared-library builds: `mvn clean install -pl services/shared-library`

### Build Failures?
1. Clear cache: `mvn clean` before retrying
2. Update IDE annotation processors if using Lombok/MapStruct
3. Verify parent pom.xml dependencies defined (don't redeclare versions in child POMs)

### Swagger/OpenAPI Not Showing?
1. Service must include `springdoc-openapi-starter-webmvc-ui` dependency
2. Hit `http://localhost:{port}/swagger-ui.html`
3. Check application.yml enables springdoc

---

## When in Doubt, Reference These

1. **Existing Customer Service** - Most complete example of Phase 2
2. **Shared Library** - Source of truth for common patterns
3. **PROJECT_OVERVIEW.md** - High-level architecture
4. **PHASE_QUICK_REFERENCE.md** - Quick navigation and commands
5. **Spring Boot & Spring Cloud docs** - Official source for framework questions

---

## Red Flags / Anti-Patterns (Avoid These)

❌ **Direct database queries in controller** - Should be in service layer  
❌ **Catching and swallowing exceptions** - Log and rethrow or throw ApplicationException  
❌ **Hardcoded values** - Use application.yml configuration  
❌ **Null pointer checks everywhere** - Use Optional<T> or Objects.requireNonNull()  
❌ **Service-to-service HTTP calls during Phase 8+** - Should use Kafka events  
❌ **Missing @Transactional on service methods** - Ensures TX boundaries  
❌ **DTOs with direct @Entity references** - Always map, maintains separation of concerns  

---

**Last Updated:** July 29, 2026
**For Phase:** Phase 6 complete (Inventory Service — reservation, release, adjustment, transaction audit, and idempotency)
**Next Phase:** Payment Service (Phase 7) — depends on Phases 1–6; see PHASE_QUICK_REFERENCE.md