package com.ahogek.cttserver.common.config;

import com.ahogek.cttserver.common.BaseIntegrationTest;
import com.ahogek.cttserver.common.LoopbackHttpProbe;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.actuate.endpoint.HealthEndpointGroup;
import org.springframework.boot.health.actuate.endpoint.HealthEndpointGroups;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.health.registry.HealthContributorRegistry;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the Kubernetes-style health probe contract.
 *
 * <p>Liveness must stay independent of external dependencies; readiness must include the database
 * and Redis and must stop routing traffic when either is unavailable.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-11-09
 */
@BaseIntegrationTest
@DisplayName("Health probe contract")
class HealthProbeIntegrationTest {

    @Autowired private HealthEndpointGroups healthGroups;

    @Autowired private HealthContributorRegistry healthContributors;

    @LocalManagementPort private int managementPort;

    @Test
    @DisplayName("Should exclude database and Redis from liveness")
    void shouldExcludeDatabaseAndRedis_whenLivenessGroupIsBuilt() {
        HealthEndpointGroup liveness = healthGroups.get("liveness");

        assertThat(liveness)
                .isNotNull()
                .extracting(
                        group -> group.isMember("livenessState"),
                        group -> group.isMember("db"),
                        group -> group.isMember("redis"))
                .containsExactly(true, false, false);
        assertThat(
                        healthContributors.stream()
                                .map(entry -> entry.name())
                                .filter(name -> !"livenessState".equals(name))
                                .filter(liveness::isMember)
                                .toList())
                .isEmpty();
    }

    @Test
    @DisplayName("Should include readiness state, database and Redis in readiness")
    void shouldIncludeReadinessStateDatabaseAndRedis_whenReadinessGroupIsBuilt() {
        HealthEndpointGroup readiness = healthGroups.get("readiness");

        assertThat(readiness)
                .isNotNull()
                .extracting(
                        group -> group.isMember("readinessState"),
                        group -> group.isMember("db"),
                        group -> group.isMember("redis"))
                .containsExactly(true, true, true);
        assertThat(healthContributors.stream().map(entry -> entry.name()).toList())
                .contains("db", "redis");
    }

    @Test
    @DisplayName("Should map a failed dependency to OUT_OF_SERVICE only for readiness")
    void shouldMapDependencyFailureToOutOfService_whenReadinessAggregates() {
        HealthEndpointGroup readiness = healthGroups.get("readiness");
        HealthEndpointGroup liveness = healthGroups.get("liveness");

        assertThat(readiness)
                .extracting(
                        group ->
                                group.getStatusAggregator()
                                        .getAggregateStatus(Set.of(Status.DOWN, Status.UP)))
                .isEqualTo(Status.OUT_OF_SERVICE);
        assertThat(
                        readiness
                                .getStatusAggregator()
                                .getAggregateStatus(Set.of(Status.UNKNOWN, Status.UP)))
                .isEqualTo(Status.OUT_OF_SERVICE);
        assertThat(liveness)
                .extracting(
                        group ->
                                group.getStatusAggregator()
                                        .getAggregateStatus(Set.of(Status.DOWN, Status.UP)))
                .isEqualTo(Status.DOWN);
    }

    @Test
    @DisplayName("Should map readiness OUT_OF_SERVICE to HTTP 503")
    void shouldMapOutOfServiceTo503_whenReadinessIsUnavailable() {
        HealthEndpointGroup readiness = healthGroups.get("readiness");

        assertThat(readiness.getHttpCodeStatusMapper().getStatusCode(Status.OUT_OF_SERVICE))
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
    }

    @Test
    @DisplayName("Should expose probe endpoints without authentication")
    void shouldExposeProbeEndpoints_whenUnauthenticated() throws IOException {
        // Probes live on the dedicated loopback management server, so the contract is asserted over
        // HTTP on that port rather than through MockMvc on the application context.
        assertThat(LoopbackHttpProbe.status(managementPort, "/actuator/health/liveness"))
                .isEqualTo(200);
        assertThat(LoopbackHttpProbe.status(managementPort, "/actuator/health/readiness"))
                .isEqualTo(200);
        assertThat(LoopbackHttpProbe.body(managementPort, "/actuator/health/liveness"))
                .contains("\"status\":\"UP\"");
        assertThat(LoopbackHttpProbe.body(managementPort, "/actuator/health/readiness"))
                .contains("\"status\":\"UP\"");
    }
}
