# ═══════════════════════════════════════════════════════════════════
# Dockerfile — Builds the Spring Boot app container
# ═══════════════════════════════════════════════════════════════════
#
# Multi-stage build:
#   Stage 1 (builder): Uses Maven to compile and package the JAR
#   Stage 2 (runtime): Uses a minimal JRE image to run the JAR
#
# WHY multi-stage?
#   The full Maven image is ~500MB. The JRE image is ~200MB.
#   We only need Maven in the build stage.
#   The final production image is smaller (less attack surface, faster pull).

# ── Stage 1: Build ────────────────────────────────────────────────────────────
FROM maven:3.9.6-eclipse-temurin-17 AS builder

WORKDIR /app

# Copy pom.xml first and download dependencies
# WHY separate COPY for pom.xml?
#   Docker caches each layer. If pom.xml hasn't changed, the 'mvn dependency:go-offline'
#   layer is cached → much faster rebuilds when only source code changes.
COPY pom.xml .
RUN mvn dependency:go-offline -q

# Copy source code and build
COPY src ./src
RUN mvn clean package -DskipTests -q

# ── Stage 2: Runtime ──────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Create non-root user for security (don't run as root in production)
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Copy the JAR from builder stage
COPY --from=builder /app/target/*.jar app.jar

# Switch to non-root user
USER appuser

# Port that Tomcat listens on
EXPOSE 8080

# Start the Spring Boot application
# -Djava.security.egd → faster random number generation for JVM startup
ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
