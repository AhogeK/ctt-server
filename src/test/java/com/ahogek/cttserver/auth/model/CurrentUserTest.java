package com.ahogek.cttserver.auth.model;

import com.ahogek.cttserver.user.enums.UserStatus;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CurrentUserTest {

    @Test
    void isActive_whenStatusIsActive_returnsTrue() {
        CurrentUser user =
                new CurrentUser(
                        UUID.randomUUID(),
                        "test@example.com",
                        UserStatus.ACTIVE,
                        Set.of("USER"),
                        CurrentUser.AuthenticationType.WEB_SESSION);

        assertTrue(user.isActive());
    }

    @Test
    void isActive_whenStatusIsNotActive_returnsFalse() {
        CurrentUser user =
                new CurrentUser(
                        UUID.randomUUID(),
                        "test@example.com",
                        UserStatus.PENDING_VERIFICATION,
                        Set.of("USER"),
                        CurrentUser.AuthenticationType.WEB_SESSION);

        assertFalse(user.isActive());
    }

    @Test
    void isActive_whenStatusIsLocked_returnsFalse() {
        CurrentUser user =
                new CurrentUser(
                        UUID.randomUUID(),
                        "test@example.com",
                        UserStatus.LOCKED,
                        Set.of("USER"),
                        CurrentUser.AuthenticationType.WEB_SESSION);

        assertFalse(user.isActive());
    }

    @Test
    void isActive_whenStatusIsSuspended_returnsFalse() {
        CurrentUser user =
                new CurrentUser(
                        UUID.randomUUID(),
                        "test@example.com",
                        UserStatus.SUSPENDED,
                        Set.of("USER"),
                        CurrentUser.AuthenticationType.WEB_SESSION);

        assertFalse(user.isActive());
    }

    @Test
    void isActive_whenStatusIsDeleted_returnsFalse() {
        CurrentUser user =
                new CurrentUser(
                        UUID.randomUUID(),
                        "test@example.com",
                        UserStatus.DELETED,
                        Set.of("USER"),
                        CurrentUser.AuthenticationType.WEB_SESSION);

        assertFalse(user.isActive());
    }

    @Test
    void authenticationType_webSession() {
        CurrentUser user =
                new CurrentUser(
                        UUID.randomUUID(),
                        "test@example.com",
                        UserStatus.ACTIVE,
                        Set.of("USER"),
                        CurrentUser.AuthenticationType.WEB_SESSION);

        assertEquals(CurrentUser.AuthenticationType.WEB_SESSION, user.authType());
    }

    @Test
    void authenticationType_apiKey() {
        CurrentUser user =
                new CurrentUser(
                        UUID.randomUUID(),
                        "test@example.com",
                        UserStatus.ACTIVE,
                        Set.of("USER"),
                        CurrentUser.AuthenticationType.API_KEY);

        assertEquals(CurrentUser.AuthenticationType.API_KEY, user.authType());
    }

    @Test
    void authenticationType_oauth2() {
        CurrentUser user =
                new CurrentUser(
                        UUID.randomUUID(),
                        "test@example.com",
                        UserStatus.ACTIVE,
                        Set.of("USER"),
                        CurrentUser.AuthenticationType.OAUTH2);

        assertEquals(CurrentUser.AuthenticationType.OAUTH2, user.authType());
    }

    @Test
    void authorities_singleRole() {
        CurrentUser user =
                new CurrentUser(
                        UUID.randomUUID(),
                        "test@example.com",
                        UserStatus.ACTIVE,
                        Set.of("ADMIN"),
                        CurrentUser.AuthenticationType.WEB_SESSION);

        assertEquals(1, user.authorities().size());
        assertTrue(user.authorities().contains("ADMIN"));
    }

    @Test
    void authorities_multipleRoles() {
        CurrentUser user =
                new CurrentUser(
                        UUID.randomUUID(),
                        "test@example.com",
                        UserStatus.ACTIVE,
                        Set.of("USER", "ADMIN", "MODERATOR"),
                        CurrentUser.AuthenticationType.WEB_SESSION);

        assertEquals(3, user.authorities().size());
        assertTrue(user.authorities().contains("USER"));
        assertTrue(user.authorities().contains("ADMIN"));
        assertTrue(user.authorities().contains("MODERATOR"));
    }
}
