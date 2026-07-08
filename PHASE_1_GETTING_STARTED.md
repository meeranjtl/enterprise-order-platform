# Phase 1 - Getting Started Guide

Your step-by-step guide to begin building the Enterprise Order Platform.

---

## Prerequisites

Before you start, ensure you have:

- ✅ **Java 21 JDK** installed
  ```bash
  java -version
  # Should show: openjdk version "21..." or later
  ```

- ✅ **Maven 3.8+** installed
  ```bash
  mvn -version
  # Should show: Apache Maven 3.8.0 or later
  ```

- ✅ **Docker & Docker Compose** installed
  ```bash
  docker --version
  docker compose version
  ```

- ✅ **Git** installed
  ```bash
  git --version
  ```

- ✅ **IDE** (IntelliJ IDEA or VS Code)

---

## Step 1: Project Structure Setup

### 1.1 Navigate to your workspace
```powershell
cd C:\dev\projects\enterprise-order-platform
```

### 1.2 Create the directory structure
```powershell
# Create directories
mkdir -p architecture, docker, kafka, scripts, services, ui, tests, docs

# Navigate to services
cd services

# Create service modules (we'll create stub pom.xml files)
mkdir shared-library, gateway, customer-service, product-service, order-service, inventory-service, payment-service, shipping-service, notification-service, analytics-service

cd ..
```

---

## Step 2: Parent POM Configuration

