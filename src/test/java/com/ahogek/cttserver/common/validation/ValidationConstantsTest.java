package com.ahogek.cttserver.common.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class ValidationConstantsTest {

    @Test
    void password_min_length_is_8() {
        assertThat(ValidationConstants.PASSWORD_MIN_LENGTH).isEqualTo(8);
    }

    @Test
    void password_max_length_is_64() {
        assertThat(ValidationConstants.PASSWORD_MAX_LENGTH).isEqualTo(64);
    }

    @Test
    void password_min_length_is_less_than_max_length() {
        assertThat(ValidationConstants.PASSWORD_MIN_LENGTH)
                .isLessThan(ValidationConstants.PASSWORD_MAX_LENGTH);
    }

    @ParameterizedTest
    @CsvSource({
        "550e8400-e29b-41d4-a716-446655440000, valid UUID v4",
        "12345678-1234-4123-8234-123456789abc, valid UUID v4 lowercase"
    })
    void regex_uuid_v4_matches_valid_uuids(String uuid, String description) {
        assertThat(uuid)
                .withFailMessage("UUID should match regex: " + description)
                .matches(ValidationConstants.REGEX_UUID_V4);
    }

    @ParameterizedTest
    @CsvSource({
        "550e8400-e29b-11d4-a716-446655440000, wrong version (not 4)",
        "550e8400-e29b-41d4-0716-446655440000, wrong variant (not 8/9/a/b)",
        "550e8400-e29b-41d4-a716, too short"
    })
    void regex_uuid_v4_rejects_invalid_uuids(String uuid, String reason) {
        assertThat(uuid)
                .withFailMessage("UUID should NOT match regex: " + reason)
                .doesNotMatch(ValidationConstants.REGEX_UUID_V4);
    }

    @ParameterizedTest
    @CsvSource({
        "john_doe, underscore",
        "John-Doe, hyphen",
        "User123, digits",
        "AB, minimum 2 chars"
    })
    void regex_display_name_matches_valid_names(String displayName, String description) {
        assertThat(displayName)
                .withFailMessage("Display name should match regex: " + description)
                .matches(ValidationConstants.REGEX_DISPLAY_NAME);
    }

    @ParameterizedTest
    @CsvSource({"john@doe, contains at sign", "john.doe, contains dot"})
    void regex_display_name_rejects_invalid_names(String displayName, String reason) {
        assertThat(displayName)
                .withFailMessage("Display name should NOT match regex: " + reason)
                .doesNotMatch(ValidationConstants.REGEX_DISPLAY_NAME);
    }

    @Test
    void regex_display_name_rejects_too_long_name() {
        String tooLongName = "a".repeat(51);

        assertThat(tooLongName)
                .withFailMessage("Display name exceeding 50 chars should be rejected")
                .doesNotMatch(ValidationConstants.REGEX_DISPLAY_NAME);
    }

    @Test
    void regex_display_name_rejects_empty_string() {
        String emptyName = "";

        assertThat(emptyName)
                .withFailMessage("Empty display name should be rejected")
                .doesNotMatch(ValidationConstants.REGEX_DISPLAY_NAME);
    }

    @Test
    void error_messages_are_defined() {
        assertThat(ValidationConstants.MSG_EMAIL_INVALID).isNotBlank();
        assertThat(ValidationConstants.MSG_PASSWORD_WEAK).isNotBlank();
        assertThat(ValidationConstants.MSG_UUID_INVALID).isNotBlank();
        assertThat(ValidationConstants.MSG_NAME_INVALID).isNotBlank();
        assertThat(ValidationConstants.MSG_NOT_BLANK).isNotBlank();
    }
}
