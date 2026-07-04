package com.ahogek.cttserver.auth.util;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/**
 * Tests for {@link CookieHelper}.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-07-04
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CookieHelper - Authentication Cookie Builder")
class CookieHelperTest {

    private static final String REFRESH_TOKEN_PATH = "/api/v1/auth/refresh";

    @Mock private HttpServletResponse response;

    @Test
    @DisplayName("Access token cookie should expose Lax SameSite and short maxAge")
    void shouldCreateAccessTokenCookieWithCorrectAttributes() {
        Cookie cookie = CookieHelper.createAccessTokenCookie("access-123");

        assertThat(cookie.getName()).isEqualTo(CookieHelper.ACCESS_TOKEN_COOKIE);
        assertThat(cookie.getValue()).isEqualTo("access-123");
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getSecure()).isTrue();
        assertThat(cookie.getPath()).isEqualTo("/");
        assertThat(cookie.getMaxAge()).isEqualTo(900);
        assertThat(cookie.getAttribute("SameSite")).isEqualTo("Lax");
    }

    @Test
    @DisplayName("Refresh token cookie should expose Strict SameSite and 14-day maxAge")
    void shouldCreateRefreshTokenCookieWithCorrectAttributes() {
        Cookie cookie = CookieHelper.createRefreshTokenCookie("refresh-456", REFRESH_TOKEN_PATH);

        assertThat(cookie.getName()).isEqualTo(CookieHelper.REFRESH_TOKEN_COOKIE);
        assertThat(cookie.getValue()).isEqualTo("refresh-456");
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getSecure()).isTrue();
        assertThat(cookie.getPath()).isEqualTo(REFRESH_TOKEN_PATH);
        assertThat(cookie.getMaxAge()).isEqualTo(14 * 24 * 60 * 60);
        assertThat(cookie.getAttribute("SameSite")).isEqualTo("Strict");
    }

    @Test
    @DisplayName("Cleared access token cookie should expire immediately")
    void shouldClearAccessTokenCookieWithMaxAgeZero() {
        Cookie cookie = CookieHelper.clearAccessTokenCookie();

        assertThat(cookie.getName()).isEqualTo(CookieHelper.ACCESS_TOKEN_COOKIE);
        assertThat(cookie.getValue()).isEmpty();
        assertThat(cookie.getPath()).isEqualTo("/");
        assertThat(cookie.getMaxAge()).isZero();
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getSecure()).isTrue();
        assertThat(cookie.getAttribute("SameSite")).isEqualTo("Lax");
    }

    @Test
    @DisplayName("Cleared refresh token cookie should expire immediately")
    void shouldClearRefreshTokenCookieWithMaxAgeZero() {
        Cookie cookie = CookieHelper.clearRefreshTokenCookie(REFRESH_TOKEN_PATH);

        assertThat(cookie.getName()).isEqualTo(CookieHelper.REFRESH_TOKEN_COOKIE);
        assertThat(cookie.getValue()).isEmpty();
        assertThat(cookie.getPath()).isEqualTo(REFRESH_TOKEN_PATH);
        assertThat(cookie.getMaxAge()).isZero();
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getSecure()).isTrue();
        assertThat(cookie.getAttribute("SameSite")).isEqualTo("Strict");
    }

    @Test
    @DisplayName("Should add both access and refresh cookies to response")
    void shouldAddBothCookiesToResponse() {
        CookieHelper.addCookiesToResponse(
                response, "access-123", "refresh-456", REFRESH_TOKEN_PATH);

        ArgumentCaptor<Cookie> captor = ArgumentCaptor.forClass(Cookie.class);
        verify(response, org.mockito.Mockito.times(2)).addCookie(captor.capture());

        assertThat(captor.getAllValues())
                .extracting(Cookie::getName)
                .containsExactly(
                        CookieHelper.ACCESS_TOKEN_COOKIE, CookieHelper.REFRESH_TOKEN_COOKIE);
        assertThat(captor.getAllValues())
                .extracting(Cookie::getValue)
                .containsExactly("access-123", "refresh-456");
    }

    @Test
    @DisplayName("Should clear both access and refresh cookies from response")
    void shouldClearBothCookiesFromResponse() {
        CookieHelper.clearCookiesFromResponse(response, REFRESH_TOKEN_PATH);

        ArgumentCaptor<Cookie> captor = ArgumentCaptor.forClass(Cookie.class);
        verify(response, org.mockito.Mockito.times(2)).addCookie(captor.capture());

        assertThat(captor.getAllValues())
                .extracting(Cookie::getName, Cookie::getMaxAge)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(CookieHelper.ACCESS_TOKEN_COOKIE, 0),
                        org.assertj.core.groups.Tuple.tuple(CookieHelper.REFRESH_TOKEN_COOKIE, 0));
    }
}
