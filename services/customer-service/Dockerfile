# Build stage
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app

# Parent POM
COPY pom.xml .

# Full sources for customer-service and its shared-library dependency
COPY services/shared-library ./services/shared-library
COPY services/customer-service ./services/customer-service

# POM stubs for the remaining reactor modules: Maven requires every <module> listed
# in the parent POM to exist when it reads the reactor, even with -pl.
COPY services/gateway/pom.xml ./services/gateway/pom.xml
COPY services/product-service/pom.xml ./services/product-service/pom.xml
COPY services/order-service/pom.xml ./services/order-service/pom.xml
COPY services/inventory-service/pom.xml ./services/inventory-service/pom.xml
COPY services/payment-service/pom.xml ./services/payment-service/pom.xml
COPY services/shipping-service/pom.xml ./services/shipping-service/pom.xml
COPY services/notification-service/pom.xml ./services/notification-service/pom.xml
COPY services/analytics-service/pom.xml ./services/analytics-service/pom.xml

# Build the project
RUN mvn -pl services/customer-service -am clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Install curl for health checks
RUN apk add --no-cache curl

# Copy JAR from builder
COPY --from=builder /app/services/customer-service/target/customer-service-1.0.0.jar app.jar

# Create non-root user
RUN addgroup -g 1000 appuser && adduser -D -u 1000 -G appuser appuser
USER appuser

# Expose port
EXPOSE 8081

# Health check
HEALTHCHECK --interval=10s --timeout=5s --retries=5 CMD curl -f http://localhost:8081/actuator/health || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