The project root already has a `pom.xml`. Update it to be a parent POM:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.enterprise.order</groupId>
    <artifactId>enterprise-order-platform-parent</artifactId>
    <version>1.0.0</version>
    <packaging>pom</packaging>

    <name>Enterprise Order Platform</name>
    <description>Multi-service order processing platform</description>

    <modules>
        <module>services/shared-library</module>
        <module>services/gateway</module>
        <module>services/customer-service</module>
        <module>services/product-service</module>
        <module>services/order-service</module>
        <module>services/inventory-service</module>
        <module>services/payment-service</module>
        <module>services/shipping-service</module>
        <module>services/notification-service</module>
        <module>services/analytics-service</module>
    </modules>

    <properties>
        <maven.compiler.source>21</maven.compiler.source>
        <maven.compiler.target>21</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        
        <spring-boot.version>3.3.0</spring-boot.version>
        <spring-cloud.version>2023.0.3</spring-cloud.version>
        <java.version>21</java.version>
    </properties>

    <dependencyManagement>
        <dependencies>
            <!-- Spring Boot BOM -->
            <dependency>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-dependencies</artifactId>
                <version>${spring-boot.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>

            <!-- Spring Cloud BOM -->
            <dependency>
                <groupId>org.springframework.cloud</groupId>
                <artifactId>spring-cloud-dependencies</artifactId>
                <version>${spring-cloud.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <build>
        <pluginManagement>
            <plugins>
                <plugin>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-maven-plugin</artifactId>
                    <version>${spring-boot.version}</version>
                </plugin>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-compiler-plugin</artifactId>
                    <version>3.11.0</version>
                    <configuration>
                        <source>21</source>
                        <target>21</target>
                    </configuration>
                </plugin>
            </plugins>
        </pluginManagement>
    </build>
</project>
```

---

## Step 3: Create Shared Library Module

### 3.1 Create shared-library pom.xml
Create `services/shared-library/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.enterprise.order</groupId>
        <artifactId>enterprise-order-platform-parent</artifactId>
        <version>1.0.0</version>
        <relativePath>../../pom.xml</relativePath>
    </parent>

    <artifactId>shared-library</artifactId>
    <name>Shared Library</name>

    <dependencies>
        <!-- Spring Boot -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <!-- Validation -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>

        <!-- Lombok -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <scope>provided</scope>
        </dependency>

        <!-- JSON Processing -->
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
        </dependency>

        <!-- Logging -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-logging</artifactId>
        </dependency>

        <!-- Testing -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

### 3.2 Create directory structure
```powershell
cd services/shared-library
mkdir -p src/main/java/com/enterprise/order/shared/{exception,dto,mapper,util,validator,constant,config}
mkdir -p src/test/java/com/enterprise/order/shared
cd ..\..\..
```

### 3.3 Create common exception classes

Create `services/shared-library/src/main/java/com/enterprise/order/shared/exception/ApplicationException.java`:

```java
package com.enterprise.order.shared.exception;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ApplicationException extends RuntimeException {
    private String errorCode;
    private int statusCode;
    private String details;

    public ApplicationException(String errorCode, String message, int statusCode) {
        super(message);
        this.errorCode = errorCode;
        this.statusCode = statusCode;
    }

    public ApplicationException(String errorCode, String message, int statusCode, String details) {
        super(message);
        this.errorCode = errorCode;
        this.statusCode = statusCode;
        this.details = details;
    }

    public ApplicationException(String errorCode, String message, int statusCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.statusCode = statusCode;
    }
}
```

Create other exception classes in the same directory:

```java
// ResourceNotFoundException.java
public class ResourceNotFoundException extends ApplicationException {
    public ResourceNotFoundException(String resource, String identifier) {
        super("RESOURCE_NOT_FOUND", 
              resource + " not found with identifier: " + identifier, 
              404);
    }
}

// BadRequestException.java
public class BadRequestException extends ApplicationException {
    public BadRequestException(String message) {
        super("BAD_REQUEST", message, 400);
    }
}

// UnauthorizedException.java
public class UnauthorizedException extends ApplicationException {
    public UnauthorizedException(String message) {
        super("UNAUTHORIZED", message, 401);
    }
}

// ForbiddenException.java
public class ForbiddenException extends ApplicationException {
    public ForbiddenException(String message) {
        super("FORBIDDEN", message, 403);
    }
}

// ConflictException.java
public class ConflictException extends ApplicationException {
    public ConflictException(String message) {
        super("CONFLICT", message, 409);
    }
}

// InternalServerException.java
public class InternalServerException extends ApplicationException {
    public InternalServerException(String message) {
        super("INTERNAL_SERVER_ERROR", message, 500);
    }
}
```

---

## Step 4: Create Base DTOs

Create `services/shared-library/src/main/java/com/enterprise/order/shared/dto/BaseResponse.java`:

```java
package com.enterprise.order.shared.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BaseResponse<T> {
    private boolean success;
    private String message;
    private T data;
    private ErrorDetails error;
    private LocalDateTime timestamp;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ErrorDetails {
        private String code;
        private String message;
        private String details;
    }

    public static <T> BaseResponse<T> success(T data, String message) {
        return BaseResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static <T> BaseResponse<T> error(String code, String message, String details) {
        return BaseResponse.<T>builder()
                .success(false)
                .error(new ErrorDetails(code, message, details))
                .timestamp(LocalDateTime.now())
                .build();
    }
}
```

Create `services/shared-library/src/main/java/com/enterprise/order/shared/dto/PaginatedResponse.java`:

```java
package com.enterprise.order.shared.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaginatedResponse<T> {
    private List<T> content;
    private long totalElements;
    private int totalPages;
    private int currentPage;
    private int pageSize;
    private boolean hasNext;
    private boolean hasPrevious;
}
```

---

## Step 5: Create Global Exception Handler

Create `services/shared-library/src/main/java/com/enterprise/order/shared/config/GlobalExceptionHandler.java`:

```java
package com.enterprise.order.shared.config;

import com.enterprise.order.shared.dto.BaseResponse;
import com.enterprise.order.shared.exception.ApplicationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApplicationException.class)
    public ResponseEntity<BaseResponse<?>> handleApplicationException(
            ApplicationException ex, WebRequest request) {
        log.error("Application exception: {}", ex.getErrorCode(), ex);
        
        return ResponseEntity.status(ex.getStatusCode())
                .body(BaseResponse.error(
                        ex.getErrorCode(),
                        ex.getMessage(),
                        ex.getDetails()
                ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<BaseResponse<?>> handleGlobalException(
            Exception ex, WebRequest request) {
        log.error("Unexpected error", ex);
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(BaseResponse.error(
                        "INTERNAL_SERVER_ERROR",
                        "An unexpected error occurred",
                        ex.getMessage()
                ));
    }
}
```

---

## Step 6: Create Gateway Skeleton

### 6.1 Create gateway pom.xml
Create `services/gateway/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.enterprise.order</groupId>
        <artifactId>enterprise-order-platform-parent</artifactId>
        <version>1.0.0</version>
        <relativePath>../../pom.xml</relativePath>
    </parent>

    <artifactId>gateway</artifactId>
    <name>API Gateway</name>

    <dependencies>
        <!-- Spring Cloud Gateway -->
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-gateway</artifactId>
        </dependency>

        <!-- Spring Boot Actuator -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>

        <!-- Testing -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

### 6.2 Create gateway application
Create `services/gateway/src/main/java/com/enterprise/order/gateway/GatewayApplication.java`:

```java
package com.enterprise.order.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }

    @Bean
    public RouteLocator routes(RouteLocatorBuilder builder) {
        return builder.routes()
                // Placeholder routes - will be updated in Phase 4
                .build();
    }
}
```

### 6.3 Create application.yml
Create `services/gateway/src/main/resources/application.yml`:

```yaml
spring:
  application:
    name: api-gateway
  cloud:
    gateway:
      routes: []

