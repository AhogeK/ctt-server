package com.ahogek.cttserver.auth.service;

import com.ahogek.cttserver.audit.enums.AuditAction;
import com.ahogek.cttserver.audit.enums.ResourceType;
import com.ahogek.cttserver.audit.service.AuditLogService;
import com.ahogek.cttserver.auth.dto.LoginRequest;
import com.ahogek.cttserver.auth.dto.LoginResponse;
import com.ahogek.cttserver.auth.lockout.LoginAttemptService;
import com.ahogek.cttserver.auth.repository.RefreshTokenRepository;
import com.ahogek.cttserver.common.config.properties.SecurityProperties;
import com.ahogek.cttserver.common.context.RequestContext;
import com.ahogek.cttserver.common.context.RequestInfo;
import com.ahogek.cttserver.common.exception.ErrorCode;
import com.ahogek.cttserver.common.exception.ForbiddenException;
import com.ahogek.cttserver.common.exception.UnauthorizedException;
import com.ahogek.cttserver.common.utils.TokenUtils;
import com.ahogek.cttserver.user.entity.User;
import com.ahogek.cttserver.user.enums.UserStatus;
import com.ahogek.cttserver.user.repository.UserRepository;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.UUID;

/**
 * User login service handling authentication and JWT token generation.
 *
 * <p>This service validates user credentials, generates access and refresh tokens, and records
 * audit events for login attempts (success/failure).
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-04-03
 */
@Service
public class UserLoginService {

    private static final String ISSUED_FOR_WEB = "WEB";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AuditLogService auditLogService;
    private final LoginAttemptService loginAttemptService;
    private final SecurityProperties.JwtProperties jwtProps;

    public UserLoginService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider,
            RefreshTokenRepository refreshTokenRepository,
            AuditLogService auditLogService,
            LoginAttemptService loginAttemptService,
            SecurityProperties securityProperties) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.refreshTokenRepository = refreshTokenRepository;
        this.auditLogService = auditLogService;
        this.loginAttemptService = loginAttemptService;
        this.jwtProps = securityProperties.jwt();
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        User user =
                userRepository
                        .findByEmailIgnoreCase(request.email())
                        .orElseThrow(
                                () ->
                                        new UnauthorizedException(
                                                ErrorCode.AUTH_001, "Invalid email or password"));

        validateUserStatus(user);

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            handleFailedLogin(user);
        }

        // Only reached on successful authentication (handleFailedLogin throws)
        loginAttemptService.recordSuccess(user.getEmail());

        String accessToken = jwtTokenProvider.generateAccessToken(user);
        String refreshToken = createRefreshToken(user.getId(), request.deviceId());

        auditLogService.logSuccess(
                user.getId(),
                AuditAction.LOGIN_SUCCESS,
                ResourceType.USER,
                user.getId().toString());

        return new LoginResponse(
                user.getId(), accessToken, refreshToken, jwtProps.accessTokenTtl().getSeconds());
    }

    private void validateUserStatus(User user) {
        loginAttemptService.checkLockStatus(user);

        // Check non-locked status issues (not handled by LoginAttemptService)
        if (user.getStatus() == UserStatus.ACTIVE) {
            return;
        }
        throw switch (user.getStatus()) {
            case PENDING_VERIFICATION ->
                    new ForbiddenException(ErrorCode.AUTH_006, "Email not verified");
            case SUSPENDED, DELETED ->
                    new ForbiddenException(ErrorCode.AUTH_005, "Account is disabled");
            case LOCKED, ACTIVE -> throw new AssertionError("Unreachable");
        };
    }

    private void handleFailedLogin(User user) {
        String clientIp = RequestContext.current().map(RequestInfo::clientIp).orElse(null);
        loginAttemptService.recordFailure(user.getEmail(), clientIp);

        auditLogService.logFailure(
                user.getId(),
                AuditAction.LOGIN_FAILED,
                ResourceType.USER,
                user.getId().toString(),
                "Invalid password");

        throw new UnauthorizedException(ErrorCode.AUTH_001, "Invalid email or password");
    }

    private String createRefreshToken(UUID userId, String deviceId) {
        Duration ttl = jwtProps.refreshTokenTtlWeb();
        UUID deviceUuid = parseDeviceId(deviceId);

        TokenUtils.RefreshTokenPair tokenPair =
                TokenUtils.createRefreshToken(
                        userId, ISSUED_FOR_WEB, ttl, deviceUuid, refreshTokenRepository);

        return tokenPair.rawToken();
    }

    private UUID parseDeviceId(String deviceId) {
        if (deviceId == null || deviceId.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(deviceId);
        } catch (IllegalArgumentException _) {
            return null;
        }
    }
}
