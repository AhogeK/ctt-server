package com.ahogek.cttserver;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Testcontainers configuration for integration tests.
 *
 * <p>Provides PostgreSQL and Redis containers for testing. Uses latest Docker image tags to stay
 * current with upstream updates.
 *
 * <p><b>Image version policy:</b> Uses `latest` tags to always run the most recent stable versions.
 * This ensures compatibility with production environments that also track latest releases.
 *
 * <p><b>Container reuse:</b> Containers can be reused across JVM processes for faster local
 * development. Enable by setting {@code ~/.testcontainers.properties}:
 *
 * <pre>
 * testcontainers.reuse.enable=true
 * </pre>
 *
 * <p>Reuse is automatically disabled in CI environments to prevent state pollution.
 *
 * @author AhogeK
 * @since 2026-03-03
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    private static final boolean IS_CI = "true".equalsIgnoreCase(System.getenv("CI"));

    /**
     * PostgreSQL container for database tests. Uses latest official postgres image to stay current
     * with production environment.
     *
     * <p>Container reuse is enabled in local development (disabled in CI) for faster test cycles.
     */
    @Bean
    @ServiceConnection
    @SuppressWarnings("resource")
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:latest")).withReuse(!IS_CI);
    }

    /**
     * Redis container for cache tests. Uses latest official redis image to stay current with
     * production environment.
     *
     * <p>Container reuse is enabled in local development (disabled in CI) for faster test cycles.
     */
    @Bean
    @ServiceConnection(name = "redis")
    @SuppressWarnings("resource")
    GenericContainer<?> redisContainer() {
        return new GenericContainer<>(DockerImageName.parse("redis:latest"))
                .withExposedPorts(6379)
                .withReuse(!IS_CI);
    }
}
