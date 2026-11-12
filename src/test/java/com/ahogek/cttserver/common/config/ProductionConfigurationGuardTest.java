package com.ahogek.cttserver.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Every check in {@link ProductionConfigurationGuard} reports the property it rejected. */
class ProductionConfigurationGuardTest {

    private static final String SITE = "https://ctt.ahogek.com";
    private static final String STRONG_JWT =
            "bG9uZy1yYW5kb20tc2VjcmV0LWZvci10ZXN0aW5nLW9ubHktcHJvZHVjdGlvbg==";
    private static final String STRONG_KEY = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
    private static final String STRONG_DB = "0123456789abcdef0123456789abcdef";
    private static final String GITHUB_SECRET = "0123456789abcdef0123456789abcdef01234567";

    private static MockEnvironment productionEnvironment() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("springdoc.api-docs.enabled", "false");
        environment.setProperty("springdoc.swagger-ui.enabled", "false");
        environment.setProperty("ctt.security.cors.allowed-origins[0]", SITE);
        environment.setProperty("ctt.security.cors.allowed-origin-patterns[0]", SITE);
        environment.setProperty("ctt.security.oauth.frontend-url", SITE);
        environment.setProperty("ctt.mail.frontend.base-url", SITE);
        environment.setProperty("ctt.security.oauth.github.client-id", "Ov23liExampleClientId");
        environment.setProperty("ctt.security.oauth.github.client-secret", GITHUB_SECRET);
        environment.setProperty("ctt.security.jwt.secret-key", STRONG_JWT);
        environment.setProperty("ctt.security.oauth.token-encryption-key", STRONG_KEY);
        environment.setProperty("spring.datasource.password", STRONG_DB);
        environment.setProperty("spring.data.redis.password", "0123456789abcdef");
        environment.setProperty("spring.mail.password", "re_0123456789abcdef0123456789abcdef");
        environment.setProperty("ctt.security.two-factor.encryption-key", STRONG_KEY);
        environment.setProperty("management.server.port", "8081");
        environment.setProperty("management.server.address", "127.0.0.1");
        return environment;
    }

    private static List<String> violations(MockEnvironment environment) {
        return ProductionConfigurationGuard.violations(environment);
    }

    @Test
    void shouldAccept_whenEveryProductionValueIsSet() {
        assertThat(violations(productionEnvironment())).isEmpty();
    }

    @Test
    void shouldRejectApiDocs_whenEnabled() {
        MockEnvironment environment = productionEnvironment();
        environment.setProperty("springdoc.api-docs.enabled", "true");

        assertThat(violations(environment))
                .anyMatch(it -> it.contains("springdoc.api-docs.enabled"));
    }

    @Test
    void shouldRejectSwaggerUi_whenEnabled() {
        MockEnvironment environment = productionEnvironment();
        environment.setProperty("springdoc.swagger-ui.enabled", "true");

        assertThat(violations(environment))
                .anyMatch(it -> it.contains("springdoc.swagger-ui.enabled"));
    }

    @Test
    void shouldRejectCors_whenOriginIsThePlaceholderDomain() {
        MockEnvironment environment = productionEnvironment();
        environment.setProperty("ctt.security.cors.allowed-origins[0]", "https://ctt.example.com");

        assertThat(violations(environment))
                .anyMatch(it -> it.contains("CORS origin") && it.contains("ctt.example.com"));
    }

    @Test
    void shouldRejectCors_whenOriginUsesAWildcard() {
        MockEnvironment environment = productionEnvironment();
        environment.setProperty(
                "ctt.security.cors.allowed-origin-patterns[0]", "https://*.ahogek.com");

        assertThat(violations(environment)).anyMatch(it -> it.contains("CORS origin"));
    }

    @Test
    void shouldRejectCors_whenOriginIsLocalhost() {
        MockEnvironment environment = productionEnvironment();
        environment.setProperty("ctt.security.cors.allowed-origins[0]", "http://localhost:5173");

        assertThat(violations(environment)).anyMatch(it -> it.contains("CORS origin"));
    }

    @Test
    void shouldRejectGithubOauth_whenSecretIsMissing() {
        MockEnvironment environment = productionEnvironment();
        environment.setProperty("ctt.security.oauth.github.client-secret", "");

        assertThat(violations(environment))
                .anyMatch(it -> it.contains("oauth.github.client-secret"));
    }

    @Test
    void shouldRejectRedis_whenTheCiLiteralIsUsed() {
        MockEnvironment environment = productionEnvironment();
        environment.setProperty("spring.data.redis.password", "ctt_redis_pass");

        assertThat(violations(environment))
                .anyMatch(
                        it ->
                                it.contains("spring.data.redis.password")
                                        && it.contains("development value"));
    }

    @Test
    void shouldRejectFrontendUrl_whenItIsLocalhost() {
        MockEnvironment environment = productionEnvironment();
        environment.setProperty("ctt.security.oauth.frontend-url", "http://localhost:5173");

        assertThat(violations(environment)).anyMatch(it -> it.contains("oauth.frontend-url"));
    }

    @Test
    void shouldRejectSecret_whenItCarriesADevelopmentMarker() {
        MockEnvironment environment = productionEnvironment();
        environment.setProperty(
                "ctt.security.jwt.secret-key", "local-dev-jwt-secret-key-value-1234567890");

        assertThat(violations(environment))
                .anyMatch(it -> it.contains("jwt.secret-key") && it.contains("development value"));
    }

    @Test
    void shouldRejectSecret_whenTheDatabasePasswordIsShort() {
        MockEnvironment environment = productionEnvironment();
        environment.setProperty("spring.datasource.password", "short-pass");

        assertThat(violations(environment))
                .anyMatch(
                        it ->
                                it.contains("spring.datasource.password")
                                        && it.contains("at least 16"));
    }

    @Test
    void shouldRejectTotp_whenThePublicFallbackIsUsed() {
        MockEnvironment environment = productionEnvironment();
        environment.setProperty(
                "ctt.security.two-factor.encryption-key",
                "TE9DQUxfREVWX1RPVFBfS0VZX05PVF9GT1JfUFJPRFg=");

        assertThat(violations(environment))
                .anyMatch(it -> it.contains("two-factor.encryption-key"));
    }

    @Test
    void shouldReportEveryViolation_whenSeveralPropertiesAreWrong() {
        MockEnvironment environment = productionEnvironment();
        environment.setProperty("springdoc.api-docs.enabled", "true");
        environment.setProperty("ctt.security.cors.allowed-origins[0]", "https://ctt.example.com");
        environment.setProperty("ctt.security.oauth.github.client-id", "");
        environment.setProperty("spring.datasource.password", "short");

        assertThat(violations(environment)).hasSizeGreaterThanOrEqualTo(4);
    }

    @Test
    void shouldRejectManagementSurface_whenItSharesTheApplicationPort() {
        MockEnvironment environment = productionEnvironment();
        environment.setProperty("server.port", "8081");

        assertThat(violations(environment))
                .anyMatch(
                        it -> it.contains("management.server.port") && it.contains("server.port"));
    }

    @Test
    void shouldRejectManagementSurface_whenTheAddressIsNotLoopback() {
        MockEnvironment environment = productionEnvironment();
        environment.setProperty("management.server.address", "0.0.0.0");

        assertThat(violations(environment))
                .anyMatch(it -> it.contains("management.server.address") && it.contains("0.0.0.0"));
    }

    @Test
    void shouldRejectManagementSurface_whenThePortIsRandom() {
        MockEnvironment environment = productionEnvironment();
        environment.setProperty("management.server.port", "0");

        assertThat(violations(environment))
                .anyMatch(it -> it.contains("management.server.port") && it.contains("fixed port"));
    }

    @Test
    void shouldRejectTracing_whenTheExportIsDisabled() {
        MockEnvironment environment = productionEnvironment();
        environment.setProperty("management.tracing.export.enabled", "false");

        assertThat(violations(environment))
                .anyMatch(it -> it.contains("management.tracing.export.enabled"));
    }

    @Test
    void shouldRejectTracing_whenTheSamplingRatioIsZero() {
        MockEnvironment environment = productionEnvironment();
        environment.setProperty("management.tracing.sampling.probability", "0");

        assertThat(violations(environment))
                .anyMatch(it -> it.contains("management.tracing.sampling.probability"));
    }

    @Test
    void shouldRejectTracing_whenTracingIsDisabled() {
        MockEnvironment environment = productionEnvironment();
        environment.setProperty("management.tracing.enabled", "false");

        assertThat(violations(environment))
                .anyMatch(it -> it.contains("management.tracing.enabled"));
    }

    @Test
    void shouldRejectTracing_whenNothingProducesW3cHeaders() {
        MockEnvironment environment = productionEnvironment();
        environment.setProperty("management.tracing.propagation.produce[0]", "B3");

        assertThat(violations(environment))
                .anyMatch(it -> it.contains("management.tracing.propagation.produce"));
    }

    @Test
    void shouldThrow_whenTheConfigurationIsRejected() {
        MockEnvironment environment = productionEnvironment();
        environment.setProperty("springdoc.api-docs.enabled", "true");

        assertThatThrownBy(() -> new ProductionConfigurationGuard(environment).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Production configuration rejected")
                .hasMessageContaining("springdoc.api-docs.enabled");
    }

    @Test
    void shouldNotThrow_whenTheConfigurationPasses() {
        new ProductionConfigurationGuard(productionEnvironment()).afterPropertiesSet();
    }
}
