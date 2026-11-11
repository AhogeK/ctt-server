package com.ahogek.cttserver.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the management surface declared by the base profile.
 *
 * <p>The test profile replaces the management port with a random one so cached contexts can run in
 * parallel, which means no other test would notice the documented defaults drifting. Prometheus
 * scrapers, container healthchecks and the deployment documentation all rely on them.
 *
 * @author AhogeK
 * @since 2026-11-11
 */
class ManagementSurfaceContractTest {

    private static final StandardEnvironment BASE = loadBaseProfile();

    private static StandardEnvironment loadBaseProfile() {
        StandardEnvironment environment = new StandardEnvironment();
        environment
                .getPropertySources()
                .remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment
                .getPropertySources()
                .remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        try {
            new YamlPropertySourceLoader()
                    .load("application", new ClassPathResource("application.yaml"))
                    .forEach(environment.getPropertySources()::addLast);
        } catch (IOException ex) {
            throw new IllegalStateException("cannot read application.yaml", ex);
        }
        return environment;
    }

    @Test
    void shouldBindTheManagementServerToLoopback_whenTheBaseProfileIsLoaded() {
        assertThat(BASE.getProperty("management.server.address")).isEqualTo("127.0.0.1");
    }

    @Test
    void shouldUseTheDocumentedPort_whenNothingOverridesIt() {
        assertThat(BASE.getProperty("management.server.port")).isEqualTo("8081");
    }

    @Test
    void shouldExposeExactlyTheDocumentedEndpoints_whenTheBaseProfileIsLoaded() {
        assertThat(BASE.getProperty("management.endpoints.web.exposure.include"))
                .isEqualTo("health,info,prometheus");
    }

    @Test
    void shouldEnableThePrometheusExport_whenTheBaseProfileIsLoaded() {
        assertThat(BASE.getProperty("management.prometheus.metrics.export.enabled"))
                .isEqualTo("true");
    }
}
