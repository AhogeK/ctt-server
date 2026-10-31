package com.ahogek.cttserver.auth.twofactor.service;

import com.ahogek.cttserver.common.config.properties.TwoFactorProperties;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TOTP service")
class TotpServiceTest {

    private static final String KEY =
            Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes());
    private static final String RFC_SECRET = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";

    private final TotpService service =
            new TotpService(properties(), Clock.fixed(Instant.ofEpochSecond(59), ZoneOffset.UTC));

    @Test
    @DisplayName("Should produce the RFC 6238 SHA-1 six-digit vector")
    void shouldProduceRfcVector_whenGeneratingCode() {
        assertThat(service.codeAt(RFC_SECRET, 1)).isEqualTo("287082");
    }

    @Test
    @DisplayName("Should find the matching step within the window")
    void shouldFindMatchingStep_whenCodeIsWithinWindow() {
        assertThat(service.matchingStep(RFC_SECRET, "287082", Instant.ofEpochSecond(89)))
                .hasValue(1);
    }

    @Test
    @DisplayName("Should reject a code outside the window")
    void shouldRejectCode_whenOutsideWindow() {
        assertThat(service.matchingStep(RFC_SECRET, "287082", Instant.ofEpochSecond(150)))
                .isEmpty();
    }

    @Test
    @DisplayName("Should build an otpauth URI with issuer and account")
    void shouldBuildOtpauthUri_whenRequested() {
        String uri = service.otpauthUri(RFC_SECRET, "user@example.com");

        assertThat(uri)
                .startsWith("otpauth://totp/")
                .contains("issuer=Code%20Time%20Tracker")
                .contains("secret=" + RFC_SECRET);
    }

    private static TwoFactorProperties properties() {
        return new TwoFactorProperties(
                "Code Time Tracker",
                KEY,
                6,
                30,
                1,
                Duration.ofMinutes(10),
                Duration.ofMinutes(5),
                Duration.ofMinutes(10),
                10);
    }
}
