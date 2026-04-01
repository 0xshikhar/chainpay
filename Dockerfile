# Multi-stage Dockerfile for ChainPay Core
# Stage 1: Build stage with Gradle
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app

# Copy gradle wrapper and configuration
COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./

# Make wrapper executable
RUN chmod +x gradlew

# Copy source files and contracts
COPY src src
COPY contracts contracts

# Build executable fat JAR skipping tests (tests run in CI/CD pipeline)
RUN ./gradlew bootJar --no-daemon

# Stage 2: Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Create non-root system group and user for security
RUN addgroup -S chainpay && adduser -S chainpay -G chainpay

# Copy compiled JAR from builder stage
COPY --from=builder /app/build/libs/*.jar app.jar

# Set ownership to non-root user
RUN chown -R chainpay:chainpay /app

USER chainpay

# Expose Spring Boot HTTP port and WebSocket port
EXPOSE 8080

# Environment defaults
ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_OPTS="-Xms256m -Xmx512m"

# Healthcheck monitoring Spring Boot Actuator
HEALTHCHECK --interval=10s --timeout=5s --retries=6 \
  CMD wget --quiet --tries=1 --spider http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
