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
 * <p>Provides PostgreSQL and Redis containers for testing. Uses fixed Docker image versions to
 * ensure reproducible CI builds.
 *
 * <p><b>Image version policy:</b> Fixed versions prevent CI instability caused by upstream image
 * updates. Update versions intentionally when aligning with production environment changes.
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
     * PostgreSQL 16.3 container for database tests. Uses official postgres image with fixed minor
     * version for reproducibility.
     *
     * <p>Container reuse is enabled in local development (disabled in CI) for faster test cycles.
     */
    @Bean
    @ServiceConnection
    @SuppressWarnings("resource")
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:16.3")).withReuse(!IS_CI);
    }

    /**
     * Redis 7.2 container for cache tests. Uses official redis image with fixed minor version for
     * reproducibility.
     *
     * <p>Container reuse is enabled in local development (disabled in CI) for faster test cycles.
     */
    @Bean
    @ServiceConnection(name = "redis")
    @SuppressWarnings("resource")
    GenericContainer<?> redisContainer() {
        return new GenericContainer<>(DockerImageName.parse("redis:7.2"))
                .withExposedPorts(6379)
                .withReuse(!IS_CI);
    }
}
