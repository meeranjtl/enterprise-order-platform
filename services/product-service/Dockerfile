FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app

COPY pom.xml .
COPY services/shared-library ./services/shared-library
COPY services/product-service ./services/product-service

RUN mvn -pl services/product-service -am clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN apk add --no-cache curl
COPY --from=builder /app/services/product-service/target/product-service-1.0.0.jar app.jar

RUN addgroup -g 1000 appuser && adduser -D -u 1000 -G appuser appuser
USER appuser

EXPOSE 8082

HEALTHCHECK --interval=10s --timeout=5s --retries=5 CMD curl -f http://localhost:8082/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]