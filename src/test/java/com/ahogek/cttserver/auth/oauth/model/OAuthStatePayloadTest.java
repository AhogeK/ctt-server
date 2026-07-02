package com.ahogek.cttserver.auth.oauth.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

class OAuthStatePayloadTest {

    private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

    @Test
    @DisplayName("should throw IllegalArgumentException when action=BIND and currentUserId is null")
    void shouldThrowIllegalArgument_whenBindActionWithoutUserId() {
        assertThatThrownBy(
                        () -> new OAuthStatePayload(OAuthStatePayload.Action.BIND, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BIND action requires currentUserId");
    }

    @Test
    @DisplayName("should throw IllegalArgumentException when action=BIND and currentUserId is null"
            + " regardless of redirectUrl")
    void shouldThrowIllegalArgument_whenBindActionWithoutUserId_evenWithRedirectUrl() {
        assertThatThrownBy(
                        () ->
                                new OAuthStatePayload(
                                        OAuthStatePayload.Action.BIND, null, "/settings/profile", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("should accept payload when action=LOGIN and currentUserId is null")
    void shouldAccept_whenLoginActionWithNullUserId() {
        OAuthStatePayload payload =
                new OAuthStatePayload(OAuthStatePayload.Action.LOGIN, null, null, null);

        assertThat(payload.action()).isEqualTo(OAuthStatePayload.Action.LOGIN);
        assertThat(payload.currentUserId()).isNull();
        assertThat(payload.redirectUrl()).isNull();
    }

    @Test
    @DisplayName("should accept payload when action=LOGIN and redirectUrl is set")
    void shouldAccept_whenLoginActionWithRedirectUrl() {
        OAuthStatePayload payload =
                new OAuthStatePayload(OAuthStatePayload.Action.LOGIN, null, "/oauth/callback", null);

        assertThat(payload.action()).isEqualTo(OAuthStatePayload.Action.LOGIN);
        assertThat(payload.currentUserId()).isNull();
        assertThat(payload.redirectUrl()).isEqualTo("/oauth/callback");
    }

    @Test
    @DisplayName("should accept payload when action=BIND and currentUserId is provided")
    void shouldAccept_whenBindActionWithUserId() {
        OAuthStatePayload payload =
                new OAuthStatePayload(
                        OAuthStatePayload.Action.BIND, USER_ID, "/settings/profile", null);

        assertThat(payload.action()).isEqualTo(OAuthStatePayload.Action.BIND);
        assertThat(payload.currentUserId()).isEqualTo(USER_ID);
        assertThat(payload.redirectUrl()).isEqualTo("/settings/profile");
    }

    @Test
    @DisplayName("should accept payload when action=BIND with currentUserId and null redirectUrl")
    void shouldAccept_whenBindActionWithUserIdAndNullRedirectUrl() {
        OAuthStatePayload payload =
                new OAuthStatePayload(OAuthStatePayload.Action.BIND, USER_ID, null, null);

        assertThat(payload.action()).isEqualTo(OAuthStatePayload.Action.BIND);
        assertThat(payload.currentUserId()).isEqualTo(USER_ID);
        assertThat(payload.redirectUrl()).isNull();
    }

    @Test
    @DisplayName("should accept BIND payload when redirectUrl is blank (BIND callers may rely on"
            + " controller default)")
    void shouldAccept_whenRedirectUrlIsBlank() {
        OAuthStatePayload blankRedirect =
                new OAuthStatePayload(OAuthStatePayload.Action.BIND, USER_ID, "", null);

        assertThat(blankRedirect.redirectUrl()).isEmpty();
        assertThat(blankRedirect.currentUserId()).isEqualTo(USER_ID);
    }

    @Test
    @DisplayName("Should throw when LOGIN action is supplied with non-null currentUserId")
    void shouldThrowIllegalArgument_whenLoginActionWithNonNullUserId() {
        assertThatThrownBy(
                        () -> new OAuthStatePayload(OAuthStatePayload.Action.LOGIN, USER_ID, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("LOGIN action must not have currentUserId");
    }

    @Test
    @DisplayName("should accept payload with clientIp set")
    void shouldAccept_whenClientIpProvided() {
        OAuthStatePayload payload =
                new OAuthStatePayload(OAuthStatePayload.Action.BIND, USER_ID, "/settings/profile", "192.168.1.1");

        assertThat(payload.action()).isEqualTo(OAuthStatePayload.Action.BIND);
        assertThat(payload.currentUserId()).isEqualTo(USER_ID);
        assertThat(payload.redirectUrl()).isEqualTo("/settings/profile");
        assertThat(payload.clientIp()).isEqualTo("192.168.1.1");
    }

    @Test
    @DisplayName("should accept payload with null clientIp")
    void shouldAccept_whenClientIpNull() {
        OAuthStatePayload payload =
                new OAuthStatePayload(OAuthStatePayload.Action.LOGIN, null, null, null);

        assertThat(payload.action()).isEqualTo(OAuthStatePayload.Action.LOGIN);
        assertThat(payload.clientIp()).isNull();
    }
}
