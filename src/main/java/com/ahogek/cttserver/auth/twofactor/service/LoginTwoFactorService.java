package com.ahogek.cttserver.auth.twofactor.service;

import com.ahogek.cttserver.audit.enums.AuditAction;
import com.ahogek.cttserver.audit.enums.ResourceType;
import com.ahogek.cttserver.audit.service.AuditLogService;
import com.ahogek.cttserver.auth.dto.LoginResponse;
import com.ahogek.cttserver.auth.service.UserLoginService;
import com.ahogek.cttserver.common.exception.ErrorCode;
import com.ahogek.cttserver.common.exception.UnauthorizedException;
import com.ahogek.cttserver.user.entity.User;
import com.ahogek.cttserver.user.repository.UserRepository;

import org.springframework.stereotype.Service;

/**
 * Completes a second-factor login challenge.
 *
 * <p>Deliberately not transactional. A rejected code still has to consume the credential (the TOTP
 * step or the recovery code) and record an audit event, and an enclosing transaction would roll
 * both back on the way out — handing the same credential a second attempt and losing the failure
 * from the audit trail. Token issuance carries its own transaction.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-10-30
 */
@Service
public class LoginTwoFactorService {

    private final TwoFactorChallengeService challengeService;
    private final UserRepository userRepository;
    private final SecondFactorVerifier verifier;
    private final UserLoginService userLoginService;
    private final AuditLogService auditLogService;

    public LoginTwoFactorService(
            TwoFactorChallengeService challengeService,
            UserRepository userRepository,
            SecondFactorVerifier verifier,
            UserLoginService userLoginService,
            AuditLogService auditLogService) {
        this.challengeService = challengeService;
        this.userRepository = userRepository;
        this.verifier = verifier;
        this.userLoginService = userLoginService;
        this.auditLogService = auditLogService;
    }

    /**
     * Verifies the second factor and issues tokens.
     *
     * @param challengeToken the one-time challenge token
     * @param code a TOTP code or an unused recovery code
     * @return the token response
     */
    public LoginResponse complete(String challengeToken, String code) {
        TwoFactorChallengeService.ChallengePayload payload =
                challengeService.consume(challengeToken);
        User user =
                userRepository
                        .findById(payload.userId())
                        .orElseThrow(
                                () ->
                                        new UnauthorizedException(
                                                ErrorCode.AUTH_026,
                                                "Two-factor challenge is invalid or expired"));

        boolean accepted =
                user.getTotpConfirmedAt() != null && verifier.verify(user, code).accepted();
        if (!accepted) {
            auditLogService.logFailure(
                    user.getId(),
                    AuditAction.TOTP_CHALLENGE_FAILED,
                    ResourceType.USER,
                    user.getId().toString(),
                    "Invalid two-factor credential");
            challengeService.recordFailure(challengeToken, payload);
            throw new UnauthorizedException(ErrorCode.AUTH_027, "Invalid two-factor code");
        }

        return userLoginService.completeLogin(user, payload.deviceId());
    }
}
