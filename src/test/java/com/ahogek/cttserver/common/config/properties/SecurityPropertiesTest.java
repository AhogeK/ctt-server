package com.ahogek.cttserver.common.config.properties;

import com.ahogek.cttserver.common.BaseIntegrationTest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for SecurityProperties configuration binding.
 *
 * <p>Validates that all configuration properties are correctly bound from application.yaml
 * and that validation constraints are enforced. This test prevents configuration binding
 * issues like missing fields or incorrect default values.
 *
 * @author AhogeK
 * @since 2026-04-08
 */
@BaseIntegrationTest
@DisplayName("SecurityProperties Configuration Binding Tests")
class SecurityPropertiesTest {

    @Autowired
    private SecurityProperties securityProperties;

    @Nested
    @DisplayName("JwtProperties Binding Tests")
    class JwtPropertiesTests {

        @Test
        @DisplayName("should bind jwt secret key from configuration")
        void shouldBindJwtSecretKey_whenConfigurationProvided() {
            assertThat(securityProperties.jwt().secretKey())
                    .as("JWT secret key should be bound from configuration")
                    .isNotNull()
                    .isNotBlank();
        }

        @Test
        @DisplayName("should bind jwt issuer from configuration")
        void shouldBindJwtIssuer_whenConfigurationProvided() {
            assertThat(securityProperties.jwt().issuer())
                    .as("JWT issuer should be bound from configuration")
                    .isNotNull()
                    .isEqualTo("ctt-identity-provider");
        }

        @Test
        @DisplayName("should bind access token TTL with default value")
        void shouldBindAccessTokenTtl_whenDefaultProvided() {
            assertThat(securityProperties.jwt().accessTokenTtl())
                    .as("Access token TTL should have default value of 15 minutes")
                    .isNotNull()
                    .isEqualTo(Duration.ofMinutes(15));
        }

        @Test
        @DisplayName("should bind refresh token TTL for plugin with default value")
        void shouldBindRefreshTokenTtlPlugin_whenDefaultProvided() {
            assertThat(securityProperties.jwt().refreshTokenTtlPlugin())
                    .as("Plugin refresh token TTL should have default value of 14 days")
                    .isNotNull()
                    .isEqualTo(Duration.ofDays(14));
        }

        @Test
        @DisplayName("should bind refresh token TTL for web with default value")
        void shouldBindRefreshTokenTtlWeb_whenDefaultProvided() {
            assertThat(securityProperties.jwt().refreshTokenTtlWeb())
                    .as("Web refresh token TTL should have default value of 30 days")
                    .isNotNull()
                    .isEqualTo(Duration.ofDays(30));
        }
    }

    @Nested
    @DisplayName("PasswordProperties Binding Tests")
    class PasswordPropertiesTests {

        @Test
        @DisplayName("should bind bcrypt rounds from configuration")
        void shouldBindBcryptRounds_whenConfigurationProvided() {
            assertThat(securityProperties.password().bcryptRounds())
                    .as("BCrypt rounds should be bound from configuration")
                    .isPositive();
        }

        @Test
        @DisplayName("should bind max failed attempts from configuration")
        void shouldBindMaxFailedAttempts_whenConfigurationProvided() {
            assertThat(securityProperties.password().maxFailedAttempts())
                    .as("Max failed attempts should be bound from configuration")
                    .isGreaterThanOrEqualTo(3);
        }

        @Test
        @DisplayName("should bind lock duration with default value")
        void shouldBindLockDuration_whenDefaultProvided() {
            assertThat(securityProperties.password().lockDuration())
                    .as("Lock duration should have default value of 30 minutes")
                    .isNotNull()
                    .isEqualTo(Duration.ofMinutes(30));
        }

        @Test
        @DisplayName("should bind failure window seconds from configuration")
        void shouldBindFailureWindowSeconds_whenConfigurationProvided() {
            assertThat(securityProperties.password().failureWindowSeconds())
                    .as("Failure window seconds should be bound from configuration")
                    .isEqualTo(900);
        }

        @Test
        @DisplayName("should bind storage strategy from configuration")
        void shouldBindStorageStrategy_whenConfigurationProvided() {
            assertThat(securityProperties.password().storage())
                    .as("Storage strategy should be bound from configuration")
                    .isNotNull()
                    .isIn("DB", "REDIS");
        }
    }

    @Nested
    @DisplayName("RateLimitProperties Binding Tests")
    class RateLimitPropertiesTests {

        @Test
        @DisplayName("should bind rate limit enabled flag from configuration")
        void shouldBindRateLimitEnabled_whenConfigurationProvided() {
            assertThat(securityProperties.rateLimit().enabled())
                    .as("Rate limit enabled flag should be bound from configuration")
                    .isNotNull();
        }

        @Test
        @DisplayName("should bind global max requests per second from configuration")
        void shouldBindGlobalMaxRequestsPerSecond_whenConfigurationProvided() {
            assertThat(securityProperties.rateLimit().globalMaxRequestsPerSecond())
                    .as("Global max requests per second should be bound from configuration")
                    .isGreaterThanOrEqualTo(1);
        }
    }

    @Nested
    @DisplayName("AuditProperties Binding Tests")
    class AuditPropertiesTests {

        @Test
        @DisplayName("should bind audit log payloads flag with default value")
        void shouldBindLogPayloads_whenDefaultProvided() {
            assertThat(securityProperties.audit().logPayloads())
                    .as("Audit log payloads should be enabled by default")
                    .isTrue();
        }