server:
  port: 8080

management:
  endpoints:
    web:
      exposure:
        include: health,metrics
```

---

## Step 7: Create Customer Service Skeleton

### 7.1 Create customer-service pom.xml
Create `services/customer-service/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.enterprise.order</groupId>
        <artifactId>enterprise-order-platform-parent</artifactId>
        <version>1.0.0</version>
        <relativePath>../../pom.xml</relativePath>
    </parent>

    <artifactId>customer-service</artifactId>
    <name>Customer Service</name>

    <dependencies>
        <!-- Shared Library -->
        <dependency>
            <groupId>com.enterprise.order</groupId>
            <artifactId>shared-library</artifactId>
            <version>1.0.0</version>
        </dependency>

        <!-- Spring Boot -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>

        <!-- Database -->
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>

        <!-- Flyway -->
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-core</artifactId>
        </dependency>

        <!-- Validation -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>

        <!-- MapStruct -->
        <dependency>
            <groupId>org.mapstruct</groupId>
            <artifactId>mapstruct</artifactId>
            <version>1.5.5.Final</version>
        </dependency>

        <!-- Lombok -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <scope>provided</scope>
        </dependency>

        <!-- OpenAPI -->
        <dependency>
            <groupId>org.springdoc</groupId>
            <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
            <version>2.1.0</version>
        </dependency>

        <!-- Actuator -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>

        <!-- Testing -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>

        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>testcontainers</artifactId>
            <version>1.19.1</version>
            <scope>test</scope>
        </dependency>

        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>postgresql</artifactId>
            <version>1.19.1</version>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>

            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <configuration>
                    <annotationProcessorPaths>
                        <path>
                            <groupId>org.mapstruct</groupId>
                            <artifactId>mapstruct-processor</artifactId>
                            <version>1.5.5.Final</version>
                        </path>
                        <path>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                            <version>${lombok.version}</version>
                        </path>
                    </annotationProcessorPaths>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

### 7.2 Create application structure
```powershell
cd services/customer-service
mkdir -p src/main/java/com/enterprise/order/customer/{controller,service,repository,entity,dto,mapper,exception,config}
mkdir -p src/main/resources/db/migration
mkdir -p src/test/java/com/enterprise/order/customer/{service,controller,repository}
cd ..\..\..
```

### 7.3 Create Customer Application
Create `services/customer-service/src/main/java/com/enterprise/order/customer/CustomerApplication.java`:

```java
package com.enterprise.order.customer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {
        "com.enterprise.order.shared",
        "com.enterprise.order.customer"
})
public class CustomerApplication {
    public static void main(String[] args) {
        SpringApplication.run(CustomerApplication.class, args);
    }
}
```

### 7.4 Create application.yml
Create `services/customer-service/src/main/resources/application.yml`:

```yaml
spring:
  application:
    name: customer-service
  datasource:
    url: jdbc:postgresql://localhost:5432/enterprise_order
    username: postgres
    password: postgres
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        format_sql: true
  flyway:
    enabled: true
    locations: classpath:db/migration

server:
  port: 8081

springdoc:
  api-docs:
    path: /api-docs
  swagger-ui:
    path: /swagger-ui.html

management:
  endpoints:
    web:
      exposure:
        include: health,metrics
```

