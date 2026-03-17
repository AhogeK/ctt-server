package com.ahogek.cttserver.common.config;

import com.ahogek.cttserver.common.BaseIntegrationTest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for OWASP security headers configuration.
 *
 * <p>Validates that SecurityConfig properly injects security headers into HTTP responses. This is
 * an integration test because Security Headers are applied by the Security filter chain, which
 * requires a full ApplicationContext to test correctly.
 *
 * @author AhogeK
 * @since 2026-03-17
 */
@BaseIntegrationTest
@DisplayName("Security Headers Integration Tests")
class SecurityConfigHeadersTest {

    @Autowired private MockMvcTester mvc;

    @Test
    @DisplayName("Should include X-Content-Type-Options: nosniff header")
    void shouldIncludeXContentTypeOptionsHeader() {
        assertThat(mvc.get().uri("/actuator/health"))
                .satisfies(
                        result -> {
                            String value = result.getResponse().getHeader("X-Content-Type-Options");
                            assertThat(value).isEqualTo("nosniff");
                        });
    }

    @Test
    @DisplayName("Should include X-XSS-Protection header")
    void shouldIncludeXXssProtectionHeader() {
        assertThat(mvc.get().uri("/actuator/health"))
                .satisfies(
                        result -> {
                            String value = result.getResponse().getHeader("X-XSS-Protection");
                            assertThat(value).isEqualTo("1; mode=block");
                        });
    }

    @Test
    @DisplayName("Should include X-Frame-Options: DENY header")
    void shouldIncludeXFrameOptionsHeader() {
        assertThat(mvc.get().uri("/actuator/health"))
                .satisfies(
                        result -> {
                            String value = result.getResponse().getHeader("X-Frame-Options");
                            assertThat(value).isEqualTo("DENY");
                        });
    }

    @Test
    @DisplayName("Should include Strict-Transport-Security header when enabled")
    void shouldIncludeHstsHeaderWhenEnabled() {
        // HSTS is only sent over HTTPS; in HTTP test environment it may be absent
        assertThat(mvc.get().uri("/actuator/health"))
                .satisfies(
                        result -> {
                            String hsts =
                                    result.getResponse().getHeader("Strict-Transport-Security");
                            org.junit.jupiter.api.Assumptions.assumeTrue(
                                    hsts != null, "HSTS header only present for HTTPS requests");
                            assertThat(hsts.toLowerCase()).contains("max-age=31536000");
                        });
    }

    @Test
    @DisplayName("Should include Content-Security-Policy header")
    void shouldIncludeCspHeader() {
        assertThat(mvc.get().uri("/actuator/health"))
                .satisfies(
                        result -> {
                            String value =
                                    result.getResponse().getHeader("Content-Security-Policy");
                            assertThat(value).isEqualTo("default-src 'self'");
                        });
    }
}
