package com.ahogek.cttserver.common.config;

import com.ahogek.cttserver.common.BaseIntegrationTest;

import org.junit.jupiter.api.Assumptions;
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
    void shouldIncludeXContentTypeOptions_whenAnyRequestIsServed() {
        // Management endpoints moved to the dedicated management server, so the header policy is
        // asserted on a public application response instead.
        assertThat(mvc.get().uri("/api/v1/config/public"))
                .satisfies(
                        result -> {
                            String value = result.getResponse().getHeader("X-Content-Type-Options");
                            assertThat(value).isEqualTo("nosniff");
                        });
    }

    @Test
    @DisplayName("Should include X-XSS-Protection header")
    void shouldIncludeXXssProtection_whenAnyRequestIsServed() {
        assertThat(mvc.get().uri("/api/v1/config/public"))
                .satisfies(
                        result -> {
                            String value = result.getResponse().getHeader("X-XSS-Protection");
                            assertThat(value).isEqualTo("1; mode=block");
                        });
    }

    @Test
    @DisplayName("Should include X-Frame-Options: DENY header")
    void shouldIncludeXFrameOptions_whenAnyRequestIsServed() {
        assertThat(mvc.get().uri("/api/v1/config/public"))
                .satisfies(
                        result -> {
                            String value = result.getResponse().getHeader("X-Frame-Options");
                            assertThat(value).isEqualTo("DENY");
                        });
    }

    @Test
    @DisplayName("Should include Strict-Transport-Security header when enabled")
    void shouldIncludeHsts_whenTheRequestIsHttps() {
        // HSTS is only sent over HTTPS; in HTTP test environment it may be absent
        assertThat(mvc.get().uri("/api/v1/config/public"))
                .satisfies(
                        result -> {
                            String hsts =
                                    result.getResponse().getHeader("Strict-Transport-Security");
                            Assumptions.assumeTrue(
                                    hsts != null, "HSTS header only present for HTTPS requests");
                            assertThat(hsts.toLowerCase()).contains("max-age=31536000");
                        });
    }

    @Test
    @DisplayName("Should include Content-Security-Policy header")
    void shouldIncludeContentSecurityPolicy_whenAnyRequestIsServed() {
        assertThat(mvc.get().uri("/api/v1/config/public"))
                .satisfies(
                        result -> {
                            String value =
                                    result.getResponse().getHeader("Content-Security-Policy");
                            assertThat(value)
                                    .isEqualTo(
                                            "default-src 'self'; "
                                                    + "script-src 'self' https://hcaptcha.com https://*.hcaptcha.com; "
                                                    + "frame-src 'self' https://*.hcaptcha.com; "
                                                    + "connect-src 'self' https://*.hcaptcha.com; "
                                                    + "img-src 'self' data: https://*.hcaptcha.com; "
                                                    + "style-src 'self' 'unsafe-inline'; "
                                                    + "font-src 'self' data:; "
                                                    + "object-src 'none'; "
                                                    + "base-uri 'self'; "
                                                    + "form-action 'self'; "
                                                    + "frame-ancestors 'none'");
                        });
    }

    @Test
    @DisplayName("Should include Referrer-Policy: no-referrer header")
    void shouldIncludeReferrerPolicy_whenAnyRequestIsServed() {
        assertThat(mvc.get().uri("/api/v1/config/public"))
                .satisfies(
                        result -> {
                            String value = result.getResponse().getHeader("Referrer-Policy");
                            assertThat(value).isEqualTo("no-referrer");
                        });
    }
}
