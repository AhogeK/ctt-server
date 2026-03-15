package com.ahogek.cttserver.common.config.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link MaskingMessageConverter}.
 *
 * @author AhogeK
 * @since 2026-03-16
 */
@DisplayName("MaskingMessageConverter - Global Log Desensitization")
class MaskingMessageConverterTest {

    private MaskingMessageConverter converter;
    private LoggerContext loggerContext;

    @BeforeEach
    void setUp() {
        converter = new MaskingMessageConverter();
        loggerContext = new LoggerContext();
    }

    @Test
    @DisplayName("Should mask password in log messages")
    void shouldMaskPassword() {
        assertThat(converter.convert(createEvent("User login with password=secret123")))
            .isEqualTo("User login with password=******");
        assertThat(converter.convert(createEvent("password: mypass")))
            .isEqualTo("password: ******");
    }

    @Test
    @DisplayName("Should mask token in log messages")
    void shouldMaskToken() {
        assertThat(converter.convert(createEvent("API call with token=abc123xyz")))
            .isEqualTo("API call with token=******");
        assertThat(converter.convert(createEvent("Using token xyz789 for auth")))
            .isEqualTo("Using token ****** for auth");
    }

    @Test
    @DisplayName("Should mask authorization in log messages")
    void shouldMaskAuthorization() {
        // Standard key-value format
        assertThat(converter.convert(createEvent("authorization: Bearer eyJhbGciOiJIUzI1NiIs")))
            .isEqualTo("authorization: ******");
        // Bearer format (special handling for authorization header)
        assertThat(converter.convert(createEvent("Authorization bearer token123")))
            .isEqualTo("Authorization ******");
    }

    @Test
    @DisplayName("Should mask cookie in log messages")
    void shouldMaskCookie() {
        assertThat(converter.convert(createEvent("Request cookie: sessionId=abc123")))
            .isEqualTo("Request cookie: ******");
    }

    @Test
    @DisplayName("Should mask secret in log messages")
    void shouldMaskSecret() {
        assertThat(converter.convert(createEvent("API secret=mysecretkey")))
            .isEqualTo("API secret=******");
    }

    @Test
    @DisplayName("Should mask multiple sensitive values in same message")
    void shouldMaskMultipleSensitiveValues() {
        String input = "Login failed: password=wrong, token=expired123";
        String expected = "Login failed: password=******, token=******";

        assertThat(converter.convert(createEvent(input))).isEqualTo(expected);
    }

    @Test
    @DisplayName("Should preserve non-sensitive messages")
    void shouldPreserveNonSensitiveMessages() {
        String message = "User john.doe logged in successfully from IP 192.168.1.1";

        assertThat(converter.convert(createEvent(message))).isEqualTo(message);
    }

    @Test
    @DisplayName("Should handle null or empty messages")
    void shouldHandleNullOrEmptyMessages() {
        assertThat(converter.convert(createEvent(null))).isNull();
        assertThat(converter.convert(createEvent(""))).isEmpty();
    }

    @Test
    @DisplayName("Should be case insensitive")
    void shouldBeCaseInsensitive() {
        assertThat(converter.convert(createEvent("PASSWORD=secret"))).isEqualTo("PASSWORD=******");
        assertThat(converter.convert(createEvent("password=secret"))).isEqualTo("password=******");
        assertThat(converter.convert(createEvent("Password=secret"))).isEqualTo("Password=******");
    }

    private LoggingEvent createEvent(String message) {
        LoggingEvent event = new LoggingEvent();
        event.setLoggerContext(loggerContext);
        event.setLoggerName(Logger.ROOT_LOGGER_NAME);
        event.setLevel(Level.INFO);
        event.setMessage(message);
        return event;
    }
}
