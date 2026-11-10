package com.ahogek.cttserver.common.config;

import com.ahogek.cttserver.common.BaseIntegrationTest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the production posture for the OpenAPI endpoints.
 *
 * <p>With {@code springdoc.api-docs.enabled=false} the path is unmapped; Spring reports a missing
 * resource, and the global handler must answer 404 rather than the catch-all 500. The production
 * profile relies on the same behaviour.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-11-10
 */
@BaseIntegrationTest
@TestPropertySource(properties = "springdoc.api-docs.enabled=false")
@DisplayName("Disabled API documentation")
class ApiDocsDisabledIntegrationTest {

    @Autowired private MockMvcTester mvc;

    @Test
    @DisplayName("Should answer 404 when the API docs path is disabled")
    void shouldReturn404_whenApiDocsAreDisabled() {
        assertThat(mvc.get().uri("/v3/api-docs"))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("COMMON_002");
    }
}
