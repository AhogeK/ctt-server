package com.ahogek.cttserver.auth.service;

import com.ahogek.cttserver.auth.repository.RefreshTokenRepository;
import com.ahogek.cttserver.common.config.properties.TwoFactorProperties;
import com.ahogek.cttserver.common.exception.ErrorCode;
import com.ahogek.cttserver.common.exception.ForbiddenException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Decides whether a session was authenticated recently enough for sensitive security changes.
 *
 * <p>This deliberately uses the session's first issuance time, not the latest refresh token
 * timestamp: rotating a token does not make an old login fresh.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-11-03
 */
@Service
public class RecentAuthenticationService {

    private final RefreshTokenRepository refreshTokens;
    private final TwoFactorProperties properties;
    private final Clock clock;

    @Autowired
    public RecentAuthenticationService(
            RefreshTokenRepository refreshTokens, TwoFactorProperties properties) {
        this(refreshTokens, properties, Clock.systemUTC());
    }

    RecentAuthenticationService(
            RefreshTokenRepository refreshTokens, TwoFactorProperties properties, Clock clock) {
        this.refreshTokens = refreshTokens;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Returns whether the session was created inside the configured recent-authentication window.
     *
     * @param userId the account
     * @param sessionId the login session from the access token
     * @return true when the session is recent
     */
    public boolean isRecent(UUID userId, UUID sessionId) {
        if (userId == null || sessionId == null) {
            return false;
        }
        Instant createdAt = refreshTokens.findSessionCreatedAt(userId, sessionId);
        return createdAt != null
                && !createdAt.isBefore(
                        clock.instant().minus(properties.recentAuthenticationWindow()));
    }

    /**
     * Requires a recent session.
     *
     * @param userId the account
     * @param sessionId the login session
     * @throws ForbiddenException with {@code AUTH_031} when the session is missing or too old
     */
    public void requireRecent(UUID userId, UUID sessionId) {
        if (!isRecent(userId, sessionId)) {
            throw new ForbiddenException(ErrorCode.AUTH_031);
        }
    }
}
