package com.ahogek.cttserver.auth.service;

import com.ahogek.cttserver.audit.enums.AuditAction;
import com.ahogek.cttserver.audit.enums.ResourceType;
import com.ahogek.cttserver.audit.enums.SecuritySeverity;
import com.ahogek.cttserver.audit.model.AuditDetails;
import com.ahogek.cttserver.audit.service.AuditLogService;
import com.ahogek.cttserver.auth.entity.RefreshToken;
import com.ahogek.cttserver.auth.enums.TokenStatus;
import com.ahogek.cttserver.auth.repository.RefreshTokenRepository;
import com.ahogek.cttserver.common.config.properties.SecurityProperties;
import com.ahogek.cttserver.common.config.properties.TermsProperties;
import com.ahogek.cttserver.common.exception.ErrorCode;
import com.ahogek.cttserver.common.exception.ForbiddenException;
import com.ahogek.cttserver.common.exception.UnauthorizedException;
import com.ahogek.cttserver.common.utils.TokenUtils;
import com.ahogek.cttserver.common.utils.TokenUtils.RefreshTokenPair;
import com.ahogek.cttserver.user.entity.User;
import com.ahogek.cttserver.user.repository.UserRepository;

import jakarta.persistence.EntityManager;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/** Token refresh service with rotation and reuse detection. */
@Service
public class TokenRefreshService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuditLogService auditLogService;
    private final SecurityProperties.JwtProperties jwtProps;
    private final TermsProperties termsProperties;
    private final EntityManager entityManager;

    public TokenRefreshService(
            RefreshTokenRepository refreshTokenRepository,
            UserRepository userRepository,
            JwtTokenProvider jwtTokenProvider,
            AuditLogService auditLogService,
            SecurityProperties securityProperties,
            TermsProperties termsProperties,
            EntityManager entityManager) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.userRepository = userRepository;
        this.jwtTokenProvider = jwtTokenProvider;
        this.auditLogService = auditLogService;
        this.jwtProps = securityProperties.jwt();
        this.termsProperties = termsProperties;
        this.entityManager = entityManager;
    }

    /** Execute token refresh with rotation. Includes reuse detection for security. */
    @Transactional
    public com.ahogek.cttserver.auth.dto.LoginResponse refresh(
            String rawRefreshToken, String ip, String userAgent) {
        String tokenHash = TokenUtils.hashToken(rawRefreshToken);

        RefreshToken oldToken =
                refreshTokenRepository
                        .findByTokenHash(tokenHash)
                        .orElseThrow(
                                () ->
                                        new UnauthorizedException(
                                                ErrorCode.AUTH_003, "Invalid refresh token"));

        // Take the session lock before deciding anything: a revocation that commits while this
        // refresh waits for the lock must be visible to the decision, not hidden by the state read
        // a moment earlier. Re-reading after the lock is what makes "kicked out" final.
        refreshTokenRepository.lockSession(oldToken.getSessionId());
        entityManager.refresh(oldToken);

        TokenStatus status = oldToken.determineStatus();

        if (status == TokenStatus.REVOKED) {
            // A revoked token is either a rotated one being replayed — theft — or a token of a
            // session that was revoked as a whole. Only the first is an attack: a client whose
            // session was kicked out keeps refreshing with the token it still holds, and answering
            // that with a global revocation would sign the user out of every other session too. A
            // session that has no live row is therefore reported as a plain ended session.
            boolean sessionStillLive =
                    refreshTokenRepository
                            .findFirstByUserIdAndSessionIdAndRevokedAtIsNullOrderByCreatedAtDesc(
                                    oldToken.getUserId(), oldToken.getSessionId())
                            .isPresent();
            if (!sessionStillLive) {
                throw new UnauthorizedException(ErrorCode.AUTH_003, "Session has been revoked");
            }

            refreshTokenRepository.revokeAllUserTokens(oldToken.getUserId(), Instant.now());

            auditLogService.logCritical(
                    oldToken.getUserId(),
                    AuditAction.REFRESH_TOKEN_REUSE_DETECTED,
                    ResourceType.REFRESH_TOKEN,
                    oldToken.getId().toString(),
                    AuditDetails.empty());

            throw new ForbiddenException(
                    ErrorCode.AUTH_009, "Security breach: Refresh token reuse detected");
        }

        if (status == TokenStatus.EXPIRED || oldToken.getExpiresAt().isBefore(Instant.now())) {
            throw new UnauthorizedException(ErrorCode.AUTH_007, "Refresh token has expired");
        }

        User user =
                userRepository
                        .findById(oldToken.getUserId())
                        .orElseThrow(
                                () ->
                                        new UnauthorizedException(
                                                ErrorCode.AUTH_001, "User not found"));

        switch (user.getStatus()) {
            case PENDING_VERIFICATION ->
                    throw new ForbiddenException(ErrorCode.AUTH_006, "Email not verified");
            case LOCKED -> throw new ForbiddenException(ErrorCode.AUTH_004, "Account is locked");
            case SUSPENDED, DELETED ->
                    throw new ForbiddenException(ErrorCode.AUTH_005, "Account is disabled");
            case ACTIVE -> {
                // No restrictions for active users, proceed with token refresh
            }
        }

        Instant now = Instant.now();
        oldToken.setLastUsedAt(now);
        oldToken.revoke();
        refreshTokenRepository.save(oldToken);

        String newAccessToken = jwtTokenProvider.generateAccessToken(user, oldToken.getSessionId());

        // Rotation continues the session: audience, its TTL, the device binding and the client
        // User-Agent all belong to the session rather than to the individual token, so the new row
        // inherits them instead of defaulting back to a web session.
        String issuedFor = oldToken.getIssuedFor();
        Duration rtTtl =
                oldToken.isPluginSession()
                        ? jwtProps.refreshTokenTtlPlugin()
                        : jwtProps.refreshTokenTtlWeb();
        RefreshTokenPair newRtPair =
                TokenUtils.createRefreshToken(
                        user.getId(),
                        issuedFor,
                        rtTtl,
                        oldToken.getDeviceId(),
                        oldToken.getSessionId(),
                        oldToken.getUserAgent(),
                        refreshTokenRepository);

        auditLogService.log(
                user.getId(),
                AuditAction.REFRESH_TOKEN_ROTATED,
                ResourceType.REFRESH_TOKEN,
                oldToken.getId().toString(),
                SecuritySeverity.INFO,
                AuditDetails.extension(Map.of("ip", ip, "userAgent", userAgent)));

        String currentVersion = termsProperties.currentVersion();
        String userTermsVersion = user.getTermsVersion();
        boolean termsExpired = userTermsVersion == null || !currentVersion.equals(userTermsVersion);

        return new com.ahogek.cttserver.auth.dto.LoginResponse(
                user.getId(),
                newAccessToken,
                newRtPair.rawToken(),
                jwtProps.accessTokenTtl().getSeconds(),
                "Bearer",
                termsExpired);
    }
}
