# ---- Build Stage ----
FROM eclipse-temurin:25-jdk AS builder
WORKDIR /app

COPY gradle/ gradle/
COPY gradlew build.gradle.kts settings.gradle.kts gradle.properties* ./
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon -q

COPY src/ src/
RUN ./gradlew bootJar -x test --no-daemon -q

# ---- Runtime Stage ----
FROM eclipse-temurin:25-jre-noble
WORKDIR /app

RUN groupadd -r appgroup && useradd -r -g appgroup appuser

COPY --from=builder /app/build/libs/*.jar app.jar

RUN chown appuser:appgroup app.jar
USER appuser

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
