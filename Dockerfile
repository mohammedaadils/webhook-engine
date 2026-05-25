# ─────────────────────────────────────────────────────────────────────────────
# Stage 1 — BUILD
# Uses the full Maven + JDK image to compile and package the JAR.
# This stage is discarded after the build; it never ends up in the final image.
# ─────────────────────────────────────────────────────────────────────────────
FROM maven:3.9.6-eclipse-temurin-17 AS builder

WORKDIR /app

# Copy pom.xml first and download dependencies in a separate layer.
# Docker caches this layer — if pom.xml hasn't changed, Maven won't
# re-download the internet on every build. Only invalidated when pom.xml changes.
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Now copy source and build the JAR.
# -DskipTests: we're not running tests in Docker build (run them locally/CI).
COPY src ./src
RUN mvn clean package -DskipTests -B

# ─────────────────────────────────────────────────────────────────────────────
# Stage 2 — RUN
# Uses a minimal JRE-only image (no Maven, no JDK, no source code).
# Final image is ~250 MB instead of ~600 MB.
# ─────────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Create a non-root user to run the app.
# Running as root inside a container is a security risk.
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Copy only the fat JAR from the build stage.
COPY --from=builder /app/target/webhookengine-1.0.0.jar app.jar

# Give ownership to the non-root user.
RUN chown appuser:appgroup app.jar

USER appuser

# Expose the port Spring Boot listens on.
EXPOSE 8080

# JVM flags for a t2.micro (1 vCPU, 1 GB RAM):
#   -Xms256m        → start heap at 256 MB
#   -Xmx512m        → cap heap at 512 MB (leaves room for OS + MySQL)
#   -XX:+UseSerialGC → lighter GC for single-core free-tier EC2
# Spring profile is set to prod → uses application-prod.properties (MySQL).
ENTRYPOINT ["java", \
            "-Xms256m", \
            "-Xmx512m", \
            "-XX:+UseSerialGC", \
            "-Dspring.profiles.active=prod", \
            "-jar", "app.jar"]
