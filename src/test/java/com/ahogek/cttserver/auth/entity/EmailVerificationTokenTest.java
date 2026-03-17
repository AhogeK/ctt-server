package com.ahogek.cttserver.auth.entity;

import com.ahogek.cttserver.auth.enums.TokenStatus;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailVerificationTokenTest {

    private EmailVerificationToken createValidToken() {
        EmailVerificationToken token = new EmailVerificationToken();
        token.setUserId(UUID.randomUUID());
        token.setTokenHash("hashedToken123");
        token.setExpiresAt(Instant.now().plus(24, ChronoUnit.HOURS));
        return token;
    }

    @Test
    void determineStatusReturnsUnavailableWhenExpiresAtIsNull() {
        EmailVerificationToken token = new EmailVerificationToken();
        token.setUserId(UUID.randomUUID());
        token.setTokenHash("hashedToken123");
        // expiresAt is null

        assertThat(token.determineStatus()).isEqualTo(TokenStatus.UNAVAILABLE);
    }

    @Test
    void determineStatusReturnsValidForFreshToken() {
        EmailVerificationToken token = createValidToken();

        assertThat(token.determineStatus()).isEqualTo(TokenStatus.VALID);
        assertThat(token.isValid()).isTrue();
    }

    @Test
    void determineStatusReturnsExpiredForPastExpiration() {
        EmailVerificationToken token = createValidToken();
        token.setExpiresAt(Instant.now().minus(1, ChronoUnit.HOURS));

        assertThat(token.determineStatus()).isEqualTo(TokenStatus.EXPIRED);
        assertThat(token.isValid()).isFalse();
    }

    @Test
    void determineStatusReturnsConsumedAfterConsumption() {
        EmailVerificationToken token = createValidToken();
        token.consume();

        assertThat(token.determineStatus()).isEqualTo(TokenStatus.CONSUMED);
        assertThat(token.isValid()).isFalse();
        assertThat(token.getConsumedAt()).isNotNull();
    }

    @Test
    void consumeThrowsExceptionForExpiredToken() {
        EmailVerificationToken token = createValidToken();
        token.setExpiresAt(Instant.now().minus(1, ChronoUnit.HOURS));

        assertThatThrownBy(token::consume)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Token is not valid for consumption");
    }

    @Test
    void consumeThrowsExceptionForAlreadyConsumedToken() {
        EmailVerificationToken token = createValidToken();
        token.consume();

        assertThatThrownBy(token::consume)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Token is not valid for consumption");
    }

    @Test
    void consumeThrowsExceptionForUnavailableToken() {
        EmailVerificationToken token = new EmailVerificationToken();
        token.setUserId(UUID.randomUUID());
        token.setTokenHash("hashedToken123");
        // expiresAt is null, so status is UNAVAILABLE

        assertThatThrownBy(token::consume)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Token is not valid for consumption");
    }

    @Test
    void isValidReturnsFalseForExpiredToken() {
        EmailVerificationToken token = createValidToken();
        token.setExpiresAt(Instant.now().minus(1, ChronoUnit.MINUTES));

        assertThat(token.isValid()).isFalse();
    }

    @Test
    void isValidReturnsTrueForValidToken() {
        EmailVerificationToken token = createValidToken();

        assertThat(token.isValid()).isTrue();
    }

    @Test
    void consumedAtIsSetAfterConsumption() {
        EmailVerificationToken token = createValidToken();
        Instant before = Instant.now();

        token.consume();

        Instant after = Instant.now();
        assertThat(token.getConsumedAt()).isBetween(before, after);
    }
}
