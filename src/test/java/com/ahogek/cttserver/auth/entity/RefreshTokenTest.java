package com.ahogek.cttserver.auth.entity;

import com.ahogek.cttserver.auth.enums.TokenStatus;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshTokenTest {

    private RefreshToken createValidToken() {
        RefreshToken token = new RefreshToken();
        token.setUserId(UUID.randomUUID());
        token.setTokenHash("hashedToken123");
        token.setDeviceId("device-456");
        token.setExpiresAt(Instant.now().plus(30, ChronoUnit.DAYS));
        return token;
    }

    @Test
    void determineStatusReturnsUnavailableWhenExpiresAtIsNull() {
        RefreshToken token = new RefreshToken();
        token.setUserId(UUID.randomUUID());
        token.setTokenHash("hashedToken123");
        // expiresAt is null

        assertThat(token.determineStatus()).isEqualTo(TokenStatus.UNAVAILABLE);
    }

    @Test
    void determineStatusReturnsValidForFreshToken() {
        RefreshToken token = createValidToken();

        assertThat(token.determineStatus()).isEqualTo(TokenStatus.VALID);
        assertThat(token.isValid()).isTrue();
    }

    @Test
    void determineStatusReturnsExpiredForPastExpiration() {
        RefreshToken token = createValidToken();
        token.setExpiresAt(Instant.now().minus(1, ChronoUnit.HOURS));

        assertThat(token.determineStatus()).isEqualTo(TokenStatus.EXPIRED);
        assertThat(token.isValid()).isFalse();
    }

    @Test
    void determineStatusReturnsRevokedAfterRevocation() {
        RefreshToken token = createValidToken();
        token.revoke();

        assertThat(token.determineStatus()).isEqualTo(TokenStatus.REVOKED);
        assertThat(token.isValid()).isFalse();
        assertThat(token.getRevokedAt()).isNotNull();
    }

    @Test
    void revokeIsIdempotent() {
        RefreshToken token = createValidToken();
        token.revoke();
        Instant firstRevokedAt = token.getRevokedAt();

        token.revoke();

        assertThat(token.getRevokedAt()).isEqualTo(firstRevokedAt);
    }

    @Test
    void revokedAtIsSetAfterRevocation() {
        RefreshToken token = createValidToken();
        Instant before = Instant.now();

        token.revoke();

        Instant after = Instant.now();
        assertThat(token.getRevokedAt()).isBetween(before, after);
    }

    @Test
    void isValidReturnsFalseForExpiredToken() {
        RefreshToken token = createValidToken();
        token.setExpiresAt(Instant.now().minus(1, ChronoUnit.MINUTES));

        assertThat(token.isValid()).isFalse();
    }

    @Test
    void isValidReturnsFalseForRevokedToken() {
        RefreshToken token = createValidToken();
        token.revoke();

        assertThat(token.isValid()).isFalse();
    }

    @Test
    void isValidReturnsTrueForValidToken() {
        RefreshToken token = createValidToken();

        assertThat(token.isValid()).isTrue();
    }

    @Test
    void isValidReturnsFalseForUnavailableToken() {
        RefreshToken token = new RefreshToken();
        token.setUserId(UUID.randomUUID());
        token.setTokenHash("hashedToken123");
        // expiresAt is null

        assertThat(token.isValid()).isFalse();
    }

    @Test
    void deviceIdCanBeNull() {
        RefreshToken token = createValidToken();
        token.setDeviceId(null);

        assertThat(token.getDeviceId()).isNull();
        assertThat(token.determineStatus()).isEqualTo(TokenStatus.VALID);
    }

    @Test
    void revokedTokenStaysRevokedEvenAfterExpiration() {
        RefreshToken token = createValidToken();
        token.revoke();
        token.setExpiresAt(Instant.now().minus(1, ChronoUnit.HOURS));

        // Revoked status takes precedence
        assertThat(token.determineStatus()).isEqualTo(TokenStatus.REVOKED);
    }
}
