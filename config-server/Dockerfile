# Multi-stage build for optimized image size
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app

# Copy Maven wrapper and pom files
COPY ../pom.xml /app/parent-pom.xml
COPY pom.xml .
COPY src ./src

# Build the jar (assumes parent pom is installed in local repo)
RUN --mount=type=cache,target=/root/.m2 \
    apk add --no-cache maven && \
    mvn package -DskipTests -f pom.xml

# Runtime stage - use Alpine for minimal image size
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Create non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

COPY --from=builder /app/target/*.jar app.jar

RUN chown appuser:appgroup app.jar
USER appuser

EXPOSE 8888

HEALTHCHECK --interval=15s --timeout=10s --retries=5 \
    CMD wget -qO- http://localhost:8888/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
