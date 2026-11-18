# ---- Build Stage ----
FROM eclipse-temurin:25-jdk AS builder
# A build on a network that cannot reach the mirror can point the download
# elsewhere: --build-arg ARTIFACT_MIRROR_URL=https://repo.maven.apache.org/maven2/
# The host gate takes the same override as ORG_GRADLE_PROJECT_artifactMirrorUrl.
ARG ARTIFACT_MIRROR_URL=

WORKDIR /app

COPY gradle/ gradle/
# Same classifier as the host build: Maven Central answers 429 to shared runner
# IPs often enough that an image build failed on it while the host build, which
# already retries, passed the same commit.
COPY .github/scripts/gradle-retry.sh .github/scripts/gradle-retry.sh
COPY gradlew build.gradle.kts settings.gradle.kts gradle.properties* ./

# The dependency download depends on nothing but those paths, so this layer is a
# cache hit until a version or a build script changes — and a cache hit means the
# image build does not talk to a repository at all. That is the point: the
# download is both the slowest part of a cold build and the part a throttled
# registry breaks. Splitting it from the compile is also what keeps the cache
# useful, because `--cache-to type=gha,mode=min` only carries layers of the final
# image and the builder stage is not one of them.
#
# No `--mount=type=cache` on purpose: Docker does not export cache mounts to
# `--cache-to` and a fresh runner starts them empty, so a mount over
# `/root/.gradle` would download the graph again on every run — and shadow the
# copy this layer already holds. A plain layer is what turns the download into a
# cache hit; a dependency change grows the image cache by the Gradle home
# (~410 MB) and it stays until evicted.
RUN if [ -n "$ARTIFACT_MIRROR_URL" ]; then export ORG_GRADLE_PROJECT_artifactMirrorUrl="$ARTIFACT_MIRROR_URL"; fi && \
    chmod +x gradlew .github/scripts/gradle-retry.sh && \
    GRADLE_RETRY_LOG=/tmp/gradle-dependencies.log \
      .github/scripts/gradle-retry.sh downloadDependencies --no-daemon -q --console=plain

COPY src/ src/

RUN GRADLE_RETRY_LOG=/tmp/gradle-build.log \
      .github/scripts/gradle-retry.sh bootJar -x test --no-daemon -q --console=plain

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
