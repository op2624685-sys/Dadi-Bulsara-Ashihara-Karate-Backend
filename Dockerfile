# =============================================================================
# Karate backend — multi-stage Dockerfile
# -----------------------------------------------------------------------------
# Stage 1 (builder): Maven image that uses the project's own Maven version
#                    (pinned by `mvnw` / .mvn/wrapper).
# Stage 2 (runtime) : eclipse-temurin 21 JRE on a slim Debian base, non-root
#                    user, tini for clean signal handling, HEALTHCHECK against
#                    /actuator/health (matches Render's healthCheckPath).
# =============================================================================

# ---------- Stage 1 : build ---------------------------------------------------
FROM maven:3.9.9-eclipse-temurin-21 AS builder
WORKDIR /build

# Resolve dependencies first so they cache across source-only changes.
# pom.xml changes invalidate this layer; source-only edits do not.
COPY pom.xml ./
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -e -ntp -DskipTests dependency:go-offline

# Now copy the rest of the source and build the executable jar.
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -e -ntp -DskipTests package

# ---------- Stage 2 : runtime -------------------------------------------------
FROM eclipse-temurin:21.0.4_9-jre-jammy AS runtime

# OCI labels — Render and Docker Hub read these.
LABEL org.opencontainers.image.title="karate-backend" \
      org.opencontainers.image.description="Dadi Bulsara / Karate Federation REST API" \
      org.opencontainers.image.source="https://github.com/omprakash/dadi-bulsara-webapp" \
      org.opencontainers.image.licenses="Proprietary"

# tini gives us a clean PID 1 that reaps zombies and forwards signals
# (so `docker stop` triggers Spring Boot's graceful shutdown).
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl tini ca-certificates \
 && rm -rf /var/lib/apt/lists/*

# Non-root user — least-privilege principle.
RUN groupadd --system --gid 1001 karate \
 && useradd  --system --uid 1001 --gid karate --create-home karate

WORKDIR /app

# Copy only the fat jar. Wildcard so a version bump doesn't break this.
COPY --from=builder --chown=karate:karate /build/target/*.jar /app/app.jar

USER karate

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError" \
    SERVER_PORT=8080 \
    SPRING_PROFILES_ACTIVE=prod

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
  CMD curl -fsS http://127.0.0.1:${SERVER_PORT}/actuator/health || exit 1

# exec form so signals hit the JVM, not the shell.
ENTRYPOINT ["/usr/bin/tini", "--", "java", "-jar", "/app/app.jar"]