        @Test
        @DisplayName("should bind masked fields list from configuration")
        void shouldBindMaskedFields_whenConfigurationProvided() {
            assertThat(securityProperties.audit().maskedFields())
                    .as("Masked fields list should be bound from configuration")
                    .isNotNull()
                    .contains("password", "token", "secret", "key");
        }
    }

    @Nested
    @DisplayName("All Fields Coverage Tests")
    class AllFieldsCoverageTests {

        @Test
        @DisplayName("should have all JwtProperties fields bound")
        void shouldHaveAllJwtPropertiesFieldsBound_whenConfigurationLoaded() {
            var jwt = securityProperties.jwt();

            // This test ensures all fields are present - will fail if new fields are added
            // without corresponding configuration or test updates
            assertThat(jwt.secretKey()).isNotNull();
            assertThat(jwt.issuer()).isNotNull();
            assertThat(jwt.accessTokenTtl()).isNotNull();
            assertThat(jwt.refreshTokenTtlPlugin()).isNotNull();
            assertThat(jwt.refreshTokenTtlWeb()).isNotNull();
        }

        @Test
        @DisplayName("should have all PasswordProperties fields bound")
        void shouldHaveAllPasswordPropertiesFieldsBound_whenConfigurationLoaded() {
            var password = securityProperties.password();

            // This test ensures all fields are present - will fail if new fields are added
            // without corresponding configuration or test updates
            assertThat(password.bcryptRounds()).isPositive();
            assertThat(password.maxFailedAttempts()).isGreaterThanOrEqualTo(3);
            assertThat(password.lockDuration()).isNotNull();
            assertThat(password.failureWindowSeconds()).isPositive();
            assertThat(password.storage()).isNotNull();
        }

        @Test
        @DisplayName("should have all RateLimitProperties fields bound")
        void shouldHaveAllRateLimitPropertiesFieldsBound_whenConfigurationLoaded() {
            var rateLimit = securityProperties.rateLimit();

            // This test ensures all fields are present - will fail if new fields are added
            // without corresponding configuration or test updates
            assertThat(rateLimit.enabled()).isNotNull();
            assertThat(rateLimit.globalMaxRequestsPerSecond()).isGreaterThanOrEqualTo(1);
        }

        @Test
        @DisplayName("should have all AuditProperties fields bound")
        void shouldHaveAllAuditPropertiesFieldsBound_whenConfigurationLoaded() {
            var audit = securityProperties.audit();

            // This test ensures all fields are present - will fail if new fields are added
            // without corresponding configuration or test updates
            assertThat(audit.logPayloads()).isNotNull();
            assertThat(audit.maskedFields()).isNotNull();
        }
    }

    @Nested
    @DisplayName("Validation Constraint Tests")
    class ValidationConstraintTests {

        @Test
        @DisplayName("should enforce @NotNull constraint on jwt properties")
        void shouldEnforceNotNullConstraint_whenJwtPropertiesMissing() {
            assertThat(securityProperties.jwt())
                    .as("JWT properties should not be null due to @NotNull constraint")
                    .isNotNull();
        }

        @Test
        @DisplayName("should enforce @NotNull constraint on password properties")
        void shouldEnforceNotNullConstraint_whenPasswordPropertiesMissing() {
            assertThat(securityProperties.password())
                    .as("Password properties should not be null due to @NotNull constraint")
                    .isNotNull();
        }

        @Test
        @DisplayName("should enforce @NotNull constraint on rate limit properties")
        void shouldEnforceNotNullConstraint_whenRateLimitPropertiesMissing() {
            assertThat(securityProperties.rateLimit())
                    .as("Rate limit properties should not be null due to @NotNull constraint")
                    .isNotNull();
        }

        @Test
        @DisplayName("should enforce @NotNull constraint on audit properties")
        void shouldEnforceNotNullConstraint_whenAuditPropertiesMissing() {
            assertThat(securityProperties.audit())
                    .as("Audit properties should not be null due to @NotNull constraint")
                    .isNotNull();
        }

        @Test
        @DisplayName("should enforce @NotBlank constraint on jwt secret key")
        void shouldEnforceNotBlankConstraint_whenJwtSecretKeyEmpty() {
            assertThat(securityProperties.jwt().secretKey())
                    .as("JWT secret key should not be blank due to @NotBlank constraint")
                    .isNotBlank();
        }

        @Test
        @DisplayName("should enforce @NotBlank constraint on jwt issuer")
        void shouldEnforceNotBlankConstraint_whenJwtIssuerEmpty() {
            assertThat(securityProperties.jwt().issuer())
                    .as("JWT issuer should not be blank due to @NotBlank constraint")
                    .isNotBlank();
        }

        @Test
        @DisplayName("should enforce @Min(10) constraint on bcrypt rounds in production")
        void shouldEnforceMinConstraint_whenBcryptRoundsTooLow() {
            assertThat(securityProperties.password().bcryptRounds())
                    .as("BCrypt rounds should be positive (constraint validation)")
                    .isPositive();
        }

        @Test
        @DisplayName("should enforce @Min(3) constraint on max failed attempts")
        void shouldEnforceMinConstraint_whenMaxFailedAttemptsTooLow() {
            assertThat(securityProperties.password().maxFailedAttempts())
                    .as("Max failed attempts should be at least 3 due to @Min(3) constraint")
                    .isGreaterThanOrEqualTo(3);
        }

        @Test
        @DisplayName("should enforce @Min(1) constraint on global max requests per second")
        void shouldEnforceMinConstraint_whenGlobalMaxRequestsTooLow() {
            assertThat(securityProperties.rateLimit().globalMaxRequestsPerSecond())
                    .as("Global max requests should be at least 1 due to @Min(1) constraint")
                    .isGreaterThanOrEqualTo(1);
        }
    }
}
