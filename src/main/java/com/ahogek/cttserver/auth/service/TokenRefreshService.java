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
import com.ahogek.cttserver.common.exception.ErrorCode;
import com.ahogek.cttserver.common.exception.ForbiddenException;
import com.ahogek.cttserver.common.exception.UnauthorizedException;
import com.ahogek.cttserver.common.utils.TokenUtils;
import com.ahogek.cttserver.common.utils.TokenUtils.RefreshTokenPair;
import com.ahogek.cttserver.user.entity.User;
import com.ahogek.cttserver.user.enums.UserStatus;
import com.ahogek.cttserver.user.repository.UserRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Token refresh service with rotation and reuse detection.
 */
@Service
public class TokenRefreshService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuditLogService auditLogService;
    private final SecurityProperties.JwtProperties jwtProps;

    public TokenRefreshService(
            RefreshTokenRepository refreshTokenRepository,
            UserRepository userRepository,
            JwtTokenProvider jwtTokenProvider,
            AuditLogService auditLogService,
            SecurityProperties securityProperties) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.userRepository = userRepository;
        this.jwtTokenProvider = jwtTokenProvider;
        this.auditLogService = auditLogService;
        this.jwtProps = securityProperties.jwt();
    }

    /**
     * Execute token refresh with rotation.
     * Includes reuse detection for security.
     */
    @Transactional
    public com.ahogek.cttserver.auth.dto.LoginResponse refresh(
            String rawRefreshToken,
            String ip,
            String userAgent) {
        String tokenHash = TokenUtils.hashToken(rawRefreshToken);

        RefreshToken oldToken = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new UnauthorizedException(ErrorCode.AUTH_003, "Invalid refresh token"));

        TokenStatus status = oldToken.determineStatus();

        if (status == TokenStatus.REVOKED) {
            refreshTokenRepository.revokeAllUserTokens(oldToken.getUserId(), Instant.now());
            
            auditLogService.logCritical(
                    oldToken.getUserId(),
                    AuditAction.REFRESH_TOKEN_REUSE_DETECTED,
                    ResourceType.REFRESH_TOKEN,
                    oldToken.getId().toString(),
                    AuditDetails.empty()
            );
            
            throw new ForbiddenException(ErrorCode.AUTH_009, "Security breach: Refresh token reuse detected");
        }

        if (status == TokenStatus.EXPIRED || oldToken.getExpiresAt().isBefore(Instant.now())) {
            throw new UnauthorizedException(ErrorCode.AUTH_007, "Refresh token has expired");
        }

        User user = userRepository.findById(oldToken.getUserId())
                .orElseThrow(() -> new UnauthorizedException(ErrorCode.AUTH_001, "User not found"));

        switch (user.getStatus()) {
            case PENDING_VERIFICATION -> throw new ForbiddenException(ErrorCode.AUTH_006, "Email not verified");
            case LOCKED -> throw new ForbiddenException(ErrorCode.AUTH_004, "Account is locked");
            case SUSPENDED, DELETED -> throw new ForbiddenException(ErrorCode.AUTH_005, "Account is disabled");
            case ACTIVE -> {}
        }

        Instant now = Instant.now();
        oldToken.setLastUsedAt(now);
        oldToken.revoke();
        refreshTokenRepository.save(oldToken);

        String newAccessToken = jwtTokenProvider.generateAccessToken(user);

        Duration rtTtl = jwtProps.refreshTokenTtlWeb();
        RefreshTokenPair newRtPair = TokenUtils.createRefreshToken(
                user.getId(),
                "WEB",
                rtTtl,
                oldToken.getDeviceId(),
                refreshTokenRepository
        );

        auditLogService.log(
                user.getId(),
                AuditAction.REFRESH_TOKEN_ROTATED,
                ResourceType.REFRESH_TOKEN,
                oldToken.getId().toString(),
                SecuritySeverity.INFO,
                AuditDetails.extension(Map.of("ip", ip, "userAgent", userAgent))
        );

        return new com.ahogek.cttserver.auth.dto.LoginResponse(
                user.getId(),
                newAccessToken,
                newRtPair.rawToken(),
                jwtProps.accessTokenTtl().getSeconds()
        );
    }
}