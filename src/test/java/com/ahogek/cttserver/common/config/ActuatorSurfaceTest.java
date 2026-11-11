package com.ahogek.cttserver.common.config;

import com.ahogek.cttserver.common.BaseIntegrationTest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.endpoint.web.WebEndpointsSupplier;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.io.IOException;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the split between the application port and the management port.
 *
 * <p>Probes and metrics must be reachable only on the dedicated management server. The application
 * port carries the public API and must not answer a management path, because the reverse proxy
 * forwards exactly that port to the internet.
 *
 * <p>The test profile already runs the split on a random loopback port, so this class shares the
 * cached context and asserts the surface a deployment exposes.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-11-11
 */
@BaseIntegrationTest
@DisplayName("Actuator surface")
class ActuatorSurfaceTest {

    @Autowired private WebEndpointsSupplier webEndpointsSupplier;

    @LocalServerPort private int applicationPort;

    @LocalManagementPort private int managementPort;

    @Test
    @DisplayName("Should serve the health probes on the management port")
    void shouldServeProbesOnTheManagementPort_whenTheServerIsSplit() throws IOException {
        assertThat(LoopbackHttpProbe.status(managementPort, "/actuator/health/liveness"))
                .isEqualTo(200);
        assertThat(LoopbackHttpProbe.status(managementPort, "/actuator/health/readiness"))
                .isEqualTo(200);
        assertThat(LoopbackHttpProbe.body(managementPort, "/actuator/health/liveness"))
                .contains("\"status\":\"UP\"");
    }

    @Test
    @DisplayName("Should serve Prometheus metrics on the management port")
    void shouldServeMetricsOnTheManagementPort_whenTheServerIsSplit() throws IOException {
        HttpResponse<String> response =
                LoopbackHttpProbe.get(managementPort, "/actuator/prometheus");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type"))
                .hasValueSatisfying(type -> assertThat(type).startsWith("text/plain"));
        assertThat(response.body())
                .contains("# HELP jvm_memory_used_bytes")
                .contains("process_uptime_seconds");
    }

    @Test
    @DisplayName("Should register only the documented web endpoints")
    void shouldRegisterOnlyDocumentedEndpoints_whenTheServerIsSplit() {
        assertThat(webEndpointsSupplier.getEndpoints())
                .map(endpoint -> endpoint.getEndpointId().toString())
                .containsExactlyInAnyOrder("health", "info", "prometheus");
    }

    @Test
    @DisplayName("Should not serve any management path on the application port")
    void shouldNotServeManagementPaths_whenTheyTargetTheApplicationPort() throws IOException {
        assertThat(managementPort).isNotEqualTo(applicationPort);

        // The application context registers no management endpoint, and the security chain permits
        // these paths for the loopback scraper, so dispatch answers 404. The assertion that matters
        // is that no management payload escapes the public port.
        assertThat(LoopbackHttpProbe.status(applicationPort, "/ctt-server/actuator/health"))
                .isEqualTo(404);
        assertThat(LoopbackHttpProbe.status(applicationPort, "/ctt-server/actuator/prometheus"))
                .isEqualTo(404);
        assertThat(LoopbackHttpProbe.status(applicationPort, "/actuator/prometheus"))
                .isEqualTo(404);
        // A management path outside the allowlist stops at the security chain instead of dispatch,
        // so it answers 401 rather than 404; no handler exists on this port either way.
        assertThat(LoopbackHttpProbe.status(applicationPort, "/ctt-server/actuator/metrics"))
                .isEqualTo(401);
        assertThat(LoopbackHttpProbe.body(applicationPort, "/ctt-server/actuator/prometheus"))
                .doesNotContain("jvm_memory_used_bytes");
    }

    @Test
    @DisplayName("Should keep serving the public API on the application port")
    void shouldKeepServingTheApi_whenTheServerIsSplit() throws IOException {
        assertThat(LoopbackHttpProbe.status(applicationPort, "/ctt-server/api/v1/config/public"))
                .isEqualTo(200);
    }
}
