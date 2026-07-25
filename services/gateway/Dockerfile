# Build stage
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app

# Parent POM
COPY pom.xml .

# Full sources for the gateway (and shared-library, kept for parity with other services)
COPY services/shared-library ./services/shared-library
COPY services/gateway ./services/gateway

# POM stubs for the remaining reactor modules: Maven requires every <module> listed
# in the parent POM to exist when it reads the reactor, even with -pl services/gateway.
COPY services/customer-service/pom.xml ./services/customer-service/pom.xml
COPY services/product-service/pom.xml ./services/product-service/pom.xml
COPY services/order-service/pom.xml ./services/order-service/pom.xml
COPY services/inventory-service/pom.xml ./services/inventory-service/pom.xml
COPY services/payment-service/pom.xml ./services/payment-service/pom.xml
COPY services/shipping-service/pom.xml ./services/shipping-service/pom.xml
COPY services/notification-service/pom.xml ./services/notification-service/pom.xml
COPY services/analytics-service/pom.xml ./services/analytics-service/pom.xml

# Build the gateway (shared-library sources are present but not a dependency —
# the gateway is reactive and must never pull in the servlet-based shared-library)
RUN mvn -pl services/gateway clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Install curl for health checks
RUN apk add --no-cache curl

# Copy JAR from builder
COPY --from=builder /app/services/gateway/target/gateway-1.0.0.jar app.jar

# Create non-root user
RUN addgroup -g 1000 appuser && adduser -D -u 1000 -G appuser appuser
USER appuser

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=10s --timeout=5s --retries=5 CMD curl -f http://localhost:8080/actuator/health || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
