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
 * @author AhogeK
 * @since 2026-03-03
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    /**
     * PostgreSQL 16.3 container for database tests. Uses official postgres image with fixed minor
     * version for reproducibility.
     */
    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:16.3"));
    }

    /**
     * Redis 7.2 container for cache tests. Uses official redis image with fixed minor version for
     * reproducibility.
     */
    @Bean
    @ServiceConnection(name = "redis")
    @SuppressWarnings("resource")
    GenericContainer<?> redisContainer() {
        return new GenericContainer<>(DockerImageName.parse("redis:7.2")).withExposedPorts(6379);
    }
}
