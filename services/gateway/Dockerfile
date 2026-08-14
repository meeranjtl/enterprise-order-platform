# Build stage
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app

# Layer 1 — POMs only. Maven requires every <module> directory listed in the
# parent POM to exist when it reads the reactor, even with -pl. Keeping this
# layer source-free means dependency resolution stays cached until a pom.xml
# changes.
COPY pom.xml .
COPY services/shared-library/pom.xml ./services/shared-library/pom.xml
COPY services/gateway/pom.xml ./services/gateway/pom.xml
COPY services/customer-service/pom.xml ./services/customer-service/pom.xml
COPY services/product-service/pom.xml ./services/product-service/pom.xml
COPY services/order-service/pom.xml ./services/order-service/pom.xml
COPY services/inventory-service/pom.xml ./services/inventory-service/pom.xml
COPY services/payment-service/pom.xml ./services/payment-service/pom.xml
COPY services/shipping-service/pom.xml ./services/shipping-service/pom.xml
COPY services/notification-service/pom.xml ./services/notification-service/pom.xml
COPY services/analytics-service/pom.xml ./services/analytics-service/pom.xml

# Prefetch dependencies into a shared BuildKit cache mount: one warm ~/.m2
# reused by every service and every future build (the id is shared across all
# Dockerfiles). dependency:go-offline has known gaps (annotation processors,
# some plugin deps) so it must never fail the build — the package step below
# backfills anything missing from the same cache.
# No -am: the gateway is reactive and must never pull in the servlet-based
# shared-library.
RUN --mount=type=cache,target=/root/.m2,id=enterprise-order-maven-m2 \
    mvn -B -ntp -pl services/gateway dependency:go-offline || true

# Layer 2 — gateway sources only. shared-library sources are intentionally NOT
# copied (the gateway does not depend on them), which also keeps the gateway
# image from rebuilding on shared-library changes.
COPY services/gateway ./services/gateway

# Build the gateway jar.
# -Dmaven.test.skip=true: image builds don't compile tests or resolve test
# deps — tests still run via `mvn test`.
# `clean` is unnecessary: WORKDIR is always fresh.
RUN --mount=type=cache,target=/root/.m2,id=enterprise-order-maven-m2 \
    mvn -B -ntp -pl services/gateway package -Dmaven.test.skip=true

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
HEALTHCHECK --interval=15s --timeout=5s --retries=5 --start-period=180s CMD curl -f http://localhost:8080/actuator/health || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
