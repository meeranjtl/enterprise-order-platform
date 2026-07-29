FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app

# Parent POM
COPY pom.xml .

# Full sources for inventory-service and its shared-library dependency
COPY services/shared-library ./services/shared-library
COPY services/inventory-service ./services/inventory-service

# POM stubs for the remaining reactor modules: Maven requires every <module> listed
# in the parent POM to exist when it reads the reactor, even with -pl.
COPY services/gateway/pom.xml ./services/gateway/pom.xml
COPY services/customer-service/pom.xml ./services/customer-service/pom.xml
COPY services/product-service/pom.xml ./services/product-service/pom.xml
COPY services/order-service/pom.xml ./services/order-service/pom.xml
COPY services/inventory-service/pom.xml ./services/inventory-service/pom.xml
COPY services/payment-service/pom.xml ./services/payment-service/pom.xml
COPY services/shipping-service/pom.xml ./services/shipping-service/pom.xml
COPY services/notification-service/pom.xml ./services/notification-service/pom.xml
COPY services/analytics-service/pom.xml ./services/analytics-service/pom.xml

RUN mvn -pl services/inventory-service -am clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN apk add --no-cache curl
COPY --from=builder /app/services/inventory-service/target/inventory-service-1.0.0.jar app.jar

RUN addgroup -g 1000 appuser && adduser -D -u 1000 -G appuser appuser
USER appuser

EXPOSE 8084

HEALTHCHECK --interval=10s --timeout=5s --retries=5 CMD curl -f http://localhost:8084/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
