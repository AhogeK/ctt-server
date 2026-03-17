package com.ahogek.cttserver.auth;

import com.ahogek.cttserver.auth.model.CurrentUser;
import com.ahogek.cttserver.common.exception.ErrorCode;
import com.ahogek.cttserver.common.exception.ForbiddenException;
import com.ahogek.cttserver.common.exception.UnauthorizedException;
import com.ahogek.cttserver.user.enums.UserStatus;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SpringSecurityCurrentUserProviderTest {

    private SpringSecurityCurrentUserProvider provider;

    @BeforeEach
    void setUp() {
        provider = new SpringSecurityCurrentUserProvider();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getCurrentUser_whenNoAuthentication_returnsEmpty() {
        assertTrue(provider.getCurrentUser().isEmpty());
    }

    @Test
    void getCurrentUser_whenNotAuthenticated_returnsEmpty() {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(null, null));

        assertTrue(provider.getCurrentUser().isEmpty());
    }

    @Test
    void getCurrentUser_whenPrincipalIsNotCurrentUser_returnsEmpty() {
        User springUser =
                new User("username", "password", Set.of(new SimpleGrantedAuthority("ROLE_USER")));
        Authentication auth =
                new UsernamePasswordAuthenticationToken(
                        springUser, null, springUser.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);

        assertTrue(provider.getCurrentUser().isEmpty());
    }

    @Test
    void getCurrentUser_whenPrincipalIsCurrentUser_returnsUser() {
        UUID userId = UUID.randomUUID();
        CurrentUser currentUser =
                new CurrentUser(
                        userId,
                        "test@example.com",
                        UserStatus.ACTIVE,
                        Set.of("USER"),
                        CurrentUser.AuthenticationType.WEB_SESSION);
        Authentication auth =
                new UsernamePasswordAuthenticationToken(
                        currentUser, null, Set.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        var result = provider.getCurrentUser();

        assertTrue(result.isPresent());
        assertEquals(userId, result.get().id());
        assertEquals("test@example.com", result.get().email());
    }

    @Test
    void getCurrentUserRequired_whenNoAuthentication_throwsUnauthorized() {
        assertThrows(UnauthorizedException.class, () -> provider.getCurrentUserRequired());
    }

    @Test
    void getCurrentUserRequired_whenNotAuthenticated_throwsUnauthorized() {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(null, null));

        assertThrows(UnauthorizedException.class, () -> provider.getCurrentUserRequired());
    }

    @Test
    void getCurrentUserRequired_whenUserExists_returnsUser() {
        UUID userId = UUID.randomUUID();
        CurrentUser currentUser =
                new CurrentUser(
                        userId,
                        "test@example.com",
                        UserStatus.ACTIVE,
                        Set.of("USER"),
                        CurrentUser.AuthenticationType.WEB_SESSION);
        Authentication auth =
                new UsernamePasswordAuthenticationToken(
                        currentUser, null, Set.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        CurrentUser result = provider.getCurrentUserRequired();

        assertEquals(userId, result.id());
        assertEquals("test@example.com", result.email());
    }

    @Test
    void getActiveUserRequired_whenUserIsActive_returnsUser() {
        UUID userId = UUID.randomUUID();
        CurrentUser currentUser =
                new CurrentUser(
                        userId,
                        "test@example.com",
                        UserStatus.ACTIVE,
                        Set.of("USER"),
                        CurrentUser.AuthenticationType.WEB_SESSION);
        Authentication auth =
                new UsernamePasswordAuthenticationToken(
                        currentUser, null, Set.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        CurrentUser result = provider.getActiveUserRequired();

        assertEquals(userId, result.id());
    }

    @Test
    void getActiveUserRequired_whenUserPending_throwsForbidden() {
        CurrentUser currentUser =
                new CurrentUser(
                        UUID.randomUUID(),
                        "test@example.com",
                        UserStatus.PENDING_VERIFICATION,
                        Set.of("USER"),
                        CurrentUser.AuthenticationType.WEB_SESSION);
        Authentication auth =
                new UsernamePasswordAuthenticationToken(
                        currentUser, null, Set.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        ForbiddenException ex =
                assertThrows(ForbiddenException.class, () -> provider.getActiveUserRequired());

        assertEquals(ErrorCode.AUTH_006, ex.errorCode());
    }

    @Test
    void getActiveUserRequired_whenUserLocked_throwsForbidden() {
        CurrentUser currentUser =
                new CurrentUser(
                        UUID.randomUUID(),
                        "test@example.com",
                        UserStatus.LOCKED,
                        Set.of("USER"),
                        CurrentUser.AuthenticationType.WEB_SESSION);
        Authentication auth =
                new UsernamePasswordAuthenticationToken(
                        currentUser, null, Set.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        ForbiddenException ex =
                assertThrows(ForbiddenException.class, () -> provider.getActiveUserRequired());

        assertEquals(ErrorCode.AUTH_004, ex.errorCode());
    }

    @Test
    void getActiveUserRequired_whenUserSuspended_throwsForbidden() {
        CurrentUser currentUser =
                new CurrentUser(
                        UUID.randomUUID(),
                        "test@example.com",
                        UserStatus.SUSPENDED,
                        Set.of("USER"),
                        CurrentUser.AuthenticationType.WEB_SESSION);
        Authentication auth =
                new UsernamePasswordAuthenticationToken(
                        currentUser, null, Set.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        ForbiddenException ex =
                assertThrows(ForbiddenException.class, () -> provider.getActiveUserRequired());

        assertEquals(ErrorCode.AUTH_005, ex.errorCode());
    }

    @Test
    void getActiveUserRequired_whenUserDeleted_throwsForbidden() {
        CurrentUser currentUser =
                new CurrentUser(
                        UUID.randomUUID(),
                        "test@example.com",
                        UserStatus.DELETED,
                        Set.of("USER"),
                        CurrentUser.AuthenticationType.WEB_SESSION);
        Authentication auth =
                new UsernamePasswordAuthenticationToken(
                        currentUser, null, Set.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        ForbiddenException ex =
                assertThrows(ForbiddenException.class, () -> provider.getActiveUserRequired());

        assertEquals(ErrorCode.AUTH_009, ex.errorCode());
    }
}