---

## Step 8: Create PostgreSQL Docker Container

Create `docker-compose.yml` in root:

```yaml
version: '3.8'

services:
  postgres:
    image: postgres:15
    container_name: enterprise-postgres
    environment:
      POSTGRES_DB: enterprise_order
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: postgres
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: [ "CMD-SHELL", "pg_isready -U postgres" ]
      interval: 10s
      timeout: 5s
      retries: 5

volumes:
  postgres_data:

networks:
  default:
    name: enterprise-order-network
```

---

## Step 9: Build the Project

### 9.1 Build all modules
```powershell
cd C:\dev\projects\enterprise-order-platform
mvn clean install
```

If successful, you should see:
```
BUILD SUCCESS
Total time: XX.XXs
```

### 9.2 Run PostgreSQL
```powershell
docker compose up postgres
```

Wait for the database to be ready (check healthcheck).

### 9.3 Run Customer Service
```powershell
cd services/customer-service
mvn spring-boot:run
```

You should see:
```
Started CustomerApplication in XX.XX seconds
```

### 9.4 Access Swagger UI
Open browser: http://localhost:8081/swagger-ui.html

---

## Step 10: Initial Flyway Migration

Create `services/customer-service/src/main/resources/db/migration/V1__initial_schema.sql`:

```sql
CREATE TABLE customers (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100),
    phone VARCHAR(20),
    city VARCHAR(100),
    state VARCHAR(100),
    zip_code VARCHAR(10),
    country VARCHAR(100),
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_customer_email ON customers(email);
CREATE INDEX idx_customer_status ON customers(status);
```

---

## What's Next?

After Phase 1 completes:

1. ✅ All modules build successfully
2. ✅ Gateway starts on port 8080
3. ✅ Customer Service starts on port 8081
4. ✅ PostgreSQL runs in Docker
5. ✅ Swagger UI accessible
6. ✅ Foundation ready

**Next Phase: Implement Customer Service CRUD** (See IMPLEMENTATION_PLAN.md - Phase 2)

---

## Troubleshooting

### Java 21 not found
```powershell
# Install Java 21
# Or update JAVA_HOME environment variable
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
java -version
```

### Maven not found
```powershell
# Add Maven to PATH
$env:PATH = "C:\tools\apache-maven-3.9.0\bin;$env:PATH"
mvn -version
```

### PostgreSQL connection refused
```powershell
# Ensure container is running
docker ps

# Check container logs
docker logs enterprise-postgres

# Wait for healthcheck to pass
docker compose logs postgres
```

### Port already in use
```powershell
# Find process using port 8080
netstat -ano | findstr :8080

# Find process using port 5432
netstat -ano | findstr :5432

# Kill process if needed
taskkill /PID <PID> /F
```

---

## Next Commands You'll Need

```powershell
# Build specific service
mvn clean install -pl services/customer-service

# Run service
cd services/customer-service
mvn spring-boot:run

# Run tests
mvn test

# View logs
docker logs enterprise-postgres

# Stop Docker
docker compose down

# View Docker containers
docker ps -a

# Rebuild Docker image
docker compose build
```

---

## Success Checklist for Phase 1

- [ ] All services build without errors: `mvn clean install`
- [ ] Gateway service runs: `mvn -pl services/gateway spring-boot:run`
- [ ] Customer service runs: `mvn -pl services/customer-service spring-boot:run`
- [ ] Swagger UI loads: http://localhost:8081/swagger-ui.html
- [ ] PostgreSQL healthy: `docker compose ps`
- [ ] Database migrations applied
- [ ] Health check passes: http://localhost:8081/actuator/health
- [ ] No Java/Maven/Docker issues

**Once complete, you're ready for Phase 2 - Customer Service CRUD Implementation!**

---

**Happy Coding! 🚀**

For detailed phase information, see IMPLEMENTATION_PLAN.md  
For quick reference, see PHASE_QUICK_REFERENCE.md

