package com.ahogek.cttserver.auth.service;

import com.ahogek.cttserver.audit.enums.AuditAction;
import com.ahogek.cttserver.audit.enums.ResourceType;
import com.ahogek.cttserver.audit.model.AuditDetails;
import com.ahogek.cttserver.audit.service.AuditLogService;
import com.ahogek.cttserver.auth.enums.TokenStatus;
import com.ahogek.cttserver.auth.repository.RefreshTokenRepository;
import com.ahogek.cttserver.common.utils.TokenUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Logout and session termination service. */
@Service
public class LogoutService {

    private static final Logger log = LoggerFactory.getLogger(LogoutService.class);

    private final RefreshTokenRepository refreshTokenRepository;
    private final AuditLogService auditLogService;

    public LogoutService(
            RefreshTokenRepository refreshTokenRepository, AuditLogService auditLogService) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.auditLogService = auditLogService;
    }

    /**
     * Execute user logout. Core logic: revoke refresh token for current session.
     *
     * @param userId User ID requesting logout
     * @param rawRefreshToken Client's plaintext refresh token
     */
    @Transactional
    public void logout(UUID userId, String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return; // Tolerance: no token is considered as logged out
        }

        String tokenHash = TokenUtils.hashToken(rawRefreshToken);

        refreshTokenRepository
                .findByTokenHash(tokenHash)
                .ifPresentOrElse(
                        token -> {
                            // Core defense: Ownership Check
                            // Absolutely cannot allow user A to revoke user B's token
                            if (!token.getUserId().equals(userId)) {
                                log.warn(
                                        "Security Alert: User {} attempted to revoke a refresh token belonging to another user.",
                                        userId);
                                auditLogService.logCritical(
                                        userId,
                                        AuditAction.SECURITY_ALERT,
                                        ResourceType.REFRESH_TOKEN,
                                        token.getId().toString(),
                                        AuditDetails.reason(
                                                "BOLA attack: attempted to revoke another user's token"));
                                return; // Intercept and silently return
                            }

                            if (token.determineStatus() == TokenStatus.VALID) {
                                token.revoke();
                                refreshTokenRepository.save(token);

                                auditLogService.logSuccess(
                                        userId,
                                        AuditAction.LOGOUT_SUCCESS,
                                        ResourceType.REFRESH_TOKEN,
                                        token.getId().toString());
                                log.debug(
                                        "User {} successfully logged out device session.", userId);
                            }
                        },
                        () ->
                                // Token does not exist, may have been revoked or forged.
                                // Logout operation should be idempotent, so return directly without
                                // exception.
                                log.debug(
                                        "Logout requested for non-existent token hash by user {}.",
                                        userId));
    }
}
