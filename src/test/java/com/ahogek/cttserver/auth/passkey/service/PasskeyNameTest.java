package com.ahogek.cttserver.auth.passkey.service;

import com.ahogek.cttserver.common.exception.ErrorCode;
import com.ahogek.cttserver.common.exception.ValidationException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Passkey name normalization")
class PasskeyNameTest {

    @Test
    @DisplayName("Should default and trim registration labels")
    void shouldNormalizeRegistrationLabel() {
        assertThat(PasskeyName.forRegistration(null)).isEqualTo("Passkey");
        assertThat(PasskeyName.forRegistration("  ")).isEqualTo("Passkey");
        assertThat(PasskeyName.forRegistration("  Work laptop  ")).isEqualTo("Work laptop");
    }

    @Test
    @DisplayName("Should reject a blank rename label")
    void shouldRejectBlankRename() {
        assertThatThrownBy(() -> PasskeyName.forRename(" "))
                .isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.COMMON_003);
    }

    @Test
    @DisplayName("Should reject a label longer than the column")
    void shouldRejectOverlongLabel() {
        assertThatThrownBy(() -> PasskeyName.forRegistration("x".repeat(65)))
                .isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.COMMON_003);
    }
}
