package com.ahogek.cttserver.auth.util;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Utility for building authentication-related cookies (access/refresh tokens) with secure defaults
 * (HttpOnly, Secure, SameSite) and for attaching them to an HTTP response.
 *
 * <p>The refresh token cookie's {@code Path} is supplied by callers (typically sourced from {@code
 * SecurityProperties.cookie().refreshTokenPath()}) so it can be configured per environment.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-07-04
 */
public final class CookieHelper {

    public static final String ACCESS_TOKEN_COOKIE = "ctt_access_token";
    public static final String REFRESH_TOKEN_COOKIE = "ctt_refresh_token";

    public static final int ACCESS_TOKEN_MAX_AGE = 900;
    public static final int REFRESH_TOKEN_MAX_AGE = 14 * 24 * 60 * 60;

    public static final String ACCESS_TOKEN_PATH = "/";

    private CookieHelper() {}

    public static Cookie createAccessTokenCookie(String token) {
        return buildCookie(
                ACCESS_TOKEN_COOKIE, token, ACCESS_TOKEN_PATH, ACCESS_TOKEN_MAX_AGE, "Lax");
    }

    public static Cookie createRefreshTokenCookie(String token, String refreshTokenPath) {
        return buildCookie(
                REFRESH_TOKEN_COOKIE, token, refreshTokenPath, REFRESH_TOKEN_MAX_AGE, "Strict");
    }

    public static Cookie clearAccessTokenCookie() {
        return buildClearedCookie(ACCESS_TOKEN_COOKIE, ACCESS_TOKEN_PATH, "Lax");
    }

    public static Cookie clearRefreshTokenCookie(String refreshTokenPath) {
        return buildClearedCookie(REFRESH_TOKEN_COOKIE, refreshTokenPath, "Strict");
    }

    public static void addCookiesToResponse(
            HttpServletResponse response,
            String accessToken,
            String refreshToken,
            String refreshTokenPath) {
        response.addCookie(createAccessTokenCookie(accessToken));
        response.addCookie(createRefreshTokenCookie(refreshToken, refreshTokenPath));
    }

    public static void clearCookiesFromResponse(
            HttpServletResponse response, String refreshTokenPath) {
        response.addCookie(clearAccessTokenCookie());
        response.addCookie(clearRefreshTokenCookie(refreshTokenPath));
    }

    private static Cookie buildCookie(
            String name, String value, String path, int maxAge, String sameSite) {
        Cookie cookie = new Cookie(name, value);
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath(path);
        cookie.setMaxAge(maxAge);
        cookie.setAttribute("SameSite", sameSite);
        return cookie;
    }

    private static Cookie buildClearedCookie(String name, String path, String sameSite) {
        Cookie cookie = new Cookie(name, "");
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath(path);
        cookie.setMaxAge(0);
        cookie.setAttribute("SameSite", sameSite);
        return cookie;
    }
}
