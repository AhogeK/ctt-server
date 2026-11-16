# ---- Build Stage ----
FROM eclipse-temurin:25-jdk AS builder
WORKDIR /app

COPY gradle/ gradle/
# Same classifier as the host build: Maven Central answers 429 to shared runner
# IPs often enough that an image build failed on it while the host build, which
# already retries, passed the same commit.
COPY .github/scripts/gradle-retry.sh .github/scripts/gradle-retry.sh
COPY gradlew build.gradle.kts settings.gradle.kts gradle.properties* ./
COPY src/ src/

RUN --mount=type=cache,target=/root/.gradle \
    chmod +x gradlew .github/scripts/gradle-retry.sh && \
    GRADLE_RETRY_LOG=/tmp/gradle.log .github/scripts/gradle-retry.sh bootJar -x test --no-daemon -q --console=plain

# ---- Runtime Stage ----
FROM eclipse-temurin:25-jre-noble
WORKDIR /app

# Port is set at build time via docker-compose build args.
# Override at runtime with: docker run -e SERVER_PORT=xxxx
ARG APP_PORT=8080
ENV SERVER_PORT=${APP_PORT}

RUN groupadd -r appgroup && useradd -r -g appgroup appuser \
    && apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

COPY --from=builder /app/build/libs/*.jar app.jar

RUN chown appuser:appgroup app.jar
USER appuser

EXPOSE ${APP_PORT}

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=45", "-XX:InitialRAMPercentage=20", "-jar", "app.jar"]
