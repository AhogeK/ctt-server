# Local image: packages the boot jar that `deploy/local/up.sh` builds on the host.
#
# The repository Dockerfile builds inside the container. That path needs Maven
# Central from within the build network, which this host's proxy breaks
# ("Remote host terminated the handshake"), so the local stack packages the
# jar instead. Production images still come from the multi-stage Dockerfile.
FROM eclipse-temurin:25-jre-noble

WORKDIR /app

RUN groupadd -r appgroup && useradd -r -g appgroup appuser \
    && apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

COPY build/local-image/app.jar app.jar
RUN chown appuser:appgroup app.jar

USER appuser
EXPOSE 8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=45", "-XX:InitialRAMPercentage=20", "-jar", "/app/app.jar"]
