package com.ahogek.cttserver.common.config.properties;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Passkey properties")
class PasskeyPropertiesTest {

    @Test
    @DisplayName("Should accept a host-only RP ID and exact http origins")
    void shouldAcceptValidConfiguration() {
        assertThatCode(
                        () ->
                                new PasskeyProperties(
                                        "CTT",
                                        "ctt.example.com",
                                        List.of("https://ctt.example.com"),
                                        Duration.ofMinutes(5),
                                        Duration.ofMinutes(5),
                                        Duration.ZERO))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should reject an RP ID containing a scheme, port or path")
    void shouldRejectInvalidRpId() {
        assertThatThrownBy(
                        () ->
                                new PasskeyProperties(
                                        "CTT",
                                        "https://ctt.example.com:8443/login",
                                        List.of("https://ctt.example.com"),
                                        Duration.ofMinutes(5),
                                        Duration.ofMinutes(5),
                                        Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should reject an origin with a path")
    void shouldRejectOriginWithPath() {
        assertThatThrownBy(
                        () ->
                                new PasskeyProperties(
                                        "CTT",
                                        "ctt.example.com",
                                        List.of("https://ctt.example.com/login"),
                                        Duration.ofMinutes(5),
                                        Duration.ofMinutes(5),
                                        Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
