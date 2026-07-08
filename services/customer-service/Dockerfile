# Build stage
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app

# Copy parent pom and shared-library
COPY pom.xml .
COPY services/shared-library ./services/shared-library
COPY services/customer-service ./services/customer-service

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
EXPOSE 8080

# Health check
HEALTHCHECK --interval=10s --timeout=5s --retries=5 CMD curl -f http://localhost:8080/actuator/health || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]

