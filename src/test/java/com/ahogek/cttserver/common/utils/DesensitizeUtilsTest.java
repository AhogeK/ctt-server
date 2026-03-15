package com.ahogek.cttserver.common.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link DesensitizeUtils}.
 *
 * @author AhogeK
 * @since 2026-03-16
 */
@DisplayName("DesensitizeUtils - Sensitive Data Masking")
class DesensitizeUtilsTest {

    @ParameterizedTest
    @DisplayName("Should mask sensitive headers")
    @CsvSource({
        "Authorization, Bearer abc123, ******(REDACTED)",
        "authorization, Basic dXNlcjpwYXNz, ******(REDACTED)",
        "Cookie, sessionId=xyz789, ******(REDACTED)",
        "cookie, token=secret, ******(REDACTED)",
        "Set-Cookie, auth=token123, ******(REDACTED)",
        "X-API-Key, secret-key-123, ******(REDACTED)",
        "x-api-key, my-api-key, ******(REDACTED)",
        "Token, bearer-token, ******(REDACTED)",
        "X-Auth-Token, session-token, ******(REDACTED)"
    })
    void shouldMaskSensitiveHeaders(String headerName, String value, String expected) {
        assertThat(DesensitizeUtils.maskHeader(headerName, value)).isEqualTo(expected);
    }

    @ParameterizedTest
    @DisplayName("Should not mask non-sensitive headers")
    @CsvSource({
        "Content-Type, application/json",
        "Accept, */*",
        "User-Agent, Mozilla/5.0",
        "X-Request-ID, abc123"
    })
    void shouldNotMaskNonSensitiveHeaders(String headerName, String value) {
        assertThat(DesensitizeUtils.maskHeader(headerName, value)).isEqualTo(value);
    }

    @ParameterizedTest
    @DisplayName("Should mask email addresses")
    @CsvSource({
        "user@example.com, us***@example.com",
        "john.doe@gmail.com, jo***@gmail.com",
        "a@b.com, ***@b.com",
        "ab@domain.org, ***@domain.org"
    })
    void shouldMaskEmailAddresses(String email, String expected) {
        assertThat(DesensitizeUtils.maskEmail(email)).isEqualTo(expected);
    }

    @Test
    @DisplayName("Should return null or original for invalid emails")
    void shouldHandleInvalidEmails() {
        assertThat(DesensitizeUtils.maskEmail(null)).isNull();
        assertThat(DesensitizeUtils.maskEmail("")).isEmpty();
        assertThat(DesensitizeUtils.maskEmail("not-an-email")).isEqualTo("not-an-email");
    }

    @Test
    @DisplayName("Should mask passwords")
    void shouldMaskPasswords() {
        assertThat(DesensitizeUtils.maskPassword("secret123")).isEqualTo("******");
        assertThat(DesensitizeUtils.maskPassword("my$uper$ecureP@ssw0rd!")).isEqualTo("******");
    }

    @ParameterizedTest
    @DisplayName("Should mask tokens with partial visibility")
    @CsvSource({
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9, 20, eyJh...VCJ9",
        "short, 20, ******",
        "exactlytwentychars!!, 21, ******"
    })
    void shouldMaskTokens(String token, int maskLength, String expected) {
        assertThat(DesensitizeUtils.maskToken(token, maskLength)).isEqualTo(expected);
    }

    @Test
    @DisplayName("Should mask strings partially")
    void shouldMaskPartialStrings() {
        // Credit card: show first 4 and last 4
        assertThat(DesensitizeUtils.maskPartial("4532123456789012", 4, 4)).isEqualTo("4532***9012");

        // Phone number: show first 3 and last 4
        assertThat(DesensitizeUtils.maskPartial("13812345678", 3, 4)).isEqualTo("138***5678");

        // Too short: full mask
        assertThat(DesensitizeUtils.maskPartial("short", 3, 3)).isEqualTo("******");

        assertThat(DesensitizeUtils.maskPartial(null, 3, 4)).isNull();
    }

    @ParameterizedTest
    @DisplayName("Should handle null or blank header values")
    @ValueSource(strings = {"", "   "})
    void shouldHandleBlankHeaderValues(String value) {
        assertThat(DesensitizeUtils.maskHeader("Authorization", value)).isEqualTo(value);
    }
}
