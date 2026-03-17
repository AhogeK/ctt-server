package com.ahogek.cttserver.auth.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TokenStatusTest {

    @Test
    void validStatusIsValid() {
        assertThat(TokenStatus.VALID.isValid()).isTrue();
    }

    @Test
    void expiredStatusIsNotValid() {
        assertThat(TokenStatus.EXPIRED.isValid()).isFalse();
    }

    @Test
    void consumedStatusIsNotValid() {
        assertThat(TokenStatus.CONSUMED.isValid()).isFalse();
    }

    @Test
    void revokedStatusIsNotValid() {
        assertThat(TokenStatus.REVOKED.isValid()).isFalse();
    }

    @Test
    void unavailableStatusIsNotValid() {
        assertThat(TokenStatus.UNAVAILABLE.isValid()).isFalse();
    }

    @Test
    void descriptionsAreDefined() {
        assertThat(TokenStatus.VALID.description()).isNotBlank();
        assertThat(TokenStatus.EXPIRED.description()).isNotBlank();
        assertThat(TokenStatus.CONSUMED.description()).isNotBlank();
        assertThat(TokenStatus.REVOKED.description()).isNotBlank();
        assertThat(TokenStatus.UNAVAILABLE.description()).isNotBlank();
    }
}
